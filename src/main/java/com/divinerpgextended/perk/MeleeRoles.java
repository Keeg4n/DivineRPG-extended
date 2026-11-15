package com.divinerpgextended.perk;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.arcana.WeaponOrigin;
import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.dummy.EntityTrainingDummy;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

import io.netty.buffer.ByteBuf;

/**
 * Subtypes of Overworld melee weapons (swords from Divine's VanillaItemsWeapons): each has its own target invulnerability window after a
 * hit and its own damage multiplier, separately for mobs and players (Config, category melee).
 * <p>
 * Window: on a fresh hit vanilla writes hurtResistantTime = maxHurtResistantTime (20), and a new hit goes through in full when the
 * value is no higher than half. Write half + N - the next hit comes in N ticks; we do not touch maxHurtResistantTime: foreign checks
 * compare against it. A hit inside the window, as in vanilla, goes through as the difference from the previous one.
 * <p>
 * The rules apply only to the player's own click hit: {@link AttackEntityEvent} sets a mark, the first direct damage from
 * that player clears it. The Bedrock mace wave and other hits "on behalf of the player" bypass it, projectiles all the more so.
 * A dummy counts as a mob: its hit goes through the ghost player, and the window lives on the dummy itself.
 * <p>
 * Also here is the crit of the full Terran set ({@link #terranCrit}): a hit from the ground x{@link #TERRAN_CRIT} without jumping. The jump crit
 * requires leaving the ground, so they do not stack. Only the set itself, it is not passed to Wanderer armor.
 */
public class MeleeRoles {

    public enum Kind {
        // maul: window 1 s, damage x1.2 (before 0.99.122 it was x2 and x1.5 against players - with the area it came out stronger than Karos's Maul)
        KNIFE(6, 0.75F, 8, 1F), RAPIER(8, 0.9F, 9, 1F), SWORD(10, 1F, 10, 1F), MAUL(20, 1.2F, 20, 1.2F),
        // Vethea: claws faster than swords, swords as usual, mauls slower but x1.3
        CLAW(7, 1F, 7, 1F), HAMMER(14, 1.3F, 14, 1.3F);

        /** Target invulnerability window in ticks: against mobs and against players. */
        public int window, windowPvp;
        /** Damage multiplier: against mobs and against players. */
        public float damage, damagePvp;

