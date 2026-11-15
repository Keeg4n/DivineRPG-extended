package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.entity.EntityTomahawk;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Tomahawk (Apalachia reliquary): a hatchet on Divine's Apalachia material, does not break. Right click - throw: flies up to
 * {@link EntityTomahawk#RANGE} blocks, hits up to {@link EntityTomahawk#TARGETS} targets in a row (from the first - to the nearest next) and
 * returns to the hand; while it flies, it cannot be thrown a second time. Thrown from afar (farther than {@link EntityTomahawk#STUN_RANGE} blocks to the
 * first target) it stuns it.
 */
public class ItemTomahawk extends ItemSword {

    public static final String OUT = "dreTomahawkOut";

    public ItemTomahawk() {
        super(net.divinerpg.utils.material.ToolMaterialMod.Apalachia);
        setMaxDamage(0);
        setUnlocalizedName("dre.tomahawk");
        setTextureName("divinerpgextended:tomahawk");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public boolean isDamageable() {
        return false;
    }

    /** Throw damage is like a hit with this weapon. */
    public static float throwDamage() {
        return net.divinerpg.utils.material.ToolMaterialMod.Apalachia.getDamageVsEntity() + 4.0F;
    }

    /** Whether it is flying now (the marker is removed on return or after {@link EntityTomahawk#LIFE} ticks). */
    public static boolean out(ItemStack s, long now) {
        return s.hasTagCompound() && s.getTagCompound().getLong(OUT) > now;
    }

    public static void setOut(ItemStack s, long until) {
        if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
        if (until <= 0) s.getTagCompound().removeTag(OUT);
        else s.getTagCompound().setLong(OUT, until);
    }

    @Override
    public EnumAction getItemUseAction(ItemStack s) {
        return EnumAction.none;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        long now = w.getTotalWorldTime();
        if (out(s, now)) return s;
        if (!w.isRemote) {
            w.spawnEntityInWorld(new EntityTomahawk(w, p));
            setOut(s, now + EntityTomahawk.LIFE);
            w.playSoundAtEntity(p, "random.bow", 0.8F, 0.6F);
        }
        p.swingItem();
        return s;
    }

    @Override
    public void onUpdate(ItemStack s, World w, Entity e, int slot, boolean held) {
        if (!w.isRemote && s.hasTagCompound() && s.getTagCompound().hasKey(OUT) && !out(s, w.getTotalWorldTime())) setOut(s, 0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List l, boolean adv) {
        l.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocalFormatted("dre.tooltip.tomahawk", EntityTomahawk.TARGETS, (int) EntityTomahawk.RANGE));
        l.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocalFormatted("dre.tooltip.tomahawk_stun", (int) EntityTomahawk.STUN_RANGE));
    }
}
