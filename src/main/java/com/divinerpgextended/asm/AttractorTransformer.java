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
 * Аттрактор Divine: снаряд при попадании толкает цель к бросившему (Entity.addVelocity). Вызов идёт через
 * {@link com.divinerpgextended.world.twilight.SeaWeapons#attract}: игрока - только где можно PvP.
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
                LOG.warn("Аттрактор: в {} не нашлось толчка - тянет как в Divine", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Аттрактор: игроков тянет только там, где можно PvP");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Аттрактор: не удалось подключиться к " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }
}