        Kind(int window, float damage, int windowPvp, float damagePvp) {
            this.window = window;
            this.damage = damage;
            this.windowPvp = windowPvp;
            this.damagePvp = damagePvp;
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    /** The window in regular Minecraft: this subtype does not touch the target's window. */
    public static final int VANILLA_WINDOW = 10;
    /** Terran set crit: the same as the regular jump crit. */
    public static final float TERRAN_CRIT = 1.5F;

    private static final String[] KNIVES = { "oceanKnife", "jungleKnife", "aquaticDagger", "terranDagger", "terranKnife", "dualClaw" };
    private static final String[] RAPIERS = { "rupeeRapier", "jungleRapier", "arlemiteStabber" };
    private static final String[] MAULS = { "aquaMaul", "bedrockMaul", "crabclawMaul", "furyMaul", "corruptedMaul", "terranMaul", "aquatoothMaul" };

    private static final Map<Item, Kind> KINDS = new HashMap<Item, Kind>();

    private static EntityPlayer striker;
    private static Entity struck;
    private static long strikeTick;

    /** Subtypes by Divine item names: whatever is missing in its version simply does not get into the list. @return how many were found */
    public static int setup() {
        KINDS.clear();
        put(Kind.KNIFE, KNIVES);
        put(Kind.RAPIER, RAPIERS);
        put(Kind.MAUL, MAULS);
        return KINDS.size();
    }

    private static void put(Kind k, String[] names) {
        for (String n : names) {
            Item i = GameRegistry.findItem("divinerpg", n);
            if (i != null) KINDS.put(i, k);
        }
    }

    /** The weapon subtype; other Overworld and Nether swords - sword; Vethea claws, swords and mauls; null - weapons of other worlds and everything else. */
    public static Kind kind(ItemStack s) {
        if (s == null) return null;
        Kind k = KINDS.get(s.getItem());
        if (k != null) return k;
        com.divinerpgextended.vethea.WeaponKind v = com.divinerpgextended.vethea.WeaponKind.ofItem(s);
        if (v == com.divinerpgextended.vethea.WeaponKind.CLAW) return Kind.CLAW;
        if (v == com.divinerpgextended.vethea.WeaponKind.HAMMER) return Kind.HAMMER;
        if (v == com.divinerpgextended.vethea.WeaponKind.SWORD) return Kind.SWORD;
        WeaponOrigin o = WeaponOrigin.of(s);
        return o == WeaponOrigin.OVERWORLD || o == WeaponOrigin.NETHER ? Kind.SWORD : null;
    }

    /** Crit without a jump: the full Terran set, on the ground; like the regular crit - not mounted and not blind. In the Vethea nightmare foreign sets are silent. */
    static boolean terranCrit(EntityPlayer p) {
        return p.onGround && p.ridingEntity == null && !p.isPotionActive(net.minecraft.potion.Potion.blindness)
            && PerkEffects.wears(p, new Item[] { net.divinerpg.utils.items.VanillaItemsArmor.terranHelmet, net.divinerpg.utils.items.VanillaItemsArmor.terranBody,
                net.divinerpg.utils.items.VanillaItemsArmor.terranLegs, net.divinerpg.utils.items.VanillaItemsArmor.terranBoots })
            && !com.divinerpgextended.vethea.Nightmare.active(p);
    }

    /** A hit cancelled by others does not arrive here. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSwing(AttackEntityEvent e) {
        if (e.entityPlayer.worldObj.isRemote) return;
        striker = e.entityPlayer;
        struck = e.target;
        strikeTick = e.entityPlayer.worldObj.getTotalWorldTime();
    }

    /** Before the rest: percentages of armor, accessories and bosses are already counted from the subtype's damage, and so is the mace wave. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHurt(LivingHurtEvent e) {
        EntityPlayer p = striker;
        Entity target = struck;
        if (p == null || e.source.getEntity() != p || e.source.getSourceOfDamage() != p) return;
        striker = null;
        struck = null;
        if (p.worldObj.getTotalWorldTime() != strikeTick) return;
        boolean dummy = target instanceof EntityTrainingDummy;
        EntityLivingBase v = e.entityLiving;
        boolean pvp = !dummy && v instanceof EntityPlayer;
        if (pvp && (!Config.meleeRolesPvp || Protection.spare(p, v))) return;
        if (terranCrit(p)) {
            e.ammount *= TERRAN_CRIT;
            p.onCriticalHit(target);
        }
        Kind k = Config.meleeRoles ? kind(p.getHeldItem()) : null;
        if (k == null) return;
        e.ammount *= pvp ? k.damagePvp : k.damage;
        int window = pvp ? k.windowPvp : k.window;
        EntityLivingBase holder = dummy ? (EntityLivingBase) target : v;
        // equals the maximum - vanilla (or the dummy) just wrote it, the hit is fresh; we do not extend the window with the difference inside the window
        if (window != VANILLA_WINDOW && holder.hurtResistantTime == holder.maxHurtResistantTime) holder.hurtResistantTime = holder.maxHurtResistantTime / 2 + window;
    }

    // ---------------- tooltip: server numbers, not the client's own config ----------------

    /** On the client - what the server sent at login (null - nothing yet). The client's own config would lie in the tooltip. */
    public static Sync shown;

    public static void registerMessages() {
        ColdHandler.NET.registerMessage(SyncHandler.class, Sync.class, 21, Side.CLIENT);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) ColdHandler.NET.sendTo(new Sync(Config.meleeRoles, Config.meleeRolesPvp), (EntityPlayerMP) e.player);
    }

    public static class Sync implements IMessage {

        private static final int KINDS_N = Kind.values().length;
        public boolean on, pvp;
        public final int[] window = new int[KINDS_N], windowPvp = new int[KINDS_N];
        public final float[] damage = new float[KINDS_N], damagePvp = new float[KINDS_N];

        public Sync() {}

        Sync(boolean on, boolean pvp) {
            this.on = on;
            this.pvp = pvp;
            for (Kind k : Kind.values()) {
                window[k.ordinal()] = k.window;
                windowPvp[k.ordinal()] = k.windowPvp;
                damage[k.ordinal()] = k.damage;
                damagePvp[k.ordinal()] = k.damagePvp;
            }
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            on = buf.readBoolean();
            pvp = buf.readBoolean();
            for (int i = 0; i < KINDS_N; i++) {
                window[i] = buf.readInt();
                windowPvp[i] = buf.readInt();
                damage[i] = buf.readFloat();
                damagePvp[i] = buf.readFloat();
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(on);
            buf.writeBoolean(pvp);
            for (int i = 0; i < KINDS_N; i++) {
                buf.writeInt(window[i]);
                buf.writeInt(windowPvp[i]);
                buf.writeFloat(damage[i]);
                buf.writeFloat(damagePvp[i]);
            }
        }
    }

    public static class SyncHandler implements IMessageHandler<Sync, IMessage> {

        @Override
        public IMessage onMessage(Sync m, MessageContext ctx) {
            shown = m;
            return null;
        }
    }
}
