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
 * A feature of Mortum - the world of the dead:
 * <ul>
 * <li>Lethe, the river of oblivion: crimson, along the bottom of the world, like lava in the Nether - a surface at one height, the riverbed cuts the bottom, under
 * hills it goes into a tunnel, waterfalls fall from cliffs. Swimming in it without Charon's Barge - the souls of the drowned pull you
 * to the bottom and gnaw ({@link #BITE} per second); a normal boat in Lethe corrodes in a couple of seconds. On entering, all effects are dropped (both poisons and buffs), mobs lose sight of you for {@link #FORGET}
 * ticks; while you stand in the river - experience drains ({@link #DRAIN} points per second) and arcana ({@link #ARCANA_BITE} per second);
 * the souls bite ({@link #BITE}) only when there is no arcana left. The crossing is at Charon.</li>
 * <li>Crypts with sarcophagi, remains of giants (a skull half in the ground - a chest inside, a ribcage), rare skulls and
 * lone gravestones ({@link WorldGenTwilightFeatures}).</li>
 * </ul>
 */
public class MortumWorld {

    public static final int FORGET = 100, DRAIN = 3;
    /** Floor height of Mortum arenas (LEVEL + 4); the Lethe surface SEA (bottom of the world - 20..23), riverbed depth up to DEEP, above the water HEAD. */
    public static final int LEVEL = 32, SEA = 19, DEEP = 5, HEAD = 3;
    public static int waterfalls;
    /** Riverbed width (in noise units: ~10 blocks). */
    static final double WIDTH = 0.11;
    /** Without the barge: damage per second, downward pull per tick; a normal boat corrodes in BOAT_ROT ticks. */
    public static final float BITE = 3, ARCANA_BITE = 25;
    public static final double PULL = 0.04;
    public static final int BOAT_ROT = 60;
    public static final net.minecraft.util.DamageSource LETHE = new net.minecraft.util.DamageSource("dre.lethe").setDamageBypassesArmor().setMagicDamage();
    static final String LAST = "dreLethe", FORGOT = "dreForgot";
    /** For selftest and measurements: river columns since launch. */
    public static int letheColumns, crypts, skulls, docks;

    /** A player in Lethe (called every tick while touching the river). In Charon's Barge Lethe does not touch them. */
    public static void inLethe(EntityPlayer p) {
        if (p.ridingEntity instanceof com.divinerpgextended.entity.EntityGondola) return;
        NBTTagCompound d = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime(), last = d.getLong(LAST);
        if (now == last) return; // already this tick (several river blocks)
        if (now - last > 20) { // entered
            p.clearActivePotions();
            d.setLong(FORGOT, now + FORGET);
            forget(p);
            p.worldObj.playSoundAtEntity(p, "ambient.cave.cave", 1.0F, 0.5F);
            ((WorldServer) p.worldObj).func_147487_a("witchMagic", p.posX, p.posY + 1, p.posZ, 25, 0.5, 0.8, 0.5, 0.05);
        }
        d.setLong(LAST, now);
        d.setLong(FORGOT, Math.max(d.getLong(FORGOT), now + 40)); // while in the river - they don't remember
        if (p.capabilities.isCreativeMode) return;
        // souls of the drowned: pull to the bottom, gnaw, experience drains
        p.motionY -= PULL;
        p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.moveSlowdown.id, 30, 1));
        if (now % 20 == 0) {
            drainXp(p, DRAIN);
            if (!eatArcana(p, ARCANA_BITE)) p.attackEntityFrom(LETHE, BITE);
            ((WorldServer) p.worldObj).func_147487_a("reddust", p.posX, p.posY + 0.3, p.posZ, 12, 0.5, 0.2, 0.5, 0);
        }
    }

    /** Lethe eats arcana and does not let it regenerate while the player is in the river. false - there is no arcana left (then it bites). */
    public static boolean eatArcana(EntityPlayer p, float amount) {
        net.divinerpg.utils.events.ArcanaHelper h = net.divinerpg.utils.events.ArcanaHelper.getProperties(p);
        if (h == null || h.getBarValue() <= 0) return false;
        h.setBarValue(Math.max(0, h.getBarValue() - amount));
        h.regenDelay = Math.max(h.regenDelay, 40);
        return true;
    }

    /**
     * A mob in Lethe (in any world - including poured from a bucket): once per second the same damage as to a player. Does not touch the undead and residents
     * of Mortum (those who spawn in the Mortum biome themselves, Mortum bosses, Charon).
     */
    public static void mobInLethe(net.minecraft.entity.EntityLivingBase e, int x, int z) {
        long now = e.worldObj.getTotalWorldTime();
        if (now % 20 != 0 || e.isDead) return; // bites once per second - in other ticks we count nothing
        NBTTagCompound d = e.getEntityData();
        if (d.getLong(LAST) == now) return; // touching several Lethe blocks per tick - one bite
        d.setLong(LAST, now);
        if (e.getCreatureAttribute() == net.minecraft.entity.EnumCreatureAttribute.UNDEAD || native_(e, x, z)) return;
        e.attackEntityFrom(LETHE, BITE);
    }

    /**
     * A resident of Mortum: in the spawn lists of the Mortum biome, a Mortum boss or Charon. The lists are only of the Mortum biome: in the normal
     * world a zombie or pig is in the local biome's lists, but Lethe gnaws them.
     */
    static boolean native_(net.minecraft.entity.EntityLivingBase e, int x, int z) {
        if (e instanceof com.divinerpgextended.entity.EntityCharon) return true;
        boolean mortum = e.worldObj.provider.dimensionId == net.divinerpg.utils.config.ConfigurationHelper.mortum;
        if (e instanceof net.minecraft.entity.boss.IBossDisplayData && mortum) return true;
        for (Class<?> c : TwilightWorld.MORTUM.mobs()) if (c == e.getClass()) return true;
        if (!mortum) return false;
        net.minecraft.world.biome.BiomeGenBase b = e.worldObj.getBiomeGenForCoords(x, z);
        for (net.minecraft.entity.EnumCreatureType t : net.minecraft.entity.EnumCreatureType.values())
            for (Object o : b.getSpawnableList(t)) if (((net.minecraft.world.biome.BiomeGenBase.SpawnListEntry) o).entityClass == e.getClass()) return true;
        for (Class<?> c : TwilightWorld.MORTUM.mobs()) if (c == e.getClass()) return true;
        return false;
    }

    /** A normal boat in Lethe corrodes in BOAT_ROT ticks (Charon's Barge does not). */
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

    /** Mobs around lose the player. */
    static void forget(EntityPlayer p) {
        for (Object o : p.worldObj.getEntitiesWithinAABB(EntityLiving.class, p.boundingBox.expand(32, 16, 32))) {
            EntityLiving m = (EntityLiving) o;
            if (m.getAttackTarget() == p) m.setAttackTarget(null);
            if (m instanceof EntityCreature && ((EntityCreature) m).getEntityToAttack() == p) ((EntityCreature) m).setTarget(null);
        }
    }

    /** Take away experience (in points, carrying across levels). */
    public static void drainXp(EntityPlayer p, int n) {
        int total = Math.max(0, p.experienceTotal - n);
        p.experience = 0;
        p.experienceLevel = 0;
        p.experienceTotal = 0;
        p.addExperience(total);
    }

    /** The forgotten is not chosen as a target. */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (!(e.target instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || !forgotten((EntityPlayer) e.target)) return;
        if (e.entityLiving instanceof EntityLiving) ((EntityLiving) e.entityLiving).setAttackTarget(null);
        if (e.entityLiving instanceof EntityCreature) ((EntityCreature) e.entityLiving).setTarget(null);
    }

    /** The old AI (EntityMob) picks a target without an event - we clean up every half second. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !e.player.worldObj.isRemote && e.player.ticksExisted % 10 == 0 && forgotten(e.player)) forget(e.player);
    }

    // ---------------------------------------------------------------- generation

    /** Smooth noise [-1, 1] over the cell grid (for the riverbed). */
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

    /** Riverbed noise: the river is where it is close to zero (a winding ribbon, like vanilla rivers). */
    static double riverNoise(long seed, int x, int z) {
        return noise(seed, x, z, 96) + 0.35 * noise(seed ^ 0x55, x, z, 29);
    }

    /** The Lethe riverbed (not accounting for ground height). */
    public static boolean river(long seed, int x, int z) {
        return Math.abs(riverNoise(seed, x, z)) < WIDTH;
    }

    /**
     * Lethe in the population area [x0, x0 + 16), like lava in the Nether: a surface at height {@link #SEA} across all of Mortum, any void
     * not above the surface is Lethe (water always lies in a terrain bowl and never hangs). The riverbed cuts the bottom of the world with noise: the bottom is
     * deeper toward the middle, {@link #HEAD} blocks are carved above the water - under hills this is a tunnel, the river goes underground and
     * comes out farther on. Occasionally a waterfall falls from a cliff above the riverbed. There is no river on arenas.
     */
    static int lethe(World w, TwilightWorld tw, int x0, int z0) {
        int n = 0;
        long seed = w.getSeed();
        int[] fall = null;
        for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
            if (onArena(w, x, z)) { // there is no river under the arena, but voids at the bottom can't be left either: Lethe is nearby - pockets on the side
                for (int y = 6; y <= SEA; y++) if (w.isAirBlock(x, y, z) || soft(w, x, y, z)) w.setBlock(x, y, z, tw.dirt(), 0, 2);
                continue;
            }
            double a = Math.abs(riverNoise(seed, x, z));
            if (a < WIDTH) { // riverbed
                int bed = SEA - 1 - (int) Math.round((1 - a / WIDTH) * (DEEP - 1));
                if (!w.getBlock(x, bed, z).isOpaqueCube()) w.setBlock(x, bed, z, tw.dirt(), 0, 2);
                for (int y = bed + 1; y <= SEA; y++) w.setBlock(x, y, z, ModBlocks.lethe, 0, 2);
                // tunnel vault: higher toward the middle of the riverbed, uneven (smooth noise), not a flat ceiling
                int head = HEAD + (int) Math.round((1 - a / WIDTH) * 3 + MortumArenas.Spire.smooth(x, z, 4, 31) * 2.5 - 0.6);
                for (int y = SEA + 1; y <= SEA + head; y++)
                    if (!w.isAirBlock(x, y, z) && w.getBlock(x, y, z) != net.minecraft.init.Blocks.bedrock) w.setBlock(x, y, z, net.minecraft.init.Blocks.air, 0, 2);
                if (soft(w, x, SEA + head + 1, z)) w.setBlock(x, SEA + head + 1, z, net.minecraft.init.Blocks.air, 0, 2); // a bush with no support
                n++;
                if (fall == null && a > WIDTH * 0.6) fall = new int[] { x, z };
            }
            // lakes: voids and plants not above the surface are Lethe, plants on the surface itself are removed
            for (int y = 6; y <= SEA; y++) if (w.isAirBlock(x, y, z) || soft(w, x, y, z)) w.setBlock(x, y, z, ModBlocks.lethe, 0, 2);
            if (w.getBlock(x, SEA, z) == ModBlocks.lethe && soft(w, x, SEA + 1, z)) w.setBlock(x, SEA + 1, z, net.minecraft.init.Blocks.air, 0, 2);
        }
        if (fall != null && new Random(seed ^ x0 * 31L ^ z0 * 17L).nextInt(3) == 0) waterfall(w, fall[0], fall[1]);
        letheColumns += n;
        return n;
    }

    /**
     * Waterfall: at the edge of the riverbed where a cliff rises nearby (a wall up to SEA + 10 and higher, empty above the riverbed), a
     * column of Lethe flows down the wall from the top of the cliff to the surface, at the top of the cliff there is a hollow with Lethe.
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

    /** Grass, bushes, flowers, vines, leaves: what water washes away (not Lethe and not blocks with contents like skulls). */
    public static boolean soft(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        if (b.isAir(w, x, y, z) || b == ModBlocks.lethe || b.hasTileEntity(w.getBlockMetadata(x, y, z)) || b.getMaterial().isLiquid()) return false;
        net.minecraft.block.material.Material m = b.getMaterial();
        return m == net.minecraft.block.material.Material.plants || m == net.minecraft.block.material.Material.vine
            || m == net.minecraft.block.material.Material.leaves || m.isReplaceable();
    }

    /**
     * Whether a structure in the square [x, x + w) x [z, z + d) would stand in Lethe: the riverbed (by its noise - the river of a
     * neighboring area may not be poured yet) within margin m of it, or the floor not above the water level (lakes fill every void up
     * to SEA). Crypts, gravestones and giant remains stand only on dry ground (1.1.9).
     */
    public static boolean wet(long seed, int x, int z, int w, int d, int m, int floor) {
        if (floor <= SEA + 1) return true;
        for (int i = -m; i < w + m; i++) for (int k = -m; k < d + m; k++) if (river(seed, x + i, z + k)) return true;
        return false;
    }

    /** A point on a Mortum arena (with margin): there is no river or small structures there. */
    static boolean onArena(World w, int x, int z) {
        int[] c = WorldGenNests.nearest(w.getSeed(), w.provider.dimensionId, x, z);
        if (c == null) return false;
        double dx = x - c[0], dz = z - c[2];
        int e = MortumArenas.extent(MortumArenas.typeAt(w, c));
        return dx * dx + dz * dz < e * e;
    }

    // ---------------------------------------------------------------- arena pier and bridges

    public static int arenaDocks, bridgeColumns;

    /** The river point by the arena of this square {x, z} (null - the arena is not by the river). */
    static int[] arenaRiver(long seed, int dim, int[] c) {
        int g = WorldGenNests.spacing(dim);
        int rx = Math.floorDiv(c[0], g), rz = Math.floorDiv(c[2], g);
        return WorldGenNests.riverPoint(seed, dim, rx, rz);
    }

    /**
     * Arena pier: from the edge of the arena to the nearest riverbed - on the shore by the water a plank deck, a post with a lantern and Charon. Built in
     * the population area that the water's edge falls into.
     */
    static boolean arenaDock(World w, TwilightWorld tw, int x0, int z0) {
        long seed = w.getSeed();
        int[] c = WorldGenNests.nearest(seed, w.provider.dimensionId, x0 + 8, z0 + 8);
        if (c == null) return false;
        int[] rp = arenaRiver(seed, w.provider.dimensionId, c);
        if (rp == null) return false;
        // from the river point to the arena up to the shore: the last river point is the water's edge
        double dx = c[0] - rp[0], dz = c[2] - rp[1], l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1) return false;
        dx /= l;
        dz /= l;
        int ex = rp[0], ez = rp[1];
        for (int i = 0; i < 40; i++) {
            int nx = (int) Math.floor(rp[0] + dx * i), nz = (int) Math.floor(rp[1] + dz * i);
            if (!river(seed, nx, nz)) break;
            ex = nx;
            ez = nz;
        }
        if (ex < x0 || ex >= x0 + 16 || ez < z0 || ez >= z0 + 16) return false;
        // deck 3 x 6: two rows over the water (at the surface), the rest along the shore toward the arena (on the ground)
        for (int i = -2; i <= 3; i++) for (int k = -1; k <= 1; k++) {
            int x = (int) Math.floor(ex + 0.5 + dx * i - dz * k), z = (int) Math.floor(ez + 0.5 + dz * i + dx * k);
            int top = SEA + 1;
            if (i > 0) {
                top = ground(w, x, z);
                if (top > SEA + 4) continue; // steep shore - the deck ends
            }
            w.setBlock(x, top, z, tw.planks(), 0, 2);
            for (int a = 1; a <= 3; a++) if (!w.isAirBlock(x, top + a, z) && w.getBlock(x, top + a, z) != Blocks.bedrock) w.setBlock(x, top + a, z, Blocks.air, 0, 2);
            if (i == -2 && Math.abs(k) == 1) { // pier posts from the water, lanterns on them
                for (int y = SEA - 3; y < top; y++) w.setBlock(x, y, z, tw.log(), 0, 2);
                w.setBlock(x, top + 1, z, Blocks.fence, 0, 2);
                w.setBlock(x, top + 2, z, tw.lamp(), tw.lampMeta(), 2);
            }
        }
        com.divinerpgextended.entity.EntityCharon ch = new com.divinerpgextended.entity.EntityCharon(w);
        ch.setLocationAndAngles(ex + 0.5 + dx, SEA + 2, ez + 0.5 + dz, 0, 0);
        w.spawnEntityInWorld(ch);
        arenaDocks++;
        return true;
    }

    /** Bridges: a BRIDGE_CELL square, at most one in each. Deck height above the surface - BRIDGE_UP. */
    public static final int BRIDGE_CELL = 112, BRIDGE_UP = 5;
    private static final java.util.Map<Long, double[]> BRIDGES = new java.util.concurrent.ConcurrentHashMap<Long, double[]>();

    /**
     * The bridge of square (bx, bz): {x, z, across ux, uz, half-width of water} or empty - no bridge. The place is the very middle of the riverbed
     * in the middle part of the square, far from arenas; the direction is across the riverbed (by the noise gradient).
     */
    public static double[] bridge(long seed, int dim, int bx, int bz) {
        long key = seed ^ bx * 0x9E3779B1L ^ bz * 0x85EBCA6BL ^ 0xB71D6EL;
        double[] b = BRIDGES.get(key);
        if (b != null) return b;
        double best = WIDTH * 0.25;
        int px = 0, pz = 0;
        boolean found = false;
        for (int i = 24; i < BRIDGE_CELL - 24; i += 3) for (int k = 24; k < BRIDGE_CELL - 24; k += 3) {
            int x = bx * BRIDGE_CELL + i, z = bz * BRIDGE_CELL + k;
            double a = Math.abs(riverNoise(seed, x, z));
            if (a < best) {
                best = a;
                px = x;
                pz = z;
                found = true;
            }
        }
        b = new double[0];
        if (found) {
            int[] c = WorldGenNests.nearest(seed, dim, px, pz);
            boolean far = c == null;
            if (c != null) {
                int g = WorldGenNests.spacing(dim);
                int e = MortumArenas.extent(MortumArenas.type(seed, Math.floorDiv(c[0], g), Math.floorDiv(c[2], g)));
                double ddx = px - c[0], ddz = pz - c[2];
                far = ddx * ddx + ddz * ddz > (e + 30) * (e + 30);
            }
            double gx = riverNoise(seed, px + 2, pz) - riverNoise(seed, px - 2, pz), gz = riverNoise(seed, px, pz + 2) - riverNoise(seed, px, pz - 2);
            double gl = Math.sqrt(gx * gx + gz * gz);
            if (far && gl > 1e-6) {
                double ux = gx / gl, uz = gz / gl;
                int half = 0;
                for (int s = -1; s <= 1; s += 2) {
                    int h = 0;
                    while (h < 22 && river(seed, (int) Math.floor(px + 0.5 + ux * h * s), (int) Math.floor(pz + 0.5 + uz * h * s))) h++;
                    half = Math.max(half, h);
                }
                if (half >= 3 && half < 22) b = new double[] { px + 0.5, pz + 0.5, ux, uz, half };
            }
        }
        BRIDGES.put(key, b);
        return b;
    }

    private static final java.util.Map<Long, Boolean> OPEN = new java.util.concurrent.ConcurrentHashMap<Long, Boolean>();
    public static final int OPEN_SKY = 35;

    /**
     * Open above the middle of the bridge: there is no rock OPEN_SKY blocks upward from the deck (otherwise the river is in a tunnel there and the bridge would
     * turn into a hall in the rock). It is decided by one column once - all pieces of the bridge decide the same way.
     */
    public static boolean open(World w, double[] b) {
        long key = w.getSeed() ^ Double.doubleToLongBits(b[0]) * 31 ^ Double.doubleToLongBits(b[1]);
        Boolean o = OPEN.get(key);
        if (o != null) return o;
        int x = (int) Math.floor(b[0]), z = (int) Math.floor(b[1]);
        boolean ok = true;
        for (int y = SEA + BRIDGE_UP + 1; y <= SEA + BRIDGE_UP + OPEN_SKY && ok; y++) {
            net.minecraft.block.Block bl = w.getBlock(x, y, z);
            if (!bl.isAir(w, x, y, z) && !soft(w, x, y, z)) ok = false;
        }
        OPEN.put(key, ok);
        return ok;
    }

    /** Ground in the column by the riverbed: the first solid block from the top down from SEA + 12 (not Lethe, not grass). */
    static int ground(World w, int x, int z) {
        for (int y = SEA + 12; y > SEA - DEEP; y--) {
            net.minecraft.block.Block b = w.getBlock(x, y, z);
            if (!b.isAir(w, x, y, z) && b != ModBlocks.lethe && !soft(w, x, y, z)) return y;
        }
        return SEA - DEEP;
    }

    /**
     * Bridges over Lethe in the population area: a deck of nether brick 5 wide at BRIDGE_UP above the surface (a gondola passes
     * under the arch), railings with lanterns, gate posts at the ends, descents to the shore; where the bank is a hill, a
     * passage is cut above the deck.
     */
    static int bridges(World w, TwilightWorld tw, int x0, int z0) {
        long seed = w.getSeed();
        int n = 0;
        int bx0 = Math.floorDiv(x0 - 40, BRIDGE_CELL), bx1 = Math.floorDiv(x0 + 56, BRIDGE_CELL);
        int bz0 = Math.floorDiv(z0 - 40, BRIDGE_CELL), bz1 = Math.floorDiv(z0 + 56, BRIDGE_CELL);
        for (int bx = bx0; bx <= bx1; bx++) for (int bz = bz0; bz <= bz1; bz++) {
            double[] b = bridge(seed, w.provider.dimensionId, bx, bz);
            if (b.length == 0 || !open(w, b)) continue;
            double cx = b[0], cz = b[1], ux = b[2], uz = b[3], wh = b[4], half = wh + 5;
            for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
                double rx = x + 0.5 - cx, rz = z + 0.5 - cz;
                double u = rx * ux + rz * uz, v = -rx * uz + rz * ux, au = Math.abs(u), av = Math.abs(v);
                if (au > half || av > 3.5) continue;
                int deck = SEA + BRIDGE_UP;
                int g = ground(w, x, z);
                boolean water = river(seed, x, z) && au <= wh;
                if (au > wh + 1) deck = Math.min(deck, Math.max(SEA + BRIDGE_UP - (int) Math.round(au - wh - 1), g + 1));
                boolean gate = Math.abs(au - (wh + 1)) < 0.6 && av > 2.5; // gate posts
                if (av <= 2.5) { // deck and underside: over the water an arch, over the shore a support down to the ground
                    w.setBlock(x, deck, z, Blocks.nether_brick, 0, 2);
                    if (water) {
                        int under = SEA + 3 + (int) Math.round(1.2 * (1 - (au / wh) * (au / wh)));
                        for (int y = under; y < deck; y++) w.setBlock(x, y, z, Blocks.nether_brick, 0, 2);
                    } else for (int y = deck - 1; y > g; y--) w.setBlock(x, y, z, Blocks.nether_brick, 0, 2);
                    for (int y = deck + 1; y <= deck + 4; y++) if (!w.isAirBlock(x, y, z) && w.getBlock(x, y, z) != Blocks.bedrock) w.setBlock(x, y, z, Blocks.air, 0, 2);
                } else { // railings; the support under them is like the deck's
                    w.setBlock(x, deck, z, Blocks.nether_brick, 0, 2);
                    if (!water) for (int y = deck - 1; y > g; y--) w.setBlock(x, y, z, Blocks.nether_brick, 0, 2);
                    for (int y = deck + 1; y <= deck + 4; y++) if (!w.isAirBlock(x, y, z) && w.getBlock(x, y, z) != Blocks.bedrock) w.setBlock(x, y, z, Blocks.air, 0, 2);
                    boolean post = Math.floorMod((int) Math.round(u), 4) == 0;
                    if (gate) {
                        for (int y = deck + 1; y <= deck + 4; y++) w.setBlock(x, y, z, Blocks.nether_brick, 0, 2);
                        w.setBlock(x, deck + 5, z, tw.lamp(), tw.lampMeta(), 2);
                    } else {
                        w.setBlock(x, deck + 1, z, Blocks.nether_brick_fence, 0, 2);
                        if (post && au <= wh + 1) w.setBlock(x, deck + 2, z, tw.lamp(), tw.lampMeta(), 2);
                    }
                }
                n++;
            }
        }
        bridgeColumns += n;
        return n;
    }

    /** A skull on the floor (skeleton, random rotation). */
    static boolean skull(World w, Random r, int x, int y, int z) {
        if (!w.isAirBlock(x, y, z)) return false;
        w.setBlock(x, y, z, Blocks.skull, 1, 2);
        if (w.getTileEntity(x, y, z) instanceof TileEntitySkull) {
            TileEntitySkull s = (TileEntitySkull) w.getTileEntity(x, y, z);
            s.func_152107_a(r.nextInt(8) == 0 ? 1 : 0); // occasionally a Wither skull
            s.func_145903_a(r.nextInt(16));
        }
        skulls++;
        return true;
    }

    /** Remains of giants (occasionally), skulls and lone gravestones on the cave floor in the population area. */
    static void bones(World w, Random r, TwilightWorld tw, int x0, int z0) {
        int roll = r.nextInt(14);
        if (roll == 0) giantSkull(w, r, tw, x0 + 5 + r.nextInt(6), z0 + 5 + r.nextInt(6));
        else if (roll == 1) giantRibs(w, r, tw, x0 + 6 + r.nextInt(4), z0 + 6 + r.nextInt(4));
        int n = r.nextInt(3) == 0 ? 1 : 0;
        for (int i = 0; i < n; i++) {
            int x = x0 + r.nextInt(16), z = z0 + r.nextInt(16), y = WorldGenTwilightFeatures.surface(w, tw, x, z);
            if (y > 0 && w.getBlock(x, y, z) != ModBlocks.lethe && !wet(w.getSeed(), x, z, 1, 1, 1, y)) skull(w, r, x, y + 1, z);
        }
        if (r.nextInt(6) == 0) {
            int x = x0 + 2 + r.nextInt(12), z = z0 + 2 + r.nextInt(12), y = WorldGenTwilightFeatures.surface(w, tw, x, z);
            if (y > 0 && w.isAirBlock(x, y + 1, z) && w.isAirBlock(x, y + 2, z) && w.getBlock(x, y, z) != ModBlocks.lethe && !wet(w.getSeed(), x, z, 1, 2, 1, y)) {
                w.setBlock(x, y + 1, z, Blocks.stonebrick, 3, 2);
                w.setBlock(x, y + 2, z, Blocks.stone_slab, 5, 2);
                skull(w, r, x, y + 1, z + 1);
            }
        }
    }

    /**
     * Crypt 7 x 9 with corner at (x, z): walls of stone brick, a gabled roof of slabs, entrance from the south; inside a sarcophagus
     * (a chest with Mortum loot between slabs), skulls, a lamp and a Mortum mob spawner; false - the place is uneven.
     */
    public static boolean crypt(World w, Random r, TwilightWorld tw, int x, int z) {
        int lo = 999, hi = -1;
        for (int i = 0; i < 7; i++) for (int k = 0; k < 9; k++) {
            int y = WorldGenTwilightFeatures.surface(w, tw, x + i, z + k);
            if (y < 0) return false;
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        if (hi - lo > 3 || wet(w.getSeed(), x, z, 7, 9, 2, lo)) return false;
        int base = hi;
        for (int i = 0; i < 7; i++) for (int k = 0; k < 9; k++) {
            int s = WorldGenTwilightFeatures.surface(w, tw, x + i, z + k);
            for (int y = s + 1; y < base; y++) w.setBlock(x + i, y, z + k, net.divinerpg.utils.blocks.TwilightBlocks.twilightStone, 0, 2);
            w.setBlock(x + i, base, z + k, Blocks.stonebrick, r.nextInt(4) == 0 ? 2 : 0, 2); // floor, cracked in places
            for (int y = base + 1; y <= base + 6; y++) w.setBlock(x + i, y, z + k, Blocks.air, 0, 2);
            boolean wall = i == 0 || i == 6 || k == 0 || k == 8;
            if (wall) for (int y = base + 1; y <= base + 3; y++) w.setBlock(x + i, y, z + k, Blocks.stonebrick, (i + k + y) % 3 == 0 ? 1 : 0, 2); // moss in places
            // roof: slabs stepped up to the ridge
            int ry = base + 4 + (3 - Math.abs(i - 3)) / 2;
            w.setBlock(x + i, ry, z + k, i == 3 ? Blocks.stonebrick : Blocks.stone_slab, i == 3 ? 3 : 5, 2);
        }
        // entrance from the south
        w.setBlock(x + 3, base + 1, z + 8, Blocks.air, 0, 2);
        w.setBlock(x + 3, base + 2, z + 8, Blocks.air, 0, 2);
        // sarcophagus by the north wall: a chest between slabs
        w.setBlock(x + 2, base + 1, z + 2, Blocks.stone_slab, 5, 2);
        w.setBlock(x + 4, base + 1, z + 2, Blocks.stone_slab, 5, 2);
        WorldGenTwilightFeatures.chest(w, r, x + 3, base + 1, z + 2, TwilightLoot.dungeon(tw), 6 + r.nextInt(3));
        Reliquaries.inCrypt(w, x, base, z); // Mortum reliquary by the sarcophagus - with a chance and a guarantee per area
        skull(w, r, x + 3, base + 2, z + 2);
        skull(w, r, x + 1, base + 1, z + 6);
        skull(w, r, x + 5, base + 1, z + 4);
        w.setBlock(x + 3, base + 3, z + 5, tw.lamp(), tw.lampMeta(), 2);
        if (com.divinerpgextended.Config.dungeonSpawners) w.setBlock(x + 1, base + 1, z + 1, Blocks.mob_spawner, 0, 2);
        if (w.getTileEntity(x + 1, base + 1, z + 1) instanceof net.minecraft.tileentity.TileEntityMobSpawner) {
            Class<? extends EntityLiving>[] mobs = tw.mobs();
            String name = (String) net.minecraft.entity.EntityList.classToStringMapping.get(mobs[r.nextInt(mobs.length)]);
            if (name != null) ((net.minecraft.tileentity.TileEntityMobSpawner) w.getTileEntity(x + 1, base + 1, z + 1)).func_145881_a().setEntityName(name);
        }
        crypts++;
        return true;
    }

    /** Charon - one per DOCK_CELL x DOCK_CELL chunk square: the pier only in the chunk of the square chosen (from the seed). */
    public static final int DOCK_CELL = 7;

    public static boolean dockChunk(long seed, int cx, int cz) {
        int qx = Math.floorDiv(cx, DOCK_CELL), qz = Math.floorDiv(cz, DOCK_CELL);
        Random q = new Random(seed ^ qx * 341873128712L ^ qz * 132897987541L ^ 0xC4A0L);
        return Math.floorMod(cx, DOCK_CELL) == q.nextInt(DOCK_CELL) && Math.floorMod(cz, DOCK_CELL) == q.nextInt(DOCK_CELL);
    }

    /** Charon's pier on the shore of Lethe (in the population area): a 3 x 3 platform, a lantern and Charon himself; false - there is no shore. */
    static boolean dock(World w, Random r, TwilightWorld tw, int x0, int z0) {
        long seed = w.getSeed();
        if (!dockChunk(seed, x0 >> 4, z0 >> 4)) return false;
        for (int t = 0; t < 24; t++) {
            int x = x0 + 1 + r.nextInt(14), z = z0 + 1 + r.nextInt(14);
            if (river(seed, x, z) || !(river(seed, x + 2, z) || river(seed, x - 2, z) || river(seed, x, z + 2) || river(seed, x, z - 2))) continue;
            int y = SEA + 6; // shore by the water: top of the ground slightly above the surface
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

    // ---------------------------------------------------------------- remains of giants

    public static int giantSkulls, giantRibs;

    /** A bone block of the giant remains: milk stone (quartz before 1.1.5; carved seams and lying ribs no longer differ - meta is ignored). */
    private static void bone(World w, int x, int y, int z, int meta) {
        if (y > 1 && y < 250) w.setBlock(x, y, z, com.divinerpgextended.world.WhiteStone.block(), 0, 2);
    }

    /** Whether it is level under the remains: the surface spread in a square 2r+1 is at most 4. */
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
     * Giant skull: a quartz ellipsoid shell (5 x 4 x 5) facing a random direction, tilted with the nose into the ground and half
     * in it. The eye sockets and nose are holes (you can crawl into a socket), the upper jaw with teeth below. Inside is a cavity, at the bottom -
     * a chest with Mortum loot.
     */
    public static boolean giantSkull(World w, Random r, TwilightWorld tw, int cx, int cz) {
        int g = flatGround(w, tw, cx, cz, 6);
        if (g < 6 || g > 110 || wet(w.getSeed(), cx - 7, cz - 7, 15, 15, 2, g)) return false;
        double yaw = r.nextInt(4) * Math.PI / 2 + (r.nextDouble() - 0.5) * 0.6, tilt = Math.toRadians(18 + r.nextInt(18));
        double cy = g + 1.5, a = 5, b = 4, c = 5;
        double cyw = Math.cos(yaw), syw = Math.sin(yaw), ct = Math.cos(tilt), st = Math.sin(tilt);
        for (int x = -7; x <= 7; x++) for (int y = -7; y <= 7; y++) for (int z = -7; z <= 7; z++) {
            // world -> skull face: rotate back by yaw, then by tilt (nose down)
            double lx = x * cyw + z * syw, lz0 = -x * syw + z * cyw;
            double ly = y * ct - lz0 * st, lz = y * st + lz0 * ct;
            int wx = cx + x, wy = (int) Math.round(cy + y), wz = cz + z;
            double q = (lx / a) * (lx / a) + (ly / b) * (ly / b) + (lz / c) * (lz / c);
            boolean eye = Math.min(dist(lx, ly, lz, 2, 0.8, 4.2), dist(lx, ly, lz, -2, 0.8, 4.2)) < 1.6;
            boolean nose = dist(lx, ly, lz, 0, -1.3, 4.6) < 1.0;
            boolean jaw = ly >= -4.2 && ly <= -2.4 && (lx / 3.2) * (lx / 3.2) + ((lz - 2.6) / 3.0) * ((lz - 2.6) / 3.0) <= 1;
            boolean tooth = ly < -4.2 && ly >= -5.4 && Math.abs((lx / 3.2) * (lx / 3.2) + ((lz - 2.6) / 3.0) * ((lz - 2.6) / 3.0) - 0.8) < 0.25
                && ((int) Math.floor(lx + 10) % 2 == 0);
            if (q <= 1 && q >= 0.5 && !eye && !nose) bone(w, wx, wy, wz, (Math.abs(lx) < 0.5 && ly > 1) ? 1 : 0); // seam along the crown - carved
            else if (q < 0.5 || (q <= 1 && (eye || nose))) {
                if (wy > g) w.setBlock(wx, wy, wz, Blocks.air, 0, 2); // cavity and holes - above the ground
            } else if (jaw || tooth) bone(w, wx, wy, wz, 0);
        }
        // chest at the bottom of the cavity
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
     * Giant ribcage: a spine of vertebrae lies on the ground (half in it), five pairs of ribs arc over it; one or two
     * are broken off, the whole cage is slightly tilted to the side.
     */
    public static boolean giantRibs(World w, Random r, TwilightWorld tw, int cx, int cz) {
        int g = flatGround(w, tw, cx, cz, 7);
        if (g < 6 || g > 110 || wet(w.getSeed(), cx - 8, cz - 8, 17, 17, 2, g)) return false;
        boolean alongX = r.nextBoolean();
        double roll = (r.nextDouble() - 0.5) * 0.5;
        int broken = r.nextInt(5), broken2 = r.nextInt(3) == 0 ? r.nextInt(5) : -1;
        // vertebrae: a pillar lying along the spine, every other one with a process pointing up
        for (int s = -8; s <= 8; s++) {
            int x = cx + (alongX ? s : 0), z = cz + (alongX ? 0 : s);
            bone(w, x, g, z, alongX ? 3 : 4);
            if (Math.floorMod(s, 2) == 0 && Math.abs(s) < 8) bone(w, x, g + 1, z, 0);
        }
        int pair = 0;
        for (int s = -6; s <= 6; s += 3, pair++) {
            for (double t = 0.1 * Math.PI; t <= 0.92 * Math.PI; t += 0.035) {
                if ((pair == broken || pair == broken2) && t > 0.55 * Math.PI) break; // broken off
                double side = 6.5 * Math.cos(t), up = 6.2 * Math.sin(t);
                // tilt to the side: rotate the arc around the spine
                double sd = side * Math.cos(roll) - up * Math.sin(roll), u = side * Math.sin(roll) + up * Math.cos(roll);
                int x = cx + (alongX ? s : (int) Math.round(sd)), z = cz + (alongX ? (int) Math.round(sd) : s), y = g + (int) Math.round(u);
                if (y >= g) bone(w, x, y, z, 0);
            }
        }
        giantRibs++;
        return true;
    }
}
