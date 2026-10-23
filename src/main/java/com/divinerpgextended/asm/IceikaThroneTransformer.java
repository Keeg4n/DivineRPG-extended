package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Заселение Iceika ставит подземелья Роллума и лучников на случайную высоту ниже 50, ледяные озёра - до 128, гигантские
 * деревья: соседние чанки заселяются уже после того, как в них достроен купол Ледяного Трона, и всё это падает в него
 * (парящие озёра, дыры в своде, подземелья внутри арены). Вызовы генераторов в заселении идут через
 * {@link IceikaThroneHooks}: внутри Трона они пропускаются.
 */
public class IceikaThroneTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.divinerpg.dimensions.iceika.ChunkProviderIceika";
    private static final String GEN_DESC = "(Lnet/minecraft/world/World;Ljava/util/Random;III)Z";
    private static final String HOOKS = "com/divinerpgextended/asm/IceikaThroneHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int calls = 0;
            for (MethodNode m : node.methods) {
                if (!("func_73153_a".equals(m.name) || "populate".equals(m.name))) continue;
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (!GEN_DESC.equals(c.desc) || !("func_76484_a".equals(c.name) || "generate".equals(c.name))) continue;
                    c.setOpcode(INVOKESTATIC);
                    c.desc = "(Lnet/minecraft/world/gen/feature/WorldGenerator;" + GEN_DESC.substring(1);
                    c.owner = HOOKS;
                    c.name = "gen";
                    calls++;
                }
            }
            if (calls == 0) {
                LOG.warn("Трон Iceika: вызовы генераторов не найдены - класс без изменений");
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Трон Iceika: заселение не трогает Трон ({} вызовов генераторов)", calls);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Трон Iceika: не удалось исправить заселение, класс без изменений", t);
            return bytes;
        }
    }
}
