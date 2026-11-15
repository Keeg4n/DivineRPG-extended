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
 * Wanderer Anvil: its own model instead of a cube (since 0.99.108 - simple, in the style of Divine models: 6 boxes, a 64x64 atlas) -
 * dark iron with a horn, Realmite belts, purple socket stones at the front and back glow. The model is models/wanderer_anvil.mesh with the atlas
 * textures/models/wanderer_anvil.png (built by anvil_model.py). In the world it rotates by metadata (stone toward whoever placed it), in hand and
 * in the inventory - the same model.
 */
@SideOnly(Side.CLIENT)
public class RenderWandererAnvil extends TileEntitySpecialRenderer implements IItemRenderer {

    static final ResourceLocation TEX = new ResourceLocation("divinerpgextended", "textures/models/wanderer_anvil.png");
    static final EasterMesh MESH = new EasterMesh(new ResourceLocation("divinerpgextended", "models/wanderer_anvil.mesh"));
    /** Rotation by metadata 0-3 - like the sofa: facing the player who placed it. */
    static final float[] YAW = { 180F, 90F, 0F, 270F };

    public static void register() {
        RenderWandererAnvil r = new RenderWandererAnvil();
        ClientRegistry.bindTileEntitySpecialRenderer(TileWandererAnvil.class, r);
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(ModBlocks.wandererAnvil), r);
    }

    /** The model around the origin: the floor of the block at y = -0.5, the middle at y = 0. */
    private static void draw() {
        GL11.glTranslatef(0F, 1.0F, 0F);
        GL11.glRotatef(180F, 0, 0, 1); // the model is in Minecraft model coordinates: y points down
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
        // in hand an item is drawn in the cube 0..1, in the inventory and on the ground - around the center
        if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) GL11.glTranslatef(0.5F, 0.5F, 0.5F);
        else GL11.glRotatef(180F, 0, 1, 0); // in the inventory - with the stone and the horn toward the viewer
        draw();
        GL11.glPopMatrix();
    }
}
