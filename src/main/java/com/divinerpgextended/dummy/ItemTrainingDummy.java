package com.divinerpgextended.dummy;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/** Манекен для битья: ПКМ по верху блока - поставить свою копию. */
public class ItemTrainingDummy extends Item {

    public ItemTrainingDummy() {
        setUnlocalizedName("dre.trainingDummy");
        setTextureName("divinerpgextended:training_dummy");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
        setMaxStackSize(16);
    }

    @Override
    public boolean onItemUse(ItemStack s, EntityPlayer p, World w, int x, int y, int z, int side, float hx, float hy, float hz) {
        if (side != 1) return false;
        if (w.isRemote) return true;
        if (!w.isAirBlock(x, y + 1, z) || !w.isAirBlock(x, y + 2, z)) return false;
        EntityTrainingDummy d = new EntityTrainingDummy(w);
        d.setLocationAndAngles(x + 0.5, y + 1, z + 0.5, p.rotationYaw + 180F, 0F);
        d.rotationYawHead = d.renderYawOffset = d.rotationYaw;
        d.copy(p);
        d.setPlacer(p);
        w.spawnEntityInWorld(d);
        if (!p.capabilities.isCreativeMode) s.stackSize--;
        return true;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        for (String l : StatCollector.translateToLocal("dre.dummy.tooltip").split("\\\\n")) list.add(EnumChatFormatting.GRAY + l);
    }
}
