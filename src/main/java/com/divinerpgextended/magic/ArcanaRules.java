package com.divinerpgextended.magic;

import java.util.HashMap;
import java.util.Map;

import net.divinerpg.utils.events.ArcanaHelper;
import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.divinerpgextended.asm.ArcanaHooks;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.trinket.Trinket;

/**
 * Правила арканы аддона. Сюда приходят все траты арканы из кода Divine (через
 * {@link com.divinerpgextended.asm.ArcanaUseTransformer}) с именем вызывающего класса:
 * <ul>
 * <li>цена и откат оружия - из таблицы {@link #SPECS} (оружие Divine без своей паузы стреляло каждый тик);</li>
 * <li>не хватило арканы - ничего не тратится и задержка восстановления не сбрасывается (у Divine сбрасывалась);</li>
 * <li>задержка после траты {@link #DELAY} тиков (Кристалл ясности - {@link #DELAY_CLARITY}, под зельем - нет);</li>
 * <li>ангельский сет тратит на полёт {@link #ANGELIC} от цены Divine;</li>
 * <li>полный сет Корма: выстрелы на 20% дешевле, +50 к запасу ({@link ArcanaHooks#max});</li>
 * <li>зелья арканы дают не мгновенную аркану, а эффект "Регенерация арканы" ({@link #buff}).</li>
 * </ul>
 * Арканный урон растёт с этапом игрока ({@link #stage}): от x1.0 на входе в Аркану до x2.2 после Леди Луны.
 */
public final class ArcanaRules {

    public static final int DELAY = 40, DELAY_CLARITY = 15;
    /** Доля цены полёта ангельского сета: было 20 арканы/с, стало 6. */
    public static final float ANGELIC = 0.3F, KORMA_DISCOUNT = 0.8F, KORMA_MAX = 50.0F;
    /** Эффект зелья: длительность (тики) и множитель восстановления у слабого и сильного. */
    public static final int BUFF_TICKS = 45 * 20, BUFF_TICKS_STRONG = 60 * 20;
    public static final String BUFF_UNTIL = "dreArcBuffUntil", BUFF_MULT = "dreArcBuffMult";

    /** Цена (меньше 0 - как у Divine) и откат в тиках. */
    static final class Spec {
        final float cost;
        final int cooldown;

        Spec(float cost, int cooldown) {
            this.cost = cost;
            this.cooldown = cooldown;
        }
    }

    static final Map<String, Spec> SPECS = new HashMap<String, Spec>();
    /** Посох звёздного света и Starlight - один класс Divine: различаем по их цене (25 и 5). */
    static final float STARLIGHT_STAFF = 15F, STARLIGHT = 4F;
    public static final float MAGE_STAFF = 8F;
    public static final int MAGE_STAFF_COOLDOWN = 10;

    static {
        SPECS.put("ItemMeteorMash", new Spec(30, 10));
        SPECS.put("ItemCaptainsSparkler", new Spec(8, 4));
        SPECS.put("ItemFirefly", new Spec(4, 6));
        SPECS.put("ItemMeriksMissile", new Spec(30, 10));
        SPECS.put("ItemLaVekor", new Spec(5, 8));
        SPECS.put("ItemArcaniteBlaster", new Spec(30, 20));
        SPECS.put("ItemGeneralsStaff", new Spec(20, 12));
        SPECS.put("ItemGhostbane", new Spec(60, 40));
        SPECS.put("ItemEnderScepter", new Spec(40, 10));
        SPECS.put("ItemDivineAccumulator", new Spec(40, 10));
        SPECS.put("ItemAttractor", new Spec(10, 6));
        SPECS.put("ItemReflector", new Spec(10, 6));
        SPECS.put("ItemStaffStarlight", new Spec(-1, 5));
        SPECS.put(ItemMageStaff.OWNER, new Spec(MAGE_STAFF, MAGE_STAFF_COOLDOWN));
    }

    private static final Map<String, Long> LAST_USE = new HashMap<String, Long>();
    private static java.lang.reflect.Field playerField;

    private ArcanaRules() {}

    public static EntityPlayer player(ArcanaHelper h) {
        try {
            if (playerField == null) playerField = cpw.mods.fml.relauncher.ReflectionHelper.findField(ArcanaHelper.class, "player");
            return (EntityPlayer) playerField.get(h);
        } catch (Throwable t) {
            return null;
        }
    }

    static String simple(String owner) {
        int i = owner.lastIndexOf('.');
        return i < 0 ? owner : owner.substring(i + 1);
    }

    /** Цена этой траты по правилам аддона (до скидки Кормы). */
    public static float cost(String owner, float divineCost) {
        String s = simple(owner);
        if ("EventArmorTick".equals(s)) return divineCost * ANGELIC;
        if ("ItemStaffStarlight".equals(s)) return divineCost >= 20 ? STARLIGHT_STAFF : STARLIGHT;
        Spec sp = SPECS.get(s);
        return sp != null && sp.cost >= 0 ? sp.cost : divineCost;
    }

