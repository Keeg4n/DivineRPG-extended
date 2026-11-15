package com.divinerpgextended.asm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Instead of the hardcoded 200 we take the Divine arcana maximum from {@link ArcanaHooks}:
 * <ul>
 * <li>ArcanaHelper.updateAllBars - the limit on the server (per player);</li>
 * <li>ClientTicker.tickClient - the client top-up of the bar to the limit;</li>
 * <li>ArcanaRenderer.onTickRender - the bar scale (25 in the width formula).</li>
 * </ul>
 * The arcana potion (ItemArcanaPotion.onItemRightClick) is drunk even with a full bar: in the addon it is a timed effect, drunk in advance.
 * Divine does not allow drinking with the bar at exactly 200; a comparison with NaN is never equal, so the check always passes.
 * Only constants in these methods are changed; if something is not found, the class stays as it was.
 */
public class ArcanaTransformer implements IClassTransformer, Opcodes {

    private static final String HELPER = "net.divinerpg.utils.events.ArcanaHelper";
    private static final String TICKER = "net.divinerpg.utils.events.ClientTicker";
    private static final String RENDERER = "net.divinerpg.client.ArcanaRenderer";
    private static final String POTION = "net.divinerpg.items.arcana.ItemArcanaPotion";
    private static final String HOOKS = "com/divinerpgextended/asm/ArcanaHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return bytes;
        boolean helper = HELPER.equals(transformedName), ticker = TICKER.equals(transformedName),
            renderer = RENDERER.equals(transformedName), potion = POTION.equals(transformedName);
        if (!helper && !ticker && !renderer && !potion) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            String playerDesc = null;
            if (helper) {
                for (FieldNode f : node.fields) if ("player".equals(f.name)) playerDesc = f.desc;
                if (playerDesc == null) {
                    LOG.warn("Arcana: ArcanaHelper has no player field - leaving as is");
                    return bytes;
                }
            }
            for (MethodNode m : node.methods) {
                if (potion) {
                    if (!"func_77659_a".equals(m.name) && !"onItemRightClick".equals(m.name)) continue;
                    for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                        if (!(in instanceof LdcInsnNode) || !Float.valueOf(200.0F).equals(((LdcInsnNode) in).cst)) continue;
                        ((LdcInsnNode) in).cst = Float.valueOf(Float.NaN);
                        patched++;
                    }
                    continue;
                }
                String method = helper ? "updateAllBars" : ticker ? "tickClient" : "onTickRender";
                if (!method.equals(m.name)) continue;
                float target = renderer ? 25.0F : 200.0F;
                List<AbstractInsnNode> found = new ArrayList<AbstractInsnNode>();
                for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                    if (in instanceof LdcInsnNode && Float.valueOf(target).equals(((LdcInsnNode) in).cst)) found.add(in);
                }
                for (AbstractInsnNode in : found) {
                    InsnList rep = new InsnList();
                    if (helper) {
                        rep.add(new VarInsnNode(ALOAD, 0));
                        rep.add(new FieldInsnNode(GETFIELD, node.name, "player", playerDesc));
                        rep.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "max", "(" + playerDesc + ")F", false));
                    } else {
                        rep.add(new MethodInsnNode(INVOKESTATIC, HOOKS, renderer ? "clientScale" : "clientMax", "()F", false));
                    }
                    m.instructions.insertBefore(in, rep);
                    m.instructions.remove(in);
                    patched++;
                }
            }
            if (patched == 0) {
                LOG.warn("Arcana: no constants to replace found in {} - leaving as is", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Arcana: maximum is taken from the addon ({}: {} replacements)", transformedName, patched);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Arcana: could not fix " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }
}
