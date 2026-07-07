# Earth on Minecraft 0.1.6 Beta

> 开发中测试版 / In-development beta

- Minecraft: `26.2`
- Loader: `NeoForge 26.2.0.7-beta`
- Mod id: `earth_on_minecraft`
- Mod version: `0.1.6`
- Jar: `earth-on-minecraft-neoforge-26.2-0.1.6.jar`
- SHA256: `0714E1E1EBADB57F779AD46FBBAFBBA06609316149564002C2F3BBE6F3F23C12`

## 本轮重点

- 增加旧测试命名空间兼容别名：`earth_online:*` 会映射到当前 `earth_on_minecraft:*`。
- 覆盖范围包括方块、物品、机器方块实体和菜单类型，目标是让改名前的测试存档进入旧区块时不再把矿床/机器解析成未知注册表键。
- 保留 0.1.5 的自然矿床优化：铝土矿风化壳、磷块岩沉积层、蒸发岩盐层、锡石砂矿和稀土碳酸岩矿体。

## 验证

- `python .\tools\validate_resources.py`: `OK json=945 png=336 models=652 refs=344 blocks=52 items=241 stacks=292 dataRefs=119 worldgenRefs=27`
- `.\gradlew.bat build --no-daemon --offline`: `BUILD SUCCESSFUL`
- 部署路径：`D:\_dx\_Games\MC\xxxxxx\.minecraft\versions\26.2-NeoForge_26.2.0.7-beta\mods`
- 运行时验证：待用户启动新日志后确认。旧区块兼容需要打开包含旧 `earth_online:*` 方块的测试世界验证。

## 已知限制

- 这是 registry alias 级兼容，不是完整数据迁移器；旧世界保存后应逐步变成新命名空间。
- 世界生成变更仍需要新世界或新生成区块观察；旧区块可能保留历史生成结果。
