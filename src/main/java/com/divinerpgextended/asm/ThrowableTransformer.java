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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Собранные стопки метательного оружия ({@link com.divinerpgextended.perk.ThrowStack}): броски Divine (ItemThrowable - сюрикены
 * и слайсеры, ItemVileStorm) списывают штуку как stack.stackSize-- - заменяем на {@link ThrowHooks#consume}, а пределом
 * стопки становится {@link ThrowHooks#limit} (собранная стопка не сливается с другими).
 */
public class ThrowableTransformer implements IClassTransformer, Opcodes {

    private static final Set<String> TARGETS = new HashSet<String>(Arrays.asList("net.divinerpg.items.base.ItemThrowable",
        "net.divinerpg.items.vanilla.ItemVileStorm"));
    private static final Set<String> USE = new HashSet<String>(Arrays.asList("func_77659_a", "onItemRightClick"));
    private static final Set<String> SIZE = new HashSet<String>(Arrays.asList("field_77994_a", "stackSize"));
    private static final String STACK = "net/minecraft/item/ItemStack", HOOKS = "com/divinerpgextended/asm/ThrowHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGETS.contains(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            boolean limit = false;
            for (MethodNode m : node.methods) {
                if ("getItemStackLimit".equals(m.name) && "(Lnet/minecraft/item/ItemStack;)I".equals(m.desc)) limit = true;
                if (!USE.contains(m.name)) continue;
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    // dup; getfield stackSize; iconst_1; isub; putfield stackSize -> invokestatic consume
                    if (i.getOpcode() != PUTFIELD) continue;
                    FieldInsnNode put = (FieldInsnNode) i;
                    if (!STACK.equals(put.owner) || !SIZE.contains(put.name)) continue;
                    AbstractInsnNode sub = put.getPrevious(), one = sub == null ? null : sub.getPrevious(), get = one == null ? null : one.getPrevious(),
                        dup = get == null ? null : get.getPrevious();
                    if (sub == null || sub.getOpcode() != ISUB || one == null || one.getOpcode() != ICONST_1 || get == null || get.getOpcode() != GETFIELD
                        || dup == null || dup.getOpcode() != DUP) continue;
                    MethodInsnNode call = new MethodInsnNode(INVOKESTATIC, HOOKS, "consume", "(L" + STACK + ";)V", false);
                    m.instructions.set(put, call);
                    m.instructions.remove(sub);
                    m.instructions.remove(one);
                    m.instructions.remove(get);
                    m.instructions.remove(dup);
                    i = call;
                    n++;
                }
            }
            if (n == 0) {
                LOG.warn("Метательное: в {} не нашлось списания броска - собранные стопки не работают", transformedName);
                return bytes;
            }
            if (!limit) {
                MethodNode m = new MethodNode(ACC_PUBLIC, "getItemStackLimit", "(L" + STACK + ";)I", null, null);
                m.instructions.add(new VarInsnNode(ALOAD, 0));
                m.instructions.add(new VarInsnNode(ALOAD, 1));
                m.instructions.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "limit", "(Lnet/minecraft/item/Item;L" + STACK + ";)I", false));
                m.instructions.add(new InsnNode(IRETURN));
                m.maxStack = 2;
                m.maxLocals = 2;
                node.methods.add(m);
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Метательное: {} бросает из собранной стопки ({} мест)", transformedName, n);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Метательное: не удалось подключиться к " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }
}
