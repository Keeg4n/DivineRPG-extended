package com.divinerpgextended.boss;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

/**
 * Личная добыча с боссов (кроме аксессуаров - те в {@link com.divinerpgextended.trinket.AccessorySources}): каждому бившему,
 * за первую победу (если вещь гарантирована) - наверняка, дальше с шансом. Падает у тела босса, помечена владельцем и
 * неуязвима, как аксессуары.
 * <ul>
 * <li>Карцинус - крабовый якорь (призыв краба): первая победа наверняка, потом {@link #CRAB_ANCHOR}.</li>
 * <li>Лиоплевродон - лук Цунами: первая победа наверняка, потом {@link #TSUNAMI}; якорь лиоплевродона (призыв акулы) -
 * {@link #LIO_ANCHOR} каждый раз.</li>
 * </ul>
 */
public final class BossDrops {

    public static final float CRAB_ANCHOR = 0.25F, TSUNAMI = 0.3F, LIO_ANCHOR = 0.3F;
    private static final String GOT = "dreBossItems";
    /** Для самопроверки: бросок вместо случайного (меньше нуля - обычный). */
    public static float testRoll = -1F;

    private BossDrops() {}

    /** Награды игроку за победу над боссом. */
    public static void reward(EntityPlayer p, Boss b, Entity at) {
        if (p.worldObj.isRemote) return;
        if (b == Boss.CARCINUS) give(p, "crabAnchor", new ItemStack(net.divinerpg.utils.items.VanillaItemsWeapons.crabAnchor), true, CRAB_ANCHOR, at);
        if (b == Boss.LIOPLEURODON) {
            give(p, "tsunami", new ItemStack(com.divinerpgextended.registry.ModItems.tsunami), true, TSUNAMI, at);
            give(p, "lioAnchor", new ItemStack(net.divinerpg.utils.items.VanillaItemsWeapons.liopleurodonAnchor), false, LIO_ANCHOR, at);
        }
    }

    private static float roll(EntityPlayer p) {
        return testRoll >= 0 ? testRoll : p.worldObj.rand.nextFloat();
    }

    /** Выдать вещь: первая победа - наверняка (если first), иначе с шансом. Возвращает выпавшее или null. */
    public static EntityItem give(EntityPlayer p, String key, ItemStack s, boolean first, float chance, Entity at) {
        if (s == null || s.getItem() == null) return null;
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        NBTTagCompound got = d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag(GOT);
        boolean firstTime = first && !got.getBoolean(key);
        if (!firstTime && roll(p) >= chance) return null;
        got.setBoolean(key, true);
        d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).setTag(GOT, got);
        ItemStack own = com.divinerpgextended.halite.Halite.own(s, p);
        Entity src = at != null && at.worldObj == p.worldObj ? at : p;
        EntityItem it = new EntityItem(p.worldObj, src.posX, src.posY + 1, src.posZ, own);
        it.delayBeforeCanPickup = 10;
        NBTTagCompound tag = new NBTTagCompound();
        it.writeToNBT(tag);
        tag.setBoolean("Invulnerable", true);
        it.readFromNBT(tag);
        p.worldObj.spawnEntityInWorld(it);
        ChatComponentTranslation m = new ChatComponentTranslation("dre.chat.boss_item_drop", own.getDisplayName());
        m.getChatStyle().setColor(EnumChatFormatting.LIGHT_PURPLE);
        p.addChatMessage(m);
        return it;
    }
}
