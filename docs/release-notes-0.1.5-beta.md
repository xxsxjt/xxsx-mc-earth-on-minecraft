# Earth on Minecraft 0.1.5 Beta

> 开发中测试版 / In-development beta

- Minecraft: `26.2`
- Loader: `NeoForge 26.2.0.7-beta`
- Mod id: `earth_on_minecraft`
- Mod version: `0.1.5`
- Jar: `earth-on-minecraft-neoforge-26.2-0.1.5.jar`
- SHA256: `760FBAFAF8984B60F938EA235346A15A4656F99045AB3E7E2508AA3341627145`

## 本轮重点

- 新增 5 类自然矿床来源：
  - 铝土矿风化壳：接铝土矿粉、赤铁矿粉、高岭土粉、氧化铝和铝锭路线。
  - 磷块岩沉积层：接磷矿粉、方解石、石膏、磷酸、过磷酸钙和复合肥路线。
  - 蒸发岩盐层：接盐粉、石膏、盐卤结晶、氯化钾、硼酸盐、氯碱和肥料路线。
  - 锡石砂矿：接锡石粉、钛白粉、独居石砂、锡锭和焊料路线。
  - 稀土碳酸岩：接氟碳铈矿粉、独居石砂、混合稀土氧化物、沥青铀矿粉和安全化核燃料材料链。
- 新矿床已加入主世界 `underground_ores` 阶段，使用合法的 26.2 vanilla configured/placed feature 范围，避免 oversized ore feature 或 disk radius Codec 崩溃。
- 新增手册页面“自然矿床来源”，让非技术玩家能直接看懂这些矿床分别进入哪条工业路线。
- 新增中英语言键、BlockItem 名称、工具标签、loot table、blockstate、26.2 `assets/<modid>/items/*.json` 物品模型定义。

## 验证

- `python .\tools\validate_resources.py`: `OK json=945 png=336 models=652 refs=344 blocks=52 items=241 stacks=292 dataRefs=119 worldgenRefs=27`
- `.\gradlew.bat build --no-daemon --offline`: `BUILD SUCCESSFUL`
- 部署路径：`D:\_dx\_Games\MC\xxxxxx\.minecraft\versions\26.2-NeoForge_26.2.0.7-beta\mods`
- 运行时验证：待用户启动新日志后确认。世界生成变更需要新世界或新生成区块验证；旧区块可能保留旧矿物。

## 已知限制

- 当前仍是数据驱动 placed feature 方案：主矿、伴生矿和围岩 halo 还不是同一个完全连续的自定义矿体。
- 新矿床贴图先补齐资源闭环；精修纹理可继续用后台 image-2 批次替换。
