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
 * Манекен: рисуется как игрок, копией которого он стал, - через рендер игрока, с его скином и моделью. Скин берёт тот же
 * путь, что у живых игроков (ванильный или мод скинов сервера: 64x64, HD, тонкие руки Alex), броня - манекена. Над
 * головой - урон удара и урон в секунду. Если рендер игрока не сработал - старая модель с 64x32-скином по имени.
 */
public class RenderTrainingDummy extends RenderBiped {

    /** Невидимый игрок-двойник для каждого манекена: не в мире, только для рендера. */
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
            super.doRender(e, x, y, z, yaw, pt);
            return;
        }
        try {
            Render r = RenderManager.instance.getEntityRenderObject(t);
            r.doRender(t, x, y, z, yaw, pt);
        } catch (Throwable ex) { // чужой рендер игрока не принял двойника - дальше старой моделью
            broken = true;
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Манекен: рендер игрока не сработал, рисую простой моделью: " + ex);
            super.doRender(e, x, y, z, yaw, pt);
            return;
        }
        passSpecialRender(d, x, y, z);
    }

    /** Двойник: профиль живого игрока, если он рядом (со скином из его данных), иначе по UUID и имени копии. */
    private EntityOtherPlayerMP twin(EntityTrainingDummy d) {
        String name = d.skinName();
        if (name == null || name.isEmpty()) return null;
        EntityOtherPlayerMP t = twins.get(d);
        if (t == null || t.worldObj != d.worldObj || !name.equals(t.getCommandSenderName())) {
            EntityPlayer live = d.worldObj.getPlayerEntityByName(name);
            GameProfile gp = live != null ? live.getGameProfile() : new GameProfile(id(d, name), name);
            t = new EntityOtherPlayerMP(d.worldObj, gp);
            // рендер не пишет имя над тем, на ком сидят: имя копии манекену не нужно (сам на себе - чтобы не держать манекен в памяти)
            t.riddenByEntity = t;
            twins.put(d, t);
        }
        // поза и вид манекена
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

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        String name = ((EntityTrainingDummy) e).skinName();
        if (name == null || name.isEmpty()) return AbstractClientPlayer.locationStevePng;
        ResourceLocation loc = AbstractClientPlayer.getLocationSkin(name);
        AbstractClientPlayer.getDownloadImageSkin(loc, name); // скачать скин, если ещё нет
        return loc;
    }

    @Override
    protected void passSpecialRender(net.minecraft.entity.EntityLivingBase e, double x, double y, double z) {
        EntityTrainingDummy d = (EntityTrainingDummy) e;
        if (d.lastHit() <= 0 && d.dps() <= 0) {
            func_147906_a(e, EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.dummy.idle"), x, y, z, 16);
            return;
        }
        func_147906_a(e, EnumChatFormatting.YELLOW + StatCollector.translateToLocalFormatted("dre.dummy.dps", String.format("%.1f", d.dps())), x, y + 0.27, z, 32);
        func_147906_a(e, EnumChatFormatting.RED + StatCollector.translateToLocalFormatted("dre.dummy.hit", String.format("%.2f", d.lastHit())), x, y, z, 32);
    }
}
