package com.divinerpgextended.client;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.TextureStitchEvent;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.easter.BlockSofa;
import com.divinerpgextended.easter.Easter;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/** Look of the Easter eggs ({@link Easter}): icons and models of renamed items, the H.E.V. suit (helmet or goggles), the crowbar, the PG-7 grenade, the sofa with an RPG. */
public final class EasterClient {

    private EasterClient() {}

    static final String D = "divinerpgextended:";
    static final ResourceLocation HEV = new ResourceLocation(D + "textures/models/easter/hev.png"), CROWBAR = new ResourceLocation(D + "textures/models/easter/crowbar.png"),
        SOFA = new ResourceLocation(D + "textures/models/easter/sofa.png"), RPG = new ResourceLocation(D + "textures/models/easter/rpg.png"),
        MUSOU = new ResourceLocation(D + "textures/models/easter/musou.png");
    static IIcon crowbar, rpg, musou, hevHelmet, hevGoggles, hevChest, hevLegs, hevBoots;
    static final EasterMesh MUSOU_MESH = new EasterMesh("musou");
    static final EasterModels.HevArmor HEV_MODEL = new EasterModels.HevArmor();
    static final EasterMesh CROWBAR_MESH = new EasterMesh("crowbar");
    static final EasterModels.Sofa SOFA_MODEL = new EasterModels.Sofa();
    /** The RPG-7 on the sofa (rpg/sofa) and the PG-7 grenade in flight (pg7/flight) - with the proportions of the real ones. */
    static final EasterMesh RPG_MESH = new EasterMesh("rpg");

    /** The Easter egg icon for an item, or null (an ordinary item). */
    static IIcon icon(ItemStack s) {
        if (Easter.crowbar(s)) return crowbar;
        if (Easter.musou(s)) return musou;
        if (Easter.rpg(s)) return rpg;
        if (!Easter.hev(s)) return null;
        Item i = s.getItem();
        return i == net.divinerpg.utils.items.VanillaItemsArmor.realmiteHelmet ? (Easter.goggles(s) ? hevGoggles : hevHelmet) : i == net.divinerpg.utils.items.VanillaItemsArmor.realmiteBody ? hevChest
            : i == net.divinerpg.utils.items.VanillaItemsArmor.realmiteLegs ? hevLegs : hevBoots;
    }

    /** The suit part by the armor item: helmet/goggles, chest, legs, boots. */
    static String piece(ItemStack s) {
        int type = ((net.minecraft.item.ItemArmor) s.getItem()).armorType;
        return type == 0 ? (Easter.goggles(s) ? "goggles" : "helmet") : type == 1 ? "chest" : type == 2 ? "legs" : "boots";
    }

    /** The suit armor model for stands and mobs ({@link com.divinerpgextended.asm.EasterHooks}); null if not a suit. */
    public static net.minecraft.client.model.ModelBiped armorModel(ItemStack s) {
        if (!Easter.hev(s) || !(s.getItem() instanceof net.minecraft.item.ItemArmor)) return null;
        HEV_MODEL.piece = piece(s);
        HEV_MODEL.owner = null;
        return HEV_MODEL;
    }

    public static String armorTexture(ItemStack s) {
        return Easter.hev(s) ? HEV.toString() : null;
    }

