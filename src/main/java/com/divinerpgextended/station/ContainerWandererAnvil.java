package com.divinerpgextended.station;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.divinerpgextended.accessory.Sockets;
import com.divinerpgextended.boss.BossProgress;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Окно Наковальни Странника. Под каждой частью брони - её гнёзда: не слоты (внутри брони нет инвентаря), а картинки;
 * клик по занятому гнезду - кнопка "вынуть" ({@link #enchantItem}, id {@link #EXTRACT} + часть * 2 + гнездо).
 */
public class ContainerWandererAnvil extends Container {

    public static final int GUI_W = 176, GUI_H = 232;
    public static final int ARMOR_Y = 20, SOCKET_Y = 40, DONOR_Y = 82, FUEL_Y = 110, INV_Y = 150, HOTBAR_Y = 208;
    /** Кнопка "вынуть из гнезда": EXTRACT + слот брони * 2 + номер гнезда. */
    public static final int EXTRACT = 100;

    private final TileWandererAnvil tile;
    private final EntityPlayer player;

    // синхронизируемое состояние (сервер считает, клиент показывает)
    public int status = -1, param, bossMask, fuelItem;

    /** Слот брони: любая часть брони, порядок не важен (наковальня сама разбирает части по типу). */
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

    /** Слот донора: часть брони (сет для улучшения или перка) или аксессуар для гнезда. */
    private static class DonorSlot extends ArmorSlot {

        DonorSlot(TileWandererAnvil inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return super.isItemValid(stack) || Sockets.accessory(stack);
        }
    }

    public ContainerWandererAnvil(InventoryPlayer inv, TileWandererAnvil tile) {
        this.tile = tile;
        this.player = inv.player;
        // сначала 4 слота брони, потом 4 донора: shift-клик заполняет сначала броню, затем донора
        for (int i = 0; i < 4; i++) addSlotToContainer(new ArmorSlot(tile, AnvilLogic.ARMOR + i, 8 + i * 18, ARMOR_Y));
        for (int i = 0; i < 4; i++) addSlotToContainer(new DonorSlot(tile, AnvilLogic.DONOR + i, 8 + i * 18, DONOR_Y));
        addSlotToContainer(new Slot(tile, AnvilLogic.FUEL, 8, FUEL_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(inv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inv, col, 8 + col * 18, HOTBAR_Y));
    }

    public TileWandererAnvil tile() {
        return tile;
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

    /**
     * Кнопки приходят сюда через стандартный пакет кнопки (как у стола зачарования): 0 - главная (улучшить, встроить,
     * разрядить), {@link #EXTRACT}.. - вынуть аксессуар из гнезда.
     */
    @Override
    public boolean enchantItem(EntityPlayer p, int id) {
        if (p.worldObj.isRemote) return false;
        double x = tile.xCoord + 0.5, y = tile.yCoord + 0.5, z = tile.zCoord + 0.5;
        if (id >= EXTRACT && id < EXTRACT + 8) {
            if (!AnvilLogic.extract(tile, p, (id - EXTRACT) / 2, (id - EXTRACT) % 2)) return false;
            p.worldObj.playSoundEffect(x, y, z, "random.pop", 0.8F, 0.8F);
            detectAndSendChanges();
            return true;
        }
        if (id != 0) return false;
        AnvilLogic.Status before = AnvilLogic.evaluate(tile, p).status;
        if (AnvilLogic.execute(tile, p)) {
            if (before == AnvilLogic.Status.READY_UNLOAD) {
                ItemStack w = tile.getStackInSlot(AnvilLogic.FUEL);
                p.worldObj.playSoundEffect(x, y, z, w != null ? com.divinerpgextended.perk.AmmoLoad.sound(w) : "random.click", 0.9F, 0.8F);
            } else {
                p.worldObj.playSoundEffect(x, y, z, "random.anvil_use", 1.0F, before == AnvilLogic.Status.READY_SOCKET ? 1.4F : 1.0F);
                p.addChatMessage(new ChatComponentTranslation(before == AnvilLogic.Status.READY_SOCKET ? "dre.chat.socketed" : "dre.chat.upgraded"));
            }
            detectAndSendChanges();
            return true;
        }
        return false;
    }

    /**
     * Shift-клик: броня в слоты брони или донора, аксессуар в донора, остальное в топливо; из станции - в инвентарь
     * игрока.
     */
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
            boolean armor = stack.getItem() instanceof ItemArmor, acc = Sockets.accessory(stack);
            if (armor || acc) {
                for (int i = acc ? 4 : 0; i < 8 && !moved; i++) {
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
