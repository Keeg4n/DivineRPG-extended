package com.divinerpgextended.fishing;

import java.util.UUID;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Золотая рыбка: съел - навсегда +1 сердце (+2 к здоровью), всего до {@link #MAX}. Счёт хранится в постоянных данных игрока
 * (переживает смерть), прибавка ставится модификатором здоровья при каждом входе в мир.
 */
public class GoldenHearts {

    public static final int MAX = 5;
    private static final String KEY = "dreGoldenHearts";
    private static final UUID ID = UUID.fromString("6b3e2f1a-9c4d-4e8b-a2f7-1d5c8e9b0a42");

    private static NBTTagCompound data(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static int hearts(EntityPlayer p) {
        return data(p).getInteger(KEY);
    }

    /** Поставить прибавку здоровья по счёту. */
    public static void apply(EntityPlayer p) {
        IAttributeInstance hp = p.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        AttributeModifier old = hp.getModifier(ID);
        int n = hearts(p);
        if (old != null && old.getAmount() == n * 2.0) return;
        if (old != null) hp.removeModifier(old);
        if (n > 0) hp.applyModifier(new AttributeModifier(ID, "dre golden fish", n * 2.0, 0));
    }

    /** Съели золотую рыбку. */
    public static void eat(EntityPlayer p) {
        int n = hearts(p);
        if (n >= MAX) {
            say(p, EnumChatFormatting.GRAY, "dre.chat.golden_fish_max", MAX);
            return;
        }
        data(p).setInteger(KEY, n + 1);
        apply(p);
        p.heal(2.0F);
        say(p, EnumChatFormatting.GOLD, "dre.chat.golden_fish", n + 1, MAX);
    }

    private static void say(EntityPlayer p, EnumChatFormatting c, String key, Object... args) {
        ChatComponentTranslation m = new ChatComponentTranslation(key, args);
        m.getChatStyle().setColor(c);
        p.addChatMessage(m);
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (!e.world.isRemote && e.entity instanceof EntityPlayer) apply((EntityPlayer) e.entity);
    }
}
