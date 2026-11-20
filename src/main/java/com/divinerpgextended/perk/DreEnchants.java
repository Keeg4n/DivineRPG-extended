package com.divinerpgextended.perk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentDamage;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.DivineRPGExtended;
import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.dummy.EntityTrainingDummy;
import com.divinerpgextended.station.AltarEnchant;
import com.divinerpgextended.vethea.WeaponKind;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

import io.netty.buffer.ByteBuf;

/**
 * The addon's own melee weapon enchantments. They multiply the damage and the target invulnerability window of the player's own click
 * hit ({@link MeleeRoles}); the multipliers stack with the weapon subtype and with each other, the way damage and fire rate items stack
 * in The Binding of Isaac.
 * <ul>
 * <li>Strike group, one per weapon, instead of Sharpness, Smite, Bane of Arthropods and Forbidden Magic Wrath: Sweep (less damage, but
 * mobs of the target's kind in front of the player are hit too), Agility I-III (shorter window, less damage), Might (longer window, more damage).</li>
 * <li>Condition group, one per weapon: Crown (full health and no damage taken for a while - more damage), Spite (for a while after taking
 * damage - shorter window).</li>
 * </ul>
 * Only from the corrupt altar (its rolls and books) and from commands: the enchanting table never offers them ({@link AltarEnchant}).
 * Each one is switched off in the config (category enchants, requires a restart) and is then not registered at all.
 * <p>
 * Vanilla checks compatibility one way only (the new enchantment against those already on the item), so Sharpness can still land on a
 * weapon with Might through the anvil: then the strike enchantment does nothing ({@link #clashes}), and the tooltip says so.
 */
public final class DreEnchants {

    public enum Kind {
        SWEEP(240, 3, 1, true), AGILITY(241, 4, 3, true), MIGHT(242, 4, 1, true), CROWN(243, 2, 1, false), SPITE(244, 2, 1, false);

        /** From the config: registered at all, and the wanted ID. */
        public boolean enabled = true;
        public int id;
        final int weight, maxLevel;
        /** Strike group (true) or condition group (false): one of each group per weapon. */
        public final boolean strike;

