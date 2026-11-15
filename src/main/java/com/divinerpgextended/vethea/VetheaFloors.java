package com.divinerpgextended.vethea;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.divinerpg.entities.vethea.EntityHiveQueen;
import net.divinerpg.entities.vethea.EntityKaros;
import net.divinerpg.entities.vethea.EntityLadyLuna;
import net.divinerpg.entities.vethea.EntityQuadro;
import net.divinerpg.entities.vethea.EntityRaglok;
import net.divinerpg.entities.vethea.EntityWreck;
import net.divinerpg.utils.blocks.VetheaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.accessory.Accessories;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.trinket.Trinket;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Vethea's vertical. Floors are dreamstone slabs 16 blocks thick every 48 (surfaces at y 16, 64, 112, 160).
 * <ul>
 * <li>Only rifts lead up: a shaft through the slab, one per 6x6 chunk square in each slab, with fire
 * crystals along the edge. Rifts are cut near players (in old worlds too), building in the shaft is impossible.</li>
 * <li>The lower part of the slabs cannot be broken (the top 4 blocks can: grass, dirt and building there).</li>
 * <li>Dream Wings (three tiers) - takeoff only in Vethea. From a rift onto floor N without wings of tier N-1 the stream throws you down.</li>
 * <li>Guardian: anyone who ends up above the allowed floor by any means (pearl, pillar, mods) is thrown down after 1.5 s.
 * Allowed: 1 + the highest wing tier the player has ever worn.</li>
 * <li>Normal flight in Vethea is disabled (except in creative mode).</li>
 * <li>Dream gifts: for floors and Vethea bosses - a reward in the normal world upon waking (new items, nothing is carried over).</li>
 * </ul>
 */
public class VetheaFloors {

    public static final int GAP = 48, REGION = 6;
    /** Rift shaft radius (square) and stream zone. */
    private static final int SHAFT_R2 = 5;
    private static final double FLOW_R2 = 6.25;
    private static final String TIER = "dre_wing_tier", GIFT_DONE = "dre_dream_done", GIFT_PENDING = "dre_dream_pending";

    private static class State {

        int over, lastMsg = -1000, lastRift = -1000;
    }

    private static final Map<UUID, State> STATE = new HashMap<UUID, State>();

    // ---------------- Geometry ----------------

    /** The slab under floor f (2..4): lower and upper y. */
    public static int slabBottom(int f) {
        return GAP * (f - 1) + 1;
    }

    public static int slabTop(int f) {
        return GAP * (f - 1) + 16;
    }

    /** Grass y of floor f. */
    public static int surface(int f) {
        return GAP * (f - 1) + 16;
    }

    public static int floorOf(double y) {
        return y >= 161 ? 4 : y >= 113 ? 3 : y >= 65 ? 2 : 1;
    }

    private static int floorDiv(int a, int b) {
        return a >= 0 ? a / b : -((-a + b - 1) / b);
    }

    /** Rift center in slab f for region (rx, rz): {x, z}. */
    public static int[] rift(World w, int rx, int rz, int f) {
        Random r = new Random(w.getSeed() ^ rx * 341873128712L ^ rz * 132897987541L ^ f * 0x5DEECE66DL);
        r.nextInt();
        int cx = rx * REGION + r.nextInt(REGION), cz = rz * REGION + r.nextInt(REGION);
        return new int[] { cx * 16 + 8, cz * 16 + 8 };
    }

    /** In which rift the point lies: slab number (2..4) or 0. below/above - height margin outside the slab. */
    public static int riftAt(World w, double x, double y, double z, double r2, int below, int above) {
        for (int f = 2; f <= 4; f++) {
            if (y < slabBottom(f) - below || y > slabTop(f) + above + 1) continue;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            int[] c = rift(w, floorDiv(bx >> 4, REGION), floorDiv(bz >> 4, REGION), f);
            double dx = x - (c[0] + 0.5), dz = z - (c[1] + 0.5);
            if (dx * dx + dz * dz <= r2) return f;
        }
        return 0;
    }

    private static boolean inShaft(World w, int x, int y, int z) {
        return riftAt(w, x + 0.5, y, z + 0.5, SHAFT_R2 + 0.5, 0, -1) != 0;
    }

    /** The unbreakable part of the slabs (except the top 4 blocks). */
    public static boolean protectedSlab(int y) {
        for (int f = 2; f <= 4; f++) if (y >= slabBottom(f) && y <= slabTop(f) - 4) return true;
        return false;
    }

    static boolean vethea(World w) {
        return w.provider.dimensionId == ConfigurationHelper.vethea;
    }

    // ---------------- Rifts: cut once ----------------

    public static class Rifts extends WorldSavedData {

