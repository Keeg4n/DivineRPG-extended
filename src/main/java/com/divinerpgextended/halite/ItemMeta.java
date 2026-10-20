package com.divinerpgextended.halite;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Простой предмет с вариантами по метаданным: имя item.dre.&lt;name&gt;.&lt;key&gt;, значок &lt;name&gt;_&lt;key&gt;, подсказка
 * dre.tooltip.&lt;name&gt;.&lt;key&gt; (или общая dre.tooltip.&lt;name&gt;).
 */
public class ItemMeta extends Item {

    private final String name;
    private final String[] keys;
    private final boolean glow;
    private IIcon[] icons;

    public ItemMeta(String name, String[] keys, int stack, boolean glow) {
        this.name = name;
        this.keys = keys;
        this.glow = glow;
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(stack);
        setUnlocalizedName("dre." + name);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    private String key(int meta) {
        return keys[Math.max(0, Math.min(meta, keys.length - 1))];
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        return "item.dre." + name + "." + key(s.getItemDamage());
    }

    @Override
    public boolean hasEffect(ItemStack s, int pass) {
        return glow;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[keys.length];
        for (int i = 0; i < keys.length; i++) icons[i] = reg.registerIcon("divinerpgextended:" + name + "_" + keys[i]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons[Math.max(0, Math.min(meta, icons.length - 1))];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < keys.length; i++) list.add(new ItemStack(item, 1, i));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        String k = "dre.tooltip." + name + "." + key(s.getItemDamage());
        if (!StatCollector.canTranslate(k)) k = "dre.tooltip." + name;
        if (StatCollector.canTranslate(k)) for (String line : StatCollector.translateToLocal(k).split("\\\\n"))
            list.add(EnumChatFormatting.GRAY + line);
    }
}
