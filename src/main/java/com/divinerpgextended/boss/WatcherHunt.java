package com.divinerpgextended.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.divinerpg.entities.vanilla.EntityTheWatcher;
import net.divinerpg.entities.vanilla.projectile.EntityWatcherShot;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import com.divinerpgextended.world.WatcherVault;
import com.divinerpgextended.world.WorldGenOuterLairs;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Смотритель в своём логове ({@link WatcherVault}) - охотник, а не болтающийся глаз: летит к ближайшему бойцу и пробивает
 * себе дорогу взрывами. Вместо его поведения из Divine (через {@link com.divinerpgextended.asm.WatcherTransformer}):
 * <ul>
 * <li>Заряд: глаз {@link #charge} тиков светится, к цели тянется луч; за треть заряда до выстрела прицел замирает - кто
 * ушёл за угол, тот увернулся. Нет прямой видимости - бьёт в первую преграду на пути к цели (прокапывается к тебе).</li>
 * <li>После выстрела {@link #HEAT} тиков перегрет - получает двойной урон.</li>
 * <li>Ниже половины здоровья заряжается быстрее, ниже {@link #DEBRIS_AT} - дробит свод: над бойцами с предупреждением
 * падают раскалённые глыбы незеррака (бьют и остаются укрытием).</li>
 * <li>Взрывы в зале ломают только завал: обсидиан и блоки с содержимым целы, сломанное ничего не роняет, предметы на
 * земле и опыт не страдают, сам Смотритель от своих взрывов тоже ({@link #onDetonate}).</li>
 * </ul>
 * Адское оружие ({@link #onHurt}): пуля пушки гаста во время заряда сбивает выстрел, оглушает на {@link #STUN} тиков и
 * перегревает; пули пушки гаста и прочее адское оружие бьют его в полтора раза сильнее; огненное (мечи Inferno, Molten,
 * Пылающая ярость, стрелы Inferno-лука) копит жар - {@link #WARMTH} попаданий подряд перегревают; стрела Bluefire-лука
 * ослепляет на {@link #BLIND} тиков (теряет цель, не стреляет), а её взрывы, как и его, прорубают завал.
 */
public class WatcherHunt {

    public static final int HEAT = 40, CHASE_RANGE = 48, STUN = 30, BLIND = 60, WARMTH = 4;
    public static final float DEBRIS_AT = 0.2F, SHOT_SPEED = 1.3F;
    /** Прямой урон перегретому: множитель. */
    public static final float HEAT_MULT = 2.0F;

    /** Состояние боя (на время жизни босса; сохранять не нужно). */
    static final class State {
        int charge, cooldown = 40, heat, still, debrisTimer = 60, stun, blind, warmth;
        double aimX, aimY, aimZ, lastX, lastY, lastZ;
        boolean locked;
        final List<int[]> debris = new ArrayList<int[]>(); // x, y, z, тик падения
    }

    static final Map<Entity, State> STATES = new WeakHashMap<Entity, State>();

    static State state(Entity e) {
        State s = STATES.get(e);
        if (s == null) STATES.put(e, s = new State());
        return s;
    }

    /** Центр логова Смотрителя, где он сейчас (или null - не в логове). */
    public static int[] vault(World w, double x, double y, double z) {
        if (!WorldGenOuterLairs.enabled(WorldGenOuterLairs.WATCHER) || w.provider.dimensionId != WorldGenOuterLairs.dim(WorldGenOuterLairs.WATCHER)) return null;
        int[] c = WorldGenOuterLairs.nearest(w.getSeed(), WorldGenOuterLairs.WATCHER, x, z);
        if (c == null) return null;
        int dx = MathHelper.floor_double(x) - c[0], dz = MathHelper.floor_double(z) - c[2], iy = MathHelper.floor_double(y);
        return dx * dx + dz * dz < (WatcherVault.R + 1) * (WatcherVault.R + 1) && iy >= WatcherVault.FLOOR - 1 && iy <= WatcherVault.TOP + 2 ? c : null;
    }

    /** Заряд выстрела по здоровью. */
    public static int charge(EntityTheWatcher w) {
        float hp = w.getHealth() / w.getMaxHealth();
        return hp > 0.5F ? 30 : hp > DEBRIS_AT ? 22 : 18;
    }

    static int cooldown(EntityTheWatcher w) {
        float hp = w.getHealth() / w.getMaxHealth();
        return hp > 0.5F ? 50 : hp > DEBRIS_AT ? 32 : 26;
    }

    public static boolean charging(Entity e) {
        State s = STATES.get(e);
        return s != null && s.charge > 0;
    }

    public static boolean stunned(Entity e) {
        State s = STATES.get(e);
        return s != null && s.stun > 0;
    }

    /** Для проверок: начать заряд / сбросить состояние боя. */
    public static void startCharge(EntityTheWatcher w) {
        State s = state(w);
        s.charge = charge(w);
        s.locked = false;
    }

    public static void reset(Entity e) {
        STATES.remove(e);
    }

    public static boolean overheated(Entity e) {
        State s = STATES.get(e);
        return s != null && s.heat > 0;
    }

    /** Ход Смотрителя (вместо его updateEntityActionState); false - не в логове, ведёт себя как в Divine. */
    public static boolean act(EntityTheWatcher w) {
        if (w.worldObj.isRemote) return false;
        int[] c = vault(w.worldObj, w.posX, w.posY + w.height / 2, w.posZ);
        if (c == null) return false;
        State s = state(w);
        EntityPlayer target = target(w, c);
        double ex = w.posX, ey = w.posY + w.height * 0.5, ez = w.posZ;
        if (s.heat > 0) {
            s.heat--;
            if (s.heat % 4 == 0) ((WorldServer) w.worldObj).func_147487_a("smoke", ex, ey + 1, ez, 6, 1.0, 0.8, 1.0, 0.02);
        }
        if (s.stun > 0) { // оглушён пушкой гаста: висит, искрит
            s.stun--;
            w.motionX *= 0.5;
            w.motionY *= 0.5;
            w.motionZ *= 0.5;
            if (s.stun % 3 == 0) ((WorldServer) w.worldObj).func_147487_a("crit", ex, ey, ez, 8, 1.2, 1.2, 1.2, 0.1);
            return true;
        }
        if (s.blind > 0) { // ослеплён Bluefire: цель потеряна, плывёт куда глаза глядят
            s.blind--;
            s.charge = 0;
            if (s.blind % 20 == 0) w.getEntityData().setIntArray("dreWBlindTo", new int[] { c[0] + w.getRNG().nextInt(25) - 12, WatcherVault.bossY(), c[2] + w.getRNG().nextInt(25) - 12 });
            int[] to = w.getEntityData().getIntArray("dreWBlindTo");
            if (to.length == 3) fly(w, to[0] + 0.5, to[1] - w.height / 2, to[2] + 0.5, 0.07);
            return true;
        }
        if (target == null) { // никого - висит в своём кармане
            s.charge = 0;
            fly(w, c[0] + 0.5, WatcherVault.bossY() - w.height / 2 + 0.5, c[2] + 0.5, 0.08);
            return true;
        }
        double tx = target.posX, ty = target.posY + target.getEyeHeight() * 0.7, tz = target.posZ;
        double dx = tx - ex, dy = ty - ey, dz = tz - ez, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        w.rotationYaw = w.renderYawOffset = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
        w.rotationPitch = (float) (-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        // движение: к цели, держась в 6-8 блоках; упёрся - стоит и пробивает
        double speed = w.getHealth() / w.getMaxHealth() > 0.5F ? 0.11 : 0.15;
        if (dist > 7) fly(w, tx, target.posY + 1 - w.height / 2, tz, speed);
        else fly(w, ex - dx / dist * 0.5, w.posY, ez - dz / dist * 0.5, speed * 0.5);
        double moved = Math.abs(w.posX - s.lastX) + Math.abs(w.posY - s.lastY) + Math.abs(w.posZ - s.lastZ);
        s.still = moved < 0.02 && dist > 7 ? s.still + 1 : 0;
        s.lastX = w.posX;
        s.lastY = w.posY;
        s.lastZ = w.posZ;
        MovingObjectPosition hit = w.worldObj.func_147447_a(Vec3.createVectorHelper(ex, ey, ez), Vec3.createVectorHelper(tx, ty, tz), false, true, false);
        boolean sight = hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK;
        if (s.charge > 0) {
            s.charge--;
            if (!s.locked) aim(s, sight, hit, tx, ty, tz);
            if (s.charge <= charge(w) / 3) s.locked = true;
            beam((WorldServer) w.worldObj, ex, ey, ez, s);
            if (s.charge == 0) fire(w, s, ex, ey, ez);
        } else if (--s.cooldown <= 0 && (sight || s.still > 20 || dist > 7 && hit != null)) {
            s.charge = charge(w);
            s.locked = false;
            aim(s, sight, hit, tx, ty, tz);
            w.worldObj.playSoundAtEntity(w, "mob.ghast.charge", 3.0F, 0.6F);
        }
        if (w.getHealth() / w.getMaxHealth() <= DEBRIS_AT) debris(w, s, c, target);
        return true;
    }

    /** Цель: ближайший живой боец в логове (творческий режим не в счёт). */
    static EntityPlayer target(EntityTheWatcher w, int[] c) {
        EntityPlayer best = null;
        double bd = CHASE_RANGE * CHASE_RANGE;
        for (Object o : w.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (!p.isEntityAlive() || p.capabilities.disableDamage || vault(p.worldObj, p.posX, p.posY + 1, p.posZ) == null) continue;
            double d = p.getDistanceSqToEntity(w);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** Плавно к точке (сила - доля скорости). */
    static void fly(EntityTheWatcher w, double x, double y, double z, double speed) {
        double dx = x - w.posX, dy = y - w.posY, dz = z - w.posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 0.3) {
            w.motionX *= 0.6;
            w.motionY *= 0.6;
            w.motionZ *= 0.6;
            return;
        }
        w.motionX = w.motionX * 0.7 + dx / d * speed * 0.3 * 3;
        w.motionY = w.motionY * 0.7 + dy / d * speed * 0.3 * 3;
        w.motionZ = w.motionZ * 0.7 + dz / d * speed * 0.3 * 3;
    }

    /** Куда бить: видно - в бойца, нет - в первую преграду на пути к нему. */
    static void aim(State s, boolean sight, MovingObjectPosition hit, double tx, double ty, double tz) {
        if (sight || hit == null) {
            s.aimX = tx;
            s.aimY = ty;
            s.aimZ = tz;
        } else {
            s.aimX = hit.blockX + 0.5;
            s.aimY = hit.blockY + 0.5;
            s.aimZ = hit.blockZ + 0.5;
        }
    }

    /** Луч от глаза к прицелу: краснеет, пока прицел ещё ведёт, белеет, когда замер. */
    static void beam(WorldServer ws, double ex, double ey, double ez, State s) {
        if (s.charge % 2 != 0) return;
        double dx = s.aimX - ex, dy = s.aimY - ey, dz = s.aimZ - ez, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        String part = s.locked ? "fireworksSpark" : "reddust";
        for (double t = 2.0; t < d; t += 0.8) ws.func_147487_a(part, ex + dx / d * t, ey + dy / d * t, ez + dz / d * t, 1, 0, 0, 0, 0);
    }

    /** Выстрел снарядом Смотрителя по прицелу (с поправкой на падение снаряда). */
    static void fire(EntityTheWatcher w, State s, double ex, double ey, double ez) {
        EntityWatcherShot shot = new EntityWatcherShot(w.worldObj, w);
        double dx = s.aimX - ex, dz = s.aimZ - ez, flat = Math.sqrt(dx * dx + dz * dz);
        double t = Math.sqrt(flat * flat + (s.aimY - ey) * (s.aimY - ey)) / SHOT_SPEED;
        double dy = s.aimY - ey + 0.5 * 0.03 * t * t;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        shot.setPosition(ex + dx / d * 2.4, ey + dy / d * 2.4, ez + dz / d * 2.4);
        shot.setThrowableHeading(dx, dy, dz, SHOT_SPEED, 0F);
        w.worldObj.spawnEntityInWorld(shot);
        w.worldObj.playSoundAtEntity(w, "mob.ghast.fireball", 3.0F, 0.7F);
        s.heat = HEAT;
        s.cooldown = cooldown(w);
        s.locked = false;
        s.still = 0;
    }

    /** Глыбы со свода над бойцами: 20 тиков предупреждение (пепел), потом падают. */
    static void debris(EntityTheWatcher w, State s, int[] c, EntityPlayer target) {
        WorldServer ws = (WorldServer) w.worldObj;
        long now = ws.getTotalWorldTime();
        if (--s.debrisTimer <= 0) {
            s.debrisTimer = 60;
            for (int i = 0; i < 3; i++) {
                int x = MathHelper.floor_double(target.posX) + w.getRNG().nextInt(7) - 3, z = MathHelper.floor_double(target.posZ) + w.getRNG().nextInt(7) - 3;
                int dx = x - c[0], dz = z - c[2];
                if (dx * dx + dz * dz >= WatcherVault.R * WatcherVault.R) continue;
                int y = WatcherVault.top(Math.sqrt(dx * dx + dz * dz));
                while (y > WatcherVault.FLOOR + 2 && !ws.isAirBlock(x, y, z)) y--;
                if (y <= target.posY + 1) continue; // над бойцом нет свободного места - не сыплем сквозь камень
                s.debris.add(new int[] { x, y, z, (int) (now + 20) });
            }
        }
        for (Iterator<int[]> it = s.debris.iterator(); it.hasNext();) {
            int[] d = it.next();
            if (now < d[3]) {
                if (now % 4 == 0) ws.func_147487_a("lava", d[0] + 0.5, d[1] + 0.2, d[2] + 0.5, 2, 0.3, 0.1, 0.3, 0);
                continue;
            }
            it.remove();
            if (!ws.isAirBlock(d[0], d[1], d[2])) continue;
            EntityFallingBlock fb = new EntityFallingBlock(ws, d[0] + 0.5, d[1] + 0.5, d[2] + 0.5, Blocks.netherrack, 0);
            fb.field_145812_b = 1; // не исчезать в первый тик
            fb.func_145806_a(true); // бьёт тех, на кого упала
            fb.setFire(5);
            ws.spawnEntityInWorld(fb);
        }
    }

    static final java.util.Set<net.minecraft.item.Item> FIRE_SWORDS = new java.util.HashSet<net.minecraft.item.Item>();

    static boolean fireSword(net.minecraft.item.Item i) {
        if (FIRE_SWORDS.isEmpty()) {
            FIRE_SWORDS.add(net.divinerpg.utils.items.VanillaItemsWeapons.infernoSword);
            FIRE_SWORDS.add(net.divinerpg.utils.items.VanillaItemsWeapons.moltenSword);
            FIRE_SWORDS.add(net.divinerpg.utils.items.VanillaItemsWeapons.flamingFury);
        }
        return i != null && FIRE_SWORDS.contains(i);
    }

    /** Пуля пушки гаста (у стрелка в руке пушка гаста). */
    static boolean ghastCannon(net.minecraft.util.DamageSource src) {
        Entity bullet = src.getSourceOfDamage(), shooter = src.getEntity();
        if (!(bullet instanceof net.divinerpg.entities.vanilla.projectile.EntityShooterBullet)) return false;
        String tex = ((net.divinerpg.entities.vanilla.projectile.EntityShooterBullet) bullet).getTextureName();
        if (tex != null && tex.toLowerCase().contains("ghast")) return true;
        return shooter instanceof EntityPlayer && ((EntityPlayer) shooter).getHeldItem() != null
            && ((EntityPlayer) shooter).getHeldItem().getItem() == net.divinerpg.utils.items.VanillaItemsWeapons.ghastCannon;
    }

    /** Адское оружие против Смотрителя (только в логове) и двойной урон перегретому. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        if (!(e.entityLiving instanceof EntityTheWatcher) || e.entityLiving.worldObj.isRemote) return;
        EntityTheWatcher w = (EntityTheWatcher) e.entityLiving;
        if (vault(w.worldObj, w.posX, w.posY + w.height / 2, w.posZ) == null) return;
        State s = state(w);
        WorldServer ws = (WorldServer) w.worldObj;
        double ex = w.posX, ey = w.posY + w.height / 2, ez = w.posZ;
        float mult = 1F;
        boolean hot = overheated(w); // перегрев от этого же удара действует со следующего
        net.minecraft.item.Item melee = com.divinerpgextended.entity.ThroneCombat.melee(e.source);
        if (ghastCannon(e.source)) {
            mult = 1.5F;
            if (s.charge > 0) { // сбил заряд: оглушён и перегрет
                s.charge = 0;
                s.locked = false;
                s.stun = STUN;
                s.heat = HEAT;
                s.cooldown = Math.max(s.cooldown, cooldown(w));
                ws.func_147487_a("largeexplode", ex, ey, ez, 1, 0, 0, 0, 0);
                w.worldObj.playSoundAtEntity(w, "random.break", 2.0F, 0.5F);
            }
        } else if (fireSword(melee) || com.divinerpgextended.entity.ThroneCombat.isArrow(e.source, "infernoArrow")) {
            if (++s.warmth >= WARMTH) {
                s.warmth = 0;
                s.heat = HEAT;
                ws.func_147487_a("flame", ex, ey, ez, 40, 1.5, 1.5, 1.5, 0.05);
                w.worldObj.playSoundAtEntity(w, "fire.ignite", 2.0F, 0.6F);
            }
        } else if (com.divinerpgextended.entity.ThroneCombat.isArrow(e.source, "bluefireArrow")) {
            s.blind = BLIND;
            s.charge = 0;
            ws.func_147487_a("magicCrit", ex, ey, ez, 30, 1.5, 1.5, 1.5, 0.1);
        }
        if (melee != null && com.divinerpgextended.arcana.WeaponOrigin.of(new net.minecraft.item.ItemStack(melee)) == com.divinerpgextended.arcana.WeaponOrigin.NETHER)
            mult = Math.max(mult, 1.5F);
        if (hot) mult *= HEAT_MULT;
        e.ammount *= mult;
    }

    /** Взрывы в зале: ломается только завал (без выпадения), предметы и опыт на земле не страдают. */
    @SubscribeEvent
    public void onDetonate(ExplosionEvent.Detonate e) {
        World w = e.world;
        if (w.isRemote) return;
        int[] c = vault(w, e.explosion.explosionX, e.explosion.explosionY, e.explosion.explosionZ);
        if (c == null) return;
        for (Iterator<Entity> it = e.getAffectedEntities().iterator(); it.hasNext();) {
            Entity en = it.next();
            if (en instanceof EntityItem || en instanceof EntityXPOrb || en instanceof EntityTheWatcher) it.remove();
        }
        List<ChunkPosition> blocks = e.getAffectedBlocks();
        List<ChunkPosition> breakNow = new ArrayList<ChunkPosition>();
        for (ChunkPosition p : blocks) {
            if (!WatcherVault.interior(p.chunkPosX - c[0], p.chunkPosY, p.chunkPosZ - c[2])) continue;
            Block b = w.getBlock(p.chunkPosX, p.chunkPosY, p.chunkPosZ);
            if (b == Blocks.air || b == Blocks.obsidian || b == Blocks.bedrock || b.hasTileEntity(w.getBlockMetadata(p.chunkPosX, p.chunkPosY, p.chunkPosZ))) continue;
            breakNow.add(p);
        }
        blocks.clear();
        for (ChunkPosition p : breakNow) w.setBlock(p.chunkPosX, p.chunkPosY, p.chunkPosZ, Blocks.air, 0, 3);
    }
}
