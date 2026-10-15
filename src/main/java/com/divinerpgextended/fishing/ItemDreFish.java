package com.divinerpgextended.fishing;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Рыба мода (мета = номер в {@link Fish}): еда с эффектами; квестовые нужны для заданий Леорны. */
public class ItemDreFish extends ItemFood {

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemDreFish() {
        super(2, 0.2F, false);
        setHasSubtypes(true);
        setMaxDamage(0);
        setUnlocalizedName("dre.fish");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    private static Fish fish(ItemStack s) {
        int m = s.getItemDamage();
        return Fish.values()[m >= 0 && m < Fish.values().length ? m : 0];
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        return "item.dre.fish." + fish(s).key();
    }

    @Override
    public int func_150905_g(ItemStack s) { // сытость
        return fish(s).food;
    }

    @Override
    public float func_150906_h(ItemStack s) { // насыщение
        return fish(s).saturation;
    }

    @Override
    protected void onFoodEaten(ItemStack s, World w, EntityPlayer p) {
        Fish f = fish(s);
        if (w.isRemote) return;
        if (f.effect != null) p.addPotionEffect(new PotionEffect(f.effect.id, f.duration, f.amplifier));
        if (f == Fish.SNOW_BURBOT) ColdHandler.addCold(p, -40); // согревает в Iceika
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[Fish.values().length];
        for (Fish f : Fish.values()) icons[f.ordinal()] = reg.registerIcon("divinerpgextended:fish_" + f.key());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int m) {
        return icons[m >= 0 && m < icons.length ? m : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (Fish f : Fish.values()) list.add(new ItemStack(item, 1, f.ordinal()));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        Fish f = fish(s);
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.zone." + f.zone.key()));
        if (f.quest) list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("dre.tooltip.quest_fish"));
        if (f.effect != null || f == Fish.SNOW_BURBOT) {
            list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.fish." + f.key()));
        }
    }
}
