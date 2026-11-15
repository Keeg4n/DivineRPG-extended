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
 * The ammo digit on the weapon icon: at the start of RenderItem.renderItemOverlayIntoGUI(font, textures, item, x, y, text)
 * we substitute the text from {@link AmmoCountHooks#text} if none was passed. The game itself draws it in the same place as the stack count.
 * Names: a (game), func_94148_a or renderItemOverlayIntoGUI (development); found by the six arguments ending with int, int, String. On
 * any error the class stays as it was.
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
                LOG.warn("Ammo count: found {} of 1 methods - leaving RenderItem unchanged", found);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Ammo count on the weapon icon hooked (RenderItem)");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Ammo count: could not hook, class unchanged", t);
            return bytes;
        }
    }
}
