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
 * The Nightmare Bed and the Vethea portal in Divine save the inventory to NBT on transition, clear it and load a second one.
 * In 1.4.2.3 this is turned off by the vetheaInventory setting, and in 1.4.1.5 (server modpacks) there is no setting and the swap always
 * happens. Calls that save, clear and load the inventory in these two classes are redirected to
 * {@link VetheaSwapHooks}: while the Vethea nightmare is on, there is no swap in any Divine version.
 */
public class VetheaSwapTransformer implements IClassTransformer, Opcodes {

    private static final String BED = "net.divinerpg.blocks.vethea.BlockNightmareBed";
    private static final String PORTAL = "net.divinerpg.blocks.vethea.BlockVetheaPortal";
    private static final String INV = "net/minecraft/entity/player/InventoryPlayer";
    private static final String HOOKS = "com/divinerpgextended/asm/VetheaSwapHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !(BED.equals(transformedName) || PORTAL.equals(transformedName))) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (!INV.equals(c.owner)) continue;
                    String hook = hook(c.name, c.desc);
                    if (hook == null) continue;
                    c.setOpcode(INVOKESTATIC);
                    c.owner = HOOKS;
                    c.name = hook;
                    c.desc = "(L" + INV + ";" + c.desc.substring(1);
                    c.itf = false;
                    patched++;
                }
            }
            if (patched == 0) {
                LOG.warn("Vethea inventory: no inventory swap in {} - class unchanged", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Vethea inventory: {} - inventory swap is controlled by the addon ({} calls)", transformedName, patched);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Vethea inventory: could not fix " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }

    private static String hook(String name, String desc) {
        if (("func_70442_a".equals(name) || "writeToNBT".equals(name))
            && "(Lnet/minecraft/nbt/NBTTagList;)Lnet/minecraft/nbt/NBTTagList;".equals(desc)) return "write";
        if (("func_70443_b".equals(name) || "readFromNBT".equals(name)) && "(Lnet/minecraft/nbt/NBTTagList;)V".equals(desc))
            return "read";
        if (("func_146027_a".equals(name) || "clearInventory".equals(name)) && "(Lnet/minecraft/item/Item;I)I".equals(desc))
            return "clear";
        return null;
    }
}
