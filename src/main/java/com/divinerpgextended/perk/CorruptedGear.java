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
 * Corrupted Divine items - items of darkness: corruption shards drop from the Grue (lives in the pitch dark of caves) and the Enthralled Dramcryx.
 * <ul>
 * <li>Pickaxe, axe and shovel: the darker it is where you stand, the faster they dig - in total darkness {@link #DARK_MULT} times, up to
 * light {@link #BRIGHT_FROM} - as usual; in bright light ({@link #BRIGHT_FROM} and above) they dull - x{@link #LIGHT_MULT}.</li>
 * <li>Eyes of the Grue: digging in the dark (light {@link #DARK_MAX} and below) with a corruption tool or hitting in the dark with a corruption sword or maul -
 * night vision, until you stop.</li>
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

    /** Dig speed multiplier at this light level (0..15). */
    public static float speed(int light) {
        if (light >= BRIGHT_FROM) return LIGHT_MULT;
        return DARK_MULT - (DARK_MULT - 1F) * light / (BRIGHT_FROM - 1F);
    }

    /** Light where the player stands (at head level). */
    public static int light(EntityPlayer p) {
        World w = p.worldObj;
        return w.getBlockLightValue(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posY + p.getEyeHeight()), MathHelper.floor_double(p.posZ));
    }

    static Item held(EntityPlayer p) {
        ItemStack s = p.getHeldItem();
        return s == null ? null : s.getItem();
    }

    /** Eyes of the Grue: in the dark - night vision (without the particle icon), while you keep going. */
    public static boolean sight(EntityPlayer p) {
        if (p.worldObj.isRemote || light(p) > DARK_MAX || com.divinerpgextended.vethea.Nightmare.active(p)) return false; // in the dream before completion the corruption is silent
        p.addPotionEffect(new PotionEffect(Potion.nightVision.id, SIGHT_TICKS, 0, true));
        return true;
    }

    @SubscribeEvent
    public void onSpeed(PlayerEvent.BreakSpeed e) {
        if (e.entityPlayer == null || !tool(held(e.entityPlayer)) || e.newSpeed <= 0 || com.divinerpgextended.vethea.Nightmare.active(e.entityPlayer)) return;
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
