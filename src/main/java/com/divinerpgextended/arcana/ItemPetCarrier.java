package com.divinerpgextended.arcana;

import java.util.Iterator;
import java.util.List;

import net.divinerpg.entities.base.InfiniteTrade;
import net.divinerpg.entities.vanilla.EntityLivestockMerchant;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Facing;
import net.minecraft.util.StatCollector;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Переноска питомцев (продаёт Торговец скотом). ПКМ по своему питомцу - он сворачивается в переноску со всем
 * (имя, здоровье, уровень дрессировки); ПКМ по блоку - выпустить. Так питомца можно унести в другой мир.
 */
public class ItemPetCarrier extends Item {

    private static final String PET = "dreCarriedPet";

    public ItemPetCarrier() {
        setMaxStackSize(1);
        setUnlocalizedName("dre.petCarrier");
        setTextureName("divinerpgextended:petCarrier");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    private static NBTTagCompound pet(ItemStack s) {
        return s.hasTagCompound() && s.getTagCompound().hasKey(PET) ? s.getTagCompound().getCompoundTag(PET) : null;
    }

    @Override
    public boolean hasEffect(ItemStack s, int pass) {
        return pet(s) != null;
    }

    @Override
    public boolean onItemUse(ItemStack s, EntityPlayer p, World w, int x, int y, int z, int side, float hx, float hy, float hz) {
        NBTTagCompound tag = pet(s);
        if (tag == null) return false;
        if (w.isRemote) return true;
        Entity e = EntityList.createEntityFromNBT(tag, w);
        if (e == null) return false;
        e.setLocationAndAngles(x + 0.5 + Facing.offsetsXForSide[side], y + Facing.offsetsYForSide[side], z + 0.5 + Facing.offsetsZForSide[side],
            p.rotationYaw, 0);
        if (!w.spawnEntityInWorld(e)) return false; // выпуск запрещён (приват и т.п.) - питомец остаётся в переноске
        s.getTagCompound().removeTag(PET);
        w.playSoundAtEntity(e, "random.pop", 0.6F, 0.8F);
        return true;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        NBTTagCompound tag = pet(s);
        if (tag == null) {
            list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.carrier_empty"));
        } else {
            String name = tag.hasKey("CustomName") && tag.getString("CustomName").length() > 0 ? tag.getString("CustomName")
                : StatCollector.translateToLocal("entity." + tag.getString("id") + ".name");
            int lvl = tag.getCompoundTag("ForgeData").getInteger("dreLevel");
            list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("dre.tooltip.carrier_full", name, lvl));
        }
    }

    public static class Handler {

        /** ПКМ переноской по своему питомцу - свернуть. */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onInteract(EntityInteractEvent e) {
            EntityPlayer p = e.entityPlayer;
            ItemStack held = p.getCurrentEquippedItem();
            if (held != null && held.getItem() == ModItems.petCarrier && e.target instanceof EntityTameable) {
                EntityTameable t = (EntityTameable) e.target;
                e.setCanceled(true);
                if (p.worldObj.isRemote || pet(held) != null || t.isDead) return; // мёртв = уже свёрнут этим же тиком (двойной клик)
                if (!t.isTamed() || t.getOwner() != p) {
                    p.addChatMessage(new ChatComponentTranslation("dre.chat.carrier_not_yours"));
                    return;
                }
                NBTTagCompound tag = new NBTTagCompound();
                if (!t.writeToNBTOptional(tag)) return;
                if (!held.hasTagCompound()) held.setTagCompound(new NBTTagCompound());
                held.getTagCompound().setTag(PET, tag);
                t.setDead();
                p.worldObj.playSoundAtEntity(p, "random.pop", 0.6F, 1.4F);
                p.inventoryContainer.detectAndSendChanges();
                return;
            }
            // Торговец скотом продаёт переноску (окно торговли Divine строит список на клиенте - дописываем на обеих сторонах)
            if (e.target instanceof EntityLivestockMerchant && !p.isSneaking()) {
                MerchantRecipeList list = ((EntityLivestockMerchant) e.target).getRecipes(p);
                if (list == null) return;
                for (Iterator<?> it = list.iterator(); it.hasNext();) {
                    ItemStack sell = ((MerchantRecipe) it.next()).getItemToSell();
                    if (sell != null && sell.getItem() == ModItems.petCarrier) it.remove();
                }
                list.add(new InfiniteTrade(new ItemStack(Items.leather, 8), new ItemStack(VanillaItemsOther.shadowCoins, 6),
                    new ItemStack(ModItems.petCarrier)));
            }
        }
    }
}
