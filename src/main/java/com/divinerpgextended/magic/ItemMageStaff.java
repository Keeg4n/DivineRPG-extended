package com.divinerpgextended.magic;

import java.util.List;

import net.divinerpg.entities.twilight.projectile.EntityTwilightMageShot;
import net.divinerpg.utils.events.ArcanaHelper;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Посох мага сумеречного мира (падает с мага своего мира): стреляет его зарядом, арканный урон. Мета - мир: 0 Дикий лес
 * (Маг), 1 Апалачия (Чародей-связыватель), 2 Небесный терн (Мистик), 3 Мортум (Колдун).
 */
public class ItemMageStaff extends Item {

    public static final String OWNER = "ItemMageStaff";
    static final String[] KEYS = { "wildwood", "apalachia", "skythern", "mortum" };
    static final String[] MAGES = { "EntityMage", "EntitySpellbinder", "EntityMystic", "EntitySorcerer" };
    static final float[] DAMAGE = { 12F, 16F, 20F, 24F };
    static final int[][] COLOR = { { 62, 212, 254 }, { 180, 90, 255 }, { 235, 235, 255 }, { 200, 40, 50 } };

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemMageStaff() {
        setHasSubtypes(true);
        setMaxStackSize(1);
        setUnlocalizedName("dre.mageStaff");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
        setFull3D();
    }

    static int meta(ItemStack s) {
        return Math.max(0, Math.min(KEYS.length - 1, s.getItemDamage()));
    }

    public static float damage(ItemStack s) {
        return DAMAGE[meta(s)];
    }

    /** Мета посоха для этого мага или -1. */
    public static int metaOf(Entity mage) {
        String n = mage.getClass().getSimpleName();
        for (int i = 0; i < MAGES.length; i++) if (MAGES[i].equals(n)) return i;
        return -1;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        if (w.isRemote) return s;
        ArcanaHelper h = ArcanaHelper.getProperties(p);
        if (!ArcanaRules.useBar(h, ArcanaRules.MAGE_STAFF, OWNER)) return s;
        int[] c = COLOR[meta(s)];
        EntityTwilightMageShot shot = new EntityTwilightMageShot(w, p, c[0], c[1], c[2]);
        w.spawnEntityInWorld(shot);
        w.playSoundAtEntity(p, "mob.blaze.hit", 0.6F, 1.6F + w.rand.nextFloat() * 0.2F);
        return s;
    }

    @Override
    public String getUnlocalizedName(ItemStack s) {
        return "item.dre.mageStaff." + KEYS[meta(s)];
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocalFormatted("dre.tooltip.arcana_cost", (int) ArcanaRules.MAGE_STAFF));
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("dre.tooltip.arcana_damage",
            String.format("%.0f", damage(s) * ArcanaRules.stage(p))));
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < KEYS.length; i++) list.add(new ItemStack(item, 1, i));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) icons[i] = reg.registerIcon("divinerpgextended:mage_staff_" + KEYS[i]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int m) {
        return icons[Math.max(0, Math.min(icons.length - 1, m))];
    }
}
