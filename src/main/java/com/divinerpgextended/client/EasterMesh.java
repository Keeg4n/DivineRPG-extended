package com.divinerpgextended.client;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * A detailed Easter egg model from resources ({@code models/easter/*.mesh}, built by ~/mc/tools/easter_build.py together with the atlas):
 * "part + bone" groups of quadrilaterals with normals. Each group is drawn with an OpenGL display list.
 * A resource reload (a resource pack switched on or off) drops the loaded meshes, the next draw reads them again: a pack may bring its own
 * meshes together with its textures (the double resolution Wanderer armor pack does), and the old UV layout would not fit the new atlas.
 */
public final class EasterMesh {

    final ResourceLocation file;
    final Map<String, float[]> groups = new HashMap<String, float[]>(); // "part/bone" -> 23 numbers per quadrilateral
    final Map<String, Integer> lists = new HashMap<String, Integer>();
    boolean loaded;

    private static final List<EasterMesh> LOADED = new ArrayList<EasterMesh>();
    private static boolean listening;

    /** First load: the reload listener is registered before the mesh is added (registration calls it at once). */
    private static void watch(EasterMesh m) {
        if (!listening) {
            listening = true;
            ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new IResourceManagerReloadListener() {

                @Override
                public void onResourceManagerReload(IResourceManager rm) {
                    for (EasterMesh e : LOADED) e.reset();
                    LOADED.clear();
                }
            });
        }
        LOADED.add(m);
    }

    void reset() {
        for (Integer l : lists.values()) GLAllocation.deleteDisplayLists(l);
        lists.clear();
        groups.clear();
        loaded = false;
    }

    public EasterMesh(String name) {
        file = new ResourceLocation("divinerpgextended", "models/easter/" + name + ".mesh");
    }

    /** A model not from the Easter eggs: the full path to the .mesh. */
    public EasterMesh(ResourceLocation file) {
        this.file = file;
    }

    void load() {
        if (loaded) return;
        loaded = true;
        watch(this);
        try {
            InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(file).getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String key = null;
            List<Float> cur = null;
            Map<String, List<Float>> tmp = new HashMap<String, List<Float>>();
            for (String line; (line = r.readLine()) != null;) {
                if (line.startsWith("p ")) {
                    String[] t = line.trim().split("\\s+");
                    key = t[1] + "/" + t[2];
                    cur = tmp.get(key);
                    if (cur == null) tmp.put(key, cur = new ArrayList<Float>());
                } else if (line.startsWith("q ") && cur != null) {
                    String[] t = line.trim().split("\\s+");
                    for (int i = 1; i < t.length; i++) cur.add(Float.parseFloat(t[i]));
                }
            }
            r.close();
            for (Map.Entry<String, List<Float>> e : tmp.entrySet()) {
                float[] a = new float[e.getValue().size()];
                for (int i = 0; i < a.length; i++) a[i] = e.getValue().get(i);
                groups.put(e.getKey(), a);
            }
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger("divinerpgextended").warn("Model " + file + " not loaded: " + e);
        }
    }

    public boolean has(String piece, String bone) {
        load();
        return groups.containsKey(piece + "/" + bone);
    }

    /** Draw a group; scale - the size of a model unit (0.0625 for armor, 1/16 for an item). */
    public void render(String piece, String bone, float scale) {
        load();
        String key = piece + "/" + bone + "@" + scale;
        Integer list = lists.get(key);
        if (list == null) {
            float[] q = groups.get(piece + "/" + bone);
            if (q == null) return;
            list = GLAllocation.generateDisplayLists(1);
            GL11.glNewList(list, GL11.GL_COMPILE);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            for (int i = 0; i + 23 <= q.length; i += 23) {
                t.setNormal(q[i], q[i + 1], q[i + 2]);
                for (int v = 0; v < 4; v++) {
                    int o = i + 3 + v * 5;
                    t.addVertexWithUV(q[o] * scale, q[o + 1] * scale, q[o + 2] * scale, q[o + 3], q[o + 4]);
                }
            }
            t.draw();
            GL11.glEndList();
            lists.put(key, list);
        }
        GL11.glCallList(list);
    }

    /** Number of quadrilaterals in the group (for checking). */
    public int quads(String piece, String bone) {
        load();
        float[] q = groups.get(piece + "/" + bone);
        return q == null ? 0 : q.length / 23;
    }
}
