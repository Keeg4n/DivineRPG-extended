package com.divinerpgextended.client;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.divinerpgextended.dummy.EntityTrainingDummy;
import com.mojang.authlib.GameProfile;

/**
 * Training Dummy: drawn as the player it is a copy of - through the player renderer, with his skin and model. The skin takes the same
 * path as live players' (vanilla or the server's skin mod: 64x64, HD, Alex slim arms), the armor is the dummy's. Above the
 * head are the hit damage and damage per second. If the player renderer failed - the old model with a 64x32 skin by name.
 */
public class RenderTrainingDummy extends RenderBiped {

    /** An invisible player double for each dummy: not in the world, only for rendering. */
    private final Map<EntityTrainingDummy, EntityOtherPlayerMP> twins = new WeakHashMap<EntityTrainingDummy, EntityOtherPlayerMP>();
    private boolean broken;

    public RenderTrainingDummy() {
        super(new ModelBiped(0.0F), 0.5F);
    }

    @Override
    public void doRender(Entity e, double x, double y, double z, float yaw, float pt) {
        EntityTrainingDummy d = (EntityTrainingDummy) e;
        EntityOtherPlayerMP t = broken ? null : twin(d);
        if (t == null) {
            drawFallback(e, x, y, z, yaw, pt);
            return;
        }
        try {
            Render r = RenderManager.instance.getEntityRenderObject(t);
            r.doRender(t, x, y, z, yaw, pt);
        } catch (Throwable ex) { // another mod's player renderer did not accept the double - then the old model
            broken = true;
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Dummy: player render failed, drawing with a simple model: " + ex);
            drawFallback(e, x, y, z, yaw, pt);
            return;
        }
        passSpecialRender(d, x, y, z);
    }

    /** The double: the profile of a live player if he is nearby (with the skin from his data), otherwise by the UUID and name of the copy. */
    private EntityOtherPlayerMP twin(EntityTrainingDummy d) {
        String name = d.skinName();
        if (name == null || name.isEmpty()) return null;
        EntityOtherPlayerMP t = twins.get(d);
        if (t == null || t.worldObj != d.worldObj || !name.equals(t.getCommandSenderName())) {
            EntityPlayer live = d.worldObj.getPlayerEntityByName(name);
            GameProfile gp = live != null ? live.getGameProfile() : new GameProfile(id(d, name), name);
            t = new EntityOtherPlayerMP(d.worldObj, gp);
            // the renderer does not write the name above the one being ridden: the dummy does not need the copy's name (on itself - so as not to keep the dummy in memory)
            t.riddenByEntity = t;
            twins.put(d, t);
        }
        // the pose and look of the dummy
        t.setPosition(d.posX, d.posY, d.posZ);
        t.prevPosX = t.lastTickPosX = d.prevPosX;
        t.prevPosY = t.lastTickPosY = d.prevPosY;
        t.prevPosZ = t.lastTickPosZ = d.prevPosZ;
        t.rotationYaw = d.rotationYaw;
        t.prevRotationYaw = d.prevRotationYaw;
        t.renderYawOffset = d.renderYawOffset;
        t.prevRenderYawOffset = d.prevRenderYawOffset;
        t.rotationYawHead = d.rotationYawHead;
        t.prevRotationYawHead = d.prevRotationYawHead;
        t.rotationPitch = t.prevRotationPitch = 0;
        t.limbSwing = t.limbSwingAmount = t.prevLimbSwingAmount = 0;
        // cloak: the double does not tick, and the point that the cloak "catches up" to would stay where it was created - the cloak would curl inward
        t.field_71091_bM = t.field_71094_bP = d.posX;
        t.field_71096_bN = t.field_71095_bQ = d.posY;
        t.field_71097_bO = t.field_71085_bR = d.posZ;
        t.cameraYaw = t.prevCameraYaw = 0;
        t.distanceWalkedModified = t.prevDistanceWalkedModified = 0;
        t.hurtTime = d.hurtTime;
        t.maxHurtTime = d.maxHurtTime;
        t.ticksExisted = d.ticksExisted;
        for (int i = 0; i < 4; i++) t.inventory.armorInventory[i] = d.getEquipmentInSlot(i + 1);
        t.inventory.mainInventory[t.inventory.currentItem] = null;
        return t;
    }

    private static UUID id(EntityTrainingDummy d, String name) {
        try {
            if (!d.skinId().isEmpty()) return UUID.fromString(d.skinId());
        } catch (IllegalArgumentException ignored) {}
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
    }

    /**
     * Its own texture (a straw scarecrow) - minimaps take it for the icon, so that the dummy does not look like a player. The dummy itself
     * is drawn by the player renderer with the copy's skin, the old model - with a skin by name.
     */
    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return fallback ? skin(e) : ICON;
    }

    private static final ResourceLocation ICON = new ResourceLocation("divinerpgextended", "textures/entity/training_dummy.png");
    private boolean fallback;

    private void drawFallback(Entity e, double x, double y, double z, float yaw, float pt) {
        fallback = true;
        try {
            super.doRender(e, x, y, z, yaw, pt);
        } finally {
            fallback = false;
        }
    }

    private static ResourceLocation skin(Entity e) {
        String name = ((EntityTrainingDummy) e).skinName();
        if (name == null || name.isEmpty()) return AbstractClientPlayer.locationStevePng;
        ResourceLocation loc = AbstractClientPlayer.getLocationSkin(name);
        AbstractClientPlayer.getDownloadImageSkin(loc, name); // download the skin if not yet
        return loc;
    }

    @Override
    protected void passSpecialRender(net.minecraft.entity.EntityLivingBase e, double x, double y, double z) {
        EntityTrainingDummy d = (EntityTrainingDummy) e;
        if (d.lastHit() <= 0 && d.dps() <= 0) {
            // the "Hit me" hint - only when the dummy is being looked at
            net.minecraft.util.MovingObjectPosition mo = net.minecraft.client.Minecraft.getMinecraft().objectMouseOver;
            if (mo == null || mo.entityHit != e) return;
            func_147906_a(e, EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.dummy.idle"), x, y, z, 16);
            return;
        }
        if (d.arcana() > 0) // arcana shield: how much arcana the copy has and how much the last hit removed
            func_147906_a(e, EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocalFormatted("dre.dummy.arcana", String.format("%.0f", d.arcana()),
                String.format("%.1f", d.arcanaSpent())), x, y + 0.54, z, 32);
        func_147906_a(e, EnumChatFormatting.YELLOW + StatCollector.translateToLocalFormatted("dre.dummy.dps", String.format("%.1f", d.dps())), x, y + 0.27, z, 32);
        func_147906_a(e, EnumChatFormatting.RED + StatCollector.translateToLocalFormatted("dre.dummy.hit", String.format("%.2f", d.lastHit())), x, y, z, 32);
    }
}
