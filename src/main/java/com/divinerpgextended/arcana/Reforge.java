package com.divinerpgextended.arcana;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

/**
 * Reforge properties at Captain Merik (like Terraria modifiers). Only for Divine swords. There are bad ones (a risk when
 * reforging), common good ones, a weapon world property (drops only for a sword of that world) and a rare Legendary one.
 */
public enum Reforge {

    DULL(10, null, -0.10F, EnumChatFormatting.DARK_GRAY),
    CLUMSY(10, null, -0.05F, EnumChatFormatting.DARK_GRAY),
    SHARP(25, null, 0.08F, EnumChatFormatting.WHITE),
    KEEN(10, null, 0.15F, EnumChatFormatting.WHITE),
    BLOODTHIRSTY(10, null, 0, EnumChatFormatting.RED),
    HEAVY(12, null, 0.03F, EnumChatFormatting.WHITE),
    ABYSSAL(18, WeaponOrigin.OVERWORLD, 0, EnumChatFormatting.AQUA),
    BLAZING(18, WeaponOrigin.NETHER, 0, EnumChatFormatting.GOLD),
    FROSTY(18, WeaponOrigin.ICEIKA, 0, EnumChatFormatting.AQUA),
    ARCANE(18, WeaponOrigin.ARCANA, 0, EnumChatFormatting.LIGHT_PURPLE),
    TWILIT(18, WeaponOrigin.TWILIGHT, 0, EnumChatFormatting.GREEN),
    DREAMY(18, WeaponOrigin.VETHEA, 0, EnumChatFormatting.DARK_AQUA),
    LEGENDARY(5, null, 0.12F, EnumChatFormatting.GOLD);

    private static final String TAG = "dreForge";

    public final int weight;
    /** null - a common property, otherwise only for weapons of this world. */
    public final WeaponOrigin world;
    /** Permanent damage bonus (a fraction). */
    public final float damage;
    public final EnumChatFormatting color;

    Reforge(int weight, WeaponOrigin world, float damage, EnumChatFormatting color) {
        this.weight = weight;
        this.world = world;
        this.damage = damage;
        this.color = color;
    }

    public String key() {
        return name().toLowerCase();
    }

    public boolean bad() {
        return damage < 0;
    }

    public static Reforge of(ItemStack s) {
        if (s == null || !s.hasTagCompound() || !s.getTagCompound().hasKey(TAG)) return null;
        try {
            return valueOf(s.getTagCompound().getString(TAG));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void apply(ItemStack s) {
        if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
        s.getTagCompound().setString(TAG, name());
    }

    /** Random property for a sword of this world (properties of other worlds do not drop). */
    public static Reforge roll(Random r, WeaponOrigin origin) {
        List<Reforge> pool = new ArrayList<Reforge>();
        int total = 0;
        for (Reforge f : values()) {
            if (f.world != null && f.world != origin) continue;
            pool.add(f);
            total += f.weight;
        }
        int n = r.nextInt(total);
        for (Reforge f : pool) {
            n -= f.weight;
            if (n < 0) return f;
        }
        return SHARP;
    }
}
