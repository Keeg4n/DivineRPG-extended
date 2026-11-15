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
 * Server part of debugging. A hit goes through steps: the attack event (can be cancelled), the damage event (perks, sets,
 * accessories change the number), then Minecraft armor, resistance and absorption. The first number is caught at the start, the second at the
 * end of the events, the result - by health at the end of the tick. Mechanic notes ({@link Debug#note}) attach to the hit during
 * which they fired.
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

    private static final String IFRAMES = "invulnerability after the previous hit";
    private static final ArrayDeque<Hit> STACK = new ArrayDeque<Hit>();
    private static final List<Hit> PENDING = new ArrayList<Hit>();
    private static final Map<UUID, Map<Integer, int[]>> EFFECTS = new HashMap<UUID, Map<Integer, int[]>>();
    private static final Map<UUID, float[]> HEALS = new HashMap<UUID, float[]>();
    private static final Map<UUID, String> CONTAINER = new HashMap<UUID, String>();

    // ---------------- Who is watching ----------------

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

    // ---------------- Formatting ----------------

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
        if (h.src.isProjectile()) b.append(", projectile");
        if (h.src.isMagicDamage()) b.append(", magic");
        if (h.src.isFireDamage()) b.append(", fire");
        if (h.src.isExplosion()) b.append(", explosion");
        if (h.src.isUnblockable()) b.append(", ignores armor");
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
            String who = taken ? "on you" : name(h.victim);
            String text, category;
            EnumChatFormatting color;
            if (cancelled == IFRAMES) {
                // ordinary Minecraft invulnerability after a hit (half a second): only to the file, in chat it is spam
                if (fl.file) Debug.file(w, "damage_iframes", name(h.victim), f(h.raw) + " (" + src + ")" + (ns.isEmpty() ? "" : " | " + ns));
                continue;
            }
            if (cancelled != null) {
                text = "[damage cancelled, " + cancelled + "] " + who + ": " + f(h.raw) + " (" + src + ")"
                    + (ns.isEmpty() ? " - cancelled by Divine or another mod" : "");
                color = EnumChatFormatting.YELLOW;
                category = "damage_cancelled";
            } else {
                float armor = h.after - dealt;
                // the target died: the rest of the damage went "nowhere", it is not armor
                String last = hp(h.victim) <= 0 ? " > finished off, excess damage " + f(Math.max(0, armor))
                    : " > armor and protection " + (armor >= 0 ? "-" : "+") + f(Math.abs(armor));
                text = (taken ? "[damage] -" : "[hit] " + who + " -") + f(dealt) + " HP (" + src + "): hit " + f(h.raw)
                    + " > after effects " + f(h.after) + last
                    + " | HP " + f(h.victim.getHealth()) + "/" + f(h.victim.getMaxHealth());
                color = taken ? EnumChatFormatting.RED : h.attacker == w || owner(h.attacker) == w ? EnumChatFormatting.GREEN
                    : EnumChatFormatting.GRAY;
                category = taken ? "damage_taken" : h.attacker == w ? "damage_dealt" : "damage_seen";
            }
            if (fl.chat) {
                Debug.chat(w, color, text);
                if (!ns.isEmpty()) Debug.chat(w, EnumChatFormatting.AQUA, "   triggered: " + ns);
            }
            if (fl.file) Debug.file(w, category, name(h.victim), text + (ns.isEmpty() ? "" : " | triggered: " + ns));
        }
    }

    private static Hit find(EntityLivingBase victim, DamageSource src) {
        for (Hit h : STACK) if (h.victim == victim && h.src == src) return h;
        return null;
    }

    // ---------------- A hit by steps ----------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void attackStart(LivingAttackEvent e) {
        if (!Debug.any() || e.entityLiving.worldObj.isRemote) return;
        // for a player the event arrives twice: the second time already adjusted for the world difficulty
        Hit same = find(e.entityLiving, e.source);
        if (same != null) {
            if (Math.abs(same.raw - e.ammount) > 0.01F) {
                same.notes.add("difficulty " + e.entityLiving.worldObj.difficultySetting.name().toLowerCase() + ": " + f(same.raw) + " > " + f(e.ammount));
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
        report(h, "on attack", 0);
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
            report(h, "on damage", 0);
            return;
        }
        h.after = e.ammount;
        h.before = hp(e.entityLiving);
        // a hit into the invulnerability window: Minecraft counts only the difference from the previous one - the damage arrived in the event already less than the hit
        if (h.hurtStart >= 0 && h.hurtStart < h.raw - 0.01F) {
            h.notes.add(0, "repeat hit during invulnerability: difference counted " + f(h.hurtStart) + " of " + f(h.raw));
        }
        PENDING.add(h);
    }

    /** A mechanic note: to the hit in which the entity takes part, otherwise to the debugger as a separate line. */
    static void note(Entity who, String text) {
        for (Hit h : STACK) {
            if (h.victim == who || h.attacker == who || owner(h.attacker) == who) {
                h.notes.add(text);
                return;
            }
        }
        Debug.Flags fl = Debug.flags(who);
        if (fl == null) return;
        if (fl.chat) Debug.chat((EntityPlayer) who, EnumChatFormatting.AQUA, "[mechanic] " + text);
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
            String t = e.isCanceled() ? "[death cancelled] " + cause : "[death] " + cause;
            if (fl.chat) Debug.chat((EntityPlayer) e.entityLiving, EnumChatFormatting.DARK_RED, t);
            if (fl.file) Debug.file(e.entityLiving, "death", e.isCanceled() ? "cancelled" : "", cause);
        }
        Debug.Flags kf = Debug.flags(killer);
        if (kf != null && kf.file && !e.isCanceled()) Debug.file(killer, "kill", name(e.entityLiving), e.source.damageType);
    }

    // ---------------- End of tick: hit results, healing, effects, snapshots ----------------

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
                    String t = "[heal] +" + f(heal[0]) + " HP per 1 s | HP " + f(p.getHealth()) + "/" + f(p.getMaxHealth());
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
            // new or of a different strength; an extension of the same effect (cold, perk night vision) is not news
            if (old == null || old[0] != v[0]) effectMsg(p, fl, en.getKey(), "+", v);
        }
        for (Integer id : was.keySet()) if (!now.containsKey(id)) effectMsg(p, fl, id, "-", null);
    }

    private static void effectMsg(EntityPlayerMP p, Debug.Flags fl, int id, String sign, int[] v) {
        Potion pot = Potion.potionTypes[id];
        if (pot == null) return;
        String tail = v != null ? " " + roman(v[0] + 1) + " over " + (v[1] / 20) + " s" : " removed";
        if (fl.chat) {
            IChatComponent c = new Debug.DebugText("[effect] " + sign);
            c.appendSibling(new ChatComponentTranslation(pot.getName()));
            c.appendText(tail);
            c.setChatStyle(new ChatStyle().setColor(com.divinerpgextended.vethea.Nightmare.bad(pot) ? EnumChatFormatting.DARK_PURPLE : EnumChatFormatting.DARK_AQUA));
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
        b.append(" | food ").append(p.getFoodStats().getFoodLevel()).append(" sat ").append(f(p.getFoodStats().getSaturationLevel()));
        b.append(" | lvl ").append(p.experienceLevel).append(" | fall ").append(f(p.fallDistance));
        b.append(" | rank ").append(com.divinerpgextended.boss.ProgressSpawns.rank(p)).append(" armor tier ")
            .append(com.divinerpgextended.item.ArmorData.effectiveTier(p));
        if (p.getEntityData().hasKey("dreCold")) b.append(" | cold ").append(f(p.getEntityData().getFloat("dreCold")));
        net.divinerpg.utils.events.ArcanaHelper ar = net.divinerpg.utils.events.ArcanaHelper.getProperties(p);
        if (ar != null) b.append(" | arcana ").append(f(ar.getBarValue())).append('/').append(f(com.divinerpgextended.asm.ArcanaHooks.max(p)));
        b.append(" | held ").append(stack(p.getHeldItem())).append(" | armor");
        for (int i = 3; i >= 0; i--) b.append(' ').append(stack(p.inventory.armorItemInSlot(i)));
        b.append(" | in sockets");
        for (ItemStack armor : p.inventory.armorInventory) {
            for (ItemStack s : com.divinerpgextended.accessory.Sockets.contents(armor)) b.append(' ').append(s.getDisplayName());
        }
        if (p.getHeldItem() != null && com.divinerpgextended.perk.Ammo.of(p.getHeldItem()) != null)
            b.append(" | loaded ammo ").append(com.divinerpgextended.perk.Ammo.loaded(p.getHeldItem()));
        Accessories a = Accessories.of(p);
        if (a.leftovers()) {
            b.append(" | old slots");
            for (int i = 0; i < Accessories.TOTAL; i++) {
                ItemStack s = a.getStackInSlot(i);
                if (s != null) b.append(' ').append(s.getDisplayName()).append(" x").append(s.stackSize);
            }
        }
        if (p.capabilities.isFlying) b.append(" | flying");
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

    // ---------------- Actions (file only) ----------------

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
            e.x + " " + e.y + " " + e.z + " side " + e.face + " | held " + stack(e.entityPlayer.getHeldItem()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onEntityInteract(EntityInteractEvent e) {
        if (e.entityPlayer.worldObj.isRemote) return;
        act(e.entityPlayer, "interact_entity", e.isCanceled() ? "cancelled" : "", name(e.target) + (e.entityPlayer.isSneaking() ? " (sneaking)" : "")
            + " | held " + stack(e.entityPlayer.getHeldItem()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onAttackEntity(AttackEntityEvent e) {
        if (e.entityPlayer.worldObj.isRemote) return;
        act(e.entityPlayer, "attack", e.isCanceled() ? "cancelled" : "", name(e.target) + " | held " + stack(e.entityPlayer.getHeldItem()));
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
        if (!e.entityPlayer.worldObj.isRemote) act(e.entityPlayer, "bow", "", "draw " + e.charge + " | " + stack(e.bow));
    }

    @SubscribeEvent
    public void onXp(PlayerPickupXpEvent e) {
        act(e.entityPlayer, "xp", "", String.valueOf(e.orb.getXpValue()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onFall(LivingFallEvent e) {
        if (!e.entityLiving.worldObj.isRemote && e.distance > 3)
            act(e.entityLiving, "fall", e.isCanceled() ? "cancelled" : "", f(e.distance) + " blocks");
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

    /** Debugging is off for the last one - close the file. */
    static void cleanup() {
        if (Debug.ON.isEmpty()) {
            STACK.clear();
            PENDING.clear();
            Debug.closeLog();
        }
    }
}
