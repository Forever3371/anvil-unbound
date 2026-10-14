package com.example.anvilunbound.mixin;

import com.example.anvilunbound.NetherStarSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 铁砧界面上两处"原版不知道我们多了一个槽"的逻辑，都定义在 {@code ForgingScreenHandler} 上
 * （AnvilScreenHandler 自己从没覆盖过它们），所以注入点放在这里，
 * 再用 {@code instanceof AnvilScreenHandler} 限定只对铁砧生效，不影响锻造台。
 */
@Mixin(ForgingScreenHandler.class)
public class ForgingScreenHandlerMixin {

    /** 关闭界面时把下界之星槽里的东西还给玩家——原版不会返还这个自建的 inventory。 */
    @Inject(method = "onClosed", at = @At("TAIL"))
    private void anvil_unbound$returnNetherStars(PlayerEntity player, CallbackInfo ci) {
        ForgingScreenHandler self = (ForgingScreenHandler) (Object) this;
        if (!(self instanceof AnvilScreenHandler)) {
            return;
        }
        for (Slot slot : self.slots) {
            if (slot instanceof NetherStarSlot && !slot.getStack().isEmpty()) {
                ItemStack stack = slot.getStack().copy();
                slot.setStack(ItemStack.EMPTY);
                player.getInventory().offerOrDrop(stack);
            }
        }
    }

    /**
     * shift 点击（快速移动）时的星槽自动填充。
     * <p>
     * 原版的 {@code quickMove} 只认铁砧那三个槽：从背包 shift 点击下界之星时，
     * 它会先试着塞进输入槽（塞不进），然后在背包与快捷栏之间挪一下就算了，星槽永远是空的。
     * 这里先截住"从玩家背包 shift 点击下界之星"的情况，把星填进星槽；
     * 一颗都塞不进去（星槽已满）就原样交还给原版逻辑。
     */
    @Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
    private void anvil_unbound$quickMoveStarIntoSlot(PlayerEntity player, int slotIndex,
                                                     CallbackInfoReturnable<ItemStack> cir) {
        ForgingScreenHandler self = (ForgingScreenHandler) (Object) this;
        if (!(self instanceof AnvilScreenHandler)) {
            return;
        }
        // 索引 0/1/2 是铁砧自己的输入与输出槽，交给原版处理
        if (slotIndex < 3 || slotIndex >= self.slots.size()) {
            return;
        }

        Slot clicked = self.slots.get(slotIndex);
        // 从星槽往外移：交给原版（它会挪回玩家背包）
        if (clicked instanceof NetherStarSlot) {
            return;
        }
        ItemStack stack = clicked.getStack();
        if (stack.isEmpty() || !stack.isOf(Items.NETHER_STAR)) {
            return;
        }

        NetherStarSlot starSlot = anvil_unbound$findStarSlot(self);
        if (starSlot == null) {
            return;
        }

        ItemStack before = stack.copy();
        boolean moved = ((ScreenHandlerInvoker) self)
                .anvil_unbound$invokeInsertItem(stack, starSlot.id, starSlot.id + 1, false);
        if (!moved || stack.getCount() == before.getCount()) {
            return; // 星槽满了，一颗都没进去，交回原版
        }

        clicked.setStack(stack);
        clicked.markDirty();
        starSlot.markDirty();
        self.sendContentUpdates();
        cir.setReturnValue(before);
    }

    private static NetherStarSlot anvil_unbound$findStarSlot(ForgingScreenHandler handler) {
        for (Slot slot : handler.slots) {
            if (slot instanceof NetherStarSlot star) {
                return star;
            }
        }
        return null;
    }
}
