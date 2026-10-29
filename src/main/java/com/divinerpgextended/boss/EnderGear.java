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
 * Способности полного сета брони Энда (цвета можно смешивать):
 * <ul>
 * <li>Пустотный шаг: двойное W с эндер-мечом в руке (с места или прямо на бегу; бег на клавише бега шага не делает) -
 * перенос на {@link #RANGE} блоков туда, куда смотришь, хоть вверх, на уступы до {@link #LEDGE} блоков. Не сработает, если
 * в точке под тобой нет земли в {@link #GROUND} блоках (в бездну не шагнуть) - причина пишется над хотбаром. Цена
 * {@link #COST} арканы, короткий откат. Двойное W ловит клиент ({@link Client}) и шлёт пакет.</li>
 * <li>Спасение из пустоты: упал ниже дна мира - тебя переносит на последнюю твёрдую землю, {@link #RESCUE_HURT} урона, не
 * чаще раза в {@link #RESCUE_CD} тиков.</li>
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

    /** Сервер -> клиент: почему шаг не вышел (ключ перевода) - строкой над хотбаром (в 1.7.10 у чата такого места нет). */
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

    /** Клиент -> сервер: двойное W (с места или на бегу). Сет, меч, аркану и откат проверяет сервер. */
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
            if (why != null && !why.isEmpty()) com.divinerpgextended.cold.ColdHandler.NET.sendTo(new Fail(why), p); // откат - молча: на бегу двойное W жмут часто
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
        // последняя твёрдая земля
        if (p.onGround && p.ticksExisted % 10 == 0 && p.worldObj.getBlock(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.boundingBox.minY) - 1,
            MathHelper.floor_double(p.posZ)).getMaterial().isSolid()) {
            d.setDouble(SAFE_X, p.posX);
            d.setDouble(SAFE_Y, p.posY);
            d.setDouble(SAFE_Z, p.posZ);
            d.setBoolean("dreSafe", true);
        }
        if (p.posY < -4 && d.getBoolean("dreSafe") && p.worldObj.getTotalWorldTime() >= d.getLong(RESCUE_AT)) rescue(p, d);
    }

    /** Пустотный шаг. @return сработал ли */
    public static boolean blink(EntityPlayer p) {
        return tryBlink(p) == null;
    }

    /**
     * Пустотный шаг: луч взгляда от глаз, ноги идут вдоль него на высоте глаз ниже; упёрлись в уступ до {@link #LEDGE}
     * блоков - поднимаемся на него (взгляд чуть вниз или ступенька не обрывают шаг). Скорость сохраняется - шаг на бегу
     * продолжает бег. @return null - сработал; "" - откат; иначе ключ причины для строки над хотбаром
     */
    public static String tryBlink(EntityPlayer p) {
        World w = p.worldObj;
        // Айрако на пустотном якоре этого стрелка перед глазами - шаг прямо к нему, вплотную
        net.minecraft.entity.EntityLiving prey = AyeracoHorde.anchoredFor(p);
        if (prey != null) {
            double dx = p.posX - prey.posX, dz = p.posZ - prey.posZ, len = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
            double gx = prey.posX + dx / len * (prey.width / 2 + 1.2), gz = prey.posZ + dz / len * (prey.width / 2 + 1.2), gy = prey.posY;
            if (fits(w, gx, gy, gz)) {
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
            if (!fits(w, nx, ny, nz)) {
                double up = -1;
                double base = Math.floor(Math.max(ny, ty)); // от ног, а не от луча: взгляд вниз уводит луч под землю
                for (int k = 0; k <= LEDGE && up < 0; k++) {
                    double c = base + k; // встать на верх блока
                    if (c > ny && c - ty <= LEDGE + 0.01 && fits(w, nx, c, nz)) up = c;
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
        if (!groundBelow(w, tx, ty, tz)) return "dre.blink.void"; // в бездну не шагнуть
        ArcanaHelper h = ArcanaHelper.getProperties(p);
        if (h == null) return "dre.blink.arcana";
        if (!ArcanaRules.useBar(h, COST, "EnderBlink")) return h.getBarValue() < COST * 0.5F ? "dre.blink.arcana" : ""; // мало арканы или откат
        particles(w, x, y, z);
        moveTo(p, tx, ty + (p.posY - p.boundingBox.minY), tz);
        p.fallDistance = 0;
        w.playSoundEffect(tx, ty, tz, "mob.endermen.portal", 1F, 1.3F);
        particles(w, tx, ty, tz);
        return null;
    }

    /** Помещается ли игрок (ноги на y): три блока столба свободны для движения. */
    static boolean fits(World w, double x, double y, double z) {
        return !solidAt(w, x, y, z) && !solidAt(w, x, y + 1, z) && !solidAt(w, x, y + 1.8, z);
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
        p.motionX = p.motionY = p.motionZ = 0;
        moveTo(p, x, y, z);
        p.fallDistance = 0;
        p.attackEntityFrom(DamageSource.magic, RESCUE_HURT);
        p.worldObj.playSoundEffect(x, y, z, "mob.endermen.portal", 1F, 0.7F);
        p.addChatMessage(new ChatComponentTranslation("dre.chat.void_rescue"));
    }

    /** Перенести игрока (с оповещением клиента, если есть соединение). */
    static void moveTo(EntityPlayer p, double x, double y, double z) {
        if (p instanceof EntityPlayerMP && ((EntityPlayerMP) p).playerNetServerHandler != null)
            ((EntityPlayerMP) p).playerNetServerHandler.setPlayerLocation(x, y, z, p.rotationYaw, p.rotationPitch);
        else p.setPosition(x, y, z);
    }

    /** Клиент: двойное нажатие W (второе - не позже {@link #DOUBLE_TAP} тиков после первого) в полном сете с эндер-мечом. */
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public static class Client {

        static final int DOUBLE_TAP = 7;
        private boolean wasDown;
        private int sinceTap = 100;

        /** Строка над хотбаром - место надписи о пластинке. */
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
                    sinceTap = 100; // третье нажатие - снова пара
                } else sinceTap = 0;
            }
            wasDown = down;
        }
    }

    static void particles(World w, double x, double y, double z) {
        if (w instanceof WorldServer) ((WorldServer) w).func_147487_a("portal", x, y + 1, z, 30, 0.4, 0.8, 0.4, 0.5);
    }
}
