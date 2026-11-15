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
 * Divine Ayeraco on save write the IDs of their pack siblings without checking that a reference to a sibling exists: a dragon without linked
 * siblings (right after a chunk load, a lone one, summoned by a command) crashes the chunk save - a crash or chunk
 * loss; in a modpack with Morph the kill crashes too (the mod saves the mob to turn the player into it). Calls to
 * getPersistentID of the sibling in Ayeraco classes are redirected to {@link AyeracoHooks#id}: no sibling - empty ID.
 */
public class AyeracoTransformer implements IClassTransformer, Opcodes {

    private static final String PREFIX = "net.divinerpg.entities.vanilla.EntityAyeraco";
    private static final String OWNER = "net/divinerpg/entities/vanilla/EntityAyeraco";
    private static final String HOOKS = "com/divinerpgextended/asm/AyeracoHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    /**
     * Base Ayeraco class: flight (updateAITasks) and the jump-teleport upward (teleportRandomUp) of the aerie horde are driven by the addon
     * ({@link AyeracoHooks#steer}, {@link AyeracoHooks#blink}): at the start of the methods - "if this is an aerie horde - do our own and exit".
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
            // stack frame at the jump label (Java 7+ classes do not pass verification without it): locals - as on entry
            if ((node.version & 0xFFFF) >= 50) in.add(new org.objectweb.asm.tree.FrameNode(F_SAME, 0, null, 0, null));
            m.instructions.insert(in);
            patched++;
        }
        if (patched == 0) return bytes;
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(w);
        LOG.info("Ayeraco: flight and teleport of the aerie horde are handled by the addon ({} methods)", patched);
        return w.toByteArray();
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || transformedName == null || !transformedName.startsWith(PREFIX)) return bytes;
        if (transformedName.equals(PREFIX)) {
            try {
                return base(bytes);
            } catch (Throwable t) {
                LOG.error("Ayeraco: horde flight not hooked, class unchanged", t);
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
            LOG.info("Ayeraco: {} - saving without siblings does not crash ({} calls)", transformedName, patched);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Ayeraco: could not fix " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }
}
