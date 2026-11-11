package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.divinerpgextended.block.BlockLockedChest.Kind;
import com.divinerpgextended.block.TileLockedChest;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Запертый сундук: модель ванильного сундука со своей текстурой вида ({@link ChestTextures}), крышка открывается как у ванильного. */
@SideOnly(Side.CLIENT)
public class RenderLockedChest extends TileEntitySpecialRenderer implements IItemRenderer {

    private static final ModelChest MODEL = new ModelChest();
    private static final ResourceLocation[] TEX = new ResourceLocation[Kind.values().length];

    static {
        for (Kind k : Kind.values()) TEX[k.ordinal()] = new ResourceLocation(ChestTextures.DOMAIN, ChestTextures.path(k));
    }

    public static void register() {
        RenderLockedChest r = new RenderLockedChest();
        ClientRegistry.bindTileEntitySpecialRenderer(TileLockedChest.class, r);
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(ModBlocks.lockedChest), r);
    }

    /** Сундук в кубе 0..1 от текущей точки: сторона как у ванильного (2 - север, 3 - юг, 4 - запад, 5 - восток), крышка 0..1. */
    private static void draw(Kind k, int facing, float lid) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEX[k.ordinal()]);
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glTranslatef(0F, 1F, 1F);
        GL11.glScalef(1F, -1F, -1F);
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
        int angle = facing == 2 ? 180 : facing == 4 ? 90 : facing == 5 ? -90 : 0;
        GL11.glRotatef(angle, 0F, 1F, 0F);
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        float f = 1F - lid;
        f = 1F - f * f * f;
        MODEL.chestLid.rotateAngleX = -(f * (float) Math.PI / 2F);
        MODEL.renderAll();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float pt) {
        if (!(te instanceof TileLockedChest)) return;
        TileLockedChest c = (TileLockedChest) te;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        draw(c.hasWorldObj() ? c.kind() : Kind.SLUDGE, c.facing, c.prevLidAngle + (c.lidAngle - c.prevLidAngle) * pt);
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
        // в руке предмет рисуется в кубе 0..1, в инвентаре и на земле - вокруг центра; поворот как у ванильного сундука
        if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) GL11.glTranslatef(0.5F, 0.5F, 0.5F);
        GL11.glRotatef(90F, 0F, 1F, 0F);
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        draw(Kind.of(item.getItemDamage()), 3, 0F);
        GL11.glPopMatrix();
    }
}
