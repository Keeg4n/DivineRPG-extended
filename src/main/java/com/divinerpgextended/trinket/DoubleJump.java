package com.divinerpgextended.trinket;

import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.potion.Potion;
import net.minecraft.world.WorldServer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.divinerpgextended.accessory.Accessories;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossProgress;
import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Movement in the air from accessories. The client computes player movement, so it jumps and hovers by itself; the server checks
 * that the item is worn and the reserve is not exhausted, and only then lifts the penalties (fall height, flight kick). What is available is
 * reported by the server to the client (on change and every 5 seconds).
 * <ul>
 * <li>Ayeraco Wing: a jump in the air, in the Twilight dimensions - two.</li>
 * <li>Flying Carpet (only in the Twilight dimensions): jump held in the air - height holds, you fly horizontally.
 * Reserve per world: Eden and Wildwood 10 s, Apalachia 8, Skythern 5, Mortum 3; unlimited after the Twilight Demon;
 * accumulates only on the ground. When it runs out, while space is held the carpet slowly lowers you (gliding) without damage. The carpet is
 * visible underfoot (to other players too). Fall height is counted from the carpet.</li>
 * </ul>
 */
public class DoubleJump {

    /** A carpet with no time limit. */
    public static final int UNLIMITED = -1;
    /** Glide speed on the carpet after the reserve ends (blocks per tick downward). */
    static final double GLIDE = -0.1;

    private static class State {

        /** Jumps since last touching the ground. */
        int used;
        /** How many ticks of carpet and dream wings remain until the ground. */
        int carpetLeft, wingLeft;
        /** What was last sent to the client. */
        int sentJumps = -2, sentCarpet = -2, sentWing = -2;
        /** For debugging: consecutive ticks hovering on the carpet, ticks of wing takeoff. */
        int hoverRun, wingRun;
        double lastY;
        /** The carpet underfoot is shown to nearby players. */
        boolean carpetShown;
    }

    private static final Map<UUID, State> STATE = new HashMap<UUID, State>();
    /** Client: jumps in the air, carpet ticks and dream wing ticks (sent by the server). */
    static int clientAllowed, clientCarpet, clientWing;
    /** Dream wing takeoff reserve by tier (ticks; climb speed up to 0.6 block per tick - enough for a 48-block floor). */
    private static final int[] WING_TICKS = { 0, 85, 100, 120 };

    public static void register() {
        ColdHandler.NET.registerMessage(SyncHandler.class, Sync.class, 6, Side.CLIENT);
        ColdHandler.NET.registerMessage(JumpHandler.class, Jump.class, 7, Side.SERVER);
        ColdHandler.NET.registerMessage(CarpetStateHandler.class, CarpetState.class, 9, Side.CLIENT);
    }

    public static boolean twilight(int d) {
        return d == ConfigurationHelper.eden || d == ConfigurationHelper.wildwood || d == ConfigurationHelper.apalachia
            || d == ConfigurationHelper.skythern || d == ConfigurationHelper.mortum;
    }

    public static int allowed(EntityPlayer p) {
        if (!Accessories.wears(p, Trinket.AYERACO_WING)) return 0;
        return twilight(p.dimension) ? 2 : 1;
    }

    /** Carpet reserve in ticks: 0 - does not work, {@link #UNLIMITED} - unlimited. */
    public static int carpetTicks(EntityPlayer p) {
        if (!Accessories.wears(p, Trinket.MAGIC_CARPET) || p.dimension == ConfigurationHelper.vethea) return 0;
        if (!p.worldObj.isRemote && com.divinerpgextended.boss.ArenaCalm.carpetBlocked(p)) return 0; // the carpet does not fly during a boss fight
        boolean demon = BossProgress.has(p, Boss.TWILIGHT_DEMON);
        // outside the twilight worlds the carpet barely holds (3 s) until the twilight worlds are completed (Twilight Demon) - then 10 s
        if (!twilight(p.dimension)) return demon ? OUTSIDE_FREE : OUTSIDE;
        if (demon) return UNLIMITED;
        // per world: in the first three there are gaps between islands - the carpet is needed most there; in Skythern height matters more,
        // in Mortum there are almost no gaps
        int d = p.dimension;
        int t = d == ConfigurationHelper.eden || d == ConfigurationHelper.wildwood ? 200 : d == ConfigurationHelper.apalachia ? 160
            : d == ConfigurationHelper.skythern ? 100 : 60;
        return skyMaster(p) ? t * 2 : t; // Wind Master: double reserve
    }

