package com.divinerpgextended.perk;

import java.util.List;

import net.divinerpg.entities.vanilla.EntityCrab;
import net.divinerpg.entities.vanilla.EntityKingCrab;
import net.divinerpg.entities.vanilla.EntityLiopleurodon;
import net.divinerpg.entities.vanilla.EntityShark;
import net.divinerpg.entities.vanilla.EntityWhale;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.entity.EntityCarcinus;
import com.divinerpgextended.entity.EntityLairCrab;
import com.divinerpgextended.item.ArmorCore;
import com.divinerpgextended.item.ArmorData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Effects of perks and the core of the full Wanderer armor set, and bonuses of Divine's water sets (Kraken, Aquastrive). */
public class PerkEffects {

    /** Perks from the Shadow, corrupted, arlemite and rupee sets: the same properties, but weaker than the sets themselves (speed x3, projectiles x1.2, damage x0.3). */
    public static final float SWIFT_SPEED = 1.25F, MARKSMAN = 1.1F, GUARD = 0.75F;
    /** High step of the Shadow perk: one block up without jumping. The player's normal step is 0.5. */
    public static final float HIGH_STEP = 1.0F, NORMAL_STEP = 0.5F;
    private static final String STEP_TAG = "dreHighStep";
    /** Core: the same as the full set. */
    public static final float CORE_DIVINE_MELEE = 6F, CORE_DIVINE_UNDEAD = 10F, CORE_CORRUPTED_RANGED = 1.2F;

    /** Bedrock steadfastness: moving farther than this from where you stood resets the stance (small nudges do not count). */
    private static final double STANCE_MOVE = 0.25;
    private static final java.util.UUID STANCE_ID = java.util.UUID.fromString("c3a71f52-8d04-4e6b-9a17-2b6f4e90d1a8");
    /** Where the player stopped and for how many ticks they have stood: {x, y, z, ticks}. */
    private static final java.util.Map<EntityPlayer, double[]> STANCE = new java.util.WeakHashMap<EntityPlayer, double[]>();

    /**
     * Melee in divine armor: the set and core hit only what is itself hostile. Undead take more, enemy mobs, bosses and players
     * as before, peaceful and neutral ones get no bonus.
     */
    public static float divineMelee(EntityLivingBase target) {
        if (target.getCreatureAttribute() == net.minecraft.entity.EnumCreatureAttribute.UNDEAD) return CORE_DIVINE_UNDEAD;
        return target instanceof IMob || target instanceof net.minecraft.entity.boss.IBossDisplayData || target instanceof EntityPlayer
            || target instanceof com.divinerpgextended.dummy.EntityTrainingDummy ? CORE_DIVINE_MELEE : 0;
    }

    /** Core of the worn set; in the dream (Vethea nightmare) foreign sets are silent, and so is the core. */
    private static ArmorCore core(EntityPlayer p) {
        ArmorCore c = ArmorCore.worn(p);
        return c == null || com.divinerpgextended.vethea.Nightmare.active(p) ? null : c;
    }

    private static final Item[] KRAKEN = { VanillaItemsArmor.krakenHelmet, VanillaItemsArmor.krakenBody,
        VanillaItemsArmor.krakenLegs, VanillaItemsArmor.krakenBoots };
    private static final Item[] AQUASTRIVE = { VanillaItemsArmor.aquastriveHelmet, VanillaItemsArmor.aquastriveBody,
        VanillaItemsArmor.aquastriveLegs, VanillaItemsArmor.aquastriveBoots };

