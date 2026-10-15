package com.divinerpgextended.accessory;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;

import com.divinerpgextended.DivineRPGExtended;
import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.station.GuiHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/** Клиент просит открыть окно аксессуаров (кнопка в инвентаре). Число открытых слотов уходит клиенту в координате x. */
public final class AccessoryMessages {

    private AccessoryMessages() {}

    public static void register() {
        ColdHandler.NET.registerMessage(OpenHandler.class, Open.class, 4, Side.SERVER);
    }

    public static class Open implements IMessage {

        @Override
        public void fromBytes(ByteBuf buf) {}

        @Override
        public void toBytes(ByteBuf buf) {}
    }

    public static class OpenHandler implements IMessageHandler<Open, IMessage> {

        @Override
        public IMessage onMessage(Open msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            p.openGui(DivineRPGExtended.instance, GuiHandler.ACCESSORIES, p.worldObj, Accessories.unlocked(p), 0, 0);
            return null;
        }
    }
}
