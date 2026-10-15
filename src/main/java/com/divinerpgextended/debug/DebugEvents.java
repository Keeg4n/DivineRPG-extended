package com.divinerpgextended.debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerPickupXpEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.divinerpgextended.accessory.Accessories;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Серверная часть отладки. Удар проходит по шагам: событие атаки (могут отменить), событие урона (перки, сеты,
 * аксессуары меняют число), затем броня, сопротивление и поглощение Minecraft. Первое число ловим в начале, второе - в
 * конце событий, итог - по здоровью в конце тика. Заметки механик ({@link Debug#note}) цепляются к удару, во время
 * которого сработали.
 */
public class DebugEvents {

    static class Hit {

        final EntityLivingBase victim;
        final Entity attacker;
        final DamageSource src;
        final String weapon;
        final List<EntityPlayerMP> watchers;
        final List<String> notes = new ArrayList<String>();
        float raw, after = -1, before, hurtStart = -1;

        Hit(EntityLivingBase victim, DamageSource src, float raw, List<EntityPlayerMP> watchers) {
            this.victim = victim;
            this.src = src;
            this.attacker = src.getEntity();
            this.raw = raw;
            this.watchers = watchers;
            ItemStack held = attacker instanceof EntityLivingBase ? ((EntityLivingBase) attacker).getHeldItem() : null;
            weapon = held != null ? held.getDisplayName() : null;
        }
    }

    private static final String IFRAMES = "неуязвимость после прошлого удара";
    private static final ArrayDeque<Hit> STACK = new ArrayDeque<Hit>();
    private static final List<Hit> PENDING = new ArrayList<Hit>();
    private static final Map<UUID, Map<Integer, int[]>> EFFECTS = new HashMap<UUID, Map<Integer, int[]>>();
    private static final Map<UUID, float[]> HEALS = new HashMap<UUID, float[]>();
    private static final Map<UUID, String> CONTAINER = new HashMap<UUID, String>();

    // ---------------- Кто смотрит ----------------

    @SuppressWarnings("unchecked")
    static List<EntityPlayerMP> debugPlayers() {
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        MinecraftServer s = MinecraftServer.getServer();
        if (s == null) return out;
        for (EntityPlayerMP p : (List<EntityPlayerMP>) s.getConfigurationManager().playerEntityList) {
            if (Debug.ON.containsKey(p.getUniqueID())) out.add(p);
        }
        return out;
    }

    private static Entity owner(Entity e) {
        return e instanceof EntityTameable ? ((EntityTameable) e).getOwner() : null;
    }

    private static List<EntityPlayerMP> watchers(EntityLivingBase victim, Entity attacker) {
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        for (EntityPlayerMP p : debugPlayers()) {
            Debug.Flags f = Debug.ON.get(p.getUniqueID());
            boolean mine = victim == p || attacker == p || owner(attacker) == p || owner(victim) == p;
            boolean near = f != null && f.mobs && p.worldObj == victim.worldObj && p.getDistanceSqToEntity(victim) < 24 * 24;
            if (mine || near) out.add(p);
        }
        return out;
    }

    // ---------------- Форматирование ----------------

    static String f(float v) {
        return String.format("%.1f", v);
    }

    static String name(Entity e) {
        return e == null ? "?" : e.getCommandSenderName();
    }

    private static String source(Hit h) {
        StringBuilder b = new StringBuilder(h.src.damageType);
        if (h.attacker != null) b.append(", ").append(name(h.attacker));
        if (h.weapon != null) b.append(", ").append(h.weapon);
        if (h.src.isProjectile()) b.append(", снаряд");
        if (h.src.isMagicDamage()) b.append(", магия");
        if (h.src.isFireDamage()) b.append(", огонь");
        if (h.src.isExplosion()) b.append(", взрыв");
        if (h.src.isUnblockable()) b.append(", сквозь броню");
        return b.toString();
    }

    private static String notes(Hit h) {
        if (h.notes.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String n : h.notes) b.append(b.length() == 0 ? "" : "; ").append(n);
        return b.toString();
    }

    private static float hp(EntityLivingBase e) {
        return e.isDead || e.getHealth() <= 0 ? 0 : e.getHealth() + e.getAbsorptionAmount();
    }

    private static void report(Hit h, String cancelled, float dealt) {
        String src = source(h), ns = notes(h);
        for (EntityPlayerMP w : h.watchers) {
            Debug.Flags fl = Debug.ON.get(w.getUniqueID());
            if (fl == null) continue;
            boolean taken = h.victim == w;
            String who = taken ? "по тебе" : name(h.victim);
            String text, category;
            EnumChatFormatting color;
            if (cancelled == IFRAMES) {
                // обычная неуязвимость Minecraft после удара (полсекунды): только в файл, в чате это спам
                if (fl.file) Debug.file(w, "damage_iframes", name(h.victim), f(h.raw) + " (" + src + ")" + (ns.isEmpty() ? "" : " | " + ns));
                continue;
            }
            if (cancelled != null) {
                text = "[урон отменён, " + cancelled + "] " + who + ": " + f(h.raw) + " (" + src + ")"
                    + (ns.isEmpty() ? " - отменил Divine или другой мод" : "");
                color = EnumChatFormatting.YELLOW;
                category = "damage_cancelled";
            } else {
                float armor = h.after - dealt;
                // цель умерла: остаток урона ушёл "в никуда", это не броня
                String last = hp(h.victim) <= 0 ? " > добит, лишний урон " + f(Math.max(0, armor))
                    : " > броня и защита " + (armor >= 0 ? "-" : "+") + f(Math.abs(armor));
                text = (taken ? "[урон] -" : "[удар] " + who + " -") + f(dealt) + " HP (" + src + "): удар " + f(h.raw)
                    + " > после эффектов " + f(h.after) + last
                    + " | HP " + f(h.victim.getHealth()) + "/" + f(h.victim.getMaxHealth());
                color = taken ? EnumChatFormatting.RED : h.attacker == w || owner(h.attacker) == w ? EnumChatFormatting.GREEN
                    : EnumChatFormatting.GRAY;
                category = taken ? "damage_taken" : h.attacker == w ? "damage_dealt" : "damage_seen";
            }
            if (fl.chat) {
                Debug.chat(w, color, text);
                if (!ns.isEmpty()) Debug.chat(w, EnumChatFormatting.AQUA, "   сработало: " + ns);
            }
            if (fl.file) Debug.file(w, category, name(h.victim), text + (ns.isEmpty() ? "" : " | сработало: " + ns));
        }
    }

    private static Hit find(EntityLivingBase victim, DamageSource src) {
        for (Hit h : STACK) if (h.victim == victim && h.src == src) return h;
        return null;
    }

    // ---------------- Удар по шагам ----------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void attackStart(LivingAttackEvent e) {
        if (!Debug.any() || e.entityLiving.worldObj.isRemote) return;
        // по игроку событие приходит дважды: второй раз уже с поправкой на сложность мира
        Hit same = find(e.entityLiving, e.source);
        if (same != null) {
            if (Math.abs(same.raw - e.ammount) > 0.01F) {
                same.notes.add("сложность " + e.entityLiving.worldObj.difficultySetting.name().toLowerCase() + ": " + f(same.raw) + " > " + f(e.ammount));
                same.raw = e.ammount;
            }
            return;
        }
        List<EntityPlayerMP> w = watchers(e.entityLiving, e.source.getEntity());
        if (w.isEmpty()) return;
        STACK.push(new Hit(e.entityLiving, e.source, e.ammount, w));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void attackEnd(LivingAttackEvent e) {
        if (!e.isCanceled() || STACK.isEmpty()) return;
        Hit h = find(e.entityLiving, e.source);
        if (h == null) return;
        STACK.remove(h);
        report(h, "на атаке", 0);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hurtStart(LivingHurtEvent e) {
        if (STACK.isEmpty()) return;
        Hit h = find(e.entityLiving, e.source);
        if (h != null) h.hurtStart = e.ammount;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void hurtEnd(LivingHurtEvent e) {
        if (STACK.isEmpty()) return;
        Hit h = find(e.entityLiving, e.source);
        if (h == null) return;
        STACK.remove(h);
        if (e.isCanceled()) {
            report(h, "на уроне", 0);
            return;
        }
        h.after = e.ammount;
        h.before = hp(e.entityLiving);
        // удар в окно неуязвимости: Minecraft засчитывает только разницу с прошлым - урон пришёл в событие уже меньше удара
        if (h.hurtStart >= 0 && h.hurtStart < h.raw - 0.01F) {
            h.notes.add(0, "повторный удар в неуязвимость: засчитана разница " + f(h.hurtStart) + " из " + f(h.raw));
        }
        PENDING.add(h);
    }

    /** Заметка механики: к удару, в котором участвует сущность, иначе отладчику отдельной строкой. */
    static void note(Entity who, String text) {
        for (Hit h : STACK) {
            if (h.victim == who || h.attacker == who || owner(h.attacker) == who) {
                h.notes.add(text);
                return;
            }
        }
        Debug.Flags fl = Debug.flags(who);
        if (fl == null) return;
        if (fl.chat) Debug.chat((EntityPlayer) who, EnumChatFormatting.AQUA, "[механика] " + text);
        if (fl.file) Debug.file(who, "mechanic", "", text);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onHeal(LivingHealEvent e) {
        Debug.Flags fl = Debug.flags(e.entityLiving);
        if (fl == null) return;
        if (e.isCanceled()) {
            if (fl.file) Debug.file(e.entityLiving, "heal_cancelled", "", f(e.amount));
            return;
        }
        float[] s = HEALS.get(e.entityLiving.getUniqueID());
        if (s == null) HEALS.put(e.entityLiving.getUniqueID(), s = new float[1]);
        s[0] += Math.min(e.amount, e.entityLiving.getMaxHealth() - e.entityLiving.getHealth());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onDeath(LivingDeathEvent e) {
        if (!Debug.any() || e.entityLiving.worldObj.isRemote) return;
        Debug.Flags fl = Debug.flags(e.entityLiving);
        Entity killer = e.source.getEntity();
        String cause = e.source.damageType + (killer != null ? ", " + name(killer) : "");
        if (fl != null) {
            String t = e.isCanceled() ? "[смерть отменена] " + cause : "[смерть] " + cause;
            if (fl.chat) Debug.chat((EntityPlayer) e.entityLiving, EnumChatFormatting.DARK_RED, t);
            if (fl.file) Debug.file(e.entityLiving, "death", e.isCanceled() ? "cancelled" : "", cause);
        }
        Debug.Flags kf = Debug.flags(killer);
        if (kf != null && kf.file && !e.isCanceled()) Debug.file(killer, "kill", name(e.entityLiving), e.source.damageType);
    }

    // ---------------- Конец тика: итоги ударов, лечение, эффекты, снимки ----------------

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        for (Hit h : PENDING) report(h, null, h.before - hp(h.victim));
        PENDING.clear();
        for (Hit h : STACK) report(h, IFRAMES, 0);
        STACK.clear();
        if (!Debug.any()) return;
        MinecraftServer s = MinecraftServer.getServer();
        int tick = s.getTickCounter();
        for (EntityPlayerMP p : debugPlayers()) {
            Debug.Flags fl = Debug.ON.get(p.getUniqueID());
            if (tick % 5 == 0) effects(p, fl);
            if (tick % 20 == 0) {
                float[] heal = HEALS.remove(p.getUniqueID());
                if (heal != null && heal[0] > 0.01F) {
                    String t = "[лечение] +" + f(heal[0]) + " HP за 1 с | HP " + f(p.getHealth()) + "/" + f(p.getMaxHealth());
                    if (fl.chat && heal[0] >= 0.5F) Debug.chat(p, EnumChatFormatting.LIGHT_PURPLE, t);
                    if (fl.file) Debug.file(p, "heal", "", t);
                }
                if (fl.file) snapshot(p);
                container(p, fl);
            }
        }
        if (tick % 20 == 0) Debug.log().flush();
    }

    private static String roman(int n) {
        String[] r = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
        return n >= 1 && n <= r.length ? r[n - 1] : String.valueOf(n);
    }

    @SuppressWarnings("unchecked")
    private static void effects(EntityPlayerMP p, Debug.Flags fl) {
        Map<Integer, int[]> was = EFFECTS.get(p.getUniqueID());
        Map<Integer, int[]> now = new HashMap<Integer, int[]>();
        for (PotionEffect pe : (Collection<PotionEffect>) p.getActivePotionEffects()) {
            now.put(pe.getPotionID(), new int[] { pe.getAmplifier(), pe.getDuration() });
        }
        EFFECTS.put(p.getUniqueID(), now);
        if (was == null) return;
        for (Map.Entry<Integer, int[]> en : now.entrySet()) {
            int[] old = was.get(en.getKey());
            int[] v = en.getValue();
            // новый или другой силы; продление того же эффекта (холод, ночное зрение перков) - не новость
            if (old == null || old[0] != v[0]) effectMsg(p, fl, en.getKey(), "+", v);
        }
        for (Integer id : was.keySet()) if (!now.containsKey(id)) effectMsg(p, fl, id, "-", null);
    }

    private static void effectMsg(EntityPlayerMP p, Debug.Flags fl, int id, String sign, int[] v) {
        Potion pot = Potion.potionTypes[id];
        if (pot == null) return;
        String tail = v != null ? " " + roman(v[0] + 1) + " на " + (v[1] / 20) + " с" : " снят";
        if (fl.chat) {
            IChatComponent c = new Debug.DebugText("[эффект] " + sign);
            c.appendSibling(new ChatComponentTranslation(pot.getName()));
            c.appendText(tail);
            c.setChatStyle(new ChatStyle().setColor(pot.isBadEffect() ? EnumChatFormatting.DARK_PURPLE : EnumChatFormatting.DARK_AQUA));
            p.addChatMessage(c);
        }
        if (fl.file) Debug.file(p, "effect", sign + pot.getName(), tail.trim());
    }

    private static String stack(ItemStack s) {
        return s == null ? "-" : s.getDisplayName() + (s.stackSize > 1 ? " x" + s.stackSize : "")
            + (s.isItemStackDamageable() ? " " + (s.getMaxDamage() - s.getItemDamage()) + "/" + s.getMaxDamage() : "");
    }

    private static void snapshot(EntityPlayerMP p) {
        StringBuilder b = new StringBuilder();
        b.append("HP ").append(f(p.getHealth())).append('/').append(f(p.getMaxHealth()));
        if (p.getAbsorptionAmount() > 0) b.append(" +").append(f(p.getAbsorptionAmount()));
        b.append(" | еда ").append(p.getFoodStats().getFoodLevel()).append(" сыт ").append(f(p.getFoodStats().getSaturationLevel()));
        b.append(" | ур ").append(p.experienceLevel).append(" | падение ").append(f(p.fallDistance));
        b.append(" | ранг ").append(com.divinerpgextended.boss.ProgressSpawns.rank(p)).append(" ступень брони ")
            .append(com.divinerpgextended.item.ArmorData.effectiveTier(p));
        if (p.getEntityData().hasKey("dreCold")) b.append(" | холод ").append(f(p.getEntityData().getFloat("dreCold")));
        net.divinerpg.utils.events.ArcanaHelper ar = net.divinerpg.utils.events.ArcanaHelper.getProperties(p);
        if (ar != null) b.append(" | аркана ").append(f(ar.getBarValue())).append('/').append(f(com.divinerpgextended.asm.ArcanaHooks.max(p)));
        b.append(" | в руке ").append(stack(p.getHeldItem())).append(" | броня");
        for (int i = 3; i >= 0; i--) b.append(' ').append(stack(p.inventory.armorItemInSlot(i)));
        Accessories a = Accessories.of(p);
        b.append(" | аксессуары");
        for (int i = 0; i < a.getSizeInventory(); i++) {
            ItemStack s = a.getStackInSlot(i);
            if (s != null) b.append(' ').append(i < a.unlocked ? "" : "(закрыт)").append(s.getDisplayName());
        }
        if (p.capabilities.isFlying) b.append(" | летит");
        Debug.file(p, "state", "", b.toString());
    }

    private static void container(EntityPlayerMP p, Debug.Flags fl) {
        Container c = p.openContainer;
        String name = c == null || c == p.inventoryContainer ? null : c.getClass().getSimpleName();
        String was = CONTAINER.get(p.getUniqueID());
        if (name == null ? was == null : name.equals(was)) return;
        if (name == null) CONTAINER.remove(p.getUniqueID());
        else CONTAINER.put(p.getUniqueID(), name);
        if (fl.file) Debug.file(p, "container", name == null ? "close" : "open", name == null ? was : name);
    }

    // ---------------- Действия (только в файл) ----------------

    private static void act(Entity who, String category, String event, String details) {
        Debug.Flags fl = Debug.flags(who);
        if (fl != null && fl.file) Debug.file(who, category, event, details);
    }

    private static String block(net.minecraft.block.Block b, int meta, int x, int y, int z) {
        return net.minecraft.block.Block.blockRegistry.getNameForObject(b) + ":" + meta + " @ " + x + " " + y + " " + z;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onBreak(BlockEvent.BreakEvent e) {
        act(e.getPlayer(), "block_break", e.isCanceled() ? "cancelled" : "", block(e.block, e.blockMetadata, e.x, e.y, e.z));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPlace(BlockEvent.PlaceEvent e) {
        act(e.player, "block_place", e.isCanceled() ? "cancelled" : "", block(e.block, e.blockMetadata, e.x, e.y, e.z));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.entityPlayer.worldObj.isRemote) return;
        act(e.entityPlayer, "interact", e.action.name() + (e.isCanceled() ? " cancelled" : ""),
            e.x + " " + e.y + " " + e.z + " side " + e.face + " | в руке " + stack(e.entityPlayer.getHeldItem()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onEntityInteract(EntityInteractEvent e) {
        if (e.entityPlayer.worldObj.isRemote) return;
        act(e.entityPlayer, "interact_entity", e.isCanceled() ? "cancelled" : "", name(e.target) + (e.entityPlayer.isSneaking() ? " (присев)" : "")
            + " | в руке " + stack(e.entityPlayer.getHeldItem()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onAttackEntity(AttackEntityEvent e) {
        if (e.entityPlayer.worldObj.isRemote) return;
        act(e.entityPlayer, "attack", e.isCanceled() ? "cancelled" : "", name(e.target) + " | в руке " + stack(e.entityPlayer.getHeldItem()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPickup(EntityItemPickupEvent e) {
        act(e.entityPlayer, "pickup", e.isCanceled() ? "cancelled" : "", stack(e.item.getEntityItem()));
    }

    @SubscribeEvent
    public void onToss(ItemTossEvent e) {
        act(e.player, "toss", "", stack(e.entityItem.getEntityItem()));
    }

    @SubscribeEvent
    public void onUseStart(PlayerUseItemEvent.Start e) {
        if (!e.entityPlayer.worldObj.isRemote) act(e.entityPlayer, "use_start", "", stack(e.item));
    }

    @SubscribeEvent
    public void onUseFinish(PlayerUseItemEvent.Finish e) {
        if (!e.entityPlayer.worldObj.isRemote) act(e.entityPlayer, "use_finish", "", stack(e.item));
    }

    @SubscribeEvent
    public void onArrow(ArrowLooseEvent e) {
        if (!e.entityPlayer.worldObj.isRemote) act(e.entityPlayer, "bow", "", "натяжение " + e.charge + " | " + stack(e.bow));
    }

    @SubscribeEvent
    public void onXp(PlayerPickupXpEvent e) {
        act(e.entityPlayer, "xp", "", String.valueOf(e.orb.getXpValue()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onFall(LivingFallEvent e) {
        if (!e.entityLiving.worldObj.isRemote && e.distance > 3)
            act(e.entityLiving, "fall", e.isCanceled() ? "cancelled" : "", f(e.distance) + " блоков");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onEnderTeleport(EnderTeleportEvent e) {
        act(e.entityLiving, "ender_teleport", e.isCanceled() ? "cancelled" : "", String.format("%.1f %.1f %.1f", e.targetX, e.targetY, e.targetZ));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onCommand(CommandEvent e) {
        if (!(e.sender instanceof EntityPlayerMP)) return;
        StringBuilder b = new StringBuilder("/").append(e.command.getCommandName());
        for (String a : e.parameters) b.append(' ').append(a);
        act((Entity) e.sender, "command", e.isCanceled() ? "cancelled" : "", b.toString());
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent e) {
        act(e.player, "chat", "", e.message);
    }

    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent e) {
        if (!e.player.worldObj.isRemote) act(e.player, "craft", "", stack(e.crafting));
    }

    @SubscribeEvent
    public void onSmelt(PlayerEvent.ItemSmeltedEvent e) {
        if (!e.player.worldObj.isRemote) act(e.player, "smelt", "", stack(e.smelting));
    }

    @SubscribeEvent
    public void onDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        act(e.player, "dimension", "", e.fromDim + " > " + e.toDim);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        act(e.player, "respawn", "", "");
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (Debug.ON.containsKey(e.player.getUniqueID())) {
            Debug.sendState((EntityPlayerMP) e.player, true);
            act(e.player, "login", "", "");
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        act(e.player, "logout", "", "");
        EFFECTS.remove(e.player.getUniqueID());
        HEALS.remove(e.player.getUniqueID());
        CONTAINER.remove(e.player.getUniqueID());
        if (Debug.any()) Debug.log().flush();
    }

    /** Отладка выключена у последнего - закрыть файл. */
    static void cleanup() {
        if (Debug.ON.isEmpty()) {
            STACK.clear();
            PENDING.clear();
            Debug.closeLog();
        }
    }
}
