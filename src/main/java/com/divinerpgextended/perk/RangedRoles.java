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
 * <li>блиц - дробовик: {@link #PELLETS} снарядов веером, каждый слабее ({@link #PELLET_DMG}), по подручным боссов x2 -
 * против толпы;</li>
 * <li>фазер - пробой: снаряд проходит насквозь до {@link #PIERCE} целей, по частям голема x2; тратит пыль своего мира
 * (у Divine он бесконечный - слишком сильный);</li>
 * <li>слайсер - рикошет до {@link #RICOCHET} целей подряд, перерезает лиану голема, сбивает замах Короля-воина и
 * издалека заряжает ветряные тотемы (как снежки в кристаллы дракона).</li>
 * </ul>
 * Колчаны-аксессуары (как в Террарии; падают со стрелков - {@link #DROP_CHANCE}: лучники - колчан (Вечный лучник - наверняка), маг - подсумок, колдун -
 * перевязь) дают бесконечные боеприпасы своему виду: бездонный колчан - лукам, подсумок пыли -
 * блицам и фазерам, перевязь метателя - слайсерам. Колчан: лук натягивается и стреляет без стрел, ничего не кладя в
 * инвентарь (клиенту о колчане говорит {@link QuiverSync}). Подсумок: фазер не тратит пыль, блицу пыль подкладывается
 * на миг выстрела ({@link #QUIVER_TAG}) и тут же им тратится - в инвентаре её не видно.
 * Чтобы колчан носили не только ради удобства: колчан +10% урона стрелами, подсумок +15% урона блица и фазера,
 * перевязь +1 цель рикошета.
 */
public class RangedRoles {

    public static final String KIND = "dreRanged", GEN = "dreRangedGen";
    public static final int BLITZ = 1, PHASER = 2, SLICER = 3;
    public static final int PELLETS = 5, PIERCE = 3, RICOCHET = 3;
    public static final float PELLET_DMG = 0.5F, ADD_BONUS = 2.0F;
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
        if (i == TwilightItemsWeapons.edenSlicer || i == TwilightItemsWeapons.wildwoodSlicer || i == TwilightItemsWeapons.apalachiaSlicer
            || i == TwilightItemsWeapons.skythernSlicer || i == TwilightItemsWeapons.mortumSlicer) return SLICER;
        return 0;
    }

    public static boolean wears(EntityPlayer p, com.divinerpgextended.trinket.Trinket t) {
        return com.divinerpgextended.accessory.Accessories.wears(p, t);
    }

    /** Пыль мира, которую тратит этот фазер. */
    public static Item phaserDust(Item phaser) {
        for (TwilightWorld w : TwilightWorld.values()) if (TwilightCrafts.weapons(w)[2] == phaser) return w.dust();
        return null;
    }

    /** Подсказка фазера: у Divine он "бесконечные боеприпасы", у нас тратит пыль своего мира (с подсумком - нет). */
    @SubscribeEvent
    public void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent e) {
        if (e.itemStack == null || kind(e.itemStack) != PHASER) return;
        Item dust = phaserDust(e.itemStack.getItem());
        if (dust == null) return;
        String inf, ammo;
        try {
            inf = net.divinerpg.utils.TooltipLocalizer.infiniteAmmo();
            ammo = net.divinerpg.utils.TooltipLocalizer.ammo(dust);
        } catch (Throwable ex) { // другая версия Divine
            return;
        }
        int at = e.toolTip.indexOf(inf);
        if (at < 0) return;
        e.toolTip.set(at, ammo);
        e.toolTip.add(at + 1, net.minecraft.util.EnumChatFormatting.GRAY + net.minecraft.util.StatCollector.translateToLocal("dre.tooltip.phaser_pouch"));
    }

    /** Дробь блица бьёт по одной цели вся: неуязвимость после попадания для неё не действует. */
    @SubscribeEvent
    public void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (!e.entityLiving.worldObj.isRemote && kind(e.source.getSourceOfDamage()) == BLITZ) e.entityLiving.hurtResistantTime = 0;
    }

    /** Боеприпас, нужный оружию в руке, и колчан для него (null - не наш случай). */
    private static Object[] ammoFor(ItemStack held) {
        if (held == null) return null;
        Item i = held.getItem();
        if (kind(held) == BLITZ) {
            Item a = field(net.divinerpg.items.base.ItemProjectileShooter.class, i, "ammo");
            return a == null ? null : new Object[] { a, com.divinerpgextended.trinket.Trinket.DUST_POUCH };
        }
        return null;
    }

    private static Item field(Class<?> c, Object o, String name) {
        try {
            java.lang.reflect.Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(o);
            return v instanceof Item ? (Item) v : null;
        } catch (Exception ex) {
            return null;
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
     * Подсумок и блиц: Divine стреляет блицем, только если пыль есть, и сразу её тратит, а события перед выстрелом нет.
     * Поэтому на клик (до выстрела) подкладываем одну помеченную пыль - выстрел её и съедает; не выстрелил (перезарядка) -
     * её убирает {@link #onPlayerTick}.
     */
    @SubscribeEvent
    public void onUse(net.minecraftforge.event.entity.player.PlayerInteractEvent e) {
        if (e.world.isRemote || e.action == net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        EntityPlayer p = e.entityPlayer;
        Object[] need = ammoFor(p.getHeldItem());
        if (need == null || p.capabilities.isCreativeMode || !wears(p, (com.divinerpgextended.trinket.Trinket) need[1])) return;
        Item a = (Item) need[0];
        if (p.inventory.hasItem(a)) return;
        ItemStack s = new ItemStack(a);
        s.setTagCompound(new NBTTagCompound());
        s.getTagCompound().setBoolean(QUIVER_TAG, true);
        p.inventory.addItemStackToInventory(s);
    }

    /** Клиент: надет ли колчан у своего игрока (лук натягивается без стрел и на клиенте). */
    public static boolean clientQuiver;
    private static final java.util.Map<EntityPlayer, Boolean> QUIVER_SENT = new java.util.WeakHashMap<EntityPlayer, Boolean>();

    private static boolean quiver(EntityPlayer p) {
        return p.worldObj.isRemote ? clientQuiver : wears(p, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER);
    }

    /** Убрать помеченные боеприпасы (подложенная пыль, не ушедшая в выстрел; старые стрелы колчана) и сказать клиенту про колчан. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote) return;
        EntityPlayer p = e.player;
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s != null && s.hasTagCompound() && s.getTagCompound().getBoolean(QUIVER_TAG)) p.inventory.mainInventory[i] = null;
        }
        if (p.ticksExisted % 10 == 0 && p instanceof net.minecraft.entity.player.EntityPlayerMP) {
            boolean q = wears(p, com.divinerpgextended.trinket.Trinket.ENDLESS_QUIVER);
            if (!Boolean.valueOf(q).equals(QUIVER_SENT.get(p))) {
                QUIVER_SENT.put(p, q);
                com.divinerpgextended.cold.ColdHandler.NET.sendTo(new QuiverSync(q), (net.minecraft.entity.player.EntityPlayerMP) p);
            }
        }
    }

    public static class QuiverSync implements cpw.mods.fml.common.network.simpleimpl.IMessage {

        boolean on;

        public QuiverSync() {}

        QuiverSync(boolean on) {
            this.on = on;
        }

        @Override
        public void fromBytes(io.netty.buffer.ByteBuf buf) {
            on = buf.readBoolean();
        }

        @Override
        public void toBytes(io.netty.buffer.ByteBuf buf) {
            buf.writeBoolean(on);
        }
    }

    public static class QuiverSyncHandler implements cpw.mods.fml.common.network.simpleimpl.IMessageHandler<QuiverSync, cpw.mods.fml.common.network.simpleimpl.IMessage> {

        @Override
        public cpw.mods.fml.common.network.simpleimpl.IMessage onMessage(QuiverSync msg, cpw.mods.fml.common.network.simpleimpl.MessageContext ctx) {
            clientQuiver = msg.on;
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
        if (e.result == null || !quiver(e.entityPlayer)) return;
        e.entityPlayer.setItemInUse(e.result, e.result.getMaxItemUseDuration());
        e.setCanceled(true);
    }

    @SubscribeEvent
    public void onLoose(net.minecraftforge.event.entity.player.ArrowLooseEvent e) {
        lendInfinity(e.entityPlayer, e.bow);
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
            return;
        }
        EntityLivingBase thrower = ((EntityShooterBullet) b).getThrower();
        if (!(thrower instanceof EntityPlayer)) return;
        EntityPlayer pl = (EntityPlayer) thrower;
        ItemStack held = pl.getHeldItem();
        int k = kind(held);
        if (k == 0) return;
        if (k == PHASER && !pl.capabilities.isCreativeMode && !wears(pl, com.divinerpgextended.trinket.Trinket.DUST_POUCH)) {
            // фазер тратит пыль своего мира; нет пыли - выстрела нет
            Item dust = phaserDust(held.getItem());
            if (dust != null && !pl.inventory.consumeInventoryItem(dust)) {
                e.setCanceled(true);
                pl.worldObj.playSoundAtEntity(pl, "random.click", 1.0F, 1.6F);
                return;
            }
        }
        if (k == SLICER && wears(pl, com.divinerpgextended.trinket.Trinket.SLICER_SLING) && !pl.capabilities.isCreativeMode) {
            // перевязь: брошенный слайсер возвращается в руку
            if (held.stackSize < held.getMaxStackSize()) held.stackSize++;
            else pl.inventory.addItemStackToInventory(new ItemStack(held.getItem()));
        }
        d.setInteger(KIND, k);
        if (k == SLICER) TOTEM_SHOTS.add(b);
        if (k == BLITZ) {
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
                e.ammount *= PELLET_DMG * (bossAdd(victim) ? ADD_BONUS : 1);
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
