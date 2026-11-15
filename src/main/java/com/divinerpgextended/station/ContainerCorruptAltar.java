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
 * Corruption altar window ({@link AltarLogic}). The window has its own slots ({@link AnvilWork}, like the Wanderer Anvil): item and
 * payment; when the window closes, everything returns to the player. Buttons arrive via the standard enchanting table button packet: 0-2 -
 * enchant the slot, {@link #REVEAL}..+2 - reveal its enchantments.
 * <p>
 * Enchantments are rolled on the server when an item is placed in the altar and live while it lies there. Only prices and already
 * paid enchantments go to the client - no free peeking.
 */
public class ContainerCorruptAltar extends Container {

    public static final int ITEM = 0, PAY = 1, SIZE = 2;
    public static final int REVEAL = 3;
    /** How many slot enchantments the window shows (all go on the item). */
    public static final int SHOWN = 8;
    // window fields: slot prices, how many enchantments are revealed (-1 - not revealed), "reveal" price, the enchantments themselves
    private static final int LEVEL = 0, COUNT = 3, PRICE = 6, LIST = 9, FIELDS = LIST + AltarLogic.SLOTS * SHOWN;

    private final World world;
    private final int x, y, z;
    private final AnvilWork work = new AnvilWork("tile.altarOfCorruption.name", SIZE);

    // client: what to show
    public final int[] levels = new int[AltarLogic.SLOTS], price = new int[AltarLogic.SLOTS], knownCount = { -1, -1, -1 };
    /** Revealed slot enchantments: enchantment number << 5 | level. */
    public final int[][] known = new int[AltarLogic.SLOTS][SHOWN];

    // server: the roll for the item in the altar, what has been revealed from it, what has already been sent
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

    /** Work slots of this window. */
    public AnvilWork work() {
        return work;
    }

    /** Slot roll for the item in the altar (server): a different item, or the same one placed again, - new enchantments, revealed ones forgotten. */
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

    /** The slot's enchantments are already paid for (server). */
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

    /** The window closed (including leaving the server and death) - the item and payment returned to the player. */
    @Override
    public void onContainerClosed(EntityPlayer p) {
        super.onContainerClosed(p);
        if (!p.worldObj.isRemote) work.giveBack(p);
    }

    /** What should be in the window now (server). */
    private int[] state() {
        int[] v = new int[FIELDS];
        ItemStack s = work.getStackInSlot(ITEM);
        boolean book = AltarLogic.book(s);
        for (int i = 0; i < AltarLogic.SLOTS; i++) {
            List<EnchantmentData> roll = rolled(i);
            v[LEVEL + i] = AltarLogic.price(roll, book, i);
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
        int cost = AltarLogic.price(list, AltarLogic.book(s), slot);
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
            p.addExperienceLevel(-cost);
        }
        AltarLogic.apply(s, list);
        work.setInventorySlotContents(ITEM, s);
        stale = true;
        world.playSoundEffect(cx, cy, cz, "mob.endermen.portal", 0.7F, 0.6F);
        detectAndSendChanges();
        return true;
    }

    /** Shift-click: payment - into the payment cell, the rest - into the item slot (one piece); from the altar - into the inventory. */
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
