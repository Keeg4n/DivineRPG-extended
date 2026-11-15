package com.divinerpgextended.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.divinerpgextended.perk.Perk;
import com.divinerpgextended.world.ArmorTier;

import net.minecraft.item.Item;

/** The tier, perks and core of Wanderer armor are stored in the NBT of each piece. */
public final class ArmorData {

    private static final String ROOT = "dre";

    private ArmorData() {}

    private static NBTTagCompound root(ItemStack stack, boolean create) {
        if (stack.getTagCompound() == null) {
            if (!create) return null;
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(ROOT)) {
            if (!create) return null;
            tag.setTag(ROOT, new NBTTagCompound());
        }
        return tag.getCompoundTag(ROOT);
    }

    public static int tier(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        return r == null ? 1 : Math.max(1, r.getInteger("tier"));
    }

    public static void setTier(ItemStack stack, int tier) {
        root(stack, true).setInteger("tier", tier);
    }

    /** The piece's core: old armor without a record is divine. */
    public static ArmorCore core(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        return r == null ? ArmorCore.DIVINE : ArmorCore.byName(r.getString("core"));
    }

    public static void setCore(ItemStack stack, ArmorCore core) {
        root(stack, true).setString("core", core.name());
    }

    /** R8 Crown or null (there is no Crown below R8). */
    public static ArmorCrown crown(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        return r == null ? null : ArmorCrown.byName(r.getString("crown"));
    }

    public static void setCrown(ItemStack stack, ArmorCrown crown) {
        root(stack, true).setString("crown", crown == null ? "" : crown.name());
    }

    /** Small perks. Crown properties ({@link Perk#crown}) set before 0.99.114 do not count: their slot is free. */
    public static List<Perk> perks(ItemStack stack) {
        List<Perk> out = new ArrayList<Perk>();
        NBTTagCompound r = root(stack, false);
        if (r == null) return out;
        NBTTagList list = r.getTagList("perks", 8);
        for (int i = 0; i < list.tagCount(); i++) {
            Perk p = Perk.byName(list.getStringTagAt(i));
            if (p != null && !p.crown()) out.add(p);
        }
        return out;
    }

    public static void setPerks(ItemStack stack, List<Perk> perks) {
        NBTTagList list = new NBTTagList();
        for (Perk p : perks) list.appendTag(new NBTTagString(p.name()));
        root(stack, true).setTag("perks", list);
    }

    /** Owner of the piece (UUID as a string) or null - nobody has worn it yet. */
    public static String owner(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        return r == null || !r.hasKey("owner") ? null : r.getString("owner");
    }

    public static String ownerName(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        return r == null ? null : r.getString("ownerName");
    }

    /** Bind the piece to a player: protection, perks, core and sockets then work only on them. */
    public static void bind(ItemStack stack, EntityPlayer p) {
        NBTTagCompound r = root(stack, true);
        r.setString("owner", p.getUniqueID().toString());
        r.setString("ownerName", p.getCommandSenderName());
    }

    public static void unbind(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        if (r == null) return;
        r.removeTag("owner");
        r.removeTag("ownerName");
    }

    /**
     * Whether the piece works on the player: own or nobody's (the first tick on them will bind a nobody's one). On the server the UUID is checked; on the client
     * the name too: a client not logged in to an account may have a UUID that does not match the server's, yet the tooltip and the armor bar need an answer.
     */
    public static boolean owns(ItemStack stack, EntityPlayer p) {
        String o = owner(stack);
        if (o == null) return true;
        if (p == null) return false;
        if (p instanceof com.divinerpgextended.dummy.DummyGhost) return true; // a copy on a training dummy measures protection, the binding does not count
        if (o.equals(p.getUniqueID().toString())) return true;
        return p.worldObj != null && p.worldObj.isRemote && p.getCommandSenderName().equals(ownerName(stack));
    }

    /** Dye color 0xRRGGBB or -1 - not dyed. Look only. */
    public static int color(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        return r == null || !r.hasKey("color") ? -1 : r.getInteger("color") & 0xFFFFFF;
    }

    public static void setColor(ItemStack stack, int color) {
        root(stack, true).setInteger("color", color & 0xFFFFFF);
    }

    public static void clearColor(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        if (r != null) r.removeTag("color");
    }

    /**
     * A full Wanderer set on the player.
     *
     * @return the minimum tier of the set or 0 if the set is not full or is someone else's ({@link #owns})
     */
    public static int equippedTier(EntityPlayer player) {
        int tier = Integer.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            ItemStack s = player.getCurrentArmor(i);
            if (s == null || !(s.getItem() instanceof ItemWandererArmor) || !owns(s, player)) return 0;
            tier = Math.min(tier, tier(s));
        }
        return tier;
    }

    /** The Crown of the worn R8 set (the same on all pieces) or null. */
    public static ArmorCrown equippedCrown(EntityPlayer player) {
        if (equippedTier(player) < 8) return null;
        ArmorCrown c = crown(player.getCurrentArmor(0));
        for (int i = 1; i < 4; i++) if (crown(player.getCurrentArmor(i)) != c) return null;
        return c;
    }

    /**
     * The player's armor tier for bosses: Wanderer set - its own tier; full Divine sets count as an equivalent
     * (realmite and elite realmite R0, final Overworld sets R1, Eden R2 ... Halite R7); everything else - R0.
     */
    public static int effectiveTier(EntityPlayer player) {
        int wanderer = equippedTier(player);
        if (wanderer > 0) return wanderer;
        // R1 - the final sets ({@link ArmorCore}), the Wanderer is forged from them; elite realmite is just an improved R0
        if (ArmorCore.wornSet(player) != null) return 1;
        int best = 0;
        for (ArmorTier t : ArmorTier.values()) if (wearsSet(player, t.donorSet())) best = Math.max(best, t.tier);
        return best;
    }

    /** set = [helmet, chestplate, leggings, boots]; player slots: 0 boots, 1 leggings, 2 chestplate, 3 helmet. */
    private static boolean wearsSet(EntityPlayer player, Item[] set) {
        for (int i = 0; i < 4; i++) {
            ItemStack s = player.getCurrentArmor(i);
            if (s == null || s.getItem() != set[3 - i]) return false;
        }
        return true;
    }

    /** A perk works only if it is on all four pieces of the worn set; the Crown property - if the Crown is the same on all R8 pieces. */
    public static boolean hasEquippedPerk(EntityPlayer player, Perk perk) {
        if (perk.crown()) return equippedCrown(player) == ArmorCrown.of(perk);
        if (equippedTier(player) == 0) return false;
        for (int i = 0; i < 4; i++) {
            if (!perks(player.getCurrentArmor(i)).contains(perk)) return false;
        }
        return true;
    }
}
