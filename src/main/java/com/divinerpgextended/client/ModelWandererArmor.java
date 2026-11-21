package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemArmor;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.item.ArmorData;
import com.divinerpgextended.item.ItemWandererArmor;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Wanderer armor look by tier R1-R7, the first tier core and the Crown R8: mesh and atlas from tools/wanderer_armor.py. The pose is like the player's armor (ModelBiped
 * bones), on each bone are the details of the worn part (helmet, chest, legs, boots), like the H.E.V. suit. Glowing details
 * are the second pass: the same mesh with the mask r&lt;N&gt;_glow.png at full brightness. The dyed part is one more pass before the glow:
 * the gray mask of the main material r&lt;N&gt;_dye.png, multiplied by the color.
 */
public class ModelWandererArmor extends ModelBiped {

    private static final String D = "divinerpgextended";
    private static final String[] PIECES = { "helmet", "chest", "legs", "boots" };
    private static final int N = ItemWandererArmor.LOOK_COUNT;
    private static final EasterMesh[] MESH = new EasterMesh[N + 1];
    private static final ResourceLocation[] TEX = new ResourceLocation[N + 1], GLOW = new ResourceLocation[N + 1], DYE = new ResourceLocation[N + 1];
    private static final Boolean[] HAS_GLOW = new Boolean[N + 1], HAS_DYE = new Boolean[N + 1];
    /** Players and mobs/stands have separate instances: a mod asking an item for its mob model mid-frame must not reset a player's pose. */
    private static final ModelWandererArmor MODEL = new ModelWandererArmor(), MOB_MODEL = new ModelWandererArmor();

    static {
        for (int t = 1; t <= N; t++) {
            MESH[t] = new EasterMesh(new ResourceLocation(D, "models/wanderer/" + ItemWandererArmor.lookName(t) + ".mesh"));
            TEX[t] = new ResourceLocation(ItemWandererArmor.lookTexture(t));
            GLOW[t] = new ResourceLocation(D, "textures/models/wanderer/" + ItemWandererArmor.lookName(t) + "_glow.png");
            DYE[t] = new ResourceLocation(D, "textures/models/wanderer/" + ItemWandererArmor.lookName(t) + "_dye.png");
        }
    }

    private int tier = 1;
    private String piece = "chest";
    /** The main model of the player wearing it (null - a mob or a stand): the item in hand is drawn on its arms ({@link #lend}). */
    private ModelBiped owner;
    /** Dye color 0xRRGGBB or -1. */
    private int color = -1;

    private ModelWandererArmor() {
        super(0F);
    }

    /** Model of an armor part (armorType 0-3) of tier tier for mobs and stands: a pose without items in hands and without sneaking. */
    public static ModelBiped forMob(int tier, int armorType, int color) {
        ModelWandererArmor m = select(MOB_MODEL, tier, armorType, color);
        m.owner = null;
        m.heldItemLeft = m.heldItemRight = 0;
        m.isSneak = m.aimedBow = false;
        return m;
    }

    private static ModelWandererArmor select(ModelWandererArmor m, int tier, int armorType, int color) {
        m.tier = tier;
        m.color = color;
        m.piece = PIECES[Math.max(0, Math.min(3, armorType))];
        return m;
    }

    /**
     * The pose of a player's armor straight from the player, the same way RenderPlayer poses its own models: swing, riding, item in hand,
     * blocking, drawn bow, sneaking. Not copied from the main model - other mods may leave its fields in another state.
     */
    public static void pose(ModelBiped m, net.minecraft.entity.player.EntityPlayer p, float partial) {
        if (m instanceof ModelWandererArmor) ((ModelWandererArmor) m).owner = null;
        m.onGround = p.getSwingProgress(partial);
        m.isRiding = p.isRiding();
        m.isChild = p.isChild();
        net.minecraft.item.ItemStack held = p.inventory.getCurrentItem();
        m.heldItemLeft = 0;
        m.heldItemRight = held != null ? 1 : 0;
        m.aimedBow = false;
        if (held != null && p.getItemInUseCount() > 0) {
            net.minecraft.item.EnumAction a = held.getItemUseAction();
            if (a == net.minecraft.item.EnumAction.block) m.heldItemRight = 3;
            else if (a == net.minecraft.item.EnumAction.bow) m.aimedBow = true;
        }
        m.isSneak = p.isSneaking();
    }

