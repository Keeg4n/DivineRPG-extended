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
 * оплата (осколки или камень порчи); окно закрылось - всё вернулось игроку. Кнопки идут стандартным пакетом кнопки стола
 * зачарования: 0-2 - зачаровать слот, {@link #REVEAL}..+2 - узнать его чары.
 * <p>
 * Клиенту уходят только цены и уже оплаченные чары. Зерно игрока остаётся на сервере - даром не подсмотреть.
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
    private final EntityPlayer player;
    private final AnvilWork work = new AnvilWork("tile.altarOfCorruption.name", SIZE);

    // клиент: что показывать
    public final int[] levels = new int[AltarLogic.SLOTS], price = new int[AltarLogic.SLOTS], knownCount = { -1, -1, -1 };
    /** Узнанные чары слота: номер чара << 5 | уровень. */
    public final int[][] known = new int[AltarLogic.SLOTS][SHOWN];

    // сервер: что уже отправлено
    private final int[] sent = new int[FIELDS];

    public ContainerCorruptAltar(InventoryPlayer inv, World world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.player = inv.player;
        Arrays.fill(sent, Integer.MIN_VALUE);
        addSlotToContainer(new Slot(work, ITEM, 15, 47) {

            @Override
            public int getSlotStackLimit() {
                return 1;
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
        int seed = AltarLogic.seed(player);
        int[] cost = AltarLogic.costs(seed, s);
        for (int i = 0; i < AltarLogic.SLOTS; i++) {
            v[LEVEL + i] = cost[i];
            v[PRICE + i] = AltarLogic.revealCount(i);
            List<EnchantmentData> l = cost[i] > 0 && AltarLogic.seen(player, s, i) ? AltarLogic.roll(seed, s, i) : null;
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
        int seed = AltarLogic.seed(p);
        int cost = AltarLogic.costs(seed, s)[slot];
        if (cost <= 0) return false;
        double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;
        if (id >= REVEAL) {
            if (AltarLogic.seen(p, s, slot)) return false;
            int n = AltarLogic.revealCount(slot);
            if (!p.capabilities.isCreativeMode && n > 0) {
                ItemStack pay = work.getStackInSlot(PAY);
                if (pay == null || pay.getItem() != AltarLogic.revealItem(slot) || pay.stackSize < n) return false;
                work.decrStackSize(PAY, n);
            }
            AltarLogic.see(p, s, slot);
            world.playSoundEffect(cx, cy, cz, "random.orb", 0.6F, 0.6F);
            detectAndSendChanges();
            return true;
        }
        if (p.experienceLevel < cost && !p.capabilities.isCreativeMode) return false;
        List<EnchantmentData> list = AltarLogic.roll(seed, s, slot);
        if (list.isEmpty()) return false;
        p.addExperienceLevel(-cost);
        AltarLogic.apply(s, list);
        work.setInventorySlotContents(ITEM, s);
        AltarLogic.reseed(p);
        world.playSoundEffect(cx, cy, cz, "mob.endermen.portal", 0.7F, 0.6F);
        detectAndSendChanges();
        return true;
    }

    /** Shift-клик: осколки и камень - в оплату, остальное - в слот вещи (одну штуку); из алтаря - в инвентарь. */
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
