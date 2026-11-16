package com.divinerpgextended.perk;

import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;

import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.entity.FrostPowers;
import com.divinerpgextended.entity.ThroneCombat;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Iceika gear in combat.
 * <ul>
 * <li>Santa in Iceika: on top of her own -80% cuts creature damage by another 40% (about -88% in total) - she takes long to farm,
 * so she has to take a hit.</li>
 * <li>The dead raised by Throne bosses: Snowslash hits them three times as hard, ice cannons four times (shatter them into shards).</li>
 * <li>Frossivence (a healing sword): heals no more often than once per 2 seconds (more often - the healing is cancelled), in a Frozen or
 * Santa set - for half health. Each healing chills the blood: hypothermia accumulates (slowness, then
 * weakness and damage), ice sets accumulate it more slowly, but not to zero. It fades on its own.</li>
 * <li>Ice blasts (Fractite shot, Frost Charge, ice boulder) do not destroy items and experience on the ground.</li>
 * </ul>
 */
public class IceikaCombat {

    private static final Item[] SANTA = { IceikaItems.santaCap, IceikaItems.santaTunic, IceikaItems.santaPants,
        IceikaItems.santaBoots };
    private static final Item[] FROZEN = { VanillaItemsArmor.frozenHelmet, VanillaItemsArmor.frozenBody,
        VanillaItemsArmor.frozenLegs, VanillaItemsArmor.frozenBoots };
    private static final String HEAL_TICK = "dreFrossivence", CHILL = "dreChill";
    private static final int COOLDOWN = 40;

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        Entity attacker = event.source.getEntity();
        if (event.entityLiving instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) event.entityLiving;
            if (attacker != null && attacker != p && ColdHandler.inIceika(p) && PerkEffects.wears(p, SANTA)) {
                event.ammount *= 0.6F;
                com.divinerpgextended.debug.Debug.note(p, "Santa in Iceika, addon bonus on top of Divine: x0.6");
            }
            return;
        }
        if (!event.entityLiving.getEntityData()
            .getBoolean(ThroneCombat.MINION_TAG)) return;
        Item melee = ThroneCombat.melee(event.source);
        if (melee == IceikaItems.snowSlash) {
            event.ammount *= 3.0F;
            com.divinerpgextended.debug.Debug.note(event.entityLiving, "Snowslash on a throne minion: x3");
        } else if (ThroneCombat.vsMinion(melee) > 1.0F) {
            event.ammount *= ThroneCombat.vsMinion(melee);
            com.divinerpgextended.debug.Debug.note(event.entityLiving, "Throne ice weapon on a minion: x2");
        }
        if (ThroneCombat.iceCannon(event.source)) {
            event.ammount *= 4.0F;
            com.divinerpgextended.debug.Debug.note(event.entityLiving, "Ice cannon on a throne minion: x4");
            FrostPowers.particles(event.entityLiving.worldObj, "snowshovel", event.entityLiving.posX,
                event.entityLiving.posY + 1, event.entityLiving.posZ, 20, 0.4, 0.1);
        }
    }

    /**
     * Frossivence: Divine heals on every click with no cooldown (in onItemRightClick, setHealth without a heal event). A click
     * into the air more often than once per 2 seconds does not reach the sword (useItem = DENY): before, the healing was cancelled at the start of
     * use, but while the sword is already "in hand" (block), repeated clicks do not trigger the start - in the Colossus fight healing went
     * without a cooldown. Each real healing adds hypothermia.
     */
    @SubscribeEvent
    public void onClick(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
        EntityPlayer p = event.entityPlayer;
        if (event.action != net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_AIR || p.worldObj.isRemote) return;
        ItemStack s = p.getHeldItem();
        if (s == null || s.getItem() != IceikaItems.frossivence) return;
        NBTTagCompound d = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (now - d.getLong(HEAL_TICK) < COOLDOWN) {
            event.useItem = cpw.mods.fml.common.eventhandler.Event.Result.DENY;
            com.divinerpgextended.debug.Debug.note(p, "Frossivence on cooldown: no healing");
            return;
        }
        if (p.getHealth() <= 0 || p.getHealth() >= 20) return; // Divine heals only below 20 - we do not spend the cooldown
        d.setLong(HEAL_TICK, now);
        boolean santa = PerkEffects.wears(p, SANTA), frozen = PerkEffects.wears(p, FROZEN);
        if ((santa || frozen) && p.getHealth() < p.getMaxHealth()) {
            p.heal(p.getMaxHealth() / 2);
            com.divinerpgextended.debug.Debug.note(p, "Frossivence with the ice set: heals half health");
        }
        float chill = d.getFloat(CHILL) + 3.0F * (santa ? 0.4F : frozen ? 0.6F : 1.0F);
        if (d.getFloat(CHILL) < 7 && chill >= 7) p.addChatMessage(new ChatComponentTranslation("dre.chat.frossivence_chill"));
        d.setFloat(CHILL, Math.min(12, chill));
        FrostPowers.particles(p.worldObj, "snowshovel", p.posX, p.posY + 1.2, p.posZ, 8, 0.4, 0.05);
    }

    /**
     * Ice blasts spare the ground: the Fractite's shot (Divine), the Frost Charge of Snow Glacon and the Lich King and the Frost
     * Colossus' ice boulder do not destroy items and experience orbs. They never broke blocks, but a vanilla explosion kills every
     * item within twice its strength, so a Fractite firing into a fight blew up the drops and a dead player's things. Each of
     * them explodes before setDead, so the projectile is still alive at the explosion point.
     */
    @SubscribeEvent
    public void onDetonate(net.minecraftforge.event.world.ExplosionEvent.Detonate e) {
        if (e.world.isRemote || !iceBlast(e.world, e.explosion)) return;
        for (java.util.Iterator<Entity> it = e.getAffectedEntities().iterator(); it.hasNext();) {
            Entity en = it.next();
            if (en instanceof net.minecraft.entity.item.EntityItem || en instanceof net.minecraft.entity.item.EntityXPOrb) it.remove();
        }
    }

    /** The explosion comes from an ice projectile: a live one sits at its center. */
    public static boolean iceBlast(net.minecraft.world.World w, net.minecraft.world.Explosion ex) {
        net.minecraft.util.AxisAlignedBB box = net.minecraft.util.AxisAlignedBB.getBoundingBox(ex.explosionX - 1, ex.explosionY - 1, ex.explosionZ - 1,
            ex.explosionX + 1, ex.explosionY + 1, ex.explosionZ + 1);
        for (Object o : w.getEntitiesWithinAABB(Entity.class, box)) {
            Entity en = (Entity) o;
            if (!en.isDead && (en instanceof net.divinerpg.entities.iceika.projectile.EntityFractiteShot
                || en instanceof com.divinerpgextended.entity.EntityFrostCharge || en instanceof com.divinerpgextended.entity.EntityIcicle)) return true;
        }
        return false;
    }

    /** Once a second we process hypothermia. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer p = event.player;
        if (event.phase != TickEvent.Phase.END || p.worldObj.isRemote) return;
        NBTTagCompound d = p.getEntityData();
        if (p.ticksExisted % 20 != 0) return;
        float chill = d.getFloat(CHILL);
        if (chill <= 0) return;
        if (chill >= 10) {
            p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 3, true));
            p.addPotionEffect(new PotionEffect(Potion.weakness.id, 40, 0, true));
            if (p.ticksExisted % 40 == 0) p.attackEntityFrom(ColdHandler.FROSTBITE, 1.0F);
        } else if (chill >= 7) {
            p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 1, true));
            p.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, 40, 0, true));
        } else if (chill >= 4) {
            p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 0, true));
        }
        d.setFloat(CHILL, Math.max(0, chill - 0.5F));
    }
}
