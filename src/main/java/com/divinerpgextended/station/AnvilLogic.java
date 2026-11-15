package com.divinerpgextended.station;

import java.util.List;

import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.Config;
import com.divinerpgextended.accessory.Sockets;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossProgress;
import com.divinerpgextended.item.ArmorCore;
import com.divinerpgextended.item.ArmorData;
import com.divinerpgextended.item.ItemWandererArmor;
import com.divinerpgextended.perk.Ammo;
import com.divinerpgextended.perk.Perk;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.world.ArmorTier;

/**
 * Rules of the Wanderer Anvil. Slots: 0-3 armor, 4-7 donor (parts in any order), 8 fuel.
 * <ul>
 * <li>final overworld set (no donor) -> Wanderer armor R1 with this set's core ({@link ArmorCore}), fuel is the set's plate, no boss needed;</li>
 * <li>Wanderer set + final set in the donor + this set's plate in the fuel -> new core (the same set with other fuel is a regular perk, if any);</li>
 * <li>Wanderer set R(n) + armor set of the next world tier -> R(n+1) (tempering R2-R7);</li>
 * <li>Wanderer set R7 + Crown property set (Angelic, Vemos, Korma) + Spirit of the Universe -> R8 with this Crown (for Lady Luna); on R8 the same
 * set with arlemite changes the Crown ({@link com.divinerpgextended.item.ArmorCrown});</li>
 * <li>Wanderer set + perk donor set -> perk into a free slot;</li>
 * <li>any armor parts with sockets + accessories in the donor -> accessories are embedded ({@link Sockets}), the price is experience levels;
 * a click on an occupied socket - the accessory comes out into a free donor slot (free);</li>
 * <li>Wanderer parts, donor and fuel empty -> remove the binding (owner only; someone else's - {@link Status#FOREIGN});</li>
 * <li>loaded weapon in the fuel slot, the rest empty -> unload into the inventory ({@link Ammo#unload}).</li>
 * </ul>
 * Donor armor with accessories is not accepted: the upgrade would consume it - take them out first.
 * <p>
 * The window is split into tabs ({@link #UPGRADE}, {@link #SOCKETS}, {@link #UNLOAD}): each looks only at its own
 * slots. {@link #AUTO} - no tab, by contents (this is how the tests count).
 */
public final class AnvilLogic {

    public static final int ARMOR = 0, DONOR = 4, FUEL = 8, SIZE = 9;
    /** Window tabs; AUTO - by contents. */
    public static final int AUTO = -1, UPGRADE = 0, SOCKETS = 1, UNLOAD = 2, MODES = 3;

    public enum Status {
        EMPTY, NEED_ARMOR, BAD_ARMOR, NEED_DONOR, BAD_DONOR, PERK_PRESENT, NO_SLOT, NEED_BOSS, NEED_FUEL,
        READY_UPGRADE, READY_PERK, MAX_TIER,
        // accessories and loaded ammo (new ones - only at the end: the number goes to the client)
        READY_SOCKET, NEED_LEVELS, SOCKET_PART, SOCKET_FULL, SOCKET_SAME, SOCKETS, DONOR_SOCKETS, READY_UNLOAD, UNLOAD_EMPTY,
        UNLOAD_FULL,
        // empty tabs and armor without sockets
        EMPTY_SOCKETS, EMPTY_UNLOAD, NO_SOCKETS,
        // transition to Wanderer without a donor
        DONOR_UNNEEDED,
        // core change
        READY_CORE,
        // binding: remove from own armor; the armor is someone else's
        READY_UNBIND, FOREIGN,
        // the core is already that one, payment in stones for a core change, Wanderer donor is only R1
        CORE_SAME, CORE_FUEL, CORE_R1_ONLY,
        // Crown R8: tempering R7 -> R8, Crown change, same Crown, Crown set at a tier below R7
        READY_R8, READY_CROWN, CROWN_SAME, CROWN_R8_ONLY
    }

