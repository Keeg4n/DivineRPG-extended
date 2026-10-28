package com.divinerpgextended.magic;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

import com.divinerpgextended.DivineRPGExtended;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Эффект арканного зелья как настоящий эффект Minecraft: виден в инвентаре со своим значком, снимается молоком,
 * сохраняется и синхронизируется как ванильный. Уровень - усиление + 1. Сами эффекты считает {@link ArcaneEffects}.
 * Номера выбираются в postInit среди свободных от 127 вниз (зелья других модов уже на местах) или берутся из конфига
 * (arcanePotionIds), массив при нужде расширяется до 128.
 */
public class PotionArcane extends Potion {

    /** Номер эффекта в пакете и в сохранении игрока - знаковый байт: выше 127 клиент и загрузка ломаются. */
    public static final int MAX_ID = 127;
    private static final Potion[] BY_EFFECT = new Potion[ArcaneEffect.values().length];
    @SideOnly(Side.CLIENT)
    private static ResourceLocation icons;
    public final ArcaneEffect effect;

    private PotionArcane(int id, ArcaneEffect e) {
        super(id, false, e.color);
        effect = e;
        setPotionName("dre.effect." + e.key());
    }

    /** Эффект Minecraft для арканного эффекта (null - зелье убрано или эффекты ещё не зарегистрированы). */
    public static Potion of(ArcaneEffect e) {
        return BY_EFFECT[e.ordinal()];
    }

    public static void register(int[] fixed) {
        widen(128);
        int k = 0, next = MAX_ID;
        for (ArcaneEffect e : ArcaneEffect.values()) {
            if (e.removed()) continue;
            int id = k < fixed.length ? fixed[k] : -1;
            k++;
            if (id <= 0 || id > MAX_ID || Potion.potionTypes[id] != null) {
                if (id > 0) DivineRPGExtended.LOG.warn("Arcane potion id " + id + " for " + e.key() + " is taken or out of range, picking a free one");
                while (next > 31 && Potion.potionTypes[next] != null) next--;
                if (next <= 31) {
                    DivineRPGExtended.LOG.error("No free potion id for arcane effect " + e.key());
                    continue;
                }
                id = next--;
            }
            BY_EFFECT[e.ordinal()] = new PotionArcane(id, e);
        }
    }

    /** Массив эффектов Minecraft - не меньше n (так же делают другие моды; уже расширенный не трогаем). */
    private static void widen(int n) {
        if (Potion.potionTypes.length >= n) return;
        try {
            Field f = null;
            for (Field x : Potion.class.getDeclaredFields())
                if (x.getType() == Potion[].class && Modifier.isStatic(x.getModifiers())) f = x;
            f.setAccessible(true);
            Field mods = Field.class.getDeclaredField("modifiers");
            mods.setAccessible(true);
            mods.setInt(f, f.getModifiers() & ~Modifier.FINAL);
            Potion[] wider = new Potion[n];
            System.arraycopy(Potion.potionTypes, 0, wider, 0, Potion.potionTypes.length);
            f.set(null, wider);
        } catch (Throwable t) {
            DivineRPGExtended.LOG.error("Could not widen the potion array", t);
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return false; // действие - в событиях урона и тиков, не по таймеру эффекта
    }

    @Override
    public boolean hasStatusIcon() {
        return false; // ванильный лист значков не наш - рисуем свой в renderInventoryEffect
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventoryEffect(int x, int y, PotionEffect pe, Minecraft mc) {
        if (icons == null) icons = new ResourceLocation("divinerpgextended", "textures/gui/arcane_effects.png");
        mc.getTextureManager().bindTexture(icons);
        int i = effect.ordinal();
        double u = (i % 8) * 18 / 144.0, v = (i / 8) * 18 / 36.0, du = 18 / 144.0, dv = 18 / 36.0;
        int px = x + 6, py = y + 7;
        org.lwjgl.opengl.GL11.glColor4f(1F, 1F, 1F, 1F);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(px, py + 18, 0, u, v + dv);
        t.addVertexWithUV(px + 18, py + 18, 0, u + du, v + dv);
        t.addVertexWithUV(px + 18, py, 0, u + du, v);
        t.addVertexWithUV(px, py, 0, u, v);
        t.draw();
    }
}
