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
 * Перк "Сытость" брони Странника (донор - сет скелета), как кольцо Одина Botania для голода:
 * <ul>
 * <li>голод тратится в {@link #SLOWER} раза медленнее (усталость от бега, прыжков, ударов и добычи копится медленнее);</li>
 * <li>от голода не умираешь и не теряешь здоровья;</li>
 * <li>бегать можно и с пустой шкалой голода (у ванили бег отключается, когда голода 6 и меньше; это решает клиент -
 * {@link Client} на время своего тика показывает ему 7).</li>
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
     * Конец тика игрока на сервере: прибавка усталости с прошлого тика делится на SLOWER. Когда усталость перевалила за 4,
     * ваниль уже сняла 4 (и единицу сытости или голода) - этот тик считаем с нуля.
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

    /** От голода не ранит. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (e.source == DamageSource.starve && e.entityLiving instanceof EntityPlayer && !e.entityLiving.worldObj.isRemote && has((EntityPlayer) e.entityLiving))
            e.setCanceled(true);
    }

    /** Клиент: бег при голоде 6 и меньше. На время тика клиента голод 7, к отрисовке - настоящий. */
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
                    // сервер мог за этот тик прислать новый голод - тогда он уже не 7 и трогать нечего
                    if (p != null && p.getFoodStats().getFoodLevel() == 7) food().setInt(p.getFoodStats(), real);
                    real = -1;
                }
            } catch (Throwable ignored) {
                real = -1;
            }
        }
    }
}
