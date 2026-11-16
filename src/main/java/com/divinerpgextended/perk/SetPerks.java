package com.divinerpgextended.perk;

import java.util.UUID;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Perks of full twilight world sets (on top of Divine's effects):
 * <ul>
 * <li>Eden, Sun Miner: x4 twilight ore (Divine); with a pickaxe it chips a horn off a stunned Eden Cadillion;</li>
 * <li>Wildwood, Forest Tracker: regeneration underwater (Divine, only with the head in water) and in foliage - when the head is in
 * the canopy;</li>
 * <li>Apalachia, Stone Wall: no knockback, {@link #REFLECT} of melee damage is returned to the attacker;</li>
 * <li>Skythern, Wind Master: the carpet flies up and down by look direction, the carpet reserve is twice as large
 * ({@link com.divinerpgextended.trinket.DoubleJump});</li>
 * <li>Mortum, Darkness Devourer: a hit with a {@link #DRAIN_CHANCE} chance ({@link #DRAIN_CHANCE_DARK} in the dark) returns {@link #DRAIN} of the
 * damage dealt as health, no more than {@link #DRAIN_MAX} - a heal, not immortality against melee bosses.</li>
 * </ul>
 */
public class SetPerks {

    public static final float REFLECT = 0.2F, DRAIN = 0.3F, DRAIN_MAX = 3.0F, DRAIN_CHANCE = 0.2F, DRAIN_CHANCE_DARK = 0.35F;
    private static final UUID STEADY = UUID.fromString("6b1f3c2e-9d4a-4f0e-8a51-2c7d9e3b5a10");

    public static boolean wears(EntityPlayer p, TwilightWorld w) {
        return HomeArmor.wears(p, w, false);
    }

    /** It is dark where the player stands (block and sky light accounting for time of day below 8). */
    public static boolean dark(EntityLivingBase e) {
        int x = MathHelper.floor_double(e.posX), y = MathHelper.floor_double(e.posY), z = MathHelper.floor_double(e.posZ);
        return e.worldObj.getBlockLightValue(x, y, z) < 8;
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        if (p.ticksExisted % 10 != 0) return;
        // Stone Wall: the modifier is held while the set is worn
        IAttributeInstance kb = p.getEntityAttribute(SharedMonsterAttributes.knockbackResistance);
        boolean wall = wears(p, TwilightWorld.APALACHIA);
        AttributeModifier cur = kb.getModifier(STEADY);
        if (wall && cur == null) kb.applyModifier(new AttributeModifier(STEADY, "dre.stone_wall", 1.0, 0));
        else if (!wall && cur != null) kb.removeModifier(cur);
        // Forest Tracker: half a heart every 2 s if the head is in foliage (underwater Divine heals); grass does not heal - there is too much of it everywhere
        if (p.ticksExisted % 40 == 0 && p.getHealth() < p.getMaxHealth() && wears(p, TwilightWorld.WILDWOOD) && inNature(p)) p.heal(1.0F);
    }

    /** The player's head is inside foliage (in the canopy). */
    public static boolean inNature(EntityPlayer p) {
        int x = MathHelper.floor_double(p.posX), y = MathHelper.floor_double(p.posY + p.getEyeHeight()), z = MathHelper.floor_double(p.posZ);
        return p.worldObj.getBlock(x, y, z).getMaterial() == Material.leaves;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote || e.ammount <= 0) return;
        // Stone Wall: return part of the melee damage
        if (e.entityLiving instanceof EntityPlayer && wears((EntityPlayer) e.entityLiving, TwilightWorld.APALACHIA)
            && e.source.getSourceOfDamage() instanceof EntityLivingBase && e.source.getSourceOfDamage() == e.source.getEntity()
            && !"thorns".equals(e.source.getDamageType()) && !Protection.spare(e.entityLiving, e.source.getEntity())) {
            ((EntityLivingBase) e.source.getEntity()).attackEntityFrom(DamageSource.causeThornsDamage(e.entityLiving), e.ammount * REFLECT);
        }
        // Darkness Devourer: vampirism
        if (e.source.getEntity() instanceof EntityPlayer && !(e.entityLiving instanceof EntityPlayer)) {
            EntityPlayer p = (EntityPlayer) e.source.getEntity();
            if (wears(p, TwilightWorld.MORTUM) && !com.divinerpgextended.perk.RangedRoles.sidePellet(e.source.getSourceOfDamage())
                && p.getRNG().nextFloat() < (dark(p) ? DRAIN_CHANCE_DARK : DRAIN_CHANCE) && !Protection.spare(p, e.entityLiving))
                p.heal(Math.min(DRAIN_MAX, Math.min(e.ammount, e.entityLiving.getHealth()) * DRAIN));
        }
    }
}