    public static final class Result {

        public final Status status;
        /**
         * NEED_BOSS - boss number, NEED_FUEL - how much is needed, READY_UPGRADE - target tier, READY_PERK and
         * PERK_PRESENT - perk number, READY_CORE and CORE_SAME - core number, CORE_FUEL - how many stones are needed.
         */
        public final int param;
        /** What fuel the current operation needs (or null). */
        public final Item fuel;
        /** Fuel subtype (-1 - any). */
        public int fuelMeta = -1;
        /** Embedding: where each donor's accessory goes - 3 bits each (armor slot + 1, 0 - nowhere). */
        public int targets;

        Result(Status status, int param, Item fuel) {
            this.status = status;
            this.param = param;
            this.fuel = fuel;
        }

        Result(Status status, int param) {
            this(status, param, null);
        }

        public boolean ready() {
            return status == Status.READY_UPGRADE || status == Status.READY_PERK || status == Status.READY_SOCKET || status == Status.READY_UNLOAD || status == Status.READY_CORE
                || status == Status.READY_UNBIND || status == Status.READY_R8 || status == Status.READY_CROWN;
        }

        Result meta(int m) {
            fuelMeta = m;
            return this;
        }
    }

    private AnvilLogic() {}

    /**
     * Armor parts from the four slots, arranged by type: [0] helmet, [1] chestplate, [2] leggings, [3] boots. The player
     * can place parts in any order. If two slots hold parts of the same type, the place stays empty and the
     * interface will ask for a full set.
     */
    private static Item[] items(IInventory inv, int from) {
        Item[] out = new Item[4];
        boolean[] dup = new boolean[4];
        for (int i = 0; i < 4; i++) {
            ItemStack s = inv.getStackInSlot(from + i);
            if (s == null || !(s.getItem() instanceof ItemArmor)) continue;
            int type = ((ItemArmor) s.getItem()).armorType;
            if (type < 0 || type > 3) continue;
            if (out[type] != null) dup[type] = true;
            out[type] = s.getItem();
        }
        for (int t = 0; t < 4; t++) if (dup[t]) out[t] = null;
        return out;
    }

