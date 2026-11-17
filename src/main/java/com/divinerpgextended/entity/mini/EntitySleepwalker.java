package com.divinerpgextended.entity.mini;

import java.util.EnumSet;

import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.vethea.WeaponKind;

/**
 * Sleepwalker, Vethea floor 3 (floors 1-2 are too early for a mini-boss): the vulnerable weapon type changes every {@link #SHIFT} ticks, the particle color hints which (red - melee,
 * green - ranged, purple - arcana). The rules are the same as for Vethea's special mobs: x2 to its own type, x0.25 to the rest, when finished off with its own
 * type it drops loot twice ({@link com.divinerpgextended.vethea.VetheaCombat}).
 */
public class EntitySleepwalker extends EntityMiniBoss {

    public static final int SHIFT = 400;
    private static final int DW_GROUP = 20;
    private static final double[] HEALTH = { 200, 300, 400 }, DAMAGE = { 8, 12, 16 };
    private static final double[][] COLOR = { { 1.0, 0.2, 0.2 }, { 0.2, 1.0, 0.3 }, { 0.8, 0.3, 1.0 } };
    private int floor = 1, shiftTimer = SHIFT;

    public EntitySleepwalker(World w) {
        super(w, 1.2F, 2.8F, false);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_GROUP, Byte.valueOf((byte) 0));
    }

    @Override
    public String key() {
        return "sleepwalker";
    }

    @Override
    protected double baseHealth() {
        return HEALTH[0];
    }

    @Override
    protected double baseDamage() {
        return DAMAGE[0];
    }

    /** Floor 1-3 sets the strength and loot. */
    public void setFloor(int f) {
        floor = Math.max(1, Math.min(3, f));
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HEALTH[floor - 1]);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(DAMAGE[floor - 1]);
        setHealth(getMaxHealth());
    }

    public int group() {
        return dataWatcher.getWatchableObjectByte(DW_GROUP);
    }

    /** Current vulnerability: a group of Vethea helmet roles. */
    public EnumSet<WeaponKind> weak() {
        EnumSet<WeaponKind> set = EnumSet.noneOf(WeaponKind.class);
        for (WeaponKind k : WeaponKind.values()) if (k.group() == group()) set.add(k);
        return set;
    }

    /** Switch the vulnerability to the next one. */
    public void shift() {
        int next = (group() + 1) % 3;
        dataWatcher.updateObject(DW_GROUP, Byte.valueOf((byte) next));
        worldObj.playSoundAtEntity(this, "mob.endermen.portal", 1.0F, 0.7F);
        tell("dre.chat.mini.sleepwalker_weak." + next);
    }

    /** Polished (red) and Shiny (purple) pearls: an ordinary mob of floors 3-4 gives one; Looting - a chance of one more shiny. */
    public static final int POLISHED_MIN = 4, POLISHED_MAX = 6, SHINY_MIN = 1, SHINY_MAX = 2;

    @Override
    public void loot(int looting) {
        entityDropItem(new ItemStack(VetheaItems.polishedPearls, POLISHED_MIN + rand.nextInt(POLISHED_MAX - POLISHED_MIN + 1)), 0.5F);
        int shiny = SHINY_MIN + rand.nextInt(SHINY_MAX - SHINY_MIN + 1) + (rand.nextInt(4) < looting ? 1 : 0);
        entityDropItem(new ItemStack(VetheaItems.shinyPearls, shiny), 0.5F);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) return;
        if (ticksExisted % 6 == 0 && worldObj instanceof WorldServer) {
            double[] c = COLOR[group()];
            for (int i = 0; i < 4; i++)
                ((WorldServer) worldObj).func_147487_a("reddust", posX + (rand.nextDouble() - 0.5) * width, posY + rand.nextDouble() * height, posZ + (rand.nextDouble() - 0.5) * width, 0, c[0], c[1], c[2], 1);
        }
        if (targetPlayer() != null && --shiftTimer <= 0) {
            shiftTimer = SHIFT;
            shift();
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("Floor", floor);
        tag.setByte("WeakGroup", (byte) group());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        floor = Math.max(1, Math.min(3, tag.getInteger("Floor")));
        dataWatcher.updateObject(DW_GROUP, Byte.valueOf(tag.getByte("WeakGroup")));
    }
}
