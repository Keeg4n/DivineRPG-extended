package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
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
 * Divine teleports (End scepter, teleportation crystal) ask permission from protected regions
 * ({@link com.divinerpgextended.portal.TeleportGuard}): at the start of onItemRightClick - "if {@link TeleportHooks#deny} - return the
 * item and exit" (no teleport, no arcana or durability spent).
 */
public class TeleportTransformer implements IClassTransformer, Opcodes {

    private static final String SCEPTER = "net.divinerpg.items.arcana.ItemEnderScepter", CRYSTAL = "net.divinerpg.items.vanilla.ItemTeleportationCrystal";
    private static final String DESC = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !(SCEPTER.equals(transformedName) || CRYSTAL.equals(transformedName))) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            for (MethodNode m : node.methods) {
                if (!("func_77659_a".equals(m.name) || "onItemRightClick".equals(m.name)) || !DESC.equals(m.desc)) continue;
                InsnList in = new InsnList();
                LabelNode go = new LabelNode();
                in.add(new VarInsnNode(ALOAD, 3));
                in.add(new VarInsnNode(ALOAD, 0));
                in.add(new MethodInsnNode(INVOKESTATIC, "com/divinerpgextended/asm/TeleportHooks", "deny",
                    "(Lnet/minecraft/entity/player/EntityPlayer;Ljava/lang/Object;)Z", false));
                in.add(new JumpInsnNode(IFEQ, go));
                in.add(new VarInsnNode(ALOAD, 1));
                in.add(new InsnNode(ARETURN));
                in.add(go);
                if ((node.version & 0xFFFF) >= 50) in.add(new FrameNode(F_SAME, 0, null, 0, null));
                m.instructions.insert(in);
                patched++;
            }
            if (patched == 0) return bytes;
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Teleport: {} asks claims", transformedName);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Teleport: could not hook the claim check into " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }
}
