package com.divinerpgextended.entity.mini;

import net.divinerpg.utils.items.IceikaItems;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.boss.BossMinions;
import com.divinerpgextended.cold.Blizzard;
import com.divinerpgextended.cold.ColdHandler;

/**
 * Вьюжная ель, Айсика: приходит с метелью, раз за метель, и уходит вместе с ней, без добычи. Швыряет снежные комья, каждый добавляет холод, и зовёт
 * снеговиков. Как все холодные мобы боится адского оружия ({@link com.divinerpgextended.perk.ColdMobs}).
 */
public class EntityBlizzardSpruce extends EntityMiniBoss {

    public static final double HEALTH = 300; // морозные мобы Айсики до 180
    public static final int THROW_EVERY = 60, CALL_EVERY = 300, MAX_SNOWMEN = 3;
    public static final float CLUMP_DAMAGE = 6F, CLUMP_COLD = 6F;
    private int throwTimer = THROW_EVERY, callTimer = CALL_EVERY / 3;

    public EntityBlizzardSpruce(World w) {
        super(w, 1.6F, 5.0F, false);
    }

    @Override
    public String key() {
        return "blizzard_spruce";
    }

    @Override
    protected double baseHealth() {
        return HEALTH;
    }

    @Override
    protected double baseDamage() {
        return 14;
    }

    @Override
    protected double baseSpeed() {
        return 0.2D;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0D);
    }

    @Override
    public void loot(int looting) {
        entityDropItem(new ItemStack(IceikaItems.snowflake, 6 + rand.nextInt(5 + looting)), 0.5F);
        entityDropItem(new ItemStack(IceikaItems.iceShards, 4 + rand.nextInt(5)), 0.5F);
        entityDropItem(new ItemStack(IceikaItems.iceStone, 1 + rand.nextInt(2)), 0.5F);
    }

    /** Три кома веером. */
    public int throwClumps(EntityPlayer t) {
        for (int i = -1; i <= 1; i++) {
            EntitySnowClump c = new EntitySnowClump(worldObj, this, CLUMP_DAMAGE, CLUMP_COLD);
            double dx = t.posX - posX, dy = t.posY + 1 - c.posY, dz = t.posZ - posZ, far = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            c.setThrowableHeading(dx - dz / far * i * 2, dy + far * 0.15, dz + dx / far * i * 2, 1.1F, 2F);
            worldObj.spawnEntityInWorld(c);
        }
        worldObj.playSoundAtEntity(this, "random.bow", 1.5F, 0.5F);
        return 3;
    }

    /** Метели нет: уйти без добычи. true - ушла. */
    public boolean leaveWithStorm() {
        if (!ColdHandler.isIceika(worldObj) || Blizzard.get(worldObj).phase == Blizzard.STORM) return false;
        if (worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("snowshovel", posX, posY + height / 2, posZ, 60, 1.0, 2.0, 1.0, 0.1);
        tell("dre.chat.mini.blizzard_spruce_gone");
        setDead();
        return true;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote || leaveWithStorm()) return;
        EntityPlayer t = targetPlayer();
        if (t == null) return;
        if (--throwTimer <= 0 && getDistanceToEntity(t) <= 24) {
            throwTimer = THROW_EVERY;
            throwClumps(t);
        }
        if (--callTimer <= 0) {
            callTimer = CALL_EVERY;
            if (BossMinions.count(this) < MAX_SNOWMEN) call(EntityBlizzardSnowman.class, 1);
        }
    }
}
