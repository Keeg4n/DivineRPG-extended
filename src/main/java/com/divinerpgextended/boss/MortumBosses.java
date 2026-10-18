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
        MAX_POWER = "dreMirrorMax", STAGE = "dreStage", SPIRE = "dreSpire", FOCUS_OUT = "dreFocusOut", HINT = "dreHint";
    /**
     * Бой в башне: на этих долях здоровья Демон уходит этажом выше (двор - зал - библиотека - лаборатория - покои - верхний
     * этаж - коронный зал); этажи от пола площадки.
     */
    public static final float[] STAGE_AT = { 0.85F, 0.72F, 0.60F, 0.48F, 0.36F, 0.24F };
    public static final int[] STAGE_FLOOR = { 0, 7, 14, 21, 28, 35 };
    /** Фокус в короне разбит - через столько тиков Демон зажигает его снова. */
    public static final int FOCUS_RELIGHT = 600;
    public static final float GUST = 1.4F;
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
        int stage = tag.getInteger(STAGE);
        int[] sp = spire(d);
        if (sp != null && stage < STAGE_AT.length && f <= STAGE_AT[stage]) {
            climb(d, stage + 1);
            return;
        }
        if (phase == 0 && f <= 2 / 3F || phase == 1 && f <= 1 / 3F) {
            tag.setInteger(OFFER_LEFT, OFFER);
            say(d, "dre.chat.demon_offer");
            d.worldObj.playSoundAtEntity(d, "mob.wither.idle", 2.0F, 0.5F);
            return;
        }
        if (sp != null && stage == STAGE_AT.length) crownTick(d, sp);
        EntityPlayer target = d.worldObj.getClosestVulnerablePlayerToEntity(d, 40);
        if (target != null && d.ticksExisted % SPELL == 0) {
            int spell = d.ticksExisted / SPELL % 2;
            if (spell == 0) runes(d, target);
            else blink(d, target, BOLTS + (stage >= 2 ? 2 : 0));
        }
        if ((phase >= 1 || stage >= 1) && d.ticksExisted % 300 == 0) summon(d, net.divinerpg.entities.twilight.EntityMortumDemon.class, phase >= 2 || stage >= 5 ? 3 : 2);
        if (stage >= 3 && d.ticksExisted % 200 == 100) fumes(d);
        if (stage >= 4 && d.ticksExisted % 240 == 60) gust(d);
        if ((phase >= 2 || stage >= 5) && d.ticksExisted % 200 == 0) {
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

    // ---------------- башня: бой по этажам

    /** Центр арены-башни {x, z} (null - Демон вызван не на башне). Запоминается при первом ходе на арене. */
    public static int[] spire(EntityTwilightDemon d) {
        NBTTagCompound tag = d.getEntityData();
        int[] s = tag.getIntArray(SPIRE);
        if (s.length == 2) return s;
        int[] h = LairSummons.home(d);
        if (h == null || tag.getInteger(STAGE) > 0 || com.divinerpgextended.world.twilight.TwilightWorld.of(d.worldObj.provider.dimensionId) != com.divinerpgextended.world.twilight.TwilightWorld.MORTUM)
            return null;
        if (com.divinerpgextended.world.twilight.MortumArenas.typeAt(d.worldObj, h) != com.divinerpgextended.world.twilight.MortumArenas.Type.DEMON) return null;
        s = new int[] { h[0], h[2] };
        tag.setIntArray(SPIRE, s);
        return s;
    }

    /** Пол этажа боя (stage 1..6) в мире. */
    public static int floorOf(int stage) {
        return com.divinerpgextended.world.twilight.MortumArenas.SPIRE_TOP + STAGE_FLOOR[stage - 1];
    }

    /** Игрок на этаже Демона: внутри башни и между полом и потолком этажа. */
    public static boolean onFloor(EntityTwilightDemon d, EntityPlayer p) {
        int stage = d.getEntityData().getInteger(STAGE);
        int[] sp = d.getEntityData().getIntArray(SPIRE);
        if (stage <= 0 || sp.length != 2) return true;
        double tx = sp[0] + com.divinerpgextended.world.twilight.MortumArenas.SPIRE_TX + 0.5, tz = sp[1] + com.divinerpgextended.world.twilight.MortumArenas.SPIRE_TZ + 0.5;
        double dx = p.posX - tx, dz = p.posZ - tz;
        int fl = floorOf(stage);
        return dx * dx + dz * dz <= (com.divinerpgextended.world.twilight.MortumArenas.SPIRE_TR + 0.5) * (com.divinerpgextended.world.twilight.MortumArenas.SPIRE_TR + 0.5)
            && p.posY >= fl && p.posY <= fl + 7;
    }

    /** Подняться на этаж stage: дым, перенос на свободное место этажа, привязь к этажу, слово игрокам. */
    public static boolean climb(EntityTwilightDemon d, int stage) {
        int[] sp = spire(d);
        if (sp == null) return false;
        NBTTagCompound tag = d.getEntityData();
        tag.setInteger(STAGE, stage);
        WorldServer ws = (WorldServer) d.worldObj;
        ws.func_147487_a("largesmoke", d.posX, d.posY + 2, d.posZ, 60, 1.2, 2, 1.2, 0.05);
        int tx = sp[0] + com.divinerpgextended.world.twilight.MortumArenas.SPIRE_TX, tz = sp[1] + com.divinerpgextended.world.twilight.MortumArenas.SPIRE_TZ;
        int fl = floorOf(stage);
        int[][] spots = { { 0, 3 }, { 0, -3 }, { 3, 0 }, { -3, 0 }, { 2, 2 }, { -2, -2 }, { 0, 0 } };
        int[] at = spots[0];
        double ox = d.posX, oy = d.posY, oz = d.posZ;
        for (int[] s : spots) {
            d.setPosition(tx + s[0] + 0.5, fl + 1, tz + s[1] + 0.5);
            if (d.worldObj.getCollidingBoundingBoxes(d, d.boundingBox).isEmpty()) {
                at = s;
                break;
            }
        }
        d.setPosition(ox, oy, oz);
        d.motionX = d.motionY = d.motionZ = 0;
        d.fallDistance = 0;
        d.getNavigator().clearPathEntity();
        d.setPositionAndUpdate(tx + at[0] + 0.5, fl + 1, tz + at[1] + 0.5);
        LairSummons.leash(d, tx + at[0], fl + 1, tz + at[1], com.divinerpgextended.world.twilight.MortumArenas.SPIRE_TR - 1);
        ws.func_147487_a("portal", d.posX, d.posY + 2, d.posZ, 60, 1, 2, 1, 0.6);
        d.worldObj.playSoundAtEntity(d, "mob.endermen.portal", 2.0F, 0.4F);
        if (stage == STAGE_AT.length) {
            relight(d, sp);
            say(d, "dre.chat.demon_crown");
        } else say(d, stage == 1 ? "dre.chat.demon_tower" : "dre.chat.demon_climb");
        return true;
    }

    static boolean focusLit(net.minecraft.world.World w, int[] sp) {
        int[] f = com.divinerpgextended.world.twilight.MortumArenas.crownFocus();
        return w.getBlock(sp[0] + f[0], f[1], sp[1] + f[2]) == com.divinerpgextended.world.twilight.MortumArenas.focusBlock();
    }

    /** Зажечь главный фокус на пьедестале коронного зала. */
    public static void relight(EntityTwilightDemon d, int[] sp) {
        int[] f = com.divinerpgextended.world.twilight.MortumArenas.crownFocus();
        d.worldObj.setBlock(sp[0] + f[0], f[1], sp[1] + f[2], com.divinerpgextended.world.twilight.MortumArenas.focusBlock(), 0, 3);
        d.getEntityData().setInteger(FOCUS_OUT, 0);
        ((WorldServer) d.worldObj).func_147487_a("witchMagic", sp[0] + f[0] + 0.5, f[1] + 0.5, sp[1] + f[2] + 0.5, 40, 0.5, 0.5, 0.5, 0.1);
    }

    /** Коронный зал: от горящего фокуса к Демону тянется нить; разбитый фокус Демон через FOCUS_RELIGHT тиков зажигает снова. */
    private void crownTick(EntityTwilightDemon d, int[] sp) {
        NBTTagCompound tag = d.getEntityData();
        int[] f = com.divinerpgextended.world.twilight.MortumArenas.crownFocus();
        double fx = sp[0] + f[0] + 0.5, fy = f[1] + 0.5, fz = sp[1] + f[2] + 0.5;
        if (focusLit(d.worldObj, sp)) {
            if (d.ticksExisted % 4 == 0) {
                WorldServer ws = (WorldServer) d.worldObj;
                for (int i = 1; i < 6; i++) {
                    double k = i / 6.0;
                    ws.func_147487_a("witchMagic", fx + (d.posX - fx) * k, fy + (d.posY + 2 - fy) * k, fz + (d.posZ - fz) * k, 1, 0, 0, 0, 0);
                }
            }
            return;
        }
        int out = tag.getInteger(FOCUS_OUT) + 1;
        tag.setInteger(FOCUS_OUT, out);
        if (out == 1) say(d, "dre.chat.demon_focus_broken");
        if (out >= FOCUS_RELIGHT) {
            relight(d, sp);
            say(d, "dre.chat.demon_focus_relit");
            d.worldObj.playSoundAtEntity(d, "mob.wither.idle", 2.0F, 0.6F);
        }
    }

    /** Ядовитые пары (лаборатория и выше): яд и тошнота тем, кто рядом. */
    public static int fumes(EntityTwilightDemon d) {
        int n = 0;
        ((WorldServer) d.worldObj).func_147487_a("mobSpell", d.posX, d.posY + 1, d.posZ, 80, 3.5, 1.2, 3.5, 0.5);
        for (EntityPlayer p : players(d, 7)) {
            p.addPotionEffect(new PotionEffect(Potion.poison.id, 80, 0));
            p.addPotionEffect(new PotionEffect(Potion.confusion.id, 100, 0));
            n++;
        }
        d.worldObj.playSoundAtEntity(d, "mob.witch.idle", 1.5F, 0.5F);
        return n;
    }

    /** Порыв (покои и выше): отбрасывает от Демона и вверх - к окнам и лестнице. */
    public static int gust(EntityTwilightDemon d) {
        int n = 0;
        ((WorldServer) d.worldObj).func_147487_a("cloud", d.posX, d.posY + 1, d.posZ, 60, 2.5, 0.8, 2.5, 0.3);
        for (EntityPlayer p : players(d, 8)) {
            double dx = p.posX - d.posX, dz = p.posZ - d.posZ, l = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            p.addVelocity(dx / l * GUST, 0.45, dz / l * GUST);
            p.velocityChanged = true;
            n++;
        }
        d.worldObj.playSoundAtEntity(d, "mob.enderdragon.wings", 2.0F, 0.6F);
        return n;
    }

    /** Блинк: на случайное место двора (в пределах привязи) и три теневых снаряда в цель. */
    public static int blink(EntityTwilightDemon d, EntityLivingBase t) {
        return blink(d, t, BOLTS);
    }

    /** Блинк с bolts теневыми снарядами веером. */
    public static int blink(EntityTwilightDemon d, EntityLivingBase t, int bolts) {
        int[] h = LairSummons.home(d);
        WorldServer ws = (WorldServer) d.worldObj;
        ws.func_147487_a("largesmoke", d.posX, d.posY + 1.5, d.posZ, 30, 0.6, 1, 0.6, 0.02);
        if (h != null) {
            for (int a = 0; a < 8; a++) {
                double ang = d.getRNG().nextDouble() * Math.PI * 2, r = Math.min(6, h[3] - 2) + d.getRNG().nextDouble() * Math.max(0, h[3] - 8);
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
        for (int i = 0; i < bolts; i++) {
            com.divinerpgextended.entity.EntityShadowBolt b = new com.divinerpgextended.entity.EntityShadowBolt(d.worldObj, d);
            double dx = t.posX - d.posX, dy = t.posY + t.height / 2 - (d.posY + d.getEyeHeight()), dz = t.posZ - d.posZ;
            double ang = Math.toRadians((i - (bolts - 1) / 2.0) * 8);
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

    /**
     * В башне: Демона не достать, пока стоишь не на его этаже (снаружи, в окна, с лестницы); в коронном зале - пока горит
     * главный фокус. Удар во время предложения - отказ: бой продолжается, фаза растёт. Сам удар не проходит.
     */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (!(e.entityLiving instanceof EntityTwilightDemon) || e.entityLiving.worldObj.isRemote) return;
        EntityTwilightDemon dm = (EntityTwilightDemon) e.entityLiving;
        NBTTagCompound tag = e.entityLiving.getEntityData();
        int stage = tag.getInteger(STAGE);
        int[] sp = tag.getIntArray(SPIRE);
        if (stage > 0 && sp.length == 2 && e.source != DamageSource.outOfWorld && tag.getInteger(OFFER_LEFT) <= 0) {
            EntityPlayer by = e.source.getEntity() instanceof EntityPlayer ? (EntityPlayer) e.source.getEntity() : null;
            String why = by != null && !onFloor(dm, by) ? "dre.chat.demon_floor" : stage == STAGE_AT.length && focusLit(dm.worldObj, sp) ? "dre.chat.demon_focus" : null;
            if (why != null) {
                e.setCanceled(true);
                ((WorldServer) dm.worldObj).func_147487_a("witchMagic", dm.posX, dm.posY + 2, dm.posZ, 12, 0.6, 1, 0.6, 0.05);
                long now = dm.worldObj.getTotalWorldTime();
                if (by != null && now - tag.getLong(HINT) > 100) {
                    tag.setLong(HINT, now);
                    ChatComponentTranslation m = new ChatComponentTranslation(why);
                    m.getChatStyle().setColor(EnumChatFormatting.DARK_PURPLE).setItalic(true);
                    by.addChatMessage(m);
                }
                return;
            }
        }
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