    public static void init() {
        Events ev = new Events();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ev);
        ClientRegistry.bindTileEntitySpecialRenderer(BlockSofa.TileSofa.class, new SofaRenderer());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.easter.EntitySeat.class, new Render() {

            @Override
            public void doRender(Entity e, double x, double y, double z, float yaw, float pt) {}

            @Override
            protected ResourceLocation getEntityTexture(Entity e) {
                return null;
            }
        });
        HevSounds.init();
    }

    /** After Divine: wrappers over its renderers (ordinary items and projectiles are drawn as before). */
    @SuppressWarnings("unchecked")
    public static void postInit() {
        Map<Item, IItemRenderer> custom;
        try {
            custom = ReflectionHelper.getPrivateValue(MinecraftForgeClient.class, null, "customItemRenderers");
        } catch (Throwable t) {
            custom = null;
        }
        for (Item i : new Item[] { net.divinerpg.utils.items.VanillaItemsWeapons.realmiteSword, net.divinerpg.utils.items.ArcanaItems.meriksMissile,
            net.divinerpg.utils.items.ArcanaItems.stormSword,
            net.divinerpg.utils.items.VanillaItemsArmor.realmiteHelmet, net.divinerpg.utils.items.VanillaItemsArmor.realmiteBody,
            net.divinerpg.utils.items.VanillaItemsArmor.realmiteLegs, net.divinerpg.utils.items.VanillaItemsArmor.realmiteBoots }) {
            IItemRenderer prev = custom == null ? null : custom.get(i);
            MinecraftForgeClient.registerItemRenderer(i, new ItemIcons(prev));
        }
        Render prev = (Render) RenderManager.instance.entityRenderMap.get(net.divinerpg.entities.arcana.projectile.EntityMerikMissile.class);
        Render rocket = new RocketRenderer(prev);
        rocket.setRenderManager(RenderManager.instance);
        RenderManager.instance.entityRenderMap.put(net.divinerpg.entities.arcana.projectile.EntityMerikMissile.class, rocket);
    }

    public static class Events {

        @SubscribeEvent
        public void onStitch(TextureStitchEvent.Pre e) {
            if (e.map.getTextureType() != 1) return;
            crowbar = e.map.registerIcon(D + "easter/crowbar");
            musou = e.map.registerIcon(D + "easter/musou");
            rpg = e.map.registerIcon(D + "easter/rpg");
            hevHelmet = e.map.registerIcon(D + "easter/hev_helmet");
            hevGoggles = e.map.registerIcon(D + "easter/hev_goggles");
            hevChest = e.map.registerIcon(D + "easter/hev_chest");
            hevLegs = e.map.registerIcon(D + "easter/hev_legs");
            hevBoots = e.map.registerIcon(D + "easter/hev_boots");
        }

        /**
         * The H.E.V. suit on the player - its own model instead of Realmite armor. The part is by the armor item type (the pass number in this
         * event goes backwards: 3 - helmet, 0 - boots).
         */
        @SubscribeEvent
        public void onArmor(RenderPlayerEvent.SetArmorModel e) {
            if (!Easter.hev(e.stack) || !(e.stack.getItem() instanceof net.minecraft.item.ItemArmor)) return;
            EasterModels.HevArmor m = HEV_MODEL;
            m.piece = piece(e.stack);
            ModelWandererArmor.pose(m, e.entityPlayer, e.partialRenderTick);
            m.owner = e.renderer.modelBipedMain;
            Minecraft.getMinecraft().getTextureManager().bindTexture(HEV);
            e.renderer.setRenderPassModel(m);
            e.result = e.stack.isItemEnchanted() ? 15 : 1;
        }
    }

    /** Icons of renamed items (ordinary ones - with Divine's previous renderer or the vanilla one). */
    static class ItemIcons implements IItemRenderer {

        final IItemRenderer prev;

        ItemIcons(IItemRenderer prev) {
            this.prev = prev;
        }

        @Override
        public boolean handleRenderType(ItemStack s, ItemRenderType type) {
            if (icon(s) != null) return type != ItemRenderType.FIRST_PERSON_MAP;
            return prev != null && prev.handleRenderType(s, type);
        }

        @Override
        public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack s, ItemRendererHelper helper) {
            if (icon(s) != null) return type == ItemRenderType.ENTITY && (helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING);
            return prev != null && prev.shouldUseRenderHelper(type, s, helper);
        }

        @Override
        public void renderItem(ItemRenderType type, ItemStack s, Object... data) {
            IIcon i = icon(s);
            if (i == null) {
                if (prev != null) prev.renderItem(type, s, data);
                return;
            }
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationItemsTexture);
            GL11.glColor4f(1, 1, 1, 1);
            if (type == ItemRenderType.INVENTORY) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                RenderItem.getInstance().renderIcon(0, 0, i, 16, 16);
                return;
            }
            if (type == ItemRenderType.ENTITY) GL11.glTranslatef(-0.5F, -0.25F, 0F);
            if (Easter.crowbar(s)) { // the crowbar in hand and on the ground is 3D, in the same layout as the sword icon
                Minecraft.getMinecraft().getTextureManager().bindTexture(CROWBAR);
                CROWBAR_MESH.render("crowbar", "item", 1F / 16F);
                return;
            }
            if (Easter.rpg(s)) { // the RPG-7 is 3D, the same model as on the sofa
                Minecraft.getMinecraft().getTextureManager().bindTexture(RPG);
                RPG_MESH.render("rpg", "item", 1F / 16F);
                return;
            }
            if (Easter.musou(s)) { // Musou no Hitotachi - a 3D katana, the blade and the emblem glow
                Minecraft.getMinecraft().getTextureManager().bindTexture(MUSOU);
                if (type != ItemRenderType.ENTITY) { // the katana is longer than a sword: in hand x KATANA around the middle of the handle
                    GL11.glTranslatef(13.7F / 16F, 2.3F / 16F, -0.03F); // the middle of the Storm Sword handle on the icon (2.3, 2.3)
                    GL11.glScalef(KATANA, KATANA, KATANA);
                    GL11.glTranslatef(-13.73F / 16F, -2.27F / 16F, 0.03F); // the middle of the katana handle (musou_model.py prints it)
                } else { // on the ground x KATANA_GROUND around the middle of the item
                    GL11.glTranslatef(0.5F, 0.5F, -0.03F);
                    GL11.glScalef(KATANA_GROUND, KATANA_GROUND, KATANA_GROUND);
                    GL11.glTranslatef(-0.5F, -0.5F, 0.03F);
                }
                MUSOU_MESH.render("musou", "item", 1F / 16F);
                glow(true);
                MUSOU_MESH.render("musou", "glow", 1F / 16F);
                glow(false);
                return;
            }
            ItemRenderer.renderItemIn2D(Tessellator.instance, i.getMaxU(), i.getMinV(), i.getMinU(), i.getMaxV(), i.getIconWidth(), i.getIconHeight(), 0.0625F);
        }
    }

    /** How many times larger Musou is than its model: in hand and on the ground. */
    static final float KATANA = 1.8F, KATANA_GROUND = 1.5F;

    private static float lastX, lastY;
    private static boolean lit;

    /** Glow: full brightness and no shadows (true), then restore as it was (false). */
    static void glow(boolean on) {
        if (on) {
            lastX = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX;
            lastY = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY;
            lit = GL11.glIsEnabled(GL11.GL_LIGHTING);
            net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240F, 240F);
            GL11.glDisable(GL11.GL_LIGHTING);
        } else {
            net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, lastX, lastY);
            if (lit) GL11.glEnable(GL11.GL_LIGHTING);
        }
    }

    /** Merik's rocket from the RPG-7 - a PG-7 grenade with a smoke trail; the others - with Divine's renderer. */
    static class RocketRenderer extends Render {

        final Render prev;
        final Map<Entity, Boolean> rocket = new WeakHashMap<Entity, Boolean>();

        RocketRenderer(Render prev) {
            this.prev = prev;
        }

        @Override
        public void setRenderManager(RenderManager m) {
            super.setRenderManager(m);
            if (prev != null) prev.setRenderManager(m);
        }

        /** Fired from an RPG-7: on the first frame a player with it in hand stands nearby. */
        boolean isRocket(Entity e) {
            Boolean b = rocket.get(e);
            if (b == null) {
                EntityPlayer p = e.worldObj.getClosestPlayerToEntity(e, 4);
                rocket.put(e, b = p != null && Easter.rpg(p.getHeldItem()));
            }
            return b;
        }

        @Override
        public void doRender(Entity e, double x, double y, double z, float yaw, float pt) {
            if (!isRocket(e)) {
                if (prev != null) prev.doRender(e, x, y, z, yaw, pt);
                return;
            }
            double h = Math.sqrt(e.motionX * e.motionX + e.motionZ * e.motionZ);
            float ry = (float) Math.toDegrees(Math.atan2(e.motionX, e.motionZ)), rp = (float) Math.toDegrees(Math.atan2(e.motionY, h));
            GL11.glPushMatrix();
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(ry, 0, 1, 0);
            GL11.glRotatef(-rp, 1, 0, 0);
            bindTexture(RPG);
            RPG_MESH.render("pg7", "flight", 0.0625F);
            GL11.glPopMatrix();
            if (e.worldObj.rand.nextBoolean()) e.worldObj.spawnParticle("smoke", e.posX - e.motionX * 0.5, e.posY - e.motionY * 0.5, e.posZ - e.motionZ * 0.5, 0, 0.01, 0);
        }

        @Override
        protected ResourceLocation getEntityTexture(Entity e) {
            return RPG;
        }
    }

    /** The sofa with an RPG: the model on the first half, over both blocks. */
    static class SofaRenderer extends TileEntitySpecialRenderer {

        static final float[] YAW = { 180F, 90F, 0F, 270F };

        @Override
        public void renderTileEntityAt(TileEntity te, double x, double y, double z, float pt) {
            int f = te.getBlockMetadata() & 3;
            GL11.glPushMatrix();
            GL11.glTranslated(x + 0.5 + BlockSofa.side(f)[0] * 0.5, y + 1.5, z + 0.5 + BlockSofa.side(f)[1] * 0.5);
            GL11.glRotatef(YAW[f], 0, 1, 0);
            GL11.glRotatef(180F, 0, 0, 1);
            bindTexture(SOFA);
            SOFA_MODEL.render(null, 0, 0, 0, 0, 0, 0.0625F);
            // the RPG-7 lies on the seat cushions: on its side (handles toward the edge), slightly diagonal, between the armrests and in front of the
            // back cushions. Scale 0.7 - 1.4 blocks, like the real one with a grenade; the position is calculated from the mesh vertices,
            // none of them goes into the sofa
            GL11.glTranslatef(-11.11F / 16F, 10.77F / 16F, -0.84F / 16F);
            GL11.glRotatef(6F, 0, 1, 0);
            GL11.glRotatef(-90F, 1, 0, 0);
            bindTexture(RPG);
            RPG_MESH.render("rpg", "sofa", 0.0625F * 0.7F);
            GL11.glPopMatrix();
        }
    }
}
