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
 * Зарядка оружия: присесть и ПКМ ({@link com.divinerpgextended.client.AmmoLoadKey}). Клиент только просит, сервер сам
 * проверяет оружие в руке и патроны в инвентаре ({@link Ammo#load}) и играет звук перезарядки своего вида.
 */
public final class AmmoLoad {

    public static final String ARROWS = "divinerpgextended:reload_arrows", DUST = "divinerpgextended:reload_dust",
        GUN = "divinerpgextended:reload_gun";

    private AmmoLoad() {}

    public static void register() {
        ColdHandler.NET.registerMessage(Handler.class, Load.class, 15, Side.SERVER);
    }

    /** Зарядить оружие в руке. @return сколько вошло */
    public static int load(EntityPlayer p) {
        ItemStack w = p.getHeldItem();
        if (p.isDead || Ammo.of(w) == null) return 0;
        int n = Ammo.load(p);
        if (n > 0) p.worldObj.playSoundAtEntity(p, sound(w), 0.9F, 0.95F + p.getRNG().nextFloat() * 0.1F);
        return n;
    }

    /** Звук перезарядки: луки - стрелы в колчан, фазер и блиц - пыль, пушки и La Vekor - затвор. */
    public static String sound(ItemStack w) {
        if (RangedRoles.dust(w.getItem()) != null) return DUST;
        if (w.getItem() instanceof ItemBow || w.getItem() instanceof net.divinerpg.items.base.ItemModBow) return ARROWS;
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
            load(p);
            return null;
        }
    }
}
