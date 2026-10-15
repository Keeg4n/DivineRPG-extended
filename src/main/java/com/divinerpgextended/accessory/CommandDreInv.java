package com.divinerpgextended.accessory;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import com.divinerpgextended.DivineRPGExtended;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.station.GuiHandler;
import com.mojang.authlib.GameProfile;

/**
 * /dreinv &lt;игрок&gt; - модерация смотрит аксессуары игрока (как /openinv). Игрок в сети - можно и править (это тот же
 * объект, что у игрока, дюпа нет); не в сети - только просмотр из его файла playerdata (писать в файл, пока игрок
 * может зайти, опасно). Уровень оператора 2.
 */
public class CommandDreInv extends CommandBase {

    private static final Map<UUID, Accessories> PENDING = new HashMap<UUID, Accessories>();

    /** Что открыть админу (забирается при создании окна). */
    public static Accessories takePending(EntityPlayer p) {
        return PENDING.remove(p.getUniqueID());
    }

    @Override
    public String getCommandName() {
        return "dreinv";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dreinv <игрок>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) throw new WrongUsageException(getCommandUsage(sender));
        EntityPlayerMP admin = getCommandSenderAsPlayer(sender);
        MinecraftServer server = MinecraftServer.getServer();
        EntityPlayerMP target = server.getConfigurationManager().func_152612_a(args[0]);
        Accessories acc;
        String name;
        if (target != null) {
            acc = Accessories.of(target);
            name = target.getCommandSenderName();
        } else {
            acc = offline(server, args[0]);
            name = args[0];
        }
        PENDING.put(admin.getUniqueID(), acc);
        admin.openGui(DivineRPGExtended.instance, GuiHandler.ACCESSORIES, admin.worldObj, acc.unlocked, acc.readOnly ? 1 : 0, 0);
        admin.addChatMessage(new ChatComponentTranslation(acc.readOnly ? "dre.cmd.inv_offline" : "dre.cmd.inv_online", name));
    }

    /** Снимок аксессуаров игрока не в сети - из world/playerdata/UUID.dat. */
    private static Accessories offline(MinecraftServer server, String name) {
        GameProfile gp = server.func_152358_ax().func_152655_a(name);
        if (gp == null || gp.getId() == null) throw new CommandException("dre.cmd.inv_unknown", name);
        File dir = new File(server.worldServers[0].getSaveHandler().getWorldDirectory(), "playerdata");
        File f = new File(dir, gp.getId().toString() + ".dat");
        if (!f.isFile()) throw new CommandException("dre.cmd.inv_unknown", name);
        try {
            FileInputStream in = new FileInputStream(f);
            NBTTagCompound root;
            try {
                root = CompressedStreamTools.readCompressed(in);
            } finally {
                in.close();
            }
            NBTTagCompound ps = root.getCompoundTag("ForgeData").getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            NBTTagList list = ps.getTagList(Accessories.TAG, 10);
            int mask = ps.getInteger("divinerpgextended_bosses"), unlocked = Accessories.BASE;
            for (Boss b : Accessories.UNLOCKS) if ((mask & b.bit()) != 0) unlocked++;
            return Accessories.snapshot(list, unlocked);
        } catch (Exception e) {
            throw new CommandException("dre.cmd.inv_unknown", name);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames()) : null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int i) {
        return i == 0;
    }
}
