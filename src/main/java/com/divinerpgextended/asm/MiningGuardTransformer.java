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
 * Breaking by a player takes the real durability of protected blocks ({@link com.divinerpgextended.perk.MiningGuard}): in
 * ForgeHooks.blockStrength the Block.getBlockHardness read is replaced with a MiningGuard.real call. This is the only read of
 * durability in the method; there are no other calls with such arguments there, so the name is not checked (it differs between the environment and the game).
 */
public class MiningGuardTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.minecraftforge.common.ForgeHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    /** The patch is in place: checked by the selftest. */
    public static boolean patched;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int count = 0;
            for (MethodNode m : node.methods) {
                if (!"blockStrength".equals(m.name)) continue;
                for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                    if (in.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) in;
                    if (!c.desc.matches("\\(L[^;]+;III\\)F")) continue;
                    MethodInsnNode rep = new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/perk/MiningGuard", "real",
                        "(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;III)F", false);
                    m.instructions.set(c, rep);
                    in = rep;
                    count++;
                }
            }
            if (count != 1) {
                LOG.error("Block protection: found {} strength reads in blockStrength instead of one, class unchanged", count);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            patched = true;
            LOG.info("Block protection: breaking by a player takes the real strength");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Block protection: could not modify ForgeHooks, class unchanged", t);
            return bytes;
        }
    }
}
