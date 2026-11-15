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
 * The tick of worn armor (Forge calls Item.onArmorTick from InventoryPlayer.decrementAnimations) goes through
 * {@link com.divinerpgextended.vethea.Nightmare#armorTick}: in the dream before completion another's armor does not tick.
 * <p>
 * A Minecraft class: at this step the names in it may still be obfuscated. onArmorTick is added by Forge and is always called that,
 * and the hook descriptor is built from the descriptor of the call itself (owner + arguments) - after renaming the types
 * will match the real ones.
 */
public class ArmorTickTransformer implements IClassTransformer, Opcodes {

    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    private static final String TARGET = "net.minecraft.entity.player.InventoryPlayer";
    /** The patch is in place: checked by the selftest. */
    public static boolean patched;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                    if (in.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) in;
                    if (!"onArmorTick".equals(c.name)) continue;
                    MethodInsnNode rep = new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/vethea/Nightmare", "armorTick", "(L" + c.owner + ";" + c.desc.substring(1), false);
                    m.instructions.set(c, rep);
                    in = rep;
                    n++;
                }
            }
            if (n != 1) {
                LOG.error("Armor tick in sleep: {} onArmorTick calls instead of one, class unchanged", n);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            patched = true;
            LOG.info("Armor tick in sleep: foreign armor in Vethea does not tick until progression");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Armor tick in sleep: could not modify InventoryPlayer, class unchanged", t);
            return bytes;
        }
    }
}
