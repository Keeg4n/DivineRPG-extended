package com.divinerpgextended.halite;

import java.util.Iterator;
import java.util.List;

import net.divinerpg.entities.vanilla.EntityAyeraco;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.divinerpgextended.boss.Boss;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Boss loot: the Mortum bosses have no finished halite items (Divine drops them - we remove them). Each player nearby
 * ({@link Halite#SHARE_R}) gets their own share on the ground: shards, trophy, soul spark; only they can pick it up
 * ({@link #onPickup}). We put nothing into inventories. A Twin that fell while its brother is alive drops nothing - there is no victory yet.
 * The last of the Ayeraco pack - an End spark.
 */
public class HaliteDrops {

    static boolean mortum(Boss b) {
        return b != null && Halite.trophyOf(b) != null;
    }

    static boolean halite(Item i) {
        Object n = Item.itemRegistry.getNameForObject(i);
        return n != null && n.toString().toLowerCase().contains("halite") && !n.toString().startsWith("divinerpgextended");
    }

    @SuppressWarnings("unchecked")
    static List<EntityPlayer> near(EntityLivingBase v) {
        return v.worldObj.getEntitiesWithinAABB(EntityPlayer.class, v.boundingBox.expand(Halite.SHARE_R, Halite.SHARE_R / 2, Halite.SHARE_R));
    }

    static boolean lastAyeraco(EntityLivingBase v) {
        if (!(v instanceof EntityAyeraco)) return false;
        for (Object o : v.worldObj.getEntitiesWithinAABB(EntityAyeraco.class, v.boundingBox.expand(128, 96, 128)))
            if (o != v && ((EntityAyeraco) o).isEntityAlive()) return false;
        return true;
    }

    /** Earlier than the boss loot invulnerability - our shares will become invulnerable too. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDrops(LivingDropsEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote) return;
        Boss b = Boss.of(v);
        if (mortum(b)) for (Iterator<EntityItem> it = e.drops.iterator(); it.hasNext();) if (halite(it.next().getEntityItem().getItem())) it.remove();
        if (b == Boss.TWINS && com.divinerpgextended.boss.MortumArenaBosses.brotherAlive(v)) return;
        if (v.getEntityData().getBoolean(com.divinerpgextended.boss.MortumArenaBosses.REVIVED)) return; // picked up by the brother - no trophies
        boolean ayeraco = lastAyeraco(v);
        if (b == null && !ayeraco) return;
        for (EntityPlayer p : near(v)) {
            if (!com.divinerpgextended.boss.ProgressSpawns.bossReady(p, v)) continue; // did not reach this boss - no share
            List<ItemStack> share = ayeraco ? java.util.Collections.singletonList(Halite.own(Halite.spark(Halite.Spark.AYERACO), p)) : Halite.share(p, b, v.worldObj.rand);
            for (ItemStack s : share) {
                EntityItem it = new EntityItem(v.worldObj, v.posX, v.posY + 1, v.posZ, s);
                it.delayBeforeCanPickup = 10;
                e.drops.add(it);
            }
        }
    }

    /** Someone else's share cannot be picked up (your own - as usual). */
    @SubscribeEvent
    public void onPickup(EntityItemPickupEvent e) {
        String o = Halite.owner(e.item.getEntityItem());
        if (o != null && !o.equals(e.entityPlayer.getUniqueID().toString())) e.setCanceled(true);
    }

    /** In the tooltip - whose loot it is. */
    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        String n = Halite.ownerName(e.itemStack);
        if (n != null) e.toolTip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocalFormatted("dre.tooltip.loot_owner", n));
    }
}
