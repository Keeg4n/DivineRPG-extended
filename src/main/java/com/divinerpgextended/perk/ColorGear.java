package com.divinerpgextended.perk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.utils.items.VanillaItemsArmor;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Цвет вещей Divine (божественные и эндер-мечи, эндер- и рупиевая броня) - стихия, одна и та же у оружия и брони.
 * Бесцветные - без стихии. Броню можно мешать: каждая часть цвета даёт четверть эффекта, полный сет цвета - ещё бонус.
 * <ul>
 * <li>красный (огонь): меч поджигает, по уже горящим +{@link #RED_BONUS}; броня - огонь слабее на четверть за часть,
 * полный сет поджигает бьющих в ближнем бою;</li>
 * <li>синий (вода и лёд): меч замедляет; броня - утопление слабее на четверть за часть, полный сет - воздух не кончается;</li>
 * <li>зелёный (природа): меч отравляет (нежить и пауков, которым яд нипочём, бьёт на {@link #GREEN_BONUS} сильнее); броня - яд слабее на четверть за часть, полный сет снимает яд и лечит раз в
 * {@link #REGEN_TICKS} тиков;</li>
 * <li>жёлтый (свет): удар ускоряет бьющего (игрока-цель - ослепляет); броня - +{@link #SPEED_PER_PIECE} скорости за часть,
 * полный сет - ночное зрение;</li>
 * <li>серый (камень): удар сбивает с ног - сильный отброс и миг оглушения (боссов не двигает); броня - отбрасывание
 * меньше на четверть за часть, полный сет - снаряды на {@link #GRAY_ARROWS} слабее.</li>
 * </ul>
 * Бедрок - несдвигаемый: с бедроковым мечом в руке не отбрасывает; бедроковая булава бьёт волной - мобы в
 * {@link #WAVE_R} блоках от цели получают {@link #WAVE_DMG} урона удара и отлетают (боссы не отлетают).
 */
public class ColorGear {

    public enum Hue {
        RED, BLUE, GREEN, YELLOW, GRAY;

        public String key() {
            return name().toLowerCase();
        }
    }

    public static final float RED_BONUS = 1.2F, GREEN_BONUS = 1.2F, GRAY_ARROWS = 0.25F, WAVE_DMG = 0.4F;
    public static final double SPEED_PER_PIECE = 0.05, KNOCK_PER_PIECE = 0.25, WAVE_R = 3;
    public static final int REGEN_TICKS = 100;
    private static final UUID SPEED = UUID.fromString("2f6c1d8e-4b7a-4e21-9c3d-7a5b8e1f0c21"), STEADY = UUID.fromString(
        "5d9e2a71-3c4b-4f8d-a6e2-1b7c9d0e4f32"), BEDROCK = UUID.fromString("8a1b3c5d-7e9f-4a2b-8c4d-6e8f0a2b4c43");
    private static final Map<Item, Hue> HUE = new HashMap<Item, Hue>();
    /** Удар волной булавы - сам волну не порождает. */
    private static boolean inWave;

    public static void setup() {
        HUE.clear();
        put(Hue.RED, VanillaItemsWeapons.redDivineSword, VanillaItemsWeapons.redEnderSword, VanillaItemsArmor.redEnderHelmet, VanillaItemsArmor.redEnderBody,
            VanillaItemsArmor.redEnderLegs, VanillaItemsArmor.redEnderBoots, VanillaItemsArmor.redRupeeHelmet, VanillaItemsArmor.redRupeeBody,
            VanillaItemsArmor.redRupeeLegs, VanillaItemsArmor.redRupeeBoots);
        put(Hue.BLUE, VanillaItemsWeapons.blueDivineSword, VanillaItemsWeapons.blueEnderSword, VanillaItemsArmor.blueEnderHelmet, VanillaItemsArmor.blueEnderBody,
            VanillaItemsArmor.blueEnderLegs, VanillaItemsArmor.blueEnderBoots, VanillaItemsArmor.blueRupeeHelmet, VanillaItemsArmor.blueRupeeBody,
            VanillaItemsArmor.blueRupeeLegs, VanillaItemsArmor.blueRupeeBoots);
        put(Hue.GREEN, VanillaItemsWeapons.greenDivineSword, VanillaItemsWeapons.greenEnderSword, VanillaItemsArmor.greenEnderHelmet,
            VanillaItemsArmor.greenEnderBody, VanillaItemsArmor.greenEnderLegs, VanillaItemsArmor.greenEnderBoots, VanillaItemsArmor.greenRupeeHelmet,
            VanillaItemsArmor.greenRupeeBody, VanillaItemsArmor.greenRupeeLegs, VanillaItemsArmor.greenRupeeBoots);
        put(Hue.YELLOW, VanillaItemsWeapons.yellowDivineSword, VanillaItemsWeapons.yellowEnderSword, VanillaItemsArmor.yellowEnderHelmet,
            VanillaItemsArmor.yellowEnderBody, VanillaItemsArmor.yellowEnderLegs, VanillaItemsArmor.yellowEnderBoots, VanillaItemsArmor.yellowRupeeHelmet,
            VanillaItemsArmor.yellowRupeeBody, VanillaItemsArmor.yellowRupeeLegs, VanillaItemsArmor.yellowRupeeBoots);
        put(Hue.GRAY, VanillaItemsWeapons.grayDivineSword, VanillaItemsWeapons.grayEnderSword, VanillaItemsArmor.grayEnderHelmet, VanillaItemsArmor.grayEnderBody,
            VanillaItemsArmor.grayEnderLegs, VanillaItemsArmor.grayEnderBoots, VanillaItemsArmor.grayRupeeHelmet, VanillaItemsArmor.grayRupeeBody,
            VanillaItemsArmor.grayRupeeLegs, VanillaItemsArmor.grayRupeeBoots);
    }

    private static void put(Hue h, Item... items) {
        for (Item i : items) if (i != null) HUE.put(i, h);
    }

    /** Стихия вещи (null - бесцветная или не из этих семейств). */
    public static Hue hue(ItemStack s) {
        return s == null ? null : HUE.get(s.getItem());
    }

    public static boolean isWeapon(ItemStack s) {
        return s != null && s.getItem() instanceof net.minecraft.item.ItemSword;
    }

    /** Сколько надетых частей этого цвета. */
    public static int pieces(EntityLivingBase e, Hue h) {
        int n = 0;
        for (int i = 1; i <= 4; i++) if (hue(e.getEquipmentInSlot(i)) == h) n++;
        return n;
    }

    // ---------------- удары ----------------

    /** Ближний удар игрока (не снаряд): оружие в руке. */
    private static EntityPlayer meleePlayer(DamageSource s) {
        Entity a = s.getEntity();
        return a instanceof EntityPlayer && s.getSourceOfDamage() == a ? (EntityPlayer) a : null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote) return;
        EntityPlayer p = meleePlayer(e.source);
        if (p != null && p != v) {
            ItemStack held = p.getHeldItem();
            Hue h = isWeapon(held) ? hue(held) : null;
            if (h != null) strike(p, v, h, e);
            if (held != null && held.getItem() == VanillaItemsWeapons.bedrockMaul && !inWave) wave(p, v, e.ammount);
        }
        if (v instanceof EntityPlayer) defend((EntityPlayer) v, e);
    }

    private static void strike(EntityPlayer p, EntityLivingBase v, Hue h, LivingHurtEvent e) {
        switch (h) {
            case RED:
                if (v.isBurning()) e.ammount *= RED_BONUS;
                v.setFire(3);
                break;
            case BLUE:
                v.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 1));
                break;
            case GREEN: // нежить и пауки яда не боятся - их природа бьёт сильнее
                PotionEffect poison = new PotionEffect(Potion.poison.id, 80, 0);
                if (v.isPotionApplicable(poison)) v.addPotionEffect(poison);
                else e.ammount *= GREEN_BONUS;
                break;
            case YELLOW:
                p.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 60, 0));
                if (v instanceof EntityPlayer) v.addPotionEffect(new PotionEffect(Potion.blindness.id, 30, 0));
                break;
            case GRAY:
                v.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 10, 4));
                if (!RangedRoles.boss(v)) push(v, p.posX, p.posZ, 0.6);
                break;
        }
    }

    private static void push(EntityLivingBase v, double fromX, double fromZ, double force) {
        double dx = v.posX - fromX, dz = v.posZ - fromZ, d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
        v.motionX += dx / d * force;
        v.motionZ += dz / d * force;
        v.motionY = Math.max(v.motionY, 0.3);
        v.velocityChanged = true;
    }

    /**
     * Кого задевает волна: того же рода, что и цель (бьёшь враждебного - только враждебных, жителей и големов деревни не
     * трогает; бьёшь голема или животное - остальных не враждебных). Игроков, жителей и прирученных - никогда: питомец
     * считается прирученным и при хозяине не в сети (хозяин записан в питомце), лошадь - по своему признаку приручения.
     */
    static boolean sameKind(EntityLivingBase o, EntityLivingBase center) {
        if (o instanceof EntityPlayer || o instanceof net.minecraft.entity.passive.EntityVillager) return false;
        if (tamed(o)) return false;
        return o instanceof net.minecraft.entity.monster.IMob == center instanceof net.minecraft.entity.monster.IMob;
    }

    static boolean tamed(EntityLivingBase o) {
        if (o instanceof net.minecraft.entity.passive.EntityTameable && ((net.minecraft.entity.passive.EntityTameable) o).isTamed()) return true;
        if (o instanceof net.minecraft.entity.passive.EntityHorse && ((net.minecraft.entity.passive.EntityHorse) o).isTame()) return true;
        if (o instanceof net.minecraft.entity.IEntityOwnable) {
            String id = ((net.minecraft.entity.IEntityOwnable) o).func_152113_b();
            if (id != null && !id.isEmpty()) return true;
        }
        return false;
    }

    /** Волна бедроковой булавы: мобы вокруг цели (не игроки) получают долю удара и отлетают от неё. */
    private static void wave(EntityPlayer p, EntityLivingBase center, float dmg) {
        @SuppressWarnings("unchecked")
        List<EntityLivingBase> l = center.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, center.boundingBox.expand(WAVE_R, 1.5, WAVE_R));
        inWave = true;
        try {
            for (EntityLivingBase o : l) {
                if (o == center || o == p || o instanceof EntityPlayer || !o.isEntityAlive() || o.getDistanceToEntity(center) > WAVE_R + 1) continue;
                if (!sameKind(o, center)) continue;
                o.hurtResistantTime = 0;
                o.attackEntityFrom(DamageSource.causePlayerDamage(p), dmg * WAVE_DMG);
                if (!RangedRoles.boss(o)) push(o, center.posX, center.posZ, 0.7);
            }
        } finally {
            inWave = false;
        }
        center.worldObj.playSoundAtEntity(center, "random.explode", 0.4F, 1.6F);
    }

    // ---------------- броня ----------------

    private static void defend(EntityPlayer p, LivingHurtEvent e) {
        DamageSource s = e.source;
        if (s.isFireDamage()) e.ammount *= 1 - 0.25F * pieces(p, Hue.RED);
        if (s == DamageSource.drown) e.ammount *= 1 - 0.25F * pieces(p, Hue.BLUE);
        if (s == DamageSource.magic && p.isPotionActive(Potion.poison)) e.ammount *= 1 - 0.25F * pieces(p, Hue.GREEN);
        if (s.isProjectile() && pieces(p, Hue.GRAY) == 4) e.ammount *= 1 - GRAY_ARROWS;
        Entity a = s.getEntity();
        if (a instanceof EntityLivingBase && s.getSourceOfDamage() == a && a != p && pieces(p, Hue.RED) == 4) a.setFire(3);
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || e.entityLiving.ticksExisted % 10 != 0) return;
        update((EntityPlayer) e.entityLiving);
    }

    /** Раз в полсекунды: модификаторы скорости и устойчивости по цвету брони и бедроку в руке, эффекты полных сетов. */
    public static void update(EntityPlayer p) {
        modifier(p.getEntityAttribute(SharedMonsterAttributes.movementSpeed), SPEED, "dre.yellow", SPEED_PER_PIECE * pieces(p, Hue.YELLOW), 1);
        modifier(p.getEntityAttribute(SharedMonsterAttributes.knockbackResistance), STEADY, "dre.gray", KNOCK_PER_PIECE * pieces(p, Hue.GRAY), 0);
        ItemStack held = p.getHeldItem();
        boolean bedrock = held != null && held.getItem() == VanillaItemsWeapons.bedrockSword;
        modifier(p.getEntityAttribute(SharedMonsterAttributes.knockbackResistance), BEDROCK, "dre.bedrock", bedrock ? 1 : 0, 0);
        if (pieces(p, Hue.BLUE) == 4 && p.isInsideOfMaterial(net.minecraft.block.material.Material.water)) p.setAir(300);
        if (pieces(p, Hue.GREEN) == 4) {
            if (p.isPotionActive(Potion.poison)) p.removePotionEffect(Potion.poison.id);
            if (p.ticksExisted % REGEN_TICKS == 0 && p.getHealth() < p.getMaxHealth()) p.heal(1);
        }
        if (pieces(p, Hue.YELLOW) == 4) {
            PotionEffect nv = p.getActivePotionEffect(Potion.nightVision);
            if (nv == null || nv.getDuration() < 220) p.addPotionEffect(new PotionEffect(Potion.nightVision.id, 300, 0, true));
        }
    }

    private static void modifier(IAttributeInstance a, UUID id, String name, double amount, int op) {
        AttributeModifier cur = a.getModifier(id);
        if (cur != null && cur.getAmount() == amount) return;
        if (cur != null) a.removeModifier(cur);
        if (amount != 0) a.applyModifier(new AttributeModifier(id, name, amount, op));
    }
}
