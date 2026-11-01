package com.divinerpgextended.station;

import java.util.List;

import net.divinerpg.utils.items.VanillaItemsArmor;
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
import com.divinerpgextended.item.ArmorData;
import com.divinerpgextended.item.ItemWandererArmor;
import com.divinerpgextended.perk.Ammo;
import com.divinerpgextended.perk.Perk;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.world.ArmorTier;

/**
 * Правила Наковальни Странника. Слоты: 0-3 броня, 4-7 донор (части в любом порядке), 8 топливо.
 * <ul>
 * <li>реалмит + сет элитного реалмита -> броня Странника R1;</li>
 * <li>сет Странника R(n) + сет брони мира следующей ступени -> R(n+1) (закалка R2-R7);</li>
 * <li>сет Странника + донорский сет перка -> перк в свободный слот;</li>
 * <li>любые части брони с гнёздами + аксессуары в доноре -> аксессуары встроены ({@link Sockets}), цена - уровни опыта;
 * клик по занятому гнезду - аксессуар выходит в свободный слот донора (бесплатно);</li>
 * <li>заряженное оружие в слоте топлива, остальное пусто -> разрядить в инвентарь ({@link Ammo#unload}).</li>
 * </ul>
 * Донорская броня с аксессуарами не принимается: её съедает улучшение - сначала вынуть.
 */
public final class AnvilLogic {

    public static final int ARMOR = 0, DONOR = 4, FUEL = 8, SIZE = 9;

    public enum Status {
        EMPTY, NEED_ARMOR, BAD_ARMOR, NEED_DONOR, BAD_DONOR, PERK_PRESENT, NO_SLOT, NEED_BOSS, NEED_FUEL,
        READY_UPGRADE, READY_PERK, MAX_TIER,
        // аксессуары и заряд (новые - только в конец: номер уходит клиенту)
        READY_SOCKET, NEED_LEVELS, SOCKET_PART, SOCKET_FULL, SOCKET_SAME, SOCKETS, DONOR_SOCKETS, READY_UNLOAD, UNLOAD_EMPTY,
        UNLOAD_FULL
    }

    public static final class Result {

        public final Status status;
        /**
         * NEED_BOSS - номер босса, NEED_FUEL - сколько нужно, READY_UPGRADE - целевая ступень, READY_PERK и
         * PERK_PRESENT - номер перка.
         */
        public final int param;
        /** Какое топливо нужно для текущей операции (или null). */
        public final Item fuel;

        Result(Status status, int param, Item fuel) {
            this.status = status;
            this.param = param;
            this.fuel = fuel;
        }

        Result(Status status, int param) {
            this(status, param, null);
        }

        public boolean ready() {
            return status == Status.READY_UPGRADE || status == Status.READY_PERK || status == Status.READY_SOCKET || status == Status.READY_UNLOAD;
        }
    }

    private static Item[] realmiteSet() {
        return new Item[] { VanillaItemsArmor.realmiteHelmet, VanillaItemsArmor.realmiteBody,
            VanillaItemsArmor.realmiteLegs, VanillaItemsArmor.realmiteBoots };
    }

    private static Item[] eliteRealmiteSet() {
        return new Item[] { VanillaItemsArmor.eliteRealmiteHelmet, VanillaItemsArmor.eliteRealmiteBody,
            VanillaItemsArmor.eliteRealmiteLegs, VanillaItemsArmor.eliteRealmiteBoots };
    }

    private AnvilLogic() {}

    /**
     * Части брони из четырёх слотов, разложенные по типу: [0] шлем, [1] нагрудник, [2] поножи, [3] ботинки. Игрок
     * может класть части в любом порядке. Если два слота заняты частями одного типа, место остаётся пустым и
     * интерфейс попросит полный сет.
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
        ItemStack s = inv.getStackInSlot(FUEL);
        return s != null && s.getItem() == fuel ? s.stackSize : 0;
    }

    private static boolean bossMissing(EntityPlayer player, Boss boss) {
        return boss != null && Config.requireBossUnlocks && !BossProgress.has(player, boss);
    }

    /** Куда встанет каждый аксессуар из донора: target[слот донора] - слот брони (-1 - пусто); fail - почему нельзя. */
    static final class Plan {

        final int[] target = { -1, -1, -1, -1 };
        int levels;
        Result fail;
    }

