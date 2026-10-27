package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Вода Эдема золотая: у Divine её цвет - почти чёрный множитель на синей текстуре, а умножением синей текстуры золото
 * не получить. В Эдеме вода берёт свою (золотую) текстуру: в начало BlockLiquid.getIcon и BlockLiquid.getLiquidIcon
 * (только на клиенте) - вызов {@link WaterHooks}; null - как было.
 * <p>
 * И течение: ванильная вода по материалу считала своей любую жидкость с материалом воды, и источник воды мечтаний держал
 * её потоки. В начало BlockLiquid.func_149804_e / getEffectiveFlowDecay: жидкость другого мода ({@link FluidHooks#foreign})
 * - не своя (-1).
 */
public class WaterTransformer implements IClassTransformer, Opcodes {

    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    private static final String HOOKS = "com/divinerpgextended/asm/WaterHooks", ICON = "Lnet/minecraft/util/IIcon;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"net.minecraft.block.BlockLiquid".equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            for (MethodNode m : node.methods) {
                InsnList l = new InsnList();
                // имена: игра (класс ещё не переименован: alw, a/e, rf, ahb, ahl), SRG и разработка
                if (("a".equals(m.name) && "(II)Lrf;".equals(m.desc)) || (("func_149691_a".equals(m.name) || "getIcon".equals(m.name)) && ("(II)" + ICON).equals(m.desc))) {
                    l.add(new VarInsnNode(ALOAD, 0));
                    l.add(new VarInsnNode(ILOAD, 1));
                    l.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "icon", "(Lnet/minecraft/block/Block;I)" + ICON, false));
                } else if (("e".equals(m.name) && "(Ljava/lang/String;)Lrf;".equals(m.desc))
                    || (("func_149803_e".equals(m.name) || "getLiquidIcon".equals(m.name)) && ("(Ljava/lang/String;)" + ICON).equals(m.desc))) {
                    l.add(new VarInsnNode(ALOAD, 0));
                    l.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "liquidIcon", "(Ljava/lang/String;)" + ICON, false));
                } else if (("e".equals(m.name) && "(Lahb;III)I".equals(m.desc)) || ("func_149804_e".equals(m.name) && "(Lnet/minecraft/world/World;III)I".equals(m.desc))
                    || ("getEffectiveFlowDecay".equals(m.name) && ("(Lahl;III)I".equals(m.desc) || "(Lnet/minecraft/world/IBlockAccess;III)I".equals(m.desc)))) {
                    LabelNode own = new LabelNode();
                    l.add(new VarInsnNode(ALOAD, 1));
                    l.add(new VarInsnNode(ILOAD, 2));
                    l.add(new VarInsnNode(ILOAD, 3));
                    l.add(new VarInsnNode(ILOAD, 4));
                    l.add(new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/asm/FluidHooks", "foreign", "(Lnet/minecraft/world/IBlockAccess;III)Z", false));
                    l.add(new JumpInsnNode(IFEQ, own));
                    l.add(new InsnNode(ICONST_M1));
                    l.add(new InsnNode(IRETURN));
                    l.add(own);
                    m.instructions.insert(l);
                    n++;
                    continue;
                } else continue;
                LabelNode skip = new LabelNode();
                l.add(new InsnNode(DUP));
                l.add(new JumpInsnNode(IFNULL, skip));
                l.add(new InsnNode(ARETURN));
                l.add(skip);
                l.add(new InsnNode(POP));
                m.instructions.insert(l);
                n++;
            }
            if (n == 0) return bytes;
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Вода: BlockLiquid - {} правок (золотая вода Эдема, течение мимо чужих жидкостей)", n);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Вода Эдема: не удалось исправить BlockLiquid, класс без изменений", t);
            return bytes;
        }
    }
}
