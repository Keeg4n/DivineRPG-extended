package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The Gaia transformation ritual (Blood Magic) picks a biome by temperature and humidity, iterating over the biome array, but
 * writes into the chunk not the ID of the found biome but its index among the non-empty cells of the array. While biomes go in a row (vanilla
 * 0-39), this coincides; after the first gap in the array (in vanilla - from 40) the ritual sets the wrong biome, and Divine biomes (ID 170+)
 * cannot be obtained. Here the index is replaced with the real ID of the found biome.
 */
public class BiomeRitualTransformer implements IClassTransformer, Opcodes {

    static final String CLS = "WayofTime.alchemicalWizardry.common.rituals.RitualEffectBiomeChanger";
    private static final String BIOME = "net/minecraft/world/biome/BiomeGenBase";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    /** Fixed on load (for the selftest). */
    public static boolean patched;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !CLS.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            for (MethodNode m : node.methods) {
                int biome = -1;
                String id = null;
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    // the biome being iterated: aload b; getfield temperature
                    if (i.getOpcode() == GETFIELD && BIOME.equals(((FieldInsnNode) i).owner) && i.getPrevious() != null && i.getPrevious().getOpcode() == ALOAD) {
                        String f = ((FieldInsnNode) i).name;
                        if ("field_76750_F".equals(f) || "temperature".equals(f)) {
                            biome = ((VarInsnNode) i.getPrevious()).var;
                            id = f.startsWith("field_") ? "field_76756_M" : "biomeID";
                        }
                    }
                    // both differences matched: fcmpg; ifge; iload index; istore chosen - we replace the index with the ID
                    AbstractInsnNode jump = back(i), cmp = jump == null ? null : back(jump), store = ahead(i);
                    if (biome >= 0 && i.getOpcode() == ILOAD && store != null && store.getOpcode() == ISTORE
                        && jump instanceof JumpInsnNode && jump.getOpcode() == IFGE && cmp != null && cmp.getOpcode() == FCMPG) {
                        AbstractInsnNode load = new VarInsnNode(ALOAD, biome);
                        m.instructions.insertBefore(i, load);
                        m.instructions.set(i, new FieldInsnNode(GETFIELD, BIOME, id, "I"));
                        i = load.getNext();
                        n++;
                    }
                }
            }
            if (n != 1) {
                LOG.warn("Gaia ritual: biome selection spot not found unambiguously ({}), class unchanged", n);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            patched = true;
            LOG.info("Gaia ritual: the real biome ID is written to the chunk");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Gaia ritual: could not fix, class unchanged", t);
            return bytes;
        }
    }

    /** The nearest real instruction before i (labels, line numbers and frames are skipped). */
    private static AbstractInsnNode back(AbstractInsnNode i) {
        for (i = i.getPrevious(); i != null && i.getOpcode() < 0; i = i.getPrevious()) {}
        return i;
    }

    /** The nearest real instruction after i. */
    private static AbstractInsnNode ahead(AbstractInsnNode i) {
        for (i = i.getNext(); i != null && i.getOpcode() < 0; i = i.getNext()) {}
        return i;
    }
}
