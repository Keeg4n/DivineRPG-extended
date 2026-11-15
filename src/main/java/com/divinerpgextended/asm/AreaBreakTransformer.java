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
 * Other mods' area breaking without a break event - through {@link AreaBreakHooks}:
 * <ul>
 * <li>Blood Magic bound pickaxe, axe and shovel (right click breaks a cube around): canMineBlock in onItemRightClick - the protection
 * of a single spawn - is replaced with a check with the break event for each block;</li>
 * <li>Botania, ToolCommons.removeBlockWithDrops (Terra Shatterer, Terra Axe and other area tools) and Thaumcraft,
 * BlockUtils.harvestBlock (axe of the stream, foci, elemental tools): at the start - "break event cancelled - exit";</li>
 * <li>Treecapitator: in the dream before completion isBreakingPossible answers "no".</li>
 * </ul>
 * Tinkers breaking (hammer, excavator, lumber axe) sends the event itself.
 */
public class AreaBreakTransformer implements IClassTransformer, Opcodes {

    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    private static final String HOOKS = "com/divinerpgextended/asm/AreaBreakHooks";
    private static final Set<String> BOUND = new HashSet<String>(Arrays.asList("WayofTime.alchemicalWizardry.common.items.BoundPickaxe",
        "WayofTime.alchemicalWizardry.common.items.BoundAxe", "WayofTime.alchemicalWizardry.common.items.BoundShovel"));
    private static final String BOTANIA = "vazkii.botania.common.item.equipment.tool.ToolCommons", THAUM = "thaumcraft.common.lib.utils.BlockUtils",
        TREECAP = "bspkrs.treecapitator.Treecapitator";
    /** The patches are in place: checked by the selftest. */
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
                        else { // fallback path: at least the canMineBlock check itself with the event
                            MethodInsnNode rep = new MethodInsnNode(INVOKESTATIC, HOOKS, "canMine", "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;III)Z", false);
                            m.instructions.set(c, rep);
                            in = rep;
                            n++;
                            LOG.warn("Area break: block breaking not recognized in {}, check only in canMineBlock", transformedName);
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
                    // the breaking overload is the one where the removedByPlayer call itself is
                    boolean botania = BOTANIA.equals(transformedName);
                    // Botania: the overload with the removedByPlayer call itself; Thaumcraft: harvestBlock(world, player, x, y, z, flag, number) - its tools and foci break through it
                    if (botania ? !"removeBlockWithDrops".equals(m.name) || !removes(m)
                        : !"harvestBlock".equals(m.name) || !"(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;IIIZI)Z".equals(m.desc)) continue;
                    // Botania: (player 0, item 1, world 2, x 3, y 4, z 5, ...)V; Thaumcraft: (world 0, player 1, x 2, y 3, z 4, ...)Z
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
                LOG.warn("Area break: no place for the check found in {}, class unchanged", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            patched.add(transformedName);
            LOG.info("Area break: {} asks the break event ({} places)", transformedName, n);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Area break: could not modify " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }

    /**
     * Blood Magic bound tools: before breaking a block (right after the canMineBlock check - the branch that bypasses
     * the check jumps there too: for the axe logs go past it) - "break event cancelled - to the next block".
     * Coordinates - by the same commands that compute the canMineBlock arguments (loads and additions).
     */
    private static boolean guardBreak(MethodNode m, MethodInsnNode c) {
        // canMineBlock arguments: world, player, x, y, z - we go backwards until we collect 5 values
        java.util.LinkedList<AbstractInsnNode> args = new java.util.LinkedList<AbstractInsnNode>();
        int need = 5;
        for (AbstractInsnNode p = c.getPrevious(); p != null && need > 0; p = p.getPrevious()) {
            if (p.getOpcode() < 0) continue; // labels, lines, frames
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
        while (at != null && at.getOpcode() < 0) at = at.getNext(); // past the label and the frame of the break start
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
