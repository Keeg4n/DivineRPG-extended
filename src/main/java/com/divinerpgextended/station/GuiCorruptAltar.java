package com.divinerpgextended.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.model.ModelBook;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnchantmentNameParts;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.util.glu.Project;

import com.divinerpgextended.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Окно алтаря порчи: ванильный стол зачарования (текстура и книга), но две ячейки - вещь и оплата, а слева от каждого
 * слота кнопка "узнать" с ценой (осколки, камень или рупии - по виду вещи). Узнанные чары пишутся в слоте вместо
 * галактических рун, полный список - в подсказке.
 */
@SideOnly(Side.CLIENT)
public class GuiCorruptAltar extends GuiContainer {

    private static final ResourceLocation TEX = new ResourceLocation("textures/gui/container/enchanting_table.png");
    private static final ResourceLocation BOOK = new ResourceLocation("textures/entity/enchanting_table_book.png");
    private static final ModelBook BOOK_MODEL = new ModelBook();
    /** Кнопка "узнать" (18x18) и сам слот: ванильный ряд 108 точек, здесь он уже - слева место под кнопку. */
    static final int EYE_X = 58, ROW_X = 78, ROW_W = 90, ROW_Y = 14, ROW_H = 19;

    private final ContainerCorruptAltar container;
    private final Random rand = new Random();
    private long nameSeed;
    private ItemStack lastItem;
    // книга, как у ванильного стола
    private float flip, oFlip, flipT, flipA, open, oOpen;

    public GuiCorruptAltar(InventoryPlayer inv, World w, int x, int y, int z) {
        super(new ContainerCorruptAltar(inv, w, x, y, z));
        container = (ContainerCorruptAltar) inventorySlots;
    }

    private static String t(String key) {
        return StatCollector.translateToLocal(key);
    }

    private ItemStack item() {
        return ((Slot) container.inventorySlots.get(ContainerCorruptAltar.ITEM)).getStack();
    }

    private ItemStack pay() {
        return ((Slot) container.inventorySlots.get(ContainerCorruptAltar.PAY)).getStack();
    }

    private boolean inRow(int mx, int my, int i) {
        int rx = mx - guiLeft - ROW_X, ry = my - guiTop - ROW_Y - ROW_H * i;
        return rx >= 0 && ry >= 0 && rx < ROW_W && ry < ROW_H;
    }

    private boolean inEye(int mx, int my, int i) {
        int rx = mx - guiLeft - EYE_X, ry = my - guiTop - ROW_Y - ROW_H * i;
        return rx >= 0 && ry >= 0 && rx < 18 && ry < 18;
    }

    private boolean canEnchant(int i) {
        int lvl = container.levels[i];
        return lvl > 0 && (mc.thePlayer.experienceLevel >= lvl || mc.thePlayer.capabilities.isCreativeMode);
    }

    private boolean paid(int i) {
        if (mc.thePlayer.capabilities.isCreativeMode || container.price[i] <= 0) return true;
        ItemStack p = pay();
        return p != null && p.getItem() == AltarLogic.revealItem(item(), i) && p.stackSize >= container.price[i];
    }

    private boolean canReveal(int i) {
        return container.levels[i] > 0 && container.knownCount[i] < 0 && paid(i);
    }

