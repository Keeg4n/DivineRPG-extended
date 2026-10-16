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
 * на {@link #CUT}, закалённый ({@link com.divinerpgextended.item.TemperRecipe}) - на {@link #TEMPERED_CUT} и защищает от
 * приёма кадиллиона своего мира. Ради неё сет мира стоит собрать и закалить, прежде чем лезть к его боссу.
 */
public class HomeArmor {

    public static final float CUT = 0.3F, TEMPERED_CUT = 0.5F;

    /** Полный сет этого мира на игроке; tempered - и все части закалены. */
    public static boolean wears(EntityPlayer p, TwilightWorld tw, boolean tempered) {
        Item[] set = tw.armor();
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(3 - i);
            if (s == null || s.getItem() != set[i]) return false;
            if (tempered && !com.divinerpgextended.item.TemperRecipe.tempered(s)) return false;
        }
        return true;
    }

    public static boolean atHome(EntityPlayer p) {
        TwilightWorld tw = TwilightWorld.of(p.dimension);
        return tw != null && wears(p, tw, false);
    }

    /** Закалённый сет какого мира надет (или null). */
    public static TwilightWorld temperedSet(EntityPlayer p) {
        for (TwilightWorld w : TwilightWorld.values()) if (wears(p, w, true)) return w;
        return null;
    }

    /**
     * Закалённый сет защищает от приёма кадиллиона своего мира: Эдем - от ослепления, Вайлдвуд - от замедления
     * лианами, Скайтерн - от молнии (см. onHurt), Мортум - от иссушения; Апалачия - от урона при падении.
     */
    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void onUpdate(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || e.entityLiving.ticksExisted % 5 != 0) return;
        TwilightWorld w = temperedSet((EntityPlayer) e.entityLiving);
        if (w == null) return;
        int potion = w == TwilightWorld.EDEN ? net.minecraft.potion.Potion.blindness.id : w == TwilightWorld.WILDWOOD
            ? net.minecraft.potion.Potion.moveSlowdown.id : w == TwilightWorld.MORTUM ? net.minecraft.potion.Potion.wither.id : -1;
        if (potion >= 0 && e.entityLiving.isPotionActive(potion)) e.entityLiving.removePotionEffect(potion);
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void onFall(net.minecraftforge.event.entity.living.LivingFallEvent e) {
        if (e.entityLiving instanceof EntityPlayer && temperedSet((EntityPlayer) e.entityLiving) == TwilightWorld.APALACHIA) e.distance = 0;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        if ("lightningBolt".equals(e.source.getDamageType()) && temperedSet(p) == TwilightWorld.SKYTHERN) {
            e.ammount = 0;
            return;
        }
        if (!(e.source.getEntity() instanceof EntityLivingBase) || e.source.getEntity() instanceof EntityPlayer) return;
        if (!atHome(p)) return;
        float cut = wears(p, TwilightWorld.of(p.dimension), true) ? TEMPERED_CUT : CUT;
        e.ammount *= 1 - cut;
        com.divinerpgextended.debug.Debug.note(e.entityLiving, "Броня дома: урон мобов x" + (1 - cut));
    }
}
