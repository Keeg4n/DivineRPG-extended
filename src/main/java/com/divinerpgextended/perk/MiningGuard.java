package com.divinerpgextended.perk;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Blocks with mining rules (ores by pickaxe tiers, Arcana blocks) are unbreakable for everyone, the real hardness is used only for
 * breaking by a player: {@link com.divinerpgextended.asm.MiningGuardTransformer} points at it in ForgeHooks.blockStrength, and
 * which tool fits is decided by the break events. Otherwise the rules are bypassed by anything that digs without these events and looks only at
 * hardness: the Blood Magic bound pickaxe, the Thaumcraft drill, pistons and others. Blocks from {@link #allowMove} can still be moved:
 * the real hardness is also seen by the "Moving" focus and the teleposer ({@link com.divinerpgextended.asm.MoverTransformer}).
 */
public final class MiningGuard {

    private static final Map<Block, Float> REAL = new IdentityHashMap<Block, Float>();
    private static final Set<Block> MOVABLE = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
    private static Field hardness;
    private static boolean moving;

    private MiningGuard() {}

    /** Real hardness of a block (for all other blocks - the usual one). */
    public static float real(Block b, World w, int x, int y, int z) {
        Float f = REAL.get(b);
        return f != null ? f : b.getBlockHardness(w, x, y, z);
    }

    /** Protect a block: real - hardness when broken by a player. */
    public static void guard(Block b, float real) {
        if (b != null) REAL.put(b, real);
    }

    /** A protected block can be moved. */
    public static void allowMove(Block b) {
        if (REAL.containsKey(b)) MOVABLE.add(b);
    }

    /** Hardness for the "Moving" focus: real for movable blocks, usual for the rest. */
    public static float forMover(Block b, World w, int x, int y, int z) {
        return MOVABLE.contains(b) ? REAL.get(b) : b.getBlockHardness(w, x, y, z);
    }

    /** The teleposer started working: during that time movable blocks show real hardness to everyone. */
    public static void beginMove(TileEntity te) {
        move(te, true);
    }

    public static void endMove(TileEntity te) {
        move(te, false);
    }

    /** Server only: in singleplayer the client runs in another thread and its teleposer must not close the server one's window. */
    private static void move(TileEntity te, boolean on) {
        World w = te.getWorldObj();
        if (w == null || w.isRemote || moving == on) return;
        moving = on;
        try {
            if (hardness == null) hardness = ReflectionHelper.findField(Block.class, "blockHardness", "field_149782_v");
            for (Block b : MOVABLE) hardness.setFloat(b, on ? REAL.get(b) : -1F);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static float field(Block b) {
        return (Float) ReflectionHelper.getPrivateValue(Block.class, b, "blockHardness", "field_149782_v");
    }

    /** Ores and stone with addon rules: the native hardness is removed. Arcana blocks are added by {@link com.divinerpgextended.item.ItemArcanePick}. */
    public static void guardOres() {
        guard(ModBlocks.jungleOre, field(ModBlocks.jungleOre));
        if (!Config.miningTiers) return;
        for (TwilightWorld w : TwilightWorld.values()) if (w.ore() != null) guard(w.ore(), field(w.ore()));
        guard(TwilightBlocks.twilightStone, field(TwilightBlocks.twilightStone));
    }

    /**
     * Set unbreakability on server start (including the integrated one): other tools dig only on the server, and on the client
     * the hardness stays native, so the Waila tooltip in multiplayer does not call the ore "unbreakable".
     */
    public static void apply() {
        for (Block b : REAL.keySet()) ReflectionHelper.setPrivateValue(Block.class, b, -1F, "blockHardness", "field_149782_v");
    }
}
