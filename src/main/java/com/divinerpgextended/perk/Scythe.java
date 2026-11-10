package com.divinerpgextended.perk;

import java.util.List;
import java.util.Random;

import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Коса Джек-О-Мена (у Divine: ПКМ - бросок крутящейся косы, 6 урона, с полным сетом Джек-О-Мена 18).
 * <ul>
 * <li>Страх: попадание с шансом {@link #FEAR_CHANCE} (полный сет Джек-О-Мена - {@link #FEAR_CHANCE_JACK}): моб
 * {@link #FEAR_TICKS} тиков убегает от бросившего и никого не бьёт, потом {@link #FEAR_IMMUNE} тиков страх его не берёт
 * (иначе бесконечная коса держала бы толпу в страхе без конца). Боссы, мини-боссы и игроки не боятся.</li>
 * <li>Полный сет скелета: с шансом {@link #PROVOKE_CHANCE} попадание провоцирует - враждебные мобы в {@link #PROVOKE_R}
 * блоках {@link #PROVOKE_TICKS} тиков бьют того, в кого попала коса (как мобы, в которых попал скелет).</li>
 * <li>Полный сет Увядшего жнеца: с шансом {@link #CHARM_CHANCE} моб {@link #CHARM_TICKS} тиков воюет за бросившего: бьёт
 * враждебных мобов рядом, игроков, мирных и боссов не трогает (урон по ним отменяется); убитые им - как убитые игроком
 * (опыт и добыча). Потом {@link #CHARM_IMMUNE} тиков не подчиняется. Крипер под подчинением не взрывается.</li>
 * </ul>
 * Драка навязанных целей ведётся и вручную: у зомби и многих мобов удар ванили бьёт только игроков и жителей, поэтому, если
 * моб сам не ударил цель за секунду, удар наносим за него (тем же уроном, что у моба).
 */
public class Scythe {

    public static final float FEAR_CHANCE = 0.2F, FEAR_CHANCE_JACK = 0.5F, PROVOKE_CHANCE = 0.25F, CHARM_CHANCE = 0.25F;
    public static final int FEAR_TICKS = 60, FEAR_IMMUNE = 100, PROVOKE_TICKS = 100, CHARM_TICKS = 60, CHARM_IMMUNE = 200;
    public static final double PROVOKE_R = 8, CHARM_R = 12;
    static final double FLEE_SPEED = 1.3, CHASE_SPEED = 1.2;

    static final String TAG = "dreScythe", FEAR_UNTIL = "dreFearUntil", FEAR_IMM = "dreFearImmune", FEAR_X = "dreFearX", FEAR_Y = "dreFearY",
        FEAR_Z = "dreFearZ", FEAR_BY = "dreFearBy", FIGHT_UNTIL = "dreFightUntil", FIGHT_TARGET = "dreFightTarget", CHARM_BY = "dreCharmBy",
        CHARM_IMM = "dreCharmImmune", LAST_HIT = "dreFightHit";

    /** Для автотеста: бросок шанса (null - случайный). */
    public static Float testRoll;
    private static final Random RAND = new Random();

    private static float roll() {
        return testRoll != null ? testRoll : RAND.nextFloat();
    }

    // ---------------- сеты ----------------

    private static boolean wears(EntityPlayer p, Item boots, Item legs, Item body, Item helmet) {
        Item[] want = { boots, legs, body, helmet };
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.inventory.armorItemInSlot(i);
            if (s == null || s.getItem() != want[i]) return false;
        }
        return true;
    }

    public static boolean jackSet(EntityPlayer p) {
        return wears(p, VanillaItemsArmor.jackOManBoots, VanillaItemsArmor.jackOManLegs, VanillaItemsArmor.jackOManBody, VanillaItemsArmor.jackOManHelmet);
    }

    public static boolean skelemanSet(EntityPlayer p) {
        return wears(p, VanillaItemsArmor.skelemanBoots, VanillaItemsArmor.skelemanLegs, VanillaItemsArmor.skelemanBody, VanillaItemsArmor.skelemanHelmet);
    }

    public static boolean reaperSet(EntityPlayer p) {
        return wears(p, VanillaItemsArmor.witherReaperBoots, VanillaItemsArmor.witherReaperLegs, VanillaItemsArmor.witherReaperBody,
            VanillaItemsArmor.witherReaperHelmet);
    }

    public static float fearChance(EntityPlayer p) {
        return jackSet(p) ? FEAR_CHANCE_JACK : FEAR_CHANCE;
    }

    static boolean boss(Entity e) {
        return RangedRoles.boss(e) || e instanceof com.divinerpgextended.entity.mini.EntityMiniBoss;
    }

    private static long now(Entity e) {
        return e.worldObj.getTotalWorldTime();
    }

    // ---------------- попадание ----------------

    /** Коса Divine - снаряд EntityShooterBullet; отличаем по косе в руке бросившего. */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof net.divinerpg.entities.vanilla.projectile.EntityShooterBullet)) return;
        EntityLivingBase t = ((EntityThrowable) e.entity).getThrower();
        if (!(t instanceof EntityPlayer)) return;
        ItemStack h = t.getHeldItem();
        if (h != null && h.getItem() == net.divinerpg.utils.items.VanillaItemsWeapons.scythe) e.entity.getEntityData().setBoolean(TAG, true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.entityLiving;
        if (t.worldObj.isRemote) return;
        Entity a = e.source.getEntity();
        // удар подчинённого моба засчитывается хозяину (опыт и добыча)
        if (a instanceof EntityLiving && a.getEntityData().hasKey(FIGHT_UNTIL)) {
            a.getEntityData().setLong(LAST_HIT, now(a));
            EntityPlayer owner = charmOwner((EntityLiving) a);
            if (owner != null) credit(t, owner);
        }
        Entity src = e.source.getSourceOfDamage();
        if (src == null || !src.getEntityData().getBoolean(TAG) || !(a instanceof EntityPlayer) || !(t instanceof EntityLiving)) return;
        hit((EntityLiving) t, (EntityPlayer) a);
    }

    /** Попадание косой игрока p по мобу t: подчинение (сет жнеца), страх, провокация (сет скелета). */
    public static void hit(EntityLiving t, EntityPlayer p) {
        if (boss(t) || Protection.spare(p, t)) return;
        if (reaperSet(p) && roll() < CHARM_CHANCE && charm(t, p)) return;
        if (roll() < fearChance(p)) fear(t, p);
        if (skelemanSet(p) && roll() < PROVOKE_CHANCE) provoke(t, p);
    }

    /** Напугать: false - моб не боится (босс, игрок, ещё не отошёл от прошлого страха). */
    public static boolean fear(EntityLiving t, EntityPlayer from) {
        if (boss(t) || t.isDead) return false;
        NBTTagCompound d = t.getEntityData();
        long now = now(t);
        if (now < d.getLong(FEAR_UNTIL) + FEAR_IMMUNE && d.hasKey(FEAR_UNTIL)) return false;
        d.setLong(FEAR_UNTIL, now + FEAR_TICKS);
        d.setInteger(FEAR_BY, from.getEntityId());
        d.setDouble(FEAR_X, from.posX);
        d.setDouble(FEAR_Y, from.posY);
        d.setDouble(FEAR_Z, from.posZ);
        t.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, FEAR_TICKS, 0));
        calm(t);
        flee(t);
        t.playSound("mob.ghast.scream", 0.4F, 1.6F);
        return true;
    }

    public static boolean afraid(EntityLivingBase t) {
        return t.getEntityData().hasKey(FEAR_UNTIL) && now(t) < t.getEntityData().getLong(FEAR_UNTIL);
    }

    /** Мобы вокруг цели бросаются на неё. @return сколько спровоцировано */
    public static int provoke(EntityLiving t, EntityPlayer p) {
        int n = 0;
        @SuppressWarnings("unchecked")
        List<EntityLiving> near = t.worldObj.getEntitiesWithinAABB(EntityLiving.class, t.boundingBox.expand(PROVOKE_R, PROVOKE_R / 2, PROVOKE_R));
        for (EntityLiving m : near) {
            if (m == t || !(m instanceof IMob) || boss(m) || m.isDead || charmed(m)) continue;
            fight(m, t, PROVOKE_TICKS);
            n++;
        }
        if (n > 0 && t.worldObj instanceof WorldServer) ((WorldServer) t.worldObj).func_147487_a("angryVillager", t.posX, t.posY + t.height + 0.3, t.posZ, 4,
            t.width * 0.4, 0.2, t.width * 0.4, 0.02);
        return n;
    }

    /** Подчинить моба игроку на CHARM_TICKS. false - не подчиняется (босс, не враждебный, недавно был подчинён). */
    public static boolean charm(EntityLiving m, EntityPlayer p) {
        if (boss(m) || !(m instanceof IMob) || m.isDead) return false;
        NBTTagCompound d = m.getEntityData();
        long now = now(m);
        if (d.hasKey(CHARM_IMM) && now < d.getLong(CHARM_IMM)) return false;
        d.setInteger(CHARM_BY, p.getEntityId());
        d.setLong(CHARM_IMM, now + CHARM_TICKS + CHARM_IMMUNE);
        d.removeTag(FEAR_UNTIL); // подчинённый не боится
        EntityLivingBase target = enemyNear(m, p);
        fight(m, target, CHARM_TICKS);
        if (target == null) calm(m);
        m.playSound("mob.wither.ambient", 0.4F, 1.8F);
        return true;
    }

    /** Подчинён ли сейчас (хозяин мог и уйти из игры - запреты всё равно действуют). */
    public static boolean charmed(EntityLivingBase m) {
        NBTTagCompound d = m.getEntityData();
        return d.hasKey(CHARM_BY) && now(m) < d.getLong(FIGHT_UNTIL);
    }

    /** Чей подчинённый (null - ничей или подчинение кончилось). */
    public static EntityPlayer charmOwner(EntityLiving m) {
        NBTTagCompound d = m.getEntityData();
        if (!d.hasKey(CHARM_BY) || now(m) >= d.getLong(FIGHT_UNTIL)) return null;
        Entity o = m.worldObj.getEntityByID(d.getInteger(CHARM_BY));
        return o instanceof EntityPlayer ? (EntityPlayer) o : null;
    }

    /** Ближний враждебный моб для подчинённого: не он сам, не босс, не подчинённый того же игрока. */
    static EntityLivingBase enemyNear(EntityLiving m, EntityPlayer owner) {
        @SuppressWarnings("unchecked")
        List<EntityLiving> near = m.worldObj.getEntitiesWithinAABB(EntityLiving.class, m.boundingBox.expand(CHARM_R, CHARM_R / 2, CHARM_R));
        EntityLiving best = null;
        double bd = Double.MAX_VALUE;
        for (EntityLiving o : near) {
            if (o == m || !(o instanceof IMob) || boss(o) || o.isDead || o.getHealth() <= 0 || charmed(o)) continue;
            double dd = o.getDistanceSqToEntity(m);
            if (dd < bd) {
                bd = dd;
                best = o;
            }
        }
        return best;
    }

    /** Навязать мобу цель на ticks тиков. */
    static void fight(EntityLiving m, EntityLivingBase target, int ticks) {
        NBTTagCompound d = m.getEntityData();
        d.setLong(FIGHT_UNTIL, now(m) + ticks);
        if (target != null) {
            d.setInteger(FIGHT_TARGET, target.getEntityId());
            aim(m, target);
        } else d.removeTag(FIGHT_TARGET);
    }

    /** Опыт и добыча с цели - игроку (как если бы бил он). */
    private static java.lang.reflect.Field attackingPlayer, recentlyHit;

    static void credit(EntityLivingBase t, EntityPlayer owner) {
        try {
            if (attackingPlayer == null) {
                attackingPlayer = cpw.mods.fml.relauncher.ReflectionHelper.findField(EntityLivingBase.class, "attackingPlayer", "field_70717_bb");
                recentlyHit = cpw.mods.fml.relauncher.ReflectionHelper.findField(EntityLivingBase.class, "recentlyHit", "field_70718_bc");
            }
            attackingPlayer.set(t, owner);
            recentlyHit.setInt(t, 100);
        } catch (Throwable ignored) {}
    }

    // ---------------- ход моба ----------------

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (!(e.entityLiving instanceof EntityLiving) || e.entityLiving.worldObj.isRemote) return;
        EntityLiving m = (EntityLiving) e.entityLiving;
        NBTTagCompound d = m.getEntityData();
        if (!d.hasKey(FEAR_UNTIL) && !d.hasKey(FIGHT_UNTIL)) return;
        tick(m);
    }

    /** Ход страха и навязанной драки (открыт для автотеста). */
    public static void tick(EntityLiving m) {
        NBTTagCompound d = m.getEntityData();
        long now = now(m);
        if (d.hasKey(FEAR_UNTIL)) {
            if (now < d.getLong(FEAR_UNTIL)) {
                calm(m);
                if (m.ticksExisted % 10 == 0) flee(m);
                if (m.ticksExisted % 10 == 0 && m.worldObj instanceof WorldServer) ((WorldServer) m.worldObj).func_147487_a("smoke", m.posX,
                    m.posY + m.height + 0.2, m.posZ, 3, m.width * 0.3, 0.1, m.width * 0.3, 0.01);
                return; // испуганный не дерётся
            }
            if (now > d.getLong(FEAR_UNTIL) + FEAR_IMMUNE) d.removeTag(FEAR_UNTIL);
        }
        if (!d.hasKey(FIGHT_UNTIL)) return;
        boolean charmed = d.hasKey(CHARM_BY);
        if (now >= d.getLong(FIGHT_UNTIL)) {
            release(m);
            return;
        }
        Entity t = d.hasKey(FIGHT_TARGET) ? m.worldObj.getEntityByID(d.getInteger(FIGHT_TARGET)) : null;
        if (!(t instanceof EntityLivingBase) || t.isDead || ((EntityLivingBase) t).getHealth() <= 0) {
            t = null;
            if (charmed) {
                EntityPlayer owner = charmOwner(m);
                t = owner == null ? null : enemyNear(m, owner);
                if (t != null) d.setInteger(FIGHT_TARGET, t.getEntityId());
            }
            if (t == null) {
                if (charmed) {
                    calm(m); // подчинённый без врага рядом просто стоит
                    return;
                }
                release(m);
                return;
            }
        }
        if (charmed && m instanceof EntityCreeper) ((EntityCreeper) m).setCreeperState(-1);
        drive(m, (EntityLivingBase) t);
    }

    /** Снять навязанную драку (подчинение тоже). */
    static void release(EntityLiving m) {
        NBTTagCompound d = m.getEntityData();
        Entity t = d.hasKey(FIGHT_TARGET) ? m.worldObj.getEntityByID(d.getInteger(FIGHT_TARGET)) : null;
        if (t != null && m.getAttackTarget() == t) m.setAttackTarget(null);
        if (t != null && m instanceof EntityCreature && ((EntityCreature) m).getEntityToAttack() == t) ((EntityCreature) m).setTarget(null);
        d.removeTag(FIGHT_UNTIL);
        d.removeTag(FIGHT_TARGET);
        d.removeTag(CHARM_BY);
        d.removeTag(LAST_HIT);
    }

    /** Без цели: ни нового ИИ, ни старого; крипер гасит запал. */
    static void calm(EntityLiving m) {
        if (m.getAttackTarget() != null) m.setAttackTarget(null);
        if (m instanceof EntityCreature && ((EntityCreature) m).getEntityToAttack() != null) ((EntityCreature) m).setTarget(null);
        if (m instanceof EntityCreeper) ((EntityCreeper) m).setCreeperState(-1);
    }

    private static void aim(EntityLiving m, EntityLivingBase t) {
        if (m.getAttackTarget() != t) m.setAttackTarget(t);
        if (m instanceof EntityCreature && ((EntityCreature) m).getEntityToAttack() != t) ((EntityCreature) m).setTarget(t);
    }

    private static java.lang.reflect.Method aiEnabled;

    /** Новый ли ИИ у моба (isAIEnabled защищён); не узнали - считаем, что новый. */
    static boolean newAi(EntityLiving m) {
        try {
            if (aiEnabled == null) aiEnabled = cpw.mods.fml.relauncher.ReflectionHelper.findMethod(EntityLivingBase.class, m,
                new String[] { "isAIEnabled", "func_70650_aV" });
            return (Boolean) aiEnabled.invoke(m);
        } catch (Throwable t) {
            return true;
        }
    }

    /** Бежать от того, кто напугал. */
    static void flee(EntityLiving m) {
        if (!(m instanceof EntityCreature)) return;
        EntityCreature c = (EntityCreature) m;
        NBTTagCompound d = m.getEntityData();
        Entity by = m.worldObj.getEntityByID(d.getInteger(FEAR_BY));
        if (by != null && by.getDistanceSqToEntity(m) < 48 * 48) {
            d.setDouble(FEAR_X, by.posX);
            d.setDouble(FEAR_Y, by.posY);
            d.setDouble(FEAR_Z, by.posZ);
        }
        Vec3 from = Vec3.createVectorHelper(d.getDouble(FEAR_X), d.getDouble(FEAR_Y), d.getDouble(FEAR_Z));
        Vec3 v = RandomPositionGenerator.findRandomTargetBlockAwayFrom(c, 16, 7, from);
        if (v == null) return;
        if (newAi(c)) c.getNavigator().tryMoveToXYZ(v.xCoord, v.yCoord, v.zCoord, FLEE_SPEED);
        else c.setPathToEntity(c.worldObj.getEntityPathToXYZ(c, (int) v.xCoord, (int) v.yCoord, (int) v.zCoord, 20F, true, false, false, true));
    }

    /** Вести моба на цель и бить её, если его собственный удар её не берёт. */
    static void drive(EntityLiving m, EntityLivingBase t) {
        aim(m, t);
        NBTTagCompound d = m.getEntityData();
        double reach = m.width * 2.0F * m.width * 2.0F + t.width, dd = m.getDistanceSq(t.posX, t.boundingBox.minY, t.posZ);
        if (dd > reach) {
            if (m.ticksExisted % 10 != 0 || m instanceof IRangedAttackMob) return;
            if (newAi(m)) m.getNavigator().tryMoveToEntityLiving(t, CHASE_SPEED);
            else if (m instanceof EntityCreature) ((EntityCreature) m).setPathToEntity(m.worldObj.getPathEntityToEntity(m, t, 20F, true, false, false, true));
            return;
        }
        if (m instanceof IRangedAttackMob) return;
        long now = now(m);
        if (d.hasKey(LAST_HIT) && now - d.getLong(LAST_HIT) < 20) return;
        d.setLong(LAST_HIT, now);
        m.swingItem();
        m.attackEntityAsMob(t);
    }

    // ---------------- запреты ----------------

    /** Испуганный никого не ранит; подчинённый ранит только враждебных мобов (не игроков, не мирных, не боссов). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        Entity a = e.source.getEntity();
        if (!(a instanceof EntityLiving) || a == e.entityLiving) return;
        EntityLiving m = (EntityLiving) a;
        if (afraid(m)) {
            e.setCanceled(true);
            return;
        }
        if (charmed(m) && (!(e.entityLiving instanceof IMob) || e.entityLiving instanceof EntityPlayer || boss(e.entityLiving))) e.setCanceled(true);
    }

    /** Испуганный не берёт цель; подчинённый не берёт целью игроков и мирных. */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (e.target == null || !(e.entityLiving instanceof EntityLiving) || e.entityLiving.worldObj.isRemote) return;
        EntityLiving m = (EntityLiving) e.entityLiving;
        if (afraid(m)) {
            calm(m);
            return;
        }
        if (charmed(m) && (!(e.target instanceof IMob) || e.target instanceof EntityPlayer)) {
            Entity t = m.getEntityData().hasKey(FIGHT_TARGET) ? m.worldObj.getEntityByID(m.getEntityData().getInteger(FIGHT_TARGET)) : null;
            if (t instanceof EntityLivingBase && !t.isDead) aim(m, (EntityLivingBase) t);
            else calm(m);
        }
    }
}