        static final String NAME = "dre_rifts";
        final Set<String> carved = new HashSet<String>();

        public Rifts(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound t) {
            NBTTagList l = t.getTagList("c", 8);
            for (int i = 0; i < l.tagCount(); i++) carved.add(l.getStringTagAt(i));
        }

        @Override
        public void writeToNBT(NBTTagCompound t) {
            NBTTagList l = new NBTTagList();
            for (String s : carved) l.appendTag(new NBTTagString(s));
            t.setTag("c", l);
        }

        static Rifts get(World w) {
            Rifts r = (Rifts) w.perWorldStorage.loadData(Rifts.class, NAME);
            if (r == null) {
                r = new Rifts(NAME);
                w.perWorldStorage.setData(NAME, r);
            }
            return r;
        }
    }

    /** For measurements: time of cutting rifts and the checks around players. */
    public static long carveNanos, carves, nearNanos;

    public static void carveNearForTest(EntityPlayer p) {
        carveNear(p);
    }

    private static void carveNear(EntityPlayer p) {
        long t0 = System.nanoTime();
        try {
            carveNear0(p);
        } finally {
            nearNanos += System.nanoTime() - t0;
        }
    }

    private static void carveNear0(EntityPlayer p) {
        World w = p.worldObj;
        Rifts rifts = Rifts.get(w);
        int rx0 = floorDiv(((int) Math.floor(p.posX)) >> 4, REGION), rz0 = floorDiv(((int) Math.floor(p.posZ)) >> 4, REGION);
        for (int rx = rx0 - 1; rx <= rx0 + 1; rx++) {
            for (int rz = rz0 - 1; rz <= rz0 + 1; rz++) {
                for (int f = 2; f <= 4; f++) {
                    String key = rx + "," + rz + "," + f;
                    if (rifts.carved.contains(key)) continue;
                    int[] c = rift(w, rx, rz, f);
                    if (!w.getChunkProvider().chunkExists(c[0] >> 4, c[1] >> 4)) continue;
                    // only in an inhabited chunk: otherwise Divine's trees and buildings later overwrite the edge (and even the shaft)
                    if (!w.getChunkFromChunkCoords(c[0] >> 4, c[1] >> 4).isTerrainPopulated) continue;
                    long c0 = System.nanoTime();
                    carve(w, c[0], c[1], f);
                    carveNanos += System.nanoTime() - c0;
                    carves++;
                    com.divinerpgextended.debug.Debug.note(p, "Rift cut through: slab " + f + ", center " + c[0] + " " + c[1]);
                    rifts.carved.add(key);
                    rifts.markDirty();
                }
            }
        }
    }

