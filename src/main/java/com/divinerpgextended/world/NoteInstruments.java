package com.divinerpgextended.world;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.divinerpg.utils.blocks.VetheaBlocks;
import net.minecraft.block.Block;
import net.minecraftforge.event.world.NoteBlockEvent;

import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * Note blocks on the blocks of the twilight worlds and Vethea play their own instruments: the block under the note block picks the
 * instrument, as stone, sand, glass and wood do in vanilla. Eden block - hypersaw, Wildwood block - electric guitar, Apalachia block -
 * synth pluck, Skythern block - crystal bell, Mortum block - bass guitar, Vethea dream stone and bricks - music box (a lullaby).
 * <p>
 * The vanilla play is cancelled (NoteBlockEvent.Play) and our sound is played at the same pitch: the note block's 25 steps are
 * 0.5..2.0 around the sample's own note (sounds/note/*.ogg: bass F#2, hypersaw F#3, guitar and pluck F#4, bell and music box F#5;
 * CC0 samples from Freesound, see the README). A cancelled play sends no block event to the clients, so the note particle comes in our
 * own message and is spawned on the client tick (client/NoteParticles).
 */
public final class NoteInstruments {

    private static Map<Block, String> sounds;

    private static Map<Block, String> sounds() {
        if (sounds == null) {
            Map<Block, String> m = new HashMap<Block, String>();
            put(m, "hypersaw", TwilightBlocks.edenBlock);
            put(m, "guitar", TwilightBlocks.wildwoodBlock);
            put(m, "pluck", TwilightBlocks.apalachiaBlock);
            put(m, "bell", TwilightBlocks.skythernBlock);
            put(m, "bass", TwilightBlocks.mortumBlock);
            put(m, "musicbox", VetheaBlocks.dreamStone, VetheaBlocks.lightDreamBricks, VetheaBlocks.darkDreamBricks, VetheaBlocks.redDreamBricks,
                VetheaBlocks.lunaStone, VetheaBlocks.lunaBricks);
            sounds = m;
        }
        return sounds;
    }

    private static void put(Map<Block, String> m, String sound, Block... blocks) {
        for (Block b : blocks) if (b != null) m.put(b, sound);
    }

    /** The instrument of the block under a note block (the sound name after "note."), null - vanilla. */
    public static String instrument(Block below) {
        return below == null ? null : sounds().get(below);
    }

    /** Pitch of a note block step 0..24, as in vanilla: two octaves around the sample's own note. */
    public static float pitch(int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    @SubscribeEvent
    public void onPlay(NoteBlockEvent.Play e) {
        if (e.world.isRemote) return;
        String s = instrument(e.world.getBlock(e.x, e.y - 1, e.z));
        if (s == null) return;
        e.setCanceled(true);
        int note = e.getVanillaNoteId();
        e.world.playSoundEffect(e.x + 0.5, e.y + 0.5, e.z + 0.5, "divinerpgextended:note." + s, 3.0F, pitch(note));
        ColdHandler.NET.sendToAllAround(new Fx(e.x, e.y, e.z, note), new TargetPoint(e.world.provider.dimensionId, e.x + 0.5, e.y + 0.5, e.z + 0.5, 64));
    }

    public static void registerMessages() {
        ColdHandler.NET.registerMessage(FxHandler.class, Fx.class, 24, Side.CLIENT);
    }

    /** Note particles to spawn on the client: {x, y, z, note}. Filled on the network thread, emptied on the client tick. */
    public static final ConcurrentLinkedQueue<int[]> PENDING = new ConcurrentLinkedQueue<int[]>();

    public static class Fx implements IMessage {

        int x, y, z, note;

        public Fx() {}

        Fx(int x, int y, int z, int note) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.note = note;
        }

        @Override
        public void fromBytes(ByteBuf b) {
            x = b.readInt();
            y = b.readInt();
            z = b.readInt();
            note = b.readByte();
        }

        @Override
        public void toBytes(ByteBuf b) {
            b.writeInt(x);
            b.writeInt(y);
            b.writeInt(z);
            b.writeByte(note);
        }
    }

    public static class FxHandler implements IMessageHandler<Fx, IMessage> {

        @Override
        public IMessage onMessage(Fx msg, MessageContext ctx) {
            if (PENDING.size() < 256) PENDING.add(new int[] { msg.x, msg.y, msg.z, msg.note });
            return null;
        }
    }
}
