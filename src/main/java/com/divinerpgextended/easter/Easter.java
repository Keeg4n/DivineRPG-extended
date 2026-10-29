package com.divinerpgextended.easter;

import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Пасхалки (как Экскалибур в Botania - переименованная вещь меняет только вид): предмет остаётся тем же предметом Divine
 * со всеми свойствами, вид и звуки - по имени, данному на наковальне.
 * <ul>
 * <li>Реалмитовая броня с именем "H.E.V" - костюм H.E.V. из Half-Life: своя модель, голос костюма в чате (вход в систему,
 * переломы, критическое здоровье), морфин при малом здоровье не чаще раза в {@link #MORPHINE_EVERY} тиков; со звуками
 * Half-Life, если они установлены ({@link com.divinerpgextended.client.HevSounds}).</li>
 * <li>Реалмитовый меч с именем "crowbar" - монтировка.</li>
 * <li>Ракетница Мерика с именем "rpg" - РПГ-7, ракета летит гранатой ПГ-7.</li>
 * </ul>
 * Переименовал обратно - вид обычный.
 */
public final class Easter {

    public static final int MORPHINE_EVERY = 6000, MORPHINE_AT = 6, CRITICAL_EVERY = 600;
    static final String WORN = "dreHevWorn", MORPHINE = "dreHevMorphine", CRITICAL = "dreHevCritical";

    private Easter() {}

    static boolean named(ItemStack s, String... names) {
        if (s == null || !s.hasDisplayName()) return false;
        String n = s.getDisplayName().trim();
        for (String k : names) if (n.equalsIgnoreCase(k)) return true;
        return false;
    }

    static boolean realmiteArmor(Item i) {
        return i == VanillaItemsArmor.realmiteHelmet || i == VanillaItemsArmor.realmiteBody || i == VanillaItemsArmor.realmiteLegs || i == VanillaItemsArmor.realmiteBoots;
    }

    /** Часть костюма H.E.V.: реалмитовая броня с именем H.E.V. */
    public static boolean hev(ItemStack s) {
        return s != null && realmiteArmor(s.getItem()) && named(s, "H.E.V", "H.E.V.", "HEV");
    }

    public static boolean fullHev(EntityPlayer p) {
        for (int i = 0; i < 4; i++) if (!hev(p.getCurrentArmor(i))) return false;
        return true;
    }

    /** Монтировка: реалмитовый меч с именем crowbar. */
    public static boolean crowbar(ItemStack s) {
        return s != null && s.getItem() == VanillaItemsWeapons.realmiteSword && named(s, "crowbar");
    }

    /** РПГ-7: ракетница Мерика с именем rpg. */
    public static boolean rpg(ItemStack s) {
        return s != null && s.getItem() == ArcanaItems.meriksMissile && named(s, "rpg", "rpg-7", "rpg7");
    }

    static void say(EntityPlayer p, String key) {
        ChatComponentTranslation m = new ChatComponentTranslation(key);
        m.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD));
        p.addChatMessage(m);
    }

    /** Голос костюма и морфин (сервер). */
    public static class Events {

        @SubscribeEvent
        public void onTick(TickEvent.PlayerTickEvent e) {
            EntityPlayer p = e.player;
            if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote || p.ticksExisted % 10 != 0) return;
            tick(p);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onHurt(LivingHurtEvent e) {
            if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || e.ammount <= 0) return;
            hurt((EntityPlayer) e.entityLiving, e.source, e.ammount);
        }
    }

    /** Раз в полсекунды: надел полный костюм - вход в систему; малое здоровье - морфин. */
    public static void tick(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        boolean full = fullHev(p);
        if (full && !d.getBoolean(WORN)) say(p, "dre.hev.logon");
        d.setBoolean(WORN, full);
        if (!full || !p.isEntityAlive() || p.getHealth() > MORPHINE_AT) return;
        long now = p.worldObj.getTotalWorldTime(), last = d.getLong(MORPHINE);
        if (d.hasKey(MORPHINE) && now - last < MORPHINE_EVERY && now >= last) return;
        d.setLong(MORPHINE, now);
        p.addPotionEffect(new PotionEffect(Potion.regeneration.id, 100, 1));
        say(p, "dre.hev.morphine");
    }

    /** Удар по игроку в костюме: переломы при падении, критическое здоровье. */
    public static void hurt(EntityPlayer p, DamageSource s, float amount) {
        if (!fullHev(p)) return;
        if (s == DamageSource.fall) say(p, amount >= 6 ? "dre.hev.major_fracture" : "dre.hev.minor_fracture");
        if (p.getHealth() - amount <= MORPHINE_AT && p.getHealth() - amount > 0) {
            long now = p.worldObj.getTotalWorldTime(), last = p.getEntityData().getLong(CRITICAL);
            if (!p.getEntityData().hasKey(CRITICAL) || now - last >= CRITICAL_EVERY || now < last) {
                p.getEntityData().setLong(CRITICAL, now);
                say(p, "dre.hev.health_critical");
            }
        }
    }
}
