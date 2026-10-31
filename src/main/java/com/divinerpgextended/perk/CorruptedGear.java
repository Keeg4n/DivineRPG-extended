package com.divinerpgextended.perk;

import net.divinerpg.utils.items.VanillaItemsTools;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Порченые вещи Divine - вещи тьмы: осколки порчи падают с Груи (живёт в кромешной тьме пещер) и Порабощённого драмкрикса.
 * <ul>
 * <li>Кирка, топор и лопата: чем темнее там, где стоишь, тем быстрее копают - в полной темноте в {@link #DARK_MULT} раза, к
 * свету {@link #BRIGHT_FROM} - как обычно; на ярком свету ({@link #BRIGHT_FROM} и выше) тупятся - x{@link #LIGHT_MULT}.</li>
 * <li>Глаза Груи: копаешь во тьме (свет {@link #DARK_MAX} и ниже) инструментом порчи или бьёшь во тьме мечом или молотом
 * порчи - ночное зрение, пока не перестанешь.</li>
 * </ul>
 */
public class CorruptedGear {

    public static final float DARK_MULT = 2.5F, LIGHT_MULT = 0.8F;
    public static final int BRIGHT_FROM = 13, DARK_MAX = 4, SIGHT_TICKS = 260;

    public static boolean tool(Item i) {
        return i == VanillaItemsTools.corruptedPickaxe || i == VanillaItemsTools.corruptedAxe || i == VanillaItemsTools.corruptedShovel;
    }

    public static boolean weapon(Item i) {
        return i == VanillaItemsWeapons.deathBringer || i == VanillaItemsWeapons.corruptedMaul;
    }

    /** Множитель скорости копания при таком свете (0..15). */
    public static float speed(int light) {
        if (light >= BRIGHT_FROM) return LIGHT_MULT;
        return DARK_MULT - (DARK_MULT - 1F) * light / (BRIGHT_FROM - 1F);
    }

    /** Свет там, где стоит игрок (на уровне головы). */
    public static int light(EntityPlayer p) {
        World w = p.worldObj;
        return w.getBlockLightValue(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posY + p.getEyeHeight()), MathHelper.floor_double(p.posZ));
    }

    static Item held(EntityPlayer p) {
        ItemStack s = p.getHeldItem();
        return s == null ? null : s.getItem();
    }

    /** Глаза Груи: во тьме - ночное зрение (без значка частиц), пока продолжаешь. */
    public static boolean sight(EntityPlayer p) {
        if (p.worldObj.isRemote || light(p) > DARK_MAX) return false;
        p.addPotionEffect(new PotionEffect(Potion.nightVision.id, SIGHT_TICKS, 0, true));
        return true;
    }

    @SubscribeEvent
    public void onSpeed(PlayerEvent.BreakSpeed e) {
        if (e.entityPlayer == null || !tool(held(e.entityPlayer)) || e.newSpeed <= 0) return;
        e.newSpeed *= speed(light(e.entityPlayer));
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (e.getPlayer() != null && tool(held(e.getPlayer()))) sight(e.getPlayer());
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (!(e.source.getEntity() instanceof EntityPlayer) || e.source.getSourceOfDamage() != e.source.getEntity()) return;
        EntityPlayer p = (EntityPlayer) e.source.getEntity();
        if (weapon(held(p))) sight(p);
    }
}