    /** Full Divine set: set = [helmet, chestplate, leggings, boots], player slots 0 boots ... 3 helmet. */
    public static boolean wears(EntityPlayer p, Item[] set) {
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s == null || s.getItem() != set[3 - i]) return false;
        }
        return true;
    }

    /** Aquatic creatures for the Kraken bonus. */
    public static boolean isAquatic(Entity e) {
        return e instanceof EntityShark || e instanceof EntityCrab || e instanceof EntityKingCrab
            || e instanceof EntityLiopleurodon || e instanceof EntityWhale || e instanceof EntityWaterMob
            || e instanceof EntityCarcinus || e instanceof EntityLairCrab;
    }

    private static boolean aquaMiner(EntityPlayer p) {
        return wears(p, AQUASTRIVE) || ArmorData.hasEquippedPerk(p, Perk.AQUA_MINER);
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        if (!player.worldObj.isRemote) {
            ArmorCore c = core(player);
            // like the sets themselves: explosions - only for those who defeated the Watcher (his fight is about explosions), fire - for bedrock
            if (c == ArmorCore.BEDROCK && event.source.isFireDamage()) {
                player.extinguish();
                event.setCanceled(true);
                return;
            }
            if ((c == ArmorCore.BEDROCK || c == ArmorCore.ENDER) && event.source.isExplosion() && com.divinerpgextended.boss.BossProgress.has(player, com.divinerpgextended.boss.Boss.WATCHER)) {
                event.setCanceled(true);
                return;
            }
        }
        if (event.source.isFireDamage() && ArmorData.hasEquippedPerk(player, Perk.FIRE_PROTECTION)) {
            player.extinguish();
            event.setCanceled(true);
            com.divinerpgextended.debug.Debug.note(player, "Fire Protection perk: fire does not hurt");
        } else if (event.source.isExplosion() && ArmorData.hasEquippedPerk(player, Perk.EXPLOSION_PROTECTION)) {
            event.setCanceled(true);
            com.divinerpgextended.debug.Debug.note(player, "Explosion Protection perk: explosion does not hurt");
        }
    }

    private static final Item[] INFERNO = { VanillaItemsArmor.infernoHelmet, VanillaItemsArmor.infernoBody,
        VanillaItemsArmor.infernoLegs, VanillaItemsArmor.infernoBoots };
    private static final Item[] NETHERITE = { VanillaItemsArmor.netheriteHelmet, VanillaItemsArmor.netheriteBody,
        VanillaItemsArmor.netheriteLegs, VanillaItemsArmor.netheriteBoots };

    /** Fire armor: a full Inferno or Netherite set, or Wanderer armor with fire protection. */
    public static boolean wearsFireArmor(EntityPlayer p) {
        return wears(p, INFERNO) || wears(p, NETHERITE) || ArmorData.hasEquippedPerk(p, Perk.FIRE_PROTECTION);
    }

    /** Water armor: a full Kraken or Aquastrive set, or Wanderer armor with the water perk. */
    public static boolean wearsWaterArmor(EntityPlayer p) {
        return wears(p, KRAKEN) || wears(p, AQUASTRIVE) || ArmorData.hasEquippedPerk(p, Perk.WATER_BREATHING)
            || ArmorData.hasEquippedPerk(p, Perk.AQUA_MINER);
    }

    /**
     * "Deep Hunter": a full Kraken set - +50% damage to aquatic creatures and -30% damage from them. Carcinus barely pierces
     * water armor with his claws: it was made precisely against dwellers of the deep. The Sand Whirl hits in full.
     */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        Entity attacker = event.source.getEntity();
        if (attacker instanceof EntityPlayer && !attacker.worldObj.isRemote && attacker != event.entityLiving) {
            EntityPlayer a = (EntityPlayer) attacker;
            ArmorCore c = core(a);
            // the arcane hit of Arcana swords and the hits of summons come "from the player", but that is not their melee: no core bonus (before 0.99.120 +6 applied to them too)
            if (c == ArmorCore.DIVINE && event.source.getSourceOfDamage() == a && !event.source.isProjectile() && !event.source.isMagicDamage()
                && !com.divinerpgextended.magic.ArcanaCombat.ARCANE.equals(event.source.damageType)) event.ammount += divineMelee(event.entityLiving);
            if (event.source.isProjectile()) {
                if (c == ArmorCore.CORRUPTED) event.ammount *= CORE_CORRUPTED_RANGED;
                if (ArmorData.hasEquippedPerk(a, Perk.MARKSMAN)) event.ammount *= MARKSMAN;
            }
        }
        if (event.entityLiving instanceof EntityPlayer && !event.entityLiving.worldObj.isRemote) {
            EntityPlayer v = (EntityPlayer) event.entityLiving;
            if ((event.source.isProjectile() || "thrown".equals(event.source.damageType)) && ArmorData.hasEquippedPerk(v, Perk.ARROW_GUARD)) event.ammount *= GUARD;
            if ("mob".equals(event.source.damageType) && !event.source.isProjectile() && ArmorData.hasEquippedPerk(v, Perk.BLOW_GUARD)) event.ammount *= GUARD;
        }
        if (attacker instanceof EntityPlayer && !attacker.worldObj.isDaytime()
            && ArmorData.hasEquippedPerk((EntityPlayer) attacker, Perk.NIGHT_REAPER)) {
            event.ammount *= 1.2F;
            com.divinerpgextended.debug.Debug.note(attacker, "Night Reaper perk: at night x1.2");
        }
        if (attacker instanceof EntityCarcinus && "mob".equals(event.source.damageType)
            && event.entityLiving instanceof EntityPlayer
            && wearsWaterArmor((EntityPlayer) event.entityLiving)) {
            event.ammount *= (float) Config.carcinusWaterArmorFactor;
            com.divinerpgextended.debug.Debug.note(event.entityLiving, "Water armor against Carcinus claws: x" + Config.carcinusWaterArmorFactor);
        }
        if (attacker instanceof EntityPlayer && isAquatic(event.entityLiving) && wears((EntityPlayer) attacker, KRAKEN)) {
            event.ammount *= 1.5F;
            com.divinerpgextended.debug.Debug.note(attacker, "Kraken armor: against water mobs x1.5");
        } else if (event.entityLiving instanceof EntityPlayer && isAquatic(attacker)
            && wears((EntityPlayer) event.entityLiving, KRAKEN)) {
                event.ammount *= 0.7F;
                com.divinerpgextended.debug.Debug.note(event.entityLiving, "Kraken armor: from water mobs x0.7");
            }
    }

    private static final Item[] BEDROCK = { VanillaItemsArmor.bedrockHelmet, VanillaItemsArmor.bedrockBody, VanillaItemsArmor.bedrockLegs, VanillaItemsArmor.bedrockBoots };

    /**
     * Steadfastness: a full bedrock set or its core. The player stands still - protection accumulates: Resistance I, then II, no knockback
     * (otherwise the very first hit would reset the stance). Once they move, knockback returns, and the effect itself ends within half a second: it must not
     * be removed, it could be a real potion. Hitting, eating and looking around are allowed.
     */
    public static void stance(EntityPlayer p) {
        net.minecraft.entity.ai.attributes.IAttributeInstance kb = p.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.knockbackResistance);
        net.minecraft.entity.ai.attributes.AttributeModifier cur = kb.getModifier(STANCE_ID);
        boolean on = (wears(p, BEDROCK) || ArmorCore.worn(p) == ArmorCore.BEDROCK) && !com.divinerpgextended.vethea.Nightmare.active(p);
        double[] s = STANCE.get(p);
        if (!on) {
            if (s != null) STANCE.remove(p);
            if (cur != null) kb.removeModifier(cur);
            return;
        }
        if (s == null) STANCE.put(p, s = new double[] { p.posX, p.posY, p.posZ, 0 });
        double dx = p.posX - s[0], dy = p.posY - s[1], dz = p.posZ - s[2];
        if (dx * dx + dy * dy + dz * dz > STANCE_MOVE * STANCE_MOVE) {
            s[0] = p.posX;
            s[1] = p.posY;
            s[2] = p.posZ;
            s[3] = 0;
        } else s[3]++;
        int ticks = (int) s[3], first = Config.bedrockStanceTicks[0], second = Config.bedrockStanceTicks[1];
        boolean firm = ticks >= first;
        if (firm && cur == null) kb.applyModifier(new net.minecraft.entity.ai.attributes.AttributeModifier(STANCE_ID, "dre.stance", 1, 0));
        else if (!firm && cur != null) kb.removeModifier(cur);
        if (!firm) return;
        if (ticks == first || ticks == second) p.worldObj.playSoundAtEntity(p, "dig.stone", 0.8F, ticks == second ? 0.6F : 0.8F);
        if (ticks == first || ticks == second || ticks % 10 == 0) p.addPotionEffect(new PotionEffect(Potion.resistance.id, 15, ticks >= second ? 1 : 0, true));
    }

    /** Ore: by Forge ore dictionary or by name, like Divine ores. Slag, logs and sand also smelt, but they are not ore. */
    static boolean isOre(ItemStack s) {
        for (int id : net.minecraftforge.oredict.OreDictionary.getOreIDs(s)) if (net.minecraftforge.oredict.OreDictionary.getOreName(id).startsWith("ore")) return true;
        Object name = Item.itemRegistry.getNameForObject(s.getItem());
        if (!(name instanceof String)) return false;
        String n = ((String) name).substring(((String) name).indexOf(':') + 1);
        return n.endsWith("Ore") || n.startsWith("ore") || n.endsWith("_ore");
    }

    /** Infernal armor: a full Inferno or Netherite set - ore has a chance to drop already smelted, one to one, as from a furnace. */
    @SubscribeEvent
    public void onHarvest(net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent e) {
        EntityPlayer p = e.harvester;
        if (p == null || e.world.isRemote || e.isSilkTouching || !(wears(p, INFERNO) || wears(p, NETHERITE)) || com.divinerpgextended.vethea.Nightmare.active(p)) return;
        boolean any = false;
        for (int i = 0; i < e.drops.size(); i++) {
            ItemStack s = e.drops.get(i);
            if (s == null || !isOre(s)) continue;
            ItemStack smelted = net.minecraft.item.crafting.FurnaceRecipes.smelting().getSmeltingResult(s);
            if (smelted == null || e.world.rand.nextDouble() >= Config.netherSmeltChance) continue;
            ItemStack out = smelted.copy();
            out.stackSize = smelted.stackSize * s.stackSize;
            e.drops.set(i, out);
            any = true;
        }
        if (any && e.world instanceof net.minecraft.world.WorldServer) ((net.minecraft.world.WorldServer) e.world).func_147487_a("flame", e.x + 0.5, e.y + 0.5, e.z + 0.5, 8, 0.25, 0.25, 0.25, 0.01);
    }

    private static final String ANGEL = "dreAngelFlight";

    /** Angelic Flight: while a set with the perk is worn (not in the dream) you can fly; take it off and flight is removed. */
    private static void angelFlight(EntityPlayer p) {
        boolean on = ArmorData.equippedTier(p) > 0 && ArmorData.hasEquippedPerk(p, Perk.ANGEL_FLIGHT)
            && !com.divinerpgextended.vethea.Nightmare.inVethea(p);
        boolean given = p.getEntityData().getBoolean(ANGEL);
        if (on && !p.capabilities.allowFlying) {
            p.capabilities.allowFlying = true;
            p.getEntityData().setBoolean(ANGEL, true);
            p.sendPlayerAbilities();
        } else if (!on && given) {
            p.getEntityData().setBoolean(ANGEL, false);
            if (!p.capabilities.isCreativeMode) {
                p.capabilities.allowFlying = false;
                p.capabilities.isFlying = false;
                p.sendPlayerAbilities();
            }
        }
    }

    private static java.lang.reflect.Method arcanaProps, arcanaRegen;

    /** Divine arcana (ArcanaHelper) - via reflection. */
    private static void arcana(EntityPlayer p, float amount) {
        try {
            if (arcanaProps == null) {
                Class<?> c = Class.forName("net.divinerpg.utils.events.ArcanaHelper");
                arcanaProps = c.getMethod("getProperties", EntityPlayer.class);
                arcanaRegen = c.getMethod("regen", float.class);
            }
            Object h = arcanaProps.invoke(null, p);
            if (h != null) arcanaRegen.invoke(h, amount);
        } catch (Throwable ignored) {}
    }

    /** "Soft landing" (donor - elite realmite): no fall damage; foreign armor does not save you. */
    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (event.entityLiving instanceof EntityPlayer && ArmorData.hasEquippedPerk((EntityPlayer) event.entityLiving, Perk.SOFT_LANDING)) {
            event.distance = 0;
        }
    }

    /** "Aqua Miner": digging in water without slowdown (as with the Diver) and even without footing. */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        EntityPlayer p = event.entityPlayer;
        if (!p.isInsideOfMaterial(Material.water) || !aquaMiner(p)) return;
        float speed = event.newSpeed;
        if (!EnchantmentHelper.getAquaAffinityModifier(p)) speed *= 5.0F;
        if (!p.onGround) speed *= 5.0F;
        event.newSpeed = speed;
    }

    /**
     * Speed of the Shadow core and perk. Every tick (in both phases, on both sides) Divine itself writes the walking speed from the worn
     * set - so we run at the lowest priority, right after it. Specifically the walking speed, not an attribute modifier: the field of view
     * does not stretch from it, just like with the Shadow set itself.
     */
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void onWalkSpeed(TickEvent.PlayerTickEvent event) {
        EntityPlayer p = event.player;
        highStep(p);
        if (ArmorData.equippedTier(p) == 0 || com.divinerpgextended.vethea.Nightmare.inVethea(p)) return;
        float mult = 1F;
        if (ArmorCore.worn(p) == ArmorCore.SHADOW) mult = (float) Config.coreShadowSpeed;
        if (ArmorData.hasEquippedPerk(p, Perk.SWIFTNESS)) mult = Math.max(mult, SWIFT_SPEED);
        if (mult != 1F) cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.entity.player.PlayerCapabilities.class, p.capabilities, 0.1F * mult, "walkSpeed", "field_75097_g");
    }

    /**
     * High step of the Shadow perk, on both sides: the client computes walking, and the server checks it with its own step. We remove only what
     * we set ourselves: the step can also be held by items of other mods (Thaumcraft boots, Botania belt).
     */
    private static void highStep(EntityPlayer p) {
        if (ArmorData.hasEquippedPerk(p, Perk.SWIFTNESS) && !com.divinerpgextended.vethea.Nightmare.inVethea(p)) {
            if (p.stepHeight < HIGH_STEP) {
                p.stepHeight = HIGH_STEP;
                p.getEntityData().setBoolean(STEP_TAG, true);
            }
        } else if (p.getEntityData().getBoolean(STEP_TAG)) {
            p.getEntityData().removeTag(STEP_TAG);
            p.stepHeight = NORMAL_STEP;
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.worldObj.isRemote) return;
        EntityPlayer player = event.player;

        if (player.isInsideOfMaterial(Material.water) && aquaMiner(player)) {
            PotionEffect nv = player.getActivePotionEffect(Potion.nightVision);
            if (nv == null || nv.getDuration() < 220) player.addPotionEffect(new PotionEffect(Potion.nightVision.id, 300, 0, true));
        }

        angelFlight(player);
        stance(player);
        // antidote of the jungle set (for Wanderer - by the perk below): poison removed, other bad effects pass faster
        if (Antidote.jungleSet(player) && player.isPotionActive(Potion.poison)) player.removePotionEffect(Potion.poison.id);
        if (player.ticksExisted % Antidote.PERIOD == 0) Antidote.tick(player);
        if (ArmorData.equippedTier(player) == 0) return;
        if (player.getHealth() < player.getMaxHealth() && ArmorData.hasEquippedPerk(player, Perk.REGENERATION)) player.heal(0.1F);
        if (ArmorData.hasEquippedPerk(player, Perk.ARCANA_FLOW)) arcana(player, 1.0F);
        if (player.ticksExisted % 20 == 0 && ArmorData.hasEquippedPerk(player, Perk.MINING_HASTE)) player.addPotionEffect(new PotionEffect(Potion.digSpeed.id, 60, 1, true));
        if (player.isInsideOfMaterial(Material.water) && ArmorData.hasEquippedPerk(player, Perk.WATER_BREATHING)) {
            player.setAir(300);
        }
        if (player.isPotionActive(Potion.poison) && ArmorData.hasEquippedPerk(player, Perk.POISON_RESISTANCE)) {
            player.removePotionEffect(Potion.poison.id);
        }
        if (player.isPotionActive(Potion.wither) && ArmorData.hasEquippedPerk(player, Perk.WITHER_PROTECTION)) {
            player.removePotionEffect(Potion.wither.id);
        }
        if (ArmorData.hasEquippedPerk(player, Perk.SATIETY)) Satiety.tick(player); // hunger twice as slow (no death from hunger - Satiety)
        if (player.ticksExisted % 20 == 0 && ArmorData.hasEquippedPerk(player, Perk.COLD_AURA)) {
            @SuppressWarnings("unchecked")
            List<EntityLivingBase> near = player.worldObj.getEntitiesWithinAABB(EntityLivingBase.class,
                player.boundingBox.expand(6, 3, 6));
            for (EntityLivingBase e : near) {
                if (e instanceof IMob) e.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 1));
            }
        }
    }
}
