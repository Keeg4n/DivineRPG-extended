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

/** Эффекты перков и ядра полного сета брони Странника и бонусы водных сетов Divine (Kraken, Aquastrive). */
public class PerkEffects {

    /** Перки из сетов Тени, порчи, арлемита и рупия: те же свойства, но слабее самих сетов (скорость x3, снаряды x1.2, урон x0.3). */
    public static final float SWIFT_SPEED = 1.25F, MARKSMAN = 1.1F, GUARD = 0.75F;
    /** Высокий шаг перка Тени: на блок вверх без прыжка. Обычный шаг игрока - 0.5. */
    public static final float HIGH_STEP = 1.0F, NORMAL_STEP = 0.5F;
    private static final String STEP_TAG = "dreHighStep";
    /** Ядро: то же, что полный сет. */
    public static final float CORE_DIVINE_MELEE = 6F, CORE_DIVINE_UNDEAD = 10F, CORE_CORRUPTED_RANGED = 1.2F;

    /** Нерушимость бедрока: сдвиг дальше этого от места, где встал, сбрасывает стойку (мелкие толчки не считаются). */
    private static final double STANCE_MOVE = 0.25;
    private static final java.util.UUID STANCE_ID = java.util.UUID.fromString("c3a71f52-8d04-4e6b-9a17-2b6f4e90d1a8");
    /** Где игрок встал и сколько тиков стоит: {x, y, z, тики}. */
    private static final java.util.Map<EntityPlayer, double[]> STANCE = new java.util.WeakHashMap<EntityPlayer, double[]>();

    /**
     * Ближний бой в божественной броне: сет и ядро бьют только то, что само враждебно. Нежить сильнее, вражеские мобы, боссы и игроки
     * как раньше, мирные и нейтральные без бонуса.
     */
    public static float divineMelee(EntityLivingBase target) {
        if (target.getCreatureAttribute() == net.minecraft.entity.EnumCreatureAttribute.UNDEAD) return CORE_DIVINE_UNDEAD;
        return target instanceof IMob || target instanceof net.minecraft.entity.boss.IBossDisplayData || target instanceof EntityPlayer
            || target instanceof com.divinerpgextended.dummy.EntityTrainingDummy ? CORE_DIVINE_MELEE : 0;
    }

    /** Ядро надетого сета; во сне (кошмар Ветеи) чужие сеты молчат, ядро тоже. */
    private static ArmorCore core(EntityPlayer p) {
        ArmorCore c = ArmorCore.worn(p);
        return c == null || com.divinerpgextended.vethea.Nightmare.active(p) ? null : c;
    }

    private static final Item[] KRAKEN = { VanillaItemsArmor.krakenHelmet, VanillaItemsArmor.krakenBody,
        VanillaItemsArmor.krakenLegs, VanillaItemsArmor.krakenBoots };
    private static final Item[] AQUASTRIVE = { VanillaItemsArmor.aquastriveHelmet, VanillaItemsArmor.aquastriveBody,
        VanillaItemsArmor.aquastriveLegs, VanillaItemsArmor.aquastriveBoots };

