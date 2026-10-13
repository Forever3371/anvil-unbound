# Anvil Unbound（铁砧突破）

Minecraft **1.21.1** / **Fabric** 模组：解除铁砧合并同名附魔时的等级上限。

**锋利 V + 锋利 V = 锋利 VI**，可以一直合到配置的上限（默认 **1000**）。

## 改了什么 / 没改什么

这是本模组的全部改动范围，改得很窄：

| | 说明 |
|---|---|
| ✅ **改了** | 铁砧里"合并后的等级不能超过该附魔自身最高等级"这一条限制 |
| ✅ **改了** | 合并等级上限 → 配置值（默认 1000） |
| ❌ **没改** | **附魔互斥规则**，全部与原版一致（见下表） |
| ❌ **没改** | 累积惩罚（Prior Work Penalty）公式 |
| ❌ **没改** | 合并经验花费的乘数表 |
| ❌ **没改** | 附魔台、砂轮、命令、其它任何地方 |

保持原版的互斥组（一个都不动）：

- 剑/斧/重锤：锋利、亡灵杀手、节肢杀手、致密、破甲
- 锄/锹/镐/斧：时运、精准采集
- 盔甲：保护、火焰保护、弹射物保护、爆炸保护
- **靴子：冰霜行者、深海探索者**
- 弓：无限、经验修补
- **三叉戟：激流、忠诚、引雷**（忠诚与引雷本来就能共存）
- 弩：多重射击、穿透

> 需求里点名要保留的靴子冰霜行者/深海探索者、三叉戟那几组，都在自检里逐条断言过。

## 安装

1. 安装 Fabric Loader（≥ 0.16）+ **Fabric API**
2. 把 `anvil-unbound-1.0.0.jar` 放进 `mods/`
3. 客户端和服务端都要装（铁砧是两边都会跑的界面逻辑）

## 用法

把两件**同名同等级**附魔的装备放进铁砧：

```
钻石剑（锋利 V） + 钻石剑（锋利 V）  →  钻石剑（锋利 VI）
钻石剑（锋利 40）+ 钻石剑（锋利 40） →  钻石剑（锋利 41）
```

规则和原版一样，只是上限被抬高了：

- 两件等级**相同** → 等级 +1
- 两件等级**不同** → 取较高的那个
- 达到配置上限后不再提升

## 配置

首次启动生成 `config/anvil_unbound.properties`：

```properties
# 铁砧合并后的等级上限（原版各附魔最高只有 I~V，默认放宽到 1000）
max_level=1000

# 是否注册 /anvilunbound selftest 自检命令（默认 true）
enable_selftest_command=true
```

改完重启游戏/服务器生效。

## ⚠️ 关于"能合到多少级"

模组只抬高了上限，**没有动累积惩罚**（那是原版另一套机制，你没要求改）。所以：

- 每次在铁砧上做非重命名操作，物品的累积惩罚变成 `2^n - 1`
- 生存/冒险模式单次操作上限是 **39 级**

实际算下来，从锋利 V 开始连续合并大概是：

| 合并次数 | 结果等级 | 累积惩罚 |
|---|---|---|
| 1 | VI | 1 |
| 2 | VII | 3 |
| 3 | VIII | 7 |
| 4 | IX | 15 |
| 5 | X | 31 |
| 6 | XI | 63 → 超过 39，生存模式做不了 |

也就是说：**生存模式实际能合到 X 级左右；创造模式（无花费限制）可以一路合到 1000。**
如果你想让生存模式也能合到 1000，还需要额外放宽累积惩罚或花费上限——那是另一项改动，说一声我加。

## 游戏内自检

```
/anvilunbound selftest
```

在有玩家的环境里执行会跑完整检查（需要 OP/权限等级 2）：

- 附魔等级上限（组件 + NBT 往返都保持 1000）
- **互斥规则逐条比对原版**（14 组）
- **真的构造一个铁砧界面跑合并**：5+5=VI、5+3=V、40+40=41、1000+1000=1000
- 互斥场景：锋利 + 亡灵杀手（带抢夺做对照）→ 亡灵杀手被拒绝、抢夺正确升级

控制台执行时因为没有玩家上下文，会跳过铁砧合并那几项并说明。

## 与其它模组共存

和 [Blast Enchant](../blast-enchant)（同样放宽了附魔等级上限的那个高炉模组）**可以同时装**。

1.21.1 的附魔等级 255 上限有三处硬编码，两个模组都必须绕过。为了不撞车，本模组带了一个 `MixinConfigPlugin`：
检测到 `blast_enchant` 已加载时，**自动跳过自己那三个上限 Mixin**（对方的上限是 100000，比需要的 1000 更宽松，功能上完全够用）。

启动日志里会看到：

```
[Anvil Unbound] 检测到 Blast Enchant 已放宽附魔等级上限，跳过 EnchantmentLevelCapBuilderMixin 避免注入冲突
[Anvil Unbound] 检测到 Blast Enchant 已放宽附魔等级上限，跳过 EnchantmentLevelCapCodecMixin 避免注入冲突
[Anvil Unbound] 检测到 Blast Enchant 已放宽附魔等级上限，跳过 EnchantmentLevelCapInitMixin 避免注入冲突
```

单独装本模组时这三个 Mixin 正常生效，不依赖任何其它模组。

## 实现原理

只改了铁砧计算等级的那一处。1.21.1 `AnvilScreenHandler#updateResult()` 的逻辑（核对过字节码，`getMaxLevel()` 在这个方法里只被调用两次，紧跟着就是 `Builder#set`）：

```java
int newLevel = (目标等级 == 牺牲等级) ? 目标等级 + 1 : Math.max(目标等级, 牺牲等级);
if (newLevel > enchantment.getMaxLevel()) {   // ← 第 1 次
    newLevel = enchantment.getMaxLevel();     // ← 第 2 次
}
builder.set(entry, newLevel);
```

`AnvilScreenHandlerMixin` 把这两处 `getMaxLevel()` 重定向成"原版上限与配置上限取较大者"，于是夹取变成 `min(newLevel, 1000)`。

互斥判断走的是另一个调用（`Enchantment.canBeCombined`），所以天生不受影响。

三个 `EnchantmentLevelCap*Mixin` 负责让附魔组件真的能存到 255 以上（`Builder#add/set` 的截断、私有构造函数的校验、NBT codec 的取值范围），否则合到 300 级时物品会存不进存档。

## 自己构建 / 测试

```bash
./gradlew build          # 产物在 build/libs/（顺带跑 gametest）
./gradlew runGameTest    # 只跑自动化测试（8 项）
./gradlew runClient      # 开发环境启动客户端
```

`tools/production-test/` 是可在**真实 Fabric 服务端**（intermediary 命名）无人值守运行的数据包，
用法见 [blast-enchant/docs/REUSABLE_TEST_KIT.md](../blast-enchant/docs/REUSABLE_TEST_KIT.md)。

## 许可

MIT
