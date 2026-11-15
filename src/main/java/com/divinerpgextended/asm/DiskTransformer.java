package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Vethea disks: right click (ItemVetheanDisk.onItemRightClick) entirely goes into {@link com.divinerpgextended.vethea.DiskThrow#use}. */
public class DiskTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.divinerpg.items.vethea.ItemVetheanDisk";
    private static final String DESC = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            for (MethodNode m : node.methods) {
                if (!DESC.equals(m.desc) || !("func_77659_a".equals(m.name) || "onItemRightClick".equals(m.name))) continue;
                // the body is replaced entirely: an early exit before the old code would leave it without stack frames (VerifyError)
                m.instructions.clear();
                m.tryCatchBlocks.clear();
                if (m.localVariables != null) m.localVariables.clear();
                InsnList c = new InsnList();
                c.add(new VarInsnNode(ALOAD, 1));
                c.add(new VarInsnNode(ALOAD, 2));
                c.add(new VarInsnNode(ALOAD, 3));
                c.add(new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/vethea/DiskThrow", "use", DESC, false));
                c.add(new InsnNode(ARETURN));
                m.instructions.add(c);
                n++;
            }
            if (n == 0) {
                LOG.warn("Vethea disks: no throw in {} - they fly as in Divine", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Vethea disks: throw like the tomahawk");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Vethea disks: could not hook into " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }
}
