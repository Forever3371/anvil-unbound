package com.example.anvilunbound.mixin;

import com.example.anvilunbound.AnvilUnboundConfig;
import com.example.anvilunbound.AnvilUnboundRules;
import com.example.anvilunbound.NetherStarSlot;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 本模组的全部服务端改动都在这一个类里：
 *
 * <ol>
 *     <li><b>等级上限</b>：{@code updateResult} 里那两处 {@code getMaxLevel()} 重定向成配置上限——
 *         1.21.1 的合并结果就是被这一段夹取的（已核对字节码）。</li>
 *     <li><b>解除互斥</b>：把互斥判断 {@code Enchantment.canBeCombined} 换成
 *         {@link AnvilUnboundRules#canCombine}，除靴子和三叉戟那两组外一律放开。</li>
 *     <li><b>下界之星槽位</b>：原版槽位都建好之后，追加一个只收下界之星的槽（输出槽右侧）。</li>
 *     <li><b>花费改成下界之星</b>：{@code canTakeOutput} 改成"必须有 1 颗下界之星"，
 *         {@code onTakeOutput} 不再扣经验、改为消耗一颗星。</li>
 * </ol>
 *
 * 经验等级完全不再消耗；累积惩罚公式与合并花费乘数表都没动。
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {

    /** 下界之星槽的位置：输出槽（x=134）右侧，仍在原版 GUI 贴图范围内。 */
    @Unique
    private static final int anvil_unbound$STAR_SLOT_X = 152;
    @Unique
    private static final int anvil_unbound$STAR_SLOT_Y = 47;

    @Unique
    private Inventory anvil_unbound$starInventory;

    // ------------------------------------------------------------ 1. 等级上限

    @Redirect(
            method = "updateResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/enchantment/Enchantment;getMaxLevel()I")
    )
    private int anvil_unbound$raiseAnvilLevelCap(Enchantment enchantment) {
        return Math.max(enchantment.getMaxLevel(), AnvilUnboundConfig.maxLevel);
    }

    // ------------------------------------------------------------ 2. 解除互斥

    @Redirect(
            method = "updateResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/enchantment/Enchantment;canBeCombined("
                            + "Lnet/minecraft/registry/entry/RegistryEntry;"
                            + "Lnet/minecraft/registry/entry/RegistryEntry;)Z"
            )
    )
    private boolean anvil_unbound$relaxExclusivity(RegistryEntry<Enchantment> first,
                                                   RegistryEntry<Enchantment> second) {
        return AnvilUnboundRules.canCombine(first, second);
    }

    // ------------------------------------------------------------ 3. 下界之星槽位

    @Inject(
            method = "<init>(ILnet/minecraft/entity/player/PlayerInventory;"
                    + "Lnet/minecraft/screen/ScreenHandlerContext;)V",
            at = @At("TAIL")
    )
    private void anvil_unbound$addStarSlot(int syncId, PlayerInventory playerInventory,
                                           ScreenHandlerContext context, CallbackInfo ci) {
        if (!AnvilUnboundConfig.requireNetherStar) {
            return;
        }
        this.anvil_unbound$starInventory = new SimpleInventory(1) {
            @Override
            public void markDirty() {
                AnvilScreenHandlerMixin.this.anvil_unbound$onStarSlotChanged();
            }
        };
        ((ScreenHandlerInvoker) this).anvil_unbound$invokeAddSlot(
                new NetherStarSlot(this.anvil_unbound$starInventory, 0,
                        anvil_unbound$STAR_SLOT_X, anvil_unbound$STAR_SLOT_Y));
    }

    /** 星槽内容变化时重算结果并同步给客户端（与原版输入槽行为一致）。 */
    @Unique
    private void anvil_unbound$onStarSlotChanged() {
        AnvilScreenHandler self = (AnvilScreenHandler) (Object) this;
        self.updateResult();
        self.sendContentUpdates();
    }

    /** 供客户端界面与自检命令查询：星槽里现在有没有下界之星。 */
    @Unique
    private boolean anvil_unbound$hasStar() {
        return this.anvil_unbound$starInventory != null
                && this.anvil_unbound$starInventory.getStack(0).isOf(Items.NETHER_STAR);
    }

    // ------------------------------------------------------------ 4. 花费改成下界之星

    /**
     * 原版：{@code (创造模式 || 经验等级 >= 花费) && 花费 > 0}。
     * 现在改成：{@code 有 1 颗下界之星 && 有有效操作}，不再看经验、也没有"过于昂贵"的限制。
     */
    @Inject(method = "canTakeOutput", at = @At("HEAD"), cancellable = true)
    private void anvil_unbound$requireStarToTake(PlayerEntity player, boolean present,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!AnvilUnboundConfig.requireNetherStar) {
            return;
        }
        AnvilScreenHandler self = (AnvilScreenHandler) (Object) this;
        cir.setReturnValue(self.getLevelCost() > 0 && anvil_unbound$hasStar());
    }

    /** 不再扣除经验等级——花费已经改成下界之星。 */
    @Redirect(
            method = "onTakeOutput",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addExperienceLevels(I)V")
    )
    private void anvil_unbound$skipExperienceCost(PlayerEntity player, int levels) {
        // 有意留空：经验不再被消耗
    }

    @Inject(method = "onTakeOutput", at = @At("TAIL"))
    private void anvil_unbound$consumeStar(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!AnvilUnboundConfig.requireNetherStar || this.anvil_unbound$starInventory == null) {
            return;
        }
        ItemStack star = this.anvil_unbound$starInventory.getStack(0);
        if (star.isOf(Items.NETHER_STAR)) {
            star.decrement(1);
            this.anvil_unbound$starInventory.markDirty();
        }
    }
}
