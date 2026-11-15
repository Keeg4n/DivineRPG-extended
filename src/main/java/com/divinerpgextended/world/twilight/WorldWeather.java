package com.divinerpgextended.world.twilight;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossMinions;
import com.divinerpgextended.entity.EntityRockfall;
import com.divinerpgextended.item.ArmorData;
import com.divinerpgextended.perk.HomeArmor;
import com.divinerpgextended.perk.MeleeRoles;
import com.divinerpgextended.perk.PerkEffects;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Weather of the twilight dimensions, like the Iceika blizzard: stillness, warning, the event itself; state is stored in the world save.
 * <ul>
 * <li>Wildwood, downpour: under open sky slowness, mobs heal slowly. The forest set heals in the rain the same way as in water,
 * Aquastrive armor and Wanderer armor from Wildwood and up do not get wet, sea weapons and Aquastrive armor hit harder ({@link SeaWeapons}).</li>
 * <li>Apalachia, rockfall: boulders fall from the sky ({@link EntityRockfall}); the Apalachia set has been protected from them since Divine, a roof
 * saves everyone. Golems are stone: they are not knocked back, mauls and maces hit them harder.</li>
 * </ul>
 */
public class WorldWeather extends WorldSavedData {

    public static final int CALM = 0, WARNING = 1, ACTIVE = 2;
    public static final float RAIN_HEAL = 5F, MOB_RAIN_HEAL = 1F, MAUL_VS_GOLEM = 1.5F;
    private static final int WARNING_TICKS = 600;
    private static final String NAME = "divinerpgextended_weather";

    public int phase = CALM, timer;

    public WorldWeather(String name) {
        super(name);
    }

    public static WorldWeather get(World w) {
        WorldWeather b = (WorldWeather) w.perWorldStorage.loadData(WorldWeather.class, NAME);
        if (b == null) {
            b = new WorldWeather(NAME);
            b.timer = calmTicks(w);
            w.perWorldStorage.setData(NAME, b);
        }
        return b;
    }

    private static boolean wild(World w) {
        return w.provider.dimensionId == ConfigurationHelper.wildwood;
    }

    private static boolean apalachia(World w) {
        return w.provider.dimensionId == ConfigurationHelper.apalachia;
    }

    /** Whether a downpour is going on in this world. */
    public static boolean rain(World w) {
        return wild(w) && Config.wildwoodRain && get(w).phase == ACTIVE;
    }

    /** Whether a rockfall is going on in this world. */
    public static boolean quake(World w) {
        return apalachia(w) && Config.apalachiaRockfall && get(w).phase == ACTIVE;
    }

    /** Downpour: 15-25 minutes of stillness. Rockfall: 10-15. */
    private static int calmTicks(World w) {
        return wild(w) ? 18000 + w.rand.nextInt(12001) : 12000 + w.rand.nextInt(6001);
    }

    /** Downpour 2-3 minutes, rockfall one minute. */
    private static int activeTicks(World w) {
        return wild(w) ? 2400 + w.rand.nextInt(1201) : 1200;
    }

    /** Open sky overhead. */
    public static boolean open(net.minecraft.entity.Entity e) {
        return e.worldObj.canBlockSeeTheSky(MathHelper.floor_double(e.posX), MathHelper.floor_double(e.posY) + 1, MathHelper.floor_double(e.posZ));
    }

