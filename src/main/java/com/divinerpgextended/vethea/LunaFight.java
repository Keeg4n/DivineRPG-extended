package com.divinerpgextended.vethea;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.divinerpg.entities.vethea.EntityLadyLuna;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Lady Luna is the last exam of Vethea, three phases by health instead of Divine's random defense:
 * <ol>
 * <li>on the ground: hurt only by melee (sword, claws, maul);</li>
 * <li>on wings she flies on an erratic trajectory (like the Eden cori): hurt by bow and staff, staff projectiles home in on
 * her;</li>
 * <li>explodes: lunar explosions flare around players (a mark first), hurt only by the cannon - fight fire with fire.</li>
 * </ol>
 * Everyone nearby gets Lunar Shards on her death: at the infusion altar an Arksiane weapon + shards = her weapon.
 */
public class LunaFight {

    public static final int SHARDS = 3, AREA = 48;
    static final String HOME = "dreLunaHome", MARKS = "dreLunaMarks";
    private static Method setType, setTimer;

    /** Phase 1..3 by health fraction (the client sees it too - for the wings). */
    public static int phase(EntityLadyLuna l) {
        float f = l.getHealth() / l.getMaxHealth();
        return f > 2F / 3 ? 1 : f > 1F / 3 ? 2 : 3;
    }

    /** Whether a hit of this kind hurts in this phase (foreign and bare-handed - no). */
    public static boolean hurts(int phase, WeaponKind k) {
        if (k == null) return false;
        switch (phase) {
            case 1: return k.group() == 0;
            case 2: return k == WeaponKind.BOW || k == WeaponKind.STAFF;
            default: return k == WeaponKind.CANNON;
        }
    }

