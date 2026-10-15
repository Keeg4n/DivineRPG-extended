package com.divinerpgextended.arcana;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.entities.base.EntityDivineRPGVillager;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Торговцы Арканы по прогрессу и Кристалл памяти.
 * <ul>
 * <li>Торговец появляется рядом, только если у всех игроков вокруг открыт его этап; кому рано - торговец не
 * разговаривает.</li>
 * <li>Встретил и поговорил - торговец запомнен. Пока не встречен, Аркана сама подсказывает: где-то в залах в
 * 20-40 блоках появляется именно он, и в чат приходит, с какой стороны слышен голос.</li>
 * <li>В Аркане у игрока всегда Кристалл памяти (вне Арканы он исчезает). Им зовётся встреченный торговец: он
 * приходит к игроку и уходит через 3 минуты без торговли, если игрок отошёл на 48 блоков или позвал другого.</li>
 * </ul>
 */
public class ArcanaMerchants {

    private static final String OWNER = "dreSummonOwner", LAST = "dreSummonLast", BEACON = "dreBeacon";
    private static final int STAY_TICKS = 3 * 60 * 20, SUMMON_COOLDOWN = 10 * 20, BEACON_EVERY = 30 * 20,
        BEACON_COOLDOWN = 5 * 60 * 20, BEACON_LIFE = 10 * 60 * 20;

    private static final Map<UUID, Entity> ACTIVE = new HashMap<UUID, Entity>();
    private static final Map<UUID, Long> LAST_SUMMON = new HashMap<UUID, Long>();
    private static final Map<String, Long> LAST_BEACON = new HashMap<String, Long>();
    private static final java.util.Set<UUID> FULL = new java.util.HashSet<UUID>();

    private static boolean arcana(World w) {
        return w.provider.dimensionId == ConfigurationHelper.arcana;
    }

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        com.divinerpgextended.audit.Audit.say(p, key, args);
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    private static ChatComponentTranslation name(ArcanaMerchant m) {
        return new ChatComponentTranslation("dre.merchant." + m.key());
    }

