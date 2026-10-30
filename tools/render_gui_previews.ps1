param(
    [string]$OutputRoot = (Join-Path $PSScriptRoot '..\tmp\gui-previews'),
    [int]$Scale = 6
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gradleUserHome = if ($env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME
} else {
    Join-Path ([System.Environment]::GetFolderPath('UserProfile')) '.gradle'
}
$neoformAssetRoot = Join-Path $gradleUserHome 'caches\neoformruntime\assets'
$resourceRoot = Join-Path $projectRoot 'neoforge-26.2\src\main\resources\assets\earth_on_minecraft'
$textureRoot = Join-Path $resourceRoot 'textures'
$langRoot = Join-Path $resourceRoot 'lang'
$artifactRoot = Join-Path $projectRoot 'neoforge-26.2\build\moddev\artifacts'
$patchedJar = Get-ChildItem -LiteralPath $artifactRoot -Filter 'minecraft-patched-*.jar' |
    Select-Object -First 1

if ($null -eq $patchedJar) {
    throw 'Run the NeoForge build once before rendering previews; patched Minecraft resources are missing.'
}

[System.IO.Directory]::CreateDirectory($OutputRoot) | Out-Null

$langs = @{
    zh_cn = Get-Content -LiteralPath (Join-Path $langRoot 'zh_cn.json') -Raw | ConvertFrom-Json -AsHashtable
    en_us = Get-Content -LiteralPath (Join-Path $langRoot 'en_us.json') -Raw | ConvertFrom-Json -AsHashtable
}

function C([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function Fill-Rect($graphics, [System.Drawing.Color]$color, [int]$x, [int]$y, [int]$width, [int]$height) {
    if ($width -le 0 -or $height -le 0) {
        return
    }
    $brush = [System.Drawing.SolidBrush]::new($color)
    try {
        $graphics.FillRectangle($brush, $x, $y, $width, $height)
    } finally {
        $brush.Dispose()
    }
}

function Draw-Outline($graphics, [System.Drawing.Color]$color, [int]$x, [int]$y, [int]$width, [int]$height) {
    Fill-Rect $graphics $color $x $y $width 1
    Fill-Rect $graphics $color $x ($y + $height - 1) $width 1
    Fill-Rect $graphics $color $x $y 1 $height
    Fill-Rect $graphics $color ($x + $width - 1) $y 1 $height
}

function Get-Tr([string]$lang, [string]$key) {
    $value = $langs[$lang][$key]
    if ($null -eq $value) {
        return $key
    }
    return [string]$value
}

function Format-Tr([string]$lang, [string]$key, [object[]]$values) {
    $result = Get-Tr $lang $key
    foreach ($value in $values) {
        $index = $result.IndexOf('%s', [System.StringComparison]::Ordinal)
        if ($index -lt 0) {
            break
        }
        $result = $result.Substring(0, $index) + [string]$value + $result.Substring($index + 2)
    }
    return $result
}

function Open-JarBitmap([string]$entryName) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($patchedJar.FullName)
    try {
        $entry = $archive.GetEntry($entryName)
        if ($null -eq $entry) {
            throw "Missing Minecraft resource: $entryName"
        }
        $stream = $entry.Open()
        try {
            $image = [System.Drawing.Image]::FromStream($stream)
            try {
                return [System.Drawing.Bitmap]::new($image)
            } finally {
                $image.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
    } finally {
        $archive.Dispose()
    }
}

$buttonSprite = Open-JarBitmap 'assets/minecraft/textures/gui/sprites/widget/button.png'
$buttonDisabledSprite = Open-JarBitmap 'assets/minecraft/textures/gui/sprites/widget/button_disabled.png'
$progressSprite = Open-JarBitmap 'assets/minecraft/textures/gui/sprites/container/furnace/burn_progress.png'
$flameSprite = Open-JarBitmap 'assets/minecraft/textures/gui/sprites/container/furnace/lit_progress.png'

$glyphHex = [System.Collections.Generic.Dictionary[int, string]]::new()
$glyphCache = [System.Collections.Generic.Dictionary[int, object]]::new()
$cjkFont = [System.Drawing.Font]::new('SimSun', 9, [System.Drawing.FontStyle]::Regular,
    [System.Drawing.GraphicsUnit]::Pixel)

function Test-CjkCodePoint([int]$codePoint) {
    return ($codePoint -ge 0x3000 -and $codePoint -le 0x9FFF) -or
           ($codePoint -ge 0xF900 -and $codePoint -le 0xFAFF)
}

function Initialize-McFont {
    $indexFile = Get-ChildItem -LiteralPath (Join-Path $neoformAssetRoot 'indexes') -Filter '*.json' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $indexFile) {
        throw 'Minecraft asset index not found.'
    }
    $index = Get-Content -LiteralPath $indexFile.FullName -Raw | ConvertFrom-Json -AsHashtable
    $fontAsset = $index.objects['minecraft/font/unifont.zip']
    if ($null -eq $fontAsset) {
        throw 'Minecraft Unihex font asset not found.'
    }
    $hash = [string]$fontAsset.hash
    $zipPath = Join-Path $neoformAssetRoot "objects\$($hash.Substring(0, 2))\$hash"
    $archive = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
    try {
        $entry = $archive.Entries | Where-Object { $_.FullName.EndsWith('.hex') } | Select-Object -First 1
        $stream = $entry.Open()
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::ASCII)
        try {
            while (-not $reader.EndOfStream) {
                $line = $reader.ReadLine()
                $separator = $line.IndexOf(':')
                if ($separator -le 0) {
                    continue
                }
                $codePoint = [Convert]::ToInt32($line.Substring(0, $separator), 16)
                $glyphHex[$codePoint] = $line.Substring($separator + 1)
            }
        } finally {
            $reader.Dispose()
            $stream.Dispose()
        }
    } finally {
        $archive.Dispose()
    }
}

function Get-McGlyph([int]$codePoint) {
    $cached = $null
    if ($glyphCache.TryGetValue($codePoint, [ref]$cached)) {
        return $cached
    }

    $hex = $null
    if (-not $glyphHex.TryGetValue($codePoint, [ref]$hex)) {
        $fallback = [pscustomobject]@{ Width = 4; Height = 8; Advance = 5; Pixels = [byte[]]::new(32) }
        $glyphCache[$codePoint] = $fallback
        return $fallback
    }

    $byteCount = [int]($hex.Length / 2)
    $bytesPerRow = [int]($byteCount / 16)
    $sourceWidth = $bytesPerRow * 8
    $destWidth = [Math]::Max(1, [int]($sourceWidth / 2))
    $pixels = [byte[]]::new($destWidth * 8)
    $bytes = [byte[]]::new($byteCount)
    for ($i = 0; $i -lt $byteCount; $i++) {
        $bytes[$i] = [Convert]::ToByte($hex.Substring($i * 2, 2), 16)
    }

    $minX = $destWidth
    $maxX = -1
    for ($dy = 0; $dy -lt 8; $dy++) {
        for ($dx = 0; $dx -lt $destWidth; $dx++) {
            $coverage = 0
            for ($oy = 0; $oy -lt 2; $oy++) {
                $sy = $dy * 2 + $oy
                for ($ox = 0; $ox -lt 2; $ox++) {
                    $sx = $dx * 2 + $ox
                    $byteIndex = $sy * $bytesPerRow + [int]($sx / 8)
                    $mask = 0x80 -shr ($sx % 8)
                    if (($bytes[$byteIndex] -band $mask) -ne 0) {
                        $coverage++
                    }
                }
            }
            if ($coverage -gt 0) {
                $pixels[$dy * $destWidth + $dx] = [byte]$coverage
                $minX = [Math]::Min($minX, $dx)
                $maxX = [Math]::Max($maxX, $dx)
            }
        }
    }

    $isWide = ($codePoint -ge 0x3000 -and $codePoint -le 0x9FFF) -or
              ($codePoint -ge 0xF900 -and $codePoint -le 0xFAFF)
    if ($codePoint -eq 32) {
        $advance = 4
    } elseif ($isWide) {
        $advance = 9
    } elseif ($maxX -lt $minX) {
        $advance = 3
    } else {
        $advance = [Math]::Max(2, $maxX - $minX + 2)
    }
    $glyph = [pscustomobject]@{
        Width = $destWidth
        Height = 8
        Advance = $advance
        MinX = if ($maxX -lt $minX) { 0 } else { $minX }
        Pixels = $pixels
    }
    $glyphCache[$codePoint] = $glyph
    return $glyph
}

function Measure-McText([string]$text) {
    $width = 0
    foreach ($char in $text.ToCharArray()) {
        $width += (Get-McGlyph ([int]$char)).Advance
    }
    return $width
}

function Fit-McText([string]$text, [int]$maxWidth) {
    if ((Measure-McText $text) -le $maxWidth) {
        return $text
    }
    $suffix = '...'
    $result = ''
    foreach ($char in $text.ToCharArray()) {
        $candidate = $result + $char + $suffix
        if ((Measure-McText $candidate) -gt $maxWidth) {
            break
        }
        $result += $char
    }
    return $result + $suffix
}

function Draw-McTextRaw($bitmap, [string]$text, [int]$x, [int]$y, [System.Drawing.Color]$color) {
    $cursor = $x
    foreach ($char in $text.ToCharArray()) {
        $codePoint = [int]$char
        if (Test-CjkCodePoint $codePoint) {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            $brush = [System.Drawing.SolidBrush]::new($color)
            try {
                $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::SingleBitPerPixelGridFit
                $graphics.DrawString([string]$char, $cjkFont, $brush, [float]$cursor, [float]($y - 1),
                    [System.Drawing.StringFormat]::GenericTypographic)
            } finally {
                $brush.Dispose()
                $graphics.Dispose()
            }
            $cursor += 9
            continue
        }

        $glyph = Get-McGlyph $codePoint
        for ($gy = 0; $gy -lt $glyph.Height; $gy++) {
            for ($gx = 0; $gx -lt $glyph.Width; $gx++) {
                $coverage = [int]$glyph.Pixels[$gy * $glyph.Width + $gx]
                if ($coverage -le 0) {
                    continue
                }
                $px = $cursor + $gx - $glyph.MinX
                $py = $y + $gy
                if ($px -ge 0 -and $py -ge 0 -and $px -lt $bitmap.Width -and $py -lt $bitmap.Height) {
                    $background = $bitmap.GetPixel($px, $py)
                    # Gamma-correct 2x2 coverage so dark text is not washed out on light panels.
                    $amount = [Math]::Sqrt($coverage / 4.0)
                    $red = [int]($color.R * $amount + $background.R * (1.0 - $amount))
                    $green = [int]($color.G * $amount + $background.G * (1.0 - $amount))
                    $blue = [int]($color.B * $amount + $background.B * (1.0 - $amount))
                    $bitmap.SetPixel($px, $py, [System.Drawing.Color]::FromArgb(255, $red, $green, $blue))
                }
            }
        }
        $cursor += $glyph.Advance
    }
}

function Draw-McText($bitmap, [string]$text, [int]$x, [int]$y, [System.Drawing.Color]$color, [bool]$shadow = $false) {
    if ($shadow) {
        Draw-McTextRaw $bitmap $text ($x + 1) ($y + 1) (C '#3F3F3F')
    }
    Draw-McTextRaw $bitmap $text $x $y $color
}

function Draw-McTextCentered($bitmap, [string]$text, [int]$centerX, [int]$y,
                             [System.Drawing.Color]$color, [bool]$shadow = $false) {
    Draw-McText $bitmap $text ($centerX - [int]((Measure-McText $text) / 2)) $y $color $shadow
}

function Draw-NineSlice($graphics, $sprite, [int]$x, [int]$y, [int]$width, [int]$height, [int]$border) {
    $borderX = [Math]::Min($border, [int]($width / 2))
    $borderY = [Math]::Min($border, [int]($height / 2))
    $sourceW = $sprite.Width
    $sourceH = $sprite.Height
    $sourceCenterW = $sourceW - 2 * $border
    $sourceCenterH = $sourceH - 2 * $border
    $destCenterW = $width - 2 * $borderX
    $destCenterH = $height - 2 * $borderY
    $unit = [System.Drawing.GraphicsUnit]::Pixel

    $parts = @(
        @($x, $y, $borderX, $borderY, 0, 0, $border, $border),
        @(($x + $borderX), $y, $destCenterW, $borderY, $border, 0, $sourceCenterW, $border),
        @(($x + $width - $borderX), $y, $borderX, $borderY, ($sourceW - $border), 0, $border, $border),
        @($x, ($y + $borderY), $borderX, $destCenterH, 0, $border, $border, $sourceCenterH),
        @(($x + $borderX), ($y + $borderY), $destCenterW, $destCenterH, $border, $border, $sourceCenterW, $sourceCenterH),
        @(($x + $width - $borderX), ($y + $borderY), $borderX, $destCenterH, ($sourceW - $border), $border, $border, $sourceCenterH),
        @($x, ($y + $height - $borderY), $borderX, $borderY, 0, ($sourceH - $border), $border, $border),
        @(($x + $borderX), ($y + $height - $borderY), $destCenterW, $borderY, $border, ($sourceH - $border), $sourceCenterW, $border),
        @(($x + $width - $borderX), ($y + $height - $borderY), $borderX, $borderY, ($sourceW - $border), ($sourceH - $border), $border, $border)
    )
    foreach ($p in $parts) {
        if ($p[2] -le 0 -or $p[3] -le 0) {
            continue
        }
        $graphics.DrawImage($sprite,
            [System.Drawing.Rectangle]::new($p[0], $p[1], $p[2], $p[3]),
            [System.Drawing.Rectangle]::new($p[4], $p[5], $p[6], $p[7]), $unit)
    }
}

function Draw-Button($bitmap, [int]$x, [int]$y, [int]$width, [int]$height,
                     [string]$text = '', [bool]$active = $true) {
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        Draw-NineSlice $graphics $(if ($active) { $buttonSprite } else { $buttonDisabledSprite }) `
            $x $y $width $height $(if ($active) { 3 } else { 1 })
    } finally {
        $graphics.Dispose()
    }
    if (-not [string]::IsNullOrEmpty($text)) {
        $label = Fit-McText $text ($width - 4)
        Draw-McTextCentered $bitmap $label ($x + [int]($width / 2)) ($y + [Math]::Max(1, [int](($height - 8) / 2))) `
            $(if ($active) { C '#FFFFFF' } else { C '#A0A0A0' }) $true
    }
}

function Draw-Texture($graphics, [string]$path, [int]$x, [int]$y, [int]$width, [int]$height) {
    if (-not (Test-Path -LiteralPath $path)) {
        return
    }
    $image = [System.Drawing.Image]::FromFile($path)
    try {
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.DrawImage($image, [System.Drawing.Rectangle]::new($x, $y, $width, $height))
    } finally {
        $image.Dispose()
    }
}

function Draw-Item($bitmap, [string]$relativePath, [int]$x, [int]$y) {
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        Draw-Texture $graphics (Join-Path $textureRoot $relativePath) $x $y 16 16
    } finally {
        $graphics.Dispose()
    }
}

