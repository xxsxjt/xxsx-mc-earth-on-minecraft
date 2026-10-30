# 26.1.2 迁移记录

三个项目已从 NeoForge 26.2.0.93 回退到 **26.1.2.124**，全部编译通过。

## 版本对照

| 项目 | 构建文件 | 旧版本 | 新版本 |
|---|---|---|---|
| earth_on_minecraft | `neoforge-26.2/build.gradle` | `26.2.0.93` | `26.1.2.124` |
| earth_human | `earth_human/build.gradle` | `26.2.0.93` | `26.1.2.124` |
| temple | `temple/mod-[虚无圣殿]Temple of Nihility/build.gradle` | `26.2.0.93` | `26.1.2.124` |

`moddev-gradle` 保持 `2.0.148`（三个项目都能正常解析 26.1.2）。

部署目标实例：`E:\_dx\_Games\MC\xxxxxx\.minecraft\versions\26.1.2-NeoForge_26.1.2.124\mods`

## 目录名说明

Gradle 项目目录仍叫 `neoforge-26.2`，没有改名。改名会牵动
`tools/validate_resources.py`、`tools/spec_coverage.py` 等脚本里的几十处
`ROOT / "neoforge-26.2"` 路径，收益不足以抵消风险。目录名只影响开发者，不影响产物。

## API 差异（26.2 → 26.1.2）

26.1.2 比 26.2 旧，部分是 26.2 新增的 API 在 26.1.2 上不存在。

### 主项目

| 26.2 写法 | 26.1.2 写法 | 影响文件 |
|---|---|---|
| `Items.DYE.white()` | `Items.WHITE_DYE` | `ProcessingMachineBlock` |
| `Blocks.WOOL.white()` | `Blocks.WHITE_WOOL` | `ProcessingMachineBlock` |
| `Minecraft.getInstance().gui.setScreen(x)` | `Minecraft.getInstance().setScreen(x)` | `EarthOnMinecraftClient`、`FieldGeologyNotebookScreen` |
| `Minecraft.getInstance().gui.pushScreenLayer(x)` | `Minecraft.getInstance().setScreen(x)` | `ProcessingMachineScreen` |
| `Minecraft.getInstance().gui.popScreenLayer()` | `Minecraft.getInstance().setScreen(null)` | `MachineSideConfigScreen` |

26.2 的 `Items.DYE` / `Blocks.WOOL` 是 `ColorCollection` 类型（`white()` 等方法），
26.1.2 还是独立的 `WHITE_DYE` / `WHITE_WOOL` 字段。

26.2 引入了 GUI 屏幕层栈（`pushScreenLayer` / `popScreenLayer`），26.1.2 只有单一
`setScreen`。**副作用**：`MachineSideConfigScreen` 原本作为覆盖层压在主界面上，
迁移后改用 `setScreen` 会替换整个界面而非叠加，关闭时返回 `null` 会回到游戏画面而
不是机器界面。如果需要保留返回体验，应在 `MachineSideConfigScreen` 里保存父屏幕引用，
关闭时 `setScreen(parent)`。

### earth_human

| 26.2 写法 | 26.1.2 写法 |
|---|---|
| `minecraft.gui.setScreen(x)` | `minecraft.setScreen(x)` |
| `minecraft.gui.hud.rightHeight` | `minecraft.gui.rightHeight` |
| `minecraft.gui.hud.isHidden()` | `minecraft.options.hideGui` |

26.2 把 HUD 拆成了独立的 `net.minecraft.client.gui.Hud` 类，字段挂在 `Gui.hud` 上；
26.1.2 的 `rightHeight` / `leftHeight` 直接在 `Gui` 上，隐藏状态用 `Options.hideGui`。

`ScreenEvent.Render.Foreground` 在 26.1.2 **存在**，无需改动——26.1.2 同时保留了
`ContainerScreenEvent` 和 `ScreenEvent` 两套事件。

### temple

`build.gradle` 只改了版本号和产物名，Java 代码无需改动。
之前从 `VoidPhaseEvents` 移除的 `PlayerInteractEvent.EntityInteractSpecific`
在 26.1.2 中其实仍然存在，但移除后代码依旧编译通过（`entityInteract` 可用），保持现状。

## 元数据

`neoforge.mods.toml`：

- `versionRange` NeoForge：`[26.1.2.124,)`
- `versionRange` Minecraft：`[26.1.2]`
- `versionRange` JEI：`[29.53.0.112,)`（对应实例里的 `jei-26.1.2-neoforge-29.53.0.112.jar`）
- 描述文本改为 Minecraft 26.1.2 + NeoForge 26.1.2.124

产物名：

- `earth-on-minecraft-neoforge-26.1.2-<version>.jar`
- `earth-human-neoforge-26.1.2-<version>.jar`
- `temple-of-nihility-neoforge-26.1.2-<version>.jar`

`tools/validate_resources.py` 里的产物名匹配已同步更新。

## JEI 依赖解析

`build.gradle` 的 `jeiSearchDirs` 现在指向 26.1.2 实例目录，匹配模式改为
`*jei-26.1*-neoforge-*.jar`。实例中解析到的是
`jei-26.1.2-neoforge-29.53.0.112.jar`。

## 验证

```
earth_on_minecraft   compileJava + verifyStaticQuality   OK
earth_human          compileJava                          OK（2 个 getGuiLeft/getGuiTop 弃用警告）
temple               compileJava                          OK
```

主项目完整质量门：

```
ASSET_AUDIT_OK assets=5085
BLOCKBENCH_PIPELINE_OK profiles=59
GEOLOGY_PIPELINE_OK blocks=18 models=126
ITEM_ICON_PIPELINE_OK items=1306
MACHINE_AUDIO_OK files=61 events=61
validate_resources OK json=3875 png=1651 blocks=117 items=1312 machineProfiles=57
```

**未启动任何客户端、服务端或 GameTest**。运行时行为需要在授权后实测。

## 回滚

改动前的文件备份在 `C:\Users\xxsx\AppData\Local\Temp\opencode\pre-2612-snapshot\`：

- `build.gradle`（主项目）
- `gradle.properties`（主项目，未改动）
- `earth_human-build.gradle`
- `temple-build.gradle`

Java 侧的改动集中在 5 个文件，可用 `git diff` 精确回退。

## 后续注意

1. **屏幕层副作用**：`MachineSideConfigScreen` 的返回行为见上文，建议改为保存父屏幕。
2. **未实测**：26.1.2 的物品定义、方块状态、数据组件编解码都通过了静态校验和编译，
   但注册表加载、网络同步、GUI 渲染只在运行时才能验证。
3. **`getGuiLeft`/`getGuiTop` 弃用警告**：earth_human 的 `InventoryBodySummary` 仍在用，
   26.1.2 标记为待删除，但当前可用。
