package com.example.anvilunbound.mixin;

import com.example.anvilunbound.AnvilUnboundConfig;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * JEI 适配：把 JEI 铁砧配方页上的"附魔花费：X 级"换成下界之星提示。
 * <p>
 * JEI 是实时模拟一次铁砧操作算出花费（{@code AnvilHelper.findLevelsCost}），
 * 再用<strong>和原版铁砧界面完全同一个语言键</strong> {@code container.repair.cost} 把数字显示出来，
 * 所以这里拦的是同一个 {@code Text.translatable} 调用——和改原版界面那一处是同一个手法。
 * <p>
 * 几个关键点：
 * <ul>
 *     <li>用 {@code @Mixin(targets = "...")} 字符串指定 JEI 的类，这样不依赖 JEI 的 jar 也能编译。</li>
 *     <li>{@code remap = false}：JEI 是第三方模组，它的方法名不该被映射；
 *         但里面的 {@code @At} 目标（net.minecraft.text.Text）是原版类，必须 {@code remap = true}，
 *         所以两处显式分开写。</li>
 *     <li>{@code require = 0}：JEI 版本一变、注入点找不到时，只是这行文案不替换，绝不让客户端崩。</li>
 *     <li>没装 JEI 时由 {@code AnvilUnboundMixinPlugin} 直接跳过整个类，连加载都不会发生。</li>
 * </ul>
 */
@Mixin(targets = "mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeCategory", remap = false)
public class JeiAnvilRecipeCategoryMixin {

    @Redirect(
            method = "createRecipeExtras(Lmezz/jei/api/gui/widgets/IRecipeExtrasBuilder;"
                    + "Lmezz/jei/api/recipe/vanilla/IJeiAnvilRecipe;"
                    + "Lmezz/jei/api/recipe/IFocusGroup;)V",
            remap = false,
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/text/Text;translatable("
                            + "Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/text/MutableText;",
                    remap = true
            )
    )
    private MutableText anvil_unbound$replaceJeiCostText(String key, Object[] args) {
        if (key.equals("container.repair.cost") && AnvilUnboundConfig.requireNetherStar) {
            return Text.translatable("anvil_unbound.star_cost");
        }
        return Text.translatable(key, args);
    }
}
