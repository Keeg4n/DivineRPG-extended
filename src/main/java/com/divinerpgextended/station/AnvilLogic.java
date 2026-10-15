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
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossProgress;
import com.divinerpgextended.item.ArmorData;
import com.divinerpgextended.item.ItemWandererArmor;
import com.divinerpgextended.perk.Perk;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.world.ArmorTier;

/**
 * Правила Наковальни Странника. Слоты: 0-3 броня, 4-7 донор (части в любом порядке), 8 топливо.
 * <ul>
 * <li>реалмит + сет элитного реалмита -> броня Странника R1;</li>
 * <li>сет Странника R(n) + сет брони мира следующей ступени -> R(n+1) (закалка R2-R7);</li>
 * <li>сет Странника + донорский сет перка -> перк в свободный слот.</li>
 * </ul>
 */
public final class AnvilLogic {

    public static final int ARMOR = 0, DONOR = 4, FUEL = 8, SIZE = 9;

    public enum Status {
        EMPTY, NEED_ARMOR, BAD_ARMOR, NEED_DONOR, BAD_DONOR, PERK_PRESENT, NO_SLOT, NEED_BOSS, NEED_FUEL,
        READY_UPGRADE, READY_PERK, MAX_TIER
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
            return status == Status.READY_UPGRADE || status == Status.READY_PERK;
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

    private static int fuelCount(IInventory inv, Item fuel) {
        ItemStack s = inv.getStackInSlot(FUEL);
        return s != null && s.getItem() == fuel ? s.stackSize : 0;
    }

    private static boolean bossMissing(EntityPlayer player, Boss boss) {
        return boss != null && Config.requireBossUnlocks && !BossProgress.has(player, boss);
    }

    public static Result evaluate(IInventory inv, EntityPlayer player) {
        Item[] armor = items(inv, ARMOR);
        Item[] donor = items(inv, DONOR);
        if (allNull(armor) && allNull(donor)) return new Result(Status.EMPTY, 0);

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

    /** Выполняет улучшение, если всё готово. Вызывается только на сервере. */
    public static boolean execute(IInventory inv, EntityPlayer player) {
        Result r = evaluate(inv, player);
        if (!r.ready()) return false;
        if (r.status == Status.READY_UPGRADE && r.param == 1) {
            for (int i = 0; i < 4; i++) {
                ItemStack s = new ItemStack(ModItems.wanderer[i]);
                ArmorData.setTier(s, 1);
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
