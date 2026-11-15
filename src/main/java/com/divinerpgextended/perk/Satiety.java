package com.divinerpgextended.perk;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.FoodStats;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

import com.divinerpgextended.item.ArmorData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * The "Satiety" perk of Wanderer armor (donor - the skeleton set), like Botania's Ring of Odin for hunger:
 * <ul>
 * <li>hunger drains {@link #SLOWER} times slower (exhaustion from running, jumping, hitting and mining accumulates more slowly);</li>
 * <li>you do not die of hunger or lose health;</li>
 * <li>you can run even with an empty hunger bar (in vanilla running is disabled when hunger is 6 or less; the client decides this -
 * {@link Client} shows it 7 for the duration of its tick).</li>
 * </ul>
 */
public class Satiety {

    public static final float SLOWER = 2.0F;
    static final String LAST = "dreSatietyEx", LAST_AT = "dreSatietyAt";
    private static java.lang.reflect.Field exhaustion, foodLevel;

    public static boolean has(EntityPlayer p) {
        return ArmorData.hasEquippedPerk(p, Perk.SATIETY);
    }

    static java.lang.reflect.Field exhaustionField() {
        if (exhaustion == null) exhaustion = ReflectionHelper.findField(FoodStats.class, "foodExhaustionLevel", "field_75126_c");
        return exhaustion;
    }

    public static float exhaustion(EntityPlayer p) {
        try {
            return exhaustionField().getFloat(p.getFoodStats());
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void setExhaustion(EntityPlayer p, float v) {
        try {
            exhaustionField().setFloat(p.getFoodStats(), v);
        } catch (Throwable ignored) {}
    }

    /**
     * End of the player's tick on the server: the exhaustion gain since the last tick is divided by SLOWER. When exhaustion crossed 4,
     * vanilla has already removed 4 (and one unit of saturation or hunger) - this tick we count from zero.
     */
    public static void tick(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        float e = exhaustion(p);
        long now = p.worldObj.getTotalWorldTime();
        if (d.hasKey(LAST) && now - d.getLong(LAST_AT) <= 1) {
            float last = d.getFloat(LAST);
            if (e > last) {
                e = last + (e - last) / SLOWER;
                setExhaustion(p, e);
            }
        }
        d.setFloat(LAST, e);
        d.setLong(LAST_AT, now);
    }

    /** Hunger does not hurt. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (e.source == DamageSource.starve && e.entityLiving instanceof EntityPlayer && !e.entityLiving.worldObj.isRemote && has((EntityPlayer) e.entityLiving))
            e.setCanceled(true);
    }

    /** Client: running at hunger 6 or less. For the client tick hunger is 7, for rendering - the real value. */
    public static class Client {

        private int real = -1;

        private static java.lang.reflect.Field food() {
            if (foodLevel == null) foodLevel = ReflectionHelper.findField(FoodStats.class, "foodLevel", "field_75127_a");
            return foodLevel;
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            EntityPlayer p = net.minecraft.client.Minecraft.getMinecraft().thePlayer;
            try {
                if (e.phase == TickEvent.Phase.START) {
                    real = -1;
                    if (p == null || p.getFoodStats().getFoodLevel() > 6 || !has(p)) return;
                    real = p.getFoodStats().getFoodLevel();
                    food().setInt(p.getFoodStats(), 7);
                } else if (real >= 0) {
                    // the server may have sent new hunger during this tick - then it is no longer 7 and there is nothing to touch
                    if (p != null && p.getFoodStats().getFoodLevel() == 7) food().setInt(p.getFoodStats(), real);
                    real = -1;
                }
            } catch (Throwable ignored) {
                real = -1;
            }
        }
    }
}
