package com.divinerpgextended.station;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.accessory.Sockets;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.perk.Ammo;
import com.divinerpgextended.perk.Perk;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Интерфейс Наковальни Странника. Фон рисуется прямоугольниками, без своей текстуры. Под каждой частью брони - её
 * гнёзда (картинки, не слоты): занятое - клик вынимает аксессуар в свободный слот донора.
 */
@SideOnly(Side.CLIENT)
public class GuiWandererAnvil extends GuiContainer {

    private final ContainerWandererAnvil container;
    private GuiButton upgrade;

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
        upgrade = new GuiButton(0, guiLeft + 100, guiTop + ContainerWandererAnvil.FUEL_Y - 4, 68, 20, StatCollector.translateToLocal("dre.gui.upgrade"));
        buttonList.add(upgrade);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.playerController.sendEnchantPacket(container.windowId, 0);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (upgrade == null) return;
        AnvilLogic.Status s = status();
        upgrade.enabled = ready(s);
        upgrade.displayString = StatCollector.translateToLocal(s == AnvilLogic.Status.READY_SOCKET ? "dre.gui.socket"
            : s == AnvilLogic.Status.READY_UNLOAD ? "dre.gui.unload" : "dre.gui.upgrade");
    }

    private static boolean ready(AnvilLogic.Status s) {
        return s == AnvilLogic.Status.READY_UPGRADE || s == AnvilLogic.Status.READY_PERK || s == AnvilLogic.Status.READY_SOCKET
            || s == AnvilLogic.Status.READY_UNLOAD;
    }

    private AnvilLogic.Status status() {
        int s = container.status;
        return s < 0 || s >= AnvilLogic.Status.values().length ? AnvilLogic.Status.EMPTY
            : AnvilLogic.Status.values()[s];
    }

    private ItemStack station(int slot) {
        return ((Slot) container.inventorySlots.get(slot)).getStack();
    }

    private String donorName() {
        ItemStack s = container.param >= 0 && container.param < 4 ? station(AnvilLogic.DONOR + container.param) : null;
        return s == null ? "?" : s.getDisplayName();
    }

    private String statusText() {
        AnvilLogic.Status s = status();
        String key = "dre.status." + s.name()
            .toLowerCase();
        switch (s) {
            case NEED_BOSS:
                return StatCollector.translateToLocalFormatted(key,
                    StatCollector.translateToLocal(Boss.values()[container.param].langKey()));
            case NEED_FUEL: {
                Item fuel = Item.getItemById(container.fuelItem);
                String item = fuel == null ? "?" : new ItemStack(fuel).getDisplayName();
                return StatCollector.translateToLocalFormatted(key, container.param, item);
            }
            case READY_UPGRADE:
                return StatCollector.translateToLocalFormatted(key, container.param,
                    StatCollector.translateToLocal("dre.tier." + container.param));
            case MAX_TIER:
            case READY_SOCKET:
            case NEED_LEVELS:
                return StatCollector.translateToLocalFormatted(key, container.param);
            case READY_PERK:
            case PERK_PRESENT:
                return StatCollector.translateToLocalFormatted(key,
                    StatCollector.translateToLocal(Perk.values()[container.param].langKey()));
            case SOCKET_PART: {
                ItemStack acc = station(AnvilLogic.DONOR + Math.max(0, Math.min(3, container.param)));
                String parts = acc == null ? "?" : com.divinerpgextended.trinket.ItemTrinket.partNames(Sockets.parts(acc)).getUnformattedText();
                return StatCollector.translateToLocalFormatted(key, donorName(), parts);
            }
            case SOCKET_FULL:
            case SOCKET_SAME:
            case DONOR_SOCKETS:
                return StatCollector.translateToLocalFormatted(key, donorName());
            case READY_UNLOAD: {
                ItemStack w = station(AnvilLogic.FUEL);
                Item a = Ammo.of(w);
                return StatCollector.translateToLocalFormatted(key, container.param, a == null ? "?" : new ItemStack(a).getDisplayName());
            }
            default:
                return StatCollector.translateToLocal(key);
        }
    }

    /** Гнездо под мышью: {слот брони, номер гнезда} или null. */
    private int[] socketAt(int mx, int my) {
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

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        int[] s = socketAt(mx, my);
        if (s != null && button == 0) {
            if (s[1] < Sockets.count(station(AnvilLogic.ARMOR + s[0]))) {
                mc.playerController.sendEnchantPacket(container.windowId, ContainerWandererAnvil.EXTRACT + s[0] * 2 + s[1]);
            }
            return;
        }
        super.mouseClicked(mx, my, button);
    }

    /** Подсказки: "Донор (?)" - какие сеты дают какие перки; гнёзда - что внутри и как вынуть. */
    @Override
    @SuppressWarnings("unchecked")
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        int rx = mx - guiLeft, ry = my - guiTop;
        int dy = ContainerWandererAnvil.DONOR_Y;
        if (rx >= 82 && rx <= 140 && ry >= dy + 2 && ry <= dy + 15) {
            List<String> tip = new ArrayList<String>();
            tip.add(StatCollector.translateToLocal("dre.gui.donor_hint"));
            for (Perk p : Perk.values()) {
                tip.add("§7" + StatCollector.translateToLocal(p.langKey()) + ": §f"
                    + StatCollector.translateToLocal("dre.donor." + p.name()
                        .toLowerCase()));
            }
            tip.add("§8" + StatCollector.translateToLocal("dre.gui.donor_upgrade"));
            tip.add("§6" + StatCollector.translateToLocal("dre.gui.tiers_hint"));
            for (int t = 2; t <= 7; t++) tip.add("§7" + StatCollector.translateToLocal("dre.gui.tier_hint." + t));
            tip.add("§d" + StatCollector.translateToLocal("dre.gui.socket_hint"));
            tip.add("§e" + StatCollector.translateToLocal("dre.gui.unload_hint"));
            drawHoveringText(tip, mx, my, fontRendererObj);
            return;
        }
        int[] s = socketAt(mx, my);
        if (s == null) return;
        ItemStack armor = station(AnvilLogic.ARMOR + s[0]);
        List<ItemStack> in = Sockets.contents(armor);
        List<String> tip = new ArrayList<String>();
        if (s[1] < in.size()) {
            tip.add(EnumChatFormatting.LIGHT_PURPLE + in.get(s[1]).getDisplayName());
            tip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.gui.socket_take"));
        } else if (locked(armor, s[1])) {
            tip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.gui.socket_locked"));
            tip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal(Sockets.SECOND[Sockets.type(armor)].langKey()));
        } else {
            tip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("dre.gui.socket_empty",
                StatCollector.translateToLocal("dre.sockets.part." + Sockets.type(armor))));
        }
        drawHoveringText(tip, mx, my, fontRendererObj);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        fontRendererObj.drawString(StatCollector.translateToLocal("tile.dre.wandererAnvil.name"), 8, 6, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("dre.gui.armor"), 84, ContainerWandererAnvil.ARMOR_Y + 5, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("dre.gui.sockets"), 84, ContainerWandererAnvil.SOCKET_Y + 5, 0x6A3A8A);
        fontRendererObj.drawString(StatCollector.translateToLocal("dre.gui.donor") + " (?)", 84, ContainerWandererAnvil.DONOR_Y + 5, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("dre.gui.fuel"), 28, ContainerWandererAnvil.FUEL_Y + 5, 0x404040);

        // гнёзда: аксессуары внутри
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
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int a = 0; a < 4; a++) {
            ItemStack armor = station(AnvilLogic.ARMOR + a);
            if (!Sockets.socketable(armor)) continue;
            for (int k = Sockets.count(armor); k < Sockets.maxCapacity(armor); k++) {
                if (locked(armor, k)) fontRendererObj.drawString("x", 8 + a * 18 + 5, ContainerWandererAnvil.SOCKET_Y + k * 18 + 4, 0x909090);
            }
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        AnvilLogic.Status s = status();
        int color = ready(s) ? 0x1E7A1E : s == AnvilLogic.Status.SOCKETS ? 0x404040 : 0x8B1A1A;
        @SuppressWarnings("unchecked")
        List<String> lines = fontRendererObj.listFormattedStringToWidth(statusText(), xSize - 16);
        for (int i = 0; i < lines.size() && i < 2; i++) {
            fontRendererObj.drawString(lines.get(i), 8, ContainerWandererAnvil.FUEL_Y + 20 + i * 9, color);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        int l = guiLeft, t = guiTop;
        drawRect(l, t, l + xSize, t + ySize, 0xFF555555);
        drawRect(l + 1, t + 1, l + xSize - 1, t + ySize - 1, 0xFFC6C6C6);
        for (Object o : inventorySlots.inventorySlots) {
            Slot slot = (Slot) o;
            int x = l + slot.xDisplayPosition, y = t + slot.yDisplayPosition;
            drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
            drawRect(x, y, x + 17, y + 17, 0xFFFFFFFF);
            drawRect(x, y, x + 16, y + 16, 0xFF8B8B8B);
        }
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
