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
 * Максимум арканы Divine вместо зашитых 200 берём из {@link ArcanaHooks}:
 * <ul>
 * <li>ArcanaHelper.updateAllBars - предел на сервере (по игроку);</li>
 * <li>ClientTicker.tickClient - клиентская догрузка полоски до предела;</li>
 * <li>ArcanaRenderer.onTickRender - масштаб полоски (25 в формуле ширины).</li>
 * </ul>
 * Меняются только константы в этих методах; если что-то не нашлось - класс остаётся как был.
 */
public class ArcanaTransformer implements IClassTransformer, Opcodes {

    private static final String HELPER = "net.divinerpg.utils.events.ArcanaHelper";
    private static final String TICKER = "net.divinerpg.utils.events.ClientTicker";
    private static final String RENDERER = "net.divinerpg.client.ArcanaRenderer";
    private static final String HOOKS = "com/divinerpgextended/asm/ArcanaHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return bytes;
        boolean helper = HELPER.equals(transformedName), ticker = TICKER.equals(transformedName),
            renderer = RENDERER.equals(transformedName);
        if (!helper && !ticker && !renderer) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            String playerDesc = null;
            if (helper) {
                for (FieldNode f : node.fields) if ("player".equals(f.name)) playerDesc = f.desc;
                if (playerDesc == null) {
                    LOG.warn("Аркана: в ArcanaHelper нет поля player - оставляю как есть");
                    return bytes;
                }
            }
            for (MethodNode m : node.methods) {
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
                LOG.warn("Аркана: в {} не нашлось констант для замены - оставляю как есть", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Аркана: максимум берётся из аддона ({}: замен {})", transformedName, patched);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Аркана: не удалось исправить " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }
}
