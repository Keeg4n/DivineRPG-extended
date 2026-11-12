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
 * Облик брони Странника по ступени R1-R7, ядру первой ступени и Венцу R8: меш и атлас из tools/wanderer_armor.py. Поза - как у брони игрока (кости
 * ModelBiped), на каждой кости - детали надетой части (helmet, chest, legs, boots), как у костюма H.E.V. Светящиеся детали
 * - вторым проходом: тот же меш с маской r&lt;N&gt;_glow.png на полной яркости. Крашеная часть - ещё проход перед свечением:
 * серая маска основного материала r&lt;N&gt;_dye.png, умноженная на цвет.
 */
public class ModelWandererArmor extends ModelBiped {

    private static final String D = "divinerpgextended";
    private static final String[] PIECES = { "helmet", "chest", "legs", "boots" };
    private static final int N = ItemWandererArmor.LOOK_COUNT;
    private static final EasterMesh[] MESH = new EasterMesh[N + 1];
    private static final ResourceLocation[] TEX = new ResourceLocation[N + 1], GLOW = new ResourceLocation[N + 1], DYE = new ResourceLocation[N + 1];
    private static final Boolean[] HAS_GLOW = new Boolean[N + 1], HAS_DYE = new Boolean[N + 1];
    private static final ModelWandererArmor MODEL = new ModelWandererArmor();

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
    /** Цвет покраски 0xRRGGBB или -1. */
    private int color = -1;

    private ModelWandererArmor() {
        super(0F);
    }

    /** Модель части брони (armorType 0-3) ступени tier для мобов и стоек: поза без предметов в руках и приседа. */
    public static ModelBiped forMob(int tier, int armorType, int color) {
        ModelWandererArmor m = select(tier, armorType, color);
        m.heldItemLeft = m.heldItemRight = 0;
        m.isSneak = m.aimedBow = false;
        return m;
    }

    private static ModelWandererArmor select(int tier, int armorType, int color) {
        MODEL.tier = tier;
        MODEL.color = color;
        MODEL.piece = PIECES[Math.max(0, Math.min(3, armorType))];
        return MODEL;
    }

    @Override
    public void render(Entity e, float f, float f1, float f2, float f3, float f4, float scale) {
        setRotationAngles(f, f1, f2, f3, f4, scale, e);
        EasterMesh mesh = MESH[tier];
        // Призрак R8: основа полупрозрачная (альфа в атласе), свечение и тьма под капюшоном - нет. Только в обычном проходе: в проходе
        // блеска зачарования смешивание своё
        boolean glass = ItemWandererArmor.translucent(tier) && bound(TEX[tier]), wasBlend = glass && GL11.glIsEnabled(GL11.GL_BLEND);
        if (glass) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        draw(mesh, scale);
        if (glass && !wasBlend) GL11.glDisable(GL11.GL_BLEND);
        // покраска и свечение - только в обычном проходе (в проходе блеска зачарования привязана другая текстура)
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

    private static boolean bound(ResourceLocation loc) {
        ITextureObject t = Minecraft.getMinecraft().getTextureManager().getTexture(loc);
        return t != null && GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == t.getGlTextureId();
    }

    /** Есть ли у облика эта текстура (маски свечения и покраски есть не у всех). */
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

    /** На игроке: поза и текстура - как у костюма H.E.V. (номер прохода в событии задом наперёд, часть - по вещи). */
    public static class Events {

        /** Пустой скин: тело не рисуется, а всё, что держится на его костях (вещь в руке, аксессуары), остаётся на месте. */
        private static final ResourceLocation CLEAR = new ResourceLocation(D, "textures/entity/clear_skin.png");
        private static java.lang.reflect.Field skin;
        private net.minecraft.client.entity.AbstractClientPlayer hidden;
        private ResourceLocation saved;

        /** Полный сет Призрака (Венец полёта R8). */
        static boolean ghost(net.minecraft.entity.player.EntityPlayer p) {
            for (int i = 0; i < 4; i++) {
                net.minecraft.item.ItemStack s = p.getCurrentArmor(i);
                if (s == null || !(s.getItem() instanceof ItemWandererArmor) || !ItemWandererArmor.translucent(ItemWandererArmor.look(s))) return false;
            }
            return true;
        }

        /**
         * Призрак полупрозрачный: сквозь латы должен быть виден мир, а не скин игрока. На время отрисовки игрока в полном сете его
         * скин - пустая текстура (тело - ничего, вместо скрытия частей модели: на их костях держатся вещь в руке и аксессуары).
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
         * Стойка для брони BiblioCraft рисует броню со своей моделью через фальшивого игрока, а его яркость всегда полная (днём и
         * ночью, на свету и в темноте) - броня выходила пересвеченной. Ставим свет по месту самой стойки: фальшивый игрок стоит
         * в координатах отрисовки (мир минус камера).
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
            ModelWandererArmor m = select(look, ((ItemArmor) e.stack.getItem()).armorType, ArmorData.color(e.stack));
            ModelBiped main = e.renderer.modelBipedMain;
            m.onGround = main.onGround;
            m.isRiding = main.isRiding;
            m.isChild = main.isChild;
            m.heldItemLeft = main.heldItemLeft;
            m.heldItemRight = main.heldItemRight;
            m.isSneak = main.isSneak;
            m.aimedBow = main.aimedBow;
            Minecraft.getMinecraft().getTextureManager().bindTexture(TEX[look]);
            e.renderer.setRenderPassModel(m);
            e.result = e.stack.isItemEnchanted() ? 15 : 1;
        }
    }
}
