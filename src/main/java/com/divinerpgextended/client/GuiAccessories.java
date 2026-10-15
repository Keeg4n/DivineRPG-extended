package com.divinerpgextended.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.accessory.Accessories;
import com.divinerpgextended.accessory.ContainerAccessories;

/** Окно аксессуаров: рисуем фон сами (в стиле ванильного инвентаря), закрытые слоты - тёмные с замком. */
public class GuiAccessories extends GuiContainer {

    private final int unlocked;
    private final boolean readOnly;

    public GuiAccessories(EntityPlayer p, int unlocked, boolean readOnly) {
        super(new ContainerAccessories(p, new Accessories(p, unlocked, readOnly)));
        this.unlocked = unlocked;
        this.readOnly = readOnly;
        xSize = 176;
        ySize = 166;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        buttonList.add(new GuiButton(0, guiLeft + xSize - 60, guiTop - 22, 60, 20, StatCollector.translateToLocal("dre.accessories.back")));
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 0) {
            mc.thePlayer.closeScreen();
            mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
        }
    }

    private void panel(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, 0xFF000000);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        drawRect(x + 1, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
        drawRect(x + 1, y + 1, x + 2, y + h - 2, 0xFFFFFFFF);
        drawRect(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
        drawRect(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
    }

    private void slot(int x, int y, boolean locked) {
        drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        drawRect(x, y, x + 17, y + 17, 0xFFFFFFFF);
        drawRect(x, y, x + 16, y + 16, locked ? 0xFF3A3A3A : 0xFF8B8B8B);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float pt, int mx, int my) {
        GL11.glColor4f(1, 1, 1, 1);
        panel(guiLeft, guiTop, xSize, ySize);
        for (int i = 0; i < Accessories.SIZE; i++) {
            slot(guiLeft + ContainerAccessories.SLOT_X + i * 18, guiTop + ContainerAccessories.SLOT_Y, i >= unlocked);
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(guiLeft + 8 + col * 18, guiTop + 84 + row * 18, false);
        for (int col = 0; col < 9; col++) slot(guiLeft + 8 + col * 18, guiTop + 142, false);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        fontRendererObj.drawString(StatCollector.translateToLocal(readOnly ? "dre.accessories.view" : "dre.accessories.title"), 8, 6, 0x404040);
        String count = StatCollector.translateToLocalFormatted("dre.accessories.count", unlocked, Accessories.SIZE);
        fontRendererObj.drawString(count, xSize - 8 - fontRendererObj.getStringWidth(count), 6, 0x606060);
        for (int i = unlocked; i < Accessories.SIZE; i++) {
            fontRendererObj.drawString("x", ContainerAccessories.SLOT_X + i * 18 + 5, ContainerAccessories.SLOT_Y + 4, 0x909090);
        }
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 8, 72, 0x404040);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        super.drawScreen(mx, my, pt);
        // подсказка на закрытом слоте: какой босс его откроет
        for (int i = unlocked; i < Accessories.SIZE; i++) {
            int x = guiLeft + ContainerAccessories.SLOT_X + i * 18, y = guiTop + ContainerAccessories.SLOT_Y;
            if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                List<String> tip = new ArrayList<String>();
                tip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.accessories.locked"));
                int need = i - Accessories.BASE;
                if (need >= 0 && need < Accessories.UNLOCKS.length) {
                    tip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal(Accessories.UNLOCKS[need].langKey()));
                }
                drawHoveringText(tip, mx, my, fontRendererObj);
            }
        }
    }
}
