package com.divinerpgextended.entity.mini;

import java.util.UUID;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Плакальщик, Мортум: каждая смерть моба в {@link #REACH} блоках его усиливает и лечит, до {@link #MAX_GRIEF} раз. Сам зовёт свиту,
 * поэтому её выгодно увести и убить подальше от него.
 */
public class EntityMourner extends EntityMiniBoss {

    public static final double HEALTH = 400; // демон Мортума 200 x2
    public static final double REACH = 16, DAMAGE_PER_GRIEF = 0.1;
    public static final int MAX_GRIEF = 5, CALL_EVERY = 400;
    public static final float HEAL_PER_GRIEF = 0.05F;
    private static final UUID GRIEF_ID = UUID.fromString("7e2a1c44-5b3d-4e0a-9f16-3c8d2b7a6e10");
    private int grief, callTimer = CALL_EVERY;

    public EntityMourner(World w) {
        super(w, 1.2F, 3.0F, false);
    }

    @Override
    public String key() {
        return "mourner";
    }

    @Override
    protected double baseHealth() {
        return HEALTH;
    }

    @Override
    protected double baseDamage() {
        return 17;
    }

    @Override
    public void loot(int looting) {
        twilightLoot(TwilightWorld.MORTUM, looting);
    }

    public int grief() {
        return grief;
    }

    /** Рядом кто-то умер. */
    public void feed() {
        if (!isEntityAlive() || grief >= MAX_GRIEF) return;
        grief++;
        applyGrief();
        heal(getMaxHealth() * HEAL_PER_GRIEF);
        worldObj.playSoundAtEntity(this, "mob.ghast.moan", 1.5F, 0.6F);
        if (worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("witchMagic", posX, posY + height / 2, posZ, 25, 0.5, 1.0, 0.5, 0.1);
    }

    private void applyGrief() {
        IAttributeInstance a = getEntityAttribute(SharedMonsterAttributes.attackDamage);
        AttributeModifier cur = a.getModifier(GRIEF_ID);
        if (cur != null) a.removeModifier(cur);
        if (grief > 0) a.applyModifier(new AttributeModifier(GRIEF_ID, "dre.grief", grief * DAMAGE_PER_GRIEF, 1).setSaved(false));
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote || targetPlayer() == null || --callTimer > 0) return;
        callTimer = CALL_EVERY;
        call(net.divinerpg.entities.twilight.EntityMortumCadillion.class, 2);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("Grief", grief);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        grief = tag.getInteger("Grief");
        applyGrief();
    }
}