    /** One second of downpour for a player. */
    public static void rainStep(EntityPlayer p) {
        if (p.capabilities.isCreativeMode || !open(p)) return;
        if (HomeArmor.wears(p, TwilightWorld.WILDWOOD, false)) {
            if (p.getHealth() < p.getMaxHealth()) p.heal(RAIN_HEAL);
            return;
        }
        if (PerkEffects.wearsWaterArmor(p) || ArmorData.equippedTier(p) >= 3) return;
        p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 0, true));
    }

    /** One second of downpour for mobs: those it soaks are healed. */
    public static void rainMobs(World w) {
        for (Object o : w.loadedEntityList) {
            if (!(o instanceof EntityLiving) || !(o instanceof IMob) || o instanceof IBossDisplayData) continue;
            EntityLiving m = (EntityLiving) o;
            if (m.isEntityAlive() && m.getHealth() < m.getMaxHealth() && Boss.of(m) == null && !BossMinions.isMinion(m) && open(m)) m.heal(MOB_RAIN_HEAL);
        }
    }

    /** One second of rockfall: a boulder nearby and a boulder farther away from each player. Boulders do not fall over the void. */
    public static int rockStep(World w) {
        int made = 0;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.capabilities.isCreativeMode) continue;
            for (int r : new int[] { 6, 12 }) {
                int x = MathHelper.floor_double(p.posX) + w.rand.nextInt(r * 2 + 1) - r, z = MathHelper.floor_double(p.posZ) + w.rand.nextInt(r * 2 + 1) - r;
                int ground = w.getTopSolidOrLiquidBlock(x, z);
                if (ground <= 1 || ground + EntityRockfall.DROP >= w.getHeight()) continue;
                w.spawnEntityInWorld(new EntityRockfall(w, x + 0.5, ground + EntityRockfall.DROP, z + 0.5, ground));
                made++;
            }
        }
        return made;
    }

    /** Phase changes and the event's effect, once per second. */
    public void second(World w) {
        boolean on = wild(w) ? Config.wildwoodRain : Config.apalachiaRockfall;
        if (!on) return;
        if (phase == ACTIVE) {
            if (wild(w)) {
                for (Object o : w.playerEntities) rainStep((EntityPlayer) o);
                rainMobs(w);
            } else rockStep(w);
        }
        timer -= 20;
        if (timer > 0) {
            if (timer % 200 == 0) markDirty();
            return;
        }
        String key = wild(w) ? "downpour" : "quake";
        if (phase == CALM) {
            phase = WARNING;
            timer = WARNING_TICKS;
            tell(w, "dre.chat." + key + "_warning");
        } else if (phase == WARNING) {
            phase = ACTIVE;
            timer = activeTicks(w);
            tell(w, "dre.chat." + key + "_start");
        } else {
            phase = CALM;
            timer = calmTicks(w);
            tell(w, "dre.chat." + key + "_end");
        }
        markDirty();
    }

    private static void tell(World w, String key) {
        for (Object o : w.playerEntities) ((EntityPlayer) o).addChatMessage(new ChatComponentTranslation(key));
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        phase = tag.getInteger("Phase");
        timer = tag.getInteger("Timer");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("Phase", phase);
        tag.setInteger("Timer", timer);
    }

    public static class Events {

        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent e) {
            World w = e.world;
            if (e.phase != TickEvent.Phase.END || w.isRemote || w.getTotalWorldTime() % 20 != 0 || !(wild(w) || apalachia(w))) return;
            get(w).second(w);
        }

        /** Stone Skin of Apalachia golems: they are not knocked back. Further the attribute is stored in the mob itself. */
        @SubscribeEvent
        public void onJoin(EntityJoinWorldEvent e) {
            if (!e.world.isRemote && e.entity instanceof net.divinerpg.entities.twilight.EntityApalachiaGolem)
                ((EntityLiving) e.entity).getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0);
        }

        @SubscribeEvent
        public void onHurt(LivingHurtEvent e) {
            if (e.entityLiving.worldObj.isRemote || !(e.entityLiving instanceof net.divinerpg.entities.twilight.EntityApalachiaGolem) || !(e.source.getEntity() instanceof EntityPlayer)
                || e.source.getSourceOfDamage() != e.source.getEntity()) return;
            if (MeleeRoles.kind(((EntityPlayer) e.source.getEntity()).getHeldItem()) == MeleeRoles.Kind.MAUL) e.ammount *= MAUL_VS_GOLEM;
        }
    }
}
