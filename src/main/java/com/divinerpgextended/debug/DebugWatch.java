package com.divinerpgextended.debug;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.AchievementEvent;

import com.divinerpgextended.accessory.Accessories;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Общие наблюдатели отладки - видят итог любой механики без правок в ней:
 * <ul>
 * <li>все сообщения в чат игроку (ключ перевода и параметры) - так видны задания, торговцы, страж, дары, лор;</li>
 * <li>изменения инвентаря каждый тик с открытым окном - сделки, награды, траты;</li>
 * <li>смена аксессуаров, ачивки, появление боссов и торговцев рядом.</li>
 * </ul>
 */
public class DebugWatch {

    /** Живая сущность игрока (после смерти новая) - для записи из сетевого потока. */
    private static final Map<UUID, EntityPlayerMP> LIVE = new ConcurrentHashMap<UUID, EntityPlayerMP>();
    private static final Map<UUID, Map<String, Integer>> INV = new HashMap<UUID, Map<String, Integer>>();
    private static final Map<UUID, List<String>> ACC = new HashMap<UUID, List<String>>();
    private static final Set<UUID> SEEN = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    // ---------------- Исходящий чат ----------------

    private static class ChatTap extends ChannelOutboundHandlerAdapter {

        final UUID id;

        ChatTap(UUID id) {
            this.id = id;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            try {
                if (msg instanceof S02PacketChat && Debug.ON.containsKey(id)) {
                    IChatComponent c = ObfuscationReflectionHelper.getPrivateValue(S02PacketChat.class, (S02PacketChat) msg, 0);
                    EntityPlayerMP p = LIVE.get(id);
                    Debug.Flags f = Debug.ON.get(id);
                    if (c != null && !(c instanceof Debug.DebugText) && p != null && f != null && f.file) {
                        String key = "";
                        if (c instanceof ChatComponentTranslation) {
                            ChatComponentTranslation t = (ChatComponentTranslation) c;
                            StringBuilder b = new StringBuilder(t.getKey());
                            for (Object a : t.getFormatArgs()) {
                                b.append(" | ").append(a instanceof IChatComponent ? ((IChatComponent) a).getUnformattedText() : a);
                            }
                            key = b.toString();
                        }
                        Debug.file(p, "chat_out", key, EnumChatFormatting.getTextWithoutFormattingCodes(c.getUnformattedText()));
                    }
                }
            } catch (Throwable ignored) {}
            super.write(ctx, msg, promise);
        }
    }

