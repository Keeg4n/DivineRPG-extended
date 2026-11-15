package com.divinerpgextended.magic;

import net.divinerpg.entities.arcana.EntityWraith;
import net.divinerpg.utils.events.ArcanaHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * Ghost from the ghost bone (Ghostbane) - a summon: flies after the owner through blocks, hits with arcane damage what the owner hits
 * (or whoever attacked the owner), takes no damage and is not saved in the world. Stays while the owner has arcana:
 * {@link #UPKEEP} per second for each. Look and sounds - Divine's ghost.
 */
public class EntityArcaneWraith extends EntityWraith {

    public static final float BASE_DAMAGE = 10F, UPKEEP = AnchorSummons.UPKEEP; // like all summons
    public static final int HIT_EVERY = 15, LEASH = 48;
    private static java.lang.reflect.Field age;
    private int cooldown, life;
    private final AnchorSummons.Aim aim = new AnchorSummons.Aim();
    /** The owner directly: in Divine he is looked up by name among the world's players, every tick. */
    private EntityPlayer master;

    public EntityArcaneWraith(World w) {
        super(w);
        noClip = true;
    }

    public EntityArcaneWraith(World w, EntityPlayer owner) {
        super(w, owner);
        master = owner;
        noClip = true;
    }

    public EntityPlayer owner() {
        if (master != null && !master.isDead) return master;
        EntityLivingBase o = func_70902_q();
        return o instanceof EntityPlayer ? (EntityPlayer) o : null;
    }

    @Override
    public void onUpdate() {
        // Divine's duration (6 s) does not apply: it stays while arcana is paid
        try {
            if (age == null) age = cpw.mods.fml.relauncher.ReflectionHelper.findField(EntityWraith.class, "age");
            age.setInt(this, 120);
        } catch (Throwable ignored) {}
        noClip = true;
        setIsBatHanging(false);
        super.onUpdate();
    }

    /** Its own flight: toward the owner's target or around the owner, through blocks. */
    @Override
    protected void updateAITasks() {
        if (worldObj.isRemote) return;
        EntityPlayer o = owner();
        if (o == null || o.isDead || o.dimension != dimension || getDistanceSqToEntity(o) > LEASH * LEASH) {
            setDead();
            return;
        }
        // the owner's first ghost pays - for all at once, as an ordinary arcana spend (with a recovery pause)
        if (++life % 20 == 0 && ArcaneSummons.index(this) == 0 && !AnchorSummons.pay(o, ArcaneSummons.count(o))) {
            ArcaneSummons.dismissAll(o);
            return;
        }
        Entity t = aim.pick(o, ArcaneSummons.target(o), ArcaneSummons.AGGRO);
        double tx, ty, tz, speed;
        if (t != null) {
            tx = t.posX;
            ty = t.posY + t.height * 0.5;
            tz = t.posZ;
            speed = 0.55;
            if (--cooldown <= 0 && getDistanceSqToEntity(t) < 2.2 * 2.2 && t instanceof EntityLivingBase) {
                cooldown = HIT_EVERY;
                ((EntityLivingBase) t).hurtResistantTime = 0;
                t.attackEntityFrom(ArcanaCombat.arcaneSource(o, t), BASE_DAMAGE * ArcanaRules.stage(o));
                swingItem();
            }
        } else {
            int n = ArcaneSummons.index(this);
            double a = life * 0.06 + n * Math.PI;
            tx = o.posX + Math.cos(a) * 1.6;
            ty = o.posY + 1.8;
            tz = o.posZ + Math.sin(a) * 1.6;
            speed = 0.35;
        }
        double dx = tx - posX, dy = ty - posY, dz = tz - posZ, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 24) { // fell behind - next to the owner
            double[] at = AnchorSummons.near(o, ArcaneSummons.index(this));
            setPosition(at[0], at[1] + 1.8, at[2]);
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
        return false; // ghost: do not hurt
    }

    @Override
    public boolean isEntityInsideOpaqueBlock() {
        return false;
    }

    /** Owner right click on the ghost - dismiss (the click does not go to the item in hand: the summon with the same bone is not repeated). */
    @Override
    public boolean interact(EntityPlayer p) {
        if (!worldObj.isRemote && owner() == p) AnchorSummons.dismiss(this);
        return true;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected void collideWithEntity(Entity e) {}

    /** Not saved: the summon lives only with its owner. */
    @Override
    public boolean writeToNBTOptional(NBTTagCompound t) {
        return false;
    }

    @Override
    protected void fall(float d) {}
}
