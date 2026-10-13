package com.example.anvilunbound.mixin;

import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code addSlot} 定义在父类 ScreenHandler 上（@Invoker 只能定位目标类自身的方法，
 * 所以这里必须 mixin ScreenHandler 而不是 AnvilScreenHandler），
 * 用来给铁砧界面追加下界之星槽位。
 */
@Mixin(ScreenHandler.class)
public interface ScreenHandlerInvoker {

    @Invoker("addSlot")
    Slot anvil_unbound$invokeAddSlot(Slot slot);
}
