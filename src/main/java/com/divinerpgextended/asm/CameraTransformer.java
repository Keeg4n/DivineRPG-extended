package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Camera in the Apalachia Cart: Forge 1.7.10 has no camera setup event, so at the start of
 * EntityRenderer.orientCamera (before the rotation by the player's view) we add a call to {@link CameraHooks#orient} - roll of the picture
 * around the view axis on a wall and ceiling, and the player.setAngles calls in updateCameraAndRender (mouse) go through
 * {@link CameraHooks#angles} - mouse shift in the axes of the tilted screen. Names: h / b (game), func_78467_g / func_78480_b
 * or orientCamera / updateCameraAndRender (development); setAngles - c, func_70082_c, setAngles. On any error the class
 * stays as it was.
 */
public class CameraTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.minecraft.client.renderer.EntityRenderer";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int orient = 0, mouse = 0;
            for (MethodNode m : node.methods) {
                if (!"(F)V".equals(m.desc)) continue;
                if ("h".equals(m.name) || "func_78467_g".equals(m.name) || "orientCamera".equals(m.name)) {
                    InsnList call = new InsnList();
                    call.add(new VarInsnNode(FLOAD, 1));
                    call.add(new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/asm/CameraHooks", "orient", "(F)V", false));
                    m.instructions.insert(call);
                    orient++;
                } else if ("b".equals(m.name) || "func_78480_b".equals(m.name) || "updateCameraAndRender".equals(m.name)) {
                    for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                        if (i.getOpcode() != INVOKEVIRTUAL) continue;
                        MethodInsnNode c = (MethodInsnNode) i;
                        if (!"(FF)V".equals(c.desc) || !("c".equals(c.name) || "func_70082_c".equals(c.name) || "setAngles".equals(c.name))) continue;
                        c.setOpcode(INVOKESTATIC);
                        c.owner = "com/divinerpgextended/asm/CameraHooks";
                        c.name = "angles";
                        c.desc = "(Ljava/lang/Object;FF)V";
                        c.itf = false;
                        mouse++;
                    }
                }
            }
            if (orient != 1) {
                LOG.warn("Minecart camera: found {} of 1 methods - leaving class unchanged", orient);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Minecart camera: view roll in the Apalachia minecart hooked (EntityRenderer), mouse - {} calls", mouse);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Minecart camera: could not hook, class unchanged", t);
            return bytes;
        }
    }
}
