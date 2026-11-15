package com.divinerpgextended.perk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.divinerpg.entities.vanilla.projectile.EntityShooterBullet;
import net.divinerpg.utils.items.TwilightItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.world.twilight.TwilightCrafts;
import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Roles of twilight ranged weapons (Divine's projectiles for blitz, phaser and slicer are the same - we tell them apart by the weapon in hand at the shot):
 * <ul>
 * <li>bow - range and accuracy (unchanged);</li>
 * <li>blitz - a cyber shotgun: {@link #PELLETS} projectiles in a fan at full strength ({@link #PELLET_DMG}), x2 against boss minions,
 * but rarely - cooldown {@link #BLITZ_COOLDOWN} ticks; for crowds and close range: pellets fly for {@link #BLITZ_LIFE} ticks
 * (~10 blocks), do not knock bosses back; its own shot sound; uses the dust of its own world ({@link #setupDust}), like the phaser;</li>
 * <li>phaser - piercing: the projectile passes through up to {@link #PIERCE} targets, x2 against golem parts;</li>
 * <li>phasers and blitzes do not break.</li>
 * <li>slicer - ricochets up to {@link #RICOCHET} targets in a row, cuts the golem's vine, interrupts the Warrior King's swing and
 * charges Wind Totems from afar (like snowballs on dragon crystals).</li>
 * </ul>
 * Quiver accessories (they drop from shooters - {@link #DROP_CHANCE}: archers - a quiver (the Eternal Archer - always), mage - a pouch, sorcerer -
 * a sling): the Bottomless Quiver - infinite arrows for bows (the bow draws and fires without arrows, putting nothing in the
 * inventory; the client learns about the quiver from {@link QuiverSync}) and +10% arrow damage; the Dust Pouch - +15% damage for the blitz and
 * phaser (they are infinite anyway); the Thrower's Sling - the slicer returns to the hand, +1 ricochet target.
 */
public class RangedRoles {

    public static final String KIND = "dreRanged", GEN = "dreRangedGen";
    public static final int BLITZ = 1, PHASER = 2, SLICER = 3, CANNON = 4;
    public static final int PELLETS = 5, PIERCE = 3, RICOCHET = 3;
    /** Guns of the Overworld, the Nether, Iceika and the rest: a shotgun like the blitz, but with this many pellets per shot. */
    public static final int CANNON_PELLETS = 3;
    public static final float PELLET_DMG = 1.0F, ADD_BONUS = 2.0F;
    /** Blitz cooldown: Divine's delay (in its units, x4 ticks) and the same in ticks - one shot per second. */
    public static final int BLITZ_DELAY = 5, BLITZ_COOLDOWN = BLITZ_DELAY * 4;
    public static final String BLITZ_SOUND = "divinerpgextended:blitz_shot";
    /** Pellets live this many ticks (range ~10 blocks); of a pellet's knockback only the KNOCK_KEEP share remains - zero: the blitz does not knock back at all. */
    public static final int BLITZ_LIFE = 7;
    public static final double KNOCK_KEEP = 0;
    /** Side pellets against a boss: share of damage (otherwise five pellets point blank wiped out a boss, and the Mortum set healed for each). */
    public static final float BOSS_SIDE = 0.3F;
    private static final List<Entity> BLITZ_SHOTS = new ArrayList<Entity>();
    /** The victim's velocity before the first pellet in a tick: at the end of the tick the knockback is trimmed. */
    private static final java.util.Map<EntityLivingBase, double[]> KNOCK = new java.util.HashMap<EntityLivingBase, double[]>();
    /** Combat bonuses of quivers: arrows +10%, blitz and phaser +15%, slicer +1 ricochet target. */
    public static final float QUIVER_DMG = 1.10F, POUCH_DMG = 1.15F;
    private static final double SPREAD = 9; // degrees between pellets

    /** Flying slicers - for recharging totems. */
    private static final List<Entity> TOTEM_SHOTS = new ArrayList<Entity>();
    public static final String QUIVER_TAG = "dreQuiver";

    public static int kind(ItemStack held) {
        if (held == null) return 0;
        Item i = held.getItem();
        for (TwilightWorld w : TwilightWorld.values()) {
            Item[] wp = TwilightCrafts.weapons(w);
            if (i == wp[3]) return BLITZ;
            if (i == wp[2]) return PHASER;
        }
        if (i == TwilightItemsWeapons.haliteBlitz) return BLITZ; // halite one - from the Twilight Demon, not from world crafting
        if (i == TwilightItemsWeapons.halitePhaser) return PHASER;
        if (i == TwilightItemsWeapons.edenSlicer || i == TwilightItemsWeapons.wildwoodSlicer || i == TwilightItemsWeapons.apalachiaSlicer
            || i == TwilightItemsWeapons.skythernSlicer || i == TwilightItemsWeapons.mortumSlicer) return SLICER;
        return cannon(i) ? CANNON : 0;
    }

    /**
     * A gun that fires like a blitz: a Divine projectile shooter named a cannon (Ice, Frostclaw, Fractite, Ghast, Crabclaw and Bowhead
     * cannons), except Vethea weapons (the dream has its own rules). Staffs, Maelstrom and Golden Fury keep their own roles; the
     * Corrupted Cannon already fires several bullets by itself.
     */
    public static boolean cannon(Item i) {
        if (!(i instanceof net.divinerpg.items.base.ItemProjectileShooter) || i instanceof net.divinerpg.items.vanilla.ItemAnchor) return false;
        if (i.getClass().getName().startsWith("net.divinerpg.items.vethea.")) return false;
        Object name = Item.itemRegistry.getNameForObject(i);
        return name != null && name.toString().toLowerCase().endsWith("cannon");
    }

    /** Shotgun kinds: their pellets live briefly, all hit one target, do not knock back and are weaker against bosses from the sides. */
    public static boolean shotgun(int kind) {
        return kind == BLITZ || kind == CANNON;
    }

    public static boolean wears(EntityPlayer p, com.divinerpgextended.trinket.Trinket t) {
        return com.divinerpgextended.accessory.Accessories.wears(p, t);
    }

    /** Dust cartridge of phasers and blitzes: Divine sets it only for blitzes, a phaser gets the dust of the blitz of the same world. */
    private static final java.util.Map<Item, Item> DUST = new java.util.HashMap<Item, Item>();
    /** Weapons whose cartridge is set for a player for this tick (at the end of the tick - dust again). */
    private static final java.util.Set<Item> ARMED = new java.util.HashSet<Item>();
    static final String SHOOT_TIME = "CanShootTime";
    private static java.lang.reflect.Field ammoField;

    private static java.lang.reflect.Field ammoField() throws Exception {
        if (ammoField == null) {
            ammoField = net.divinerpg.items.base.ItemProjectileShooter.class.getDeclaredField("ammo");
            ammoField.setAccessible(true);
        }
        return ammoField;
    }

    /**
     * At startup (server and client): phasers and blitzes shoot the dust of their own world. Divine checks and spends a cartridge only
     * if one is set on the weapon - so we set phasers the dust of the blitz of the same world. @return how many weapons use dust
     */
    public static int setupDust() {
        DUST.clear();
        try {
            java.lang.reflect.Field f = ammoField();
            for (TwilightWorld w : TwilightWorld.values()) {
                Item[] wp = TwilightCrafts.weapons(w);
                Object d = f.get(wp[3]);
                if (!(d instanceof Item)) continue;
                DUST.put(wp[3], (Item) d);
                DUST.put(wp[2], (Item) d);
            }
            Object hd = f.get(TwilightItemsWeapons.haliteBlitz);
            if (hd instanceof Item) {
                DUST.put(TwilightItemsWeapons.haliteBlitz, (Item) hd);
                DUST.put(TwilightItemsWeapons.halitePhaser, (Item) hd);
            }
            for (java.util.Map.Entry<Item, Item> en : DUST.entrySet()) f.set(en.getKey(), en.getValue());
        } catch (Exception ex) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Phaser and blitz dust: could not set Divine ammo - " + ex);
        }
        tune();
        return DUST.size();
    }

    private static java.lang.reflect.Field shooterField(String name) throws Exception {
        java.lang.reflect.Field f = net.divinerpg.items.base.ItemProjectileShooter.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** Phasers and blitzes do not break (uses = -1, like Divine blitzes); the blitz fires once per second and without Divine's sound - our own comes from {@link #onJoin}. */
    private static void tune() {
        for (Item i : DUST.keySet()) {
            boolean blitz = kind(new ItemStack(i)) == BLITZ;
            try {
                shooterField("uses").setInt(i, -1);
                i.setMaxDamage(0);
                if (blitz) {
                    shooterField("delay").setInt(i, BLITZ_DELAY);
                    shooterField("soundName").set(i, null);
                }
            } catch (Exception ex) {
                com.divinerpgextended.DivineRPGExtended.LOG.warn("Phasers and blitzes: could not configure " + i.getUnlocalizedName() + " - " + ex);
            }
        }
    }

    /** Divine's tick counter (the weapon cooldown is based on it; null - there is none). */
    static java.lang.reflect.Field tickerField() {
        try {
            return Class.forName("net.divinerpg.utils.events.Ticker").getField("tick");
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Divine's counter resets to zero after 100000 ticks (~1.5 h), but the next-shot time recorded on the weapon does
     * not: the blitz would go silent for an hour and a half. A time beyond the cooldown gets reset.
     */
    static void fixCooldown(ItemStack held) {
        if (!held.hasTagCompound()) return;
        java.lang.reflect.Field t = tickerField();
        try {
            if (t != null && held.getTagCompound().getLong(SHOOT_TIME) - t.getInt(null) > BLITZ_COOLDOWN) held.getTagCompound().setLong(SHOOT_TIME, 0);
        } catch (Exception ignored) {
            // no counter - as in Divine
        }
    }

    /** Dust cartridge of this phaser or blitz (null - not one of them). */
    public static Item dust(Item weapon) {
        return DUST.get(weapon);
    }

    /** Infinite ammo for a phaser and blitz: a Dust Pouch is worn or the weapon has Infinity. */
    public static boolean infiniteDust(EntityPlayer p, ItemStack held) {
        return (p.worldObj.isRemote ? clientPouch : wears(p, com.divinerpgextended.trinket.Trinket.DUST_POUCH))
            || net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(INFINITY, held) > 0;
    }

    /**
     * Before a shot: the weapon's cartridge is set for this player (infinite - no cartridge, otherwise dust). Divine looks at the weapon's
     * cartridge in the same moment, right after the click; each click is preceded by its own event - each player has their own calculation. At the
     * end of the tick the cartridge is restored ({@link #onServerTick}). The player's items are not touched.
     */
    static void arm(EntityPlayer p, ItemStack held) {
        Item d = DUST.get(held.getItem());
        if (d == null) return;
        if (held.getItemDamage() != 0) held.setItemDamage(0); // phasers worn out before they stopped breaking
        fixCooldown(held);
        boolean inf = infiniteDust(p, held);
        // Divine takes dust from the weapon's loaded ammo itself - via AmmoHooks
        try {
            ammoField().set(held.getItem(), inf ? null : d);
            ARMED.add(held.getItem());
        } catch (Exception ignored) {
            // field unavailable - Divine shoots as usual
        }
    }

    /** End of server tick: cartridges of phasers and blitzes are dust again. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) disarm();
    }

    /** Return dust to phasers and blitzes whose cartridge was set for a player. */
    public static void disarm() {
        if (ARMED.isEmpty()) return;
        for (Item i : ARMED) try {
            ammoField().set(i, DUST.get(i));
        } catch (Exception ignored) {
            // see arm
        }
        ARMED.clear();
    }

    /** Infinity on a phaser and blitz by a book on the anvil - by the general rule for ranged weapons: {@link EarlyWeapons#onAnvil}. */
    public static final int ANVIL_COST = 6;

    /**
     * A blitz's pellets all hit one target: the invulnerability after a hit does not apply to them. But the knockback from five pellets
     * added up and blew even bosses off the arena - we remember the velocity before the first pellet and trim the knockback at the end of the tick.
     */
    @SubscribeEvent
    public void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || !shotgun(kind(e.source.getSourceOfDamage()))) return;
        v.hurtResistantTime = 0;
        if (!KNOCK.containsKey(v)) KNOCK.put(v, new double[] { v.motionX, v.motionY, v.motionZ });
    }

    /** A blitz's side pellet (a copy): vampirism and other "per hit" effects count only from the central one. */
    public static boolean sidePellet(Entity bullet) {
        return bullet != null && shotgun(kind(bullet)) && bullet.getEntityData().getInteger(GEN) > 0;
    }

    static boolean boss(Entity e) {
        return e instanceof net.minecraft.entity.boss.IBossDisplayData || e instanceof net.divinerpg.entities.base.EntityDivineRPGBoss;
    }

    /** End of tick: pellets older than BLITZ_LIFE vanish; pellet knockback - KNOCK_KEEP of what accumulated (zero for bosses). */
    @SubscribeEvent
    public void onBlitzTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        for (Iterator<Entity> it = BLITZ_SHOTS.iterator(); it.hasNext();) {
            Entity b = it.next();
            if (b.worldObj != e.world) continue;
            // the pellet's chunk was unloaded: it froze and does not age - remove it rather than keep it in the list
            if (!b.isDead && !e.world.blockExists((int) Math.floor(b.posX), (int) Math.floor(b.posY), (int) Math.floor(b.posZ))) b.setDead();
            if (!b.isDead && b.ticksExisted >= BLITZ_LIFE) b.setDead();
            if (b.isDead) it.remove();
        }
        for (Iterator<java.util.Map.Entry<EntityLivingBase, double[]>> it = KNOCK.entrySet().iterator(); it.hasNext();) {
            java.util.Map.Entry<EntityLivingBase, double[]> en = it.next();
            EntityLivingBase v = en.getKey();
            if (v.worldObj != e.world) continue;
            it.remove();
            double[] m = en.getValue();
            double keep = boss(v) ? 0 : KNOCK_KEEP;
            v.motionX = m[0] + (v.motionX - m[0]) * keep;
            v.motionY = m[1] + (v.motionY - m[1]) * keep;
            v.motionZ = m[2] + (v.motionZ - m[2]) * keep;
            v.velocityChanged = true;
        }
    }

    public static final float DROP_CHANCE = 0.10F, DROP_LOOTING = 0.02F;
    /** Endless Quiver from any monster killed by a player: 0.4% and +0.2% per Looting level, no more than 1% (from the Eternal Archer - always). */
    public static final float QUIVER_CHANCE = 0.004F, QUIVER_LOOTING = 0.002F, QUIVER_MAX = 0.01F;

    /** Chance of this kind of quiver dropping from an ordinary shooter at this Looting. */
    public static float dropChance(com.divinerpgextended.trinket.Trinket t, int looting) {
        if (t == com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER) return Math.min(QUIVER_MAX, QUIVER_CHANCE + QUIVER_LOOTING * looting);
        return DROP_CHANCE + DROP_LOOTING * looting;
    }

    /** Which quiver drops from this mob (null - none). */
    public static com.divinerpgextended.trinket.Trinket quiverOf(Entity e) {
        if (e instanceof net.divinerpg.entities.twilight.EntityEternalArcher || e instanceof net.divinerpg.entities.twilight.EntityApalachiaArcher
            || e instanceof net.divinerpg.entities.twilight.EntityMortumArcher
            || e instanceof net.divinerpg.entities.twilight.EntitySunArcher || e instanceof net.divinerpg.entities.iceika.EntityFrostArcher)
            return com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER; // Skythern and Twilight archers are descendants of the Apalachia archer
        if (e instanceof net.divinerpg.entities.twilight.EntityMage) return com.divinerpgextended.trinket.Trinket.DUST_POUCH;
        if (e instanceof net.divinerpg.entities.twilight.EntitySorcerer) return com.divinerpgextended.trinket.Trinket.SLICER_SLING;
        return null;
    }

    /**
     * Quivers are not craftable. The Endless Quiver drops from any monster killed by a player with the same chance
     * ({@link #dropChance}), the Eternal Archer always gives his; the dust pouch and the slicer sling - from mages and sorcerers.
     */
    @SubscribeEvent
    public void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent e) {
        if (e.entityLiving.worldObj.isRemote || !e.recentlyHit || !(e.source.getEntity() instanceof EntityPlayer)) return;
        com.divinerpgextended.trinket.Trinket q = com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER, t = quiverOf(e.entityLiving);
        boolean boss = e.entityLiving instanceof net.divinerpg.entities.twilight.EntityEternalArcher;
        java.util.Random r = e.entityLiving.worldObj.rand;
        if (boss || e.entityLiving instanceof net.minecraft.entity.monster.IMob && r.nextFloat() < dropChance(q, e.lootingLevel)) drop(e, q);
        if (t != null && t != q && r.nextFloat() < dropChance(t, e.lootingLevel)) drop(e, t);
    }

    private static void drop(net.minecraftforge.event.entity.living.LivingDropsEvent e, com.divinerpgextended.trinket.Trinket t) {
        e.drops.add(new net.minecraft.entity.item.EntityItem(e.entityLiving.worldObj, e.entityLiving.posX, e.entityLiving.posY + 0.5, e.entityLiving.posZ,
            new ItemStack(com.divinerpgextended.registry.ModItems.trinket, 1, t.ordinal())));
    }

    /**
     * Phaser and blitz: on a click (before the shot) the weapon's cartridge is set for the player ({@link #arm}). Other shooters and bows
     * take their cartridge themselves - first from the weapon's loaded ammo, then from the inventory ({@link com.divinerpgextended.asm.AmmoHooks}).
     */
    @SubscribeEvent
    public void onUse(net.minecraftforge.event.entity.player.PlayerInteractEvent e) {
        if (e.world.isRemote || e.action == net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        EntityPlayer p = e.entityPlayer;
        ItemStack held = p.getHeldItem();
        if (held == null || held.getItem() instanceof net.minecraft.item.ItemBow || p.capabilities.isCreativeMode) return;
        if (DUST.get(held.getItem()) != null) arm(p, held); // pouch or Infinity - dust is not needed
    }

    /** The weapon broke in hand - the loaded ammo is not lost but spills to the player. */
    @SubscribeEvent
    public void onBreak(net.minecraftforge.event.entity.player.PlayerDestroyItemEvent e) {
        if (!e.entityPlayer.worldObj.isRemote && e.original != null && Ammo.loaded(e.original) > 0) Ammo.spill(e.entityPlayer, e.original);
    }

    /** Client: whether your own player wears a quiver (the bow draws without arrows on the client too). */
    public static boolean clientQuiver;
    /** Client: whether a Dust Pouch is worn (the number on the phaser and blitz icon is "∞"). */
    public static boolean clientPouch;
    private static final java.util.Map<EntityPlayer, String> QUIVER_SENT = new java.util.WeakHashMap<EntityPlayer, String>();

    /** Whether a quiver is worn: the bow shoots without arrows (on the client - by a flag from the server). */
    public static boolean quiverOn(EntityPlayer p) {
        return quiver(p);
    }

    private static boolean quiver(EntityPlayer p) {
        return p.worldObj.isRemote ? clientQuiver : wears(p, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER);
    }

    /**
     * Remove marked ammunition (old quiver arrows), remove the tag from a cartridge borrowed before 0.99.67, and tell the
     * client about the quiver and about items in the old accessory slots (the button is visible while something is lying there).
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote) return;
        EntityPlayer p = e.player;
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s != null && s.hasTagCompound() && s.getTagCompound().getBoolean(QUIVER_TAG)) p.inventory.mainInventory[i] = null;
        }
        Ammo.settle(p);
        if (p.ticksExisted % 10 == 0 && p instanceof net.minecraft.entity.player.EntityPlayerMP) {
            boolean q = wears(p, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER);
            boolean pouch = wears(p, com.divinerpgextended.trinket.Trinket.DUST_POUCH);
            boolean left = com.divinerpgextended.accessory.Accessories.of(p).leftovers();
            String sig = q + "/" + left + "/" + pouch;
            if (!sig.equals(QUIVER_SENT.get(p))) {
                QUIVER_SENT.put(p, sig);
                com.divinerpgextended.cold.ColdHandler.NET.sendTo(new QuiverSync(q, left, pouch), (net.minecraft.entity.player.EntityPlayerMP) p);
            }
        }
    }

    public static class QuiverSync implements cpw.mods.fml.common.network.simpleimpl.IMessage {

        boolean on, leftovers, pouch;

        public QuiverSync() {}

        QuiverSync(boolean on, boolean leftovers, boolean pouch) {
            this.on = on;
            this.leftovers = leftovers;
            this.pouch = pouch;
        }

        @Override
        public void fromBytes(io.netty.buffer.ByteBuf buf) {
            on = buf.readBoolean();
            leftovers = buf.readBoolean();
            pouch = buf.isReadable() && buf.readBoolean();
        }

        @Override
        public void toBytes(io.netty.buffer.ByteBuf buf) {
            buf.writeBoolean(on);
            buf.writeBoolean(leftovers);
            buf.writeBoolean(pouch);
        }
    }

    public static class QuiverSyncHandler implements cpw.mods.fml.common.network.simpleimpl.IMessageHandler<QuiverSync, cpw.mods.fml.common.network.simpleimpl.IMessage> {

        @Override
        public cpw.mods.fml.common.network.simpleimpl.IMessage onMessage(QuiverSync msg, cpw.mods.fml.common.network.simpleimpl.MessageContext ctx) {
            clientQuiver = msg.on;
            clientPouch = msg.pouch;
            com.divinerpgextended.accessory.Accessories.clientLeftovers = msg.leftovers;
            return null;
        }
    }

    public static void registerMessages() {
        com.divinerpgextended.cold.ColdHandler.NET.registerMessage(QuiverSyncHandler.class, QuiverSync.class, 10, cpw.mods.fml.relauncher.Side.CLIENT);
    }

    public static final String TMP_INF = "dreTmpInfinity";
    private static final int INFINITY = net.minecraft.enchantment.Enchantment.infinity.effectId;

    /**
     * Quiver: a bow (vanilla and Divine) draws without arrows (both require an arrow even with Infinity - we draw
     * ourselves) and shoots as with Infinity - arrows are not needed and not spent.
     */
    @SubscribeEvent
    public void onNock(net.minecraftforge.event.entity.player.ArrowNockEvent e) {
        if (e.result == null || !quiver(e.entityPlayer)) return;
        e.entityPlayer.setItemInUse(e.result, e.result.getMaxItemUseDuration());
        e.setCanceled(true);
    }

    @SubscribeEvent
    public void onLoose(net.minecraftforge.event.entity.player.ArrowLooseEvent e) {
        if (quiver(e.entityPlayer)) lendInfinity(e.entityPlayer, e.bow);
    }

    /** Put a temporary Infinity on the bow (removed when the bow is released). */
    public static void lendInfinity(EntityPlayer p, ItemStack bow) {
        if (bow == null || !quiver(p)) return;
        if (net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(INFINITY, bow) > 0) return;
        bow.addEnchantment(net.minecraft.enchantment.Enchantment.infinity, 1);
        bow.getTagCompound().setBoolean(TMP_INF, true);
    }

    /** Remove the temporary Infinity from a bow that is no longer being drawn. */
    public static void returnInfinity(ItemStack bow) {
        if (bow == null || !bow.hasTagCompound() || !bow.getTagCompound().getBoolean(TMP_INF)) return;
        bow.getTagCompound().removeTag(TMP_INF);
        net.minecraft.nbt.NBTTagList ench = bow.getEnchantmentTagList();
        if (ench != null) {
            for (int i = 0; i < ench.tagCount(); i++) if (ench.getCompoundTagAt(i).getShort("id") == INFINITY) {
                ench.removeTag(i);
                break;
            }
            if (ench.tagCount() == 0) bow.getTagCompound().removeTag("ench");
        }
    }

    @SubscribeEvent
    public void onBowTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayer p = e.player;
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s != null && !(p.isUsingItem() && s == p.getHeldItem())) returnInfinity(s); // we do not touch a bow being drawn
        }
    }

    /** Arrows from the quiver are not picked back up (otherwise the quiver is an arrow farm). */
    @SubscribeEvent
    public void onArrow(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof net.minecraft.entity.projectile.EntityArrow)) return;
        net.minecraft.entity.projectile.EntityArrow a = (net.minecraft.entity.projectile.EntityArrow) e.entity;
        if (a.shootingEntity instanceof EntityPlayer && wears((EntityPlayer) a.shootingEntity, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER)
            && a.canBePickedUp == 1) a.canBePickedUp = 2;
    }

    /** Frost Archer pause between shots (ticks): in Divine it is 2 ticks, ten arrows per second. */
    public static final int FROST_ARCHER_MIN = 20, FROST_ARCHER_MAX = 40;

    /** The Frost Archer shoots like a skeleton - once per 1-2 seconds, not in a burst. */
    @SubscribeEvent
    public void onFrostArcher(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof net.divinerpg.entities.iceika.EntityFrostArcher)) return;
        net.divinerpg.entities.iceika.EntityFrostArcher a = (net.divinerpg.entities.iceika.EntityFrostArcher) e.entity;
        for (Object o : new java.util.ArrayList<Object>(a.tasks.taskEntries)) {
            net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry t = (net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry) o;
            if (!(t.action instanceof net.minecraft.entity.ai.EntityAIArrowAttack)) continue;
            a.tasks.removeTask(t.action);
            a.tasks.addTask(t.priority, new net.minecraft.entity.ai.EntityAIArrowAttack(a,
                a.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.movementSpeed).getBaseValue(), FROST_ARCHER_MIN, FROST_ARCHER_MAX, 10.0F));
        }
    }

    public static int kind(Entity bullet) {
        return bullet == null ? 0 : bullet.getEntityData().getInteger(KIND);
    }

    /** Boss minion (by summon tags). */
    public static boolean bossAdd(Entity e) {
        NBTTagCompound d = e.getEntityData();
        return d.hasKey(com.divinerpgextended.entity.EntityGroveGuardian.ADD_TAG) || d.hasKey("dreCadillionAdd") || d.hasKey("dreDemonAdd")
            || d.hasKey(com.divinerpgextended.entity.EntityLairCrab.CARCINUS_ADD);
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof EntityShooterBullet)) return;
        Entity b = e.entity;
        NBTTagCompound d = b.getEntityData();
        if (d.hasKey(KIND)) {
            if (d.getInteger(KIND) == SLICER) TOTEM_SHOTS.add(b);
            if (shotgun(d.getInteger(KIND))) BLITZ_SHOTS.add(b);
            return;
        }
        EntityLivingBase thrower = ((EntityShooterBullet) b).getThrower();
        if (!(thrower instanceof EntityPlayer)) return;
        EntityPlayer pl = (EntityPlayer) thrower;
        ItemStack held = pl.getHeldItem();
        int k = kind(held);
        if (k == 0) return;
        if (k == SLICER && wears(pl, com.divinerpgextended.trinket.Trinket.SLICER_SLING) && !pl.capabilities.isCreativeMode) {
            // sling: a thrown slicer returns to the hand (into a gathered stack too)
            ThrowStack.giveBack(pl, held);
        }
        d.setInteger(KIND, k);
        if (k == SLICER) TOTEM_SHOTS.add(b);
        if (shotgun(k)) {
            BLITZ_SHOTS.add(b);
            if (k == BLITZ) e.world.playSoundAtEntity(pl, BLITZ_SOUND, 1.3F, 0.95F + e.world.rand.nextFloat() * 0.1F); // guns keep their own sound
            // shot: another PELLETS - 1 projectiles in a horizontal fan (3 in total for guns)
            int pellets = k == BLITZ ? PELLETS : CANNON_PELLETS;
            for (int i = 1; i < pellets; i++) {
                double ang = Math.toRadians(SPREAD * ((i + 1) / 2) * (i % 2 == 0 ? 1 : -1));
                double c = Math.cos(ang), s = Math.sin(ang);
                double mx = b.motionX * c - b.motionZ * s, mz = b.motionX * s + b.motionZ * c;
                // slightly ahead of the shooter so the pellet does not start its path inside him
                spawnClone(b, b.posX + mx * 0.6, b.posY + b.motionY * 0.6, b.posZ + mz * 0.6, mx, b.motionY + (e.world.rand.nextDouble() - 0.5) * 0.08, mz, 1);
            }
        }
    }

    /**
     * A copy of a Divine projectile (via NBT - texture, damage, shooter) with a new heading and generation. It is created by the same class
     * directly, not through the entity registry: a projectile with particles (blitz) might not be found by name - the side pellet did not appear.
     */
    public static Entity spawnClone(Entity src, double x, double y, double z, double mx, double my, double mz, int gen) {
        NBTTagCompound tag = new NBTTagCompound();
        src.writeToNBT(tag);
        tag.removeTag("UUIDMost");
        tag.removeTag("UUIDLeast");
        Entity c;
        try {
            c = src.getClass().getConstructor(World.class).newInstance(src.worldObj);
            c.readFromNBT(tag);
        } catch (Exception ex) {
            c = EntityList.createEntityFromNBT(tag, src.worldObj);
        }
        if (c == null) return null;
        // shooter: without it the copy hit the shooter himself (spawned inside him) and did not count as his shot
        if (src instanceof net.minecraft.entity.projectile.EntityThrowable && c instanceof net.minecraft.entity.projectile.EntityThrowable) {
            EntityLivingBase owner = ((net.minecraft.entity.projectile.EntityThrowable) src).getThrower();
            if (owner != null) cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.entity.projectile.EntityThrowable.class,
                (net.minecraft.entity.projectile.EntityThrowable) c, owner, "thrower", "field_70192_c");
        }
        c.setPosition(x, y, z);
        c.motionX = mx;
        c.motionY = my;
        c.motionZ = mz;
        c.getEntityData().setInteger(KIND, kind(src));
        c.getEntityData().setInteger(GEN, gen);
        src.worldObj.spawnEntityInWorld(c);
        return c;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        Entity b = e.source.getSourceOfDamage();
        if (b instanceof net.minecraft.entity.projectile.EntityArrow && kind(b) == 0) {
            Entity sh = ((net.minecraft.entity.projectile.EntityArrow) b).shootingEntity;
            if (sh instanceof EntityPlayer && wears((EntityPlayer) sh, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER)) e.ammount *= QUIVER_DMG;
            return;
        }
        int k = kind(b);
        if (k == 0) return;
        EntityLivingBase owner = b instanceof EntityShooterBullet ? ((EntityShooterBullet) b).getThrower() : null;
        boolean pouch = owner instanceof EntityPlayer && wears((EntityPlayer) owner, com.divinerpgextended.trinket.Trinket.DUST_POUCH);
        boolean sling = owner instanceof EntityPlayer && wears((EntityPlayer) owner, com.divinerpgextended.trinket.Trinket.SLICER_SLING);
        if (pouch && (k == BLITZ || k == PHASER)) e.ammount *= POUCH_DMG;
        EntityLivingBase victim = e.entityLiving;
        int gen = b.getEntityData().getInteger(GEN);
        switch (k) {
            case BLITZ:
            case CANNON:
                e.ammount *= PELLET_DMG * (bossAdd(victim) ? ADD_BONUS : 1) * (gen > 0 && boss(victim) ? BOSS_SIDE : 1);
                break;
            case PHASER:
                // piercing: the projectile flies on behind the target
                if (gen + 1 < PIERCE) {
                    double v = Math.sqrt(b.motionX * b.motionX + b.motionY * b.motionY + b.motionZ * b.motionZ);
                    if (v > 0.01) {
                        double k2 = (victim.width / 2 + 1.0) / v;
                        spawnClone(b, victim.posX + b.motionX * k2, b.posY + b.motionY * k2, victim.posZ + b.motionZ * k2, b.motionX, b.motionY, b.motionZ, gen + 1);
                    }
                }
                break;
            default: // slicer: ricochet to the nearest other enemy
                if (gen + 1 < RICOCHET + (sling ? 1 : 0)) {
                    EntityLivingBase next = nearestFoe(victim, 8);
                    if (next != null) {
                        double dx = next.posX - victim.posX, dy = next.posY + next.height / 2 - (victim.posY + victim.height / 2), dz = next.posZ - victim.posZ;
                        double d = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));
                        double sp = Math.max(1.0, Math.sqrt(b.motionX * b.motionX + b.motionY * b.motionY + b.motionZ * b.motionZ));
                        double ox = dx / d * (victim.width / 2 + 0.6), oz = dz / d * (victim.width / 2 + 0.6);
                        spawnClone(b, victim.posX + ox, victim.posY + victim.height / 2, victim.posZ + oz, dx / d * sp, dy / d * sp + 0.05, dz / d * sp, gen + 1);
                    }
                }
        }
    }

    private static EntityLivingBase nearestFoe(EntityLivingBase from, double r) {
        EntityLivingBase best = null;
        double bd = r * r;
        for (Object o : from.worldObj.getEntitiesWithinAABB(EntityLiving.class, from.boundingBox.expand(r, r / 2, r))) {
            EntityLivingBase m = (EntityLivingBase) o;
            if (m == from || !m.isEntityAlive() || !(m instanceof IMob || m instanceof net.minecraft.entity.boss.IBossDisplayData)) continue;
            double d = m.getDistanceSqToEntity(from);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    /** Slicers near a Wind Totem recharge it. */
    @SubscribeEvent
    public void onTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || TOTEM_SHOTS.isEmpty()) return;
        for (Iterator<Entity> it = TOTEM_SHOTS.iterator(); it.hasNext();) {
            Entity b = it.next();
            if (b.isDead) {
                it.remove();
                continue;
            }
            if (b.worldObj != e.world) continue;
            World w = b.worldObj;
            int bx = (int) Math.floor(b.posX), by = (int) Math.floor(b.posY), bz = (int) Math.floor(b.posZ);
            // slicer in an unloaded chunk (or stuck): do not read blocks there - that loaded the chunk every tick
            if (b.ticksExisted > 200 || !w.checkChunksExist(bx - 1, by - 1, bz - 1, bx + 1, by + 1, bz + 1)) {
                it.remove();
                continue;
            }
            for (int x = bx - 1; x <= bx + 1; x++) for (int y = by - 1; y <= by + 1; y++) for (int z = bz - 1; z <= bz + 1; z++) {
                if (w.getBlock(x, y, z) != com.divinerpgextended.registry.ModBlocks.windTotem || w.getBlockMetadata(x, y, z) != 0) continue;
                for (Object o : w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntityWindLord.class,
                    net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 48, y - 32, z - 48, x + 48, y + 32, z + 48))) {
                    ((com.divinerpgextended.entity.EntityWindLord) o).remoteCharge(x, y, z);
                    break;
                }
                b.setDead();
                it.remove();
                return;
            }
        }
    }
}
