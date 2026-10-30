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

/** Вид пасхалок ({@link Easter}): значки и модели переименованных вещей, костюм H.E.V. (шлем или очки), монтировка, граната ПГ-7, диван с РПГ. */
public final class EasterClient {

    private EasterClient() {}

    static final String D = "divinerpgextended:";
    static final ResourceLocation HEV = new ResourceLocation(D + "textures/models/easter/hev.png"), CROWBAR = new ResourceLocation(D + "textures/models/easter/crowbar.png"),
        SOFA = new ResourceLocation(D + "textures/models/easter/sofa.png"), PG7 = new ResourceLocation(D + "textures/models/easter/pg7.png");
    static IIcon crowbar, rpg, hevHelmet, hevGoggles, hevChest, hevLegs, hevBoots;
    static final EasterModels.HevArmor HEV_MODEL = new EasterModels.HevArmor();
    static final EasterMesh CROWBAR_MESH = new EasterMesh("crowbar");
    static final EasterModels.Sofa SOFA_MODEL = new EasterModels.Sofa();
    static final EasterModels.Pg7 PG7_MODEL = new EasterModels.Pg7();

    /** Значок пасхалки для вещи или null (обычная вещь). */
    static IIcon icon(ItemStack s) {
        if (Easter.crowbar(s)) return crowbar;
        if (Easter.rpg(s)) return rpg;
        if (!Easter.hev(s)) return null;
        Item i = s.getItem();
        return i == net.divinerpg.utils.items.VanillaItemsArmor.realmiteHelmet ? (Easter.goggles(s) ? hevGoggles : hevHelmet) : i == net.divinerpg.utils.items.VanillaItemsArmor.realmiteBody ? hevChest
            : i == net.divinerpg.utils.items.VanillaItemsArmor.realmiteLegs ? hevLegs : hevBoots;
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

    /** После Divine: обёртки над его рендерами (обычные вещи и снаряды рисуются как раньше). */
    @SuppressWarnings("unchecked")
    public static void postInit() {
        Map<Item, IItemRenderer> custom;
        try {
            custom = ReflectionHelper.getPrivateValue(MinecraftForgeClient.class, null, "customItemRenderers");
        } catch (Throwable t) {
            custom = null;
        }
        for (Item i : new Item[] { net.divinerpg.utils.items.VanillaItemsWeapons.realmiteSword, net.divinerpg.utils.items.ArcanaItems.meriksMissile,
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
            rpg = e.map.registerIcon(D + "easter/rpg");
            hevHelmet = e.map.registerIcon(D + "easter/hev_helmet");
            hevGoggles = e.map.registerIcon(D + "easter/hev_goggles");
            hevChest = e.map.registerIcon(D + "easter/hev_chest");
            hevLegs = e.map.registerIcon(D + "easter/hev_legs");
            hevBoots = e.map.registerIcon(D + "easter/hev_boots");
        }

        /**
         * Костюм H.E.V. на игроке - своя модель вместо реалмитовой брони. Часть - по типу вещи брони (номер прохода в этом
         * событии идёт задом наперёд: 3 - шлем, 0 - ботинки).
         */
        @SubscribeEvent
        public void onArmor(RenderPlayerEvent.SetArmorModel e) {
            if (!Easter.hev(e.stack) || !(e.stack.getItem() instanceof net.minecraft.item.ItemArmor)) return;
            int type = ((net.minecraft.item.ItemArmor) e.stack.getItem()).armorType;
            EasterModels.HevArmor m = HEV_MODEL;
            m.piece = type == 0 ? (Easter.goggles(e.stack) ? "goggles" : "helmet") : type == 1 ? "chest" : type == 2 ? "legs" : "boots";
            net.minecraft.client.model.ModelBiped main = e.renderer.modelBipedMain;
            m.onGround = main.onGround;
            m.isRiding = main.isRiding;
            m.isChild = main.isChild;
            m.heldItemLeft = main.heldItemLeft;
            m.heldItemRight = main.heldItemRight;
            m.isSneak = main.isSneak;
            m.aimedBow = main.aimedBow;
            Minecraft.getMinecraft().getTextureManager().bindTexture(HEV);
            e.renderer.setRenderPassModel(m);
            e.result = e.stack.isItemEnchanted() ? 15 : 1;
        }
    }

    /** Значки переименованных вещей (обычные - прежним рендером Divine или ванильным). */
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
            if (Easter.crowbar(s)) { // монтировка в руке и на земле - объёмная, в той же раскладке, что значок меча
                Minecraft.getMinecraft().getTextureManager().bindTexture(CROWBAR);
                CROWBAR_MESH.render("crowbar", "item", 1F / 16F);
                return;
            }
            ItemRenderer.renderItemIn2D(Tessellator.instance, i.getMaxU(), i.getMinV(), i.getMinU(), i.getMaxV(), i.getIconWidth(), i.getIconHeight(), 0.0625F);
        }
    }

    /** Ракета Мерика из РПГ-7 - граната ПГ-7 с дымным следом; остальные - рендером Divine. */
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

        /** Выпущена из РПГ-7: в первый кадр рядом стоит игрок с ним в руке. */
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
            bindTexture(PG7);
            PG7_MODEL.render(0.0625F);
            GL11.glPopMatrix();
            if (e.worldObj.rand.nextBoolean()) e.worldObj.spawnParticle("smoke", e.posX - e.motionX * 0.5, e.posY - e.motionY * 0.5, e.posZ - e.motionZ * 0.5, 0, 0.01, 0);
        }

        @Override
        protected ResourceLocation getEntityTexture(Entity e) {
            return PG7;
        }
    }

    /** Диван с РПГ: модель на первой половине, на оба блока. */
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
            GL11.glPopMatrix();
        }
    }
}
