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
 * Генерация Ветеи у Divine тормозила сервер при быстром полёте: украшения (узоры потолка, конусы, цветы, шипы деревень,
 * постройки) ставят блоки с оповещением соседей и читают блоки соседних, ещё не созданных чанков - те создаются и
 * заселяются сами, и так по цепочке (замер: 88% времени генерации - в узорах потолка, худший шаг - 9 с). Вызовы мира в
 * классах генерации Ветеи идут через {@link VetheaGenHooks}: украшения ({@link #decor}) не трогают несозданные чанки
 * (обрезаются по краю); постройки (арены боссов, склепы, дома) строятся целиком, как раньше. Всё - без оповещения соседей.
 */
public class VetheaGenTransformer implements IClassTransformer, Opcodes {

    private static final String PKG = "net.divinerpg.dimensions.vethea.";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String HOOKS = "com/divinerpgextended/asm/VetheaGenHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");
    private static int classes, calls;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || transformedName == null || !transformedName.startsWith(PKG)) return bytes;
        // портал и сам мир - не генерация: там всё как было
        if (transformedName.contains("Teleporter") || transformedName.contains("WorldProvider")) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            boolean decor = decor(transformedName.substring(PKG.length()));
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (!WORLD.equals(c.owner)) continue;
                    String hook = hook(c.name, c.desc);
                    if (hook != null && !decor) hook = "set".equals(hook) ? "build" : null; // постройка: только без оповещения
                    if (hook == null) continue;
                    c.setOpcode(INVOKESTATIC);
                    c.owner = HOOKS;
                    c.desc = "(L" + WORLD + ";" + c.desc.substring(1);
                    c.name = hook;
                    n++;
                }
            }
            if (n == 0) return bytes;
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            classes++;
            calls += n;
            LOG.info("Ветея: генерация {} не трогает несозданные чанки ({} вызовов; всего {} классов, {} вызовов)", transformedName, n, classes, calls);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Ветея: не удалось исправить " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }

    /** Украшение (узоры, конусы, цветы, деревья, шипы деревень, само заселение), а не постройка. */
    static boolean decor(String cls) {
        return cls.startsWith("all.") || cls.equals("WorldGenVetheanFlower") || cls.equals("ChunkProviderVethea") || cls.endsWith("Forest")
            || cls.contains("Tree") || cls.equals("village.WorldGenVillageIsland");
    }

    /** Обёртка для вызова мира (null - вызов не трогаем). */
    static String hook(String name, String desc) {
        if (("func_147449_b".equals(name) || "setBlock".equals(name)) && "(IIILnet/minecraft/block/Block;)Z".equals(desc)) return "set";
        if (("func_147465_d".equals(name) || "setBlock".equals(name)) && "(IIILnet/minecraft/block/Block;II)Z".equals(desc)) return "set";
        if (("func_147439_a".equals(name) || "getBlock".equals(name)) && "(III)Lnet/minecraft/block/Block;".equals(desc)) return "get";
        if (("func_147437_c".equals(name) || "isAirBlock".equals(name)) && "(III)Z".equals(desc)) return "air";
        if (("func_72805_g".equals(name) || "getBlockMetadata".equals(name)) && "(III)I".equals(desc)) return "meta";
        return null;
    }
}
