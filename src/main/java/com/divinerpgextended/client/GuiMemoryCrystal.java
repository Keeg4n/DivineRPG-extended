package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.arcana.ArcanaMerchant;
import com.divinerpgextended.arcana.MerchantMessages;
import com.divinerpgextended.cold.ColdHandler;

/** Окно Кристалла памяти: портреты торговцев Арканы, встреченных можно позвать. */
public class GuiMemoryCrystal extends GuiScreen {

    private static final int CARD_W = 104, CARD_H = 112, COLS = 3, GAP = 8;

    private final int met, unlocked;
    private final EntityLivingBase[] portraits = new EntityLivingBase[ArcanaMerchant.values().length];
    private int left, top;

    public GuiMemoryCrystal(int met, int unlocked) {
        this.met = met;
        this.unlocked = unlocked;
    }

    public static void open(int met, int unlocked) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiMemoryCrystal(met, unlocked));
    }

    private boolean isMet(ArcanaMerchant m) {
        return (met & 1 << m.ordinal()) != 0;
    }

    private boolean isUnlocked(ArcanaMerchant m) {
        return (unlocked & 1 << m.ordinal()) != 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        int rows = (ArcanaMerchant.values().length + COLS - 1) / COLS;
        left = (width - (COLS * CARD_W + (COLS - 1) * GAP)) / 2;
        top = (height - (rows * CARD_H + (rows - 1) * GAP)) / 2 + 8;
        buttonList.clear();
        for (ArcanaMerchant m : ArcanaMerchant.values()) {
            int i = m.ordinal(), x = left + (i % COLS) * (CARD_W + GAP), y = top + (i / COLS) * (CARD_H + GAP);
            GuiButton b = new GuiButton(i, x + 6, y + CARD_H - 24, CARD_W - 12, 20,
                StatCollector.translateToLocal("dre.merchants.summon"));
            b.enabled = isMet(m) && isUnlocked(m);
            buttonList.add(b);
            if (portraits[i] == null && mc.theWorld != null) portraits[i] = m.create(mc.theWorld);
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        ColdHandler.NET.sendToServer(new MerchantMessages.Summon(b.id));
        mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, StatCollector.translateToLocal("dre.merchants.title"), width / 2, top - 18, 0xE0C8FF);
        for (ArcanaMerchant m : ArcanaMerchant.values()) {
            int i = m.ordinal(), x = left + (i % COLS) * (CARD_W + GAP), y = top + (i / COLS) * (CARD_H + GAP);
            boolean known = isMet(m);
            drawRect(x, y, x + CARD_W, y + CARD_H, known ? 0xC0281A40 : 0xC0141418);
            drawRect(x, y, x + CARD_W, y + 1, known ? 0xFFB08CF0 : 0xFF404048);
            drawCenteredString(fontRendererObj, StatCollector.translateToLocal("dre.merchant." + m.key()), x + CARD_W / 2, y + 4,
                known ? 0xFFFFFF : 0x808088);
            EntityLivingBase e = portraits[i];
            if (e != null) {
                GL11.glColor4f(1, 1, 1, 1);
                GuiInventory.func_147046_a(x + CARD_W / 2, y + 74, 26, x + CARD_W / 2 - mx, y + 40 - my, e);
            }
            if (!known) {
                // модель рисуется ближе к экрану - затемнение и подсказку выносим поверх неё
                GL11.glPushMatrix();
                GL11.glTranslatef(0, 0, 300);
                drawRect(x + 1, y + 14, x + CARD_W - 1, y + CARD_H - 26, 0xC8000000);
                String hint = isUnlocked(m) ? StatCollector.translateToLocal("dre.merchants.unmet")
                    : StatCollector.translateToLocalFormatted("dre.merchants.locked",
                        StatCollector.translateToLocal(m.gate.langKey()));
                int ly = y + 40;
                for (Object line : fontRendererObj.listFormattedStringToWidth(hint, CARD_W - 10)) {
                    drawCenteredString(fontRendererObj, (String) line, x + CARD_W / 2, ly, 0xC8C8D0);
                    ly += 10;
                }
                GL11.glPopMatrix();
            }
        }
        super.drawScreen(mx, my, pt);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
