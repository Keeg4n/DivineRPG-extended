package com.divinerpgextended.cold;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import net.divinerpg.utils.config.ConfigurationHelper;

/** /dreblizzard start|stop - для проверки: сразу начать (через предупреждение) или прекратить вьюгу в Iceika. */
public class CommandBlizzard extends CommandBase {

    @Override
    public String getCommandName() {
        return "dreblizzard";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "dre.command.blizzard.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1 || !("start".equals(args[0]) || "stop".equals(args[0]))) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        World w = DimensionManager.getWorld(ConfigurationHelper.iceika);
        if (w == null) {
            sender.addChatMessage(new ChatComponentTranslation("dre.command.blizzard.no_world"));
            return;
        }
        Blizzard b = Blizzard.get(w);
        if ("start".equals(args[0])) {
            b.phase = Blizzard.CALM; // следующая секунда - предупреждение, через 30 секунд вьюга
            b.timer = 20;
        } else {
            b.phase = Blizzard.STORM; // следующая секунда - конец вьюги
            b.timer = 20;
        }
        b.markDirty();
        sender.addChatMessage(new ChatComponentTranslation("dre.command.blizzard.ok"));
    }
}
