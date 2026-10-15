package com.divinerpgextended.station;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.divinerpgextended.boss.BossProgress;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ContainerWandererAnvil extends Container {

    public static final int GUI_W = 176, GUI_H = 196;

    private final TileWandererAnvil tile;
    private final EntityPlayer player;

    // синхронизируемое состояние (сервер считает, клиент показывает)
    public int status = -1, param, bossMask, fuelItem;

    /** Слот брони или донора: любая часть брони, порядок не важен (наковальня сама разбирает части по типу). */
    private static class ArmorSlot extends Slot {

        ArmorSlot(TileWandererAnvil inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return stack.getItem() instanceof ItemArmor;
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }

    public ContainerWandererAnvil(InventoryPlayer inv, TileWandererAnvil tile) {
        this.tile = tile;
        this.player = inv.player;
        // сначала 4 слота брони, потом 4 донора: shift-клик заполняет сначала броню, затем донора
        for (int i = 0; i < 4; i++) addSlotToContainer(new ArmorSlot(tile, AnvilLogic.ARMOR + i, 8 + i * 18, 20));
        for (int i = 0; i < 4; i++) addSlotToContainer(new ArmorSlot(tile, AnvilLogic.DONOR + i, 8 + i * 18, 48));
        addSlotToContainer(new Slot(tile, AnvilLogic.FUEL, 8, 76));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 114 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inv, col, 8 + col * 18, 172));
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return tile.isUseableByPlayer(p);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        AnvilLogic.Result r = AnvilLogic.evaluate(tile, player);
        int s = r.status.ordinal(), p = r.param, m = BossProgress.mask(player);
        int f = r.fuel == null ? 0 : net.minecraft.item.Item.getIdFromItem(r.fuel);
        for (Object o : crafters) {
            ICrafting c = (ICrafting) o;
            if (s != status) c.sendProgressBarUpdate(this, 0, s);
            if (p != param) c.sendProgressBarUpdate(this, 1, p);
            if (m != bossMask) c.sendProgressBarUpdate(this, 2, m);
            if (f != fuelItem) c.sendProgressBarUpdate(this, 3, f);
        }
        status = s;
        param = p;
        bossMask = m;
        fuelItem = f;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int value) {
        if (id == 0) status = value;
        else if (id == 1) param = value;
        else if (id == 2) bossMask = value;
        else if (id == 3) fuelItem = value;
    }

    /** Кнопка "Улучшить" приходит сюда через стандартный пакет кнопки (как у стола зачарования). */
    @Override
    public boolean enchantItem(EntityPlayer p, int id) {
        if (id != 0 || p.worldObj.isRemote) return false;
        if (AnvilLogic.execute(tile, p)) {
            p.worldObj.playSoundEffect(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5, "random.anvil_use",
                1.0F, 1.0F);
            p.addChatMessage(new ChatComponentTranslation("dre.chat.upgraded"));
            detectAndSendChanges();
            return true;
        }
        return false;
    }

    /** Shift-клик: броня в слоты брони или донора, остальное в топливо; из станции - в инвентарь игрока. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        int stationSlots = 9;
        if (index < stationSlots) {
            if (!mergeItemStack(stack, stationSlots, inventorySlots.size(), true)) return null;
        } else {
            boolean moved = false;
            if (stack.getItem() instanceof ItemArmor) {
                for (int i = 0; i < 8 && !moved; i++) {
                    Slot target = (Slot) inventorySlots.get(i);
                    if (!target.getHasStack() && target.isItemValid(stack)) {
                        target.putStack(stack.splitStack(1));
                        moved = true;
                    }
                }
            } else {
                moved = mergeItemStack(stack, 8, 9, false);
            }
            if (!moved) return null;
        }
        if (stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        return copy;
    }
}
