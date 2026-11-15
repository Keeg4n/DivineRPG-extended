package com.divinerpgextended.cold;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import net.divinerpg.utils.config.ConfigurationHelper;

import com.divinerpgextended.world.twilight.WorldWeather;

/** /dreblizzard start|stop - for testing: an Iceika blizzard, a Wildwood downpour and an Apalachia rockfall at once. Start (through the warning) or stop a blizzard in Iceika. */
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
        boolean start = "start".equals(args[0]);
        MinecraftServer server = MinecraftServer.getServer();
        // weather is stored in the world save: a world without players is loaded, as in a teleport
        Blizzard b = Blizzard.get(server.worldServerForDimension(ConfigurationHelper.iceika));
        // start: from a lull - the next second is the warning, the event comes after 30 seconds; one already in progress is not touched.
        // stop: the one in progress (or the warning) ends on the next second; a lull is not touched, so there is no extra "ended"
        if (start ? b.phase == Blizzard.CALM : b.phase != Blizzard.CALM) {
            b.phase = start ? Blizzard.CALM : Blizzard.STORM;
            b.timer = 20;
            b.markDirty();
        }
        for (int dim : new int[] { ConfigurationHelper.wildwood, ConfigurationHelper.apalachia }) {
            WorldWeather w = WorldWeather.get(server.worldServerForDimension(dim));
            if (start ? w.phase != WorldWeather.CALM : w.phase == WorldWeather.CALM) continue;
            w.phase = start ? WorldWeather.CALM : WorldWeather.ACTIVE;
            w.timer = 20;
            w.markDirty();
        }
        sender.addChatMessage(new ChatComponentTranslation(start ? "dre.command.blizzard.started" : "dre.command.blizzard.stopped"));
    }
}
