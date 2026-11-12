package com.divinerpgextended.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.utils.events.ArcanaHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Призывы на аркане: слизневый меч зовёт слизня, крабовый якорь (с Карцинуса) - краба, циклопический жезл - око, якорь
 * лиоплевродона (с Лиоплевродона) - акулу; ПКМ - только призыв (броска у якорей и выстрела у жезла нет, меч бьёт как раньше).
 * Как призрак кости призрака ({@link ArcaneSummons}): держатся, пока хозяин платит арканой ({@link Kind#upkeep} в секунду за
 * каждого), бьют то, что бьёт хозяин (или того, кто напал на хозяина), а сами - ближайшего врага в своём радиусе агра
 * ({@link Kind#aggro}); не ранятся и не сохраняются; одновременно не больше {@link #cap} каждого вида (полный сет Кормы или
 * Венец арканы R8 - на одного больше), лишний - самый старый. Лестница призывов: слизень - краб - око - акула - призрак.
 * <p>
 * С 0.99.120: призывы одного хозяина - только одного вида (позвал краба - остальные, и призраки тоже, уходят); ПКМ по своему
 * призыву или удар свистком - убрать; свисток зовёт их к хозяину; наземные (слизень, краб) толкаются друг с другом и при
 * переносе к хозяину встают кольцом.
 * <p>
 * С 0.99.121 плата одна на всех призывов хозяина раз в секунду ({@link #pay}): {@link #UPKEEP} за каждого, обычной тратой
 * арканы - с паузой восстановления (пока призывы живы, аркана сама не восстанавливается), со скидкой Кормы и Собранности.
 * Без улучшений два призыва на 200 арканы живут 10 секунд; дольше - Корма (дешевле, запас больше), кристалл ясности (пауза
 * короче), задания Ваттикуса (запас больше), Венец арканы R8 (восстановление вдвое быстрее), зелья арканы (Ясность: без паузы).
 */
public class AnchorSummons {

    /** Цена призыва в аркане за секунду - у всех одна (и у призрака). */
    public static final float UPKEEP = 10F;

    public enum Kind {
        /** Слизень слизневого меча: прыгает по земле, агр ближе всех. */
        SLIME(UPKEEP, 2, 3F, 6),
        /** Бегает по земле. */
        CRAB(UPKEEP, 2, 4F, 8),
        /** Око циклопического жезла: спутник над головой, бьёт слабее всех, но отбрасывает врага. */
        EYE(UPKEEP, 2, 2F, 10),
        /** Летает, как призрак. */
        SHARK(UPKEEP, 2, 8F, 12);

        public final float upkeep, damage;
        public final int cap;
        /** Радиус агра (блоки от хозяина): сам бросается на врагов в нём; цель хозяина дальше - не преследует. */
        public final double aggro;

        Kind(float upkeep, int cap, float damage, double aggro) {
            this.upkeep = upkeep;
            this.cap = cap;
            this.damage = damage;
            this.aggro = aggro;
        }
    }

    /** Призыв на аркане хозяина: какой он вид. */
    public interface Summon {

        Kind kind();

        /** Хозяин или null (на клиенте - всегда null). */
        EntityPlayer owner();
    }

    /** Призыватель: полный сет Кормы или Венец арканы R8 - каждого призыва на одного больше. */
    public static boolean summoner(EntityPlayer p) {
        return p != null && (ArcanaRules.korma(p) || com.divinerpgextended.item.ArmorData.equippedCrown(p) == com.divinerpgextended.item.ArmorCrown.ARCANA);
    }

    /** Сколько призывов вида держит игрок. */
    public static int cap(EntityPlayer p, Kind k) {
        return k.cap + (summoner(p) ? 1 : 0);
    }

    /** Убрать призыв (ПКМ хозяина, свисток): искры и звук. */
    public static void dismiss(Entity e) {
        if (e.worldObj instanceof net.minecraft.world.WorldServer)
            ((net.minecraft.world.WorldServer) e.worldObj).func_147487_a("witchMagic", e.posX, e.posY + e.height / 2, e.posZ, 16, e.width * 0.4, e.height * 0.4, e.width * 0.4, 0.05);
        e.worldObj.playSoundAtEntity(e, "random.pop", 0.5F, 0.6F);
        e.setDead();
    }

    /** Куда встать призыву при переносе к хозяину: кольцом, чтобы не стоять друг в друге. */
    static double[] near(EntityPlayer o, int index) {
        double a = index * 2.4 + 0.6;
        return new double[] { o.posX + Math.cos(a) * 1.3, o.posY, o.posZ + Math.sin(a) * 1.3 };
    }

    /** Отброс удара ока (скорость, блоки за тик): боссам - {@link #EYE_PUSH_BOSS} от него. */
    public static final double EYE_PUSH = 0.9, EYE_PUSH_BOSS = 0.25;

    public static final int MEMORY = 200, LEASH = 48;
    static final Map<UUID, List<EntityLiving>> LIVE = new HashMap<UUID, List<EntityLiving>>();
    static final Map<UUID, Object[]> TARGET = new HashMap<UUID, Object[]>();

    public static Kind kind(Item i) {
        if (i == null) return null;
        if (i == net.divinerpg.utils.items.VanillaItemsWeapons.slimeSword) return Kind.SLIME;
        if (i == net.divinerpg.utils.items.VanillaItemsWeapons.cyclopsianStaff) return Kind.EYE;
        if (i == net.divinerpg.utils.items.VanillaItemsWeapons.crabAnchor) return Kind.CRAB;
        if (i == net.divinerpg.utils.items.VanillaItemsWeapons.liopleurodonAnchor) return Kind.SHARK;
        return null;
    }

    static Kind kind(EntityLiving e) {
        return e instanceof Summon ? ((Summon) e).kind() : Kind.SHARK;
    }

    static List<EntityLiving> of(EntityPlayer p) {
        List<EntityLiving> l = LIVE.get(p.getUniqueID());
        if (l == null) LIVE.put(p.getUniqueID(), l = new ArrayList<EntityLiving>());
        for (java.util.Iterator<EntityLiving> it = l.iterator(); it.hasNext();) if (it.next().isDead) it.remove();
        return l;
    }

    public static int count(EntityPlayer p, Kind k) {
        int n = 0;
        for (EntityLiving e : of(p)) if (kind(e) == k) n++;
        return n;
    }

    static int index(EntityLiving e, EntityPlayer o) {
        return o == null ? 0 : Math.max(0, of(o).indexOf(e));
    }

    public static void dismissAll(EntityPlayer p) {
        for (EntityLiving e : new ArrayList<EntityLiving>(of(p))) dismiss(e);
        LIVE.remove(p.getUniqueID());
    }

    /** Призвать у хозяина. */
    public static EntityLiving summon(EntityPlayer p, Kind k) {
        List<EntityLiving> l = of(p);
        // призывы одного хозяина - одного вида: другие виды и призраки уходят
        for (EntityLiving o : new ArrayList<EntityLiving>(l)) if (kind(o) != k) {
            dismiss(o);
            l.remove(o);
        }
        ArcaneSummons.dismissAll(p);
        int cap = cap(p, k);
        while (count(p, k) >= cap) for (EntityLiving e : l) if (kind(e) == k) {
            e.setDead();
            l.remove(e);
            break;
        }
        EntityLiving e;
        switch (k) {
            case SLIME:
                e = new EntitySummonSlime(p.worldObj, p);
                break;
            case CRAB:
                e = new EntitySummonCrab(p.worldObj, p);
                break;
            case EYE:
                e = new EntitySummonEye(p.worldObj, p);
                break;
            default:
                e = new EntitySummonShark(p.worldObj, p);
        }
        boolean flies = k == Kind.SHARK || k == Kind.EYE;
        e.setLocationAndAngles(p.posX + p.worldObj.rand.nextGaussian(), p.posY + (flies ? 1.5 : 0.2), p.posZ + p.worldObj.rand.nextGaussian(), p.rotationYaw, 0);
        p.worldObj.spawnEntityInWorld(e);
        l.add(e);
        String[] snd = { "mob.slime.small", "mob.silverfish.say", "random.orb", "random.splash" };
        float[] pitch = { 0.8F, 0.6F, 0.5F, 1.2F };
        p.worldObj.playSoundAtEntity(e, snd[k.ordinal()], 0.6F, pitch[k.ordinal()]);
        return e;
    }

    /** Цель призывов хозяина (или null). */
    static Entity target(EntityPlayer p) {
        Object[] t = TARGET.get(p.getUniqueID());
        if (t == null) return null;
        Entity e = (Entity) t[0];
        if (e.isDead || !(e instanceof EntityLivingBase) || ((EntityLivingBase) e).getHealth() <= 0 || e.worldObj != p.worldObj
            || p.worldObj.getTotalWorldTime() - (Long) t[1] > MEMORY || e.getDistanceSqToEntity(p) > 24 * 24) {
            TARGET.remove(p.getUniqueID());
            return null;
        }
        return e;
    }

    /**
     * Цель призыва: цель хозяина, если она в радиусе агра r от хозяина, иначе свой враг в этом радиусе (ищется раз в
     * {@link Aim#SCAN} тиков). Держит один призыв - у каждого своя.
     */
    public static final class Aim {

        public static final int SCAN = 10;
        private EntityLivingBase auto;
        private int wait;

        public Entity pick(EntityPlayer o, Entity marked, double r) {
            if (marked != null && marked.getDistanceSqToEntity(o) <= r * r) return marked;
            if (auto != null && (!auto.isEntityAlive() || auto.worldObj != o.worldObj || auto.getDistanceSqToEntity(o) > r * r)) auto = null;
            if (auto == null && --wait <= 0) {
                wait = SCAN;
                auto = foe(o, r);
            }
            return auto;
        }
    }

    /**
     * Ближайший к хозяину враг в радиусе r, которого хозяин видит: монстры, кроме призывов, приручённых, манекена и
     * нейтральных, пока те не злы (свинозомби, эндермены - иначе призыв поднимал бы на хозяина всю стаю).
     */
    public static EntityLivingBase foe(EntityPlayer o, double r) {
        EntityLivingBase best = null;
        double bd = r * r;
        for (Object x : o.worldObj.getEntitiesWithinAABB(EntityLiving.class, o.boundingBox.expand(r, r / 2 + 2, r))) {
            EntityLiving m = (EntityLiving) x;
            if (!m.isEntityAlive() || !(m instanceof net.minecraft.entity.monster.IMob) || m instanceof Summon || m instanceof EntityArcaneWraith
                || m instanceof com.divinerpgextended.dummy.EntityTrainingDummy) continue;
            if (m instanceof net.minecraft.entity.passive.EntityTameable && ((net.minecraft.entity.passive.EntityTameable) m).isTamed()) continue;
            if (m instanceof net.minecraft.entity.monster.EntityPigZombie && ((net.minecraft.entity.monster.EntityPigZombie) m).getEntityToAttack() == null) continue;
            if (m instanceof net.minecraft.entity.monster.EntityEnderman && !((net.minecraft.entity.monster.EntityEnderman) m).isScreaming()) continue;
            double d = m.getDistanceSqToEntity(o);
            if (d < bd && o.canEntityBeSeen(m)) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    public static void mark(EntityPlayer p, Entity t) {
        if (t instanceof Summon || t == p) return;
        TARGET.put(p.getUniqueID(), new Object[] { t, p.worldObj.getTotalWorldTime() });
    }

    /**
     * Плата за секунду жизни всех призывов хозяина сразу ({@link #UPKEEP} за каждого) - обычной тратой арканы: включает паузу
     * восстановления, со скидкой Кормы и Собранности. Одна трата на всех, иначе каждая заново сбрасывала бы паузу. Не хватило -
     * false (ничего не тратится).
     */
    public static boolean pay(EntityPlayer o, int count) {
        if (o.capabilities.isCreativeMode || count <= 0) return true;
        ArcanaHelper h = ArcanaHelper.getProperties(o);
        return h != null && ArcanaRules.useBar(h, UPKEEP * count, "Summon");
    }

    /** Удар призыва: урон вида, растёт со стадией (как у призрака). */
    static float damage(EntityPlayer o, Kind k) {
        return k.damage * ArcanaRules.stage(o);
    }

    /** Подсказка призывных якорей. */
    @SubscribeEvent
    public void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent e) {
        Kind k = e.itemStack == null ? null : kind(e.itemStack.getItem());
        if (k == null) return;
        e.toolTip.add(net.minecraft.util.EnumChatFormatting.LIGHT_PURPLE + net.minecraft.util.StatCollector.translateToLocalFormatted("dre.tooltip.summon_" + k.name().toLowerCase(),
            (int) k.upkeep, cap(e.entityPlayer, k)));
        e.toolTip.add(net.minecraft.util.EnumChatFormatting.GRAY + net.minecraft.util.StatCollector.translateToLocalFormatted("dre.tooltip.summon_stats",
            String.format("%.0f", damage(e.entityPlayer, k)), (int) k.aggro));
    }

    /**
     * ПКМ призывным якорем: призыв вместо броска. Меч и жезл зовут только кликом в воздух (клик по блоку тоже приходит
     * в воздух, если блок ничего не сделал): иначе сундук открывался бы вместе с призывом.
     */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        if (e.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        EntityPlayer p = e.entityPlayer;
        ItemStack held = p.getHeldItem();
        Kind k = held == null ? null : kind(held.getItem());
        if (k == null) return;
        if ((k == Kind.SLIME || k == Kind.EYE) && e.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR) return;
        e.useItem = Event.Result.DENY;
        if (p.worldObj.isRemote) return;
        long now = p.worldObj.getTotalWorldTime();
        String key = "dreAnchorSummon" + (k == Kind.CRAB ? "" : k.name()); // у каждого вида свой: слизень и око подряд - оба
        if (p.getEntityData().hasKey(key) && now - p.getEntityData().getLong(key) < 10 && now >= p.getEntityData().getLong(key))
            return; // двойной пакет от одного клика
        p.getEntityData().setLong(key, now);
        summon(p, k);
    }

    /** Хозяин бьёт - призывы туда же. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        Entity a = e.source.getEntity();
        if (a instanceof EntityPlayer && !(e.entityLiving instanceof EntityPlayer) && LIVE.containsKey(a.getUniqueID())) mark((EntityPlayer) a, e.entityLiving);
    }

    /** На хозяина нацелились - призывы на этого врага (если хозяин сам никого не бьёт). */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (!(e.target instanceof EntityPlayer) || !(e.entityLiving instanceof EntityLiving) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.target;
        if (LIVE.containsKey(p.getUniqueID()) && target(p) == null) mark(p, e.entityLiving);
    }
}
