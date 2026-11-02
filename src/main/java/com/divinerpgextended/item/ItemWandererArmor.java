package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.ISpecialArmor;
import net.minecraftforge.common.util.EnumHelper;

import com.divinerpgextended.Config;
import com.divinerpgextended.perk.Perk;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.divinerpg.utils.items.VanillaItemsArmor;

/**
 * Броня Странника: одна броня на всю игру. Защита считается так же, как у брони Divine: полный сет даёт
 * процент снижения урона ступени, части делят его в пропорции 5/8/7/4 из 24. У каждой ступени R1-R7 свой облик: модель
 * ({@link com.divinerpgextended.client.ModelWandererArmor}) и значки частей; без облика - как божественная броня.
 */
public class ItemWandererArmor extends ItemArmor implements ISpecialArmor {

    public static final ArmorMaterial MATERIAL = EnumHelper
        .addArmorMaterial("DRE_WANDERER", 0, new int[] { 3, 8, 6, 3 }, 15);
    private static final int[] SHARE = { 5, 8, 7, 4 };
    private static final String[] ICONS = { "divinerpg:eliteRealmiteHelmet", "divinerpg:eliteRealmiteChestplate",
        "divinerpg:eliteRealmiteLeggings", "divinerpg:eliteRealmiteBoots" };
    private static final String[] PARTS = { "helmet", "chest", "legs", "boots" };
    /** Сколько ступеней с обликом (R1..LOOKS). */
    public static final int LOOKS = 7;

    @SideOnly(Side.CLIENT)
    private IIcon[] looks;

    public static boolean hasLook(int tier) {
        return tier >= 1 && tier <= LOOKS;
    }

    /** Атлас облика ступени (модель брони, tools/wanderer_armor.py). */
    public static String lookTexture(int tier) {
        return "divinerpgextended:textures/models/wanderer/r" + tier + ".png";
    }

    public ItemWandererArmor(int armorType, String name) {
        super(MATERIAL, 0, armorType);
        setUnlocalizedName("dre." + name);
        setTextureName(ICONS[armorType]);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
        setMaxDamage(0);
    }

    /** Та же часть божественной брони (ищется при вызове: поля Divine заполняются в его preInit). */
    private Item divine() {
        Item[] set = { VanillaItemsArmor.divineHelmet, VanillaItemsArmor.divineBody, VanillaItemsArmor.divineLegs,
            VanillaItemsArmor.divineBoots };
        return set[armorType];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        Item d = divine();
        IIcon icon = d == null ? null : d.getIconFromDamage(0);
        return icon != null ? icon : itemIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        super.registerIcons(reg);
        looks = new IIcon[LOOKS + 1];
        for (int t = 1; t <= LOOKS; t++) looks[t] = reg.registerIcon("divinerpgextended:wanderer/r" + t + "_" + PARTS[armorType]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        int t = ArmorData.tier(stack);
        return looks != null && hasLook(t) ? looks[t] : getIconFromDamage(0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        return getIconIndex(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModelBiped getArmorModel(EntityLivingBase entity, ItemStack stack, int slot) {
        int t = ArmorData.tier(stack);
        return hasLook(t) ? com.divinerpgextended.client.ModelWandererArmor.forMob(t, armorType) : null;
    }

    private double pieceRatio(ItemStack stack) {
        return Config.fullReduction(ArmorData.tier(stack)) / 100.0 * SHARE[armorType] / 24.0;
    }

    @Override
    public ArmorProperties getProperties(EntityLivingBase player, ItemStack armor, DamageSource source,
        double damage, int slot) {
        if (source.isUnblockable()) return new ArmorProperties(0, 0, 0);
        return new ArmorProperties(0, pieceRatio(armor), Integer.MAX_VALUE);
    }

    @Override
    public int getArmorDisplay(EntityPlayer player, ItemStack armor, int slot) {
        return (int) Math.round(pieceRatio(armor) * 100.0 / 4.0);
    }

    @Override
    public void damageArmor(EntityLivingBase entity, ItemStack stack, DamageSource source, int damage, int slot) {
        // неразрушима
    }

    @Override
    public boolean isDamageable() {
        return false;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, int slot, String type) {
        if (hasLook(ArmorData.tier(stack))) return lookTexture(ArmorData.tier(stack));
        Item d = divine();
        String skin = d == null ? null : d.getArmorTexture(new ItemStack(d), entity, slot, type);
        if (skin != null) return skin;
        return armorType == 2 ? "divinerpg:textures/armor/eliteRealmite_2.png"
            : "divinerpg:textures/armor/eliteRealmite_1.png";
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int tier = ArmorData.tier(stack);
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted("dre.tooltip.tier", tier,
            StatCollector.translateToLocal("dre.tier." + tier)));
        list.add(StatCollector.translateToLocalFormatted("dre.tooltip.full_reduction", Config.fullReduction(tier)));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.unbreakable"));
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.no_fall"));
        List<Perk> perks = ArmorData.perks(stack);
        list.add(StatCollector.translateToLocalFormatted("dre.tooltip.perks", perks.size(), Config.smallSlots(tier)));
        for (Perk p : perks) {
            list.add(EnumChatFormatting.GREEN + " - " + StatCollector.translateToLocal(p.langKey()));
        }
        list.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("dre.tooltip.full_set"));
    }
}
