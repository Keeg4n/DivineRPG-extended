package com.divinerpgextended.perk;

import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * Обморожение ледяным оружием (мечи и молот Айсики, Фроссивенс, Морозный меч; ледяные луки, пушки и снежный сюрикен):
 * первый удар или попадание по мобу замедляет, второй - сильнее, третий - моб вмерзает в лёд на {@link #FREEZE} тиков:
 * стоит, не бьёт и не стреляет, вокруг него ледяная глыба. Ступени копятся, если удары идут не реже раза в
 * {@link #WINDOW} тиков, и не чаще раза в {@link #GAP} (скорострельное не вмораживает мгновенно). Оттаявший моб
 * {@link #IMMUNE} тиков не вмерзает снова (только сильное замедление). Боссов только замедляет, игроков и питомцев не
 * трогает.
 */
public class FrostBite {

    public static final int WINDOW = 80, GAP = 8, FREEZE = 50, IMMUNE = 100, SLOW_TICKS = 60;
    private static final UUID STOP = UUID.fromString("5d0c1f3e-6b8a-4e8f-9f7a-3c1d2e4f5a61");
    private static final String STACKS = "dreFrost", LAST = "dreFrostLast", UNTIL = "dreFrozenUntil", IMMUNE_UNTIL = "dreFrostImmune";

    private static Set<Item> melee, ranged;

    private static synchronized void lists() {
        if (melee != null) return;
        melee = new HashSet<Item>(Arrays.asList(IceikaItems.icicleBane, IceikaItems.icineSword, IceikaItems.enderice, IceikaItems.snowSlash,
            IceikaItems.glacierSword, IceikaItems.frostkingSword, IceikaItems.frozenMaul, IceikaItems.frossivence, VanillaItemsWeapons.frostSword));
        ranged = new HashSet<Item>(Arrays.asList(IceikaItems.icicleBow, IceikaItems.snowstormBow, IceikaItems.frostclawCannon, IceikaItems.fractiteCannon,
            IceikaItems.snowflakeShuriken, VanillaItemsWeapons.frostCannon));
    }

    /** Ледяное оружие (для подсказки). */
    public static boolean iceWeapon(Item i) {
        lists();
        return i != null && (melee.contains(i) || ranged.contains(i));
    }

    /** Урон нанесло ледяное оружие: удар им или выстрел, пока ледяное стрелковое в руке. */
    static boolean byIce(DamageSource s) {
        if (!(s.getEntity() instanceof EntityPlayer)) return false;
        ItemStack h = ((EntityPlayer) s.getEntity()).getHeldItem();
        if (h == null) return false;
        lists();
        return s.getSourceOfDamage() == s.getEntity() ? melee.contains(h.getItem()) : ranged.contains(h.getItem());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.entityLiving;
        if (!Config.iceFreeze || e.isCanceled() || t.worldObj.isRemote || e.ammount <= 0 || !(t instanceof EntityLiving)) return;
        if (t instanceof EntityTameable && ((EntityTameable) t).isTamed()) return; // питомцев не морозим
        if (!byIce(e.source)) return;
        hit(t, t.worldObj.getTotalWorldTime());
    }

    /** Один ледяной удар по мобу в момент now: ступень после удара (1, 2) или 3 - вморожен. */
    public static int hit(EntityLivingBase t, long now) {
        NBTTagCompound d = t.getEntityData();
        if (now < d.getLong(UNTIL)) return 3;
        boolean had = d.hasKey(LAST);
        long last = d.getLong(LAST);
        if (had && now >= last && now - last < GAP) return d.getInteger(STACKS);
        int stacks = had && now >= last && now - last <= WINDOW ? d.getInteger(STACKS) + 1 : 1;
        d.setLong(LAST, now);
        if (stacks >= 3 && !(t instanceof IBossDisplayData) && now >= d.getLong(IMMUNE_UNTIL)) {
            freeze(t, now);
            d.setInteger(STACKS, 0);
            return 3;
        }
        stacks = Math.min(stacks, 2); // боссы и только что оттаявшие - не больше сильного замедления
        d.setInteger(STACKS, stacks);
        t.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, SLOW_TICKS, stacks == 1 ? 0 : 2));
        puff(t, stacks * 4);
        return stacks;
    }

    /** Вморозить сразу (Серенада льда): боссов и только что оттаявших - сильное замедление. */
    public static void freezeNow(EntityLivingBase t, long now) {
        NBTTagCompound d = t.getEntityData();
        if (now < d.getLong(UNTIL)) return;
        if (t instanceof IBossDisplayData || now < d.getLong(IMMUNE_UNTIL)) {
            t.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, SLOW_TICKS, 2));
            puff(t, 8);
            return;
        }
        freeze(t, now);
        d.setInteger(STACKS, 0);
    }

    private static void freeze(EntityLivingBase t, long now) {
        NBTTagCompound d = t.getEntityData();
        d.setLong(UNTIL, now + FREEZE);
        d.setLong(IMMUNE_UNTIL, now + FREEZE + IMMUNE);
        IAttributeInstance sp = t.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        if (sp != null && sp.getModifier(STOP) == null) sp.applyModifier(new AttributeModifier(STOP, "dre.frozen", -1.0, 2));
        hold(t);
        t.worldObj.playSoundAtEntity(t, "dig.glass", 1.0F, 1.3F);
        puff(t, 24);
        if (!t.worldObj.isRemote) ColdHandler.NET.sendToAllAround(new Fx(t.getEntityId(), FREEZE), new TargetPoint(t.dimension, t.posX, t.posY, t.posZ, 80));
    }

    /** Вмороженный: на месте, без цели. */
    private static void hold(EntityLivingBase t) {
        t.motionX = 0;
        t.motionZ = 0;
        if (t.motionY > 0) t.motionY = 0;
        if (t instanceof EntityLiving) {
            ((EntityLiving) t).setAttackTarget(null);
            ((EntityLiving) t).getNavigator().clearPathEntity();
        }
        if (t instanceof EntityCreature) ((EntityCreature) t).setTarget(null);
    }

    private static void puff(EntityLivingBase t, int n) {
        if (t.worldObj instanceof WorldServer)
            ((WorldServer) t.worldObj).func_147487_a("snowshovel", t.posX, t.posY + t.height * 0.6, t.posZ, n, t.width * 0.4, t.height * 0.3, t.width * 0.4, 0.02);
    }

    /** Вморожен ли моб сейчас. */
    public static boolean frozen(EntityLivingBase t) {
        return t.getEntityData().getLong(UNTIL) > t.worldObj.getTotalWorldTime();
    }

    /** Тик моба: держать вмороженного, оттаять в срок. */
    public static void tick(EntityLivingBase t, long now) {
        NBTTagCompound d = t.getEntityData();
        if (!d.hasKey(UNTIL)) return;
        if (now < d.getLong(UNTIL)) {
            hold(t);
            return;
        }
        d.removeTag(UNTIL);
        IAttributeInstance sp = t.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        AttributeModifier m = sp == null ? null : sp.getModifier(STOP);
        if (m != null) sp.removeModifier(m);
        t.worldObj.playSoundAtEntity(t, "random.glass", 0.6F, 1.5F);
        puff(t, 16);
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (!e.entityLiving.worldObj.isRemote && e.entityLiving instanceof EntityLiving) tick(e.entityLiving, e.entityLiving.worldObj.getTotalWorldTime());
    }

    /** Вмороженный не бьёт и не стреляет: его удары и снаряды не ранят. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent e) {
        Entity a = e.source.getEntity();
        if (a instanceof EntityLiving && !a.worldObj.isRemote && frozen((EntityLivingBase) a)) e.setCanceled(true);
    }

    // ---------------- клиент: ледяная глыба ----------------

    /** Вмороженные на клиенте: номер сущности -> когда оттает (мс). Рисует client.FrostRender. */
    public static final Map<Integer, Long> CLIENT = new ConcurrentHashMap<Integer, Long>();

    public static void register() {
        ColdHandler.NET.registerMessage(FxHandler.class, Fx.class, 22, Side.CLIENT);
    }

    public static class Fx implements IMessage {

        int id, ticks;

        public Fx() {}

        Fx(int id, int ticks) {
            this.id = id;
            this.ticks = ticks;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            id = buf.readInt();
            ticks = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(id);
            buf.writeInt(ticks);
        }
    }

    public static class FxHandler implements IMessageHandler<Fx, IMessage> {

        @Override
        public IMessage onMessage(Fx msg, MessageContext ctx) {
            CLIENT.put(msg.id, System.currentTimeMillis() + msg.ticks * 50L);
            return null;
        }
    }
}
