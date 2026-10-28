package com.divinerpgextended.client;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.magic.ContainerArcaneBrewer;
import com.divinerpgextended.magic.TileArcaneBrewer;

/** Окно арканной стойки: ванильное окно стойки в лиловых тонах, слева слот арканиума и запас варок. */
public class GuiArcaneBrewer extends GuiContainer {

    private static final ResourceLocation TEX = new ResourceLocation("textures/gui/container/brewing_stand.png");
    private static final int[] BUBBLES = { 29, 24, 20, 16, 11, 6, 0 };
    private final TileArcaneBrewer tile;

    public GuiArcaneBrewer(InventoryPlayer inv, TileArcaneBrewer t) {
        super(new ContainerArcaneBrewer(inv, t));
        tile = t;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        String name = StatCollector.translateToLocal("tile.dre.arcaneBrewer.name");
        fontRendererObj.drawString(name, xSize / 2 - fontRendererObj.getStringWidth(name) / 2, 6, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 8, ySize - 94, 0x404040);
        fontRendererObj.drawString("x" + tile.charges, 17, 37, 0x7A3CFF);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float pt, int mx, int my) {
        GL11.glColor4f(0.85F, 0.75F, 1F, 1F);
        mc.getTextureManager().bindTexture(TEX);
        int k = (width - xSize) / 2, l = (height - ySize) / 2;
        drawTexturedModalRect(k, l, 0, 0, xSize, ySize);
        // слот арканиума (в ванильном окне его нет) - рамкой
        drawRect(k + 16, l + 16, k + 34, l + 34, 0xFF373737);
        drawRect(k + 17, l + 17, k + 34, l + 34, 0xFFFFFFFF);
        drawRect(k + 17, l + 17, k + 33, l + 33, 0xFF8B8B8B);
        GL11.glColor4f(0.85F, 0.75F, 1F, 1F);
        mc.getTextureManager().bindTexture(TEX);
        if (tile.brewTime > 0) {
            int arrow = (int) (28F * (1F - tile.brewTime / (float) TileArcaneBrewer.BREW_TICKS));
            if (arrow > 0) drawTexturedModalRect(k + 97, l + 16, 176, 0, 9, arrow);
            int b = BUBBLES[tile.brewTime / 2 % 7];
            if (b > 0) drawTexturedModalRect(k + 65, l + 14 + 29 - b, 185, 29 - b, 12, b);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }
}