    /** Full Skythern set (Wind Master): the carpet flies up and down by gaze. */
    public static boolean skyMaster(EntityPlayer p) {
        return com.divinerpgextended.perk.HomeArmor.wears(p, com.divinerpgextended.world.twilight.TwilightWorld.SKYTHERN, false);
    }

    /** Vertical speed of the Wind Master carpet: gaze up/down while moving forward, sneaking - down. */
    public static double skyLift(EntityPlayer p, boolean forward, boolean sneak) {
        if (sneak) return -SKY_SPEED;
        if (!forward || Math.abs(p.rotationPitch) < 15) return 0;
        return Math.max(-SKY_SPEED, Math.min(SKY_SPEED, -p.rotationPitch / 60.0 * SKY_SPEED));
    }

    static final double SKY_SPEED = 0.3;

    /** The carpet outside the twilight worlds (ticks): before defeating the Twilight Demon and after. */
    public static final int OUTSIDE = 60, OUTSIDE_FREE = 200;

    /** Dream wing takeoff reserve, ticks: always in Vethea, in other worlds - when Vethea is completed (Lady Luna). */
    public static int wingTicks(EntityPlayer p) {
        if (p.dimension != ConfigurationHelper.vethea && !com.divinerpgextended.vethea.VetheaFloors.dreamDone(p)) return 0;
        return WING_TICKS[com.divinerpgextended.vethea.VetheaFloors.wingTier(p)];
    }

    /** Touches support: jumps, carpet and wings are restored. */
    private static boolean grounded(EntityPlayer p) {
        return p.onGround || p.isInWater() || p.handleLavaMovement() || p.isOnLadder() || p.capabilities.isFlying
            || p.ridingEntity != null;
    }

