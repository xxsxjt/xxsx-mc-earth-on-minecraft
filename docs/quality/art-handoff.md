# 贴图生成交接文档

本文件交给独立会话执行。目标是**把剩余占位贴图全部换成 AI 生成图**，不需要改任何 Java 代码。

---

## 0. 一句话任务

用 `tools/generate_gemini_art.py` 为 `docs/quality/art-pending.json` 里列出的
**1041 个物品贴图** 和 **42 个方块贴图** 生成图标，写入指定的 `textures/` 目录。

---

## 1. 环境

| 项目 | 值 |
|---|---|
| 工作目录 | `D:\Users\xxsx\orca\workspaces\earth_on_minecraft\iron-loop-migration` |
| Shell | Windows PowerShell 5.1（**没有** heredoc；`python -c` 多行会失败，要写临时 `.py` 文件） |
| Python | `python`（已在 PATH），需要 `PIL` |
| 编码 | 控制台会乱码显示中文，但**文件本身是 UTF-8，正常**。不要因为控制台乱码去改文件编码 |
| `rg` | 未安装，用内置 grep 工具 |

**重要**：不要用 PowerShell 的 `Set-Content` / here-string 写含中文的 Python 代码，
会被 shell 吞掉引号导致语法错误。**一律用 write 工具写 `.py` 文件再 `python xxx.py`**。

---

## 2. 生成管线现状

脚本：`tools/generate_gemini_art.py`

### 2.1 模型回退链（已实现，不要改）

按顺序尝试，前一个配额耗尽自动切下一个：

| 顺序 | 模型 | 返回格式 | 后处理 |
|---|---|---|---|
| 1 | `google-antigravity/gemini-3.1-flash-image` | markdown 链接到 relay artifact（JPEG） | 品红 `#FF00FF` 抠图 |
| 2 | `AIGCPro-image/gpt-image-2.5-sunburst` | 内联 `data:image/png;base64` | 有 alpha 就跳过，否则抠图 |
| 3 | `AIGCPro-image/gpt-image-2.5-flare` | 同上 | 同上 |

- 端点：`http://127.0.0.1:10100/v1/chat/completions`
- 鉴权：relay 接受空 bearer，脚本会自己处理，**不要打印任何密钥**
- 429 响应体带恢复倒计时（`Your quota will reset after 4h25m28s`），脚本已解析：
  剩余 > 3 分钟就立即切换供应商，不会空转
- 三个都耗尽时退出码 3

### 2.2 现有命令行参数

```powershell
--kind item|block|both       # 默认 item
--limit N                    # 0 = 全部
--only a,b,c                 # 逗号分隔的 id
--template-only              # 只处理仍是模板尺寸（<=1200 字节）的
--force                      # 覆盖已有大图
--dry-run                    # 只打印提示词，不请求模型
--pause 秒                   # 每次请求间隔，默认 0.5
```

### 2.3 产物位置

| 内容 | 路径 |
|---|---|
| 物品贴图（128×128 PNG） | `neoforge-26.2/src/main/resources/assets/earth_on_minecraft/textures/item/<id>.png` |
| 方块贴图（128×128 PNG） | `neoforge-26.2/src/main/resources/assets/earth_on_minecraft/textures/block/<id>.png` |
| 覆盖率账本 | `docs/quality/gemini-art-manifest.json`（每生成一张立即写盘，跨运行累积） |
| 对比图 | `output/quality/gemini-art-only.png` |

---

## 3. 必须遵守的规则

1. **`sponge_iron` 和 `wrought_iron` 是已批准的手绘 pilot 图，绝对不能覆盖。**
   脚本内 `PROTECTED` 已硬编码，不要绕过。

2. **不要修改任何 Java / JSON / 模型文件。** 这个任务只产出 PNG。

3. **不要运行 Gradle、不要启动 Minecraft、不要部署。** 只跑 Python 脚本。

4. **不要删除已有的大图**（>1200 字节）。判断标准就是文件大小。

5. **每批跑完检查 `remaining` 字段**，它来自 manifest，反映还有多少 curated 条目没生成。

6. 遇到连续 429：先跑一次 `--limit 1` 探活；如果返回 200 说明配额恢复；
   如果脚本以退出码 3 结束，说明三个供应商全耗尽，等 manifest 里报告的时间后再来。

---

## 4. 待办清单

`docs/quality/art-pending.json` 已生成，结构：

```json
{
  "schema_version": 1,
  "template_threshold_bytes": 1200,
  "curated_items": 82,
  "curated_blocks": 48,
  "item_pending": [
    {"id": "abalone_shell", "en": "Abalone Shell", "zh": "鲍壳",
     "family": "herb", "kind": "item", "note": "..."}
  ],
  "block_pending": [
    {"id": "...", "en": "...", "zh": "...", "family": "...", "kind": "block"}
  ]
}
```

- **1041 个物品**待生成
- **42 个方块**待生成

已完成的 82 个物品和 48 个方块不要重做。

---

## 5. 执行步骤

### 步骤 1：探活

```powershell
cd 'D:\Users\xxsx\orca\workspaces\earth_on_minecraft\iron-loop-migration'
python tools\generate_gemini_art.py --kind item --only salt --force --pause 0
```

