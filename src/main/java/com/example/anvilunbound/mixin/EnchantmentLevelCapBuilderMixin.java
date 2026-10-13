package com.example.anvilunbound.mixin;

import com.example.anvilunbound.AnvilUnboundConfig;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 附魔等级上限第 3/3 处：{@code Builder#add} / {@code #set} 里的
 * {@code Math.min(level, 255)}。铁砧最终就是通过 {@code Builder#set} 写入等级的，
 * 不改这里，"锋利 1000"会被静默截断成"锋利 255"。
 */
@Mixin(ItemEnchantmentsComponent.Builder.class)
public class EnchantmentLevelCapBuilderMixin {

    @Redirect(
            method = {"add", "set"},
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", remap = false)
    )
    private static int anvil_unbound$raiseBuilderCap(int level, int vanillaCap) {
        return Math.min(level, AnvilUnboundConfig.storageCap());
    }
}
