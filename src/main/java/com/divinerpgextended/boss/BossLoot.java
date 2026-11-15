package com.divinerpgextended.boss;

import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Loot from any boss (ours, Divine and vanilla - everything with a boss health bar) is invulnerable, like the Nether star
 * from the Wither, only more reliable: neither explosions, nor fire, nor lava destroy it. The Invulnerable flag is stored in the NBT
 * of the item on the ground and survives a world reload; such loot disappears after the usual 5 minutes.
 */
public class BossLoot {

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (!(event.entityLiving instanceof IBossDisplayData)) return;
        for (EntityItem item : event.drops) {
            NBTTagCompound tag = new NBTTagCompound();
            item.writeToNBT(tag);
            tag.setBoolean("Invulnerable", true);
            item.readFromNBT(tag);
        }
    }
}
