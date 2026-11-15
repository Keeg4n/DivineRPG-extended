package com.divinerpgextended.entity.mini;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.entity.EntityRockfall;
import com.divinerpgextended.perk.MeleeRoles;
import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Stonebreaker, Apalachia: stone skin holds {@link #SKIN_GUARD} damage. It is stripped for {@link #BARE} ticks by a hit from a maul or mace and by a boulder
 * that fell on him himself: he calls boulders onto the target himself, so it pays to stand next to him and step away in time.
 */
public class EntityStonebreaker extends EntityMiniBoss {

    public static final double HEALTH = 300; // Apalachia golem 250 x1.2, guardian 500
    public static final float SKIN_GUARD = 0.7F;
    public static final int BARE = 200, CALL_EVERY = 200;
    private static final int DW_BARE = 20;
    private int bare, rocks = CALL_EVERY;

    public EntityStonebreaker(World w) {
        super(w, 1.7F, 3.6F, false);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_BARE, Byte.valueOf((byte) 0));
    }

    @Override
    public String key() {
        return "stonebreaker";
    }

    @Override
    protected double baseHealth() {
        return HEALTH;
    }

    @Override
    protected double baseDamage() {
        return 16;
    }

    @Override
    protected double baseSpeed() {
        return 0.22D;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0D);
    }

    @Override
    public void loot(int looting) {
        twilightLoot(TwilightWorld.APALACHIA, looting);
    }

    public boolean skin() {
        return dataWatcher.getWatchableObjectByte(DW_BARE) == 0;
    }

    /** Strip the skin: a maul hit or a boulder. */
    public void breakSkin() {
        if (skin()) {
            worldObj.playSoundAtEntity(this, "random.anvil_break", 1.5F, 0.6F);
            tell("dre.chat.mini.stonebreaker_bare");
        }
        bare = BARE;
        dataWatcher.updateObject(DW_BARE, Byte.valueOf((byte) 1));
    }

    /** With the skin the model is darker: you can see the blows do not land. */
    @Override
    public float shade() {
        return skin() ? 0.55F : 1F;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (!worldObj.isRemote && src.getEntity() instanceof EntityPlayer && src.getSourceOfDamage() == src.getEntity()
            && MeleeRoles.kind(((EntityPlayer) src.getEntity()).getHeldItem()) == MeleeRoles.Kind.MAUL) breakSkin();
        return super.attackEntityFrom(src, skin() ? amount * (1 - SKIN_GUARD) : amount);
    }

    /** Call three boulders onto the target. How many will fall. */
    public int callRocks(EntityPlayer t) {
        int made = 0;
        for (int i = 0; i < 3; i++) {
            int x = MathHelper.floor_double(t.posX) + rand.nextInt(5) - 2, z = MathHelper.floor_double(t.posZ) + rand.nextInt(5) - 2;
            int ground = worldObj.getTopSolidOrLiquidBlock(x, z);
            if (ground <= 1 || ground + EntityRockfall.DROP >= worldObj.getHeight()) continue;
            worldObj.spawnEntityInWorld(new EntityRockfall(worldObj, x + 0.5, ground + EntityRockfall.DROP, z + 0.5, ground));
            made++;
        }
        if (made > 0) worldObj.playSoundAtEntity(this, "mob.irongolem.throw", 2.0F, 0.5F);
        return made;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) return;
        if (bare > 0 && --bare == 0) dataWatcher.updateObject(DW_BARE, Byte.valueOf((byte) 0));
        if (skin() && ticksExisted % 8 == 0 && worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("crit", posX, posY + height / 2, posZ, 4, 0.7, 1.2, 0.7, 0);
        EntityPlayer t = targetPlayer();
        if (t != null && --rocks <= 0) {
            rocks = CALL_EVERY;
            callRocks(t);
        }
    }
}
