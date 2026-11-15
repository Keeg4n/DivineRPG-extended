package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The Divine Watcher in its lair behaves by the addon rules ({@link com.divinerpgextended.boss.WatcherHunt}): at the start of
 * its onLivingUpdate - "if lair - own move, then only what is common to all creatures (super), and exit": Divine's drift and shot
 * do not work in the lair. (updateEntityActionState is not called for Divine mobs - they have task-based AI.) Outside the lair -
 * as in Divine.
 */
public class WatcherTransformer implements IClassTransformer, Opcodes {

    private static final String CLS = "net.divinerpg.entities.vanilla.EntityTheWatcher";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !CLS.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            for (MethodNode m : node.methods) {
                if (!("func_70636_d".equals(m.name) || "onLivingUpdate".equals(m.name)) || !"()V".equals(m.desc)) continue;
                InsnList in = new InsnList();
                LabelNode go = new LabelNode();
                in.add(new VarInsnNode(ALOAD, 0));
                in.add(new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/boss/WatcherHunt", "lair", "(Lnet/divinerpg/entities/vanilla/EntityTheWatcher;)Z", false));
                in.add(new JumpInsnNode(IFEQ, go));
                in.add(new VarInsnNode(ALOAD, 0));
                in.add(new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/boss/WatcherHunt", "act", "(Lnet/divinerpg/entities/vanilla/EntityTheWatcher;)Z", false));
                in.add(new InsnNode(POP));
                in.add(new VarInsnNode(ALOAD, 0));
                in.add(new MethodInsnNode(INVOKESPECIAL, node.superName, m.name, "()V", false));
                in.add(new InsnNode(RETURN));
                in.add(go);
                if ((node.version & 0xFFFF) >= 50) in.add(new FrameNode(F_SAME, 0, null, 0, null));
                m.instructions.insert(in);
                patched++;
            }
            if (patched == 0) return bytes;
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Watcher: hunts by addon rules in the lair (edits " + patched + ")");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Watcher: hunting not hooked, class unchanged", t);
            return bytes;
        }
    }
}
