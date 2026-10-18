package com.divinerpgextended.world.twilight;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;

import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Особенность Мортума - мир мёртвых:
 * <ul>
 * <li>Лета, река забвения: вьётся по дну пещеры. Вошёл - слетают все эффекты (и яды, и бафы), мобы на {@link #FORGET}
 * тиков теряют тебя из виду; пока стоишь в реке - утекает опыт ({@link #DRAIN} очков в секунду). Переправа - у Харона.</li>
 * <li>Склепы с саркофагами, разбросанные черепа и одинокие надгробия ({@link WorldGenTwilightFeatures}).</li>
 * </ul>
 */
public class MortumWorld {

    public static final int FORGET = 100, DRAIN = 3;
    static final String LAST = "dreLethe", FORGOT = "dreForgot";
    /** Для автотеста и замеров: столбцов реки с запуска. */
    public static int letheColumns, crypts, skulls, docks;

    /** Игрок в Лете (вызывается каждый тик, пока касается реки). */
    public static void inLethe(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime(), last = d.getLong(LAST);
        if (now == last) return; // уже за этот тик (несколько блоков реки)
        if (now - last > 20) { // вошёл
            p.clearActivePotions();
            d.setLong(FORGOT, now + FORGET);
            forget(p);
            p.worldObj.playSoundAtEntity(p, "ambient.cave.cave", 1.0F, 0.5F);
            ((WorldServer) p.worldObj).func_147487_a("witchMagic", p.posX, p.posY + 1, p.posZ, 25, 0.5, 0.8, 0.5, 0.05);
        }
        d.setLong(LAST, now);
        d.setLong(FORGOT, Math.max(d.getLong(FORGOT), now + 40)); // пока в реке - не помнят
        if (now % 20 == 0 && !p.capabilities.isCreativeMode) drainXp(p, DRAIN);
    }

    public static boolean forgotten(EntityPlayer p) {
        return p.getEntityData().getLong(FORGOT) > p.worldObj.getTotalWorldTime();
    }

    /** Мобы вокруг теряют игрока. */
    static void forget(EntityPlayer p) {
        for (Object o : p.worldObj.getEntitiesWithinAABB(EntityLiving.class, p.boundingBox.expand(32, 16, 32))) {
            EntityLiving m = (EntityLiving) o;
            if (m.getAttackTarget() == p) m.setAttackTarget(null);
            if (m instanceof EntityCreature && ((EntityCreature) m).getEntityToAttack() == p) ((EntityCreature) m).setTarget(null);
        }
    }

    /** Отнять опыт (очками, с переходом через уровни). */
    public static void drainXp(EntityPlayer p, int n) {
        int total = Math.max(0, p.experienceTotal - n);
        p.experience = 0;
        p.experienceLevel = 0;
        p.experienceTotal = 0;
        p.addExperience(total);
    }

    /** Забытого не выбирают целью. */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (!(e.target instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || !forgotten((EntityPlayer) e.target)) return;
        if (e.entityLiving instanceof EntityLiving) ((EntityLiving) e.entityLiving).setAttackTarget(null);
        if (e.entityLiving instanceof EntityCreature) ((EntityCreature) e.entityLiving).setTarget(null);
    }

    /** Старый ИИ (EntityMob) выбирает цель без события - чистим раз в полсекунды. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !e.player.worldObj.isRemote && e.player.ticksExisted % 10 == 0 && forgotten(e.player)) forget(e.player);
    }

    // ---------------------------------------------------------------- генерация

    /** Плавный шум [-1, 1] по сетке cell (для русла). */
    static double noise(long seed, double x, double z, int cell) {
        double fx = x / cell, fz = z / cell;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = rnd(seed, ix, iz), b = rnd(seed, ix + 1, iz), c = rnd(seed, ix, iz + 1), d = rnd(seed, ix + 1, iz + 1);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }

    private static double rnd(long seed, int x, int z) {
        long h = seed ^ x * 341873128712L ^ z * 132897987541L ^ 0x1E7EL;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h & 0xffffff) / (double) 0x7fffff - 1;
    }

    /** Русло Леты: где шум пересекает ноль - узкая извилистая лента. */
    public static boolean river(long seed, int x, int z) {
        double n = noise(seed, x, z, 72) + 0.35 * noise(seed ^ 0x55, x, z, 23);
        return Math.abs(n) < 0.05;
    }

    /** Река в области заселения [x0, x0 + 16): заменяет дно пещеры на два блока Леты, где берега держат. */
    static int lethe(World w, TwilightWorld tw, int x0, int z0) {
        int n = 0;
        long seed = w.getSeed();
        for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
            if (!river(seed, x, z)) continue;
            int y = WorldGenTwilightFeatures.surface(w, tw, x, z);
            if (y < 6) continue;
            boolean banks = true; // соседи вне русла не ниже уровня реки - иначе она висела бы стенкой
            int[][] nb = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (int[] o : nb) {
                if (river(seed, x + o[0], z + o[1])) continue;
                Block b = w.getBlock(x + o[0], y, z + o[1]);
                if (!b.isOpaqueCube()) banks = false;
            }
            if (!banks) continue;
            w.setBlock(x, y, z, ModBlocks.lethe, 0, 2);
            w.setBlock(x, y - 1, z, ModBlocks.lethe, 0, 2);
            n++;
        }
        letheColumns += n;
        return n;
    }

    /** Череп на полу (скелет, случайный поворот). */
    static boolean skull(World w, Random r, int x, int y, int z) {
        if (!w.isAirBlock(x, y, z)) return false;
        w.setBlock(x, y, z, Blocks.skull, 1, 2);
        if (w.getTileEntity(x, y, z) instanceof TileEntitySkull) {
            TileEntitySkull s = (TileEntitySkull) w.getTileEntity(x, y, z);
            s.func_152107_a(r.nextInt(8) == 0 ? 1 : 0); // изредка череп иссушителя
            s.func_145903_a(r.nextInt(16));
        }
        skulls++;
        return true;
    }

    /** Черепа и изредка надгробие на дне пещеры в области заселения. */
    static void bones(World w, Random r, TwilightWorld tw, int x0, int z0) {
        int n = r.nextInt(4);
        for (int i = 0; i < n; i++) {
            int x = x0 + r.nextInt(16), z = z0 + r.nextInt(16), y = WorldGenTwilightFeatures.surface(w, tw, x, z);
            if (y > 0 && w.getBlock(x, y, z) != ModBlocks.lethe) skull(w, r, x, y + 1, z);
        }
        if (r.nextInt(6) == 0) {
            int x = x0 + 2 + r.nextInt(12), z = z0 + 2 + r.nextInt(12), y = WorldGenTwilightFeatures.surface(w, tw, x, z);
            if (y > 0 && w.isAirBlock(x, y + 1, z) && w.isAirBlock(x, y + 2, z) && w.getBlock(x, y, z) != ModBlocks.lethe) {
                w.setBlock(x, y + 1, z, Blocks.stonebrick, 3, 2);
                w.setBlock(x, y + 2, z, Blocks.stone_slab, 5, 2);
                skull(w, r, x, y + 1, z + 1);
            }
        }
    }

    /**
     * Склеп 7 x 9 с углом в (x, z): стены из каменного кирпича, двускатная крыша из плит, вход с юга; внутри саркофаг
     * (сундук с добычей Мортума между плитами), черепа, светильник и спавнер мобов Мортума; false - место неровное.
     */
    public static boolean crypt(World w, Random r, TwilightWorld tw, int x, int z) {
        int lo = 999, hi = -1;
        for (int i = 0; i < 7; i++) for (int k = 0; k < 9; k++) {
            int y = WorldGenTwilightFeatures.surface(w, tw, x + i, z + k);
            if (y < 0) return false;
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        if (hi - lo > 3) return false;
        int base = hi;
        for (int i = 0; i < 7; i++) for (int k = 0; k < 9; k++) {
            int s = WorldGenTwilightFeatures.surface(w, tw, x + i, z + k);
            for (int y = s + 1; y < base; y++) w.setBlock(x + i, y, z + k, net.divinerpg.utils.blocks.TwilightBlocks.twilightStone, 0, 2);
            w.setBlock(x + i, base, z + k, Blocks.stonebrick, r.nextInt(4) == 0 ? 2 : 0, 2); // пол, местами треснувший
            for (int y = base + 1; y <= base + 6; y++) w.setBlock(x + i, y, z + k, Blocks.air, 0, 2);
            boolean wall = i == 0 || i == 6 || k == 0 || k == 8;
            if (wall) for (int y = base + 1; y <= base + 3; y++) w.setBlock(x + i, y, z + k, Blocks.stonebrick, (i + k + y) % 3 == 0 ? 1 : 0, 2); // местами мох
            // крыша: плиты ступенями к коньку
            int ry = base + 4 + (3 - Math.abs(i - 3)) / 2;
            w.setBlock(x + i, ry, z + k, i == 3 ? Blocks.stonebrick : Blocks.stone_slab, i == 3 ? 3 : 5, 2);
        }
        // вход с юга
        w.setBlock(x + 3, base + 1, z + 8, Blocks.air, 0, 2);
        w.setBlock(x + 3, base + 2, z + 8, Blocks.air, 0, 2);
        // саркофаг у северной стены: сундук между плитами
        w.setBlock(x + 2, base + 1, z + 2, Blocks.stone_slab, 5, 2);
        w.setBlock(x + 4, base + 1, z + 2, Blocks.stone_slab, 5, 2);
        WorldGenTwilightFeatures.chest(w, r, x + 3, base + 1, z + 2, TwilightLoot.dungeon(tw), 6 + r.nextInt(3));
        skull(w, r, x + 3, base + 2, z + 2);
        skull(w, r, x + 1, base + 1, z + 6);
        skull(w, r, x + 5, base + 1, z + 4);
        w.setBlock(x + 3, base + 3, z + 5, tw.lamp(), tw.lampMeta(), 2);
        w.setBlock(x + 1, base + 1, z + 1, Blocks.mob_spawner, 0, 2);
        if (w.getTileEntity(x + 1, base + 1, z + 1) instanceof net.minecraft.tileentity.TileEntityMobSpawner) {
            Class<? extends EntityLiving>[] mobs = tw.mobs();
            String name = (String) net.minecraft.entity.EntityList.classToStringMapping.get(mobs[r.nextInt(mobs.length)]);
            if (name != null) ((net.minecraft.tileentity.TileEntityMobSpawner) w.getTileEntity(x + 1, base + 1, z + 1)).func_145881_a().setEntityName(name);
        }
        crypts++;
        return true;
    }

    /** Пристань Харона на берегу Леты (в области заселения): помост 3 x 3, фонарь и сам Харон; false - берега нет. */
    static boolean dock(World w, Random r, TwilightWorld tw, int x0, int z0) {
        long seed = w.getSeed();
        for (int t = 0; t < 24; t++) {
            int x = x0 + 1 + r.nextInt(14), z = z0 + 1 + r.nextInt(14);
            if (river(seed, x, z) || !(river(seed, x + 2, z) || river(seed, x - 2, z) || river(seed, x, z + 2) || river(seed, x, z - 2))) continue;
            int y = WorldGenTwilightFeatures.surface(w, tw, x, z);
            if (y < 6 || !w.isAirBlock(x, y + 3, z)) continue;
            for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) {
                if (w.getBlock(x + i, y, z + k) == ModBlocks.lethe) continue;
                w.setBlock(x + i, y, z + k, tw.planks(), 0, 2);
            }
            w.setBlock(x + 1, y + 1, z + 1, Blocks.fence, 0, 2);
            w.setBlock(x + 1, y + 2, z + 1, tw.lamp(), tw.lampMeta(), 2);
            com.divinerpgextended.entity.EntityCharon c = new com.divinerpgextended.entity.EntityCharon(w);
            c.setLocationAndAngles(x + 0.5, y + 1, z + 0.5, r.nextFloat() * 360, 0);
            w.spawnEntityInWorld(c);
            docks++;
            return true;
        }
        return false;
    }
}
