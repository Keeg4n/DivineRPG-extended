package com.divinerpgextended.client;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.divinerpgextended.dummy.EntityTrainingDummy;

/** Манекен: модель игрока со скином того, чья это копия, и его броней; над головой - урон удара и урон в секунду. */
public class RenderTrainingDummy extends RenderBiped {

    public RenderTrainingDummy() {
        super(new ModelBiped(0.0F), 0.5F);
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
