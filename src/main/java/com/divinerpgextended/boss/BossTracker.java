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

/** Counts the boss for all players who dealt damage to it, not just the one who finished it. */
public class BossTracker {

    private final Map<EntityLivingBase, Set<UUID>> attackers = new WeakHashMap<EntityLivingBase, Set<UUID>>();

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (event.entityLiving.worldObj.isRemote || Boss.of(event.entityLiving) == null && !(event.entityLiving instanceof net.divinerpg.entities.vanilla.EntityAyeraco)) return;
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
        if (boss == null && !(event.entityLiving instanceof net.divinerpg.entities.vanilla.EntityAyeraco)) return;

        Set<UUID> ids = attackers.remove(event.entityLiving);
        if (ids == null) ids = new HashSet<UUID>();
        Entity killer = event.source.getEntity();
        if (killer instanceof EntityPlayer) ids.add(((EntityPlayer) killer).getUniqueID());
        if (boss == Boss.TWINS) { // the brother is still alive - no victory; those who hit this brother are counted together with the last one
            EntityLivingBase brother = MortumArenaBosses.brother(event.entityLiving, 64);
            if (brother != null) {
                Set<UUID> rest = attackers.get(brother);
                if (rest == null) attackers.put(brother, rest = new HashSet<UUID>());
                rest.addAll(ids);
                return;
            }
        }

        // stage: in the Overworld, the Nether and the End, a player who has not reached the boss gets neither a share nor a victory; if nobody reached it - nothing drops
        boolean anyReady = ids.isEmpty();
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) o;
            if (!ids.contains(player.getUniqueID())) continue;
            if (ProgressSpawns.bossReady(player, event.entityLiving)) anyReady = true;
            else {
                String need = ProgressSpawns.bossNeedKey(event.entityLiving);
                ChatComponentTranslation early = new ChatComponentTranslation("dre.chat.boss_too_early", new ChatComponentTranslation("entity." + net.minecraft.entity.EntityList.getEntityString(event.entityLiving) + ".name"),
                    need == null ? "?" : new ChatComponentTranslation(need));
                early.getChatStyle().setColor(EnumChatFormatting.GRAY);
                player.addChatMessage(early);
            }
        }
        if (!anyReady && com.divinerpgextended.Config.lowRankNoDrops) ProgressSpawns.markNoDrops(event.entityLiving);
        if (boss == null) return; // Ayeraco: the victory is not recorded

        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) o;
            if (ids.contains(player.getUniqueID()) && !ProgressSpawns.bossReady(player, event.entityLiving)) continue;
            if (ids.contains(player.getUniqueID())) {
                com.divinerpgextended.trinket.AccessorySources.reward(player, com.divinerpgextended.trinket.AccessorySources.forBoss(boss), event.entityLiving);
                com.divinerpgextended.trinket.AccessorySources.reward(player, com.divinerpgextended.trinket.AccessorySources.extraForBoss(boss), event.entityLiving);
                BossDrops.reward(player, boss, event.entityLiving); // Tsunami Bow, summoning anchors
            }
            int rankWas = ProgressSpawns.rank(player);
            if (ids.contains(player.getUniqueID()) && BossProgress.grant(player, boss)) {
                ProgressSync.send(player);
                int rankNow = ProgressSpawns.rank(player);
                if (rankNow > rankWas) {
                    ChatComponentTranslation up = new ChatComponentTranslation("dre.chat.rank_up", rankNow);
                    up.getChatStyle().setColor(EnumChatFormatting.GOLD);
                    player.addChatMessage(up);
                }
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

    /** What changed in the world after the first victory: an atmospheric line and an ominous sound (if the boss has one). */
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
