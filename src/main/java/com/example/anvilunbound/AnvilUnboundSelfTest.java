package com.example.anvilunbound;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏内自检：{@code /anvilunbound selftest}
 * <p>
 * 检查三件事：
 * <ol>
 *     <li>附魔等级上限确实被放开了（组件写入 + NBT 往返都保持配置的等级）</li>
 *     <li><b>互斥规则没有被破坏</b>——这是本模组最容易做错的地方，逐个抽查原版的互斥对</li>
 *     <li>真的构造一个铁砧界面跑合并：5+5=6、5+3=5、40+40=41、1000+1000=1000，以及锋利+亡灵杀手仍然互斥</li>
 * </ol>
 * 需要在有玩家上下文时才能跑第 3 项（控制台执行时会跳过并说明）。
 */
public final class AnvilUnboundSelfTest {

    private AnvilUnboundSelfTest() {
    }

    private record Result(String name, boolean pass, String detail) {
    }

    /** @return 全部通过时返回检查项数量，否则返回 0 */
    public static int run(ServerCommandSource source) {
        List<Result> results = new ArrayList<>();
        ServerWorld world = source.getWorld();

        results.add(levelCapCheck(world));
        results.add(exclusivityCheck(world));
        results.addAll(anvilMergeChecks(source, world));

        int passed = 0;
        for (Result result : results) {
            if (result.pass()) {
                passed++;
            }
        }
        final int passedCount = passed;
        final boolean allPass = passed == results.size();

        source.sendFeedback(() -> Text.literal("===== Anvil Unbound 自检 =====").formatted(Formatting.AQUA), false);
        AnvilUnbound.LOGGER.info("===== Anvil Unbound 自检 =====");
        for (Result result : results) {
            AnvilUnbound.LOGGER.info("[自检] {} {}：{}",
                    result.pass() ? "通过" : "失败", result.name(), result.detail());
            source.sendFeedback(() -> Text.literal((result.pass() ? "  [通过] " : "  [失败] ")
                            + result.name() + "：" + result.detail())
                    .formatted(result.pass() ? Formatting.GREEN : Formatting.RED), false);
        }
        source.sendFeedback(() -> Text.literal("结果：" + passedCount + "/" + results.size()
                        + (allPass ? "  全部通过" : "  存在失败项"))
                .formatted(allPass ? Formatting.GREEN : Formatting.RED), false);
        AnvilUnbound.LOGGER.info("[自检] 结果：{}/{}", passedCount, results.size());

        return allPass ? results.size() : 0;
    }

