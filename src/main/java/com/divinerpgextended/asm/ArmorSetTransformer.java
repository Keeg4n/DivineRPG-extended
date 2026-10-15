package com.divinerpgextended.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * В Divine EventArmorFullSet держит броню игрока в общих полях одного объекта, а пишут в них три обработчика урона
 * (и один из них - ещё и на клиенте). В одиночной игре клиент и сервер перетирают поля друг другу, в PvP бонус
 * атакующего считается по броне жертвы: свойства сетов срабатывают не всегда и не по тому игроку. Тела этих трёх
 * обработчиков делаем пустыми, а сами свойства (с теми же числами) считает {@link com.divinerpgextended.perk.ArmorSets}.
 * Признак успешной правки - поле dre_patched в классе. При любой ошибке класс остаётся как был.
 */
public class ArmorSetTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.divinerpg.utils.events.EventArmorFullSet";
    private static final Logger LOG = LogManager.getLogger("divinerpgextended-core");

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            for (MethodNode m : node.methods) {
                boolean hurt = ("onPlayerHurtEvent".equals(m.name) || "onLivingHurtEvent".equals(m.name))
                    && "(Lnet/minecraftforge/event/entity/living/LivingHurtEvent;)V".equals(m.desc);
                boolean attack = "onPlayerAttackEvent".equals(m.name)
                    && "(Lnet/minecraftforge/event/entity/living/LivingAttackEvent;)V".equals(m.desc);
                if (!hurt && !attack) continue;
                m.instructions.clear();
                m.instructions.add(new InsnNode(RETURN));
                m.tryCatchBlocks.clear();
                if (m.localVariables != null) m.localVariables.clear();
                m.maxStack = 0;
                patched++;
            }
            if (patched != 3) {
                LOG.warn("Сеты брони Divine: найдено обработчиков {} из 3 - оставляю класс без изменений", patched);
                return bytes;
            }
            node.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "dre_patched", "Z", null, Integer.valueOf(1)));
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            LOG.info("Сеты брони Divine: свойства сетов считает аддон (EventArmorFullSet)");
            return w.toByteArray();
        } catch (Throwable t) {
            LOG.error("Сеты брони Divine: не удалось исправить, класс без изменений", t);
            return bytes;
        }
    }
}
