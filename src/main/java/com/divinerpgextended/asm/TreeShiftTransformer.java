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
 * Divine twilight world population places trees (and Eden stone cones) from the chunk corner instead of with a +8 shift, as
 * it should: the crown reaches into neighboring chunks that the player has already loaded. The server sends these blocks to the client, and the client
 * recomputes sky light for each - above the void at the island edge this is a column ~100 high in five columns, hence
 * the stutter when exploring Eden and Wildwood. Calls of these generators go through {@link TreeShiftHooks}: the same point
 * with a +8 shift and a height recomputed for the new place. Without a shift: Wildwood tree 2 (already +8), ores, flowers, lakes.
 */
public class TreeShiftTransformer implements IClassTransformer, Opcodes {

    private static final String PKG = "net.divinerpg.dimensions.twilight.";
    private static final String[] TARGETS = { "eden.ChunkProviderEden", "wildwood.ChunkProviderWildwood",
        "apalachia.ChunkProviderApalachia", "skythern.ChunkProviderSkythern", "mortum.ChunkProviderMortum" };
    private static final String GEN_DESC = "(Lnet/minecraft/world/World;Ljava/util/Random;III)Z";
    private static final String CONE = "net/divinerpg/dimensions/vethea/all/WorldGenConeUp";
    private static final String CONE_DESC = "(Lnet/minecraft/world/World;Ljava/util/Random;IIII)Z";
    private static final String HOOKS = "com/divinerpgextended/asm/TreeShiftHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !transformedName.startsWith(PKG) || Boolean.getBoolean("dre.noTreeShift")) return bytes;
        boolean target = false;
        for (String t : TARGETS) target |= transformedName.equals(PKG + t);
        if (!target) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int trees = 0, cones = 0;
            for (MethodNode m : node.methods) {
                if (!("func_73153_a".equals(m.name) || "populate".equals(m.name))) continue;
                // the "population in progress" mark for the whole method (for resending chunks whole - PopulationResend)
                m.instructions.insert(new MethodInsnNode(INVOKESTATIC, HOOKS, "begin", "()V", false));
                for (AbstractInsnNode r = m.instructions.getFirst(); r != null; r = r.getNext()) {
                    if (r.getOpcode() == RETURN) m.instructions.insertBefore(r, new MethodInsnNode(INVOKESTATIC, HOOKS, "end", "()V", false));
                }
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (GEN_DESC.equals(c.desc) && ("func_76484_a".equals(c.name) || "generate".equals(c.name)) && tree(c.owner)) {
                        c.setOpcode(INVOKESTATIC);
                        c.desc = "(Lnet/minecraft/world/gen/feature/WorldGenerator;" + GEN_DESC.substring(1);
                        c.owner = HOOKS;
                        c.name = "tree";
                        trees++;
                    } else if (CONE.equals(c.owner) && "generate".equals(c.name) && CONE_DESC.equals(c.desc)) {
                        c.setOpcode(INVOKESTATIC);
                        c.desc = "(L" + CONE + ";" + CONE_DESC.substring(1);
                        c.owner = HOOKS;
                        c.name = "cone";
                        cones++;
                    }
                }
            }
            if (trees + cones == 0) {
                LOG.warn("Trees {}: calls not found - class unchanged", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Trees {}: shift +8 ({} trees, {} cones)", transformedName, trees, cones);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Trees " + transformedName + ": could not fix, class unchanged", t);
            return bytes;
        }
    }

    /** Divine tree generators that are placed from the chunk corner (Wildwood tree 2 is already shifted). */
    private static boolean tree(String owner) {
        String simple = owner.substring(owner.lastIndexOf('/') + 1);
        return owner.startsWith("net/divinerpg/") && simple.contains("Tree") && !"WorldGenWildwoodTree2".equals(simple);
    }
}
