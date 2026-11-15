package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.entity.EntityCarcinus;
import com.divinerpgextended.entity.EntityFrostColossus;
import com.divinerpgextended.entity.EntityGreatCadillion;
import com.divinerpgextended.entity.EntityLichKing;
import com.divinerpgextended.entity.EntitySnowGlacon;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The health bar of our bosses like Divine's: a 182x20 texture for each boss, a colored strip on top with patterns
 * and the name written in, the same gray one below. The gray one is drawn in full and the colored one over it by health fraction. Our bosses do not
 * show the vanilla bar.
 */
@SideOnly(Side.CLIENT)
public class DreBossBar extends Gui {

    private static float health;
    private static long shownAt = -1000;
    private static String theme;

    /** Called by the boss renderer every frame while the boss is visible. */
    public static void show(EntityLiving e) {
        String t = themeOf(e);
        if (t == null) return;
        health = e.getHealth() / e.getMaxHealth();
        theme = t;
        shownAt = e.worldObj.getTotalWorldTime();
    }

    private static String themeOf(EntityLiving e) {
        if (e instanceof EntityCarcinus) return "sea";
        if (e instanceof EntitySnowGlacon) return "frost";
        if (e instanceof EntityFrostColossus) return "colossus";
        if (e instanceof EntityLichKing) return "lich";
        if (e instanceof com.divinerpgextended.entity.EntityAncientGolem) return "golem";
        if (e instanceof com.divinerpgextended.entity.EntityWarriorKing) return "king";
        if (e instanceof com.divinerpgextended.entity.EntityWindLord) return "windlord";
        if (e instanceof com.divinerpgextended.entity.EntityAncientDramcryx) return "dramcryx";
        if (e instanceof EntityGreatCadillion) {
            String[] v = { "eden", "wildwood", "apalachia", "skythern" };
            return v[Math.max(0, Math.min(3, ((EntityGreatCadillion) e).getVariant()))];
        }
        return null;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || theme == null || mc.theWorld.getTotalWorldTime() - shownAt > 20) return;
        int x = event.resolution.getScaledWidth() / 2 - 91, y = 12, len = 183;
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_BLEND);
        mc.getTextureManager()
            .bindTexture(new ResourceLocation("divinerpgextended", "textures/gui/bossbar_" + theme + ".png"));
        drawTexturedModalRect(x, y, 0, 10, len, 10); // gray strip (lost health)
        int fill = (int) (Math.max(0, Math.min(1, health)) * len);
        if (fill > 0) drawTexturedModalRect(x, y, 0, 0, fill, 10); // colored by health, the name is written into the texture
        GL11.glColor4f(1, 1, 1, 1);
    }
}
