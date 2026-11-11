package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
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
 * Цунами (с Лиоплевродона): лук, который выпускает веер из {@link #ARROWS} стрел (по {@link #SPREAD} градуса между ними) за
 * одну обычную стрелу; каждая бьёт как стрела лука Апалачии ({@link #DMG_MIN}-{@link #DMG_MAX}). Подобрать можно только
 * среднюю. Не ломается.
 */
public class ItemTsunamiBow extends ItemBow {

    public static final int ARROWS = 5;
    public static final float SPREAD = 6F, DMG_MIN = 10F, DMG_MAX = 20F;

    @SideOnly(Side.CLIENT)
    private IIcon[] pull;

    public ItemTsunamiBow() {
        setMaxDamage(0);
        setUnlocalizedName("dre.tsunami");
        setTextureName("divinerpgextended:tsunami");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public boolean isDamageable() {
        return false;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        ArrowNockEvent ev = new ArrowNockEvent(p, s);
        MinecraftForge.EVENT_BUS.post(ev);
        if (ev.isCanceled()) return ev.result;
        if (p.capabilities.isCreativeMode || p.inventory.hasItem(Items.arrow) || EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, s) > 0)
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
        if (!infinite && !p.inventory.hasItem(Items.arrow)) return;
        float f = charge / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f < 0.1F) return;
        if (f > 1.0F) f = 1.0F;
        if (!infinite) p.inventory.consumeInventoryItem(Items.arrow);
        w.playSoundAtEntity(p, "random.bow", 1.0F, 1.0F / (itemRand.nextFloat() * 0.4F + 1.2F) + f * 0.5F);
        w.playSoundAtEntity(p, "random.splash", 0.5F, 1.4F);
        if (w.isRemote) return;
        int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, s), punch = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, s);
        boolean flame = EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, s) > 0;
        for (int i = 0; i < ARROWS; i++) {
            float yaw = (i - (ARROWS - 1) / 2F) * SPREAD;
            EntityArrow a = new net.divinerpg.entities.base.EntityDivineArrow(w, p, f * 2.0F, DMG_MIN, DMG_MAX, "wildwoodArrow");
            if (yaw != 0) turn(a, yaw);
            if (f == 1.0F) a.setIsCritical(true);
            if (power > 0) a.setDamage(a.getDamage() + power * 0.5D + 0.5D);
            if (punch > 0) a.setKnockbackStrength(punch);
            if (flame) a.setFire(100);
            a.canBePickedUp = infinite || yaw != 0 ? 2 : 1; // подобрать - только среднюю
            w.spawnEntityInWorld(a);
        }
    }

    /** Повернуть скорость стрелы вокруг вертикали. */
    static void turn(EntityArrow a, float deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), sn = Math.sin(r);
        double mx = a.motionX * c - a.motionZ * sn, mz = a.motionX * sn + a.motionZ * c;
        a.motionX = mx;
        a.motionZ = mz;
        a.rotationYaw = (float) (Math.atan2(mx, mz) * 180.0D / Math.PI);
        a.prevRotationYaw = a.rotationYaw;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(net.minecraft.client.renderer.texture.IIconRegister reg) {
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
        l.add(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("dre.tooltip.tsunami", ARROWS, (int) DMG_MIN, (int) DMG_MAX));
    }
}
