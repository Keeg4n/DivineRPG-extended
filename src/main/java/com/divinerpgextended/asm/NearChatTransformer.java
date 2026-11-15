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
 * The Divine Ayeraco summon writes about every beam and the appearance of bosses to the whole server. Util.sendMessageToAll calls in
 * TileEntityAyeracoSpawn go through {@link NearChatHooks#near}: only players near the summon spot receive the message.
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
                    m.instructions.insertBefore(c, new VarInsnNode(ALOAD, 0)); // the tile - from where to count "near"
                    c.owner = "com/divinerpgextended/asm/NearChatHooks";
                    c.name = "near";
                    c.desc = "(" + MSG + "Ljava/lang/Object;)V";
                    calls++;
                }
            }
            if (calls == 0) {
                LOG.warn("Ayeraco messages: calls not found - class unchanged");
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Ayeraco messages: only to nearby players ({} calls)", calls);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Ayeraco messages: could not fix, class unchanged", t);
            return bytes;
        }
    }
}
