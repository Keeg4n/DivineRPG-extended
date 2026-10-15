package com.divinerpgextended.client;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.accessory.AccessoryMessages;
import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Маленькая кнопка-звёздочка "Аксессуары" у правого края окна инвентаря (обычного и творческого). */
public class AccessoryButton {

    private static final int ID = 2401, SIZE = 18;
    private static final ResourceLocation STAR = new ResourceLocation("divinerpgextended", "textures/gui/accessories_star.png");

    /** Кнопка рисует только рамку ванильной кнопки и звезду поверх. */
    private static class StarButton extends GuiButton {

        StarButton(int x, int y) {
            super(ID, x, y, SIZE, SIZE, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) return;
            field_146123_n = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            mc.getTextureManager().bindTexture(buttonTextures);
            GL11.glColor4f(1, 1, 1, 1);
            int v = 46 + getHoverState(field_146123_n) * 20;
            // ванильная кнопка 200x20 - берём левый и правый края, чтобы получилась маленькая квадратная
            drawTexturedModalRect(xPosition, yPosition, 0, v, width / 2, height);
            drawTexturedModalRect(xPosition + width / 2, yPosition, 200 - width / 2, v, width / 2, height);
            drawTexturedModalRect(xPosition, yPosition + height - 2, 0, v + 18, width / 2, 2);
            drawTexturedModalRect(xPosition + width / 2, yPosition + height - 2, 200 - width / 2, v + 18, width / 2, 2);
            mc.getTextureManager().bindTexture(STAR);
            GL11.glEnable(GL11.GL_BLEND);
            Gui.func_146110_a(xPosition + 1, yPosition + 1, 0, 0, 16, 16, 16, 16);
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    private StarButton button;

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onInit(GuiScreenEvent.InitGuiEvent.Post e) {
        boolean inv = e.gui instanceof GuiInventory, creative = e.gui instanceof GuiContainerCreative;
        if (!inv && !creative) return;
        // настоящие координаты окна (с эффектами зелий ванильный инвентарь сдвигается вправо)
        int left = cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.inventory.GuiContainer.class,
            (net.minecraft.client.gui.inventory.GuiContainer) e.gui, "guiLeft", "field_147003_i");
        int top = cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.inventory.GuiContainer.class,
            (net.minecraft.client.gui.inventory.GuiContainer) e.gui, "guiTop", "field_147009_r");
        // обычный инвентарь: внутри окна, под слотом результата крафта (там пусто и NEI не перекрывает);
        // творческий: свободного места внутри нет - слева снаружи
        button = inv ? new StarButton(left + 143, top + 59) : new StarButton(left - SIZE - 2, top + 4);
        e.buttonList.add(button);
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre e) {
        if (e.button.id != ID || !(e.gui instanceof GuiInventory || e.gui instanceof GuiContainerCreative)) return;
        ColdHandler.NET.sendToServer(new AccessoryMessages.Open());
        e.setCanceled(true);
    }

    /** Подсказка "Аксессуары" при наведении. */
    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (button == null || !(e.gui instanceof GuiInventory || e.gui instanceof GuiContainerCreative)) return;
        if (e.mouseX < button.xPosition || e.mouseY < button.yPosition || e.mouseX >= button.xPosition + SIZE
            || e.mouseY >= button.yPosition + SIZE) return;
        String text = StatCollector.translateToLocal("dre.accessories.title");
        Minecraft mc = Minecraft.getMinecraft();
        int x = e.mouseX + 12, y = e.mouseY - 12, tw = mc.fontRenderer.getStringWidth(text);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        Gui.drawRect(x - 3, y - 3, x + tw + 3, y + 11, 0xF0100010);
        Gui.drawRect(x - 3, y - 3, x + tw + 3, y - 2, 0x505000FF);
        mc.fontRenderer.drawStringWithShadow(text, x, y, 0xFFFFFF);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
