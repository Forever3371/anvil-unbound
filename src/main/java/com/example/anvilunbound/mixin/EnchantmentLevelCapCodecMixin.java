package com.example.anvilunbound.mixin;

import com.example.anvilunbound.AnvilUnboundConfig;
import com.mojang.serialization.Codec;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 附魔等级上限第 1/3 处：NBT 序列化。
 * 原版 {@code ENCHANTMENT_LEVEL_CODEC = Codec.intRange(0, 255)}，不改掉的话，
 * 铁砧合出来的 300 级附魔写进存档时会直接报错、物品丢失。
 * <p>
 * 注意 {@code @At} 里的目标是 Mojang 的库类，需要 remap = false；
 * 但注解级<strong>不能</strong>写 remap = false，否则 refmap 不会生成方法映射，
 * 正式环境会找不到目标（开发环境反而正常）。
 */
@Mixin(ItemEnchantmentsComponent.class)
public class EnchantmentLevelCapCodecMixin {

    @Redirect(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Codec;intRange(II)Lcom/mojang/serialization/Codec;",
                    remap = false
            ),
            remap = false
    )
    private static Codec<Integer> anvil_unbound$raiseCodecCap(int min, int vanillaMax) {
        return Codec.intRange(min, AnvilUnboundConfig.storageCap());
    }
}
