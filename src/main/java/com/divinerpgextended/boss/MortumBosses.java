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
 * <li>Демон сумерек - маг. Пробить его можно только фазером (остальное - x{@link #NON_PHASER}); рядом с ним у луков рвётся
 * тетива. Вместо снарядов Divine (тошнота) - заклинания по очереди каждые {@link #SPELL} тиков: руны под ногами цели
 * взрываются через {@link #RUNE_FUSE} тиков (уходи), "блинк" - переносится по двору и бьёт тремя теневыми снарядами.
 * Дилемма: три фазы; на 2/3 и 1/3 здоровья он замирает ({@link #OFFER} тиков, неуязвим) и
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
    public static final float NON_PHASER = 0.1F, RUNE_DMG = 10;
    public static final int SPELL = 80, RUNE_FUSE = 30, RUNES = 3, BOLTS = 3;
    /** Руны на взводе: {мир, x, y, z, тик взрыва, демон}. */
    private static final List<Object[]> RUNE_LIST = new java.util.ArrayList<Object[]>();
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
        EntityPlayer target = d.worldObj.getClosestVulnerablePlayerToEntity(d, 40);
        if (target != null && d.ticksExisted % SPELL == 0) {
            int spell = d.ticksExisted / SPELL % 2;
            if (spell == 0) runes(d, target);
            else blink(d, target);
        }
        if (phase >= 1 && d.ticksExisted % 300 == 0) summon(d, net.divinerpg.entities.twilight.EntityMortumDemon.class, phase >= 2 ? 3 : 2);
        if (phase >= 2 && d.ticksExisted % 200 == 0) {
            for (EntityPlayer p : players(d, 30)) p.addPotionEffect(new PotionEffect(Potion.blindness.id, 70, 0));
            d.worldObj.playSoundAtEntity(d, "mob.wither.ambient", 2.0F, 0.4F);
            summon(d, net.divinerpg.entities.twilight.EntitySoulSpider.class, 5);
        }
    }

    /** Руны вокруг цели: светятся RUNE_FUSE тиков, потом взрываются (урон и подброс тем, кто рядом; блоки целы). */
    public static int runes(EntityTwilightDemon d, EntityLivingBase t) {
        int n = 0;
        for (int i = 0; i < RUNES; i++) {
            double x = i == 0 ? t.posX : t.posX + (d.getRNG().nextDouble() - 0.5) * 7, z = i == 0 ? t.posZ : t.posZ + (d.getRNG().nextDouble() - 0.5) * 7;
            RUNE_LIST.add(new Object[] { d.worldObj, x, t.posY, z, d.worldObj.getTotalWorldTime() + RUNE_FUSE, d });
            n++;
        }
        d.worldObj.playSoundAtEntity(t, "mob.wither.idle", 0.7F, 1.6F);
        return n;
    }

    /** Блинк: на случайное место двора (в пределах привязи) и три теневых снаряда в цель. */
    public static int blink(EntityTwilightDemon d, EntityLivingBase t) {
        int[] h = LairSummons.home(d);
        WorldServer ws = (WorldServer) d.worldObj;
        ws.func_147487_a("largesmoke", d.posX, d.posY + 1.5, d.posZ, 30, 0.6, 1, 0.6, 0.02);
        if (h != null) {
            for (int a = 0; a < 8; a++) {
                double ang = d.getRNG().nextDouble() * Math.PI * 2, r = 6 + d.getRNG().nextDouble() * (h[3] - 8);
                double x = h[0] + 0.5 + Math.cos(ang) * r, z = h[2] + 0.5 + Math.sin(ang) * r;
                double ox = d.posX, oy = d.posY, oz = d.posZ;
                d.setPosition(x, h[1], z);
                if (d.worldObj.getCollidingBoundingBoxes(d, d.boundingBox).isEmpty()) {
                    d.setPositionAndUpdate(x, h[1], z);
                    break;
                }
                d.setPosition(ox, oy, oz);
            }
        }
        d.worldObj.playSoundAtEntity(d, "mob.endermen.portal", 1.5F, 0.5F);
        int n = 0;
        for (int i = 0; i < BOLTS; i++) {
            com.divinerpgextended.entity.EntityShadowBolt b = new com.divinerpgextended.entity.EntityShadowBolt(d.worldObj, d);
            double dx = t.posX - d.posX, dy = t.posY + t.height / 2 - (d.posY + d.getEyeHeight()), dz = t.posZ - d.posZ;
            double ang = Math.toRadians((i - 1) * 8);
            double rx = dx * Math.cos(ang) - dz * Math.sin(ang), rz = dx * Math.sin(ang) + dz * Math.cos(ang);
            b.setThrowableHeading(rx, dy, rz, 1.3F, 0);
            if (d.worldObj.spawnEntityInWorld(b)) n++;
        }
        return n;
    }

    /** Руны: искры на взводе, взрыв по сроку. */
    @SubscribeEvent
    public void onWorldTick(cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent e) {
        if (e.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END || e.world.isRemote || RUNE_LIST.isEmpty()) return;
        long now = e.world.getTotalWorldTime();
        for (java.util.Iterator<Object[]> it = RUNE_LIST.iterator(); it.hasNext();) {
            Object[] r = it.next();
            if (r[0] != e.world) continue;
            double x = (Double) r[1], y = (Double) r[2], z = (Double) r[3];
            WorldServer ws = (WorldServer) e.world;
            if (now < (Long) r[4]) {
                if (now % 4 == 0) ws.func_147487_a("witchMagic", x, y + 0.1, z, 12, 0.9, 0.02, 0.9, 0);
                continue;
            }
            it.remove();
            ws.func_147487_a("largeexplode", x, y + 0.5, z, 3, 0.5, 0.2, 0.5, 0);
            e.world.playSoundEffect(x, y, z, "random.explode", 1.0F, 1.3F);
            Entity src = (Entity) r[5];
            for (EntityPlayer p : players(e.world, x, y, z, 2.2)) {
                p.attackEntityFrom(DamageSource.causeIndirectMagicDamage(src, src), RUNE_DMG);
                p.addVelocity(0, 0.7, 0);
                p.velocityChanged = true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayer> players(net.minecraft.world.World w, double x, double y, double z, double r) {
        return w.getEntitiesWithinAABB(EntityPlayer.class, net.minecraft.util.AxisAlignedBB.getBoundingBox(x - r, y - 1, z - r, x + r, y + 3, z + r));
    }

    /** Снаряды Divine (тошнота) Демону не нужны - у него заклинания. */
    @SubscribeEvent
    public void onJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
        if (!e.world.isRemote && e.entity instanceof net.divinerpg.entities.twilight.projectile.EntityTwilightDemonShot) e.setCanceled(true);
    }

    /** Рядом с Демоном у луков рвётся тетива. */
    @SubscribeEvent
    public void onLoose(net.minecraftforge.event.entity.player.ArrowLooseEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (p.worldObj.isRemote) return;
        if (p.worldObj.getEntitiesWithinAABB(EntityTwilightDemon.class, p.boundingBox.expand(40, 20, 40)).isEmpty()) return;
        e.setCanceled(true);
        p.worldObj.playSoundAtEntity(p, "random.break", 0.8F, 1.4F);
        ((WorldServer) p.worldObj).func_147487_a("witchMagic", p.posX, p.posY + 1.2, p.posZ, 10, 0.3, 0.3, 0.3, 0.05);
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
        EntityPlayer near = d.worldObj.getClosestPlayerToEntity(d, 40);
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(d, near != null ? DamageSource.causePlayerDamage(near) : DamageSource.generic));
        d.setDead();
    }

    /** Халитовое оружие с Демона не падает (его делают, а не выбивают). Полная победа (3-я фаза) - статуя и больше душ. */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        if (e.entityLiving instanceof EntityTwilightDemon) {
            for (java.util.Iterator<EntityItem> it = e.drops.iterator(); it.hasNext();) {
                net.minecraft.item.Item i = it.next().getEntityItem().getItem();
                if (i == TwilightItemsWeapons.haliteBlitz || i == TwilightItemsWeapons.haliteBow || i == TwilightItemsWeapons.halitePhaser) it.remove();
            }
            if (phase(e.entityLiving) >= 2) {
                drop(e, new ItemStack(TwilightItemsOther.mortumSoul, 16));
                drop(e, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.divinerpg.utils.blocks.VanillaBlocks.twilightDemonStatue)));
            }
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

    /** Карот бьёт как оружие игрока и держит удар как его броня. Демона сумерек пробивает только фазер. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        if (e.entityLiving instanceof EntityTwilightDemon && e.source != DamageSource.outOfWorld
            && com.divinerpgextended.perk.RangedRoles.kind(e.source.getSourceOfDamage()) != com.divinerpgextended.perk.RangedRoles.PHASER) {
            e.ammount *= NON_PHASER;
            ((WorldServer) e.entityLiving.worldObj).func_147487_a("witchMagic", e.entityLiving.posX, e.entityLiving.posY + 1.5, e.entityLiving.posZ, 8, 0.5, 0.8, 0.5, 0.05);
        }
        Entity src = e.source.getEntity();
        if (src instanceof EntityKarot && src.getEntityData().hasKey(MIRROR_W)) {
            e.ammount *= Math.max(0.2F, Math.min(3.0F, src.getEntityData().getFloat(MIRROR_W) / MIRROR_REF));
        } else if (e.entityLiving instanceof EntityKarot && e.entityLiving.getEntityData().hasKey(MIRROR_A)) {
            e.ammount *= 1 - Math.min(0.8F, e.entityLiving.getEntityData().getInteger(MIRROR_A) * 0.04F);
        }
    }
}
