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
 * Движение в воздухе от аксессуаров. Движение игрока считает клиент, поэтому прыгает и парит он сам; сервер сверяет,
 * что вещь надета и запас не исчерпан, и только тогда снимает наказания (высоту падения, кик за полёт). Что доступно,
 * сервер сообщает клиенту (при изменении и раз в 5 секунд).
 * <ul>
 * <li>Крыло Айрако: прыжок в воздухе, в Сумеречных мирах - два.</li>
 * <li>Ковёр-самолёт (только в Сумеречных мирах): прыжок зажат в воздухе - высота держится, летишь по горизонтали.
 * Запас по миру: Эдем и Вайлдвуд 10 с, Апалачия 8, Скайтерн 5, Мортум 3; без предела после Сумеречного демона;
 * копится только на земле. Кончился - пока пробел зажат, ковёр медленно опускает (планирование) без урона. Под ногами
 * виден ковёр (и другим игрокам). Высота падения считается от ковра.</li>
 * </ul>
 */
public class DoubleJump {

    /** Ковёр без предела по времени. */
    public static final int UNLIMITED = -1;
    /** Скорость планирования на ковре после конца запаса (блоков за тик вниз). */
    static final double GLIDE = -0.1;

    private static class State {

        /** Прыжков с последнего касания земли. */
        int used;
        /** Сколько тиков ковра и крыльев сна осталось до земли. */
        int carpetLeft, wingLeft;
        /** Что ушло клиенту в последний раз. */
        int sentJumps = -2, sentCarpet = -2, sentWing = -2;
        /** Для отладки: тиков подряд парит на ковре, тиков взлёта на крыльях. */
        int hoverRun, wingRun;
        double lastY;
        /** Ковёр под ногами показан игрокам рядом. */
        boolean carpetShown;
    }

    private static final Map<UUID, State> STATE = new HashMap<UUID, State>();
    /** Клиент: прыжков в воздухе, тиков ковра и крыльев сна (присылает сервер). */
    static int clientAllowed, clientCarpet, clientWing;
    /** Запас взлёта крыльев сна по ступеням (тиков; скорость подъёма до 0.6 блока за тик - хватает на этаж в 48). */
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

    /** Запас ковра в тиках: 0 - не работает, {@link #UNLIMITED} - без предела. */
    public static int carpetTicks(EntityPlayer p) {
        if (!Accessories.wears(p, Trinket.MAGIC_CARPET) || p.dimension == ConfigurationHelper.vethea) return 0;
        if (!p.worldObj.isRemote && com.divinerpgextended.boss.ArenaCalm.carpetBlocked(p)) return 0; // в бою с боссом ковёр не летает
        boolean demon = BossProgress.has(p, Boss.TWILIGHT_DEMON);
        // вне сумеречных миров ковёр еле держит (3 с), пока не пройдены сумеречные миры (Демон сумерек) - тогда 10 с
        if (!twilight(p.dimension)) return demon ? OUTSIDE_FREE : OUTSIDE;
        if (demon) return UNLIMITED;
        // по миру: в первых трёх пропасти между островами - там ковёр нужнее всего; в Скайтерне важнее высота,
        // в Мортуме пропастей почти нет
        int d = p.dimension;
        int t = d == ConfigurationHelper.eden || d == ConfigurationHelper.wildwood ? 200 : d == ConfigurationHelper.apalachia ? 160
            : d == ConfigurationHelper.skythern ? 100 : 60;
        return skyMaster(p) ? t * 2 : t; // Хозяин ветров: запас вдвое
    }

    /** Полный сет Скайтерна (Хозяин ветров): ковёр летает вверх-вниз по взгляду. */
    public static boolean skyMaster(EntityPlayer p) {
        return com.divinerpgextended.perk.HomeArmor.wears(p, com.divinerpgextended.world.twilight.TwilightWorld.SKYTHERN, false);
    }

    /** Вертикальная скорость ковра Хозяина ветров: взгляд вверх-вниз при движении вперёд, присед - вниз. */
    public static double skyLift(EntityPlayer p, boolean forward, boolean sneak) {
        if (sneak) return -SKY_SPEED;
        if (!forward || Math.abs(p.rotationPitch) < 15) return 0;
        return Math.max(-SKY_SPEED, Math.min(SKY_SPEED, -p.rotationPitch / 60.0 * SKY_SPEED));
    }

    static final double SKY_SPEED = 0.3;

    /** Ковёр вне сумеречных миров (тиков): до победы над Демоном сумерек и после. */
    public static final int OUTSIDE = 60, OUTSIDE_FREE = 200;

