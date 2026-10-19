package com.divinerpgextended.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.bestiary.Bestiary;
import com.divinerpgextended.bestiary.Bestiary.Chapter;

/**
 * Книга бестиария: слева главы (миры) с прогрессом, справа карточки мобов главы - живая модель, имя, сколько убито из
 * нужного. Не встреченный моб - тёмная карточка с "???". Вверху - состояние награды за главу.
 */
public class GuiBestiary extends GuiScreen {

    private static final int W = 360, H = 232, TAB_W = 104, CARD_W = 80, CARD_H = 84, COLS = 3, ROWS = 2, PER = COLS * ROWS;
    private static final Map<String, EntityLivingBase> MODELS = new HashMap<String, EntityLivingBase>();
    private static Chapter last = Chapter.OVERWORLD;

    private final NBTTagCompound kills;
    private final int claimed;
    private Chapter chapter = last;
    private int page;
    private int left, top;

    public GuiBestiary(NBTTagCompound kills, int claimed) {
        this.kills = kills;
        this.claimed = claimed;
    }

    public static void open(NBTTagCompound kills, int claimed) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiBestiary(kills, claimed));
    }

    @Override
    public void initGui() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    private List<String> mobs() {
        return Bestiary.mobs(chapter);
    }

    private int pages() {
        return Math.max(1, (mobs().size() + PER - 1) / PER);
    }

    private static String mobName(String id) {
        String k = "entity." + id + ".name", t = StatCollector.translateToLocal(k);
        if (!t.equals(k)) return t;
        return id.startsWith("DRPG") ? id.substring(4) : id;
    }

    private EntityLivingBase model(String id) {
        if (MODELS.containsKey(id)) return MODELS.get(id);
        EntityLivingBase e = null;
        try {
            Entity en = EntityList.createEntityByName(id, mc.theWorld);
            if (en instanceof EntityLivingBase) e = (EntityLivingBase) en;
        } catch (Throwable ignored) {
            // моб не умеет жить вне мира - карточка без модели
        }
        MODELS.put(id, e);
        return e;
    }

    @Override
    protected void mouseClicked(int mx, int my, int b) {
        super.mouseClicked(mx, my, b);
        int ty = top + 26;
        for (Chapter c : Chapter.values()) {
            if (mx >= left + 6 && mx < left + TAB_W - 4 && my >= ty && my < ty + 17 && !Bestiary.mobs(c).isEmpty()) {
                chapter = last = c;
                page = 0;
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
                return;
            }
            ty += 18;
        }
        int ax = left + W - 30, ay = top + H - 20;
        if (my >= ay && my < ay + 14) {
            if (mx >= ax - 30 && mx < ax - 12 && page > 0) page--;
            if (mx >= ax && mx < ax + 18 && page < pages() - 1) page++;
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawRect(left, top, left + W, top + H, 0xF0201810);
        drawRect(left + 2, top + 2, left + W - 2, top + H - 2, 0xF03A2C1C);
        drawRect(left + TAB_W, top + 6, left + TAB_W + 1, top + H - 6, 0xFF6A5236);
        drawCenteredString(fontRendererObj, StatCollector.translateToLocal("dre.bestiary.title"), left + TAB_W / 2, top + 9, 0xF0D8A0);
        // главы
        int ty = top + 26;
        for (Chapter c : Chapter.values()) {
            int total = Bestiary.mobs(c).size(), done = Bestiary.studied(kills, c);
            boolean sel = c == chapter, full = total > 0 && done == total;
            if (sel) drawRect(left + 5, ty - 1, left + TAB_W - 3, ty + 15, 0x80A07840);
            String name = StatCollector.translateToLocal("dre.bestiary.chapter." + c.key());
            int col = total == 0 ? 0x605848 : full ? 0x80E080 : 0xE8DCC0;
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(name, TAB_W - 40), left + 9, ty + 3, col);
            String cnt = done + "/" + total;
            fontRendererObj.drawString(cnt, left + TAB_W - 7 - fontRendererObj.getStringWidth(cnt), ty + 3, col);
            ty += 18;
        }
        // шапка главы
        List<String> mobs = mobs();
        int done = Bestiary.studied(kills, chapter), rx = left + TAB_W + 10;
        fontRendererObj.drawString(StatCollector.translateToLocal("dre.bestiary.chapter." + chapter.key()) + "  " + done + "/" + mobs.size(), rx, top + 9, 0xF0D8A0);
        boolean got = (claimed & 1 << chapter.ordinal()) != 0, full = Bestiary.complete(kills, chapter);
        String reward = got ? StatCollector.translateToLocal("dre.bestiary.state_got")
            : full ? StatCollector.translateToLocal("dre.bestiary.state_ready") : StatCollector.translateToLocal("dre.bestiary.state_locked");
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(reward, W - TAB_W - 20), rx, top + 20, got ? 0x80E080 : full ? 0xFFD040 : 0xA09078);
        // карточки
        int cx0 = left + TAB_W + 12, cy0 = top + 34;
        for (int i = 0; i < PER; i++) {
            int idx = page * PER + i;
            if (idx >= mobs.size()) break;
            String id = mobs.get(idx);
            int x = cx0 + (i % COLS) * (CARD_W + 4), y = cy0 + (i / COLS) * (CARD_H + 4);
            int k = kills.getInteger(id), n = Bestiary.need(id);
            boolean seen = k > 0, ok = k >= n;
            drawRect(x, y, x + CARD_W, y + CARD_H, seen ? 0xC0281C12 : 0xC0141010);
            drawRect(x, y, x + CARD_W, y + 1, ok ? 0xFF80E080 : seen ? 0xFFB08850 : 0xFF403830);
            EntityLivingBase e = seen ? model(id) : null;
            if (e != null) {
                float size = Math.max(e.height, e.width * 0.9F);
                int scale = (int) Math.max(6, Math.min(30, 46 / Math.max(0.5F, size)));
                GL11.glColor4f(1, 1, 1, 1);
                try {
                    GuiInventory.func_147046_a(x + CARD_W / 2, y + CARD_H - 24, scale, x + CARD_W / 2 - mx, y + 30 - my, e);
                } catch (Throwable ignored) {
                    MODELS.put(id, null); // не рисуется - больше не пробуем
                }
            }
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0, 300); // поверх модели
            String nm = seen ? mobName(id) : "???";
            drawCenteredString(fontRendererObj, fontRendererObj.trimStringToWidth(nm, CARD_W - 4), x + CARD_W / 2, y + 4, seen ? 0xFFFFFF : 0x807060);
            String cnt = Math.min(k, n) + "/" + n;
            drawCenteredString(fontRendererObj, cnt, x + CARD_W / 2, y + CARD_H - 12, ok ? 0x80E080 : 0xE0C080);
            GL11.glPopMatrix();
        }
        // страницы
        if (pages() > 1) {
            int ax = left + W - 30, ay = top + H - 20;
            String pg = (page + 1) + "/" + pages();
            drawCenteredString(fontRendererObj, pg, ax - 6, ay + 3, 0xE8DCC0);
            drawCenteredString(fontRendererObj, "<", ax - 21, ay + 3, page > 0 ? 0xFFD040 : 0x605848);
            drawCenteredString(fontRendererObj, ">", ax + 9, ay + 3, page < pages() - 1 ? 0xFFD040 : 0x605848);
        }
        super.drawScreen(mx, my, pt);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
