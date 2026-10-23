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
 * Камера в вагонетке Апалачии: в Forge 1.7.10 нет события настройки камеры, поэтому в начало
 * EntityRenderer.orientCamera (до поворота по взгляду игрока) добавляем вызов {@link CameraHooks#orient} - крен картинки
 * вокруг оси взгляда на стене и потолке, а вызовы player.setAngles в updateCameraAndRender (мышь) идут через
 * {@link CameraHooks#angles} - сдвиг мыши в осях наклонённого экрана. Имена: h / b (игра), func_78467_g / func_78480_b
 * или orientCamera / updateCameraAndRender (разработка); setAngles - c, func_70082_c, setAngles. При любой ошибке класс
 * остаётся как был.
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
            int orient = 0, mouse = 0;
            for (MethodNode m : node.methods) {
                if (!"(F)V".equals(m.desc)) continue;
                if ("h".equals(m.name) || "func_78467_g".equals(m.name) || "orientCamera".equals(m.name)) {
                    InsnList call = new InsnList();
                    call.add(new VarInsnNode(FLOAD, 1));
                    call.add(new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/asm/CameraHooks", "orient", "(F)V", false));
                    m.instructions.insert(call);
                    orient++;
                } else if ("b".equals(m.name) || "func_78480_b".equals(m.name) || "updateCameraAndRender".equals(m.name)) {
                    for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                        if (i.getOpcode() != INVOKEVIRTUAL) continue;
                        MethodInsnNode c = (MethodInsnNode) i;
                        if (!"(FF)V".equals(c.desc) || !("c".equals(c.name) || "func_70082_c".equals(c.name) || "setAngles".equals(c.name))) continue;
                        c.setOpcode(INVOKESTATIC);
                        c.owner = "com/divinerpgextended/asm/CameraHooks";
                        c.name = "angles";
                        c.desc = "(Ljava/lang/Object;FF)V";
                        c.itf = false;
                        mouse++;
                    }
                }
            }
            if (orient != 1) {
                LOG.warn("Камера вагонетки: найдено методов {} из 1 - оставляю класс без изменений", orient);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Камера вагонетки: крен вида в вагонетке Апалачии подключён (EntityRenderer), мышь - {} вызовов", mouse);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Камера вагонетки: не удалось подключить, класс без изменений", t);
            return bytes;
        }
    }
}
