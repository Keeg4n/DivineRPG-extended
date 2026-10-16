package com.divinerpgextended.arcana;

import java.util.ArrayList;
import java.util.List;

import net.divinerpg.entities.arcana.EntityVatticus;
import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.TwilightItemsOther;
import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import com.divinerpgextended.asm.ArcanaHooks;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossProgress;
import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.fishing.FishingEvents;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Лорд Ваттикус - знаток мира. Присесть и ПКМ: подсказка "что дальше" по убитым боссам, совет на стороне и
 * исследование мага; с аксессуаром в руке - подробный рассказ о нём. Исследование - принести материалы; за каждое +25 к максимуму арканы навсегда (8 исследований,
 * 200 -> 400). Максимум работает через {@link ArcanaHooks}.
 */
public class Vatticus {

    /** Шаги прогрессии по порядку: первый неубитый босс - следующий шаг. */
    private static final Boss[] STEPS = { Boss.CARCINUS, Boss.SNOW_GLACON, Boss.LICH_KING, Boss.ANCIENT_ENTITY, Boss.WATCHER,
        Boss.CADILLION_EDEN, Boss.CADILLION_WILDWOOD, Boss.CADILLION_APALACHIA, Boss.CADILLION_SKYTHERN, Boss.TWILIGHT_DEMON,
        Boss.KAROT };

    private static Object[][] task(int i) {
        switch (i) {
            case 0: return new Object[][] { { ArcanaItems.arcanium, 16 }, { ArcanaItems.chargedCollector, 1 } };
            case 1: return new Object[][] { { IceikaItems.snowflake, 8 }, { IceikaItems.iceShards, 16 } };
            case 2: return new Object[][] { { TwilightItemsOther.edenSoul, 8 } };
            case 3: return new Object[][] { { TwilightItemsOther.wildwoodSoul, 8 } };
            case 4: return new Object[][] { { TwilightItemsOther.apalachiaSoul, 8 } };
            case 5: return new Object[][] { { TwilightItemsOther.skythernSoul, 8 } };
            case 6: return new Object[][] { { TwilightItemsOther.mortumSoul, 8 } };
            case 7: return new Object[][] { { VetheaItems.polishedPearls, 16 } };
            default: return null;
        }
    }

    public static final int TASK_COUNT = 8;

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        com.divinerpgextended.audit.Audit.say(p, key, args);
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    private static int count(EntityPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) if (s != null && s.getItem() == item) n += s.stackSize;
        return n;
    }

    private static void take(EntityPlayer p, Item item, int n) {
        for (int i = 0; i < p.inventory.mainInventory.length && n > 0; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s == null || s.getItem() != item) continue;
            int t = Math.min(n, s.stackSize);
            s.stackSize -= t;
            n -= t;
            if (s.stackSize <= 0) p.inventory.mainInventory[i] = null;
        }
    }

    private static IChatComponent needs(Object[][] t) {
        StringBuilder sb = new StringBuilder();
        for (Object[] r : t) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r[1]).append(" x ").append(new ItemStack((Item) r[0]).getDisplayName());
        }
        return new ChatComponentTranslation("%s", sb.toString());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(EntityInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (!(e.target instanceof EntityVatticus) || !p.isSneaking()) return;
        e.setCanceled(true);
        if (p.worldObj.isRemote) return;
        // с аксессуаром в руке - рассказывает о нём (подсказка на самой вещи короткая, остальное игрок узнаёт у него)
        net.minecraft.item.ItemStack held = p.getHeldItem();
        if (held != null && held.getItem() == com.divinerpgextended.registry.ModItems.trinket) {
            say(p, EnumChatFormatting.GOLD, "dre.trinket." + com.divinerpgextended.trinket.ItemTrinket.of(held).key() + ".lore");
            return;
        }
        int done = ArcanaHooks.tasks(p);
        Object[][] t = task(done);
        if (t != null && has(p, t)) {
            if (!p.capabilities.isCreativeMode) for (Object[] r : t) take(p, (Item) r[0], (Integer) r[1]);
            NBTTagCompound ps = p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            ps.setInteger(ArcanaHooks.TASKS, done + 1);
            p.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, ps);
            sync(p);
            p.inventoryContainer.detectAndSendChanges();
            p.worldObj.playSoundAtEntity(p, "random.levelup", 0.7F, 1.4F);
            say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.vatticus.task_done", String.valueOf(done + 1),
                String.valueOf((int) ArcanaHooks.max(p)));
            Object[][] next = task(done + 1);
            if (next != null) say(p, EnumChatFormatting.GRAY, "dre.vatticus.task_next", needs(next));
            return;
        }
        // справочник: следующий шаг, совет на стороне, текущее исследование
        say(p, EnumChatFormatting.GOLD, "dre.vatticus.step." + nextStep(p));
        String tip = tip(p);
        if (tip != null) say(p, EnumChatFormatting.YELLOW, "dre.vatticus.tip." + tip);
        if (t != null) {
            say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.vatticus.task", String.valueOf(done + 1), String.valueOf(TASK_COUNT), needs(t),
                String.valueOf((int) ArcanaHooks.PER_TASK));
        } else {
            say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.vatticus.tasks_all", String.valueOf((int) ArcanaHooks.max(p)));
        }
    }

    private static boolean has(EntityPlayer p, Object[][] t) {
        for (Object[] r : t) if (count(p, (Item) r[0]) < (Integer) r[1]) return false;
        return true;
    }

    private static String nextStep(EntityPlayer p) {
        for (Boss b : STEPS) if (!BossProgress.has(p, b)) return b.name().toLowerCase();
        return "end";
    }

    /** Совет на стороне: случайный из того, что игрок ещё не сделал. */
    private static String tip(EntityPlayer p) {
        List<String> l = new ArrayList<String>();
        if (!BossProgress.has(p, Boss.KING_OF_SCORCHERS)) l.add("scorchers");
        if (WarGeneral.statueCount(p) < 12) l.add("statues");
        if (FishingEvents.quests(p) < 10) l.add("leorna");
        l.add("merik");
        l.add("orders");
        return l.get(p.worldObj.rand.nextInt(l.size()));
    }

    /** Клиенту - его максимум арканы (для полоски Divine). */
    public static void sync(EntityPlayer p) {
        if (p instanceof EntityPlayerMP) ColdHandler.NET.sendTo(new MerchantMessages.ArcanaMax(ArcanaHooks.max(p)), (EntityPlayerMP) p);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !e.player.worldObj.isRemote && e.player.ticksExisted % 40 == 0) sync(e.player);
    }

    /** Divine при смене мира и возрождении ставит аркане ровно 200 (зашито) - заполняем до настоящего максимума. */
    private static void refill(EntityPlayer p) {
        if (p.worldObj.isRemote) return;
        net.divinerpg.utils.events.ArcanaHelper a = net.divinerpg.utils.events.ArcanaHelper.getProperties(p);
        if (a == null) return;
        a.setBarValue(ArcanaHooks.max(p));
        sync(p);
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void onDimension(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent e) {
        refill(e.player);
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void onRespawn(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent e) {
        refill(e.player);
    }
}
