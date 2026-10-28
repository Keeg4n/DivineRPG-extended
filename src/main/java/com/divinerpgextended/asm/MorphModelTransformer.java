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
 * Morph, получая форму убитого моба, копирует его модель и удаляет display list её частей через GLAllocation. У моделей,
 * где одна часть записана в несколько полей (Skythern Golem Divine и др.), список удаляется второй раз - GLAllocation
 * падает на NPE, и ошибка в обработке пакета выкидывает игрока из мира. Здесь удаление идёт через {@link MorphHooks}:
 * уже удалённый список пропускается.
 */
public class MorphModelTransformer implements IClassTransformer, Opcodes {

    static final String CLS = "morph.client.model.ModelHelper";
    private static final String GL = "net/minecraft/client/renderer/GLAllocation";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    /** Исправлено при загрузке (для самопроверки). */
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
                LOG.warn("Morph: удаление списков модели не найдено, класс без изменений");
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            patched = true;
            LOG.info("Morph: удаление списков модели без вылета ({})", n);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Morph: не удалось исправить, класс без изменений", t);
            return bytes;
        }
    }
}
