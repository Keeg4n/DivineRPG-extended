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
        SOFA = new ResourceLocation(D + "textures/models/easter/sofa.png"), RPG = new ResourceLocation(D + "textures/models/easter/rpg.png"),
        MUSOU = new ResourceLocation(D + "textures/models/easter/musou.png");
    static IIcon crowbar, rpg, musou, hevHelmet, hevGoggles, hevChest, hevLegs, hevBoots;
    static final EasterMesh MUSOU_MESH = new EasterMesh("musou");
    static final EasterModels.HevArmor HEV_MODEL = new EasterModels.HevArmor();
    static final EasterMesh CROWBAR_MESH = new EasterMesh("crowbar");
    static final EasterModels.Sofa SOFA_MODEL = new EasterModels.Sofa();
    /** РПГ-7 на диване (rpg/sofa) и граната ПГ-7 в полёте (pg7/flight) - по пропорциям настоящих. */
    static final EasterMesh RPG_MESH = new EasterMesh("rpg");

    /** Значок пасхалки для вещи или null (обычная вещь). */
    static IIcon icon(ItemStack s) {
        if (Easter.crowbar(s)) return crowbar;
        if (Easter.musou(s)) return musou;
        if (Easter.rpg(s)) return rpg;
        if (!Easter.hev(s)) return null;
        Item i = s.getItem();
        return i == net.divinerpg.utils.items.VanillaItemsArmor.realmiteHelmet ? (Easter.goggles(s) ? hevGoggles : hevHelmet) : i == net.divinerpg.utils.items.VanillaItemsArmor.realmiteBody ? hevChest
            : i == net.divinerpg.utils.items.VanillaItemsArmor.realmiteLegs ? hevLegs : hevBoots;
    }

    /** Часть костюма по вещи брони: helmet/goggles, chest, legs, boots. */
    static String piece(ItemStack s) {
        int type = ((net.minecraft.item.ItemArmor) s.getItem()).armorType;
        return type == 0 ? (Easter.goggles(s) ? "goggles" : "helmet") : type == 1 ? "chest" : type == 2 ? "legs" : "boots";
    }

    /** Модель брони костюма для стоек и мобов ({@link com.divinerpgextended.asm.EasterHooks}); не костюм - null. */
    public static net.minecraft.client.model.ModelBiped armorModel(ItemStack s) {
        if (!Easter.hev(s) || !(s.getItem() instanceof net.minecraft.item.ItemArmor)) return null;
        HEV_MODEL.piece = piece(s);
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
            net.divinerpg.utils.items.VanillaItemsWeapons.serenadeStriker,
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
         * Костюм H.E.V. на игроке - своя модель вместо реалмитовой брони. Часть - по типу вещи брони (номер прохода в этом
         * событии идёт задом наперёд: 3 - шлем, 0 - ботинки).
         */
        @SubscribeEvent
        public void onArmor(RenderPlayerEvent.SetArmorModel e) {
            if (!Easter.hev(e.stack) || !(e.stack.getItem() instanceof net.minecraft.item.ItemArmor)) return;
            EasterModels.HevArmor m = HEV_MODEL;
            m.piece = piece(e.stack);
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
            if (Easter.rpg(s)) { // РПГ-7 - объёмный, та же модель, что на диване
                Minecraft.getMinecraft().getTextureManager().bindTexture(RPG);
                RPG_MESH.render("rpg", "item", 1F / 16F);
                return;
            }
            if (Easter.musou(s)) { // Musou no Hitotachi - объёмный тати
                Minecraft.getMinecraft().getTextureManager().bindTexture(MUSOU);
                boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                if (type == ItemRenderType.EQUIPPED) {
                    swordGrip();
                    GL11.glDisable(GL11.GL_CULL_FACE); // у хвата меча масштаб по Y отрицательный - грани вывернуты
                }
                MUSOU_MESH.render("musou", "item", 1F / 16F);
                if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
                return;
            }
            ItemRenderer.renderItemIn2D(Tessellator.instance, i.getMaxU(), i.getMinV(), i.getMinU(), i.getMaxV(), i.getIconWidth(), i.getIconHeight(), 0.0625F);
        }
    }

    /**
     * Серенада у Divine - не меч (isFull3D нет), и в руке у игрока и мобов ванилла держит её как простой предмет: в 0.6
     * размера меча и плашмя перед собой. Снять преобразование предмета из RenderPlayer/RenderBiped и поставить хват меча.
     */
    static void swordGrip() {
        GL11.glRotatef(-20F, 0, 0, 1); // обратное к хвату предмета: Rz(20), Rx(-90), Rz(60), масштаб 0.375, сдвиг
        GL11.glRotatef(90F, 1, 0, 0);
        GL11.glRotatef(-60F, 0, 0, 1);
        GL11.glScalef(1F / 0.375F, 1F / 0.375F, 1F / 0.375F);
        GL11.glTranslatef(-0.25F, 0F, 0.1875F); // минус сдвиг предмета (0.25, 0.1875, -0.1875) плюс сдвиг меча (0, 0.1875, 0)
        GL11.glScalef(0.625F, -0.625F, 0.625F); // хват меча, как у isFull3D
        GL11.glRotatef(-100F, 1, 0, 0);
        GL11.glRotatef(45F, 0, 1, 0);
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
            // РПГ-7 на подушках: чуть по диагонали, на боку (рукояти к краю сиденья), граната на подлокотнике
            GL11.glTranslatef(-2F / 16F, 10.3F / 16F, -2F / 16F);
            GL11.glRotatef(-11.5F, 0, 0, 1);
            GL11.glRotatef(17F, 0, 1, 0);
            GL11.glRotatef(-90F, 1, 0, 0);
            GL11.glTranslatef(-15F / 16F, 0, 0);
            bindTexture(RPG);
            RPG_MESH.render("rpg", "sofa", 0.0625F);
            GL11.glPopMatrix();
        }
    }
}