看到 `generated via gemini` 或 `generated via gpt-image-*` 即为正常。
若退出码 3，记录剩余时间后停止，稍后再试。

### 步骤 2：给待办条目补提示词

脚本的 `ITEM_SUBJECTS` / `BLOCK_SUBJECTS` 是 Python 字典，格式：

```python
ITEM_SUBJECTS = {
    "magnetite_chunk": "a chunk of magnetite ore, dark metallic black octahedral crystals in a gray rock matrix",
    ...
}
```

**你要做的**：读 `docs/quality/art-pending.json`，为每个 id 写一条英文描述，
追加到对应字典里。写作要求：

- 只描述**物体本身**，不要写风格词（风格由 `STYLE` 常量统一加）
- 名词短语，不带句号
- 具体、可画：材质、形状、颜色、关键特征
- 液体/化学品用「a glass bottle of ...」或「a small heap of ... powder」
- 植物用「a bundle of dried ... leaves」等
- 工具用「a ... with a wooden handle」
- 食物用「a ... on a shallow dish」或「a piece of ...」

示例（照这个颗粒度）：

```
"abalone_shell": "an iridescent abalone shell, nacre interior with blue-green sheen"
"acetanilide": "a small heap of white acetanilide crystals in a shallow paper packet"
"aconite": "a bundle of dried aconite roots with dark twisted tubers"
"adzuki_bean": "a small heap of dark red adzuki beans"
```

### 步骤 3：分批生成

字典补齐后，分批跑，**每批 40~60 张**，避免单次运行过长：

```powershell
python tools\generate_gemini_art.py --kind item --template-only --limit 50 --pause 3
```

`--template-only` 会自动跳过已完成的大图，所以重复跑是安全的、幂等的。

重复直到输出里 `remaining` 为 0 或全部条目都已是大图。

### 步骤 4：方块贴图

方块用 `--kind block`，注意方块贴图要求「无缝平铺、正面视角、铺满画面」，
`build_prompt` 里已针对 block 分支做了不同措辞，不用你操心。

```powershell
python tools\generate_gemini_art.py --kind block --template-only --limit 20 --pause 3
```

### 步骤 5：出对比图

```powershell
python tools\make_gemini_only_sheet.py
```

产物：`output/quality/gemini-art-only.png`

### 步骤 6：验收

```powershell
python tools\list_pending_art.py
```

期望输出 `item pending 0` 和 `block pending 0`。
如果不是 0，继续跑步骤 3/4。

---

## 6. 质量检查

生成完抽样检查，重点看这几类容易出问题的：

| 类型 | 检查点 |
|---|---|
| 粉末（`*_dust`） | 颜色是否区分得开（硫黄/赤铁矿/磁铁矿/石墨） |
| 溶液（`*_acid`, `*_solution`） | 是否画成了瓶子而不是一滩液体 |
| 合金（`*_alloy`, `*_ingot`） | 是否有金属质感，不要画成石头 |
| 织物（`*_cloth`, `*_thread`） | 是否和纸/布区分得开 |
| 药品（`*_tablet`） | 是否是药片形状 |

明显失败的条目用 `--only <id> --force` 重生成。

**判断失败的标准**：主体不居中、背景没抠干净（有品红残留）、完全看不出是什么东西。

---

## 7. 已知坑

1. **gpt-image 有时返回 RGB 而不是 RGBA**，脚本会自动回落到抠图路径，正常现象。

2. **Gemini 返回的是 JPEG**，必须走品红抠图。如果提示词里没写 `#FF00FF` 背景，
   抠图会失败。`build_prompt` 已统一处理，**不要手动改提示词里的背景要求**。

3. **中文控制台乱码不代表文件坏了**。用 `python -c "print(open('file','rb').read()[:50])"` 验证。

4. **manifest 的 `remaining` 只统计 curated 条目**（即字典里有的）。
   字典没补完时 `remaining` 会偏小，以 `list_pending_art.py` 的输出为准。

5. **不要并行跑多个生成进程**，relay 会更快触发限流。

---

## 8. 完成标准

- [ ] `python tools/list_pending_art.py` 输出 `item pending 0`
- [ ] 输出 `block pending 0`
- [ ] `output/quality/gemini-art-only.png` 已重新生成
- [ ] `sponge_iron.png` 和 `wrought_iron.png` 未被修改（对比文件时间戳）
- [ ] 没有修改任何 `.java` / `.json` 文件（除了 `gemini-art-manifest.json`）

---

## 9. 参考文件

| 文件 | 用途 |
|---|---|
| `tools/generate_gemini_art.py` | 生成器主脚本，含 `ITEM_SUBJECTS` / `BLOCK_SUBJECTS` 字典 |
| `tools/list_pending_art.py` | 统计待办，写 `art-pending.json` |
| `tools/make_gemini_only_sheet.py` | 出对比图 |
| `tools/backfill_art_manifest.py` | 回填 manifest 历史（一般不需要） |
| `docs/quality/art-pending.json` | 待办清单，含英文名/中文名/分类/说明 |
| `docs/quality/gemini-art-manifest.json` | 覆盖率账本 |
| `docs/asset-prompts.md` | 通用提示词规范（历史文档，末节有当前管线说明） |
