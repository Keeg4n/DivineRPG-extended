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
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Призыв Айрако у Divine пишет о каждом луче и о появлении боссов всему серверу. Вызовы Util.sendMessageToAll в
 * TileEntityAyeracoSpawn идут через {@link NearChatHooks#near}: сообщение получают только игроки рядом с местом призыва.
 */
public class NearChatTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.divinerpg.blocks.vanilla.container.tileentity.TileEntityAyeracoSpawn";
    private static final String UTIL = "net/divinerpg/utils/Util";
    private static final String MSG = "Lnet/minecraft/util/ChatComponentTranslation;";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int calls = 0;
            for (MethodNode m : node.methods) {
                if ((m.access & ACC_STATIC) != 0) continue;
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != INVOKESTATIC) continue;
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (!UTIL.equals(c.owner) || !"sendMessageToAll".equals(c.name) || !("(" + MSG + ")V").equals(c.desc)) continue;
                    m.instructions.insertBefore(c, new VarInsnNode(ALOAD, 0)); // тайл - откуда считать "рядом"
                    c.owner = "com/divinerpgextended/asm/NearChatHooks";
                    c.name = "near";
                    c.desc = "(" + MSG + "Ljava/lang/Object;)V";
                    calls++;
                }
            }
            if (calls == 0) {
                LOG.warn("Сообщения Айрако: вызовы не найдены - класс без изменений");
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Сообщения Айрако: только игрокам рядом ({} вызовов)", calls);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Сообщения Айрако: не удалось исправить, класс без изменений", t);
            return bytes;
        }
    }
}
