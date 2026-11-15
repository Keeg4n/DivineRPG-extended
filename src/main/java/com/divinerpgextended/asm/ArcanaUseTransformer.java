package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Arcana spending in Divine code goes through the addon rules ({@code com.divinerpgextended.magic.ArcanaRules}): every call to
 * ArcanaHelper.useBar(F) and forceRegen(F) in Divine classes (except ArcanaHelper itself) is replaced with a rule call with
 * the name of the calling class - by it the rule knows which weapon or armor it is (price, cooldown, potion).
 */
public class ArcanaUseTransformer implements IClassTransformer, Opcodes {

    private static final String HELPER = "net/divinerpg/utils/events/ArcanaHelper";
    private static final String RULES = "com/divinerpgextended/magic/ArcanaRules";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || transformedName == null || !transformedName.startsWith("net.divinerpg.")
            || transformedName.equals("net.divinerpg.utils.events.ArcanaHelper")) return bytes;
        // cheaply filter out classes without arcana calls
        if (!contains(bytes, "useBar") && !contains(bytes, "forceRegen")) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                    if (in.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode mi = (MethodInsnNode) in;
                    if (!HELPER.equals(mi.owner)) continue;
                    String to;
                    if ("useBar".equals(mi.name) && "(F)Z".equals(mi.desc)) to = "(L" + HELPER + ";FLjava/lang/String;)Z";
                    else if ("forceRegen".equals(mi.name) && "(F)V".equals(mi.desc)) to = "(L" + HELPER + ";FLjava/lang/String;)V";
                    else continue;
                    m.instructions.insertBefore(mi, new LdcInsnNode(transformedName));
                    MethodInsnNode rep = new MethodInsnNode(INVOKESTATIC, RULES, mi.name, to, false);
                    m.instructions.set(mi, rep);
                    in = rep;
                    patched++;
                }
            }
            if (patched == 0) return bytes;
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Arcana: spending in {} goes through the addon rules ({} calls)", transformedName, patched);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Arcana: could not redirect spending in " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }

    private static boolean contains(byte[] b, String s) {
        byte[] p = s.getBytes();
        outer: for (int i = 0; i + p.length <= b.length; i++) {
            for (int k = 0; k < p.length; k++) if (b[i + k] != p[k]) continue outer;
            return true;
        }
        return false;
    }
}
