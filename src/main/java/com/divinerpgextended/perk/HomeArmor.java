package com.divinerpgextended.perk;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Броня дома: полный сет сумеречного мира в своём мире (Эдем в Эдеме, Вайлдвуд в Вайлдвуде...) снижает урон от мобов
 * на {@link #CUT}. Ради неё сет мира стоит собрать, прежде чем лезть к его боссу.
 */
public class HomeArmor {

    public static final float CUT = 0.3F;

    public static boolean atHome(EntityPlayer p) {
        TwilightWorld tw = TwilightWorld.of(p.dimension);
        if (tw == null) return false;
        Item[] set = tw.armor();
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(3 - i);
            if (s == null || s.getItem() != set[i]) return false;
        }
        return true;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote) return;
        if (!(e.source.getEntity() instanceof EntityLivingBase) || e.source.getEntity() instanceof EntityPlayer) return;
        if (!atHome((EntityPlayer) e.entityLiving)) return;
        e.ammount *= 1 - CUT;
        com.divinerpgextended.debug.Debug.note(e.entityLiving, "Броня дома: урон мобов x" + (1 - CUT));
    }
}
