package com.divinerpgextended.world;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;

import com.divinerpgextended.asm.TreeShiftHooks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Eden and Wildwood: when population of a new chunk puts blocks (lakes, flowers, ore, crowns) into a neighboring chunk that the
 * player already has, Minecraft sends them as a batch of single blocks, and the client recomputes the column's sky light for each -
 * for an island over the void this is a freeze of up to a second. Instead of the batch we send the chunk's changed sections whole (as
 * Minecraft itself does at 64+ changes): the client accepts them without recomputing for every block, and finishes the light spread out over time
 * ({@link com.divinerpgextended.client.TwilightRelight}). Ordinary changes (a player breaking and placing) - as before.
 */
public class PopulationResend implements IWorldAccess {

    /** For measurement: how many times a chunk went out whole instead of a batch of blocks. */
    public static int resends;

    private static Method watcher;
    private static Field count;
    private final WorldServer world;

    PopulationResend(WorldServer world) {
        this.world = world;
    }

    public static boolean applies(int dim) {
        return dim == ConfigurationHelper.eden || dim == ConfigurationHelper.wildwood;
    }

    public static class Installer {

        @SubscribeEvent
        public void onLoad(WorldEvent.Load e) {
            if (e.world instanceof WorldServer && applies(e.world.provider.dimensionId))
                e.world.addWorldAccess(new PopulationResend((WorldServer) e.world));
        }
    }

    @Override
    public void markBlockForUpdate(int x, int y, int z) {
        if (TreeShiftHooks.populating == 0) return;
        try {
            if (watcher == null) {
                watcher = ReflectionHelper.findMethod(PlayerManager.class, null, new String[] { "getOrCreateChunkWatcher", "func_72690_a" },
                    int.class, int.class, boolean.class);
                Class<?> inst = Class.forName("net.minecraft.server.management.PlayerManager$PlayerInstance");
                count = ReflectionHelper.findField(inst, "numberOfTilesToUpdate", "field_73262_e");
            }
            Object w = watcher.invoke(world.getPlayerManager(), x >> 4, z >> 4, false);
            if (w == null) return; // the chunk is not loaded by anyone - nothing to send
            int n = count.getInt(w);
            if (n > 0 && n < 64) {
                count.setInt(w, 64); // the sections are already marked by Minecraft - they go out whole
                resends++;
            }
        } catch (Throwable t) {
            watcher = null;
        }
    }

    @Override
    public void markBlockForRenderUpdate(int x, int y, int z) {}

    @Override
    public void markBlockRangeForRenderUpdate(int a, int b, int c, int d, int e, int f) {}

    @Override
    public void playSound(String s, double x, double y, double z, float v, float p) {}

    @Override
    public void playSoundToNearExcept(EntityPlayer pl, String s, double x, double y, double z, float v, float p) {}

    @Override
    public void spawnParticle(String s, double x, double y, double z, double a, double b, double c) {}

    @Override
    public void onEntityCreate(Entity e) {}

    @Override
    public void onEntityDestroy(Entity e) {}

    @Override
    public void playRecord(String s, int x, int y, int z) {}

    @Override
    public void broadcastSound(int id, int x, int y, int z, int d) {}

    @Override
    public void playAuxSFX(EntityPlayer pl, int id, int x, int y, int z, int d) {}

    @Override
    public void destroyBlockPartially(int id, int x, int y, int z, int p) {}

    @Override
    public void onStaticEntitiesChanged() {}
}
