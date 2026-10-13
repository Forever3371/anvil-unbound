package com.example.anvilunbound.test;

import com.example.anvilunbound.AnvilUnboundConfig;
import com.example.anvilunbound.NetherStarSlot;
import com.example.anvilunbound.mixin.AnvilScreenHandlerInvoker;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

/**
 * 自动化测试：在无头服务端里真的构造一个铁砧界面，把装备与下界之星放进去，检查结果。
 * 运行：./gradlew runGameTest
 */
public class AnvilUnboundGameTest implements FabricGameTest {

    // ---------------------------------------------------------- 合并等级上限

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

    /** 其它物品类型也同样能突破原版上限 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void otherItemTypesCanAlsoExceedVanillaCap(TestContext context) {
        AnvilScreenHandler handler = newAnvil(context);
        int boots = merge(context, handler, Items.DIAMOND_BOOTS, "protection", 4, "protection", 4, "protection");
        context.assertTrue(boots == 5, "靴子 保护 IV + IV 应当是 V，实际 " + boots);

        int bow = merge(context, handler, Items.BOW, "power", 5, "power", 5, "power");
        context.assertTrue(bow == 6, "弓 力量 V + V 应当是 VI，实际 " + bow);

        int trident = merge(context, handler, Items.TRIDENT, "impaling", 5, "impaling", 5, "impaling");
        context.assertTrue(trident == 6, "三叉戟 穿刺 V + V 应当是 VI，实际 " + trident);
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

    // ---------------------------------------------------------- 互斥：该解除的已解除

    /** 剑：锋利 + 亡灵杀手 现在应该能共存（原版互斥） */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void weaponDamageEnchantmentsNowCombine(TestContext context) {
        ServerWorld world = context.getWorld();
        AnvilScreenHandler handler = newAnvil(context);
        put(handler, AnvilScreenHandler.INPUT_1_ID,
                enchantedItem(world, Items.DIAMOND_SWORD, "sharpness", 5, "looting", 3));
        put(handler, AnvilScreenHandler.INPUT_2_ID,
                enchantedItem(world, Items.DIAMOND_SWORD, "smite", 5, "looting", 3));
        handler.updateResult();

        ItemStack output = take(handler, AnvilScreenHandler.OUTPUT_ID);
        context.assertTrue(!output.isEmpty(), "剑合并没有产出：" + describe(handler));
        int sharpness = levelOf(world, output, "sharpness");
        int smite = levelOf(world, output, "smite");
        context.assertTrue(sharpness == 5 && smite == 5,
                "锋利与亡灵杀手现在应当能共存，实际 锋利=" + sharpness + " 亡灵杀手=" + smite);
        context.complete();
    }

    /** 盔甲：四种保护现在应该能共存（原版互斥） */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void protectionEnchantmentsNowCombine(TestContext context) {
        ServerWorld world = context.getWorld();
        AnvilScreenHandler handler = newAnvil(context);
        put(handler, AnvilScreenHandler.INPUT_1_ID,
                enchantedItem(world, Items.DIAMOND_CHESTPLATE, "protection", 4, "thorns", 3));
        put(handler, AnvilScreenHandler.INPUT_2_ID,
                enchantedItem(world, Items.DIAMOND_CHESTPLATE, "fire_protection", 4, "thorns", 3));
        handler.updateResult();

        ItemStack output = take(handler, AnvilScreenHandler.OUTPUT_ID);
        context.assertTrue(!output.isEmpty(), "盔甲合并没有产出：" + describe(handler));
        int protection = levelOf(world, output, "protection");
        int fire = levelOf(world, output, "fire_protection");
        context.assertTrue(protection == 4 && fire == 4,
                "保护与火焰保护现在应当能共存，实际 保护=" + protection + " 火焰保护=" + fire);
        context.complete();
    }

    /** 工具：时运 + 精准采集 现在应该能共存 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void miningEnchantmentsNowCombine(TestContext context) {
        ServerWorld world = context.getWorld();
        AnvilScreenHandler handler = newAnvil(context);
        put(handler, AnvilScreenHandler.INPUT_1_ID,
                enchantedItem(world, Items.DIAMOND_PICKAXE, "fortune", 3, "efficiency", 5));
        put(handler, AnvilScreenHandler.INPUT_2_ID,
                enchantedItem(world, Items.DIAMOND_PICKAXE, "silk_touch", 1, "efficiency", 5));
        handler.updateResult();

        ItemStack output = take(handler, AnvilScreenHandler.OUTPUT_ID);
        context.assertTrue(!output.isEmpty(), "镐合并没有产出：" + describe(handler));
        int fortune = levelOf(world, output, "fortune");
        int silk = levelOf(world, output, "silk_touch");
        context.assertTrue(fortune == 3 && silk == 1,
                "时运与精准采集现在应当能共存，实际 时运=" + fortune + " 精准采集=" + silk);
        context.complete();
    }

    // ---------------------------------------------------------- 互斥：该保留的仍在

    /** 靴子：冰霜行者与深海探索者仍然互斥（需求要求保留） */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void bootsWalkersStayExclusive(TestContext context) {
        ServerWorld world = context.getWorld();
        AnvilScreenHandler handler = newAnvil(context);
        // 带一个能正常合并的附魔（保护 IV+IV→V），保证铁砧一定有产出
        put(handler, AnvilScreenHandler.INPUT_1_ID,
                enchantedItem(world, Items.DIAMOND_BOOTS, "frost_walker", 2, "protection", 4));
        put(handler, AnvilScreenHandler.INPUT_2_ID,
                enchantedItem(world, Items.DIAMOND_BOOTS, "depth_strider", 3, "protection", 4));
        handler.updateResult();

        ItemStack output = take(handler, AnvilScreenHandler.OUTPUT_ID);
        context.assertTrue(!output.isEmpty(), "靴子合并没有产出：" + describe(handler));
        int frost = levelOf(world, output, "frost_walker");
        int depth = levelOf(world, output, "depth_strider");
        context.assertTrue(frost > 0 && depth == 0,
                "冰霜行者与深海探索者应当仍然互斥，实际 冰霜行者=" + frost + " 深海探索者=" + depth);
        context.complete();
    }

