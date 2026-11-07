package com.divinerpgextended.world.twilight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.perk.PerkEffects;
import com.divinerpgextended.perk.Protection;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Морское оружие.
 * <ul>
 * <li>Влага: в воде и под дождём морское оружие бьёт на {@link #WET_BONUS} сильнее, под ливнем Дикого леса так же и водная броня.</li>
 * <li>Гарпун: брошенный якорь тянет цель к игроку, не чаще раза в {@link #HARPOON_EVERY} тиков; удар этим якорем в руке в
 * ближайшие {@link #HARPOON_WINDOW} тиков сильнее в {@link #HARPOON_HIT} раза. Босса не тянет, а подтягивает игрока к нему. Дальность: крабовый 8,
 * акулий 12, гренландский 12 с оглушением на секунду, лиоплевродоновый 16 и до трёх целей; из воды в полтора раза дальше.</li>
 * </ul>
 * Игроков гарпун тянет только при melee.pvp и не в привате.
 */
public class SeaWeapons {

    public static final float WET_BONUS = 1.25F, HARPOON_HIT = 1.5F;
    public static final int HARPOON_EVERY = 60, HARPOON_WINDOW = 40;
    public static final String PULL_AT = "dreHarpoonAt";
    private static final String SEA = "dreSeaShot", ANCHOR = "dreAnchor", HIT_UNTIL = "dreHarpoonHit";
    private static final double[] RANGE = { 8, 12, 12, 16 };
    private static final double SWARM = 4, WATER_RANGE = 1.5;
    private static Set<Item> sea;
    /** Рывок ждёт конца тика: сразу за уроном игра сама отбрасывает цель прочь. */
    private static final List<Object[]> PULLS = new ArrayList<Object[]>();

    private static Set<Item> sea() {
        if (sea == null) {
            Set<Item> s = new HashSet<Item>();
            java.util.Collections.addAll(s, VanillaItemsWeapons.oceanKnife, VanillaItemsWeapons.aquaticMaul, VanillaItemsWeapons.aquaticTrident, VanillaItemsWeapons.aquaticDagger, VanillaItemsWeapons.aquaton,
                VanillaItemsWeapons.sharkSword, VanillaItemsWeapons.crabclawMaul, VanillaItemsWeapons.dualClaw, VanillaItemsWeapons.aquatoothSword, VanillaItemsWeapons.aquatoothMaul,
                VanillaItemsWeapons.crabclawCannon, VanillaItemsWeapons.bowheadCannon, VanillaItemsWeapons.crabAnchor, VanillaItemsWeapons.sharkAnchor, VanillaItemsWeapons.bowheadAnchor,
                VanillaItemsWeapons.liopleurodonAnchor);
            sea = s;
        }
        return sea;
    }

    public static boolean seaWeapon(Item i) {
        return i != null && sea().contains(i);
    }

    /** Номер якоря (-1 - не якорь). */
    public static int anchor(Item i) {
        return i == null ? -1 : i == VanillaItemsWeapons.crabAnchor ? 0 : i == VanillaItemsWeapons.sharkAnchor ? 1 : i == VanillaItemsWeapons.bowheadAnchor ? 2 : i == VanillaItemsWeapons.liopleurodonAnchor ? 3 : -1;
    }

    /** Дальность гарпуна этого якоря без поправки на воду (для подсказки). */
    public static int range(int anchor) {
        return (int) RANGE[anchor];
    }

    /** Игрок мокрый: в воде или под дождём. В сумеречных мирах небо своё: там считается только ливень. */
    public static boolean wet(EntityPlayer p) {
        if (p.isInWater()) return true;
        if (TwilightWorld.of(p.dimension) == null) return p.isWet();
        return WorldWeather.rain(p.worldObj) && WorldWeather.open(p);
    }

    /** Снаряд, выпущенный морским оружием, помнит это: пока он летит, игрок мог сменить предмет в руке. */
    public static void tagShot(Entity shot) {
        if (!(shot instanceof EntityThrowable) || !(((EntityThrowable) shot).getThrower() instanceof EntityPlayer)) return;
        ItemStack held = ((EntityThrowable) shot).getThrower().getHeldItem();
        if (held == null || !seaWeapon(held.getItem())) return;
        shot.getEntityData().setBoolean(SEA, true);
        if (anchor(held.getItem()) >= 0) shot.getEntityData().setInteger(ANCHOR, anchor(held.getItem()));
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (!e.world.isRemote) tagShot(e.entity);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote || !(e.source.getEntity() instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) e.source.getEntity();
        Entity via = e.source.getSourceOfDamage();
        boolean melee = via == p, shot = via != null && via.getEntityData().getBoolean(SEA);
        ItemStack held = p.getHeldItem();
        boolean seaHit = shot || melee && held != null && seaWeapon(held.getItem());
        if (seaHit && wet(p) || WorldWeather.rain(p.worldObj) && WorldWeather.open(p) && PerkEffects.wearsWaterArmor(p)) e.ammount *= WET_BONUS;
        long now = p.worldObj.getTotalWorldTime();
        if (melee && held != null && anchor(held.getItem()) >= 0 && p.getEntityData().getLong(HIT_UNTIL) > now) {
            e.ammount *= HARPOON_HIT;
            p.getEntityData().removeTag(HIT_UNTIL);
        }
        if (shot && via.getEntityData().hasKey(ANCHOR)) harpoon(p, e.entityLiving, via.getEntityData().getInteger(ANCHOR), now);
    }

    private static boolean tall(EntityLivingBase t) {
        return t instanceof IBossDisplayData || t instanceof com.divinerpgextended.entity.mini.EntityMiniBoss || Boss.of(t) != null;
    }

    private static boolean pullable(EntityPlayer p, EntityLivingBase t) {
        if (t instanceof EntityPlayer && (!Config.meleeRolesPvp || Protection.spare(p, t))) return false;
        return t.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).getAttributeValue() < 1.0;
    }

    private static void harpoon(EntityPlayer p, EntityLivingBase t, int kind, long now) {
        if (p.getEntityData().hasKey(PULL_AT) && now - p.getEntityData().getLong(PULL_AT) < HARPOON_EVERY) return;
        double range = RANGE[kind] * (p.isInWater() ? WATER_RANGE : 1);
        if (p.getDistanceToEntity(t) > range) return;
        if (tall(t)) {
            PULLS.add(new Object[] { p, t });
        } else {
            if (!pullable(p, t)) return;
            PULLS.add(new Object[] { t, p });
            if (kind == 2) t.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 20, 9));
            if (kind == 3) {
                int more = 2;
                for (Object o : t.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, t.boundingBox.expand(SWARM, SWARM, SWARM))) {
                    EntityLivingBase x = (EntityLivingBase) o;
                    if (more == 0) break;
                    if (x == t || x == p || !(x instanceof IMob) || tall(x) || !pullable(p, x)) continue;
                    PULLS.add(new Object[] { x, p });
                    more--;
                }
            }
        }
        p.getEntityData().setLong(PULL_AT, now);
        p.getEntityData().setLong(HIT_UNTIL, now + HARPOON_WINDOW);
        p.worldObj.playSoundAtEntity(p, "random.bowhit", 1.0F, 0.6F);
    }

    /** Дёрнуть всех, кого зацепили: скорость рассчитана на остановку рядом с тем, к кому тянет. */
    public static void flushPulls() {
        for (Object[] pull : PULLS) {
            Entity who = (Entity) pull[0], to = (Entity) pull[1];
            if (who.isDead || to.isDead) continue;
            double dx = to.posX - who.posX, dz = to.posZ - who.posZ, far = Math.sqrt(dx * dx + dz * dz), d = Math.max(0.5, far);
            double push = Math.min(2.0, far * 0.18);
            who.motionX = dx / d * push;
            who.motionZ = dz / d * push;
            who.motionY = 0.35;
            who.isAirBorne = true;
            who.velocityChanged = true;
        }
        PULLS.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !PULLS.isEmpty()) flushPulls();
    }
}
