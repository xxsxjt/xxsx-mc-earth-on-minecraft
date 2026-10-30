# Earth on Minecraft 设计方案

## 核心边界

Earth on Minecraft 只重做自然来源和处理链，不破坏 MC 现有物品生态。

- 原版矿物方块：不自然生成。
- 原版锭、宝石、合成台：保留。
- 真实矿物、岩石、矿床：新增。
- 其他 mod 兼容：优先通过原版锭、原版标签和可配置配方输出实现。

## 原版矿物替代

| 原版资源 | 第一版真实来源 | 最终兼容输出 |
|---|---|---|
| Iron Ore | 磁铁矿 `Fe3O4` | `minecraft:iron_ingot` |
| Copper Ore | 黄铜矿 `CuFeS2` | `minecraft:copper_ingot` |
| Gold Ore | 含金石英脉 / 自然金 | `minecraft:gold_ingot` |
| Coal Ore | 烟煤 / 无烟煤含煤岩 | `minecraft:coal` |
| Diamond Ore | 金伯利岩筒 | `minecraft:diamond` |
| Lapis Ore | 青金石矿 | `minecraft:lapis_lazuli` |
| Emerald Ore | 绿柱石矿化脉 | `minecraft:emerald` |
| Redstone Ore | 红石矿物，保留 MC 幻想设定 | `minecraft:redstone` |
| Nether Quartz Ore | 石英脉 | `minecraft:quartz` |

## 岩石原则

岩石不是单一化学物质，不给岩石伪造单一化学式。岩石 tooltip 展示“主要矿物组成”。

示例：花岗岩。

```text
花岗岩:
- 石英 SiO2: 20-40%
- 钾长石 KAlSi3O8: 20-45%
- 钠长石 NaAlSi3O8: 10-35%
- 黑云母 K(Mg,Fe)3AlSi3O10(OH)2: 2-15%
- 磁铁矿 Fe3O4: 0-3%
- 锆石 ZrSiO4: 微量
```

第一版覆盖：

| MC 石头 | Earth on Minecraft 解释 |
|---|---|
| Stone | 未分类主世界石料；MC 泛化本地地层石料，约等于本地硅酸盐-碳酸盐混合岩，不等于花岗岩 |
| Cobblestone | 破碎本地采石骨料；不是独立天然岩石，表示石料碎块和采石粉尘 |
| Deepslate | 板岩 / 低级变质泥质岩 |
| Granite | 花岗岩 |
| Diorite | 闪长岩 |
| Andesite | 安山岩 |
| Basalt | 玄武岩 |
| Tuff | 凝灰岩 |
| Calcite | 方解石，接近 `CaCO3` |
| Dripstone | 钙质滴石，主要 `CaCO3` |
| Sandstone | 砂岩 |
| Blackstone | 富铁镁质火山岩 |

## 质量与粉末

为了接近质量守恒又保持 MC 背包友好：

```text
1 个岩石方块 = 1000 物质单位
1 个粉末 = 100 物质单位
1 个小撮粉末 = 10 物质单位
9 个粉末 = 1 个粉末块
```

示例：

```text
1 花岗岩
→ 10 花岗岩粉
→ 约 3 石英粉 + 3 长石粉 + 1 云母粉 + 少量磁铁矿粉 + 尾粉
```

现实中分离不会得到 100% 纯净物，但 MC 世界允许通过机器和合成体系得到纯净粉末，作为可玩性折中。

## 处理路线

### 首铁路线

首铁路线不再允许磁铁矿碎块或粉末直接在原版熔炉/高炉中产出铁锭。前置机器应可在没有铁锭的情况下制作和供能，焦炭可由煤在熔炉中烧制；这样既保留逐步工业化，也避免机器配方造成死锁。

```text
石镐开采磁铁矿
→ 颚式破碎机
→ 球磨机
→ 磁选机
→ 铁精矿 + 尾粉
→ 焦炭还原
→ 海绵铁 + 矿渣
→ 石砧 + 地质锤锻打
→ 熟铁
→ 再锻打
→ minecraft:iron_ingot
```

球磨机用石料和熔炉制作；磁选机用铜锭和磁铁矿碎块制作并接受本地煤燃料；首铁还原炉用砖和熔炉制作，路线不依赖先有铁锭、EOU 电网或大型多方块结构。石砧也可由石料制作，地质锤为耐久工具。

### 完整路线

完整路线更真实，产量更高，有副产物。

```text
磁铁矿矿石
→ 破碎磁铁矿
→ 磁选铁精粉 + 硅酸盐尾粉
→ 焦炭还原
→ 海绵铁 + 矿渣
→ 锻打熟铁
→ 石砧锻造成锭
→ minecraft:iron_ingot
```

```text
黄铜矿矿石
→ 破碎黄铜矿
→ 浮选铜精矿 + 含铁尾粉
→ 焙烧铜精矿 + 硫副产
→ 粗铜 + 铁硅酸盐渣
→ 精炼
→ minecraft:copper_ingot
```

## 矿床生成

矿物生成改成真实矿床，不再是原版散落小矿团。

| 类型 | 资源 | 生成形态 |
|---|---|---|
| 层状矿床 | 煤、石灰岩、沉积铁矿 | 大面积薄层 |
| 脉状矿床 | 金、石英、铜、锡 | 细长矿脉，穿插围岩 |
| 透镜状矿体 | 磁铁矿、铝土矿 | 大型椭球/透镜矿体 |
| 热液矿脉 | 黄铜矿、金、石英 | 跟断裂和裂隙绑定 |
| 砂矿 | 金、锡石、钛铁矿 | 河流、海滩、冲积层附近 |
| 岩筒 | 金伯利岩、钻石 | 稀有竖直管状岩体 |

