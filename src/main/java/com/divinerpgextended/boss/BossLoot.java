package com.divinerpgextended.boss;

import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Добыча с любого босса (наши, Divine и ванильные - все, у кого полоска здоровья босса) неуязвима, как звезда Незера
 * с иссушителя, только надёжнее: её не уничтожают ни взрывы, ни огонь, ни лава. Флаг Invulnerable хранится в NBT
 * предмета на земле и переживает перезагрузку мира; исчезает такой лут через обычные 5 минут.
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
