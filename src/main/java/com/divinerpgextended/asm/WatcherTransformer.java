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
 * Смотритель Divine в своём логове ведёт себя по правилам аддона ({@link com.divinerpgextended.boss.WatcherHunt}): в начало
 * его updateEntityActionState - "если логово - свой ход и выйти". Вне логова (вызов где угодно) - как в Divine.
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
                if (!("func_70619_bc".equals(m.name) || "updateEntityActionState".equals(m.name)) || !"()V".equals(m.desc)) continue;
                InsnList in = new InsnList();
                LabelNode go = new LabelNode();
                in.add(new VarInsnNode(ALOAD, 0));
                in.add(new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/boss/WatcherHunt", "act", "(Lnet/divinerpg/entities/vanilla/EntityTheWatcher;)Z", false));
                in.add(new JumpInsnNode(IFEQ, go));
                in.add(new InsnNode(RETURN));
                in.add(go);
                if ((node.version & 0xFFFF) >= 50) in.add(new FrameNode(F_SAME, 0, null, 0, null));
                m.instructions.insert(in);
                patched++;
            }
            if (patched == 0) return bytes;
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Смотритель: в логове охотится по правилам аддона");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Смотритель: охота не подключена, класс без изменений", t);
            return bytes;
        }
    }
}
