package com.divinerpgextended.debug;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumChatFormatting;

/** /dredebug - отладка для себя: подробности в чат и журнал CSV в logs/dre-debug. */
public class CommandDreDebug extends CommandBase {

    @Override
    public String getCommandName() {
        return "dredebug";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dredebug [on|off|status] | /dredebug <chat|file|mobs> <on|off>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    private static boolean flag(String v) {
        if ("on".equalsIgnoreCase(v)) return true;
        if ("off".equalsIgnoreCase(v)) return false;
        throw new WrongUsageException("/dredebug <chat|file|mobs> <on|off>");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        String op = args.length == 0 ? "status" : args[0].toLowerCase();
        Debug.Flags f = Debug.ON.get(p.getUniqueID());
        if (op.equals("on")) {
            if (f == null) Debug.ON.put(p.getUniqueID(), f = new Debug.Flags());
            Debug.sendState(p, f.file);
            DebugWatch.tap(p);
            Debug.file(p, "debug", "on", "");
            status(p, f);
            return;
        }
        if (op.equals("off")) {
            if (f != null) Debug.file(p, "debug", "off", "");
            Debug.ON.remove(p.getUniqueID());
            Debug.sendState(p, false);
            DebugEvents.cleanup();
            Debug.chat(p, EnumChatFormatting.GRAY, "Отладка выключена.");
            return;
        }
        if (op.equals("status")) {
            status(p, f);
            return;
        }
        if (args.length < 2) throw new WrongUsageException(getCommandUsage(sender));
        if (f == null) Debug.ON.put(p.getUniqueID(), f = new Debug.Flags());
        boolean v = flag(args[1]);
        if (op.equals("chat")) f.chat = v;
        else if (op.equals("file")) f.file = v;
        else if (op.equals("mobs")) f.mobs = v;
        else throw new WrongUsageException(getCommandUsage(sender));
        Debug.sendState(p, f.file);
        status(p, f);
    }

    private static void status(EntityPlayerMP p, Debug.Flags f) {
        if (f == null) {
            Debug.chat(p, EnumChatFormatting.GRAY, "Отладка выключена. /dredebug on - включить.");
            return;
        }
        Debug.chat(p, EnumChatFormatting.GOLD, "Отладка: чат " + (f.chat ? "вкл" : "выкл") + ", файл " + (f.file ? "вкл" : "выкл")
            + ", бои мобов рядом " + (f.mobs ? "вкл" : "выкл"));
        if (f.file) Debug.chat(p, EnumChatFormatting.GRAY, "Файл: " + Debug.log().file().getAbsolutePath()
            + " (клавиши пишутся рядом, в файл -client.csv)");
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "on", "off", "status", "chat", "file", "mobs");
        if (args.length == 2) return getListOfStringsMatchingLastWord(args, "on", "off");
        return null;
    }
}
