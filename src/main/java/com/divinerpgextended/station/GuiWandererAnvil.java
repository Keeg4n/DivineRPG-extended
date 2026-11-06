package com.divinerpgextended.station;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.divinerpgextended.accessory.Sockets;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.perk.Ammo;
import com.divinerpgextended.perk.Perk;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Интерфейс Наковальни Странника. Фон рисуется прямоугольниками, без своей текстуры. Сверху - вкладки (улучшение,
 * гнёзда, разрядка) и кнопка справки "?": справка - панель поверх окна, по страницам, текст переносится по ширине.
 * Во вкладке гнёзд под каждой частью брони - её гнёзда (картинки, не слоты): занятое - клик вынимает аксессуар в
 * свободный слот аксессуаров; гнездо, куда встанет аксессуар из нижнего ряда, подсвечено.
 */
@SideOnly(Side.CLIENT)
public class GuiWandererAnvil extends GuiContainer {

    private static final int TAB_Y = 3, TAB_H = 15, HELP_W = 14, HELP_MAX_W = 300;
    private static final String[] TAB_KEYS = { "dre.gui.tab.0", "dre.gui.tab.1", "dre.gui.tab.2" };
    private static final String[] BUTTON_KEYS = { "dre.gui.upgrade", "dre.gui.socket", "dre.gui.unload" };

    private final ContainerWandererAnvil container;
    private GuiButton action;
    private final int[] tabX = new int[AnvilLogic.MODES], tabW = new int[AnvilLogic.MODES];
    /** Открытая страница справки (-1 - закрыта) и её строки, уже перенесённые по ширине. */
    private int helpPage = -1, helpPerPage;
    private List<String> helpLines;

    public GuiWandererAnvil(InventoryPlayer inv, TileWandererAnvil tile) {
        super(new ContainerWandererAnvil(inv, tile));
        this.container = (ContainerWandererAnvil) inventorySlots;
        xSize = ContainerWandererAnvil.GUI_W;
        ySize = ContainerWandererAnvil.GUI_H;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        action = new GuiButton(0, 0, 0, 68, 20, "");
        buttonList.add(action);
        // вкладки во всю ширину до "?": поля у надписей - сколько остаётся
        int[] text = new int[AnvilLogic.MODES];
        int sum = 0;
        for (int m = 0; m < AnvilLogic.MODES; m++) sum += text[m] = fontRendererObj.getStringWidth(t(TAB_KEYS[m]));
        int avail = xSize - 7 - HELP_W - 3 - 7 - 2 * (AnvilLogic.MODES - 1);
        int pad = Math.max(2, Math.min(12, (avail - sum) / AnvilLogic.MODES));
        int x = 7;
        for (int m = 0; m < AnvilLogic.MODES; m++) {
            tabX[m] = x;
            tabW[m] = text[m] + pad;
            x += tabW[m] + 2;
        }
        placeButton();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.playerController.sendEnchantPacket(container.windowId, 0);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        placeButton();
    }

    /** Главная кнопка: своя надпись и место во вкладке; доступна, только когда всё готово. */
    private void placeButton() {
        if (action == null) return;
        int m = container.mode;
        action.displayString = t(BUTTON_KEYS[m]);
        action.xPosition = guiLeft + 100;
        action.yPosition = guiTop + (m == AnvilLogic.UPGRADE ? ContainerWandererAnvil.FUEL_Y - 2 : m == AnvilLogic.SOCKETS ? 60 : ContainerWandererAnvil.ROW1_Y - 2);
        action.enabled = ready(status()) && helpPage < 0;
    }

    private static boolean ready(AnvilLogic.Status s) {
        return s == AnvilLogic.Status.READY_UPGRADE || s == AnvilLogic.Status.READY_PERK || s == AnvilLogic.Status.READY_SOCKET
            || s == AnvilLogic.Status.READY_UNLOAD || s == AnvilLogic.Status.READY_CORE;
    }

    private AnvilLogic.Status status() {
        int s = container.status;
        return s < 0 || s >= AnvilLogic.Status.values().length ? AnvilLogic.Status.EMPTY : AnvilLogic.Status.values()[s];
    }

