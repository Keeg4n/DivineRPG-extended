package com.divinerpgextended.world.twilight;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/** /dreweather rain|quake start|stop - для проверки: начать (через предупреждение) или прекратить ливень в Диком лесу, камнепад в Апалачии. */
public class CommandWorldWeather extends CommandBase {

    @Override
    public String getCommandName() {
        return "dreweather";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "dre.command.weather.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 2 || !("rain".equals(args[0]) || "quake".equals(args[0])) || !("start".equals(args[1]) || "stop".equals(args[1])))
            throw new WrongUsageException(getCommandUsage(sender));
        World w = DimensionManager.getWorld("rain".equals(args[0]) ? ConfigurationHelper.wildwood : ConfigurationHelper.apalachia);
        if (w == null) {
            sender.addChatMessage(new ChatComponentTranslation("dre.command.weather.no_world"));
            return;
        }
        WorldWeather b = WorldWeather.get(w);
        b.phase = "start".equals(args[1]) ? WorldWeather.CALM : WorldWeather.ACTIVE; // на следующей секунде - предупреждение или конец
        b.timer = 20;
        b.markDirty();
        sender.addChatMessage(new ChatComponentTranslation("dre.command.blizzard.ok"));
    }
}
