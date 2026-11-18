package com.divinerpgextended.client;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundEventAccessorComposite;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundRegistry;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Divine's sounds.json puts every mob and boss voice into the "master" category: the "Hostile Creatures" and "Friendly Creatures"
 * sliders do not reach them, only the master volume does. After every resource reload (after the sound handler has read the sounds) the
 * Divine events with "master" get the category of their creature: friendly ones (pets, merchants, the bunny) - "Friendly Creatures", the
 * rest - "Hostile Creatures". Weapon and item sounds stay in "neutral", as vanilla keeps the bow there. A resource pack that sets its own
 * category for a Divine sound keeps it.
 */
public final class DivineSoundCategories implements IResourceManagerReloadListener {

    static final Logger LOG = LogManager.getLogger("divinerpgextended");
    static final String DOMAIN = "divinerpg";
    /** Voices of friendly creatures: the bunny, Jack-o-Man and the Livestock Merchant, the Grizzle, Wraith and Golem of Rejuvenation pets. */
    static final Set<String> FRIENDLY = new HashSet<String>(Arrays.asList("bunny", "bunnyHurt", "jackOMan", "livestockMerchant",
        "livestockMerchantHurt", "grizzle", "grizzleHurt", "wraith", "wraithHurt", "golem"));

    private static Field category;

    public static void register() {
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new DivineSoundCategories());
    }

    /** The category a Divine sound should have; {@code now} - the one it has after loading. */
    static SoundCategory target(String name, SoundCategory now) {
        if (now != SoundCategory.MASTER) return now;
        return FRIENDLY.contains(name) ? SoundCategory.ANIMALS : SoundCategory.MOBS;
    }

    @Override
    public void onResourceManagerReload(IResourceManager m) {
        SoundHandler h = Minecraft.getMinecraft().getSoundHandler();
        if (h == null) return;
        try {
            int[] n = fix((SoundRegistry) ReflectionHelper.getPrivateValue(SoundHandler.class, h, "sndRegistry", "field_147697_e"));
            if (n[0] + n[1] > 0) LOG.info("Divine sounds: " + n[0] + " to hostile creatures, " + n[1] + " to friendly creatures");
        } catch (Throwable t) {
            LOG.warn("Divine sounds: categories not fixed, the creature sliders will not reach them: " + t);
        }
    }

    /** Moves the Divine events of the registry into their categories; returns {hostile, friendly} moved. */
    static int[] fix(SoundRegistry reg) throws IllegalAccessException {
        if (category == null) category = ReflectionHelper.findField(SoundEventAccessorComposite.class, "field_148732_d");
        int[] n = new int[2];
        for (Object k : reg.getKeys()) {
            ResourceLocation r = (ResourceLocation) k;
            if (!DOMAIN.equals(r.getResourceDomain())) continue;
            SoundEventAccessorComposite c = (SoundEventAccessorComposite) reg.getObject(r);
            if (c == null) continue;
            SoundCategory want = target(r.getResourcePath(), c.getSoundCategory());
            if (want == c.getSoundCategory()) continue;
            category.set(c, want);
            n[want == SoundCategory.MOBS ? 0 : 1]++;
        }
        return n;
    }
}