function New-ContainerCanvas([string]$fileName) {
    $sourcePath = Join-Path $textureRoot "gui\container\$fileName"
    $source = [System.Drawing.Bitmap]::FromFile($sourcePath)
    try {
        $width = 176
        $height = 0
        for ($y = $source.Height - 1; $y -ge 0 -and $height -eq 0; $y--) {
            for ($x = 0; $x -lt $width; $x++) {
                if ($source.GetPixel($x, $y).A -gt 0) {
                    $height = $y + 1
                    break
                }
            }
        }
        if ($height -eq 0) {
            $height = 166
        }
        $bitmap = [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $graphics.DrawImage($source, [System.Drawing.Rectangle]::new(0, 0, $width, $height),
                [System.Drawing.Rectangle]::new(0, 0, $width, $height), [System.Drawing.GraphicsUnit]::Pixel)
        } finally {
            $graphics.Dispose()
        }
        return $bitmap
    } finally {
        $source.Dispose()
    }
}

function Save-Preview($bitmap, [string]$name) {
    $scaled = [System.Drawing.Bitmap]::new($bitmap.Width * $Scale, $bitmap.Height * $Scale,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($scaled)
    try {
        $graphics.Clear((C '#151515'))
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.DrawImage($bitmap, [System.Drawing.Rectangle]::new(0, 0, $scaled.Width, $scaled.Height),
            [System.Drawing.Rectangle]::new(0, 0, $bitmap.Width, $bitmap.Height), [System.Drawing.GraphicsUnit]::Pixel)
        $path = Join-Path $OutputRoot $name
        $scaled.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        return $path
    } finally {
        $graphics.Dispose()
        $scaled.Dispose()
    }
}

function Draw-Inset($graphics, [int]$x, [int]$y, [int]$width, [int]$height) {
    Fill-Rect $graphics (C '#555555') $x $y $width $height
    Fill-Rect $graphics (C '#FFFFFF') ($x + 1) ($y + 1) ($width - 2) ($height - 2)
    Fill-Rect $graphics (C '#8B8B8B') ($x + 2) ($y + 2) ($width - 3) ($height - 3)
}

function Draw-EnergyBar($graphics, [int]$x, [int]$y, [int]$width, [int]$height, [int]$percent) {
    Fill-Rect $graphics (C '#3B3B3B') ($x - 1) ($y - 1) ($width + 2) ($height + 2)
    Fill-Rect $graphics (C '#10151A') $x $y $width $height
    $filled = [int]($width * [Math]::Max(0, [Math]::Min(100, $percent)) / 100)
    Fill-Rect $graphics (C '#31A9C9') $x $y $filled $height
    Fill-Rect $graphics (C '#82DFF0') $x $y $filled 2
}

function Draw-NotebookGlyph($graphics, [int]$x, [int]$y) {
    Fill-Rect $graphics (C '#E5D6A6') ($x + 1) ($y + 1) 4 9
    Fill-Rect $graphics (C '#F4E9C6') ($x + 5) ($y + 2) 4 8
    Fill-Rect $graphics (C '#6D4C2D') ($x + 4) ($y + 1) 2 9
    Fill-Rect $graphics (C '#7A684B') ($x + 2) ($y + 3) 2 1
    Fill-Rect $graphics (C '#7A684B') ($x + 6) ($y + 4) 2 1
}

function Draw-IoGlyph($graphics, [int]$x, [int]$y) {
    Fill-Rect $graphics (C '#2D74C4') ($x + 1) ($y + 3) 7 2
    Fill-Rect $graphics (C '#2D74C4') ($x + 1) ($y + 2) 2 4
    Fill-Rect $graphics (C '#C46A22') ($x + 3) ($y + 7) 7 2
    Fill-Rect $graphics (C '#C46A22') ($x + 8) ($y + 6) 2 4
}

function Draw-RedstoneGlyph($graphics, [int]$x, [int]$y, [string]$mode) {
    if ($mode -eq 'always') {
        Fill-Rect $graphics (C '#7A1D1D') ($x + 1) ($y + 1) 9 9
        Fill-Rect $graphics (C '#B52A2A') ($x + 2) ($y + 2) 7 7
        for ($i = 1; $i -lt 9; $i++) {
            Fill-Rect $graphics (C '#E05A4F') ($x + $i) ($y + 9 - $i) 2 2
        }
        return
    }
    $head = if ($mode -eq 'signal') { C '#FF5A32' } else { C '#5A4740' }
    $glow = if ($mode -eq 'signal') { C '#FFD05A' } else { C '#81716B' }
    Fill-Rect $graphics (C '#6B3D2A') ($x + 4) ($y + 4) 3 6
    Fill-Rect $graphics $head ($x + 2) ($y + 2) 7 3
    Fill-Rect $graphics $glow ($x + 4) ($y + 1) 3 2
}

function Draw-PowerGlyph($graphics, [int]$x, [int]$y, [System.Drawing.Color]$color) {
    Fill-Rect $graphics $color ($x + 5) $y 4 5
    Fill-Rect $graphics $color ($x + 2) ($y + 4) 6 4
    Fill-Rect $graphics $color ($x + 4) ($y + 7) 3 5
}

function Draw-Progress($graphics, [int]$x, [int]$y, [int]$width) {
    if ($width -le 0) {
        return
    }
    $graphics.DrawImage($progressSprite,
        [System.Drawing.Rectangle]::new($x, $y, $width, 16),
        [System.Drawing.Rectangle]::new(0, 0, $width, 16), [System.Drawing.GraphicsUnit]::Pixel)
}

function Draw-Flame($graphics, [int]$x, [int]$y, [int]$height) {
    if ($height -le 0) {
        return
    }
    $sourceY = 14 - $height
    $graphics.DrawImage($flameSprite,
        [System.Drawing.Rectangle]::new($x, $y + $sourceY, 14, $height),
        [System.Drawing.Rectangle]::new(0, $sourceY, 14, $height), [System.Drawing.GraphicsUnit]::Pixel)
}

function Assert-NoOverlap([string]$aName, [int[]]$a, [string]$bName, [int[]]$b) {
    $overlap = $a[0] -lt $b[0] + $b[2] -and $a[0] + $a[2] -gt $b[0] -and
               $a[1] -lt $b[1] + $b[3] -and $a[1] + $a[3] -gt $b[1]
    if ($overlap) {
        throw "GUI overlap: $aName [$($a -join ',')] intersects $bName [$($b -join ',')]"
    }
}

function Assert-Within([string]$name, [int[]]$rect, [int]$width, [int]$height) {
    if ($rect[0] -lt 0 -or $rect[1] -lt 0 -or
        $rect[0] + $rect[2] -gt $width -or $rect[1] + $rect[3] -gt $height) {
        throw "GUI bounds: $name [$($rect -join ',')] exceeds ${width}x${height}"
    }
}

function Render-Processing([string]$lang, [bool]$multiblock, [string]$name,
                           [string]$statusKey = 'screen.earth_on_minecraft.machine.running') {
    $bitmap = New-ContainerCanvas 'processing_machine.png'
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $title = Get-Tr $lang $(if ($multiblock) { 'block.earth_on_minecraft.steam_cracker' } else { 'block.earth_on_minecraft.crystallizer' })
        $family = Get-Tr $lang $(if ($multiblock) { 'screen.earth_on_minecraft.machine.family.thermal' } else { 'screen.earth_on_minecraft.machine.family.crystallization' })
        $inventory = if ($lang -eq 'zh_cn') { '物品栏' } else { 'Inventory' }
        $accent = if ($multiblock) { C '#A44C2B' } else { C '#6B7692' }

        Draw-Button $bitmap 7 56 14 14
        if (-not $multiblock) {
            Draw-Button $bitmap 23 56 14 14
        }
        Draw-Button $bitmap 72 56 26 14 '2/3'
        Draw-Button $bitmap 155 4 14 14

        Fill-Rect $graphics $accent 6 19 22 2
        Draw-Item $bitmap $(if ($multiblock) { 'block\steam_cracker.png' } else { 'block\crystallizer.png' }) 8 22
        Draw-McText $bitmap (Fit-McText $family 31) 7 41 $accent
        Draw-McText $bitmap (Fit-McText $title 140) 8 6 (C '#404040')
        Draw-McText $bitmap $inventory 8 94 (C '#404040')

        $hasInput = $statusKey -ne 'screen.earth_on_minecraft.machine.empty_input_short'
        $hasRecipe = $hasInput -and $statusKey -ne 'screen.earth_on_minecraft.machine.unsupported_input_short'
        if ($hasInput) {
            Draw-Item $bitmap 'item\salt_dust.png' 38 35
        }
        if ($hasRecipe) {
            Draw-Item $bitmap 'item\neutral_salt.png' 102 20
            Draw-Item $bitmap 'item\tailings_dust.png' 120 20
            Draw-Item $bitmap 'item\gypsum_dust.png' 138 20
        }
        if ($statusKey -eq 'screen.earth_on_minecraft.machine.missing_secondary_short') {
            Draw-Item $bitmap 'item\salt_dust.png' 8 76
            Draw-Item $bitmap 'item\distilled_water.png' 26 76
        } elseif ($hasRecipe) {
            Draw-Item $bitmap 'item\distilled_water.png' 8 76
        }
        if ($statusKey -eq 'screen.earth_on_minecraft.machine.missing_reductant_short' -and -not $multiblock) {
            Draw-Item $bitmap 'item\distilled_water.png' 26 76
        }
        if ($statusKey -eq 'screen.earth_on_minecraft.machine.running') {
            Draw-Progress $graphics 64 38 15
        }

        Fill-Rect $graphics (C '#20282D') 39 58 16 16
        Draw-PowerGlyph $graphics 42 60 $accent
        Draw-RedstoneGlyph $graphics 9 58 $(if ($statusKey -eq 'screen.earth_on_minecraft.machine.redstone_paused_short') { 'signal' } else { 'always' })
        if (-not $multiblock) {
            Draw-IoGlyph $graphics 25 58
        }
        Draw-NotebookGlyph $graphics 157 6

        $statusColor = if ($statusKey -eq 'screen.earth_on_minecraft.machine.running' -or
                           $statusKey -eq 'screen.earth_on_minecraft.machine.recipe_ready') {
            C '#207030'
        } elseif ($statusKey -eq 'screen.earth_on_minecraft.machine.empty_input_short') {
            C '#66727A'
        } else {
            C '#AA3322'
        }
        Draw-Inset $graphics 100 62 69 11
        Fill-Rect $graphics $statusColor 103 65 5 5
        Draw-McText $bitmap (Fit-McText (Get-Tr $lang $statusKey) 54) 111 63 $statusColor
    } finally {
        $graphics.Dispose()
    }
    try {
        return Save-Preview $bitmap $name
    } finally {
        $bitmap.Dispose()
    }
}

