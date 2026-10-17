package com.divinerpgextended.boss;

import java.util.List;

import net.divinerpg.entities.twilight.EntityKarot;
import net.divinerpg.entities.twilight.EntityTwilightDemon;
import net.divinerpg.utils.items.TwilightItemsOther;
import net.divinerpg.utils.items.TwilightItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.google.common.collect.Multimap;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Боссы Демонического круга Мортума:
 * <ul>
 * <li>Демон сумерек - дилемма. Три фазы; на 2/3 и 1/3 здоровья он замирает ({@link #OFFER} тиков, неуязвим) и
 * предлагает разойтись. Не трогать - уходит во тьму: победа засчитана, но трофеи только этой фазы. Ударить - бой
 * продолжается, и он злее: во 2-й фазе стреляет чаще и зовёт демонов, в 3-й - тьма (слепота) и толпа. Полная победа -
 * все трофеи.</li>
 * <li>Карот - зеркало. Копирует снаряжение того, с кем дерётся: бьёт как его оружие, держит удар как его броня.
 * Пришёл в топе - тяжело, но и трофеев больше (по самому сильному снаряжению за бой); голым - легко и скромно.
 * Ступень брони против Карота не требуется ({@link BossDamage}).</li>
 * </ul>
 */
public class MortumBosses {

    public static final String PHASE = "drePhase", OFFER_LEFT = "dreOffer", MIRROR_W = "dreMirrorW", MIRROR_A = "dreMirrorA",
        MAX_POWER = "dreMirrorMax";
    public static final int OFFER = 200;
    /** Оружие с таким уроном - Карот бьёт в полную силу Divine; кулак - в 0.2, топовое оружие - до x3. */
    public static final float MIRROR_REF = 10F;

    // ---------------- Демон сумерек ----------------

    public static int phase(EntityLivingBase e) {
        return e.getEntityData().getInteger(PHASE);
    }

    public static int offerLeft(EntityLivingBase e) {
        return e.getEntityData().getInteger(OFFER_LEFT);
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        if (e.entityLiving instanceof EntityTwilightDemon) demonTick((EntityTwilightDemon) e.entityLiving);
        else if (e.entityLiving instanceof EntityKarot && e.entityLiving.ticksExisted % 10 == 0) mirror((EntityKarot) e.entityLiving);
    }

    private void demonTick(EntityTwilightDemon d) {
        NBTTagCompound tag = d.getEntityData();
        WorldServer ws = (WorldServer) d.worldObj;
        int offer = tag.getInteger(OFFER_LEFT);
        if (offer > 0) {
            d.motionX = d.motionZ = 0;
            d.getNavigator().clearPathEntity();
            setShooting(d, 0);
            if (offer % 5 == 0) ws.func_147487_a("portal", d.posX, d.posY + 2, d.posZ, 20, 1.5, 1.5, 1.5, 0.5);
            tag.setInteger(OFFER_LEFT, offer - 1);
            if (offer == 1) leave(d);
            return;
        }
        int phase = tag.getInteger(PHASE);
        float f = d.getHealth() / d.getMaxHealth();
        if (phase == 0 && f <= 2 / 3F || phase == 1 && f <= 1 / 3F) {
            tag.setInteger(OFFER_LEFT, OFFER);
            say(d, "dre.chat.demon_offer");
            d.worldObj.playSoundAtEntity(d, "mob.wither.idle", 2.0F, 0.5F);
            return;
        }
        if (phase >= 1 && d.ticksExisted % 100 == 0) setShooting(d, 60); // стреляет вдвое чаще
        if (phase >= 1 && d.ticksExisted % 300 == 0) summon(d, net.divinerpg.entities.twilight.EntityMortumDemon.class, phase >= 2 ? 3 : 2);
        if (phase >= 2 && d.ticksExisted % 200 == 0) {
            for (EntityPlayer p : players(d, 30)) p.addPotionEffect(new PotionEffect(Potion.blindness.id, 70, 0));
            d.worldObj.playSoundAtEntity(d, "mob.wither.ambient", 2.0F, 0.4F);
            summon(d, net.divinerpg.entities.twilight.EntitySoulSpider.class, 5);
        }
    }

    private static void setShooting(EntityTwilightDemon d, int v) {
        try {
            java.lang.reflect.Field f = EntityTwilightDemon.class.getDeclaredField("shooting");
            f.setAccessible(true);
            f.setInt(d, v);
        } catch (Exception ignored) {
            // нет поля в этой версии Divine - стрельба как в Divine
        }
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayer> players(Entity d, double r) {
        return d.worldObj.getEntitiesWithinAABB(EntityPlayer.class, d.boundingBox.expand(r, r / 2, r));
    }

    private static void say(Entity d, String key) {
        for (EntityPlayer p : players(d, 40)) {
            ChatComponentTranslation m = new ChatComponentTranslation(key);
            m.getChatStyle().setColor(EnumChatFormatting.DARK_PURPLE).setItalic(true);
            p.addChatMessage(m);
        }
    }

    private static void summon(EntityLivingBase boss, Class<? extends EntityLiving> kind, int max) {
        int alive = 0;
        for (Object o : boss.worldObj.getEntitiesWithinAABB(EntityLiving.class, boss.boundingBox.expand(30, 15, 30)))
            if (((Entity) o).getEntityData().getInteger("dreDemonAdd") == boss.getEntityId()) alive++;
        for (int i = 0; i < 2 && alive + i < max; i++) {
            try {
                EntityLiving m = kind.getConstructor(net.minecraft.world.World.class).newInstance(boss.worldObj);
                double a = boss.worldObj.rand.nextDouble() * Math.PI * 2;
                m.setLocationAndAngles(boss.posX + Math.cos(a) * 5, boss.posY + 0.5, boss.posZ + Math.sin(a) * 5, 0, 0);
                if (!boss.worldObj.getCollidingBoundingBoxes(m, m.boundingBox).isEmpty()) continue;
                m.getEntityData().setInteger("dreDemonAdd", boss.getEntityId());
                boss.worldObj.spawnEntityInWorld(m);
            } catch (Exception ignored) {
                // нет конструктора (World)
            }
        }
    }

    /** Удар во время предложения - отказ: бой продолжается, фаза растёт. Сам удар не проходит. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (!(e.entityLiving instanceof EntityTwilightDemon) || e.entityLiving.worldObj.isRemote) return;
        NBTTagCompound tag = e.entityLiving.getEntityData();
        if (tag.getInteger(OFFER_LEFT) <= 0) return;
        e.setCanceled(true);
        if (!(e.source.getEntity() instanceof EntityPlayer)) return;
        tag.setInteger(OFFER_LEFT, 0);
        tag.setInteger(PHASE, tag.getInteger(PHASE) + 1);
        say(e.entityLiving, tag.getInteger(PHASE) >= 2 ? "dre.chat.demon_refuse2" : "dre.chat.demon_refuse");
        e.entityLiving.worldObj.playSoundAtEntity(e.entityLiving, "mob.wither.spawn", 1.5F, 0.6F);
    }

    /** Ушёл: победа засчитана тем, кто бился, трофеи этой фазы. */
    private static void leave(EntityTwilightDemon d) {
        int phase = d.getEntityData().getInteger(PHASE);
        say(d, "dre.chat.demon_leave");
        ((WorldServer) d.worldObj).func_147487_a("largesmoke", d.posX, d.posY + 2, d.posZ, 60, 1.5, 2, 1.5, 0.05);
        d.entityDropItem(new ItemStack(TwilightItemsOther.mortumSoul, 6 + phase * 4), 1);
        if (phase >= 1) d.entityDropItem(new ItemStack(d.worldObj.rand.nextBoolean() ? TwilightItemsWeapons.haliteBlitz : TwilightItemsWeapons.haliteBow), 1);
        EntityPlayer near = d.worldObj.getClosestPlayerToEntity(d, 40);
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(d, near != null ? DamageSource.causePlayerDamage(near) : DamageSource.generic));
        d.setDead();
    }

    /** Полная победа (3-я фаза): оба оружия халита, статуя и больше душ. */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        if (e.entityLiving instanceof EntityTwilightDemon && phase(e.entityLiving) >= 2) {
            boolean blitz = false, bow = false;
            for (EntityItem it : e.drops) {
                if (it.getEntityItem().getItem() == TwilightItemsWeapons.haliteBlitz) blitz = true;
                if (it.getEntityItem().getItem() == TwilightItemsWeapons.haliteBow) bow = true;
            }
            if (!blitz) drop(e, new ItemStack(TwilightItemsWeapons.haliteBlitz));
            if (!bow) drop(e, new ItemStack(TwilightItemsWeapons.haliteBow));
            drop(e, new ItemStack(TwilightItemsOther.mortumSoul, 16));
        }
        if (e.entityLiving instanceof EntityKarot) {
            float power = e.entityLiving.getEntityData().getFloat(MAX_POWER);
            drop(e, new ItemStack(TwilightItemsOther.mortumSoul, 4 + (int) (power * 4)));
            if (power >= 2.5F) drop(e, new ItemStack(TwilightItemsWeapons.halitePhaser));
            if (power >= 1.5F) drop(e, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.divinerpg.utils.blocks.VanillaBlocks.karotStatue)));
        }
    }

    private static void drop(LivingDropsEvent e, ItemStack s) {
        e.drops.add(new EntityItem(e.entityLiving.worldObj, e.entityLiving.posX, e.entityLiving.posY + 1, e.entityLiving.posZ, s));
    }

    // ---------------- Карот ----------------

    /** Урон оружия в руке (как в подсказке: 1 + модификаторы урона предмета). */
    @SuppressWarnings("unchecked")
    public static float weaponDamage(EntityPlayer p) {
        ItemStack s = p.getHeldItem();
        float d = 1;
        if (s == null) return d;
        Multimap<String, AttributeModifier> m = s.getAttributeModifiers();
        for (AttributeModifier a : m.get(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName())) d += a.getAmount();
        return d;
    }

    /** Сила зеркала: 0 - голый с кулаком, 1 - средний, 3+ - топ. */
    public static float power(float weapon, int armor) {
        return weapon / 10F + armor / 20F;
    }

    private void mirror(EntityKarot k) {
        EntityLivingBase t = k.getAttackTarget();
        if (!(t instanceof EntityPlayer)) t = k.worldObj.getClosestVulnerablePlayerToEntity(k, 40);
        if (!(t instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) t;
        float w = weaponDamage(p);
        int a = p.getTotalArmorValue();
        NBTTagCompound tag = k.getEntityData();
        if (Math.abs(tag.getFloat(MIRROR_W) - w) > 0.01 || tag.getInteger(MIRROR_A) != a) {
            tag.setFloat(MIRROR_W, w);
            tag.setInteger(MIRROR_A, a);
            ((WorldServer) k.worldObj).func_147487_a("mobSpell", k.posX, k.posY + 2, k.posZ, 30, 1.5, 1.5, 1.5, 1);
            k.worldObj.playSoundAtEntity(k, "random.orb", 1.0F, 0.5F);
        }
        tag.setFloat(MAX_POWER, Math.max(tag.getFloat(MAX_POWER), power(w, a)));
    }

    /** Карот бьёт как оружие игрока и держит удар как его броня. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        Entity src = e.source.getEntity();
        if (src instanceof EntityKarot && src.getEntityData().hasKey(MIRROR_W)) {
            e.ammount *= Math.max(0.2F, Math.min(3.0F, src.getEntityData().getFloat(MIRROR_W) / MIRROR_REF));
        } else if (e.entityLiving instanceof EntityKarot && e.entityLiving.getEntityData().hasKey(MIRROR_A)) {
            e.ammount *= 1 - Math.min(0.8F, e.entityLiving.getEntityData().getInteger(MIRROR_A) * 0.04F);
        }
    }
}
