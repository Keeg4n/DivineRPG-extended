package com.divinerpgextended.halite;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.registry.ModItems;

/**
 * Halite and the dimension spirits. Finished halite does not drop from the Mortum bosses: each drops shards (on the ground), and each participant of the fight gets
 * the boss trophy (unlocks its own halite items) and a soul spark. Sparks make dimension spirits, spirits make the Spirit of the Universe, which makes the
 * Nightmare Bed (the entrance to Vethea).
 * <ul>
 * <li>Ingot = 4 shards + Mortum soul; items - ingots + the trophy of the needed boss ({@link HaliteRecipes}).</li>
 * <li>Twilight Spirit - three sparks from three different Mortum bosses ({@link RecipeTwilightSpirit}).</li>
 * <li>Arcana Spirit - Dramix and Parasecta, World Spirit - Ancient Entity (and the jungle boss, when it appears), Nether Spirit - King of Scorchers,
 * End Spirit - the last of the Ayeraco pack.</li>
 * </ul>
 * The numbers in the enums are item metadata: new ones only at the end.
 */
public final class Halite {

    public enum Part {
        SHARD, INGOT, HANDLE;

        public String key() {
            return name().toLowerCase();
        }
    }

    /** Mortum boss trophies. */
    public enum Trophy {
        DEMON, ARCHER, TWINS, FIEND, VAMACHERON, KAROT;

        public String key() {
            return name().toLowerCase();
        }
    }

    /** Soul sparks: six Mortum bosses, then bosses of other dimensions. */
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

    /** Mortum boss trophy (null - not that one). */
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

    /** Soul spark for a boss (null - none is given for it; Ayeraco - separately, for the last of the pack). */
    public static Spark sparkOf(Boss b) {
        Trophy t = trophyOf(b);
        if (t != null) return Spark.values()[t.ordinal()];
        switch (b) {
            case DRAMIX: return Spark.DRAMIX;
            case PARASECTA: return Spark.PARASECTA;
            case ANCIENT_ENTITY: return Spark.ANCIENT_ENTITY;
            case KING_OF_SCORCHERS: return Spark.SCORCHERS;
            case JUNGLE_KING: return Spark.JUNGLE;
            default: return null;
        }
    }

    /** The radius within which players get their share of the boss loot. */
    public static final double SHARE_R = 48;
    public static final String OWNER = "dreOwner";

    /** Mark the loot: only this player can pick it up. */
    public static ItemStack own(ItemStack s, EntityPlayer p) {
        if (!s.hasTagCompound()) s.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        net.minecraft.nbt.NBTTagCompound o = new net.minecraft.nbt.NBTTagCompound();
        o.setString("id", p.getUniqueID().toString());
        o.setString("name", p.getCommandSenderName());
        s.getTagCompound().setTag(OWNER, o);
        return s;
    }

    /** Whose loot (null - nobody's). */
    public static String owner(ItemStack s) {
        return s != null && s.hasTagCompound() && s.getTagCompound().hasKey(OWNER) ? s.getTagCompound().getCompoundTag(OWNER).getString("id") : null;
    }

    public static String ownerName(ItemStack s) {
        return s != null && s.hasTagCompound() && s.getTagCompound().hasKey(OWNER) ? s.getTagCompound().getCompoundTag(OWNER).getString("name") : null;
    }

    /** One player's share from a boss: shards (if Mortum), trophy, spark - marked with their name. */
    public static java.util.List<ItemStack> share(EntityPlayer p, Boss b, java.util.Random r) {
        java.util.List<ItemStack> l = new java.util.ArrayList<ItemStack>();
        Trophy t = trophyOf(b);
        if (t != null) {
            l.add(own(part(Part.SHARD, SHARDS_MIN + r.nextInt(SHARDS_MAX - SHARDS_MIN + 1)), p));
            l.add(own(trophy(t), p));
        }
        Spark s = sparkOf(b);
        if (s != null) l.add(own(spark(s), p));
        return l;
    }
}
