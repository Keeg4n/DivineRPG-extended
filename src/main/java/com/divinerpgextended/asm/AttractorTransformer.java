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
 * Divine attractor: on hit the projectile pushes the target toward the thrower (Entity.addVelocity). The call goes through
 * {@link com.divinerpgextended.world.twilight.SeaWeapons#attract}: a player - only where PvP is allowed.
 */
public class AttractorTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.divinerpg.entities.arcana.projectile.EntityAttractor";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            for (MethodNode m : node.methods) {
                if (!"(Lnet/minecraft/util/MovingObjectPosition;)V".equals(m.desc)) continue;
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (!"(DDD)V".equals(c.desc) || !("func_70024_g".equals(c.name) || "addVelocity".equals(c.name))) continue;
                    m.instructions.insertBefore(c, new VarInsnNode(ALOAD, 0));
                    MethodInsnNode hook = new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/world/twilight/SeaWeapons", "attract",
                        "(Lnet/minecraft/entity/Entity;DDDLnet/minecraft/entity/projectile/EntityThrowable;)V", false);
                    m.instructions.set(c, hook);
                    i = hook;
                    n++;
                }
            }
            if (n == 0) {
                LOG.warn("Attractor: no push found in {} - pulls as in Divine", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Attractor: players are pulled only where PvP is allowed");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Attractor: could not hook into " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }
}