    private ItemStack station(int slot) {
        return ((Slot) container.inventorySlots.get(slot)).getStack();
    }

    private static String t(String key) {
        return StatCollector.translateToLocal(key);
    }

    private String donorName() {
        ItemStack s = container.param >= 0 && container.param < 4 ? station(AnvilLogic.DONOR + container.param) : null;
        return s == null ? "?" : s.getDisplayName();
    }

    private String ammoName() {
        Item a = Ammo.of(station(AnvilLogic.FUEL));
        return a == null ? "?" : new ItemStack(a).getDisplayName();
    }

    private String statusText() {
        AnvilLogic.Status s = status();
        String key = "dre.status." + s.name().toLowerCase();
        switch (s) {
            case NEED_BOSS:
                return StatCollector.translateToLocalFormatted(key, t(Boss.values()[container.param].langKey()));
            case NEED_FUEL: {
                Item fuel = Item.getItemById(container.fuelItem);
                String item = fuel == null ? "?" : new ItemStack(fuel).getDisplayName();
                return StatCollector.translateToLocalFormatted(key, container.param, item);
            }
            case READY_UPGRADE:
                return StatCollector.translateToLocalFormatted(key, container.param, t("dre.tier." + container.param));
            case MAX_TIER:
            case READY_SOCKET:
            case NEED_LEVELS:
                return StatCollector.translateToLocalFormatted(key, container.param);
            case READY_CORE:
                return StatCollector.translateToLocalFormatted(key, t("dre.core." + com.divinerpgextended.item.ArmorCore.values()[container.param].key() + ".name"));
            case READY_PERK:
            case PERK_PRESENT:
                return StatCollector.translateToLocalFormatted(key, t(Perk.values()[container.param].langKey()));
            case SOCKET_PART: {
                ItemStack acc = station(AnvilLogic.DONOR + Math.max(0, Math.min(3, container.param)));
                String parts = acc == null ? "?" : com.divinerpgextended.trinket.ItemTrinket.partNames(Sockets.parts(acc)).getUnformattedText();
                return StatCollector.translateToLocalFormatted(key, donorName(), parts);
            }
            case SOCKET_FULL:
            case SOCKET_SAME:
            case DONOR_SOCKETS:
                return StatCollector.translateToLocalFormatted(key, donorName());
            case READY_UNLOAD:
                return StatCollector.translateToLocalFormatted(key, container.param, ammoName());
            default:
                return t(key);
        }
    }

    // ---- вкладки и справка ----

    private int tabAt(int mx, int my) {
        int rx = mx - guiLeft, ry = my - guiTop;
        if (ry < TAB_Y || ry >= TAB_Y + TAB_H) return -1;
        for (int m = 0; m < AnvilLogic.MODES; m++) if (rx >= tabX[m] && rx < tabX[m] + tabW[m]) return m;
        return -1;
    }

    private boolean helpAt(int mx, int my) {
        int rx = mx - guiLeft, ry = my - guiTop;
        return ry >= TAB_Y && ry < TAB_Y + TAB_H && rx >= xSize - 7 - HELP_W && rx < xSize - 7;
    }