    /** Узнанные чары слота: "Защита IV". */
    private List<String> names(int i) {
        List<String> out = new ArrayList<String>();
        for (int k = 0; k < container.knownCount[i] && k < ContainerCorruptAltar.SHOWN; k++) {
            int v = container.known[i][k];
            Enchantment e = v < 0 || (v >> 5) >= Enchantment.enchantmentsList.length ? null : Enchantment.enchantmentsList[v >> 5];
            out.add(e == null ? "?" : e.getTranslatedName(v & 31));
        }
        return out;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        super.mouseClicked(mx, my, button);
        if (button != 0) return;
        for (int i = 0; i < AltarLogic.SLOTS; i++) {
            if (inRow(mx, my, i) && canEnchant(i)) mc.playerController.sendEnchantPacket(container.windowId, i);
            else if (inEye(mx, my, i) && canReveal(i)) mc.playerController.sendEnchantPacket(container.windowId, ContainerCorruptAltar.REVEAL + i);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ItemStack s = item();
        if (!ItemStack.areItemStacksEqual(s, lastItem)) {
            lastItem = s == null ? null : s.copy();
            nameSeed = rand.nextLong();
            do flipT += rand.nextInt(4) - rand.nextInt(4);
            while (flip <= flipT + 1.0F && flip >= flipT - 1.0F);
        }
        oFlip = flip;
        oOpen = open;
        boolean any = false;
        for (int i = 0; i < AltarLogic.SLOTS; i++) any |= container.levels[i] != 0;
        open = Math.max(0.0F, Math.min(1.0F, open + (any ? 0.2F : -0.2F)));
        float d = Math.max(-0.2F, Math.min(0.2F, (flipT - flip) * 0.4F));
        flipA += (d - flipA) * 0.9F;
        flip += flipA;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        String title = EnumChatFormatting.getTextWithoutFormattingCodes(t("tile.altarOfCorruption.name"));
        fontRendererObj.drawString(title, 12, 5, 0x404040);
        fontRendererObj.drawString(t("container.inventory"), 8, ySize - 96 + 2, 0x404040);
        // цена "узнать": осколки, камень или рупии по виду вещи; узнано или нечем платить - притушено
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        for (int i = 0; i < AltarLogic.SLOTS; i++) {
            if (container.levels[i] <= 0) continue;
            ItemStack price = new ItemStack(AltarLogic.revealItem(item(), i), Math.max(1, container.price[i]));
            int x = EYE_X + 1, y = ROW_Y + ROW_H * i + 1;
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), price, x, y);
            itemRender.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(), price, x, y);
        }
        Slot paySlot = (Slot) container.inventorySlots.get(ContainerCorruptAltar.PAY);
        boolean hint = pay() == null && item() != null;
        if (hint) itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), new ItemStack(AltarLogic.revealItem(item(), 0)),
            paySlot.xDisplayPosition, paySlot.yDisplayPosition);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int i = 0; i < AltarLogic.SLOTS; i++) {
            if (container.levels[i] <= 0) continue;
            int x = EYE_X + 1, y = ROW_Y + ROW_H * i + 1;
            if (container.knownCount[i] >= 0) drawRect(x, y, x + 16, y + 16, 0xC0281838);
            else if (!paid(i)) drawRect(x, y, x + 16, y + 16, 0x80202020);
        }
        if (hint) drawRect(paySlot.xDisplayPosition, paySlot.yDisplayPosition, paySlot.xDisplayPosition + 16, paySlot.yDisplayPosition + 16, 0xA08B8B8B);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void frame(int x, int y) {
        drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        drawRect(x, y, x + 17, y + 17, 0xFFFFFFFF);
        drawRect(x, y, x + 16, y + 16, 0xFF8B8B8B);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        int l = guiLeft, t = guiTop;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(TEX);
        drawTexturedModalRect(l, t, 0, 0, xSize, ySize);
        // ванильная ячейка вещи - под две: вещь и оплата
        drawRect(l + 24, t + 46, l + 42, t + 64, 0xFFC6C6C6);
        for (int s = 0; s < ContainerCorruptAltar.SIZE; s++) {
            Slot slot = (Slot) container.inventorySlots.get(s);
            frame(l + slot.xDisplayPosition, t + slot.yDisplayPosition);
        }
        // кнопки "узнать"
        for (int i = 0; i < AltarLogic.SLOTS; i++) {
            int x = l + EYE_X, y = t + ROW_Y + ROW_H * i;
            boolean shut = container.levels[i] <= 0, known = container.knownCount[i] >= 0;
            int fill = shut ? 0xFF6E6E6E : known ? 0xFF3A2450 : canReveal(i) && inEye(mx, my, i) ? 0xFFB89AE0 : 0xFF8E7AAE;
            drawRect(x, y, x + 18, y + 18, 0xFF2A1640);
            drawRect(x + 1, y + 1, x + 17, y + 17, fill);
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawBook(partial);

        EnchantmentNameParts.instance.reseedRandomGenerator(nameSeed);
        for (int i = 0; i < AltarLogic.SLOTS; i++) {
            String runes = EnchantmentNameParts.instance.generateNewRandomName();
            int x = l + ROW_X, y = t + ROW_Y + ROW_H * i, lvl = container.levels[i];
            boolean can = canEnchant(i), hover = can && inRow(mx, my, i);
            int v = lvl == 0 || !can ? 185 : hover ? 204 : 166;
            zLevel = 0.0F;
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(TEX);
            drawTexturedModalRect(x, y, 0, v, ROW_W - 1, ROW_H);
            drawTexturedModalRect(x + ROW_W - 1, y, 107, v, 1, ROW_H);
            if (lvl == 0) continue;
            String cost = "" + lvl;
            int costW = fontRendererObj.getStringWidth(cost);
            int color = !can ? 0x342F25 : hover ? 0xFFFF80 : 0x685E4A;
            if (container.knownCount[i] < 0) {
                mc.standardGalacticFontRenderer.drawSplitString(runes, x + 2, y + 2, ROW_W - 4, color);
            } else {
                List<String> n = names(i);
                String first = n.isEmpty() ? "" : n.get(0), rest = "";
                for (int k = 1; k < n.size(); k++) rest += (k > 1 ? ", " : "") + n.get(k);
                int known = !can ? 0x2A1A3A : hover ? 0xFFFF80 : 0x3A1A5A;
                fontRendererObj.drawString(fit(first, ROW_W - 4), x + 2, y + 2, known);
                fontRendererObj.drawString(fit(rest, ROW_W - 7 - costW), x + 2, y + 10, known);
            }
            fontRendererObj.drawStringWithShadow(cost, x + ROW_W - 2 - costW, y + 9, can ? 0x80FF20 : 0x407F10);
        }
    }

    /** Строка по ширине; не влезла - с ".." (полный список - в подсказке). */
    private String fit(String s, int w) {
        if (fontRendererObj.getStringWidth(s) <= w) return s;
        return fontRendererObj.trimStringToWidth(s, w - fontRendererObj.getStringWidth("..")) + "..";
    }

    /** Книга над ячейками - как у ванильного стола. */
    private void drawBook(float partial) {
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        GL11.glViewport((sr.getScaledWidth() - 320) / 2 * sr.getScaleFactor(), (sr.getScaledHeight() - 240) / 2 * sr.getScaleFactor(),
            320 * sr.getScaleFactor(), 240 * sr.getScaleFactor());
        GL11.glTranslatef(-0.34F, 0.23F, 0.0F);
        Project.gluPerspective(90.0F, 1.3333334F, 9.0F, 80.0F);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        RenderHelper.enableStandardItemLighting();
        GL11.glTranslatef(0.0F, 3.3F, -16.0F);
        GL11.glScalef(5.0F, 5.0F, 5.0F);
        GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
        mc.getTextureManager().bindTexture(BOOK);
        GL11.glRotatef(20.0F, 1.0F, 0.0F, 0.0F);
        float o = oOpen + (open - oOpen) * partial;
        GL11.glTranslatef((1.0F - o) * 0.2F, (1.0F - o) * 0.1F, (1.0F - o) * 0.25F);
        GL11.glRotatef(-(1.0F - o) * 90.0F - 90.0F, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
        float f = oFlip + (flip - oFlip) * partial;
        float p1 = page(f + 0.25F), p2 = page(f + 0.75F);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        BOOK_MODEL.render((Entity) null, 0.0F, p1, p2, o, 0.0F, 0.0625F);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        RenderHelper.disableStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static float page(float f) {
        return Math.max(0.0F, Math.min(1.0F, (f - MathHelper.truncateDoubleToInt(f)) * 1.6F - 0.3F));
    }

    /** Подсказки: слот - узнанные чары или "скрыты", цена; кнопка - что стоит узнать; пустая оплата - чем платить. */
    @Override
    @SuppressWarnings("unchecked")
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        List<String> tip = new ArrayList<String>();
        boolean book = item() != null && item().getItem() == Items.book;
        for (int i = 0; i < AltarLogic.SLOTS; i++) {
            int lvl = container.levels[i];
            if (lvl <= 0) continue;
            if (inRow(mx, my, i)) {
                if (container.knownCount[i] >= 0) {
                    for (String n : names(i)) tip.add(EnumChatFormatting.LIGHT_PURPLE + n);
                } else {
                    tip.add(EnumChatFormatting.GRAY + t("dre.altar.hidden"));
                    tip.add(EnumChatFormatting.GOLD + t("dre.altar.grade"));
                }
                if (book) tip.add(EnumChatFormatting.GRAY + t("dre.altar.book"));
                tip.add((canEnchant(i) ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + StatCollector.translateToLocalFormatted("dre.altar.cost", lvl));
            } else if (inEye(mx, my, i)) {
                if (container.knownCount[i] >= 0) tip.add(EnumChatFormatting.GREEN + t("dre.altar.revealed"));
                else if (mc.thePlayer.capabilities.isCreativeMode || container.price[i] <= 0) tip.add(EnumChatFormatting.GREEN + t("dre.altar.reveal_free"));
                else {
                    String what = new ItemStack(AltarLogic.revealItem(item(), i)).getDisplayName();
                    tip.add((paid(i) ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + StatCollector.translateToLocalFormatted("dre.altar.reveal", container.price[i], what));
                    if (!paid(i)) tip.add(EnumChatFormatting.GRAY + t("dre.altar.reveal_need"));
                }
                tip.add(EnumChatFormatting.DARK_GRAY + t("dre.altar.keep"));
            }
        }
        Slot paySlot = (Slot) container.inventorySlots.get(ContainerCorruptAltar.PAY);
        int px = mx - guiLeft - paySlot.xDisplayPosition, py = my - guiTop - paySlot.yDisplayPosition;
        if (tip.isEmpty() && pay() == null && px >= 0 && py >= 0 && px < 16 && py < 16) {
            ItemStack s = item();
            if (s == null) tip.add(EnumChatFormatting.GRAY + t("dre.altar.pay_any"));
            else {
                String first = new ItemStack(AltarLogic.revealItem(s, 0)).getDisplayName(), last = new ItemStack(AltarLogic.revealItem(s, AltarLogic.SLOTS - 1)).getDisplayName();
                tip.add(EnumChatFormatting.GRAY + (first.equals(last) ? StatCollector.translateToLocalFormatted("dre.altar.pay_one", first)
                    : StatCollector.translateToLocalFormatted("dre.altar.pay", first, last)));
            }
        }
        if (tip.isEmpty()) return;
        // drawHoveringText строки не переносит: длинная подсказка уходила за экран
        int room = Math.min(200, Math.max(width - mx, mx) - 24);
        List<String> lines = new ArrayList<String>();
        for (String s : tip) lines.addAll(fontRendererObj.listFormattedStringToWidth(s, room));
        drawHoveringText(lines, mx, my, fontRendererObj);
    }
}
