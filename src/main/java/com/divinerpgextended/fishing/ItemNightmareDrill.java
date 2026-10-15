package com.divinerpgextended.fishing;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.EnumHelper;

import com.divinerpgextended.Config;

/**
 * Кошмарный бур - реликвия рыбалки в Ветее. Кирка, лопата и топор сразу, не ломается, копает очень быстро и
 * 3x3 по плоскости удара (присев - один блок; 3x3 выключается в конфиге).
 */
public class ItemNightmareDrill extends ItemPickaxe {

    public static final ToolMaterial MATERIAL = EnumHelper.addToolMaterial("DRE_NIGHTMARE", 10, 0, 24.0F, 8.0F, 22);
    private static final Set<String> CLASSES = new HashSet<String>();
    static {
        CLASSES.add("pickaxe");
        CLASSES.add("shovel");
        CLASSES.add("axe");
    }

    private boolean breaking;

    public ItemNightmareDrill() {
        super(MATERIAL);
        setMaxDamage(0);
        setUnlocalizedName("dre.nightmareDrill");
        setTextureName("divinerpgextended:nightmareDrill");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
        for (String c : CLASSES) setHarvestLevel(c, 10);
    }

    @Override
    public Set<String> getToolClasses(ItemStack s) {
        return CLASSES;
    }

    @Override
    public int getHarvestLevel(ItemStack s, String toolClass) {
        return 10;
    }

    @Override
    public boolean func_150897_b(Block b) { // может добыть любой блок
        return true;
    }

    @Override
    public float getDigSpeed(ItemStack s, Block b, int meta) {
        return b.getMaterial() == Material.air ? 1.0F : MATERIAL.getEfficiencyOnProperMaterial();
    }

    @Override
    public boolean isDamageable() {
        return false;
    }

    @Override
    public EnumRarity getRarity(ItemStack s) {
        return EnumRarity.epic;
    }

    /** Ломает соседей 3x3 в плоскости, на которую смотрит игрок, обычным путём игрока (дроп, защита привата). */
    @Override
    public boolean onBlockStartBreak(ItemStack s, int x, int y, int z, EntityPlayer p) {
        World w = p.worldObj;
        if (w.isRemote || breaking || !Config.drillArea || p.isSneaking() || !(p instanceof EntityPlayerMP)) return false;
        MovingObjectPosition hit = getMovingObjectPositionFromPlayer(w, p, false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return false;
        float center = w.getBlock(x, y, z).getBlockHardness(w, x, y, z);
        int side = hit.sideHit;
        breaking = true;
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    int nx = x, ny = y, nz = z;
                    if (side == 0 || side == 1) {
                        nx += a;
                        nz += b;
                    } else if (side == 2 || side == 3) {
                        nx += a;
                        ny += b;
                    } else {
                        nz += a;
                        ny += b;
                    }
                    Block nb = w.getBlock(nx, ny, nz);
                    if (nb.getMaterial() == Material.air || nb.getMaterial().isLiquid()) continue;
                    float h = nb.getBlockHardness(w, nx, ny, nz);
                    if (h < 0 || h > center * 4 + 2) continue; // бедрок не берёт, обсидиан вокруг земли не срывает
                    ((EntityPlayerMP) p).theItemInWorldManager.tryHarvestBlock(nx, ny, nz);
                }
            }
        } finally {
            breaking = false;
        }
        return false;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("dre.tooltip.drill1"));
        if (Config.drillArea) list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.drill2"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.drill3"));
    }
}