    /** 三叉戟：激流与忠诚仍然互斥（需求要求保留） */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void tridentRiptideStaysExclusive(TestContext context) {
        ServerWorld world = context.getWorld();
        AnvilScreenHandler handler = newAnvil(context);
        put(handler, AnvilScreenHandler.INPUT_1_ID,
                enchantedItem(world, Items.TRIDENT, "riptide", 3, "unbreaking", 3));
        put(handler, AnvilScreenHandler.INPUT_2_ID,
                enchantedItem(world, Items.TRIDENT, "loyalty", 3, "unbreaking", 3));
        handler.updateResult();

        ItemStack output = take(handler, AnvilScreenHandler.OUTPUT_ID);
        context.assertTrue(!output.isEmpty(), "三叉戟合并没有产出：" + describe(handler));
        int riptide = levelOf(world, output, "riptide");
        int loyalty = levelOf(world, output, "loyalty");
        context.assertTrue(riptide > 0 && loyalty == 0,
                "激流与忠诚应当仍然互斥，实际 激流=" + riptide + " 忠诚=" + loyalty);
        context.complete();
    }

    // ---------------------------------------------------------- 下界之星

    /** 星槽存在、没星取不出来、放星后能取、取出后星被消耗、经验不受影响 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void netherStarSlotGatesAndIsConsumed(TestContext context) {
        PlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        AnvilScreenHandler handler = new AnvilScreenHandler(999, player.getInventory(), ScreenHandlerContext.EMPTY);

        NetherStarSlot starSlot = findStarSlot(handler);
        context.assertTrue(starSlot != null, "铁砧界面里没有找到下界之星槽位");
        context.assertTrue(starSlot.canInsert(new ItemStack(Items.NETHER_STAR)), "星槽应当接受下界之星");
        context.assertFalse(starSlot.canInsert(new ItemStack(Items.DIAMOND)), "星槽不该接受钻石");
        context.assertTrue(starSlot.getMaxItemCount() >= 64,
                "星槽应当能放一整组（64），实际上限 " + starSlot.getMaxItemCount());
        starSlot.setStack(new ItemStack(Items.NETHER_STAR, 64));
        context.assertTrue(starSlot.getStack().getCount() == 64,
                "星槽应当容纳 64 颗，实际 " + starSlot.getStack().getCount());
        starSlot.setStack(ItemStack.EMPTY);

        put(handler, AnvilScreenHandler.INPUT_1_ID, enchanted(context.getWorld(), Items.DIAMOND_SWORD, "sharpness", 5));
        put(handler, AnvilScreenHandler.INPUT_2_ID, enchanted(context.getWorld(), Items.DIAMOND_SWORD, "sharpness", 5));
        handler.updateResult();
        ItemStack output = take(handler, AnvilScreenHandler.OUTPUT_ID);
        context.assertTrue(!output.isEmpty(), "铁砧没有产出：" + describe(handler));

        AnvilScreenHandlerInvoker invoker = (AnvilScreenHandlerInvoker) handler;
        context.assertFalse(invoker.anvil_unbound$invokeCanTakeOutput(player, false),
                "星槽为空时不该能取出结果");

        starSlot.setStack(new ItemStack(Items.NETHER_STAR));
        context.assertTrue(invoker.anvil_unbound$invokeCanTakeOutput(player, false),
                "放下下界之星后应当可以取出结果");

        int experienceBefore = player.experienceLevel;
        handler.getSlot(AnvilScreenHandler.OUTPUT_ID).onTakeItem(player, output);
        context.assertTrue(starSlot.getStack().isEmpty(), "取出结果后下界之星应当被消耗掉");
        context.assertTrue(player.experienceLevel == experienceBefore,
                "取出结果不该消耗经验，经验从 " + experienceBefore + " 变成了 " + player.experienceLevel);
        context.complete();
    }

    // ---------------------------------------------------------- 辅助方法

    /** 关闭界面时星槽里的下界之星必须还给玩家——原版不会返还这个自建 inventory，漏了就是吞物品。 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void closingAnvilReturnsNetherStars(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        AnvilScreenHandler handler = new AnvilScreenHandler(999, player.getInventory(), ScreenHandlerContext.EMPTY);
        NetherStarSlot starSlot = findStarSlot(handler);
        context.assertTrue(starSlot != null, "铁砧界面里没有找到下界之星槽位");

        starSlot.setStack(new ItemStack(Items.NETHER_STAR, 5));
        int before = countNetherStars(player);
        handler.onClosed(player);
        int after = countNetherStars(player);

        context.assertTrue(starSlot.getStack().isEmpty(), "关闭界面后星槽应当被清空");
        context.assertTrue(after == before + 5,
                "关闭界面后应当收回 5 颗下界之星，实际收回 " + (after - before) + " 颗");
        context.complete();
    }

    private static int countNetherStars(PlayerEntity player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.NETHER_STAR)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static NetherStarSlot findStarSlot(AnvilScreenHandler handler) {
        for (Slot slot : handler.slots) {
            if (slot instanceof NetherStarSlot star) {
                return star;
            }
        }
        return null;
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