    private void click() {
        mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    /** Абзацы справки вкладки ("" - пустая строка между разделами). */
    private static List<String> helpText(int mode) {
        List<String> out = new ArrayList<String>();
        if (mode == AnvilLogic.UPGRADE) {
            out.add(t("dre.help.0.how"));
            out.add("");
            out.add("§e" + t("dre.gui.donor_upgrade"));
            String speed = new java.text.DecimalFormat("0.##").format(com.divinerpgextended.Config.coreShadowSpeed);
            for (com.divinerpgextended.item.ArmorCore c : com.divinerpgextended.item.ArmorCore.values()) {
                out.add("§7" + t("dre.core." + c.key() + ".name") + ": §f" + c.fuelCount() + " x " + new ItemStack(c.fuel()).getDisplayName());
                out.add("§8" + StatCollector.translateToLocalFormatted("dre.core." + c.key() + ".desc", speed));
            }
            out.add("§7" + t("dre.gui.core_swap"));
            out.add("");
            out.add("§6" + t("dre.gui.tiers_hint"));
            for (int tier = 2; tier <= 7; tier++) out.add("§7" + t("dre.gui.tier_hint." + tier));
            out.add("");
            out.add("§6" + t("dre.gui.donor_hint"));
            for (Perk p : Perk.values()) {
                String boss = p.unlock == null ? "" : " §8(" + t(p.unlock.langKey()) + ")";
                out.add("§7" + t(p.langKey()) + ": §f" + t("dre.donor." + p.name().toLowerCase()) + boss);
            }
        } else {
            int n = mode == AnvilLogic.SOCKETS ? 6 : 4;
            for (int i = 1; i <= n; i++) {
                if (i > 1) out.add("");
                out.add(t("dre.help." + mode + "." + i));
            }
        }
        return out;
    }

    private int helpWidth() {
        return Math.min(width - 16, HELP_MAX_W);
    }

    @SuppressWarnings("unchecked")
    private void openHelp() {
        int w = helpWidth() - 16;
        helpLines = new ArrayList<String>();
        for (String para : helpText(container.mode)) {
            if (para.isEmpty()) helpLines.add("");
            else helpLines.addAll(fontRendererObj.listFormattedStringToWidth(para, w));
        }
        helpPerPage = Math.max(4, (Math.min(height - 16, 260) - 40) / 10);
        helpPage = 0;
    }

    private int helpPages() {
        return Math.max(1, (helpLines.size() + helpPerPage - 1) / helpPerPage);
    }

    private void drawHelp() {
        int pw = helpWidth(), from = helpPage * helpPerPage, n = Math.min(helpPerPage, helpLines.size() - from);
        int ph = 40 + Math.min(helpPerPage, helpLines.size()) * 10;
        int x0 = (width - pw) / 2, y0 = (height - ph) / 2;
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        drawRect(0, 0, width, height, 0xA0000000);
        drawRect(x0 - 1, y0 - 1, x0 + pw + 1, y0 + ph + 1, 0xFF6A4A9A);
        drawRect(x0, y0, x0 + pw, y0 + ph, 0xF0140C1C);
        fontRendererObj.drawStringWithShadow(t("dre.help." + container.mode + ".title"), x0 + 8, y0 + 7, 0xFFE080);
        for (int i = 0; i < n; i++) fontRendererObj.drawStringWithShadow("§f" + helpLines.get(from + i), x0 + 8, y0 + 22 + i * 10, 0xFFFFFF);
        String foot = helpPage + 1 < helpPages() ? StatCollector.translateToLocalFormatted("dre.help.next", helpPage + 1, helpPages()) : t("dre.help.close");
        fontRendererObj.drawStringWithShadow(foot, x0 + pw - 8 - fontRendererObj.getStringWidth(foot), y0 + ph - 12, 0x909090);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (helpPage >= 0) {
            if (key == 1 || key == mc.gameSettings.keyBindInventory.getKeyCode()) helpPage = -1;
            else if (key == 205 || key == 57) helpPage = Math.min(helpPage + 1, helpPages() - 1); // вправо, пробел
            else if (key == 203) helpPage = Math.max(helpPage - 1, 0); // влево
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    public void handleMouseInput() {
        if (helpPage >= 0) {
            int d = Mouse.getEventDWheel();
            if (d < 0) helpPage = Math.min(helpPage + 1, helpPages() - 1);
            else if (d > 0) helpPage = Math.max(helpPage - 1, 0);
        }
        super.handleMouseInput();
    }

    // ---- гнёзда ----

    /** Гнездо под мышью: {слот брони, номер гнезда} или null. */
    private int[] socketAt(int mx, int my) {
        if (container.mode != AnvilLogic.SOCKETS) return null;
        int rx = mx - guiLeft, ry = my - guiTop;
        for (int a = 0; a < 4; a++) {
            ItemStack armor = station(AnvilLogic.ARMOR + a);
            if (!Sockets.socketable(armor)) continue;
            for (int k = 0; k < Sockets.maxCapacity(armor); k++) {
                int x = 8 + a * 18, y = ContainerWandererAnvil.SOCKET_Y + k * 18;
                if (rx >= x && rx < x + 16 && ry >= y && ry < y + 16) return new int[] { a, k };
            }
        }
        return null;
    }

    /** Второе гнездо брони Странника закрыто для этого игрока (босс части не побеждён). */
    private boolean locked(ItemStack armor, int k) {
        return k >= 1 && armor.getItem() instanceof com.divinerpgextended.item.ItemWandererArmor
            && (container.bossMask & Sockets.SECOND[Sockets.type(armor)].bit()) == 0;
    }

    /** Куда встанет аксессуар каждого слота аксессуаров: {слот брони, номер гнезда} или null. */
    private int[][] planned() {
        int[][] out = new int[4][];
        int[] used = new int[4];
        for (int d = 0; d < 4; d++) {
            int a = (container.targets >> d * 3 & 7) - 1;
            if (a < 0 || a > 3 || station(AnvilLogic.DONOR + d) == null) continue;
            out[d] = new int[] { a, Sockets.count(station(AnvilLogic.ARMOR + a)) + used[a]++ };
        }
        return out;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (helpPage >= 0) {
            // ЛКМ - дальше (на последней странице - закрыть), ПКМ - закрыть
            helpPage = button == 0 && helpPage + 1 < helpPages() ? helpPage + 1 : -1;
            return;
        }
        if (button == 0) {
            int tab = tabAt(mx, my);
            if (tab >= 0) {
                if (tab != container.mode) {
                    click();
                    mc.thePlayer.getEntityData().setInteger(ContainerWandererAnvil.LAST_TAB, tab);
                    mc.playerController.sendEnchantPacket(container.windowId, ContainerWandererAnvil.TAB + tab);
                }
                return;
            }
            if (helpAt(mx, my)) {
                click();
                openHelp();
                return;
            }
        }
        int[] s = socketAt(mx, my);
        if (s != null && button == 0) {
            if (s[1] < Sockets.count(station(AnvilLogic.ARMOR + s[0]))) {
                mc.playerController.sendEnchantPacket(container.windowId, ContainerWandererAnvil.EXTRACT + s[0] * 2 + s[1]);
            }
            return;
        }
        super.mouseClicked(mx, my, button);
    }

    /** Подсказки: справка, гнёзда - что внутри, что встанет, как вынуть, что закрыто. */
    @Override
    public void drawScreen(int mx, int my, float partial) {
        if (helpPage >= 0) {
            super.drawScreen(-1000, -1000, partial); // без подсветки слотов и подсказок под справкой
            drawHelp();
            return;
        }
        super.drawScreen(mx, my, partial);
        List<String> tip = new ArrayList<String>();
        if (helpAt(mx, my)) tip.add(t("dre.gui.help"));
        else {
            int[] s = socketAt(mx, my);
            if (s != null) socketTip(s, tip);
        }
        if (!tip.isEmpty()) drawHoveringText(tip, mx, my, fontRendererObj);
    }

    private void socketTip(int[] s, List<String> tip) {
        ItemStack armor = station(AnvilLogic.ARMOR + s[0]);
        List<ItemStack> in = Sockets.contents(armor);
        if (s[1] < in.size()) {
            tip.add(EnumChatFormatting.LIGHT_PURPLE + in.get(s[1]).getDisplayName());
            tip.add(EnumChatFormatting.GRAY + t("dre.gui.socket_take"));
            return;
        }
        int[][] plan = planned();
        for (int d = 0; d < 4; d++) {
            if (plan[d] != null && plan[d][0] == s[0] && plan[d][1] == s[1]) {
                tip.add(EnumChatFormatting.GOLD + station(AnvilLogic.DONOR + d).getDisplayName());
                tip.add(EnumChatFormatting.GRAY + t("dre.gui.socket_planned"));
                return;
            }
        }
        if (locked(armor, s[1])) {
            tip.add(EnumChatFormatting.GRAY + t("dre.gui.socket_locked"));
            tip.add(EnumChatFormatting.GOLD + t(Sockets.SECOND[Sockets.type(armor)].langKey()));
        } else {
            tip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.gui.socket_empty", t("dre.sockets.part." + Sockets.type(armor))));
        }
    }

    private void label(String key, int x, int y, int color) {
        fontRendererObj.drawString(t(key), x, y, color);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        int mode = container.mode;
        for (int m = 0; m < AnvilLogic.MODES; m++) {
            String name = t(TAB_KEYS[m]);
            fontRendererObj.drawString(name, tabX[m] + (tabW[m] - fontRendererObj.getStringWidth(name) + 1) / 2, TAB_Y + 4, m == mode ? 0x303030 : 0x505050);
        }
        fontRendererObj.drawString("?", xSize - 7 - HELP_W + 5, TAB_Y + 4, 0x2A4A8A);

        if (mode == AnvilLogic.UPGRADE) {
            label("dre.gui.armor_wanderer", 84, ContainerWandererAnvil.ROW1_Y + 5, 0x404040);
            label("dre.gui.donor_set", 84, ContainerWandererAnvil.ROW2_Y + 5, 0x404040);
            label("dre.gui.fuel", 28, ContainerWandererAnvil.FUEL_Y + 5, 0x404040);
        } else if (mode == AnvilLogic.SOCKETS) {
            label("dre.gui.armor", 84, ContainerWandererAnvil.ROW1_Y + 5, 0x404040);
            label("dre.gui.sockets", 84, ContainerWandererAnvil.SOCKET_Y + 3, 0x6A3A8A);
            label("dre.gui.accessories", 84, ContainerWandererAnvil.ACC_Y + 5, 0x404040);
            drawSocketItems();
        } else {
            label("dre.gui.weapon", 28, ContainerWandererAnvil.ROW1_Y + 5, 0x404040);
            ItemStack w = station(AnvilLogic.FUEL);
            int y = ContainerWandererAnvil.ROW1_Y + 22;
            if (w != null && Ammo.of(w) != null) {
                fontRendererObj.drawString(StatCollector.translateToLocalFormatted("dre.gui.loaded", Ammo.loaded(w), Ammo.capacity(w), ammoName()), 8, y, 0x404040);
                y += 12;
            }
            List<String> info = fontRendererObj.listFormattedStringToWidth(t("dre.gui.unload_info"), xSize - 16);
            for (int i = 0; i < info.size() && y + 9 <= ContainerWandererAnvil.STATUS_Y - 2; i++, y += 9) fontRendererObj.drawString(info.get(i), 8, y, 0x707070);
        }

        AnvilLogic.Status s = status();
        int color = ready(s) ? 0x1E7A1E
            : s == AnvilLogic.Status.SOCKETS || s == AnvilLogic.Status.EMPTY || s == AnvilLogic.Status.EMPTY_SOCKETS || s == AnvilLogic.Status.EMPTY_UNLOAD
                ? 0x404040 : 0x8B1A1A;
        List<String> lines = fontRendererObj.listFormattedStringToWidth(statusText(), xSize - 16);
        for (int i = 0; i < lines.size() && i < 4; i++) fontRendererObj.drawString(lines.get(i), 8, ContainerWandererAnvil.STATUS_Y + i * 9, color);
    }

    /** Аксессуары в гнёздах; те, что встанут по кнопке, - бледные в золотой рамке; закрытые гнёзда - крестик. */
    private void drawSocketItems() {
        int[][] plan = planned();
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        for (int a = 0; a < 4; a++) {
            ItemStack armor = station(AnvilLogic.ARMOR + a);
            if (!Sockets.socketable(armor)) continue;
            List<ItemStack> in = Sockets.contents(armor);
            for (int k = 0; k < in.size(); k++) {
                itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), in.get(k), 8 + a * 18, ContainerWandererAnvil.SOCKET_Y + k * 18);
            }
        }
        for (int d = 0; d < 4; d++) {
            if (plan[d] == null || plan[d][1] > 1) continue;
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), station(AnvilLogic.DONOR + d), 8 + plan[d][0] * 18,
                ContainerWandererAnvil.SOCKET_Y + plan[d][1] * 18);
        }
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int d = 0; d < 4; d++) {
            if (plan[d] == null || plan[d][1] > 1) continue;
            int x = 8 + plan[d][0] * 18, y = ContainerWandererAnvil.SOCKET_Y + plan[d][1] * 18;
            drawRect(x, y, x + 16, y + 16, 0x70D8C8F0); // бледнее встроенных
            drawRect(x - 1, y - 1, x + 17, y, 0xFFE0A030);
            drawRect(x - 1, y + 16, x + 17, y + 17, 0xFFE0A030);
            drawRect(x - 1, y, x, y + 16, 0xFFE0A030);
            drawRect(x + 16, y, x + 17, y + 16, 0xFFE0A030);
        }
        for (int a = 0; a < 4; a++) {
            ItemStack armor = station(AnvilLogic.ARMOR + a);
            if (!Sockets.socketable(armor)) continue;
            for (int k = Sockets.count(armor); k < Sockets.maxCapacity(armor); k++) {
                if (locked(armor, k)) fontRendererObj.drawString("x", 8 + a * 18 + 5, ContainerWandererAnvil.SOCKET_Y + k * 18 + 4, 0x909090);
            }
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        int l = guiLeft, t = guiTop, mode = container.mode;
        drawRect(l, t, l + xSize, t + ySize, 0xFF555555);
        drawRect(l + 1, t + 1, l + xSize - 1, t + ySize - 1, 0xFFC6C6C6);
        // вкладки: линия под ними, неактивные - темнее, активная - одно целое с окном
        int line = t + TAB_Y + TAB_H;
        drawRect(l + 4, line, l + xSize - 4, line + 1, 0xFF555555);
        int hover = helpPage < 0 ? tabAt(mx, my) : -1;
        for (int m = 0; m < AnvilLogic.MODES; m++) {
            int x0 = l + tabX[m], x1 = x0 + tabW[m], y0 = t + TAB_Y;
            if (m == mode) {
                drawRect(x0, y0, x1, line + 1, 0xFF555555);
                drawRect(x0 + 1, y0 + 1, x1 - 1, line + 1, 0xFFC6C6C6);
            } else {
                drawRect(x0, y0 + 1, x1, line, 0xFF6E6E6E);
                drawRect(x0 + 1, y0 + 2, x1 - 1, line, m == hover ? 0xFFB4B4B4 : 0xFF9C9C9C);
            }
        }
        int hx = l + xSize - 7 - HELP_W, hy = t + TAB_Y + 1;
        drawRect(hx, hy, hx + HELP_W, line - 1, 0xFF4A6AAA);
        drawRect(hx + 1, hy + 1, hx + HELP_W - 1, line - 2, helpPage < 0 && helpAt(mx, my) ? 0xFFDCE6FF : 0xFFC0CCEE);

        for (Object o : inventorySlots.inventorySlots) {
            Slot slot = (Slot) o;
            if (slot.xDisplayPosition < 0) continue; // скрыт во вкладке
            int x = l + slot.xDisplayPosition, y = t + slot.yDisplayPosition;
            drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
            drawRect(x, y, x + 17, y + 17, 0xFFFFFFFF);
            drawRect(x, y, x + 16, y + 16, 0xFF8B8B8B);
        }
        if (mode != AnvilLogic.SOCKETS) return;
        // гнёзда - фиолетовые ячейки под частью брони (закрытое второе гнездо Странника - тёмное)
        for (int a = 0; a < 4; a++) {
            ItemStack armor = station(AnvilLogic.ARMOR + a);
            if (!Sockets.socketable(armor)) continue;
            for (int k = 0; k < Sockets.maxCapacity(armor); k++) {
                int x = l + 8 + a * 18, y = t + ContainerWandererAnvil.SOCKET_Y + k * 18;
                boolean shut = k >= Sockets.count(armor) && locked(armor, k);
                drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF3A2450);
                drawRect(x, y, x + 17, y + 17, 0xFFD8C8F0);
                drawRect(x, y, x + 16, y + 16, shut ? 0xFF3A3A3A : 0xFF8E7AAE);
            }
        }
    }
}
