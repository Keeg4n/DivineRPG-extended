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
 * Отдельный инвентарь Ветеи из Divine при кошмаре не нужен (чужие вещи и так работают наоборот), а сам он ненадёжен:
 * при входе и выходе инвентарь сохраняется в одну копию и грузится из другой, старые копии не стираются, у кровати
 * кошмаров общее поле на блоке. Аддон выключает его ({@link #disableDivineSwap}) и один раз возвращает то, что Divine
 * успел запереть.
 * <p>
 * Какая копия настоящая: вне Ветеи живой инвентарь - обычный, значит в "VetheaInv" лежат ветейские вещи, а
 * "OverworldInv" - уже выданная старая копия (её не трогаем, иначе дюп). В Ветее - наоборот. Что не влезло - ждёт в
 * "dre_stash" и выдаётся, когда освободится место.
 */
public class VetheaStash {

    private static final Logger LOG = LogManager.getLogger("divinerpgextended");
    private static final String STASH = "dre_stash";

    public static void disableDivineSwap() {
        if (Config.vetheaNightmare && ConfigurationHelper.vetheaInventory) {
            ConfigurationHelper.vetheaInventory = false;
            LOG.info("Ветея: отдельный инвентарь Divine выключен - при кошмаре чужие вещи работают наоборот");
        }
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

    /** Перенести запертую копию Divine в очередь выдачи (один раз), обе копии Divine убрать. */
    private static void migrate(EntityPlayer p) {
        if (ConfigurationHelper.vetheaInventory) return; // отдельный инвентарь включён намеренно - не вмешиваемся
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
        LOG.info("Ветея: {} - из отдельного инвентаря Divine ({}) к возврату {} стаков", p.getCommandSenderName(), real, n);
        com.divinerpgextended.debug.Debug.note(p, "Инвентарь Divine: к возврату " + n + " стаков из " + real);
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
            p.inventory.addItemStackToInventory(s); // уменьшает stackSize на то, что влезло
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
