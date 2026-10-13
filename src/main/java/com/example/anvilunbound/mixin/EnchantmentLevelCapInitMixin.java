package com.example.anvilunbound.mixin;

import com.example.anvilunbound.AnvilUnboundConfig;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 附魔等级上限第 2/3 处：私有构造函数的校验。
 * 原版会遍历所有附魔，只要有一个等级 &gt; 255 就
 * {@code throw new IllegalArgumentException("Enchantment ... has invalid level ...")}，
 * 于是 {@code Builder#build()} 当场抛异常。
 */
@Mixin(ItemEnchantmentsComponent.class)
public class EnchantmentLevelCapInitMixin {

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 255))
    private int anvil_unbound$raiseConstructorCap(int vanillaMax) {
        return AnvilUnboundConfig.storageCap();
    }
}
