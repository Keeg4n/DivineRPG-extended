package com.divinerpgextended.client;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Лук аддона в руке от третьего лица (Цунами, Грозовой лук). Игра поворачивает "как лук" только сам ванильный лук (сравнение
 * с Items.bow), остальные луки держатся как плоская вещь - повёрнутыми. Здесь поворот плоской вещи снимается и ставится
 * поворот лука, как у ванильного (так делает и Divine для своих луков).
 */
@SideOnly(Side.CLIENT)
public class BowRender implements IItemRenderer {

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.EQUIPPED;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        if (data.length < 2 || !(data[1] instanceof EntityLivingBase)) return;
        EntityLivingBase e = (EntityLivingBase) data[1];
        GL11.glPopMatrix(); // без поворота плоской вещи из ForgeHooksClient.renderEquippedItem
        // снять поворот плоской вещи из RenderPlayer / RenderBiped
        float k = 0.375F;
        GL11.glRotatef(-20.0F, 0.0F, 0.0F, 1.0F);
        GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(-60.0F, 0.0F, 0.0F, 1.0F);
        GL11.glScalef(1.0F / k, 1.0F / k, 1.0F / k);
        GL11.glTranslatef(-0.25F, -0.1875F, 0.1875F);
        // поворот лука
        float b = 0.625F;
        GL11.glTranslatef(0.0F, 0.125F, 0.3125F);
        GL11.glRotatef(-20.0F, 0.0F, 1.0F, 0.0F);
        GL11.glScalef(b, -b, b);
        GL11.glRotatef(-100.0F, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
        draw(e, item);
        GL11.glPushMatrix(); // вернуть уровень стека для ForgeHooksClient
    }

    /** Плоская вещь с толщиной, как ItemRenderer.renderItem (значок натяжения - от того, кто держит). */
    private static void draw(EntityLivingBase e, ItemStack s) {
        IIcon icon = e.getItemIcon(s, 0);
        if (icon == null) return;
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glTranslatef(0.0F, -0.3F, 0.0F);
        GL11.glScalef(1.5F, 1.5F, 1.5F);
        GL11.glRotatef(50.0F, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(335.0F, 0.0F, 0.0F, 1.0F);
        GL11.glTranslatef(-0.9375F, -0.0625F, 0.0F);
        ItemRenderer.renderItemIn2D(Tessellator.instance, icon.getMaxU(), icon.getMinV(), icon.getMinU(), icon.getMaxV(), icon.getIconWidth(), icon.getIconHeight(), 0.0625F);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }
}
