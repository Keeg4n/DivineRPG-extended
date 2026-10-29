package com.divinerpgextended.client;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent17;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.divinerpgextended.easter.Easter;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Звуки Half-Life для пасхалок. Сами файлы Valve в мод не входят: их кладёт ~/mc/tools/hev_sounds.py из своей установленной
 * Half-Life в папку config/divinerpgextended/hevsounds - она подключается как ресурсы. Нет файла - звук Minecraft.
 * <ul>
 * <li>Игрок в полном костюме H.E.V.: шаги, боль, падение, плавание - звуки Half-Life; при беге дыхание; голос костюма (вход
 * в систему, морфин, переломы, критическое здоровье) - по сообщению костюма в чате.</li>
 * <li>Монтировка: удары по мобам и блокам. РПГ-7: выстрел.</li>
 * </ul>
 */
public final class HevSounds {

    private HevSounds() {}

    static final Map<String, String> PLAYER = new HashMap<String, String>();
    static final Map<String, String> VOICE = new HashMap<String, String>();
    static {
        PLAYER.put("game.player.hurt", "hev.pain");
        PLAYER.put("game.player.hurt.fall.big", "hev.fallpain");
        PLAYER.put("game.player.hurt.fall.small", "hev.fallpain");
        PLAYER.put("game.player.die", "hev.flatline");
        PLAYER.put("game.player.swim", "hev.wade");
        PLAYER.put("game.player.swim.splash", "hev.slosh");
        for (String s : new String[] { "stone", "wood" }) PLAYER.put("step." + s, "hev.step");
        for (String s : new String[] { "grass", "gravel", "sand", "snow", "cloth" }) PLAYER.put("step." + s, "hev.dirt");
        PLAYER.put("step.ladder", "hev.ladder");
        VOICE.put("dre.hev.logon", "hev.logon");
        VOICE.put("dre.hev.morphine", "hev.morphine");
        VOICE.put("dre.hev.major_fracture", "hev.major_fracture");
        VOICE.put("dre.hev.minor_fracture", "hev.minor_fracture");
        VOICE.put("dre.hev.health_critical", "hev.health_critical");
    }

    /** Первый файл каждого события (по нему проверяем, установлены ли звуки). */
    static final Map<String, String> FIRST = new HashMap<String, String>();
    static {
        String[][] f = { { "hev.pain", "pain2" }, { "hev.fallpain", "fallpain1" }, { "hev.flatline", "flatline" }, { "hev.wade", "wade1" },
            { "hev.slosh", "slosh1" }, { "hev.step", "step1" }, { "hev.dirt", "dirt1" }, { "hev.ladder", "ladder1" }, { "hev.breathe", "breathe1" },
            { "hev.logon", "logon" }, { "hev.morphine", "morphine" }, { "hev.major_fracture", "major_fracture" }, { "hev.minor_fracture", "minor_fracture" },
            { "hev.health_critical", "health_critical" }, { "hev.cbar_hit", "cbar_hit1" }, { "hev.cbar_hitbod", "cbar_hitbod1" }, { "hev.rocketfire", "rocketfire1" } };
        for (String[] e : f) FIRST.put(e[0], e[1]);
    }

    static final Map<String, Boolean> HAVE = new HashMap<String, Boolean>();

