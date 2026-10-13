package com.example.anvilunbound;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 极简配置：读写 config/anvil_unbound.properties。
 */
public final class AnvilUnboundConfig {

    /** 铁砧合并后的等级上限。原版受"每个附魔自身的最高等级"限制（锋利最高 V），这里统一放宽。 */
    public static int maxLevel = 1000;

    /** 是否保留"冰霜行者 / 深海探索者"这一组的互斥（按需求保留）。 */
    public static boolean keepBootsExclusive = true;

    /** 是否保留"激流 / 忠诚 / 引雷"这一组的互斥（按需求保留）。 */
    public static boolean keepRiptideExclusive = true;

    /** 铁砧操作是否改为消耗一颗下界之星（并且完全不再消耗经验等级）。 */
    public static boolean requireNetherStar = true;

    /** 是否注册 /anvilunbound selftest 自检命令。 */
    public static boolean enableSelfTestCommand = true;

    private static final String FILE_NAME = "anvil_unbound.properties";

    private AnvilUnboundConfig() {
    }

    /**
     * 附魔组件实际能存的等级上限。
     * 至少不低于原版的 255，避免把原版行为改窄；同时必须覆盖配置的铁砧上限。
     */
    public static int storageCap() {
        return Math.max(255, maxLevel);
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties props = new Properties();

        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, defaultContent(), StandardCharsets.UTF_8);
                AnvilUnbound.LOGGER.info("[Anvil Unbound] 已生成默认配置：{}", path);
            }
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        } catch (IOException e) {
            AnvilUnbound.LOGGER.warn("[Anvil Unbound] 读取配置失败，使用内置默认值", e);
        }

        maxLevel = readInt(props, "max_level", 1000, 1, 100000);
        keepBootsExclusive = Boolean.parseBoolean(props.getProperty("keep_boots_exclusive", "true").trim());
        keepRiptideExclusive = Boolean.parseBoolean(props.getProperty("keep_riptide_exclusive", "true").trim());
        requireNetherStar = Boolean.parseBoolean(props.getProperty("require_nether_star", "true").trim());
        enableSelfTestCommand = Boolean.parseBoolean(props.getProperty("enable_selftest_command", "true").trim());
    }

    private static int readInt(Properties props, String key, int fallback, int min, int max) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                AnvilUnbound.LOGGER.warn("[Anvil Unbound] 配置项 {}={} 超出范围 [{}, {}]，使用默认值 {}",
                        key, value, min, max, fallback);
                return fallback;
            }
            return value;
        } catch (NumberFormatException e) {
            AnvilUnbound.LOGGER.warn("[Anvil Unbound] 配置项 {}={} 不是整数，使用默认值 {}", key, raw, fallback);
            return fallback;
        }
    }

    private static String defaultContent() {
        return """
                # Anvil Unbound 配置
                # 让铁砧合并同名附魔时可以超过原版上限：锋利 V + 锋利 V = 锋利 VI。
                # 同时解除大部分附魔互斥，并把经验花费改成下界之星。

                # 铁砧合并后的等级上限（原版各附魔最高只有 I~V，默认放宽到 1000）
                max_level=1000

                # 是否保留"冰霜行者 / 深海探索者"的互斥（默认 true = 保留，这两个仍然不能共存）
                keep_boots_exclusive=true

                # 是否保留"激流 / 忠诚 / 引雷"的互斥（默认 true = 保留）
                # 注意：忠诚与引雷本来就能共存，这一点不受影响
                keep_riptide_exclusive=true

                # 铁砧操作是否改为消耗一颗下界之星（默认 true）
                # 打开后：不再消耗任何经验等级，每次操作消耗 1 颗下界之星；
                #        星不够时能看到结果但取不出来。关掉则退回原版的等级花费。
                require_nether_star=true

                # 是否注册 /anvilunbound selftest 自检命令（默认 true）
                enable_selftest_command=true
                """;
    }
}
