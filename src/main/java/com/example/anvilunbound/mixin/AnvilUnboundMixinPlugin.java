package com.example.anvilunbound.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 让本模组能和 Blast Enchant（同样放宽了附魔等级上限的模组）共存。
 * <p>
 * 附魔等级的 255 上限在 1.21.1 里有三处硬编码，任何想超过 255 级的模组都必须全部绕过。
 * 两个模组各自往同一处注入会直接冲突（Critical injection failure），所以这里做个让位：
 * 只要检测到 blast_enchant 已加载，就跳过本模组自带的三个等级上限 Mixin——
 * 它的上限是 100000，比我们需要的 1000 更宽松，功能上完全够用。
 * <p>
 * 单独运行本模组时这三个 Mixin 正常生效，不依赖任何其他模组。
 */
public class AnvilUnboundMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("anvil_unbound");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("EnchantmentLevelCap")
                && FabricLoader.getInstance().isModLoaded("blast_enchant")) {
            LOGGER.info("[Anvil Unbound] 检测到 Blast Enchant 已放宽附魔等级上限，跳过 {} 避免注入冲突",
                    mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
