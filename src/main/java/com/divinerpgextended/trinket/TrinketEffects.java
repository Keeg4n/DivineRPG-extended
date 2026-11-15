package com.divinerpgextended.trinket;

import java.util.List;
import java.util.UUID;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.events.ArcanaHelper;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;

import com.divinerpgextended.arcana.WeaponOrigin;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Accessory effects. Work only when worn in accessory slots (not in the backpack). */
public class TrinketEffects {

    /** Sun Halo: how much it heals and how often (ticks). */
    public static final float SUN_HALO_HEAL = 1.0F;
    public static final int SUN_HALO_TICKS = 40;

    private static final String DEAD_CD = "dreDeadAmuletCd", ANKH_CD = "dreAnkhCd";
    private static final UUID PAULDRON = UUID.fromString("9c1e5a2d-3f47-4b8e-a6d1-5e2c7f9b0a13");

    public static boolean wears(EntityPlayer p, Trinket t) {
        return com.divinerpgextended.accessory.Accessories.wears(p, t);
    }

    // ---------------- Before damage: evasion and immunities ----------------

    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        if (e.source.isFireDamage() && wears(p, Trinket.BLUE_FLAME)) {
            e.setCanceled(true);
            com.divinerpgextended.debug.Debug.note(p, "Blue Flame: fire does not hurt");
            return;
        }
        // Ancient Charm: 8% chance to dodge any hit (except falling into the void)
        if (e.source != DamageSource.outOfWorld && wears(p, Trinket.ANCIENT_CHARM) && p.worldObj.rand.nextFloat() < 0.08F) {
            e.setCanceled(true);
            p.worldObj.playSoundAtEntity(p, "random.bow", 0.4F, 1.8F);
            com.divinerpgextended.debug.Debug.note(p, "Ancient Charm: dodge");
        }
    }

    // ---------------- Damage ----------------

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        Entity src = e.source.getEntity();
        boolean melee = src != null && e.source.getSourceOfDamage() == src && !e.source.isProjectile();
        // accessory bonuses apply only against mobs: players take only normal damage (PvP-disabled protected regions cannot be bypassed)
        if (src instanceof EntityPlayer && !src.worldObj.isRemote && !(e.entityLiving instanceof EntityPlayer) && !com.divinerpgextended.perk.Protection.spare(src, e.entityLiving)) {
            EntityPlayer p = (EntityPlayer) src;
            if (e.source.isProjectile() && wears(p, Trinket.BONE_RING)) {
                e.ammount *= 1.2F;
                com.divinerpgextended.debug.Debug.note(p, "Bone Archer Ring: arrows x1.2");
            }
            if (melee && wears(p, Trinket.WITHER_RING) && p.worldObj.rand.nextFloat() < 0.2F) {
                e.entityLiving.addPotionEffect(new PotionEffect(Potion.wither.id, 60, 1));
                com.divinerpgextended.debug.Debug.note(p, "Wither Ring: wither on target");
            }
            if (!p.worldObj.isDaytime() && wears(p, Trinket.JACK_AMULET)) {
                e.ammount *= 1.15F;
                com.divinerpgextended.debug.Debug.note(p, "Pumpkin Amulet: at night x1.15");
            }
            if (melee && wears(p, Trinket.PARASECTA_STING)) {
                e.entityLiving.addPotionEffect(new PotionEffect(Potion.poison.id, 60, 0));
                com.divinerpgextended.debug.Debug.note(p, "Parasecta Sting: poison on target");
            }
            if (melee && wears(p, Trinket.ETERNAL_ICE_RING) && p.worldObj.rand.nextFloat() < 0.1F) {
                e.entityLiving.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 20, 4));
                e.entityLiving.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, 20, 2));
                com.divinerpgextended.debug.Debug.note(p, "Ring of Eternal Ice: freeze on target");
            }
            if (melee && WeaponOrigin.of(p.getCurrentEquippedItem()) != null && wears(p, Trinket.MASTER_SCABBARD)) {
                e.ammount *= 1.1F;
                com.divinerpgextended.debug.Debug.note(p, "Master Scabbard: Divine sword x1.1");
            }
            if (e.entityLiving instanceof IBossDisplayData && wears(p, Trinket.VALOR_MEDAL)) {
                e.ammount *= 1.1F;
                com.divinerpgextended.debug.Debug.note(p, "Medal of Valor: against boss x1.1");
            }
        }
        if (e.entityLiving instanceof EntityPlayer && !e.entityLiving.worldObj.isRemote) {
            EntityPlayer v = (EntityPlayer) e.entityLiving;
            if (e.source.isExplosion() && wears(v, Trinket.BLAST_BELT)) {
                e.ammount *= 0.25F;
                com.divinerpgextended.debug.Debug.note(v, "Creeper Tamer Belt: explosion x0.25");
            }
            if (!e.source.isUnblockable() && wears(v, Trinket.COLOSSUS_PAULDRON)) {
                e.ammount *= 0.88F; // like +3 armor
                com.divinerpgextended.debug.Debug.note(v, "Colossus Pauldron: x0.88");
            }
            if (src != null && src.getClass().getName().startsWith("net.divinerpg.entities.arcana") && wears(v, Trinket.DUNGEON_LAMP)) {
                e.ammount *= 0.85F;
                com.divinerpgextended.debug.Debug.note(v, "Dungeon Lamp: Arcana mob x0.85");
            }
            // Carcinus Shell: 15% - a melee hit is reflected back (thorns are not reflected, to avoid ping-pong)
            if (melee && src instanceof EntityLivingBase && !(src instanceof EntityPlayer) && !"thorns".equals(e.source.damageType)
                && wears(v, Trinket.CARCINUS_SHELL)
                && v.worldObj.rand.nextFloat() < 0.15F) {
                src.attackEntityFrom(DamageSource.causeThornsDamage(v), e.ammount);
                e.ammount = 0;
                com.divinerpgextended.debug.Debug.note(v, "Carcinus Shell: hit reflected");
            }
        }
    }

    /** Whether Pharaoh's Ankh will save from death right now (worn and not on cooldown). */
    public static boolean ankhReady(EntityPlayer p) {
        return wears(p, Trinket.PHARAOH_ANKH) && p.worldObj.getTotalWorldTime() >= p.getEntityData().getLong(ANKH_CD);
    }

    /** Pharaoh's Ankh: once per 10 minutes, instead of death - half health. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDeath(LivingDeathEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || e.source == DamageSource.outOfWorld) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        long now = p.worldObj.getTotalWorldTime();
        if (!wears(p, Trinket.PHARAOH_ANKH) || now < p.getEntityData().getLong(ANKH_CD)) return;
        e.setCanceled(true);
        p.setHealth(p.getMaxHealth() / 2);
        com.divinerpgextended.debug.Debug.note(p, "Pharaoh's Ankh: saved from death");
        p.getEntityData().setLong(ANKH_CD, now + 12000);
        p.worldObj.playSoundAtEntity(p, "mob.zombie.unfect", 1.0F, 1.2F);
        ChatComponentTranslation c = new ChatComponentTranslation("dre.chat.ankh");
        c.getChatStyle().setColor(EnumChatFormatting.GOLD);
        p.addChatMessage(c);
    }

    /** Bone ring: 25% chance that the arrow returns. */
    @SubscribeEvent
    public void onLoose(ArrowLooseEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (p.worldObj.isRemote || p.capabilities.isCreativeMode || !wears(p, Trinket.BONE_RING)) return;
        if (p.worldObj.rand.nextFloat() < 0.25F) p.inventory.addItemStackToInventory(new ItemStack(Items.arrow));
    }

    /** Jack's amulet: at night enemies do not notice the wearer beyond 8 blocks. */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (!(e.target instanceof EntityPlayer) || !(e.entityLiving instanceof EntityLiving) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.target;
        if (p.worldObj.isDaytime() || !wears(p, Trinket.JACK_AMULET) || e.entityLiving.getDistanceSqToEntity(p) <= 64) return;
        ((EntityLiving) e.entityLiving).setAttackTarget(null);
    }

    // ---------------- Constant effects ----------------

    private static void keep(EntityPlayer p, Potion potion, int amp) {
        PotionEffect cur = p.getActivePotionEffect(potion);
        if (cur == null || cur.getDuration() < 220) p.addPotionEffect(new PotionEffect(potion.id, 300, amp, true));
    }

    private static void clear(EntityPlayer p, Potion potion) {
        if (p.isPotionActive(potion)) p.removePotionEffect(potion.id);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote) return;
        if (p.ticksExisted % 2 == 0 && wears(p, Trinket.POCKET_MAGNET)) magnet(p);
        if (p.ticksExisted % 10 != 0) return;
        if (wears(p, Trinket.WITHER_RING)) clear(p, Potion.wither);
        if (wears(p, Trinket.DEAD_AMULET)) {
            clear(p, Potion.hunger);
            long now = p.worldObj.getTotalWorldTime();
            if (p.getHealth() < p.getMaxHealth() * 0.3F && now >= p.getEntityData().getLong(DEAD_CD)) {
                p.addPotionEffect(new PotionEffect(Potion.regeneration.id, 100, 1));
                com.divinerpgextended.debug.Debug.note(p, "Dead Man's Amulet: Regeneration II");
                p.getEntityData().setLong(DEAD_CD, now + 600);
            }
        }
        if (!p.worldObj.isDaytime() && wears(p, Trinket.JACK_AMULET)) keep(p, Potion.nightVision, 0);
        if (wears(p, Trinket.ICE_BROOCH)) clear(p, Potion.moveSlowdown);
        if (wears(p, Trinket.WATCHER_EYE)) {
            clear(p, Potion.blindness);
            clear(p, Potion.confusion);
        }
        if (wears(p, Trinket.BLUE_FLAME)) {
            keep(p, Potion.fireResistance, 0);
            p.extinguish();
        }
        if (wears(p, Trinket.PARASECTA_STING)) clear(p, Potion.poison);
        // Sun Halo: by day (the sun never sets in Eden) under open sky - half a heart every 2 s
        if (p.ticksExisted % SUN_HALO_TICKS == 0 && p.getHealth() < p.getMaxHealth() && wears(p, Trinket.SUN_HALO)
            && (p.dimension == ConfigurationHelper.eden || p.worldObj.isDaytime()) && com.divinerpgextended.world.twilight.WorldWeather.open(p)) p.heal(SUN_HALO_HEAL);
        if (p.ticksExisted % 600 == 0 && wears(p, Trinket.LICH_CROWN) && !p.isPotionActive(Potion.field_76444_x)) {
            p.addPotionEffect(new PotionEffect(Potion.field_76444_x.id, 600, 1, true)); // absorption: a shield of 4 hearts
            com.divinerpgextended.debug.Debug.note(p, "Lich Crown: shield for 4 hearts");
        }
        if (wears(p, Trinket.DEEP_SHELL) && p.isInsideOfMaterial(Material.water)) {
            p.setAir(Math.min(300, p.getAir() + 5)); // air runs out twice as slowly
            keep(p, Potion.nightVision, 0);
        }
        if (wears(p, Trinket.DUNGEON_LAMP) && p.dimension == ConfigurationHelper.arcana) keep(p, Potion.nightVision, 0);
        if (wears(p, Trinket.DRAMIX_SEAL)) {
            ArcanaHelper h = ArcanaHelper.getProperties(p);
            if (h != null && h.regenDelay == 0) h.forceRegen(10.0F); // as much again as Divine restores in 10 ticks
        }
        pauldron(p, wears(p, Trinket.COLOSSUS_PAULDRON));
        // the old Bottomless Bait Bag (worked from the backpack) turns into an accessory - Tackle Box
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s != null && s.getItem() == ModItems.baitBag) {
                p.inventory.mainInventory[i] = new ItemStack(ModItems.trinket, 1, Trinket.TACKLE_BOX.ordinal());
            }
        }
    }

    /** Colossus Pauldron: almost no knockback (the modifier is held while worn). */
    private static void pauldron(EntityPlayer p, boolean on) {
        IAttributeInstance kb = p.getEntityAttribute(SharedMonsterAttributes.knockbackResistance);
        AttributeModifier cur = kb.getModifier(PAULDRON);
        if (on && cur == null) kb.applyModifier(new AttributeModifier(PAULDRON, "Colossus Pauldron", 0.8, 0).setSaved(false));
        else if (!on && cur != null) kb.removeModifier(cur);
    }

    /** Pocket Magnet: items within 6 blocks fly to the player. */
    private static void magnet(EntityPlayer p) {
        @SuppressWarnings("unchecked")
        List<EntityItem> items = p.worldObj.getEntitiesWithinAABB(EntityItem.class, p.boundingBox.expand(6, 3, 6));
        for (EntityItem it : items) {
            if (it.isDead || it.delayBeforeCanPickup > 0) continue;
            double dx = p.posX - it.posX, dy = p.posY + 0.5 - it.posY, dz = p.posZ - it.posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 0.5) continue;
            it.motionX = dx / d * 0.35;
            it.motionY = dy / d * 0.35;
            it.motionZ = dz / d * 0.35;
        }
    }
}
