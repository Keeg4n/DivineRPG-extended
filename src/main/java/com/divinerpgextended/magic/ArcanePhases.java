package com.divinerpgextended.magic;

import net.divinerpg.entities.arcana.EntityDramix;
import net.divinerpg.entities.arcana.EntityParasecta;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.boss.MortumArenaBosses;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Boss phases where arcane damage decides.
 * <ul>
 * <li>Arcana bosses (Dramix, Parasecta) are hurt only by arcana and explosions ({@link #hurtsArcanaBoss}); a regular hit does not land,
 * the attacker gets a hint. Merik's missile near an Arcana boss targets it, invisibility does not hinder it.</li>
 * <li>Dramix: while invisible (half the cycle), arcane damage x{@link #DRAMIX_ARCANE}, explosions - x{@link #DRAMIX_OTHER}.</li>
 * <li>Parasecta: at 75%, 50% and 25% health it closes in a chitin cocoon for up to {@link #SHELL_TICKS} ticks - explosions
 * x{@link #SHELL_OTHER}; arcane damage cracks the cocoon ({@link #CRACK} per player nearby): the cocoon falls off, she is
 * stunned and for {@link #EXPOSED_TICKS} ticks takes x{@link #EXPOSED}.</li>
 * <li>Soul Fiend souls (while they are alive, he is invulnerable): die three times faster from arcana, twice as slowly from regular damage.</li>
 * </ul>
 */
public class ArcanePhases {

    public static final float DRAMIX_OTHER = 0.2F, DRAMIX_ARCANE = 1.5F, SHELL_OTHER = 0.1F, EXPOSED = 1.5F, CRACK = 60F, SOUL_ARCANE = 3F,
        SOUL_OTHER = 0.5F;
    public static final int SHELL_TICKS = 300, EXPOSED_TICKS = 100;
    static final String SHELL_UNTIL = "dreShellUntil", SHELL_LEFT = "dreShellLeft", SHELL_STAGE = "dreShellStage", EXPOSED_UNTIL = "dreExposedUntil";

    /** Whether this is an arcane hit (a projectile of an arcane weapon or a Vethea staff, an Arcana sword hit). */
    public static boolean arcane(DamageSource s) {
        if (ArcanaCombat.ARCANE.equals(s.damageType)) return true;
        Entity src = s.getSourceOfDamage();
        if (src == null) return false;
        NBTTagCompound t = src.getEntityData();
        return t.hasKey(ArcanaCombat.DMG) || t.getBoolean(ArcanaCombat.MOB) && s.getEntity() instanceof EntityPlayer;
    }

    /** Arcana boss: hurt only by arcana and explosions. */
    public static boolean arcanaBoss(Entity e) {
        return e instanceof EntityDramix || e instanceof EntityParasecta;
    }

    /** Whether such damage goes through to an Arcana boss: arcana, explosion, void and /kill, a player in creative. */
    public static boolean hurtsArcanaBoss(DamageSource s) {
        if (arcane(s) || s.isExplosion() || s == DamageSource.outOfWorld) return true;
        Entity a = s.getEntity();
        return a instanceof EntityPlayer && ((EntityPlayer) a).capabilities.isCreativeMode;
    }

    static final String HINT_AT = "dreArcBossHint";

    /** A regular hit on an Arcana boss does not land (no damage, no knockback); the attacker gets a hint once every 10 s. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (e.entityLiving.worldObj.isRemote || !arcanaBoss(e.entityLiving) || hurtsArcanaBoss(e.source)) return;
        e.setCanceled(true);
        Entity a = e.source.getEntity();
        if (!(a instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) a;
        // Arcana sword: the regular part of the hit does not land, while the arcane hit (on the next tick) does
        net.minecraft.item.ItemStack h = p.getHeldItem();
        if (e.source.getSourceOfDamage() == p && "player".equals(e.source.damageType) && h != null && ArcanaCombat.arcanaSword(h.getItem())) {
            // rhythm of regular hits: the attack event fires on every click, while the boss is invulnerable for half a second after a hit
            EntityLivingBase b = e.entityLiving;
            if (b.hurtResistantTime > b.maxHurtResistantTime / 2 || ArcanaCombat.edgeQueued(b)) return;
            if (ArcanaCombat.edgePaidOnArcanaBoss(p, h.getItem())) ArcanaCombat.queueEdge(b, p);
            return;
        }
        long now = p.worldObj.getTotalWorldTime();
        if (now - p.getEntityData().getLong(HINT_AT) < 200) return;
        p.getEntityData().setLong(HINT_AT, now);
        ChatComponentTranslation m = new ChatComponentTranslation("dre.chat.arcana_boss_immune");
        m.getChatStyle().setColor(EnumChatFormatting.LIGHT_PURPLE);
        p.addChatMessage(m);
    }

    private static int playersNear(EntityLivingBase b) {
        int n = 0;
        for (Object o : b.worldObj.playerEntities) if (((EntityPlayer) o).getDistanceSqToEntity(b) < 48 * 48) n++;
        return Math.max(1, n);
    }

    private static void say(EntityLivingBase b, String key, EnumChatFormatting c) {
        for (Object o : b.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.getDistanceSqToEntity(b) > 48 * 48) continue;
            ChatComponentTranslation m = new ChatComponentTranslation(key);
            m.getChatStyle().setColor(c);
            p.addChatMessage(m);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.entityLiving;
        if (t.worldObj.isRemote) return;
        boolean arc = arcane(e.source);
        if (arcanaBoss(t) && !hurtsArcanaBoss(e.source)) { // bypassing the attack event (thorns, other mods) - still zero
            e.ammount = 0;
            return;
        }
        if (t instanceof EntityDramix && t.isInvisible()) {
            e.ammount *= arc ? DRAMIX_ARCANE : DRAMIX_OTHER;
            return;
        }
        if (t instanceof EntityParasecta) {
            parasecta((EntityParasecta) t, e, arc);
            return;
        }
        if (t.getEntityData().hasKey(MortumArenaBosses.SOUL)) e.ammount *= arc ? SOUL_ARCANE : SOUL_OTHER;
    }

    void parasecta(EntityParasecta b, LivingHurtEvent e, boolean arc) {
        NBTTagCompound d = b.getEntityData();
        long now = b.worldObj.getTotalWorldTime();
        if (now < d.getLong(EXPOSED_UNTIL)) {
            e.ammount *= EXPOSED;
            return;
        }
        if (now < d.getLong(SHELL_UNTIL)) {
            if (!arc) {
                e.ammount *= SHELL_OTHER;
                return;
            }
            float left = d.getFloat(SHELL_LEFT) - e.ammount;
            if (left > 0) {
                d.setFloat(SHELL_LEFT, left);
                e.ammount *= SHELL_OTHER;
                return;
            }
            // the cocoon cracked
            d.setLong(SHELL_UNTIL, 0);
            d.setLong(EXPOSED_UNTIL, now + EXPOSED_TICKS);
            b.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 5));
            b.addPotionEffect(new PotionEffect(Potion.weakness.id, EXPOSED_TICKS, 2));
            b.worldObj.playSoundAtEntity(b, "random.anvil_break", 1.2F, 0.6F);
            say(b, "dre.chat.parasecta_cracked", EnumChatFormatting.LIGHT_PURPLE);
            return;
        }
        // health threshold: 75%, 50%, 25% - cocoon
        float after = (b.getHealth() - e.ammount) / b.getMaxHealth();
        int stage = d.getInteger(SHELL_STAGE), want = after <= 0.25F ? 3 : after <= 0.5F ? 2 : after <= 0.75F ? 1 : 0;
        if (want > stage && b.getHealth() - e.ammount > 0) {
            d.setInteger(SHELL_STAGE, want);
            d.setLong(SHELL_UNTIL, now + SHELL_TICKS);
            d.setFloat(SHELL_LEFT, CRACK * playersNear(b));
            b.worldObj.playSoundAtEntity(b, "mob.silverfish.step", 1.5F, 0.5F);
            say(b, "dre.chat.parasecta_shell", EnumChatFormatting.DARK_GREEN);
        }
    }

    /** Parasecta's cocoon is visible: shell particles around her. */
    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        EntityLivingBase b = e.entityLiving;
        if (!(b instanceof EntityParasecta) || b.worldObj.isRemote || b.ticksExisted % 5 != 0) return;
        if (b.worldObj.getTotalWorldTime() < b.getEntityData().getLong(SHELL_UNTIL) && b.worldObj instanceof WorldServer) {
            ((WorldServer) b.worldObj).func_147487_a("blockcrack_49_0", b.posX, b.posY + b.height * 0.5, b.posZ, 6, b.width * 0.5, b.height * 0.4,
                b.width * 0.5, 0.02);
        }
    }

    /** For the autotest: whether it is in the cocoon. */
    public static boolean shelled(EntityLivingBase b) {
        return b.worldObj.getTotalWorldTime() < b.getEntityData().getLong(SHELL_UNTIL);
    }
}
