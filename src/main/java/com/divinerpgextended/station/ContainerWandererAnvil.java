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
import com.divinerpgextended.perk.Ammo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Окно Наковальни Странника. Три вкладки ({@link AnvilLogic#UPGRADE}, {@link AnvilLogic#SOCKETS},
 * {@link AnvilLogic#UNLOAD}) на одних и тех же 9 слотах: вкладка решает, какие слоты видны, где стоят и что в них
 * кладётся. При смене вкладки всё, что ей не подходит, уходит в инвентарь игрока (нет места - вкладка не меняется).
 * Под каждой частью брони во вкладке гнёзд - её гнёзда: не слоты (внутри брони нет инвентаря), а картинки; клик по
 * занятому гнезду - кнопка "вынуть" ({@link #enchantItem}, id {@link #EXTRACT} + часть * 2 + гнездо).
 */
public class ContainerWandererAnvil extends Container {

    public static final int GUI_W = 176, GUI_H = 232;
    public static final int ROW1_Y = 24, ROW2_Y = 48, FUEL_Y = 72, SOCKET_Y = 44, ACC_Y = 86, STATUS_Y = 106, INV_Y = 150, HOTBAR_Y = 208;
    /** Кнопка "вынуть из гнезда": EXTRACT + слот брони * 2 + номер гнезда. */
    public static final int EXTRACT = 100;
    /** Кнопка вкладки: TAB + вкладка (номер кнопки идёт байтом - меньше 128). */
    public static final int TAB = 110;
    /** Последняя открытая вкладка игрока (в данных сущности, до выхода из игры). */
    public static final String LAST_TAB = "dreAnvilTab";
    private static final int HIDDEN = -10000;

    private final TileWandererAnvil tile;
    private final EntityPlayer player;

    // синхронизируемое состояние (сервер считает, клиент показывает)
    public int status = -1, param, bossMask, fuelItem, mode, targets;
    private int sentMode = -1, sentTargets;

    /** Слот станции: что в него кладётся, решает текущая вкладка. */
    private class StationSlot extends Slot {

        StationSlot(int index) {
            super(tile, index, HIDDEN, HIDDEN);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return visible(mode, getSlotIndex()) && valid(mode, getSlotIndex(), stack);
        }

        @Override
        public int getSlotStackLimit() {
            return getSlotIndex() == AnvilLogic.FUEL ? 64 : 1;
        }

        /** Пустой слот брони - силуэт своей части (как в инвентаре игрока). */
        @Override
        @SideOnly(Side.CLIENT)
        public net.minecraft.util.IIcon getBackgroundIconIndex() {
            int i = getSlotIndex();
            if (i == AnvilLogic.FUEL || i >= AnvilLogic.DONOR && mode != AnvilLogic.UPGRADE) return null;
            return ItemArmor.func_94602_b(i % 4);
        }
    }

    public ContainerWandererAnvil(InventoryPlayer inv, TileWandererAnvil tile) {
        this.tile = tile;
        this.player = inv.player;
        for (int i = 0; i < AnvilLogic.SIZE; i++) addSlotToContainer(new StationSlot(i));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(inv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inv, col, 8 + col * 18, HOTBAR_Y));
        mode = firstMode(tile, player);
        layout();
    }

    /** Слот станции виден во вкладке: улучшение - все, гнёзда - броня и доноры, разрядка - только топливо (оружие). */
    public static boolean visible(int mode, int slot) {
        return mode == AnvilLogic.UPGRADE || (mode == AnvilLogic.SOCKETS ? slot != AnvilLogic.FUEL : slot == AnvilLogic.FUEL);
    }

    /**
     * Что кладётся в слот во вкладке. Броня (и донорский сет) - по частям: шлем в первый слот ряда, ботинки в
     * последний. Топливо улучшения - что угодно, кроме оружия с зарядом; во вкладке разрядки там лежит оружие.
     */
    public static boolean valid(int mode, int slot, ItemStack s) {
        if (s == null) return true;
        if (slot == AnvilLogic.FUEL) return mode == AnvilLogic.UNLOAD ? Ammo.of(s) != null : Ammo.of(s) == null;
        if (slot >= AnvilLogic.DONOR && mode == AnvilLogic.SOCKETS) return Sockets.accessory(s);
        return s.getItem() instanceof ItemArmor && ((ItemArmor) s.getItem()).armorType == slot % 4;
    }

    /** Всё содержимое наковальни подходит вкладке (видно и кладётся туда). */
    static boolean fits(TileWandererAnvil tile, int mode) {
        for (int i = 0; i < AnvilLogic.SIZE; i++) {
            ItemStack s = tile.getStackInSlot(i);
            if (s != null && !(visible(mode, i) && valid(mode, i, s))) return false;
        }
        return true;
    }

    /** Вкладка при открытии: последняя у игрока, если в неё помещается содержимое, иначе первая подходящая. */
    static int firstMode(TileWandererAnvil tile, EntityPlayer p) {
        int last = p.getEntityData().getInteger(LAST_TAB);
        if (last >= 0 && last < AnvilLogic.MODES && fits(tile, last)) return last;
        for (int m = 0; m < AnvilLogic.MODES; m++) if (fits(tile, m)) return m;
        return AnvilLogic.UPGRADE; // видно всё - игрок сам заберёт лишнее
    }

    /** Расставить слоты станции под вкладку (скрытые - далеко за экраном). */
    private void layout() {
        for (int i = 0; i < AnvilLogic.SIZE; i++) {
            Slot s = (Slot) inventorySlots.get(i);
            int x = HIDDEN, y = HIDDEN;
            if (visible(mode, i)) {
                if (i == AnvilLogic.FUEL) {
                    x = 8;
                    y = mode == AnvilLogic.UNLOAD ? ROW1_Y : FUEL_Y;
                } else {
                    x = 8 + i % 4 * 18;
                    y = i < AnvilLogic.DONOR ? ROW1_Y : mode == AnvilLogic.SOCKETS ? ACC_Y : ROW2_Y;
                }
            }
            s.xDisplayPosition = x;
            s.yDisplayPosition = y;
        }
    }

    /**
     * Сменить вкладку (сервер): то, что ей не подходит, - в инвентарь игрока. Не влезло - остаётся в наковальне, вкладка
     * прежняя. Стак уменьшается при переносе в инвентарь, остаток кладётся обратно в слот - ничего не двоится.
     */
    public boolean select(EntityPlayer p, int m) {
        if (m < 0 || m >= AnvilLogic.MODES) return false;
        if (m == mode) return true;
        boolean ok = true;
        for (int i = 0; i < AnvilLogic.SIZE; i++) {
            ItemStack s = tile.getStackInSlot(i);
            if (s == null || visible(m, i) && valid(m, i, s)) continue;
            // в творческом addItemStackToInventory без места обнуляет стак, никуда не положив, - не отдаём
            if (p.capabilities.isCreativeMode && p.inventory.getFirstEmptyStack() < 0) {
                ok = false;
                continue;
            }
            p.inventory.addItemStackToInventory(s);
            if (s.stackSize <= 0) tile.setInventorySlotContents(i, null);
            else {
                tile.setInventorySlotContents(i, s);
                ok = false;
            }
        }
        tile.markDirty();
        if (!ok) {
            p.addChatMessage(new ChatComponentTranslation("dre.chat.anvil_tab_full"));
            return false;
        }
        mode = m;
        layout();
        p.getEntityData().setInteger(LAST_TAB, m);
        return true;
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
        AnvilLogic.Result r = AnvilLogic.evaluate(tile, player, mode);
        int s = r.status.ordinal(), p = r.param, m = BossProgress.mask(player), t = r.targets;
        int f = r.fuel == null ? 0 : net.minecraft.item.Item.getIdFromItem(r.fuel);
        for (Object o : crafters) {
            ICrafting c = (ICrafting) o;
            if (s != status) c.sendProgressBarUpdate(this, 0, s);
            if (p != param) c.sendProgressBarUpdate(this, 1, p);
            if (m != bossMask) c.sendProgressBarUpdate(this, 2, m);
            if (f != fuelItem) c.sendProgressBarUpdate(this, 3, f);
            if (mode != sentMode) c.sendProgressBarUpdate(this, 4, mode);
            if (t != sentTargets) c.sendProgressBarUpdate(this, 5, t);
        }
        status = s;
        param = p;
        bossMask = m;
        fuelItem = f;
        targets = sentTargets = t;
        sentMode = mode;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int value) {
        if (id == 0) status = value;
        else if (id == 1) param = value;
        else if (id == 2) bossMask = value;
        else if (id == 3) fuelItem = value;
        else if (id == 4 && value >= 0 && value < AnvilLogic.MODES) {
            mode = value;
            layout();
        } else if (id == 5) targets = value;
    }

    /**
     * Кнопки приходят сюда через стандартный пакет кнопки (как у стола зачарования): 0 - главная (улучшить, встроить,
     * разрядить), {@link #EXTRACT}.. - вынуть аксессуар из гнезда, {@link #TAB}.. - вкладка.
     */
    @Override
    public boolean enchantItem(EntityPlayer p, int id) {
        if (p.worldObj.isRemote) return false;
        double x = tile.xCoord + 0.5, y = tile.yCoord + 0.5, z = tile.zCoord + 0.5;
        if (id >= TAB && id < TAB + AnvilLogic.MODES) {
            boolean ok = select(p, id - TAB);
            detectAndSendChanges();
            return ok;
        }
        if (id >= EXTRACT && id < EXTRACT + 8) {
            if (mode != AnvilLogic.SOCKETS || !AnvilLogic.extract(tile, p, (id - EXTRACT) / 2, (id - EXTRACT) % 2)) return false;
            p.worldObj.playSoundEffect(x, y, z, "random.pop", 0.8F, 0.8F);
            detectAndSendChanges();
            return true;
        }
        if (id != 0) return false;
        AnvilLogic.Status before = AnvilLogic.evaluate(tile, p, mode).status;
        if (AnvilLogic.execute(tile, p, mode)) {
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
     * Shift-клик: в первый видимый слот станции, который принимает вещь (броня - в слот своей части, потом в донорский
     * ряд; аксессуар - в свободный слот аксессуаров; топливо и оружие - в нижний слот); из станции - в инвентарь игрока.
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index < AnvilLogic.SIZE) {
            if (!mergeItemStack(stack, AnvilLogic.SIZE, inventorySlots.size(), true)) return null;
        } else {
            boolean moved = false;
            for (int i = 0; i < AnvilLogic.SIZE && !moved; i++) {
                Slot target = (Slot) inventorySlots.get(i);
                if (!target.isItemValid(stack)) continue; // проверка вкладки: mergeItemStack её не делает
                if (i == AnvilLogic.FUEL) moved = mergeItemStack(stack, i, i + 1, false);
                else if (!target.getHasStack()) {
                    target.putStack(stack.splitStack(1));
                    moved = true;
                }
            }
            if (!moved) return null;
        }
        if (stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        return copy;
    }
}
