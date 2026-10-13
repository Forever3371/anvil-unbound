package com.example.anvilunbound.mixin;

import com.example.anvilunbound.AnvilUnboundConfig;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 本模组的核心，也是唯一的改动点。
 * <p>
 * 1.21.1 的 {@code AnvilScreenHandler#updateResult()} 里，合并附魔的等级是这样定下来的
 * （已核对字节码，{@code getMaxLevel()} 只在这个方法里被调用两次，紧跟着就是
 * {@code ItemEnchantmentsComponent.Builder#set}）：
 *
 * <pre>
 *   int newLevel = (目标等级 == 牺牲等级) ? 目标等级 + 1 : Math.max(目标等级, 牺牲等级);
 *   if (newLevel &gt; enchantment.getMaxLevel()) {      // ← 第 1 次 getMaxLevel()
 *       newLevel = enchantment.getMaxLevel();        // ← 第 2 次 getMaxLevel()
 *   }
 *   builder.set(entry, newLevel);
 * </pre>
 *
 * 原版 {@code getMaxLevel()} 返回的是数据驱动定义里的 {@code max_level}（锋利是 5），
 * 所以"锋利 V + 锋利 V"会被夹回 V。这里把它替换成配置的上限，
 * 于是变成 VI、VII……一直到 1000。
 * <p>
 * <b>刻意没碰的东西</b>：互斥判断走的是另一个调用（{@code Enchantment.canBeCombined}），
 * 累积惩罚（{@code getNextCost}）、经验花费公式也都没动，所以原版规则里
 * "锋利/亡灵杀手/节肢杀手互斥""保护系互斥""冰霜行者与深海探索者互斥"
 * "三叉戟的激流/忠诚/引雷互斥"等全部照旧。
 */
@Mixin(AnvilScreenHandler.class)
public class AnvilScreenHandlerMixin {

    @Redirect(
            method = "updateResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/enchantment/Enchantment;getMaxLevel()I")
    )
    private int anvil_unbound$raiseAnvilLevelCap(Enchantment enchantment) {
        return Math.max(enchantment.getMaxLevel(), AnvilUnboundConfig.maxLevel);
    }
}