function Render-Generator([string]$lang, [bool]$turbine, [string]$name) {
    $bitmap = New-ContainerCanvas 'energy_generator.png'
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $titleKey = if ($turbine) { 'block.earth_on_minecraft.steam_turbine_generator' } else { 'block.earth_on_minecraft.combustion_generator' }
        $typeKey = if ($turbine) { 'screen.earth_on_minecraft.energy.type.steam_turbine' } else { 'screen.earth_on_minecraft.energy.type.combustion' }
        $sourceKey = if ($turbine) { 'screen.earth_on_minecraft.energy.steam_source' } else { 'screen.earth_on_minecraft.energy.fuel' }
        $accent = if ($turbine) { C '#2B91AA' } else { C '#B05B27' }
        $inventory = if ($lang -eq 'zh_cn') { '物品栏' } else { 'Inventory' }

        Draw-Button $bitmap 155 4 14 14
        Draw-McText $bitmap (Fit-McText (Get-Tr $lang $titleKey) 140) 8 6 (C '#404040')
        Draw-McText $bitmap $inventory 8 73 (C '#404040')
        Fill-Rect $graphics $accent 6 19 60 2
        Draw-Item $bitmap $(if ($turbine) { 'block\steam_turbine_generator_front.png' } else { 'block\combustion_generator_front.png' }) 8 22
        Draw-McText $bitmap (Fit-McText (Get-Tr $lang $typeKey) 38) 27 26 $accent
        Draw-Item $bitmap $(if ($turbine) { 'item\steam_turbine_assembly.png' } else { 'item\coal_dust.png' }) 38 57
        Draw-Flame $graphics 59 59 11
        Draw-EnergyBar $graphics 76 31 92 9 62
        Draw-McText $bitmap '40k/64k EOU' 76 20 (C '#404040')
        Draw-McText $bitmap (Get-Tr $lang $sourceKey) 26 47 (C '#606060')
        Draw-Inset $graphics 76 45 92 11
        Fill-Rect $graphics (C '#1E7C9A') 79 48 5 5
        Draw-McText $bitmap (Get-Tr $lang 'screen.earth_on_minecraft.machine.running') 87 46 (C '#1E7C9A')
        Draw-McText $bitmap $(if ($turbine) { '+256 / 512 EOU/t' } else { '+64 / 128 EOU/t' }) 76 59 (C '#404040')
        Draw-NotebookGlyph $graphics 157 6
    } finally {
        $graphics.Dispose()
    }
    try {
        return Save-Preview $bitmap $name
    } finally {
        $bitmap.Dispose()
    }
}

