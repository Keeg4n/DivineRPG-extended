package com.divinerpgextended.magic;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * Краб крабового якоря ({@link AnchorSummons}): бегает по земле за хозяином (отстал на 16 блоков - появляется рядом), бьёт
 * клешнями цель хозяина арканным уроном, держится на аркане хозяина. Не ранится, не сохраняется.
 */
public class EntitySummonCrab extends EntityCreature implements AnchorSummons.Summon {

    public static final int HIT_EVERY = 15;
    private EntityPlayer master;
    private int cooldown, life;
    private final AnchorSummons.Aim aim = new AnchorSummons.Aim();

    public EntitySummonCrab(World w) {
        super(w);
        setSize(0.9F, 0.6F);
    }

    public EntitySummonCrab(World w, EntityPlayer owner) {
        this(w);
        master = owner;
    }

    public EntityPlayer owner() {
        return master != null && !master.isDead ? master : null;
    }

    @Override
    public AnchorSummons.Kind kind() {
        return AnchorSummons.Kind.CRAB;
    }

    /** Удар по цели (урон уже прошёл): звук своего вида. */
    protected void onHit(EntityLivingBase t, EntityPlayer o) {
        worldObj.playSoundAtEntity(this, "mob.silverfish.hit", 0.5F, 0.6F);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.35D);
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20D);
    }

    @Override
    protected boolean isAIEnabled() {
        return true; // навигатор пути работает только с новым ИИ
    }

    @Override
    protected void updateAITasks() {
        super.updateAITasks();
        if (worldObj.isRemote) return;
        EntityPlayer o = owner();
        if (o == null || o.dimension != dimension || getDistanceSqToEntity(o) > AnchorSummons.LEASH * AnchorSummons.LEASH) {
            setDead();
            return;
        }
        if (++life % 20 == 0 && !AnchorSummons.upkeep(o, kind().upkeep)) {
            AnchorSummons.dismissAll(o);
            return;
        }
        Entity t = aim.pick(o, AnchorSummons.target(o), kind().aggro);
        if (t != null) {
            getNavigator().tryMoveToEntityLiving(t, 1.3D);
            if (--cooldown <= 0 && getDistanceSqToEntity(t) < 2.0 * 2.0 && t instanceof EntityLivingBase) {
                cooldown = HIT_EVERY;
                ((EntityLivingBase) t).hurtResistantTime = 0;
                if (t.attackEntityFrom(ArcanaCombat.arcaneSource(o), AnchorSummons.damage(o, kind()))) onHit((EntityLivingBase) t, o);
                swingItem();
            }
            faceEntity(t, 30F, 30F);
        } else if (getDistanceSqToEntity(o) > 16 * 16) {
            setPosition(o.posX, o.posY, o.posZ);
        } else if (getDistanceSqToEntity(o) > 3 * 3) {
            getNavigator().tryMoveToEntityLiving(o, 1.1D);
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource s, float a) {
        if (s == DamageSource.outOfWorld) {
            setDead();
            return true;
        }
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
}