    private static State state(EntityPlayer p) {
        State s = STATE.get(p.getUniqueID());
        if (s == null) STATE.put(p.getUniqueID(), s = new State());
        return s;
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote) return;
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        State s = state(p);
        double dy = p.posY - s.lastY;
        s.lastY = p.posY;
        // the client sends a movement packet every tick, in the air the server counts them in the flight counter (we reset it every
        // hover tick). No packets since the last tick - the client hung (freeze): carpet and wing reserve is not spent
        int packets = ReflectionHelper.<Integer, NetHandlerPlayServer>getPrivateValue(NetHandlerPlayServer.class, p.playerNetServerHandler,
            "floatingTickCount", "field_147365_f");
        boolean moved = packets > 0;
        int carpet = carpetTicks(p), wing = wingTicks(p);
        boolean hovering = false, rising = false, gliding = false;
        if (grounded(p)) {
            s.used = 0;
            s.carpetLeft = carpet;
            s.wingLeft = wing;
        } else if (wing > 0 && s.wingLeft > 0 && dy > -0.03) {
            // dream wing takeoff: not forbidden flight
            rising = true;
            if (moved) s.wingLeft = Math.min(s.wingLeft, wing) - 1;
            ReflectionHelper.setPrivateValue(NetHandlerPlayServer.class, p.playerNetServerHandler, 0, "floatingTickCount",
                "field_147365_f");
            if (s.wingLeft == 0) com.divinerpgextended.debug.Debug.note(p, "Dream Wings: flight reserve ran out at y " + (int) p.posY);
        } else if (carpet != 0 && (carpet == UNLIMITED || s.carpetLeft > 0)
            && (skyMaster(p) ? dy < SKY_SPEED + 0.05 && dy > -SKY_SPEED - 0.05 : dy < 0.05 && dy > -0.1)) {
            // hovering on the carpet: the server must not take this for forbidden flight; fall height is counted from the carpet,
            // not from the place where the fall began before it
            hovering = true;
            if (carpet != UNLIMITED && moved) s.carpetLeft = Math.min(s.carpetLeft, carpet) - 1;
            p.fallDistance = 0;
            ReflectionHelper.setPrivateValue(NetHandlerPlayServer.class, p.playerNetServerHandler, 0, "floatingTickCount",
                "field_147365_f");
            if (p.ticksExisted % 4 == 0) {
                ((WorldServer) p.worldObj).func_147487_a("cloud", p.posX, p.boundingBox.minY, p.posZ, 2, 0.3, 0.02, 0.3, 0.01);
            }
            if (s.carpetLeft == 0 && carpet != UNLIMITED) com.divinerpgextended.debug.Debug.note(p, "Carpet: reserve ran out, gliding from now on");
        } else if (carpet != 0 && dy <= -0.03 && dy >= -0.2) {
            // the carpet slowly lowers (about 2 blocks per second) - the fall does not accumulate. The remaining reserve is not checked:
            // after freezes the client may have spent the reserve before the server, and gliding would then not count
            gliding = true;
            p.fallDistance = 0;
        }
        // the carpet underfoot is seen by everyone nearby: on state change and once a second while hovering (for those who came later)
        boolean onCarpet = hovering && s.hoverRun >= 1 || gliding;
        if (onCarpet != s.carpetShown || onCarpet && p.ticksExisted % 20 == 0) {
            s.carpetShown = onCarpet;
            ColdHandler.NET.sendToAllAround(new CarpetState(p.getEntityId(), onCarpet),
                new cpw.mods.fml.common.network.NetworkRegistry.TargetPoint(p.dimension, p.posX, p.posY, p.posZ, 96));
        }
        // debug: start of hovering (5 ticks in a row, so as not to confuse with the top of a jump) and takeoff
        s.hoverRun = hovering ? s.hoverRun + 1 : 0;
        s.wingRun = rising ? s.wingRun + 1 : 0;
        if (s.hoverRun == 5) com.divinerpgextended.debug.Debug.note(p, "Carpet: hovering, reserve "
            + (carpet == UNLIMITED ? "unlimited" : s.carpetLeft / 20.0 + " s of " + carpet / 20.0));
        if (s.wingRun == 3) com.divinerpgextended.debug.Debug.note(p, "Dream Wings: takeoff from y " + (int) p.posY + ", reserve " + (s.wingLeft + 3) + " ticks");
        if (p.ticksExisted % 10 != 0) return;
        int a = allowed(p);
        if (a != s.sentJumps || carpet != s.sentCarpet || wing != s.sentWing || p.ticksExisted % 100 == 0) {
            s.sentJumps = a;
            s.sentCarpet = carpet;
            s.sentWing = wing;
            ColdHandler.NET.sendTo(new Sync(a, carpet, wing), p);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        STATE.remove(e.player.getUniqueID());
    }

    // ---------------- Messages ----------------

    public static class Sync implements IMessage {

        int allowed, carpet, wing;

        public Sync() {}

        Sync(int allowed, int carpet, int wing) {
            this.allowed = allowed;
            this.carpet = carpet;
            this.wing = wing;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            allowed = buf.readByte();
            carpet = buf.readShort();
            wing = buf.readShort();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(allowed);
            buf.writeShort(carpet);
            buf.writeShort(wing);
        }
    }

    public static class SyncHandler implements IMessageHandler<Sync, IMessage> {

        @Override
        public IMessage onMessage(Sync msg, MessageContext ctx) {
            clientAllowed = msg.allowed;
            clientCarpet = msg.carpet;
            clientWing = msg.wing;
            return null;
        }
    }

    /** Who is currently standing on the carpet (for drawing the carpet underfoot for other players). */
    public static class CarpetState implements IMessage {

        int entity;
        boolean on;

        public CarpetState() {}

