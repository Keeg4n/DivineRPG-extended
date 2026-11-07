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
 * Броня Divine и Странника понимает встроенные очки откровения ({@link com.divinerpgextended.accessory.Goggles}). Таум и другие моды
 * узнают шлем по интерфейсам вещи ({@code instanceof IRevealer}, {@code IGoggles}), поэтому классам брони добавляются эти
 * интерфейсы и их методы. Только если Таум есть: иначе класс с чужим интерфейсом не загрузится. Весь класс один на все части,
 * а ответ зависит от того, что встроено в саму вещь.
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
            LOG.info("Очки откровения: {} понимает встроенные очки", transformedName);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Очки откровения: не удалось изменить " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }

    /** Метод интерфейса: первый аргумент (вещь) - в статический метод {@link com.divinerpgextended.accessory.Goggles}. */
    private static MethodNode forward(String name, String desc, String hook, String hookDesc, int ret) {
        MethodNode m = new MethodNode(ACC_PUBLIC, name, desc, null, null);
        m.instructions.add(new VarInsnNode(ALOAD, 1));
        m.instructions.add(new MethodInsnNode(INVOKESTATIC, HOOK, hook, hookDesc, false));
        m.instructions.add(new InsnNode(ret));
        return m;
    }
}
