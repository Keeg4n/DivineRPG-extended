package com.divinerpgextended.world.twilight;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;

import com.divinerpgextended.Config;
import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.item.ArmorData;
import com.divinerpgextended.perk.HomeArmor;
import com.divinerpgextended.perk.Perk;
import com.divinerpgextended.perk.PerkEffects;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Солнце Эдема не заходит: подземные мобы горят на нём, и игрок тоже перегревается. Шкала 0-100 растёт под открытым небом за
 * {@link #FULL_SECONDS} секунд, спадает в тени и быстрее в воде. Пороги: 50 - слабость, 75 - ещё и тошнота, 100 - горишь, пока не
 * остынешь. Сет Frozen и Солнечный нимб не греются вовсе; огненная броня, сет Эдема, броня Странника с Эдема и выше и перк холода - вдвое медленнее.
 */
public class EdenSun {

    public static final float FULL_SECONDS = 300F, SHADE_COOL = 1F, WATER_COOL = 5F, AWAY_COOL = 5F;
    private static final String KEY = "dreSunHeat";
    private static final Item[] FROZEN = { VanillaItemsArmor.frozenHelmet, VanillaItemsArmor.frozenBody, VanillaItemsArmor.frozenLegs, VanillaItemsArmor.frozenBoots };

    private static NBTTagCompound data(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static float heat(EntityPlayer p) {
        return data(p).getFloat(KEY);
    }

    public static void setHeat(EntityPlayer p, float v) {
        data(p).setFloat(KEY, v);
    }

    /** Во сколько раз быстрее обычного греется этот игрок на солнце. */
    public static float rate(EntityPlayer p) {
        if (PerkEffects.wears(p, FROZEN) || com.divinerpgextended.accessory.Accessories.wears(p, com.divinerpgextended.trinket.Trinket.SUN_HALO)) return 0F;
        return PerkEffects.wearsFireArmor(p) || HomeArmor.wears(p, TwilightWorld.EDEN, false) || ArmorData.equippedTier(p) >= 2 || ArmorData.hasEquippedPerk(p, Perk.COLD_AURA) ? 0.5F : 1F;
    }

    /** Секунда игрока. */
    public static void step(EntityPlayer p) {
        float before = heat(p), h = before;
        boolean eden = p.dimension == ConfigurationHelper.eden && Config.edenSun && !p.capabilities.isCreativeMode;
        if (!eden) h -= AWAY_COOL;
        else if (p.isInWater()) h -= WATER_COOL;
        else if (WorldWeather.open(p) && rate(p) > 0) h += 100F / FULL_SECONDS * rate(p);
        else h -= SHADE_COOL;
        h = Math.max(0, Math.min(100, h));
        setHeat(p, h);
        if (!eden) return;
        apply(p, h);
        if (before < 50 && h >= 50) p.addChatMessage(new ChatComponentTranslation("dre.chat.sun_hot"));
        if (before < 100 && h >= 100) p.addChatMessage(new ChatComponentTranslation("dre.chat.sun_burn"));
    }

    private static void apply(EntityPlayer p, float h) {
        if (h >= 50) p.addPotionEffect(new PotionEffect(Potion.weakness.id, 40, 0, true));
        if (h >= 75) p.addPotionEffect(new PotionEffect(Potion.confusion.id, 100, 0, true));
        if (h >= 100) p.setFire(2);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote || p.ticksExisted % 20 != 0) return;
        float was = heat(p);
        step(p);
        boolean weather = p.dimension == ConfigurationHelper.wildwood || p.dimension == ConfigurationHelper.apalachia;
        if (p instanceof EntityPlayerMP && !(p instanceof net.minecraftforge.common.util.FakePlayer) && (weather || p.dimension == ConfigurationHelper.eden || was > 0))
            ColdHandler.NET.sendTo(new WorldStateMessage(heat(p), weather ? WorldWeather.get(p.worldObj).phase : WorldWeather.CALM), (EntityPlayerMP) p);
    }
}