    @Override
    public void render(Entity e, float f, float f1, float f2, float f3, float f4, float scale) {
        setRotationAngles(f, f1, f2, f3, f4, scale, e);
        lend(this, owner);
        EasterMesh mesh = MESH[tier];
        // Wraith R8: the base is translucent (alpha in the atlas), the glow and the darkness under the hood are not. Only in the normal pass: in the pass
        // of the enchantment glint the blending is its own
        boolean glass = ItemWandererArmor.translucent(tier) && bound(TEX[tier]), wasBlend = glass && GL11.glIsEnabled(GL11.GL_BLEND);
        if (glass) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        draw(mesh, scale);
        if (glass && !wasBlend) GL11.glDisable(GL11.GL_BLEND);
        // dye and glow only in the normal pass (in the enchantment glint pass a different texture is bound)
        if (!bound(TEX[tier])) return;
        TextureManager tm = Minecraft.getMinecraft().getTextureManager();
        if (color >= 0 && has(DYE, HAS_DYE, tier)) {
            tm.bindTexture(DYE[tier]);
            GL11.glColor4f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, 1F);
            draw(mesh, scale);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            tm.bindTexture(TEX[tier]);
        }
        if (has(GLOW, HAS_GLOW, tier)) {
            tm.bindTexture(GLOW[tier]);
            EasterClient.glow(true);
            draw(mesh, scale);
            EasterClient.glow(false);
            tm.bindTexture(TEX[tier]);
        }
    }

    private void draw(EasterMesh mesh, float scale) {
        part(mesh, bipedHead, "head", scale);
        part(mesh, bipedBody, "body", scale);
        skirt(mesh, scale);
        part(mesh, bipedRightArm, "rightArm", scale);
        part(mesh, bipedLeftArm, "leftArm", scale);
        part(mesh, bipedRightLeg, "rightLeg", scale);
        part(mesh, bipedLeftLeg, "leftLeg", scale);
    }

    private void part(EasterMesh mesh, ModelRenderer bone, String name, float scale) {
        if (!mesh.has(piece, name)) return;
        GL11.glPushMatrix();
        bone.postRender(scale);
        mesh.render(piece, name, scale);
        GL11.glPopMatrix();
    }

    /**
     * The skirt - leg-piece details hanging below the belt (Wraith tatters, the R6 tail, R7 plates): it hangs from the waist and does not tilt
     * with the body. When sneaking the body leans forward and the skirt drawn on the body flew back like a tail (1.1.10).
     */
    private void skirt(EasterMesh mesh, float scale) {
        if (!mesh.has(piece, "skirt")) return;
        GL11.glPushMatrix();
        bipedBody.postRender(scale);
        if (bipedBody.rotateAngleX != 0F) {
            GL11.glTranslatef(0F, WAIST * scale, 0F);
            GL11.glRotatef(-bipedBody.rotateAngleX * (180F / (float) Math.PI), 1F, 0F, 0F);
            GL11.glTranslatef(0F, -WAIST * scale, 0F);
        }
        mesh.render(piece, "skirt", scale);
        GL11.glPopMatrix();
    }

    /** Waist height on the body (model pixels from the neck). */
    private static final float WAIST = 12F;

    /**
     * The arms of the armor go to the player's main model: the game draws the item in hand after the armor, on the arm of the main model. In some
     * builds the main model stays in the rest pose (other mods), then the armor arms moved while the bow or the sword hung still (1.1.11).
     */
    public static void lend(ModelBiped from, ModelBiped to) {
        if (to == null || to == from) return;
        copy(from.bipedRightArm, to.bipedRightArm);
        copy(from.bipedLeftArm, to.bipedLeftArm);
    }

    private static void copy(net.minecraft.client.model.ModelRenderer a, net.minecraft.client.model.ModelRenderer b) {
        b.rotateAngleX = a.rotateAngleX;
        b.rotateAngleY = a.rotateAngleY;
        b.rotateAngleZ = a.rotateAngleZ;
        b.rotationPointX = a.rotationPointX;
        b.rotationPointY = a.rotationPointY;
        b.rotationPointZ = a.rotationPointZ;
    }

    private static boolean bound(ResourceLocation loc) {
        ITextureObject t = Minecraft.getMinecraft().getTextureManager().getTexture(loc);
        return t != null && GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == t.getGlTextureId();
    }

    /** Whether the look has this texture (not all of them have glow and dye masks). */
    private static boolean has(ResourceLocation[] tex, Boolean[] known, int tier) {
        if (known[tier] == null) {
            try {
                Minecraft.getMinecraft().getResourceManager().getResource(tex[tier]);
                known[tier] = true;
            } catch (Exception ex) {
                known[tier] = false;
            }
        }
        return known[tier];
    }

    /** On the player: the pose and texture are like the H.E.V. suit (the pass number in the event goes backwards, the part is by item). */
    public static class Events {

        /** An empty skin: the body is not drawn, while everything held on its bones (the item in hand, accessories) stays in place. */
        private static final ResourceLocation CLEAR = new ResourceLocation(D, "textures/entity/clear_skin.png");
        private static java.lang.reflect.Field skin;
        private net.minecraft.client.entity.AbstractClientPlayer hidden;
        private ResourceLocation saved;

        /** Full Wraith set (Crown of Flight R8). */
        static boolean ghost(net.minecraft.entity.player.EntityPlayer p) {
            for (int i = 0; i < 4; i++) {
                net.minecraft.item.ItemStack s = p.getCurrentArmor(i);
                if (s == null || !(s.getItem() instanceof ItemWandererArmor) || !ItemWandererArmor.translucent(ItemWandererArmor.look(s))) return false;
            }
            return true;
        }

        /**
         * The Wraith is translucent: the world must be visible through the plate, not the player's skin. While drawing a player in the full set his
         * skin is an empty texture (the body is nothing, instead of hiding the model parts: the item in hand and accessories are held on their bones).
         */
        @SubscribeEvent
        public void onPre(RenderPlayerEvent.Pre e) {
            if (!(e.entityPlayer instanceof net.minecraft.client.entity.AbstractClientPlayer) || !ghost(e.entityPlayer)) return;
            try {
                if (skin == null) skin = cpw.mods.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.entity.AbstractClientPlayer.class, "locationSkin", "field_110312_d");
                hidden = (net.minecraft.client.entity.AbstractClientPlayer) e.entityPlayer;
                saved = (ResourceLocation) skin.get(hidden);
                skin.set(hidden, CLEAR);
            } catch (Throwable t) {
                hidden = null;
            }
        }

        @SubscribeEvent(receiveCanceled = true)
        public void onPost(RenderPlayerEvent.Post e) {
            if (hidden == null || e.entityPlayer != hidden) return;
            try {
                skin.set(hidden, saved);
            } catch (Throwable ignored) {}
            hidden = null;
            saved = null;
        }

        /**
         * The BiblioCraft armor stand draws armor with its own model through a fake player, and his brightness is always full (day and
         * night, in light and in darkness) - the armor came out overlit. We set the light by the stand's own position: the fake player stands
         * in render coordinates (world minus camera).
         */
        static void standLight(net.minecraft.entity.player.EntityPlayer steve) {
            if (steve.worldObj == null) return;
            int x = net.minecraft.util.MathHelper.floor_double(steve.posX + net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.staticPlayerX);
            int y = net.minecraft.util.MathHelper.floor_double(steve.posY + net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.staticPlayerY);
            int z = net.minecraft.util.MathHelper.floor_double(steve.posZ + net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.staticPlayerZ);
            int b = Math.max(steve.worldObj.getLightBrightnessForSkyBlocks(x, y, z, 0), steve.worldObj.getLightBrightnessForSkyBlocks(x, y + 1, z, 0));
            net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, b % 65536, b / 65536);
        }

        @SubscribeEvent
        public void onArmor(RenderPlayerEvent.SetArmorModel e) {
            if (e.stack == null || !(e.stack.getItem() instanceof ItemWandererArmor)) return;
            if (!ItemWandererArmor.hasLook(ArmorData.tier(e.stack))) return;
            int look = ItemWandererArmor.look(e.stack);
            if ("jds.bibliocraft.AbstractSteve".equals(e.entityPlayer.getClass().getName())) standLight(e.entityPlayer);
            ModelWandererArmor m = select(MODEL, look, ((ItemArmor) e.stack.getItem()).armorType, ArmorData.color(e.stack));
            pose(m, e.entityPlayer, e.partialRenderTick);
            m.owner = e.renderer.modelBipedMain;
            Minecraft.getMinecraft().getTextureManager().bindTexture(TEX[look]);
            e.renderer.setRenderPassModel(m);
            e.result = e.stack.isItemEnchanted() ? 15 : 1;
        }
    }
}
