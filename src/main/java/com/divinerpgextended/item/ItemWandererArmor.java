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
import net.minecraft.world.World;
import net.minecraftforge.common.ISpecialArmor;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.common.util.FakePlayer;

import com.divinerpgextended.Config;
import com.divinerpgextended.perk.Perk;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.divinerpg.utils.items.VanillaItemsArmor;

/**
 * Броня Странника: одна броня на всю игру. Защита считается так же, как у брони Divine: полный сет даёт
 * процент снижения урона ступени, части делят его в пропорции 5/8/7/4 из 24. У каждой ступени R1-R7 свой облик: модель
 * ({@link com.divinerpgextended.client.ModelWandererArmor}) и значки частей; без облика - как божественная броня.
 * Привязывается к тому, кто наденет первым ({@link ArmorData#owner}): на чужом не защищает и не даёт эффектов. Красится
 * красителями в верстаке ({@link RecipeWandererDye}), это только облик: основной материал части под цвет.
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
    /** Всего обликов: ступени и ещё по одному на первой ступени у каждого ядра, кроме божественного. */
    public static final int LOOK_COUNT = LOOKS + ArmorCore.values().length - 1;

    @SideOnly(Side.CLIENT)
    private IIcon[] looks, dyeLooks;

    public static boolean hasLook(int tier) {
        return tier >= 1 && tier <= LOOKS;
    }

    /** Номер облика вещи: ступень, а на первой ступени с ядром не из божественной брони - свой номер после ступеней. */
    public static int look(ItemStack stack) {
        int t = ArmorData.tier(stack);
        ArmorCore core = ArmorData.core(stack);
        return t == 1 && core != ArmorCore.DIVINE ? LOOKS + core.ordinal() : t;
    }

    /** Имя файлов облика: r2, r1_shadow (tools/wanderer_armor.py). */
    public static String lookName(int look) {
        return look <= LOOKS ? "r" + look : "r1_" + ArmorCore.values()[look - LOOKS].key();
    }

    /** Атлас облика (модель брони, tools/wanderer_armor.py). */
    public static String lookTexture(int look) {
        return "divinerpgextended:textures/models/wanderer/" + lookName(look) + ".png";
    }

    public ItemWandererArmor(int armorType, String name) {
        super(MATERIAL, 0, armorType);
        setUnlocalizedName("dre." + name);
        setTextureName(ICONS[armorType]);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
        setMaxDamage(0);
    }

    /** Часть брони этой ступени с этим ядром. */
    public static ItemStack stack(Item item, int tier, ArmorCore core) {
        ItemStack s = new ItemStack(item);
        ArmorData.setTier(s, tier);
        ArmorData.setCore(s, core);
        return s;
    }

    /** В творческом и в NEI - все облики: R1 с ядром каждого финального сета и R2-R7. */
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (ArmorCore c : ArmorCore.values()) list.add(stack(item, 1, c));
        for (com.divinerpgextended.world.ArmorTier t : com.divinerpgextended.world.ArmorTier.values()) list.add(stack(item, t.tier, ArmorCore.DIVINE));
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
        looks = new IIcon[LOOK_COUNT + 1];
        for (int t = 1; t <= LOOK_COUNT; t++) looks[t] = reg.registerIcon("divinerpgextended:wanderer/" + lookName(t) + "_" + PARTS[armorType]);
        dyeLooks = new IIcon[LOOK_COUNT + 1];
        for (int t = 1; t <= LOOK_COUNT; t++) dyeLooks[t] = reg.registerIcon("divinerpgextended:wanderer/" + lookName(t) + "_" + PARTS[armorType] + "_dye");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        return looks != null && hasLook(ArmorData.tier(stack)) ? looks[look(stack)] : getIconFromDamage(0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        // второй проход - покраска: серая маска основного материала, умноженная на цвет (не крашена - тот же значок ещё раз)
        if (pass == 1 && dyeLooks != null && ArmorData.color(stack) >= 0 && hasLook(ArmorData.tier(stack))) return dyeLooks[look(stack)];
        return getIconIndex(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        int c = ArmorData.color(stack);
        return pass == 1 && c >= 0 ? c : 0xFFFFFF;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModelBiped getArmorModel(EntityLivingBase entity, ItemStack stack, int slot) {
        return hasLook(ArmorData.tier(stack)) ? com.divinerpgextended.client.ModelWandererArmor.forMob(look(stack), armorType, ArmorData.color(stack)) : null;
    }

    private double pieceRatio(ItemStack stack) {
        return Config.fullReduction(ArmorData.tier(stack)) / 100.0 * SHARE[armorType] / 24.0;
    }

    @Override
    public ArmorProperties getProperties(EntityLivingBase player, ItemStack armor, DamageSource source,
        double damage, int slot) {
        if (source.isUnblockable() || !works(player, armor)) return new ArmorProperties(0, 0, 0);
        return new ArmorProperties(0, pieceRatio(armor), Integer.MAX_VALUE);
    }

    @Override
    public int getArmorDisplay(EntityPlayer player, ItemStack armor, int slot) {
        return ArmorData.owns(armor, player) ? (int) Math.round(pieceRatio(armor) * 100.0 / 4.0) : 0;
    }

    /** Защищает ли часть того, на ком надета: игрока - только своя, моба - только ничья. */
    private static boolean works(EntityLivingBase wearer, ItemStack armor) {
        return wearer instanceof EntityPlayer ? ArmorData.owns(armor, (EntityPlayer) wearer) : ArmorData.owner(armor) == null;
    }

    /** Кто первым надел, тот и владелец (игроки-заглушки модов не в счёт). */
    @Override
    public void onArmorTick(World world, EntityPlayer player, ItemStack stack) {
        if (world.isRemote || player instanceof FakePlayer || ArmorData.owner(stack) != null) return;
        ArmorData.bind(stack, player);
        com.divinerpgextended.audit.Audit.log(player, "Wanderer", "привязана " + stack.getDisplayName());
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
        if (hasLook(ArmorData.tier(stack))) return lookTexture(look(stack));
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
        ArmorCore core = ArmorData.core(stack);
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted("dre.tooltip.tier", tier,
            StatCollector.translateToLocal(tier == 1 ? "dre.core." + core.key() + ".name" : "dre.tier." + tier)));
        list.add(StatCollector.translateToLocalFormatted("dre.tooltip.full_reduction", Config.fullReduction(tier)));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.unbreakable"));
        if (ArmorData.owner(stack) == null) list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.owner_free"));
        else {
            list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.owner", ArmorData.ownerName(stack)));
            if (player != null && !ArmorData.owns(stack, player)) list.add(EnumChatFormatting.RED + StatCollector.translateToLocal("dre.tooltip.owner_foreign"));
        }
        if (ArmorData.color(stack) >= 0) list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.dyed"));
        list.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocalFormatted("dre.core." + core.key() + ".desc", new java.text.DecimalFormat("0.##").format(Config.coreShadowSpeed)));
        List<Perk> perks = ArmorData.perks(stack);
        list.add(StatCollector.translateToLocalFormatted("dre.tooltip.perks", perks.size(), Config.smallSlots(tier)));
        for (Perk p : perks) {
            list.add(EnumChatFormatting.GREEN + " - " + StatCollector.translateToLocal(p.langKey()));
        }
        list.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("dre.tooltip.full_set"));
    }
}
