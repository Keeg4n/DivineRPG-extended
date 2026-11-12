package com.divinerpgextended.asm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
 * Молнии Ударной серенады и Меча бури Divine - безопасные ({@link com.divinerpgextended.entity.EntitySafeLightning}): new
 * EntityLightningBolt в их коде заменяется на свою молнию с тем же конструктором.
 */
public class LightningTransformer implements IClassTransformer, Opcodes {

    private static final Set<String> TARGETS = new HashSet<String>(Arrays.asList("net.divinerpg.items.vanilla.ItemSerenadeStriker",
        "net.divinerpg.items.arcana.ItemStormSword"));
    private static final String BOLT = "net/minecraft/entity/effect/EntityLightningBolt", SAFE = "com/divinerpgextended/entity/EntitySafeLightning";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGETS.contains(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() == NEW && BOLT.equals(((TypeInsnNode) i).desc)) {
                        ((TypeInsnNode) i).desc = SAFE;
                        n++;
                    } else if (i.getOpcode() == INVOKESPECIAL && BOLT.equals(((MethodInsnNode) i).owner) && "<init>".equals(((MethodInsnNode) i).name)) {
                        ((MethodInsnNode) i).owner = SAFE;
                    }
                }
            }
            if (n == 0) {
                LOG.warn("Молнии оружия: в {} не нашлось молний - бьют как в Divine", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Молнии оружия: {} бьёт безопасной молнией ({} мест)", transformedName, n);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Молнии оружия: не удалось подключиться к " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }
}
