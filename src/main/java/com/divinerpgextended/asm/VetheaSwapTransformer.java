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
 * Кровать кошмаров и портал Ветеи в Divine при переходе сохраняют инвентарь в NBT, очищают его и загружают второй.
 * В 1.4.2.3 это выключается настройкой vetheaInventory, а в 1.4.1.5 (сборки серверов) настройки нет и обмен идёт
 * всегда. Вызовы сохранения, очистки и загрузки инвентаря в этих двух классах перенаправляем в
 * {@link VetheaSwapHooks}: пока кошмар Ветеи включён, обмена нет ни в одной версии Divine.
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
                LOG.warn("Инвентарь Ветеи: в {} нет обмена инвентаря - класс без изменений", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Инвентарь Ветеи: {} - обмен инвентаря под контролем аддона ({} вызовов)", transformedName, patched);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Инвентарь Ветеи: не удалось исправить " + transformedName + ", класс без изменений", t);
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
