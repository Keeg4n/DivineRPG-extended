package com.divinerpgextended.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
 * <li>Пустотный шаг: начал бег (двойное W или клавиша бега) с эндер-мечом в руке - перенос на {@link #RANGE} блоков туда,
 * куда смотришь, хоть вверх. Не сработает, если в точке под тобой нет земли в {@link #GROUND} блоках (в бездну не шагнуть).
 * Цена {@link #COST} арканы, откат как у оружия. Начало бега сервер видит сам - отдельной кнопки и пакета нет.</li>
 * <li>Спасение из пустоты: упал ниже дна мира - тебя переносит на последнюю твёрдую землю, {@link #RESCUE_HURT} урона, не
 * чаще раза в {@link #RESCUE_CD} тиков.</li>
 * </ul>
 */
public class EnderGear {

    public static final double RANGE = 9;
    public static final int GROUND = 32, RESCUE_CD = 1200;
    public static final float COST = 25F, RESCUE_HURT = 6F;
    static final String SAFE_X = "dreSafeX", SAFE_Y = "dreSafeY", SAFE_Z = "dreSafeZ", RESCUE_AT = "dreRescueAt";
    private static final Map<UUID, Boolean> SPRINT = new HashMap<UUID, Boolean>();

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote) return;
        boolean full = EnderColors.fullSet(p);
        Boolean was = SPRINT.put(p.getUniqueID(), p.isSprinting());
        if (full && p.isSprinting() && was != null && !was && EnderColors.sword(p.getHeldItem())) blink(p);
        if (!full) return;
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
        World w = p.worldObj;
        Vec3 look = p.getLookVec();
        double x = p.posX, y = p.posY, z = p.posZ;
        double tx = x, ty = y, tz = z;
        for (double s = 0.5; s <= RANGE; s += 0.5) {
            double nx = x + look.xCoord * s, ny = y + look.yCoord * s, nz = z + look.zCoord * s;
            if (solidAt(w, nx, ny, nz) || solidAt(w, nx, ny + 1, nz)) break;
            tx = nx;
            ty = ny;
            tz = nz;
        }
        if ((tx - x) * (tx - x) + (ty - y) * (ty - y) + (tz - z) * (tz - z) < 2 * 2) return false;
        if (!groundBelow(w, tx, ty, tz)) return false; // в бездну не шагнуть
        ArcanaHelper h = ArcanaHelper.getProperties(p);
        if (h == null || !ArcanaRules.useBar(h, COST, "EnderBlink")) return false;
        particles(w, x, y, z);
        moveTo(p, tx, ty, tz);
        p.fallDistance = 0;
        w.playSoundEffect(tx, ty, tz, "mob.endermen.portal", 1F, 1.3F);
        particles(w, tx, ty, tz);
        return true;
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

    static void particles(World w, double x, double y, double z) {
        if (w instanceof WorldServer) ((WorldServer) w).func_147487_a("portal", x, y + 1, z, 30, 0.4, 0.8, 0.4, 0.5);
    }
}
