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
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Порталы Divine (сумеречные миры) на каждый вход создают DivineTeleporter, который ищет парный портал медленным
 * перебором. Подменяем создаваемый класс на {@link com.divinerpgextended.portal.FastTeleporter} (тот же конструктор).
 */
public class PortalTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.divinerpg.blocks.base.portal.BlockModPortal";
    private static final String FROM = "net/divinerpg/dimensions/base/DivineTeleporter";
    private static final String TO = "com/divinerpgextended/portal/FastTeleporter";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int news = 0, inits = 0;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() == NEW && FROM.equals(((TypeInsnNode) i).desc)) {
                        ((TypeInsnNode) i).desc = TO;
                        news++;
                    } else if (i.getOpcode() == INVOKESPECIAL && FROM.equals(((MethodInsnNode) i).owner)
                        && "<init>".equals(((MethodInsnNode) i).name)) {
                        ((MethodInsnNode) i).owner = TO;
                        inits++;
                    }
                }
            }
            if (news == 0 || news != inits) {
                LOG.warn("Порталы Divine: создание телепорта не найдено ({}/{}) - класс без изменений", news, inits);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Порталы Divine: быстрый поиск парного портала ({} мест)", news);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Порталы Divine: не удалось исправить, класс без изменений", t);
            return bytes;
        }
    }
}