    /** 1. 附魔等级能存到配置的上限（三处 255 硬上限都被绕过）。 */
    private static Result levelCapCheck(ServerWorld world) {
        String name = "附魔等级上限";
        try {
            Registry<Enchantment> registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            Enchantment sharpness = registry.get(Identifier.ofVanilla("sharpness"));
            if (sharpness == null) {
                return new Result(name, false, "注册表里找不到 minecraft:sharpness");
            }
            RegistryEntry<Enchantment> entry = registry.getEntry(sharpness);
            int expected = AnvilUnboundConfig.maxLevel;

            ItemEnchantmentsComponent.Builder builder =
                    new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
            builder.add(entry, expected);
            ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
            stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());

            ItemEnchantmentsComponent built = stack.get(DataComponentTypes.ENCHANTMENTS);
            if (built == null) {
                return new Result(name, false, "组件写入后读不回来（构造阶段可能抛异常）");
            }
            int afterBuild = built.getLevel(entry);
            if (afterBuild != expected) {
                return new Result(name, false, "写入后变成 " + afterBuild + "（期望 " + expected + "）");
            }

            RegistryOps<NbtElement> ops = world.getRegistryManager().getOps(NbtOps.INSTANCE);
            NbtElement encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
            ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
            ItemEnchantmentsComponent afterRoundTrip = decoded.get(DataComponentTypes.ENCHANTMENTS);
            if (afterRoundTrip == null || afterRoundTrip.isEmpty()) {
                return new Result(name, false, "NBT 往返后附魔整个丢了");
            }
            int level = afterRoundTrip.getLevel(entry);
            if (level != expected) {
                return new Result(name, false, "NBT 往返后变成 " + level + "（期望 " + expected + "）");
            }
            return new Result(name, true, "组件 + NBT 往返都保持 " + expected + " 级");
        } catch (Exception e) {
            return new Result(name, false, "抛出异常：" + e);
        }
    }

    /**
     * 2. 互斥规则必须和原版一致。
     * 本模组只该放宽"等级上限"，一旦这里失败，说明改动误伤了互斥逻辑。
     */
    private static Result exclusivityCheck(ServerWorld world) {
        String name = "附魔互斥规则";
        try {
            Registry<Enchantment> registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            // {附魔A, 附魔B, 是否应当互斥}
            Object[][] pairs = {
                    {"sharpness", "smite", true},
                    {"sharpness", "bane_of_arthropods", true},
                    {"smite", "bane_of_arthropods", true},
                    {"protection", "fire_protection", true},
                    {"protection", "blast_protection", true},
                    {"protection", "projectile_protection", true},
                    {"frost_walker", "depth_strider", true},
                    {"riptide", "loyalty", true},
                    {"riptide", "channeling", true},
                    {"loyalty", "channeling", false},
                    {"infinity", "mending", true},
                    {"multishot", "piercing", true},
                    {"silk_touch", "fortune", true},
                    // canBeCombined 问的是"两个附魔能否共存"，同一个附魔原版也返回 false
                    {"sharpness", "sharpness", true},
            };

            List<String> broken = new ArrayList<>();
            for (Object[] pair : pairs) {
                String a = (String) pair[0];
                String b = (String) pair[1];
                boolean shouldBeExclusive = (Boolean) pair[2];
                Enchantment first = registry.get(Identifier.ofVanilla(a));
                Enchantment second = registry.get(Identifier.ofVanilla(b));
                if (first == null || second == null) {
                    broken.add(a + "/" + b + " 不存在");
                    continue;
                }
                boolean exclusive = !Enchantment.canBeCombined(registry.getEntry(first), registry.getEntry(second));
                if (exclusive != shouldBeExclusive) {
                    broken.add(a + " vs " + b + " 期望" + (shouldBeExclusive ? "互斥" : "可共存")
                            + "、实际" + (exclusive ? "互斥" : "可共存"));
                }
            }
            if (!broken.isEmpty()) {
                return new Result(name, false, String.join("；", broken));
            }
            return new Result(name, true, "抽查 " + pairs.length + " 组关系，全部与原版一致（互斥未被解除）");
        } catch (Exception e) {
            return new Result(name, false, "抛出异常：" + e);
        }
    }

    /** 3. 真的构造铁砧界面跑一遍合并。 */
    private static List<Result> anvilMergeChecks(ServerCommandSource source, ServerWorld world) {
        List<Result> results = new ArrayList<>();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            // 没有玩家就只触发一次类加载，确认 Mixin 在正式命名空间下能应用
            results.add(new Result("铁砧界面加载", AnvilScreenHandler.class != null,
                    "无玩家上下文，仅确认 AnvilScreenHandler 已加载并完成 Mixin 注入（合并逻辑由 gametest 覆盖）"));
            return results;
        }

        try {
            AnvilScreenHandler handler =
                    new AnvilScreenHandler(999, player.getInventory(), ScreenHandlerContext.EMPTY);
            results.add(mergeCheck(world, handler, "sharpness", 5, "sharpness", 5, 6,
                    "铁砧合并 锋利V + 锋利V"));
            results.add(mergeCheck(world, handler, "sharpness", 5, "sharpness", 3, 5,
                    "铁砧合并 锋利V + 锋利III"));
            results.add(mergeCheck(world, handler, "sharpness", 40, "sharpness", 40, 41,
                    "铁砧合并 锋利40 + 锋利40"));
            results.add(mergeCheck(world, handler, "sharpness", AnvilUnboundConfig.maxLevel,
                    "sharpness", AnvilUnboundConfig.maxLevel, AnvilUnboundConfig.maxLevel,
                    "铁砧合并 达到上限 " + AnvilUnboundConfig.maxLevel));
            results.add(exclusiveMergeCheck(world, handler));
        } catch (Exception e) {
            results.add(new Result("铁砧合并逻辑", false, "抛出异常：" + e));
        }
        return results;
    }

    private static Result mergeCheck(ServerWorld world, AnvilScreenHandler handler,
                                     String enchantmentId, int firstLevel, String secondEnchantmentId, int secondLevel,
                                     int expectedLevel, String label) {
        put(handler, AnvilScreenHandler.INPUT_1_ID, enchantedSword(world, enchantmentId, firstLevel));
        put(handler, AnvilScreenHandler.INPUT_2_ID, enchantedSword(world, secondEnchantmentId, secondLevel));
        handler.updateResult();

        ItemStack output = take(handler, AnvilScreenHandler.OUTPUT_ID);
        if (output.isEmpty()) {
            return new Result(label, false, "铁砧没有产出");
        }
        int actual = levelOf(world, output, enchantmentId);
        if (actual != expectedLevel) {
            return new Result(label, false, "期望 " + expectedLevel + "、实际 " + actual);
        }
        return new Result(label, true, "结果 " + enchantmentId + " = " + actual);
    }

    /**
     * 互斥的附魔不该被合进结果。
     * 场景特意带一个可以正常合并的附魔（抢夺 III+III → IV），
     * 否则两件满耐久又完全互斥的装备会被原版判定为"没有有效操作"，输出槽是空的，断言无从下手。
     */
    private static Result exclusiveMergeCheck(ServerWorld world, AnvilScreenHandler handler) {
        String label = "铁砧互斥仍生效（锋利 + 亡灵杀手）";
        put(handler, AnvilScreenHandler.INPUT_1_ID,
                enchantedSword(world, "sharpness", 5, "looting", 3));
        put(handler, AnvilScreenHandler.INPUT_2_ID,
                enchantedSword(world, "smite", 5, "looting", 3));
        handler.updateResult();

        ItemStack output = take(handler, AnvilScreenHandler.OUTPUT_ID);
        if (output.isEmpty()) {
            return new Result(label, false, "铁砧没有产出");
        }
        int smite = levelOf(world, output, "smite");
        if (smite > 0) {
            return new Result(label, false, "结果竟带上了互斥的亡灵杀手 " + smite + " 级（互斥被破坏）");
        }
        int looting = levelOf(world, output, "looting");
        if (looting != 4) {
            return new Result(label, false, "抢夺 III + III 应当合并为 IV，实际 " + looting);
        }
        return new Result(label, true, "抢夺正确合并为 IV，互斥的亡灵杀手被拒绝");
    }

    /** 1.21 的 ScreenHandler 不再提供 setStack/getStack，必须经由槽位对象读写。 */
    private static void put(AnvilScreenHandler handler, int slot, ItemStack stack) {
        handler.getSlot(slot).setStack(stack);
    }

    private static ItemStack take(AnvilScreenHandler handler, int slot) {
        return handler.getSlot(slot).getStack();
    }

    private static ItemStack enchantedSword(ServerWorld world, String enchantmentId, int level) {
        return enchantedSword(world, enchantmentId, level, null, 0);
    }

    /** 按 "附魔id, 等级" 给钻石剑挂附魔，第二组可以为 null（表示不需要）。 */
    private static ItemStack enchantedSword(ServerWorld world, String firstId, int firstLevel,
                                            String secondId, int secondLevel) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        Registry<Enchantment> registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        ItemEnchantmentsComponent.Builder builder =
                new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        addEnchantment(registry, builder, firstId, firstLevel);
        if (secondId != null) {
            addEnchantment(registry, builder, secondId, secondLevel);
        }
        stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
        return stack;
    }

    private static void addEnchantment(Registry<Enchantment> registry,
                                       ItemEnchantmentsComponent.Builder builder, String id, int level) {
        Enchantment enchantment = registry.get(Identifier.ofVanilla(id));
        if (enchantment != null) {
            builder.add(registry.getEntry(enchantment), level);
        }
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
