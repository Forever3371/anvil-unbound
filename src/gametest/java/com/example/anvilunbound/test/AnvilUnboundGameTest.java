package com.example.anvilunbound.test;

import com.example.anvilunbound.AnvilUnboundConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

/**
 * 自动化测试：在无头服务端里真的构造一个铁砧界面，把两件装备放进去合并，检查产物等级。
 * 运行：./gradlew runGameTest
 */
public class AnvilUnboundGameTest implements FabricGameTest {

    // ---------- 合并等级：本模组要改的就是这一块 ----------

    /** 锋利 V + 锋利 V 应该变成 VI（原版会夹回 V） */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void equalLevelsMergeIntoOneHigher(TestContext context) {
        AnvilScreenHandler handler = newAnvil(context);
        int level = merge(context, handler, Items.DIAMOND_SWORD, "sharpness", 5, "sharpness", 5, "sharpness");
        context.assertTrue(level == 6, "锋利 V + 锋利 V 应当是 VI，实际 " + level + "（" + describe(handler) + "）");
        context.complete();
    }

    /** 等级不同时取较高者（原版行为，不该被改变） */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void differentLevelsKeepTheHigherOne(TestContext context) {
        AnvilScreenHandler handler = newAnvil(context);
        int level = merge(context, handler, Items.DIAMOND_SWORD, "sharpness", 5, "sharpness", 3, "sharpness");
        context.assertTrue(level == 5, "锋利 V + 锋利 III 应当保持 V，实际 " + level);
        context.complete();
    }

    /** 可以一直往上合，远远超过原版上限 V */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void canMergeFarBeyondVanillaMaxLevel(TestContext context) {
        AnvilScreenHandler handler = newAnvil(context);
        int level = merge(context, handler, Items.DIAMOND_SWORD, "sharpness", 40, "sharpness", 40, "sharpness");
        context.assertTrue(level == 41, "锋利 40 + 锋利 40 应当是 41（原版上限只有 V），实际 " + level);
        context.complete();
    }

    /** 到达配置上限后不再提升 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void stopsAtConfiguredCap(TestContext context) {
        AnvilScreenHandler handler = newAnvil(context);
        int cap = AnvilUnboundConfig.maxLevel;
        int level = merge(context, handler, Items.DIAMOND_SWORD, "sharpness", cap, "sharpness", cap, "sharpness");
        context.assertTrue(level == cap, "到达上限 " + cap + " 后不应继续提升，实际 " + level);
        context.complete();
    }

    /** 其它物品类型也同样能突破原版上限（靴子、弓、三叉戟、盔甲） */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void otherItemTypesCanAlsoExceedVanillaCap(TestContext context) {
        AnvilScreenHandler handler = newAnvil(context);
        int boots = merge(context, handler, Items.DIAMOND_BOOTS, "protection", 4, "protection", 4, "protection");
        context.assertTrue(boots == 5, "靴子 保护 IV + IV 应当是 V，实际 " + boots);

        int bow = merge(context, handler, Items.BOW, "power", 5, "power", 5, "power");
        context.assertTrue(bow == 6, "弓 力量 V + V 应当是 VI，实际 " + bow);

        int trident = merge(context, handler, Items.TRIDENT, "impaling", 5, "impaling", 5, "impaling");
        context.assertTrue(trident == 6, "三叉戟 穿刺 V + V 应当是 VI，实际 " + trident);

        int chest = merge(context, handler, Items.DIAMOND_CHESTPLATE, "protection", 4, "protection", 4, "protection");
        context.assertTrue(chest == 5, "胸甲 保护 IV + IV 应当是 V，实际 " + chest);
        context.complete();
    }

