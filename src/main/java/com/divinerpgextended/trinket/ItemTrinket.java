package com.divinerpgextended.trinket;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Бижутерия Джека: надевается только в слоты аксессуаров (вкладка в инвентаре или ПКМ вещью), в рюкзаке не
 * работает. Эффекты - в {@link TrinketEffects}.
 */
public class ItemTrinket extends Item {

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemTrinket() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
        setUnlocalizedName("dre.trinket");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    public static Trinket of(ItemStack s) {
        int m = s.getItemDamage();
        return Trinket.values()[m >= 0 && m < Trinket.values().length ? m : 0];
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        return "item.dre.trinket." + of(s).key();
    }

    @Override
    public EnumRarity getRarity(ItemStack s) {
        Trinket t = of(s);
        return t == Trinket.JACK_AMULET || t == Trinket.PHARAOH_ANKH || t.parts() != null ? EnumRarity.epic : EnumRarity.rare;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[Trinket.values().length];
        for (Trinket t : Trinket.values()) icons[t.ordinal()] = reg.registerIcon("divinerpgextended:trinket_" + t.key());
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
        for (Trinket t : Trinket.values()) list.add(new ItemStack(item, 1, t.ordinal()));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        Trinket t = of(s);
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.trinket." + t.key() + ".desc"));
        list.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("dre.trinket.how"));
    }

    /** ПКМ вещью в руке - надеть в первый свободный открытый слот аксессуаров. */
    @Override
    public ItemStack onItemRightClick(ItemStack s, net.minecraft.world.World w, EntityPlayer p) {
        if (w.isRemote) return s;
        if (com.divinerpgextended.accessory.Accessories.of(p).equip(s)) {
            w.playSoundAtEntity(p, "random.orb", 0.5F, 1.4F);
            if (!p.capabilities.isCreativeMode) s.stackSize--;
        } else {
            p.addChatMessage(new net.minecraft.util.ChatComponentTranslation("dre.accessories.no_slot"));
        }
        return s;
    }
}
