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
 * Arcane potion effects on a player - real Minecraft effects ({@link PotionArcane}): visible in the inventory, removed
 * by milk, saved and sent to the client like vanilla ones. Level 1 - a regular potion, 2 - strong.
 */
public class ArcaneEffects {

    /** Where effects were stored before 0.99.30: on login they are moved into Minecraft effects. */
    static final String TAG = "dreArcEffects";

    /** Effect level (1 regular, 2 strong) or 0 if there is none. On the client - from the effects sent by the server. */
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

    /** Apply an effect: as with vanilla potions, a stronger one replaces, one of the same level extends to the longer duration. */
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

    /** Drink a potion: the effect on yourself (for the shared one - on players within 8 blocks too). Removed potions (return, call to arms, stillness) are empty. */
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

    // ---------------- effect actions ----------------

    /** Shot price multiplier (Composure). */
    public static float costFactor(EntityPlayer p) {
        int l = level(p, ArcaneEffect.COMPOSURE);
        return l == 0 ? 1F : l == 1 ? 0.75F : 0.6F;
    }

    static boolean melee(DamageSource s) {
        return s.getEntity() != null && s.getEntity() == s.getSourceOfDamage() && !s.isProjectile() && !s.isMagicDamage() && !s.isExplosion()
            && !ArcanaCombat.ARCANE.equals(s.damageType);
    }

    /** On the player: Charm Ward, Stone Skin, Missile ward, Fortitude, Bramble Hide. Before arcana defense. */
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

    /** From the player: sorcerous might, Battle Fervor, Steady Hand, Arcane Hunger. After the arcane damage rules. */
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

    /** Steady Hand: chance that the arrow returns. */
    @SubscribeEvent
    public void onLoose(ArrowLooseEvent e) {
        EntityPlayer p = e.entityPlayer;
        int l = level(p, ArcaneEffect.STEADY_HAND);
        if (l == 0 || p.worldObj.isRemote || p.capabilities.isCreativeMode) return;
        if (p.worldObj.rand.nextFloat() < (l == 1 ? 0.2F : 0.3F)) p.inventory.addItemStackToInventory(new ItemStack(Items.arrow));
    }

    /** Old effect records (before 0.99.30) - into Minecraft effects, once. */
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

    /** Iceika cold does not build up under Inner Heat (for {@link ColdHandler}). */
    public static boolean warm(EntityPlayer p) {
        return has(p, ArcaneEffect.INNER_HEAT);
    }

    /** Angler's luck: multiplier of the bite wait and of rare loot (for the addon's rods). */
    public static float biteFactor(EntityPlayer p) {
        int l = level(p, ArcaneEffect.FISHERS_FORTUNE);
        return l == 0 ? 1F : l == 1 ? 0.6F : 0.45F;
    }

    public static float rareFactor(EntityPlayer p) {
        int l = level(p, ArcaneEffect.FISHERS_FORTUNE);
        return l == 0 ? 1F : l == 1 ? 1.5F : 2F;
    }
}
