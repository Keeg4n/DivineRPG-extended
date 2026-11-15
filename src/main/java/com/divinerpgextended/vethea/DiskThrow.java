package com.divinerpgextended.vethea;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.divinerpgextended.entity.EntityTomahawk;
import com.divinerpgextended.item.ItemTomahawk;

/**
 * Vethea disks are thrown like a tomahawk ({@link EntityTomahawk}): not consumed, hit up to three targets in a row and return to
 * the hand themselves; while in flight, a second one cannot be thrown. In Divine the disk left the stack and flew back and forth until caught.
 * The Divine disk's right-click goes entirely here ({@link com.divinerpgextended.asm.DiskTransformer}), on the client too - the stack does not shrink.
 */
public final class DiskThrow {

    private static java.lang.reflect.Field damage;

    private DiskThrow() {}

    /** Disk damage from Divine (damage field); no field - the damage of the weakest. */
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
