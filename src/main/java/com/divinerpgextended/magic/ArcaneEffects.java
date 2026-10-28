package com.divinerpgextended.magic;

import java.util.List;

import net.divinerpg.utils.events.ArcanaHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;

import com.divinerpgextended.asm.ArcanaHooks;
import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Эффекты арканных зелий у игрока - настоящие эффекты Minecraft ({@link PotionArcane}): видны в инвентаре, снимаются
 * молоком, сохраняются и уходят клиенту как ванильные. Уровень 1 - обычное зелье, 2 - сильное.
 */
public class ArcaneEffects {

    /** Где эффекты хранились до 0.99.30: при входе переносятся в эффекты Minecraft. */
    static final String TAG = "dreArcEffects";

    /** Уровень эффекта (1 обычное, 2 сильное) или 0, если его нет. На клиенте - по эффектам, присланным сервером. */
    public static int level(EntityPlayer p, ArcaneEffect e) {
        Potion pt = p == null ? null : PotionArcane.of(e);
        PotionEffect pe = pt == null ? null : p.getActivePotionEffect(pt);
        return pe == null ? 0 : pe.getAmplifier() + 1;
    }

    public static boolean has(EntityPlayer p, ArcaneEffect e) {
        return level(p, e) > 0;
    }

    public static int left(EntityPlayer p, ArcaneEffect e) {
        Potion pt = PotionArcane.of(e);
        PotionEffect pe = pt == null ? null : p.getActivePotionEffect(pt);
        return pe == null ? 0 : pe.getDuration();
    }

    /** Наложить эффект: как у ванильных зелий, сильнее - заменяет, того же уровня - продлевает до большего срока. */
    public static void apply(EntityPlayer p, ArcaneEffect e, int level, int ticks) {
        Potion pt = PotionArcane.of(e);
        if (p.worldObj.isRemote || pt == null) return;
        p.addPotionEffect(new PotionEffect(pt.id, ticks, Math.max(0, level - 1)));
    }

    public static void clear(EntityPlayer p) {
        for (ArcaneEffect e : ArcaneEffect.values()) {
            Potion pt = PotionArcane.of(e);
            if (pt != null && p.isPotionActive(pt)) p.removePotionEffect(pt.id);
        }
    }

    /** Выпить зелье: эффект себе (у общего - и игрокам в 8 блоках). Убранные зелья (возврат, зов битвы, затишье) - пустые. */
    public static void drink(EntityPlayer p, ArcaneEffect e, boolean strong, boolean longer, boolean shared) {
        if (p.worldObj.isRemote || e.removed()) return;
        List<EntityPlayer> who = new java.util.ArrayList<EntityPlayer>();
        who.add(p);
        if (shared) for (Object o : p.worldObj.playerEntities) if (o != p && ((EntityPlayer) o).getDistanceSqToEntity(p) <= 64) who.add((EntityPlayer) o);
        int lvl = strong && e.leveled() ? 2 : 1;
        int ticks = e.seconds * 20 * (longer ? 2 : 1);
        for (EntityPlayer t : who) {
            if (e == ArcaneEffect.CLARITY) ArcanaRules.buff(t, lvl + 1, ticks);
            else apply(t, e, lvl, ticks);
        }
    }

    // ---------------- действие эффектов ----------------

    /** Множитель цены выстрела (Собранность). */
    public static float costFactor(EntityPlayer p) {
        int l = level(p, ArcaneEffect.COMPOSURE);
        return l == 0 ? 1F : l == 1 ? 0.75F : 0.6F;
    }

    static boolean melee(DamageSource s) {
        return s.getEntity() != null && s.getEntity() == s.getSourceOfDamage() && !s.isProjectile() && !s.isMagicDamage() && !s.isExplosion()
            && !ArcanaCombat.ARCANE.equals(s.damageType);
    }

