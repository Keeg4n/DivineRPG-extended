package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Костюм H.E.V. не только на игроке: стойки для брони (BiblioCraft берёт у вещи её модель брони напрямую) и мобы в броне
 * спрашивают модель и текстуру брони у вещи - {@code Item.getArmorModel} и {@code ForgeHooksClient.getArmorTexture}. В начало
 * обоих - вызов {@link EasterHooks} с тем же описанием: вернул не null - это и ответ, иначе всё как было.
 */
public class EasterArmorTransformer implements IClassTransformer, Opcodes {

    private static final String ITEM = "net.minecraft.item.Item", HOOKS = "net.minecraftforge.client.ForgeHooksClient";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !(ITEM.equals(transformedName) || HOOKS.equals(transformedName))) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            boolean item = ITEM.equals(transformedName);
            int patched = 0;
            for (MethodNode m : node.methods) {
                boolean stat = (m.access & ACC_STATIC) != 0;
                Type[] args = Type.getArgumentTypes(m.desc);
                if (item ? !("getArmorModel".equals(m.name) && !stat && args.length == 3)
                    : !("getArmorTexture".equals(m.name) && stat && args.length == 5)) continue;
                InsnList in = new InsnList();
                int slot = stat ? 0 : 1;
                for (Type t : args) {
                    in.add(new VarInsnNode(t.getOpcode(ILOAD), slot));
                    slot += t.getSize();
                }
                in.add(new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/asm/EasterHooks", item ? "armorModel" : "armorTexture", m.desc, false));
                LabelNode keep = new LabelNode();
                in.add(new InsnNode(DUP));
                in.add(new JumpInsnNode(IFNULL, keep));
                in.add(new InsnNode(ARETURN));
                in.add(keep);
                in.add(new InsnNode(POP));
                m.instructions.insert(in);
                patched++;
            }
            if (patched == 0) return bytes;
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Пасхалки: модель и текстура брони H.E.V. через {} ({})", transformedName, patched);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Пасхалки: не удалось поправить " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }
}