    /** Полный сет Кормы. */
    public static boolean korma(EntityPlayer p) {
        if (p == null) return false;
        Item[] set = { ArcanaItems.kormaBoots, ArcanaItems.kormaLegs, ArcanaItems.kormaBody, ArcanaItems.kormaHelmet };
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s == null || s.getItem() != set[i]) return false;
        }
        return true;
    }

    /** Полный сет Вемоса. */
    public static boolean vemos(EntityPlayer p) {
        if (p == null) return false;
        Item[] set = { ArcanaItems.vemosBoots, ArcanaItems.vemosLegs, ArcanaItems.vemosBody, ArcanaItems.vemosHelmet };
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s == null || s.getItem() != set[i]) return false;
        }
        return true;
    }

    /** Трата арканы из кода Divine (вместо ArcanaHelper.useBar). */
    public static boolean useBar(ArcanaHelper h, float divineCost, String owner) {
        if (h == null) return false;
        EntityPlayer p = player(h);
        String s = simple(owner);
        boolean armor = "EventArmorTick".equals(s);
        float c = cost(owner, divineCost);
        if (!armor && korma(p)) c *= KORMA_DISCOUNT;
        Spec sp = SPECS.get(s);
        String key = null;
        long now = 0;
        if (sp != null && sp.cooldown > 0 && p != null) {
            key = (p.worldObj.isRemote ? "c" : "s") + p.getUniqueID() + s + ("ItemStaffStarlight".equals(s) && divineCost < 20 ? "1" : "");
            now = p.worldObj.getTotalWorldTime();
            Long last = LAST_USE.get(key);
            if (last != null && now - last < sp.cooldown && now >= last) return false;
        }
        if (h.getBarValue() < c) return false; // не хватило - задержку восстановления не трогаем
        if (!h.useBar(c)) return false;
        h.regenDelay = delay(p);
        if (key != null) {
            if (LAST_USE.size() > 5000) LAST_USE.clear();
            LAST_USE.put(key, now);
        }
        return true;
    }

    /** Задержка восстановления после траты. */
    public static int delay(EntityPlayer p) {
        if (p == null || p.worldObj.isRemote) return DELAY;
        if (buffMult(p) > 0) return 0;
        return com.divinerpgextended.accessory.Accessories.wears(p, Trinket.CLARITY_CRYSTAL) ? DELAY_CLARITY : DELAY;
    }

    /** Мгновенная аркана из кода Divine: у зелий - эффект "Регенерация арканы", остальное как было. */
    public static void forceRegen(ArcanaHelper h, float amount, String owner) {
        if (h == null) return;
        EntityPlayer p = player(h);
        if (p != null && "ItemArcanaPotion".equals(simple(owner))) {
            if (!p.worldObj.isRemote) buff(p, amount >= 200 ? 3 : 2, amount >= 200 ? BUFF_TICKS_STRONG : BUFF_TICKS);
            return;
        }
        h.forceRegen(amount);
    }

    /** Эффект "Регенерация арканы": без задержки восстановления, восстановление x mult. */
    public static void buff(EntityPlayer p, int mult, int ticks) {
        NBTTagCompound d = p.getEntityData();
        d.setLong(BUFF_UNTIL, p.worldObj.getTotalWorldTime() + ticks);
        d.setInteger(BUFF_MULT, mult);
        ArcanaCombat.syncBuff(p);
    }

    /** Множитель восстановления под зельем или 0, если эффекта нет. */
    public static int buffMult(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        return d.getLong(BUFF_UNTIL) > p.worldObj.getTotalWorldTime() ? Math.max(1, d.getInteger(BUFF_MULT)) : 0;
    }

    public static int buffLeft(EntityPlayer p) {
        return (int) Math.max(0, p.getEntityData().getLong(BUFF_UNTIL) - p.worldObj.getTotalWorldTime());
    }

    /** Боссы после входа в Аркану: каждый +8% к арканному урону игрока. */
    static final Boss[] STAGE = { Boss.CADILLION_EDEN, Boss.CADILLION_WILDWOOD, Boss.CADILLION_APALACHIA, Boss.CADILLION_SKYTHERN,
        Boss.TWILIGHT_DEMON, Boss.KAROT, Boss.ETERNAL_ARCHER, Boss.TWINS, Boss.SOUL_FIEND, Boss.VAMACHERON, Boss.DRAMIX,
        Boss.PARASECTA, Boss.WRECK, Boss.RAGLOK, Boss.LADY_LUNA };
    public static final float PER_BOSS = 0.08F;

    public static float stage(EntityPlayer p) {
        if (p == null) return 1.0F;
        int n = 0;
        for (Boss b : STAGE) if (com.divinerpgextended.boss.ProgressSync.has(p, b)) n++; // на клиенте - по присланной маске
        return 1.0F + PER_BOSS * n;
    }

    // ---------------- защита арканой ----------------

    /** Доля входящего арканного урона, которую берёт на себя аркана, и цена в аркане за единицу урона. */
    public static float shieldShare(EntityPlayer p) {
        float s = com.divinerpgextended.accessory.Accessories.wears(p, Trinket.ARCANE_WARD) ? 0.75F : 0.6F;
        return vemos(p) ? s + 0.1F : s;
    }

    public static float shieldCost(EntityPlayer p) {
        return com.divinerpgextended.accessory.Accessories.wears(p, Trinket.ARCANE_WARD) ? 1.0F : 2.0F;
    }

    /** Списать с арканы часть арканного урона. @return урон, который дойдёт до здоровья */
    public static float absorb(EntityPlayer p, float amount) {
        ArcanaHelper h = ArcanaHelper.getProperties(p);
        if (h == null || amount <= 0) return amount;
        float per = shieldCost(p), want = amount * shieldShare(p), can = h.getBarValue() / per, took = Math.min(want, can);
        if (took <= 0) return amount;
        h.setBarValue(h.getBarValue() - took * per);
        h.regenDelay = Math.max(h.regenDelay, delay(p));
        return amount - took;
    }

    /** Для автотеста. */
    public static void resetCooldowns() {
        LAST_USE.clear();
    }
}
