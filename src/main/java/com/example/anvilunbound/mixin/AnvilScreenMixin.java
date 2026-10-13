package com.example.anvilunbound.mixin;

import com.example.anvilunbound.AnvilUnboundConfig;
import com.example.anvilunbound.NetherStarSlot;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端的两件事：把"花费 X 级"的文案换成下界之星提示，以及给星槽补上槽位图案。
 * <p>
 * 文案那两个注入都写了 {@code require = 0}：这是纯装饰性的替换，
 * 万一将来游戏版本里注入点变了，也应该只是"文案没换掉"，而不是让整个客户端崩掉。
 * （1.1.0 就是漏了这一点，在 {@code TOO_EXPENSIVE_TEXT} 上扫到 0 个目标而直接崩。）
 * <p>
 * 取 handler 用的是公开的 {@code getScreenHandler()} 而不是 @Shadow 字段：
 * 那个字段声明在 {@code HandledScreen} 上且是泛型 {@code T}，@Shadow 的类型容易对不上。
 */
@Mixin(AnvilScreen.class)
public class AnvilScreenMixin {

    /** 复用原版铁砧自己的贴图，补出来的槽位图案才和旁边完全一致。 */
    private static final Identifier anvil_unbound$ANVIL_TEXTURE =
            Identifier.ofVanilla("textures/gui/container/anvil.png");

    /** 贴图里"输入槽 1"的图案位置：槽位坐标是 (27,47)，对应贴图 uv (26,46)。 */
    private static final int anvil_unbound$SLOT_U = 26;
    private static final int anvil_unbound$SLOT_V = 46;
    private static final int anvil_unbound$SLOT_SIZE = 18;

    /**
     * GUI 左上角的屏幕坐标。
     * 用 {@link HandledScreenAccessor} 取，而不是 {@code @Shadow}——
     * x / y 声明在父类 HandledScreen 上，@Shadow 在 AnvilScreen 里找不到它们，会直接崩。
     */
    private int anvil_unbound$guiX() {
        return ((HandledScreenAccessor) (Object) this).anvil_unbound$getScreenX();
    }

    private int anvil_unbound$guiY() {
        return ((HandledScreenAccessor) (Object) this).anvil_unbound$getScreenY();
    }

    /**
     * 原版 "container.repair.cost"（花费 X 级）是运行时拼出来的：
     * {@code Text.translatable("container.repair.cost", levelCost)}。
     */
    @Redirect(
            method = "drawForeground",
            require = 0,
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

    /**
     * 原版 "container.repair.expensive"（过于昂贵）**不是在 drawForeground 里拼的**，
     * 而是在类初始化时存进静态字段 {@code TOO_EXPENSIVE_TEXT}，drawForeground 只负责读它，
     * 所以必须拦字段读取（拦 Text.translatable 会扫到 0 个目标）。
     */
    @Redirect(
            method = "drawForeground",
            require = 0,
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETSTATIC,
                    target = "Lnet/minecraft/client/gui/screen/ingame/AnvilScreen;"
                            + "TOO_EXPENSIVE_TEXT:Lnet/minecraft/text/Text;"
            )
    )
    private Text anvil_unbound$replaceExpensiveText() {
        return Text.translatable(anvil_unbound$starTextKey());
    }

    /**
     * 原版铁砧贴图里只有 3 个槽位图案，我们新增的星槽画出来是一片空白、和背景糊在一起。
     * 这里照着输入槽那一小块图案复制一份，位置和风格都与旁边的槽一致。
     */
    @Inject(method = "drawBackground", require = 0, at = @At("TAIL"))
    private void anvil_unbound$drawStarSlotBackground(DrawContext context, float delta,
                                                      int mouseX, int mouseY, CallbackInfo ci) {
        Slot star = anvil_unbound$starSlot();
        if (star == null) {
            return;
        }
        context.drawTexture(anvil_unbound$ANVIL_TEXTURE,
                anvil_unbound$guiX() + star.x - 1, anvil_unbound$guiY() + star.y - 1,
                anvil_unbound$SLOT_U, anvil_unbound$SLOT_V,
                anvil_unbound$SLOT_SIZE, anvil_unbound$SLOT_SIZE, 256, 256);
    }

    private Slot anvil_unbound$starSlot() {
        AnvilScreenHandler handler = anvil_unbound$handler();
        if (handler == null) {
            return null;
        }
        for (Slot slot : handler.slots) {
            if (slot instanceof NetherStarSlot) {
                return slot;
            }
        }
        return null;
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
