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
 * Слой "холмов и мутаций" генерации обычного мира (ванильный GenLayerHills и его копия у Biomes O' Plenty) делает из
 * холмистого биома мутацию, если под номером биом + 128 вообще кто-то зарегистрирован. Моды ставят свои биомы на эти
 * номера (Twilight Forest: Thornlands 150 = холмы джунглей + 128, Dark Forest 146 = лесные холмы + 128 ...) - и в обычном
 * мире вырастают биомы Сумеречного леса. Все вызовы BiomeGenBase.getBiome в этих слоях идут через
 * {@link BiomeHooks#mutation}: чужой биом на месте мутации - как будто его там нет.
 */
public class BiomeMutationTransformer implements IClassTransformer, Opcodes {

    private static final String HILLS = "net.minecraft.world.gen.layer.GenLayerHills", BOP = "biomesoplenty.common.world.layer.GenLayerMutationBOP";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !(HILLS.equals(transformedName) || BOP.equals(transformedName))) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                    if (in.getOpcode() != INVOKESTATIC) continue;
                    MethodInsnNode c = (MethodInsnNode) in;
                    // игра: ahu.d(I)Lahu;  моды и Forge: BiomeGenBase.func_150568_d;  разработка: BiomeGenBase.getBiome
                    boolean obf = "ahu".equals(c.owner) && "d".equals(c.name) && "(I)Lahu;".equals(c.desc);
                    boolean deobf = "net/minecraft/world/biome/BiomeGenBase".equals(c.owner) && ("func_150568_d".equals(c.name) || "getBiome".equals(c.name))
                        && "(I)Lnet/minecraft/world/biome/BiomeGenBase;".equals(c.desc);
                    if (!obf && !deobf) continue;
                    c.owner = "com/divinerpgextended/asm/BiomeHooks";
                    c.name = "mutation";
                    c.itf = false; // описание оставляем как было: его имена переведёт сама игра
                    patched++;
                }
            }
            if (patched == 0) return bytes;
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Биомы: мутации в {} - только ванильные ({} вызовов)", transformedName, patched);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Биомы: не удалось поправить " + transformedName + ", класс без изменений", t);
            return bytes;
        }
    }
}
