package com.divinerpgextended.entity;

import java.util.List;

import net.divinerpg.entities.iceika.EntityFrostArcher;
import net.divinerpg.entities.iceika.EntityRollum;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.block.BlockBrazier;
import com.divinerpgextended.block.BlockFrostIce;
import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.registry.ModBlocks;

/** Общие способности боссов Ледяного Трона (Колосс с Королём-личом и сам Король-лич). */
public final class FrostPowers {

    private FrostPowers() {}

    public static void particles(World w, String name, double x, double y, double z, int n, double spread, double speed) {
        if (w instanceof WorldServer) ((WorldServer) w).func_147487_a(name, x, y, z, n, spread, spread, spread, speed);
    }

    @SuppressWarnings("unchecked")
    public static List<EntityPlayer> players(EntityLiving boss, double r) {
        return boss.worldObj.getEntitiesWithinAABB(EntityPlayer.class, boss.boundingBox.expand(r, r / 2, r));
    }

    public static void tell(EntityLiving boss, String key) {
        for (EntityPlayer p : players(boss, 40)) p.addChatMessage(new ChatComponentTranslation(key));
    }

    /**
     * Воющий буран: конус ледяного ветра (60 градусов, 16 блоков) в сторону цели. Всем в конусе +30% холода и
     * замедление.
     */
    public static void howlingBlast(EntityLiving caster, EntityLivingBase target, double fromY) {
        double dx = target.posX - caster.posX, dz = target.posZ - caster.posZ, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) return;
        dx /= len;
        dz /= len;
        World w = caster.worldObj;
        for (int i = 1; i <= 16; i++) {
            particles(w, "snowshovel", caster.posX + dx * i, fromY - i * 0.15, caster.posZ + dz * i, 6 + i, 0.15 * i,
                0.05);
        }
        w.playSoundAtEntity(caster, "mob.wither.shoot", 2.0F, 0.4F);
        for (EntityPlayer p : players(caster, 18)) {
            double px = p.posX - caster.posX, pz = p.posZ - caster.posZ, d = Math.sqrt(px * px + pz * pz);
            if (d > 16 || d < 0.01 || (px * dx + pz * dz) / d < 0.5) continue; // cos 60 = 0.5
            ColdHandler.addCold(p, 30);
            p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 80, 1));
        }
        tell(caster, "dre.chat.howling_blast");
    }

    /** Ледяная тюрьма: клетка из крепкого льда вокруг игрока, тает сама за 8 секунд. */
    public static void icePrison(EntityPlayer p) {
        World w = p.worldObj;
        int x = MathHelper.floor_double(p.posX), y = MathHelper.floor_double(p.posY), z = MathHelper.floor_double(p.posZ);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 2; dy++) {
            boolean inside = dx == 0 && dz == 0 && (dy == 0 || dy == 1);
            if (!inside) BlockFrostIce.place(w, ModBlocks.frostIce, x + dx, y + dy, z + dz, BlockFrostIce.PRISON);
        }
        w.playSoundAtEntity(p, "random.glass", 1.5F, 0.5F);
        p.addChatMessage(new ChatComponentTranslation("dre.chat.ice_prison"));
    }

    /** Поднимает мертвецов Iceika: лучников и ролумов, не больше max рядом. */
    public static void raiseDead(EntityLiving boss, int count, int max) {
        World w = boss.worldObj;
        AxisAlignedBB box = boss.boundingBox.expand(30, 12, 30);
        int have = w.getEntitiesWithinAABB(EntityFrostArcher.class, box)
            .size()
            + w.getEntitiesWithinAABB(EntityRollum.class, box)
                .size();
        for (int i = 0; i < count && have < max; i++, have++) {
            EntityLiving mob = w.rand.nextInt(3) == 0 ? new EntityRollum(w) : new EntityFrostArcher(w);
            double a = w.rand.nextDouble() * Math.PI * 2, r = 4 + w.rand.nextDouble() * 4;
            mob.setLocationAndAngles(boss.posX + Math.cos(a) * r, boss.posY + 0.5, boss.posZ + Math.sin(a) * r,
                w.rand.nextFloat() * 360, 0);
            if (!w.getCollidingBoundingBoxes(mob, mob.boundingBox)
                .isEmpty()) continue;
            mob.getEntityData()
                .setBoolean(ThroneCombat.MINION_TAG, true);
            w.spawnEntityInWorld(mob);
            particles(w, "spell", mob.posX, mob.posY + 1, mob.posZ, 20, 0.4, 0.05);
        }
        w.playSoundAtEntity(boss, "mob.zombie.unfect", 2.0F, 0.5F);
    }

    /** Гасит ближайшую зажжённую жаровню в радиусе r. */
    public static boolean extinguishBrazier(EntityLiving boss, int r) {
        World w = boss.worldObj;
        int bx = MathHelper.floor_double(boss.posX), by = MathHelper.floor_double(boss.posY),
            bz = MathHelper.floor_double(boss.posZ);
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int x = bx - r; x <= bx + r; x++) for (int z = bz - r; z <= bz + r; z++) for (int y = by - 3; y <= by + 6; y++) {
            if (w.getBlock(x, y, z) != ModBlocks.brazier || !BlockBrazier.isLit(w, x, y, z)) continue;
            double d = (x - bx) * (x - bx) + (z - bz) * (z - bz);
            if (d < bestD) {
                bestD = d;
                best = new int[] { x, y, z };
            }
        }
        if (best == null) return false;
        BlockBrazier.extinguish(w, best[0], best[1], best[2]);
        return true;
    }
}
