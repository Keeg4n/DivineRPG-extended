package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Divine and Wanderer armor understands built-in Goggles of Revealing ({@link com.divinerpgextended.accessory.Goggles}). Thaum and other mods
 * recognize the helmet by item interfaces ({@code instanceof IRevealer}, {@code IGoggles}), so these
 * interfaces and their methods are added to the armor classes. Only if Thaum is present: otherwise a class with a foreign interface will not load. One class for all parts,
 * and the answer depends on what is built into the item itself.
 */
public class GogglesTransformer implements IClassTransformer, Opcodes {

    private static final String DIVINE = "net.divinerpg.items.base.ItemDivineArmor", WANDERER = "com.divinerpgextended.item.ItemWandererArmor";
    private static final String HOOK = "com/divinerpgextended/accessory/Goggles", STACK = "Lnet/minecraft/item/ItemStack;";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !(DIVINE.equals(transformedName) || WANDERER.equals(transformedName))) return bytes;
        if (Launch.classLoader.getResource("thaumcraft/api/IGoggles.class") == null) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            node.interfaces.add("thaumcraft/api/nodes/IRevealer");
            node.interfaces.add("thaumcraft/api/IGoggles");
            node.interfaces.add("thaumcraft/api/IVisDiscountGear");
            String living = "(" + STACK + "Lnet/minecraft/entity/EntityLivingBase;)Z";
            node.methods.add(forward("showNodes", living, "reveal", "(" + STACK + ")Z", IRETURN));
            node.methods.add(forward("showIngamePopups", living, "reveal", "(" + STACK + ")Z", IRETURN));
            node.methods.add(forward("getVisDiscount", "(" + STACK + "Lnet/minecraft/entity/player/EntityPlayer;Lthaumcraft/api/aspects/Aspect;)I", "discount", "(" + STACK + ")I", IRETURN));
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Goggles of Revelation: {} understands built-in goggles", transformedName);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Goggles of Revelation: could not modify " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }

    /** Interface method: the first argument (the item) goes to a static method of {@link com.divinerpgextended.accessory.Goggles}. */
    private static MethodNode forward(String name, String desc, String hook, String hookDesc, int ret) {
        MethodNode m = new MethodNode(ACC_PUBLIC, name, desc, null, null);
        m.instructions.add(new VarInsnNode(ALOAD, 1));
        m.instructions.add(new MethodInsnNode(INVOKESTATIC, HOOK, hook, hookDesc, false));
        m.instructions.add(new InsnNode(ret));
        return m;
    }
}
