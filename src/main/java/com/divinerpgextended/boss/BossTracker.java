package com.divinerpgextended.boss;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Засчитывает босса всем игрокам, которые нанесли ему урон, а не только добившему. */
public class BossTracker {

    private final Map<EntityLivingBase, Set<UUID>> attackers = new WeakHashMap<EntityLivingBase, Set<UUID>>();

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (event.entityLiving.worldObj.isRemote || Boss.of(event.entityLiving) == null) return;
        Entity source = event.source.getEntity();
        if (source instanceof EntityPlayer) {
            Set<UUID> set = attackers.get(event.entityLiving);
            if (set == null) {
                set = new HashSet<UUID>();
                attackers.put(event.entityLiving, set);
            }
            set.add(((EntityPlayer) source).getUniqueID());
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.entityLiving.worldObj.isRemote) return;
        Boss boss = Boss.of(event.entityLiving);
        if (boss == null) return;

        Set<UUID> ids = attackers.remove(event.entityLiving);
        if (ids == null) ids = new HashSet<UUID>();
        Entity killer = event.source.getEntity();
        if (killer instanceof EntityPlayer) ids.add(((EntityPlayer) killer).getUniqueID());
        if (boss == Boss.TWINS) { // брат ещё жив - победы нет; бившие этого брата засчитаются вместе с последним
            EntityLivingBase brother = MortumArenaBosses.brother(event.entityLiving, 64);
            if (brother != null) {
                Set<UUID> rest = attackers.get(brother);
                if (rest == null) attackers.put(brother, rest = new HashSet<UUID>());
                rest.addAll(ids);
                return;
            }
        }

        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) o;
            if (ids.contains(player.getUniqueID())) {
                com.divinerpgextended.trinket.AccessorySources.reward(player, com.divinerpgextended.trinket.AccessorySources.forBoss(boss), event.entityLiving);
                com.divinerpgextended.trinket.AccessorySources.reward(player, com.divinerpgextended.trinket.AccessorySources.extraForBoss(boss), event.entityLiving);
            }
            if (ids.contains(player.getUniqueID()) && boss.requires() != null && !BossProgress.has(player, boss.requires())) {
                ChatComponentTranslation msg = new ChatComponentTranslation("dre.chat.boss_order", new ChatComponentTranslation(boss.langKey()),
                    new ChatComponentTranslation(boss.requires().langKey()));
                msg.getChatStyle().setColor(EnumChatFormatting.GRAY);
                player.addChatMessage(msg);
                continue;
            }
            if (ids.contains(player.getUniqueID()) && BossProgress.grant(player, boss)) {
                ProgressSync.send(player);
                com.divinerpgextended.DreAchievements.give(player, com.divinerpgextended.DreAchievements.forBoss(boss));
                ChatComponentTranslation msg = new ChatComponentTranslation(
                    "dre.chat.boss_unlocked",
                    new ChatComponentTranslation(boss.langKey()));
                msg.getChatStyle()
                    .setColor(EnumChatFormatting.GOLD);
                player.addChatMessage(msg);
                lore(player, boss);
            }
        }
    }

    /** Что изменилось в мире после первой победы: атмосферная строка и мрачный звук (если для босса она есть). */
    private static void lore(EntityPlayerMP player, Boss boss) {
        String key = "dre.lore." + boss.name().toLowerCase();
        if (!net.minecraft.util.StatCollector.canTranslate(key)) return;
        ChatComponentTranslation lore = new ChatComponentTranslation(key);
        lore.getChatStyle().setColor(EnumChatFormatting.DARK_PURPLE).setItalic(true);
        player.addChatMessage(lore);
        player.playerNetServerHandler.sendPacket(new net.minecraft.network.play.server.S29PacketSoundEffect(
            boss == Boss.ANCIENT_ENTITY ? "mob.enderdragon.growl" : "ambient.cave.cave", player.posX, player.posY,
            player.posZ, 0.8F, 0.7F));
    }
}