## 地层生成规划

默认 MC 主世界高度是 `min_y=-64,height=384`，对现实地层来说很薄。Earth on Minecraft 不应该在默认包里强行覆盖旧世界高度，而应提供“新世界预设 / 显式配置”的安全路线。

第一阶段地层模型：

| 地层 | 现实含义 | 主要方块/材料 | 典型资源 |
|---|---|---|---|
| 表层风化物 | 土壤、风化壳、冲积物 | 草方块、泥土、黏土、砂、沙砾、泥巴 | 腐殖质、黏土、砂矿、铝土矿风化壳 |
| 沉积盆地 | 砂岩、页岩、石灰岩、蒸发岩 | 砂岩、方解石/石灰岩代理、深板岩/页岩代理 | 烟煤、磷块岩、盐层、石油/天然气预留 |
| 花岗质/热液带 | 侵入岩、断裂、石英脉 | 花岗岩、闪长岩、方解石脉、凝灰岩 | 黄铜矿、含金石英、辰砂、绿柱石 |
| 镁铁质/火山带 | 玄武岩、安山岩、凝灰岩 | 玄武岩、安山岩、黑石代理、凝灰岩 | 磁铁矿、铬铁矿、镁铁质硅酸盐 |
| 变质基底 | 板岩、片岩、片麻岩代理 | 深板岩、深层圆石、片状铝硅酸盐 | 无烟煤、青金石、红石矿物、稀土碳酸岩 |
| 岩筒 | 地幔来源火山角砾岩 | 金伯利岩、凝灰质角砾围岩 | 钻石、金伯利岩碎块 |

高度策略：

- 当前默认：不强改旧世界，继续使用 `-64..320`，所有矿床高度保持在 26.2 默认范围内。
- 保守高地层预设：新世界可考虑 `min_y=-128,height=512`，给沉积层和深部变质层更多空间。
- 真实测试预设：新测试世界可考虑 `min_y=-256,height=768`，但必须先验证 26.2 `dimension_type` / world preset Codec。
- 智能核心联动：只读取共享配置或预设值，例如高度倍率；不要让 Earth on Minecraft 直接依赖 Smart Core Java 类。

示例：磁铁矿透镜。

```text
形状: 透镜状 / 不规则团块
尺寸: 长 16-48，厚 4-12，高 8-20
围岩: 深板岩、玄武岩、辉长岩、片麻岩
组成:
- 磁铁矿石 55-80%
- 贫磁铁矿石 10-30%
- 钛磁铁矿 0-5%
- 围岩 10-30%
```

## 数据驱动

第一版就应该数据驱动，避免后续扩展现实矿物时改 Java。

建议目录：

```text
data/earth_on_minecraft/earth/rocks/
data/earth_on_minecraft/earth/minerals/
data/earth_on_minecraft/earth/deposits/
data/earth_on_minecraft/recipes/processing/
```

岩石定义示例：

```json
{
  "id": "earth_on_minecraft:granite",
  "type": "igneous_intrusive",
  "components": [
    { "material": "earth_on_minecraft:quartz", "formula": "SiO2", "min": 0.20, "max": 0.40 },
    { "material": "earth_on_minecraft:orthoclase", "formula": "KAlSi3O8", "min": 0.20, "max": 0.45 },
    { "material": "earth_on_minecraft:albite", "formula": "NaAlSi3O8", "min": 0.10, "max": 0.35 },
    { "material": "earth_on_minecraft:biotite", "formula": "K(Mg,Fe)3AlSi3O10(OH)2", "min": 0.02, "max": 0.15 },
    { "material": "earth_on_minecraft:magnetite", "formula": "Fe3O4", "min": 0.00, "max": 0.03 }
  ],
  "processing": ["crushing", "milling", "sieving", "flotation", "magnetic_separation"]
}
```

矿床定义示例：

```json
{
  "deposit": "earth_on_minecraft:magnetite_lens",
  "dimension": "minecraft:overworld",
  "host_rocks": [
    "minecraft:deepslate",
    "earth_on_minecraft:gabbro",
    "earth_on_minecraft:basalt",
    "earth_on_minecraft:gneiss"
  ],
  "shape": "lens",
  "size": {
    "length": [16, 48],
    "height": [6, 18],
    "thickness": [4, 12]
  },
  "composition": [
    { "block": "earth_on_minecraft:magnetite_ore", "weight": 60 },
    { "block": "earth_on_minecraft:poor_magnetite_ore", "weight": 25 },
    { "block": "earth_on_minecraft:titanomagnetite_ore", "weight": 5 },
    { "block": "minecraft:deepslate", "weight": 10 }
  ],
  "rarity": 0.015,
  "y": [-48, 24]
}
```

## 第一版机器

- 破碎机：方块/矿石 → 碎块。
- 球磨机：碎块 → 粉。
- 筛分机：混合粉 → 粗粒/细粉。
- 磁选机：含磁性矿物粉 → 磁性精粉 + 非磁性尾粉。
- 浮选机：硫化矿/复杂矿粉 → 精矿 + 尾粉。
- 焙烧炉：硫化矿 → 氧化矿/焙烧矿 + 硫副产。
- 高炉/还原炉：氧化矿 + 碳 → 粗金属 + 矿渣。
- 压粉机：粉末 ↔ 粉末块。

## 体验目标

玩家不必懂化学也能玩。JEI/EMI 和 tooltip 要让流程自己说话：

```text
挖到花岗岩
→ 打碎成粉
→ 分出石英粉和长石粉
→ 石英粉烧玻璃
→ 长石粉做陶瓷/釉料
→ 少量磁铁矿粉攒起来炼铁
```
