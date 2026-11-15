package com.divinerpgextended.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.divinerpgextended.item.ItemTomahawk;
import com.divinerpgextended.registry.ModItems;

/**
 * A thrown Tomahawk ({@link ItemTomahawk}). Flies straight for up to {@link #RANGE} blocks; on a target it hits with melee damage and turns to the
 * nearest next one (no farther than {@link #CHAIN} blocks), up to {@link #TARGETS} targets in all; upon hitting a block, reaching its range or running out of targets -
 * returns to the thrower through walls. The first target farther than {@link #STUN_RANGE} blocks from the thrower is stunned for
 * {@link #STUN_TICKS} ticks (slowness X and weakness). Hits players only with PvP enabled, does not hit the thrower's pets.
 * Is not saved: it disappears after a world reload, and the throw marker on the item is removed by time.
 * <p>
 * Vethea discs ({@link com.divinerpgextended.vethea.DiskThrow}) fly the same way: the item and the throw damage are their own, a disc is rendered.
 */
public class EntityTomahawk extends Entity {

    public static final float RANGE = 24F, CHAIN = 8F, STUN_RANGE = 8F, SPEED = 1.3F, BACK_SPEED = 1.5F;
    public static final int TARGETS = 3, STUN_TICKS = 50, LIFE = 200;

    private EntityPlayer owner;
    /** Throw damage to the target. */
    private float damage;
    /** What was thrown (the same is rendered; the client learns it via DataWatcher). */
    static final int ITEM = 10;
    private final Set<Integer> hit = new HashSet<Integer>();
    private boolean back;
    private double flown;
    /** For the selftest: how many targets were hit, whether the first was stunned. */
    public int hits;
    public boolean stunned;

    public EntityTomahawk(World w) {
        super(w);
        setSize(0.5F, 0.5F);
    }

    public EntityTomahawk(World w, EntityPlayer p) {
        this(w, p, new ItemStack(ModItems.tomahawk), ItemTomahawk.throwDamage());
    }

    public EntityTomahawk(World w, EntityPlayer p, ItemStack thrown, float damage) {
        this(w);
        owner = p;
        this.damage = damage;
        ItemStack one = thrown.copy();
        one.stackSize = 1;
        one.setTagCompound(null);
        dataWatcher.updateObject(ITEM, one);
        Vec3 look = p.getLookVec();
        setPosition(p.posX + look.xCoord * 0.8, p.posY + p.getEyeHeight() - 0.2 + look.yCoord * 0.8, p.posZ + look.zCoord * 0.8);
        motionX = look.xCoord * SPEED;
        motionY = look.yCoord * SPEED;
        motionZ = look.zCoord * SPEED;
    }

    public EntityPlayer owner() {
        return owner;
    }

    /** The thrown item (a tomahawk or a disc). */
    public ItemStack item() {
        ItemStack s = dataWatcher.getWatchableObjectItemStack(ITEM);
        return s != null ? s : new ItemStack(ModItems.tomahawk);
    }

