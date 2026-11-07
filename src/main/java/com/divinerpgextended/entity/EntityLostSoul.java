package com.divinerpgextended.entity;

import java.util.List;

import net.divinerpg.utils.items.TwilightItemsOther;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.boss.BossMinions;
import com.divinerpgextended.perk.HomeArmor;
import com.divinerpgextended.world.twilight.MortumAfterlife;
import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Душа убитого в Мортуме моба ({@link MortumAfterlife}): висит над телом {@link #RISE} тиков. Ударил - рассеялась и оставила
 * душу мира; полный сет Мортума забирает её сам и лечит. Не успел - моб встаёт снова, слабее и быстрее, уже без добычи.
 */
public class EntityLostSoul extends Entity {

    public static final int RISE = 60;
    public static final float RISE_HEALTH = 0.4F, MORTUM_HEAL = 2F;
    private static final double MORTUM_REACH = 4;

    private String mob = "";
    private int age;

    public EntityLostSoul(World w) {
        super(w);
        setSize(0.6F, 0.9F);
        isImmuneToFire = true;
    }

    public EntityLostSoul(World w, EntityLiving dead) {
        this(w);
        mob = EntityList.getEntityString(dead);
        setPosition(dead.posX, dead.posY + 0.5, dead.posZ);
    }

    @Override
    protected void entityInit() {}

    @Override
    public boolean canBeCollidedWith() {
        return !isDead;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) {
            if (ticksExisted % 2 == 0) worldObj.spawnParticle("witchMagic", posX + (rand.nextDouble() - 0.5) * 0.6, posY + rand.nextDouble() * 0.8, posZ + (rand.nextDouble() - 0.5) * 0.6, 0, 0.05, 0);
            return;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(MORTUM_REACH, MORTUM_REACH, MORTUM_REACH));
        for (EntityPlayer p : near) {
            if (!HomeArmor.wears(p, TwilightWorld.MORTUM, false)) continue;
            p.heal(MORTUM_HEAL);
            banish();
            return;
        }
        if (++age >= RISE) rise();
    }

    @Override
    public boolean attackEntityFrom(DamageSource s, float amount) {
        if (worldObj.isRemote || isDead || !(s.getEntity() instanceof EntityPlayer)) return false;
        banish();
        return true;
    }

    /** Рассеять: душа мира остаётся на земле. */
    private void banish() {
        entityDropItem(new ItemStack(TwilightItemsOther.mortumSoul), 0.2F);
        worldObj.playSoundAtEntity(this, "mob.ghast.death", 0.6F, 1.6F);
        if (worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("witchMagic", posX, posY + 0.4, posZ, 20, 0.3, 0.3, 0.3, 0.1);
        setDead();
    }

    private void rise() {
        Entity e = EntityList.createEntityByName(mob, worldObj);
        if (e instanceof EntityLiving) {
            EntityLiving m = (EntityLiving) e;
            m.setLocationAndAngles(posX, posY - 0.5, posZ, rotationYaw, 0);
            m.getEntityData().setBoolean(MortumAfterlife.RISEN, true);
            BossMinions.noXp(m);
            m.setHealth(m.getMaxHealth() * RISE_HEALTH);
            m.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 1200, 0));
            worldObj.spawnEntityInWorld(m);
            worldObj.playSoundAtEntity(m, "mob.wither.spawn", 0.5F, 1.5F);
        }
        setDead();
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        mob = tag.getString("Mob");
        age = tag.getInteger("Age");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setString("Mob", mob);
        tag.setInteger("Age", age);
    }
}