    private static boolean same(Item[] a, Item[] b) {
        for (int i = 0; i < 4; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private static boolean anyNull(Item[] a) {
        for (Item i : a) if (i == null) return true;
        return false;
    }

    private static boolean allNull(Item[] a) {
        for (Item i : a) if (i != null) return false;
        return true;
    }

    private static boolean wanderers(Item[] a) {
        for (Item i : a) if (!(i instanceof ItemWandererArmor)) return false;
        return true;
    }

    private static int fuelCount(IInventory inv, Item fuel) {
        return fuelCount(inv, fuel, -1);
    }

    private static int fuelCount(IInventory inv, Item fuel, int meta) {
        ItemStack s = inv.getStackInSlot(FUEL);
        return s != null && s.getItem() == fuel && (meta < 0 || s.getItemDamage() == meta) ? s.stackSize : 0;
    }

    /** Payment for tempering to R8: Spirit of the Universe. */
    public static ItemStack r8Fuel() {
        return com.divinerpgextended.halite.Halite.spirit(com.divinerpgextended.halite.Halite.Spirit.UNIVERSE);
    }

    private static boolean bossMissing(EntityPlayer player, Boss boss) {
        return boss != null && Config.requireBossUnlocks && !BossProgress.has(player, boss);
    }

    /** Where each donor accessory goes: target[donor slot] - armor slot (-1 - empty); fail - why it is impossible. */
    static final class Plan {

        final int[] target = { -1, -1, -1, -1 };
        int levels;
        Result fail;
    }

    /** Order of refusal reasons: we show the most specific one. */
    private static int rank(Sockets.Fit f) {
        return f == Sockets.Fit.SAME ? 3 : f == Sockets.Fit.BOSS ? 2 : f == Sockets.Fit.FULL ? 1 : 0;
    }

    @SuppressWarnings("unchecked")
    static Plan plan(IInventory inv, EntityPlayer player) {
        Plan pl = new Plan();
        List<ItemStack>[] planned = new List[4];
        for (int a = 0; a < 4; a++) planned[a] = new java.util.ArrayList<ItemStack>();
        for (int d = 0; d < 4; d++) {
            ItemStack acc = inv.getStackInSlot(DONOR + d);
            if (acc == null) continue;
            Sockets.Fit best = Sockets.Fit.PART;
            int bossType = -1;
            for (int a = 0; a < 4 && pl.target[d] < 0; a++) {
                ItemStack armor = inv.getStackInSlot(ARMOR + a);
                Sockets.Fit f = Sockets.fit(armor, acc, player, planned[a]);
                if (f == Sockets.Fit.OK) {
                    pl.target[d] = a;
                    planned[a].add(acc);
                    pl.levels += Sockets.levels(acc);
                } else if (rank(f) > rank(best)) {
                    best = f;
                    if (f == Sockets.Fit.BOSS) bossType = Sockets.type(armor);
                }
            }
            if (pl.target[d] >= 0) continue;
            if (best == Sockets.Fit.BOSS) pl.fail = new Result(Status.NEED_BOSS, Sockets.SECOND[bossType].ordinal());
            else pl.fail = new Result(best == Sockets.Fit.SAME ? Status.SOCKET_SAME : best == Sockets.Fit.FULL ? Status.SOCKET_FULL : Status.SOCKET_PART, d);
            return pl;
        }
        return pl;
    }

    /** Sockets tab: armor in the armor slots, accessories in the donors. */
    private static Result sockets(IInventory inv, EntityPlayer player) {
        boolean acc = false;
        for (int d = 0; d < 4; d++) {
            ItemStack s = inv.getStackInSlot(DONOR + d);
            if (s == null) continue;
            if (!Sockets.accessory(s)) return new Result(Status.BAD_DONOR, 0);
            acc = true;
        }
        boolean any = false, socketable = false;
        for (int a = 0; a < 4; a++) {
            ItemStack s = inv.getStackInSlot(ARMOR + a);
            if (s == null) continue;
            any = true;
            if (Sockets.socketable(s)) socketable = true;
        }
        if (!acc) return new Result(!any ? Status.EMPTY_SOCKETS : socketable ? Status.SOCKETS : Status.NO_SOCKETS, 0);
        Plan pl = plan(inv, player);
        if (pl.fail != null) {
            // the needed part is there but without sockets (vanilla or foreign) - say so, rather than "place a part"
            if (pl.fail.status == Status.SOCKET_PART) {
                int parts = Sockets.parts(inv.getStackInSlot(DONOR + pl.fail.param));
                for (int a = 0; a < 4; a++) {
                    ItemStack s = inv.getStackInSlot(ARMOR + a);
                    if (s != null && !Sockets.socketable(s) && s.getItem() instanceof ItemArmor && (parts & 1 << ((ItemArmor) s.getItem()).armorType) != 0)
                        return new Result(Status.NO_SOCKETS, 0);
                }
            }
            return pl.fail;
        }
        Result r = !player.capabilities.isCreativeMode && player.experienceLevel < pl.levels ? new Result(Status.NEED_LEVELS, pl.levels)
            : new Result(Status.READY_SOCKET, pl.levels);
        for (int d = 0; d < 4; d++) r.targets |= (pl.target[d] + 1) << d * 3;
        return r;
    }

    /** Unload tab: only a weapon in the fuel slot. */
    private static Result unload(IInventory inv, EntityPlayer player) {
        ItemStack w = inv.getStackInSlot(FUEL);
        if (w == null || Ammo.of(w) == null) return new Result(Status.EMPTY_UNLOAD, 0);
        int n = Ammo.loaded(w);
        if (n <= 0) return new Result(Status.UNLOAD_EMPTY, 0);
        int room = Ammo.room(player, Ammo.of(w));
        return room <= 0 ? new Result(Status.UNLOAD_FULL, 0) : new Result(Status.READY_UNLOAD, Math.min(n, room));
    }

    private static boolean emptySlots(IInventory inv, int from) {
        for (int i = 0; i < 4; i++) if (inv.getStackInSlot(from + i) != null) return false;
        return true;
    }

    /** Removing the binding: only Wanderer parts on top (at least one bound), donor and fuel empty. Otherwise null. */
    private static Result unbind(IInventory inv, EntityPlayer player) {
        if (!emptySlots(inv, DONOR) || inv.getStackInSlot(FUEL) != null) return null;
        int bound = 0, foreign = -1;
        for (int i = 0; i < 4; i++) {
            ItemStack s = inv.getStackInSlot(ARMOR + i);
            if (s == null) continue;
            if (!(s.getItem() instanceof ItemWandererArmor)) return null;
            if (ArmorData.owner(s) == null) continue;
            bound++;
            if (foreign < 0 && !ArmorData.owns(s, player)) foreign = i;
        }
        if (bound == 0) return null;
        return foreign >= 0 ? new Result(Status.FOREIGN, foreign) : new Result(Status.READY_UNBIND, bound);
    }

    public static Result evaluate(IInventory inv, EntityPlayer player) {
        return evaluate(inv, player, AUTO);
    }

    public static Result evaluate(IInventory inv, EntityPlayer player, int mode) {
        if (mode == SOCKETS) return sockets(inv, player);
        if (mode == UNLOAD) return unload(inv, player);
        // accessories in the donor - embedding into any armor parts with sockets
        boolean accDonor = false, armorDonor = false;
        for (int d = 0; d < 4; d++) {
            ItemStack s = inv.getStackInSlot(DONOR + d);
            if (s == null) continue;
            if (Sockets.accessory(s)) accDonor = true;
            else armorDonor = true;
        }
        if (accDonor) return mode == AUTO && !armorDonor ? sockets(inv, player) : new Result(Status.BAD_DONOR, 0);
        // a loaded weapon alone in the fuel slot - unload
        ItemStack fuelStack = inv.getStackInSlot(FUEL);
        if (mode == AUTO && fuelStack != null && Ammo.of(fuelStack) != null && emptySlots(inv, ARMOR) && emptySlots(inv, DONOR)) return unload(inv, player);
        // donor armor with accessories: the upgrade will consume it - take them out first
        for (int d = 0; d < 4; d++) if (Sockets.count(inv.getStackInSlot(DONOR + d)) > 0) return new Result(Status.DONOR_SOCKETS, d);

        Item[] armor = items(inv, ARMOR);
        Item[] donor = items(inv, DONOR);
        if (allNull(armor) && allNull(donor)) return new Result(Status.EMPTY, 0);
        Result bind = unbind(inv, player);
        if (bind != null) return bind;
        // only armor with sockets (not a set for upgrading) - hint about sockets
        ArmorCore forge = ArmorCore.ofSet(armor);
        boolean setFlow = forge != null || !anyNull(armor) && wanderers(armor);
        if (mode == AUTO && allNull(donor) && !setFlow) {
            for (int a = 0; a < 4; a++) if (Sockets.socketable(inv.getStackInSlot(ARMOR + a))) return new Result(Status.SOCKETS, 0);
        }

        // -> R1: the final set itself becomes Wanderer armor (it already equals tier R1 - no boss and donor needed)
        if (forge != null) {
            if (!emptySlots(inv, DONOR)) return new Result(Status.DONOR_UNNEEDED, 0);
            Item fuel = forge.fuel();
            int need = forge.fuelCount();
            if (fuelCount(inv, fuel) < need) return new Result(Status.NEED_FUEL, need, fuel);
            return new Result(Status.READY_UPGRADE, 1, fuel);
        }

        // Further - only a full identical Wanderer set
        if (anyNull(armor)) return new Result(Status.NEED_ARMOR, 0);
        for (Item a : armor) if (!(a instanceof ItemWandererArmor)) return new Result(Status.BAD_ARMOR, 0);
        ItemStack first = inv.getStackInSlot(ARMOR);
        int tier = ArmorData.tier(first);
        List<Perk> perks = ArmorData.perks(first);
        ArmorCore core = ArmorData.core(first);
        com.divinerpgextended.item.ArmorCrown crown = ArmorData.crown(first);
        for (int i = 1; i < 4; i++) {
            ItemStack s = inv.getStackInSlot(ARMOR + i);
            if (ArmorData.tier(s) != tier || ArmorData.core(s) != core || ArmorData.crown(s) != crown || !ArmorData.perks(s)
                .equals(perks)) return new Result(Status.BAD_ARMOR, 0);
        }
        if (anyNull(donor)) return new Result(Status.NEED_DONOR, 0);

        // Crown R8: Crown property set (Angelic, Vemos, Korma) - tempering R7 into R8 for Lady Luna and Spirit of the Universe, on R8 - Crown change
        com.divinerpgextended.item.ArmorCrown wantCrown = com.divinerpgextended.item.ArmorCrown.ofSet(donor);
        if (wantCrown != null) {
            if (tier == 7) {
                if (bossMissing(player, Boss.LADY_LUNA)) return new Result(Status.NEED_BOSS, Boss.LADY_LUNA.ordinal());
                ItemStack f = r8Fuel();
                int need = Config.upgradeR8Spirits;
                if (fuelCount(inv, f.getItem(), f.getItemDamage()) < need) return new Result(Status.NEED_FUEL, need, f.getItem()).meta(f.getItemDamage());
                return new Result(Status.READY_R8, wantCrown.ordinal(), f.getItem()).meta(f.getItemDamage());
            }
            if (tier >= 8) {
                if (wantCrown == crown) return new Result(Status.CROWN_SAME, wantCrown.ordinal());
                Item fuel = VanillaItemsOther.arlemiteIngot;
                int need = Config.perkFuelArlemiteIngots;
                if (fuelCount(inv, fuel) < need) return new Result(Status.NEED_FUEL, need, fuel);
                return new Result(Status.READY_CROWN, wantCrown.ordinal(), fuel);
            }
            return new Result(Status.CROWN_R8_ONLY, wantCrown.ordinal());
        }

        // World tempering: next world's armor set in the donor
        ArmorTier next = ArmorTier.next(tier);
        if (next != null && same(donor, next.donorSet())) {
            if (bossMissing(player, next.boss)) return new Result(Status.NEED_BOSS, next.boss.ordinal());
            Item fuel = next.fuel();
            int need = Config.upgradeTwilightFuelCount;
            if (fuelCount(inv, fuel) < need) return new Result(Status.NEED_FUEL, need, fuel);
            return new Result(Status.READY_UPGRADE, next.tier, fuel);
        }
        // Core change by a Wanderer R1 donor: the core transfers, the donor is consumed, no fuel needed - the core was already paid for at forging
        if (wanderers(donor)) {
            ArmorCore given = ArmorData.core(inv.getStackInSlot(DONOR));
            for (int i = 0; i < 4; i++) {
                ItemStack s = inv.getStackInSlot(DONOR + i);
                if (!ArmorData.owns(s, player)) return new Result(Status.FOREIGN, DONOR + i); // someone else's R1 cannot be burned for the core
                if (ArmorData.tier(s) != 1) return new Result(Status.CORE_R1_ONLY, 0);
                if (ArmorData.core(s) != given) return new Result(Status.BAD_DONOR, 0);
            }
            if (given == core) return new Result(Status.CORE_SAME, core.ordinal());
            return new Result(Status.READY_CORE, given.ordinal());
        }
        // Core change by a final set - for its plate. Arlemite in the fuel instead - a perk of the same set (Divine armor has no perk)
        ArmorCore wanted = ArmorCore.ofSet(donor);
        ItemStack fuelNow = inv.getStackInSlot(FUEL);
        if (wanted != null && (wanted == ArmorCore.DIVINE || fuelNow == null || fuelNow.getItem() != VanillaItemsOther.arlemiteIngot)) {
            if (wanted == core) return new Result(Status.CORE_SAME, core.ordinal());
            if (fuelCount(inv, wanted.fuel()) < wanted.fuelCount())
                return new Result(wanted == ArmorCore.DIVINE ? Status.NEED_FUEL : Status.CORE_FUEL, wanted.fuelCount(), wanted.fuel());
            return new Result(Status.READY_CORE, wanted.ordinal(), wanted.fuel());
        }
        // The donor is a set of another world: hint that the tier is wrong
        for (ArmorTier t : ArmorTier.values()) {
            if (same(donor, t.donorSet())) {
                return new Result(t.tier <= tier ? Status.MAX_TIER : Status.BAD_DONOR, t.tier);
            }
        }

        // Installing a perk
        Perk perk = Perk.byDonor(donor);
        if (perk == null) return new Result(Status.BAD_DONOR, 0);
        if (perks.contains(perk)) return new Result(Status.PERK_PRESENT, perk.ordinal());
        if (perks.size() >= Config.smallSlots(tier)) return new Result(Status.NO_SLOT, 0);
        if (bossMissing(player, perk.unlock)) return new Result(Status.NEED_BOSS, perk.unlock.ordinal());
        Item fuel = VanillaItemsOther.arlemiteIngot;
        int need = Config.perkFuelArlemiteIngots;
        if (fuelCount(inv, fuel) < need) return new Result(Status.NEED_FUEL, need, fuel);
        return new Result(Status.READY_PERK, perk.ordinal(), fuel);
    }

    /**
     * Take an accessory out of socket index of the part in armor slot armorSlot - into the first free donor slot (none - it is not
     * taken out). Free. Server only.
     */
    public static boolean extract(IInventory inv, EntityPlayer player, int armorSlot, int index) {
        if (armorSlot < 0 || armorSlot > 3) return false;
        ItemStack armor = inv.getStackInSlot(ARMOR + armorSlot);
        if (!Sockets.socketable(armor) || index < 0 || index >= Sockets.count(armor)) return false;
        for (int d = 0; d < 4; d++) {
            if (inv.getStackInSlot(DONOR + d) != null) continue;
            ItemStack acc = Sockets.extract(armor, index); // first from the armor
            if (acc == null) return false;
            inv.setInventorySlotContents(DONOR + d, acc); // then into the donor
            inv.markDirty();
            com.divinerpgextended.audit.Audit.log(player, "Sockets", "removed " + acc.getDisplayName() + " from " + armor.getDisplayName());
            return true;
        }
        return false;
    }

    /** Performs the upgrade if everything is ready. Called only on the server. */
    public static boolean execute(IInventory inv, EntityPlayer player) {
        return execute(inv, player, AUTO);
    }

    public static boolean execute(IInventory inv, EntityPlayer player, int mode) {
        Result r = evaluate(inv, player, mode);
        if (!r.ready()) return false;
        if (r.status == Status.READY_UNBIND) {
            for (int i = 0; i < 4; i++) {
                ItemStack s = inv.getStackInSlot(ARMOR + i);
                if (s == null || ArmorData.owner(s) == null) continue;
                ArmorData.unbind(s);
                com.divinerpgextended.audit.Audit.log(player, "Wanderer", "binding removed " + s.getDisplayName());
            }
            inv.markDirty();
            return true;
        }
        if (r.status == Status.READY_SOCKET) {
            Plan pl = plan(inv, player);
            for (int d = 0; d < 4; d++) {
                if (pl.target[d] < 0) continue;
                ItemStack armor = inv.getStackInSlot(ARMOR + pl.target[d]), acc = inv.getStackInSlot(DONOR + d);
                inv.setInventorySlotContents(DONOR + d, null); // first from the donor
                Sockets.insert(armor, acc); // then into the armor
                com.divinerpgextended.audit.Audit.log(player, "Sockets", "inserted " + acc.getDisplayName() + " into " + armor.getDisplayName());
            }
            if (!player.capabilities.isCreativeMode) player.addExperienceLevel(-pl.levels);
            inv.markDirty();
            return true;
        }
        if (r.status == Status.READY_UNLOAD) {
            ItemStack w = inv.getStackInSlot(FUEL);
            int n = Ammo.unload(player, w);
            com.divinerpgextended.audit.Audit.log(player, "Ammo", "unloaded " + n + " from " + w.getDisplayName());
            inv.markDirty();
            return n > 0;
        }
        if (r.status == Status.READY_UPGRADE && r.param == 1) {
            // accessories of the set parts move to Wanderer parts of the same type
            ArmorCore forged = ArmorCore.ofSet(items(inv, ARMOR));
            ItemStack[] old = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                ItemStack s = inv.getStackInSlot(ARMOR + i);
                if (s != null && s.getItem() instanceof ItemArmor) old[((ItemArmor) s.getItem()).armorType] = s;
            }
            for (int i = 0; i < 4; i++) {
                ItemStack s = new ItemStack(ModItems.wanderer[i]);
                ArmorData.setTier(s, 1);
                ArmorData.setCore(s, forged);
                if (Sockets.count(old[i]) > 0) s.getTagCompound().setTag(Sockets.TAG, old[i].getTagCompound().getTag(Sockets.TAG).copy());
                inv.setInventorySlotContents(ARMOR + i, s);
            }
            inv.decrStackSize(FUEL, forged.fuelCount());
        } else if (r.status == Status.READY_UPGRADE) {
            for (int i = 0; i < 4; i++) ArmorData.setTier(inv.getStackInSlot(ARMOR + i), r.param);
            inv.decrStackSize(FUEL, Config.upgradeTwilightFuelCount);
        } else if (r.status == Status.READY_R8) {
            com.divinerpgextended.item.ArmorCrown c = com.divinerpgextended.item.ArmorCrown.values()[r.param];
            for (int i = 0; i < 4; i++) {
                ArmorData.setTier(inv.getStackInSlot(ARMOR + i), 8);
                ArmorData.setCrown(inv.getStackInSlot(ARMOR + i), c);
            }
            inv.decrStackSize(FUEL, Config.upgradeR8Spirits);
        } else if (r.status == Status.READY_CROWN) {
            com.divinerpgextended.item.ArmorCrown c = com.divinerpgextended.item.ArmorCrown.values()[r.param];
            for (int i = 0; i < 4; i++) ArmorData.setCrown(inv.getStackInSlot(ARMOR + i), c);
            inv.decrStackSize(FUEL, Config.perkFuelArlemiteIngots);
        } else if (r.status == Status.READY_CORE) {
            ArmorCore wanted = ArmorCore.values()[r.param];
            for (int i = 0; i < 4; i++) ArmorData.setCore(inv.getStackInSlot(ARMOR + i), wanted);
            if (r.fuel != null) inv.decrStackSize(FUEL, wanted.fuelCount()); // Wanderer donor: no payment
        } else {
            Perk perk = Perk.values()[r.param];
            for (int i = 0; i < 4; i++) {
                ItemStack s = inv.getStackInSlot(ARMOR + i);
                List<Perk> perks = ArmorData.perks(s);
                perks.add(perk);
                ArmorData.setPerks(s, perks);
            }
            inv.decrStackSize(FUEL, Config.perkFuelArlemiteIngots);
        }
        for (int i = 0; i < 4; i++) inv.setInventorySlotContents(DONOR + i, null);
        inv.markDirty();
        return true;
    }
}