    /** Подключить перехват чата к соединению игрока (один раз). */
    static void tap(EntityPlayerMP p) {
        LIVE.put(p.getUniqueID(), p);
        try {
            ChannelPipeline pl = p.playerNetServerHandler.netManager.channel().pipeline();
            if (pl.get("dre_debug") == null && pl.get("packet_handler") != null) {
                pl.addBefore("packet_handler", "dre_debug", new ChatTap(p.getUniqueID()));
            }
        } catch (Throwable t) {
            Debug.file(p, "debug", "chat_tap_failed", String.valueOf(t));
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (Debug.ON.containsKey(e.player.getUniqueID())) tap((EntityPlayerMP) e.player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LIVE.remove(e.player.getUniqueID());
        INV.remove(e.player.getUniqueID());
        ACC.remove(e.player.getUniqueID());
    }

    // ---------------- Инвентарь и аксессуары ----------------

    private static String key(ItemStack s) {
        return s.getDisplayName();
    }

    private static Map<String, Integer> count(EntityPlayerMP p) {
        Map<String, Integer> m = new HashMap<String, Integer>();
        List<ItemStack> all = new ArrayList<ItemStack>();
        Collections.addAll(all, p.inventory.mainInventory);
        Collections.addAll(all, p.inventory.armorInventory);
        all.add(p.inventory.getItemStack()); // на курсоре - иначе перекладывание выглядит как потеря
        for (ItemStack s : all) {
            if (s == null) continue;
            Integer n = m.get(key(s));
            m.put(key(s), (n == null ? 0 : n) + s.stackSize);
        }
        return m;
    }

    private static String container(EntityPlayerMP p) {
        Container c = p.openContainer;
        return c == null || c == p.inventoryContainer ? "" : c.getClass().getSimpleName();
    }

    private static List<String> accessories(EntityPlayerMP p) {
        List<String> l = new ArrayList<String>();
        Accessories a = Accessories.of(p);
        for (int i = 0; i < a.getSizeInventory(); i++) {
            ItemStack s = a.getStackInSlot(i);
            if (s != null) l.add(i >= Accessories.SIZE ? "боеприпасы: " + s.getDisplayName() + " x" + s.stackSize : s.getDisplayName() + (i < a.unlocked ? "" : " (закрытый слот)"));
        }
        return l;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Debug.any()) return;
        for (EntityPlayerMP p : DebugEvents.debugPlayers()) {
            Debug.Flags f = Debug.ON.get(p.getUniqueID());
            LIVE.put(p.getUniqueID(), p);
            if (f.file) inventory(p);
            if (p.ticksExisted % 10 == 0) accessories(p, f);
        }
    }

    private static void inventory(EntityPlayerMP p) {
        Map<String, Integer> now = count(p), was = INV.put(p.getUniqueID(), now);
        if (was == null) return;
        Map<String, Integer> diff = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> en : now.entrySet()) {
            Integer o = was.get(en.getKey());
            int d = en.getValue() - (o == null ? 0 : o);
            if (d != 0) diff.put(en.getKey(), d);
        }
        for (Map.Entry<String, Integer> en : was.entrySet()) if (!now.containsKey(en.getKey())) diff.put(en.getKey(), -en.getValue());
        if (diff.isEmpty()) return;
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> en : diff.entrySet()) {
            b.append(b.length() == 0 ? "" : ", ").append(en.getValue() > 0 ? "+" : "").append(en.getValue()).append(' ').append(en.getKey());
        }
        Debug.file(p, "inventory", container(p), b.toString());
    }

    private static void accessories(EntityPlayerMP p, Debug.Flags f) {
        List<String> now = accessories(p), was = ACC.put(p.getUniqueID(), now);
        if (was == null || was.equals(now)) return;
        List<String> on = new ArrayList<String>(now), off = new ArrayList<String>(was);
        on.removeAll(was);
        off.removeAll(now);
        String t = "[аксессуары] " + (on.isEmpty() ? "" : "надето: " + on + " ") + (off.isEmpty() ? "" : "снято: " + off) + " | открыто слотов "
            + Accessories.of(p).unlocked;
        if (f.chat) Debug.chat(p, EnumChatFormatting.DARK_AQUA, t);
        if (f.file) Debug.file(p, "accessories", "", t);
    }

    // ---------------- Ачивки и появление сущностей ----------------

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onAchievement(AchievementEvent e) {
        if (!(e.entityPlayer instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.entityPlayer;
        Debug.Flags f = Debug.flags(p);
        if (f == null || p.func_147099_x().hasAchievementUnlocked(e.achievement)) return;
        boolean can = p.func_147099_x().canUnlockAchievement(e.achievement);
        Debug.file(p, "achievement", can ? "got" : "blocked", e.achievement.statId + (can ? "" : " - нет родительской ачивки")
            + (e.isCanceled() ? " (отменено)" : ""));
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !Debug.any()) return;
        Entity en = e.entity;
        boolean boss = en instanceof IBossDisplayData, merchant = en instanceof IMerchant;
        boolean ours = en.getClass().getName().startsWith("com.divinerpgextended");
        if (!boss && !merchant && !ours || !SEEN.add(en.getUniqueID())) return;
        for (EntityPlayerMP p : DebugEvents.debugPlayers()) {
            if (p.worldObj != e.world) continue;
            double d = Math.sqrt(p.getDistanceSqToEntity(en));
            if (d > 96) continue;
            Debug.Flags f = Debug.ON.get(p.getUniqueID());
            String t = "[появился] " + en.getCommandSenderName() + (boss ? " (босс)" : merchant ? " (торговец)" : "") + " в "
                + Math.round(d) + " блоках: " + Math.round(en.posX) + " " + Math.round(en.posY) + " " + Math.round(en.posZ);
            if (f.chat && (boss || merchant) && d <= 48) Debug.chat(p, EnumChatFormatting.GOLD, t);
            if (f.file) Debug.file(p, "entity_join", en.getClass().getSimpleName(), t);
        }
    }

}
