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
 * <li>Лета, река забвения: багровая, по дну мира, как лава в Аду - гладь на одной высоте, русло прорезает дно, под
 * холмами уходит в тоннель, со скал падают водопады. Плыть по ней без ладьи Харона - души утопленников тянут
 * на дно и грызут ({@link #BITE} в секунду); обычная лодка в Лете за пару секунд разъедается. Вошёл - слетают все эффекты (и яды, и бафы), мобы на {@link #FORGET}
 * тиков теряют тебя из виду; пока стоишь в реке - утекает опыт ({@link #DRAIN} очков в секунду). Переправа - у Харона.</li>
 * <li>Склепы с саркофагами, останки гигантов (череп наполовину в земле - внутри сундук, грудная клетка), редкие черепа и
 * одинокие надгробия ({@link WorldGenTwilightFeatures}).</li>
 * </ul>
 */
public class MortumWorld {

    public static final int FORGET = 100, DRAIN = 3;
    /** Высота пола арен Мортума (LEVEL + 4); гладь Леты SEA (дно мира - 20..23), глубина русла до DEEP, над водой HEAD. */
    public static final int LEVEL = 32, SEA = 19, DEEP = 5, HEAD = 3;
    public static int waterfalls;
    /** Ширина русла (в долях шума: ~10 блоков). */
    static final double WIDTH = 0.11;
    /** Без ладьи: урон в секунду, тяга вниз за тик; обычная лодка разъедается за BOAT_ROT тиков. */
    public static final float BITE = 3;
    public static final double PULL = 0.04;
    public static final int BOAT_ROT = 60;
    public static final net.minecraft.util.DamageSource LETHE = new net.minecraft.util.DamageSource("dre.lethe").setDamageBypassesArmor().setMagicDamage();
    static final String LAST = "dreLethe", FORGOT = "dreForgot";
    /** Для автотеста и замеров: столбцов реки с запуска. */
    public static int letheColumns, crypts, skulls, docks;

    /** Игрок в Лете (вызывается каждый тик, пока касается реки). В ладье Харона Лета его не трогает. */
    public static void inLethe(EntityPlayer p) {
        if (p.ridingEntity instanceof com.divinerpgextended.entity.EntityGondola) return;
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
        if (p.capabilities.isCreativeMode) return;
        // души утопленников: тянут на дно, грызут, опыт утекает
        p.motionY -= PULL;
        p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.moveSlowdown.id, 30, 1));
        if (now % 20 == 0) {
            drainXp(p, DRAIN);
            p.attackEntityFrom(LETHE, BITE);
            ((WorldServer) p.worldObj).func_147487_a("reddust", p.posX, p.posY + 0.3, p.posZ, 12, 0.5, 0.2, 0.5, 0);
        }
    }

    /** Обычная лодка в Лете разъедается за BOAT_ROT тиков (ладья Харона - нет). */
    public static void boatInLethe(net.minecraft.entity.item.EntityBoat b) {
        if (b instanceof com.divinerpgextended.entity.EntityGondola || b.isDead) return;
        NBTTagCompound d = b.getEntityData();
        long now = b.worldObj.getTotalWorldTime();
        if (d.getLong(LAST) == now) return;
        d.setLong(LAST, now);
        int rot = d.getInteger("dreRot") + 1;
        d.setInteger("dreRot", rot);
        if (rot % 10 == 0) ((WorldServer) b.worldObj).func_147487_a("smoke", b.posX, b.posY + 0.4, b.posZ, 8, 0.4, 0.1, 0.4, 0.01);
        if (rot < BOAT_ROT) return;
        b.worldObj.playSoundAtEntity(b, "random.fizz", 1.0F, 0.6F);
        if (b.riddenByEntity != null) b.riddenByEntity.mountEntity(null);
        b.setDead();
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

    /** Шум русла: река там, где он близок к нулю (извилистая лента, как ванильные реки). */
    static double riverNoise(long seed, int x, int z) {
        return noise(seed, x, z, 96) + 0.35 * noise(seed ^ 0x55, x, z, 29);
    }

    /** Русло Леты (без учёта высоты земли). */
    public static boolean river(long seed, int x, int z) {
        return Math.abs(riverNoise(seed, x, z)) < WIDTH;
    }

    /**
     * Лета в области заселения [x0, x0 + 16), как лава в Аду: гладь на высоте {@link #SEA} по всему Мортуму, любая пустота
     * не выше глади - Лета (вода всегда лежит в чаше рельефа и никогда не висит). Русло шумом прорезает дно мира: дно
     * глубже к середине, над водой вырезано {@link #HEAD} блока - под холмами это тоннель, река уходит под землю и
     * выходит дальше. Изредка со скалы над руслом падает водопад. На аренах реки нет.
     */
    static int lethe(World w, TwilightWorld tw, int x0, int z0) {
        int n = 0;
        long seed = w.getSeed();
        int[] fall = null;
        for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
            if (onArena(w, x, z)) continue;
            double a = Math.abs(riverNoise(seed, x, z));
            if (a < WIDTH) { // русло
                int bed = SEA - 1 - (int) Math.round((1 - a / WIDTH) * (DEEP - 1));
                if (!w.getBlock(x, bed, z).isOpaqueCube()) w.setBlock(x, bed, z, tw.dirt(), 0, 2);
                for (int y = bed + 1; y <= SEA; y++) w.setBlock(x, y, z, ModBlocks.lethe, 0, 2);
                // свод тоннеля: выше к середине русла, неровный (плавный шум), а не плоский потолок
                int head = HEAD + (int) Math.round((1 - a / WIDTH) * 3 + MortumArenas.Spire.smooth(x, z, 4, 31) * 2.5 - 0.6);
                for (int y = SEA + 1; y <= SEA + head; y++)
                    if (!w.isAirBlock(x, y, z) && w.getBlock(x, y, z) != net.minecraft.init.Blocks.bedrock) w.setBlock(x, y, z, net.minecraft.init.Blocks.air, 0, 2);
                if (soft(w, x, SEA + head + 1, z)) w.setBlock(x, SEA + head + 1, z, net.minecraft.init.Blocks.air, 0, 2); // куст без опоры
                n++;
                if (fall == null && a > WIDTH * 0.6) fall = new int[] { x, z };
            }
            // озёра: пустота и растения не выше глади - Лета, растения на самой глади убираются
            for (int y = 6; y <= SEA; y++) if (w.isAirBlock(x, y, z) || soft(w, x, y, z)) w.setBlock(x, y, z, ModBlocks.lethe, 0, 2);
            if (w.getBlock(x, SEA, z) == ModBlocks.lethe && soft(w, x, SEA + 1, z)) w.setBlock(x, SEA + 1, z, net.minecraft.init.Blocks.air, 0, 2);
        }
        if (fall != null && new Random(seed ^ x0 * 31L ^ z0 * 17L).nextInt(3) == 0) waterfall(w, fall[0], fall[1]);
        letheColumns += n;
        return n;
    }

    /**
     * Водопад: у края русла, где рядом поднимается скала (стена до SEA + 10 и выше, над руслом пусто), по стене стекает
     * столб Леты от верха скалы до глади, наверху скалы - выемка с Летой.
     */
    static boolean waterfall(World w, int x, int z) {
        int[][] nb = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] o : nb) {
            int wx = x + o[0], wz = z + o[1];
            if (river(w.getSeed(), wx, wz)) continue;
            int top = SEA + 1;
            while (top < 110 && w.getBlock(wx, top, wz).isOpaqueCube()) top++;
            if (top - SEA < 10) continue;
            boolean open = true;
            for (int y = SEA + 1; y < top && open; y++) if (!w.isAirBlock(x, y, z) && w.getBlock(x, y, z) != ModBlocks.lethe) open = false;
            if (!open) continue;
            for (int y = SEA + 1; y < top; y++) w.setBlock(x, y, z, ModBlocks.lethe, 0, 2);
            w.setBlock(wx, top - 1, wz, ModBlocks.lethe, 0, 2);
            waterfalls++;
            return true;
        }
        return false;
    }

    /** Трава, кусты, цветы, лианы, листва: то, что вода смывает (не Лета и не блоки с содержимым вроде черепов). */
    public static boolean soft(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        if (b.isAir(w, x, y, z) || b == ModBlocks.lethe || b.hasTileEntity(w.getBlockMetadata(x, y, z)) || b.getMaterial().isLiquid()) return false;
        net.minecraft.block.material.Material m = b.getMaterial();
        return m == net.minecraft.block.material.Material.plants || m == net.minecraft.block.material.Material.vine
            || m == net.minecraft.block.material.Material.leaves || m.isReplaceable();
    }

    /** Точка на арене Мортума (с запасом): там нет ни реки, ни мелких построек. */
    static boolean onArena(World w, int x, int z) {
        int[] c = WorldGenNests.nearest(w.getSeed(), w.provider.dimensionId, x, z);
        if (c == null) return false;
        double dx = x - c[0], dz = z - c[2];
        return dx * dx + dz * dz < (WorldGenNests.MORTUM_R + 2) * (WorldGenNests.MORTUM_R + 2);
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

    /** Останки гигантов (изредка), черепа и одинокие надгробия на дне пещеры в области заселения. */
    static void bones(World w, Random r, TwilightWorld tw, int x0, int z0) {
        int roll = r.nextInt(14);
        if (roll == 0) giantSkull(w, r, tw, x0 + 5 + r.nextInt(6), z0 + 5 + r.nextInt(6));
        else if (roll == 1) giantRibs(w, r, tw, x0 + 6 + r.nextInt(4), z0 + 6 + r.nextInt(4));
        int n = r.nextInt(3) == 0 ? 1 : 0;
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

    /** Харон - один на квадрат DOCK_CELL x DOCK_CELL чанков: пристань только в выбранном (из сида) чанке квадрата. */
    public static final int DOCK_CELL = 7;

    public static boolean dockChunk(long seed, int cx, int cz) {
        int qx = Math.floorDiv(cx, DOCK_CELL), qz = Math.floorDiv(cz, DOCK_CELL);
        Random q = new Random(seed ^ qx * 341873128712L ^ qz * 132897987541L ^ 0xC4A0L);
        return Math.floorMod(cx, DOCK_CELL) == q.nextInt(DOCK_CELL) && Math.floorMod(cz, DOCK_CELL) == q.nextInt(DOCK_CELL);
    }

    /** Пристань Харона на берегу Леты (в области заселения): помост 3 x 3, фонарь и сам Харон; false - берега нет. */
    static boolean dock(World w, Random r, TwilightWorld tw, int x0, int z0) {
        long seed = w.getSeed();
        if (!dockChunk(seed, x0 >> 4, z0 >> 4)) return false;
        for (int t = 0; t < 24; t++) {
            int x = x0 + 1 + r.nextInt(14), z = z0 + 1 + r.nextInt(14);
            if (river(seed, x, z) || !(river(seed, x + 2, z) || river(seed, x - 2, z) || river(seed, x, z + 2) || river(seed, x, z - 2))) continue;
            int y = SEA + 6; // берег у воды: верх земли чуть выше глади
            while (y > SEA && w.isAirBlock(x, y, z)) y--;
            if (y <= SEA || !w.isAirBlock(x, y + 1, z) || !w.isAirBlock(x, y + 3, z)) continue;
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

    // ---------------------------------------------------------------- останки гигантов

    public static int giantSkulls, giantRibs;

    private static void bone(World w, int x, int y, int z, int meta) {
        if (y > 1 && y < 250) w.setBlock(x, y, z, Blocks.quartz_block, meta, 2);
    }

    /** Ровно ли под останками: разброс поверхности в квадрате 2r+1 не больше 4. */
    private static int flatGround(World w, TwilightWorld tw, int cx, int cz, int r) {
        int lo = 999, hi = -1;
        for (int i = -r; i <= r; i += 2) for (int k = -r; k <= r; k += 2) {
            int y = WorldGenTwilightFeatures.surface(w, tw, cx + i, cz + k);
            if (y < 0) return -1;
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        return hi - lo <= 4 ? WorldGenTwilightFeatures.surface(w, tw, cx, cz) : -1;
    }

    /**
     * Череп гиганта: кварцевая скорлупа-эллипсоид (5 x 4 x 5) лицом в случайную сторону, наклонён носом в землю и наполовину
     * в ней. Глазницы и нос - дыры (в глазницу можно пролезть), снизу верхняя челюсть с зубами. Внутри полость, на дне -
     * сундук с добычей Мортума.
     */
    public static boolean giantSkull(World w, Random r, TwilightWorld tw, int cx, int cz) {
        int g = flatGround(w, tw, cx, cz, 6);
        if (g < 6 || g > 110) return false;
        double yaw = r.nextInt(4) * Math.PI / 2 + (r.nextDouble() - 0.5) * 0.6, tilt = Math.toRadians(18 + r.nextInt(18));
        double cy = g + 1.5, a = 5, b = 4, c = 5;
        double cyw = Math.cos(yaw), syw = Math.sin(yaw), ct = Math.cos(tilt), st = Math.sin(tilt);
        for (int x = -7; x <= 7; x++) for (int y = -7; y <= 7; y++) for (int z = -7; z <= 7; z++) {
            // мир -> лицо черепа: поворот обратно по рысканью, потом по наклону (нос вниз)
            double lx = x * cyw + z * syw, lz0 = -x * syw + z * cyw;
            double ly = y * ct - lz0 * st, lz = y * st + lz0 * ct;
            int wx = cx + x, wy = (int) Math.round(cy + y), wz = cz + z;
            double q = (lx / a) * (lx / a) + (ly / b) * (ly / b) + (lz / c) * (lz / c);
            boolean eye = Math.min(dist(lx, ly, lz, 2, 0.8, 4.2), dist(lx, ly, lz, -2, 0.8, 4.2)) < 1.6;
            boolean nose = dist(lx, ly, lz, 0, -1.3, 4.6) < 1.0;
            boolean jaw = ly >= -4.2 && ly <= -2.4 && (lx / 3.2) * (lx / 3.2) + ((lz - 2.6) / 3.0) * ((lz - 2.6) / 3.0) <= 1;
            boolean tooth = ly < -4.2 && ly >= -5.4 && Math.abs((lx / 3.2) * (lx / 3.2) + ((lz - 2.6) / 3.0) * ((lz - 2.6) / 3.0) - 0.8) < 0.25
                && ((int) Math.floor(lx + 10) % 2 == 0);
            if (q <= 1 && q >= 0.5 && !eye && !nose) bone(w, wx, wy, wz, (Math.abs(lx) < 0.5 && ly > 1) ? 1 : 0); // шов по темени - резной
            else if (q < 0.5 || (q <= 1 && (eye || nose))) {
                if (wy > g) w.setBlock(wx, wy, wz, Blocks.air, 0, 2); // полость и дыры - выше земли
            } else if (jaw || tooth) bone(w, wx, wy, wz, 0);
        }
        // сундук на дне полости
        int sy = g + 1;
        while (sy < g + 4 && !w.isAirBlock(cx, sy, cz)) sy++;
        if (w.isAirBlock(cx, sy, cz)) WorldGenTwilightFeatures.chest(w, r, cx, sy, cz, TwilightLoot.dungeon(tw), 4 + r.nextInt(3));
        giantSkulls++;
        return true;
    }

    private static double dist(double x, double y, double z, double px, double py, double pz) {
        return Math.sqrt((x - px) * (x - px) + (y - py) * (y - py) + (z - pz) * (z - pz));
    }

    /**
     * Грудная клетка гиганта: хребет из позвонков лежит на земле (наполовину в ней), над ним дугами пять пар рёбер; одно-два
     * обломаны, вся клетка чуть завалена набок.
     */
    public static boolean giantRibs(World w, Random r, TwilightWorld tw, int cx, int cz) {
        int g = flatGround(w, tw, cx, cz, 7);
        if (g < 6 || g > 110) return false;
        boolean alongX = r.nextBoolean();
        double roll = (r.nextDouble() - 0.5) * 0.5;
        int broken = r.nextInt(5), broken2 = r.nextInt(3) == 0 ? r.nextInt(5) : -1;
        // позвонки: столб лёжа вдоль хребта, через один - отросток вверх
        for (int s = -8; s <= 8; s++) {
            int x = cx + (alongX ? s : 0), z = cz + (alongX ? 0 : s);
            bone(w, x, g, z, alongX ? 3 : 4);
            if (Math.floorMod(s, 2) == 0 && Math.abs(s) < 8) bone(w, x, g + 1, z, 0);
        }
        int pair = 0;
        for (int s = -6; s <= 6; s += 3, pair++) {
            for (double t = 0.1 * Math.PI; t <= 0.92 * Math.PI; t += 0.035) {
                if ((pair == broken || pair == broken2) && t > 0.55 * Math.PI) break; // обломано
                double side = 6.5 * Math.cos(t), up = 6.2 * Math.sin(t);
                // завал набок: поворот дуги вокруг хребта
                double sd = side * Math.cos(roll) - up * Math.sin(roll), u = side * Math.sin(roll) + up * Math.cos(roll);
                int x = cx + (alongX ? s : (int) Math.round(sd)), z = cz + (alongX ? (int) Math.round(sd) : s), y = g + (int) Math.round(u);
                if (y >= g) bone(w, x, y, z, 0);
            }
        }
        giantRibs++;
        return true;
    }
}
