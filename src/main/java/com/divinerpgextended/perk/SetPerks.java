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
 * Перки полных сетов сумеречных миров (сверх эффектов Divine):
 * <ul>
 * <li>Эдем, Солнечный шахтёр: x4 к сумеречной руде (Divine); киркой откалывает рог оглушённому кадиллиону Эдема;</li>
 * <li>Дикий лес, Лесной следопыт: регенерация под водой (Divine, только с головой в воде) и в листве - когда голова в
 * кроне;</li>
 * <li>Апалачия, Каменная стена: не отбрасывает, {@link #REFLECT} ближнего урона возвращается атакующему;</li>
 * <li>Скайтерн, Хозяин ветров: ковёр летит вверх и вниз по взгляду, запас ковра вдвое больше
 * ({@link com.divinerpgextended.trinket.DoubleJump});</li>
 * <li>Мортум, Пожиратель тьмы: удар с шансом {@link #DRAIN_CHANCE} (в темноте {@link #DRAIN_CHANCE_DARK}) возвращает здоровьем
 * {@link #DRAIN} нанесённого урона, не больше {@link #DRAIN_MAX} - отхил, а не бессмертие против боссов-рукопашников.</li>
 * </ul>
 */
public class SetPerks {

    public static final float REFLECT = 0.2F, DRAIN = 0.3F, DRAIN_MAX = 3.0F, DRAIN_CHANCE = 0.2F, DRAIN_CHANCE_DARK = 0.35F;
    private static final UUID STEADY = UUID.fromString("6b1f3c2e-9d4a-4f0e-8a51-2c7d9e3b5a10");

    public static boolean wears(EntityPlayer p, TwilightWorld w) {
        return HomeArmor.wears(p, w, false);
    }

    /** Темно там, где стоит игрок (свет блока и неба с учётом времени суток ниже 8). */
    public static boolean dark(EntityLivingBase e) {
        int x = MathHelper.floor_double(e.posX), y = MathHelper.floor_double(e.posY), z = MathHelper.floor_double(e.posZ);
        return e.worldObj.getBlockLightValue(x, y, z) < 8;
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        if (p.ticksExisted % 10 != 0) return;
        // Каменная стена: модификатор держится, пока надет сет
        IAttributeInstance kb = p.getEntityAttribute(SharedMonsterAttributes.knockbackResistance);
        boolean wall = wears(p, TwilightWorld.APALACHIA);
        AttributeModifier cur = kb.getModifier(STEADY);
        if (wall && cur == null) kb.applyModifier(new AttributeModifier(STEADY, "dre.stone_wall", 1.0, 0));
        else if (!wall && cur != null) kb.removeModifier(cur);
        // Лесной следопыт: раз в 2 с полсердца, если голова в листве (под водой лечит Divine); трава не лечит - её везде много
        if (p.ticksExisted % 40 == 0 && p.getHealth() < p.getMaxHealth() && wears(p, TwilightWorld.WILDWOOD) && inNature(p)) p.heal(1.0F);
    }

    /** Голова игрока внутри листвы (в кроне). */
    public static boolean inNature(EntityPlayer p) {
        int x = MathHelper.floor_double(p.posX), y = MathHelper.floor_double(p.posY + p.getEyeHeight()), z = MathHelper.floor_double(p.posZ);
        return p.worldObj.getBlock(x, y, z).getMaterial() == Material.leaves;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote || e.ammount <= 0) return;
        // Каменная стена: вернуть часть ближнего урона
        if (e.entityLiving instanceof EntityPlayer && wears((EntityPlayer) e.entityLiving, TwilightWorld.APALACHIA)
            && e.source.getSourceOfDamage() instanceof EntityLivingBase && e.source.getSourceOfDamage() == e.source.getEntity()
            && !"thorns".equals(e.source.getDamageType())) {
            ((EntityLivingBase) e.source.getEntity()).attackEntityFrom(DamageSource.causeThornsDamage(e.entityLiving), e.ammount * REFLECT);
        }
        // Пожиратель тьмы: вампиризм
        if (e.source.getEntity() instanceof EntityPlayer && !(e.entityLiving instanceof EntityPlayer)) {
            EntityPlayer p = (EntityPlayer) e.source.getEntity();
            if (wears(p, TwilightWorld.MORTUM) && !com.divinerpgextended.perk.RangedRoles.sidePellet(e.source.getSourceOfDamage())
                && p.getRNG().nextFloat() < (dark(p) ? DRAIN_CHANCE_DARK : DRAIN_CHANCE))
                p.heal(Math.min(DRAIN_MAX, Math.min(e.ammount, e.entityLiving.getHealth()) * DRAIN));
        }
    }
}
