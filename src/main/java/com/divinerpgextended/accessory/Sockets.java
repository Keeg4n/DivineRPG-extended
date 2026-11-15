package com.divinerpgextended.accessory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.divinerpgextended.Config;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossProgress;
import com.divinerpgextended.item.ItemWandererArmor;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.trinket.ItemTrinket;
import com.divinerpgextended.trinket.Trinket;

/**
 * Accessory sockets in armor. Only the Wanderer Anvil ({@link com.divinerpgextended.station.AnvilLogic}) inserts and removes them:
 * there is no inventory or window inside the armor, only an entry in its NBT, so there is nothing to dupe with - insertion works like a craft.
 * <ul>
 * <li>Divine armor - one socket per part; Wanderer armor - one socket, and a second one for the part's boss ({@link #SECOND});</li>
 * <li>an accessory fits only its own part ({@link Trinket#armorParts}), one effect per part - only once;</li>
 * <li>Thaumcraft Goggles of Revealing ({@link Goggles}) - only in the helmet and not instead of an accessory: the Divine helmet has a separate second socket for them,
 * in the Wanderer helmet they go into the second socket at once, without waiting for the boss;</li>
 * <li>works while the part is worn; death and armor drops do not touch the accessories - they are inside.</li>
 * </ul>
 */
public final class Sockets {

    public static final String TAG = "dreSockets";
    /** Bosses that open the second socket in a Wanderer armor part: [helmet, chestplate, leggings, boots]. */
    public static final Boss[] SECOND = { Boss.KAROT, Boss.LICH_KING, Boss.WATCHER, Boss.TWILIGHT_DEMON };
    private static final Trinket[] ALL = Trinket.values();

    /** Why an accessory does not fit into an armor part. */
    public enum Fit {
        OK, PART, FULL, SAME, BOSS
    }

    private Sockets() {}

    /** Armor with sockets: Wanderer and any Divine (vanilla and other mods' armor have no sockets). */
    public static boolean socketable(ItemStack s) {
        if (s == null || !(s.getItem() instanceof ItemArmor)) return false;
        int t = ((ItemArmor) s.getItem()).armorType;
        return t >= 0 && t <= 3 && (s.getItem() instanceof ItemWandererArmor || s.getItem() instanceof net.divinerpg.items.base.ItemDivineArmor);
    }

    public static int type(ItemStack armor) {
        return ((ItemArmor) armor.getItem()).armorType;
    }

    /** How many sockets a part can have at all, with room for the goggles (for the window and the hint). */
    public static int maxCapacity(ItemStack armor) {
        if (armor.getItem() instanceof ItemWandererArmor) return 2;
        return type(armor) == 0 && Goggles.available() ? 2 : 1;
    }

    /** How many accessory sockets are open for this player: the second one in Wanderer armor is for the part's boss. */
    public static int capacity(ItemStack armor, EntityPlayer p) {
        if (!(armor.getItem() instanceof ItemWandererArmor)) return 1;
        return secondOpen(p, type(armor)) ? 2 : 1;
    }

    public static boolean secondOpen(EntityPlayer p, int type) {
        return !Config.requireBossUnlocks || BossProgress.has(p, SECOND[type]);
    }

    /** An addon accessory, a Divine item worn as an accessory, or Goggles of Revealing. */
    public static boolean accessory(ItemStack s) {
        return s != null && (s.getItem() == ModItems.trinket || Accessories.divineAccessory(s) || Goggles.is(s));
    }

    /** Which parts it is built into (bits {@link Trinket#HEAD}..{@link Trinket#FEET}). */
    public static int parts(ItemStack acc) {
        if (acc.getItem() == ModItems.trinket) return ItemTrinket.of(acc).armorParts();
        if (acc.getItem() == net.divinerpg.utils.items.VetheaItems.minersAmulet) return Trinket.HEAD;
        if (acc.getItem() == net.divinerpg.utils.items.VetheaItems.heivaBelt) return Trinket.LEGS;
        if (Goggles.is(acc)) return Trinket.HEAD;
        return 0;
    }

    /** Cost of insertion, in levels. */
    public static int levels(ItemStack acc) {
        if (acc.getItem() == ModItems.trinket) return ItemTrinket.of(acc).levels();
        return acc.getItem() == net.divinerpg.utils.items.VetheaItems.heivaBelt ? 15 : 10;
    }