function Render-Battery([string]$lang, [string]$name) {
    $bitmap = New-ContainerCanvas 'battery_box.png'
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $inventory = if ($lang -eq 'zh_cn') { '物品栏' } else { 'Inventory' }
        Draw-Button $bitmap 155 4 14 14
        Draw-McText $bitmap (Fit-McText (Get-Tr $lang 'block.earth_on_minecraft.battery_box') 140) 8 6 (C '#404040')
        Draw-McText $bitmap $inventory 8 73 (C '#404040')
        Draw-McText $bitmap '128k/256k EOU' 20 20 (C '#404040')
        Draw-McText $bitmap '50%' 136 20 (C '#404040')
        Draw-EnergyBar $graphics 20 31 136 10 50
        Draw-McTextCentered $bitmap (Get-Tr $lang 'screen.earth_on_minecraft.energy.battery_flow') 88 45 (C '#606060')

        Fill-Rect $graphics (C '#20282D') 24 55 10 10
        Fill-Rect $graphics (C '#10151A') 25 56 8 8
        Fill-Rect $graphics (C '#2D74C4') 26 59 6 2
        Fill-Rect $graphics (C '#2D74C4') 30 57 2 6
        Fill-Rect $graphics (C '#20282D') 142 55 10 10
        Fill-Rect $graphics (C '#10151A') 143 56 8 8
        Fill-Rect $graphics (C '#C46A22') 144 59 6 2
        Fill-Rect $graphics (C '#C46A22') 144 57 2 6
        Fill-Rect $graphics (C '#555555') 34 59 108 2
        Fill-Rect $graphics (C '#1E7C9A') 83 57 5 6
        Draw-McTextCentered $bitmap (Format-Tr $lang 'screen.earth_on_minecraft.energy.battery_throughput' @(128)) 88 63 (C '#404040')
        Draw-NotebookGlyph $graphics 157 6
    } finally {
        $graphics.Dispose()
    }
    try {
        return Save-Preview $bitmap $name
    } finally {
        $bitmap.Dispose()
    }
}

