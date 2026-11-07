package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Спавнеры Арканы для копателей неломаемые, а переносить их можно ({@link com.divinerpgextended.perk.MiningGuard}).
 * <ul>
 * <li>Набалдашник "Перемещение" (Thaumic Tinkerer) отказывает при прочности меньше нуля: его единственное чтение прочности
 * заменяется на MiningGuard.forMover.</li>
 * <li>Телепозер Blood Magic сам прочность не читает, но в сборках, где запрет добавлен (RedServer), неизвестно, где он стоит: в самом
 * телепозере, в обработчике его события или в другом моде. Поэтому обновление TETeleposer оборачивается в MiningGuard.beginMove и
 * endMove: пока оно идёт, спавнеры показывают настоящую прочность кому угодно. Другие блоки и обмен блоков ритуалами это не задевает.</li>
 * </ul>
 */
public class MoverTransformer implements IClassTransformer, Opcodes {

    static final String FOCUS = "thaumic.tinkerer.common.item.foci.ItemFocusDislocation";
    static final String TELEPOSER = "WayofTime.alchemicalWizardry.common.tileEntity.TETeleposer";
    private static final String GUARD = "com/divinerpgextended/perk/MiningGuard";
    private static final String RENAMED = "dre$update";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    /** Поправки встали: проверяет самопроверка. */
    public static boolean focusPatched, teleposerPatched;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        boolean focus = FOCUS.equals(transformedName);
        if (!focus && !TELEPOSER.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            if (!(focus ? focus(node) : teleposer(node))) return bytes;
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            if (focus) focusPatched = true;
            else teleposerPatched = true;
            LOG.info("Перенос спавнеров: {} исправлен", transformedName);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Перенос спавнеров: не удалось изменить " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }

    private static boolean focus(ClassNode node) {
        int count = 0;
        for (MethodNode m : node.methods) {
            if (!"onFocusRightClick".equals(m.name)) continue;
            for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                if (in.getOpcode() != INVOKEVIRTUAL || !((MethodInsnNode) in).desc.matches("\\(L[^;]+;III\\)F")) continue;
                MethodInsnNode rep = new MethodInsnNode(INVOKESTATIC, GUARD, "forMover", "(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;III)F", false);
                m.instructions.set(in, rep);
                in = rep;
                count++;
            }
        }
        if (count != 1) LOG.warn("Перенос спавнеров: у набалдашника чтений прочности {} вместо одного, класс без изменений", count);
        return count == 1;
    }

    /** Старое обновление под новым именем, на его месте обёртка: begin; try { старое } finally { end }. */
    private static boolean teleposer(ClassNode node) {
        MethodNode update = null;
        for (MethodNode m : node.methods) if ("()V".equals(m.desc) && ("func_145845_h".equals(m.name) || "updateEntity".equals(m.name))) update = m;
        if (update == null) {
            LOG.warn("Перенос спавнеров: обновление телепозера не найдено, класс без изменений");
            return false;
        }
        MethodNode wrapper = new MethodNode(ACC_PUBLIC, update.name, "()V", null, null);
        update.name = RENAMED;
        update.access = ACC_PRIVATE;
        LabelNode start = new LabelNode(), end = new LabelNode(), handler = new LabelNode();
        wrapper.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
        InsnList c = wrapper.instructions;
        guard(c, "beginMove");
        c.add(start);
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new MethodInsnNode(INVOKESPECIAL, node.name, RENAMED, "()V", false));
        c.add(end);
        guard(c, "endMove");
        c.add(new InsnNode(RETURN));
        c.add(handler);
        c.add(new FrameNode(F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" }));
        c.add(new VarInsnNode(ASTORE, 1));
        guard(c, "endMove");
        c.add(new VarInsnNode(ALOAD, 1));
        c.add(new InsnNode(ATHROW));
        node.methods.add(wrapper);
        return true;
    }

    private static void guard(InsnList c, String method) {
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new MethodInsnNode(INVOKESTATIC, GUARD, method, "(Lnet/minecraft/tileentity/TileEntity;)V", false));
    }
}