    /** What is inside (copies - change via {@link #insert} and {@link #extract}). */
    public static List<ItemStack> contents(ItemStack armor) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        NBTTagList l = list(armor);
        for (int i = 0; l != null && i < l.tagCount(); i++) {
            ItemStack s = ItemStack.loadItemStackFromNBT(l.getCompoundTagAt(i));
            if (s != null) out.add(s);
        }
        return out;
    }

    public static int count(ItemStack armor) {
        NBTTagList l = list(armor);
        return l == null ? 0 : l.tagCount();
    }

    private static NBTTagList list(ItemStack armor) {
        if (armor == null || !armor.hasTagCompound() || !armor.getTagCompound().hasKey(TAG)) return null;
        return armor.getTagCompound().getTagList(TAG, 10);
    }

    /** Whether the accessory will fit into this part (together with the same ones already planned for it). */
    public static Fit fit(ItemStack armor, ItemStack acc, EntityPlayer p, List<ItemStack> planned) {
        if (!socketable(armor) || !accessory(acc) || (parts(acc) & 1 << type(armor)) == 0) return Fit.PART;
        List<ItemStack> in = contents(armor);
        if (planned != null) in.addAll(planned);
        boolean goggles = false;
        for (ItemStack s : in) {
            if (overlap(s, acc)) return Fit.SAME;
            if (Goggles.is(s)) goggles = true;
        }
        if (Goggles.is(acc)) return in.size() < maxCapacity(armor) ? Fit.OK : Fit.FULL;
        // goggles take a place in the window, but do not close the accessory's socket
        int trinkets = in.size() - (goggles ? 1 : 0), rows = maxCapacity(armor) - (goggles ? 1 : 0);
        if (trinkets < Math.min(capacity(armor, p), rows)) return Fit.OK;
        return armor.getItem() instanceof ItemWandererArmor && trinkets < rows ? Fit.BOSS : Fit.FULL;
    }

    /** Same effect: the same Divine item, or accessories with a shared effect (the combined one and its part). */
    static boolean overlap(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) return false;
        if (a.getItem() != ModItems.trinket) return true;
        for (Trinket t : ItemTrinket.of(a).provides()) if (ItemTrinket.of(b).provides().contains(t)) return true;
        return false;
    }

    /** Write an accessory into a part (check - {@link #fit}). Called only by the anvil on the server. */
    public static void insert(ItemStack armor, ItemStack acc) {
        ItemStack one = acc.copy();
        one.stackSize = 1;
        if (!armor.hasTagCompound()) armor.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = armor.getTagCompound();
        NBTTagList l = tag.hasKey(TAG) ? tag.getTagList(TAG, 10) : new NBTTagList();
        l.appendTag(one.writeToNBT(new NBTTagCompound()));
        tag.setTag(TAG, l);
    }

    /** Take an accessory out of a socket (null - empty). The entry is erased before the item is handed out. */
    public static ItemStack extract(ItemStack armor, int index) {
        NBTTagList l = list(armor);
        if (l == null || index < 0 || index >= l.tagCount()) return null;
        NBTTagCompound c = l.getCompoundTagAt(index);
        l.removeTag(index);
        if (l.tagCount() == 0) armor.getTagCompound().removeTag(TAG);
        return ItemStack.loadItemStackFromNBT(c);
    }

    /** Take everything out (the armor goes into a craft or to smelting). */
    public static List<ItemStack> extractAll(ItemStack armor) {
        List<ItemStack> out = contents(armor);
        if (armor != null && armor.hasTagCompound()) armor.getTagCompound().removeTag(TAG);
        return out;
    }

    /**
     * Whether an accessory with this effect is built into the worn armor. Asked on every hit and tick - we read NBT without
     * creating items.
     */
    public static boolean wears(EntityPlayer p, Trinket t) {
        for (ItemStack armor : p.inventory.armorInventory) {
            if (foreign(armor, p)) continue;
            NBTTagList l = list(armor);
            for (int i = 0; l != null && i < l.tagCount(); i++) {
                NBTTagCompound c = l.getCompoundTagAt(i);
                if (Item.getItemById(c.getShort("id")) != ModItems.trinket) continue;
                int m = c.getShort("Damage");
                if (m >= 0 && m < ALL.length && ALL[m].provides().contains(t)) return true;
            }
        }
        return false;
    }

    /** Whether the item is built into this armor part (without creating items). */
    static boolean has(ItemStack armor, Item item) {
        NBTTagList l = list(armor);
        for (int i = 0; l != null && i < l.tagCount(); i++) if (Item.getItemById(l.getCompoundTagAt(i).getShort("id")) == item) return true;
        return false;
    }

    /** Another player's Wanderer armor: its sockets are silent on this player. */
    private static boolean foreign(ItemStack armor, EntityPlayer p) {
        return armor != null && armor.getItem() instanceof ItemWandererArmor && !com.divinerpgextended.item.ArmorData.owns(armor, p);
    }

    /** Whether the item (a Divine accessory) is built into the worn armor. */
    public static boolean wearsItem(EntityPlayer p, Item item) {
        for (ItemStack armor : p.inventory.armorInventory) {
            if (foreign(armor, p)) continue;
            NBTTagList l = list(armor);
            for (int i = 0; l != null && i < l.tagCount(); i++) if (Item.getItemById(l.getCompoundTagAt(i).getShort("id")) == item) return true;
        }
        return false;
    }
}
