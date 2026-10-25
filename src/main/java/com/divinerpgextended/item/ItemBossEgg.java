package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.entity.*;
import com.divinerpgextended.world.CadillionWorld;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Яйца призыва боссов аддона (как ванильные яйца: клик по блоку - босс на нём). Для администраторов и проверки: босс
 * появляется без своего логова, награды и этапы - как за обычное убийство.
 */
public class ItemBossEgg extends Item {

    /** Босс, вариант (мир кадиллиона) и цвета яйца: скорлупа, пятна. */
    public enum Boss {
        CARCINUS(EntityCarcinus.class, -1, 0xC87B3A, 0x6E2A12),
        SNOW_GLACON(EntitySnowGlacon.class, -1, 0xE6F4FF, 0x5BA7D6),
        FROST_COLOSSUS(EntityFrostColossus.class, -1, 0xA9D8F0, 0x3D6E8F),
        LICH_KING(EntityLichKing.class, -1, 0x2A3B4C, 0x7FE3FF),
        CADILLION_EDEN(EntityGreatCadillion.class, 0, 0xD9B43A, 0x6B4E10),
        CADILLION_WILDWOOD(EntityGreatCadillion.class, 1, 0x2E6FD0, 0x0D2A5C),
        CADILLION_APALACHIA(EntityGreatCadillion.class, 2, 0xB047C8, 0x4A1258),
        CADILLION_SKYTHERN(EntityGreatCadillion.class, 3, 0xCCCCCC, 0x555555),
        ANCIENT_GOLEM(EntityAncientGolem.class, -1, 0x5B7F3A, 0x8A8A80),
        WARRIOR_KING(EntityWarriorKing.class, -1, 0x8E3CAA, 0xE0C060),
        WIND_LORD(EntityWindLord.class, -1, 0xEFEFEF, 0x8FB8E0),
        ANCIENT_DRAMCRYX(EntityAncientDramcryx.class, -1, 0x3E5E2A, 0xB8962E);

        final Class<? extends EntityLiving> cls;
        final int variant, shell, spots;

        Boss(Class<? extends EntityLiving> cls, int variant, int shell, int spots) {
            this.cls = cls;
            this.variant = variant;
            this.shell = shell;
            this.spots = spots;
        }

        public String entityName() {
            return cls.getSimpleName().substring("Entity".length());
        }
    }

    @SideOnly(Side.CLIENT)
    private IIcon overlay;

    public ItemBossEgg() {
        setHasSubtypes(true);
        setUnlocalizedName("dre.bossEgg");
        setTextureName("spawn_egg");
        setCreativeTab(CreativeTabs.tabMisc);
    }

    static Boss boss(ItemStack s) {
        Boss[] b = Boss.values();
        return b[Math.max(0, Math.min(s.getItemDamage(), b.length - 1))];
    }

    /** Босс из яйца в точке (null - не вышло). */
    public static EntityLiving spawn(World w, ItemStack s, double x, double y, double z) {
        Boss b = boss(s);
        EntityLiving e;
        try {
            e = b.cls.getConstructor(World.class).newInstance(w);
        } catch (Exception ex) {
            return null;
        }
        if (b.variant >= 0) ((EntityGreatCadillion) e).setVariant(CadillionWorld.byMeta(b.variant).ordinal());
        e.setLocationAndAngles(x, y, z, w.rand.nextFloat() * 360.0F, 0.0F);
        e.rotationYawHead = e.rotationYaw;
        e.renderYawOffset = e.rotationYaw;
        e.onSpawnWithEgg(null);
        if (s.hasDisplayName()) e.setCustomNameTag(s.getDisplayName());
        w.spawnEntityInWorld(e);
        e.playLivingSound();
        return e;
    }

    @Override
    public boolean onItemUse(ItemStack s, EntityPlayer p, World w, int x, int y, int z, int side, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        x += Facing.offsetsXForSide[side];
        y += Facing.offsetsYForSide[side];
        z += Facing.offsetsZForSide[side];
        if (spawn(w, s, x + 0.5, y, z + 0.5) != null && !p.capabilities.isCreativeMode) --s.stackSize;
        return true;
    }

    @Override
    public String getItemStackDisplayName(ItemStack s) {
        return StatCollector.translateToLocalFormatted("item.dre.bossEgg.name",
            StatCollector.translateToLocal("entity.divinerpgextended." + boss(s).entityName() + ".name"))
            + (boss(s).variant >= 0 ? " (" + StatCollector.translateToLocal("dre.world." + CadillionWorld.byMeta(boss(s).variant).key()) + ")" : "");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack s, int pass) {
        return pass == 0 ? boss(s).shell : boss(s).spots;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamageForRenderPass(int meta, int pass) {
        return pass > 0 ? overlay : itemIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister r) {
        itemIcon = r.registerIcon(getIconString());
        overlay = r.registerIcon(getIconString() + "_overlay");
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item i, CreativeTabs tab, List l) {
        for (Boss b : Boss.values()) l.add(new ItemStack(i, 1, b.ordinal()));
    }
}
