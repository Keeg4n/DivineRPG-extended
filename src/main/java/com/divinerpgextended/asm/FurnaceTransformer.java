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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * В Divine у "бесконечных" печей (TileEntityInfiniteFurnace: угольная печь Iceika, Molten, Oceanfire, Whitefire,
 * Demon) всего два слота - вход (0) и результат (1), но сторонняя логика списана с ванильной печи: сбоку доступных
 * слотов нет вовсе, а забирать из слота 1 снизу разрешено только ведро (у ванильной печи слот 1 - топливо). Поэтому
 * воронки и трубы с ними не работают. Заменяем тела двух методов на вызовы {@link FurnaceHooks}. При любой ошибке
 * класс остаётся как был.
 */
public class FurnaceTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.divinerpg.blocks.base.tileentity.TileEntityInfiniteFurnace";
    private static final String HOOKS = "com/divinerpgextended/asm/FurnaceHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            for (MethodNode m : node.methods) {
                boolean slots = ("func_94128_d".equals(m.name) || "getAccessibleSlotsFromSide".equals(m.name))
                    && "(I)[I".equals(m.desc);
                boolean extract = ("func_102008_b".equals(m.name) || "canExtractItem".equals(m.name))
                    && "(ILnet/minecraft/item/ItemStack;I)Z".equals(m.desc);
                if (!slots && !extract) continue;
                InsnList body = new InsnList();
                body.add(new VarInsnNode(ILOAD, 1)); // сторона или номер слота
                if (slots) {
                    body.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "slots", "(I)[I", false));
                    body.add(new InsnNode(ARETURN));
                } else {
                    body.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "canExtract", "(I)Z", false));
                    body.add(new InsnNode(IRETURN));
                }
                m.instructions.clear();
                m.instructions.add(body);
                m.tryCatchBlocks.clear();
                if (m.localVariables != null) m.localVariables.clear();
                m.maxStack = 1;
                m.maxLocals = Math.max(m.maxLocals, extract ? 4 : 2);
                patched++;
            }
            if (patched != 2) {
                LOG.warn("Печи Divine: найдено методов для исправления {} из 2 - оставляю класс без изменений", patched);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Печи Divine: воронки и трубы починены (TileEntityInfiniteFurnace)");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Печи Divine: не удалось исправить, класс без изменений", t);
            return bytes;
        }
    }
}
