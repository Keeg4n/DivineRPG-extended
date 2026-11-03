package com.divinerpgextended.station;

import java.util.Arrays;
import java.util.List;

import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Окно алтаря порчи ({@link AltarLogic}). Слоты свои у окна ({@link AnvilWork}, как у Наковальни Странника): вещь и
 * оплата; окно закрылось - всё вернулось игроку. Кнопки идут стандартным пакетом кнопки стола зачарования: 0-2 -
 * зачаровать слот, {@link #REVEAL}..+2 - узнать его чары.
 * <p>
 * Чары бросаются на сервере, когда вещь кладут в алтарь, и живут, пока она там лежит. Клиенту уходят только цены и уже
 * оплаченные чары - даром не подсмотреть.
 */
public class ContainerCorruptAltar extends Container {

    public static final int ITEM = 0, PAY = 1, SIZE = 2;
    public static final int REVEAL = 3;
    /** Сколько чаров слота показывает окно (на вещь ложатся все). */
    public static final int SHOWN = 8;
    // поля окна: цены слотов, сколько чаров узнано (-1 - не узнаны), цена "узнать", сами чары
    private static final int LEVEL = 0, COUNT = 3, PRICE = 6, LIST = 9, FIELDS = LIST + AltarLogic.SLOTS * SHOWN;

    private final World world;
    private final int x, y, z;
    private final AnvilWork work = new AnvilWork("tile.altarOfCorruption.name", SIZE);

    // клиент: что показывать
    public final int[] levels = new int[AltarLogic.SLOTS], price = new int[AltarLogic.SLOTS], knownCount = { -1, -1, -1 };
    /** Узнанные чары слота: номер чара << 5 | уровень. */
    public final int[][] known = new int[AltarLogic.SLOTS][SHOWN];

    // сервер: бросок для вещи в алтаре, что из него узнано, что уже отправлено
    private List<List<EnchantmentData>> rolls;
    private ItemStack rolledFor;
    private boolean stale = true;
    private final boolean[] seen = new boolean[AltarLogic.SLOTS];
    private final int[] sent = new int[FIELDS];

    public ContainerCorruptAltar(InventoryPlayer inv, World world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        Arrays.fill(sent, Integer.MIN_VALUE);
        addSlotToContainer(new Slot(work, ITEM, 15, 47) {

            @Override
            public int getSlotStackLimit() {
                return 1;
            }

            @Override
            public void putStack(ItemStack s) {
                super.putStack(s);
                stale = true;
            }
        });
        addSlotToContainer(new Slot(work, PAY, 35, 47) {

            @Override
            public boolean isItemValid(ItemStack s) {
                return AltarLogic.payment(s);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inv, col, 8 + col * 18, 142));
    }

    /** Рабочие слоты этого окна. */
    public AnvilWork work() {
        return work;
    }

    /** Бросок слота для вещи в алтаре (сервер): другая вещь или та же, но положенная заново, - новые чары, узнанное забыто. */
    public List<EnchantmentData> rolled(int slot) {
        ItemStack s = work.getStackInSlot(ITEM);
        if (stale || s != rolledFor) {
            stale = false;
            rolledFor = s;
            rolls = AltarLogic.rollAll(world.rand, s);
            Arrays.fill(seen, false);
        }
        return rolls.get(slot);
    }

    /** Чары слота уже оплачены (сервер). */
    public boolean revealed(int slot) {
        rolled(slot);
        return seen[slot];
    }

    public static int pack(EnchantmentData d) {
        return d.enchantmentobj.effectId << 5 | Math.min(d.enchantmentLevel, 31);
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return AltarLogic.altar(world, x, y, z) && p.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) <= 64.0;
    }

    /** Окно закрылось (в том числе выход с сервера и смерть) - вещь и оплата вернулись игроку. */
    @Override
    public void onContainerClosed(EntityPlayer p) {
        super.onContainerClosed(p);
        if (!p.worldObj.isRemote) work.giveBack(p);
    }

    /** Что сейчас должно быть в окне (сервер). */
    private int[] state() {
        int[] v = new int[FIELDS];
        ItemStack s = work.getStackInSlot(ITEM);
        boolean book = AltarLogic.book(s);
        for (int i = 0; i < AltarLogic.SLOTS; i++) {
            List<EnchantmentData> roll = rolled(i);
            v[LEVEL + i] = AltarLogic.price(roll, book);
            v[PRICE + i] = AltarLogic.revealCount(s, i);
            List<EnchantmentData> l = v[LEVEL + i] > 0 && seen[i] ? roll : null;
            v[COUNT + i] = l == null ? -1 : Math.min(l.size(), SHOWN);
            for (int k = 0; k < SHOWN; k++) v[LIST + i * SHOWN + k] = l != null && k < l.size() ? pack(l.get(k)) : -1;
        }
        return v;
    }

    @Override
    public void addCraftingToCrafters(ICrafting c) {
        super.addCraftingToCrafters(c);
        Arrays.fill(sent, Integer.MIN_VALUE);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int[] v = state();
        for (int i = 0; i < FIELDS; i++) {
            if (v[i] == sent[i]) continue;
            for (Object o : crafters) ((ICrafting) o).sendProgressBarUpdate(this, i, v[i]);
            sent[i] = v[i];
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int value) {
        if (id < COUNT) levels[id - LEVEL] = value;
        else if (id < PRICE) knownCount[id - COUNT] = value;
        else if (id < LIST) price[id - PRICE] = value;
        else if (id < FIELDS) known[(id - LIST) / SHOWN][(id - LIST) % SHOWN] = value;
    }

    @Override
    public boolean enchantItem(EntityPlayer p, int id) {
        if (p.worldObj.isRemote || id < 0 || id >= REVEAL + AltarLogic.SLOTS) return false;
        int slot = id < REVEAL ? id : id - REVEAL;
        ItemStack s = work.getStackInSlot(ITEM);
        List<EnchantmentData> list = rolled(slot);
        int cost = AltarLogic.price(list, AltarLogic.book(s));
        if (cost <= 0) return false;
        double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;
        if (id >= REVEAL) {
            if (seen[slot]) return false;
            int n = AltarLogic.revealCount(s, slot);
            if (!p.capabilities.isCreativeMode && n > 0) {
                ItemStack pay = work.getStackInSlot(PAY);
                if (pay == null || pay.getItem() != AltarLogic.revealItem(s, slot) || pay.stackSize < n) return false;
                work.decrStackSize(PAY, n);
            }
            seen[slot] = true;
            world.playSoundEffect(cx, cy, cz, "random.orb", 0.6F, 0.6F);
            detectAndSendChanges();
            return true;
        }
        if (!p.capabilities.isCreativeMode) {
            if (p.experienceLevel < cost) return false;
            AltarLogic.takeLevels(p, cost);
        }
        AltarLogic.apply(s, list);
        work.setInventorySlotContents(ITEM, s);
        stale = true;
        world.playSoundEffect(cx, cy, cz, "mob.endermen.portal", 0.7F, 0.6F);
        detectAndSendChanges();
        return true;
    }

    /** Shift-клик: оплата - в ячейку оплаты, остальное - в слот вещи (одну штуку); из алтаря - в инвентарь. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index < SIZE) {
            if (!mergeItemStack(stack, SIZE, inventorySlots.size(), true)) return null;
        } else if (AltarLogic.payment(stack)) {
            if (!mergeItemStack(stack, PAY, PAY + 1, false)) return null;
        } else {
            Slot item = (Slot) inventorySlots.get(ITEM);
            if (item.getHasStack()) return null;
            item.putStack(stack.splitStack(1));
        }
        if (stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        return copy;
    }
}
