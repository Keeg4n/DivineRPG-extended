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
 * Accessories: work only embedded in worn armor ({@link com.divinerpgextended.accessory.Sockets}, inserted at the
 * Wanderer Anvil), do not work in the backpack. Effects are in {@link TrinketEffects}.
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
        return t == Trinket.JACK_AMULET || t == Trinket.PHARAOH_ANKH || t == Trinket.DREAM_WINGS_3 || t.parts() != null ? EnumRarity.epic : EnumRarity.rare;
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
        list.add(EnumChatFormatting.GOLD + howTo(s).getUnformattedText());
        list.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("dre.trinket.ask"));
    }

    /** "Embeds into boots at the Wanderer Anvil: 20 lvl." - also for Divine accessory items. */
    public static net.minecraft.util.IChatComponent howTo(ItemStack s) {
        return new net.minecraft.util.ChatComponentTranslation("dre.trinket.how", partNames(com.divinerpgextended.accessory.Sockets.parts(s)),
            com.divinerpgextended.accessory.Sockets.levels(s));
    }

    /** Armor part names by bits: "helmet or chestplate". */
    public static net.minecraft.util.IChatComponent partNames(int mask) {
        net.minecraft.util.IChatComponent out = new net.minecraft.util.ChatComponentText("");
        boolean first = true;
        for (int i = 0; i < 4; i++) {
            if ((mask & 1 << i) == 0) continue;
            if (!first) out.appendSibling(new net.minecraft.util.ChatComponentTranslation("dre.sockets.or"));
            out.appendSibling(new net.minecraft.util.ChatComponentTranslation("dre.sockets.part." + i));
            first = false;
        }
        return out;
    }

    /** Right-click with the item in hand - a hint where and how it embeds. */
    @Override
    public ItemStack onItemRightClick(ItemStack s, net.minecraft.world.World w, EntityPlayer p) {
        if (!w.isRemote) p.addChatMessage(howTo(s));
        return s;
    }
}
