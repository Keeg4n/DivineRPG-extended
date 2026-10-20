package com.divinerpgextended.boss;

import java.util.ArrayList;
import java.util.List;

import net.divinerpg.utils.blocks.VetheaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Вход в Ветею - после поздних боссов: шесть боссов Мортума (близнецы - одной победой) и два босса Арканы; босс
 * подземелья джунглей добавится в {@link #NEED}, когда появится. Кровать кошмаров без них не пускает и говорит, кого не
 * хватает; попавшего в Ветею иначе (телепорт) возвращает домой. Творческий режим не трогаем.
 */
public class VetheaGate {

    public static final Boss[] NEED = { Boss.TWILIGHT_DEMON, Boss.TWINS, Boss.KAROT, Boss.ETERNAL_ARCHER, Boss.SOUL_FIEND, Boss.VAMACHERON,
        Boss.DRAMIX, Boss.PARASECTA };

    /** Каких боссов из нужных игрок ещё не победил. */
    public static List<Boss> missing(EntityPlayer p) {
        List<Boss> l = new ArrayList<Boss>();
        for (Boss b : NEED) if (!BossProgress.has(p, b)) l.add(b);
        return l;
    }

    static IChatComponent refusal(List<Boss> miss) {
        IChatComponent m = new ChatComponentTranslation("dre.chat.vethea_locked");
        for (int i = 0; i < miss.size(); i++) {
            if (i > 0) m.appendSibling(new ChatComponentText(", "));
            m.appendSibling(new ChatComponentTranslation(miss.get(i).langKey()));
        }
        m.getChatStyle().setColor(EnumChatFormatting.DARK_PURPLE);
        return m;
    }

    /** Кровать кошмаров: без нужных побед не уснуть - сказать, кого не хватает. */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.entityPlayer.capabilities.isCreativeMode) return;
        if (e.world.getBlock(e.x, e.y, e.z) != VetheaBlocks.nightmareBedBlock) return;
        List<Boss> miss = missing(e.entityPlayer);
        if (miss.isEmpty()) return;
        e.setCanceled(true);
        if (!e.world.isRemote) e.entityPlayer.addChatMessage(refusal(miss));
    }

    /** В Ветее без нужных побед (попал не через кровать) - домой, к кровати или на точку появления. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.START || p.worldObj.isRemote || !(p instanceof EntityPlayerMP) || p.capabilities.isCreativeMode) return;
        if (p.dimension != ConfigurationHelper.vethea || p.ticksExisted % 20 != 0) return;
        List<Boss> miss = missing(p);
        if (miss.isEmpty()) return;
        sendHome((EntityPlayerMP) p);
        p.addChatMessage(refusal(miss));
    }

    static void sendHome(EntityPlayerMP p) {
        WorldServer home = p.mcServer.worldServerForDimension(0);
        ChunkCoordinates bed = p.getBedLocation(0);
        ChunkCoordinates at = bed != null ? EntityPlayer.verifyRespawnCoordinates(home, bed, p.isSpawnForced(0)) : null;
        if (at == null) {
            ChunkCoordinates sp = home.getSpawnPoint();
            at = new ChunkCoordinates(sp.posX, home.getTopSolidOrLiquidBlock(sp.posX, sp.posZ), sp.posZ);
        }
        final double x = at.posX + 0.5, y = at.posY + 0.1, z = at.posZ + 0.5;
        p.timeUntilPortal = 100;
        p.mcServer.getConfigurationManager().transferPlayerToDimension(p, 0, new Teleporter(home) {

            @Override
            public void placeInPortal(Entity en, double px, double py, double pz, float yaw) {
                en.setLocationAndAngles(x, y, z, en.rotationYaw, en.rotationPitch);
                en.motionX = en.motionY = en.motionZ = 0;
            }

            @Override
            public boolean placeInExistingPortal(Entity en, double px, double py, double pz, float yaw) {
                placeInPortal(en, px, py, pz, yaw);
                return true;
            }

            @Override
            public boolean makePortal(Entity en) {
                return true;
            }

            @Override
            public void removeStalePortalLocations(long time) {}
        });
        p.setPositionAndUpdate(x, y, z);
    }
}
