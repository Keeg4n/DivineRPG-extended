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
 * Other mods' Baubles accessories are silent in the dream before completion ({@link com.divinerpgextended.vethea.NightmareBaubles}):
 * <ul>
 * <li>the wear tick (EventHandlerEntity.playerTick): IBauble.onWornTick goes through a check;</li>
 * <li>BaublesApi.getBaubles gives mods a view without other people's accessories (the items themselves lie in the slots as before, the Baubles
 * window takes them bypassing the API).</li>
 * </ul>
 * The Baubles API lies as a copy in many mods, but one class is loaded - the patch by class name applies to any copy.
 */
public class BaublesTransformer implements IClassTransformer, Opcodes {

    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    private static final String TICK = "baubles.common.event.EventHandlerEntity", API = "baubles.api.BaublesApi";
    private static final String HOOKS = "com/divinerpgextended/vethea/NightmareBaubles";
    /** How many patches are in place (2 - both): checked by the selftest. */
    public static int patched;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !(TICK.equals(transformedName) || API.equals(transformedName))) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            for (MethodNode m : node.methods) {
                if (TICK.equals(transformedName)) {
                    if (!"playerTick".equals(m.name)) continue;
                    for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                        if (in.getOpcode() != INVOKEINTERFACE) continue;
                        MethodInsnNode c = (MethodInsnNode) in;
                        if (!"baubles/api/IBauble".equals(c.owner) || !"onWornTick".equals(c.name)) continue;
                        MethodInsnNode rep = new MethodInsnNode(INVOKESTATIC, HOOKS, "wornTick",
                            "(Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V", false);
                        m.instructions.set(c, rep);
                        in = rep;
                        n++;
                    }
                } else {
                    if (!"getBaubles".equals(m.name) || !"(Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/inventory/IInventory;".equals(m.desc)) continue;
                    for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                        if (in.getOpcode() != ARETURN) continue;
                        InsnList v = new InsnList();
                        v.add(new VarInsnNode(ALOAD, 0));
                        v.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "view",
                            "(Lnet/minecraft/inventory/IInventory;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/inventory/IInventory;", false));
                        m.instructions.insertBefore(in, v);
                        n++;
                    }
                }
            }
            if (n == 0) {
                LOG.warn("Accessories in sleep: no place for the fix found in {}, class unchanged", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            patched++;
            LOG.info("Accessories in sleep: {} - {} places", transformedName, n);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Accessories in sleep: could not modify " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }
}
