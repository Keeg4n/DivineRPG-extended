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
 * Spawn by rank ({@link com.divinerpgextended.boss.ProgressSpawns}) - at the level of mob kind selection:
 * <ul>
 * <li>WorldServer.spawnRandomCreature (ordinary spawn of all types): the list of kinds for a pack is taken through
 * {@link SpawnHooks#potentialSpawns} instead of ForgeEventFactory.getPotentialSpawns - the same Forge call, and then a copy of the
 * list without kinds above the rank of nearby players. The Forge event hands out the biome's own list, it must not be modified. The call descriptor
 * stays the same (in the game it is still in the names mt/sx, FML renaming brings it to the hook).</li>
 * <li>SpawnerAnimals.performWorldGenSpawning (creatures at chunk generation - without any spawn check; this is how Divine
 * settles Glacons, for example): the biome's creature list passes through {@link SpawnHooks#worldGenSpawns}.</li>
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
                LOG.warn("Rank spawning: no place for selection found in {} - only the check on mob spawn remains here", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Rank spawning: {} picks the mob type from those allowed by rank ({} places)", transformedName, n);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Rank spawning: could not hook into " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }

    /** WorldServer: ForgeEventFactory.getPotentialSpawns -> SpawnHooks.potentialSpawns (same descriptor). */
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
     * SpawnerAnimals.performWorldGenSpawning(World, BiomeGenBase, int x, int z, int, int, Random) - static, found by
     * descriptor (in the game the names are not renamed yet). The first call returning a List is biome.getSpawnableList(creature): after
     * it the list goes to SpawnHooks.worldGenSpawns(list, world, x, z).
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
