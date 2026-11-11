package com.divinerpgextended.fishing;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.item.ItemSoulHarvest;
import com.divinerpgextended.registry.ModItems;

/**
 * Блуждающая душа - ловится в Мортуме (в Лете и тёмной воде). ПКМ: отдаёт {@link #SOULS} души Жатве душ из инвентаря (до полного
 * запаса) и тает. Нет Жатвы - ничего не тратится.
 */
public class ItemWanderingSoul extends Item {

    public static final int SOULS = 3;

    public ItemWanderingSoul() {
        setUnlocalizedName("dre.wanderingSoul");
        setTextureName("divinerpgextended:wandering_soul");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        if (w.isRemote) return s;
        ItemStack scythe = null;
        for (ItemStack it : p.inventory.mainInventory) if (it != null && it.getItem() == ModItems.soulHarvest) {
            scythe = it;
            break;
        }
        ChatComponentTranslation m;
        if (scythe == null) {
            m = new ChatComponentTranslation("dre.chat.soul_no_harvest");
            m.getChatStyle().setColor(EnumChatFormatting.GRAY);
        } else {
            long now = w.getTotalWorldTime();
            int was = ItemSoulHarvest.souls(scythe, now);
            ItemSoulHarvest.setSouls(scythe, was + SOULS, now);
            if (!p.capabilities.isCreativeMode) s.stackSize--;
            m = new ChatComponentTranslation("dre.chat.soul_charged", ItemSoulHarvest.souls(scythe, now), ItemSoulHarvest.MAX);
            m.getChatStyle().setColor(EnumChatFormatting.RED);
            w.playSoundAtEntity(p, "mob.wither.idle", 0.4F, 1.6F);
            p.inventoryContainer.detectAndSendChanges();
        }
        p.addChatMessage(m);
        return s;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List l, boolean adv) {
        l.add(EnumChatFormatting.RED + StatCollector.translateToLocalFormatted("dre.tooltip.wandering_soul", SOULS));
    }
}