    /** По игроку: оберег от чар, каменная кожа, отвод снарядов, стойкость, колючки. До защиты арканой. */
    @SubscribeEvent
    public void onHurtTaken(LivingHurtEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        float k = 1F;
        int l;
        if (ArcanaCombat.incoming(e.source) && (l = level(p, ArcaneEffect.CHARM_WARD)) > 0) k *= l == 1 ? 0.7F : 0.55F;
        boolean melee = melee(e.source);
        if (melee && (l = level(p, ArcaneEffect.STONE_SKIN)) > 0) k *= l == 1 ? 0.85F : 0.75F;
        if (e.source.isProjectile() && (l = level(p, ArcaneEffect.DEFLECTION)) > 0) k *= l == 1 ? 0.65F : 0.5F;
        if ((l = level(p, ArcaneEffect.FORTITUDE)) > 0) k *= l == 1 ? 0.9F : 0.85F;
        if (melee && (l = level(p, ArcaneEffect.BRAMBLE_HIDE)) > 0 && e.source.getEntity() instanceof EntityLivingBase && !"thorns".equals(e.source.damageType)) {
            e.source.getEntity().attackEntityFrom(DamageSource.causeThornsDamage(p), e.ammount * (l == 1 ? 0.3F : 0.45F));
        }
        e.ammount *= k;
    }

    /** От игрока: чародейская мощь, боевой раж, твёрдая рука, голод арканы. После правил арканного урона. */
    @SubscribeEvent
    public void onHurtDealt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote || !(e.source.getEntity() instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) e.source.getEntity();
        Entity src = e.source.getSourceOfDamage();
        boolean arcane = src != null && src.getEntityData().hasKey(ArcanaCombat.DMG) || ArcanaCombat.ARCANE.equals(e.source.damageType);
        int l;
        if (arcane && (l = level(p, ArcaneEffect.SORCERY)) > 0) e.ammount *= l == 1 ? 1.2F : 1.35F;
        if (melee(e.source) && (l = level(p, ArcaneEffect.BATTLE_FERVOR)) > 0) e.ammount *= l == 1 ? 1.15F : 1.25F;
        if (e.source.isProjectile() && !arcane && (l = level(p, ArcaneEffect.STEADY_HAND)) > 0) e.ammount *= l == 1 ? 1.15F : 1.25F;
        if (arcane && (l = level(p, ArcaneEffect.ARCANE_HUNGER)) > 0) {
            ArcanaHelper h = ArcanaHelper.getProperties(p);
            if (h != null) h.setBarValue(Math.min(ArcanaHooks.max(p), h.getBarValue() + e.ammount * (l == 1 ? 0.05F : 0.08F)));
        }
    }

    /** Твёрдая рука: шанс, что стрела вернётся. */
    @SubscribeEvent
    public void onLoose(ArrowLooseEvent e) {
        EntityPlayer p = e.entityPlayer;
        int l = level(p, ArcaneEffect.STEADY_HAND);
        if (l == 0 || p.worldObj.isRemote || p.capabilities.isCreativeMode) return;
        if (p.worldObj.rand.nextFloat() < (l == 1 ? 0.2F : 0.3F)) p.inventory.addItemStackToInventory(new ItemStack(Items.arrow));
    }

    /** Старые записи эффектов (до 0.99.30) - в эффекты Minecraft, один раз. */
    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote || p.ticksExisted % 20 != 0) return;
        NBTTagCompound ps = p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!ps.hasKey(TAG)) return;
        NBTTagCompound d = ps.getCompoundTag(TAG);
        MinecraftServer s = MinecraftServer.getServer();
        long now = s != null && s.worldServers.length > 0 ? s.worldServers[0].getTotalWorldTime() : 0;
        for (ArcaneEffect ef : ArcaneEffect.values()) {
            long left = d.getLong(ef.key()) - now;
            if (left > 0 && !ef.removed()) apply(p, ef, Math.max(1, d.getInteger(ef.key() + "L")), (int) Math.min(left, Integer.MAX_VALUE));
        }
        ps.removeTag(TAG);
    }

    /** Холод Айсики не копится под Внутренним жаром (для {@link ColdHandler}). */
    public static boolean warm(EntityPlayer p) {
        return has(p, ArcaneEffect.INNER_HEAT);
    }

    /** Удача рыбака: множитель ожидания поклёвки и редкой добычи (для удочек аддона). */
    public static float biteFactor(EntityPlayer p) {
        int l = level(p, ArcaneEffect.FISHERS_FORTUNE);
        return l == 0 ? 1F : l == 1 ? 0.6F : 0.45F;
    }

    public static float rareFactor(EntityPlayer p) {
        int l = level(p, ArcaneEffect.FISHERS_FORTUNE);
        return l == 0 ? 1F : l == 1 ? 1.5F : 2F;
    }
}
