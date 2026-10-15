package com.divinerpgextended.fishing;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/** Справочник рыбака (награда Леорны): ПКМ - зона под ногами, сила снасти, рыба дня и прогресс заданий. */
public class ItemFishGuide extends Item {

    public ItemFishGuide() {
        setUnlocalizedName("dre.fishGuide");
        setTextureName("divinerpgextended:fishGuide");
        setMaxStackSize(1);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        if (w.isRemote) return s;
        Zone zone = Zone.at(w, p.posX, p.posY, p.posZ, false);
        int slot = Bait.find(p);
        Bait bait = slot < 0 ? null : Bait.of(p.inventory.mainInventory[slot].getItem());
        Rod rod = FishingGear.bestRod(p);
        say(p, EnumChatFormatting.AQUA, "dre.guide.zone", tr("dre.zone." + zone.key()));
        if (rod == null) {
            say(p, EnumChatFormatting.GRAY, "dre.guide.no_rod");
        } else {
            String baitName = slot < 0 ? tr("dre.guide.none") : p.inventory.mainInventory[slot].getDisplayName();
            say(p, EnumChatFormatting.AQUA, "dre.guide.power", String.valueOf(FishingGear.power(w, p, rod, bait, zone)),
                String.valueOf(rod.power), baitName, String.valueOf(FishingGear.armorPower(p)));
        }
        Fish f = FishingEvents.questFish(w);
        say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.guide.quest", tr("item.dre.fish." + f.key() + ".name"),
            tr("dre.zone." + f.zone.key()));
        FishingEvents.progress(p);
        return s;
    }

    private static String tr(String k) {
        return StatCollector.translateToLocal(k);
    }

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("dre.tooltip.fish_guide"));
    }
}
