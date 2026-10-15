package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.ISpecialArmor;
import net.minecraftforge.common.util.EnumHelper;

import com.divinerpgextended.Config;
import com.divinerpgextended.perk.Perk;

/**
 * Броня Странника: одна броня на всю игру. Защита считается так же, как у брони Divine: полный сет даёт
 * процент снижения урона ступени, части делят его в пропорции 5/8/7/4 из 24.
 */
public class ItemWandererArmor extends ItemArmor implements ISpecialArmor {

    public static final ArmorMaterial MATERIAL = EnumHelper
        .addArmorMaterial("DRE_WANDERER", 0, new int[] { 3, 8, 6, 3 }, 15);
    private static final int[] SHARE = { 5, 8, 7, 4 };
    private static final String[] ICONS = { "divinerpg:eliteRealmiteHelmet", "divinerpg:eliteRealmiteChestplate",
        "divinerpg:eliteRealmiteLeggings", "divinerpg:eliteRealmiteBoots" };

    public ItemWandererArmor(int armorType, String name) {
        super(MATERIAL, 0, armorType);
        setUnlocalizedName("dre." + name);
        setTextureName(ICONS[armorType]);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
        setMaxDamage(0);
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
