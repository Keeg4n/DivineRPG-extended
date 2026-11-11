package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.ArrowNockEvent;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Грозовой лук (реликварий Дикого леса): стреляет как лук Дикого леса Divine (те же стрелы Дикого леса и урон 6-16), не
 * ломается. В ливень Дикого леса (и в любой дождь) каждая стрела бьёт молнией в цель, иначе - каждая {@link #EVERY}-я: молния в
 * точку попадания и +{@link #BONUS} урона по цели ({@link com.divinerpgextended.perk.RelicEffects}).
 */
public class ItemStormBow extends ItemBow {

    public static final String TAG = "dreStorm", SHOTS = "dreStormShots";
    public static final int EVERY = 5;
    public static final float BONUS = 0.25F, DMG_MIN = 6F, DMG_MAX = 16F;

    @SideOnly(Side.CLIENT)
    private IIcon[] pull;

    public ItemStormBow() {
        setMaxDamage(0);
        setUnlocalizedName("dre.stormBow");
        setTextureName("divinerpgextended:storm_bow");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public boolean isDamageable() {
        return false;
    }

    /** Стрелы Дикого леса Divine (нет поля - обычные). */
    public static Item ammo() {
        Item a = net.divinerpg.utils.items.TwilightItemsWeapons.wildwoodArrow;
        return a != null ? a : net.minecraft.init.Items.arrow;
    }

    /** Гроза: ливень Дикого леса или обычный дождь. */
    public static boolean storm(World w) {
        return com.divinerpgextended.world.twilight.WorldWeather.rain(w) || w.isRaining();
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        ArrowNockEvent ev = new ArrowNockEvent(p, s);
        MinecraftForge.EVENT_BUS.post(ev);
        if (ev.isCanceled()) return ev.result;
        if (p.capabilities.isCreativeMode || p.inventory.hasItem(ammo()) || EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, s) > 0)
            p.setItemInUse(s, getMaxItemUseDuration(s));
        return s;
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack s, World w, EntityPlayer p, int left) {
        int charge = getMaxItemUseDuration(s) - left;
        ArrowLooseEvent ev = new ArrowLooseEvent(p, s, charge);
        MinecraftForge.EVENT_BUS.post(ev);
        if (ev.isCanceled()) return;
        charge = ev.charge;
        boolean infinite = p.capabilities.isCreativeMode || EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, s) > 0;
        if (!infinite && !p.inventory.hasItem(ammo())) return;
        float f = charge / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f < 0.1F) return;
        if (f > 1.0F) f = 1.0F;
        EntityArrow arrow = new net.divinerpg.entities.base.EntityDivineArrow(w, p, f * 2.0F, DMG_MIN, DMG_MAX, "wildwoodArrow");
        if (f == 1.0F) arrow.setIsCritical(true);
        int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, s);
        if (power > 0) arrow.setDamage(arrow.getDamage() + power * 0.5D + 0.5D);
        int punch = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, s);
        if (punch > 0) arrow.setKnockbackStrength(punch);
        if (EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, s) > 0) arrow.setFire(100);
        if (infinite) arrow.canBePickedUp = 2;
        else p.inventory.consumeInventoryItem(ammo());
        if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
        int shots = s.getTagCompound().getInteger(SHOTS) + 1;
        s.getTagCompound().setInteger(SHOTS, shots % 1000);
        if (storm(w) || shots % EVERY == 0) arrow.getEntityData().setBoolean(TAG, true);
        w.playSoundAtEntity(p, "random.bow", 1.0F, 1.0F / (itemRand.nextFloat() * 0.4F + 1.2F) + f * 0.5F);
        if (!w.isRemote) {
            w.spawnEntityInWorld(arrow);
            if (arrow.getEntityData().getBoolean(TAG)) com.divinerpgextended.perk.RelicEffects.track(arrow);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        itemIcon = reg.registerIcon(getIconString() + "_standby");
        pull = new IIcon[3];
        for (int i = 0; i < 3; i++) pull[i] = reg.registerIcon(getIconString() + "_pulling_" + i);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getItemIconForUseDuration(int i) {
        return pull[Math.max(0, Math.min(2, i))];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack s, int pass, EntityPlayer p, ItemStack using, int useRemaining) {
        if (using == null || using != s) return itemIcon;
        int used = s.getMaxItemUseDuration() - useRemaining;
        return used >= 18 ? pull[2] : used > 13 ? pull[1] : used > 0 ? pull[0] : itemIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List l, boolean adv) {
        l.add(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("dre.tooltip.storm_bow", EVERY, (int) (BONUS * 100)));
        l.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.storm_bow_ammo", new ItemStack(ammo()).getDisplayName()));
    }
}
