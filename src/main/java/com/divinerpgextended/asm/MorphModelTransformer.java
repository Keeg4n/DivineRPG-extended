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
 * Morph, when getting the form of a killed mob, copies its model and deletes the display lists of its parts through GLAllocation. In models
 * where one part is stored in several fields (Skythern Golem Divine and others), the list is deleted a second time - GLAllocation
 * fails with an NPE, and the error in packet handling kicks the player out of the world. Here deletion goes through {@link MorphHooks}:
 * an already deleted list is skipped.
 */
public class MorphModelTransformer implements IClassTransformer, Opcodes {

    static final String CLS = "morph.client.model.ModelHelper";
    private static final String GL = "net/minecraft/client/renderer/GLAllocation";
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
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != INVOKESTATIC) continue;
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (!GL.equals(c.owner) || !"(I)V".equals(c.desc) || !("func_74523_b".equals(c.name) || "deleteDisplayLists".equals(c.name))) continue;
                    c.owner = "com/divinerpgextended/asm/MorphHooks";
                    c.name = "deleteList";
                    n++;
                }
            }
            if (n == 0) {
                LOG.warn("Morph: model list removal not found, class unchanged");
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            patched = true;
            LOG.info("Morph: model list removal without a crash ({})", n);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Morph: could not fix, class unchanged", t);
            return bytes;
        }
    }
}
