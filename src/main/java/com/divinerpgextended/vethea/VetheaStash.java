package com.divinerpgextended.vethea;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.divinerpgextended.Config;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Divine's separate Vethea inventory is not needed for the nightmare (foreign items already work in reverse), and it is unreliable itself:
 * on entering and leaving, the inventory is saved in one copy and loaded from another, old copies are not erased, and the nightmare
 * bed has a shared field on the block. The addon disables it ({@link #disableDivineSwap}) and once returns what Divine
 * managed to lock away.
 * <p>
 * Which copy is real: outside Vethea the live inventory is the normal one, so "VetheaInv" holds Vethean items, and
 * "OverworldInv" is an old copy already handed out (we do not touch it, otherwise a dupe). In Vethea it is the opposite. What did not fit waits in
 * "dre_stash" and is handed out when room frees up.
 */
public class VetheaStash {

    private static final Logger LOG = LogManager.getLogger("divinerpgextended");
    private static final String STASH = "dre_stash";

    /** The vetheaInventory setting exists only since Divine 1.4.2: in 1.4.1.5 the inventory swap is always on. */
    private static final java.lang.reflect.Field SWAP_OPTION = swapOption();

    private static java.lang.reflect.Field swapOption() {
        try {
            return ConfigurationHelper.class.getField("vetheaInventory");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Whether Divine will swap the inventory on entering and leaving Vethea. */
    public static boolean divineSwap() {
        if (com.divinerpgextended.asm.VetheaSwapHooks.blocked) return false;
        try {
            return SWAP_OPTION == null || SWAP_OPTION.getBoolean(null);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void disableDivineSwap() {
        if (!Config.vetheaNightmare) return;
        com.divinerpgextended.asm.VetheaSwapHooks.blocked = true;
        try {
            if (SWAP_OPTION != null) SWAP_OPTION.setBoolean(null, false);
        } catch (Throwable t) {
            LOG.warn("Vethea: could not turn off the vetheaInventory setting", t);
        }
        LOG.info("Vethea: separate Divine inventory turned off{} - during the nightmare foreign items work in reverse",
            SWAP_OPTION == null ? " (Divine without the vetheaInventory setting, swap turned off by the patch)" : "");
    }

    private static void say(EntityPlayer p, String key, Object... args) {
        com.divinerpgextended.audit.Audit.say(p, key, args);
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.LIGHT_PURPLE));
        p.addChatMessage(c);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!e.player.worldObj.isRemote) migrate(e.player);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote || e.player.ticksExisted % 40 != 0) return;
        if (e.player.isDead || e.player.getHealth() <= 0) return;
        NBTTagCompound ps = e.player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (ps.hasKey(STASH)) give(e.player, ps);
    }

    /** Move Divine's locked copy to the handout queue (once), remove both Divine copies. */
    private static void migrate(EntityPlayer p) {
        if (divineSwap()) return; // the separate inventory is enabled deliberately - we do not interfere
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) return;
        NBTTagCompound ps = d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!ps.hasKey("VetheaInv") && !ps.hasKey("OverworldInv")) return;
        String real = p.dimension == ConfigurationHelper.vethea ? "OverworldInv" : "VetheaInv";
        NBTTagList locked = ps.getTagList(real, 10);
        NBTTagList stash = ps.getTagList(STASH, 10);
        int n = 0;
        for (int i = 0; i < locked.tagCount(); i++) {
            ItemStack s = ItemStack.loadItemStackFromNBT(locked.getCompoundTagAt(i));
            if (s == null) continue;
            NBTTagCompound c = new NBTTagCompound();
            s.writeToNBT(c);
            stash.appendTag(c);
            n++;
        }
        ps.removeTag("VetheaInv");
        ps.removeTag("OverworldInv");
        if (stash.tagCount() > 0) ps.setTag(STASH, stash);
        LOG.info("Vethea: {} - from the separate Divine inventory ({}), {} stacks to return", p.getCommandSenderName(), real, n);
        com.divinerpgextended.debug.Debug.note(p, "Divine inventory: to return " + n + " stacks from " + real);
        if (n > 0) say(p, "dre.vethea.stash_found", String.valueOf(n));
    }

    private static void give(EntityPlayer p, NBTTagCompound ps) {
        NBTTagList stash = ps.getTagList(STASH, 10);
        NBTTagList left = new NBTTagList();
        int given = 0;
        for (int i = 0; i < stash.tagCount(); i++) {
            ItemStack s = ItemStack.loadItemStackFromNBT(stash.getCompoundTagAt(i));
            if (s == null) continue;
            int before = s.stackSize;
            p.inventory.addItemStackToInventory(s); // reduces stackSize by what fit
            if (s.stackSize < before) given++;
            if (s.stackSize > 0) {
                NBTTagCompound c = new NBTTagCompound();
                s.writeToNBT(c);
                left.appendTag(c);
            }
        }
        if (left.tagCount() == 0) ps.removeTag(STASH);
        else ps.setTag(STASH, left);
        if (given > 0) {
            p.inventoryContainer.detectAndSendChanges();
            if (left.tagCount() == 0) say(p, "dre.vethea.stash_done");
        }
        if (left.tagCount() > 0 && p.ticksExisted % 600 == 0) say(p, "dre.vethea.stash_wait", String.valueOf(left.tagCount()));
    }
}
