package com.divinerpgextended.halite;

import java.util.Iterator;
import java.util.List;

import net.divinerpg.entities.vanilla.EntityAyeraco;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import com.divinerpgextended.boss.Boss;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Дроп боссов Мортума: готовых халитовых вещей нет (Divine их роняет - убираем), вместо них осколки халита. Близнец,
 * павший при живом брате, ничего не роняет - победа ещё не одержана. Частичка Края - тем, кто рядом с последним Айрако.
 * Трофеи и прочие частички выдаёт учёт боссов участникам ({@link Halite#reward}).
 */
public class HaliteDrops {

    static boolean mortum(Boss b) {
        return b != null && Halite.trophyOf(b) != null;
    }

    static boolean halite(Item i) {
        Object n = Item.itemRegistry.getNameForObject(i);
        return n != null && n.toString().toLowerCase().contains("halite") && !n.toString().startsWith("divinerpgextended");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDrops(LivingDropsEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote) return;
        Boss b = Boss.of(v);
        if (!mortum(b)) return;
        for (Iterator<EntityItem> it = e.drops.iterator(); it.hasNext();) if (halite(it.next().getEntityItem().getItem())) it.remove();
        if (b == Boss.TWINS && com.divinerpgextended.boss.MortumArenaBosses.brotherAlive(v)) return;
        if (v.getEntityData().getBoolean(com.divinerpgextended.boss.MortumArenaBosses.REVIVED)) return; // поднятый братом - без трофеев
        int n = Halite.SHARDS_MIN + v.worldObj.rand.nextInt(Halite.SHARDS_MAX - Halite.SHARDS_MIN + 1);
        e.drops.add(new EntityItem(v.worldObj, v.posX, v.posY + 1, v.posZ, Halite.part(Halite.Part.SHARD, n)));
    }

    /** Последний из пяти Айрако: частичка Края всем, кто рядом. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.entityLiving.worldObj.isRemote || !(e.entityLiving instanceof EntityAyeraco)) return;
        List<?> flock = e.entityLiving.worldObj.getEntitiesWithinAABB(EntityAyeraco.class, e.entityLiving.boundingBox.expand(128, 96, 128));
        for (Object o : flock) if (o != e.entityLiving && ((EntityAyeraco) o).isEntityAlive()) return;
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = e.entityLiving.worldObj.getEntitiesWithinAABB(EntityPlayer.class, e.entityLiving.boundingBox.expand(64, 48, 64));
        for (EntityPlayer p : near) Halite.give(p, Halite.spark(Halite.Spark.AYERACO));
    }
}
