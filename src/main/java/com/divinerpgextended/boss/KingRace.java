package com.divinerpgextended.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.divinerpg.entities.vanilla.EntityKingOfScorchers;
import net.divinerpg.entities.vanilla.EntityScorcher;
import net.divinerpg.entities.vanilla.projectile.EntityKingOfScorchersMeteor;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.world.ScorcherPit;
import com.divinerpgextended.world.WatcherVault;
import com.divinerpgextended.world.WorldGenOuterLairs;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Король скорчеров - лихач. У Divine его выстрелы и метеоры написаны, но не работают (сидят в updateEntityActionState, а у
 * мобов Divine ИИ на задачах и этот метод не вызывается) - здесь они идут сами, каждый тик ({@link #onUpdate}).
 * <p>
 * В своей палате ({@link ScorcherPit}) он гоняет по треку:
 * <ul>
 * <li>Круги по разметке (радиус {@link #LAP_R}) на большой скорости. За ним тянется огненный след ({@link #TRAIL_LIFE} тиков):
 * стоять в нём - огонь. Задел бойца - таран с отбросом и поджогом. На бегу стреляет огнём и раз в {@link #METEORS_EVERY}
 * тиков зовёт метеоры; время от времени выпускает на трек скорчеров (до {@link #SCORCHERS_MAX}).</li>
 * <li>После {@link #LAPS_TO_PIT} кругов - пит-стоп на клетчатой финишной полосе ({@link #PIT} тиков): стоит и не атакует,
 * щит пламени снят - удары вплотную проходят полностью. Это окно для ближнего боя.</li>
 * <li>Ниже {@link #LOW} здоровья на осях иногда срезает через мостки и помост - в центре больше не отсидеться.</li>
 * </ul>
 * Урон по нему ({@link #onHurt}): щит пламени - удары вплотную вполовину, но водное оружие (водные кинжал, молот и трезубец -
 * вода гасит пламя) - в полтора раза, на пит-стопе - в два с половиной; Maelstrom и Sound of Music - в полтора раза. Урон от
 * него: огонь, жар и поджог - без огнестойкости не выстоять; в полном сете Inferno или Netherite (огнестойкость) таран,
 * выстрелы и метеоры - вдвое слабее.
 */
public class KingRace {

    public static final double LAP_R = 16, SPEED = 0.6, CUT_SPEED = 0.75, PIT_DX = -14;
    public static final int LAPS_TO_PIT = 3, PIT = 80, TRAIL_LIFE = 80, FIREBALL_EVERY = 30, METEORS_EVERY = 250, SCORCHERS_EVERY = 400,
        SCORCHERS_MAX = 4, RAM_EVERY = 20;
    public static final float RAM = 8F, TRAIL_DMG = 3F, LOW = 0.35F, SHIELD = 0.5F, AQUA = 1.5F, PIT_AQUA = 2.5F, RANGED = 1.5F, HEAT_ARMOR = 0.5F;

    static final int RACE = 0, TO_PIT = 1, STOP = 2, CUT = 3;

    static final class State {
        int mode = RACE, timer, fireball, meteors = 120, scorchers = 200;
        double angle, travelled, cutX, cutZ;
        boolean init;
        final List<double[]> trail = new ArrayList<double[]>(); // x, z, тик угасания
        final Map<Entity, Long> rammed = new WeakHashMap<Entity, Long>();
        final List<Entity> minions = new ArrayList<Entity>();
    }

    static final Map<Entity, State> STATES = new WeakHashMap<Entity, State>();

    static State state(Entity e) {
        State s = STATES.get(e);
        if (s == null) STATES.put(e, s = new State());
        return s;
    }

    /** Центр новой палаты, где сейчас король (или null - вне её или палата старого вида). */
    public static int[] pit(World w, double x, double y, double z) {
        if (!WorldGenOuterLairs.inside(w, WorldGenOuterLairs.SCORCHERS, x, y, z)) return null;
        int[] c = WorldGenOuterLairs.nearest(w.getSeed(), WorldGenOuterLairs.SCORCHERS, x, z);
        return c == null || !ScorcherPit.isNew(w, c[0], c[2]) ? null : c;
    }

    // ---------------------------------------------------------------- для проверок

    public static boolean pitStop(Entity e) {
        State s = STATES.get(e);
        return s != null && s.mode == STOP;
    }

    public static int trail(Entity e) {
        State s = STATES.get(e);
        return s == null ? 0 : s.trail.size();
    }

    public static int minions(Entity e) {
        State s = STATES.get(e);
        if (s == null) return 0;
        prune(s);
        return s.minions.size();
    }

    /** Сразу на пит-стоп (сделал свои круги). */
    public static void toPit(EntityKingOfScorchers k) {
        State s = state(k);
        s.travelled = LAPS_TO_PIT * Math.PI * 2;
    }

    public static void spawnScorchersFor(EntityKingOfScorchers k) {
        spawnScorchers(k, state(k), 2);
    }

    public static void reset(Entity e) {
        STATES.remove(e);
    }

    // ---------------------------------------------------------------- ход

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (!(e.entityLiving instanceof EntityKingOfScorchers) || e.entityLiving.worldObj.isRemote || e.entityLiving.isDead) return;
        EntityKingOfScorchers k = (EntityKingOfScorchers) e.entityLiving;
        int[] c = pit(k.worldObj, k.posX, k.posY + 1, k.posZ);
        if (c != null) race(k, c);
        else divineAttacks(k);
    }

    /** Вне палаты - как задумано в Divine: выстрел раз в 15 тиков, метеоры раз в 250. */
    static void divineAttacks(EntityKingOfScorchers k) {
        EntityPlayer t = k.worldObj.getClosestVulnerablePlayerToEntity(k, 40);
        if (t == null) return;
        if (k.ticksExisted % 15 == 0) k.attackEntityWithRangedAttack(t);
        if (k.ticksExisted % METEORS_EVERY == 0) meteors(k, t);
    }

    static void race(EntityKingOfScorchers k, int[] c) {
        State s = state(k);
        WorldServer ws = (WorldServer) k.worldObj;
        double cx = c[0] + 0.5, cz = c[2] + 0.5;
        if (!s.init) { // свой ход вместо задач Divine (иначе идёт напролом к игроку)
            k.tasks.taskEntries.clear();
            k.targetTasks.taskEntries.clear();
            k.getNavigator().clearPathEntity();
            s.angle = Math.atan2(k.posZ - cz, k.posX - cx);
            s.init = true;
        }
        long now = ws.getTotalWorldTime();
        burnTrail(ws, s, now);
        EntityPlayer t = fighter(ws, k);
        if (t == null) { // никого - стоит на финише
            k.motionX = k.motionZ = 0;
            return;
        }
        boolean low = k.getHealth() <= k.getMaxHealth() * LOW;
        if (s.mode == STOP) {
            double px = cx + PIT_DX, pz = cz;
            drive(k, px, pz, Math.min(0.3, Math.hypot(px - k.posX, pz - k.posZ) * 0.3));
            if (s.timer % 4 == 0) ws.func_147487_a("largesmoke", k.posX, k.posY + 2.5, k.posZ, 4, 0.5, 0.5, 0.5, 0.02);
            if (s.timer % 20 == 0) ws.playSoundAtEntity(k, "random.fizz", 1.0F, 0.6F);
            if (--s.timer <= 0) {
                s.mode = RACE;
                s.travelled = 0;
                s.angle = Math.atan2(k.posZ - cz, k.posX - cx);
                ws.playSoundAtEntity(k, "mob.blaze.breathe", 3.0F, 0.6F);
            }
            return;
        }
        if (s.mode == CUT) { // напрямик через мостки и помост
            drive(k, s.cutX, s.cutZ, CUT_SPEED);
            if (Math.hypot(s.cutX - k.posX, s.cutZ - k.posZ) < 1.5) {
                s.mode = RACE;
                s.angle = Math.atan2(k.posZ - cz, k.posX - cx);
            }
        } else {
            double prev = s.angle;
            s.angle += SPEED / LAP_R;
            s.travelled += SPEED / LAP_R;
            double a = s.angle + 0.3;
            drive(k, cx + Math.cos(a) * LAP_R, cz + Math.sin(a) * LAP_R, SPEED);
            if (s.travelled >= LAPS_TO_PIT * Math.PI * 2) s.mode = TO_PIT;
            if (s.mode == TO_PIT && Math.abs(wrap(s.angle - Math.PI)) < 0.25) { // на финишной прямой - в боксы
                s.mode = STOP;
                s.timer = PIT;
                ws.playSoundAtEntity(k, "random.anvil_land", 1.5F, 0.5F);
                return;
            }
            double q = Math.PI / 2;
            if (low && s.mode == RACE && Math.floor(prev / q) != Math.floor(s.angle / q) && k.getRNG().nextBoolean()) { // срезать по оси
                double ax = Math.round(s.angle / q) * q + Math.PI;
                s.cutX = cx + Math.cos(ax) * LAP_R;
                s.cutZ = cz + Math.sin(ax) * LAP_R;
                s.mode = CUT;
                ws.playSoundAtEntity(k, "mob.ghast.scream", 3.0F, 0.5F);
            }
        }
        if (k.ticksExisted % 2 == 0) s.trail.add(new double[] { k.posX, k.posZ, now + TRAIL_LIFE });
        ram(k, s, now);
        if (--s.fireball <= 0) {
            s.fireball = low ? FIREBALL_EVERY * 2 / 3 : FIREBALL_EVERY;
            k.attackEntityWithRangedAttack(t);
        }
        if (--s.meteors <= 0) {
            s.meteors = METEORS_EVERY;
            meteors(k, t);
        }
        if (--s.scorchers <= 0) {
            s.scorchers = SCORCHERS_EVERY;
            spawnScorchers(k, s, 2);
        }
    }

    static double wrap(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    /** Ехать к точке (по горизонтали; вниз тянет как обычно). */
    static void drive(EntityKingOfScorchers k, double x, double z, double speed) {
        double dx = x - k.posX, dz = z - k.posZ, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 0.05) {
            k.motionX = k.motionZ = 0;
            return;
        }
        k.motionX = dx / d * speed;
        k.motionZ = dz / d * speed;
        k.rotationYaw = k.renderYawOffset = k.rotationYawHead = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
    }

    /** Огненный след: искры, бойцу в нём - огонь. */
    static void burnTrail(WorldServer ws, State s, long now) {
        for (Iterator<double[]> it = s.trail.iterator(); it.hasNext();) {
            double[] p = it.next();
            if (now >= p[2]) {
                it.remove();
                continue;
            }
            if (now % 3 == 0) ws.func_147487_a("flame", p[0], WatcherVault.FLOOR + 1.1, p[1], 1, 0.3, 0.05, 0.3, 0.01);
        }
        if (now % 10 != 0) return;
        for (Object o : ws.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.capabilities.disableDamage || p.posY > WatcherVault.FLOOR + 3) continue;
            for (double[] q : s.trail)
                if (Math.abs(q[0] - p.posX) < 1.0 && Math.abs(q[1] - p.posZ) < 1.0) {
                    p.attackEntityFrom(DamageSource.inFire, TRAIL_DMG);
                    p.setFire(4);
                    break;
                }
        }
    }

    /** Таран: задел бойца - урон, отброс от себя, поджог (одного и того же - не чаще раза в RAM_EVERY тиков). */
    static void ram(EntityKingOfScorchers k, State s, long now) {
        for (Object o : k.worldObj.getEntitiesWithinAABB(EntityPlayer.class, k.boundingBox.expand(0.5, 0.2, 0.5))) {
            EntityPlayer p = (EntityPlayer) o;
            Long last = s.rammed.get(p);
            if (p.capabilities.disableDamage || last != null && now - last < RAM_EVERY) continue;
            s.rammed.put(p, now);
            p.attackEntityFrom(DamageSource.causeMobDamage(k), RAM);
            double dx = p.posX - k.posX, dz = p.posZ - k.posZ, d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
            p.addVelocity(dx / d * 1.3, 0.45, dz / d * 1.3);
            p.velocityChanged = true;
            p.setFire(5);
        }
    }

    /** Три метеора над бойцом (как в Divine). */
    static void meteors(EntityKingOfScorchers k, EntityPlayer t) {
        for (int i = 0; i < 3; i++) {
            EntityKingOfScorchersMeteor m = new EntityKingOfScorchersMeteor(k.worldObj, t.posX + (k.getRNG().nextDouble() - k.getRNG().nextDouble()) * 2.0, t.posY + 10.0,
                t.posZ + (k.getRNG().nextDouble() - k.getRNG().nextDouble()) * 2.0);
            k.worldObj.spawnEntityInWorld(m);
        }
    }

    static void prune(State s) {
        for (Iterator<Entity> it = s.minions.iterator(); it.hasNext();) if (it.next().isDead) it.remove();
    }

    /** Скорчеры на треке (приспешники: без добычи, гибнут с королём). */
    static void spawnScorchers(EntityKingOfScorchers k, State s, int n) {
        prune(s);
        for (int i = 0; i < n && s.minions.size() < SCORCHERS_MAX; i++) {
            EntityScorcher sc = new EntityScorcher(k.worldObj);
            double a = k.getRNG().nextDouble() * Math.PI * 2;
            int[] c = WorldGenOuterLairs.nearest(k.worldObj.getSeed(), WorldGenOuterLairs.SCORCHERS, k.posX, k.posZ);
            double x = (c == null ? k.posX : c[0] + 0.5) + Math.cos(a) * 18, z = (c == null ? k.posZ : c[2] + 0.5) + Math.sin(a) * 18;
            sc.setLocationAndAngles(x, WatcherVault.FLOOR + 1, z, k.getRNG().nextFloat() * 360, 0);
            BossMinions.mark(sc, k);
            k.worldObj.spawnEntityInWorld(sc);
            s.minions.add(sc);
            ((WorldServer) k.worldObj).func_147487_a("flame", x, WatcherVault.FLOOR + 1.5, z, 20, 0.4, 0.8, 0.4, 0.05);
        }
    }

    static EntityPlayer fighter(World w, EntityKingOfScorchers k) {
        EntityPlayer best = null;
        double bd = 48 * 48;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (!p.isEntityAlive() || p.capabilities.disableDamage || pit(w, p.posX, p.posY + 1, p.posZ) == null) continue;
            double d = p.getDistanceSqToEntity(k);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- урон

    /** Водное оружие: вода гасит щит пламени. */
    public static boolean aquatic(Item i) {
        return i == VanillaItemsWeapons.aquaticDagger || i == VanillaItemsWeapons.aquaticMaul || i == VanillaItemsWeapons.aquaticTrident;
    }

    /** Полный сет Inferno или Netherite. */
    public static boolean heatproof(EntityPlayer p) {
        ItemStack[] a = p.inventory.armorInventory;
        if (a[0] == null || a[1] == null || a[2] == null || a[3] == null) return false;
        return a[0].getItem() == VanillaItemsArmor.infernoBoots && a[1].getItem() == VanillaItemsArmor.infernoLegs && a[2].getItem() == VanillaItemsArmor.infernoBody
            && a[3].getItem() == VanillaItemsArmor.infernoHelmet
            || a[0].getItem() == VanillaItemsArmor.netheriteBoots && a[1].getItem() == VanillaItemsArmor.netheriteLegs && a[2].getItem() == VanillaItemsArmor.netheriteBody
                && a[3].getItem() == VanillaItemsArmor.netheriteHelmet;
    }

    /** Король или его снаряды. */
    static boolean fromKing(DamageSource src) {
        Entity a = src.getEntity(), b = src.getSourceOfDamage();
        return a instanceof EntityKingOfScorchers || b instanceof EntityKingOfScorchers || b instanceof EntityKingOfScorchersMeteor
            || b instanceof net.divinerpg.entities.vanilla.projectile.EntityKingOfScorchersShot;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        if (e.entityLiving instanceof EntityPlayer) {
            if (fromKing(e.source) && heatproof((EntityPlayer) e.entityLiving)) e.ammount *= HEAT_ARMOR;
            return;
        }
        if (!(e.entityLiving instanceof EntityKingOfScorchers)) return;
        Item melee = com.divinerpgextended.entity.ThroneCombat.melee(e.source);
        boolean stop = pitStop(e.entityLiving);
        if (melee != null) e.ammount *= aquatic(melee) ? (stop ? PIT_AQUA : AQUA) : (stop ? 1F : SHIELD);
        else if (NetherArsenal.maelstrom(e.source) || e.source.getSourceOfDamage() instanceof net.divinerpg.entities.iceika.projectile.EntityMusic) e.ammount *= RANGED;
    }

    /** Не трогать задачи моба вне палаты: проверка для тестов - моб под нашим управлением. */
    public static boolean driven(EntityLiving k) {
        State s = STATES.get(k);
        return s != null && s.init;
    }
}
