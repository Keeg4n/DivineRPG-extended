package com.divinerpgextended.cold;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import net.divinerpg.utils.config.ConfigurationHelper;

import com.divinerpgextended.world.twilight.WorldWeather;

/** /dreblizzard start|stop - для проверки: метель Айсики, ливень Дикого леса и камнепад Апалачии сразу. Начать (через предупреждение) или прекратить вьюгу в Iceika. */
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
        // погода хранится в сохранении мира: мир без игроков подгружается, как при телепорте
        Blizzard b = Blizzard.get(server.worldServerForDimension(ConfigurationHelper.iceika));
        // старт: из затишья - следующая секунда предупреждение, через 30 секунд событие; уже идущее не трогаем.
        // стоп: идущее (или предупреждение) кончается на следующей секунде; затишье не трогаем, чтобы не было лишнего "закончилось"
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