    private static void carve(World w, int x, int z, int f) {
        int b = slabBottom(f), t = slabTop(f);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int d = dx * dx + dz * dz;
                if (d <= SHAFT_R2) {
                    for (int y = b; y <= t; y++) {
                        if (w.getTileEntity(x + dx, y, z + dz) == null) w.setBlock(x + dx, y, z + dz, Blocks.air, 0, 2);
                    }
                } else if (d <= 10) {
                    rim(w, x + dx, t, z + dz);
                    rim(w, x + dx, b, z + dz);
                }
            }
        }
    }

    private static void rim(World w, int x, int y, int z) {
        // any ordinary solid slab block (Vethea floors contain crystals and so on), but not air, a chest or bedrock
        Block bl = w.getBlock(x, y, z);
        if (bl.isAir(w, x, y, z) || w.getTileEntity(x, y, z) != null || bl.getBlockHardness(w, x, y, z) < 0) return;
        w.setBlock(x, y, z, VetheaBlocks.fireCrystal, 0, 2);
    }

    // ---------------- Wings and the allowed floor ----------------

    private static final Trinket[] WINGS = { Trinket.DREAM_WINGS_1, Trinket.DREAM_WINGS_2, Trinket.DREAM_WINGS_3 };

    /** Tier of the worn wings (0 - none). */
    public static int wingTier(EntityPlayer p) {
        for (int t = WINGS.length; t >= 1; t--) if (Accessories.wears(p, WINGS[t - 1])) return t;
        return 0;
    }

    /** Vethea is completed: Lady Luna is defeated (her gift is received). */
    public static boolean dreamDone(EntityPlayer p) {
        return (dreamMask(p) & 1 << Gift.LADY_LUNA.ordinal()) != 0;
    }

    /** The player's dream gifts (one bit per boss): on the server - from player data, on the client - sent by the server. */
    public static int dreamMask(EntityPlayer p) {
        if (p.worldObj != null && p.worldObj.isRemote) return com.divinerpgextended.boss.ProgressSync.dream(p);
        return persisted(p).getInteger(GIFT_DONE);
    }

    /** Vethea bosses (gifts for them). */
    private static final Gift[] BOSSES = { Gift.HIVE_QUEEN, Gift.QUADRO, Gift.KAROS, Gift.WRECK, Gift.RAGLOK, Gift.LADY_LUNA };

    /** The dream is conquered: all Vethea bosses are defeated - dream items work in waking life, and waking items work in the dream. */
    public static boolean allBosses(EntityPlayer p) {
        int done = dreamMask(p);
        for (Gift g : BOSSES) if ((done & 1 << g.ordinal()) == 0) return false;
        return true;
    }

    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static int allowedFloor(EntityPlayer p) {
        return 1 + Math.max(persisted(p).getInteger(TIER), wingTier(p));
    }

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        com.divinerpgextended.audit.Audit.say(p, key, args);
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    private static void sendDown(EntityPlayerMP p, int floor, int need) {
        World w = p.worldObj;
        int x = (int) Math.floor(p.posX), z = (int) Math.floor(p.posZ), base = surface(floor) + 1, ty = base;
        for (int y = base; y < base + 30; y++) {
            if (w.isAirBlock(x, y, z) && w.isAirBlock(x, y + 1, z) && !w.isAirBlock(x, y - 1, z)) {
                ty = y;
                break;
            }
        }
        p.setPositionAndUpdate(x + 0.5, ty, z + 0.5);
        p.fallDistance = 0;
        p.motionY = 0;
        say(p, EnumChatFormatting.DARK_PURPLE, "dre.vethea.guard", String.valueOf(need));
        com.divinerpgextended.debug.Debug.note(p, "Floor Guardian: dropped to floor " + floor + " (step needed: " + need + ")");
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote) return;
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        if (!vethea(p.worldObj)) {
            if (p.ticksExisted % 40 == 0) deliverGifts(p);
            return;
        }
        if (!Config.vetheaFloors) return;
        State s = STATE.get(p.getUniqueID());
        if (s == null) STATE.put(p.getUniqueID(), s = new State());
        int tier = wingTier(p);
        if (tier > persisted(p).getInteger(TIER)) persisted(p).setInteger(TIER, tier);
        if (p.ticksExisted % 20 == 0) carveNear(p);
        if (p.capabilities.isCreativeMode) return;
        // normal flight does not work in the dream
        if (p.capabilities.allowFlying || p.capabilities.isFlying) {
            p.capabilities.allowFlying = false;
            p.capabilities.isFlying = false;
            p.sendPlayerAbilities();
            Nightmare.hint(p, "flight");
        }
        // stream in a rift: without wings of the needed tier - down
        int f = riftAt(p.worldObj, p.posX, p.posY, p.posZ, FLOW_R2, 8, 1);
        if (f > 0) {
            s.lastRift = p.ticksExisted;
            if (tier < f - 1) {
                p.motionY = -0.8;
                if (p.ticksExisted % 20 == 0) com.divinerpgextended.debug.Debug.note(p, "Rift Current: downward, wing step " + tier + " < " + (f - 1));
                p.velocityChanged = true;
                if (p.ticksExisted - s.lastMsg > 100) {
                    s.lastMsg = p.ticksExisted;
                    say(p, EnumChatFormatting.DARK_PURPLE, "dre.vethea.downdraft", String.valueOf(f - 1));
                }
            }
        }
        // floor guardian
        int floor = floorOf(p.posY), allowed = allowedFloor(p);
        if (floor > allowed) {
            if (++s.over > 30) {
                s.over = 0;
                sendDown(p, allowed, floor - 1);
            }
        } else {
            s.over = 0;
            if (floor >= 2) mark(p, Gift.values()[floor - 2]);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        STATE.remove(e.player.getUniqueID());
    }

    /** Wings cancel the fall; through a rift you can also jump down without damage. */
    @SubscribeEvent
    public void onFall(LivingFallEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || !vethea(e.entityLiving.worldObj)) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        State s = STATE.get(p.getUniqueID());
        if (wingTier(p) > 0 || s != null && p.ticksExisted - s.lastRift < 100) {
            e.setCanceled(true);
            com.divinerpgextended.debug.Debug.note(p, "Fall does not hurt: " + (wingTier(p) > 0 ? "Dream Wings" : "jump across the rift"));
        }
    }

    // ---------------- Slabs and shafts ----------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent e) {
        if (!Config.vetheaFloors || !vethea(e.world) || e.getPlayer() == null || e.getPlayer().capabilities.isCreativeMode) return;
        if (!protectedSlab(e.y)) return;
        e.setCanceled(true);
        com.divinerpgextended.debug.Debug.note(e.getPlayer(), "Floor base: cannot be broken (y " + e.y + ")");
        Nightmare.hint(e.getPlayer(), "slab");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlace(BlockEvent.PlaceEvent e) {
        if (!Config.vetheaFloors || !vethea(e.world) || e.player == null || e.player.capabilities.isCreativeMode) return;
        if (!inShaft(e.world, e.x, e.y, e.z)) return;
        e.setCanceled(true);
        com.divinerpgextended.debug.Debug.note(e.player, "Rift shaft: cannot build");
        Nightmare.hint(e.player, "rift_place");
    }

    @SubscribeEvent
    public void onExplode(ExplosionEvent.Detonate e) {
        if (!Config.vetheaFloors || !vethea(e.world)) return;
        for (Iterator<ChunkPosition> it = e.getAffectedBlocks().iterator(); it.hasNext();) {
            ChunkPosition c = it.next();
            if (protectedSlab(c.chunkPosY)) it.remove();
        }
    }

    // ---------------- Dream gifts ----------------

    public enum Gift {
        FLOOR_2(200, new ItemStack(VanillaItemsOther.realmiteIngot, 12), new ItemStack(Items.diamond, 6)),
        FLOOR_3(400, new ItemStack(VanillaItemsOther.arlemiteIngot, 12), new ItemStack(VanillaItemsOther.healingStone, 4)),
        FLOOR_4(800, new ItemStack(VanillaItemsOther.rupeeIngot, 16), new ItemStack(Items.nether_star)),
        HIVE_QUEEN(300, new ItemStack(VanillaItemsOther.bloodgem, 8)),
        QUADRO(500, new ItemStack(VanillaItemsOther.divineStone, 4)),
        KAROS(500, new ItemStack(VanillaItemsOther.divineStone, 4)),
        RAGLOK(800, new ItemStack(VanillaItemsOther.legendaryEnderEye)),
        WRECK(800, new ItemStack(VanillaItemsOther.shadowBar, 16)),
        LADY_LUNA(1000, new ItemStack(Items.nether_star, 2));

        final int xp;
        final ItemStack[] items;

        Gift(int xp, ItemStack... items) {
            this.xp = xp;
            this.items = items;
        }

        String key() {
            return "dre.gift." + name().toLowerCase();
        }

        static Gift forBoss(Entity e) {
            if (e instanceof EntityHiveQueen) return HIVE_QUEEN;
            if (e instanceof EntityQuadro) return QUADRO;
            if (e instanceof EntityKaros) return KAROS;
            if (e instanceof EntityRaglok) return RAGLOK;
            if (e instanceof EntityWreck) return WRECK;
            if (e instanceof EntityLadyLuna) return LADY_LUNA;
            return null;
        }
    }

    static void mark(EntityPlayer p, Gift g) {
        NBTTagCompound d = persisted(p);
        int bit = 1 << g.ordinal(), done = d.getInteger(GIFT_DONE);
        if ((done & bit) != 0) return;
        d.setInteger(GIFT_DONE, done | bit);
        d.setInteger(GIFT_PENDING, d.getInteger(GIFT_PENDING) | bit);
        com.divinerpgextended.debug.Debug.note(p, "Dream gift marked: " + g.name());
        say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.vethea.gift_marked", new ChatComponentTranslation(g.key()));
    }

    private static void deliverGifts(EntityPlayer p) {
        NBTTagCompound d = persisted(p);
        int pending = d.getInteger(GIFT_PENDING);
        if (pending == 0) return;
        d.setInteger(GIFT_PENDING, 0);
        for (Gift g : Gift.values()) {
            if ((pending & 1 << g.ordinal()) == 0) continue;
            for (ItemStack s : g.items) {
                ItemStack c = s.copy();
                if (!p.inventory.addItemStackToInventory(c)) p.worldObj.spawnEntityInWorld(new EntityItem(p.worldObj, p.posX, p.posY + 0.5, p.posZ, c));
            }
            p.addExperience(g.xp);
            com.divinerpgextended.debug.Debug.note(p, "Dream gift given: " + g.name());
            say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.vethea.gift", new ChatComponentTranslation(g.key()));
        }
        p.inventoryContainer.detectAndSendChanges();
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.entityLiving.worldObj.isRemote || !vethea(e.entityLiving.worldObj)) return;
        Gift g = Gift.forBoss(e.entityLiving);
        if (g == null) return;
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = e.entityLiving.worldObj.getEntitiesWithinAABB(EntityPlayer.class, e.entityLiving.boundingBox.expand(48, 32, 48));
        for (EntityPlayer p : near) mark(p, g);
    }

    // Wings are made at the infusion altar ({@link Infusion}), there are no crafting table recipes.
}
