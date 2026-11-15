package com.divinerpgextended.vethea;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Foreign Baubles accessories are silent in the dream before completion (patches in {@link com.divinerpgextended.asm.BaublesTransformer}):
 * their wear tick does not run, mods that ask BaublesApi "what is worn" do not see them, and on entering the nightmare the
 * accessory is taken off (onUnequipped - removes its own health, speed and other bonuses), on leaving it is put on
 * again. The items themselves never leave the slots. The addon's accessories (amulets, rings, dream wings) follow their own rules in
 * {@link com.divinerpgextended.accessory.Accessories}.
 */
public class NightmareBaubles {

    private static Method onWornTick, onEquipped, onUnequipped, playerBaubles;
    private static boolean looked;
    /** Which of the player's accessories are currently taken off by the nightmare (by slot). */
    private static final Map<UUID, ItemStack[]> MASKED = new HashMap<UUID, ItemStack[]>();

    private static synchronized void lookup() {
        if (looked) return;
        looked = true;
        try {
            Class<?> b = Class.forName("baubles.api.IBauble");
            onWornTick = b.getMethod("onWornTick", ItemStack.class, EntityLivingBase.class);
            onEquipped = b.getMethod("onEquipped", ItemStack.class, EntityLivingBase.class);
            onUnequipped = b.getMethod("onUnequipped", ItemStack.class, EntityLivingBase.class);
            playerBaubles = Class.forName("baubles.common.lib.PlayerHandler").getMethod("getPlayerBaubles", EntityPlayer.class);
        } catch (Throwable ignored) {}
    }

    /** Whether the accessory is hidden from mods right now. */
    public static boolean masked(EntityPlayer p, ItemStack s) {
        return s != null && Nightmare.active(p) && !Nightmare.vethean(s);
    }

    /** Called from Baubles instead of IBauble.onWornTick. */
    public static void wornTick(Object bauble, ItemStack s, EntityLivingBase e) {
        if (e instanceof EntityPlayer && masked((EntityPlayer) e, s)) return;
        lookup();
        call(onWornTick, bauble, s, e);
    }

    /** Called from BaublesApi.getBaubles: in the dream - a view without foreign accessories. */
    public static IInventory view(IInventory real, EntityPlayer p) {
        if (real == null || p == null || !Nightmare.active(p)) return real;
        return new Masked(real, p);
    }

    private static void call(Method m, Object bauble, ItemStack s, EntityLivingBase e) {
        if (m == null || bauble == null || !m.getDeclaringClass().isInstance(bauble)) return;
        try {
            m.invoke(bauble, s, e);
        } catch (InvocationTargetException x) { // the accessory itself failed - as without the patch
            Throwable c = x.getCause();
            if (c instanceof RuntimeException) throw (RuntimeException) c;
            if (c instanceof Error) throw (Error) c;
            throw new RuntimeException(c);
        } catch (IllegalAccessException ignored) {}
    }

    /** The real accessory inventory (bypassing the view). */
    private static IInventory real(EntityPlayer p) {
        lookup();
        if (playerBaubles == null) return null;
        try {
            return (IInventory) playerBaubles.invoke(null, p);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Take off on entering the nightmare, put on when leaving. */
    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.START || e.player.worldObj.isRemote) return;
        sync(e.player);
    }

    public static void sync(EntityPlayer p) {
        UUID id = p.getUniqueID();
        ItemStack[] m = MASKED.get(id);
        boolean active = Nightmare.active(p);
        if (m == null && !active) return;
        IInventory inv = real(p);
        if (inv == null) return;
        if (m == null || m.length != inv.getSizeInventory()) {
            ItemStack[] n = new ItemStack[inv.getSizeInventory()];
            if (m != null) System.arraycopy(m, 0, n, 0, Math.min(m.length, n.length));
            MASKED.put(id, m = n);
        }
        boolean left = false;
        for (int i = 0; i < m.length; i++) {
            ItemStack s = inv.getStackInSlot(i);
            boolean hide = active && s != null && !Nightmare.vethean(s);
            if (hide) {
                if (m[i] != s) {
                    call(onUnequipped, s.getItem(), s, p);
                    m[i] = s;
                    Nightmare.hint(p, "bauble");
                }
                left = true;
            } else if (m[i] != null) {
                if (m[i] == s) call(onEquipped, s.getItem(), s, p); // still in the slot - put it back on
                m[i] = null;
            }
        }
        if (!left) MASKED.remove(id);
    }

    /** Leaving the server: put back on what the nightmare took off, so that the save goes out as it was. */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ItemStack[] m = MASKED.remove(e.player.getUniqueID());
        if (m == null) return;
        IInventory inv = real(e.player);
        if (inv == null) return;
        for (int i = 0; i < m.length && i < inv.getSizeInventory(); i++)
            if (m[i] != null && m[i] == inv.getStackInSlot(i)) call(onEquipped, m[i].getItem(), m[i], e.player);
    }

    /** A view of the accessory inventory without foreign ones: empty slots in their place, writing to such slots does not go through. */
    static final class Masked implements IInventory {

        private final IInventory real;
        private final EntityPlayer p;

        Masked(IInventory real, EntityPlayer p) {
            this.real = real;
            this.p = p;
        }

        private boolean hidden(int i) {
            return masked(p, real.getStackInSlot(i));
        }

        @Override
        public int getSizeInventory() {
            return real.getSizeInventory();
        }

        @Override
        public ItemStack getStackInSlot(int i) {
            return hidden(i) ? null : real.getStackInSlot(i);
        }

        @Override
        public ItemStack decrStackSize(int i, int n) {
            return hidden(i) ? null : real.decrStackSize(i, n);
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int i) {
            return hidden(i) ? null : real.getStackInSlotOnClosing(i);
        }

        @Override
        public void setInventorySlotContents(int i, ItemStack s) {
            if (!hidden(i)) real.setInventorySlotContents(i, s);
        }

        @Override
        public String getInventoryName() {
            return real.getInventoryName();
        }

        @Override
        public boolean hasCustomInventoryName() {
            return real.hasCustomInventoryName();
        }

        @Override
        public int getInventoryStackLimit() {
            return real.getInventoryStackLimit();
        }

        @Override
        public void markDirty() {
            real.markDirty();
        }

        @Override
        public boolean isUseableByPlayer(EntityPlayer pl) {
            return real.isUseableByPlayer(pl);
        }

        @Override
        public void openInventory() {
            real.openInventory();
        }

        @Override
        public void closeInventory() {
            real.closeInventory();
        }

        @Override
        public boolean isItemValidForSlot(int i, ItemStack s) {
            return !hidden(i) && real.isItemValidForSlot(i, s);
        }
    }
}
