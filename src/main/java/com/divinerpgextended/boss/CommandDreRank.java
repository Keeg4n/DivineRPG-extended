package com.divinerpgextended.boss;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

/** /drerank - ранги Divine (0-3, от них зависят мобы Divine рядом) у всех игроков онлайн и какой босс следующий. Доступна всем. */
public class CommandDreRank extends CommandBase {

    /** Босс, открывающий следующий ранг (null - ранг наивысший). */
    public static Boss next(int rank) {
        switch (rank) {
            case 0: return Boss.CARCINUS;
            case 1: return Boss.LICH_KING;
            case 2: return Boss.ANCIENT_ENTITY;
            default: return null;
        }
    }

    @Override
    public String getCommandName() {
        return "drerank";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "dre.command.rank.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        ChatComponentTranslation head = new ChatComponentTranslation("dre.command.rank.header");
        head.getChatStyle().setColor(EnumChatFormatting.GOLD);
        sender.addChatMessage(head);
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            int r = ProgressSpawns.rank(p);
            Boss b = next(r);
            sender.addChatMessage(b == null ? new ChatComponentTranslation("dre.command.rank.line_max", p.getCommandSenderName(), r)
                : new ChatComponentTranslation("dre.command.rank.line", p.getCommandSenderName(), r, new ChatComponentTranslation(b.langKey())));
        }
    }
}
