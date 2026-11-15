package com.divinerpgextended.perk;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Loaded ammo inside a weapon: each shooter has one kind of ammunition ({@link #of}), so what is stored inside is not an inventory but a single
 * number in NBT ({@link #LOADED}). No window and no items inside - nothing to dupe.
 * <ul>
 * <li>to load - sneak and right-click with the weapon ({@link #load}): everything suitable from the inventory, up to {@link #STACKS} stacks;</li>
 * <li>a shot takes the loaded ammo first, then the inventory: the weapon asks the inventory itself, and
 * {@link com.divinerpgextended.asm.AmmoTransformer} replaces that query with {@link com.divinerpgextended.asm.AmmoHooks};</li>
 * <li>to unload - at the Wanderer Anvil ({@link #unload}); if the weapon breaks, the loaded ammo spills to the player ({@link #spill}).</li>
 * </ul>
 * Every transfer is minus at the source first, then plus at the target, in one call on the server.
 */
public final class Ammo {

    public static final String LOADED = "dreLoaded";
    /** Capacity: this many stacks of ammunition of its kind. */
    public static final int STACKS = 4;
    /** Tag of a cartridge borrowed for a shot before 0.99.67 (left over after a failure - we remove it). */
    public static final String TAG = "dreAmmo";
    static final String STASH = "dreAmmoStash";

    private static Field bowArrow, shooterAmmo;

    private Ammo() {}

    /** Which ammunition this weapon uses (null - none or unknown). */
    public static Item of(ItemStack held) {
        if (held == null) return null;
        Item i = held.getItem();
        Item dust = RangedRoles.dust(i);
        if (dust != null) return dust; // phaser and blitz - dust of their own world
        if (i instanceof com.divinerpgextended.item.ItemStormBow) return com.divinerpgextended.item.ItemStormBow.ammo(); // Wildwood arrows
        if (i instanceof net.divinerpg.items.base.ItemModBow) return value(bowArrow(), i);
        if (i instanceof ItemBow) return Items.arrow;
        if (i instanceof net.divinerpg.items.base.ItemProjectileShooter) return value(shooterAmmo(), i);
        String cn = i.getClass().getName();
        if (cn.endsWith(".ItemLaVekor")) return net.divinerpg.utils.items.ArcanaItems.grenade;
        if (cn.endsWith(".ItemCorruptedCannon")) return net.divinerpg.utils.items.VanillaItemsOther.corruptedBullet;
        return null;
    }

    /** What a Divine shooter uses by its design (blitz - dust), without our fixes. */
    public static Item divineAmmo(ItemStack held) {
        if (held == null || !(held.getItem() instanceof net.divinerpg.items.base.ItemProjectileShooter)) return null;
        return value(shooterAmmo(), held.getItem());
    }

    private static Field bowArrow() {
        if (bowArrow == null) bowArrow = field(net.divinerpg.items.base.ItemModBow.class, "arrow");
        return bowArrow;
    }

    private static Field shooterAmmo() {
        if (shooterAmmo == null) shooterAmmo = field(net.divinerpg.items.base.ItemProjectileShooter.class, "ammo");
        return shooterAmmo;
    }

    private static Field field(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception ex) {
            return null;
        }
    }

    private static Item value(Field f, Object o) {
        try {
            Object v = f == null ? null : f.get(o);
            return v instanceof Item ? (Item) v : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /** Infinity on a weapon: no ammo needed or spent (bows, cannons, staffs, phasers - any ranged weapon). */
    public static boolean infinity(ItemStack w) {
        return w != null && w.isItemEnchanted() && net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.enchantment.Enchantment.infinity.effectId, w) > 0;
    }

    public static int loaded(ItemStack w) {
        return w != null && w.hasTagCompound() ? Math.max(0, w.getTagCompound().getInteger(LOADED)) : 0;
    }

    /** Write the loaded amount (server: loading, shot, unloading; the selftest sets it directly). */
    public static void setLoaded(ItemStack w, int n) {
        if (n > 0) {
            if (!w.hasTagCompound()) w.setTagCompound(new NBTTagCompound());
            w.getTagCompound().setInteger(LOADED, n);
        } else if (w.hasTagCompound()) {
            w.getTagCompound().removeTag(LOADED);
            w.getTagCompound().removeTag(STORM_AMMO);
            if (w.getTagCompound().hasNoTags()) w.setTagCompound(null);
        }
    }

    /** How much fits in the weapon: {@link #STACKS} stacks of its ammunition. */
    public static int capacity(ItemStack w) {
        Item a = of(w);
        return a == null ? 0 : STACKS * new ItemStack(a).getMaxStackSize();
    }

    /** Whether the weapon is loaded with this ammunition. */
    public static boolean has(ItemStack w, Item a) {
        return a != null && loaded(w) > 0 && of(w) == a;
    }

    /** A shot: one loaded round from the weapon for this cartridge. @return true - there was one */
    public static boolean take(ItemStack w, Item a) {
        if (!has(w, a)) return false;
        setLoaded(w, loaded(w) - 1);
        return true;
    }

    /** How many of these cartridges go into a shot with weapon w: blitz - dust for each pellet, the rest - one. */
    public static int perShot(ItemStack w, Item a) {
        return w != null && a != null && RangedRoles.kind(w) == RangedRoles.BLITZ && RangedRoles.dust(w.getItem()) == a ? Math.max(1, com.divinerpgextended.Config.blitzDustPerShot) : 1;
    }

    /** Supply for shots: the loaded ammo of weapon w for this cartridge plus all such cartridges in the inventory (as inventory.hasItem sees them). */
    public static int stock(net.minecraft.entity.player.InventoryPlayer inv, ItemStack w, Item a) {
        if (a == null) return 0;
        int n = of(w) == a ? loaded(w) : 0;
        for (ItemStack s : inv.mainInventory) if (s != null && s.getItem() == a) n += s.stackSize;
        return n;
    }

    /** Whether weapon w has infinite ammo for this player (on the client - by flags from the server). */
    public static boolean endless(EntityPlayer p, ItemStack w) {
        if (RangedRoles.dust(w.getItem()) != null) return RangedRoles.infiniteDust(p, w);
        if (infinity(w)) return true; // Infinity on any ranged weapon - no ammo
        return (w.getItem() instanceof ItemBow || w.getItem() instanceof net.divinerpg.items.base.ItemModBow) && RangedRoles.quiverOn(p); // quiver: a bow without arrows
    }

    /**
     * The number on the icon of a ranged weapon from the player's inventory: ammo supply (loaded + inventory), infinite - "∞", less
     * than one shot - red, thousands are abbreviated ("2k"). null - not a ranged weapon or not from this player's inventory.
     */
    public static String label(EntityPlayer p, ItemStack w) {
        String thrown = ThrowStack.label(w); // a gathered stack of throwables - always a number (it is its size)
        if (thrown != null) return thrown;
        if (p == null || w == null || !com.divinerpgextended.Config.ammoCountOnIcon) return null;
        Item a = of(w);
        if (a == null) return null;
        boolean own = false;
        for (ItemStack s : p.inventory.mainInventory) if (s == w) own = true;
        if (!own) return null; // NEI, chests, other hands - no number
        if (endless(p, w)) return "∞";
        int n = stock(p.inventory, w, a);
        if (n < perShot(w, a)) return "§c" + n; // not enough for a shot (blitz needs 5)
        return n < 1000 ? String.valueOf(n) : (n / 1000) + "k";
    }

    /**
     * Fits as loaded ammo: the same item without its own data and subtype (otherwise they would be lost when turned into a number). An empty tag is
     * not data: it is left by servers with Bukkit (Crucible) and some mods, and that does not make an arrow special.
     */
    static boolean plain(ItemStack s, Item a) {
        return s != null && s.getItem() == a && s.stackSize > 0 && s.getItemDamage() == 0 && (!s.hasTagCompound() || s.getTagCompound().hasNoTags());
    }

    /** A line for the log: what is in hand, what it is loaded with, how much is in the weapon and in the inventory (suitable and rejected - with data or subtype). */
    public static String describe(EntityPlayer p) {
        ItemStack w = p.getHeldItem();
        Item a = of(w);
        if (a == null) return "in hand " + (w == null ? "empty" : Item.itemRegistry.getNameForObject(w.getItem())) + " - not a ranged weapon";
        int good = 0, bad = 0;
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            if (i == p.inventory.currentItem || inv[i] == null || inv[i].getItem() != a) continue;
            if (plain(inv[i], a)) good += inv[i].stackSize;
            else bad += inv[i].stackSize;
        }
        return Item.itemRegistry.getNameForObject(w.getItem()) + ", ammo " + Item.itemRegistry.getNameForObject(a) + ", loaded in weapon " + loaded(w) + "/"
            + capacity(w) + ", suitable in inventory " + good + ", with data or subtype " + bad;
    }

    /** How many cartridges from the inventory can be loaded into the weapon in hand (0 - nothing to load or nowhere to put it). */
    public static int loadable(EntityPlayer p) {
        ItemStack w = p.getHeldItem();
        Item a = of(w);
        if (a == null) return 0;
        int room = capacity(w) - loaded(w), n = 0;
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length && n < room; i++) if (i != p.inventory.currentItem && plain(inv[i], a)) n += inv[i].stackSize;
        return Math.min(n, Math.max(0, room));
    }

    /** Load the weapon in hand from the inventory - everything suitable while it fits. Server only. @return how much went in */
    public static int load(EntityPlayer p) {
        if (p.worldObj.isRemote) return 0;
        ItemStack w = p.getHeldItem();
        Item a = of(w);
        if (a == null) return 0;
        int got = 0;
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            int room = capacity(w) - loaded(w);
            if (room <= 0) break;
            if (i == p.inventory.currentItem || !plain(inv[i], a)) continue;
            int n = Math.min(room, inv[i].stackSize);
            inv[i].stackSize -= n; // minus in the inventory first
            if (inv[i].stackSize <= 0) inv[i] = null;
            setLoaded(w, loaded(w) + n); // then plus in the weapon
            got += n;
        }
        if (got > 0) {
            if (w.getItem() instanceof com.divinerpgextended.item.ItemStormBow) w.getTagCompound().setBoolean(STORM_AMMO, true);
            p.inventory.markDirty();
        }
        return got;
    }

    /** How many of these cartridges still fit into the player's inventory. */
    public static int room(EntityPlayer p, Item a) {
        int max = new ItemStack(a).getMaxStackSize(), n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            if (s == null) n += max;
            else if (plain(s, a)) n += Math.max(0, max - s.stackSize);
        }
        return n;
    }

    /** Put exactly count into the inventory (room was checked by {@link #room}): onto identical stacks first, then into empty slots. */
    private static void put(EntityPlayer p, Item a, int count) {
        int max = new ItemStack(a).getMaxStackSize();
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length && count > 0; i++) {
            if (!plain(inv[i], a)) continue;
            int n = Math.min(count, max - inv[i].stackSize);
            if (n <= 0) continue;
            inv[i].stackSize += n;
            count -= n;
        }
        for (int i = 0; i < inv.length && count > 0; i++) {
            if (inv[i] != null) continue;
            int n = Math.min(count, max);
            inv[i] = new ItemStack(a, n);
            count -= n;
        }
        p.inventory.markDirty();
    }

    /** Unload into the player's inventory as much as fits; the rest stays in the weapon. @return how much came out */
    public static int unload(EntityPlayer p, ItemStack w) {
        Item a = of(w);
        if (a == null || p.worldObj.isRemote) return 0;
        int n = Math.min(loaded(w), room(p, a));
        if (n <= 0) return 0;
        setLoaded(w, loaded(w) - n); // minus in the weapon first
        put(p, a, n); // then plus in the inventory
        return n;
    }

    /** The weapon broke: the loaded ammo goes to the player's inventory, whatever does not fit - under their feet. */
    public static void spill(EntityPlayer p, ItemStack w) {
        Item a = of(w);
        int n = loaded(w);
        if (a == null || n <= 0) return;
        setLoaded(w, 0);
        int in = Math.min(n, room(p, a));
        if (in > 0) put(p, a, in);
        int max = new ItemStack(a).getMaxStackSize();
        for (int rest = n - in; rest > 0; rest -= max) p.dropPlayerItemWithRandomChoice(new ItemStack(a, Math.min(rest, max)), false);
    }

    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    /** Return an item set aside for a shot by version 0.86 (if one is left in the player's data): to its slot, to the inventory or under their feet. */
    public static void unstash(EntityPlayer p) {
        NBTTagCompound pd = persisted(p);
        if (!pd.hasKey(STASH)) return;
        NBTTagCompound st = pd.getCompoundTag(STASH);
        pd.removeTag(STASH);
        ItemStack orig = ItemStack.loadItemStackFromNBT(st);
        if (orig == null) return;
        int i = st.getInteger("slot");
        ItemStack[] inv = p.inventory.mainInventory;
        if (i >= 0 && i < inv.length && inv[i] == null) inv[i] = orig;
        else if (!p.inventory.addItemStackToInventory(orig) || orig.stackSize > 0) p.entityDropItem(orig, 0.5F);
    }

    /** Tag for the Storm Bow loaded with Wildwood arrows (before 0.99.133 it was loaded with ordinary arrows). */
    public static final String STORM_AMMO = "dreStormAmmo";

    /**
     * A Storm Bow loaded before 0.99.133: it holds ordinary arrows inside - return them to the player (to the inventory, whatever does not fit - under their feet)
     * so they do not turn into Wildwood arrows. Server only.
     */
    static void refundOldStorm(EntityPlayer p, ItemStack w) {
        if (w == null || !(w.getItem() instanceof com.divinerpgextended.item.ItemStormBow) || loaded(w) <= 0 || w.getTagCompound().getBoolean(STORM_AMMO)) return;
        int n = loaded(w);
        setLoaded(w, 0);
        Item a = Items.arrow;
        int in = Math.min(n, room(p, a));
        if (in > 0) put(p, a, in);
        int max = new ItemStack(a).getMaxStackSize();
        for (int rest = n - in; rest > 0; rest -= max) p.dropPlayerItemWithRandomChoice(new ItemStack(a, Math.min(rest, max)), false);
    }

    /** End of tick: for a cartridge borrowed for a shot before 0.99.67 and left after a failure - remove the tag; the old 0.86 stash - return it. */
    public static void settle(EntityPlayer p) {
        if (!p.worldObj.isRemote) for (ItemStack s : p.inventory.mainInventory) refundOldStorm(p, s);
        for (ItemStack s : p.inventory.mainInventory) {
            if (s == null || !s.hasTagCompound() || !s.getTagCompound().getBoolean(TAG)) continue;
            s.getTagCompound().removeTag(TAG);
            if (s.getTagCompound().hasNoTags()) s.setTagCompound(null);
        }
        unstash(p);
    }
}
