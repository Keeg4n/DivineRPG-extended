package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Жатва душ (реликварий Мортума): меч на материале Мортума Divine, не ломается. Каждое убийство им даёт душу (до {@link #MAX}),
 * каждая душа - +{@link #PER_SOUL} урона; без убийств души улетучиваются по одной за {@link #DECAY_TICKS} тиков. На полном запасе
 * следующий удар тратит все души на взрыв душ: {@link #BLAST_SHARE} урона удара по всем мобам вокруг (радиус
 * {@link #BLAST_RADIUS}), игроков и чужих питомцев не задевает ({@link com.divinerpgextended.perk.RelicEffects}).
 */
public class ItemSoulHarvest extends ItemSword {

    public static final String SOULS = "dreSouls", TIME = "dreSoulTime";
    public static final int MAX = 10, DECAY_TICKS = 200;
    public static final float PER_SOUL = 0.02F, BLAST_SHARE = 0.5F, BLAST_RADIUS = 4F;

    public ItemSoulHarvest() {
        super(net.divinerpg.utils.material.ToolMaterialMod.Mortum);
        setMaxDamage(0);
        setUnlocalizedName("dre.soulHarvest");
        setTextureName("divinerpgextended:soul_harvest");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public boolean isDamageable() {
        return false;
    }

    private static NBTTagCompound tag(ItemStack s) {
        if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
        return s.getTagCompound();
    }

    /** Души сейчас (с учётом улетучивания; пишет остаток обратно). */
    public static int souls(ItemStack s, long now) {
        if (s == null || !s.hasTagCompound()) return 0;
        NBTTagCompound t = s.getTagCompound();
        int n = t.getInteger(SOULS);
        if (n <= 0) return 0;
        long since = now - t.getLong(TIME);
        int gone = since <= 0 ? 0 : (int) Math.min(n, since / DECAY_TICKS);
        if (gone > 0) {
            n -= gone;
            t.setInteger(SOULS, n);
            t.setLong(TIME, t.getLong(TIME) + gone * (long) DECAY_TICKS);
        }
        return n;
    }

    public static void setSouls(ItemStack s, int n, long now) {
        NBTTagCompound t = tag(s);
        t.setInteger(SOULS, Math.max(0, Math.min(MAX, n)));
        t.setLong(TIME, now);
    }

    /** Добавить душу (убийство или улов души в Мортуме). */
    public static void addSoul(ItemStack s, long now) {
        setSouls(s, souls(s, now) + 1, now);
    }

    @Override
    public void onUpdate(ItemStack s, World w, Entity e, int slot, boolean held) {
        if (!w.isRemote && w.getTotalWorldTime() % 20 == 0) souls(s, w.getTotalWorldTime());
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List l, boolean adv) {
        int n = s.hasTagCompound() ? s.getTagCompound().getInteger(SOULS) : 0;
        l.add(EnumChatFormatting.RED + StatCollector.translateToLocalFormatted("dre.tooltip.soul_harvest", (int) (PER_SOUL * 100), (int) (PER_SOUL * MAX * 100), DECAY_TICKS / 20));
        l.add(EnumChatFormatting.DARK_RED + StatCollector.translateToLocalFormatted("dre.tooltip.soul_harvest_blast", (int) (BLAST_SHARE * 100)));
        l.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.soul_harvest_now", n, MAX));
    }
}
