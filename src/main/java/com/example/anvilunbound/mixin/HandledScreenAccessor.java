package com.example.anvilunbound.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取 GUI 左上角在屏幕上的坐标（画自定义槽位背景要用）。
 * <p>
 * <b>不要</b>用 {@code @Shadow} 去 shadow {@code AnvilScreen} 从父类继承来的 {@code x} / {@code y}：
 * Mixin 的注解处理器只在目标类<strong>自身</strong>的声明里找字段，找不到就不会生成 refmap 映射，
 * 运行时就变成 {@code @Shadow field field_2776 was not located in the target class} 直接崩。
 * <p>
 * 正确做法是 mixin 到字段真正所属的 {@code HandledScreen}，再用 {@code @Accessor} 暴露出来。
 * 注意这个类只能放在 mixins.json 的 {@code client} 列表里——HandledScreen 是纯客户端类。
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

    @Accessor("x")
    int anvil_unbound$getScreenX();

    @Accessor("y")
    int anvil_unbound$getScreenY();
}
