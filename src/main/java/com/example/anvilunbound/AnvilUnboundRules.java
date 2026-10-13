package com.example.anvilunbound;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * 铁砧的规则判定，集中放在这里，方便自检命令和 gametest 直接调用同一套逻辑。
 */
public final class AnvilUnboundRules {

    /** 原版互斥组：靴子（冰霜行者 / 深海探索者）—— 按需求保留互斥。 */
    private static final TagKey<Enchantment> EXCLUSIVE_BOOTS = tag("exclusive_set/boots");

    /** 原版互斥组：三叉戟（激流 / 忠诚 / 引雷）—— 按需求保留互斥。 */
    private static final TagKey<Enchantment> EXCLUSIVE_RIPTIDE = tag("exclusive_set/riptide");

    private AnvilUnboundRules() {
    }

    private static TagKey<Enchantment> tag(String path) {
        return TagKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla(path));
    }

    /**
     * 铁砧里"这两个附魔能不能同时存在于一件物品上"。
     * <p>
     * 除靴子和三叉戟那两组之外，其它原版互斥组（伤害类、保护类、时运/精准采集、无限/经验修补、
     * 多重射击/穿透）在这里一律放开。保留的那两组仍走原版判断，
     * 所以"忠诚与引雷可以共存、但都与激流互斥"这类细节也不会被破坏。
     */
    public static boolean canCombine(RegistryEntry<Enchantment> first, RegistryEntry<Enchantment> second) {
        if (AnvilUnboundConfig.keepBootsExclusive && (inBootsGroup(first) || inBootsGroup(second))) {
            return Enchantment.canBeCombined(first, second);
        }
        if (AnvilUnboundConfig.keepRiptideExclusive && (inRiptideGroup(first) || inRiptideGroup(second))) {
            return Enchantment.canBeCombined(first, second);
        }
        return true;
    }

    /** {@code #exclusive_set/boots} 里两个附魔都在标签内。 */
    public static boolean inBootsGroup(RegistryEntry<Enchantment> entry) {
        return entry.isIn(EXCLUSIVE_BOOTS);
    }

    /**
     * 三叉戟那组：标签 {@code #exclusive_set/riptide} 只列了忠诚和引雷，
     * 激流本身不在标签里（它是靠自己的 exclusiveSet 排斥另外两个），
     * 所以除了查标签，还要看这个附魔的 exclusiveSet 里有没有标签成员。
     */
    public static boolean inRiptideGroup(RegistryEntry<Enchantment> entry) {
        if (entry.isIn(EXCLUSIVE_RIPTIDE)) {
            return true;
        }
        RegistryEntryList<Enchantment> exclusive = entry.value().exclusiveSet();
        for (RegistryEntry<Enchantment> other : exclusive) {
            if (other.isIn(EXCLUSIVE_RIPTIDE)) {
                return true;
            }
        }
        return false;
    }
}
