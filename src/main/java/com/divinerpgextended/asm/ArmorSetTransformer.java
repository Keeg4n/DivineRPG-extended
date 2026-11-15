package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * In Divine EventArmorFullSet keeps the player's armor in shared fields of a single object, and three damage handlers write to them
 * (and one of them also on the client). In singleplayer the client and server overwrite each other's fields, in PvP the attacker's bonus
 * is computed from the victim's armor: set properties fire not always and not on the right player. We make the bodies of these three
 * handlers empty, and the properties themselves (with the same numbers) are computed by {@link com.divinerpgextended.perk.ArmorSets}.
 * The mark of a successful patch is the dre_patched field in the class. On any error the class stays as it was.
 */
public class ArmorSetTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.divinerpg.utils.events.EventArmorFullSet";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            for (MethodNode m : node.methods) {
                boolean hurt = ("onPlayerHurtEvent".equals(m.name) || "onLivingHurtEvent".equals(m.name))
                    && "(Lnet/minecraftforge/event/entity/living/LivingHurtEvent;)V".equals(m.desc);
                boolean attack = "onPlayerAttackEvent".equals(m.name)
                    && "(Lnet/minecraftforge/event/entity/living/LivingAttackEvent;)V".equals(m.desc);
                if (!hurt && !attack) continue;
                m.instructions.clear();
                m.instructions.add(new InsnNode(RETURN));
                m.tryCatchBlocks.clear();
                if (m.localVariables != null) m.localVariables.clear();
                m.maxStack = 0;
                patched++;
            }
            if (patched != 3) {
                LOG.warn("Divine armor sets: found {} of 3 handlers - leaving class unchanged", patched);
                return bytes;
            }
            node.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "dre_patched", "Z", null, Integer.valueOf(1)));
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Divine armor sets: set properties are computed by the addon (EventArmorFullSet)");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Divine armor sets: could not fix, class unchanged", t);
            return bytes;
        }
    }
}
