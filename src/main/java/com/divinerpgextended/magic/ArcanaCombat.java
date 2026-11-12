package com.divinerpgextended.magic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.divinerpg.utils.events.ArcanaHelper;
import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.asm.ArcanaHooks;
import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Арканный урон и бой.
 * <ul>
 * <li>Снаряды арканного оружия игрока помечаются при появлении: урон из {@link #BASE} с ростом по этапу игрока
 * ({@link ArcanaRules#stage}). Такой урон идёт сквозь броню, снаряды одного залпа не перекрывают друг друга (у
 * Divine попадало одно), маги сумеречных миров держат его вдвое лучше.</li>
 * <li>Звёзды и метеоры падают с 12 блоков, а не с 25 (у Divine долетали слишком долго).</li>
 * <li>По игроку арканным уроном бьют выстрелы магов, снаряды Ветеи и ближние удары мобов Арканы: сначала его
 * принимает на себя аркана ({@link ArcanaRules#absorb}).</li>
 * <li>Маги сумеречных миров стреляют раз в 1-1.5 с и только видя цель (у Divine - дважды в секунду сквозь стены).</li>
 * <li>Мечи Арканы добавляют к удару арканный урон, молнии Storm Sword не бьют своего хозяина.</li>
 * <li>Шкала арканы переживает перезаход (Divine обнулял её при входе), эффект зелья восстанавливает без задержки.</li>
 * </ul>
 */
public class ArcanaCombat {

    public static final String DMG = "dreArcDmg", MOB = "dreArcMob", NEXT_SHOT = "dreNextShot", SAVED = "dreArcSaved";
    /** Базовый арканный урон снаряда по оружию в руке (на входе в Аркану). */
    static final Map<Item, Float> BASE = new HashMap<Item, Float>();
    /** Прибавка мечей Арканы к удару (арканный урон). */
    public static final float SWORD_BONUS = 8.0F, MAGE_RESIST = 0.5F;
    /** На сколько ниже Divine ставит падающие звёзды и метеоры (Divine - 25 блоков над целью). */
    static final double DROP_LOWER = 13.0;

    private static final Set<String> MAGES = new HashSet<String>();
    private static final Set<String> VETHEA_ARCANE = new HashSet<String>();

    static {
        for (String m : new String[] { "EntityMage", "EntityMystic", "EntitySorcerer", "EntitySpellbinder" }) MAGES.add("net.divinerpg.entities.twilight." + m);
        for (String v : new String[] { "net.divinerpg.entities.vethea.projectile.EntityBouncingProjectile",
            "net.divinerpg.entities.vethea.projectile.EntityWreckBouncingProjectile", "net.divinerpg.entities.vethea.EntityLadyLunaSparkler",
            "net.divinerpg.entities.twilight.projectile.EntityTwilightMageShot" }) VETHEA_ARCANE.add(v);
    }

    public static void init() {
        BASE.put(ArcanaItems.staffStarlight, 7F);
        BASE.put(ArcanaItems.starlight, 7F);
        BASE.put(ArcanaItems.meteorMash, 30F);
        BASE.put(ArcanaItems.captainsSparkler, 14F);
        BASE.put(ArcanaItems.firefly, 9F);
        BASE.put(ArcanaItems.meriksMissile, 35F);
        BASE.put(ArcanaItems.arcaniteBlaster, 3F);
        BASE.put(ArcanaItems.generalsStaff, 16F);
    }

    public static boolean isMage(Entity e) {
        return e != null && MAGES.contains(e.getClass().getName());
    }

    static boolean arcanaSword(Item i) {
        return i == ArcanaItems.arcaniumSaber || i == ArcanaItems.arcaniteBlade || i == ArcanaItems.shadowSword || i == ArcanaItems.stormSword
            || i == ArcanaItems.liviciaSword;
    }

    /** Базовый урон оружия в руке или null, если это не арканное оружие. */
    public static Float base(ItemStack held) {
        if (held == null) return null;
        if (held.getItem() instanceof ItemMageStaff) return ItemMageStaff.damage(held);
        return BASE.get(held.getItem());
    }

    /** Арканный урон снаряда этого игрока с этим оружием (с ростом по этапу). */
    public static float damage(EntityPlayer p, ItemStack held) {
        Float b = base(held);
        return b == null ? 0 : b * ArcanaRules.stage(p) * crown(p);
    }

    /** Венец арканы R8: арканный урон x{@link com.divinerpgextended.Config#crownArcanaDamage}. */
    public static float crown(EntityPlayer p) {
        return p != null && com.divinerpgextended.item.ArmorData.equippedCrown(p) == com.divinerpgextended.item.ArmorCrown.ARCANA
            ? com.divinerpgextended.Config.crownArcanaDamage : 1F;
    }

    // ---------------- снаряды ----------------

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof EntityThrowable) || !e.entity.getClass().getName().startsWith("net.divinerpg.")) return;
        EntityThrowable t = (EntityThrowable) e.entity;
        NBTTagCompound d = t.getEntityData();
        if (d.hasKey(DMG)) return;
        EntityLivingBase thrower = t.getThrower();
        String cls = t.getClass().getSimpleName();
        // звёзды и метеоры Divine создаёт без бросившего, высоко над целью: хозяин - ближний игрок с посохом
        if (thrower == null && ("EntityStar".equals(cls) || "EntityMeteor".equals(cls))) {
            EntityPlayer owner = null;
            double best = 40 * 40;
            for (Object o : e.world.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                ItemStack h = p.getHeldItem();
                if (h == null || (h.getItem() != ArcanaItems.staffStarlight && h.getItem() != ArcanaItems.starlight && h.getItem() != ArcanaItems.meteorMash)) continue;
                double dx = p.posX - t.posX, dz = p.posZ - t.posZ, dd = dx * dx + dz * dz;
                if (dd < best) {
                    best = dd;
                    owner = p;
                }
            }
            if (owner != null) {
                d.setFloat(DMG, damage(owner, owner.getHeldItem()));
                t.setPosition(t.posX, t.posY - DROP_LOWER, t.posZ);
            }
            return;
        }
        if (thrower instanceof EntityPlayer && "EntityMerikMissile".equals(cls)) aimAtArcanaBoss(t);
        if (thrower instanceof EntityPlayer && "EntityShooterBullet".equals(cls) && thrower.getHeldItem() != null
            && thrower.getHeldItem().getItem() == ArcanaItems.arcaniteBlaster) converge(t, (EntityPlayer) thrower);
        if (thrower instanceof EntityPlayer) {
            float dmg = damage((EntityPlayer) thrower, thrower.getHeldItem());
            if (dmg > 0) d.setFloat(DMG, dmg);
            else if (VETHEA_ARCANE.contains(t.getClass().getName())) d.setBoolean(MOB, true); // посохи Ветеи: свой урон, но арканный
            return;
        }
        if (VETHEA_ARCANE.contains(t.getClass().getName())) {
            if (isMage(thrower) && !mageMayShoot((EntityLiving) thrower)) {
                e.setCanceled(true);
                return;
            }
            d.setBoolean(MOB, true);
        }
    }

    /** Точка прицела игрока на этот тик (у взрывателя 30 пуль за раз - луч считаем один раз). */
    private static final Map<EntityPlayer, Object[]> AIM = new java.util.WeakHashMap<EntityPlayer, Object[]>();
    static final double AIM_RANGE = 32;

    /** Куда смотрит игрок: первое существо на луче, иначе блок, иначе точка на AIM_RANGE. */
    static net.minecraft.util.Vec3 aimPoint(EntityPlayer p) {
        long now = p.worldObj.getTotalWorldTime();
        Object[] c = AIM.get(p);
        if (c != null && (Long) c[0] == now) return (net.minecraft.util.Vec3) c[1];
        net.minecraft.util.Vec3 eye = net.minecraft.util.Vec3.createVectorHelper(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        net.minecraft.util.Vec3 look = p.getLookVec();
        net.minecraft.util.Vec3 far = eye.addVector(look.xCoord * AIM_RANGE, look.yCoord * AIM_RANGE, look.zCoord * AIM_RANGE);
        net.minecraft.util.MovingObjectPosition blk = p.worldObj.rayTraceBlocks(net.minecraft.util.Vec3.createVectorHelper(eye.xCoord, eye.yCoord, eye.zCoord),
            net.minecraft.util.Vec3.createVectorHelper(far.xCoord, far.yCoord, far.zCoord));
        double reach = blk != null && blk.hitVec != null ? blk.hitVec.distanceTo(eye) : AIM_RANGE;
        net.minecraft.util.Vec3 best = blk != null && blk.hitVec != null ? blk.hitVec : far;
        net.minecraft.util.AxisAlignedBB sweep = p.boundingBox.addCoord(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach).expand(1, 1, 1);
        for (Object o : p.worldObj.getEntitiesWithinAABBExcludingEntity(p, sweep)) {
            Entity en = (Entity) o;
            if (!(en instanceof EntityLivingBase) || !en.canBeCollidedWith()) continue;
            net.minecraft.util.MovingObjectPosition hit = en.boundingBox.expand(0.3, 0.3, 0.3).calculateIntercept(eye, far);
            if (hit == null) continue;
            double d = hit.hitVec.distanceTo(eye);
            if (d < reach) {
                reach = d;
                best = net.minecraft.util.Vec3.createVectorHelper(en.posX, en.boundingBox.minY + en.height * 0.55, en.posZ);
            }
        }
        AIM.put(p, new Object[] { now, best });
        return best;
    }

    /**
     * Пули взрывателя Арканы сходятся в точку прицела. Divine разбрасывает их не по направлению, а по точке старта (+-1.5
     * блока по каждой оси), и 30 пуль летят параллельным облаком шириной 3 блока: цель ловила лишь его часть на любой
     * дистанции, а стартовавшие в стене рядом гибли сразу. Скорость пули та же; стартовавшая в блоке или за преградой - вылетает от глаз.
     */
    static void converge(EntityThrowable b, EntityPlayer p) {
        net.minecraft.util.Vec3 aim = aimPoint(p);
        boolean inBlock = b.worldObj.getBlock(net.minecraft.util.MathHelper.floor_double(b.posX), net.minecraft.util.MathHelper.floor_double(b.posY),
            net.minecraft.util.MathHelper.floor_double(b.posZ)).getMaterial().blocksMovement();
        // в стене или путь до прицела перегорожен блоком - вылетает от глаз (оттуда до прицела чисто)
        if (inBlock || b.worldObj.rayTraceBlocks(net.minecraft.util.Vec3.createVectorHelper(b.posX, b.posY, b.posZ),
            net.minecraft.util.Vec3.createVectorHelper(aim.xCoord, aim.yCoord, aim.zCoord)) != null) b.setPosition(p.posX, p.posY + p.getEyeHeight() - 0.1, p.posZ);
        double dx = aim.xCoord - b.posX, dy = aim.yCoord - b.posY, dz = aim.zCoord - b.posZ, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double v = Math.sqrt(b.motionX * b.motionX + b.motionY * b.motionY + b.motionZ * b.motionZ);
        if (len < 0.01 || v < 0.01) return;
        b.motionX = dx / len * v;
        b.motionY = dy / len * v;
        b.motionZ = dz / len * v;
    }

    private static java.lang.reflect.Field seekTarget;

    /**
     * Ракета Мерика рядом с боссом Арканы сразу берёт целью его (Divine берёт ближнее живое и не меняет цель, пока та жива:
     * миньон или напарник рядом уводили ракету). Невидимость Драмикса самонаводке не мешает. Для автотеста - публичный.
     */
    public static net.minecraft.entity.Entity aimAtArcanaBoss(EntityThrowable missile) {
        net.minecraft.entity.Entity best = null;
        double bd = 50 * 50;
        for (Object o : missile.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, missile.boundingBox.expand(50, 50, 50))) {
            EntityLivingBase b = (EntityLivingBase) o;
            if (!ArcanePhases.arcanaBoss(b) || !b.isEntityAlive()) continue;
            double d = b.getDistanceSqToEntity(missile);
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        if (best == null) return null;
        try {
            if (seekTarget == null) seekTarget = cpw.mods.fml.relauncher.ReflectionHelper.findField(net.divinerpg.entities.base.EntityHeatSeekingProjectile.class, "target");
            seekTarget.set(missile, best);
            return best;
        } catch (Throwable ex) {
            return null; // нет поля - наводится как у Divine
        }
    }

    /** Маг стреляет раз в 1-1.5 с и только видя цель. */
    static boolean mageMayShoot(EntityLiving m) {
        long now = m.worldObj.getTotalWorldTime();
        NBTTagCompound d = m.getEntityData();
        if (now < d.getLong(NEXT_SHOT)) return false;
        Entity target = m instanceof EntityCreature ? ((EntityCreature) m).getEntityToAttack() : m.getAttackTarget();
        if (target != null && !m.canEntityBeSeen(target)) return false;
        d.setLong(NEXT_SHOT, now + 20 + m.getRNG().nextInt(11));
        return true;
    }

    private static NBTTagCompound tagOf(DamageSource s) {
        Entity src = s.getSourceOfDamage();
        return src == null ? null : src.getEntityData();
    }

    /** Залп арканного оружия: каждый снаряд попадает (у Divine после первого цель была неуязвима полсекунды). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        NBTTagCompound t = tagOf(e.source);
        if (t != null && t.hasKey(DMG) && !(e.entityLiving instanceof EntityPlayer)) e.entityLiving.hurtResistantTime = 0;
    }

    private static java.lang.reflect.Field unblockable;

    /** Урон сквозь броню (поле isUnblockable источника; источник у снаряда каждый раз новый). */
    static void pierce(DamageSource s) {
        try {
            if (unblockable == null) unblockable = cpw.mods.fml.relauncher.ReflectionHelper.findField(DamageSource.class, "isUnblockable", "field_76374_o");
            unblockable.setBoolean(s, true);
        } catch (Throwable ignored) {}
    }

    /** Нападение: арканный урон снарядов и мечей Арканы. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHurtDealt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        NBTTagCompound t = tagOf(e.source);
        boolean mage = isMage(e.entityLiving);
        if (t != null && t.hasKey(DMG)) {
            e.ammount = t.getFloat(DMG) * (mage ? MAGE_RESIST : 1F);
            if (!(e.entityLiving instanceof EntityPlayer)) pierce(e.source);
            com.divinerpgextended.debug.Debug.note(e.source.getEntity(), "Арканный урон: " + e.ammount + (mage ? " (маг держит x0.5)" : ""));
            return;
        }
        if (t != null && t.getBoolean(MOB) && mage) {
            e.ammount *= MAGE_RESIST;
            return;
        }
        if (ARCANE.equals(e.source.damageType)) { // арканный удар мечом
            if (mage) e.ammount *= MAGE_RESIST;
            return;
        }
        Entity a = e.source.getEntity();
        if (a instanceof EntityPlayer && e.source.getSourceOfDamage() == a && "player".equals(e.source.damageType)) {
            EntityPlayer p = (EntityPlayer) a;
            ItemStack h = p.getHeldItem();
            if (h != null && arcanaSword(h.getItem()) && edgePaid(p, h.getItem())) {
                // отдельным ударом следующим тиком: в этот тик цель уже неуязвима, а прибавка к обычному удару резалась бы бронёй
                queueEdge(e.entityLiving, p);
            }
        }
    }

    /** Уже ждёт ли цель арканного удара (два клика за тик - один удар). */
    static boolean edgeQueued(EntityLivingBase target) {
        for (Object[] o : PENDING) if (o[0] == target) return true;
        return false;
    }

    /**
     * Оплата арканного удара по боссу Арканы: обычный удар там отменён ещё на атаке, попадания нет, и теневой меч (Divine
     * берёт его 12 арканы при попадании) платит здесь.
     */
    static boolean edgePaidOnArcanaBoss(EntityPlayer p, Item sword) {
        if (sword != ArcanaItems.shadowSword) return edgePaid(p, sword);
        ArcanaHelper h = ArcanaHelper.getProperties(p);
        return h != null && ArcanaRules.useBar(h, 12F, "ItemShadowSword");
    }

    /**
     * Арканный удар меча Арканы по цели - отдельным ударом следующим тиком. Манекен меряет удар на своей скрытой копии игрока:
     * арканный удар - по самому манекену (до 0.99.120 он уходил в копию, и манекен показывал один обычный удар).
     */
    static void queueEdge(EntityLivingBase target, EntityPlayer p) {
        if (target instanceof com.divinerpgextended.dummy.DummyGhost && ((com.divinerpgextended.dummy.DummyGhost) target).owner != null)
            target = ((com.divinerpgextended.dummy.DummyGhost) target).owner;
        PENDING.add(new Object[] { target, p, SWORD_BONUS * ArcanaRules.stage(p) * crown(p) });
    }

    /** Арканный удар мечом: свой тип урона, сквозь броню. */
    public static final String ARCANE = "dre.arcane";
    private static final java.util.List<Object[]> PENDING = new java.util.ArrayList<Object[]>();

    public static DamageSource arcaneSource(EntityPlayer p) {
        return new net.minecraft.util.EntityDamageSource(ARCANE, p).setDamageBypassesArmor();
    }

    /**
     * Оплачен ли арканный удар: сабля и клинок Арканы списывают аркану сами до удара (иначе удара нет), теневой меч - после
     * (нужно, чтобы её хватало), Storm Sword и меч Ливиции - 6 арканы на сам арканный удар.
     */
    static boolean edgePaid(EntityPlayer p, Item sword) {
        ArcanaHelper h = ArcanaHelper.getProperties(p);
        if (h == null) return false;
        if (sword == ArcanaItems.arcaniumSaber || sword == ArcanaItems.arcaniteBlade) return true;
        if (sword == ArcanaItems.shadowSword) return h.getBarValue() >= ArcanaRules.cost("ItemShadowSword", 12F);
        return ArcanaRules.useBar(h, 6F, "ArcaneEdge");
    }

    /** Отложенные арканные удары мечей. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) flushEdges();
    }

    /** Нанести отложенные арканные удары (конец тика сервера; самопроверка зовёт сама). */
    public static void flushEdges() {
        if (PENDING.isEmpty()) return;
        java.util.List<Object[]> now = new java.util.ArrayList<Object[]>(PENDING);
        PENDING.clear();
        for (Object[] h : now) {
            EntityLivingBase t = (EntityLivingBase) h[0];
            EntityPlayer p = (EntityPlayer) h[1];
            if (t.isDead || t.getHealth() <= 0) continue;
            t.hurtResistantTime = 0;
            t.attackEntityFrom(arcaneSource(p), (Float) h[2]);
            if (t.worldObj instanceof net.minecraft.world.WorldServer) ((net.minecraft.world.WorldServer) t.worldObj).func_147487_a("witchMagic", t.posX,
                t.posY + t.height * 0.6, t.posZ, 12, t.width * 0.4, t.height * 0.3, t.width * 0.4, 0.05);
        }
    }

    /** Арканный ли этот удар по игроку. */
    public static boolean incoming(DamageSource s) {
        if ("arcana".equals(s.damageType) || ARCANE.equals(s.damageType)) return true;
        NBTTagCompound t = tagOf(s);
        if (t != null && (t.getBoolean(MOB) || t.hasKey(DMG))) return true;
        Entity a = s.getEntity();
        return a != null && a == s.getSourceOfDamage() && "mob".equals(s.damageType) && a.getClass().getName().startsWith("net.divinerpg.entities.arcana.");
    }

    /** Защита: арканный урон сначала принимает на себя аркана игрока. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurtTaken(LivingHurtEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || !incoming(e.source)) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        float before = e.ammount;
        e.ammount = ArcanaRules.absorb(p, e.ammount);
        if (e.ammount < before) com.divinerpgextended.debug.Debug.note(p, String.format("Аркана приняла %.1f из %.1f арканного урона", before - e.ammount, before));
    }

    /** Молнии Storm Sword бьют вокруг хозяина, а не его самого. */
    @SubscribeEvent
    public void onLightning(EntityStruckByLightningEvent e) {
        if (!(e.entity instanceof EntityPlayer)) return;
        ItemStack h = ((EntityPlayer) e.entity).getHeldItem();
        if (h != null && h.getItem() == ArcanaItems.stormSword) e.setCanceled(true);
    }

    /** С магов сумеречных миров - их посох (4%, +1% за уровень добычи). */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        if (!isMage(e.entityLiving) || !e.recentlyHit || e.entityLiving.worldObj.isRemote) return;
        int meta = ItemMageStaff.metaOf(e.entityLiving);
        if (meta < 0 || e.entityLiving.worldObj.rand.nextFloat() >= 0.04F + 0.01F * e.lootingLevel) return;
        e.drops.add(new net.minecraft.entity.item.EntityItem(e.entityLiving.worldObj, e.entityLiving.posX, e.entityLiving.posY, e.entityLiving.posZ,
            new ItemStack(com.divinerpgextended.registry.ModItems.mageStaff, 1, meta)));
    }

    // ---------------- восстановление и сохранение шкалы ----------------

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote) return;
        ArcanaHelper h = ArcanaHelper.getProperties(p);
        if (h == null) return;
        NBTTagCompound d = p.getEntityData();
        if (d.hasKey(SAVED) && p.ticksExisted > 2) { // после входа: Divine обнулил шкалу - вернуть, что было
            h.setBarValue(Math.min(d.getFloat(SAVED), ArcanaHooks.max(p)));
            d.removeTag(SAVED);
        }
        float max = ArcanaHooks.max(p);
        int mult = ArcanaRules.buffMult(p);
        if (mult > 0) {
            h.regenDelay = 0;
            if (h.getBarValue() < max) h.forceRegen(mult - 1);
        } else if (h.regenDelay == 0 && h.getBarValue() < max && com.divinerpgextended.accessory.Accessories.wears(p, com.divinerpgextended.trinket.Trinket.CLARITY_CRYSTAL)) {
            h.forceRegen(0.5F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ArcanaHelper h = ArcanaHelper.getProperties(e.player);
        if (h == null) return;
        NBTTagCompound ps = e.player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        ps.setFloat(SAVED, h.getBarValue());
        e.player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, ps);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        NBTTagCompound ps = e.player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (ps.hasKey(SAVED)) e.player.getEntityData().setFloat(SAVED, ps.getFloat(SAVED));
    }

    /** Смерть снимает эффекты зелий. */
    @SubscribeEvent
    public void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if (e.entityLiving instanceof EntityPlayer && !e.entityLiving.worldObj.isRemote) ArcaneEffects.clear((EntityPlayer) e.entityLiving);
    }
}
