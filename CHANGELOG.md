# 更新日志

## [1.1.5] - 2026-10-14

### 新增

- **JEI 适配**：JEI 的铁砧配方页现在也显示「消耗 1 颗下界之星」，不再是会误导人的"附魔花费：X 级"。
  查下来 JEI 用的就是**和原版铁砧界面同一个语言键** `container.repair.cost`，所以拦同一处调用即可。
- **shift 点击自动填充**：从背包 shift 点击下界之星会直接填进星槽（星槽满时交回原版逻辑）。
- **客户端启动自检**：启动时主动加载铁砧界面类，把客户端注入问题提前暴露在启动日志里；
  装了 JEI 时也会加载它的铁砧配方类别，确认适配确实织入生效。

### 修复

- **1.1.3**：不再用 `@Shadow` 取父类 `HandledScreen` 的 `x` / `y`——Mixin **不沿继承链查找字段**，
  注解处理器找不到就不会生成 refmap 映射，运行时直接崩。改为 `@Mixin(HandledScreen.class)`
  + `@Accessor`。
- **1.1.2**：
  - 星槽容量从 1 改成**一组（64）**。
  - 关闭界面时把星槽里的东西**还给玩家**——`onClosed` 实际定义在父类 `ForgingScreenHandler` 上，
    原版不会返还我们这个自建的 inventory，之前会吞物品。
  - 给星槽补上**背景图案**（从原版铁砧贴图里复制输入槽那一小块，风格与旁边一致）。
- **1.1.1**：`container.repair.expensive` 是在**类初始化时**存进静态字段 `TOO_EXPENSIVE_TEXT` 的，
  不是在渲染时拼字符串；改拦字段读取。两个文案注入都加 `require = 0`，
  以后注入点变化只会"文案没换掉"，**不会再让客户端崩**。

### 说明

- JEI 以 `compileOnly` 方式引用（`transitive = false`）：只供 Mixin 注解处理器解析目标类，
  不打包进 jar、也不强制玩家安装。没装 JEI 时对应 Mixin 由 `MixinConfigPlugin` 直接跳过。
- 新增 gametest：星槽容量、关闭界面返还、shift 点击填充（12 → 14 项）。

## [1.1.0] - 2026-10-13

### 新增

- **解除大部分附魔互斥**：剑/斧/重锤的伤害类（锋利、亡灵杀手、节肢杀手、穿刺、致密、破甲）、
  盔甲的四种保护、时运/精准采集、无限/经验修补、多重射击/穿透，现在都能共存。
  按需求**保留**靴子（冰霜行者/深海探索者）与三叉戟（激流/忠诚/引雷）两组的互斥。
- **花费从经验改成下界之星**：完全不消耗经验等级，改为每次操作消耗 1 颗下界之星。
- **铁砧界面新增下界之星槽位**：位于输出槽右侧，只接受下界之星；
  槽里没星时结果照常显示但取不出来，界面左侧文案由「花费 X 级」改为「需要 1 颗下界之星」。
- 配置项 `keep_boots_exclusive`、`keep_riptide_exclusive`、`require_nether_star`。

### 变更

- `canTakeOutput` 不再检查经验等级，也不再受"过于昂贵"（≥40 级）限制——因为已经不花经验了。

## [1.0.0] - 2026-10-13

### 新增

- 解除铁砧合并同名附魔的等级上限：锋利 V + 锋利 V = 锋利 VI，可一路合到配置上限（默认 1000）。
- 配置项 `max_level` 与 `enable_selftest_command`。
- 内置自检命令 `/anvilunbound selftest`。
- 语言文件补了 `enchantment.level.11` ~ `1000`。
- gametest 8 项（`./gradlew runGameTest`）。
- `tools/production-test/`：可在真实 Fabric 服务端无人值守运行的验证数据包。

### 兼容性

- Minecraft 1.21.1 / Fabric Loader ≥ 0.16.0 / Fabric API / Java 21。
- 与 Blast Enchant 1.1.0+ 可共存：检测到对方已放宽附魔等级上限时，
  本模组通过 `MixinConfigPlugin` 跳过自带的三个等级上限 Mixin，避免注入冲突。