    /** Запас взлёта крыльев сна, тиков: в Ветее всегда, в остальных мирах - когда Ветея пройдена (Леди Луна). */
    public static int wingTicks(EntityPlayer p) {
        if (p.dimension != ConfigurationHelper.vethea && !com.divinerpgextended.vethea.VetheaFloors.dreamDone(p)) return 0;
        return WING_TICKS[com.divinerpgextended.vethea.VetheaFloors.wingTier(p)];
    }

    /** Касается опоры: прыжки, ковёр и крылья восстанавливаются. */
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
        // клиент шлёт пакет движения каждый тик, сервер в воздухе считает их в счётчике полёта (мы его обнуляем каждый
        // тик парения). Пакетов с прошлого тика не было - клиент завис (фриз): запас ковра и крыльев не тратим
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
            // взлёт на крыльях сна: не запрещённый полёт
            rising = true;
            if (moved) s.wingLeft = Math.min(s.wingLeft, wing) - 1;
            ReflectionHelper.setPrivateValue(NetHandlerPlayServer.class, p.playerNetServerHandler, 0, "floatingTickCount",
                "field_147365_f");
            if (s.wingLeft == 0) com.divinerpgextended.debug.Debug.note(p, "Крылья сна: запас взлёта кончился на y " + (int) p.posY);
        } else if (carpet != 0 && (carpet == UNLIMITED || s.carpetLeft > 0)
            && (skyMaster(p) ? dy < SKY_SPEED + 0.05 && dy > -SKY_SPEED - 0.05 : dy < 0.05 && dy > -0.1)) {
            // парит на ковре: сервер не должен принимать это за запрещённый полёт; высота падения считается от ковра,
            // а не от места, откуда начал падать до него
            hovering = true;
            if (carpet != UNLIMITED && moved) s.carpetLeft = Math.min(s.carpetLeft, carpet) - 1;
            p.fallDistance = 0;
            ReflectionHelper.setPrivateValue(NetHandlerPlayServer.class, p.playerNetServerHandler, 0, "floatingTickCount",
                "field_147365_f");
            if (p.ticksExisted % 4 == 0) {
                ((WorldServer) p.worldObj).func_147487_a("cloud", p.posX, p.boundingBox.minY, p.posZ, 2, 0.3, 0.02, 0.3, 0.01);
            }
            if (s.carpetLeft == 0 && carpet != UNLIMITED) com.divinerpgextended.debug.Debug.note(p, "Ковёр: запас кончился, дальше планирование");
        } else if (carpet != 0 && dy <= -0.03 && dy >= -0.2) {
            // ковёр медленно опускает (около 2 блоков в секунду) - падение не копится. Остаток запаса не проверяем:
            // после фризов клиент мог истратить запас раньше сервера, и планирование тогда не засчитывалось бы
            gliding = true;
            p.fallDistance = 0;
        }
        // ковёр под ногами видят все рядом: при смене состояния и раз в секунду, пока парит (для подошедших позже)
        boolean onCarpet = hovering && s.hoverRun >= 1 || gliding;
        if (onCarpet != s.carpetShown || onCarpet && p.ticksExisted % 20 == 0) {
            s.carpetShown = onCarpet;
            ColdHandler.NET.sendToAllAround(new CarpetState(p.getEntityId(), onCarpet),
                new cpw.mods.fml.common.network.NetworkRegistry.TargetPoint(p.dimension, p.posX, p.posY, p.posZ, 96));
        }
        // отладка: начало парения (5 тиков подряд, чтобы не путать с верхней точкой прыжка) и взлёта
        s.hoverRun = hovering ? s.hoverRun + 1 : 0;
        s.wingRun = rising ? s.wingRun + 1 : 0;
        if (s.hoverRun == 5) com.divinerpgextended.debug.Debug.note(p, "Ковёр: парение, запас "
            + (carpet == UNLIMITED ? "без предела" : s.carpetLeft / 20.0 + " с из " + carpet / 20.0));
        if (s.wingRun == 3) com.divinerpgextended.debug.Debug.note(p, "Крылья сна: взлёт с y " + (int) p.posY + ", запас " + (s.wingLeft + 3) + " тиков");
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

    // ---------------- Сообщения ----------------

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

    /** Кто сейчас стоит на ковре (для отрисовки ковра под ногами у других игроков). */
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

    /** Клиент: id игроков на ковре -> когда пришло (без обновления 2 с - ковра нет). */
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
                com.divinerpgextended.debug.Debug.note(p, "Прыжок в воздухе отклонён сервером: " + s.used + "/" + allowed(p));
                return null; // крыла нет или прыжки кончились - падение считается как обычно
            }
            s.used++;
            com.divinerpgextended.debug.Debug.note(p, "Крыло Айрако: прыжок в воздухе " + s.used + "/" + allowed(p));
            p.fallDistance = 0;
            p.worldObj.playSoundAtEntity(p, "mob.enderdragon.wings", 0.35F, 1.6F);
            return null;
        }
    }

    // ---------------- Клиент ----------------

    @SideOnly(Side.CLIENT)
    public static class Client {

        private int used, carpetLeft, wingLeft;
        private boolean wasDown, airborne;
        /** В этом полёте уже встал на ковёр (для Хозяина ветров: дальше ковёр может и подниматься). */
        private boolean carpetOn;
        /** Кто сейчас стоит на ковре (по последнему кадру) - им не перебирать ногами. */
        private final java.util.Set<Integer> CARPET_NOW = new java.util.HashSet<Integer>();

        /** На ковре стоят, а не бегут: гасим размах ног перед отрисовкой игрока. */
        @SubscribeEvent
        public void onRenderPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
            if (!CARPET_NOW.contains(e.entityPlayer.getEntityId())) return;
            e.entityPlayer.limbSwingAmount = 0;
            e.entityPlayer.prevLimbSwingAmount = 0;
        }

        /** Тик, когда свой игрок последний раз держался на ковре (свой ковёр рисуем сразу, не ждём сервер). */
        private int selfCarpet = -100;

        /**
         * Ковёр под ногами: красная шерсть с золотой каймой, повёрнут по корпусу игрока и чуть колышется. Свой - по
         * своему парению, чужие - по сообщениям сервера.
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
                    // после смены мира или перезахода тики игрока идут заново с нуля - старая отметка в будущем не считается
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
            // полотно 1.0 x 1.5 (длинной стороной вдоль взгляда), разбито на полосы - по ним бежит волна
            double w = 0.5, l = 0.75, h = 0.05, b = 0.08;
            int n = 6;
            for (int i = 0; i < n; i++) {
                double z0 = -l + 2 * l * i / n, z1 = -l + 2 * l * (i + 1) / n;
                double y0 = Math.sin(wave + i * 0.9) * 0.03, y1 = Math.sin(wave + (i + 1) * 0.9) * 0.03;
                strip(t, red, -w + b, w - b, z0, z1, y0, y1, h);
                strip(t, gold, -w, -w + b, z0, z1, y0, y1, h);
                strip(t, gold, w - b, w, z0, z1, y0, y1, h);
            }
            // золотые торцы спереди и сзади
            double ye = Math.sin(wave) * 0.03, yf = Math.sin(wave + n * 0.9) * 0.03;
            strip(t, gold, -w, w, -l - b, -l, ye, ye, h);
            strip(t, gold, -w, w, l, l + b, yf, yf, h);
            t.draw();
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GL11.glPopMatrix();
        }

        /** Полоса ковра: верх, низ и боковые грани толщиной h; y0/y1 - высота на ближнем и дальнем краю. */
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
            // запас ковра приходит с сервера раз в полсекунды - в Ветее ковра нет
            if (p.dimension == ConfigurationHelper.vethea) clientCarpet = 0;
            boolean down = mc.gameSettings.keyBindJump.getIsKeyPressed();
            // обычный прыжок с земли отрывает игрока в тот же тик, что и нажатие - прыжком в воздухе считается
            // только нажатие, когда игрок был в воздухе уже на прошлом тике
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
            // в творческом двойной пробел включает полёт - не мешаем ему
            if (mc.currentScreen != null || p.capabilities.allowFlying) return;
            // крылья сна: прыжок зажат - тянет вверх, пока есть запас; зависнуть нельзя
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
            // ковёр, запас кончился: пробел зажат - медленно опускаем (около 2 блоков в секунду), падение не копится
            if (down && p.motionY < 0 && clientCarpet > 0 && carpetLeft <= 0) {
                p.motionY = Math.max(p.motionY, GLIDE);
                p.fallDistance = 0;
                selfCarpet = p.ticksExisted;
                return;
            }
            // ковёр: прыжок зажат, игрок уже не поднимается - держим высоту
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

        /** Полоска запаса ковра или крыльев под прицелом, пока он тратится. */
        @SubscribeEvent
        public void onOverlay(RenderGameOverlayEvent.Post e) {
            if (e.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS || !airborne) return;
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