function Side-ModeText([string]$lang, [string]$face, [string]$mode) {
    return Format-Tr $lang 'screen.earth_on_minecraft.side.config.button' @(
        (Get-Tr $lang "screen.earth_on_minecraft.side.relative.short.$face"),
        (Get-Tr $lang "screen.earth_on_minecraft.side.mode.short.$mode"))
}

function Render-SideConfig([string]$lang, [string]$name) {
    $bitmap = [System.Drawing.Bitmap]::new(200, 183, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear((C '#252525'))
        Fill-Rect $graphics ([System.Drawing.Color]::FromArgb(176, 0, 0, 0)) 0 0 200 183
        $left = 12
        $top = 17
        Fill-Rect $graphics (C '#555555') $left $top 176 148
        Fill-Rect $graphics (C '#FFFFFF') ($left + 1) ($top + 1) 174 146
        Fill-Rect $graphics (C '#C6C6C6') ($left + 3) ($top + 3) 171 143
        Fill-Rect $graphics (C '#8B8B8B') ($left + 7) ($top + 34) 162 1
        Draw-McTextCentered $bitmap (Fit-McText (Get-Tr $lang 'screen.earth_on_minecraft.side.config.title') 156) 100 ($top + 8) (C '#404040')
        Draw-McTextCentered $bitmap (Fit-McText (Get-Tr $lang 'screen.earth_on_minecraft.side.config.subtitle') 156) 100 ($top + 21) (C '#606060')

        $faces = @(
            @('top', 48, 39, 'input'), @('left', 10, 59, 'off'),
            @('front', 48, 59, 'both'), @('right', 86, 59, 'output'),
            @('back', 124, 59, 'input'), @('bottom', 48, 79, 'off')
        )
        foreach ($face in $faces) {
            Draw-Button $bitmap ($left + $face[1]) ($top + $face[2]) 36 18 (Side-ModeText $lang $face[0] $face[3])
        }

        $legend = @(
            @('input', '#2D74C4', 9), @('output', '#C46A22', 51),
            @('both', '#2E8B57', 93), @('off', '#555555', 135)
        )
        foreach ($entry in $legend) {
            Fill-Rect $graphics (C $entry[1]) ($left + $entry[2]) ($top + 107) 6 6
            Draw-McText $bitmap (Get-Tr $lang "screen.earth_on_minecraft.side.$($entry[0])") `
                ($left + $entry[2] + 9) ($top + 105) (C '#404040')
        }
        Draw-McText $bitmap (Fit-McText (Get-Tr $lang 'screen.earth_on_minecraft.side.config.hint') 104) `
            ($left + 9) ($top + 126) (C '#606060')
        Draw-Button $bitmap ($left + 118) ($top + 124) 48 16 (Get-Tr $lang 'screen.earth_on_minecraft.side.config.done')
    } finally {
        $graphics.Dispose()
    }
    try {
        return Save-Preview $bitmap $name
    } finally {
        $bitmap.Dispose()
    }
}

function Wrap-McText([string]$text, [int]$width) {
    $result = [System.Collections.Generic.List[string]]::new()
    $current = ''
    foreach ($char in $text.ToCharArray()) {
        if ($char -eq "`r") {
            continue
        }
        if ($char -eq "`n") {
            $result.Add($current)
            $current = ''
            continue
        }
        if ((Measure-McText ($current + $char)) -gt $width -and $current.Length -gt 0) {
            $result.Add($current.TrimEnd())
            $current = ''
        }
        $current += $char
    }
    if ($current.Length -gt 0) {
        $result.Add($current.TrimEnd())
    }
    return $result
}

function Render-NotebookCompact([string]$lang, [string]$name) {
    $bitmap = [System.Drawing.Bitmap]::new(200, 183, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear((C '#1A1A1A'))
        $left = 6; $top = 6; $bw = 188; $bh = 171
        Fill-Rect $graphics (C '#D6B77D') $left $top $bw $bh
        Fill-Rect $graphics (C '#F0DFBA') ($left + 3) ($top + 3) ($bw - 6) ($bh - 6)
        Draw-Outline $graphics (C '#50351F') $left $top $bw $bh
        Fill-Rect $graphics ([System.Drawing.Color]::FromArgb(31, 42, 33, 24)) ($left + 4) ($top + 4) ($bw - 8) 16
        Fill-Rect $graphics (C '#D6B77D') ($left + 7) ($top + 41) ($bw - 14) 1

        $pageText = Format-Tr $lang 'screen.earth_on_minecraft.notebook.page' @(1, 32)
        Draw-McText $bitmap (Fit-McText (Get-Tr $lang 'screen.earth_on_minecraft.notebook.title') ($bw - 76)) `
            ($left + 10) ($top + 8) (C '#2B2117')
        Draw-McText $bitmap $pageText ($left + $bw - 10 - (Measure-McText $pageText)) ($top + 8) (C '#755F42')

        $sectionKeys = @('start', 'geology', 'industry', 'advanced', 'reference')
        $sectionW = 32
        for ($i = 0; $i -lt $sectionKeys.Count; $i++) {
            $key = "screen.earth_on_minecraft.notebook.section.$($sectionKeys[$i]).short"
            Draw-Button $bitmap ($left + 8 + $i * 34) ($top + 24) $sectionW 14 (Get-Tr $lang $key) ($i -ne 0)
        }

        $contentX = $left + 12
        $contentY = $top + 49
        $contentW = $bw - 24
        Fill-Rect $graphics ([System.Drawing.Color]::FromArgb(32, 255, 255, 255)) ($contentX - 6) ($contentY - 6) ($contentW + 4) 20
        Fill-Rect $graphics (C '#2D6841') ($contentX - 6) ($contentY - 6) 4 20
        $heading = Get-Tr $lang 'handbook.earth_on_minecraft.page.intro.title'
        Draw-McText $bitmap (Fit-McText $heading ($contentW - 8)) $contentX ($contentY - 3) (C '#2D6841')
        Fill-Rect $graphics (C '#2D6841') $contentX ($contentY + 12) ([Math]::Min($contentW, 146)) 1

        $lines = Wrap-McText (Get-Tr $lang 'handbook.earth_on_minecraft.page.intro.lines') ($contentW - 10)
        $y = $contentY + 18
        for ($i = 0; $i -lt [Math]::Min(5, $lines.Count); $i++) {
            Fill-Rect $graphics (C '#2D6841') ($contentX + 5) ($y + 4) 3 3
            Draw-McText $bitmap $lines[$i] ($contentX + 10) $y (C '#3C2F20')
            $y += 12
        }

        $bottom = $top + $bh - 21
        Draw-Button $bitmap ($left + 8) $bottom 36 16 (Get-Tr $lang 'screen.earth_on_minecraft.notebook.close')
        Draw-Button $bitmap ($left + $bw - 112) $bottom 50 16 (Get-Tr $lang 'screen.earth_on_minecraft.notebook.prev') $false
        Draw-Button $bitmap ($left + $bw - 58) $bottom 50 16 (Get-Tr $lang 'screen.earth_on_minecraft.notebook.next')
    } finally {
        $graphics.Dispose()
    }
    try {
        return Save-Preview $bitmap $name
    } finally {
        $bitmap.Dispose()
    }
}

function Render-Settlement([string]$lang, [string]$name) {
    $bitmap = [System.Drawing.Bitmap]::new(176, 166, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        Fill-Rect $graphics (C '#6D4C2D') 0 0 176 166
        Fill-Rect $graphics (C '#D4C39D') 3 3 170 160
        Fill-Rect $graphics (C '#F0E5C7') 6 6 164 154
        Fill-Rect $graphics (C '#6D4C2D') 7 26 162 1
        Fill-Rect $graphics (C '#D4C39D') 7 46 162 1
        Fill-Rect $graphics (C '#D4C39D') 7 144 162 1

        $title = if ($lang -eq 'zh_cn') { '河谷工业镇' } else { 'River Valley Works' }
        $subtitle = if ($lang -eq 'zh_cn') { '城镇 · 工业化' } else { 'Town · Industrial' }
        Draw-McTextCentered $bitmap $title 88 7 (C '#2E2A24')
        Draw-McTextCentered $bitmap $subtitle 88 17 (C '#665E51')
        Draw-Button $bitmap 8 28 52 16 $(if ($lang -eq 'zh_cn') { '概览' } else { 'Overview' }) $false
        Draw-Button $bitmap 62 28 52 16 $(if ($lang -eq 'zh_cn') { '居民' } else { 'People' })
        Draw-Button $bitmap 116 28 52 16 $(if ($lang -eq 'zh_cn') { '设施' } else { 'Facilities' })

        $labels = if ($lang -eq 'zh_cn') {
            @(@('聚落类型', '矿冶聚落'), @('人口', '18'), @('产业', '采矿 / 冶炼'), @('需求', '电力 / 水'), @('供应', '铁矿 / 煤'))
        } else {
            @(@('Profile', 'Mining works'), @('Population', '18'), @('Industries', 'Mining / Smelting'), @('Needs', 'Power / Water'), @('Supplies', 'Iron / Coal'))
        }
        $y = 52
        for ($i = 0; $i -lt $labels.Count; $i++) {
            $labelColor = if ($i -eq 3) { C '#A85C32' } else { C '#2E6F62' }
            Draw-McText $bitmap $labels[$i][0] 10 $y $labelColor
            Draw-McText $bitmap (Fit-McText $labels[$i][1] $(if ($i -lt 3) { 94 } else { 118 })) `
                $(if ($i -lt 3) { 72 } else { 48 }) $y (C '#2E2A24')
            $y += $(if ($i -eq 2) { 16 } else { 13 })
        }
        Fill-Rect $graphics (C '#4A4238') 10 124 156 9
        Fill-Rect $graphics (C '#B9A980') 11 125 154 7
        Fill-Rect $graphics (C '#2E6F62') 11 125 112 7
        Draw-McTextCentered $bitmap $(if ($lang -eq 'zh_cn') { '治安 73' } else { 'Security 73' }) 88 124 (C '#FFFFFF')
        Draw-McText $bitmap $(if ($lang -eq 'zh_cn') { '声望：12' } else { 'Reputation: 12' }) 10 133 (C '#665E51')
        Draw-Button $bitmap 154 146 14 14
        Draw-NotebookGlyph $graphics 156 148
    } finally {
        $graphics.Dispose()
    }
    try {
        return Save-Preview $bitmap $name
    } finally {
        $bitmap.Dispose()
    }
}

