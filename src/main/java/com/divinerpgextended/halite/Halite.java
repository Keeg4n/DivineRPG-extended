package com.divinerpgextended.halite;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.registry.ModItems;

/**
 * Халит и духи миров. Готовый халит с боссов Мортума не падает: с каждого - осколки (на землю), а каждому участнику боя
 * трофей босса (открывает свои халитовые вещи) и частичка души. Частички - в духов миров, духи - в Дух вселенной, он - в
 * Кровать кошмаров (вход в Ветею).
 * <ul>
 * <li>Слиток = 4 осколка + душа Мортума; вещи - слитки + трофей нужного босса ({@link HaliteRecipes}).</li>
 * <li>Дух сумерек - три частички от трёх разных боссов Мортума ({@link RecipeTwilightSpirit}).</li>
 * <li>Дух Арканы - Драмикс и Парасекта, мира - Древняя сущность (и босс джунглей, когда появится), Ада - Король Скорчеров,
 * Края - последний из стаи Айрако.</li>
 * </ul>
 * Номера в перечислениях - метаданные предметов: новые только в конец.
 */
public final class Halite {

    public enum Part {
        SHARD, INGOT, HANDLE;

        public String key() {
            return name().toLowerCase();
        }
    }

    /** Трофеи боссов Мортума. */
    public enum Trophy {
        DEMON, ARCHER, TWINS, FIEND, VAMACHERON, KAROT;

        public String key() {
            return name().toLowerCase();
        }
    }

    /** Частички душ: шесть боссов Мортума, дальше - боссы других миров. */
    public enum Spark {
        DEMON, ARCHER, TWINS, FIEND, VAMACHERON, KAROT, DRAMIX, PARASECTA, ANCIENT_ENTITY, JUNGLE, SCORCHERS, AYERACO;

        public boolean mortum() {
            return ordinal() <= KAROT.ordinal();
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    public enum Spirit {
        TWILIGHT, ARCANA, WORLD, NETHER, END, UNIVERSE;

        public String key() {
            return name().toLowerCase();
        }
    }

    public static final int SHARDS_MIN = 4, SHARDS_MAX = 6;

    private Halite() {}

    static String[] keys(Enum<?>[] v) {
        String[] k = new String[v.length];
        for (int i = 0; i < v.length; i++) k[i] = v[i].name().toLowerCase();
        return k;
    }

    public static ItemStack part(Part p, int n) {
        return new ItemStack(ModItems.haliteMaterial, n, p.ordinal());
    }

    public static ItemStack trophy(Trophy t) {
        return new ItemStack(ModItems.mortumTrophy, 1, t.ordinal());
    }

    public static ItemStack spark(Spark s) {
        return new ItemStack(ModItems.spiritSpark, 1, s.ordinal());
    }

    public static ItemStack spirit(Spirit s) {
        return new ItemStack(ModItems.worldSpirit, 1, s.ordinal());
    }

    /** Трофей босса Мортума (null - не он). */
    public static Trophy trophyOf(Boss b) {
        switch (b) {
            case TWILIGHT_DEMON: return Trophy.DEMON;
            case ETERNAL_ARCHER: return Trophy.ARCHER;
            case TWINS: return Trophy.TWINS;
            case SOUL_FIEND: return Trophy.FIEND;
            case VAMACHERON: return Trophy.VAMACHERON;
            case KAROT: return Trophy.KAROT;
            default: return null;
        }
    }

    /** Частичка души за босса (null - за него не даётся; Айрако - отдельно, за последнего из стаи). */
    public static Spark sparkOf(Boss b) {
        Trophy t = trophyOf(b);
        if (t != null) return Spark.values()[t.ordinal()];
        switch (b) {
            case DRAMIX: return Spark.DRAMIX;
            case PARASECTA: return Spark.PARASECTA;
            case ANCIENT_ENTITY: return Spark.ANCIENT_ENTITY;
            case KING_OF_SCORCHERS: return Spark.SCORCHERS;
            default: return null;
        }
    }

    /** Участнику победы над боссом: трофей и частичка (в инвентарь, некуда - под ноги). */
    public static void reward(EntityPlayer p, Boss b) {
        Trophy t = trophyOf(b);
        if (t != null) give(p, trophy(t));
        Spark s = sparkOf(b);
        if (s != null) give(p, spark(s));
    }

    public static void give(EntityPlayer p, ItemStack s) {
        if (!p.inventory.addItemStackToInventory(s) || s.stackSize > 0) p.entityDropItem(s, 0.5F);
        p.inventoryContainer.detectAndSendChanges();
    }
}
