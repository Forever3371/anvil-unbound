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
                # 附魔互斥规则（锋利/亡灵杀手/节肢杀手、保护系、冰霜行者/深海探索者、激流/忠诚/引雷……）
                # 完全保持原版，不受本模组影响。

                # 铁砧合并后的等级上限（原版各附魔最高只有 I~V，默认放宽到 1000）
                max_level=1000

                # 是否注册 /anvilunbound selftest 自检命令（默认 true）
                enable_selftest_command=true
                """;
    }
}
