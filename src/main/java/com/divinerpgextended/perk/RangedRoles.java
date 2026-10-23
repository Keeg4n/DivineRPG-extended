package com.divinerpgextended.perk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.divinerpg.entities.vanilla.projectile.EntityShooterBullet;
import net.divinerpg.utils.items.TwilightItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.world.twilight.TwilightCrafts;
import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Роли сумеречного дальнобоя (снаряды Divine у блица, фазера и слайсера одни - различаем по оружию в руке при выстреле):
 * <ul>
 * <li>лук - дальность и точность (без изменений);</li>
 * <li>блиц - кибер-дробовик: {@link #PELLETS} снарядов веером в полную силу ({@link #PELLET_DMG}), по подручным боссов x2,
 * но редко - перезарядка {@link #BLITZ_COOLDOWN} тиков; против толпы и вблизи: дробь летит {@link #BLITZ_LIFE} тиков
 * (~10 блоков), боссов не сносит; свой звук выстрела; тратит пыль своего мира ({@link #setupDust}), как и фазер;</li>
 * <li>фазер - пробой: снаряд проходит насквозь до {@link #PIERCE} целей, по частям голема x2;</li>
 * <li>фазеры и блицы не ломаются.</li>
 * <li>слайсер - рикошет до {@link #RICOCHET} целей подряд, перерезает лиану голема, сбивает замах Короля-воина и
 * издалека заряжает ветряные тотемы (как снежки в кристаллы дракона).</li>
 * </ul>
 * Колчаны-аксессуары (как в Террарии; падают со стрелков - {@link #DROP_CHANCE}: лучники - колчан (Вечный лучник - наверняка), маг - подсумок, колдун -
 * перевязь): бездонный колчан - бесконечные стрелы лукам (лук натягивается и стреляет без стрел, ничего не кладя в
 * инвентарь; клиенту о колчане говорит {@link QuiverSync}) и +10% урона стрелами; подсумок пыли - +15% урона блица и
 * фазера (они и так бесконечные); перевязь метателя - слайсер возвращается в руку, +1 цель рикошета.
 */
public class RangedRoles {

    public static final String KIND = "dreRanged", GEN = "dreRangedGen";
    public static final int BLITZ = 1, PHASER = 2, SLICER = 3;
    public static final int PELLETS = 5, PIERCE = 3, RICOCHET = 3;
    public static final float PELLET_DMG = 1.0F, ADD_BONUS = 2.0F;
    /** Перезарядка блица: задержка Divine (в её единицах, x4 тика) и она же в тиках - выстрел в секунду. */
    public static final int BLITZ_DELAY = 5, BLITZ_COOLDOWN = BLITZ_DELAY * 4;
    public static final String BLITZ_SOUND = "divinerpgextended:blitz_shot";
    /** Дробь живёт столько тиков (дальность ~10 блоков); от отброса дробью остаётся доля KNOCK_KEEP - ноль: блиц не отбрасывает вовсе. */
    public static final int BLITZ_LIFE = 7;
    public static final double KNOCK_KEEP = 0;
    /** Боковые дробины по боссу: доля урона (иначе пять дробин в упор выносили босса, а сет Мортума лечил за каждую). */
    public static final float BOSS_SIDE = 0.3F;
    private static final List<Entity> BLITZ_SHOTS = new ArrayList<Entity>();
    /** Скорость жертвы до первой дробины за тик: в конце тика отброс урезается. */
    private static final java.util.Map<EntityLivingBase, double[]> KNOCK = new java.util.HashMap<EntityLivingBase, double[]>();
    /** Боевые бонусы колчанов: стрелы +10%, блиц и фазер +15%, слайсер +1 цель рикошета. */
    public static final float QUIVER_DMG = 1.10F, POUCH_DMG = 1.15F;
    private static final double SPREAD = 9; // градусов между дробинами

    /** Летящие слайсеры - для подзарядки тотемов. */
    private static final List<Entity> TOTEM_SHOTS = new ArrayList<Entity>();
    public static final String QUIVER_TAG = "dreQuiver";

    public static int kind(ItemStack held) {
        if (held == null) return 0;
        Item i = held.getItem();
        for (TwilightWorld w : TwilightWorld.values()) {
            Item[] wp = TwilightCrafts.weapons(w);
            if (i == wp[3]) return BLITZ;
            if (i == wp[2]) return PHASER;
        }
        if (i == TwilightItemsWeapons.haliteBlitz) return BLITZ; // халитовое - с Демона сумерек, не из крафта миров
        if (i == TwilightItemsWeapons.halitePhaser) return PHASER;
        if (i == TwilightItemsWeapons.edenSlicer || i == TwilightItemsWeapons.wildwoodSlicer || i == TwilightItemsWeapons.apalachiaSlicer
            || i == TwilightItemsWeapons.skythernSlicer || i == TwilightItemsWeapons.mortumSlicer) return SLICER;
        return 0;
    }

    public static boolean wears(EntityPlayer p, com.divinerpgextended.trinket.Trinket t) {
        return com.divinerpgextended.accessory.Accessories.wears(p, t);
    }

    /** Пыль-патрон фазеров и блицев: у Divine она задана только блицам, фазеру - пыль блица того же мира. */
    private static final java.util.Map<Item, Item> DUST = new java.util.HashMap<Item, Item>();
    /** Оружие, которому на этот тик патрон выставлен под игрока (в конце тика - обратно пыль). */
    private static final java.util.Set<Item> ARMED = new java.util.HashSet<Item>();
    /** Пыль из слотов боеприпасов: {игрок, оружие, CanShootTime до клика, пыль} - списать, если выстрел был. */
    private static final List<Object[]> FROM_SLOTS = new ArrayList<Object[]>();
    static final String SHOOT_TIME = "CanShootTime";
    private static java.lang.reflect.Field ammoField;

    private static java.lang.reflect.Field ammoField() throws Exception {
        if (ammoField == null) {
            ammoField = net.divinerpg.items.base.ItemProjectileShooter.class.getDeclaredField("ammo");
            ammoField.setAccessible(true);
        }
        return ammoField;
    }

    /**
     * При запуске (сервер и клиент): фазеры и блицы стреляют пылью своего мира. Divine проверяет и тратит патрон, только
     * если он задан у оружия, - фазерам задаём пыль блица того же мира. @return сколько оружия на пыли
     */
    public static int setupDust() {
        DUST.clear();
        try {
            java.lang.reflect.Field f = ammoField();
            for (TwilightWorld w : TwilightWorld.values()) {
                Item[] wp = TwilightCrafts.weapons(w);
                Object d = f.get(wp[3]);
                if (!(d instanceof Item)) continue;
                DUST.put(wp[3], (Item) d);
                DUST.put(wp[2], (Item) d);
            }
            Object hd = f.get(TwilightItemsWeapons.haliteBlitz);
            if (hd instanceof Item) {
                DUST.put(TwilightItemsWeapons.haliteBlitz, (Item) hd);
                DUST.put(TwilightItemsWeapons.halitePhaser, (Item) hd);
            }
            for (java.util.Map.Entry<Item, Item> en : DUST.entrySet()) f.set(en.getKey(), en.getValue());
        } catch (Exception ex) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Пыль фазеров и блицев: не удалось задать патрон Divine - " + ex);
        }
        tune();
        return DUST.size();
    }

    private static java.lang.reflect.Field shooterField(String name) throws Exception {
        java.lang.reflect.Field f = net.divinerpg.items.base.ItemProjectileShooter.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** Фазеры и блицы не ломаются (uses = -1, как у блицев Divine); блиц стреляет раз в секунду и без звука Divine - свой даёт {@link #onJoin}. */
    private static void tune() {
        for (Item i : DUST.keySet()) {
            boolean blitz = kind(new ItemStack(i)) == BLITZ;
            try {
                shooterField("uses").setInt(i, -1);
                i.setMaxDamage(0);
                if (blitz) {
                    shooterField("delay").setInt(i, BLITZ_DELAY);
                    shooterField("soundName").set(i, null);
                }
            } catch (Exception ex) {
                com.divinerpgextended.DivineRPGExtended.LOG.warn("Фазеры и блицы: не удалось настроить " + i.getUnlocalizedName() + " - " + ex);
            }
        }
    }

    /** Счётчик тиков Divine (по нему перезарядка оружия; null - нет такого). */
    static java.lang.reflect.Field tickerField() {
        try {
            return Class.forName("net.divinerpg.utils.events.Ticker").getField("tick");
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Счётчик Divine после 100000 тиков (~1,5 ч) сбрасывается в ноль, а записанное на оружии время следующего выстрела -
     * нет: блиц замолкал бы на полтора часа. Время дальше перезарядки - сбрасываем.
     */
    static void fixCooldown(ItemStack held) {
        if (!held.hasTagCompound()) return;
        java.lang.reflect.Field t = tickerField();
        try {
            if (t != null && held.getTagCompound().getLong(SHOOT_TIME) - t.getInt(null) > BLITZ_COOLDOWN) held.getTagCompound().setLong(SHOOT_TIME, 0);
        } catch (Exception ignored) {
            // нет счётчика - как у Divine
        }
    }

    /** Пыль-патрон этого фазера или блица (null - не они). */
    public static Item dust(Item weapon) {
        return DUST.get(weapon);
    }

    /** Бесконечные патроны фазеру и блицу: надет подсумок пыли или на оружии Бесконечность. */
    public static boolean infiniteDust(EntityPlayer p, ItemStack held) {
        return wears(p, com.divinerpgextended.trinket.Trinket.DUST_POUCH)
            || net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(INFINITY, held) > 0;
    }

    /**
     * Перед выстрелом: патрон оружия - под этого игрока (бесконечные - без патрона, иначе пыль). Divine смотрит на патрон
     * оружия в ту же минуту, сразу после клика; каждому клику предшествует своё событие - у каждого игрока свой расчёт. В
     * конце тика патрон возвращается ({@link #onServerTick}). Предметы игрока не трогаются.
     */
    static void arm(EntityPlayer p, ItemStack held) {
        Item d = DUST.get(held.getItem());
        if (d == null) return;
        if (held.getItemDamage() != 0) held.setItemDamage(0); // фазеры, поношенные до того, как перестали ломаться
        fixCooldown(held);
        boolean inf = infiniteDust(p, held);
        // пыль только в слотах боеприпасов: выстрел без патрона, пыль спишется из слота, если выстрел был (в рюкзак не кладём)
        boolean slots = !inf && !p.inventory.hasItem(d) && Ammo.has(p, d);
        try {
            ammoField().set(held.getItem(), inf || slots ? null : d);
            ARMED.add(held.getItem());
            if (slots) FROM_SLOTS.add(new Object[] { p, held, held.hasTagCompound() ? held.getTagCompound().getLong(SHOOT_TIME) : Long.MIN_VALUE, d });
        } catch (Exception ignored) {
            // поле недоступно - Divine стреляет как обычно
        }
    }

    /** Конец тика сервера: патроны фазеров и блицев - снова пыль. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) disarm();
    }

    /** Вернуть пыль фазерам и блицам, которым патрон выставлялся под игрока; за выстрелы пылью из слотов - списать её. */
    public static void disarm() {
        for (Object[] f : FROM_SLOTS) {
            ItemStack gun = (ItemStack) f[1];
            long before = (Long) f[2], now = gun.hasTagCompound() ? gun.getTagCompound().getLong(SHOOT_TIME) : Long.MIN_VALUE;
            if (now != before) Ammo.consume((EntityPlayer) f[0], (Item) f[3]); // Divine выстрелил - перезарядка записана заново
        }
        FROM_SLOTS.clear();
        if (ARMED.isEmpty()) return;
        for (Item i : ARMED) try {
            ammoField().set(i, DUST.get(i));
        } catch (Exception ignored) {
            // см. arm
        }
        ARMED.clear();
    }

    /** Бесконечность на фазер и блиц - книгой на наковальне (столу зачарований её не даёт сама игра: она только для луков). */
    @SubscribeEvent
    public void onAnvil(net.minecraftforge.event.AnvilUpdateEvent e) {
        if (e.left == null || e.right == null || e.right.getItem() != net.minecraft.init.Items.enchanted_book || DUST.get(e.left.getItem()) == null) return;
        if (net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(INFINITY, e.left) > 0) return;
        net.minecraft.nbt.NBTTagList l = net.minecraft.init.Items.enchanted_book.func_92110_g(e.right);
        boolean inf = false;
        for (int i = 0; l != null && i < l.tagCount(); i++) if (l.getCompoundTagAt(i).getShort("id") == INFINITY) inf = true;
        if (!inf) return;
        ItemStack out = e.left.copy();
        out.addEnchantment(net.minecraft.enchantment.Enchantment.infinity, 1);
        if (e.name != null && !e.name.isEmpty() && !e.name.equals(e.left.getDisplayName())) out.setStackDisplayName(e.name);
        e.output = out;
        e.cost = ANVIL_COST;
        e.materialCost = 1;
    }

    public static final int ANVIL_COST = 6;

    /**
     * Дробь блица бьёт по одной цели вся: неуязвимость после попадания для неё не действует. Но отброс от пяти дробин
     * складывался и сносил даже боссов с арены - запоминаем скорость до первой дробины, в конце тика урезаем отброс.
     */
    @SubscribeEvent
    public void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || kind(e.source.getSourceOfDamage()) != BLITZ) return;
        v.hurtResistantTime = 0;
        if (!KNOCK.containsKey(v)) KNOCK.put(v, new double[] { v.motionX, v.motionY, v.motionZ });
    }

    /** Боковая дробина блица (копия): вампиризм и прочие "за попадание" считаем только с центральной. */
    public static boolean sidePellet(Entity bullet) {
        return bullet != null && kind(bullet) == BLITZ && bullet.getEntityData().getInteger(GEN) > 0;
    }

    static boolean boss(Entity e) {
        return e instanceof net.minecraft.entity.boss.IBossDisplayData || e instanceof net.divinerpg.entities.base.EntityDivineRPGBoss;
    }

    /** Конец тика: дробь старше BLITZ_LIFE исчезает; отброс дробью - KNOCK_KEEP от набранного (боссам - ноль). */
    @SubscribeEvent
    public void onBlitzTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        for (Iterator<Entity> it = BLITZ_SHOTS.iterator(); it.hasNext();) {
            Entity b = it.next();
            if (b.worldObj != e.world) continue;
            if (!b.isDead && b.ticksExisted >= BLITZ_LIFE) b.setDead();
            if (b.isDead) it.remove();
        }
        for (Iterator<java.util.Map.Entry<EntityLivingBase, double[]>> it = KNOCK.entrySet().iterator(); it.hasNext();) {
            java.util.Map.Entry<EntityLivingBase, double[]> en = it.next();
            EntityLivingBase v = en.getKey();
            if (v.worldObj != e.world) continue;
            it.remove();
            double[] m = en.getValue();
            double keep = boss(v) ? 0 : KNOCK_KEEP;
            v.motionX = m[0] + (v.motionX - m[0]) * keep;
            v.motionY = m[1] + (v.motionY - m[1]) * keep;
            v.motionZ = m[2] + (v.motionZ - m[2]) * keep;
            v.velocityChanged = true;
        }
    }

    public static final float DROP_CHANCE = 0.10F, DROP_LOOTING = 0.02F;

    /** Какой колчан падает с этого моба (null - никакой). */
    public static com.divinerpgextended.trinket.Trinket quiverOf(Entity e) {
        if (e instanceof net.divinerpg.entities.twilight.EntityEternalArcher || e instanceof net.divinerpg.entities.twilight.EntityApalachiaArcher
            || e instanceof net.divinerpg.entities.twilight.EntityMortumArcher
            || e instanceof net.divinerpg.entities.twilight.EntitySunArcher || e instanceof net.divinerpg.entities.iceika.EntityFrostArcher)
            return com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER; // Скайтерна и сумеречный - наследники лучника Апалачии
        if (e instanceof net.divinerpg.entities.twilight.EntityMage) return com.divinerpgextended.trinket.Trinket.DUST_POUCH;
        if (e instanceof net.divinerpg.entities.twilight.EntitySorcerer) return com.divinerpgextended.trinket.Trinket.SLICER_SLING;
        return null;
    }

    /** Колчаны не крафтятся - падают со стрелков, убитых игроком. */
    @SubscribeEvent
    public void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent e) {
        if (e.entityLiving.worldObj.isRemote || !e.recentlyHit || !(e.source.getEntity() instanceof EntityPlayer)) return;
        com.divinerpgextended.trinket.Trinket t = quiverOf(e.entityLiving);
        // с Вечного лучника (босс) - всегда, с остальных стрелков - по шансу
        boolean boss = e.entityLiving instanceof net.divinerpg.entities.twilight.EntityEternalArcher;
        if (t == null || !boss && e.entityLiving.worldObj.rand.nextFloat() >= DROP_CHANCE + DROP_LOOTING * e.lootingLevel) return;
        e.drops.add(new net.minecraft.entity.item.EntityItem(e.entityLiving.worldObj, e.entityLiving.posX, e.entityLiving.posY + 0.5, e.entityLiving.posZ,
            new ItemStack(com.divinerpgextended.registry.ModItems.trinket, 1, t.ordinal())));
    }

    /**
     * Стрелковое оружие Divine (блиц, пушки, La Vekor): стреляет, только если патрон есть в инвентаре, и сразу его тратит, а
     * события перед выстрелом нет. Поэтому на клик (до выстрела), если в инвентаре патрона нет, подкладываем один:
     * блицу - бесплатный, он бесконечный ({@link #QUIVER_TAG}, не выстрелил - убирается), иначе - из слотов боеприпасов
     * ({@link Ammo#lend}, не выстрелил - возвращается). Луки и фазер - отдельно.
     */
    @SubscribeEvent
    public void onUse(net.minecraftforge.event.entity.player.PlayerInteractEvent e) {
        if (e.world.isRemote || e.action == net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        EntityPlayer p = e.entityPlayer;
        ItemStack held = p.getHeldItem();
        if (held == null || held.getItem() instanceof net.minecraft.item.ItemBow || p.capabilities.isCreativeMode) return;
        if (DUST.get(held.getItem()) != null) {
            arm(p, held); // пыль в рюкзаке - тратит Divine, в слотах - спишем сами, подсумок или Бесконечность - не нужна
            return;
        }
        Item a = Ammo.of(held);
        if (a == null || p.inventory.hasItem(a)) return;
        Ammo.lend(p, a);
    }

    /** Клиент: надет ли колчан у своего игрока (лук натягивается без стрел и на клиенте). */
    public static boolean clientQuiver;
    private static final java.util.Map<EntityPlayer, String> QUIVER_SENT = new java.util.WeakHashMap<EntityPlayer, String>();

    private static boolean quiver(EntityPlayer p) {
        return p.worldObj.isRemote ? clientQuiver : wears(p, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER);
    }

    /**
     * Убрать помеченные боеприпасы (подложенная пыль, не ушедшая в выстрел; старые стрелы колчана), вернуть в слоты
     * не потраченные из слотов боеприпасов и сказать клиенту про колчан и что лежит в слотах боеприпасов.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote) return;
        EntityPlayer p = e.player;
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s != null && s.hasTagCompound() && s.getTagCompound().getBoolean(QUIVER_TAG)) p.inventory.mainInventory[i] = null;
        }
        Ammo.settle(p);
        if (p.ticksExisted % 10 == 0 && p instanceof net.minecraft.entity.player.EntityPlayerMP) {
            boolean q = wears(p, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER);
            int[] ids = Ammo.ids(p);
            String sig = q + java.util.Arrays.toString(ids);
            if (!sig.equals(QUIVER_SENT.get(p))) {
                QUIVER_SENT.put(p, sig);
                com.divinerpgextended.cold.ColdHandler.NET.sendTo(new QuiverSync(q, ids), (net.minecraft.entity.player.EntityPlayerMP) p);
            }
        }
    }

    public static class QuiverSync implements cpw.mods.fml.common.network.simpleimpl.IMessage {

        boolean on;
        int[] ammo = new int[0];

        public QuiverSync() {}

        QuiverSync(boolean on, int[] ammo) {
            this.on = on;
            this.ammo = ammo;
        }

        @Override
        public void fromBytes(io.netty.buffer.ByteBuf buf) {
            on = buf.readBoolean();
            int n = Math.min(buf.readUnsignedByte(), com.divinerpgextended.accessory.Accessories.TOTAL);
            ammo = new int[n];
            for (int i = 0; i < n; i++) ammo[i] = buf.readInt();
        }

        @Override
        public void toBytes(io.netty.buffer.ByteBuf buf) {
            buf.writeBoolean(on);
            buf.writeByte(ammo.length);
            for (int id : ammo) buf.writeInt(id);
        }
    }

    public static class QuiverSyncHandler implements cpw.mods.fml.common.network.simpleimpl.IMessageHandler<QuiverSync, cpw.mods.fml.common.network.simpleimpl.IMessage> {

        @Override
        public cpw.mods.fml.common.network.simpleimpl.IMessage onMessage(QuiverSync msg, cpw.mods.fml.common.network.simpleimpl.MessageContext ctx) {
            clientQuiver = msg.on;
            java.util.Set<Integer> a = new java.util.HashSet<Integer>();
            for (int id : msg.ammo) a.add(id);
            Ammo.client = a;
            return null;
        }
    }

    public static void registerMessages() {
        com.divinerpgextended.cold.ColdHandler.NET.registerMessage(QuiverSyncHandler.class, QuiverSync.class, 10, cpw.mods.fml.relauncher.Side.CLIENT);
    }

    public static final String TMP_INF = "dreTmpInfinity";
    private static final int INFINITY = net.minecraft.enchantment.Enchantment.infinity.effectId;

    /**
     * Колчан: лук (ванильный и Divine) натягивается без стрел (оба требуют стрелу даже с Бесконечностью - натягиваем
     * сами) и стреляет как с Бесконечностью - стрелы не нужны и не тратятся.
     */
    @SubscribeEvent
    public void onNock(net.minecraftforge.event.entity.player.ArrowNockEvent e) {
        if (e.result == null || !(quiver(e.entityPlayer) || fromSlots(e.entityPlayer, e.result))) return;
        e.entityPlayer.setItemInUse(e.result, e.result.getMaxItemUseDuration());
        e.setCanceled(true);
    }

    /** Стрелы для лука не в инвентаре, а в слотах боеприпасов (луки сами смотрят только инвентарь). */
    private static boolean fromSlots(EntityPlayer p, ItemStack bow) {
        Item a = Ammo.of(bow);
        return a != null && !p.capabilities.isCreativeMode && !p.inventory.hasItem(a) && Ammo.has(p, a);
    }

    @SubscribeEvent
    public void onLoose(net.minecraftforge.event.entity.player.ArrowLooseEvent e) {
        if (quiver(e.entityPlayer)) {
            lendInfinity(e.entityPlayer, e.bow);
            return;
        }
        // стрела из слотов боеприпасов: на миг в инвентарь, лук её тут же потратит
        if (e.bow != null && !e.entityPlayer.worldObj.isRemote && fromSlots(e.entityPlayer, e.bow)
            && net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(INFINITY, e.bow) == 0) Ammo.lend(e.entityPlayer, Ammo.of(e.bow));
    }

    /** Повесить на лук временную Бесконечность (снимается, когда лук отпущен). */
    public static void lendInfinity(EntityPlayer p, ItemStack bow) {
        if (bow == null || !quiver(p)) return;
        if (net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(INFINITY, bow) > 0) return;
        bow.addEnchantment(net.minecraft.enchantment.Enchantment.infinity, 1);
        bow.getTagCompound().setBoolean(TMP_INF, true);
    }

    /** Снять временную Бесконечность с лука, который больше не натягивают. */
    public static void returnInfinity(ItemStack bow) {
        if (bow == null || !bow.hasTagCompound() || !bow.getTagCompound().getBoolean(TMP_INF)) return;
        bow.getTagCompound().removeTag(TMP_INF);
        net.minecraft.nbt.NBTTagList ench = bow.getEnchantmentTagList();
        if (ench != null) {
            for (int i = 0; i < ench.tagCount(); i++) if (ench.getCompoundTagAt(i).getShort("id") == INFINITY) {
                ench.removeTag(i);
                break;
            }
            if (ench.tagCount() == 0) bow.getTagCompound().removeTag("ench");
        }
    }

    @SubscribeEvent
    public void onBowTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayer p = e.player;
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s != null && !(p.isUsingItem() && s == p.getHeldItem())) returnInfinity(s); // натягиваемый лук не трогаем
        }
    }

    /** Стрелы из колчана не подбираются обратно (иначе колчан - ферма стрел). */
    @SubscribeEvent
    public void onArrow(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof net.minecraft.entity.projectile.EntityArrow)) return;
        net.minecraft.entity.projectile.EntityArrow a = (net.minecraft.entity.projectile.EntityArrow) e.entity;
        if (a.shootingEntity instanceof EntityPlayer && wears((EntityPlayer) a.shootingEntity, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER)
            && a.canBePickedUp == 1) a.canBePickedUp = 2;
    }

    /** Пауза Морозного лучника между выстрелами (тики): у Divine - 2 тика, десять стрел в секунду. */
    public static final int FROST_ARCHER_MIN = 20, FROST_ARCHER_MAX = 40;

    /** Морозный лучник стреляет как скелет - раз в 1-2 секунды, а не очередью. */
    @SubscribeEvent
    public void onFrostArcher(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof net.divinerpg.entities.iceika.EntityFrostArcher)) return;
        net.divinerpg.entities.iceika.EntityFrostArcher a = (net.divinerpg.entities.iceika.EntityFrostArcher) e.entity;
        for (Object o : new java.util.ArrayList<Object>(a.tasks.taskEntries)) {
            net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry t = (net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry) o;
            if (!(t.action instanceof net.minecraft.entity.ai.EntityAIArrowAttack)) continue;
            a.tasks.removeTask(t.action);
            a.tasks.addTask(t.priority, new net.minecraft.entity.ai.EntityAIArrowAttack(a,
                a.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.movementSpeed).getBaseValue(), FROST_ARCHER_MIN, FROST_ARCHER_MAX, 10.0F));
        }
    }

    public static int kind(Entity bullet) {
        return bullet == null ? 0 : bullet.getEntityData().getInteger(KIND);
    }

    /** Подручный босса (по меткам призыва). */
    public static boolean bossAdd(Entity e) {
        NBTTagCompound d = e.getEntityData();
        return d.hasKey(com.divinerpgextended.entity.EntityGroveGuardian.ADD_TAG) || d.hasKey("dreCadillionAdd") || d.hasKey("dreDemonAdd");
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof EntityShooterBullet)) return;
        Entity b = e.entity;
        NBTTagCompound d = b.getEntityData();
        if (d.hasKey(KIND)) {
            if (d.getInteger(KIND) == SLICER) TOTEM_SHOTS.add(b);
            if (d.getInteger(KIND) == BLITZ) BLITZ_SHOTS.add(b);
            return;
        }
        EntityLivingBase thrower = ((EntityShooterBullet) b).getThrower();
        if (!(thrower instanceof EntityPlayer)) return;
        EntityPlayer pl = (EntityPlayer) thrower;
        ItemStack held = pl.getHeldItem();
        int k = kind(held);
        if (k == 0) return;
        if (k == SLICER && wears(pl, com.divinerpgextended.trinket.Trinket.SLICER_SLING) && !pl.capabilities.isCreativeMode) {
            // перевязь: брошенный слайсер возвращается в руку
            if (held.stackSize < held.getMaxStackSize()) held.stackSize++;
            else pl.inventory.addItemStackToInventory(new ItemStack(held.getItem()));
        }
        d.setInteger(KIND, k);
        if (k == SLICER) TOTEM_SHOTS.add(b);
        if (k == BLITZ) {
            BLITZ_SHOTS.add(b);
            e.world.playSoundAtEntity(pl, BLITZ_SOUND, 1.3F, 0.95F + e.world.rand.nextFloat() * 0.1F);
            // дробь: ещё PELLETS - 1 снарядов веером по горизонтали
            for (int i = 1; i < PELLETS; i++) {
                double ang = Math.toRadians(SPREAD * ((i + 1) / 2) * (i % 2 == 0 ? 1 : -1));
                double c = Math.cos(ang), s = Math.sin(ang);
                double mx = b.motionX * c - b.motionZ * s, mz = b.motionX * s + b.motionZ * c;
                // чуть впереди стрелка, чтобы дробина не начинала путь внутри него
                spawnClone(b, b.posX + mx * 0.6, b.posY + b.motionY * 0.6, b.posZ + mz * 0.6, mx, b.motionY + (e.world.rand.nextDouble() - 0.5) * 0.08, mz, 1);
            }
        }
    }

    /**
     * Копия снаряда Divine (через NBT - текстура, урон, стрелок) с новым курсом и поколением. Создаётся тем же классом
     * напрямую, а не через реестр сущностей: снаряд с частицами (блиц) мог не находиться по имени - боковая дробь не появлялась.
     */
    public static Entity spawnClone(Entity src, double x, double y, double z, double mx, double my, double mz, int gen) {
        NBTTagCompound tag = new NBTTagCompound();
        src.writeToNBT(tag);
        tag.removeTag("UUIDMost");
        tag.removeTag("UUIDLeast");
        Entity c;
        try {
            c = src.getClass().getConstructor(World.class).newInstance(src.worldObj);
            c.readFromNBT(tag);
        } catch (Exception ex) {
            c = EntityList.createEntityFromNBT(tag, src.worldObj);
        }
        if (c == null) return null;
        // стрелок: без него копия била самого стрелка (появлялась в нём) и не считалась его выстрелом
        if (src instanceof net.minecraft.entity.projectile.EntityThrowable && c instanceof net.minecraft.entity.projectile.EntityThrowable) {
            EntityLivingBase owner = ((net.minecraft.entity.projectile.EntityThrowable) src).getThrower();
            if (owner != null) cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.entity.projectile.EntityThrowable.class,
                (net.minecraft.entity.projectile.EntityThrowable) c, owner, "thrower", "field_70192_c");
        }
        c.setPosition(x, y, z);
        c.motionX = mx;
        c.motionY = my;
        c.motionZ = mz;
        c.getEntityData().setInteger(KIND, kind(src));
        c.getEntityData().setInteger(GEN, gen);
        src.worldObj.spawnEntityInWorld(c);
        return c;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote) return;
        Entity b = e.source.getSourceOfDamage();
        if (b instanceof net.minecraft.entity.projectile.EntityArrow && kind(b) == 0) {
            Entity sh = ((net.minecraft.entity.projectile.EntityArrow) b).shootingEntity;
            if (sh instanceof EntityPlayer && wears((EntityPlayer) sh, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER)) e.ammount *= QUIVER_DMG;
            return;
        }
        int k = kind(b);
        if (k == 0) return;
        EntityLivingBase owner = b instanceof EntityShooterBullet ? ((EntityShooterBullet) b).getThrower() : null;
        boolean pouch = owner instanceof EntityPlayer && wears((EntityPlayer) owner, com.divinerpgextended.trinket.Trinket.DUST_POUCH);
        boolean sling = owner instanceof EntityPlayer && wears((EntityPlayer) owner, com.divinerpgextended.trinket.Trinket.SLICER_SLING);
        if (pouch && (k == BLITZ || k == PHASER)) e.ammount *= POUCH_DMG;
        EntityLivingBase victim = e.entityLiving;
        int gen = b.getEntityData().getInteger(GEN);
        switch (k) {
            case BLITZ:
                e.ammount *= PELLET_DMG * (bossAdd(victim) ? ADD_BONUS : 1) * (gen > 0 && boss(victim) ? BOSS_SIDE : 1);
                break;
            case PHASER:
                // пробой: снаряд летит дальше за спину цели
                if (gen + 1 < PIERCE) {
                    double v = Math.sqrt(b.motionX * b.motionX + b.motionY * b.motionY + b.motionZ * b.motionZ);
                    if (v > 0.01) {
                        double k2 = (victim.width / 2 + 1.0) / v;
                        spawnClone(b, victim.posX + b.motionX * k2, b.posY + b.motionY * k2, victim.posZ + b.motionZ * k2, b.motionX, b.motionY, b.motionZ, gen + 1);
                    }
                }
                break;
            default: // слайсер: рикошет в ближайшего другого врага
                if (gen + 1 < RICOCHET + (sling ? 1 : 0)) {
                    EntityLivingBase next = nearestFoe(victim, 8);
                    if (next != null) {
                        double dx = next.posX - victim.posX, dy = next.posY + next.height / 2 - (victim.posY + victim.height / 2), dz = next.posZ - victim.posZ;
                        double d = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));
                        double sp = Math.max(1.0, Math.sqrt(b.motionX * b.motionX + b.motionY * b.motionY + b.motionZ * b.motionZ));
                        double ox = dx / d * (victim.width / 2 + 0.6), oz = dz / d * (victim.width / 2 + 0.6);
                        spawnClone(b, victim.posX + ox, victim.posY + victim.height / 2, victim.posZ + oz, dx / d * sp, dy / d * sp + 0.05, dz / d * sp, gen + 1);
                    }
                }
        }
    }

    private static EntityLivingBase nearestFoe(EntityLivingBase from, double r) {
        EntityLivingBase best = null;
        double bd = r * r;
        for (Object o : from.worldObj.getEntitiesWithinAABB(EntityLiving.class, from.boundingBox.expand(r, r / 2, r))) {
            EntityLivingBase m = (EntityLivingBase) o;
            if (m == from || !m.isEntityAlive() || !(m instanceof IMob || m instanceof net.minecraft.entity.boss.IBossDisplayData)) continue;
            double d = m.getDistanceSqToEntity(from);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    /** Слайсеры рядом с ветряным тотемом подзаряжают его. */
    @SubscribeEvent
    public void onTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || TOTEM_SHOTS.isEmpty()) return;
        for (Iterator<Entity> it = TOTEM_SHOTS.iterator(); it.hasNext();) {
            Entity b = it.next();
            if (b.isDead) {
                it.remove();
                continue;
            }
            if (b.worldObj != e.world) continue;
            World w = b.worldObj;
            int bx = (int) Math.floor(b.posX), by = (int) Math.floor(b.posY), bz = (int) Math.floor(b.posZ);
            for (int x = bx - 1; x <= bx + 1; x++) for (int y = by - 1; y <= by + 1; y++) for (int z = bz - 1; z <= bz + 1; z++) {
                if (w.getBlock(x, y, z) != com.divinerpgextended.registry.ModBlocks.windTotem || w.getBlockMetadata(x, y, z) != 0) continue;
                for (Object o : w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntityWindLord.class,
                    net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 48, y - 32, z - 48, x + 48, y + 32, z + 48))) {
                    ((com.divinerpgextended.entity.EntityWindLord) o).remoteCharge(x, y, z);
                    break;
                }
                b.setDead();
                it.remove();
                return;
            }
        }
    }
}
