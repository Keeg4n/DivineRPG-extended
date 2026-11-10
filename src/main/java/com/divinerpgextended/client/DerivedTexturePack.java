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
 * Встроенный набор ресурсов с текстурами, собранными из текстур установленного DivineRPG ({@link DerivedTextures}).
 * Ставится в preInit клиента в конец списка ресурсов модов: Forge добавляет ресурсы всех модов ещё до preInit, так что этот
 * набор идёт после DivineRPG и перекрывает и её текстуры, которые мы перекрашиваем (броня tormented).
 */
public class DerivedTexturePack implements IResourcePack {

    private final Map<String, byte[]> cache = new HashMap<String, byte[]>();

    @SuppressWarnings("unchecked")
    public static void install() {
        try {
            List<IResourcePack> packs = (List<IResourcePack>) ReflectionHelper.getPrivateValue(Minecraft.class, Minecraft.getMinecraft(), "defaultResourcePacks",
                "field_110449_ao");
            packs.add(new DerivedTexturePack());
        } catch (Throwable t) {
            DivineRPGExtended.LOG.warn("Текстуры из DivineRPG не подключены: " + t);
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
        BufferedImage img = r == null ? null : DerivedTextures.build(r);
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
        if (!DerivedTextures.recipes().containsKey(key(rl))) return false;
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
