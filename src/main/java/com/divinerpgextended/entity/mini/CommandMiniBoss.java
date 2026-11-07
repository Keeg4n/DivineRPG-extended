package com.divinerpgextended.entity.mini;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

/** /dreminiboss - для проверки: поставить мини-босса этого мира рядом. */
public class CommandMiniBoss extends CommandBase {

    @Override
    public String getCommandName() {
        return "dreminiboss";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "dre.command.miniboss.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        if (MiniBosses.spawnNear(p) == null) sender.addChatMessage(new ChatComponentTranslation("dre.command.miniboss.none"));
    }
}