    /** We turn off Divine's random defense: type 0, timer far away. */
    private static void noDivineProtection(EntityLadyLuna l) {
        try {
            if (setType == null) {
                setType = EntityLadyLuna.class.getMethod("setProtectionType", int.class);
                setTimer = EntityLadyLuna.class.getMethod("setProtectionTimer", int.class);
            }
            setType.invoke(l, 0);
            setTimer.invoke(l, 1000);
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (!(e.entityLiving instanceof EntityLadyLuna) || e.entityLiving.worldObj.isRemote) return;
        EntityLadyLuna l = (EntityLadyLuna) e.entityLiving;
        if (l.getHealth() <= 0) return;
        noDivineProtection(l);
        NBTTagCompound t = l.getEntityData();
        if (!t.hasKey(HOME)) t.setIntArray(HOME, new int[] { (int) l.posX, (int) l.posY, (int) l.posZ });
        int ph = phase(l);
        if (ph == 2) {
            fly(l, t.getIntArray(HOME));
            homeStaff(l);
        }
        if (ph == 3) blasts(l);
    }

    // ---- phase 2: flight and staff homing

    private void fly(EntityLadyLuna l, int[] home) {
        NBTTagCompound t = l.getEntityData();
        double wx = t.getDouble("dreWpX"), wy = t.getDouble("dreWpY"), wz = t.getDouble("dreWpZ");
        int cool = t.getInteger("dreWpCool") - 1;
        double dx = wx - l.posX, dy = wy - l.posY, dz = wz - l.posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (cool <= 0 || d < 1.5 || !t.hasKey("dreWpX")) { // like the cori: a new point often and sharply
            wx = home[0] + (l.getRNG().nextDouble() * 2 - 1) * 12;
            wy = home[1] + 4 + l.getRNG().nextDouble() * 7;
            wz = home[2] + (l.getRNG().nextDouble() * 2 - 1) * 12;
            t.setDouble("dreWpX", wx);
            t.setDouble("dreWpY", wy);
            t.setDouble("dreWpZ", wz);
            cool = 10 + l.getRNG().nextInt(25);
            dx = wx - l.posX;
            dy = wy - l.posY;
            dz = wz - l.posZ;
            d = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        t.setInteger("dreWpCool", cool);
        l.getNavigator().clearPathEntity();
        l.motionX += dx / d * 0.12;
        l.motionY += dy / d * 0.12 + 0.08; // cancels the fall
        l.motionZ += dz / d * 0.12;
        double v = Math.sqrt(l.motionX * l.motionX + l.motionY * l.motionY + l.motionZ * l.motionZ);
        if (v > 0.7) {
            l.motionX *= 0.7 / v;
            l.motionY *= 0.7 / v;
            l.motionZ *= 0.7 / v;
        }
        l.fallDistance = 0;
        l.renderYawOffset = l.rotationYaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        if (l.ticksExisted % 4 == 0)
            ((WorldServer) l.worldObj).func_147487_a("fireworksSpark", l.posX, l.posY + 1.2, l.posZ, 2, 0.4, 0.2, 0.4, 0.01);
    }

    private static final String[] STAFF = { "EntityBouncingProjectile", "EntityEvernightProjectile" };

    private static boolean staffShot(Entity e) {
        for (Class<?> c = e.getClass(); c != null && c != Entity.class; c = c.getSuperclass())
            for (String n : STAFF) if (n.equals(c.getSimpleName())) return true;
        return false;
    }

    private void homeStaff(EntityLadyLuna l) {
        for (Object o : l.worldObj.getEntitiesWithinAABBExcludingEntity(l, l.boundingBox.expand(32, 24, 32))) {
            Entity p = (Entity) o;
            if (!staffShot(p)) continue;
            double v = Math.sqrt(p.motionX * p.motionX + p.motionY * p.motionY + p.motionZ * p.motionZ);
            double dx = l.posX - p.posX, dy = l.posY + l.height / 2 - p.posY, dz = l.posZ - p.posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 0.1 || v < 0.05) continue;
            p.motionX = p.motionX * 0.6 + dx / d * v * 0.4;
            p.motionY = p.motionY * 0.6 + dy / d * v * 0.4;
            p.motionZ = p.motionZ * 0.6 + dz / d * v * 0.4;
            p.velocityChanged = true;
        }
    }

    // ---- phase 3: lunar explosions

    private void blasts(EntityLadyLuna l) {
        NBTTagCompound t = l.getEntityData();
        WorldServer ws = (WorldServer) l.worldObj;
        List<double[]> marks = new ArrayList<double[]>();
        int[] raw = t.getIntArray(MARKS); // x*8, y*8, z*8, explosion tick
        for (int i = 0; i + 3 < raw.length; i += 4) marks.add(new double[] { raw[i] / 8.0, raw[i + 1] / 8.0, raw[i + 2] / 8.0, raw[i + 3] });
        for (Iterator<double[]> it = marks.iterator(); it.hasNext();) {
            double[] m = it.next();
            if (l.ticksExisted >= m[3]) {
                l.worldObj.newExplosion(l, m[0], m[1], m[2], 3.0F, false, false);
                it.remove();
            } else if (l.ticksExisted % 3 == 0) ws.func_147487_a("witchMagic", m[0], m[1] + 0.3, m[2], 6, 0.6, 0.1, 0.6, 0);
        }
        if (l.ticksExisted % 50 == 0) { // a mark under every player nearby, explosion after 1.5 s
            for (Object o : l.worldObj.getEntitiesWithinAABB(EntityPlayer.class, l.boundingBox.expand(24, 12, 24)))
                marks.add(new double[] { ((EntityPlayer) o).posX, ((EntityPlayer) o).boundingBox.minY, ((EntityPlayer) o).posZ, l.ticksExisted + 30 });
            l.worldObj.playSoundAtEntity(l, "mob.wither.shoot", 1.0F, 1.4F);
        }
        int[] out = new int[marks.size() * 4];
        for (int i = 0; i < marks.size(); i++) {
            double[] m = marks.get(i);
            out[i * 4] = (int) (m[0] * 8);
            out[i * 4 + 1] = (int) (m[1] * 8);
            out[i * 4 + 2] = (int) (m[2] * 8);
            out[i * 4 + 3] = (int) m[3];
        }
        t.setIntArray(MARKS, out);
    }

    /** What hurts Luna is decided by the phase; her own explosions and everything else - no. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent e) {
        if (!(e.entityLiving instanceof EntityLadyLuna) || e.entityLiving.worldObj.isRemote) return;
        EntityLadyLuna l = (EntityLadyLuna) e.entityLiving;
        int ph = phase(l);
        WeaponKind k = WeaponKind.of(e.source);
        if (hurts(ph, k)) return;
        e.setCanceled(true);
        if (e.source.getEntity() instanceof EntityPlayer && l.ticksExisted % 10 == 0)
            Nightmare.hint((EntityPlayer) e.source.getEntity(), "luna_" + ph);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (!(e.entityLiving instanceof EntityLadyLuna) || e.entityLiving.worldObj.isRemote) return;
        for (Object o : e.entityLiving.worldObj.getEntitiesWithinAABB(EntityPlayer.class, e.entityLiving.boundingBox.expand(AREA, 32, AREA)))
            HiveTimer.give((EntityPlayer) o, DreamItem.stack(DreamItem.LUNAR_SHARD, SHARDS));
    }

    // ---- Everlight is stronger than the maul

    /** Damage in the Divine tooltip = material damage + 5; Everlight - at least 50 and 3 higher than Karos's maul. */
    public static void buffEverlight() {
        Item ever = Infusion.vethea("everlight"), maul = Infusion.vethea("karosRockmaul");
        if (!(ever instanceof ItemSword)) return;
        try {
            Field mat = ReflectionHelper.findField(ever.getClass().getSuperclass(), "mat");
            float maulDmg = maul instanceof ItemSword ? ((Item.ToolMaterial) mat.get(maul)).getDamageVsEntity() : 0;
            Item.ToolMaterial old = (Item.ToolMaterial) mat.get(ever);
            float want = Math.max(50 - 5, maulDmg + 3);
            if (old.getDamageVsEntity() >= want) return;
            Item.ToolMaterial m = EnumHelper.addToolMaterial("DRE_EVERLIGHT", old.getHarvestLevel(), old.getMaxUses(), old.getEfficiencyOnProperMaterial(), want,
                old.getEnchantability());
            mat.set(ever, m);
            ReflectionHelper.setPrivateValue(ItemSword.class, (ItemSword) ever, 4.0F + want, "field_150934_a");
            ReflectionHelper.setPrivateValue(ItemSword.class, (ItemSword) ever, m, "field_150933_b");
            com.divinerpgextended.DivineRPGExtended.LOG.info("Everlight: damage " + (want + 5) + " (maul " + (maulDmg + 5) + ")");
        } catch (Throwable t) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Everlight: damage not raised - " + t);
        }
    }
}
