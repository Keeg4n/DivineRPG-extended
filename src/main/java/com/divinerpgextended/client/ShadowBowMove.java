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
 * The Shadow Bow does not slow you while running: the game cuts the step by 5 times while you draw a bow (and the sprint breaks because of it). Our own movement
 * input multiplies the step by 5 in advance - after the cut it is the same as before, the sprint does not break. Only when the player is running and drawing
 * the Shadow Bow; the input is replaced only if it is vanilla (we do not touch other mods' input).
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
