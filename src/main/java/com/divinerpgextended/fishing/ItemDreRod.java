package com.divinerpgextended.fishing;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Удочка мода: забрасывает {@link EntityDreHook} со своей силой и особенностью. */
public class ItemDreRod extends ItemFishingRod {

    public final Rod rod;
    @SideOnly(Side.CLIENT)
    private IIcon cast;

    public ItemDreRod(Rod rod) {
        this.rod = rod;
        setMaxDamage(64 + rod.power * 8);
        setUnlocalizedName("dre.rod." + rod.key);
        setTextureName("divinerpgextended:rod_" + rod.key);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        if (p.fishEntity != null) {
            s.damageItem(p.fishEntity.func_146034_e(), p);
        } else {
            w.playSoundAtEntity(p, "random.bow", 0.5F, 0.4F / (itemRand.nextFloat() * 0.4F + 0.8F));
            if (!w.isRemote) w.spawnEntityInWorld(new EntityDreHook(w, p, rod));
        }
        p.swingItem();
        return s;
    }

    @Override
    public int getItemEnchantability() {
        return 10;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        itemIcon = reg.registerIcon(getIconString() + "_uncast");
        cast = reg.registerIcon(getIconString() + "_cast");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon func_94597_g() {
        return cast;
    }

    @Override
    public IIcon getIcon(ItemStack s, int pass, EntityPlayer p, ItemStack using, int remaining) {
        return p != null && p.fishEntity != null ? cast : itemIcon;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("dre.tooltip.rod_power", rod.power));
        if (rod.hasSpecial()) {
            list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.rod." + rod.key));
        }
        Bait.tooltip(p, list);
    }
}
