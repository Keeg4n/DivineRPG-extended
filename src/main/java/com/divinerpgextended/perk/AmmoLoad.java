package com.divinerpgextended.perk;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;

import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * Loading a weapon: sneak and right-click ({@link com.divinerpgextended.client.AmmoLoadKey}). The client only asks, the server
 * checks the weapon in hand and the cartridges in the inventory itself ({@link Ammo#load}) and plays the reload sound of its kind.
 */
public final class AmmoLoad {

    public static final String DUST = "divinerpgextended:reload_dust", GUN = "divinerpgextended:reload_gun";

    public static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("DRE-Ammo");

    private AmmoLoad() {}

    public static void register() {
        ColdHandler.NET.registerMessage(Handler.class, Load.class, 15, Side.SERVER);
    }

    /** Load the weapon in hand (throwable - gather into one stack). @return how much went in */
    public static int load(EntityPlayer p) {
        ItemStack w = p.getHeldItem();
        if (p.isDead) return 0;
        if (ThrowStack.is(w)) {
            int got = ThrowStack.gather(p);
            if (got > 0) p.worldObj.playSoundAtEntity(p, "random.pop", 0.6F, 0.8F + p.getRNG().nextFloat() * 0.2F);
            return got;
        }
        if (Ammo.of(w) == null) return 0;
        String snd = sound(w);
        int n = Ammo.load(p);
        if (n > 0 && snd != null) p.worldObj.playSoundAtEntity(p, snd, 0.9F, 0.95F + p.getRNG().nextFloat() * 0.1F);
        return n;
    }

    /** Reload sound: phaser and blitz - dust, cannons and La Vekor - bolt, bows - no sound (null). */
    public static String sound(ItemStack w) {
        if (RangedRoles.dust(w.getItem()) != null) return DUST;
        if (w.getItem() instanceof ItemBow) return null;
        return GUN;
    }

    public static class Load implements IMessage {

        @Override
        public void fromBytes(ByteBuf buf) {}

        @Override
        public void toBytes(ByteBuf buf) {}
    }

    public static class Handler implements IMessageHandler<Load, IMessage> {

        @Override
        public IMessage onMessage(Load msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            String before = Ammo.describe(p);
            int n = load(p);
            LOG.info("Loading (server, " + p.getCommandSenderName() + "): " + before + " - loaded " + n);
            return null;
        }
    }
}