    /** 合到 300 级之后必须能通过 NBT 往返（三处 255 硬上限的回归测试） */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void highLevelSurvivesNbtRoundTrip(TestContext context) {
        ServerWorld world = context.getWorld();
        AnvilScreenHandler handler = newAnvil(context);
        int level = merge(context, handler, Items.DIAMOND_SWORD, "sharpness", 300, "sharpness", 300, "sharpness");
        context.assertTrue(level == 301, "锋利 300 + 300 应当是 301，实际 " + level);

        ItemStack output = take(handler, AnvilScreenHandler.OUTPUT_ID);
        var ops = world.getRegistryManager().getOps(NbtOps.INSTANCE);
        var encoded = ItemStack.CODEC.encodeStart(ops, output).getOrThrow();
        ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        int afterRoundTrip = levelOf(world, decoded, "sharpness");
        context.assertTrue(afterRoundTrip == 301, "NBT 往返之后等级被改成了 " + afterRoundTrip);
        context.complete();
    }

    // ---------- 互斥规则：本模组不该动这一块 ----------

    /**
     * 直接断言原版的互斥规则仍然成立。
     * {@code Enchantment.canBeCombined} 就是铁砧判断"两个附魔能不能共存"用的函数，
     * 比绕一圈去合并物品更直接、也不受合并流程细节影响。
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void vanillaExclusivityIsUntouched(TestContext context) {
        ServerWorld world = context.getWorld();
        // 需求里点名要保留的两组
        assertExclusive(context, world, "frost_walker", "depth_strider", true, "靴子：冰霜行者 vs 深海探索者");
        assertExclusive(context, world, "riptide", "loyalty", true, "三叉戟：激流 vs 忠诚");
        assertExclusive(context, world, "riptide", "channeling", true, "三叉戟：激流 vs 引雷");
        assertExclusive(context, world, "loyalty", "channeling", false, "三叉戟：忠诚与引雷本就可以共存");
        // 其余原版互斥组
        assertExclusive(context, world, "sharpness", "smite", true, "剑：锋利 vs 亡灵杀手");
        assertExclusive(context, world, "sharpness", "bane_of_arthropods", true, "剑：锋利 vs 节肢杀手");
        assertExclusive(context, world, "protection", "fire_protection", true, "盔甲：保护 vs 火焰保护");
        assertExclusive(context, world, "protection", "blast_protection", true, "盔甲：保护 vs 爆炸保护");
        assertExclusive(context, world, "infinity", "mending", true, "弓：无限 vs 经验修补");
        assertExclusive(context, world, "multishot", "piercing", true, "弩：多重射击 vs 穿透");
        assertExclusive(context, world, "silk_touch", "fortune", true, "工具：精准采集 vs 时运");
        // 注意 canBeCombined 问的是"两个附魔能否共存"，同一个附魔谈不上共存，原版也返回 false
        assertExclusive(context, world, "sharpness", "sharpness", true, "锋利与自己（原版语义即不可共存）");
        context.complete();
    }

    /**
     * 剑：互斥的附魔不会被合进结果。
     * 场景特意选成"有其它可以合并的附魔"（抢夺 III+III → IV），
     * 这样铁砧一定会有产出；否则两件满耐久又完全互斥的装备在原版里会被判定为"没有有效操作"，
     * 输出槽直接是空的，断言就没法做了。
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void exclusiveEnchantmentIsNotMergedIn(TestContext context) {
        ServerWorld world = context.getWorld();
        AnvilScreenHandler handler = newAnvil(context);
        put(handler, AnvilScreenHandler.INPUT_1_ID,
                enchantedItem(world, Items.DIAMOND_SWORD, "sharpness", 5, "looting", 3));
        put(handler, AnvilScreenHandler.INPUT_2_ID,
                enchantedItem(world, Items.DIAMOND_SWORD, "smite", 5, "looting", 3));
        handler.updateResult();

        ItemStack output = take(handler, AnvilScreenHandler.OUTPUT_ID);
        context.assertTrue(!output.isEmpty(), "剑合并没有产出：" + describe(handler));
        int smite = levelOf(world, output, "smite");
        context.assertTrue(smite == 0, "锋利与亡灵杀手应当互斥，结果不该出现亡灵杀手 " + smite + " 级：" + output);
        int looting = levelOf(world, output, "looting");
        context.assertTrue(looting == 4, "抢夺 III + III 应当合并为 IV，实际 " + looting);
        context.complete();
    }

    // ---------- 辅助方法 ----------

    private static void assertExclusive(TestContext context, ServerWorld world,
                                        String firstId, String secondId, boolean shouldBeExclusive, String label) {
        Registry<Enchantment> registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        Enchantment first = registry.get(Identifier.ofVanilla(firstId));
        Enchantment second = registry.get(Identifier.ofVanilla(secondId));
        if (first == null || second == null) {
            context.assertTrue(false, label + "：附魔不存在（" + firstId + " / " + secondId + "）");
            return;
        }
        RegistryEntry<Enchantment> a = registry.getEntry(first);
        RegistryEntry<Enchantment> b = registry.getEntry(second);
        boolean exclusive = !Enchantment.canBeCombined(a, b);
        context.assertTrue(exclusive == shouldBeExclusive,
                label + " 期望" + (shouldBeExclusive ? "互斥" : "可共存") + "，实际" + (exclusive ? "互斥" : "可共存"));
    }

    private static AnvilScreenHandler newAnvil(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        return new AnvilScreenHandler(999, player.getInventory(), ScreenHandlerContext.EMPTY);
    }

    /** 把两件装备放进铁砧合并，返回产物上指定附魔的等级。 */
    private static int merge(TestContext context, AnvilScreenHandler handler, Item item,
                             String firstId, int firstLevel, String secondId, int secondLevel, String resultId) {
        put(handler, AnvilScreenHandler.INPUT_1_ID, enchanted(context.getWorld(), item, firstId, firstLevel));
        put(handler, AnvilScreenHandler.INPUT_2_ID, enchanted(context.getWorld(), item, secondId, secondLevel));
        handler.updateResult();
        return levelOf(context.getWorld(), take(handler, AnvilScreenHandler.OUTPUT_ID), resultId);
    }

