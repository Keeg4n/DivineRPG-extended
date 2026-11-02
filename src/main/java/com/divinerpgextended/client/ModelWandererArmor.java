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
 * Облик брони Странника по ступени R1-R7: меш и атлас из tools/wanderer_armor.py. Поза - как у брони игрока (кости
 * ModelBiped), на каждой кости - детали надетой части (helmet, chest, legs, boots), как у костюма H.E.V. Светящиеся детали
 * - вторым проходом: тот же меш с маской r&lt;N&gt;_glow.png на полной яркости.
 */
public class ModelWandererArmor extends ModelBiped {

    private static final String D = "divinerpgextended";
    private static final String[] PIECES = { "helmet", "chest", "legs", "boots" };
    private static final int N = ItemWandererArmor.LOOKS;
    private static final EasterMesh[] MESH = new EasterMesh[N + 1];
    private static final ResourceLocation[] TEX = new ResourceLocation[N + 1], GLOW = new ResourceLocation[N + 1];
    private static final Boolean[] HAS_GLOW = new Boolean[N + 1];
    private static final ModelWandererArmor MODEL = new ModelWandererArmor();

    static {
        for (int t = 1; t <= N; t++) {
            MESH[t] = new EasterMesh(new ResourceLocation(D, "models/wanderer/r" + t + ".mesh"));
            TEX[t] = new ResourceLocation(ItemWandererArmor.lookTexture(t));
            GLOW[t] = new ResourceLocation(D, "textures/models/wanderer/r" + t + "_glow.png");
        }
    }

    private int tier = 1;
    private String piece = "chest";

    private ModelWandererArmor() {
        super(0F);
    }

    /** Модель части брони (armorType 0-3) ступени tier для мобов и стоек: поза без предметов в руках и приседа. */
    public static ModelBiped forMob(int tier, int armorType) {
        ModelWandererArmor m = select(tier, armorType);
        m.heldItemLeft = m.heldItemRight = 0;
        m.isSneak = m.aimedBow = false;
        return m;
    }

    private static ModelWandererArmor select(int tier, int armorType) {
        MODEL.tier = tier;
        MODEL.piece = PIECES[Math.max(0, Math.min(3, armorType))];
        return MODEL;
    }

    @Override
    public void render(Entity e, float f, float f1, float f2, float f3, float f4, float scale) {
        setRotationAngles(f, f1, f2, f3, f4, scale, e);
        EasterMesh mesh = MESH[tier];
        draw(mesh, scale);
        // свечение - только в обычном проходе (в проходе блеска зачарования привязана другая текстура)
        if (hasGlow(tier) && bound(TEX[tier])) {
            TextureManager tm = Minecraft.getMinecraft().getTextureManager();
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

    private static boolean hasGlow(int tier) {
        if (HAS_GLOW[tier] == null) {
            try {
                Minecraft.getMinecraft().getResourceManager().getResource(GLOW[tier]);
                HAS_GLOW[tier] = true;
            } catch (Exception ex) {
                HAS_GLOW[tier] = false;
            }
        }
        return HAS_GLOW[tier];
    }

    /** На игроке: поза и текстура - как у костюма H.E.V. (номер прохода в событии задом наперёд, часть - по вещи). */
    public static class Events {

        @SubscribeEvent
        public void onArmor(RenderPlayerEvent.SetArmorModel e) {
            if (e.stack == null || !(e.stack.getItem() instanceof ItemWandererArmor)) return;
            int tier = ArmorData.tier(e.stack);
            if (!ItemWandererArmor.hasLook(tier)) return;
            ModelWandererArmor m = select(tier, ((ItemArmor) e.stack.getItem()).armorType);
            ModelBiped main = e.renderer.modelBipedMain;
            m.onGround = main.onGround;
            m.isRiding = main.isRiding;
            m.isChild = main.isChild;
            m.heldItemLeft = main.heldItemLeft;
            m.heldItemRight = main.heldItemRight;
            m.isSneak = main.isSneak;
            m.aimedBow = main.aimedBow;
            Minecraft.getMinecraft().getTextureManager().bindTexture(TEX[tier]);
            e.renderer.setRenderPassModel(m);
            e.result = e.stack.isItemEnchanted() ? 15 : 1;
        }
    }
}
