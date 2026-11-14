package com.divinerpgextended.vethea;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.divinerpgextended.entity.EntityTomahawk;
import com.divinerpgextended.item.ItemTomahawk;

/**
 * Диски Ветеи бросаются как томагавк ({@link EntityTomahawk}): не тратятся, бьют до трёх целей подряд и сами возвращаются в
 * руку, пока летят - второй раз не бросить. У Divine диск уходил из стопки и летал туда-обратно, пока его не поймают.
 * ПКМ диска Divine целиком идёт сюда ({@link com.divinerpgextended.asm.DiskTransformer}), на клиенте тоже - стопка не убывает.
 */
public final class DiskThrow {

    private static java.lang.reflect.Field damage;

    private DiskThrow() {}

    /** Урон диска по Divine (поле damage); нет поля - урон самого слабого. */
    public static float damage(ItemStack s) {
        try {
            if (damage == null) damage = cpw.mods.fml.relauncher.ReflectionHelper.findField(net.divinerpg.items.vethea.ItemVetheanDisk.class, "damage");
            return damage.getInt(s.getItem());
        } catch (Throwable t) {
            return 4F;
        }
    }

    public static ItemStack use(ItemStack s, World w, EntityPlayer p) {
        long now = w.getTotalWorldTime();
        if (ItemTomahawk.out(s, now)) return s;
        if (!w.isRemote) {
            w.spawnEntityInWorld(new EntityTomahawk(w, p, s, damage(s)));
            ItemTomahawk.setOut(s, now + EntityTomahawk.LIFE);
            w.playSoundAtEntity(p, "random.bow", 0.5F, 0.4F / (w.rand.nextFloat() * 0.4F + 0.8F));
        }
        p.swingItem();
        return s;
    }
}
