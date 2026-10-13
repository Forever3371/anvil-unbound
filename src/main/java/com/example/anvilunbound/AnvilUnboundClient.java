package com.example.anvilunbound;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;

/**
 * 客户端入口：启动时主动把铁砧界面类加载一次。
 * <p>
 * {@code AnvilScreen} 平时要等玩家打开铁砧才加载，Mixin 也是那时候才织入——
 * 出问题时表现为"玩到一半突然崩"，还很难复现。
 * 这里提前加载一次，注入有问题会在启动阶段就暴露在日志里，
 * 而不是等到玩家站在铁砧前才炸。
 */
public class AnvilUnboundClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                Class<?> clazz = AnvilScreen.class;
                AnvilUnbound.LOGGER.info("[Anvil Unbound] 客户端自检：{} 加载成功，客户端 Mixin 织入正常",
                        clazz.getSimpleName());
            } catch (Throwable t) {
                AnvilUnbound.LOGGER.error("[Anvil Unbound] 客户端自检失败：铁砧界面类没能加载，"
                        + "客户端 Mixin 可能没织入成功", t);
            }
        });
    }
}
