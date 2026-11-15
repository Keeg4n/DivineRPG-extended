package com.divinerpgextended.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

import com.divinerpgextended.DivineRPGExtended;
import com.divinerpgextended.registry.DerivedTextures;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * A built-in resource pack with textures assembled from the textures of the installed DivineRPG ({@link DerivedTextures}).
 * Installed in the client preInit at the end of the mod resource list: Forge adds the resources of all mods before preInit, so this
 * pack goes after DivineRPG and overrides even its textures that we recolor (tormented armor).
 */
public class DerivedTexturePack implements IResourcePack {

    private final Map<String, byte[]> cache = new HashMap<String, byte[]>();
    /** Our own pictures over Divine textures: the crab anchor and the Liopleurodon Anchor are summons, real anchors are drawn for them. */
    static final Map<String, String> OWN = new HashMap<String, String>();

    static {
        OWN.put("divinerpg:textures/items/crabAnchor.png", "/assets/divinerpgextended/textures/items/anchor_crab.png");
        OWN.put("divinerpg:textures/items/liopleurodonAnchor.png", "/assets/divinerpgextended/textures/items/anchor_lio.png");
    }

    @SuppressWarnings("unchecked")
    public static void install() {
        try {
            List<IResourcePack> packs = (List<IResourcePack>) ReflectionHelper.getPrivateValue(Minecraft.class, Minecraft.getMinecraft(), "defaultResourcePacks",
                "field_110449_ao");
            packs.add(new DerivedTexturePack());
        } catch (Throwable t) {
            DivineRPGExtended.LOG.warn("Textures from DivineRPG not hooked: " + t);
        }
    }

    private static String key(ResourceLocation rl) {
        return rl.getResourceDomain() + ":" + rl.getResourcePath();
    }

    private synchronized byte[] png(ResourceLocation rl) throws IOException {
        String k = key(rl);
        byte[] b = cache.get(k);
        if (b != null) return b;
        DerivedTextures.Recipe r = DerivedTextures.recipes().get(k);
        com.divinerpgextended.block.BlockLockedChest.Kind chest = ChestTextures.kind(k);
        String own = OWN.get(k); // our own picture instead of the Divine texture (summon anchors)
        if (own != null) {
            InputStream in = DerivedTexturePack.class.getResourceAsStream(own);
            if (in == null) throw new FileNotFoundException(k);
            try {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                for (int n; (n = in.read(chunk)) > 0;) buf.write(chunk, 0, n);
                b = buf.toByteArray();
            } finally {
                in.close();
            }
            cache.put(k, b);
            return b;
        }
        com.divinerpgextended.world.twilight.TwilightWorld keyWorld = ChestTextures.keyWorld(k);
        BufferedImage img = chest != null ? ChestTextures.build(chest) : keyWorld != null ? ChestTextures.buildKey(keyWorld) : r == null ? null : DerivedTextures.build(r);
        if (img == null) throw new FileNotFoundException(k);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        b = out.toByteArray();
        cache.put(k, b);
        return b;
    }

    @Override
    public InputStream getInputStream(ResourceLocation rl) throws IOException {
        return new ByteArrayInputStream(png(rl));
    }

    @Override
    public boolean resourceExists(ResourceLocation rl) {
        if (!DerivedTextures.recipes().containsKey(key(rl)) && ChestTextures.kind(key(rl)) == null && ChestTextures.keyWorld(key(rl)) == null && !OWN.containsKey(key(rl)))
            return false;
        try {
            png(rl);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public Set<String> getResourceDomains() {
        Set<String> d = new HashSet<String>();
        for (String k : DerivedTextures.recipes().keySet()) d.add(k.substring(0, k.indexOf(':')));
        d.add(ChestTextures.DOMAIN); // locked chests ({@link ChestTextures})
        d.add("divinerpg"); // our own anchors over Divine
        return d;
    }

    @Override
    public IMetadataSection getPackMetadata(IMetadataSerializer s, String section) {
        return null;
    }

    @Override
    public BufferedImage getPackImage() {
        return null;
    }

    @Override
    public String getPackName() {
        return "DivineRPG Extended: textures built from DivineRPG";
    }
}
