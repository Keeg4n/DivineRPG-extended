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
 * Айрако Divine при сохранении пишут ID братьев по стае, не проверяя, что ссылка на брата есть: дракон без связанных
 * братьев (сразу после загрузки чанка, одиночный, вызванный командой) роняет сохранение чанка - вылет или потеря
 * чанка; в сборке с Morph падает и убийство (мод сохраняет моба, чтобы превратить в него игрока). Вызовы
 * getPersistentID у брата в классах Айрако перенаправляем в {@link AyeracoHooks#id}: нет брата - пустой ID.
 */
public class AyeracoTransformer implements IClassTransformer, Opcodes {

    private static final String PREFIX = "net.divinerpg.entities.vanilla.EntityAyeraco";
    private static final String OWNER = "net/divinerpg/entities/vanilla/EntityAyeraco";
    private static final String HOOKS = "com/divinerpgextended/asm/AyeracoHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    /**
     * Базовый класс Айрако: полёт (updateAITasks) и прыжок-телепорт вверх (teleportRandomUp) у орды гнездовья ведёт аддон
     * ({@link AyeracoHooks#steer}, {@link AyeracoHooks#blink}): в начало методов - "если это орда гнездовья - своё и выйти".
     */
    private static byte[] base(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int patched = 0;
        for (MethodNode m : node.methods) {
            String hook = null;
            if (("func_70619_bc".equals(m.name) || "updateAITasks".equals(m.name)) && "()V".equals(m.desc)) hook = "steer";
            else if ("teleportRandomUp".equals(m.name) && "(Lnet/minecraft/world/World;)V".equals(m.desc)) hook = "blink";
            if (hook == null) continue;
            org.objectweb.asm.tree.InsnList in = new org.objectweb.asm.tree.InsnList();
            org.objectweb.asm.tree.LabelNode go = new org.objectweb.asm.tree.LabelNode();
            in.add(new org.objectweb.asm.tree.VarInsnNode(ALOAD, 0));
            in.add(new MethodInsnNode(INVOKESTATIC, HOOKS, hook, "(Lnet/minecraft/entity/EntityLiving;)Z", false));
            in.add(new org.objectweb.asm.tree.JumpInsnNode(IFEQ, go));
            in.add(new org.objectweb.asm.tree.InsnNode(RETURN));
            in.add(go);
            // кадр стека у метки перехода (классы Java 7+ без него не проходят проверку): локальные - как на входе
            if ((node.version & 0xFFFF) >= 50) in.add(new org.objectweb.asm.tree.FrameNode(F_SAME, 0, null, 0, null));
            m.instructions.insert(in);
            patched++;
        }
        if (patched == 0) return bytes;
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(w);
        LOG.info("Айрако: полёт и телепорт орды гнездовья ведёт аддон ({} методов)", patched);
        return w.toByteArray();
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || transformedName == null || !transformedName.startsWith(PREFIX)) return bytes;
        if (transformedName.equals(PREFIX)) {
            try {
                return base(bytes);
            } catch (Throwable t) {
                LOG.error("Айрако: полёт орды не подключён, класс без изменений", t);
                return bytes;
            }
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (!c.owner.startsWith(OWNER) || !"getPersistentID".equals(c.name) || !"()Ljava/util/UUID;".equals(c.desc)) continue;
                    c.setOpcode(INVOKESTATIC);
                    c.owner = HOOKS;
                    c.name = "id";
                    c.desc = "(Lnet/minecraft/entity/Entity;)Ljava/util/UUID;";
                    c.itf = false;
                    patched++;
                }
            }
            if (patched == 0) return bytes;
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Айрако: {} - сохранение без братьев не падает ({} вызовов)", transformedName, patched);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Айрако: не удалось исправить " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }
}
