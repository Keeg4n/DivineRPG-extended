package com.divinerpgextended.magic;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Арканное зелье (с арканной стойки зельеварения). Метаданные: младшие 5 бит - эффект ({@link ArcaneEffect}), 32 -
 * сильное (светопыль), 64 - долгое (редстоун), 128 - общее (порох: и игрокам в 8 блоках).
 */
public class ItemArcanePotion extends Item {

    public static final int STRONG = 32, LONG = 64, SHARED = 128;

    @SideOnly(Side.CLIENT)
    private IIcon bottle, splash, overlay;

    public ItemArcanePotion() {
        setHasSubtypes(true);
        setMaxStackSize(16);
        setUnlocalizedName("dre.arcanePotion");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    public static ItemStack of(ArcaneEffect e, boolean strong, boolean longer, boolean shared) {
        return new ItemStack(com.divinerpgextended.registry.ModItems.arcanePotion, 1,
            e.ordinal() | (strong ? STRONG : 0) | (longer ? LONG : 0) | (shared ? SHARED : 0));
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        p.setItemInUse(s, getMaxItemUseDuration(s));
        return s;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack s) {
        return 32;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack s) {
        return EnumAction.drink;
    }

    @Override
    public ItemStack onEaten(ItemStack s, World w, EntityPlayer p) {
        int m = s.getItemDamage();
        ArcaneEffects.drink(p, ArcaneEffect.of(m), (m & STRONG) != 0, (m & LONG) != 0, (m & SHARED) != 0);
        if (p.capabilities.isCreativeMode) return s;
        if (--s.stackSize <= 0) return new ItemStack(Items.glass_bottle);
        p.inventory.addItemStackToInventory(new ItemStack(Items.glass_bottle));
        return s;
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        return "item.dre.arcanePotion." + ArcaneEffect.of(s.getItemDamage()).key();
    }

    /** Зелье "Название" (общее - Общее зелье "Название"); сила и время - в подсказке, как у ванильных. */
    @Override
    public String getItemStackDisplayName(ItemStack s) {
        int m = s.getItemDamage();
        return StatCollector.translateToLocalFormatted((m & SHARED) != 0 ? "dre.potion.name.shared" : "dre.potion.name",
            StatCollector.translateToLocal(getUnlocalizedName(s) + ".name"));
    }

    /** "Эффект II (4:00)" - видно сразу; что он делает - под Shift. */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        int m = s.getItemDamage();
        ArcaneEffect e = ArcaneEffect.of(m);
        if (e.removed()) {
            list.add(com.divinerpgextended.client.TooltipShift.KEEP + EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("dre.potion.removed"));
            return;
        }
        boolean strong = (m & STRONG) != 0 && e.leveled();
        int sec = e.seconds * ((m & LONG) != 0 ? 2 : 1);
        list.add(com.divinerpgextended.client.TooltipShift.KEEP + EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.effect." + e.key())
            + (strong ? " II" : "") + String.format(" (%d:%02d)", sec / 60, sec % 60));
        list.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("dre.potion." + e.key() + (strong ? ".desc2" : ".desc1")));
        if ((m & SHARED) != 0) list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.potion.shared.desc"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (ArcaneEffect e : ArcaneEffect.values()) {
            if (e.removed()) continue;
            list.add(of(e, false, false, false));
            if (e.leveled()) list.add(of(e, true, false, false));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamageForRenderPass(int meta, int pass) {
        return pass == 0 ? overlay : (meta & SHARED) != 0 ? splash : bottle;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return (meta & SHARED) != 0 ? splash : bottle;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack s, int pass) {
        return pass == 0 ? ArcaneEffect.of(s.getItemDamage()).color : 0xFFFFFF;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack s, int pass) {
        return pass == 0 && (s.getItemDamage() & STRONG) != 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        bottle = reg.registerIcon("potion_bottle_drinkable");
        splash = reg.registerIcon("potion_bottle_splash");
        overlay = reg.registerIcon("potion_overlay");
        itemIcon = bottle;
    }
}
