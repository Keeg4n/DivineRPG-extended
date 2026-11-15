package com.divinerpgextended.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ISoundEventAccessor;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundEventAccessorComposite;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundPoolEntry;
import net.minecraft.client.audio.SoundRegistry;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent17;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.divinerpgextended.easter.Easter;
import com.divinerpgextended.easter.HalfLifeMount;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Half-Life sounds for the Easter eggs - straight from the player's installed Half-Life and Half-Life 2 ({@link HalfLifeMount}): its own
 * resource pack serves their files as {@code divinerpgextended:sounds/hev/hl1/...wav}, the {@code hev.*} events are assembled in code
 * after every resource reload (the WAV is played by Minecraft's WAV codec). No games - Minecraft sounds.
 * <ul>
 * <li>Full H.E.V. suit: footsteps by surface (Half-Life 2), pain and fall, death - a monitor flatline, in water - a splash; when you start
 * to run - the suit sound from Half-Life 2; the suit voice (login, morphine, fractures, critical health) - by its
 * chat message.</li>
 * <li>Crowbar: every swing - at a mob, at a block or at the air, as in Half-Life. RPG-7: the shot.</li>
 * </ul>
 * Footsteps, pain and death of other players in the suit are replaced too, the dash and crowbar swings of other players are audible near them.
 * But the files come only from your own games: whoever does not have Half-Life installed hears the ordinary sounds.
 */
public final class HevSounds {

    private HevSounds() {}

    static final Logger LOG = LogManager.getLogger("divinerpgextended");
    static final String DOMAIN = "divinerpgextended", PREFIX = "sounds/hev/";
    static final Map<String, String> PLAYER = new HashMap<String, String>();
    static final Map<String, String> VOICE = new HashMap<String, String>();
    static {
        PLAYER.put("game.player.hurt", "hev.pain");
        PLAYER.put("game.player.hurt.fall.big", "hev.fallpain");
        PLAYER.put("game.player.hurt.fall.small", "hev.fallpain");
        PLAYER.put("game.player.die", "hev.death");
        PLAYER.put("game.player.swim", "hev.step.wade");
        PLAYER.put("game.player.swim.splash", "hev.step.slosh");
        PLAYER.put("step.stone", "hev.step.concrete");
        PLAYER.put("step.wood", "hev.step.wood");
        PLAYER.put("step.grass", "hev.step.grass");
        PLAYER.put("step.gravel", "hev.step.gravel");
        PLAYER.put("step.sand", "hev.step.sand");
        PLAYER.put("step.snow", "hev.step.dirt");
        PLAYER.put("step.cloth", "hev.step.dirt");
        PLAYER.put("step.ladder", "hev.step.ladder");
        VOICE.put("dre.hev.logon", "hev.logon");
        VOICE.put("dre.hev.morphine", "hev.morphine");
        VOICE.put("dre.hev.major_fracture", "hev.major_fracture");
        VOICE.put("dre.hev.minor_fracture", "hev.minor_fracture");
        VOICE.put("dre.hev.health_critical", "hev.health_critical");
    }

    /** The assembled events (at least one file exists). */
    static final Set<String> READY = Collections.synchronizedSet(new HashSet<String>());

    public static boolean have(String event) {
        return READY.contains(event);
    }

    /** Before the first resource load: the WAV codec and the resource pack with the Valve game files. */
    @SuppressWarnings("unchecked")
    public static void addPack(File gameDir) {
        try {
            paulscode.sound.SoundSystemConfig.setCodec("wav", paulscode.sound.codecs.CodecWav.class);
        } catch (Throwable t) {
            LOG.warn("H.E.V.: WAV codec not hooked: " + t);
        }
        try {
            List<IResourcePack> packs = ReflectionHelper.getPrivateValue(Minecraft.class, Minecraft.getMinecraft(), "defaultResourcePacks", "field_110449_ao");
            packs.add(new Pack());
        } catch (Throwable t) {
            LOG.warn("H.E.V.: sound set not hooked: " + t);
        }
    }

    public static void init() {
        Events e = new Events();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(e);
        FMLCommonHandler.instance().bus().register(e);
        // after SoundHandler: it clears the sound registry on reload, we add our events
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new Registrar());
    }

    /** Valve game files as mod resources: {@code divinerpgextended:sounds/hev/hl1/player/pl_pain2.wav}. */
    static class Pack implements IResourcePack {

        static String name(ResourceLocation r) {
            if (!DOMAIN.equals(r.getResourceDomain())) return null;
            String p = r.getResourcePath();
            return p.startsWith(PREFIX) && p.endsWith(".wav") ? p.substring(PREFIX.length(), p.length() - 4) : null;
        }

        @Override
        public InputStream getInputStream(ResourceLocation r) throws IOException {
            String n = name(r);
            byte[] b = n == null ? null : HalfLifeMount.wav(n);
            if (b == null) throw new FileNotFoundException(r.toString());
            return new ByteArrayInputStream(b);
        }

        @Override
        public boolean resourceExists(ResourceLocation r) {
            String n = name(r);
            return n != null && HalfLifeMount.has(n);
        }

        @Override
        public Set<String> getResourceDomains() {
            return Collections.singleton(DOMAIN);
        }

        @Override
        public IMetadataSection getPackMetadata(IMetadataSerializer s, String section) {
            return null;
        }

        @Override
        public BufferedImage getPackImage() throws IOException {
            throw new FileNotFoundException("pack.png");
        }

        @Override
        public String getPackName() {
            return "Half-Life (Steam)";
        }
    }

    /** One event file. */
    static class Entry implements ISoundEventAccessor {

        final SoundPoolEntry entry;

        Entry(String file) {
            entry = new SoundPoolEntry(new ResourceLocation(DOMAIN, PREFIX + file + ".wav"), 1.0, 1.0, false);
        }

        @Override
        public int func_148721_a() {
            return 1;
        }

        @Override
        public Object func_148720_g() {
            return new SoundPoolEntry(entry);
        }
    }

    /** After every resource reload: the hev.* events from the files found. */
    static class Registrar implements IResourceManagerReloadListener {

        @Override
        public void onResourceManagerReload(IResourceManager m) {
            READY.clear();
            SoundRegistry reg;
            try {
                reg = ReflectionHelper.getPrivateValue(SoundHandler.class, Minecraft.getMinecraft().getSoundHandler(), "sndRegistry", "field_147697_e");
            } catch (Throwable t) {
                LOG.warn("H.E.V.: sound registry unavailable: " + t);
                return;
            }
            int files = 0;
            for (Map.Entry<String, String[][]> e : HalfLifeMount.EVENTS.entrySet()) {
                SoundEventAccessorComposite c = new SoundEventAccessorComposite(new ResourceLocation(DOMAIN, e.getKey()), 1.0, 1.0, SoundCategory.PLAYERS);
                int n = 0;
                for (String[] variant : e.getValue()) {
                    String f = HalfLifeMount.resolve(variant);
                    if (f == null) continue;
                    c.addSoundToEventPool(new Entry(f));
                    n++;
                }
                if (n == 0) continue;
                reg.registerSound(c);
                READY.add(e.getKey());
                files += n;
            }
            LOG.info("H.E.V.: sounds from Valve games - events " + READY.size() + "/" + HalfLifeMount.EVENTS.size() + ", files " + files + " (Half-Life: "
                + HalfLifeMount.halfLife() + ", Half-Life 2: " + HalfLifeMount.halfLife2() + ")");
        }
    }

    static void play(String event, double x, double y, double z, float volume) {
        if (!have(event)) return;
        Minecraft.getMinecraft().getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(DOMAIN, event), volume, 1.0F, (float) x, (float) y, (float) z));
    }

    /** A sound at the player himself (suit voice, dash, a missed swing): no attenuation, does not stay in the place where it sounded. */
    static class Self extends net.minecraft.client.audio.PositionedSound {

        Self(String event, float volume) {
            super(new ResourceLocation(DOMAIN, event));
            this.volume = volume;
            this.field_147663_c = 1.0F;
            this.field_147666_i = ISound.AttenuationType.NONE;
        }
    }

    static void playSelf(String event, float volume) {
        if (have(event)) Minecraft.getMinecraft().getSoundHandler().playSound(new Self(event, volume));
    }

    /** The player at whose feet the sound played (footsteps and pain). */
    static EntityPlayer near(ISound s) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return null;
        EntityPlayer best = null;
        double bd = 2.5 * 2.5;
        for (Object o : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            double dx = p.posX - s.getXPosF(), dz = p.posZ - s.getZPosF();
            double dy = Math.min(Math.abs(p.boundingBox.minY - s.getYPosF()), Math.abs(p.posY - s.getYPosF()));
            double d = dx * dx + dz * dz + dy * dy;
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    public static class Events {

        boolean sprinting, swinging;
        int lastSwing;
        /** Other players nearby: {ran, swung, previous swing step}. */
        final Map<Integer, int[]> others = new HashMap<Integer, int[]>();

        @SubscribeEvent
        public void onSound(PlaySoundEvent17 e) {
            if (e.sound == null || e.name == null) return;
            String to = PLAYER.get(e.name);
            boolean bow = "random.bow".equals(e.name);
            if (to == null && !bow) return;
            EntityPlayer p = near(e.sound);
            if (p == null) return;
            if (bow) {
                if (!Easter.rpg(p.getHeldItem())) return;
                to = "hev.rocketfire";
            } else if (!Easter.fullHev(p)) return;
            if (!have(to)) return;
            float vol = to.startsWith("hev.step.") ? Math.max(e.sound.getVolume(), 0.5F) : e.sound.getVolume();
            e.result = new PositionedSoundRecord(new ResourceLocation(DOMAIN, to), vol, 1.0F, e.sound.getXPosF(), e.sound.getYPosF(), e.sound.getZPosF());
        }

        /** Suit voice: the suit's chat message. */
        @SubscribeEvent
        public void onChat(ClientChatReceivedEvent e) {
            if (!(e.message instanceof ChatComponentTranslation)) return;
            String to = VOICE.get(((ChatComponentTranslation) e.message).getKey());
            EntityPlayer p = Minecraft.getMinecraft().thePlayer;
            if (to != null && p != null) playSelf(to, 1.0F);
        }

        /** Other players within 32 blocks: a dash in the suit and crowbar swings - the sound is at them. */
        void others(Minecraft mc, EntityPlayer self) {
            if (mc.theWorld == null) { // entering and leaving the world: the player exists, the world does not yet (or no longer)
                others.clear();
                return;
            }
            java.util.Set<Integer> seen = new HashSet<Integer>();
            for (Object o : mc.theWorld.playerEntities) {
                EntityPlayer q = (EntityPlayer) o;
                if (q == self || q.getDistanceSqToEntity(self) > 32 * 32) continue;
                seen.add(q.getEntityId());
                int[] s = others.get(q.getEntityId());
                if (s == null) others.put(q.getEntityId(), s = new int[] { q.isSprinting() ? 1 : 0, q.isSwingInProgress ? 1 : 0, q.swingProgressInt });
                boolean run = q.isSprinting();
                if (run && s[0] == 0 && Easter.fullHev(q)) play("hev.sprint", q.posX, q.posY, q.posZ, 0.7F);
                s[0] = run ? 1 : 0;
                boolean swing = q.isSwingInProgress, fresh = swing && (s[1] == 0 || q.swingProgressInt < s[2]);
                s[1] = swing ? 1 : 0;
                s[2] = q.swingProgressInt;
                if (fresh && Easter.crowbar(q.getHeldItem())) play("hev.cbar_miss", q.posX, q.posY + 1.2, q.posZ, 0.8F);
            }
            others.keySet().retainAll(seen);
        }

        /** Own player: a dash in the suit and crowbar swings. */
        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer p = mc.thePlayer;
            if (e.phase != TickEvent.Phase.END || p == null || mc.isGamePaused()) return;
            others(mc, p);
            boolean run = p.isSprinting();
            if (run && !sprinting && Easter.fullHev(p)) playSelf("hev.sprint", 0.7F);
            sprinting = run;
            // a new swing: started or started again (a button held on a block)
            boolean swing = p.isSwingInProgress;
            boolean fresh = swing && (!swinging || p.swingProgressInt < lastSwing);
            swinging = swing;
            lastSwing = p.swingProgressInt;
            if (!fresh || !Easter.crowbar(p.getHeldItem())) return;
            MovingObjectPosition mop = mc.objectMouseOver;
            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && mop.entityHit != null)
                play("hev.cbar_hitbod", mop.entityHit.posX, mop.entityHit.posY + mop.entityHit.height / 2, mop.entityHit.posZ, 1.0F);
            else if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
                play("hev.cbar_hit", mop.blockX + 0.5, mop.blockY + 0.5, mop.blockZ + 0.5, 0.9F);
            else
                playSelf("hev.cbar_miss", 0.8F);
        }
    }
}
