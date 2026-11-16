package com.divinerpgextended.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.entities.vanilla.EntityTheWatcher;
import net.divinerpg.utils.blocks.ArcanaBlocks;
import net.divinerpg.utils.blocks.TwilightBlocks;
import net.divinerpg.utils.blocks.VetheaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.bestiary.Bestiary.Chapter;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Personal passes to the worlds: a portal or a Nightmare Bed that someone else lit admits only those who reached the world
 * themselves ({@link ProgressSpawns#reached}). Arcana and Eden - after the Watcher, each next twilight world - after the guardian of
 * the previous one, Vethea - after the bosses of the Spirit of the Universe. Otherwise on a server a newcomer walked through other
 * players' portals into any world and took its chests, fish and ore.
 * <ul>
 * <li>The Eye of the Watcher drops from the Watcher; Arcana portal frames are crafted only with it (the Divine recipe is replaced).</li>
 * <li>Portals: those who have not reached the world get a transfer pause already on approach (Divine transfers only at zero pause,
 * and on the very first touch - checking only the block underfoot is too late) and are told what is needed. Leaving a world through
 * its own portal is always allowed.</li>
 * <li>Nightmare Bed: the click is cancelled, the chat lists the missing bosses.</li>
 * <li>If one still got into the world (a fall, a teleport, a command) - we return him at once to where he stood recently, far from
 * the portal.</li>
 * </ul>
 * Creative mode is not touched. Arcana is always gated, the twilight worlds and Vethea - by {@link Config#worldGates}.
 */
public class WorldGates {

    /** The player's last safe place in a world he has reached: dimension and coordinates. */
    private static final Map<UUID, double[]> SAFE = new HashMap<UUID, double[]>();

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (!(event.entityLiving instanceof EntityTheWatcher) || event.entityLiving.worldObj.isRemote) return;
        EntityItem eye = new EntityItem(event.entityLiving.worldObj, event.entityLiving.posX, event.entityLiving.posY + 1,
            event.entityLiving.posZ, new ItemStack(ModItems.watcherEye));
        event.drops.add(eye);
    }

    /** The gated world of a dimension, or null. */
    public static Chapter chapterOf(int dim) {
        if (dim == ConfigurationHelper.arcana) return Chapter.ARCANA;
        if (!Config.worldGates) return null;
        if (dim == ConfigurationHelper.eden) return Chapter.EDEN;
        if (dim == ConfigurationHelper.wildwood) return Chapter.WILDWOOD;
        if (dim == ConfigurationHelper.apalachia) return Chapter.APALACHIA;
        if (dim == ConfigurationHelper.skythern) return Chapter.SKYTHERN;
        if (dim == ConfigurationHelper.mortum) return Chapter.MORTUM;
        if (dim == ConfigurationHelper.vethea) return Chapter.VETHEA;
        return null;
    }

    /** The gated world this portal block leads to, or null. */
    public static Chapter portalTo(Block b) {
        if (b == ArcanaBlocks.arcanaPortal) return Chapter.ARCANA;
        if (!Config.worldGates) return null;
        if (b == TwilightBlocks.edenPortal) return Chapter.EDEN;
        if (b == TwilightBlocks.wildwoodPortal) return Chapter.WILDWOOD;
        if (b == TwilightBlocks.apalachiaPortal) return Chapter.APALACHIA;
        if (b == TwilightBlocks.skythernPortal) return Chapter.SKYTHERN;
        if (b == TwilightBlocks.mortumPortal) return Chapter.MORTUM;
        if (b == VetheaBlocks.vetheaPortal) return Chapter.VETHEA;
        return null;
    }

    static int dimOf(Chapter ch) {
        switch (ch) {
            case ARCANA: return ConfigurationHelper.arcana;
            case EDEN: return ConfigurationHelper.eden;
            case WILDWOOD: return ConfigurationHelper.wildwood;
            case APALACHIA: return ConfigurationHelper.apalachia;
            case SKYTHERN: return ConfigurationHelper.skythern;
            case MORTUM: return ConfigurationHelper.mortum;
            default: return ConfigurationHelper.vethea;
        }
    }

    public static boolean allowed(EntityPlayer p, Chapter ch) {
        return ch == null || p.capabilities.isCreativeMode || ProgressSpawns.reached(p, ch);
    }

    /** The world the player is in without having reached it (he must be returned), or null. */
    public static Chapter stray(EntityPlayer p) {
        Chapter here = chapterOf(p.dimension);
        return allowed(p, here) ? null : here;
    }

    /** Why the player is not let in: who must be defeated first. */
    public static IChatComponent refusal(EntityPlayer p, Chapter ch) {
        IChatComponent m;
        if (ch == Chapter.ARCANA) m = new ChatComponentTranslation("dre.chat.arcana_locked");
        else if (ch == Chapter.VETHEA) {
            m = new ChatComponentTranslation("dre.chat.vethea_locked");
            boolean first = true;
            for (Boss b : ProgressSpawns.VETHEA_NEED) {
                if (BossProgress.has(p, b)) continue;
                if (!first) m.appendSibling(new ChatComponentText(", "));
                m.appendSibling(new ChatComponentTranslation(b.langKey()));
                first = false;
            }
            int left = ProgressSpawns.VETHEA_MORTUM - ProgressSpawns.mortumBosses(p);
            if (left > 0) {
                if (!first) m.appendSibling(new ChatComponentText(", "));
                m.appendSibling(new ChatComponentTranslation("dre.chat.vethea_mortum", left, ProgressSpawns.VETHEA_MORTUM));
            }
        } else {
            m = new ChatComponentTranslation("dre.chat.world_locked", new ChatComponentTranslation("dre.world." + ch.name().toLowerCase()),
                new ChatComponentTranslation(ProgressSpawns.needKey(ch)));
        }
        m.getChatStyle().setColor(ch == Chapter.VETHEA ? EnumChatFormatting.DARK_PURPLE : EnumChatFormatting.GRAY);
        return m;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer p = event.player;
        if (event.phase != TickEvent.Phase.START || p.worldObj.isRemote || !(p instanceof EntityPlayerMP) || p.capabilities.isCreativeMode) return;
        Chapter stray = stray(p);
        if (stray != null) {
            sendBack((EntityPlayerMP) p, stray);
            return;
        }
        if (p.ticksExisted % 4 != 0) return;
        Chapter locked = lockedPortalNear(p);
        if (locked != null) {
            p.timeUntilPortal = 20; // the Divine portal will not transfer until the pause ends
            if (p.ticksExisted % 60 == 0) p.addChatMessage(refusal(p, locked));
            if (locked == Chapter.ARCANA) com.divinerpgextended.DreAchievements.give(p, com.divinerpgextended.DreAchievements.lockedPortal);
        } else if (p.ticksExisted % 20 == 0 && p.onGround) {
            SAFE.put(p.getUniqueID(), new double[] { p.dimension, p.posX, p.posY, p.posZ });
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        SAFE.remove(e.player.getUniqueID());
    }

    /** Nightmare Bed outside Vethea: without the Spirit of the Universe bosses you cannot fall asleep - we say whom to defeat. */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        // only on the server: the client does not know the progress, and a click cancelled there would not reach the server
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.world.isRemote) return;
        if (e.world.getBlock(e.x, e.y, e.z) != VetheaBlocks.nightmareBedBlock || e.entityPlayer.dimension == ConfigurationHelper.vethea) return;
        if (!Config.worldGates || allowed(e.entityPlayer, Chapter.VETHEA)) return;
        e.setCanceled(true);
        e.entityPlayer.addChatMessage(refusal(e.entityPlayer, Chapter.VETHEA));
    }

    /**
     * A portal within a couple of blocks around (and below - with a margin for falling speed) that leads to a world the player has not
     * reached: that world, otherwise null. The portal of the world the player is in leads out - it does not count.
     */
    public static Chapter lockedPortalNear(EntityPlayer p) {
        int x0 = MathHelper.floor_double(p.boundingBox.minX) - 2, x1 = MathHelper.floor_double(p.boundingBox.maxX) + 2;
        int z0 = MathHelper.floor_double(p.boundingBox.minZ) - 2, z1 = MathHelper.floor_double(p.boundingBox.maxZ) + 2;
        int y1 = MathHelper.floor_double(p.boundingBox.minY) + 1;
        int y0 = MathHelper.floor_double(p.boundingBox.minY + Math.min(0, p.motionY * 4)) - 2; // every 4 ticks: margin for a fall in 4 ticks
        Chapter seen = null;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++) {
                    Chapter ch = portalTo(p.worldObj.getBlock(x, y, z));
                    if (ch == null || ch == seen) continue;
                    seen = ch;
                    if (dimOf(ch) != p.dimension && !allowed(p, ch)) return ch;
                }
        return null;
    }

    /** Where to return a player who is not allowed here: his last safe place, otherwise his bed or the spawn of the Overworld. */
    public static double[] backTarget(EntityPlayerMP p) {
        double[] s = SAFE.get(p.getUniqueID());
        if (s != null && chapterOf((int) s[0]) != null && !allowed(p, chapterOf((int) s[0]))) s = null;
        if (s != null) return s;
        WorldServer home = p.mcServer.worldServerForDimension(0);
        ChunkCoordinates bed = p.getBedLocation(0);
        ChunkCoordinates at = bed != null ? EntityPlayer.verifyRespawnCoordinates(home, bed, p.isSpawnForced(0)) : null;
        if (at == null) {
            ChunkCoordinates sp = home.getSpawnPoint();
            at = new ChunkCoordinates(sp.posX, home.getTopSolidOrLiquidBlock(sp.posX, sp.posZ), sp.posZ);
        }
        return new double[] { 0, at.posX + 0.5, at.posY + 0.1, at.posZ + 0.5 };
    }

    private static void sendBack(EntityPlayerMP p, Chapter from) {
        double[] t = backTarget(p);
        final int dim = (int) t[0];
        final double x = t[1], y = t[2], z = t[3];
        p.timeUntilPortal = 100;
        if (dim == p.dimension) p.setPositionAndUpdate(x, y, z);
        else {
            WorldServer target = p.mcServer.worldServerForDimension(dim);
            p.mcServer.getConfigurationManager().transferPlayerToDimension(p, dim, new Teleporter(target) {

                @Override
                public void placeInPortal(Entity e, double px, double py, double pz, float yaw) {
                    e.setLocationAndAngles(x, y, z, e.rotationYaw, e.rotationPitch);
                    e.motionX = e.motionY = e.motionZ = 0;
                }

                @Override
                public boolean placeInExistingPortal(Entity e, double px, double py, double pz, float yaw) {
                    placeInPortal(e, px, py, pz, yaw);
                    return true;
                }

                @Override
                public boolean makePortal(Entity e) {
                    return true;
                }

                @Override
                public void removeStalePortalLocations(long time) {}
            });
            p.setPositionAndUpdate(x, y, z);
        }
        p.addChatMessage(refusal(p, from));
    }
}