        Kind(int id, int weight, int maxLevel, boolean strike) {
            this.id = id;
            this.weight = weight;
            this.maxLevel = maxLevel;
            this.strike = strike;
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    // ---------------- numbers (config, category enchants.<kind>) ----------------

    public static float sweepDamage = 0.75F, sweepArc = 120F;
    public static double sweepRadius = 3;
    public static int sweepTargets = 4;
    public static float[] agilityWindow = { 0.8F, 0.65F, 0.5F }, agilityDamage = { 0.85F, 0.72F, 0.6F };
    public static int agilityMinWindow = 4;
    public static float mightWindow = 1.4F, mightDamage = 1.3F;
    public static int mightMaxWindow = 30;
    public static float crownDamage = 1.4F;
    public static int crownCalmSeconds = 10;
    public static float spiteWindow = 0.7F;
    public static int spiteSeconds = 5;

    private static final Ench[] ENCH = new Ench[Kind.values().length];
    /** The loader state at registration (AVAILABLE - after every mod's init and postInit); for the selftest. */
    public static String registeredIn = "never";

    /** 1.1.6 defaults (SWEEP 120 ... SPITE 124); 120 is the Morph enchantment in the RedServer client build. */
    private static final int OLD_FIRST_ID = 120;

    public static void config(Configuration cfg) {
        // the old defaults move to the new ones once; the flag keeps IDs set back to 120-124 by hand from moving again
        Property moved = cfg.get("enchants", "oldIdsMoved", false,
            "1.1.7 moved IDs left at the old defaults 120-124 to 240-244 (Morph takes 120 in some packs). Set once, so IDs put back to 120-124 by hand stay");
        boolean move = !moved.getBoolean(false);
        for (Kind k : Kind.values()) {
            String c = "enchants." + k.key();
            k.enabled = cfg.getBoolean("enabled", c, k.enabled,
                "The enchantment exists at all (false - not registered: the altar does not roll it, items that have it lose the effect). Requires a restart");
            Property p = cfg.get(c, "id", k.id,
                "Enchantment ID (for /enchant and item data). Registered after all mods have loaded; if another mod already took it, a free one is chosen from 255 down and written to the log. Must be the same on the server and the clients. Requires a restart",
                1, 255);
            if (move && p.getInt(k.id) == OLD_FIRST_ID + k.ordinal()) p.set(k.id);
            k.id = Math.max(1, Math.min(255, p.getInt(k.id)));
        }
        if (move) moved.set(true);
        String s = "enchants.sweep", a = "enchants.agility", m = "enchants.might", cr = "enchants.crown", sp = "enchants.spite";
        cfg.setCategoryComment("enchants", "The addon's melee enchantments: only from the corrupt altar (its rolls and books), never from the enchanting table. "
            + "Sweep, Agility and Might replace Sharpness, Smite, Bane of Arthropods and Wrath (one of them per weapon); Crown and Spite - one of the two per weapon. "
            + "Multipliers stack with the weapon subtype (melee.*) and with each other. Against players they work only with melee.pvp");
        sweepDamage = cfg.getFloat("damage", s, sweepDamage, 0F, 10F, "Sweep: damage multiplier of the hit; the mobs swept along take the same damage");
        sweepRadius = cfg.get(s, "radius", sweepRadius, "Sweep: how far from the target (in blocks) other mobs are hit").getDouble();
        sweepTargets = cfg.getInt("targets", s, sweepTargets, 0, 64, "Sweep: at most this many mobs besides the target, the nearest to it first");
        sweepArc = cfg.getFloat("arcDegrees", s, sweepArc, 1F, 360F,
            "Sweep: only mobs in front of the player, within this angle around where the player looks. Hit a hostile - only hostiles are swept along; players, villagers and tamed animals - never");
        agilityWindow = floats(cfg, a, "window", agilityWindow, "Agility I, II, III: target invulnerability window multiplier (less - the next hit comes sooner)");
        agilityDamage = floats(cfg, a, "damage", agilityDamage, "Agility I, II, III: damage multiplier");
        agilityMinWindow = cfg.getInt("minWindowTicks", a, agilityMinWindow, 1, 200, "The window never gets shorter than this from Agility and Spite (20 ticks = 1 second)");
        mightWindow = cfg.getFloat("window", m, mightWindow, 1F, 10F, "Might: window multiplier (more - the target stays invulnerable longer)");
        mightDamage = cfg.getFloat("damage", m, mightDamage, 0F, 10F, "Might: damage multiplier");
        mightMaxWindow = cfg.getInt("maxWindowTicks", m, mightMaxWindow, 1, 200, "The window never gets longer than this from Might");
        crownDamage = cfg.getFloat("damage", cr, crownDamage, 0F, 10F, "Crown: damage multiplier while the player has full health and took no damage for crownCalmSeconds");
        crownCalmSeconds = cfg.getInt("calmSeconds", cr, crownCalmSeconds, 0, 3600, "Crown: seconds without taking damage before it works again");
        spiteWindow = cfg.getFloat("window", sp, spiteWindow, 0.01F, 1F, "Spite: window multiplier after the player took damage");
        spiteSeconds = cfg.getInt("seconds", sp, spiteSeconds, 1, 3600, "Spite: how many seconds after taking damage it works (new damage starts it over)");
    }

    /** Three numbers from the config; a shorter list is padded with the defaults. */
    private static float[] floats(Configuration cfg, String cat, String key, float[] def, String comment) {
        double[] d = new double[def.length];
        for (int i = 0; i < d.length; i++) d[i] = def[i];
        double[] v = cfg.get(cat, key, d, comment).getDoubleList();
        float[] out = def.clone();
        for (int i = 0; i < out.length && i < v.length; i++) out[i] = (float) v[i];
        return out;
    }

    /**
     * Registration (load complete, both sides): after init and postInit of every mod, because a mod that registers its enchantment later
     * crashes on a taken ID (Morph in the RedServer client build does it in init). An ID taken by another mod is replaced with a free one
     * from 255 down.
     */
    public static void register() {
        registeredIn = cpw.mods.fml.common.Loader.instance().getLoaderState().name();
        for (Kind k : Kind.values()) {
            ENCH[k.ordinal()] = null;
            if (!k.enabled) continue;
            int id = k.id;
            if (id <= 0 || id >= Enchantment.enchantmentsList.length || Enchantment.enchantmentsList[id] != null) {
                Enchantment busy = id > 0 && id < Enchantment.enchantmentsList.length ? Enchantment.enchantmentsList[id] : null;
                id = free();
                if (id < 0) {
                    DivineRPGExtended.LOG.error("Enchantment " + k.key() + ": no free enchantment ID left, not registered");
                    continue;
                }
                DivineRPGExtended.LOG.warn("Enchantment " + k.key() + ": ID " + k.id + " is taken" + (busy != null ? " by " + busy.getName() + " (" + busy.getClass().getName() + ")" : "")
                    + ", using " + id + "; set enchants." + k.key() + ".id to a free one, the same on the server and the clients");
            }
            ENCH[k.ordinal()] = new Ench(id, k);
        }
    }

    private static int free() {
        for (int i = Enchantment.enchantmentsList.length - 1; i > 0; i--) if (Enchantment.enchantmentsList[i] == null) return i;
        return -1;
    }

    /** The registered enchantment or null (switched off). */
    public static Enchantment get(Kind k) {
        return ENCH[k.ordinal()];
    }

    /** A melee weapon: a sword (all Divine melee weapons are swords) or a Vethea claw, sword or hammer. */
    public static boolean weapon(ItemStack s) {
        if (s == null || s.getItem() == null) return false;
        if (s.getItem() instanceof ItemSword) return true;
        WeaponKind v = WeaponKind.ofItem(s);
        return v == WeaponKind.CLAW || v == WeaponKind.SWORD || v == WeaponKind.HAMMER;
    }

    /** Sharpness, Smite, Bane of Arthropods and Forbidden Magic Wrath: the strike group stands in their place. */
    public static boolean damageGroup(Enchantment e) {
        return e instanceof EnchantmentDamage || e != null && "fox.spiteful.forbidden.enchantments.EnchantmentWrath".equals(e.getClass().getName());
    }

    /** The weapon also has an enchantment of the damage group: the strike enchantment does not work. */
    public static boolean clashes(ItemStack s) {
        NBTTagList l = s == null ? null : s.getEnchantmentTagList();
        if (l == null) return false;
        for (int i = 0; i < l.tagCount(); i++) {
            int id = l.getCompoundTagAt(i).getShort("id");
            if (id >= 0 && id < Enchantment.enchantmentsList.length && damageGroup(Enchantment.enchantmentsList[id])) return true;
        }
        return false;
    }

    /** Working level on the weapon (0 - none, switched off, not a melee weapon, or a strike one beside Sharpness and its group). */
    public static int level(Kind k, ItemStack s) {
        Ench e = ENCH[k.ordinal()];
        if (e == null || s == null) return 0;
        int l = EnchantmentHelper.getEnchantmentLevel(e.effectId, s);
        if (l <= 0 || !weapon(s) || k.strike && clashes(s)) return 0;
        return Math.min(l, k.maxLevel);
    }

    // ---------------- the hit ----------------

    /** Damage multiplier of the player's own click hit with this weapon (1 - nothing of ours). */
    static float damage(EntityPlayer p, ItemStack held) {
        float m = 1F;
        if (level(Kind.SWEEP, held) > 0) m *= sweepDamage;
        int a = level(Kind.AGILITY, held);
        if (a > 0) m *= agilityDamage[Math.min(a, agilityDamage.length) - 1];
        if (level(Kind.MIGHT, held) > 0) m *= mightDamage;
        if (level(Kind.CROWN, held) > 0 && crowned(p)) m *= crownDamage;
        return m;
    }

    /** Target window after the hit: the subtype window times ours, no shorter than {@link #agilityMinWindow}, no longer than {@link #mightMaxWindow} (and never past the subtype window the other way). */
    static int window(EntityPlayer p, ItemStack held, int base) {
        double m = 1;
        int a = level(Kind.AGILITY, held);
        if (a > 0) m *= agilityWindow[Math.min(a, agilityWindow.length) - 1];
        if (level(Kind.MIGHT, held) > 0) m *= mightWindow;
        if (level(Kind.SPITE, held) > 0 && spiteful(p)) m *= spiteWindow;
        if (m == 1) return base;
        int w = (int) Math.round(base * m + 1e-4); // 10 x 0.65F is 6.4999998: half a tick rounds up
        if (m < 1) return Math.min(base, Math.max(agilityMinWindow, w));
        return Math.max(base, Math.min(mightMaxWindow, w));
    }

    /** Crown works: full health and no damage taken for {@link #crownCalmSeconds}. */
    public static boolean crowned(EntityPlayer p) {
        return p.getHealth() >= p.getMaxHealth() && sinceHurt(p) >= crownCalmSeconds * 20L;
    }

    /** Spite works: the player took damage less than {@link #spiteSeconds} ago. */
    public static boolean spiteful(EntityPlayer p) {
        return sinceHurt(p) < spiteSeconds * 20L;
    }

    /**
     * Sweep after the hit: mobs of the target's kind in front of the player, within {@link #sweepRadius} of the target, take the same damage.
     * The damage source: the player is the attacker (loot, experience, kill credit, knockback away from the player), the direct source is
     * the main target - so the on-hit effects of weapons and accessories, which look for a direct melee hit, do not fire again on the sweep,
     * and the Bedrock and Aquatooth Maul waves are not set off by it. A hit on a player or a training dummy sweeps nothing.
     * @return how many were hit
     */
    static int sweep(EntityPlayer p, EntityLivingBase center, float dmg) {
        if (level(Kind.SWEEP, p.getHeldItem()) <= 0 || center instanceof EntityPlayer || center instanceof EntityTrainingDummy || sweepTargets <= 0 || dmg <= 0) return 0;
        final EntityLivingBase c = center;
        double r = sweepRadius, yaw = Math.toRadians(p.rotationYaw), lx = -Math.sin(yaw), lz = Math.cos(yaw), cosHalf = Math.cos(Math.toRadians(Math.min(360F, sweepArc) / 2));
        @SuppressWarnings("unchecked")
        List<EntityLivingBase> near = c.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, c.boundingBox.expand(r, 1.5, r));
        List<EntityLivingBase> picked = new ArrayList<EntityLivingBase>();
        for (EntityLivingBase o : near) {
            if (o == c || o == p || o instanceof EntityPlayer || o instanceof EntityTrainingDummy || !o.isEntityAlive() || o.getDistanceToEntity(c) > r) continue;
            double dx = o.posX - p.posX, dz = o.posZ - p.posZ, d = Math.sqrt(dx * dx + dz * dz);
            if (sweepArc < 360F && (d < 1e-3 || (dx * lx + dz * lz) / d < cosHalf)) continue;
            if (!ColorGear.sameKind(o, c) || Protection.spare(p, o)) continue;
            picked.add(o);
        }
        Collections.sort(picked, new Comparator<EntityLivingBase>() {
            @Override
            public int compare(EntityLivingBase x, EntityLivingBase y) {
                return Double.compare(x.getDistanceSqToEntity(c), y.getDistanceSqToEntity(c));
            }
        });
        int n = 0;
        for (EntityLivingBase o : picked) {
            if (n >= sweepTargets) break;
            n++;
            o.attackEntityFrom(new EntityDamageSourceIndirect("player", c, p), dmg);
            if (o.worldObj instanceof WorldServer)
                ((WorldServer) o.worldObj).func_147487_a("crit", o.posX, o.posY + o.height * 0.6, o.posZ, 6, o.width * 0.4, o.height * 0.3, o.width * 0.4, 0.1);
        }
        return n;
    }

