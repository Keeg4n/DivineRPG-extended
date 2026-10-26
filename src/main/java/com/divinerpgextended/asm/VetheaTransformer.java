package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Ветея у Divine, через {@link VetheaHooks}:
 * <ul>
 * <li>алтарь наполнения: результат и трата глыб считает аддон ({@link com.divinerpgextended.vethea.Infusion}) - глыб
 * можно положить больше нужного, есть рецепты крыльев и лунного оружия;</li>
 * <li>Лхейву бьёт кто угодно (у Divine - только с Поясом хейвы в инвентаре): кем её бить, решают слабости мобов;</li>
 * <li>Шахтёрский амулет работает из слота аксессуаров, а не из инвентаря.</li>
 * </ul>
 */
public class VetheaTransformer implements IClassTransformer, Opcodes {

    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    private static final String HOOKS = "com/divinerpgextended/asm/VetheaHooks";
    private static final String TABLE = "net.divinerpg.blocks.vethea.container.tileentity.TileEntityInfusionTable";
    private static final String SLOT = "net.divinerpg.blocks.vethea.container.SlotInfusion";
    private static final String LHEIVA = "net.divinerpg.entities.vethea.EntityLheiva";
    private static final String ARMOR_TICK = "net.divinerpg.utils.events.EventArmorTick";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || transformedName == null) return bytes;
        if (!transformedName.equals(TABLE) && !transformedName.equals(SLOT) && !transformedName.equals(LHEIVA) && !transformedName.equals(ARMOR_TICK))
            return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            for (MethodNode m : node.methods) {
                if (transformedName.equals(TABLE) && ("func_145845_h".equals(m.name) || "updateEntity".equals(m.name)) && "()V".equals(m.desc)) {
                    // inventory считает аддон, затем TileEntity.updateEntity
                    InsnList l = new InsnList();
                    l.add(new VarInsnNode(ALOAD, 0));
                    l.add(new FieldInsnNode(GETFIELD, node.name, "inventory", "[Lnet/minecraft/item/ItemStack;"));
                    l.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "infusionUpdate", "([Lnet/minecraft/item/ItemStack;)V", false));
                    l.add(new VarInsnNode(ALOAD, 0));
                    l.add(new MethodInsnNode(INVOKESPECIAL, "net/minecraft/tileentity/TileEntity", m.name, "()V", false));
                    l.add(new InsnNode(RETURN));
                    replace(m, l);
                    n++;
                } else if (transformedName.equals(SLOT) && ("func_75208_c".equals(m.name) || "onCrafting".equals(m.name))
                    && "(Lnet/minecraft/item/ItemStack;)V".equals(m.desc)) {
                    InsnList l = new InsnList();
                    l.add(new VarInsnNode(ALOAD, 0));
                    l.add(new FieldInsnNode(GETFIELD, "net/minecraft/inventory/Slot", "func_75208_c".equals(m.name) ? "field_75224_c" : "inventory",
                        "Lnet/minecraft/inventory/IInventory;"));
                    l.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "infusionTake", "(Lnet/minecraft/inventory/IInventory;)V", false));
                    l.add(new InsnNode(RETURN));
                    replace(m, l);
                    n++;
                } else if (transformedName.equals(LHEIVA) || transformedName.equals(ARMOR_TICK)) {
                    // inventory.hasItem(VetheaItems.heivaBelt / minersAmulet) -> VetheaHooks.has(inventory, item)
                    for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                        if (i.getOpcode() != INVOKEVIRTUAL) continue;
                        MethodInsnNode c = (MethodInsnNode) i;
                        if (!"net/minecraft/entity/player/InventoryPlayer".equals(c.owner) || !"(Lnet/minecraft/item/Item;)Z".equals(c.desc)) continue;
                        AbstractInsnNode prev = c.getPrevious();
                        if (!(prev instanceof FieldInsnNode)) continue;
                        String f = ((FieldInsnNode) prev).name;
                        if (!"heivaBelt".equals(f) && !"minersAmulet".equals(f)) continue;
                        c.setOpcode(INVOKESTATIC);
                        c.owner = HOOKS;
                        c.name = "has";
                        c.desc = "(Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/item/Item;)Z";
                        c.itf = false;
                        n++;
                    }
                }
            }
            if (n == 0) return bytes;
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Ветея: {} - {} правок", transformedName, n);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Ветея: не удалось исправить " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }

    private static void replace(MethodNode m, InsnList body) {
        m.instructions.clear();
        m.tryCatchBlocks.clear();
        if (m.localVariables != null) m.localVariables.clear();
        m.instructions.add(body);
    }
}
