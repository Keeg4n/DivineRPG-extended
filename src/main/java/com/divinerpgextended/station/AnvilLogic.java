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
 * Правила Наковальни Странника. Слоты: 0-3 броня, 4-7 донор (части в любом порядке), 8 топливо.
 * <ul>
 * <li>финальный сет обычного мира (без донора) -> броня Странника R1 с ядром этого сета ({@link ArmorCore}), топливо - плата сета, босс не нужен;</li>
 * <li>сет Странника + финальный сет в доноре + плата этого сета в топливе -> новое ядро (тот же сет с другим топливом - обычный перк, если есть);</li>
 * <li>сет Странника R(n) + сет брони мира следующей ступени -> R(n+1) (закалка R2-R7);</li>
 * <li>сет Странника R7 + сет свойства Венца (Ангельский, Вемос, Корма) + Дух вселенной -> R8 с этим Венцом (за Леди Луну); на R8 тот
 * же сет с арлемитом - смена Венца ({@link com.divinerpgextended.item.ArmorCrown});</li>
 * <li>сет Странника + донорский сет перка -> перк в свободный слот;</li>
 * <li>любые части брони с гнёздами + аксессуары в доноре -> аксессуары встроены ({@link Sockets}), цена - уровни опыта;
 * клик по занятому гнезду - аксессуар выходит в свободный слот донора (бесплатно);</li>
 * <li>части Странника, донор и топливо пусты -> снять привязку (только владелец; чужая - {@link Status#FOREIGN});</li>
 * <li>заряженное оружие в слоте топлива, остальное пусто -> разрядить в инвентарь ({@link Ammo#unload}).</li>
 * </ul>
 * Донорская броня с аксессуарами не принимается: её съедает улучшение - сначала вынуть.
 * <p>
 * Окно разделено на вкладки ({@link #UPGRADE}, {@link #SOCKETS}, {@link #UNLOAD}): каждая смотрит только на свои
 * слоты. {@link #AUTO} - без вкладки, по содержимому (так считают тесты).
 */
public final class AnvilLogic {

    public static final int ARMOR = 0, DONOR = 4, FUEL = 8, SIZE = 9;
    /** Вкладки окна; AUTO - по содержимому. */
    public static final int AUTO = -1, UPGRADE = 0, SOCKETS = 1, UNLOAD = 2, MODES = 3;

    public enum Status {
        EMPTY, NEED_ARMOR, BAD_ARMOR, NEED_DONOR, BAD_DONOR, PERK_PRESENT, NO_SLOT, NEED_BOSS, NEED_FUEL,
        READY_UPGRADE, READY_PERK, MAX_TIER,
        // аксессуары и заряд (новые - только в конец: номер уходит клиенту)
        READY_SOCKET, NEED_LEVELS, SOCKET_PART, SOCKET_FULL, SOCKET_SAME, SOCKETS, DONOR_SOCKETS, READY_UNLOAD, UNLOAD_EMPTY,
        UNLOAD_FULL,
        // пустые вкладки и броня без гнёзд
        EMPTY_SOCKETS, EMPTY_UNLOAD, NO_SOCKETS,
        // переход в Странника без донора
        DONOR_UNNEEDED,
        // смена ядра
        READY_CORE,
        // привязка: снять со своей брони; броня чужая
        READY_UNBIND, FOREIGN,
        // ядро уже такое, плата камнями за смену ядра, донор-Странник только R1
        CORE_SAME, CORE_FUEL, CORE_R1_ONLY,
        // Венец R8: закалка R7 -> R8, смена Венца, Венец такой же, сет Венца на ступени ниже R7
        READY_R8, READY_CROWN, CROWN_SAME, CROWN_R8_ONLY
    }

    public static final class Result {

        public final Status status;
        /**
         * NEED_BOSS - номер босса, NEED_FUEL - сколько нужно, READY_UPGRADE - целевая ступень, READY_PERK и
         * PERK_PRESENT - номер перка, READY_CORE и CORE_SAME - номер ядра, CORE_FUEL - сколько камней нужно.
         */
        public final int param;
        /** Какое топливо нужно для текущей операции (или null). */
        public final Item fuel;
        /** Подтип топлива (-1 - любой). */
        public int fuelMeta = -1;
        /** Встраивание: куда встанет аксессуар каждого донора - по 3 бита (слот брони + 1, 0 - никуда). */
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
        return fuelCount(inv, fuel, -1);
    }

    private static int fuelCount(IInventory inv, Item fuel, int meta) {
        ItemStack s = inv.getStackInSlot(FUEL);
        return s != null && s.getItem() == fuel && (meta < 0 || s.getItemDamage() == meta) ? s.stackSize : 0;
    }

    /** Плата за закалку в R8: Дух вселенной. */
    public static ItemStack r8Fuel() {
        return com.divinerpgextended.halite.Halite.spirit(com.divinerpgextended.halite.Halite.Spirit.UNIVERSE);
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

    /** Вкладка гнёзд: броня в слотах брони, аксессуары в донорах. */
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
            // нужная часть лежит, но без гнёзд (ванильная или чужая) - сказать про это, а не "положите часть"
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

    /** Вкладка разрядки: только оружие в слоте топлива. */
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

    /** Снятие привязки: сверху только части Странника (хоть одна привязана), донор и топливо пусты. Иначе null. */
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
        // аксессуары в доноре - встраивание в любые части брони с гнёздами
        boolean accDonor = false, armorDonor = false;
        for (int d = 0; d < 4; d++) {
            ItemStack s = inv.getStackInSlot(DONOR + d);
            if (s == null) continue;
            if (Sockets.accessory(s)) accDonor = true;
            else armorDonor = true;
        }
        if (accDonor) return mode == AUTO && !armorDonor ? sockets(inv, player) : new Result(Status.BAD_DONOR, 0);
        // заряженное оружие одно в слоте топлива - разрядить
        ItemStack fuelStack = inv.getStackInSlot(FUEL);
        if (mode == AUTO && fuelStack != null && Ammo.of(fuelStack) != null && emptySlots(inv, ARMOR) && emptySlots(inv, DONOR)) return unload(inv, player);
        // донорская броня с аксессуарами: улучшение её съест - сначала вынуть
        for (int d = 0; d < 4; d++) if (Sockets.count(inv.getStackInSlot(DONOR + d)) > 0) return new Result(Status.DONOR_SOCKETS, d);

        Item[] armor = items(inv, ARMOR);
        Item[] donor = items(inv, DONOR);
        if (allNull(armor) && allNull(donor)) return new Result(Status.EMPTY, 0);
        Result bind = unbind(inv, player);
        if (bind != null) return bind;
        // только броня с гнёздами (не сет для улучшения) - подсказка про гнёзда
        ArmorCore forge = ArmorCore.ofSet(armor);
        boolean setFlow = forge != null || !anyNull(armor) && wanderers(armor);
        if (mode == AUTO && allNull(donor) && !setFlow) {
            for (int a = 0; a < 4; a++) if (Sockets.socketable(inv.getStackInSlot(ARMOR + a))) return new Result(Status.SOCKETS, 0);
        }

        // -> R1: финальный сет сам становится бронёй Странника (он и так равен ступени R1 - босс и донор не нужны)
        if (forge != null) {
            if (!emptySlots(inv, DONOR)) return new Result(Status.DONOR_UNNEEDED, 0);
            Item fuel = forge.fuel();
            int need = forge.fuelCount();
            if (fuelCount(inv, fuel) < need) return new Result(Status.NEED_FUEL, need, fuel);
            return new Result(Status.READY_UPGRADE, 1, fuel);
        }

        // Дальше - только полный одинаковый сет Странника
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

        // Венец R8: сет свойства Венца (Ангельский, Вемос, Корма) - закалка R7 в R8 за Леди Луну и Дух вселенной, на R8 - смена Венца
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

        // Закалка мирами: сет брони следующего мира в доноре
        ArmorTier next = ArmorTier.next(tier);
        if (next != null && same(donor, next.donorSet())) {
            if (bossMissing(player, next.boss)) return new Result(Status.NEED_BOSS, next.boss.ordinal());
            Item fuel = next.fuel();
            int need = Config.upgradeTwilightFuelCount;
            if (fuelCount(inv, fuel) < need) return new Result(Status.NEED_FUEL, need, fuel);
            return new Result(Status.READY_UPGRADE, next.tier, fuel);
        }
        // Смена ядра донором R1 Странника: ядро переходит, донор расходуется, топливо не нужно - за ядро уже заплатили при ковке
        if (wanderers(donor)) {
            ArmorCore given = ArmorData.core(inv.getStackInSlot(DONOR));
            for (int i = 0; i < 4; i++) {
                ItemStack s = inv.getStackInSlot(DONOR + i);
                if (!ArmorData.owns(s, player)) return new Result(Status.FOREIGN, DONOR + i); // чужой R1 не сжечь ради ядра
                if (ArmorData.tier(s) != 1) return new Result(Status.CORE_R1_ONLY, 0);
                if (ArmorData.core(s) != given) return new Result(Status.BAD_DONOR, 0);
            }
            if (given == core) return new Result(Status.CORE_SAME, core.ordinal());
            return new Result(Status.READY_CORE, given.ordinal());
        }
        // Смена ядра финальным сетом - за его плату. Арлемит в топливе вместо неё - перк этого же сета (у божественной перка нет)
        ArmorCore wanted = ArmorCore.ofSet(donor);
        ItemStack fuelNow = inv.getStackInSlot(FUEL);
        if (wanted != null && (wanted == ArmorCore.DIVINE || fuelNow == null || fuelNow.getItem() != VanillaItemsOther.arlemiteIngot)) {
            if (wanted == core) return new Result(Status.CORE_SAME, core.ordinal());
            if (fuelCount(inv, wanted.fuel()) < wanted.fuelCount())
                return new Result(wanted == ArmorCore.DIVINE ? Status.NEED_FUEL : Status.CORE_FUEL, wanted.fuelCount(), wanted.fuel());
            return new Result(Status.READY_CORE, wanted.ordinal(), wanted.fuel());
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
                com.divinerpgextended.audit.Audit.log(player, "Wanderer", "снята привязка " + s.getDisplayName());
            }
            inv.markDirty();
            return true;
        }
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
            // аксессуары частей сета переходят в части Странника того же типа
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
            if (r.fuel != null) inv.decrStackSize(FUEL, wanted.fuelCount()); // донор-Странник: без платы
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
