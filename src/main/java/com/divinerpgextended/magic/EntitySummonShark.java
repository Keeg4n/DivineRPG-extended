package com.divinerpgextended.magic;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * Shark of the Liopleurodon Anchor ({@link AnchorSummons}): flies after the owner through blocks, like the ghost, bites the owner's target
 * for arcane damage, lives on the owner's arcana. Cannot be hurt, is not saved.
 */
public class EntitySummonShark extends EntityLiving implements AnchorSummons.Summon {

    public static final int HIT_EVERY = 15;
    private EntityPlayer master;
    private int cooldown, life;
    private final AnchorSummons.Aim aim = new AnchorSummons.Aim();

    public EntitySummonShark(World w) {
        super(w);
        setSize(0.9F, 0.6F);
        noClip = true;
    }

    public EntitySummonShark(World w, EntityPlayer owner) {
        this(w);
        master = owner;
    }

    public EntityPlayer owner() {
        return master != null && !master.isDead ? master : null;
    }

    @Override
    public AnchorSummons.Kind kind() {
        return AnchorSummons.Kind.SHARK;
    }

    /** The bite went through: the sound of its own kind (for the eye - knockback too). */
    protected void onHit(EntityLivingBase t, EntityPlayer o) {
        worldObj.playSoundAtEntity(this, "random.splash", 0.4F, 1.6F);
    }

    /** A circle without a target: radius and height above the owner. */
    protected double orbitRadius() {
        return 1.8;
    }

    protected double orbitHeight() {
        return 1.6;
    }

    /** Called every tick while there is a target, before moving (the Ayeraco teleports here). */
    protected void chase(Entity t, EntityPlayer o) {}

    /** No gravity and no ground friction: only its own speed. */
    @Override
    public void moveEntityWithHeading(float strafe, float forward) {
        moveEntity(motionX, motionY, motionZ);
        motionX *= 0.85;
        motionY *= 0.85;
        motionZ *= 0.85;
    }

    @Override
    public void onUpdate() {
        noClip = true;
        super.onUpdate();
    }

    @Override
    protected void updateEntityActionState() {
        if (worldObj.isRemote) return;
        EntityPlayer o = owner();
        if (o == null || o.dimension != dimension || getDistanceSqToEntity(o) > AnchorSummons.LEASH * AnchorSummons.LEASH) {
            setDead();
            return;
        }
        // the first in the owner's list pays - for all at once
        if (++life % 20 == 0 && AnchorSummons.index(this, o) == 0 && !AnchorSummons.pay(o, AnchorSummons.of(o).size())) {
            AnchorSummons.dismissAll(o);
            return;
        }
        Entity t = aim.pick(o, AnchorSummons.target(o), kind().aggro);
        double tx, ty, tz, speed;
        if (t != null) {
            chase(t, o);
            tx = t.posX;
            ty = t.posY + t.height * 0.5;
            tz = t.posZ;
            speed = 0.6;
            if (--cooldown <= 0 && getDistanceSqToEntity(t) < 2.2 * 2.2 && t instanceof EntityLivingBase) {
                cooldown = HIT_EVERY;
                ((EntityLivingBase) t).hurtResistantTime = 0;
                if (t.attackEntityFrom(ArcanaCombat.arcaneSource(o, t), AnchorSummons.damage(o, kind()))) onHit((EntityLivingBase) t, o);
                swingItem();
            }
        } else {
            int n = AnchorSummons.index(this, o);
            double a = life * 0.07 + n * Math.PI;
            tx = o.posX + Math.cos(a) * orbitRadius();
            ty = o.posY + orbitHeight();
            tz = o.posZ + Math.sin(a) * orbitRadius();
            speed = 0.4;
        }
        double dx = tx - posX, dy = ty - posY, dz = tz - posZ, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 24) {
            double[] at = AnchorSummons.near(o, AnchorSummons.index(this, o));
            setPosition(at[0], at[1] + orbitHeight(), at[2]);
            return;
        }
        double v = Math.min(speed, len * 0.35);
        if (len > 0.01) {
            motionX = dx / len * v;
            motionY = dy / len * v;
            motionZ = dz / len * v;
            rotationYaw = renderYawOffset = rotationYawHead = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource s, float a) {
        if (s == DamageSource.outOfWorld) {
            setDead();
            return true;
        }
        if (AnchorSummons.rivalHit(owner(), s)) return super.attackEntityFrom(s, a); // another player, where PvP is allowed
        return false;
    }

    @Override
    public boolean isEntityInsideOpaqueBlock() {
        return false;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound t) {
        return false;
    }

    @Override
    protected void collideWithEntity(Entity e) {}

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected void fall(float d) {}

    /** Owner right click - dismiss; the click does not go to the item in hand (the anchor does not summon a new one). */
    @Override
    public boolean interact(EntityPlayer p) {
        if (!worldObj.isRemote && owner() == p) AnchorSummons.dismiss(this);
        return true;
    }
}
