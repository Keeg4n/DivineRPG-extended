package com.divinerpgextended.entity;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Глыба земли, которую Древний голем вырывает из острова и швыряет: летит дугой, при падении разбивается - урон и
 * отброс всем в {@link #RADIUS} блоках. Блоков не ставит; мшистые камни (укрытия капища) рядом с местом удара крошит.
 */
public class EntityBoulder extends EntityThrowable {

    public static final double RADIUS = 2.5;
    private float damage = 10;

    public EntityBoulder(World w) {
        super(w);
        setSize(1.0F, 1.0F);
    }

    public EntityBoulder(World w, EntityLivingBase thrower, float damage) {
        super(w, thrower);
        setSize(1.0F, 1.0F);
        this.damage = damage;
    }

    @Override
    protected float getGravityVelocity() {
        return 0.05F;
    }

    @Override
    protected void onImpact(MovingObjectPosition mop) {
        if (worldObj.isRemote) return;
        if (mop.entityHit != null && mop.entityHit == getThrower()) return;
        WorldServer ws = (WorldServer) worldObj;
        int dirt = net.minecraft.block.Block.getIdFromBlock(net.divinerpg.utils.blocks.TwilightBlocks.wildwoodDirt);
        ws.func_147487_a("blockcrack_" + dirt + "_0", posX, posY + 0.5, posZ, 40, 0.8, 0.5, 0.8, 0.2);
        worldObj.playSoundAtEntity(this, "dig.gravel", 2.0F, 0.6F);
        worldObj.playSoundAtEntity(this, "random.explode", 0.6F, 1.4F);
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(RADIUS, RADIUS, RADIUS));
        for (EntityPlayer p : near) {
            if (p.getDistanceToEntity(this) > RADIUS + 0.5) continue;
            p.attackEntityFrom(DamageSource.causeThrownDamage(this, getThrower()), damage);
            double dx = p.posX - posX, dz = p.posZ - posZ, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            p.motionX += dx / d * 0.6;
            p.motionZ += dz / d * 0.6;
            p.motionY += 0.3;
            p.velocityChanged = true;
        }
        // глыба крошит мшистые камни-укрытия капища
        int bx = (int) Math.floor(posX), by = (int) Math.floor(posY), bz = (int) Math.floor(posZ);
        for (int x = bx - 1; x <= bx + 1; x++) for (int y = by - 1; y <= by + 2; y++) for (int z = bz - 1; z <= bz + 1; z++) {
            if (worldObj.getBlock(x, y, z) != net.minecraft.init.Blocks.mossy_cobblestone) continue;
            worldObj.func_147480_a(x, y, z, false); // разрушить без выпадения, с частицами
        }
        setDead();
    }
}