        CarpetState(int entity, boolean on) {
            this.entity = entity;
            this.on = on;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            entity = buf.readInt();
            on = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(entity);
            buf.writeBoolean(on);
        }
    }

    public static class CarpetStateHandler implements IMessageHandler<CarpetState, IMessage> {

        @Override
        public IMessage onMessage(CarpetState msg, MessageContext ctx) {
            if (msg.on) CARPETS.put(msg.entity, System.currentTimeMillis());
            else CARPETS.remove(msg.entity);
            return null;
        }
    }

    /** Client: ids of players on the carpet -> when it arrived (no update for 2 s - no carpet). */
    static final Map<Integer, Long> CARPETS = new java.util.concurrent.ConcurrentHashMap<Integer, Long>();

    public static class Jump implements IMessage {

        @Override
        public void fromBytes(ByteBuf buf) {}

        @Override
        public void toBytes(ByteBuf buf) {}
    }

    public static class JumpHandler implements IMessageHandler<Jump, IMessage> {

        @Override
        public IMessage onMessage(Jump msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            State s = state(p);
            if (s.used >= allowed(p)) {
                com.divinerpgextended.debug.Debug.note(p, "Air jump rejected by the server: " + s.used + "/" + allowed(p));
                return null; // no wing or jumps are used up - the fall is counted as usual
            }
            s.used++;
            com.divinerpgextended.debug.Debug.note(p, "Ayeraco Wing: air jump " + s.used + "/" + allowed(p));
            p.fallDistance = 0;
            p.worldObj.playSoundAtEntity(p, "mob.enderdragon.wings", 0.35F, 1.6F);
            return null;
        }
    }

    // ---------------- Client ----------------

    @SideOnly(Side.CLIENT)
    public static class Client {

        private int used, carpetLeft, wingLeft;
        private boolean wasDown, airborne;
        /** In this flight the player already stood on the carpet (for Wind Master: after that the carpet can also rise). */
        private boolean carpetOn;
        /** Who is currently standing on the carpet (by the last frame) - they do not move their legs. */
        private final java.util.Set<Integer> CARPET_NOW = new java.util.HashSet<Integer>();

        /** They stand on the carpet, not run: damp the leg swing before rendering the player. */
        @SubscribeEvent
        public void onRenderPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
            if (!CARPET_NOW.contains(e.entityPlayer.getEntityId())) return;
            e.entityPlayer.limbSwingAmount = 0;
            e.entityPlayer.prevLimbSwingAmount = 0;
        }

        /** The tick when the own player last stayed on the carpet (the own carpet is drawn at once, without waiting for the server). */
        private int selfCarpet = -100;

