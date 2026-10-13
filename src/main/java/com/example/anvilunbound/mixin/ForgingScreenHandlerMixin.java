package com.example.anvilunbound.mixin;

import com.example.anvilunbound.NetherStarSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 关闭界面时把下界之星槽里的东西还给玩家。
 * <p>
 * {@code onClosed} 定义在 {@code ForgingScreenHandler} 上（AnvilScreenHandler 没有覆盖它），
 * 所以注入点必须放在这里；再用 {@code instanceof AnvilScreenHandler} 限定只对铁砧生效，
 * 不影响锻造台等其它界面。
 * <p>
 * 星槽的 inventory 是铁砧专属的，原版不会返还它——不处理的话玩家关掉界面东西就没了。
 * 这里通过遍历槽位找到 {@link NetherStarSlot}，而不是读另一个 Mixin 的私有字段。
 */
@Mixin(ForgingScreenHandler.class)
public class ForgingScreenHandlerMixin {

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
}