function New-ContactSheet([string[]]$paths) {
    $images = @($paths | ForEach-Object { [System.Drawing.Bitmap]::FromFile($_) })
    try {
        $thumbScale = 0.5
        $cellW = [int](($images | Measure-Object Width -Maximum).Maximum * $thumbScale) + 24
        $cellH = [int](($images | Measure-Object Height -Maximum).Maximum * $thumbScale) + 24
        $cols = 3
        $rows = [int][Math]::Ceiling($images.Count / $cols)
        $sheet = [System.Drawing.Bitmap]::new($cellW * $cols, $cellH * $rows)
        $graphics = [System.Drawing.Graphics]::FromImage($sheet)
        try {
            $graphics.Clear((C '#202124'))
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            for ($i = 0; $i -lt $images.Count; $i++) {
                $col = $i % $cols
                $row = [int][Math]::Floor($i / $cols)
                $w = [int]($images[$i].Width * $thumbScale)
                $h = [int]($images[$i].Height * $thumbScale)
                $x = $col * $cellW + [int](($cellW - $w) / 2)
                $y = $row * $cellH + [int](($cellH - $h) / 2)
                $graphics.DrawImage($images[$i], [System.Drawing.Rectangle]::new($x, $y, $w, $h))
            }
            $path = Join-Path $OutputRoot 'earth-on-minecraft-gui-contact-sheet.png'
            $sheet.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
            return $path
        } finally {
            $graphics.Dispose()
            $sheet.Dispose()
        }
    } finally {
        foreach ($image in $images) {
            $image.Dispose()
        }
    }
}

