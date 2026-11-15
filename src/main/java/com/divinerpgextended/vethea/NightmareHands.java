package com.divinerpgextended.vethea;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * In the dream before completion foreign items do not work:
 * <ul>
 * <li>right-click (in the air, on a block, on an entity) with a foreign item does nothing, except building, food, potions and bows
 * ({@link Nightmare#usable}); chests and doors open as usual;</li>
 * <li>a foreign tool breaks only the block the player started breaking: area (hammers, excavators, lumber axes,
 * Terra Shatterer, foci) does not apply.</li>
 * </ul>
 * Works on the client too (item dashes and jumps are computed by the client), it knows completion from
 * {@link com.divinerpgextended.boss.ProgressSync}.
 */
public class NightmareHands {

    private static final Map<Class<?>, Boolean> USE_FIRST = new HashMap<Class<?>, Boolean>(), ON_ENTITY = new HashMap<Class<?>, Boolean>(),
        ON_HIT = new HashMap<Class<?>, Boolean>();
    /** The block the player started breaking (click on a block): a foreign tool can break it, nothing else. */
    private static final Map<UUID, int[]> DIGGING = new HashMap<UUID, int[]>();

    /** Whether the item (or its ancestors up to Item) overrides a method with one of the names. */
    private static boolean overrides(Item i, Map<Class<?>, Boolean> cache, String... names) {
        Boolean b = cache.get(i.getClass());
        if (b != null) return b;
        boolean r = false;
        outer: for (Class<?> c = i.getClass(); c != null && c != Item.class; c = c.getSuperclass()) {
            Method[] ms;
            try {
                ms = c.getDeclaredMethods();
            } catch (Throwable t) {
                r = true; // the signatures contain client classes - assume it overrides
                break;
            }
            for (Method m : ms) for (String n : names) if (n.equals(m.getName())) {
                r = true;
                break outer;
            }
        }
        cache.put(i.getClass(), r);
        return r;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (e.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            if (!p.worldObj.isRemote) DIGGING.put(p.getUniqueID(), new int[] { e.x, e.y, e.z });
            return;
        }
        if (!Nightmare.active(p)) return;
        ItemStack h = p.getHeldItem();
        if (Nightmare.usable(h)) return;
        e.useItem = Event.Result.DENY; // onItemUse and onItemRightClick
        // onItemUseFirst is called even on denial - only cancelling stops it (then the block will not open either)
        boolean air = e.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR;
        if (air || overrides(h.getItem(), USE_FIRST, "onItemUseFirst")) e.setCanceled(true);
        if (air && !p.worldObj.isRemote) Nightmare.hint(p, "use");
        com.divinerpgextended.debug.Debug.note(p, "Nightmare: right click with a foreign item does nothing (" + Item.itemRegistry.getNameForObject(h.getItem()) + ")");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityInteract(EntityInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (!Nightmare.active(p)) return;
        ItemStack h = p.getHeldItem();
        if (Nightmare.usable(h) || !overrides(h.getItem(), ON_ENTITY, "itemInteractionForEntity", "func_111207_a")) return;
        e.setCanceled(true);
        if (!p.worldObj.isRemote) Nightmare.hint(p, "use");
    }

    /**
     * A hit by a foreign weapon on a dream mob is stopped before the hit: the mob heals by the hit strength, and the weapon's properties on
     * hit (Divine special effects, lightning, Tinkers on-hit effects, addon perks) do not trigger.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMelee(net.minecraftforge.event.entity.player.AttackEntityEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (p.worldObj.isRemote || !Nightmare.active(p) || !Nightmare.nativeTarget(e.target)) return;
        ItemStack h = p.getHeldItem();
        if (h == null || Nightmare.vethean(h)) return;
        if (!Nightmare.weapon(h) && !overrides(h.getItem(), ON_HIT, "onLeftClickEntity", "hitEntity", "func_77644_a")) return;
        e.setCanceled(true);
        if (!(e.target instanceof net.minecraft.entity.EntityLivingBase)) return;
        net.minecraft.entity.EntityLivingBase t = (net.minecraft.entity.EntityLivingBase) e.target;
        float f = (float) p.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.attackDamage).getAttributeValue()
            + net.minecraft.enchantment.EnchantmentHelper.getEnchantmentModifierLiving(p, t);
        Nightmare.soothe(p, t, f);
    }

    /** A foreign tool in the dream breaks only the started block: extra area blocks are cancelled. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent e) {
        EntityPlayer p = e.getPlayer();
        if (p == null || p instanceof FakePlayer || p.worldObj.isRemote || !Nightmare.active(p)) return;
        ItemStack h = p.getHeldItem();
        if (h == null || Nightmare.vethean(h)) return;
        int[] d = DIGGING.get(p.getUniqueID());
        if (d != null && d[0] == e.x && d[1] == e.y && d[2] == e.z) return;
        e.setCanceled(true);
        Nightmare.hint(p, "area");
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        DIGGING.remove(e.player.getUniqueID());
    }
}