    /** Звук события установлен (есть его файл в ресурсах). */
    public static boolean have(String event) {
        Boolean b = HAVE.get(event);
        if (b == null) {
            String f = FIRST.get(event);
            try {
                Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation("divinerpgextended", "sounds/hev/" + f + ".ogg"));
                b = f != null;
            } catch (Exception missing) {
                b = false;
            }
            HAVE.put(event, b);
        }
        return b;
    }

    /** Подключить папку со звуками Half-Life как ресурсы (до первой загрузки ресурсов). */
    @SuppressWarnings("unchecked")
    public static void addPack(File gameDir) {
        File dir = new File(gameDir, "config/divinerpgextended/hevsounds");
        if (!new File(dir, "assets").isDirectory()) return;
        try {
            List<IResourcePack> packs = ReflectionHelper.getPrivateValue(Minecraft.class, Minecraft.getMinecraft(), "defaultResourcePacks", "field_110449_ao");
            packs.add(new FolderResourcePack(dir));
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("divinerpgextended").warn("Звуки H.E.V. не подключены: " + t);
        }
    }

    static void play(String event, double x, double y, double z, float volume) {
        if (!have(event)) return;
        Minecraft.getMinecraft().getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation("divinerpgextended", event), volume, 1.0F, (float) x, (float) y, (float) z));
    }

    public static void init() {
        Events e = new Events();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(e);
        FMLCommonHandler.instance().bus().register(e);
    }

    /** Звук игрока, рядом с которым он прозвучал (шаги и боль звучат у ног). */
    static EntityPlayer near(ISound s) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return null;
        EntityPlayer best = null;
        double bd = 2.5 * 2.5;
        for (Object o : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            double dx = p.posX - s.getXPosF(), dz = p.posZ - s.getZPosF(), dy = p.boundingBox.minY - s.getYPosF();
            double d = dx * dx + dz * dz + Math.min(dy * dy, (p.posY - s.getYPosF()) * (p.posY - s.getYPosF()));
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    public static class Events {

        int sprint, swing;

        @SubscribeEvent
        public void onSound(PlaySoundEvent17 e) {
            if (e.sound == null || e.name == null) return;
            String to = PLAYER.get(e.name);
            boolean bow = "random.bow".equals(e.name);
            if (to == null && !bow) return;
            EntityPlayer p = near(e.sound);
            if (p == null) return;
            if (bow) {
                if (!Easter.rpg(p.getHeldItem()) || !have("hev.rocketfire")) return;
                to = "hev.rocketfire";
            } else if (!Easter.fullHev(p) || !have(to)) return;
            e.result = new PositionedSoundRecord(new ResourceLocation("divinerpgextended", to), e.sound.getVolume(), 1.0F, e.sound.getXPosF(),
                e.sound.getYPosF(), e.sound.getZPosF());
        }

        /** Голос костюма: сообщение костюма в чате. */
        @SubscribeEvent
        public void onChat(ClientChatReceivedEvent e) {
            if (!(e.message instanceof ChatComponentTranslation)) return;
            String to = VOICE.get(((ChatComponentTranslation) e.message).getKey());
            EntityPlayer p = Minecraft.getMinecraft().thePlayer;
            if (to != null && p != null) play(to, p.posX, p.posY, p.posZ, 1.0F);
        }

        /** Монтировка: удар по мобу. */
        @SubscribeEvent
        public void onAttack(AttackEntityEvent e) {
            if (!e.entityPlayer.worldObj.isRemote || !Easter.crowbar(e.entityPlayer.getHeldItem())) return;
            play("hev.cbar_hitbod", e.target.posX, e.target.posY + e.target.height / 2, e.target.posZ, 1.0F);
        }

        /** Монтировка: удар по блоку (не чаще раза в 5 тиков, пока зажата кнопка). */
        @SubscribeEvent
        public void onClickBlock(PlayerInteractEvent e) {
            if (e.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK || !e.entityPlayer.worldObj.isRemote || !Easter.crowbar(e.entityPlayer.getHeldItem())) return;
            if (e.entityPlayer.ticksExisted - swing < 5 && e.entityPlayer.ticksExisted >= swing) return;
            swing = e.entityPlayer.ticksExisted;
            play("hev.cbar_hit", e.x + 0.5, e.y + 0.5, e.z + 0.5, 0.8F);
        }

        /** Бег в костюме - тяжёлое дыхание раз в 3 секунды. */
        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            EntityPlayer p = Minecraft.getMinecraft().thePlayer;
            if (e.phase != TickEvent.Phase.END || p == null || Minecraft.getMinecraft().isGamePaused()) return;
            if (!p.isSprinting() || !Easter.fullHev(p)) {
                sprint = 0;
                return;
            }
            if (++sprint % 60 == 40) play("hev.breathe", p.posX, p.posY, p.posZ, 0.6F);
        }
    }
}
