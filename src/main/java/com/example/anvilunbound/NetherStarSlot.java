package com.example.anvilunbound;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;

/**
 * 铁砧界面里新增的"下界之星"槽位。
 * <p>
 * 做成独立的 Slot 子类，客户端渲染时就能靠 {@code instanceof NetherStarSlot} 精确找到它，
 * 不必假设它的索引（万一有其它模组也往铁砧界面加槽位，按索引找就会找错）。
 */
public class NetherStarSlot extends Slot {

    public NetherStarSlot(Inventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean canInsert(ItemStack stack) {
        return stack.isOf(Items.NETHER_STAR);
    }

    /** 一个槽只放一颗，够用又不会误吞玩家成组的星。 */
    @Override
    public int getMaxItemCount() {
        return 1;
    }

    @Override
    public boolean canTakeItems(PlayerEntity playerEntity) {
        return true;
    }
}
