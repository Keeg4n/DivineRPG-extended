package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Камера в вагонетке Апалачии: в Forge 1.7.10 нет события настройки камеры, поэтому в конец
 * EntityRenderer.orientCamera (после поворота по взгляду игрока) добавляем вызов {@link CameraHooks#orient} - он
 * наклоняет мир на стене и переворачивает на потолке. Имя метода: h (игра), func_78467_g или orientCamera (разработка).
 * При любой ошибке класс остаётся как был.
 */
public class CameraTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.minecraft.client.renderer.EntityRenderer";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            for (MethodNode m : node.methods) {
                if (!"(F)V".equals(m.desc) || !("h".equals(m.name) || "func_78467_g".equals(m.name) || "orientCamera".equals(m.name))) continue;
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != RETURN) continue;
                    InsnList call = new InsnList();
                    call.add(new VarInsnNode(FLOAD, 1));
                    call.add(new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/asm/CameraHooks", "orient", "(F)V", false));
                    m.instructions.insertBefore(i, call);
                }
                patched++;
            }
            if (patched != 1) {
                LOG.warn("Камера вагонетки: найдено методов {} из 1 - оставляю класс без изменений", patched);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Камера вагонетки: наклон вида в вагонетке Апалачии подключён (EntityRenderer)");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Камера вагонетки: не удалось подключить, класс без изменений", t);
            return bytes;
        }
    }
}
