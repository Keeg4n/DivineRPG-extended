package com.divinerpgextended.station;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.perk.Perk;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Интерфейс Наковальни Странника. Фон рисуется прямоугольниками, без своей текстуры. */
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
        upgrade = new GuiButton(0, guiLeft + 100, guiTop + 72, 68, 20, StatCollector.translateToLocal("dre.gui.upgrade"));
        buttonList.add(upgrade);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.playerController.sendEnchantPacket(container.windowId, 0);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (upgrade != null) {
            AnvilLogic.Status s = status();
            upgrade.enabled = s == AnvilLogic.Status.READY_UPGRADE || s == AnvilLogic.Status.READY_PERK;
        }
    }

    private AnvilLogic.Status status() {
        int s = container.status;
        return s < 0 || s >= AnvilLogic.Status.values().length ? AnvilLogic.Status.EMPTY
            : AnvilLogic.Status.values()[s];
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
                return StatCollector.translateToLocalFormatted(key, container.param);
            case READY_PERK:
            case PERK_PRESENT:
                return StatCollector.translateToLocalFormatted(key,
                    StatCollector.translateToLocal(Perk.values()[container.param].langKey()));
            default:
                return StatCollector.translateToLocal(key);
        }
    }

    /** Подсказка при наведении на надпись "Донор (?)": какие сеты дают какие перки. */
    @Override
    @SuppressWarnings("unchecked")
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        int rx = mx - guiLeft, ry = my - guiTop;
        if (rx >= 82 && rx <= 140 && ry >= 50 && ry <= 63) {
            java.util.List<String> tip = new java.util.ArrayList<String>();
            tip.add(StatCollector.translateToLocal("dre.gui.donor_hint"));
            for (Perk p : Perk.values()) {
                tip.add("§7" + StatCollector.translateToLocal(p.langKey()) + ": §f"
                    + StatCollector.translateToLocal("dre.donor." + p.name()
                        .toLowerCase()));
            }
            tip.add("§8" + StatCollector.translateToLocal("dre.gui.donor_upgrade"));
            tip.add("§6" + StatCollector.translateToLocal("dre.gui.tiers_hint"));
            for (int t = 2; t <= 7; t++) tip.add("§7" + StatCollector.translateToLocal("dre.gui.tier_hint." + t));
            drawHoveringText(tip, mx, my, fontRendererObj);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        fontRendererObj.drawString(StatCollector.translateToLocal("tile.dre.wandererAnvil.name"), 8, 6, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("dre.gui.armor"), 84, 25, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("dre.gui.donor") + " (?)", 84, 53, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("dre.gui.fuel"), 28, 81, 0x404040);

        AnvilLogic.Status s = status();
        int color = s == AnvilLogic.Status.READY_UPGRADE || s == AnvilLogic.Status.READY_PERK ? 0x1E7A1E : 0x8B1A1A;
        @SuppressWarnings("unchecked")
        List<String> lines = fontRendererObj.listFormattedStringToWidth(statusText(), xSize - 16);
        for (int i = 0; i < lines.size() && i < 2; i++) {
            fontRendererObj.drawString(lines.get(i), 8, 96 + i * 9, color);
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
    }
}
