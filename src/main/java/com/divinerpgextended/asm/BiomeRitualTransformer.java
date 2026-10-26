package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Ритуал преображения Гайи (Blood Magic) подбирает биом по температуре и влажности, перебирая массив биомов, но
 * записывает в чанк не ID найденного биома, а его номер среди непустых ячеек массива. Пока биомы идут подряд (ванильные
 * 0-39), это совпадает; за первой дырой в массиве (у ванили - с 40) ритуал ставит чужой биом, и биомы Divine (ID 170+)
 * получить нельзя. Здесь номер заменяется на настоящий ID найденного биома.
 */
public class BiomeRitualTransformer implements IClassTransformer, Opcodes {

    static final String CLS = "WayofTime.alchemicalWizardry.common.rituals.RitualEffectBiomeChanger";
    private static final String BIOME = "net/minecraft/world/biome/BiomeGenBase";
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
                int biome = -1;
                String id = null;
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    // перебираемый биом: aload b; getfield temperature
                    if (i.getOpcode() == GETFIELD && BIOME.equals(((FieldInsnNode) i).owner) && i.getPrevious() != null && i.getPrevious().getOpcode() == ALOAD) {
                        String f = ((FieldInsnNode) i).name;
                        if ("field_76750_F".equals(f) || "temperature".equals(f)) {
                            biome = ((VarInsnNode) i.getPrevious()).var;
                            id = f.startsWith("field_") ? "field_76756_M" : "biomeID";
                        }
                    }
                    // совпали обе разницы: fcmpg; ifge; iload номер; istore выбранный - номер заменяем на ID
                    AbstractInsnNode jump = back(i), cmp = jump == null ? null : back(jump), store = ahead(i);
                    if (biome >= 0 && i.getOpcode() == ILOAD && store != null && store.getOpcode() == ISTORE
                        && jump instanceof JumpInsnNode && jump.getOpcode() == IFGE && cmp != null && cmp.getOpcode() == FCMPG) {
                        AbstractInsnNode load = new VarInsnNode(ALOAD, biome);
                        m.instructions.insertBefore(i, load);
                        m.instructions.set(i, new FieldInsnNode(GETFIELD, BIOME, id, "I"));
                        i = load.getNext();
                        n++;
                    }
                }
            }
            if (n != 1) {
                LOG.warn("Ритуал Гайи: место выбора биома не найдено однозначно ({}), класс без изменений", n);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            patched = true;
            LOG.info("Ритуал Гайи: в чанк пишется настоящий ID биома");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Ритуал Гайи: не удалось исправить, класс без изменений", t);
            return bytes;
        }
    }

    /** Ближайшая настоящая инструкция перед i (метки, номера строк и кадры пропускаются). */
    private static AbstractInsnNode back(AbstractInsnNode i) {
        for (i = i.getPrevious(); i != null && i.getOpcode() < 0; i = i.getPrevious()) {}
        return i;
    }

    /** Ближайшая настоящая инструкция после i. */
    private static AbstractInsnNode ahead(AbstractInsnNode i) {
        for (i = i.getNext(); i != null && i.getOpcode() < 0; i = i.getNext()) {}
        return i;
    }
}
