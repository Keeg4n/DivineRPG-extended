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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Цифра патронов на значке оружия: в начало RenderItem.renderItemOverlayIntoGUI(шрифт, текстуры, вещь, x, y, текст)
 * подставляем текст из {@link AmmoCountHooks#text}, если его не передали. Игра сама рисует его там же, где число в стопке.
 * Имена: a (игра), func_94148_a или renderItemOverlayIntoGUI (разработка); узнаём по шести аргументам с int, int, String в
 * конце. При любой ошибке класс остаётся как был.
 */
public class AmmoCountTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.minecraft.client.renderer.entity.RenderItem";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int found = 0;
            for (MethodNode m : node.methods) {
                if (!("a".equals(m.name) || "func_94148_a".equals(m.name) || "renderItemOverlayIntoGUI".equals(m.name))) continue;
                Type[] args = Type.getArgumentTypes(m.desc);
                if (args.length != 6 || Type.getReturnType(m.desc) != Type.VOID_TYPE || args[3] != Type.INT_TYPE || args[4] != Type.INT_TYPE
                    || !"Ljava/lang/String;".equals(args[5].getDescriptor()) || (m.access & ACC_STATIC) != 0) continue;
                InsnList call = new InsnList();
                call.add(new VarInsnNode(ALOAD, 6));
                call.add(new VarInsnNode(ALOAD, 3));
                call.add(new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/asm/AmmoCountHooks", "text", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;", false));
                call.add(new VarInsnNode(ASTORE, 6));
                m.instructions.insert(call);
                found++;
            }
            if (found != 1) {
                LOG.warn("Цифра патронов: найдено методов {} из 1 - оставляю RenderItem без изменений", found);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Цифра патронов на значке оружия подключена (RenderItem)");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Цифра патронов: не удалось подключить, класс без изменений", t);
            return bytes;
        }
    }
}
