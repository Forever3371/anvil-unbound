# 更新日志

## [1.0.0] - 2026-10-13

### 新增

- 解除铁砧合并同名附魔的等级上限：锋利 V + 锋利 V = 锋利 VI，可一路合到配置上限（默认 1000）。
- 配置项 `max_level`（默认 1000）与 `enable_selftest_command`（默认 true）。
- 内置自检命令 `/anvilunbound selftest`：检查等级上限、逐一比对 14 组原版互斥关系、
  并真的构造铁砧界面跑合并断言。
- 语言文件补了 `enchantment.level.11` ~ `1000`，让高等级附魔在 tooltip 上显示成干净的数字。
- gametest 8 项（`./gradlew runGameTest`）。
- `tools/production-test/`：可在真实 Fabric 服务端无人值守运行的验证数据包。

### 明确未改动

- 附魔互斥规则（保护系、锋利/亡灵杀手/节肢杀手、冰霜行者/深海探索者、激流/忠诚/引雷、
  无限/经验修补、多重射击/穿透、时运/精准采集）全部保持原版。
- 累积惩罚（Prior Work Penalty）与合并经验花费公式保持原版。

### 兼容性

- Minecraft 1.21.1 / Fabric Loader ≥ 0.16.0 / Fabric API / Java 21。
- 与 Blast Enchant 1.1.0+ 可共存：检测到对方已放宽附魔等级上限时，
  本模组通过 `MixinConfigPlugin` 跳过自带的三个等级上限 Mixin，避免注入冲突。
