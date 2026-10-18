package com.divinerpgextended.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.divinerpg.entities.twilight.EntityDensos;
import net.divinerpg.entities.twilight.EntityEternalArcher;
import net.divinerpg.entities.twilight.EntityReyvor;
import net.divinerpg.entities.twilight.EntitySoulFiend;
import net.divinerpg.entities.twilight.EntityVamacheron;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.world.twilight.MortumArenas;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Механики боссов на аренах Мортума ({@link MortumArenas}); работают только у боссов, вызванных на своей арене
 * (у них есть дом логова - {@link LairSummons#home}).
 * <ul>
 * <li>Денсос и Рейвор - близнецы. Рядом друг с другом ({@link #TWIN_NEAR}) прикрывают друг друга: урон x{@link #TWIN_SHIELD}
 * - их надо развести. По очереди каждые {@link #STEP} тиков один шагает сквозь тень за спину цели и бьёт
 * ({@link #STEP_DMG}; Денсос ослепляет, Рейвор замедляет), за секунду до шага у ног цели дым. Убил одного - живой
 * впадает в ярость (сила II, скорость) и через {@link #REVIVE} тиков поднимает брата (с половиной здоровья, без
 * трофеев): добивать обоих почти одновременно.</li>
 * <li>Вечный лучник - каждые {@link #VOLLEY} тиков залп веером ({@link #VOLLEY_ARROWS} стрел) с замахом в 1.5 секунды;
 * от залпа прячутся за колоннами галереи, но каждый залп обрушивает верх колонны у цели - укрытий всё меньше.</li>
 * <li>Пожиратель душ - каждые {@link #RAISE} тиков поднимает души из целых могил погоста рядом с собой ({@link #GRAVE_R}):
 * по душе из могилы, могила при этом рассыпается (надгробия можно ломать и самому). Пока его душа жива рядом с ним
 * ({@link #SOUL_R}), он неуязвим: уводи его туда, где могил не осталось.</li>
 * <li>Вамачерон - рывок: замах {@link #WINDUP} тиков, потом несётся по прямой. Сбивает с ног; врезался в столб или стену -
 * оглушён на {@link #STUN} тиков и получает двойной урон.</li>
 * </ul>
 */
public class MortumArenaBosses {

    public static final double TWIN_NEAR = 6;
    public static final float TWIN_SHIELD = 0.3F;
    public static final int REVIVE = 200;
    public static final String REVIVED = "dreRevived", SOUL = "dreFiendSoul", DASH = "dreDash", WIND = "dreWind", STUNNED = "dreStun";
    public static final int VOLLEY = 200, VOLLEY_WARN = 30, VOLLEY_ARROWS = 9, RAISE = 400, SOULS = 3, CHARGE = 160, WINDUP = 20, DASH_TICKS = 40, STUN = 60;
    public static final int STEP = 160, STEP_WARN = 20, GRAVE_R = 14, SOUL_R = 20;
    public static final float VOLLEY_DMG = 6, DASH_DMG = 10, STEP_DMG = 8;

    /** Ждут воскрешения: {мир, класс, живой брат, тик мира}. */
    private static final List<Object[]> REVIVES = new ArrayList<Object[]>();

    /** Сколько близнецов ждут воскрешения (для автотеста). */
    public static int pending() {
        return REVIVES.size();
    }

    static boolean arena(Entity e) {
        return LairSummons.home(e) != null;
    }

    // ---------------------------------------------------------------- близнецы

    static boolean twin(Entity e) {
        return e instanceof EntityDensos || e instanceof EntityReyvor;
    }

    /** Живой брат-близнец рядом (другой класс). */
    static EntityLivingBase brother(EntityLivingBase t, double r) {
        Class<?> other = t instanceof EntityDensos ? EntityReyvor.class : EntityDensos.class;
        EntityLivingBase best = null;
        double bd = r * r;
        for (Object o : t.worldObj.getEntitiesWithinAABB(other, t.boundingBox.expand(r, r / 2, r))) {
            EntityLivingBase b = (EntityLivingBase) o;
            double d = b.getDistanceSqToEntity(t);
            if (b.isEntityAlive() && d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || !arena(v)) return;
        if (twin(v) && brother(v, TWIN_NEAR) != null) {
            e.ammount *= TWIN_SHIELD;
            ((WorldServer) v.worldObj).func_147487_a("magicCrit", v.posX, v.posY + v.height / 2, v.posZ, 8, 0.6, 0.6, 0.6, 0.1);
        }
        if (v instanceof EntityVamacheron && v.getEntityData().getInteger(STUNNED) > 0) e.ammount *= 2;
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || !twin(v) || !arena(v)) return;
        EntityLivingBase b = brother(v, 64);
        if (b == null) return; // второй уже мёртв - победа
        REVIVES.add(new Object[] { v.worldObj, v.getClass(), b, v.worldObj.getTotalWorldTime() + REVIVE });
        v.worldObj.playSoundAtEntity(b, "mob.wither.idle", 1.5F, 0.6F);
        // ярость живого до воскрешения брата
        b.addPotionEffect(new PotionEffect(Potion.damageBoost.id, REVIVE + 20, 1));
        b.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, REVIVE + 20, 0));
    }

    /** Поднятые братом - без трофеев (иначе ферма). */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        if (!e.entityLiving.worldObj.isRemote && e.entityLiving.getEntityData().getBoolean(REVIVED)) e.drops.clear();
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || REVIVES.isEmpty()) return;
        for (Iterator<Object[]> it = REVIVES.iterator(); it.hasNext();) {
            Object[] r = it.next();
            if (r[0] != e.world) continue;
            EntityLivingBase b = (EntityLivingBase) r[2];
            if (!b.isEntityAlive()) { // оба мертвы
                it.remove();
                continue;
            }
            if (e.world.getTotalWorldTime() < (Long) r[3]) continue;
            it.remove();
            revive(e.world, (Class<?>) r[1], b);
        }
    }

    /** Брат поднимает брата: рядом с собой, половина здоровья, та же арена. */
    public static EntityLivingBase revive(World w, Class<?> kind, EntityLivingBase b) {
        try {
            EntityLivingBase t = (EntityLivingBase) kind.getConstructor(World.class).newInstance(w);
            t.setLocationAndAngles(b.posX + 1.5, b.posY, b.posZ, b.rotationYaw, 0);
            int[] h = LairSummons.home(b);
            if (h != null) t.getEntityData().setIntArray("dreLairHome", h);
            t.getEntityData().setBoolean(REVIVED, true);
            w.spawnEntityInWorld(t);
            t.setHealth(t.getMaxHealth() / 2);
            ((WorldServer) w).func_147487_a("witchMagic", t.posX, t.posY + 1, t.posZ, 40, 0.8, 1.2, 0.8, 0.1);
            w.playSoundAtEntity(t, "mob.wither.spawn", 1.2F, 1.2F);
            return t;
        } catch (Exception ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------- ход боссов

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        EntityLivingBase m = e.entityLiving;
        if (m.worldObj.isRemote || !arena(m)) return;
        if (twin(m)) twinTick((EntityLiving) m);
        else if (m instanceof EntityEternalArcher) archer((EntityLiving) m);
        else if (m instanceof EntitySoulFiend) fiend((EntityLiving) m);
        else if (m instanceof EntityVamacheron) vamacheron((EntityLiving) m);
    }

    static EntityPlayer target(EntityLiving m, double r) {
        if (m.getAttackTarget() instanceof EntityPlayer) return (EntityPlayer) m.getAttackTarget();
        return m.worldObj.getClosestVulnerablePlayerToEntity(m, r);
    }

    // ---------------- близнецы: шаг сквозь тень

    private void twinTick(EntityLiving t) {
        int k = (t.ticksExisted + (t instanceof EntityDensos ? 0 : STEP / 2)) % STEP; // по очереди
        EntityPlayer p = target(t, 32);
        if (p == null || t.ticksExisted < STEP / 2) return;
        if (k == STEP - STEP_WARN) {
            ((WorldServer) t.worldObj).func_147487_a("largesmoke", p.posX, p.posY + 0.1, p.posZ, 20, 0.4, 0.05, 0.4, 0.01);
            t.worldObj.playSoundAtEntity(p, "mob.endermen.stare", 0.8F, t instanceof EntityDensos ? 1.4F : 0.7F);
        }
        if (k == 0) step(t, p);
    }

    /** Шаг сквозь тень: за спину цели (2 блока), удар; Денсос ослепляет, Рейвор замедляет. false - за спиной стена. */
    public static boolean step(EntityLiving t, EntityLivingBase p) {
        double yaw = Math.toRadians(p.rotationYaw);
        double x = p.posX + Math.sin(yaw) * 2, z = p.posZ - Math.cos(yaw) * 2; // взгляд игрока - (-sin, cos)
        double ox = t.posX, oy = t.posY, oz = t.posZ;
        t.setPosition(x, p.posY, z);
        if (!t.worldObj.getCollidingBoundingBoxes(t, t.boundingBox).isEmpty()) {
            t.setPosition(ox, oy, oz);
            return false;
        }
        ((WorldServer) t.worldObj).func_147487_a("portal", ox, oy + 1, oz, 30, 0.5, 1, 0.5, 0.5);
        t.setPositionAndUpdate(x, p.posY, z);
        t.rotationYaw = t.rotationYawHead = (float) Math.toDegrees(Math.atan2(-(p.posX - x), p.posZ - z));
        t.setAttackTarget(p);
        t.worldObj.playSoundAtEntity(t, "mob.endermen.portal", 1.2F, t instanceof EntityDensos ? 1.3F : 0.6F);
        p.attackEntityFrom(DamageSource.causeMobDamage(t), STEP_DMG);
        if (t instanceof EntityDensos) p.addPotionEffect(new PotionEffect(Potion.blindness.id, 50, 0));
        else p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 70, 1));
        return true;
    }

    // ---------------- Вечный лучник

    private void archer(EntityLiving a) {
        int t = a.ticksExisted % VOLLEY;
        EntityPlayer p = target(a, 48);
        if (p == null) return;
        if (t == VOLLEY - VOLLEY_WARN) {
            a.worldObj.playSoundAtEntity(a, "random.bow", 2.0F, 0.4F);
            ((WorldServer) a.worldObj).func_147487_a("crit", a.posX, a.posY + a.height, a.posZ, 30, 0.6, 0.4, 0.6, 0.2);
        }
        if (t == 0 && a.ticksExisted > 0) volley(a, p);
    }

    /** Залп веером в цель; потом рушит верх колонны-укрытия у цели. */
    public static int volley(EntityLiving a, EntityLivingBase p) {
        int n = 0;
        for (int i = 0; i < VOLLEY_ARROWS; i++) {
            EntityArrow ar = new EntityArrow(a.worldObj, a, p, 1.8F, 1.0F);
            double ang = Math.toRadians((i - (VOLLEY_ARROWS - 1) / 2.0) * 6);
            double c = Math.cos(ang), s = Math.sin(ang), mx = ar.motionX * c - ar.motionZ * s, mz = ar.motionX * s + ar.motionZ * c;
            ar.motionX = mx;
            ar.motionZ = mz;
            ar.setDamage(VOLLEY_DMG / 1.8);
            ar.canBePickedUp = 0;
            if (a.worldObj.spawnEntityInWorld(ar)) n++;
        }
        a.worldObj.playSoundAtEntity(a, "random.bow", 2.0F, 0.8F);
        erode(a.worldObj, p);
        return n;
    }

    /** Верхний блок ближайшей к цели колонны (резной камень) обрушивается. */
    public static boolean erode(World w, EntityLivingBase p) {
        int px = (int) Math.floor(p.posX), py = (int) Math.floor(p.posY), pz = (int) Math.floor(p.posZ);
        int[] best = null;
        double bd = 26;
        for (int x = px - 5; x <= px + 5; x++) for (int z = pz - 5; z <= pz + 5; z++) for (int y = py + 6; y >= py - 1; y--) {
            if (w.getBlock(x, y, z) != Blocks.stonebrick || w.getBlockMetadata(x, y, z) != 3 || w.getBlock(x, y + 1, z) == Blocks.stonebrick) continue;
            double d = (x - px) * (x - px) + (z - pz) * (z - pz);
            if (d < bd) {
                bd = d;
                best = new int[] { x, y, z };
            }
            break;
        }
        if (best == null) return false;
        w.func_147480_a(best[0], best[1], best[2], false); // ломается с частицами, без дропа
        return true;
    }

    // ---------------- Пожиратель душ

    private void fiend(EntityLiving f) {
        if (f.ticksExisted % RAISE != 60) return;
        raise(f);
    }

    /** Надгробие могилы цело (резной камень на месте) - из неё ещё можно поднять душу. */
    public static boolean graveIntact(World w, int[] h, int[] g) {
        int x = h[0] + g[0], y = h[1], z = h[2] + g[1] + 1;
        return w.getBlock(x, y, z) == Blocks.stonebrick && w.getBlockMetadata(x, y, z) == 3;
    }

    /**
     * Поднять души из целых могил рядом с боссом (не больше SOULS живых): по душе из могилы, надгробие рассыпается -
     * второй раз из этой могилы не встанут.
     */
    public static int raise(EntityLiving f) {
        int[] h = LairSummons.home(f);
        if (h == null) return 0;
        int alive = souls(f).size(), made = 0;
        List<int[]> graves = new ArrayList<int[]>();
        for (int[] g : MortumArenas.graves()) {
            double dx = h[0] + g[0] + 0.5 - f.posX, dz = h[2] + g[1] + 0.5 - f.posZ;
            if (dx * dx + dz * dz <= GRAVE_R * GRAVE_R && graveIntact(f.worldObj, h, g)) graves.add(g);
        }
        java.util.Collections.shuffle(graves, f.getRNG());
        for (int[] g : graves) {
            if (alive + made >= SOULS) break;
            f.worldObj.func_147480_a(h[0] + g[0], h[1] + 1, h[2] + g[1] + 1, false); // плита надгробия
            f.worldObj.func_147480_a(h[0] + g[0], h[1], h[2] + g[1] + 1, false); // камень надгробия
            net.divinerpg.entities.twilight.EntitySoulSpider s = new net.divinerpg.entities.twilight.EntitySoulSpider(f.worldObj);
            s.setLocationAndAngles(h[0] + g[0] + 0.5, h[1], h[2] + g[1] + 0.5, f.getRNG().nextFloat() * 360, 0);
            s.getEntityData().setInteger(SOUL, f.getEntityId());
            s.func_110163_bv();
            if (f.worldObj.spawnEntityInWorld(s)) {
                made++;
                ((WorldServer) f.worldObj).func_147487_a("largesmoke", s.posX, s.posY + 0.3, s.posZ, 15, 0.3, 0.2, 0.3, 0.02);
            }
        }
        if (made > 0) f.worldObj.playSoundAtEntity(f, "mob.zombie.unfect", 2.0F, 0.5F);
        return made;
    }

    @SuppressWarnings("unchecked")
    public static List<Entity> souls(EntityLiving f) {
        List<Entity> l = new ArrayList<Entity>();
        for (Object o : f.worldObj.getEntitiesWithinAABB(EntityLiving.class, f.boundingBox.expand(SOUL_R, 12, SOUL_R)))
            if (((Entity) o).isEntityAlive() && ((Entity) o).getEntityData().getInteger(SOUL) == f.getEntityId()) l.add((Entity) o);
        return l;
    }

    /** Пока его души живы рядом с ним, Пожиратель неуязвим. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || !(v instanceof EntitySoulFiend) || !arena(v) || e.source == DamageSource.outOfWorld) return;
        if (souls((EntityLiving) v).isEmpty()) return;
        e.setCanceled(true);
        ((WorldServer) v.worldObj).func_147487_a("witchMagic", v.posX, v.posY + v.height / 2, v.posZ, 10, 0.5, 0.8, 0.5, 0.05);
    }

    // ---------------- Вамачерон

    private void vamacheron(EntityLiving v) {
        NBTTagCompound d = v.getEntityData();
        int stun = d.getInteger(STUNNED), wind = d.getInteger(WIND), dash = d.getInteger(DASH);
        if (stun > 0) {
            d.setInteger(STUNNED, stun - 1);
            v.motionX = v.motionZ = 0;
            v.getNavigator().clearPathEntity();
            if (stun % 10 == 0) ((WorldServer) v.worldObj).func_147487_a("magicCrit", v.posX, v.posY + v.height, v.posZ, 6, 0.4, 0.2, 0.4, 0.05);
            return;
        }
        if (wind > 0) {
            d.setInteger(WIND, wind - 1);
            v.motionX = v.motionZ = 0;
            v.getNavigator().clearPathEntity();
            if (wind % 4 == 0) ((WorldServer) v.worldObj).func_147487_a("largesmoke", v.posX, v.posY + 0.2, v.posZ, 6, 0.6, 0.1, 0.6, 0.02);
            if (wind == 1) {
                d.setInteger(DASH, DASH_TICKS);
                v.worldObj.playSoundAtEntity(v, "mob.enderdragon.growl", 1.5F, 1.4F);
            }
            return;
        }
        if (dash > 0) {
            dashTick(v, dash);
            return;
        }
        EntityPlayer p = target(v, 40);
        if (p != null && v.ticksExisted % CHARGE == 0) startCharge(v, p);
    }

    /** Замах: запомнить направление на цель. */
    public static void startCharge(EntityLiving v, EntityLivingBase p) {
        double dx = p.posX - v.posX, dz = p.posZ - v.posZ, l = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
        NBTTagCompound d = v.getEntityData();
        d.setDouble("dreDashX", dx / l);
        d.setDouble("dreDashZ", dz / l);
        d.setInteger(WIND, WINDUP);
        v.rotationYaw = v.renderYawOffset = (float) Math.toDegrees(Math.atan2(-dx, dz));
        v.worldObj.playSoundAtEntity(v, "mob.wolf.growl", 2.0F, 0.5F);
    }

    /** Ход рывка: по прямой; задел игрока - урон и отброс; упёрся - оглушён. */
    public static void dashTick(EntityLiving v, int dash) {
        NBTTagCompound d = v.getEntityData();
        double dx = d.getDouble("dreDashX"), dz = d.getDouble("dreDashZ");
        v.getNavigator().clearPathEntity();
        if (dash < DASH_TICKS - 2 && v.isCollidedHorizontally) {
            d.setInteger(DASH, 0);
            d.setInteger(STUNNED, STUN);
            v.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, STUN, 5));
            v.worldObj.playSoundAtEntity(v, "random.anvil_land", 1.5F, 0.5F);
            ((WorldServer) v.worldObj).func_147487_a("explode", v.posX, v.posY + 1, v.posZ, 10, 0.8, 0.8, 0.8, 0.05);
            return;
        }
        d.setInteger(DASH, dash - 1);
        v.motionX = dx * 1.1;
        v.motionZ = dz * 1.1;
        v.velocityChanged = true;
        for (Object o : v.worldObj.getEntitiesWithinAABB(EntityPlayer.class, v.boundingBox.expand(0.6, 0.2, 0.6))) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.hurtResistantTime > 10) continue;
            p.attackEntityFrom(DamageSource.causeMobDamage(v), DASH_DMG);
            p.addVelocity(dx * 1.5, 0.5, dz * 1.5);
            p.velocityChanged = true;
        }
    }
}