    /** A Vethea disc was thrown. */
    public boolean disk() {
        return item().getItem() != ModItems.tomahawk;
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObjectByDataType(ITEM, 5);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) {
            setPosition(posX + motionX, posY + motionY, posZ + motionZ);
            return;
        }
        if (owner == null || owner.isDead || owner.worldObj != worldObj || ticksExisted > LIFE) {
            finish();
            return;
        }
        if (back) {
            Vec3 to = Vec3.createVectorHelper(owner.posX - posX, owner.posY + owner.getEyeHeight() * 0.7 - posY, owner.posZ - posZ);
            double d = to.lengthVector();
            if (d < 1.6) {
                finish();
                return;
            }
            to = to.normalize();
            motionX = to.xCoord * BACK_SPEED;
            motionY = to.yCoord * BACK_SPEED;
            motionZ = to.zCoord * BACK_SPEED;
            setPosition(posX + motionX, posY + motionY, posZ + motionZ);
            return;
        }
        Vec3 from = Vec3.createVectorHelper(posX, posY, posZ), next = Vec3.createVectorHelper(posX + motionX, posY + motionY, posZ + motionZ);
        MovingObjectPosition block = worldObj.rayTraceBlocks(from, next);
        EntityLivingBase target = null;
        double best = Double.MAX_VALUE;
        AxisAlignedBB sweep = boundingBox.addCoord(motionX, motionY, motionZ).expand(0.6, 0.6, 0.6);
        for (Object o : worldObj.getEntitiesWithinAABBExcludingEntity(this, sweep)) {
            if (!(o instanceof EntityLivingBase) || !valid((EntityLivingBase) o)) continue;
            double d = ((Entity) o).getDistanceSqToEntity(this);
            if (d < best) {
                best = d;
                target = (EntityLivingBase) o;
            }
        }
        if (target != null) {
            strike(target);
            return;
        }
        if (block != null && block.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            back = true;
            return;
        }
        setPosition(posX + motionX, posY + motionY, posZ + motionZ);
        flown += Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        if (flown >= RANGE) back = true;
    }

    /** The target is valid: alive, not the thrower, not yet hit, not the thrower's pet or summon, a player only where PvP is allowed. */
    private boolean valid(EntityLivingBase e) {
        if (e == owner || e.isDead || e.getHealth() <= 0 || hit.contains(e.getEntityId())) return false;
        if (e instanceof EntityTameable && ((EntityTameable) e).getOwner() == owner || com.divinerpgextended.magic.AnchorSummons.own(owner, e)) return false;
        if (e instanceof EntityPlayer) return !com.divinerpgextended.perk.Protection.spare(owner, e);
        return true;
    }

    private void strike(EntityLivingBase t) {
        boolean first = hits == 0;
        boolean far = first && owner.getDistanceToEntity(t) > STUN_RANGE;
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causeThrownDamage(this, owner), damage);
        hit.add(t.getEntityId());
        hits++;
        if (far) {
            t.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, STUN_TICKS, 9));
            t.addPotionEffect(new PotionEffect(Potion.weakness.id, STUN_TICKS, 4));
            stunned = true;
        }
        worldObj.playSoundAtEntity(t, "random.break", 0.6F, 1.6F);
        if (hits >= TARGETS) {
            back = true;
            return;
        }
        // to the nearest next target
        EntityLivingBase next = null;
        double best = CHAIN * CHAIN;
        List<?> near = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, t.boundingBox.expand(CHAIN, CHAIN / 2, CHAIN));
        for (Object o : near) {
            EntityLivingBase e = (EntityLivingBase) o;
            if (!valid(e) || !(e instanceof net.minecraft.entity.monster.IMob) && !(e instanceof net.minecraft.entity.EntityLiving && ((net.minecraft.entity.EntityLiving) e).getAttackTarget() == owner))
                continue;
            double d = e.getDistanceSqToEntity(t);
            if (d < best && t.canEntityBeSeen(e)) {
                best = d;
                next = e;
            }
        }
        if (next == null) {
            back = true;
            return;
        }
        Vec3 to = Vec3.createVectorHelper(next.posX - posX, next.posY + next.height / 2 - posY, next.posZ - posZ).normalize();
        motionX = to.xCoord * SPEED;
        motionY = to.yCoord * SPEED;
        motionZ = to.zCoord * SPEED;
        flown = 0;
    }

    /** Returned (or vanished): remove the throw marker from the thrown item on the thrower. */
    private void finish() {
        setDead();
        if (owner == null) return;
        net.minecraft.item.Item it = item().getItem();
        for (ItemStack s : owner.inventory.mainInventory) {
            if (s == null || s.getItem() != it || !s.hasTagCompound() || !s.getTagCompound().hasKey(ItemTomahawk.OUT)) continue;
            ItemTomahawk.setOut(s, 0);
            if (s.getTagCompound().hasNoTags()) s.setTagCompound(null); // the disc stacks with identical ones again
        }
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound t) {
        setDead(); // does not survive a reload
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound t) {}
}
