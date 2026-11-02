package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.station.TileWandererAnvil;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Наковальня Странника: своя модель вместо куба - тёмное железо с рогом, реалмитовые пояса, молот на рабочей поверхности,
 * фиолетовые камни-гнёзда спереди и сзади светятся. Модель - models/wanderer_anvil.mesh с атласом
 * textures/models/wanderer_anvil.png (собирает anvil_model.py). В мире поворот по мете (камнем к тому, кто ставил), в руке и
 * в инвентаре - та же модель.
 */
@SideOnly(Side.CLIENT)
public class RenderWandererAnvil extends TileEntitySpecialRenderer implements IItemRenderer {

    static final ResourceLocation TEX = new ResourceLocation("divinerpgextended", "textures/models/wanderer_anvil.png");
    static final EasterMesh MESH = new EasterMesh(new ResourceLocation("divinerpgextended", "models/wanderer_anvil.mesh"));
    /** Поворот по мете 0-3 - как у дивана: лицом к игроку, который ставил. */
    static final float[] YAW = { 180F, 90F, 0F, 270F };

    public static void register() {
        RenderWandererAnvil r = new RenderWandererAnvil();
        ClientRegistry.bindTileEntitySpecialRenderer(TileWandererAnvil.class, r);
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(ModBlocks.wandererAnvil), r);
    }

    /** Модель вокруг начала координат: пол блока на y = -0.5, середина на y = 0. */
    private static void draw() {
        GL11.glTranslatef(0F, 1.0F, 0F);
        GL11.glRotatef(180F, 0, 0, 1); // модель в координатах Minecraft-моделей: y вниз
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEX);
        MESH.render("anvil", "body", 0.0625F);
        EasterClient.glow(true);
        MESH.render("anvil", "glow", 0.0625F);
        EasterClient.glow(false);
    }

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float pt) {
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(YAW[te.getBlockMetadata() & 3], 0, 1, 0);
        draw();
        GL11.glPopMatrix();
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.INVENTORY || type == ItemRenderType.ENTITY || type == ItemRenderType.EQUIPPED
            || type == ItemRenderType.EQUIPPED_FIRST_PERSON;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return type == ItemRenderType.INVENTORY && helper == ItemRendererHelper.INVENTORY_BLOCK
            || type == ItemRenderType.ENTITY && (helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING);
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();
        // в руке предмет рисуется в кубе 0..1, в инвентаре и на земле - вокруг центра
        if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) GL11.glTranslatef(0.5F, 0.5F, 0.5F);
        else GL11.glRotatef(180F, 0, 1, 0); // в инвентаре - камнем и рогом к зрителю
        draw();
        GL11.glPopMatrix();
    }
}
