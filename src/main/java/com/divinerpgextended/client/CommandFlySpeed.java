package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Клиентская команда /dreflyspeed N: скорость полёта в N раз выше обычной (1-10), без числа - обычная. Полёт
 * считается на клиенте, поэтому работает и на сервере; сбрасывается при смене режима игры или мира.
 */
@SideOnly(Side.CLIENT)
public class CommandFlySpeed extends CommandBase {

    private static final float BASE = 0.05F;

    @Override
    public String getCommandName() {
        return "dreflyspeed";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "dre.command.flyspeed.usage";
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
        if (args.length > 1) throw new WrongUsageException(getCommandUsage(sender));
        double mult = args.length == 0 ? 1.0D : parseDoubleBounded(sender, args[0], 0.1D, 10.0D);
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        player.capabilities.setFlySpeed((float) (BASE * mult));
        sender.addChatMessage(new ChatComponentTranslation("dre.command.flyspeed.set", String.format("%.1f", mult)));
    }
}
