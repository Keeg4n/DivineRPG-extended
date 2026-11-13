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
 * Тик надетой брони (Forge зовёт Item.onArmorTick из InventoryPlayer.decrementAnimations) идёт через
 * {@link com.divinerpgextended.vethea.Nightmare#armorTick}: во сне до прохождения чужая броня не тикает.
 * <p>
 * Класс Minecraft: на этом шаге имена в нём могут быть ещё обфусцированными. onArmorTick добавлен Forge и зовётся так
 * всегда, а описание хука собирается из описания самого вызова (владелец + аргументы) - после переименования типы
 * совпадут с настоящими.
 */
public class ArmorTickTransformer implements IClassTransformer, Opcodes {

    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    private static final String TARGET = "net.minecraft.entity.player.InventoryPlayer";
    /** Поправка встала: проверяет самопроверка. */
    public static boolean patched;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                    if (in.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) in;
                    if (!"onArmorTick".equals(c.name)) continue;
                    MethodInsnNode rep = new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/vethea/Nightmare", "armorTick", "(L" + c.owner + ";" + c.desc.substring(1), false);
                    m.instructions.set(c, rep);
                    in = rep;
                    n++;
                }
            }
            if (n != 1) {
                LOG.error("Тик брони во сне: вызовов onArmorTick {} вместо одного, класс без изменений", n);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            patched = true;
            LOG.info("Тик брони во сне: чужая броня в Ветее не тикает до прохождения");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Тик брони во сне: не удалось изменить InventoryPlayer, класс без изменений", t);
            return bytes;
        }
    }
}