    // ---------------- when the player was hurt (Crown, Spite) ----------------

    private static final Map<UUID, Long> HURT = new HashMap<UUID, Long>();

    /** Ticks since the player last took damage (very many - never in this session). */
    public static long sinceHurt(EntityPlayer p) {
        Long t = HURT.get(p.getUniqueID());
        return t == null ? Long.MAX_VALUE / 2 : p.worldObj.getTotalWorldTime() - t;
    }

    /** For the self-test: as if the player took damage this many ticks ago (negative - never). */
    public static void testHurtAgo(EntityPlayer p, long ticks) {
        if (ticks < 0) HURT.remove(p.getUniqueID());
        else HURT.put(p.getUniqueID(), p.worldObj.getTotalWorldTime() - ticks);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving instanceof EntityPlayer && !e.entityLiving.worldObj.isRemote && e.ammount > 0)
            HURT.put(e.entityLiving.getUniqueID(), e.entityLiving.worldObj.getTotalWorldTime());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        HURT.remove(e.player.getUniqueID());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) ColdHandler.NET.sendTo(new Sync(true), (EntityPlayerMP) e.player);
    }

    // ---------------- the enchantment ----------------

    public static final class Ench extends Enchantment implements AltarEnchant {

        public final Kind kind;

        Ench(int id, Kind k) {
            super(id, k.weight, EnumEnchantmentType.weapon);
            kind = k;
            setName("dre." + k.key());
        }

        @Override
        public int getMaxLevel() {
            return kind.maxLevel;
        }

        /** Altar strength needed: first slot 3-17, second 10-35, last 50 (Agility III - from the second slot). */
        @Override
        public int getMinEnchantability(int l) {
            switch (kind) {
                case AGILITY:
                    return 5 + (l - 1) * 12;
                case SWEEP:
                case CROWN:
                    return 15;
                default:
                    return 10;
            }
        }

        @Override
        public int getMaxEnchantability(int l) {
            return getMinEnchantability(l) + 50;
        }

        @Override
        public boolean canApply(ItemStack s) {
            return weapon(s);
        }

        @Override
        public boolean canApplyAtEnchantingTable(ItemStack s) {
            return false;
        }

        @Override
        public boolean canApplyTogether(Enchantment o) {
            if (o == this) return false;
            if (o instanceof Ench && ((Ench) o).kind.strike == kind.strike) return false;
            return !(kind.strike && damageGroup(o));
        }

        @Override
        public boolean canApplyAtAltar(ItemStack s) {
            return weapon(s);
        }

        @Override
        public int altarTier() {
            return kind.strike ? 0 : 1;
        }
    }

    // ---------------- tooltip numbers: the server's, not the client's own config ----------------

    /** On the client - what the server sent at login (null - nothing yet: the client's own numbers). */
    public static Sync shown;

    public static void registerMessages() {
        ColdHandler.NET.registerMessage(SyncHandler.class, Sync.class, 23, Side.CLIENT);
    }

    /** The numbers for the tooltip: from the server if it sent them, otherwise the client's own. */
    public static Sync numbers() {
        return shown != null ? shown : new Sync(true);
    }

    public static class Sync implements IMessage {

        public float sweepDamage, sweepRadius, mightWindow, mightDamage, crownDamage, spiteWindow;
        public int sweepTargets, crownCalm, spiteSeconds;
        public float[] agilityWindow = new float[3], agilityDamage = new float[3];

        public Sync() {}

        Sync(boolean fill) {
            sweepDamage = DreEnchants.sweepDamage;
            sweepRadius = (float) DreEnchants.sweepRadius;
            sweepTargets = DreEnchants.sweepTargets;
            for (int i = 0; i < 3; i++) {
                agilityWindow[i] = DreEnchants.agilityWindow[Math.min(i, DreEnchants.agilityWindow.length - 1)];
                agilityDamage[i] = DreEnchants.agilityDamage[Math.min(i, DreEnchants.agilityDamage.length - 1)];
            }
            mightWindow = DreEnchants.mightWindow;
            mightDamage = DreEnchants.mightDamage;
            crownDamage = DreEnchants.crownDamage;
            crownCalm = DreEnchants.crownCalmSeconds;
            spiteWindow = DreEnchants.spiteWindow;
            spiteSeconds = DreEnchants.spiteSeconds;
        }

        @Override
        public void fromBytes(ByteBuf b) {
            sweepDamage = b.readFloat();
            sweepRadius = b.readFloat();
            sweepTargets = b.readInt();
            for (int i = 0; i < 3; i++) {
                agilityWindow[i] = b.readFloat();
                agilityDamage[i] = b.readFloat();
            }
            mightWindow = b.readFloat();
            mightDamage = b.readFloat();
            crownDamage = b.readFloat();
            crownCalm = b.readInt();
            spiteWindow = b.readFloat();
            spiteSeconds = b.readInt();
        }

        @Override
        public void toBytes(ByteBuf b) {
            b.writeFloat(sweepDamage);
            b.writeFloat(sweepRadius);
            b.writeInt(sweepTargets);
            for (int i = 0; i < 3; i++) {
                b.writeFloat(agilityWindow[i]);
                b.writeFloat(agilityDamage[i]);
            }
            b.writeFloat(mightWindow);
            b.writeFloat(mightDamage);
            b.writeFloat(crownDamage);
            b.writeInt(crownCalm);
            b.writeFloat(spiteWindow);
            b.writeInt(spiteSeconds);
        }
    }

    public static class SyncHandler implements IMessageHandler<Sync, IMessage> {

        @Override
        public IMessage onMessage(Sync m, MessageContext ctx) {
            shown = m;
            return null;
        }
    }
}