    /** 1.21 的 ScreenHandler 不再提供 setStack/getStack，必须经由槽位对象读写。 */
    private static void put(AnvilScreenHandler handler, int slot, ItemStack stack) {
        handler.getSlot(slot).setStack(stack);
    }

    private static ItemStack take(AnvilScreenHandler handler, int slot) {
        return handler.getSlot(slot).getStack();
    }

    private static String describe(AnvilScreenHandler handler) {
        return "levelCost=" + handler.getLevelCost()
                + " in1=" + describeStack(take(handler, AnvilScreenHandler.INPUT_1_ID))
                + " in2=" + describeStack(take(handler, AnvilScreenHandler.INPUT_2_ID))
                + " out=" + describeStack(take(handler, AnvilScreenHandler.OUTPUT_ID));
    }

    private static String describeStack(ItemStack stack) {
        return stack.isEmpty() ? "<空>" : stack.getItem() + "x" + stack.getCount();
    }

    private static ItemStack enchanted(ServerWorld world, Item item, String enchantmentId, int level) {
        return enchantedItem(world, item, enchantmentId, level);
    }

    /** 按 "附魔id, 等级, 附魔id, 等级…" 的顺序给物品挂多个附魔。 */
    private static ItemStack enchantedItem(ServerWorld world, Item item, Object... idsAndLevels) {
        ItemStack stack = new ItemStack(item);
        Registry<Enchantment> registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        ItemEnchantmentsComponent.Builder builder =
                new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        for (int i = 0; i + 1 < idsAndLevels.length; i += 2) {
            String id = (String) idsAndLevels[i];
            int level = (Integer) idsAndLevels[i + 1];
            Enchantment enchantment = registry.get(Identifier.ofVanilla(id));
            if (enchantment != null) {
                builder.add(registry.getEntry(enchantment), level);
            }
        }
        stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
        return stack;
    }

    private static int levelOf(ServerWorld world, ItemStack stack, String enchantmentId) {
        ItemEnchantmentsComponent component = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (component == null || component.isEmpty()) {
            return 0;
        }
        Registry<Enchantment> registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        Enchantment enchantment = registry.get(Identifier.ofVanilla(enchantmentId));
        if (enchantment == null) {
            return 0;
        }
        return component.getLevel(registry.getEntry(enchantment));
    }
}
