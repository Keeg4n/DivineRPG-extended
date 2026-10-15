package com.divinerpgextended.boss;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

/**
 * /dreboss list [игрок] - убитые боссы; /dreboss grant <босс|all> [игрок] - засчитать; /dreboss revoke <босс|all>
 * [игрок] - снять. Для тестов в новом мире и для админов сервера (уровень оператора 2).
 */
public class CommandDreBoss extends CommandBase {

    @Override
    public String getCommandName() {
        return "dreboss";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dreboss <list|grant|revoke> [босс|all] [игрок]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) throw new WrongUsageException(getCommandUsage(sender));
        String op = args[0].toLowerCase();
        if (op.equals("list")) {
            EntityPlayerMP p = args.length >= 2 ? getPlayer(sender, args[1]) : getCommandSenderAsPlayer(sender);
            StringBuilder sb = new StringBuilder();
            for (Boss b : Boss.values()) {
                sb.append(BossProgress.has(p, b) ? EnumChatFormatting.GREEN : EnumChatFormatting.DARK_GRAY).append(b.name().toLowerCase())
                    .append(' ');
            }
            sender.addChatMessage(new ChatComponentTranslation("dre.cmd.boss_list", p.getCommandSenderName(), sb.toString().trim()));
            return;
        }
        if (!op.equals("grant") && !op.equals("revoke") || args.length < 2) throw new WrongUsageException(getCommandUsage(sender));
        EntityPlayerMP p = args.length >= 3 ? getPlayer(sender, args[2]) : getCommandSenderAsPlayer(sender);
        List<Boss> bosses = new ArrayList<Boss>();
        if (args[1].equalsIgnoreCase("all")) {
            for (Boss b : Boss.values()) bosses.add(b);
        } else {
            try {
                bosses.add(Boss.valueOf(args[1].toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new WrongUsageException("dre.cmd.boss_unknown", args[1]);
            }
        }
        boolean grant = op.equals("grant");
        for (Boss b : bosses) {
            if (grant) BossProgress.grant(p, b);
            else BossProgress.revoke(p, b);
        }
        ProgressSync.send(p);
        func_152373_a(sender, this, grant ? "dre.cmd.boss_granted" : "dre.cmd.boss_revoked", args[1].toLowerCase(),
            p.getCommandSenderName());
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "list", "grant", "revoke");
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            String[] names = new String[Boss.values().length + 1];
            names[0] = "all";
            for (Boss b : Boss.values()) names[b.ordinal() + 1] = b.name().toLowerCase();
            return getListOfStringsMatchingLastWord(args, names);
        }
        if (args.length == 3 || args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return getListOfStringsMatchingLastWord(args, net.minecraft.server.MinecraftServer.getServer().getAllUsernames());
        }
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int i) {
        return args.length > 0 && (args[0].equalsIgnoreCase("list") ? i == 1 : i == 2);
    }
}
