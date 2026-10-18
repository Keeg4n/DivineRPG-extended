package com.divinerpgextended.entity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.world.twilight.MortumArenas;
import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Харон - перевозчик на пристанях Леты, тёмная фигура в балахоне. Не торгует и не возит: покажи ему кристалл босса
 * Мортума - скажет, в какой стороне и как далеко арена этого босса, и пустит туда по воздуху дымный след. Без
 * кристалла - говорит, что ему показать.
 */
public class EntityCharon extends EntityWanderer {

    public static final int SEARCH = 8, TRAIL = 14;
    private static final String[] DIRS = { "s", "sw", "w", "nw", "n", "ne", "e", "se" };

    public EntityCharon(World w) {
        super(w);
        setHome(TwilightWorld.MORTUM);
    }

    @Override
    public boolean interact(EntityPlayer p) {
        if (worldObj.isRemote) return true;
        ItemStack h = p.getHeldItem();
        MortumArenas.Type t = h == null ? null : MortumArenas.ofCrystal(h.getItem());
        if (t == null) {
            say(p, "dre.charon.hello");
            return true;
        }
        int[] c = MortumArenas.nearestOf(worldObj, t, posX, posZ, SEARCH);
        if (c == null) {
            say(p, "dre.charon.none");
            return true;
        }
        double dx = c[0] + 0.5 - p.posX, dz = c[2] + 0.5 - p.posZ, dist = Math.sqrt(dx * dx + dz * dz);
        say(p, "dre.charon.way." + t.key(), new ChatComponentTranslation("dre.dir." + direction(dx, dz)), (int) Math.round(dist / 10) * 10);
        trail((WorldServer) worldObj, p, dx / Math.max(1, dist), dz / Math.max(1, dist));
        return true;
    }

    /** Сторона света к точке (8 румбов): ключ s, sw, w, nw, n, ne, e, se. */
    public static String direction(double dx, double dz) {
        double yaw = Math.toDegrees(Math.atan2(-dx, dz)); // 0 - юг (+Z), 90 - запад, 180 - север, -90 - восток
        int i = (int) Math.floor(((yaw + 360) % 360 + 22.5) / 45) % 8;
        return DIRS[i];
    }

    /** Дымный след от игрока в сторону арены. */
    private static void trail(WorldServer w, EntityPlayer p, double nx, double nz) {
        for (int i = 2; i <= TRAIL; i++) w.func_147487_a("witchMagic", p.posX + nx * i, p.posY + 1.2, p.posZ + nz * i, 4, 0.1, 0.1, 0.1, 0.01);
        w.playSoundAtEntity(p, "ambient.cave.cave", 0.7F, 0.6F);
    }

    private void say(EntityPlayer p, String key, Object... args) {
        ChatComponentTranslation m = new ChatComponentTranslation("dre.charon.says", new ChatComponentTranslation(key, args));
        m.getChatStyle().setColor(EnumChatFormatting.DARK_AQUA).setItalic(true);
        p.addChatMessage(m);
    }

    @Override
    public String getCommandSenderName() {
        return hasCustomNameTag() ? getCustomNameTag() : StatCollector.translateToLocal("entity.divinerpgextended.Charon.name");
    }

    /** Харон молчит. */
    @Override
    protected String getHurtSound() {
        return null;
    }

    @Override
    protected String getDeathSound() {
        return null;
    }
}
