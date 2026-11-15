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

/**
 * A small star button for the old accessory slots in the lower right corner of the frame with the player model (the regular inventory
 * and the inventory tab in creative). Visible only while something is left in the old slots
 * ({@link com.divinerpgextended.accessory.Accessories#clientLeftovers}).
 */
public class AccessoryButton {

    private static final int ID = 2401, SIZE = 10;
    private static final ResourceLocation STAR = new ResourceLocation("divinerpgextended", "textures/gui/accessories_star.png");

    /** The button draws only the vanilla button frame and a star on top. */
    private static class StarButton extends GuiButton {

        StarButton(int x, int y) {
            super(ID, x, y, SIZE, SIZE, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            // position by the window right now: NEI moves the inventory (potion effects) already during rendering - the button jumped
            if (mc.currentScreen != null) place(this, mc.currentScreen);
            if (!visible) return;
            field_146123_n = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            mc.getTextureManager().bindTexture(buttonTextures);
            GL11.glColor4f(1, 1, 1, 1);
            int v = 46 + getHoverState(field_146123_n) * 20;
            // the vanilla button is 200x20 - take the left and right edges to get a small square one
            drawTexturedModalRect(xPosition, yPosition, 0, v, width / 2, height);
            drawTexturedModalRect(xPosition + width / 2, yPosition, 200 - width / 2, v, width / 2, height);
            drawTexturedModalRect(xPosition, yPosition + height - 2, 0, v + 18, width / 2, 2);
            drawTexturedModalRect(xPosition + width / 2, yPosition + height - 2, 200 - width / 2, v + 18, width / 2, 2);
            mc.getTextureManager().bindTexture(STAR);
            GL11.glEnable(GL11.GL_BLEND);
            Gui.func_146110_a(xPosition + 1, yPosition + 1, 0, 0, SIZE - 2, SIZE - 2, SIZE - 2, SIZE - 2);
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    private StarButton button;

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onInit(GuiScreenEvent.InitGuiEvent.Post e) {
        boolean inv = e.gui instanceof GuiInventory, creative = e.gui instanceof GuiContainerCreative;
        if (!inv && !creative) return;
        button = new StarButton(0, 0);
        place(button, e.gui);
        e.buttonList.add(button);
    }

    /**
     * By the real window coordinates: with potion effects the vanilla inventory shifts to the right, and NEI after
     * initialization puts it back (it moves the effects to its own panel) - so it is recalculated before every draw.
     */
    private static void place(StarButton button, net.minecraft.client.gui.GuiScreen gui) {
        if (!com.divinerpgextended.accessory.Accessories.clientLeftovers || !(gui instanceof GuiInventory || gui instanceof GuiContainerCreative)) {
            button.visible = false;
            return;
        }
        net.minecraft.client.gui.inventory.GuiContainer c = (net.minecraft.client.gui.inventory.GuiContainer) gui;
        int left = cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.inventory.GuiContainer.class,
            c, "guiLeft", "field_147003_i");
        int top = cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.inventory.GuiContainer.class,
            c, "guiTop", "field_147009_r");
        // lower right corner of the frame with the model: in the regular inventory the frame is 26..77 x 8..78, in creative 27..61 x 5..50
        if (gui instanceof GuiInventory) {
            button.visible = true;
            button.xPosition = left + 77 - SIZE - 1;
            button.yPosition = top + 78 - SIZE - 1;
        } else {
            button.visible = ((GuiContainerCreative) gui).func_147056_g() == net.minecraft.creativetab.CreativeTabs.tabInventory.getTabIndex();
            button.xPosition = left + 61 - SIZE - 1;
            button.yPosition = top + 50 - SIZE - 1;
        }
    }

    @SubscribeEvent
    public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre e) {
        if (button != null && (e.gui instanceof GuiInventory || e.gui instanceof GuiContainerCreative)) place(button, e.gui);
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre e) {
        if (e.button.id != ID || !(e.gui instanceof GuiInventory || e.gui instanceof GuiContainerCreative)) return;
        ColdHandler.NET.sendToServer(new AccessoryMessages.Open());
        e.setCanceled(true);
    }

    /** The "Accessories" tooltip on hover. */
    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (button == null || !button.visible || !(e.gui instanceof GuiInventory || e.gui instanceof GuiContainerCreative)) return;
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
