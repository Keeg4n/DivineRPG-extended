package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Dream Water is the inverted water of the lakes in the ceiling of Vethea floors: flows upward (density below zero), the surface faces
 * down. For the game it is water (water material): you swim, sink and fish in it. Between two sources under a support a new one is born
 * (infinite, like water).
 * <p>
 * Whoever touches it with the top of the hitbox is immediately inverted ({@link #inverted}): hangs upside down, feet in the water, head in the
 * air - holds at the surface by itself, and fishing happens here (the bobber is cast up, into the lake). The screen is inverted, keys work as in ordinary water by the screen: Shift -
 * dive deeper (up in the world), space - rise to the surface (down), space at the surface - surface and fall. In the depth you are pulled to
 * the lake bottom (up).
 */
public class BlockDreamWater extends BlockFluidClassic {

    /** From what depth (from the top of the hitbox) the player is inverted; at what depth he holds at the surface; where the depth begins. */
    public static final double FLIP = 0.01, HOLD = 0.6, DEEP = 1.3;

    @SideOnly(Side.CLIENT)
    private IIcon still, flow;

    public BlockDreamWater(Fluid f) {
        super(f, Material.water);
        setBlockName("dre.dreamWater");
        setLightLevel(0.4F);
        setHardness(100F);
        setLightOpacity(2);
    }

    @Override
    public void updateTick(World w, int x, int y, int z, Random r) {
        super.updateTick(w, x, y, z, r);
        if (w.isRemote || w.getBlock(x, y, z) != this || w.getBlockMetadata(x, y, z) == 0) return;
        int sources = 0;
        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
            if (isSource(w, x + d[0], y, z + d[1])) sources++;
        // the support is on top: the water flows up
        if (sources >= 2 && (w.getBlock(x, y + 1, z).getMaterial().isSolid() || isSource(w, x, y + 1, z))) w.setBlock(x, y, z, this, 0, 3);
    }

    public static boolean isSource(World w, int x, int y, int z) {
        return w.getBlock(x, y, z) instanceof BlockDreamWater && w.getBlockMetadata(x, y, z) == 0;
    }

    /** How far the top of the hitbox went into Dream Water (by the middle column); 0 - not in it. */
    public static double depth(Entity e) {
        int x = MathHelper.floor_double(e.posX), z = MathHelper.floor_double(e.posZ);
        int y0 = MathHelper.floor_double(e.boundingBox.minY), y1 = MathHelper.floor_double(e.boundingBox.maxY);
        for (int y = y0; y <= y1; y++) if (e.worldObj.getBlock(x, y, z) instanceof BlockDreamWater) return Math.max(0, e.boundingBox.maxY - y);
        return 0;
    }

    public static boolean inside(Entity e) {
        return depth(e) > 0;
    }

    /** Inverted: feet (top of the hitbox) in Dream Water. */
    public static boolean inverted(Entity e) {
        return depth(e) >= FLIP;
    }

    /** The inverted one's head (bottom of the hitbox) is in water - drowning. For small mobs the head is the bottom quarter of the hitbox. */
    public static boolean headUnder(EntityLivingBase e) {
        return e.worldObj.getBlock(MathHelper.floor_double(e.posX), MathHelper.floor_double(e.boundingBox.minY + Math.min(0.2, e.height * 0.25)),
            MathHelper.floor_double(e.posZ)) instanceof BlockDreamWater;
    }

    /** At what depth he holds at the surface: a player - {@link #HOLD}, a small mob - a third of the height (otherwise the head is underwater and he drowns). */
    public static double hold(EntityLivingBase e) {
        return e instanceof EntityPlayer ? HOLD : Math.min(HOLD, e.height * 0.35);
    }

    /**
     * Non-living (dropped items, experience): sinks in its own way in Dream Water - goes up, to the lake bottom, and lies there. Called
     * every tick on the server and on the client (both sides compute item movement).
     */
    public static void sinkLoose(World w) {
        if (w == null) return;
        // over a copy and only in loaded chunks: reading a block at the edge loaded the chunk further, its entities got into the list
        // in the middle of iteration - the server crashed (ConcurrentModificationException, flight to the End island)
        for (Object o : w.loadedEntityList.toArray()) {
            if (!(o instanceof net.minecraft.entity.item.EntityItem) && !(o instanceof net.minecraft.entity.item.EntityXPOrb)) continue;
            Entity e = (Entity) o;
            int ex = MathHelper.floor_double(e.posX), ez = MathHelper.floor_double(e.posZ);
            if (!w.checkChunksExist(ex - 1, 0, ez - 1, ex + 1, 0, ez + 1) || !inside(e)) continue;
            e.motionY += 0.04 + 0.02; // cancels gravity (0.04) and pulls up, to the lake bottom
            e.motionY = Math.min(0.12, e.motionY);
            e.fallDistance = 0;
        }
    }

    public static class Loose {

        @SubscribeEvent
        public void onWorld(cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent ev) {
            if (ev.phase == cpw.mods.fml.common.gameevent.TickEvent.Phase.START) sinkLoose(ev.world);
        }

        @SubscribeEvent
        @SideOnly(Side.CLIENT)
        public void onClient(cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent ev) {
            if (ev.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.START) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.isGamePaused()) return;
            sinkLoose(mc.theWorld);
        }
    }

    /** Inverted swimming (on both sides: the client computes player movement). */
    public static class Swim {

        private static java.lang.reflect.Field jumping, floating;

        static boolean jumping(EntityLivingBase e) {
            try {
                if (jumping == null) jumping = ReflectionHelper.findField(EntityLivingBase.class, "isJumping", "field_70703_bu");
                return jumping.getBoolean(e);
            } catch (Throwable t) {
                return false;
            }
        }

        /** The server considers one hanging at the surface "flying" and kicks after 4 s - we reset his counter. */
        static void notFlying(EntityPlayerMP p) {
            if (p.playerNetServerHandler == null) return;
            try {
                if (floating == null) floating = ReflectionHelper.findField(net.minecraft.network.NetHandlerPlayServer.class, "floatingTickCount", "field_147365_f");
                floating.setInt(p.playerNetServerHandler, 0);
            } catch (Throwable ignored) {}
        }

        /** Released space at the surface: the next press throws him out of the lake. */
        private static final java.util.Map<EntityLivingBase, Boolean> ARMED = new java.util.WeakHashMap<EntityLivingBase, Boolean>();

        @SubscribeEvent
        public void onUpdate(LivingEvent.LivingUpdateEvent ev) {
            EntityLivingBase e = ev.entityLiving;
            double d = depth(e);
            if (d <= 0) {
                ARMED.remove(e);
                return;
            }
            if (e instanceof EntityPlayer && ((EntityPlayer) e).capabilities.isFlying) return;
            e.fallDistance = 0;
            if (!e.worldObj.isRemote && e instanceof EntityPlayerMP) {
                notFlying((EntityPlayerMP) e);
                if (!headUnder(e)) e.setAir(300); // head in the air (the game counts the eyes at the top of the hitbox - in the water)
            } else if (!e.worldObj.isRemote && !headUnder(e)) {
                e.setAir(300); // and the mob: hangs upside down, head in the air - does not drown
            }
            // as in ordinary water, but by the inverted screen: space - to the surface (down in the world), Shift - deeper (up)
            // mobs in water constantly "jump" (swimming AI) - they have no keys: they just hang at the surface
            boolean player = e instanceof EntityPlayer, surface = player && jumping(e), dive = player && e.isSneaking();
            if (!player) { // a mob floats to the surface from any depth (as in ordinary water) and hangs with its head in the air
                if (d >= FLIP || e.motionY > 0) e.motionY = Math.max(-0.3, (hold(e) - d) * 0.3 + 0.02);
                return;
            }
            if (d >= DEEP) { // in the depth you are pulled to the lake bottom (up), as in ordinary water to the bottom
                e.motionY += 0.04; // water pulls down by 0.02, the water jump lifts by 0.04 - the net is up
                if (surface) e.motionY -= 0.10;
                if (dive) e.motionY += 0.03;
                e.motionY = Math.max(-0.3, Math.min(0.3, e.motionY));
                if (surface) ARMED.remove(e); // surfaced with space held - holds at the surface instead of falling out
            } else if (d >= FLIP || e.motionY > 0) { // at the surface: holds with feet in the water
                if (!surface) ARMED.put(e, Boolean.TRUE);
                if (surface && ARMED.containsKey(e)) e.motionY = -0.35; // space at the surface - surface: falls out of the lake
                else if (dive) e.motionY = 0.12;
                else e.motionY = (HOLD - d) * 0.3 + 0.02; // springs to depth HOLD (+0.02 cancels the water's downward pull)
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        still = reg.registerIcon("divinerpgextended:dreamwater_still");
        flow = reg.registerIcon("divinerpgextended:dreamwater_flow");
        getFluid().setIcons(still, flow);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 0 || side == 1 ? still : flow;
    }
}
