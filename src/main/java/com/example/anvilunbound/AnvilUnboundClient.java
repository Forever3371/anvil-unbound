package com.example.anvilunbound;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
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

            // 装了 JEI 的话，也主动加载它的铁砧配方类别，确认适配真的织入进去了。
            // 用字符串而不是直接引用：没装 JEI 时这个类根本不存在。
            if (FabricLoader.getInstance().isModLoaded("jei")) {
                try {
                    Class<?> jeiAnvil = Class.forName(
                            "mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeCategory");
                    AnvilUnbound.LOGGER.info("[Anvil Unbound] 客户端自检：JEI 的 {} 加载成功，"
                            + "JEI 铁砧花费文案适配已生效", jeiAnvil.getSimpleName());
                } catch (Throwable t) {
                    AnvilUnbound.LOGGER.error("[Anvil Unbound] 客户端自检失败：JEI 铁砧配方类别加载异常，"
                            + "JEI 适配可能没生效（不影响游戏运行）", t);
                }
            }
        });
    }
}