    /** Порядок причин отказа: показываем самую конкретную. */
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

    private static boolean emptySlots(IInventory inv, int from) {
        for (int i = 0; i < 4; i++) if (inv.getStackInSlot(from + i) != null) return false;
        return true;
    }

    public static Result evaluate(IInventory inv, EntityPlayer player) {
        // аксессуары в доноре - встраивание в любые части брони с гнёздами
        boolean accDonor = false, armorDonor = false;
        for (int d = 0; d < 4; d++) {
            ItemStack s = inv.getStackInSlot(DONOR + d);
            if (s == null) continue;
            if (Sockets.accessory(s)) accDonor = true;
            else armorDonor = true;
        }
        if (accDonor) {
            if (armorDonor) return new Result(Status.BAD_DONOR, 0);
            Plan pl = plan(inv, player);
            if (pl.fail != null) return pl.fail;
            if (!player.capabilities.isCreativeMode && player.experienceLevel < pl.levels) return new Result(Status.NEED_LEVELS, pl.levels);
            return new Result(Status.READY_SOCKET, pl.levels);
        }
        // заряженное оружие одно в слоте топлива - разрядить
        ItemStack fuelStack = inv.getStackInSlot(FUEL);
        if (fuelStack != null && Ammo.of(fuelStack) != null && emptySlots(inv, ARMOR) && emptySlots(inv, DONOR)) {
            int n = Ammo.loaded(fuelStack);
            if (n <= 0) return new Result(Status.UNLOAD_EMPTY, 0);
            int room = Ammo.room(player, Ammo.of(fuelStack));
            return room <= 0 ? new Result(Status.UNLOAD_FULL, 0) : new Result(Status.READY_UNLOAD, Math.min(n, room));
        }
        // донорская броня с аксессуарами: улучшение её съест - сначала вынуть
        for (int d = 0; d < 4; d++) if (Sockets.count(inv.getStackInSlot(DONOR + d)) > 0) return new Result(Status.DONOR_SOCKETS, d);

        Item[] armor = items(inv, ARMOR);
        Item[] donor = items(inv, DONOR);
        if (allNull(armor) && allNull(donor)) return new Result(Status.EMPTY, 0);
        // только броня с гнёздами (не сет для улучшения) - подсказка про гнёзда
        boolean setFlow = same(armor, realmiteSet()) || !anyNull(armor) && wanderers(armor);
        if (allNull(donor) && !setFlow) {
            for (int a = 0; a < 4; a++) if (Sockets.socketable(inv.getStackInSlot(ARMOR + a))) return new Result(Status.SOCKETS, 0);
        }

        // R0 -> R1: реалмитовый сет + элитный реалмит
        if (same(armor, realmiteSet())) {
            if (anyNull(donor)) return new Result(Status.NEED_DONOR, 0);
            if (!same(donor, eliteRealmiteSet())) return new Result(Status.BAD_DONOR, 0);
            if (bossMissing(player, Boss.ANCIENT_ENTITY))
                return new Result(Status.NEED_BOSS, Boss.ANCIENT_ENTITY.ordinal());
            Item fuel = VanillaItemsOther.realmiteIngot;
            int need = Config.upgradeR1FuelRealmiteIngots;
            if (fuelCount(inv, fuel) < need) return new Result(Status.NEED_FUEL, need, fuel);
            return new Result(Status.READY_UPGRADE, 1, fuel);
        }

        // Дальше - только полный одинаковый сет Странника
        if (anyNull(armor)) return new Result(Status.NEED_ARMOR, 0);
        for (Item a : armor) if (!(a instanceof ItemWandererArmor)) return new Result(Status.BAD_ARMOR, 0);
        ItemStack first = inv.getStackInSlot(ARMOR);
        int tier = ArmorData.tier(first);
        List<Perk> perks = ArmorData.perks(first);
        for (int i = 1; i < 4; i++) {
            ItemStack s = inv.getStackInSlot(ARMOR + i);
            if (ArmorData.tier(s) != tier || !ArmorData.perks(s)
                .equals(perks)) return new Result(Status.BAD_ARMOR, 0);
        }
        if (anyNull(donor)) return new Result(Status.NEED_DONOR, 0);

        // Закалка мирами: сет брони следующего мира в доноре
        ArmorTier next = ArmorTier.next(tier);
        if (next != null && same(donor, next.donorSet())) {
            if (bossMissing(player, next.boss)) return new Result(Status.NEED_BOSS, next.boss.ordinal());
            Item fuel = next.fuel();
            int need = Config.upgradeTwilightFuelCount;
            if (fuelCount(inv, fuel) < need) return new Result(Status.NEED_FUEL, need, fuel);
            return new Result(Status.READY_UPGRADE, next.tier, fuel);
        }
        // Донор - сет другого мира: подсказать, что ступень не та
        for (ArmorTier t : ArmorTier.values()) {
            if (same(donor, t.donorSet())) {
                return new Result(t.tier <= tier ? Status.MAX_TIER : Status.BAD_DONOR, t.tier);
            }
        }

        // Установка перка
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
     * Вынуть аксессуар из гнезда index части в слоте брони armorSlot - в первый свободный слот донора (нет такого - не
     * вынимается). Бесплатно. Только сервер.
     */
    public static boolean extract(IInventory inv, EntityPlayer player, int armorSlot, int index) {
        if (armorSlot < 0 || armorSlot > 3) return false;
        ItemStack armor = inv.getStackInSlot(ARMOR + armorSlot);
        if (!Sockets.socketable(armor) || index < 0 || index >= Sockets.count(armor)) return false;
        for (int d = 0; d < 4; d++) {
            if (inv.getStackInSlot(DONOR + d) != null) continue;
            ItemStack acc = Sockets.extract(armor, index); // сначала из брони
            if (acc == null) return false;
            inv.setInventorySlotContents(DONOR + d, acc); // потом в донора
            inv.markDirty();
            com.divinerpgextended.audit.Audit.log(player, "Sockets", "вынут " + acc.getDisplayName() + " из " + armor.getDisplayName());
            return true;
        }
        return false;
    }

    /** Выполняет улучшение, если всё готово. Вызывается только на сервере. */
    public static boolean execute(IInventory inv, EntityPlayer player) {
        Result r = evaluate(inv, player);
        if (!r.ready()) return false;
        if (r.status == Status.READY_SOCKET) {
            Plan pl = plan(inv, player);
            for (int d = 0; d < 4; d++) {
                if (pl.target[d] < 0) continue;
                ItemStack armor = inv.getStackInSlot(ARMOR + pl.target[d]), acc = inv.getStackInSlot(DONOR + d);
                inv.setInventorySlotContents(DONOR + d, null); // сначала из донора
                Sockets.insert(armor, acc); // потом в броню
                com.divinerpgextended.audit.Audit.log(player, "Sockets", "встроен " + acc.getDisplayName() + " в " + armor.getDisplayName());
            }
            if (!player.capabilities.isCreativeMode) player.addExperienceLevel(-pl.levels);
            inv.markDirty();
            return true;
        }
        if (r.status == Status.READY_UNLOAD) {
            ItemStack w = inv.getStackInSlot(FUEL);
            int n = Ammo.unload(player, w);
            com.divinerpgextended.audit.Audit.log(player, "Ammo", "разряжено " + n + " из " + w.getDisplayName());
            inv.markDirty();
            return n > 0;
        }
        if (r.status == Status.READY_UPGRADE && r.param == 1) {
            // аксессуары реалмитовых частей переходят в части Странника того же типа
            ItemStack[] old = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                ItemStack s = inv.getStackInSlot(ARMOR + i);
                if (s != null && s.getItem() instanceof ItemArmor) old[((ItemArmor) s.getItem()).armorType] = s;
            }
            for (int i = 0; i < 4; i++) {
                ItemStack s = new ItemStack(ModItems.wanderer[i]);
                ArmorData.setTier(s, 1);
                if (Sockets.count(old[i]) > 0) s.getTagCompound().setTag(Sockets.TAG, old[i].getTagCompound().getTag(Sockets.TAG).copy());
                inv.setInventorySlotContents(ARMOR + i, s);
            }
            inv.decrStackSize(FUEL, Config.upgradeR1FuelRealmiteIngots);
        } else if (r.status == Status.READY_UPGRADE) {
            for (int i = 0; i < 4; i++) ArmorData.setTier(inv.getStackInSlot(ARMOR + i), r.param);
            inv.decrStackSize(FUEL, Config.upgradeTwilightFuelCount);
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
