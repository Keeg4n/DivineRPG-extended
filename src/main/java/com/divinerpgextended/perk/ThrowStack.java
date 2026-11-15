package com.divinerpgextended.perk;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Divine throwable weapons (shurikens, Sneaky Hurricane, twilight slicers) accumulate in a single item up to {@link #MAX}: in
 * 1.7.10 a stack cannot exceed 64, and you need a lot of shurikens. A gathered stack is one item with the number {@link #COUNT} in NBT
 * (two or more); there are no items inside, nothing to dupe.
 * <ul>
 * <li>sneak and right-click - gather into the item in hand all identical ones from the inventory ({@link #gather});</li>
 * <li>a throw spends one of the number ({@link #consume}, called from Divine's throws via
 * {@link com.divinerpgextended.asm.ThrowableTransformer});</li>
 * <li>identical ones picked up from the ground add themselves to the gathered stack ({@link #absorb});</li>
 * <li>a gathered stack does not merge with others (stack limit 1), the number is on the icon.</li>
 * </ul>
 */
public final class ThrowStack {

    public static final String COUNT = "dreThrown";
    public static final int MAX = 256;

    private ThrowStack() {}

    public static boolean is(Item i) {
        return i instanceof net.divinerpg.items.base.ItemThrowable || i instanceof net.divinerpg.items.vanilla.ItemVileStorm;
    }

    public static boolean is(ItemStack s) {
        return s != null && is(s.getItem());
    }

    /** Number of the gathered stack (0 - an ordinary stack). */
    public static int count(ItemStack s) {
        return s != null && s.hasTagCompound() && s.getTagCompound().hasKey(COUNT) ? Math.max(0, s.getTagCompound().getInteger(COUNT)) : 0;
    }

    /** How many pieces in total are in this stack. */
    public static int total(ItemStack s) {
        if (s == null) return 0;
        int c = count(s);
        return c > 0 ? c : s.stackSize;
    }

    /** Write the number: two or more - a gathered stack (one item), one - an ordinary item, zero - empty. */
    public static void setTotal(ItemStack s, int n) {
        if (n >= 2) {
            if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
            s.getTagCompound().setInteger(COUNT, n);
            s.stackSize = 1;
            return;
        }
        if (s.hasTagCompound()) {
            s.getTagCompound().removeTag(COUNT);
            if (s.getTagCompound().hasNoTags()) s.setTagCompound(null);
        }
        s.stackSize = Math.max(0, n);
    }

    /** Remove n pieces: from the number of a gathered stack or from an ordinary stack (an ordinary one does not become gathered). */
    static void take(ItemStack s, int n) {
        if (count(s) > 0) setTotal(s, count(s) - n);
        else s.stackSize -= n;
    }

    /** A throw: one piece from the number (gathered stack) or from the stack, as in Divine. */
    public static void consume(ItemStack s) {
        if (s == null) return;
        int c = count(s);
        if (c > 0) setTotal(s, c - 1);
        else s.stackSize--;
    }

    /** Stack limit: gathered - 1 (does not merge with others), ordinary - as the item has. */
    public static int limit(Item item, ItemStack s) {
        return count(s) > 0 ? 1 : item.getItemStackLimit();
    }

    /** Fits for gathering into held: the same item and subtype, without its own data (except the number). */
    static boolean same(ItemStack held, ItemStack s) {
        if (s == null || s == held || s.getItem() != held.getItem() || s.getItemDamage() != held.getItemDamage() || s.stackSize <= 0) return false;
        if (!s.hasTagCompound()) return true;
        NBTTagCompound t = (NBTTagCompound) s.getTagCompound().copy();
        t.removeTag(COUNT);
        return t.hasNoTags();
    }

    /** How much can be gathered into the item in hand (0 - nothing to gather or nowhere to put it). */
    public static int gatherable(EntityPlayer p) {
        ItemStack held = p.getHeldItem();
        if (!is(held)) return 0;
        int room = MAX - total(held), n = 0;
        for (ItemStack s : p.inventory.mainInventory) if (same(held, s)) n += total(s);
        return Math.max(0, Math.min(room, n));
    }

    /** Gather identical ones from the inventory into the item in hand while it fits. Server only. @return how much went in */
    public static int gather(EntityPlayer p) {
        if (p.worldObj.isRemote) return 0;
        ItemStack held = p.getHeldItem();
        if (!is(held)) return 0;
        int have = total(held), got = 0;
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length && have < MAX; i++) {
            if (i == p.inventory.currentItem || !same(held, inv[i])) continue;
            int n = Math.min(MAX - have, total(inv[i]));
            take(inv[i], n); // minus at the source first (an ordinary stack stays ordinary)
            if (inv[i].stackSize <= 0) inv[i] = null;
            have += n; // then plus in the hand
            got += n;
        }
        if (got > 0) {
            setTotal(held, have);
            p.inventory.markDirty();
        }
        return got;
    }

    /** A stack picked up from the ground: into a gathered stack of the same kind, as much as fits. @return how much went in */
    public static int absorb(EntityPlayer p, EntityItem e) {
        ItemStack in = e.getEntityItem();
        if (!is(in) || count(in) > 0 || in.hasTagCompound()) return 0;
        int got = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            if (in.stackSize <= 0) break;
            if (s == null || count(s) <= 0 || s.getItem() != in.getItem() || s.getItemDamage() != in.getItemDamage()) continue;
            int n = Math.min(MAX - count(s), in.stackSize);
            if (n <= 0) continue;
            in.stackSize -= n;
            setTotal(s, count(s) + n);
            got += n;
        }
        if (got > 0) {
            e.setEntityItemStack(in);
            p.inventory.markDirty();
        }
        return got;
    }

    /** Return one piece into the item in hand (Thrower's Sling): into a gathered or an ordinary stack, otherwise into the inventory. */
    public static void giveBack(EntityPlayer p, ItemStack held) {
        int c = count(held);
        if (c > 0 && c < MAX) setTotal(held, c + 1);
        else if (c == 0 && held.stackSize > 0 && held.stackSize < held.getMaxStackSize()) held.stackSize++;
        else {
            ItemStack one = new ItemStack(held.getItem(), 1, held.getItemDamage());
            if (!p.inventory.addItemStackToInventory(one)) p.dropPlayerItemWithRandomChoice(one, false);
        }
    }

    /** Number on the icon of a gathered stack (null - an ordinary one). */
    public static String label(ItemStack s) {
        int c = count(s);
        return c > 0 ? String.valueOf(c) : null;
    }
}
