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
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Чужая площадная ломка без события ломки - через {@link AreaBreakHooks}:
 * <ul>
 * <li>связанные кирка, топор и лопата Blood Magic (ПКМ ломает куб вокруг): canMineBlock в onItemRightClick - защита
 * одного спавна - заменяется проверкой с событием ломки на каждый блок;</li>
 * <li>Ботания, ToolCommons.removeBlockWithDrops (Крушитель Терры, Рубитель Терры и другие площадные) и Таумкрафт,
 * BlockUtils.harvestBlock (топор потока, фокусы, стихийные инструменты): в начале - "событие ломки отменено - выйти";</li>
 * <li>Treecapitator: во сне до прохождения чужим топором isBreakingPossible отвечает "нет".</li>
 * </ul>
 * Ломка Tinkers (молот, экскаватор, лесоруб) событие шлёт сама.
 */
public class AreaBreakTransformer implements IClassTransformer, Opcodes {

    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    private static final String HOOKS = "com/divinerpgextended/asm/AreaBreakHooks";
    private static final Set<String> BOUND = new HashSet<String>(Arrays.asList("WayofTime.alchemicalWizardry.common.items.BoundPickaxe",
        "WayofTime.alchemicalWizardry.common.items.BoundAxe", "WayofTime.alchemicalWizardry.common.items.BoundShovel"));
    private static final String BOTANIA = "vazkii.botania.common.item.equipment.tool.ToolCommons", THAUM = "thaumcraft.common.lib.utils.BlockUtils",
        TREECAP = "bspkrs.treecapitator.Treecapitator";
    /** Поправки встали: проверяет самопроверка. */
    public static final Set<String> patched = new HashSet<String>();

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return bytes;
        boolean bound = BOUND.contains(transformedName);
        if (!bound && !BOTANIA.equals(transformedName) && !THAUM.equals(transformedName) && !TREECAP.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            boolean frames = (node.version & 0xFFFF) >= 50;
            int n = 0;
            for (MethodNode m : node.methods) {
                if (bound) {
                    if (!"func_77659_a".equals(m.name) && !"onItemRightClick".equals(m.name)) continue;
                    for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                        if (in.getOpcode() != INVOKEVIRTUAL) continue;
                        MethodInsnNode c = (MethodInsnNode) in;
                        if (!"net/minecraft/world/World".equals(c.owner) || !("func_72962_a".equals(c.name) || "canMineBlock".equals(c.name))
                            || !"(Lnet/minecraft/entity/player/EntityPlayer;III)Z".equals(c.desc)) continue;
                        if (guardBreak(m, c)) n++;
                        else { // запасной путь: хотя бы сама проверка canMineBlock с событием
                            MethodInsnNode rep = new MethodInsnNode(INVOKESTATIC, HOOKS, "canMine", "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;III)Z", false);
                            m.instructions.set(c, rep);
                            in = rep;
                            n++;
                            LOG.warn("Площадная ломка: в {} не узнал ломку блока, проверка только в canMineBlock", transformedName);
                        }
                        break;
                    }
                } else if (TREECAP.equals(transformedName)) {
                    if (!"isBreakingPossible".equals(m.name) || !m.desc.startsWith("(Lnet/minecraft/entity/player/EntityPlayer;") || !m.desc.endsWith(")Z")) continue;
                    InsnList g = new InsnList();
                    LabelNode go = new LabelNode();
                    g.add(new VarInsnNode(ALOAD, 0));
                    g.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "treecapitatorDenied", "(Lnet/minecraft/entity/player/EntityPlayer;)Z", false));
                    g.add(new JumpInsnNode(IFEQ, go));
                    g.add(new InsnNode(ICONST_0));
                    g.add(new InsnNode(IRETURN));
                    g.add(go);
                    if (frames) g.add(new FrameNode(F_SAME, 0, null, 0, null));
                    m.instructions.insert(g);
                    n++;
                } else {
                    // ломающая перегрузка - та, где сам вызов removedByPlayer
                    boolean botania = BOTANIA.equals(transformedName);
                    // Ботания: перегрузка с самим вызовом removedByPlayer; Таумкрафт: harvestBlock(мир, игрок, x, y, z, флаг, число) - через неё ломают его инструменты и фокусы
                    if (botania ? !"removeBlockWithDrops".equals(m.name) || !removes(m)
                        : !"harvestBlock".equals(m.name) || !"(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;IIIZI)Z".equals(m.desc)) continue;
                    // Ботания: (игрок 0, вещь 1, мир 2, x 3, y 4, z 5, ...)V; Таумкрафт: (мир 0, игрок 1, x 2, y 3, z 4, ...)Z
                    int world = botania ? 2 : 0, player = botania ? 0 : 1, x = botania ? 3 : 2;
                    InsnList g = new InsnList();
                    LabelNode go = new LabelNode();
                    g.add(new VarInsnNode(ALOAD, world));
                    g.add(new VarInsnNode(ALOAD, player));
                    g.add(new VarInsnNode(ILOAD, x));
                    g.add(new VarInsnNode(ILOAD, x + 1));
                    g.add(new VarInsnNode(ILOAD, x + 2));
                    g.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "mayBreak", "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;III)Z", false));
                    g.add(new JumpInsnNode(IFNE, go));
                    if (m.desc.endsWith(")V")) g.add(new InsnNode(RETURN));
                    else {
                        g.add(new InsnNode(ICONST_0));
                        g.add(new InsnNode(IRETURN));
                    }
                    g.add(go);
                    if (frames) g.add(new FrameNode(F_SAME, 0, null, 0, null));
                    m.instructions.insert(g);
                    n++;
                }
            }
            if (n == 0) {
                LOG.warn("Площадная ломка: в {} не нашлось места для проверки, класс без изменений", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            patched.add(transformedName);
            LOG.info("Площадная ломка: {} спрашивает событие ломки ({} мест)", transformedName, n);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Площадная ломка: не удалось изменить " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }

    /**
     * Связанные инструменты Blood Magic: перед ломкой блока (сразу за проверкой canMineBlock - туда же прыгает ветка,
     * которая проверку обходит: у топора брёвна идут мимо неё) - "событие ломки отменено - к следующему блоку".
     * Координаты - теми же командами, что считают аргументы canMineBlock (загрузки и сложения).
     */
    private static boolean guardBreak(MethodNode m, MethodInsnNode c) {
        // аргументы canMineBlock: мир, игрок, x, y, z - идём назад, пока не наберём 5 значений
        java.util.LinkedList<AbstractInsnNode> args = new java.util.LinkedList<AbstractInsnNode>();
        int need = 5;
        for (AbstractInsnNode p = c.getPrevious(); p != null && need > 0; p = p.getPrevious()) {
            if (p.getOpcode() < 0) continue; // метки, строки, кадры
            int op = p.getOpcode();
            if (op == ALOAD || op == ILOAD || op == ICONST_M1 || op >= ICONST_0 && op <= ICONST_5 || op == BIPUSH || op == SIPUSH) need--;
            else if (op == IADD || op == ISUB) need++;
            else return false;
            args.addFirst(p);
        }
        if (need != 0) return false;
        AbstractInsnNode j = c.getNext();
        while (j != null && j.getOpcode() < 0) j = j.getNext();
        if (j == null || j.getOpcode() != IFEQ) return false;
        LabelNode skip = ((JumpInsnNode) j).label;
        AbstractInsnNode at = j.getNext();
        while (at != null && at.getOpcode() < 0) at = at.getNext(); // за метку и кадр начала ломки
        if (at == null) return false;
        InsnList g = new InsnList();
        java.util.Map<LabelNode, LabelNode> none = java.util.Collections.emptyMap();
        for (AbstractInsnNode a : args) g.add(a.clone(none));
        g.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "mayBreak", "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;III)Z", false));
        g.add(new JumpInsnNode(IFEQ, skip));
        m.instructions.insertBefore(at, g);
        return true;
    }

    private static boolean removes(MethodNode m) {
        for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext())
            if (in instanceof MethodInsnNode && "removedByPlayer".equals(((MethodInsnNode) in).name)) return true;
        return false;
    }
}
