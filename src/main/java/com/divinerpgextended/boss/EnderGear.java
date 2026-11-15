package com.divinerpgextended.boss;

import net.divinerpg.utils.events.ArcanaHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.magic.ArcanaRules;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Abilities of a full End armor set (colors can be mixed):
 * <ul>
 * <li>Void Step: double W with an Ender sword in hand (from standing or right while running; the sprint key does not make a step) -
 * teleports {@link #RANGE} blocks in the direction you look, even upward, onto ledges up to {@link #LEDGE} blocks high. Fails if
 * there is no ground within {@link #GROUND} blocks below the target point (you can't step into the abyss) - the reason is shown above the hotbar. Costs
 * {@link #COST} arcana, short cooldown. The client ({@link Client}) catches the double W and sends a packet.</li>
 * <li>Void Rescue: if you fall below the bottom of the world, you are moved to the last solid ground, taking {@link #RESCUE_HURT} damage, no
 * more than once per {@link #RESCUE_CD} ticks.</li>
 * </ul>
 */
public class EnderGear {

    public static final double RANGE = 9;
    public static final int GROUND = 32, RESCUE_CD = 1200, LEDGE = 2;
    public static final float COST = 25F, RESCUE_HURT = 6F;
    static final String SAFE_X = "dreSafeX", SAFE_Y = "dreSafeY", SAFE_Z = "dreSafeZ", RESCUE_AT = "dreRescueAt";

    public static void register() {
        com.divinerpgextended.cold.ColdHandler.NET.registerMessage(BlinkHandler.class, Blink.class, 13, cpw.mods.fml.relauncher.Side.SERVER);
        com.divinerpgextended.cold.ColdHandler.NET.registerMessage(FailHandler.class, Fail.class, 14, cpw.mods.fml.relauncher.Side.CLIENT);
    }

    /** Server -> client: why the step failed (translation key) - as a line above the hotbar (in 1.7.10 the chat has no such place). */
    public static class Fail implements cpw.mods.fml.common.network.simpleimpl.IMessage {

        String key = "";

        public Fail() {}

        Fail(String key) {
            this.key = key;
        }

        @Override
        public void fromBytes(io.netty.buffer.ByteBuf buf) {
            key = cpw.mods.fml.common.network.ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(io.netty.buffer.ByteBuf buf) {
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, key);
        }
    }

    public static class FailHandler implements cpw.mods.fml.common.network.simpleimpl.IMessageHandler<Fail, cpw.mods.fml.common.network.simpleimpl.IMessage> {

        @Override
        public cpw.mods.fml.common.network.simpleimpl.IMessage onMessage(Fail msg, cpw.mods.fml.common.network.simpleimpl.MessageContext ctx) {
            Client.show(msg.key);
            return null;
        }
    }

    /** Client -> server: double W (from standing or while running). The server checks the set, sword, arcana and cooldown. */
    public static class Blink implements cpw.mods.fml.common.network.simpleimpl.IMessage {

        @Override
        public void fromBytes(io.netty.buffer.ByteBuf buf) {}

        @Override
        public void toBytes(io.netty.buffer.ByteBuf buf) {}
    }

    public static class BlinkHandler implements cpw.mods.fml.common.network.simpleimpl.IMessageHandler<Blink, cpw.mods.fml.common.network.simpleimpl.IMessage> {

        @Override
        public cpw.mods.fml.common.network.simpleimpl.IMessage onMessage(Blink msg, cpw.mods.fml.common.network.simpleimpl.MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            if (!EnderColors.fullSet(p) || !EnderColors.sword(p.getHeldItem())) return null;
            String why = tryBlink(p);
            if (why != null && !why.isEmpty()) com.divinerpgextended.cold.ColdHandler.NET.sendTo(new Fail(why), p); // cooldown - silently: while running, double W is pressed often
            if (why != null) p.worldObj.playSoundAtEntity(p, "random.fizz", 0.25F, 1.8F);
            return null;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote) return;
        if (!EnderColors.fullSet(p)) return;
        NBTTagCompound d = p.getEntityData();
        // last solid ground
        if (p.onGround && p.ticksExisted % 10 == 0 && p.worldObj.getBlock(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.boundingBox.minY) - 1,
            MathHelper.floor_double(p.posZ)).getMaterial().isSolid()) {
            d.setDouble(SAFE_X, p.posX);
            d.setDouble(SAFE_Y, p.posY);
            d.setDouble(SAFE_Z, p.posZ);
            d.setBoolean("dreSafe", true);
        }
        if (p.posY < -4 && d.getBoolean("dreSafe") && p.worldObj.getTotalWorldTime() >= d.getLong(RESCUE_AT)) rescue(p, d);
    }

    /** Void Step. @return whether it worked */
    public static boolean blink(EntityPlayer p) {
        return tryBlink(p) == null;
    }

    /**
     * Void Step: a look ray from the eyes, the feet go along it at eye height lower; if we hit a ledge up to {@link #LEDGE}
     * blocks high, we climb onto it (looking slightly down or a step does not break the step). Speed is preserved - a step while running
     * keeps the run going. @return null - worked; "" - cooldown; otherwise the reason key for the line above the hotbar
     */
    public static String tryBlink(EntityPlayer p) {
        World w = p.worldObj;
        // an Ayeraco on this archer's void anchor in front of the eyes - step straight to it, point blank
        net.minecraft.entity.EntityLiving prey = AyeracoHorde.anchoredFor(p);
        if (prey != null) {
            double dx = p.posX - prey.posX, dz = p.posZ - prey.posZ, len = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
            double gx = prey.posX + dx / len * (prey.width / 2 + 1.2), gz = prey.posZ + dz / len * (prey.width / 2 + 1.2), gy = prey.posY;
            if (fits(w, gx, gy, gz) && clear(p, gx, gy, gz)) {
                if (com.divinerpgextended.perk.EnderGear.pinned(p)) return "dre.blink.pinned";
                if (!com.divinerpgextended.portal.TeleportGuard.allowed(p, gx, gy, gz)) return "dre.blink.private";
                ArcanaHelper h = ArcanaHelper.getProperties(p);
                if (h == null) return "dre.blink.arcana";
                if (!ArcanaRules.useBar(h, COST, "EnderBlink")) return h.getBarValue() < COST * 0.5F ? "dre.blink.arcana" : "";
                particles(w, p.posX, p.boundingBox.minY, p.posZ);
                moveTo(p, gx, gy + (p.posY - p.boundingBox.minY), gz);
                p.fallDistance = 0;
                w.playSoundEffect(gx, gy, gz, "mob.endermen.portal", 1F, 0.9F);
                particles(w, gx, gy, gz);
                return null;
            }
        }
        Vec3 look = p.getLookVec();
        double x = p.posX, y = p.boundingBox.minY, z = p.posZ;
        double tx = x, ty = y, tz = z, lift = 0;
        for (double s = 0.5; s <= RANGE; s += 0.5) {
            double nx = x + look.xCoord * s, nz = z + look.zCoord * s, ny = y + look.yCoord * s + lift;
            if (!fits(w, nx, ny, nz) || !clear(p, nx, ny, nz)) {
                double up = -1;
                double base = Math.floor(Math.max(ny, ty)); // from the feet, not from the ray: looking down leads the ray under the ground
                for (int k = 0; k <= LEDGE && up < 0; k++) {
                    double c = base + k; // stand on top of the block
                    if (c > ny && c - ty <= LEDGE + 0.01 && fits(w, nx, c, nz) && clear(p, nx, c, nz)) up = c;
                }
                if (up < 0) break;
                lift += up - ny;
                ny = up;
            }
            tx = nx;
            ty = ny;
            tz = nz;
        }
        if ((tx - x) * (tx - x) + (ty - y) * (ty - y) + (tz - z) * (tz - z) < 2 * 2) return "dre.blink.wall";
        if (!clear(p, tx, ty, tz)) return "dre.blink.wall"; // not into blocks
        if (!groundBelow(w, tx, ty, tz)) return "dre.blink.void"; // can't step into the abyss
        if (com.divinerpgextended.perk.EnderGear.pinned(p)) return "dre.blink.pinned";
        if (!com.divinerpgextended.portal.TeleportGuard.allowed(p, tx, ty, tz)) return "dre.blink.private"; // a protected region that forbids teleports
        ArcanaHelper h = ArcanaHelper.getProperties(p);
        if (h == null) return "dre.blink.arcana";
        if (!ArcanaRules.useBar(h, COST, "EnderBlink")) return h.getBarValue() < COST * 0.5F ? "dre.blink.arcana" : ""; // not enough arcana or on cooldown
        particles(w, x, y, z);
        moveTo(p, tx, ty + (p.posY - p.boundingBox.minY), tz);
        p.fallDistance = 0;
        w.playSoundEffect(tx, ty, tz, "mob.endermen.portal", 1F, 1.3F);
        particles(w, tx, ty, tz);
        return null;
    }

    /** Half the player width (0.3) slightly inward, so that a player standing right against a wall is not counted as inside it: in a corner of blocks the center is in the air, but the hitbox edges are in walls (suffocated). */
    static final double HALF = 0.29;

    /** Whether the player fits (feet at y): all four hitbox corners are free at three heights. */
    static boolean fits(World w, double x, double y, double z) {
        for (int cx = -1; cx <= 1; cx += 2)
            for (int cz = -1; cz <= 1; cz += 2) {
                double ax = x + cx * HALF, az = z + cz * HALF;
                if (solidAt(w, ax, y, az) || solidAt(w, ax, y + 1, az) || solidAt(w, ax, y + 1.8, az)) return false;
            }
        return true;
    }

    /** The player hitbox at the point (feet at y) does not touch anything - an exact check against block bounding boxes before the teleport. */
    static boolean clear(EntityPlayer p, double x, double y, double z) {
        return p.worldObj.func_147461_a(p.boundingBox.getOffsetBoundingBox(x - p.posX, y - p.boundingBox.minY, z - p.posZ)).isEmpty();
    }

    static boolean solidAt(World w, double x, double y, double z) {
        return w.getBlock(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z)).getMaterial().blocksMovement();
    }

    static boolean groundBelow(World w, double x, double y, double z) {
        int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z), by = MathHelper.floor_double(y);
        for (int k = 0; k <= GROUND && by - k >= 0; k++) if (w.getBlock(bx, by - k, bz).getMaterial().isSolid()) return true;
        return false;
    }

    static void rescue(EntityPlayer p, NBTTagCompound d) {
        d.setLong(RESCUE_AT, p.worldObj.getTotalWorldTime() + RESCUE_CD);
        double x = d.getDouble(SAFE_X), y = d.getDouble(SAFE_Y), z = d.getDouble(SAFE_Z);
        if (!com.divinerpgextended.portal.TeleportGuard.check(p, x, y, z)) return; // a protected region forbids teleports - and rescue too
        p.motionX = p.motionY = p.motionZ = 0;
        moveTo(p, x, y, z);
        p.fallDistance = 0;
        p.attackEntityFrom(DamageSource.magic, RESCUE_HURT);
        p.worldObj.playSoundEffect(x, y, z, "mob.endermen.portal", 1F, 0.7F);
        p.addChatMessage(new ChatComponentTranslation("dre.chat.void_rescue"));
    }

    /** Teleport the player (notifying the client if there is a connection). */
    static void moveTo(EntityPlayer p, double x, double y, double z) {
        if (p instanceof EntityPlayerMP && ((EntityPlayerMP) p).playerNetServerHandler != null)
            ((EntityPlayerMP) p).playerNetServerHandler.setPlayerLocation(x, y, z, p.rotationYaw, p.rotationPitch);
        else p.setPosition(x, y, z);
    }

    /** Client: double tap of W (the second no later than {@link #DOUBLE_TAP} ticks after the first) in a full set with an Ender sword. */
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public static class Client {

        static final int DOUBLE_TAP = 7;
        private boolean wasDown;
        private int sinceTap = 100;

        /** The line above the hotbar - the place for the record disc caption. */
        static void show(String key) {
            net.minecraft.client.Minecraft.getMinecraft().ingameGUI.func_110326_a(net.minecraft.util.EnumChatFormatting.LIGHT_PURPLE
                + net.minecraft.util.StatCollector.translateToLocal(key), false);
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            EntityPlayer p = mc.thePlayer;
            if (p == null || mc.currentScreen != null) {
                wasDown = false;
                return;
            }
            boolean down = mc.gameSettings.keyBindForward.getIsKeyPressed();
            sinceTap++;
            if (down && !wasDown) {
                if (sinceTap <= DOUBLE_TAP && EnderColors.fullSet(p) && EnderColors.sword(p.getHeldItem())) {
                    com.divinerpgextended.cold.ColdHandler.NET.sendToServer(new Blink());
                    sinceTap = 100; // third press - a pair again
                } else sinceTap = 0;
            }
            wasDown = down;
        }
    }

    static void particles(World w, double x, double y, double z) {
        if (w instanceof WorldServer) ((WorldServer) w).func_147487_a("portal", x, y + 1, z, 30, 0.4, 0.8, 0.4, 0.5);
    }
}