    /** Полный сет Divine: set = [шлем, нагрудник, поножи, ботинки], слоты игрока 0 ботинки ... 3 шлем. */
    public static boolean wears(EntityPlayer p, Item[] set) {
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            if (s == null || s.getItem() != set[3 - i]) return false;
        }
        return true;
    }

    /** Водные существа для бонуса Kraken. */
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
            // как сами сеты: взрывы - только у победивших Смотрителя (его бой - про взрывы), огонь - у бедрока
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
            com.divinerpgextended.debug.Debug.note(player, "Перк защиты от огня: огонь не ранит");
        } else if (event.source.isExplosion() && ArmorData.hasEquippedPerk(player, Perk.EXPLOSION_PROTECTION)) {
            event.setCanceled(true);
            com.divinerpgextended.debug.Debug.note(player, "Перк защиты от взрывов: взрыв не ранит");
        }
    }

    private static final Item[] INFERNO = { VanillaItemsArmor.infernoHelmet, VanillaItemsArmor.infernoBody,
        VanillaItemsArmor.infernoLegs, VanillaItemsArmor.infernoBoots };
    private static final Item[] NETHERITE = { VanillaItemsArmor.netheriteHelmet, VanillaItemsArmor.netheriteBody,
        VanillaItemsArmor.netheriteLegs, VanillaItemsArmor.netheriteBoots };

    /** Огненная броня: полный сет Inferno или Netherite, либо броня Странника с огнезащитой. */
    public static boolean wearsFireArmor(EntityPlayer p) {
        return wears(p, INFERNO) || wears(p, NETHERITE) || ArmorData.hasEquippedPerk(p, Perk.FIRE_PROTECTION);
    }

    /** Водная броня: полный сет Kraken или Aquastrive, либо броня Странника с водным перком. */
    public static boolean wearsWaterArmor(EntityPlayer p) {
        return wears(p, KRAKEN) || wears(p, AQUASTRIVE) || ArmorData.hasEquippedPerk(p, Perk.WATER_BREATHING)
            || ArmorData.hasEquippedPerk(p, Perk.AQUA_MINER);
    }

    /**
     * "Охотник глубин": полный сет Kraken - +50% урона по водным существам и -30% урона от них. Карцинус в водную
     * броню клешнями почти не пробивает: она сделана как раз против обитателей глубин. Песчаный вихрь бьёт полностью.
     */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        Entity attacker = event.source.getEntity();
        if (attacker instanceof EntityPlayer && !attacker.worldObj.isRemote && attacker != event.entityLiving) {
            EntityPlayer a = (EntityPlayer) attacker;
            ArmorCore c = core(a);
            if (c == ArmorCore.DIVINE && event.source.getSourceOfDamage() == a && !event.source.isProjectile() && !event.source.isMagicDamage()) event.ammount += divineMelee(event.entityLiving);
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
            com.divinerpgextended.debug.Debug.note(attacker, "Перк Ночной жнец: ночью x1.2");
        }
        if (attacker instanceof EntityCarcinus && "mob".equals(event.source.damageType)
            && event.entityLiving instanceof EntityPlayer
            && wearsWaterArmor((EntityPlayer) event.entityLiving)) {
            event.ammount *= (float) Config.carcinusWaterArmorFactor;
            com.divinerpgextended.debug.Debug.note(event.entityLiving, "Водная броня против клешней Карцинуса: x" + Config.carcinusWaterArmorFactor);
        }
        if (attacker instanceof EntityPlayer && isAquatic(event.entityLiving) && wears((EntityPlayer) attacker, KRAKEN)) {
            event.ammount *= 1.5F;
            com.divinerpgextended.debug.Debug.note(attacker, "Сет Kraken: по водным x1.5");
        } else if (event.entityLiving instanceof EntityPlayer && isAquatic(attacker)
            && wears((EntityPlayer) event.entityLiving, KRAKEN)) {
                event.ammount *= 0.7F;
                com.divinerpgextended.debug.Debug.note(event.entityLiving, "Сет Kraken: от водных x0.7");
            }
    }

    private static final Item[] BEDROCK = { VanillaItemsArmor.bedrockHelmet, VanillaItemsArmor.bedrockBody, VanillaItemsArmor.bedrockLegs, VanillaItemsArmor.bedrockBoots };

    /**
     * Нерушимость: полный сет бедрока или его ядро. Игрок стоит на месте - копится защита: Сопротивление I, затем II, отброса нет
     * (иначе первый же удар сбрасывал бы стойку). Сдвинулся - отброс возвращается, а эффект сам кончается за полсекунды: снимать
     * его нельзя, это мог быть настоящий эликсир. Бить, есть и смотреть по сторонам можно.
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

    /** Руда: по словарю Forge или по имени, как у руд Divine. Шлак, бревно и песок тоже плавятся, но это не руда. */
    static boolean isOre(ItemStack s) {
        for (int id : net.minecraftforge.oredict.OreDictionary.getOreIDs(s)) if (net.minecraftforge.oredict.OreDictionary.getOreName(id).startsWith("ore")) return true;
        Object name = Item.itemRegistry.getNameForObject(s.getItem());
        if (!(name instanceof String)) return false;
        String n = ((String) name).substring(((String) name).indexOf(':') + 1);
        return n.endsWith("Ore") || n.startsWith("ore") || n.endsWith("_ore");
    }

    /** Адская броня: полный сет Inferno или Netherite - руда с шансом падает сразу переплавленной, один к одному, как из печи. */
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

    /** Ангельский полёт: пока надет сет с перком (не во сне) - можно летать; снял - полёт отбирается. */
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

    /** Аркана Divine (ArcanaHelper) - отражением. */
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

    /** Без урона от падения встроено в ступень R1 (элитный реалмит). */
    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (event.entityLiving instanceof EntityPlayer && ArmorData.equippedTier((EntityPlayer) event.entityLiving) >= 1) {
            event.distance = 0;
        }
    }

    /** "Подводный шахтёр": в воде копание без замедления (как с Подводником) и даже без опоры под ногами. */
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
     * Скорость ядра Тени и перка. Divine в каждом тике (в обеих фазах, на обеих сторонах) сама пишет скорость ходьбы по надетому
     * сету - поэтому самым низким приоритетом, сразу за ней. Именно скорость ходьбы, а не модификатор атрибута: поле зрения
     * от неё не растягивается, как и у самого сета Тени.
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
     * Высокий шаг перка Тени, на обеих сторонах: ходьбу считает клиент, а сервер сверяет её своим шагом. Снимаем только то, что
     * поставили сами: шаг могут держать и вещи других модов (сапоги Таума, пояс Ботании).
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
        if (player.ticksExisted % 40 == 0 && player.getFoodStats().needFood() && ArmorData.hasEquippedPerk(player, Perk.SATIETY)) {
            player.getFoodStats().addStats(1, 0.0F);
        }
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