Initialize-McFont

Assert-NoOverlap 'route button' @(72, 56, 26, 14) 'fuel slot' @(37, 56, 18, 18)
Assert-NoOverlap 'redstone button' @(7, 56, 14, 14) 'inventory label' @(8, 73, 40, 8)
Assert-NoOverlap 'side button' @(23, 56, 14, 14) 'inventory label' @(8, 73, 40, 8)
Assert-NoOverlap 'status line' @(100, 62, 69, 11) 'inventory label' @(8, 73, 40, 8)
Assert-NoOverlap 'processing status' @(100, 62, 69, 11) 'processing outputs' @(101, 19, 73, 41)
Assert-NoOverlap 'generator status' @(76, 45, 92, 11) 'generator rate' @(76, 59, 92, 8)
Assert-NoOverlap 'battery throughput' @(40, 63, 96, 8) 'battery input port' @(24, 55, 10, 10)
Assert-NoOverlap 'battery throughput' @(40, 63, 96, 8) 'battery output port' @(142, 55, 10, 10)
Assert-NoOverlap 'settlement subtitle' @(11, 17, 154, 8) 'settlement top divider' @(7, 26, 162, 1)
Assert-NoOverlap 'settlement reputation' @(10, 133, 100, 8) 'settlement bottom divider' @(7, 144, 162, 1)
Assert-NoOverlap 'notebook sections' @(14, 30, 168, 14) 'notebook content' @(18, 55, 164, 94)
Assert-NoOverlap 'notebook content' @(18, 55, 164, 94) 'notebook footer buttons' @(14, 156, 172, 16)

