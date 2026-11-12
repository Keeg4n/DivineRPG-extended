package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovementInputFromOptions;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Теневой лук на бегу не замедляет: игра режет шаг в 5 раз, пока натягиваешь лук (и бег при этом обрывается). Свой ввод
 * движения заранее умножает шаг на 5 - после урезания он прежний, бег не обрывается. Только когда игрок бежит и натягивает
 * Теневой лук; ввод заменяется, только если он ванильный (чужой ввод других модов не трогаем).
 */
@SideOnly(Side.CLIENT)
public class ShadowBowMove {

    public static class Input extends MovementInputFromOptions {

        public Input(GameSettings s) {
            super(s);
        }

        @Override
        public void updatePlayerMoveState() {
            super.updatePlayerMoveState();
            EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
            if (p == null || !p.isUsingItem() || !p.isSprinting() || p.isRiding()) return;
            ItemStack u = p.getItemInUse();
            if (u == null || u.getItem() != net.divinerpg.utils.items.VanillaItemsWeapons.shadowBow) return;
            moveStrafe *= 5F;
            moveForward *= 5F;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p != null && p.movementInput != null && p.movementInput.getClass() == MovementInputFromOptions.class) p.movementInput = new Input(mc.gameSettings);
    }
}
