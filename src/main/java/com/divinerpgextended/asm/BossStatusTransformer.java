package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 * Боссы Divine - не "боссы" для других модов: у их общего класса нет IBossDisplayData, как у Иссушителя и дракона, и
 * моды, которые щадят боссов, их не щадят (жертвенный кинжал Blood Magic убивает с одного удара). Добавляем интерфейс
 * классу EntityDivineRPGBoss; его методы (здоровье, наибольшее здоровье, имя) у любой живой сущности уже есть.
 */
public class BossStatusTransformer implements IClassTransformer {

    private static final String TARGET = "net.divinerpg.entities.base.EntityDivineRPGBoss";
    private static final String BOSS = "net/minecraft/entity/boss/IBossDisplayData";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            if (node.interfaces.contains(BOSS)) return bytes;
            node.interfaces.add(BOSS);
            ClassWriter w = new ClassWriter(0);
            node.accept(w);
            LOG.info("Боссы Divine: статус босса (IBossDisplayData) добавлен");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Боссы Divine: не удалось добавить статус босса, класс без изменений", t);
            return bytes;
        }
    }
}
