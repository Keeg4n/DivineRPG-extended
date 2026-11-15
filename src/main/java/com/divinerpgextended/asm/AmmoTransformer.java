package com.divinerpgextended.asm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
 * Loaded ammo inside the weapon ({@link com.divinerpgextended.perk.Ammo}): shooters ask the inventory themselves - "is there ammo" and
 * "consume ammo". In the bow (vanilla and Divine), Divine shooters, La Vekor and the Corruption Cannon these two calls go through
 * {@link AmmoHooks}: first the loaded ammo of the weapon in hand, then the inventory. On the client likewise - the bow is drawn loaded.
 * <p>
 * The vanilla bow arrives here still with game names (yx.b/yx.a with descriptor (Ladb;)Z), Divine classes - with SRG names.
 */
public class AmmoTransformer implements IClassTransformer, Opcodes {

    private static final Set<String> TARGETS = new HashSet<String>(Arrays.asList("net.minecraft.item.ItemBow",
        "net.divinerpg.items.base.ItemModBow", "net.divinerpg.items.base.ItemProjectileShooter", "net.divinerpg.items.arcana.ItemLaVekor",
        "net.divinerpg.items.vanilla.ItemCorruptedCannon"));
    private static final Set<String> OWNERS = new HashSet<String>(Arrays.asList("yx", "net/minecraft/entity/player/InventoryPlayer"));
    private static final Set<String> DESCS = new HashSet<String>(Arrays.asList("(Ladb;)Z", "(Lnet/minecraft/item/Item;)Z"));
    private static final Set<String> HAS = new HashSet<String>(Arrays.asList("b", "func_146028_b", "hasItem"));
    private static final Set<String> CONSUME = new HashSet<String>(Arrays.asList("a", "func_146026_a", "consumeInventoryItem"));
    private static final String HOOKS = "com/divinerpgextended/asm/AmmoHooks",
        DESC = "(Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/item/Item;)Z";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGETS.contains(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int n = 0;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i.getOpcode() != INVOKEVIRTUAL) continue;
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (!OWNERS.contains(c.owner) || !DESCS.contains(c.desc)) continue;
                    boolean has = HAS.contains(c.name);
                    if (!has && !CONSUME.contains(c.name)) continue;
                    // in the vanilla class the names are still the game ones: "a"/"b" with this descriptor in InventoryPlayer are only these two
                    if (c.owner.equals("yx") != c.desc.equals("(Ladb;)Z")) continue;
                    c.setOpcode(INVOKESTATIC);
                    c.owner = HOOKS;
                    c.name = has ? "has" : "consume";
                    c.desc = DESC;
                    c.itf = false;
                    n++;
                }
            }
            if (n == 0) {
                LOG.warn("Weapon charge: no inventory queries found in {} - the weapon fires from the inventory only", transformedName);
                return bytes;
            }
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Weapon charge: {} takes ammo from the charge first ({} places)", transformedName, n);
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Weapon charge: could not hook into " + transformedName + ", class unchanged", t);
            return bytes;
        }
    }
}
