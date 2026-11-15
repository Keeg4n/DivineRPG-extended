package com.divinerpgextended.perk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.TwilightItemsOther;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Overworld weapons with no use in Divine - each gets its own role (slime and eye summons - {@link com.divinerpgextended.magic.AnchorSummons},
 * throwable stacks - {@link ThrowStack}):
 * <ul>
 * <li>Corrupted Cannon - the deeper in the Overworld, the stronger ({@link #cannonDepth});</li>
 * <li>Golden Fury - mobs killed with it drop gold nuggets and shadow coins;</li>
 * <li>Stone-blood sword - vampirism; Poison Saber - a poisoned target cannot heal;</li>
 * <li>Palavence, Massivence, Frossivence - a hit on a player or one's own pet heals them instead of damaging (before damage: protected regions without
 * PvP do not cancel it);</li>
 * <li>Hunter Bow - sniper; Shadow Bow - running inertia; shuriken - a chain of hits; Sneaky Hurricane - a poison cloud;</li>
 * <li>Serenade of Death - saves from death like the life amulet, and heals on right-click; Serenade of Infusion - arcana and Clarity;</li>
 * <li>weapon lightning does not burn loot and does not ignite ({@link com.divinerpgextended.entity.EntitySafeLightning});</li>
 * <li>Divine ranged weapons - Thaumcraft books of Infinity (no ammo), Durability and Repair on the anvil.</li>
 * </ul>
 */
public class EarlyWeapons {

    public static final int CANNON_TOP = 64, CANNON_BOTTOM = 5;
    public static final float CANNON_DEEP = 1.6F;
    public static final float FURY_NUGGET = 0.25F, FURY_COIN = 0.10F;
    public static final float VAMP = 0.08F, VAMP_LOW = 0.20F;
    public static final int NO_HEAL_TICKS = 100;
    public static final int HEAL_EVERY = 10;
    public static final double SNIPE_FROM = 16, SNIPE_FULL = 32;
    public static final float SNIPE_MAX = 1.5F;
    public static final int RUN_FULL = 60;
    public static final float RUN_MAX = 1.5F;
    public static final int SERIES_WINDOW = 40, SERIES_MAX = 4;
    public static final float SERIES_STEP = 0.15F;
    public static final int CLOUD_TICKS = 80;
    public static final double CLOUD_R = 2.5;
    public static final int DEATH_USES = 15, DEATH_REGEN_TICKS = 200;
    public static final float INFUSION_ARCANA = 50F;
    public static final int INFUSION_CLARITY = 200, REPAIR_EVERY = 40;

    /** Projectile tag: whose it is (fury, shuriken, snipe, run). */
    public static final String TAG = "dreEarly";
    static final String SX = "dreEarlyX", SY = "dreEarlyY", SZ = "dreEarlyZ", RUN_MULT = "dreRunMult";
    /** Player data: accumulated run (ticks), shuriken chain, last hit. */
    public static final String RUN = "dreRun", SERIES = "dreSeries", SERIES_AT = "dreSeriesAt";
    /** Target data: until which tick it cannot heal (Poison Saber), when it was healed by a sword. */
    public static final String NO_HEAL = "dreNoHeal", SWORD_HEAL = "dreSwordHeal";

    private static final List<EntityThrowable> STORMS = new ArrayList<EntityThrowable>();
    private static final List<Cloud> CLOUDS = new ArrayList<Cloud>();
    private static Enchantment tcRepair;
    private static boolean tcLooked;

    static final class Cloud {

        final World w;
        final double x, y, z;
        final long end;
        final EntityLivingBase owner;

        Cloud(World w, double x, double y, double z, long end, EntityLivingBase owner) {
            this.w = w;
            this.x = x;
            this.y = y;
            this.z = z;
            this.end = end;
            this.owner = owner;
        }
    }

    /** After items are registered: recipe and durability of the Serenade of Death, the cyclops wand without ammo (it summons now). */
    public static void postInit() {
        AquatoothEffects.removeRecipes(VanillaItemsWeapons.serenadeOfDeath);
        cpw.mods.fml.common.registry.GameRegistry.addRecipe(new ItemStack(VanillaItemsWeapons.serenadeOfDeath), " M ", "MSM", " M ", 'M', TwilightItemsOther.mortumGem, 'S',
            new ItemStack(VanillaItemsWeapons.serenadeOfHealth, 1, net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE));
        VanillaItemsWeapons.serenadeOfDeath.setMaxDamage(DEATH_USES);
        try {
            java.lang.reflect.Field f = net.divinerpg.items.base.ItemProjectileShooter.class.getDeclaredField("ammo");
            f.setAccessible(true);
            f.set(VanillaItemsWeapons.cyclopsianStaff, null);
        } catch (Exception ex) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Cyclops wand: Divine ammo not removed - " + ex);
        }
    }

    // ---------------- Corrupted Cannon ----------------

    /** Corrupted Cannon multiplier by shooter height: in the Overworld from 64 and up x1, toward bedrock - up to {@link #CANNON_DEEP}. */
    public static float cannonDepth(EntityLivingBase shooter) {
        if (shooter == null || shooter.dimension != 0) return 1F;
        double t = (CANNON_TOP - shooter.posY) / (CANNON_TOP - CANNON_BOTTOM);
        return 1F + (CANNON_DEEP - 1F) * (float) Math.max(0, Math.min(1, t));
    }

    // ---------------- projectiles: tags on firing ----------------

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote) return;
        Entity b = e.entity;
        if (b instanceof net.divinerpg.entities.vanilla.projectile.EntityVileStorm) {
            if (((EntityThrowable) b).getThrower() instanceof EntityPlayer) STORMS.add((EntityThrowable) b);
            return;
        }
        if (b.getEntityData().hasKey(TAG)) return; // a copy of the projectile (piercing, ricochet)
        if (b instanceof net.divinerpg.entities.vanilla.projectile.EntityShooterBullet) {
            EntityLivingBase th = ((EntityThrowable) b).getThrower();
            ItemStack h = th instanceof EntityPlayer ? th.getHeldItem() : null;
            if (h == null) return;
            if (h.getItem() == VanillaItemsWeapons.goldenFury) b.getEntityData().setString(TAG, "fury");
            else if (h.getItem() == VanillaItemsWeapons.shuriken) b.getEntityData().setString(TAG, "shuriken");
            return;
        }
        if (b instanceof EntityArrow && ((EntityArrow) b).shootingEntity instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) ((EntityArrow) b).shootingEntity;
            ItemStack h = p.getHeldItem();
            if (h == null) return;
            NBTTagCompound d = b.getEntityData();
            if (h.getItem() == VanillaItemsWeapons.hunterBow) {
                d.setString(TAG, "snipe");
                d.setDouble(SX, p.posX);
                d.setDouble(SY, p.posY + p.getEyeHeight());
                d.setDouble(SZ, p.posZ);
            } else if (h.getItem() == VanillaItemsWeapons.shadowBow) {
                d.setString(TAG, "run");
                d.setFloat(RUN_MULT, runMult(p));
                p.getEntityData().setInteger(RUN, 0); // the run-up is spent on this shot
            }
        }
    }

    /** The Shadow Bow fires by itself while held: at every full draw (twice as fast as a normal one). */
    public static final int SHADOW_AUTO = 10;

    /**
     * A bow with accelerated draw: after how many ticks it is drawn fully (0 - draws normally). In Divine the draw is
     * ticks multiplied by 72000 / use duration (an integer), a full draw is 20, so everything shorter than 36001 is faster than normal.
     */
    public static int autoDraw(net.minecraft.item.ItemStack s) {
        if (s == null || !(s.getItem() instanceof net.minecraft.item.ItemBow)) return 0;
        int max = s.getMaxItemUseDuration();
        if (max <= 0 || max >= 72000) return 0;
        int k = 72000 / max;
        return k < 2 ? 0 : (20 + k - 1) / k;
    }

    /** Bows with accelerated draw (and the Shadow Bow) fire by themselves while right-click is held: a shot at every full draw and a new draw at once. */
    @SubscribeEvent
    public void onBowTick(net.minecraftforge.event.entity.player.PlayerUseItemEvent.Tick e) {
        if (e.entityPlayer.worldObj.isRemote || e.item == null) return;
        int full = e.item.getItem() == VanillaItemsWeapons.shadowBow ? SHADOW_AUTO : autoDraw(e.item);
        if (full <= 0) return;
        int max = e.item.getMaxItemUseDuration();
        if (max - e.duration < full) return;
        e.item.getItem().onPlayerStoppedUsing(e.item, e.entityPlayer.worldObj, e.entityPlayer, e.duration);
        e.duration = max; // and draw again at once
    }

    /** Shadow Bow shot multiplier by accumulated run. */
    public static float runMult(EntityPlayer p) {
        return 1F + (RUN_MAX - 1F) * Math.min(1F, p.getEntityData().getInteger(RUN) / (float) RUN_FULL);
    }

    /** Hunter Bow multiplier by shot distance. */
    public static float snipeMult(double dist) {
        return 1F + (SNIPE_MAX - 1F) * (float) Math.max(0, Math.min(1, (dist - SNIPE_FROM) / (SNIPE_FULL - SNIPE_FROM)));
    }

    /** Shuriken chain: multiplier of this hit and the count for the next. */
    public static float series(EntityPlayer p, long now) {
        NBTTagCompound d = p.getEntityData();
        long last = d.getLong(SERIES_AT);
        int n = d.hasKey(SERIES_AT) && now - last <= SERIES_WINDOW && now >= last ? Math.min(SERIES_MAX, d.getInteger(SERIES) + 1) : 0;
        d.setInteger(SERIES, n);
        d.setLong(SERIES_AT, now);
        return 1F + SERIES_STEP * n;
    }

    /** Run-up for the Shadow Bow: running builds it, a step resets it twice as fast. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote) return;
        EntityPlayer p = e.player;
        NBTTagCompound d = p.getEntityData();
        int r = d.getInteger(RUN), n = p.isSprinting() ? Math.min(RUN_FULL, r + 1) : Math.max(0, r - 2);
        if (n != r) d.setInteger(RUN, n);
        if (p.ticksExisted % REPAIR_EVERY == 0) repair(p);
    }

    // ---------------- damage ----------------

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote) return;
        Entity src = e.source.getSourceOfDamage(), atk = e.source.getEntity();
        if (src instanceof net.divinerpg.entities.vanilla.projectile.EntityCorruptedBullet) {
            float m = cannonDepth(((EntityThrowable) src).getThrower());
            if (m > 1F) {
                e.ammount *= m;
                com.divinerpgextended.debug.Debug.note(v, String.format("Damaged cannon at depth: x%.2f", m));
            }
            return;
        }
        String k = src == null ? "" : src.getEntityData().getString(TAG);
        if ("snipe".equals(k)) {
            NBTTagCompound d = src.getEntityData();
            double dx = v.posX - d.getDouble(SX), dy = v.posY + v.height / 2 - d.getDouble(SY), dz = v.posZ - d.getDouble(SZ);
            float m = snipeMult(Math.sqrt(dx * dx + dy * dy + dz * dz));
            e.ammount *= m;
            if (m > 1F) com.divinerpgextended.debug.Debug.note(v, String.format("Hunting bow from afar: x%.2f", m));
        } else if ("run".equals(k)) {
            float m = src.getEntityData().getFloat(RUN_MULT);
            if (m > 1F) {
                e.ammount *= m;
                com.divinerpgextended.debug.Debug.note(v, String.format("Shadow bow at a run: x%.2f", m));
            }
        } else if ("shuriken".equals(k) && atk instanceof EntityPlayer) {
            float m = series((EntityPlayer) atk, v.worldObj.getTotalWorldTime());
            e.ammount *= m;
            if (m > 1F) com.divinerpgextended.debug.Debug.note(v, String.format("Shuriken, streak: x%.2f", m));
        }
        // Poison Saber: a poisoned target cannot heal
        if (atk instanceof EntityPlayer && src == atk) {
            ItemStack h = ((EntityPlayer) atk).getHeldItem();
            if (h != null && h.getItem() == VanillaItemsWeapons.poisonSaber) v.getEntityData().setLong(NO_HEAL, v.worldObj.getTotalWorldTime() + NO_HEAL_TICKS);
        }
    }

    /** Stone-blood sword: vampirism from the damage dealt (last, after all multipliers). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onVampire(LivingHurtEvent e) {
        if (e.ammount <= 0 || e.entityLiving.worldObj.isRemote) return;
        Entity atk = e.source.getEntity();
        if (!(atk instanceof EntityPlayer) || e.source.getSourceOfDamage() != atk) return;
        EntityPlayer p = (EntityPlayer) atk;
        ItemStack h = p.getHeldItem();
        if (h == null || h.getItem() != VanillaItemsWeapons.bloodgemSword || p.getHealth() >= p.getMaxHealth()) return;
        float rate = p.getHealth() < p.getMaxHealth() / 3F ? VAMP_LOW : VAMP;
        float dealt = Math.min(e.ammount, e.entityLiving.getHealth() + e.entityLiving.getAbsorptionAmount());
        p.heal(dealt * rate);
        com.divinerpgextended.debug.Debug.note(p, String.format("Stone-blood sword: +%.1f health (%.0f%%)", dealt * rate, rate * 100));
    }

    /** A target poisoned by the Poison Saber cannot heal. */
    @SubscribeEvent
    public void onHeal(LivingHealEvent e) {
        NBTTagCompound d = e.entityLiving.getEntityData();
        if (!d.hasKey(NO_HEAL)) return;
        long until = d.getLong(NO_HEAL), now = e.entityLiving.worldObj.getTotalWorldTime();
        if (now < until && now >= until - NO_HEAL_TICKS - 1) e.setCanceled(true);
        else d.removeTag(NO_HEAL);
    }

    /** Whether the target cannot heal right now (Poison Saber). */
    public static boolean noHeal(EntityLivingBase v) {
        long until = v.getEntityData().getLong(NO_HEAL), now = v.worldObj.getTotalWorldTime();
        return v.getEntityData().hasKey(NO_HEAL) && now < until && now >= until - NO_HEAL_TICKS - 1;
    }

    // ---------------- healing swords ----------------

    /** Healing of a hit by a healing sword (0 - not a healing one). */
    public static float swordHeal(ItemStack h) {
        if (h == null) return 0;
        Item i = h.getItem();
        if (i == VanillaItemsWeapons.palavence) return 2F;
        if (i == VanillaItemsWeapons.massivence) return 3F;
        if (i == IceikaItems.frossivence) return 4F;
        return 0;
    }

    /** Ally for a healing sword: any other player or one's own tamed animal (Divine writes the player name as owner, vanilla - the UUID). */
    public static boolean friend(EntityPlayer p, Entity t) {
        if (t instanceof EntityPlayer) return t != p;
        String owner = null;
        if (t instanceof EntityTameable && ((EntityTameable) t).isTamed()) owner = ((EntityTameable) t).func_152113_b();
        else if (t instanceof EntityHorse && ((EntityHorse) t).isTame()) owner = ((EntityHorse) t).func_152119_ch();
        if (owner == null || owner.isEmpty()) return false;
        return owner.equals(p.getUniqueID().toString()) || owner.equals(p.getCommandSenderName()) || owner.equals(p.getDisplayName());
    }

    /**
     * A healing sword on a player or one's own pet: healing instead of a hit (one unit of durability, no more often than once per
     * {@link #HEAL_EVERY} ticks per target). Before everyone and even after someone else's hit cancellation: it heals even where a protected region forbids
     * PvP - we cancel the hit ourselves, so it never gets to damage.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onSwing(AttackEntityEvent e) {
        EntityPlayer p = e.entityPlayer;
        float heal = swordHeal(p.getHeldItem());
        if (heal <= 0 || !(e.target instanceof EntityLivingBase) || !friend(p, e.target)) return;
        e.setCanceled(true);
        if (p.worldObj.isRemote) return;
        EntityLivingBase t = (EntityLivingBase) e.target;
        long now = p.worldObj.getTotalWorldTime(), last = t.getEntityData().getLong(SWORD_HEAL);
        if (t.getEntityData().hasKey(SWORD_HEAL) && now - last < HEAL_EVERY && now >= last) return;
        if (t.getHealth() >= t.getMaxHealth() || t.getHealth() <= 0) return;
        t.getEntityData().setLong(SWORD_HEAL, now);
        t.heal(heal);
        p.getHeldItem().damageItem(1, p);
        if (p.getHeldItem() != null && p.getHeldItem().stackSize <= 0) p.destroyCurrentEquippedItem();
        p.worldObj.playSoundAtEntity(t, net.divinerpg.libs.Sounds.heal.getPrefixedName(), 0.7F, 1.2F);
        if (p.worldObj instanceof WorldServer) ((WorldServer) p.worldObj).func_147487_a("heart", t.posX, t.posY + t.height + 0.3, t.posZ, 3, 0.3, 0.1, 0.3, 0);
        com.divinerpgextended.debug.Debug.note(t, String.format("Healing sword: +%.1f health", heal));
    }

    // ---------------- Golden Fury ----------------

    /** Mobs killed with Golden Fury drop gold. Boss minions and mobs of the wrong stage - nothing (their loot is cleaned up last). */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        EntityLivingBase v = e.entityLiving;
        Entity src = e.source.getSourceOfDamage();
        if (v.worldObj.isRemote || src == null || !"fury".equals(src.getEntityData().getString(TAG)) || v instanceof EntityPlayer
            || v instanceof com.divinerpgextended.magic.AnchorSummons.Summon || RangedRoles.bossAdd(v)) return;
        java.util.Random r = v.worldObj.rand;
        if (r.nextFloat() < FURY_NUGGET) e.drops.add(new EntityItem(v.worldObj, v.posX, v.posY + 0.5, v.posZ, new ItemStack(Items.gold_nugget)));
        if (r.nextFloat() < FURY_COIN) e.drops.add(new EntityItem(v.worldObj, v.posX, v.posY + 0.5, v.posZ, new ItemStack(VanillaItemsOther.shadowCoins)));
    }

    // ---------------- Sneaky Hurricane ----------------

    /** The hurricane landed - a poison cloud appears in its place; clouds poison mobs in a radius. */
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        long now = e.world.getTotalWorldTime();
        for (Iterator<EntityThrowable> it = STORMS.iterator(); it.hasNext();) {
            EntityThrowable s = it.next();
            if (s.worldObj != e.world) continue;
            if (s.isDead) {
                if (s.ticksExisted > 0) CLOUDS.add(new Cloud(e.world, s.posX, s.posY, s.posZ, now + CLOUD_TICKS, s.getThrower()));
                it.remove();
            } else if (s.ticksExisted > 400) it.remove(); // flew away or got stuck in an unloaded chunk
        }
        for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext();) {
            Cloud c = it.next();
            if (c.w != e.world) continue;
            if (now >= c.end) {
                it.remove();
                continue;
            }
            if (now % 4 == 0 && e.world instanceof WorldServer) for (int i = 0; i < 6; i++) {
                double a = e.world.rand.nextDouble() * Math.PI * 2, d = e.world.rand.nextDouble() * CLOUD_R;
                ((WorldServer) e.world).func_147487_a("mobSpell", c.x + Math.cos(a) * d, c.y + e.world.rand.nextDouble(), c.z + Math.sin(a) * d, 0, 0.3, 0.75, 0.15, 1);
            }
            if (now % 10 == 0) poisonCloud(c);
        }
    }

    /** Poison mobs in the cloud (not players, pets or peaceful ones). */
    static int poisonCloud(Cloud c) {
        int n = 0;
        for (Object o : c.w.getEntitiesWithinAABB(EntityLiving.class,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(c.x - CLOUD_R, c.y - 1.5, c.z - CLOUD_R, c.x + CLOUD_R, c.y + 2, c.z + CLOUD_R))) {
            EntityLiving m = (EntityLiving) o;
            if (!(m instanceof IMob) || m == c.owner || m instanceof com.divinerpgextended.magic.AnchorSummons.Summon || m.getDistanceSq(c.x, c.y, c.z) > CLOUD_R * CLOUD_R + 4) continue;
            if (m instanceof EntityTameable && ((EntityTameable) m).isTamed()) continue;
            m.addPotionEffect(new PotionEffect(Potion.poison.id, 30, 1));
            n++;
        }
        return n;
    }

    /** For the selftest: a cloud at a point and one wave of poison. @return how many were poisoned */
    public static int testCloud(World w, double x, double y, double z) {
        return poisonCloud(new Cloud(w, x, y, z, w.getTotalWorldTime() + CLOUD_TICKS, null));
    }

    /** Whether a cloud hangs at the point (for the selftest). */
    public static boolean cloudAt(World w, double x, double y, double z) {
        for (Cloud c : CLOUDS) if (c.w == w && Math.abs(c.x - x) < 1.5 && Math.abs(c.y - y) < 1.5 && Math.abs(c.z - z) < 1.5) return true;
        return false;
    }

    // ---------------- serenades ----------------

    /**
     * Right-click with the Serenade of Death heals to full (one unit of durability), instead of Divine's poison projectile. Right-click with the Serenade of
     * Infusion also gives arcana and Clarity (Divine's serenade itself heals as before).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR) return;
        EntityPlayer p = e.entityPlayer;
        ItemStack h = p.getHeldItem();
        if (h == null) return;
        if (h.getItem() == VanillaItemsWeapons.serenadeOfDeath) {
            e.useItem = Event.Result.DENY;
            if (p.worldObj.isRemote || p.getHealth() >= p.getMaxHealth()) return;
            p.setHealth(p.getMaxHealth());
            if (h.getItemDamage() >= h.getMaxDamage()) h.setItemDamage(h.getMaxDamage() - 1); // old serenades with Divine durability (500)
            if (!p.capabilities.isCreativeMode) h.damageItem(1, p);
            if (h.stackSize <= 0) p.destroyCurrentEquippedItem();
            p.worldObj.playSoundAtEntity(p, net.divinerpg.libs.Sounds.heal.getPrefixedName(), 1F, 1F);
            return;
        }
        if (h.getItem() == VanillaItemsWeapons.serenadeOfInfusion && !p.worldObj.isRemote && e.useItem != Event.Result.DENY && !e.isCanceled()) infuse(p);
    }

    /** Serenade of Infusion: +{@link #INFUSION_ARCANA} arcana and Clarity for {@link #INFUSION_CLARITY} ticks. */
    public static void infuse(EntityPlayer p) {
        net.divinerpg.utils.events.ArcanaHelper h = net.divinerpg.utils.events.ArcanaHelper.getProperties(p);
        if (h != null) h.setBarValue(Math.min(com.divinerpgextended.asm.ArcanaHooks.max(p), h.getBarValue() + INFUSION_ARCANA));
        com.divinerpgextended.magic.ArcaneEffects.apply(p, com.divinerpgextended.magic.ArcaneEffect.CLARITY, 1, INFUSION_CLARITY);
    }

    /**
     * A Serenade of Death in the inventory saves from death: full health and regeneration, the serenade breaks (the life amulet from the
     * Twilight Forest). Before the addon's death handlers - for them the player did not die. A ready Pharaoh's Ankh saves first
     * (it recharges, the serenade does not). Does not work in Vethea: only Vethea items work there; the void and /kill are not saved from.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDeath(LivingDeathEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || e.source == DamageSource.outOfWorld) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        if (p.dimension == net.divinerpg.utils.config.ConfigurationHelper.vethea || com.divinerpgextended.trinket.TrinketEffects.ankhReady(p)) return;
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] == null || inv[i].getItem() != VanillaItemsWeapons.serenadeOfDeath) continue;
            if (--inv[i].stackSize <= 0) inv[i] = null;
            p.inventory.markDirty();
            e.setCanceled(true);
            p.setHealth(p.getMaxHealth());
            p.extinguish();
            p.addPotionEffect(new PotionEffect(Potion.regeneration.id, DEATH_REGEN_TICKS, 1));
            p.worldObj.playSoundAtEntity(p, net.divinerpg.libs.Sounds.serenade.getPrefixedName(), 1F, 0.8F);
            if (p.worldObj instanceof WorldServer) ((WorldServer) p.worldObj).func_147487_a("heart", p.posX, p.posY + 1.2, p.posZ, 12, 0.5, 0.5, 0.5, 0);
            ChatComponentTranslation c = new ChatComponentTranslation("dre.chat.serenade_death");
            c.getChatStyle().setColor(EnumChatFormatting.GOLD);
            p.addChatMessage(c);
            com.divinerpgextended.debug.Debug.note(p, "Death Serenade: saved from death and broke");
            return;
        }
    }

    // ---------------- lightning ----------------

    /**
     * A weapon's safe lightning hits only enemies: it does not touch items, experience, frames, players, pets or peaceful mobs (no burnt
     * loot, zombie pigmen from pigs, or witches from villagers). Later, easter eggs with the storm sword in lightning.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onStruck(EntityStruckByLightningEvent e) {
        if (!(e.lightning instanceof com.divinerpgextended.entity.EntitySafeLightning)) return;
        Entity t = e.entity;
        if (t instanceof net.minecraft.entity.player.EntityPlayer) { // other players - where PvP is allowed, yourself - never
            net.minecraft.entity.player.EntityPlayer by = ((com.divinerpgextended.entity.EntitySafeLightning) e.lightning).caster;
            if (by == null || by == t || Protection.spare(by, t)) e.setCanceled(true);
            return;
        }
        boolean foe = t instanceof EntityLiving && (t instanceof IMob || t instanceof net.minecraft.entity.boss.IBossDisplayData)
            && !(t instanceof EntityTameable && ((EntityTameable) t).isTamed()) && !(t instanceof com.divinerpgextended.magic.AnchorSummons.Summon);
        if (!foe) e.setCanceled(true);
        // in the dream: a weapon with lightning is not Vethean - until completion the lightning does not hit dream mobs near the player
        else if (com.divinerpgextended.vethea.Nightmare.inVetheaWorld(t.worldObj) && t.getClass().getName().startsWith("net.divinerpg.entities.vethea.")) {
            net.minecraft.entity.player.EntityPlayer near = t.worldObj.getClosestPlayer(e.lightning.posX, e.lightning.posY, e.lightning.posZ, 64);
            if (near != null && com.divinerpgextended.vethea.Nightmare.active(near)) e.setCanceled(true);
        }
    }

    // ---------------- throwable stacks ----------------

    /**
     * Shurikens and slicers picked up from the ground add themselves to the gathered stack. After the handlers that forbid
     * pickup (personal loot, protected regions); we do not touch another player's item that has an owner, just like the game itself.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPickup(EntityItemPickupEvent e) {
        if (e.entityPlayer.worldObj.isRemote || e.item == null || e.item.isDead) return;
        String owner = e.item.func_145798_i();
        if (owner != null && !owner.equals(e.entityPlayer.getCommandSenderName()) && e.item.lifespan - e.item.age > 200) return;
        int before = e.item.getEntityItem().stackSize, got = ThrowStack.absorb(e.entityPlayer, e.item);
        if (got <= 0) return;
        e.entityPlayer.onItemPickup(e.item, got);
        e.item.worldObj.playSoundAtEntity(e.entityPlayer, "random.pop", 0.2F, ((e.item.worldObj.rand.nextFloat() - e.item.worldObj.rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
        if (got >= before) {
            e.item.setDead();
            e.setCanceled(true);
        }
    }

    // ---------------- anvil and Repair ----------------

    /** Divine ranged weapons (bows, shooters, cannons, La Vekor), except Vethea. */
    public static boolean divineRanged(ItemStack s) {
        if (s == null || s.getItem() == VanillaItemsWeapons.cyclopsianStaff || com.divinerpgextended.vethea.Nightmare.vethean(s)) return false;
        Item i = s.getItem();
        String id = Item.itemRegistry.getNameForObject(i);
        if (id == null || !id.startsWith("divinerpg:")) return false;
        return i instanceof net.divinerpg.items.base.ItemModBow || i instanceof net.divinerpg.items.base.ItemProjectileShooter || Ammo.of(s) != null;
    }

    /** Thaumcraft Repair (null - no Thaumcraft). */
    public static Enchantment tcRepair() {
        if (!tcLooked) {
            tcLooked = true;
            for (Enchantment en : Enchantment.enchantmentsList)
                if (en != null && en.getClass().getName().equals("thaumcraft.common.lib.enchantment.EnchantmentRepair")) tcRepair = en;
        }
        return tcRepair;
    }

    /** Whether this enchantment book can go on a Divine ranged weapon: Infinity - if it has ammo, Durability and Repair - if it breaks. */
    static boolean allowed(Enchantment en, ItemStack s) {
        if (en == Enchantment.infinity) return Ammo.of(s) != null || s.getItem() instanceof net.divinerpg.items.base.ItemModBow;
        if (en == Enchantment.unbreaking || en == tcRepair() && en != null) return s.isItemStackDamageable();
        return false;
    }

    /**
     * A Thaumcraft book of Infinity, Durability or Repair on a Divine ranged weapon (the table and the anvil itself give
     * Infinity only to bows, and Repair only to Thaumcraft items). Enchantments the anvil applies itself, it applies;
     * ones incompatible with those already present (Repair and Durability) - no.
     */
    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onAnvil(AnvilUpdateEvent e) {
        if (e.left == null || e.right == null || e.right.getItem() != Items.enchanted_book || !divineRanged(e.left)) return;
        NBTTagList l = Items.enchanted_book.func_92110_g(e.right);
        if (l == null) return;
        Map<Integer, Integer> have = EnchantmentHelper.getEnchantments(e.left);
        boolean ours = false;
        int cost = 0;
        for (int n = 0; n < l.tagCount(); n++) {
            int id = l.getCompoundTagAt(n).getShort("id"), lvl = l.getCompoundTagAt(n).getShort("lvl");
            Enchantment en = id >= 0 && id < Enchantment.enchantmentsList.length ? Enchantment.enchantmentsList[id] : null;
            if (en == null || !allowed(en, e.left)) continue;
            boolean fits = true;
            for (Integer other : have.keySet()) if (other != id && Enchantment.enchantmentsList[other] != null && !en.canApplyTogether(Enchantment.enchantmentsList[other])) fits = false;
            if (!fits) continue;
            int cur = have.containsKey(id) ? have.get(id) : 0, next = cur == lvl ? Math.min(cur + 1, en.getMaxLevel()) : Math.max(cur, lvl);
            if (next <= cur) continue;
            have.put(id, next);
            cost += next * 3;
            if (!en.canApply(e.left)) ours = true;
        }
        if (!ours) return; // only what the anvil can do by itself - let it do it
        ItemStack out = e.left.copy();
        EnchantmentHelper.setEnchantments(have, out);
        if (e.name != null && !e.name.isEmpty() && !e.name.equals(e.left.getDisplayName())) out.setStackDisplayName(e.name);
        e.output = out;
        e.cost = Math.max(RangedRoles.ANVIL_COST, cost);
        e.materialCost = 1;
    }

    /** Thaumcraft Repair on a Divine ranged weapon: Thaumcraft itself repairs only its own items - we call its repair for ours (with the same vis cost). */
    static void repair(EntityPlayer p) {
        Enchantment r = tcRepair();
        if (r == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s == null || !s.isItemDamaged() || !divineRanged(s) || EnchantmentHelper.getEnchantmentLevel(r.effectId, s) <= 0) continue;
            try {
                if (doRepair == null) doRepair = Class.forName("thaumcraft.common.lib.events.EventHandlerEntity").getMethod("doRepair", ItemStack.class, EntityPlayer.class);
                doRepair.invoke(null, s, p);
            } catch (Throwable t) {
                tcRepair = null; // a different Thaumcraft - no repair
                return;
            }
        }
    }

    private static java.lang.reflect.Method doRepair;
}
