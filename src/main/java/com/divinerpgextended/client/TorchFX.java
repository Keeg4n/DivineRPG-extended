package com.divinerpgextended.client;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Частицы факелов миров вместо обычного огонька: у Эдема из солнца бьют лучи, у Дикого леса падают капли и всплывают
 * лунные искры, Апалачию затягивает в разлом, у Небесного терна ветер сносит облачка и кружит искры, у Мортума капает
 * кровь и поднимается тёмный дым, у адского факела летят угли.
 */
@SideOnly(Side.CLIENT)
public class TorchFX extends EntityFX {

    /** Текстуры частиц игры: дымка (0..7, по убыванию размера), капля, искра фейерверка (160..167, гаснет). */
    static final int PUFF = 0, DROP = 113, SPARK = 160;

    private final float size;
    private final int tex;
    private final boolean glow, fade;
    /** Затягивать к точке (разлом Апалачии). */
    private double cx, cy, cz;
    private boolean pull;
    /** Кружить вокруг точки: угол, радиус, шаг. */
    private double angle, radius, spin;
    private boolean orbit;

    TorchFX(World w, double x, double y, double z, double mx, double my, double mz, int color, int tex, float size, int life,
            float gravity, boolean glow) {
        super(w, x, y, z, 0, 0, 0);
        motionX = mx;
        motionY = my;
        motionZ = mz;
        particleRed = (color >> 16 & 255) / 255F;
        particleGreen = (color >> 8 & 255) / 255F;
        particleBlue = (color & 255) / 255F;
        this.tex = tex;
        this.size = size;
        this.glow = glow;
        this.fade = tex == SPARK || tex == PUFF;
        particleScale = size;
        particleMaxAge = life;
        particleGravity = gravity;
        noClip = gravity == 0;
        setParticleTextureIndex(tex == PUFF ? 7 : tex);
    }

    TorchFX pull(double x, double y, double z) {
        pull = true;
        cx = x;
        cy = y;
        cz = z;
        return this;
    }

    TorchFX orbit(double x, double z, double angle, double radius, double spin) {
        orbit = true;
        cx = x;
        cz = z;
        this.angle = angle;
        this.radius = radius;
        this.spin = spin;
        return this;
    }

    @Override
    public void onUpdate() {
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        if (particleAge++ >= particleMaxAge) {
            setDead();
            return;
        }
        float t = (float) particleAge / particleMaxAge;
        if (tex == SPARK) setParticleTextureIndex(SPARK + 7 - (int) (t * 7.99F));
        else if (tex == PUFF) setParticleTextureIndex(7 - (int) (t * 7.99F));
        if (orbit) {
            angle += spin;
            radius *= 1.04;
            setPosition(cx + Math.cos(angle) * radius, posY + motionY, cz + Math.sin(angle) * radius);
            return;
        }
        if (pull) { // разгон к центру, у центра гаснет
            motionX += (cx - posX) * 0.04;
            motionY += (cy - posY) * 0.04;
            motionZ += (cz - posZ) * 0.04;
            if ((cx - posX) * (cx - posX) + (cy - posY) * (cy - posY) + (cz - posZ) * (cz - posZ) < 0.003) setDead();
        }
        motionY -= 0.04 * particleGravity;
        moveEntity(motionX, motionY, motionZ);
        double drag = pull ? 0.85 : 0.96;
        motionX *= drag;
        motionY *= drag;
        motionZ *= drag;
        if (onGround && particleGravity > 0) setDead();
    }

    @Override
    public void renderParticle(Tessellator tes, float pt, float rx, float rz, float ryz, float rxy, float rxz) {
        float t = (particleAge + pt) / particleMaxAge;
        particleScale = fade ? size * (1 - t * 0.6F) : size;
        particleAlpha = glow ? 1 - t * t : 1;
        super.renderParticle(tes, pt, rx, rz, ryz, rxy, rxz);
    }

    @Override
    public int getBrightnessForRender(float pt) {
        return glow ? 0xF000F0 : super.getBrightnessForRender(pt);
    }

    @Override
    public float getBrightness(float pt) {
        return glow ? 1 : super.getBrightness(pt);
    }

    private static void add(EntityFX fx) {
        Minecraft.getMinecraft().effectRenderer.addEffect(fx);
    }

    private static double jit(Random r, double d) {
        return (r.nextDouble() - 0.5) * d;
    }