        /**
         * The carpet underfoot: red wool with a golden border, rotated with the player's body and gently waving. Own - by
         * own hovering, others - by server messages.
         */
        @SubscribeEvent
        public void onRenderWorld(net.minecraftforge.client.event.RenderWorldLastEvent e) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null) return;
            long now = System.currentTimeMillis();
            for (Object o : mc.theWorld.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                boolean on;
                if (p == mc.thePlayer) {
                    // after a world change or rejoin the player's ticks restart from zero - an old mark in the future does not count
                    int d = p.ticksExisted - selfCarpet;
                    on = d >= 0 && d <= 1 && !grounded(p);
                }
                else {
                    Long t = CARPETS.get(p.getEntityId());
                    on = t != null && now - t < 2000 && !p.onGround;
                }
                if (on) drawCarpet(p, e.partialTicks);
                if (on) CARPET_NOW.add(p.getEntityId());
                else CARPET_NOW.remove(p.getEntityId());
            }
        }

        private static void drawCarpet(EntityPlayer p, float pt) {
            Minecraft mc = Minecraft.getMinecraft();
            double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt - net.minecraft.client.renderer.entity.RenderManager.renderPosX;
            double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt - p.yOffset
                - net.minecraft.client.renderer.entity.RenderManager.renderPosY;
            double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt - net.minecraft.client.renderer.entity.RenderManager.renderPosZ;
            float yaw = p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * pt;
            float wave = (p.ticksExisted + pt) * 0.25F;
            int light = p.getBrightnessForRender(pt);
            net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit,
                light % 65536, light / 65536);
            mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.locationBlocksTexture);
            net.minecraft.util.IIcon red = net.minecraft.init.Blocks.wool.getIcon(1, 14);
            net.minecraft.util.IIcon gold = net.minecraft.init.Blocks.wool.getIcon(1, 4);
            org.lwjgl.opengl.GL11.glPushMatrix();
            org.lwjgl.opengl.GL11.glTranslated(x, y - 0.06, z);
            org.lwjgl.opengl.GL11.glRotatef(-yaw, 0, 1, 0);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
            net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.instance;
            t.startDrawingQuads();
            // a 1.0 x 1.5 canvas (long side along the gaze), split into strips - a wave runs along them
            double w = 0.5, l = 0.75, h = 0.05, b = 0.08;
            int n = 6;
            for (int i = 0; i < n; i++) {
                double z0 = -l + 2 * l * i / n, z1 = -l + 2 * l * (i + 1) / n;
                double y0 = Math.sin(wave + i * 0.9) * 0.03, y1 = Math.sin(wave + (i + 1) * 0.9) * 0.03;
                strip(t, red, -w + b, w - b, z0, z1, y0, y1, h);
                strip(t, gold, -w, -w + b, z0, z1, y0, y1, h);
                strip(t, gold, w - b, w, z0, z1, y0, y1, h);
            }
            // golden ends at the front and back
            double ye = Math.sin(wave) * 0.03, yf = Math.sin(wave + n * 0.9) * 0.03;
            strip(t, gold, -w, w, -l - b, -l, ye, ye, h);
            strip(t, gold, -w, w, l, l + b, yf, yf, h);
            t.draw();
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GL11.glPopMatrix();
        }

        /** Carpet strip: top, bottom and side faces of thickness h; y0/y1 - height at the near and far edge. */
        private static void strip(net.minecraft.client.renderer.Tessellator t, net.minecraft.util.IIcon i, double x0, double x1,
            double z0, double z1, double y0, double y1, double h) {
            double u0 = i.getMinU(), u1 = i.getMaxU(), v0 = i.getMinV(), v1 = i.getMaxV();
            t.addVertexWithUV(x0, y0 + h, z0, u0, v0);
            t.addVertexWithUV(x0, y1 + h, z1, u0, v1);
            t.addVertexWithUV(x1, y1 + h, z1, u1, v1);
            t.addVertexWithUV(x1, y0 + h, z0, u1, v0);
            t.addVertexWithUV(x0, y0, z0, u0, v0);
            t.addVertexWithUV(x1, y0, z0, u1, v0);
            t.addVertexWithUV(x1, y1, z1, u1, v1);
            t.addVertexWithUV(x0, y1, z1, u0, v1);
            t.addVertexWithUV(x0, y0, z0, u0, v0);
            t.addVertexWithUV(x0, y0 + h, z0, u0, v1);
            t.addVertexWithUV(x1, y0 + h, z0, u1, v1);
            t.addVertexWithUV(x1, y0, z0, u1, v0);
            t.addVertexWithUV(x0, y1, z1, u0, v0);
            t.addVertexWithUV(x1, y1, z1, u1, v0);
            t.addVertexWithUV(x1, y1 + h, z1, u1, v1);
            t.addVertexWithUV(x0, y1 + h, z1, u0, v1);
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            EntityClientPlayerMP p = mc.thePlayer;
            if (p == null) {
                clientAllowed = clientCarpet = clientWing = 0;
                return;
            }
            // the carpet reserve arrives from the server twice a second - there is no carpet in Vethea
            if (p.dimension == ConfigurationHelper.vethea) clientCarpet = 0;
            boolean down = mc.gameSettings.keyBindJump.getIsKeyPressed();
            // a normal jump from the ground lifts the player in the same tick as the press - a jump in the air counts
            // only a press when the player was already in the air on the previous tick
            boolean wasAirborne = airborne;
            boolean pressed = down && !wasDown && wasAirborne;
            wasDown = down;
            airborne = !grounded(p);
            if (!airborne) {
                used = 0;
                carpetOn = false;
                carpetLeft = clientCarpet;
                wingLeft = clientWing;
                return;
            }
            // in creative, double space enables flight - we do not interfere
            if (mc.currentScreen != null || p.capabilities.allowFlying) return;
            // dream wings: jump held - pulls up while there is reserve; hovering is impossible
            if (clientWing > 0) {
                if (down && wingLeft > 0) {
                    wingLeft--;
                    p.motionY = Math.min(p.motionY + 0.15, 0.6);
                    p.fallDistance = 0;
                    p.worldObj.spawnParticle("witchMagic", p.posX + (p.getRNG().nextDouble() - 0.5), p.boundingBox.minY + 0.6,
                        p.posZ + (p.getRNG().nextDouble() - 0.5), 0, -0.1, 0);
                }
                return;
            }
            if (pressed && used < clientAllowed) {
                jump(p);
                return;
            }
            // carpet, reserve ran out: space held - lower slowly (about 2 blocks per second), the fall does not accumulate
            if (down && p.motionY < 0 && clientCarpet > 0 && carpetLeft <= 0) {
                p.motionY = Math.max(p.motionY, GLIDE);
                p.fallDistance = 0;
                selfCarpet = p.ticksExisted;
                return;
            }
            // carpet: jump held, the player no longer rises - hold the height
            boolean sky = skyMaster(p);
            if (down && (p.motionY <= 0 || sky && carpetOn) && clientCarpet != 0 && (clientCarpet == UNLIMITED || carpetLeft > 0)) {
                carpetOn = true;
                p.motionY = sky ? skyLift(p, p.movementInput.moveForward > 0, p.movementInput.sneak) : 0;
                p.fallDistance = 0;
                selfCarpet = p.ticksExisted;
                if (clientCarpet != UNLIMITED) carpetLeft--;
                if (p.ticksExisted % 2 == 0) {
                    p.worldObj.spawnParticle("reddust", p.posX + (p.getRNG().nextDouble() - 0.5), p.boundingBox.minY,
                        p.posZ + (p.getRNG().nextDouble() - 0.5), 0.9, 0.2, 0.1);
                }
            }
        }

        private void jump(EntityClientPlayerMP p) {
            used++;
            p.motionY = 0.48;
            if (p.isPotionActive(Potion.jump)) p.motionY += (p.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F;
            p.fallDistance = 0;
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                p.worldObj.spawnParticle("cloud", p.posX + Math.cos(a) * 0.5, p.boundingBox.minY, p.posZ + Math.sin(a) * 0.5,
                    Math.cos(a) * 0.08, -0.05, Math.sin(a) * 0.08);
            }
            ColdHandler.NET.sendToServer(new Jump());
        }

        /** Carpet or wing reserve bar under the crosshair while it is being spent. */
        @SubscribeEvent
        public void onOverlay(RenderGameOverlayEvent.Post e) {
            // after the whole interface, not after the crosshair: in third person a single gray backdrop was drawn after the crosshair
            if (e.type != RenderGameOverlayEvent.ElementType.ALL || !airborne) return;
            boolean wings = clientWing > 0;
            int max = wings ? clientWing : clientCarpet, left = wings ? wingLeft : carpetLeft;
            if (max <= 0 || left >= max) return;
            ScaledResolution r = e.resolution;
            int x = r.getScaledWidth() / 2 - 20, y = r.getScaledHeight() / 2 + 10;
            int color = left <= max / 4 ? 0xFFE04030 : wings ? 0xFFB060F0 : 0xFFE0B040;
            Gui.drawRect(x - 1, y - 1, x + 41, y + 3, 0x90000000);
            Gui.drawRect(x, y, x + 40 * Math.max(left, 0) / max, y + 2, color);
        }
    }
}
