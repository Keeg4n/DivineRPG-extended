package com.divinerpgextended.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;

import org.lwjgl.opengl.GL11;

import com.divinerpgextended.accessory.Sockets;
import com.divinerpgextended.accessory.WearData;
import com.divinerpgextended.item.ItemWandererArmor;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.trinket.ItemTrinket;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Аксессуары видны на теле, как фенечки Ботании: вещи из гнёзд надетой брони рисуются поверх модели игрока (и чужих
 * игроков - броня с гнёздами приходит клиенту вместе с NBT). Посадка - {@link WearData}: для каждой кости берётся профиль той
 * части брони, что её закрывает (голова - шлем, торс и руки - нагрудник, ноги - ботинки, иначе поножи): облик Странника, другая
 * броня или без брони. Грани кости собираются в список OpenGL один раз.
 */
public class AccessoryWear {

    private final Map<String, Integer> lists = new HashMap<String, Integer>();

    @SubscribeEvent
    public void onRender(RenderPlayerEvent.Specials.Post e) {
        if (!com.divinerpgextended.Config.accessoriesOnBody) return;
        EntityPlayer p = e.entityPlayer;
        if (p.isInvisible()) return;
        List<String> keys = worn(p);
        if (keys.isEmpty()) return;
        ModelBiped m = e.renderer.modelBipedMain;
        ModelRenderer[] bones = { m.bipedHead, m.bipedBody, m.bipedRightArm, m.bipedLeftArm, m.bipedRightLeg, m.bipedLeftLeg };
        String[] prof = { profile(p.inventory.armorInventory[3]), profile(p.inventory.armorInventory[2]), null, null,
            profile(p.inventory.armorInventory[0] != null ? p.inventory.armorInventory[0] : p.inventory.armorInventory[1]), null };
        prof[2] = prof[3] = prof[1];
        prof[5] = prof[4];
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_NORMALIZE);
        for (int b = 0; b < bones.length; b++) {
            boolean moved = false;
            for (String key : keys) {
                int list = list(key, prof[b], b);
                if (list == 0) continue;
                if (!moved) {
                    GL11.glPushMatrix();
                    bones[b].postRender(0.0625F);
                    GL11.glScalef(0.0625F, 0.0625F, 0.0625F);
                    moved = true;
                }
                GL11.glCallList(list);
            }
            if (moved) GL11.glPopMatrix();
        }
        GL11.glPopAttrib();
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** Ключи аксессуаров в гнёздах надетой брони (без повторов). */
    static List<String> worn(EntityPlayer p) {
        List<String> out = new ArrayList<String>();
        for (ItemStack armor : p.inventory.armorInventory) {
            if (armor == null || !Sockets.socketable(armor)) continue;
            for (ItemStack s : Sockets.contents(armor)) {
                if (s == null || s.getItem() != ModItems.trinket) continue;
                String k = ItemTrinket.of(s).key();
                if (!out.contains(k)) out.add(k);
            }
        }
        return out;
    }

    /** Профиль посадки для части брони: облик Странника, другая броня (divine) или без брони (bare). */
    static String profile(ItemStack armor) {
        if (armor == null) return "bare";
        if (armor.getItem() instanceof ItemWandererArmor) return ItemWandererArmor.lookName(ItemWandererArmor.look(armor));
        return "divine";
    }

    private int list(String key, String profile, int bone) {
        String id = key + '/' + profile + '/' + bone;
        Integer l = lists.get(id);
        if (l != null) return l;
        WearData.Mesh mesh = WearData.mesh(key, profile, WearData.BONES[bone]);
        if (mesh == null || mesh.quads.isEmpty()) {
            lists.put(id, 0);
            return 0;
        }
        int n = GLAllocation.generateDisplayLists(1);
        GL11.glNewList(n, GL11.GL_COMPILE);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (float[] q : mesh.quads) {
            t.setNormal(q[12], q[13], q[14]);
            t.setColorOpaque_F(q[15], q[16], q[17]);
            for (int i = 0; i < 4; i++) t.addVertex(q[i * 3], q[i * 3 + 1], q[i * 3 + 2]);
        }
        t.draw();
        GL11.glEndList();
        lists.put(id, n);
        return n;
    }
}
