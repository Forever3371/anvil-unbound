package com.example.anvilunbound.mixin;

import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code canTakeOutput} 是 AnvilScreenHandler 自己的 protected 方法，
 * 用 @Invoker 暴露出来，方便测试里断言"没星取不出来、有星能取"。
 */
@Mixin(AnvilScreenHandler.class)
public interface AnvilScreenHandlerInvoker {

    @Invoker("canTakeOutput")
    boolean anvil_unbound$invokeCanTakeOutput(PlayerEntity player, boolean present);
}