foreach ($control in @(
    @('processing notebook', 155, 4, 14, 14), @('processing redstone', 7, 56, 14, 14),
    @('processing side', 23, 56, 14, 14), @('processing route', 72, 56, 26, 14),
    @('processing status', 100, 62, 69, 11), @('generator notebook', 155, 4, 14, 14),
    @('generator energy', 76, 31, 92, 9), @('battery notebook', 155, 4, 14, 14),
    @('battery energy', 20, 31, 136, 10), @('settlement notebook', 154, 146, 14, 14)
)) {
    Assert-Within $control[0] @($control[1], $control[2], $control[3], $control[4]) 176 166
}
Assert-Within 'side configuration panel' @(12, 17, 176, 148) 200 183
Assert-Within 'compact notebook' @(6, 6, 188, 171) 200 183

$outputs = [System.Collections.Generic.List[string]]::new()
$processingStates = @(
    @{ Id = 'empty'; Key = 'screen.earth_on_minecraft.machine.empty_input_short'; Multiblock = $false },
    @{ Id = 'unsupported-input'; Key = 'screen.earth_on_minecraft.machine.unsupported_input_short'; Multiblock = $false },
    @{ Id = 'missing-power'; Key = 'screen.earth_on_minecraft.machine.missing_power_short'; Multiblock = $false },
    @{ Id = 'redstone-paused'; Key = 'screen.earth_on_minecraft.machine.redstone_paused_short'; Multiblock = $true },
    @{ Id = 'missing-additive'; Key = 'screen.earth_on_minecraft.machine.missing_secondary_short'; Multiblock = $false },
    @{ Id = 'output-blocked'; Key = 'screen.earth_on_minecraft.machine.outputs_full_short'; Multiblock = $false },
    @{ Id = 'structure-fault'; Key = 'screen.earth_on_minecraft.machine.structure_missing_short'; Multiblock = $true },
    @{ Id = 'running'; Key = 'screen.earth_on_minecraft.machine.running'; Multiblock = $false },
    @{ Id = 'multi-route'; Key = 'screen.earth_on_minecraft.machine.recipe_ready'; Multiblock = $false }
)
foreach ($lang in @('zh_cn', 'en_us')) {
    $shortLang = if ($lang -eq 'zh_cn') { 'zh' } else { 'en' }
    foreach ($state in $processingStates) {
        $outputs.Add((Render-Processing $lang $state.Multiblock "processing-$shortLang-$($state.Id).png" $state.Key))
    }
}
$outputs.Add((Render-Generator 'zh_cn' $false 'generator-zh-combustion.png'))
$outputs.Add((Render-Generator 'en_us' $true 'generator-en-turbine.png'))
$outputs.Add((Render-Battery 'zh_cn' 'battery-zh.png'))
$outputs.Add((Render-Battery 'en_us' 'battery-en.png'))
$outputs.Add((Render-SideConfig 'zh_cn' 'side-config-zh.png'))
$outputs.Add((Render-SideConfig 'en_us' 'side-config-en.png'))
$outputs.Add((Render-NotebookCompact 'zh_cn' 'notebook-compact-zh.png'))
$outputs.Add((Render-NotebookCompact 'en_us' 'notebook-compact-en.png'))
$outputs.Add((Render-Settlement 'zh_cn' 'settlement-zh.png'))
$outputs.Add((Render-Settlement 'en_us' 'settlement-en.png'))
$contactSheet = New-ContactSheet $outputs.ToArray()

$buttonSprite.Dispose()
$buttonDisabledSprite.Dispose()
$progressSprite.Dispose()
$flameSprite.Dispose()
$cjkFont.Dispose()

Write-Output "GUI previews: $($outputs.Count)"
Write-Output "Contact sheet: $contactSheet"
