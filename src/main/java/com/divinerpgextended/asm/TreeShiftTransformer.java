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
 * Заселение сумеречных миров Divine ставит деревья (и каменные конусы Эдема) от угла чанка, а не со сдвигом +8, как
 * положено: крона залезает в соседние чанки, которые у игрока уже загружены. Сервер шлёт эти блоки клиенту, и клиент
 * на каждый пересчитывает небесный свет - над пустотой у края острова это столб высотой ~100 в пяти колоннах, отсюда
 * рывки при исследовании Эдема и Вайлдвуда. Вызовы этих генераторов идут через {@link TreeShiftHooks}: та же точка
 * со сдвигом +8 и высотой, пересчитанной под новое место. Без сдвига: дерево 2 Вайлдвуда (уже +8), руды, цветы, озёра.
 */
public class TreeShiftTransformer implements IClassTransformer, Opcodes {

    private static final String PKG = "net.divinerpg.dimensions.twilight.";
    private static final String[] TARGETS = { "eden.ChunkProviderEden", "wildwood.ChunkProviderWildwood",
        "apalachia.ChunkProviderApalachia", "skythern.ChunkProviderSkythern", "mortum.ChunkProviderMortum" };
    private static final String GEN_DESC = "(Lnet/minecraft/world/World;Ljava/util/Random;III)Z";
    private static final String CONE = "net/divinerpg/dimensions/vethea/all/WorldGenConeUp";
    private static final String CONE_DESC = "(Lnet/minecraft/world/World;Ljava/util/Random;IIII)Z";
    private static final String HOOKS = "com/divinerpgextended/asm/TreeShiftHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !transformedName.startsWith(PKG) || Boolean.getBoolean("dre.noTreeShift")) return bytes;
        boolean target = false;
        for (String t : TARGETS) target |= transformedName.equals(PKG + t);
        if (!target) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int trees = 0, cones = 0;
            for (MethodNode m : node.methods) {
                if (!("func_73153_a".equals(m.name) || "populate".equals(m.name))) continue;
                // метка "идёт заселение" на всё время метода (для пересылки чанков целиком - PopulationResend)
                m.instructions.insert(new MethodInsnNode(INVOKESTATIC, HOOKS, "begin", "()V", false));
                for (AbstractInsnNode r = m.instructions.getFirst(); r != null; r = r.getNext()) {
                    if (r.getOpcode() == RETURN) m.instructions.insertBefore(r, new MethodInsnNode(INVOKESTATIC, HOOKS, "end", "()V", false));
                }
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (GEN_DESC.equals(c.desc) && ("func_76484_a".equals(c.name) || "generate".equals(c.name)) && tree(c.owner)) {
                        c.setOpcode(INVOKESTATIC);
                        c.desc = "(Lnet/minecraft/world/gen/feature/WorldGenerator;" + GEN_DESC.substring(1);
                        c.owner = HOOKS;
                        c.name = "tree";
                        trees++;
                    } else if (CONE.equals(c.owner) && "generate".equals(c.name) && CONE_DESC.equals(c.desc)) {
                        c.setOpcode(INVOKESTATIC);
                        c.desc = "(L" + CONE + ";" + CONE_DESC.substring(1);
                        c.owner = HOOKS;
                        c.name = "cone";
                        cones++;
                    }
                }
            }
            if (trees + cones == 0) {
                LOG.warn("Деревья {}: вызовы не найдены - класс без изменений", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Деревья {}: сдвиг +8 ({} деревьев, {} конусов)", transformedName, trees, cones);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Деревья " + transformedName + ": не удалось исправить, класс без изменений", t);
            return bytes;
        }
    }

    /** Генераторы деревьев Divine, которые ставятся от угла чанка (дерево 2 Вайлдвуда уже со сдвигом). */
    private static boolean tree(String owner) {
        String simple = owner.substring(owner.lastIndexOf('/') + 1);
        return owner.startsWith("net/divinerpg/") && simple.contains("Tree") && !"WorldGenWildwoodTree2".equals(simple);
    }
}
