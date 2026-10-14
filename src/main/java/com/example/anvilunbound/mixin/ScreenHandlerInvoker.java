package com.example.anvilunbound.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 ScreenHandler 上的两个 protected 成员：
 * <ul>
 *     <li>{@code addSlot} —— 给铁砧界面追加下界之星槽位</li>
 *     <li>{@code insertItem} —— shift 点击时把星塞进星槽（返回"是否至少插入了一个"，剩余量在传入的 stack 里）</li>
 * </ul>
 * 注意：这些方法都定义在 ScreenHandler 上，而 {@code @Invoker} 只能定位目标类自身的方法，
 * 所以必须 mixin ScreenHandler 而不是某个子类；另外签名（含返回类型）必须完全一致。
 */
@Mixin(ScreenHandler.class)
public interface ScreenHandlerInvoker {

    @Invoker("addSlot")
    Slot anvil_unbound$invokeAddSlot(Slot slot);

    @Invoker("insertItem")
    boolean anvil_unbound$invokeInsertItem(ItemStack stack, int startIndex, int endIndex, boolean fromLast);
}
