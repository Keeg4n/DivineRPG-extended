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
 * Iceika population places Rollum and archer dungeons at a random height below 50, ice lakes up to 128, giant
 * trees: neighboring chunks are populated after the Frozen Throne dome is already built in them, and all this falls into it
 * (floating lakes, holes in the vault, dungeons inside the arena). Generator calls in the population go through
 * {@link IceikaThroneHooks}: inside the Throne they are skipped.
 */
public class IceikaThroneTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.divinerpg.dimensions.iceika.ChunkProviderIceika";
    private static final String GEN_DESC = "(Lnet/minecraft/world/World;Ljava/util/Random;III)Z";
    private static final String SET_DESC = "(IIILnet/minecraft/block/Block;II)Z";
    private static final String HOOKS = "com/divinerpgextended/asm/IceikaThroneHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int calls = 0;
            for (MethodNode m : node.methods) {
                if (!("func_73153_a".equals(m.name) || "populate".equals(m.name))) continue;
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) i;
                    // freezing and snow at the end of population are placed straight into the world - also bypassing the Throne
                    if (SET_DESC.equals(c.desc) && ("func_147465_d".equals(c.name) || "setBlock".equals(c.name))) {
                        c.setOpcode(INVOKESTATIC);
                        c.desc = "(Lnet/minecraft/world/World;" + SET_DESC.substring(1);
                        c.owner = HOOKS;
                        c.name = "set";
                        calls++;
                        continue;
                    }
                    if (!GEN_DESC.equals(c.desc) || !("func_76484_a".equals(c.name) || "generate".equals(c.name))) continue;
                    c.setOpcode(INVOKESTATIC);
                    c.desc = "(Lnet/minecraft/world/gen/feature/WorldGenerator;" + GEN_DESC.substring(1);
                    c.owner = HOOKS;
                    c.name = "gen";
                    calls++;
                }
            }
            if (calls == 0) {
                LOG.warn("Iceika Throne: generator calls not found - class unchanged");
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Iceika Throne: population does not touch the Throne ({} generator calls)", calls);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Iceika Throne: could not fix population, class unchanged", t);
            return bytes;
        }
    }
}
