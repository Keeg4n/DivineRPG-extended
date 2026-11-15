package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 * Divine bosses are not "bosses" for other mods: their common class has no IBossDisplayData, unlike the Wither and the dragon, and
 * mods that spare bosses do not spare them (the Blood Magic sacrificial dagger kills in one hit). We add the interface
 * to the EntityDivineRPGBoss class; its methods (health, max health, name) already exist for any living entity.
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
            LOG.info("Divine bosses: boss status (IBossDisplayData) added");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Divine bosses: could not add boss status, class unchanged", t);
            return bytes;
        }
    }
}
