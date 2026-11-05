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
 * Ломка игроком берёт настоящую прочность защищённых блоков ({@link com.divinerpgextended.perk.MiningGuard}): в
 * ForgeHooks.blockStrength чтение Block.getBlockHardness заменяется вызовом MiningGuard.real. Это единственное чтение
 * прочности в методе; других вызовов с такими аргументами там нет, поэтому имя не сверяется (оно разное в среде и в игре).
 */
public class MiningGuardTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.minecraftforge.common.ForgeHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    /** Поправка встала: проверяет самопроверка. */
    public static boolean patched;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int count = 0;
            for (MethodNode m : node.methods) {
                if (!"blockStrength".equals(m.name)) continue;
                for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                    if (in.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) in;
                    if (!c.desc.matches("\\(L[^;]+;III\\)F")) continue;
                    MethodInsnNode rep = new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/perk/MiningGuard", "real",
                        "(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;III)F", false);
                    m.instructions.set(c, rep);
                    in = rep;
                    count++;
                }
            }
            if (count != 1) {
                LOG.error("Защита блоков: в blockStrength найдено чтений прочности {} вместо одного, класс без изменений", count);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            patched = true;
            LOG.info("Защита блоков: ломка игроком берёт настоящую прочность");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Защита блоков: не удалось изменить ForgeHooks, класс без изменений", t);
            return bytes;
        }
    }
}