    /** Частицы факела {@code world} (null - адский) у головки (x, y, z). */
    public static void spawn(World w, TwilightWorld world, double x, double y, double z, Random r) {
        int set = Minecraft.getMinecraft().gameSettings.particleSetting; // 0 - все, 1 - меньше, 2 - минимум
        if (set == 2 && r.nextInt(3) != 0) return;
        boolean less = set > 0;
        if (world == null) { // ад: угли рвутся вверх, иногда хлопок лавы
            w.spawnParticle("flame", x, y, z, 0, 0, 0);
            for (int i = less ? 1 : 2; i > 0; i--)
                add(new TorchFX(w, x + jit(r, 0.15), y + 0.05, z + jit(r, 0.15), jit(r, 0.03), 0.04 + r.nextDouble() * 0.04, jit(r, 0.03),
                    r.nextBoolean() ? 0xFF8A1A : 0xFFC040, SPARK, 0.35F, 14 + r.nextInt(10), -0.05F, true));
            if (r.nextInt(8) == 0) w.spawnParticle("lava", x, y, z, 0, 0, 0);
            if (r.nextInt(3) == 0) w.spawnParticle("smoke", x, y + 0.1, z, 0, 0, 0);
            return;
        }
        switch (world) {
            case EDEN: { // солнце: лучи во все стороны и тёплое сияние вверх
                int n = less ? 3 : 5;
                double a0 = r.nextDouble() * Math.PI * 2;
                for (int i = 0; i < n; i++) {
                    double a = a0 + i * Math.PI * 2 / n, tilt = jit(r, 0.6);
                    add(new TorchFX(w, x, y, z, Math.cos(a) * 0.07, tilt * 0.07, Math.sin(a) * 0.07,
                        i % 2 == 0 ? 0xFFE27A : 0xFFFBE0, SPARK, 0.3F, 10 + r.nextInt(4), 0, true));
                }
                add(new TorchFX(w, x, y + 0.05, z, 0, 0.015, 0, 0xFFD23F, PUFF, 0.9F, 14, 0, true));
                break;
            }
            case WILDWOOD: { // луна и вода: капля срывается вниз, лунные искры всплывают покачиваясь
                add(new TorchFX(w, x + jit(r, 0.1), y - 0.15, z + jit(r, 0.1), 0, -0.01, 0, 0x3C7FE0, DROP, 0.8F, 40, 0.6F, false));
                for (int i = less ? 1 : 2; i > 0; i--)
                    add(new TorchFX(w, x + jit(r, 0.4), y + jit(r, 0.2), z + jit(r, 0.4), jit(r, 0.01), 0.012, jit(r, 0.01),
                        r.nextBoolean() ? 0xC4ECFF : 0x7FC4E6, SPARK, 0.25F, 25 + r.nextInt(15), 0, true));
                break;
            }
            case APALACHIA: { // разлом: всё вокруг затягивает внутрь, мелькают бирюзовые сбои
                for (int i = less ? 2 : 4; i > 0; i--) {
                    double a = r.nextDouble() * Math.PI * 2, d = 0.6 + r.nextDouble() * 0.4;
                    add(new TorchFX(w, x + Math.cos(a) * d, y + jit(r, 0.8), z + Math.sin(a) * d, 0, 0, 0,
                        r.nextBoolean() ? 0xFF4FD8 : 0xB04CE0, PUFF, 0.5F, 30, 0, true).pull(x, y, z));
                }
                if (r.nextInt(3) == 0)
                    add(new TorchFX(w, x + jit(r, 0.8), y + jit(r, 0.6), z + jit(r, 0.8), 0, 0, 0, 0x3DFFF2, SPARK, 0.35F, 4, 0, true));
                break;
            }
            case SKYTHERN: { // ветер: облачка сносит в одну сторону (поворачивается со временем), искры вьются вокруг
                double wind = w.getTotalWorldTime() / 400.0;
                for (int i = less ? 1 : 2; i > 0; i--)
                    add(new TorchFX(w, x + jit(r, 0.2), y + 0.05, z + jit(r, 0.2), Math.cos(wind) * 0.05, 0.008, Math.sin(wind) * 0.05,
                        r.nextBoolean() ? 0xFFFFFF : 0xE6F0F8, PUFF, 1.1F, 30 + r.nextInt(10), 0, false));
                double a = r.nextDouble() * Math.PI * 2;
                add(new TorchFX(w, x, y - 0.1, z, 0, 0.02, 0, 0xA8DCFF, SPARK, 0.3F, 26, 0, true).orbit(x, z, a, 0.2, 0.35));
                if (!less) add(new TorchFX(w, x, y - 0.1, z, 0, 0.02, 0, 0x5FB8F5, SPARK, 0.3F, 26, 0, true).orbit(x, z, a + Math.PI, 0.2, 0.35));
                break;
            }
            default: { // Мортум: кровь капает, над каплей тёмный дым, изредка багровые искры
                add(new TorchFX(w, x + jit(r, 0.1), y - 0.15, z + jit(r, 0.1), 0, -0.01, 0, 0x8A0000, DROP, 0.8F, 40, 0.7F, false));
                add(new TorchFX(w, x + jit(r, 0.1), y + 0.1, z + jit(r, 0.1), jit(r, 0.01), 0.025, jit(r, 0.01),
                    r.nextBoolean() ? 0x2A0505 : 0x4A0000, PUFF, 1.0F, 30 + r.nextInt(10), 0, false));
                if (r.nextInt(4) == 0)
                    add(new TorchFX(w, x, y, z, jit(r, 0.06), 0.03, jit(r, 0.06), 0xE01010, SPARK, 0.3F, 16, 0.1F, true));
            }
        }
    }
}
