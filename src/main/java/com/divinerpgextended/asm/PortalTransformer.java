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
 * Divine portals (twilight worlds) create a DivineTeleporter on every entry, which looks for the paired portal by slow
 * brute force. We swap the created class for {@link com.divinerpgextended.portal.FastTeleporter} (same constructor).
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
                LOG.warn("Divine portals: teleport creation not found ({}/{}) - class unchanged", news, inits);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Divine portals: fast search for the paired portal ({} places)", news);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Divine portals: could not fix, class unchanged", t);
            return bytes;
        }
    }
}
