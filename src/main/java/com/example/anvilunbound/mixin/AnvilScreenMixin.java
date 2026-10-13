package com.example.anvilunbound.mixin;

import com.example.anvilunbound.AnvilUnboundConfig;
import com.example.anvilunbound.NetherStarSlot;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 客户端：把铁砧界面左侧那行"花费 X 级经验"换成下界之星的提示。
 * <p>
 * 只替换文案——槽位背景由 {@code HandledScreen} 遍历 {@code handler.slots} 统一绘制，
 * 我们新加的下界之星槽会自动被画出来，不需要额外渲染代码。
 * <p>
 * 取 handler 用的是公开的 {@code getScreenHandler()} 而不是 @Shadow 字段：
 * 那个字段声明在 {@code HandledScreen} 上且是泛型 {@code T}，@Shadow 的类型容易对不上。
 */
@Mixin(AnvilScreen.class)
public class AnvilScreenMixin {

    /** 原版 "container.repair.cost"（花费 X 级）。 */
    @Redirect(
            method = "drawForeground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/text/Text;translatable("
                            + "Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/text/MutableText;"
            )
    )
    private MutableText anvil_unbound$replaceCostText(String key, Object[] args) {
        if (key.equals("container.repair.cost")) {
            return Text.translatable(anvil_unbound$starTextKey());
        }
        return Text.translatable(key, args);
    }

    /** 原版 "container.repair.expensive"（过于昂贵）——现在没有这个限制了。 */
    @Redirect(
            method = "drawForeground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/text/Text;translatable("
                            + "Ljava/lang/String;)Lnet/minecraft/text/MutableText;"
            )
    )
    private MutableText anvil_unbound$replaceExpensiveText(String key) {
        if (key.equals("container.repair.expensive")) {
            return Text.translatable(anvil_unbound$starTextKey());
        }
        return Text.translatable(key);
    }

    private String anvil_unbound$starTextKey() {
        if (!AnvilUnboundConfig.requireNetherStar) {
            return "container.repair.cost";
        }
        return anvil_unbound$hasStar() ? "anvil_unbound.star_cost" : "anvil_unbound.star_required";
    }

    private boolean anvil_unbound$hasStar() {
        AnvilScreenHandler handler = anvil_unbound$handler();
        if (handler == null) {
            return false;
        }
        for (Slot slot : handler.slots) {
            if (slot instanceof NetherStarSlot && slot.hasStack()) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private AnvilScreenHandler anvil_unbound$handler() {
        return ((HandledScreen<AnvilScreenHandler>) (Object) this).getScreenHandler();
    }
}
