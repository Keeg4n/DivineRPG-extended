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
 * Спавн по рангам ({@link com.divinerpgextended.boss.ProgressSpawns}) - на уровне выбора вида моба:
 * <ul>
 * <li>WorldServer.spawnRandomCreature (обычный спавн всех типов): список видов для стаи берётся через
 * {@link SpawnHooks#potentialSpawns} вместо ForgeEventFactory.getPotentialSpawns - тот же вызов Forge, а затем копия
 * списка без видов выше ранга игроков рядом. Событие Forge отдаёт сам список биома, менять его нельзя. Описание вызова
 * остаётся прежним (в игре оно ещё в именах mt/sx, переименование FML приводит его к хуку).</li>
 * <li>SpawnerAnimals.performWorldGenSpawning (существа при генерации чанка - без проверки появления вообще; так Divine
 * селит, например, Глаконов): список существ биома проходит через {@link SpawnHooks#worldGenSpawns}.</li>
 * </ul>
 */
public class SpawnTransformer implements IClassTransformer, Opcodes {

    private static final String SERVER = "net.minecraft.world.WorldServer", SPAWNER = "net.minecraft.world.SpawnerAnimals",
        FORGE = "net/minecraftforge/event/ForgeEventFactory", HOOKS = "com/divinerpgextended/asm/SpawnHooks";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        boolean server = SERVER.equals(transformedName), spawner = SPAWNER.equals(transformedName);
        if (bytes == null || !server && !spawner) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            for (MethodNode m : node.methods) n += server ? potentialSpawns(m) : worldGen(m);
            if (n == 0) {
                LOG.warn("Спавн по рангам: в {} не нашлось места для отбора - здесь остаётся только проверка при появлении моба", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Спавн по рангам: {} выбирает вид моба из разрешённых по рангу ({} мест)", transformedName, n);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Спавн по рангам: не удалось подключиться к " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }

    /** WorldServer: ForgeEventFactory.getPotentialSpawns -> SpawnHooks.potentialSpawns (то же описание). */
    private static int potentialSpawns(MethodNode m) {
        int n = 0;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i.getOpcode() != INVOKESTATIC) continue;
            MethodInsnNode c = (MethodInsnNode) i;
            if (!FORGE.equals(c.owner) || !"getPotentialSpawns".equals(c.name)) continue;
            c.owner = HOOKS;
            c.name = "potentialSpawns";
            n++;
        }
        return n;
    }

    /**
     * SpawnerAnimals.performWorldGenSpawning(World, BiomeGenBase, int x, int z, int, int, Random) - статический, узнаём по
     * описанию (в игре имена ещё не переименованы). Первый вызов, отдающий List, - biome.getSpawnableList(creature): после
     * него список уходит в SpawnHooks.worldGenSpawns(list, world, x, z).
     */
    private static int worldGen(MethodNode m) {
        if ((m.access & ACC_STATIC) == 0 || !m.desc.endsWith("IIIILjava/util/Random;)V")) return 0;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (!(i instanceof MethodInsnNode) || !((MethodInsnNode) i).desc.endsWith(")Ljava/util/List;")) continue;
            InsnList l = new InsnList();
            l.add(new VarInsnNode(ALOAD, 0));
            l.add(new VarInsnNode(ILOAD, 2));
            l.add(new VarInsnNode(ILOAD, 3));
            l.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "worldGenSpawns", "(Ljava/util/List;Lnet/minecraft/world/World;II)Ljava/util/List;", false));
            m.instructions.insert(i, l);
            return 1;
        }
        return 0;
    }
}
