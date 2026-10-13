package com.example.anvilunbound;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Anvil Unbound —— 解除铁砧合并同名附魔的等级上限。
 * <p>
 * 只做一件事：把铁砧里"合并后的等级不能超过该附魔自身最高等级"这条限制放宽到配置值（默认 1000）。
 * 附魔互斥规则、累积惩罚、经验花费公式全部保持原版。
 */
public class AnvilUnbound implements ModInitializer {

    public static final String MOD_ID = "anvil_unbound";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        AnvilUnboundConfig.load();

        if (AnvilUnboundConfig.enableSelfTestCommand) {
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                    dispatcher.register(CommandManager.literal("anvilunbound")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.literal("selftest")
                                    .executes(context -> AnvilUnboundSelfTest.run(context.getSource())))));
        }

        LOGGER.info("[Anvil Unbound] 已加载：铁砧等级上限 {}，保留互斥组={}，花费={}",
                AnvilUnboundConfig.maxLevel,
                (AnvilUnboundConfig.keepBootsExclusive ? "靴子" : "无")
                        + "/" + (AnvilUnboundConfig.keepRiptideExclusive ? "三叉戟" : "无"),
                AnvilUnboundConfig.requireNetherStar ? "1 颗下界之星（不再消耗经验）" : "原版经验等级");
    }
}
