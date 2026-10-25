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
 * <li>Денсос и Рейвор - близнецы, бьются по очереди ({@link #DUEL} тиков): дуэлянт затягивает цель на свою половину
 * (Денсос - светлую, Рейвор - тёмную) и шагает сквозь тень за спину ({@link #STEP_DMG}; Денсос ослепляет, Рейвор
 * замедляет); второй неуязвим, стоит у своего помоста и мешает через разлом. Убил одного - живой в ярости и сразу
 * дуэлянт; не добил его за {@link #REVIVE} тиков - брат встаёт с половиной здоровья (один раз, без трофеев).</li>
 * <li>Вечный лучник - каждые {@link #VOLLEY} тиков залп веером ({@link #VOLLEY_ARROWS} стрел) с замахом в 1.5 секунды;
 * от залпа прячутся за колоннами галереи, но каждый залп обрушивает верх колонны у цели - укрытий всё меньше.</li>
 * <li>Пожиратель душ - каждые {@link #RAISE} тиков поднимает души из целых могил погоста рядом с собой ({@link #GRAVE_R}):
 * по душе из могилы, могила при этом рассыпается. Пока жива хоть одна его душа ({@link #SOUL_R}), он неуязвим: уводи его
 * туда, где могил не осталось. Свои пауки Divine у него на арене не появляются. Разбить надгробие можно, но дорого:
 * он лечится ({@link #DESECRATE_HEAL}), из разбитой могилы сразу встаёт душа, разбившему - иссушение.</li>
 * <li>Вамачерон - рывок: замах {@link #WINDUP} тиков, потом несётся по прямой. Сбивает с ног; врезался в столб или стену -
 * оглушён на {@link #STUN} тиков и получает двойной урон.</li>
 * </ul>
 */
public class MortumArenaBosses {

    /**
     * Дуэль близнецов: по DUEL тиков поочерёдно Денсос (светлая половина, запад) и Рейвор (тёмная, восток) - дуэлянт
     * затягивает цель на свою половину, второй неуязвим, стоит у своего помоста и мешает через разлом раз в HARASS тиков.
     */
    public static final int DUEL = 400, HARASS = 60;
    public static final float PULL = 0.8F;
    public static final int REVIVE = 200;
    public static final String REVIVED = "dreRevived", SOUL = "dreFiendSoul", DASH = "dreDash", WIND = "dreWind", STUNNED = "dreStun";
    public static final int VOLLEY = 160, VOLLEY_WARN = 30, VOLLEY_ARROWS = 9, RAISE = 400, SOULS = 3, CHARGE = 160, WINDUP = 20, DASH_TICKS = 40, STUN = 60;
    public static final int STEP = 160, STEP_WARN = 20, GRAVE_R = 14, SOUL_R = 40;
    /** Разбил надгробие при живом Пожирателе: он лечится на эту долю, душа встаёт у разбившего, тому иссушение. */
    public static final float DESECRATE_HEAL = 0.08F;
    public static final float VOLLEY_DMG = 6, DASH_DMG = 10, STEP_DMG = 8;
    /** Лучник: бегуны по галерее (не больше RUNNERS, подкрепление раз в RUNNER_EVERY), дождь стрел раз в RAIN тиков. */
    public static final int RUNNERS = 3, RUNNER_EVERY = 300, RAIN = 240, RAIN_WARN = 25, RAIN_ARROWS = 12, PIERCE_BLOCKS = 4;
    public static final String ARCHER_ADD = "dreArcherAdd", PIERCED = "drePierced";
    /** Стрелы залпа, пробивающие блоки: {стрела, центр галереи x, пол, z}. */
    private static final List<Object[]> PIERCING = new ArrayList<Object[]>();
    /** Дождь стрел на взводе: {мир, x, y, z, тик, лучник}. */
    private static final List<Object[]> RAINS = new ArrayList<Object[]>();

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

    /** Жив ли брат этого близнеца (тогда победы над близнецами ещё нет). */
    public static boolean brotherAlive(EntityLivingBase t) {
        return brother(t, 64) != null;
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

    /** Чья сейчас дуэль; брат мёртв - выживший всегда дуэлянт. */
    public static boolean duelist(EntityLivingBase t) {
        if (brother(t, 64) == null) return true;
        boolean densosTurn = t.worldObj.getTotalWorldTime() / DUEL % 2 == 0;
        return t instanceof EntityDensos == densosTurn;
    }

    /** Сторона брата: Денсос - запад (-1), Рейвор - восток (+1). */
    public static int side(EntityLivingBase t) {
        return t instanceof EntityDensos ? -1 : 1;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || !arena(v)) return;
        if (v instanceof EntityVamacheron && v.getEntityData().getInteger(STUNNED) > 0) e.ammount *= 2;
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || !twin(v) || !arena(v)) return;
        EntityLivingBase b = brother(v, 64);
        if (b == null) return; // второй уже мёртв - победа
        v.worldObj.playSoundAtEntity(b, "mob.wither.idle", 1.5F, 0.6F);
        // ярость живого: он теперь дуэлянт до конца боя
        b.addPotionEffect(new PotionEffect(Potion.damageBoost.id, 20 * 600, 1));
        b.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 20 * 600, 0));
        if (v.getEntityData().getBoolean(REVIVED)) return; // брата поднимают один раз
        REVIVES.add(new Object[] { v.worldObj, v.getClass(), b, v.worldObj.getTotalWorldTime() + REVIVE });
    }

    /** Поднятые братом и бегуны лучника - без трофеев (иначе ферма). */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        if (!e.entityLiving.worldObj.isRemote && (e.entityLiving.getEntityData().getBoolean(REVIVED) || e.entityLiving.getEntityData().hasKey(ARCHER_ADD))) e.drops.clear();
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        pierceTick(e.world);
        rainTick(e.world);
        if (REVIVES.isEmpty()) return;
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

    // ---------------- близнецы: дуэль на своей половине, шаг сквозь тень

    private void twinTick(EntityLiving t) {
        int[] h = LairSummons.home(t);
        if (h == null) return;
        double cx = h[0] + 0.5;
        int side = side(t);
        if ((t.posX - cx) * side < 2.5) { // за разлом не заходит
            t.motionX = side * 0.4;
            t.velocityChanged = true;
        }
        EntityPlayer p = target(t, 32);
        if (p == null) return;
        if (!duelist(t)) { // второй - у своего помоста, мешает через разлом
            double dx = cx + side * MortumArenas.TWINS_DAIS - t.posX, dz = h[2] + 0.5 - t.posZ;
            if (dx * dx + dz * dz > 9) t.getNavigator().tryMoveToXYZ(cx + side * MortumArenas.TWINS_DAIS, h[1], h[2] + 0.5, 1.0);
            if (t.ticksExisted % HARASS == 0) harass(t, p);
            return;
        }
        long now = t.worldObj.getTotalWorldTime();
        if (now % DUEL == 0 && brother(t, 64) != null) tell(t, t instanceof EntityDensos ? "dre.chat.twins_duel_densos" : "dre.chat.twins_duel_reyvor");
        if ((p.posX - cx) * side < 3) pull(t, p, cx, side);
        int k = t.ticksExisted % STEP;
        if (t.ticksExisted < STEP / 2) return;
        if (k == STEP - STEP_WARN) {
            ((WorldServer) t.worldObj).func_147487_a("largesmoke", p.posX, p.posY + 0.1, p.posZ, 20, 0.4, 0.05, 0.4, 0.01);
            t.worldObj.playSoundAtEntity(p, "mob.endermen.stare", 0.8F, t instanceof EntityDensos ? 1.4F : 0.7F);
        }
        if (k == 0) step(t, p);
    }

    /** Дуэлянт тянет цель на свою половину: цепь из частиц, цель летит к нему над разломом. */
    public static void pull(EntityLiving t, EntityPlayer p, double cx, int side) {
        double tx = cx + side * 8, dx = tx - p.posX, dz = t.posZ - p.posZ, l = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
        p.motionX = dx / l * PULL;
        p.motionZ = dz / l * PULL * 0.3;
        p.motionY = Math.max(p.motionY, 0.22);
        p.fallDistance = 0;
        p.velocityChanged = true;
        WorldServer ws = (WorldServer) t.worldObj;
        for (int i = 1; i < 6; i++) {
            double k = i / 6.0;
            ws.func_147487_a("portal", p.posX + (t.posX - p.posX) * k, p.posY + 1 + (t.posY + 1 - p.posY - 1) * k, p.posZ + (t.posZ - p.posZ) * k, 2, 0, 0, 0, 0);
        }
        if (t.ticksExisted % 20 == 0) t.worldObj.playSoundAtEntity(p, "mob.endermen.portal", 0.8F, t instanceof EntityDensos ? 1.4F : 0.6F);
    }

    /** Второй брат мешает через разлом: теневой снаряд; Денсос ослепляет, Рейвор замедляет. */
    public static void harass(EntityLiving t, EntityPlayer p) {
        com.divinerpgextended.entity.EntityShadowBolt b = new com.divinerpgextended.entity.EntityShadowBolt(t.worldObj, t);
        b.setThrowableHeading(p.posX - t.posX, p.posY + p.height / 2 - (t.posY + t.getEyeHeight()), p.posZ - t.posZ, 1.2F, 2);
        t.worldObj.spawnEntityInWorld(b);
        if (t instanceof EntityDensos) p.addPotionEffect(new PotionEffect(Potion.blindness.id, 40, 0));
        else p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 1));
        t.worldObj.playSoundAtEntity(t, "mob.ghast.fireball", 0.8F, t instanceof EntityDensos ? 1.4F : 0.6F);
    }

    static void tell(Entity src, String key) {
        for (Object o : src.worldObj.getEntitiesWithinAABB(EntityPlayer.class, src.boundingBox.expand(40, 16, 40))) {
            net.minecraft.util.ChatComponentTranslation m = new net.minecraft.util.ChatComponentTranslation(key);
            m.getChatStyle().setColor(net.minecraft.util.EnumChatFormatting.DARK_PURPLE).setItalic(true);
            ((EntityPlayer) o).addChatMessage(m);
        }
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
        if (a.ticksExisted % RUNNER_EVERY == 40) runners(a, p);
        if (a.ticksExisted % RAIN == RAIN / 2) rain(a, p);
    }

    /** Бегуны: василиски Мортума выбегают из южного входа галереи и гонят цель - на месте не отсидеться. */
    @SuppressWarnings("unchecked")
    public static int runners(EntityLiving a, EntityLivingBase p) {
        int[] h = LairSummons.home(a);
        if (h == null) return 0;
        int alive = 0;
        for (Object o : a.worldObj.getEntitiesWithinAABB(EntityLiving.class, a.boundingBox.expand(48, 16, 48)))
            if (((Entity) o).isEntityAlive() && ((Entity) o).getEntityData().getInteger(ARCHER_ADD) == a.getEntityId()) alive++;
        int made = 0;
        for (int i = 0; i < 2 && alive + made < RUNNERS; i++) {
            net.divinerpg.entities.twilight.EntityBaslisk r = new net.divinerpg.entities.twilight.EntityBaslisk(a.worldObj);
            double x = h[0] + 0.5 + (i == 0 ? -3 : 3), z = h[2] + 0.5 + MortumArenas.GALLERY_HZ - 3;
            r.setLocationAndAngles(x, h[1], z, 180, 0);
            if (!a.worldObj.getCollidingBoundingBoxes(r, r.boundingBox).isEmpty()) continue;
            r.getEntityData().setInteger(ARCHER_ADD, a.getEntityId());
            r.getEntityData().setBoolean("dreDemonAdd", true); // успокоение арены их не уносит
            r.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 20 * 600, 1));
            r.setAttackTarget(p);
            BossMinions.mark(r, a);
            if (a.worldObj.spawnEntityInWorld(r)) {
                made++;
                ((WorldServer) a.worldObj).func_147487_a("largesmoke", x, h[1] + 0.5, z, 15, 0.4, 0.4, 0.4, 0.02);
            }
        }
        if (made > 0) a.worldObj.playSoundAtEntity(a, "mob.wolf.howl", 1.5F, 0.6F);
        return made;
    }

    /** Дождь стрел: под ногами цели круг искр RAIN_WARN тиков, потом сверху падают RAIN_ARROWS стрел. */
    public static void rain(EntityLiving a, EntityLivingBase p) {
        RAINS.add(new Object[] { a.worldObj, p.posX, p.posY, p.posZ, a.worldObj.getTotalWorldTime() + RAIN_WARN, a });
        a.worldObj.playSoundAtEntity(p, "random.bow", 1.0F, 0.3F);
    }

    private static void rainTick(World w) {
        if (RAINS.isEmpty()) return;
        long now = w.getTotalWorldTime();
        for (Iterator<Object[]> it = RAINS.iterator(); it.hasNext();) {
            Object[] r = it.next();
            if (r[0] != w) continue;
            double x = (Double) r[1], y = (Double) r[2], z = (Double) r[3];
            if (now < (Long) r[4]) {
                if (now % 3 == 0) for (int i = 0; i < 12; i++) {
                    double ang = i * Math.PI / 6;
                    ((WorldServer) w).func_147487_a("crit", x + Math.cos(ang) * 3, y + 0.1, z + Math.sin(ang) * 3, 1, 0, 0, 0, 0);
                }
                continue;
            }
            it.remove();
            arrowsFall(w, (EntityLivingBase) r[5], x, y, z);
        }
    }

    /** Стрелы падают на круг радиусом 3 из-под потолка (не выше 10 блоков). */
    public static int arrowsFall(World w, EntityLivingBase a, double x, double y, double z) {
        int n = 0;
        java.util.Random rnd = w.rand;
        for (int i = 0; i < RAIN_ARROWS; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * 3;
            double ax = x + Math.cos(ang) * rr, az = z + Math.sin(ang) * rr;
            int top = (int) Math.floor(y) + 1;
            while (top < (int) y + 10 && w.isAirBlock((int) Math.floor(ax), top + 1, (int) Math.floor(az))) top++;
            EntityArrow ar = new EntityArrow(w, ax, top, az);
            ar.shootingEntity = a;
            ar.motionX = ar.motionZ = 0;
            ar.motionY = -1.6;
            ar.setDamage(VOLLEY_DMG / 3);
            ar.canBePickedUp = 0;
            if (w.spawnEntityInWorld(ar)) n++;
        }
        w.playSoundEffect(x, y, z, "random.bow", 1.5F, 0.5F);
        return n;
    }

    /** Залп веером в цель; стрелы пробивают блоки внутри галереи; потом рушит верх колонны-укрытия у цели. */
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
            if (a.worldObj.spawnEntityInWorld(ar)) {
                n++;
                int[] h = LairSummons.home(a);
                if (h != null) PIERCING.add(new Object[] { ar, h[0], h[1], h[2] });
            }
        }
        a.worldObj.playSoundAtEntity(a, "random.bow", 2.0F, 0.8F);
        erode(a.worldObj, p);
        return n;
    }

    /** Блок можно пробить залпом: внутри стен галереи, выше пола, не алтарь и не неразрушимый. */
    static boolean pierceable(World w, int x, int y, int z, int cx, int floor, int cz) {
        if (Math.abs(x - cx) > MortumArenas.GALLERY_HX - 1 || Math.abs(z - cz) > MortumArenas.GALLERY_HZ - 1 || y < floor) return false;
        net.minecraft.block.Block b = w.getBlock(x, y, z);
        if (b.isAir(w, x, y, z) || b == com.divinerpgextended.registry.ModBlocks.arenaAltar || b.getMaterial().isLiquid()) return false;
        return b.getBlockHardness(w, x, y, z) >= 0;
    }

    /** Стрелы залпа летят сквозь блоки: блок на пути следующего шага стрелы ломается (до PIERCE_BLOCKS на стрелу). */
    public static int pierceTick(World w) {
        int broken = 0;
        for (Iterator<Object[]> it = PIERCING.iterator(); it.hasNext();) {
            Object[] r = it.next();
            EntityArrow ar = (EntityArrow) r[0];
            if (ar.worldObj != w) continue;
            double sp = ar.motionX * ar.motionX + ar.motionY * ar.motionY + ar.motionZ * ar.motionZ;
            int done = ar.getEntityData().getInteger(PIERCED);
            if (ar.isDead || ar.ticksExisted > 60 || sp < 0.04 || done >= PIERCE_BLOCKS) {
                it.remove();
                continue;
            }
            net.minecraft.util.Vec3 from = net.minecraft.util.Vec3.createVectorHelper(ar.posX, ar.posY, ar.posZ);
            net.minecraft.util.Vec3 to = net.minecraft.util.Vec3.createVectorHelper(ar.posX + ar.motionX * 1.3, ar.posY + ar.motionY * 1.3, ar.posZ + ar.motionZ * 1.3);
            net.minecraft.util.MovingObjectPosition hit = w.func_147447_a(from, to, false, true, false);
            if (hit == null || hit.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) continue;
            if (!pierceable(w, hit.blockX, hit.blockY, hit.blockZ, (Integer) r[1], (Integer) r[2], (Integer) r[3])) continue;
            w.func_147480_a(hit.blockX, hit.blockY, hit.blockZ, false);
            ar.getEntityData().setInteger(PIERCED, done + 1);
            broken++;
        }
        return broken;
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
            BossMinions.mark(s, f);
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

    /** Живой Пожиратель на арене рядом (null - нет). */
    static EntitySoulFiend fiendNear(World w, double x, double y, double z, double r) {
        for (Object o : w.getEntitiesWithinAABB(EntitySoulFiend.class, net.minecraft.util.AxisAlignedBB.getBoundingBox(x - r, y - r / 2, z - r, x + r, y + r / 2, z + r)))
            if (((Entity) o).isEntityAlive() && arena((Entity) o)) return (EntitySoulFiend) o;
        return null;
    }

    /** Свои пауки Пожирателя (Divine) на его арене не появляются - только души из могил. */
    @SubscribeEvent
    public void onJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof net.divinerpg.entities.twilight.EntitySoulSpider) || e.entity.getEntityData().hasKey(SOUL)) return;
        if (fiendNear(e.world, e.entity.posX, e.entity.posY, e.entity.posZ, 40) != null) e.setCanceled(true);
    }

    /** Разбитое надгробие на погосте при живом Пожирателе: он лечится, из могилы встаёт душа, разбившему - иссушение. */
    @SubscribeEvent
    public void onBreak(net.minecraftforge.event.world.BlockEvent.BreakEvent e) {
        if (e.world.isRemote || e.block != Blocks.stonebrick || e.blockMetadata != 3 || e.getPlayer() == null) return;
        EntitySoulFiend f = fiendNear(e.world, e.x, e.y, e.z, 40);
        if (f == null) return;
        f.heal(f.getMaxHealth() * DESECRATE_HEAL);
        e.getPlayer().addPotionEffect(new PotionEffect(Potion.wither.id, 100, 1));
        net.divinerpg.entities.twilight.EntitySoulSpider s = new net.divinerpg.entities.twilight.EntitySoulSpider(e.world);
        s.setLocationAndAngles(e.x + 0.5, e.y, e.z + 0.5, 0, 0);
        s.getEntityData().setInteger(SOUL, f.getEntityId());
        s.func_110163_bv();
        BossMinions.mark(s, f);
        e.world.spawnEntityInWorld(s);
        ((WorldServer) e.world).func_147487_a("witchMagic", e.x + 0.5, e.y + 1, e.z + 0.5, 30, 0.4, 0.6, 0.4, 0.05);
        e.world.playSoundEffect(e.x + 0.5, e.y + 0.5, e.z + 0.5, "mob.wither.hurt", 1.0F, 0.6F);
    }

    /** Пока жива хоть одна его душа, Пожиратель неуязвим. Близнеца не в его дуэли не ранить. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || !arena(v) || e.source == DamageSource.outOfWorld) return;
        if (twin(v) && !duelist(v)) {
            e.setCanceled(true);
            ((WorldServer) v.worldObj).func_147487_a("magicCrit", v.posX, v.posY + v.height / 2, v.posZ, 8, 0.6, 0.6, 0.6, 0.1);
            return;
        }
        if (!(v instanceof EntitySoulFiend)) return;
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
