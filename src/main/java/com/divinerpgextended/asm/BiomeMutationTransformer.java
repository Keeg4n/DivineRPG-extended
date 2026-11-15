package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The "hills and mutations" layer of Overworld generation (vanilla GenLayerHills and its copy in Biomes O' Plenty) turns a
 * hilly biome into a mutation if anything at all is registered under biome number + 128. Mods put their biomes on these
 * numbers (Twilight Forest: Thornlands 150 = jungle hills + 128, Dark Forest 146 = forest hills + 128 ...) - and Twilight Forest
 * biomes grow in the Overworld. All BiomeGenBase.getBiome calls in these layers go through
 * {@link BiomeHooks#mutation}: another mod's biome in the place of a mutation is treated as if it were not there.
 */
public class BiomeMutationTransformer implements IClassTransformer, Opcodes {

    private static final String HILLS = "net.minecraft.world.gen.layer.GenLayerHills", BOP = "biomesoplenty.common.world.layer.GenLayerMutationBOP";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !(HILLS.equals(transformedName) || BOP.equals(transformedName))) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                    if (in.getOpcode() != INVOKESTATIC) continue;
                    MethodInsnNode c = (MethodInsnNode) in;
                    // game: ahu.d(I)Lahu;  mods and Forge: BiomeGenBase.func_150568_d;  development: BiomeGenBase.getBiome
                    boolean obf = "ahu".equals(c.owner) && "d".equals(c.name) && "(I)Lahu;".equals(c.desc);
                    boolean deobf = "net/minecraft/world/biome/BiomeGenBase".equals(c.owner) && ("func_150568_d".equals(c.name) || "getBiome".equals(c.name))
                        && "(I)Lnet/minecraft/world/biome/BiomeGenBase;".equals(c.desc);
                    if (!obf && !deobf) continue;
                    c.owner = "com/divinerpgextended/asm/BiomeHooks";
                    c.name = "mutation";
                    c.itf = false; // we leave the descriptor as it was: the game will translate its names itself
                    patched++;
                }
            }
            if (patched == 0) return bytes;
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Biomes: mutations in {} - vanilla only ({} calls)", transformedName, patched);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Biomes: could not fix " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }
}