    /** Торговец не появляется рядом с тем, кому он ещё не открыт. */
    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn e) {
        if (e.world.isRemote || !arcana(e.world)) return;
        ArcanaMerchant m = ArcanaMerchant.of(e.entityLiving);
        if (m == null || m.gate == null) return;
        for (Object o : e.world.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            double dx = p.posX - e.x, dz = p.posZ - e.z;
            if (dx * dx + dz * dz <= 128 * 128 && !m.unlocked(p)) {
                e.setResult(Event.Result.DENY);
                return;
            }
        }
    }

    /** Разговор: встреча (запоминание), отказ тем, кому рано, продление вызова. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteract(EntityInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        ArcanaMerchant m = ArcanaMerchant.of(e.target);
        if (m == null || p.worldObj.isRemote) return;
        if (!m.unlocked(p)) {
            say(p, EnumChatFormatting.GRAY, "dre.chat.merchant_notready", name(m));
            e.setCanceled(true);
            return;
        }
        NBTTagCompound tag = e.target.getEntityData();
        if (m.meet(p)) say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.chat.merchant_met", name(m));
        if (tag.hasKey(BEACON)) {
            // встреча состоялась: дальше он ведёт себя как вызванный этим игроком и уйдёт по тем же правилам
            tag.removeTag(BEACON);
            tag.setString(OWNER, p.getUniqueID().toString());
            ACTIVE.put(p.getUniqueID(), e.target);
        }
        if (tag.hasKey(OWNER)) tag.setLong(LAST, p.worldObj.getTotalWorldTime());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote || p.ticksExisted % 20 != 0) return;
        boolean inArcana = arcana(p.worldObj);
        int count = 0;
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s == null || s.getItem() != ModItems.memoryCrystal) continue;
            if (!inArcana || count > 0) p.inventory.mainInventory[i] = null;
            else count++;
        }
        if (!inArcana) FULL.remove(p.getUniqueID());
        if (inArcana && count == 0) {
            if (p.inventory.addItemStackToInventory(new ItemStack(ModItems.memoryCrystal))) {
                say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.chat.crystal_given");
                FULL.remove(p.getUniqueID());
            } else if (FULL.add(p.getUniqueID())) {
                // инвентарь полон: кристалл ждёт свободного слота и появится сам, говорим об этом один раз
                say(p, EnumChatFormatting.GRAY, "dre.chat.crystal_full");
            }
        }
        // кто уже брал задания у Леорны, тот с ней знаком
        if (inArcana && !ArcanaMerchant.LEORNA.met(p) && com.divinerpgextended.fishing.FishingEvents.quests(p) > 0) {
            ArcanaMerchant.LEORNA.meet(p);
        }
        if (inArcana && p.ticksExisted % BEACON_EVERY == 0) beacon(p);
    }

    /** Первую встречу Аркана устраивает сама: не встреченный, но уже открытый торговец появляется в залах неподалёку. */
    private static void beacon(EntityPlayer p) {
        World w = p.worldObj;
        long now = w.getTotalWorldTime();
        for (ArcanaMerchant m : ArcanaMerchant.values()) {
            if (!m.unlocked(p) || m.met(p)) continue;
            String key = p.getUniqueID() + ":" + m.key();
            Long last = LAST_BEACON.get(key);
            if (last != null && now - last < BEACON_COOLDOWN) continue;
            if (nearby(w, m, p, 96) != null) continue;
            EntityDivineRPGVillager v = m.create(w);
            double[] spot = spot(w, p, 20, 40);
            if (v == null || spot == null) continue;
            v.setLocationAndAngles(spot[0], spot[1], spot[2], w.rand.nextFloat() * 360, 0);
            v.func_110163_bv(); // не исчезает, пока его не встретили
            v.getEntityData().setLong(BEACON, now);
            w.spawnEntityInWorld(v);
            LAST_BEACON.put(key, now);
            say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.chat.merchant_voice", name(m),
                new ChatComponentTranslation("dre.dir." + direction(spot[0] - p.posX, spot[2] - p.posZ)));
            return; // по одному за раз
        }
    }

    private static Entity nearby(World w, ArcanaMerchant m, EntityPlayer p, double r) {
        for (Object o : w.loadedEntityList) {
            Entity e = (Entity) o;
            if (e.getClass() == m.type && !e.isDead && e.getDistanceSqToEntity(p) < r * r) return e;
        }
        return null;
    }

    private static String direction(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz)); // 0 - север (-z), 90 - восток (+x)
        int i = (int) Math.round(((a % 360) + 360) % 360 / 45.0) % 8;
        return new String[] { "n", "ne", "e", "se", "s", "sw", "w", "nw" }[i];
    }

    /** Место с полом и двумя блоками воздуха в кольце rMin..rMax вокруг игрока (в залах Арканы, ниже 40). */
    private static double[] spot(World w, EntityPlayer p, int rMin, int rMax) {
        for (int t = 0; t < 40; t++) {
            double a = w.rand.nextDouble() * Math.PI * 2, d = rMin + w.rand.nextDouble() * (rMax - rMin);
            int x = MathHelper.floor_double(p.posX + Math.cos(a) * d), z = MathHelper.floor_double(p.posZ + Math.sin(a) * d);
            if (!w.blockExists(x, 0, z)) continue;
            for (int y = Math.min(39, (int) p.posY + 8); y > 4; y--) {
                if (w.getBlock(x, y - 1, z).getMaterial().isSolid() && w.isAirBlock(x, y, z) && w.isAirBlock(x, y + 1, z)) {
                    return new double[] { x + 0.5, y, z + 0.5 };
                }
            }
        }
        return null;
    }

    private static double[] near(World w, EntityPlayer p) {
        for (int t = 0; t < 20; t++) {
            int x = MathHelper.floor_double(p.posX) + w.rand.nextInt(5) - 2, z = MathHelper.floor_double(p.posZ) + w.rand.nextInt(5) - 2;
            for (int y = (int) p.posY + 2; y >= (int) p.posY - 2; y--) {
                if ((x != (int) Math.floor(p.posX) || z != (int) Math.floor(p.posZ)) && w.getBlock(x, y - 1, z).getMaterial().isSolid()
                    && w.isAirBlock(x, y, z) && w.isAirBlock(x, y + 1, z)) return new double[] { x + 0.5, y, z + 0.5 };
            }
        }
        return new double[] { p.posX, p.posY, p.posZ };
    }

    /** Вызов Кристаллом памяти (с проверками: в Аркане, встречен и открыт, откат). */
    public static void summon(EntityPlayerMP p, int index) {
        if (index < 0 || index >= ArcanaMerchant.values().length || !arcana(p.worldObj)) return;
        ArcanaMerchant m = ArcanaMerchant.values()[index];
        if (!m.met(p) || !m.unlocked(p) || !p.inventory.hasItem(ModItems.memoryCrystal)) return;
        World w = p.worldObj;
        long now = w.getTotalWorldTime();
        Long last = LAST_SUMMON.get(p.getUniqueID());
        if (last != null && now - last < SUMMON_COOLDOWN) {
            say(p, EnumChatFormatting.GRAY, "dre.chat.summon_cooldown");
            return;
        }
        Entity old = ACTIVE.remove(p.getUniqueID());
        if (old != null && !old.isDead) vanish(old);
        EntityDivineRPGVillager v = m.create(w);
        if (v == null) return;
        double[] s = near(w, p);
        v.setLocationAndAngles(s[0], s[1], s[2], p.rotationYaw + 180, 0);
        v.func_110163_bv();
        v.getEntityData().setString(OWNER, p.getUniqueID().toString());
        v.getEntityData().setLong(LAST, now);
        w.spawnEntityInWorld(v);
        ACTIVE.put(p.getUniqueID(), v);
        LAST_SUMMON.put(p.getUniqueID(), now);
        if (w instanceof WorldServer) ((WorldServer) w).func_147487_a("portal", s[0], s[1] + 1, s[2], 60, 0.4, 0.8, 0.4, 0.5);
        w.playSoundEffect(s[0], s[1], s[2], "mob.endermen.portal", 0.8F, 1.2F);
    }

    private static void vanish(Entity e) {
        if (e.worldObj instanceof WorldServer) {
            ((WorldServer) e.worldObj).func_147487_a("portal", e.posX, e.posY + 1, e.posZ, 40, 0.4, 0.8, 0.4, 0.5);
        }
        e.setDead();
    }

    /**
     * Сделки торговцев Divine не кончаются. В Divine сделка закрывается после 7 покупок, а сброс сломан (условие в
     * коде Divine никогда не выполняется) - закрытая сделка больше не открывалась. Делаем на обеих сторонах: окно
     * торговли Divine строит список на клиенте сам.
     */
    @SubscribeEvent
    public void onMerchantTick(LivingEvent.LivingUpdateEvent e) {
        if (!(e.entityLiving instanceof EntityDivineRPGVillager) || e.entityLiving.ticksExisted % 10 != 0) return;
        MerchantRecipeList list = ((EntityDivineRPGVillager) e.entityLiving).getRecipes(null);
        if (list == null) return;
        for (Object o : list) {
            MerchantRecipe r = (MerchantRecipe) o;
            if (r.isRecipeDisabled()) r.func_82783_a(7);
        }
    }

    /** Вызванный торговец уходит: хозяина нет рядом, долго без торговли или позвали другого. */
    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent e) {
        Entity v = e.entityLiving;
        if (v.worldObj.isRemote || !(v instanceof EntityDivineRPGVillager) || v.ticksExisted % 20 != 0) return;
        NBTTagCompound tag = v.getEntityData();
        if (tag.hasKey(BEACON)) {
            // не дождался встречи: через 10 минут уходит, если рядом никого
            if (v.worldObj.getTotalWorldTime() - tag.getLong(BEACON) > BEACON_LIFE
                && v.worldObj.getClosestPlayerToEntity(v, 32) == null) vanish(v);
            return;
        }
        if (!tag.hasKey(OWNER)) return;
        UUID owner;
        try {
            owner = UUID.fromString(tag.getString(OWNER));
        } catch (IllegalArgumentException ex) {
            vanish(v);
            return;
        }
        EntityPlayer p = v.worldObj.func_152378_a(owner);
        boolean idle = v.worldObj.getTotalWorldTime() - tag.getLong(LAST) > STAY_TICKS;
        if (p == null || p.getDistanceSqToEntity(v) > 48 * 48 || idle || ACTIVE.get(owner) != v) {
            if (ACTIVE.get(owner) == v) ACTIVE.remove(owner);
            vanish(v);
        }
    }

    /** Для окна кристалла. */
    public static MerchantMessages.Open openMessage(EntityPlayer p) {
        return new MerchantMessages.Open(ArcanaMerchant.metMask(p), ArcanaMerchant.unlockedMask(p));
    }
}
