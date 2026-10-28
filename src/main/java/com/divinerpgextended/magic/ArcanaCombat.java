package com.divinerpgextended.magic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.divinerpg.utils.events.ArcanaHelper;
import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.asm.ArcanaHooks;
import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Арканный урон и бой.
 * <ul>
 * <li>Снаряды арканного оружия игрока помечаются при появлении: урон из {@link #BASE} с ростом по этапу игрока
 * ({@link ArcanaRules#stage}). Такой урон идёт сквозь броню, снаряды одного залпа не перекрывают друг друга (у
 * Divine попадало одно), маги сумеречных миров держат его вдвое лучше.</li>
 * <li>Звёзды и метеоры падают с 12 блоков, а не с 25 (у Divine долетали слишком долго).</li>
 * <li>По игроку арканным уроном бьют выстрелы магов, снаряды Ветеи и ближние удары мобов Арканы: сначала его
 * принимает на себя аркана ({@link ArcanaRules#absorb}).</li>
 * <li>Маги сумеречных миров стреляют раз в 1-1.5 с и только видя цель (у Divine - дважды в секунду сквозь стены).</li>
 * <li>Мечи Арканы добавляют к удару арканный урон, молнии Storm Sword не бьют своего хозяина.</li>
 * <li>Шкала арканы переживает перезаход (Divine обнулял её при входе), эффект зелья восстанавливает без задержки.</li>
 * </ul>
 */
public class ArcanaCombat {

    public static final String DMG = "dreArcDmg", MOB = "dreArcMob", NEXT_SHOT = "dreNextShot", SAVED = "dreArcSaved";
    /** Базовый арканный урон снаряда по оружию в руке (на входе в Аркану). */
    static final Map<Item, Float> BASE = new HashMap<Item, Float>();
    /** Прибавка мечей Арканы к удару (арканный урон). */
    public static final float SWORD_BONUS = 8.0F, MAGE_RESIST = 0.5F;
    /** На сколько ниже Divine ставит падающие звёзды и метеоры (Divine - 25 блоков над целью). */
    static final double DROP_LOWER = 13.0;

    private static final Set<String> MAGES = new HashSet<String>();
    private static final Set<String> VETHEA_ARCANE = new HashSet<String>();

    static {
        for (String m : new String[] { "EntityMage", "EntityMystic", "EntitySorcerer", "EntitySpellbinder" }) MAGES.add("net.divinerpg.entities.twilight." + m);
        for (String v : new String[] { "net.divinerpg.entities.vethea.projectile.EntityBouncingProjectile",
            "net.divinerpg.entities.vethea.projectile.EntityWreckBouncingProjectile", "net.divinerpg.entities.vethea.EntityLadyLunaSparkler",
            "net.divinerpg.entities.twilight.projectile.EntityTwilightMageShot" }) VETHEA_ARCANE.add(v);
    }

    public static void init() {
        BASE.put(ArcanaItems.staffStarlight, 7F);
        BASE.put(ArcanaItems.starlight, 7F);
        BASE.put(ArcanaItems.meteorMash, 30F);
        BASE.put(ArcanaItems.captainsSparkler, 14F);
        BASE.put(ArcanaItems.firefly, 9F);
        BASE.put(ArcanaItems.meriksMissile, 35F);
        BASE.put(ArcanaItems.arcaniteBlaster, 3F);
        BASE.put(ArcanaItems.generalsStaff, 16F);
    }

    public static boolean isMage(Entity e) {
        return e != null && MAGES.contains(e.getClass().getName());
    }

    static boolean arcanaSword(Item i) {
        return i == ArcanaItems.arcaniumSaber || i == ArcanaItems.arcaniteBlade || i == ArcanaItems.shadowSword || i == ArcanaItems.stormSword;
    }

    /** Базовый урон оружия в руке или null, если это не арканное оружие. */
    public static Float base(ItemStack held) {
        if (held == null) return null;
        if (held.getItem() instanceof ItemMageStaff) return ItemMageStaff.damage(held);
        return BASE.get(held.getItem());
    }

    /** Арканный урон снаряда этого игрока с этим оружием (с ростом по этапу). */
    public static float damage(EntityPlayer p, ItemStack held) {
        Float b = base(held);
        return b == null ? 0 : b * ArcanaRules.stage(p);
    }

    // ---------------- снаряды ----------------

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof EntityThrowable) || !e.entity.getClass().getName().startsWith("net.divinerpg.")) return;
        EntityThrowable t = (EntityThrowable) e.entity;
        NBTTagCompound d = t.getEntityData();
        if (d.hasKey(DMG)) return;
        EntityLivingBase thrower = t.getThrower();
        String cls = t.getClass().getSimpleName();
        // звёзды и метеоры Divine создаёт без бросившего, высоко над целью: хозяин - ближний игрок с посохом
        if (thrower == null && ("EntityStar".equals(cls) || "EntityMeteor".equals(cls))) {
            EntityPlayer owner = null;
            double best = 40 * 40;
            for (Object o : e.world.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                ItemStack h = p.getHeldItem();
                if (h == null || (h.getItem() != ArcanaItems.staffStarlight && h.getItem() != ArcanaItems.starlight && h.getItem() != ArcanaItems.meteorMash)) continue;
                double dx = p.posX - t.posX, dz = p.posZ - t.posZ, dd = dx * dx + dz * dz;
                if (dd < best) {
                    best = dd;
                    owner = p;
                }
            }
            if (owner != null) {
                d.setFloat(DMG, damage(owner, owner.getHeldItem()));
                t.setPosition(t.posX, t.posY - DROP_LOWER, t.posZ);
            }
            return;
        }
        if (thrower instanceof EntityPlayer) {
            float dmg = damage((EntityPlayer) thrower, thrower.getHeldItem());
            if (dmg > 0) d.setFloat(DMG, dmg);
            else if (VETHEA_ARCANE.contains(t.getClass().getName())) d.setBoolean(MOB, true); // посохи Ветеи: свой урон, но арканный
            return;
        }
        if (VETHEA_ARCANE.contains(t.getClass().getName())) {
            if (isMage(thrower) && !mageMayShoot((EntityLiving) thrower)) {
                e.setCanceled(true);
                return;
            }
            d.setBoolean(MOB, true);
        }
    }

    /** Маг стреляет раз в 1-1.5 с и только видя цель. */
    static boolean mageMayShoot(EntityLiving m) {
        long now = m.worldObj.getTotalWorldTime();
        NBTTagCompound d = m.getEntityData();
        if (now < d.getLong(NEXT_SHOT)) return false;
        Entity target = m instanceof EntityCreature ? ((EntityCreature) m).getEntityToAttack() : m.getAttackTarget();
        if (target != null && !m.canEntityBeSeen(target)) return false;
        d.setLong(NEXT_SHOT, now + 20 + m.getRNG().nextInt(11));
        return true;
    }

    private static NBTTagCompound tagOf(DamageSource s) {
        Entity src = s.getSourceOfDamage();
        return src == null ? null : src.getEntityData();
    }

    /** Залп арканного оружия: каждый снаряд попадает (у Divine после первого цель была неуязвима полсекунды). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        NBTTagCompound t = tagOf(e.source);
        if (t != null && t.hasKey(DMG) && !(e.entityLiving instanceof EntityPlayer)) e.entityLiving.hurtResistantTime = 0;
    }

    private static java.lang.reflect.Field unblockable;

    /** Урон сквозь броню (поле isUnblockable источника; источник у снаряда каждый раз новый). */
    static void pierce(DamageSource s) {
        try {
            if (unblockable == null) unblockable = cpw.mods.fml.relauncher.ReflectionHelper.findField(DamageSource.class, "isUnblockable", "field_76374_o");
            unblockable.setBoolean(s, true);
        } catch (Throwable ignored) {}
    }

    /** Нападение: арканный урон снарядов и мечей Арканы. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHurtDealt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        NBTTagCompound t = tagOf(e.source);
        boolean mage = isMage(e.entityLiving);
        if (t != null && t.hasKey(DMG)) {
            e.ammount = t.getFloat(DMG) * (mage ? MAGE_RESIST : 1F);
            if (!(e.entityLiving instanceof EntityPlayer)) pierce(e.source);
            com.divinerpgextended.debug.Debug.note(e.source.getEntity(), "Арканный урон: " + e.ammount + (mage ? " (маг держит x0.5)" : ""));
            return;
        }
        if (t != null && t.getBoolean(MOB) && mage) {
            e.ammount *= MAGE_RESIST;
            return;
        }
        Entity a = e.source.getEntity();
        if (a instanceof EntityPlayer && e.source.getSourceOfDamage() == a && "player".equals(e.source.damageType)) {
            ItemStack h = ((EntityPlayer) a).getHeldItem();
            if (h != null && arcanaSword(h.getItem())) e.ammount += SWORD_BONUS * ArcanaRules.stage((EntityPlayer) a) * (mage ? MAGE_RESIST : 1F);
        }
    }

    /** Арканный ли этот удар по игроку. */
    public static boolean incoming(DamageSource s) {
        if ("arcana".equals(s.damageType)) return true;
        NBTTagCompound t = tagOf(s);
        if (t != null && (t.getBoolean(MOB) || t.hasKey(DMG))) return true;
        Entity a = s.getEntity();
        return a != null && a == s.getSourceOfDamage() && "mob".equals(s.damageType) && a.getClass().getName().startsWith("net.divinerpg.entities.arcana.");
    }

    /** Защита: арканный урон сначала принимает на себя аркана игрока. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurtTaken(LivingHurtEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || !incoming(e.source)) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        float before = e.ammount;
        e.ammount = ArcanaRules.absorb(p, e.ammount);
        if (e.ammount < before) com.divinerpgextended.debug.Debug.note(p, String.format("Аркана приняла %.1f из %.1f арканного урона", before - e.ammount, before));
    }

    /** Молнии Storm Sword бьют вокруг хозяина, а не его самого. */
    @SubscribeEvent
    public void onLightning(EntityStruckByLightningEvent e) {
        if (!(e.entity instanceof EntityPlayer)) return;
        ItemStack h = ((EntityPlayer) e.entity).getHeldItem();
        if (h != null && h.getItem() == ArcanaItems.stormSword) e.setCanceled(true);
    }

    /** С магов сумеречных миров - их посох (4%, +1% за уровень добычи). */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        if (!isMage(e.entityLiving) || !e.recentlyHit || e.entityLiving.worldObj.isRemote) return;
        int meta = ItemMageStaff.metaOf(e.entityLiving);
        if (meta < 0 || e.entityLiving.worldObj.rand.nextFloat() >= 0.04F + 0.01F * e.lootingLevel) return;
        e.drops.add(new net.minecraft.entity.item.EntityItem(e.entityLiving.worldObj, e.entityLiving.posX, e.entityLiving.posY, e.entityLiving.posZ,
            new ItemStack(com.divinerpgextended.registry.ModItems.mageStaff, 1, meta)));
    }

    // ---------------- восстановление и сохранение шкалы ----------------

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote) return;
        ArcanaHelper h = ArcanaHelper.getProperties(p);
        if (h == null) return;
        NBTTagCompound d = p.getEntityData();
        if (d.hasKey(SAVED) && p.ticksExisted > 2) { // после входа: Divine обнулил шкалу - вернуть, что было
            h.setBarValue(Math.min(d.getFloat(SAVED), ArcanaHooks.max(p)));
            d.removeTag(SAVED);
        }
        float max = ArcanaHooks.max(p);
        int mult = ArcanaRules.buffMult(p);
        if (mult > 0) {
            h.regenDelay = 0;
            if (h.getBarValue() < max) h.forceRegen(mult - 1);
        } else if (h.regenDelay == 0 && h.getBarValue() < max && com.divinerpgextended.accessory.Accessories.wears(p, com.divinerpgextended.trinket.Trinket.CLARITY_CRYSTAL)) {
            h.forceRegen(0.5F);
        }
        if (p.ticksExisted % 20 == 0 && (mult > 0 || d.getBoolean("dreArcBuffShown"))) {
            syncBuff(p);
            d.setBoolean("dreArcBuffShown", mult > 0);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ArcanaHelper h = ArcanaHelper.getProperties(e.player);
        if (h == null) return;
        NBTTagCompound ps = e.player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        ps.setFloat(SAVED, h.getBarValue());
        e.player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, ps);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        NBTTagCompound ps = e.player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (ps.hasKey(SAVED)) e.player.getEntityData().setFloat(SAVED, ps.getFloat(SAVED));
    }

    public static void syncBuff(EntityPlayer p) {
        if (p instanceof EntityPlayerMP) ColdHandler.NET.sendTo(new com.divinerpgextended.arcana.MerchantMessages.ArcanaBuff(ArcanaRules.buffLeft(p),
            ArcanaRules.buffMult(p)), (EntityPlayerMP) p);
    }
}
