package com.divinerpgextended.selftest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.divinerpg.entities.vanilla.EntityEnderSpider;
import net.divinerpg.entities.vanilla.EntityJackOMan;
import net.divinerpg.utils.blocks.VetheaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.events.ArcanaHelper;
import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.TwilightItemsArmor;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.potion.Potion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.Explosion;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.accessory.Accessories;
import com.divinerpgextended.asm.ArcanaHooks;
import com.divinerpgextended.audit.Audit;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossProgress;
import com.divinerpgextended.boss.ProgressSpawns;
import com.divinerpgextended.fishing.FishingLoot;
import com.divinerpgextended.fishing.Rod;
import com.divinerpgextended.fishing.Zone;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.trinket.DoubleJump;
import com.divinerpgextended.trinket.Trinket;
import com.divinerpgextended.vethea.VetheaFloors;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Самопроверка аддона на сервере (только с -Ddre.selftest=true, в обычной игре не включается). Через 2 секунды после
 * старта создаёт тестовых игроков, прогоняет сценарии из таблицы тестов настоящим игровым кодом (удары, события, тики),
 * пишет logs/dre-selftest.txt и dre-selftest.csv (лист;строка;итог;подробности) и выключает сервер.
 */
public class SelfTest {

    public static boolean enabled() {
        return Boolean.getBoolean("dre.selftest");
    }

    public static void start() {
        FMLCommonHandler.instance().bus().register(new SelfTest());
    }

    private int ticks;
    private boolean done;
    private final List<String[]> results = new ArrayList<String[]>();
    private final StringBuilder fishing = new StringBuilder();

    /** Отложенные шаги (тесты, которым нужны следующие тики): номер тика -> шаг. */
    private final java.util.TreeMap<Integer, List<Runnable>> later = new java.util.TreeMap<Integer, List<Runnable>>();

    private void at(int tick, Runnable r) {
        List<Runnable> l = later.get(tick);
        if (l == null) later.put(tick, l = new ArrayList<Runnable>());
        l.add(r);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || done) return;
        ++ticks;
        try {
            if (ticks == 40) runAll();
            List<Runnable> l = later.remove(ticks);
            if (l != null) for (Runnable r : l) r.run();
        } catch (Throwable t) {
            results.add(new String[] { "-", "-", "СБОЙ", "самопроверка упала: " + t });
        }
        if (ticks >= 40 && later.isEmpty()) {
            done = true;
            cleanup();
            write();
            MinecraftServer.getServer().initiateShutdown();
        }
    }

    /** Тестовые игроки, временно добавленные в список подключённых и в мир. */
    private final List<TestPlayer> joined = new ArrayList<TestPlayer>();

    private TestPlayer join(TestPlayer p, boolean inWorld) {
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> list = server.getConfigurationManager().playerEntityList;
        if (!list.contains(p)) list.add(p);
        if (inWorld) p.worldObj.spawnEntityInWorld(p);
        joined.add(p);
        return p;
    }

    private void cleanup() {
        for (TestPlayer p : joined) {
            server.getConfigurationManager().playerEntityList.remove(p);
            if (p.worldObj.playerEntities.contains(p)) p.worldObj.removeEntity(p);
        }
        joined.clear();
        if (prot != null) MinecraftForge.EVENT_BUS.unregister(prot);
    }

    /** Имитация плагина привата: отменяет ломание и клики в "чужой" зоне. */
    public static class Protection {

        int dim, x0, x1, y0, y1, z0, z1;

        boolean inside(net.minecraft.world.World w, int x, int y, int z) {
            return w.provider.dimensionId == dim && x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }

        @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
        public void onBreak(BlockEvent.BreakEvent e) {
            if (inside(e.world, e.x, e.y, e.z)) e.setCanceled(true);
        }

        @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
        public void onInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent e) {
            if (inside(e.world, e.x, e.y, e.z)) e.setCanceled(true);
        }
    }

    private Protection prot;

    // ---------------- Каркас ----------------

    private interface Body {

        String run() throws Exception;
    }

    private static class Fail extends RuntimeException {

        Fail(String m) {
            super(m);
        }
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new Fail(msg);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.06F;
    }

    private void t(String sheet, String row, Body b) {
        try {
            String detail = b.run();
            results.add(new String[] { sheet, row, "работает", detail });
        } catch (Fail f) {
            results.add(new String[] { sheet, row, "баг", f.getMessage() });
        } catch (Throwable x) {
            StackTraceElement[] st = x.getStackTrace();
            results.add(new String[] { sheet, row, "сбой теста", x + (st.length > 0 ? " @ " + st[0] : "") });
        }
    }

    /** Тестовый игрок: в отличие от FakePlayer получает урон и проходит через событие смерти. */
    static class TestPlayer extends FakePlayer {

        boolean died;
        /** Что пришло в чат: ключ перевода (или текст) и текст. */
        final List<String> chat = new ArrayList<String>();
        /** Выданные статистики и ачивки. */
        final List<String> stats = new ArrayList<String>();

        @Override
        public void addChatMessage(net.minecraft.util.IChatComponent c) {
            String key = c instanceof net.minecraft.util.ChatComponentTranslation ? ((net.minecraft.util.ChatComponentTranslation) c).getKey() : "";
            chat.add(key + " | " + c.getUnformattedText());
        }

        @Override
        public void addChatComponentMessage(net.minecraft.util.IChatComponent c) {
            addChatMessage(c);
        }

        @Override
        public void addStat(net.minecraft.stats.StatBase s, int n) {
            if (s != null) stats.add(s.statId);
        }

        boolean heard(String keyOrText) {
            for (String s : chat) if (s.contains(keyOrText)) return true;
            return false;
        }

        TestPlayer(WorldServer w, String name) {
            super(w, new GameProfile(UUID.nameUUIDFromBytes(("dre-selftest-" + name).getBytes(StandardCharsets.UTF_8)), name));
        }

        @Override
        public boolean isEntityInvulnerable() {
            return false;
        }

        @Override
        public boolean canAttackPlayer(EntityPlayer p) {
            return true;
        }

        @Override
        public void onDeath(DamageSource s) {
            if (ForgeHooks.onLivingDeath(this, s)) return;
            died = true;
        }
    }

    private final MinecraftServer server = MinecraftServer.getServer();

    private TestPlayer player(int dim, String name, double x, double y, double z) {
        WorldServer w = server.worldServerForDimension(dim);
        TestPlayer p = new TestPlayer(w, name);
        // соединение-заглушка: пакеты игроку уходят в пустой канал (Forge без диспетчера их молча отбрасывает)
        NetworkManager nm = new NetworkManager(false);
        ReflectionHelper.setPrivateValue(NetworkManager.class, nm, new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter()), "channel", "field_150746_k");
        new NetHandlerPlayServer(server, nm, p);
        ReflectionHelper.setPrivateValue(EntityPlayerMP.class, p, 0, "field_147101_bU");
        p.setPosition(x, y, z);
        w.getChunkFromBlockCoords((int) x, (int) z);
        reset(p);
        return p;
    }

    private static void reset(TestPlayer p) {
        for (int i = 0; i < 4; i++) p.inventory.armorInventory[i] = null;
        for (int i = 0; i < p.inventory.mainInventory.length; i++) p.inventory.mainInventory[i] = null;
        Accessories a = Accessories.of(p);
        for (int i = 0; i < Accessories.SIZE; i++) a.setInventorySlotContents(i, null);
        for (Boss b : Boss.values()) BossProgress.revoke(p, b);
        p.clearActivePotions();
        p.capabilities.isCreativeMode = false;
        p.capabilities.allowFlying = false;
        p.capabilities.isFlying = false;
        p.setHealth(p.getMaxHealth());
        p.hurtResistantTime = 0;
        p.died = false;
        p.fallDistance = 0;
    }

    private static void armor(EntityPlayer p, Item helmet, Item body, Item legs, Item boots) {
        p.inventory.armorInventory[3] = helmet == null ? null : new ItemStack(helmet);
        p.inventory.armorInventory[2] = body == null ? null : new ItemStack(body);
        p.inventory.armorInventory[1] = legs == null ? null : new ItemStack(legs);
        p.inventory.armorInventory[0] = boots == null ? null : new ItemStack(boots);
    }

    private static void hold(EntityPlayer p, ItemStack s) {
        p.inventory.currentItem = 8;
        p.inventory.mainInventory[8] = s;
    }

    private static ItemStack trinket(Trinket t) {
        return new ItemStack(ModItems.trinket, 1, t.ordinal());
    }

    private static float hp(EntityLivingBase e) {
        return e.getHealth() + e.getAbsorptionAmount();
    }

    /** Урон по цели: здоровье до минус после (минус - значит вылечили). */
    private static float hit(EntityLivingBase victim, DamageSource s, float amount) {
        victim.hurtResistantTime = 0;
        float before = hp(victim);
        victim.attackEntityFrom(s, amount);
        return before - hp(victim);
    }

    private EntityIronGolem golem(WorldServer w, double x, double y, double z) {
        EntityIronGolem g = new EntityIronGolem(w);
        g.setPosition(x, y, z);
        w.spawnEntityInWorld(g);
        return g;
    }

    private static float golemHit(EntityIronGolem g, EntityPlayer p, float amount) {
        g.setHealth(g.getMaxHealth() / 2);
        return hit(g, DamageSource.causePlayerDamage(p), amount);
    }

    private static Object call(Class<?> c, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = c.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static int count(EntityPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) if (s != null && s.getItem() == item) n += s.stackSize;
        return n;
    }

    private static int countTrinket(EntityPlayer p, Trinket t) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) if (s != null && s.getItem() == ModItems.trinket && s.getItemDamage() == t.ordinal()) n++;
        return n;
    }

    private static void tick(EntityPlayer p, int times) {
        for (int i = 0; i < times; i++) {
            p.ticksExisted++;
            FMLCommonHandler.instance().bus().post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
        }
    }

    private static String f(float v) {
        return String.format("%.2f", v);
    }

    // ---------------- Сценарии ----------------

    private void runAll() throws Exception {
        WorldServer ow = server.worldServerForDimension(0);
        time(ow, 6000);
        int sx = ow.getSpawnPoint().posX, sz = ow.getSpawnPoint().posZ, sy = ow.getTopSolidOrLiquidBlock(sx, sz) + 1;
        final TestPlayer p = player(0, "TestA", sx, sy, sz);
        final TestPlayer q = player(0, "TestB", sx + 2, sy, sz);
        final EntityIronGolem g = golem(ow, sx + 1, sy, sz + 1);

        armorTests(p, g, ow, sx, sy, sz);
        accessoryTests(p, q, g, ow);
        progressTests(p, ow, sx, sy, sz);
        tradeTests(p, ow, sx, sy, sz);
        arcanaTests(p);
        twilightTests();
        vetheaTests(p, ow);
        serverTests(p, q);
        auditTests(p);
        moreTests(p, q, g, ow, sx, sy, sz);
        fishingReport();
        g.setDead();
    }

    private void armorTests(final TestPlayer p, final EntityIronGolem g, final WorldServer ow, final int sx, final int sy, final int sz) {
        t("Броня", "Бонус урона сета", () -> {
            reset(p);
            float none = golemHit(g, p, 1);
            armor(p, VanillaItemsArmor.divineHelmet, VanillaItemsArmor.divineBody, VanillaItemsArmor.divineLegs, VanillaItemsArmor.divineBoots);
            float divine = golemHit(g, p, 1);
            armor(p, TwilightItemsArmor.haliteHelmet, TwilightItemsArmor.haliteChestplate, TwilightItemsArmor.haliteLeggings, TwilightItemsArmor.haliteBoots);
            float halite = golemHit(g, p, 1);
            check(near(none, 1) && near(divine, 7) && near(halite, 17), "кулак: без сета " + f(none) + ", Divine " + f(divine) + ", Halite " + f(halite) + " (ждали 1/7/17)");
            return "кулак 1 > Divine 7, Halite 17";
        });
        t("Броня", "Santa в Айсике", () -> {
            reset(p);
            armor(p, IceikaItems.santaCap, IceikaItems.santaTunic, IceikaItems.santaPants, IceikaItems.santaBoots);
            float over = golemHit(g, p, 1);
            WorldServer ice = server.worldServerForDimension(ConfigurationHelper.iceika);
            TestPlayer pi = player(ConfigurationHelper.iceika, "TestIce", 0, 120, 0);
            armor(pi, IceikaItems.santaCap, IceikaItems.santaTunic, IceikaItems.santaPants, IceikaItems.santaBoots);
            EntityIronGolem gi = golem(ice, 1, 120, 1);
            float iced = golemHit(gi, pi, 1);
            EntityZombie z = new EntityZombie(ice);
            float takenIce = hit(pi, DamageSource.causeMobDamage(z), 10);
            pi.setHealth(pi.getMaxHealth());
            EntityZombie zo = new EntityZombie(ow);
            float takenOver = hit(p, DamageSource.causeMobDamage(zo), 10);
            gi.setDead();
            check(near(over, 1) && near(iced, 7), "удар: вне Айсики " + f(over) + ", в Айсике " + f(iced) + " (ждали 1 и 7)");
            check(takenIce < takenOver * 0.2F, "входящий урон в Айсике " + f(takenIce) + " не меньше x0.2 от " + f(takenOver));
            return "+6 к удару только в Айсике; входящий урон " + f(takenOver) + " > " + f(takenIce) + " (x" + f(takenIce / takenOver) + ")";
        });
        t("Броня", "Защита сетов", () -> {
            reset(p);
            DamageSource boom = new DamageSource("explosion").setExplosion();
            armor(p, VanillaItemsArmor.enderHelmet, VanillaItemsArmor.enderBody, VanillaItemsArmor.enderLegs, VanillaItemsArmor.enderBoots);
            float ender = hit(p, boom, 10);
            armor(p, VanillaItemsArmor.bedrockHelmet, VanillaItemsArmor.bedrockBody, VanillaItemsArmor.bedrockLegs, VanillaItemsArmor.bedrockBoots);
            float bedrock = hit(p, boom, 10);
            armor(p, VanillaItemsArmor.witherReaperHelmet, VanillaItemsArmor.witherReaperBody, VanillaItemsArmor.witherReaperLegs, VanillaItemsArmor.witherReaperBoots);
            float wither = hit(p, DamageSource.wither, 4);
            armor(p, VanillaItemsArmor.krakenHelmet, VanillaItemsArmor.krakenBody, VanillaItemsArmor.krakenLegs, VanillaItemsArmor.krakenBoots);
            float drown = hit(p, DamageSource.drown, 4);
            armor(p, VanillaItemsArmor.jungleHelmet, VanillaItemsArmor.jungleBody, VanillaItemsArmor.jungleLegs, VanillaItemsArmor.jungleBoots);
            float poison = hit(p, DamageSource.magic, 4);
            armor(p, TwilightItemsArmor.apalachiaHelmet, TwilightItemsArmor.apalachiaChestplate, TwilightItemsArmor.apalachiaLeggings, TwilightItemsArmor.apalachiaBoots);
            float cactus = hit(p, DamageSource.cactus, 4);
            check(ender == 0 && bedrock == 0 && wither == 0 && drown == 0 && poison == 0 && cactus == 0, "урон: Ender " + f(ender) + ", Bedrock " + f(bedrock)
                + ", Wither Reaper " + f(wither) + ", Kraken " + f(drown) + ", Jungle " + f(poison) + ", Apalachia " + f(cactus));
            return "взрыв (Ender, Bedrock), иссушение, утопление, яд, кактус - урон 0";
        });
        t("Броня", "Arlemite и Rupee", () -> {
            reset(p);
            EntityZombie z = new EntityZombie(ow);
            EntityArrow arrow = new EntityArrow(ow, z, 1.0F);
            armor(p, VanillaItemsArmor.arlemiteHelmet, VanillaItemsArmor.arlemiteBody, VanillaItemsArmor.arlemiteLegs, VanillaItemsArmor.arlemiteBoots);
            float aArrow = hit(p, DamageSource.causeArrowDamage(arrow, z), 10);
            p.setHealth(p.getMaxHealth());
            float aMelee = hit(p, DamageSource.causeMobDamage(z), 10);
            p.setHealth(p.getMaxHealth());
            armor(p, VanillaItemsArmor.rupeeHelmet, VanillaItemsArmor.rupeeBody, VanillaItemsArmor.rupeeLegs, VanillaItemsArmor.rupeeBoots);
            float rArrow = hit(p, DamageSource.causeArrowDamage(arrow, z), 10);
            p.setHealth(p.getMaxHealth());
            float rMelee = hit(p, DamageSource.causeMobDamage(z), 10);
            p.setHealth(p.getMaxHealth());
            check(Math.abs(aArrow / aMelee - 0.3F) < 0.05F, "Arlemite: стрела " + f(aArrow) + " / удар " + f(aMelee) + " (ждали x0.3)");
            check(Math.abs(rMelee / rArrow - 0.3F) < 0.05F, "Rupee: удар " + f(rMelee) + " / стрела " + f(rArrow) + " (ждали x0.3)");
            return "Arlemite: стрела x" + f(aArrow / aMelee) + " к удару; Rupee: удар моба x" + f(rMelee / rArrow) + " к стреле";
        });
        t("Броня", "Сет из разных цветов", () -> {
            reset(p);
            armor(p, VanillaItemsArmor.redEnderHelmet, VanillaItemsArmor.blueEnderBody, VanillaItemsArmor.yellowEnderLegs, VanillaItemsArmor.enderBoots);
            float d = hit(p, new DamageSource("explosion").setExplosion(), 10);
            check(d == 0, "смешанный Ender: урон от взрыва " + f(d));
            return "Ender четырёх цветов: взрыв 0";
        });
        t("Броня", "Сет без одной вещи", () -> {
            reset(p);
            armor(p, null, VanillaItemsArmor.divineBody, VanillaItemsArmor.divineLegs, VanillaItemsArmor.divineBoots);
            float d = golemHit(g, p, 1);
            armor(p, null, VanillaItemsArmor.bedrockBody, VanillaItemsArmor.bedrockLegs, VanillaItemsArmor.bedrockBoots);
            float b = hit(p, new DamageSource("explosion").setExplosion(), 10);
            check(near(d, 1) && b > 0, "Divine без шлема: удар " + f(d) + " (ждали 1); Bedrock без шлема: взрыв " + f(b) + " (ждали > 0)");
            return "без шлема: бонуса и защиты нет";
        });
        t("Броня", "Vemos лечит выше 10 сердец", () -> {
            reset(p);
            UUID id = UUID.fromString("0d0d0d0d-0000-4000-8000-000000000001");
            p.getEntityAttribute(SharedMonsterAttributes.maxHealth).applyModifier(new AttributeModifier(id, "selftest", 8, 0));
            try {
                armor(p, ArcanaItems.vemosHelmet, ArcanaItems.vemosBody, ArcanaItems.vemosLegs, ArcanaItems.vemosBoots);
                p.setHealth(21);
                tick(p, 20);
                float h = p.getHealth();
                check(h > 22.5F, "за 20 тиков здоровье 21 > " + f(h) + " (ждали около 23)");
                return "28 макс: 21 > " + f(h) + " за секунду";
            } finally {
                p.getEntityAttribute(SharedMonsterAttributes.maxHealth).removeModifier(p.getEntityAttribute(SharedMonsterAttributes.maxHealth).getModifier(id));
            }
        });
        t("Броня", "Wildwood в воде выше 10 сердец", () -> {
            reset(p);
            UUID id = UUID.fromString("0d0d0d0d-0000-4000-8000-000000000002");
            p.getEntityAttribute(SharedMonsterAttributes.maxHealth).applyModifier(new AttributeModifier(id, "selftest", 8, 0));
            int x = (int) Math.floor(p.posX), y = (int) Math.floor(p.posY), z = (int) Math.floor(p.posZ);
            Block b0 = ow.getBlock(x, y, z), b1 = ow.getBlock(x, y + 1, z);
            try {
                ow.setBlock(x, y, z, Blocks.water);
                ow.setBlock(x, y + 1, z, Blocks.water);
                armor(p, TwilightItemsArmor.wildwoodHelmet, TwilightItemsArmor.wildwoodChestplate, TwilightItemsArmor.wildwoodLeggings, TwilightItemsArmor.wildwoodBoots);
                p.setHealth(21);
                tick(p, 8);
                float h = p.getHealth();
                check(h > 22.5F, "в воде за 8 тиков 21 > " + f(h));
                return "в воде 21 > " + f(h) + " за 8 тиков";
            } finally {
                ow.setBlock(x, y, z, b0);
                ow.setBlock(x, y + 1, z, b1);
                p.getEntityAttribute(SharedMonsterAttributes.maxHealth).removeModifier(p.getEntityAttribute(SharedMonsterAttributes.maxHealth).getModifier(id));
            }
        });
    }

    private void accessoryTests(final TestPlayer p, final TestPlayer q, final EntityIronGolem g, final WorldServer ow) {
        t("Аксессуары", "Открытие слотов", () -> {
            reset(p);
            int[] n = new int[5];
            n[0] = Accessories.unlocked(p);
            Boss[] order = { Boss.LICH_KING, Boss.WATCHER, Boss.TWILIGHT_DEMON, Boss.KAROT };
            for (int i = 0; i < 4; i++) {
                BossProgress.grant(p, order[i]);
                n[i + 1] = Accessories.unlocked(p);
            }
            check(n[0] == 2 && n[1] == 3 && n[2] == 4 && n[3] == 5 && n[4] == 6, "слотов: " + n[0] + ">" + n[1] + ">" + n[2] + ">" + n[3] + ">" + n[4]);
            return "2 > 3 > 4 > 5 > 6";
        });
        t("Аксессуары", "Только из слотов", () -> {
            reset(p);
            p.inventory.mainInventory[5] = trinket(Trinket.BLUE_FLAME);
            boolean inBag = Accessories.wears(p, Trinket.BLUE_FLAME);
            Accessories.of(p).setInventorySlotContents(0, trinket(Trinket.BLUE_FLAME));
            boolean inSlot = Accessories.wears(p, Trinket.BLUE_FLAME);
            Accessories.of(p).setInventorySlotContents(0, null);
            Accessories.of(p).setInventorySlotContents(4, trinket(Trinket.BLUE_FLAME));
            boolean locked = Accessories.wears(p, Trinket.BLUE_FLAME);
            check(!inBag && inSlot && !locked, "в рюкзаке " + inBag + ", в слоте " + inSlot + ", в закрытом слоте " + locked);
            return "в рюкзаке и закрытом слоте не работает, в открытом работает";
        });
        t("Аксессуары", "Два одинаковых", () -> {
            reset(p);
            time(ow, 18000);
            Accessories.of(p).setInventorySlotContents(0, trinket(Trinket.JACK_AMULET));
            float one = golemHit(g, p, 1);
            Accessories.of(p).setInventorySlotContents(1, trinket(Trinket.JACK_AMULET));
            float two = golemHit(g, p, 1);
            time(ow, 6000);
            check(near(one, 1.15F) && near(two, 1.15F), "один амулет " + f(one) + ", два " + f(two) + " (ждали 1.15 оба)");
            return "ночью x1.15 и с одним, и с двумя";
        });
        t("Аксессуары", "Объединённый и его часть", () -> {
            reset(p);
            Accessories.of(p).setInventorySlotContents(0, trinket(Trinket.STEADFAST_BELT));
            Accessories.of(p).setInventorySlotContents(1, trinket(Trinket.BLAST_BELT));
            float d = hit(p, new DamageSource("explosion").setExplosion(), 8);
            check(near(d, 1.76F), "взрыв 8 > " + f(d) + " (ждали 1.76: пояс x0.25 один раз и наплечник x0.88)");
            return "взрыв 8 > 1.76: пояс x0.25 не удваивается (+ наплечник из Несокрушимого пояса x0.88)";
        });
        t("Аксессуары", "Слот закрылся с вещью", () -> {
            reset(p);
            BossProgress.grant(p, Boss.LICH_KING);
            Accessories.of(p).setInventorySlotContents(2, trinket(Trinket.BLUE_FLAME));
            boolean a = Accessories.wears(p, Trinket.BLUE_FLAME);
            BossProgress.revoke(p, Boss.LICH_KING);
            boolean b = Accessories.wears(p, Trinket.BLUE_FLAME);
            boolean kept = Accessories.of(p).getStackInSlot(2) != null;
            BossProgress.grant(p, Boss.LICH_KING);
            boolean c = Accessories.wears(p, Trinket.BLUE_FLAME);
            check(a && !b && kept && c, "открыт " + a + ", закрыт " + b + ", вещь на месте " + kept + ", снова открыт " + c);
            return "закрытый слот не работает, вещь не пропадает";
        });
        t("Аксессуары", "Смерть без keepInventory", () -> {
            reset(p);
            Accessories.of(p).setInventorySlotContents(0, trinket(Trinket.VALOR_MEDAL));
            // как при возрождении: новый объект игрока, данные переносит clonePlayer
            TestPlayer clone = new TestPlayer(ow, "TestA");
            clone.clonePlayer(p, false);
            ItemStack s = Accessories.of(clone).getStackInSlot(0);
            check(s != null && s.getItemDamage() == Trinket.VALOR_MEDAL.ordinal(), "после смерти слот 0: " + s);
            return "после возрождения аксессуар в слоте";
        });
        t("Аксессуары", "Анх фараона", () -> {
            reset(p);
            Accessories.of(p).setInventorySlotContents(0, trinket(Trinket.PHARAOH_ANKH));
            p.getEntityData().removeTag("dreAnkhCd");
            p.setHealth(2);
            p.attackEntityFrom(DamageSource.magic, 50);
            float after = p.getHealth();
            check(!p.died && near(after, p.getMaxHealth() / 2), "после смертельного удара: умер " + p.died + ", здоровье " + f(after));
            return "смертельный удар > половина здоровья";
        });
        t("Аксессуары", "Анх два раза подряд", () -> {
            reset(p);
            Accessories.of(p).setInventorySlotContents(0, trinket(Trinket.PHARAOH_ANKH));
            p.getEntityData().removeTag("dreAnkhCd");
            p.setHealth(2);
            p.attackEntityFrom(DamageSource.magic, 50);
            p.hurtResistantTime = 0;
            p.attackEntityFrom(DamageSource.magic, 50);
            check(p.died, "второй смертельный удар подряд не убил (Анх сработал дважды)");
            return "второй раз Анх не спасает";
        });
        t("Аксессуары", "С боссов", () -> {
            reset(p);
            persisted(p).removeTag("dre_acc_got");
            com.divinerpgextended.trinket.AccessorySources.reward(p, Trinket.CARCINUS_SHELL);
            int first = countTrinket(p, Trinket.CARCINUS_SHELL);
            for (int i = 0; i < p.inventory.mainInventory.length; i++) p.inventory.mainInventory[i] = null;
            int got = 0;
            for (int i = 0; i < 2000; i++) {
                com.divinerpgextended.trinket.AccessorySources.reward(p, Trinket.CARCINUS_SHELL);
                got += countTrinket(p, Trinket.CARCINUS_SHELL);
                for (int k = 0; k < p.inventory.mainInventory.length; k++) p.inventory.mainInventory[k] = null;
            }
            check(first == 1 && got > 140 && got < 270, "первая победа: " + first + ", повторные: " + got + " из 2000 (ждали около 200)");
            return "первая победа наверняка; повторные " + got + " из 2000 (" + (got / 20.0) + "%)";
        });
        t("Аксессуары", "Объединение", () -> {
            reset(p);
            hold(p, trinket(Trinket.BLAST_BELT));
            p.inventory.mainInventory[3] = trinket(Trinket.COLOSSUS_PAULDRON);
            p.inventory.mainInventory[4] = new ItemStack(IceikaItems.snowflake, 16);
            Class<?> as = com.divinerpgextended.trinket.AccessorySources.class;
            call(as, "combine", new Class<?>[] { EntityPlayer.class }, p);
            call(as, "combine", new Class<?>[] { EntityPlayer.class }, p);
            check(countTrinket(p, Trinket.STEADFAST_BELT) == 1 && countTrinket(p, Trinket.BLAST_BELT) == 0
                && countTrinket(p, Trinket.COLOSSUS_PAULDRON) == 0 && count(p, IceikaItems.snowflake) == 0,
                "пояс " + countTrinket(p, Trinket.STEADFAST_BELT) + ", части " + countTrinket(p, Trinket.BLAST_BELT) + "/"
                    + countTrinket(p, Trinket.COLOSSUS_PAULDRON) + ", снежинок " + count(p, IceikaItems.snowflake));
            return "Пояс + Наплечник + 16 снежинок = Несокрушимый пояс";
        });
        t("Аксессуары", "Двойной прыжок", () -> {
            reset(p);
            Accessories.of(p).setInventorySlotContents(0, trinket(Trinket.AYERACO_WING));
            int a = DoubleJump.allowed(p);
            check(a == 1, "прыжков в воздухе в обычном мире: " + a);
            return "только серверная часть: в обычном мире 1 прыжок в воздухе (сам прыжок - на клиенте)";
        });
    }

    private void progressTests(final TestPlayer p, final WorldServer ow, final int sx, final int sy, final int sz) {
        t("Прохождение", "Ранги мобов", () -> {
            reset(p);
            int[] r = new int[4];
            r[0] = ProgressSpawns.rank(p);
            BossProgress.grant(p, Boss.CARCINUS);
            r[1] = ProgressSpawns.rank(p);
            BossProgress.grant(p, Boss.LICH_KING);
            r[2] = ProgressSpawns.rank(p);
            BossProgress.grant(p, Boss.ANCIENT_ENTITY);
            r[3] = ProgressSpawns.rank(p);
            EntityEnderSpider spider = new EntityEnderSpider(ow);
            float high = hit(p, DamageSource.causeMobDamage(spider), 4);
            for (Boss b : Boss.values()) BossProgress.revoke(p, b);
            p.setHealth(p.getMaxHealth());
            float low = hit(p, DamageSource.causeMobDamage(spider), 4);
            check(r[0] == 0 && r[1] == 1 && r[2] == 2 && r[3] == 3, "ранги " + r[0] + r[1] + r[2] + r[3]);
            check(low == 0 && high > 0, "эндер-паук (ранг 3): по рангу 0 урон " + f(low) + ", по рангу 3 урон " + f(high));
            return "ранги 0-1-2-3; эндер-паук не бьёт ранг 0, бьёт ранг 3";
        });
    }

    private void tradeTests(final TestPlayer p, final WorldServer ow, final int sx, final int sy, final int sz) {
        final Class<?> jack = com.divinerpgextended.trinket.JackOMan.class;
        t("Торговля", "Этапы", () -> {
            reset(p);
            int[] r = new int[4];
            r[0] = (Integer) call(jack, "tier", new Class<?>[] { EntityPlayer.class }, p);
            BossProgress.grant(p, Boss.CARCINUS);
            r[1] = (Integer) call(jack, "tier", new Class<?>[] { EntityPlayer.class }, p);
            BossProgress.grant(p, Boss.LICH_KING);
            r[2] = (Integer) call(jack, "tier", new Class<?>[] { EntityPlayer.class }, p);
            BossProgress.grant(p, Boss.ANCIENT_ENTITY);
            r[3] = (Integer) call(jack, "tier", new Class<?>[] { EntityPlayer.class }, p);
            check(r[0] == 0 && r[1] == 1 && r[2] == 2 && r[3] == 3, "этапы " + r[0] + r[1] + r[2] + r[3]);
            return "этап Джека 0 > 1 (Карцинус) > 2 (Лич) > 3 (Древняя сущность)";
        });
        t("Торговля", "Скупка хлама", () -> {
            int bone = (Integer) call(jack, "junkRate", new Class<?>[] { Item.class }, Items.bone);
            int string = (Integer) call(jack, "junkRate", new Class<?>[] { Item.class }, Items.string);
            int gun = (Integer) call(jack, "junkRate", new Class<?>[] { Item.class }, Items.gunpowder);
            int eye = (Integer) call(jack, "junkRate", new Class<?>[] { Item.class }, Items.spider_eye);
            check(bone == 32 && string == 24 && gun == 16 && eye == 12, "курс " + bone + "/" + string + "/" + gun + "/" + eye);
            return "32 кости / 24 нити / 16 пороха / 12 глаз за монету";
        });
        t("Торговля", "Ровно 6 монет", () -> {
            reset(p);
            time(ow, 18000);
            p.getEntityData().removeTag("dreJackNight");
            int[] stacks = new int[4];
            for (int i = 0; i < 4; i++) {
                ItemStack bones = new ItemStack(Items.bone, 64);
                hold(p, bones);
                call(jack, "sellJunk", new Class<?>[] { EntityPlayer.class, ItemStack.class }, p, bones);
                stacks[i] = p.inventory.mainInventory[8] == null ? 0 : p.inventory.mainInventory[8].stackSize;
            }
            int coins = count(p, VanillaItemsOther.shadowCoins);
            time(ow, 6000);
            check(coins == 6 && stacks[3] == 64, "монет " + coins + ", четвёртая пачка осталась " + stacks[3] + " (ждали 6 и 64)");
            return "3 пачки по 64 кости = 6 монет, четвёртую Джек не берёт";
        });
        t("Торговля", "Только ночью", () -> {
            reset(p);
            EntityJackOMan j = new EntityJackOMan(ow);
            j.setPosition(p.posX + 1, p.posY, p.posZ);
            time(ow, 6000);
            EntityInteractEvent day = new EntityInteractEvent(p, j);
            boolean dayCancel = MinecraftForge.EVENT_BUS.post(day);
            time(ow, 18000);
            EntityInteractEvent night = new EntityInteractEvent(p, j);
            boolean nightCancel = MinecraftForge.EVENT_BUS.post(night);
            time(ow, 6000);
            check(dayCancel && !nightCancel, "днём отменено " + dayCancel + ", ночью отменено " + nightCancel);
            return "днём не торгует, ночью окно открывается";
        });
    }

    private void arcanaTests(final TestPlayer p) {
        t("Аркана", "Аркана после смены мира", () -> {
            reset(p);
            persisted(p).setInteger(ArcanaHooks.TASKS, 5);
            ArcanaHelper a = ArcanaHelper.getProperties(p);
            check(a != null, "у тестового игрока нет арканы Divine");
            try {
                a.setBarValue(100);
            } catch (Throwable ignored) {}
            try {
                FMLCommonHandler.instance().bus().post(new PlayerEvent.PlayerChangedDimensionEvent(p, 56, 0));
            } catch (Throwable ignored) {}
            float v = a.getBarValue();
            persisted(p).removeTag(ArcanaHooks.TASKS);
            check(near(v, 325), "после смены мира аркана " + f(v) + " (ждали 325 = максимум с 5 исследованиями)");
            return "максимум 325, после смены мира полная 325 (Divine ставил 200)";
        });
    }

    private void twilightTests() {
        t("Сумеречные миры", "Запас растёт", () -> {
            TestPlayer e = player(ConfigurationHelper.eden, "TestEden", 0, 100, 0);
            Accessories.of(e).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            int[] v = new int[6];
            v[0] = DoubleJump.carpetTicks(e);
            Boss[] seq = { Boss.CADILLION_EDEN, Boss.CADILLION_WILDWOOD, Boss.CADILLION_APALACHIA, Boss.CADILLION_SKYTHERN, Boss.TWILIGHT_DEMON };
            for (int i = 0; i < 5; i++) {
                BossProgress.grant(e, seq[i]);
                v[i + 1] = DoubleJump.carpetTicks(e);
            }
            check(v[0] == 60 && v[1] == 80 && v[2] == 100 && v[3] == 140 && v[4] == 200 && v[5] == -1,
                "тиков: " + v[0] + "/" + v[1] + "/" + v[2] + "/" + v[3] + "/" + v[4] + "/" + v[5]);
            return "3 > 4 > 5 > 7 > 10 с > без предела";
        });
        t("Сумеречные миры", "Отозвать Сумеречного демона", () -> {
            TestPlayer e = player(ConfigurationHelper.eden, "TestEden", 0, 100, 0);
            Accessories.of(e).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            BossProgress.grant(e, Boss.CADILLION_SKYTHERN);
            BossProgress.grant(e, Boss.TWILIGHT_DEMON);
            int a = DoubleJump.carpetTicks(e);
            BossProgress.revoke(e, Boss.TWILIGHT_DEMON);
            int b = DoubleJump.carpetTicks(e);
            check(a == -1 && b == 200, "с демоном " + a + ", после revoke " + b);
            return "без предела > 10 с";
        });
        t("Сумеречные миры", "Только в Сумеречных", () -> {
            TestPlayer o = player(0, "TestOver", 0, 100, 0);
            Accessories.of(o).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            int c = DoubleJump.carpetTicks(o);
            check(c == 0, "в обычном мире запас ковра " + c);
            return "в обычном мире ковёр не работает";
        });
        t("Сумеречные миры", "Сумеречные миры", () -> {
            TestPlayer e = player(ConfigurationHelper.eden, "TestEden", 0, 100, 0);
            Accessories.of(e).setInventorySlotContents(0, trinket(Trinket.AYERACO_WING));
            int a = DoubleJump.allowed(e);
            check(a == 2, "прыжков в воздухе в Эдеме " + a);
            return "в Эдеме 2 прыжка в воздухе (серверная часть)";
        });
    }

    private void vetheaTests(final TestPlayer p, final WorldServer ow) throws Exception {
        final int vd = ConfigurationHelper.vethea;
        final WorldServer vw = server.worldServerForDimension(vd);
        final TestPlayer v = player(vd, "TestDream", 8, 17, 8);
        final TestPlayer v2 = player(vd, "TestDream2", 10, 17, 8);
        final EntityZombie z = new EntityZombie(vw);
        z.setPosition(9, 17, 9);
        vw.spawnEntityInWorld(z);
        final DamageSource mob = DamageSource.causeMobDamage(z);
        t("Ветея - кошмар", "Вещи не забираются", () -> {
            check(!com.divinerpgextended.vethea.VetheaStash.divineSwap(), "отдельный инвентарь Divine включён");
            return "отдельный инвентарь Divine выключен аддоном";
        });
        t("Ветея - кошмар", "Обмен инвентаря без настройки", () -> {
            // байты классов Divine как в jar: патч должен найти обмен и в 1.4.2.3, и в 1.4.1.5 (там настройки нет)
            for (String c : new String[] { "BlockNightmareBed", "BlockVetheaPortal" }) {
                String res = "/net/divinerpg/blocks/vethea/" + c + ".class";
                java.io.InputStream in = net.divinerpg.utils.config.ConfigurationHelper.class.getResourceAsStream(res);
                check(in != null, "нет " + res);
                byte[] raw = org.apache.commons.io.IOUtils.toByteArray(in);
                in.close();
                byte[] out = new com.divinerpgextended.asm.VetheaSwapTransformer().transform(c,
                    "net.divinerpg.blocks.vethea." + c, raw);
                check(out != raw, c + ": обмен инвентаря не найден");
            }
            // заблокированный обмен: инвентарь не сохраняется в копию, не чистится и не подменяется
            TestPlayer w = player(0, "TestSwap", 40, 70, 40);
            w.inventory.setInventorySlotContents(3, new ItemStack(Items.diamond, 5));
            NBTTagList copy = com.divinerpgextended.asm.VetheaSwapHooks.write(w.inventory, new NBTTagList());
            int cleared = com.divinerpgextended.asm.VetheaSwapHooks.clear(w.inventory, null, -1);
            NBTTagList other = new NBTTagList();
            NBTTagCompound dirt = new NBTTagCompound();
            new ItemStack(Blocks.dirt).writeToNBT(dirt);
            dirt.setByte("Slot", (byte) 3);
            other.appendTag(dirt);
            com.divinerpgextended.asm.VetheaSwapHooks.read(w.inventory, other);
            ItemStack left = w.inventory.getStackInSlot(3);
            check(com.divinerpgextended.asm.VetheaSwapHooks.blocked && copy.tagCount() == 0 && cleared == 0 && left != null
                && left.getItem() == Items.diamond && left.stackSize == 5, "копия " + copy.tagCount() + ", очищено " + cleared
                    + ", в слоте " + left);
            return "патч нашёл обмен инвентаря в кровати и портале, заблокированный обмен ничего не трогает";
        });
        t("Ветея - кошмар", "Чужое оружие лечит", () -> {
            reset(v);
            hold(v, new ItemStack(Items.diamond_sword));
            z.setHealth(10);
            float d = hit(z, DamageSource.causePlayerDamage(v), 7);
            check(d < 0, "алмазный меч: здоровье зомби изменилось на " + f(-d));
            return "алмазный меч лечит зомби на " + f(-d);
        });
        t("Ветея - кошмар", "Ветейское оружие бьёт", () -> {
            reset(v);
            hold(v, new ItemStack(VetheaItems.teakerSword));
            z.setHealth(20);
            float d = hit(z, DamageSource.causePlayerDamage(v), 7);
            check(d > 0, "Teaker Backsword: урон " + f(d));
            return "Teaker Backsword бьёт на " + f(d);
        });
        t("Ветея - кошмар", "Кулак и блок в руке", () -> {
            reset(v);
            z.setHealth(20);
            float fist = hit(z, DamageSource.causePlayerDamage(v), 1);
            hold(v, new ItemStack(Blocks.dirt, 10));
            float block = hit(z, DamageSource.causePlayerDamage(v), 1);
            check(fist > 0 && block > 0, "кулак " + f(fist) + ", блок " + f(block));
            return "кулак и блок бьют обычно";
        });
        t("Ветея - кошмар", "Чужой лук и посохи", () -> {
            reset(v);
            hold(v, new ItemStack(Items.bow));
            z.setHealth(10);
            float d = hit(z, DamageSource.causeArrowDamage(new EntityArrow(vw, v, 1.0F), v), 5);
            check(d < 0, "стрела из обычного лука: изменение " + f(-d));
            return "стрела из обычного лука лечит";
        });
        t("Ветея - кошмар", "Подмена в руке", () -> {
            reset(v);
            hold(v, new ItemStack(Items.diamond_sword));
            z.setHealth(10);
            float d = hit(z, DamageSource.causeArrowDamage(new EntityArrow(vw, v, 1.0F), v), 5);
            check(d < 0, "стрела, в руке алмазный меч: изменение " + f(-d));
            return "считается по вещи в руке в момент попадания - лечит";
        });
        t("Ветея - кошмар", "Чужая броня ранит", () -> {
            reset(v);
            float naked = hit(v, mob, 10);
            v.setHealth(v.getMaxHealth());
            armor(v, Items.leather_helmet, Items.leather_chestplate, Items.leather_leggings, Items.leather_boots);
            float leather = hit(v, mob, 10);
            v.setHealth(v.getMaxHealth());
            armor(v, Items.diamond_helmet, Items.diamond_chestplate, Items.diamond_leggings, Items.diamond_boots);
            float diamond = hit(v, mob, 10);
            v.setHealth(v.getMaxHealth());
            check(near(naked, 10) && near(leather, 12.8F) && near(diamond, 18), "урон 10: голый " + f(naked) + ", кожа " + f(leather) + ", алмаз " + f(diamond)
                + " (ждали 10 / 12.8 / 18)");
            return "удар 10: голый 10, кожа 12.8, алмаз 18";
        });
        t("Ветея - кошмар", "Ветейская броня защищает", () -> {
            reset(v);
            armor(v, VetheaItems.degradedHelmet, VetheaItems.degradedBody, VetheaItems.degradedLegs, VetheaItems.degradedBoots);
            float d = hit(v, mob, 10);
            check(d < 10, "Degraded: урон " + f(d));
            return "Degraded: удар 10 > " + f(d);
        });
        t("Ветея - кошмар", "Смешанная броня", () -> {
            reset(v);
            armor(v, VetheaItems.degradedHelmet, Items.diamond_chestplate, Items.diamond_leggings, VetheaItems.degradedBoots);
            float d = hit(v, mob, 10);
            check(near(d, 15.6F), "2 ветейские + алмазные нагрудник и поножи: урон " + f(d) + " (ждали 15.6 = x1.56, бонуса сета нет)");
            return "усиливают только чужие вещи: x1.56";
        });
        t("Ветея - кошмар", "Чужая еда", () -> {
            reset(v);
            food(v, 10, 5);
            eat(v, new ItemStack(Items.bread));
            int food = v.getFoodStats().getFoodLevel();
            check(food == 5 && v.isPotionActive(Potion.hunger), "голод 10 + хлеб > " + food + " (ждали 5), эффект голода " + v.isPotionActive(Potion.hunger));
            return "голод 10 > хлеб > 5 и эффект голода";
        });
        t("Ветея - кошмар", "Своя еда", () -> {
            reset(v);
            food(v, 10, 5);
            eat(v, new ItemStack(VetheaItems.dreamCarrot));
            int food = v.getFoodStats().getFoodLevel();
            check(food == 13, "голод 10 + Dream Carrot > " + food + " (ждали 13)");
            return "Dream Carrot кормит: 10 > 13";
        });
        t("Ветея - кошмар", "Еда на пустой голод", () -> {
            reset(v);
            food(v, 1, 0);
            eat(v, new ItemStack(Items.bread));
            int food = v.getFoodStats().getFoodLevel();
            check(food == 0, "голод 1 + хлеб > " + food);
            return "голод не уходит в минус: 0";
        });
        t("Ветея - кошмар", "Золотое яблоко", () -> {
            reset(v);
            food(v, 10, 5);
            eat(v, new ItemStack(Items.golden_apple));
            int food = v.getFoodStats().getFoodLevel();
            check(food < 10 && v.isPotionActive(Potion.regeneration), "голод " + food + ", регенерация " + v.isPotionActive(Potion.regeneration));
            return "сытость падает (" + food + "), регенерация остаётся";
        });
        t("Ветея - кошмар", "Чужие зелья", () -> {
            reset(v);
            v.setHealth(20);
            eat(v, new ItemStack(Items.potionitem, 1, 8197));
            float afterHeal = v.getHealth();
            eat(v, new ItemStack(Items.potionitem, 1, 8194));
            boolean slow = v.isPotionActive(Potion.moveSlowdown), fast = v.isPotionActive(Potion.moveSpeed);
            check(afterHeal < 20 && slow && !fast, "лечение: здоровье 20 > " + f(afterHeal) + "; скорость: замедление " + slow + ", скорость " + fast);
            return "лечение ранит (20 > " + f(afterHeal) + "), скорость > замедление";
        });
        t("Ветея - кошмар", "Зелье лечения при малом здоровье", () -> {
            reset(v);
            v.setHealth(6);
            v.hurtResistantTime = 0;
            eat(v, new ItemStack(Items.potionitem, 1, 8229));
            check(true, "");
            return "3 сердца + лечение II: " + (v.died ? "смерть" : "здоровье " + f(v.getHealth())) + " (решение за тобой)";
        });
        t("Ветея - кошмар", "Чужие инструменты", () -> {
            reset(v);
            hold(v, new ItemStack(Items.diamond_pickaxe));
            BreakSpeed bs = new BreakSpeed(v, VetheaBlocks.dreamStone, 0, 8.0F, 0, 10, 0);
            MinecraftForge.EVENT_BUS.post(bs);
            check(near(bs.newSpeed, 1.6F), "скорость алмазной кирки 8 > " + f(bs.newSpeed));
            return "алмазная кирка: скорость 8 > 1.6 (x0.2)";
        });
        t("Ветея - кошмар", "Аксессуары молчат", () -> {
            reset(v);
            Accessories.of(v).setInventorySlotContents(0, trinket(Trinket.AYERACO_WING));
            boolean dream = Accessories.wears(v, Trinket.AYERACO_WING);
            Accessories.of(v).setInventorySlotContents(0, trinket(Trinket.DREAM_WINGS_1));
            boolean wings = Accessories.wears(v, Trinket.DREAM_WINGS_1);
            check(!dream && wings, "Крыло Айрако в Ветее " + dream + ", Крылья сна " + wings);
            return "Крыло Айрако молчит, Крылья сна работают";
        });
        t("Ветея - кошмар", "По игроку чужим мечом", () -> {
            reset(v);
            reset(v2);
            hold(v2, new ItemStack(Items.diamond_sword));
            v.setHealth(10);
            float d = hit(v, DamageSource.causePlayerDamage(v2), 7);
            check(d == 0, "игрок по игроку алмазным мечом: изменение " + f(d));
            return "ни урона, ни лечения";
        });
        t("Ветея - кошмар", "Выключатель", () -> {
            reset(v);
            hold(v, new ItemStack(Items.diamond_sword));
            Config.vetheaNightmare = false;
            try {
                z.setHealth(20);
                float d = hit(z, DamageSource.causePlayerDamage(v), 7);
                check(d > 0, "с выключенным кошмаром меч: изменение " + f(-d));
                return "vetheaNightmare=false: алмазный меч бьёт";
            } finally {
                Config.vetheaNightmare = true;
            }
        });
        t("Ветея - кошмар", "Возврат запертых вещей", () -> {
            TestPlayer o = player(0, "TestStash", p.posX, p.posY, p.posZ);
            NBTTagCompound ps = persisted(o);
            NBTTagList vinv = new NBTTagList(), oinv = new NBTTagList();
            NBTTagCompound c = new NBTTagCompound();
            new ItemStack(VetheaItems.dirtyPearls, 7).writeToNBT(c);
            vinv.appendTag(c);
            NBTTagCompound c2 = new NBTTagCompound();
            new ItemStack(Items.diamond, 5).writeToNBT(c2);
            oinv.appendTag(c2);
            ps.setTag("VetheaInv", vinv);
            ps.setTag("OverworldInv", oinv);
            Class<?> st = com.divinerpgextended.vethea.VetheaStash.class;
            call(st, "migrate", new Class<?>[] { EntityPlayer.class }, o);
            call(st, "give", new Class<?>[] { EntityPlayer.class, NBTTagCompound.class }, o, ps);
            call(st, "migrate", new Class<?>[] { EntityPlayer.class }, o);
            int pearls = count(o, VetheaItems.dirtyPearls), dia = count(o, Items.diamond);
            check(pearls == 7 && dia == 0 && !ps.hasKey("VetheaInv") && !ps.hasKey("OverworldInv") && !ps.hasKey("dre_stash"),
                "жемчуг " + pearls + " (ждали 7), алмазов " + dia + " (ждали 0 - устаревшая копия), копии Divine убраны " + !ps.hasKey("VetheaInv"));
            return "вне Ветеи вернулся ветейский склад (7 жемчуга), устаревшая копия не выдана";
        });

        // этажи
        t("Ветея - этажи", "Низ плиты не ломается", () -> {
            reset(v);
            boolean[] s = { VetheaFloors.protectedSlab(50), VetheaFloors.protectedSlab(60), VetheaFloors.protectedSlab(61), VetheaFloors.protectedSlab(64),
                VetheaFloors.protectedSlab(16) };
            v.setPosition(8, 40, 8);
            BlockEvent.BreakEvent be = new BlockEvent.BreakEvent(8, 55, 8, vw, VetheaBlocks.dreamStone, 0, v);
            boolean cancelled = MinecraftForge.EVENT_BUS.post(be);
            check(s[0] && s[1] && !s[2] && !s[3] && !s[4] && cancelled, "защищено y50/60/61/64/16: " + s[0] + s[1] + s[2] + s[3] + s[4] + ", ломание отменено " + cancelled);
            return "y 49-60 не ломается, 61-64 ломается";
        });
        t("Ветея - этажи", "Разломы прорубаются", () -> {
            int[] c = VetheaFloors.rift(vw, 0, 0, 2);
            // загрузить соседей, чтобы чанк разлома прошёл население Divine (как при обычной игре)
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) vw.getChunkFromChunkCoords((c[0] >> 4) + dx, (c[1] >> 4) + dz);
            check(vw.getChunkFromChunkCoords(c[0] >> 4, c[1] >> 4).isTerrainPopulated, "чанк разлома не населился");
            // где по краю был твёрдый блок (в полах бывают природные полости кристальных пещер Divine)
            List<int[]> solid = new ArrayList<int[]>();
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                int r2 = dx * dx + dz * dz;
                if (r2 <= 5 || r2 > 10) continue;
                for (int y : new int[] { 49, 64 }) {
                    Block b = vw.getBlock(c[0] + dx, y, c[1] + dz);
                    if (!b.isAir(vw, c[0] + dx, y, c[1] + dz) && vw.getTileEntity(c[0] + dx, y, c[1] + dz) == null
                        && b.getBlockHardness(vw, c[0] + dx, y, c[1] + dz) >= 0) solid.add(new int[] { c[0] + dx, y, c[1] + dz });
                }
            }
            call(VetheaFloors.class, "carve", new Class<?>[] { net.minecraft.world.World.class, int.class, int.class, int.class }, vw, c[0], c[1], 2);
            boolean air = vw.isAirBlock(c[0], 50, c[1]) && vw.isAirBlock(c[0], 57, c[1]) && vw.isAirBlock(c[0], 64, c[1]);
            int rim = 0;
            for (int[] s : solid) if (vw.getBlock(s[0], s[1], s[2]) == VetheaBlocks.fireCrystal) rim++;
            int at = VetheaFloors.riftAt(vw, c[0] + 0.5, 55, c[1] + 0.5, 6.25, 8, 1);
            check(air && rim == solid.size() && at == 2, "дыра сквозная " + air + ", кристаллов " + rim + " из " + solid.size() + " твёрдых мест края, riftAt " + at);
            return "шахта 49-64 пустая; край: кристаллы на всех " + solid.size() + " твёрдых местах" + (solid.size() < 32 ? " (часть края - природная полость)" : "");
        });
        t("Ветея - этажи", "Отрицательные координаты", () -> {
            int[] c = VetheaFloors.rift(vw, -1, -1, 2);
            check(c[0] < 0 && c[0] >= -96 && c[1] < 0 && c[1] >= -96, "центр разлома региона (-1,-1): " + c[0] + " " + c[1]);
            return "регион (-1,-1): центр " + c[0] + " " + c[1];
        });
        t("Ветея - этажи", "В шахте не построить", () -> {
            reset(v);
            int[] c = VetheaFloors.rift(vw, 0, 0, 2);
            BlockEvent.PlaceEvent pe = new BlockEvent.PlaceEvent(new BlockSnapshot(vw, c[0], 55, c[1], Blocks.dirt, 0), Blocks.dirt, v);
            boolean in = MinecraftForge.EVENT_BUS.post(pe);
            BlockEvent.PlaceEvent out = new BlockEvent.PlaceEvent(new BlockSnapshot(vw, c[0] + 6, 65, c[1], Blocks.dirt, 0), Blocks.dirt, v);
            boolean outside = MinecraftForge.EVENT_BUS.post(out);
            check(in && !outside, "в шахте отменено " + in + ", снаружи отменено " + outside);
            return "в шахте нельзя, рядом можно";
        });
        t("Ветея - этажи", "Страж этажей", () -> {
            reset(v);
            persisted(v).removeTag("dre_wing_tier");
            v.setPosition(30, 70, 30);
            tick(v, 31);
            int fl = VetheaFloors.floorOf(v.posY);
            check(fl == 1, "без крыльев на 2 этаже через 31 тик: этаж " + fl + " (y " + f((float) v.posY) + ")");
            return "сброшен на 1 этаж, y " + (int) v.posY;
        });
        t("Ветея - этажи", "Этаж запоминается", () -> {
            reset(v);
            persisted(v).removeTag("dre_wing_tier");
            Accessories.of(v).setInventorySlotContents(0, trinket(Trinket.DREAM_WINGS_1));
            v.setPosition(30, 20, 30);
            tick(v, 1);
            Accessories.of(v).setInventorySlotContents(0, null);
            v.setPosition(30, 70, 30);
            tick(v, 31);
            int fl = VetheaFloors.floorOf(v.posY);
            check(fl == 2 && VetheaFloors.allowedFloor(v) == 2, "после снятых крыльев: этаж " + fl + ", дозволено " + VetheaFloors.allowedFloor(v));
            return "крылья надевал - 2 этаж разрешён и без них";
        });
        t("Ветея - этажи", "Полёт выключен", () -> {
            reset(v);
            v.capabilities.allowFlying = true;
            v.capabilities.isFlying = true;
            tick(v, 1);
            check(!v.capabilities.allowFlying && !v.capabilities.isFlying, "полёт остался: " + v.capabilities.allowFlying);
            return "полёт снимается за тик";
        });
        t("Ветея - этажи", "Поток без крыльев", () -> {
            reset(v);
            persisted(v).removeTag("dre_wing_tier");
            int[] c = VetheaFloors.rift(vw, 0, 0, 2);
            v.setPosition(c[0] + 0.5, 55, c[1] + 0.5);
            v.motionY = 0.5;
            tick(v, 1);
            check(v.motionY < -0.5, "в шахте без крыльев motionY " + f((float) v.motionY));
            return "в шахте без крыльев тянет вниз";
        });
        t("Ветея - этажи", "Падение не ранит", () -> {
            reset(v);
            Accessories.of(v).setInventorySlotContents(0, trinket(Trinket.DREAM_WINGS_1));
            v.setPosition(30, 20, 30);
            LivingFallEvent fe = new LivingFallEvent(v, 30);
            boolean wings = MinecraftForge.EVENT_BUS.post(fe);
            Accessories.of(v).setInventorySlotContents(0, null);
            v.ticksExisted += 200; // давно не был в разломе
            LivingFallEvent fe2 = new LivingFallEvent(v, 30);
            boolean none = MinecraftForge.EVENT_BUS.post(fe2);
            check(wings && !none, "в крыльях падение отменено " + wings + ", без крыльев отменено " + none);
            return "в крыльях падение не ранит, без них ранит";
        });
        t("Ветея - этажи", "Взрыв у плиты", () -> {
            Explosion ex = new Explosion(vw, null, 8, 47, 8, 4);
            ex.affectedBlockPositions.add(new net.minecraft.world.ChunkPosition(8, 50, 8));
            ex.affectedBlockPositions.add(new net.minecraft.world.ChunkPosition(8, 47, 8));
            ExplosionEvent.Detonate de = new ExplosionEvent.Detonate(vw, ex, new ArrayList<net.minecraft.entity.Entity>());
            MinecraftForge.EVENT_BUS.post(de);
            boolean slab = false, air = false;
            for (Object o : ex.affectedBlockPositions) {
                net.minecraft.world.ChunkPosition cp = (net.minecraft.world.ChunkPosition) o;
                if (cp.chunkPosY == 50) slab = true;
                if (cp.chunkPosY == 47) air = true;
            }
            check(!slab && air, "блок плиты в списке взрыва " + slab + ", блок под плитой " + air);
            return "плита убрана из взрыва, остальное нет";
        });
        t("Ветея - этажи", "Крафт Крыльев сновидца", () -> {
            ItemStack r1 = craft(vw, new Object[] { VetheaItems.dirtyPearls, null, VetheaItems.dirtyPearls, VetheaItems.dirtyPearls, VetheaItems.teakerLump,
                VetheaItems.dirtyPearls, VetheaItems.dirtyPearls, VetheaItems.amthirmisLump, VetheaItems.dirtyPearls });
            check(r1 != null && r1.getItem() == ModItems.trinket && r1.getItemDamage() == Trinket.DREAM_WINGS_1.ordinal(), "рецепт дал " + r1);
            return "P_P / PTP / PAP = Крылья сновидца";
        });
        t("Ветея - этажи", "Ступени II и III", () -> {
            ItemStack w1 = trinket(Trinket.DREAM_WINGS_1), w2 = trinket(Trinket.DREAM_WINGS_2);
            ItemStack r2 = craft(vw, new Object[] { VetheaItems.cleanPearls, null, VetheaItems.cleanPearls, VetheaItems.cleanPearls, w1, VetheaItems.cleanPearls,
                VetheaItems.darvenLump, VetheaItems.cermileLump, VetheaItems.darvenLump });
            ItemStack r3 = craft(vw, new Object[] { VetheaItems.polishedPearls, null, VetheaItems.polishedPearls, VetheaItems.polishedPearls, w2,
                VetheaItems.polishedPearls, VetheaItems.quadroticLump, VetheaItems.karosLump, VetheaItems.quadroticLump });
            check(r2 != null && r2.getItemDamage() == Trinket.DREAM_WINGS_2.ordinal() && r3 != null && r3.getItemDamage() == Trinket.DREAM_WINGS_3.ordinal(),
                "II: " + r2 + ", III: " + r3);
            return "рецепты II и III собираются";
        });
        t("Ветея - этажи", "Крылья вне Ветеи", () -> {
            TestPlayer o = player(0, "TestWing", p.posX, p.posY, p.posZ);
            Accessories.of(o).setInventorySlotContents(0, trinket(Trinket.DREAM_WINGS_1));
            Accessories.of(v).setInventorySlotContents(0, trinket(Trinket.DREAM_WINGS_1));
            int out = DoubleJump.wingTicks(o), in = DoubleJump.wingTicks(v);
            Accessories.of(v).setInventorySlotContents(0, null);
            check(out == 0 && in == 85, "запас вне Ветеи " + out + ", в Ветее " + in);
            return "вне Ветеи 0, в Ветее 85 тиков";
        });
        t("Ветея - этажи", "Этаж засчитан", () -> {
            TestPlayer o = player(0, "TestGift", p.posX, p.posY, p.posZ);
            persisted(o).removeTag("dre_dream_done");
            persisted(o).removeTag("dre_dream_pending");
            Class<?> giftC = Class.forName("com.divinerpgextended.vethea.VetheaFloors$Gift");
            Object floor2 = giftC.getEnumConstants()[0];
            call(VetheaFloors.class, "mark", new Class<?>[] { EntityPlayer.class, giftC }, o, floor2);
            int pending = persisted(o).getInteger("dre_dream_pending");
            check(pending == 1, "отметка дара: " + pending);
            call(VetheaFloors.class, "deliverGifts", new Class<?>[] { EntityPlayer.class }, o);
            int realmite = count(o, VanillaItemsOther.realmiteIngot), dia = count(o, Items.diamond);
            check(realmite == 12 && dia == 6, "дар: реалмит " + realmite + ", алмазы " + dia);
            call(VetheaFloors.class, "mark", new Class<?>[] { EntityPlayer.class, giftC }, o, floor2);
            int again = persisted(o).getInteger("dre_dream_pending");
            check(again == 0, "повторная отметка: " + again);
            return "отмечен, выдан (12 реалмита, 6 алмазов), повторно нет";
        });
        z.setDead();
    }

    private void serverTests(final TestPlayer p, final TestPlayer q) {
        t("Сервер", "Эффекты по игроку", () -> {
            reset(p);
            reset(q);
            Accessories.of(p).setInventorySlotContents(0, trinket(Trinket.PARASECTA_STING));
            hit(q, DamageSource.causePlayerDamage(p), 1);
            check(!q.isPotionActive(Potion.poison), "Жало Парасекты отравило игрока");
            return "Жало Парасекты не травит игрока (PvE-only)";
        });
        t("Сервер", "Панцирь Карцинуса", () -> {
            reset(p);
            reset(q);
            Accessories.of(q).setInventorySlotContents(0, trinket(Trinket.CARCINUS_SHELL));
            float taken = 0;
            for (int i = 0; i < 100; i++) {
                p.hurtResistantTime = 0;
                q.setHealth(q.getMaxHealth());
                float before = hp(p);
                hit(q, DamageSource.causePlayerDamage(p), 1);
                taken += before - hp(p);
            }
            check(taken == 0, "атакующий игрок получил отражённый урон " + f(taken));
            return "за 100 ударов игрок не получил отражения";
        });
    }

    private void auditTests(final TestPlayer p) {
        t("Отладка", "Журнал аудита", () -> {
            Audit.log(p, "Selftest", "проверка журнала");
            File log = new File(server.getFile("logs"), "dre-audit.log");
            String all = new String(java.nio.file.Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            check(all.contains("Selftest: проверка журнала"), "строки нет в " + log);
            return "строка в logs/dre-audit.log";
        });
    }

    // ---------------- Второй блок ----------------

    private static Object field(Class<?> c, Object o, String... names) {
        return ReflectionHelper.getPrivateValue((Class) c, o, names);
    }

    private static int floating(EntityPlayerMP p) {
        return (Integer) field(NetHandlerPlayServer.class, p.playerNetServerHandler, "floatingTickCount", "field_147365_f");
    }

    private static void setFloating(EntityPlayerMP p, int v) {
        ReflectionHelper.setPrivateValue(NetHandlerPlayServer.class, p.playerNetServerHandler, v, "floatingTickCount", "field_147365_f");
    }

    private static int carpetLeft(EntityPlayer p) throws Exception {
        java.lang.reflect.Field f = DoubleJump.class.getDeclaredField("STATE");
        f.setAccessible(true);
        Object st = ((java.util.Map<?, ?>) f.get(null)).get(p.getUniqueID());
        java.lang.reflect.Field c = st.getClass().getDeclaredField("carpetLeft");
        c.setAccessible(true);
        return c.getInt(st);
    }

    private void moreTests(final TestPlayer p, final TestPlayer q, final EntityIronGolem g, final WorldServer ow, final int sx, final int sy,
        final int sz) throws Exception {
        // ---- боссы: настоящая смерть Карцинуса ----
        final TestPlayer k = join(player(0, "TestBoss", sx + 3, sy, sz), false);
        t("Прохождение", "Лор после боссов", () -> {
            com.divinerpgextended.entity.EntityCarcinus c = new com.divinerpgextended.entity.EntityCarcinus(ow);
            c.setPosition(sx + 4, sy, sz);
            ow.spawnEntityInWorld(c);
            for (int i = 0; i < 20 && c.isEntityAlive(); i++) {
                c.hurtResistantTime = 0;
                c.attackEntityFrom(DamageSource.causePlayerDamage(k), 10000);
            }
            boolean dead = !c.isEntityAlive();
            c.setDead();
            check(dead, "Карцинус не умер");
            check(BossProgress.has(k, Boss.CARCINUS), "Карцинус не засчитан");
            check(k.heard("dre.chat.boss_unlocked"), "нет сообщения о победе, чат: " + k.chat);
            boolean lore = !net.minecraft.util.StatCollector.canTranslate("dre.lore.carcinus") || k.heard("dre.lore.carcinus");
            check(lore, "нет строки лора, чат: " + k.chat);
            return "засчитан, сообщение о победе и строка лора пришли";
        });
        t("Прохождение", "Ачивки", () -> {
            net.minecraft.stats.Achievement a = com.divinerpgextended.DreAchievements.forBoss(Boss.CARCINUS);
            check(a != null && k.stats.contains(a.statId), "ачивки за Карцинуса нет, выдано: " + k.stats);
            String parent = a.parentAchievement == null ? null : a.parentAchievement.statId;
            check(parent == null || k.stats.contains(parent), "родительская ачивка " + parent + " не выдана: " + k.stats);
            return "ачивка " + a.statId + (parent != null ? " и родительская " + parent : "") + " выданы";
        });
        t("Аксессуары", "С боссов", () -> {
            check(countTrinket(k, Trinket.CARCINUS_SHELL) == 1, "Панцирь Карцинуса после первой победы: " + countTrinket(k, Trinket.CARCINUS_SHELL));
            return "настоящая победа над Карцинусом - Панцирь в инвентаре; повторные победы ~10% (2000 бросков)";
        });

        // ---- стая Айрако ----
        t("Аксессуары", "Дроп после всей стаи", () -> {
            TestPlayer a = join(player(0, "TestFlock", sx + 6, sy, sz + 6), true);
            persisted(a).removeTag("dre_acc_got");
            net.divinerpg.entities.vanilla.EntityAyeracoGreen g1 = new net.divinerpg.entities.vanilla.EntityAyeracoGreen(ow);
            net.divinerpg.entities.vanilla.EntityAyeracoRed g2 = new net.divinerpg.entities.vanilla.EntityAyeracoRed(ow);
            g1.setPosition(sx + 8, sy + 3, sz + 8);
            g2.setPosition(sx + 9, sy + 3, sz + 8);
            ow.spawnEntityInWorld(g1);
            ow.spawnEntityInWorld(g2);
            g1.setHealth(0);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(g1, DamageSource.causePlayerDamage(a)));
            int afterFirst = countTrinket(a, Trinket.AYERACO_WING);
            g1.setDead();
            g2.setHealth(0);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(g2, DamageSource.causePlayerDamage(a)));
            int afterLast = countTrinket(a, Trinket.AYERACO_WING);
            g2.setDead();
            check(afterFirst == 0 && afterLast == 1, "крыло после первого: " + afterFirst + ", после последнего: " + afterLast);
            return "после первого дракона крыла нет, после последнего - есть";
        });

        // ---- прыжок Крылом Айрако гасит падение ----
        t("Аксессуары", "Гасит падение", () -> {
            reset(p);
            Accessories.of(p).setInventorySlotContents(0, trinket(Trinket.AYERACO_WING));
            java.lang.reflect.Constructor<cpw.mods.fml.common.network.simpleimpl.MessageContext> mc = cpw.mods.fml.common.network.simpleimpl.MessageContext.class
                .getDeclaredConstructor(net.minecraft.network.INetHandler.class, cpw.mods.fml.relauncher.Side.class);
            mc.setAccessible(true);
            cpw.mods.fml.common.network.simpleimpl.MessageContext ctx = mc.newInstance(p.playerNetServerHandler, cpw.mods.fml.relauncher.Side.SERVER);
            p.onGround = false;
            p.fallDistance = 20;
            new DoubleJump.JumpHandler().onMessage(new DoubleJump.Jump(), ctx);
            float first = p.fallDistance;
            p.fallDistance = 20;
            new DoubleJump.JumpHandler().onMessage(new DoubleJump.Jump(), ctx);
            float second = p.fallDistance;
            check(first == 0 && second == 20, "падение после прыжка: " + f(first) + ", после лишнего прыжка: " + f(second));
            return "прыжок в воздухе обнуляет падение, лишний (сверх 1) сервер отклоняет";
        });

        // ---- окно аксессуаров без дюпа ----
        t("Аксессуары", "Без дюпов", () -> {
            reset(p);
            p.inventory.mainInventory[0] = trinket(Trinket.VALOR_MEDAL);
            com.divinerpgextended.accessory.ContainerAccessories c = new com.divinerpgextended.accessory.ContainerAccessories(p, Accessories.of(p));
            int inv = slotOf(c, p.inventory, 0);
            c.slotClick(inv, 0, 1, p);
            int total1 = countTrinket(p, Trinket.VALOR_MEDAL) + accCount(p, Trinket.VALOR_MEDAL);
            int inAcc = accCount(p, Trinket.VALOR_MEDAL);
            c.slotClick(0, 0, 1, p);
            int total2 = countTrinket(p, Trinket.VALOR_MEDAL) + accCount(p, Trinket.VALOR_MEDAL);
            check(inAcc == 1 && total1 == 1 && total2 == 1, "в слот: " + inAcc + ", всего после: " + total1 + " и " + total2);
            return "shift-клик туда и обратно: вещь одна";
        });
        t("Аксессуары", "Правка админом при открытом окне", () -> {
            reset(p);
            reset(q);
            Accessories.of(p).setInventorySlotContents(0, trinket(Trinket.VALOR_MEDAL));
            com.divinerpgextended.accessory.ContainerAccessories own = new com.divinerpgextended.accessory.ContainerAccessories(p, Accessories.of(p));
            com.divinerpgextended.accessory.ContainerAccessories adm = new com.divinerpgextended.accessory.ContainerAccessories(q, Accessories.of(p));
            adm.slotClick(0, 0, 1, q);
            own.slotClick(0, 0, 1, p);
            int total = countTrinket(p, Trinket.VALOR_MEDAL) + countTrinket(q, Trinket.VALOR_MEDAL) + accCount(p, Trinket.VALOR_MEDAL);
            check(total == 1 && countTrinket(q, Trinket.VALOR_MEDAL) == 1, "всего медалей " + total + ", у админа " + countTrinket(q, Trinket.VALOR_MEDAL));
            return "админ забрал, окно владельца пустое - медаль одна";
        });

        // ---- торговля ----
        t("Аксессуары", "У торговцев", () -> {
            reset(p);
            net.divinerpg.entities.arcana.EntityCaptianMerik merik = new net.divinerpg.entities.arcana.EntityCaptianMerik(ow);
            p.chat.clear();
            boolean refused = MinecraftForge.EVENT_BUS.post(new EntityInteractEvent(p, merik)) && p.heard("dre.chat.merchant_notready");
            BossProgress.grant(p, Boss.CADILLION_EDEN); // Мерик приходит после кадиллиона Эдема
            MinecraftForge.EVENT_BUS.post(new EntityInteractEvent(p, merik));
            boolean m = sells(merik.getRecipes(p), Trinket.MASTER_SCABBARD);
            check(refused, "Мерик до прогресса не отказал");
            net.divinerpg.entities.iceika.EntityWorkshopMerchant shop = new net.divinerpg.entities.iceika.EntityWorkshopMerchant(ow);
            MinecraftForge.EVENT_BUS.post(new EntityInteractEvent(p, shop));
            boolean s = sells(shop.getRecipes(p), Trinket.WARM_SCARF);
            check(m && s, "Мерик продаёт ножны " + m + ", торговец мастерской - шарф " + s);
            return "до кадиллиона Эдема Мерик отказывает; после - Ножны мастера у Мерика, Тёплый шарф у торговца мастерской";
        });
        t("Аркана", "Без лимитов", () -> {
            net.divinerpg.entities.base.InfiniteTrade tr = new net.divinerpg.entities.base.InfiniteTrade(new ItemStack(Items.bone), new ItemStack(Items.apple));
            for (int i = 0; i < 200; i++) tr.incrementToolUses();
            check(!tr.isRecipeDisabled(), "после 200 сделок сделка заблокирована");
            return "200 сделок подряд - не блокируется";
        });
        t("Торговля", "Добыча III", () -> {
            reset(p);
            ItemStack sword = new ItemStack(Items.diamond_sword);
            hold(p, sword);
            int plain = heads(p, ow, 20000);
            sword.addEnchantment(net.minecraft.enchantment.Enchantment.looting, 3);
            int loot3 = heads(p, ow, 20000);
            check(plain > 280 && plain < 520 && loot3 > 850 && loot3 < 1150, "голов на 20000 зомби: без Добычи " + plain + ", Добыча III " + loot3 + " (ждали ~400 и ~1000)");
            return String.format("голова зомби: без Добычи %.1f%%, с Добычей III %.1f%%", plain / 200.0, loot3 / 200.0);
        });
        t("Торговля", "Заказы из голов", () -> {
            reset(p);
            BossProgress.grant(p, Boss.LICH_KING);
            hold(p, new ItemStack(Items.skull, 8, 0));
            p.inventory.mainInventory[1] = new ItemStack(Items.bone, 32);
            p.inventory.mainInventory[2] = new ItemStack(VanillaItemsOther.shadowCoins, 16);
            Class<?> jack = com.divinerpgextended.trinket.JackOMan.class;
            call(jack, "order", new Class<?>[] { EntityPlayer.class }, p);
            int before = countTrinket(p, Trinket.BONE_RING);
            call(jack, "order", new Class<?>[] { EntityPlayer.class }, p);
            int after = countTrinket(p, Trinket.BONE_RING);
            check(before == 0 && after == 1 && count(p, Items.skull) == 0 && count(p, Items.bone) == 0 && count(p, VanillaItemsOther.shadowCoins) == 0,
                "кольцо до подтверждения " + before + ", после " + after + "; осталось голов " + count(p, Items.skull) + ", костей " + count(p, Items.bone)
                    + ", монет " + count(p, VanillaItemsOther.shadowCoins));
            return "8 голов скелета + 32 кости + 16 монет, повтор = Кольцо костяного стрелка";
        });

        // ---- сердца Генерала ----
        t("Аркана", "Сердца после смены мира", () -> {
            reset(p);
            NBTTagCompound gen = persisted(p).getCompoundTag("dre_general");
            gen.setInteger("statues", 3);
            persisted(p).setTag("dre_general", gen);
            com.divinerpgextended.arcana.WarGeneral.applyHearts(p, true);
            float max1 = p.getMaxHealth();
            FMLCommonHandler.instance().bus().post(new PlayerEvent.PlayerChangedDimensionEvent(p, 0, 56));
            float max2 = p.getMaxHealth();
            p.getEntityData().setFloat("dreSavedHp", 23);
            p.setHealth(20);
            FMLCommonHandler.instance().bus().post(new PlayerEvent.PlayerLoggedInEvent(p));
            p.ticksExisted = 39;
            tick(p, 1);
            float hpAfter = p.getHealth();
            gen.setInteger("statues", 0);
            com.divinerpgextended.arcana.WarGeneral.applyHearts(p, true);
            check(near(max1, 24) && near(max2, 24), "максимум после статуй " + f(max1) + ", после смены мира " + f(max2));
            check(near(hpAfter, 23), "здоровье после перезахода " + f(hpAfter) + " (было 23)");
            return "2 статуи = 24 здоровья, держится после смены мира; после перезахода 23 не обрезалось";
        });
        t("Аркана", "Кролик выше 20 здоровья", () -> {
            reset(p);
            net.divinerpg.entities.twilight.EntityBunny b = new net.divinerpg.entities.twilight.EntityBunny(ow);
            b.setPosition(p.posX + 1, p.posY, p.posZ);
            b.setTamed(true);
            b.func_152115_b(p.getUniqueID().toString());
            b.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(30);
            b.setHealth(22);
            hold(p, new ItemStack(Items.cooked_porkchop, 5));
            boolean cancelled = MinecraftForge.EVENT_BUS.post(new EntityInteractEvent(p, b));
            check(b.getHealth() > 22 && count(p, Items.cooked_porkchop) == 4, "кролик 22/30 после мяса: " + f(b.getHealth()) + ", мяса осталось "
                + count(p, Items.cooked_porkchop));
            return "кролик 22/30 + мясо > " + f(b.getHealth());
        });

        // ---- Сумеречные миры: ковёр на сервере ----
        final TestPlayer e = player(ConfigurationHelper.eden, "TestCarpet", 0, 150, 0);
        t("Сумеречные миры", "Откуда", () -> {
            check(com.divinerpgextended.trinket.AccessorySources.forBoss(Boss.CADILLION_EDEN) == Trinket.MAGIC_CARPET, "за кадиллиона Эдема не ковёр");
            return "Великий кадиллион Эдема даёт ковёр (первая победа наверняка, дальше 10%)";
        });
        t("Сумеречные миры", "Заряд только на земле", () -> {
            reset(e);
            Accessories.of(e).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            e.onGround = true;
            tick(e, 1);
            int full = carpetLeft(e);
            e.onGround = false;
            tick(e, 10);
            int spent = carpetLeft(e);
            tick(e, 5);
            int still = carpetLeft(e);
            check(full == 60 && spent == 50 && still == 45, "запас: на земле " + full + ", после 10 тиков парения " + spent + ", ещё 5 " + still);
            return "на земле 60 тиков, в воздухе тратится и не восстанавливается";
        });
        t("Сумеречные миры", "Снять ковёр в воздухе", () -> {
            int before = carpetLeft(e);
            Accessories.of(e).setInventorySlotContents(0, null);
            tick(e, 3);
            Accessories.of(e).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            tick(e, 1);
            int after = carpetLeft(e);
            check(after < before, "после снятия и надевания в воздухе: было " + before + ", стало " + after);
            return "снял и надел в воздухе: запас " + before + " > " + after + " (не восстановился)";
        });
        t("Сумеречные миры", "Касание воды", () -> {
            ReflectionHelper.setPrivateValue(net.minecraft.entity.Entity.class, e, true, "inWater", "field_70171_ac");
            tick(e, 1);
            int left = carpetLeft(e);
            ReflectionHelper.setPrivateValue(net.minecraft.entity.Entity.class, e, false, "inWater", "field_70171_ac");
            check(left == 60, "после касания воды запас " + left);
            return "в воде запас снова полный (60)";
        });
        t("Сумеречные миры", "Падение не гасит", () -> {
            boolean c = MinecraftForge.EVENT_BUS.post(new LivingFallEvent(e, 20));
            check(!c, "ковёр отменил урон от падения");
            return "с ковром падение ранит как обычно";
        });
        t("Сервер", "Нет кика за полёт", () -> {
            Accessories.of(e).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            e.onGround = true;
            tick(e, 1);
            e.onGround = false;
            setFloating(e, 70);
            tick(e, 1);
            int fl = floating(e);
            check(fl == 0, "счётчик кика за полёт при парении: " + fl);
            return "пока ковёр держит - счётчик кика (порог 80) сбрасывается в 0";
        });
        t("Сумеречные миры", "Высота падения от ковра", () -> {
            Accessories.of(e).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            e.onGround = true;
            tick(e, 1);
            e.onGround = false;
            e.fallDistance = 12;
            tick(e, 2);
            float hover = e.fallDistance;
            check(hover == 0, "высота падения на ковре " + hover);
            return "пока ковёр держит, накопленная высота падения (12) сбрасывается - урон считается от ковра";
        });
        t("Сервер", "Ковёр у порога кика", () -> {
            e.onGround = true;
            tick(e, 1);
            e.onGround = false;
            int maxSeen = 0;
            for (int i = 0; i < 59; i++) {
                setFloating(e, floating(e) + 1); // как сервер: +1 за тик в воздухе
                tick(e, 1);
                maxSeen = Math.max(maxSeen, floating(e));
            }
            check(maxSeen < 80, "счётчик дошёл до " + maxSeen);
            return "за весь запас (3 с) счётчик кика не растёт выше " + maxSeen;
        });

        // ---- Ветея ----
        final int vd = ConfigurationHelper.vethea;
        final WorldServer vw = server.worldServerForDimension(vd);
        t("Ветея - этажи", "Крыльев I впритык", () -> {
            double y = 0, m = 0.42; // прыжок с пола
            int fuel = 85;
            for (int i = 0; i < 400; i++) {
                if (fuel > 0) {
                    m = Math.min(m + 0.15, 0.6);
                    fuel--;
                }
                y += m;
                m = (m - 0.08) * 0.98;
                if (m < 0 && fuel == 0) break;
            }
            check(y >= 49, "крылья I поднимают на " + String.format("%.1f", y) + " блоков, а до пола 2 этажа 48-49");
            return String.format("взлёт с пола на %.1f блоков при подъёме в 48 (запас %.1f)", y, y - 48);
        });
        t("Ветея - кошмар", "Подсказка на входе", () -> {
            TestPlayer v = player(vd, "TestHint", 30, 20, 30);
            FMLCommonHandler.instance().bus().post(new PlayerEvent.PlayerChangedDimensionEvent(v, 0, vd));
            check(v.heard("dre.nightmare.enter"), "подсказки нет, чат: " + v.chat);
            return "при входе в Ветею пришла подсказка";
        });
        t("Ветея - этажи", "Боссы Ветеи", () -> {
            TestPlayer v1 = join(player(vd, "TestQueen1", 40, 20, 40), true), v2 = join(player(vd, "TestQueen2", 60, 20, 40), true);
            persisted(v1).removeTag("dre_dream_done");
            persisted(v1).removeTag("dre_dream_pending");
            persisted(v2).removeTag("dre_dream_done");
            persisted(v2).removeTag("dre_dream_pending");
            net.divinerpg.entities.vethea.EntityHiveQueen hq = new net.divinerpg.entities.vethea.EntityHiveQueen(vw);
            hq.setPosition(42, 20, 40);
            vw.spawnEntityInWorld(hq);
            hq.setHealth(0);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(hq, DamageSource.causePlayerDamage(v1)));
            hq.setDead();
            int bit = 1 << 3; // HIVE_QUEEN
            int a = persisted(v1).getInteger("dre_dream_pending"), b = persisted(v2).getInteger("dre_dream_pending");
            check((a & bit) != 0 && (b & bit) != 0, "дар за Королеву улья: убийце " + ((a & bit) != 0) + ", игроку в 18 блоках " + ((b & bit) != 0));
            return "дар за Королеву улья отмечен и убийце, и стоявшему рядом";
        });
        copyResult("Ветея - этажи", "Боссы Ветеи", "Ветея - этажи", "Босса добил другой");
        t("Ветея - этажи", "Дар после смерти или выхода", () -> {
            TestPlayer o = player(0, "TestGift2", sx, sy, sz);
            persisted(o).setInteger("dre_dream_done", 1);
            persisted(o).setInteger("dre_dream_pending", 1);
            TestPlayer reborn = new TestPlayer(ow, "TestGift2");
            new NetHandlerPlayServer(server, nmStub(), reborn);
            reborn.clonePlayer(o, false);
            call(VetheaFloors.class, "deliverGifts", new Class<?>[] { EntityPlayer.class }, reborn);
            int realmite = count(reborn, VanillaItemsOther.realmiteIngot);
            check(realmite == 12, "после смерти дар не выдан: реалмита " + realmite);
            return "отмеченный дар пережил смерть и выдан";
        });
        copyResult("Ветея - этажи", "Этаж засчитан", "Ветея - этажи", "Дар по пробуждении");
        copyResult("Ветея - этажи", "Этаж засчитан", "Ветея - этажи", "Без повторов");
        copyResult("Ветея - этажи", "В шахте не построить", "Ветея - этажи", "Край разлома");
        copyResult("Ветея - этажи", "Падение не ранит", "Ветея - этажи", "Снять крылья в полёте");
        t("Ветея - этажи", "Перезаход выше дозволенного", () -> {
            TestPlayer v = player(vd, "TestRelog", 30, 70, 30);
            persisted(v).removeTag("dre_wing_tier");
            FMLCommonHandler.instance().bus().post(new PlayerEvent.PlayerLoggedOutEvent(v));
            FMLCommonHandler.instance().bus().post(new PlayerEvent.PlayerLoggedInEvent(v));
            tick(v, 31);
            check(VetheaFloors.floorOf(v.posY) == 1, "после перезахода на 2 этаже не сброшен: y " + f((float) v.posY));
            return "после перезахода на 2 этаже без крыльев - сброшен на 1";
        });
        copyResultNote("Ветея - этажи", "Страж этажей", "Ветея - этажи", "Водяной лифт и жемчуг",
            "страж срабатывает по положению игрока, способ попадания (лифт, жемчуг) не важен");
        copyResultNote("Ветея - этажи", "Страж этажей", "Ветея - этажи", "Кровать кошмаров наверху",
            "страж срабатывает по положению после возрождения так же, как после входа");

        // ---- третий блок ----
        t("Аксессуары", "Из сундуков", () -> {
            Class<?> ll = com.divinerpgextended.world.LairLoot.class;
            int shell = chestRate(call(ll, "crab", new Class<?>[0]), Trinket.DEEP_SHELL);
            int ring = chestRate(call(ll, "iceikaDungeon", new Class<?>[0]), Trinket.ETERNAL_ICE_RING);
            int ankh = chestRate(call(ll, "tomb", new Class<?>[0]), Trinket.PHARAOH_ANKH);
            check(shell > 0 && ring > 0 && ankh > 0, "на 2000 сундуков: Ракушка " + shell + ", Кольцо льда " + ring + ", Анх " + ankh);
            return String.format("на 2000 сундуков: Ракушка глубин %d (логово краба), Кольцо вечного льда %d (Айсика), Анх %d (гробница)", shell, ring, ankh);
        });
        t("Сервер", "PvP по своей броне", () -> {
            reset(p);
            reset(q);
            float naked = hit(q, DamageSource.causePlayerDamage(p), 1);
            q.setHealth(q.getMaxHealth());
            armor(p, VanillaItemsArmor.divineHelmet, VanillaItemsArmor.divineBody, VanillaItemsArmor.divineLegs, VanillaItemsArmor.divineBoots);
            float divine = hit(q, DamageSource.causePlayerDamage(p), 1);
            q.setHealth(q.getMaxHealth());
            armor(p, null, null, null, null);
            armor(q, VanillaItemsArmor.divineHelmet, VanillaItemsArmor.divineBody, VanillaItemsArmor.divineLegs, VanillaItemsArmor.divineBoots);
            float victimSet = hit(q, DamageSource.causePlayerDamage(p), 1);
            q.setHealth(q.getMaxHealth());
            check(near(naked, 1) && near(divine, 7) && victimSet < 1.01F, "голый по голому " + f(naked) + ", атакующий в Divine " + f(divine)
                + ", жертва в Divine " + f(victimSet));
            return "бонус +6 только от сета атакующего; сет жертвы ему бонуса не даёт";
        });
        t("Ветея - кошмар", "Белый список", () -> {
            TestPlayer v = player(vd, "TestWhite", 30, 20, 30);
            hold(v, new ItemStack(Items.diamond_sword));
            EntityZombie zz = new EntityZombie(vw);
            String[] old = Config.vetheaWhitelist;
            java.lang.reflect.Field cache = com.divinerpgextended.vethea.Nightmare.class.getDeclaredField("vethean");
            cache.setAccessible(true);
            try {
                Config.vetheaWhitelist = new String[] { "minecraft:diamond_sword" };
                cache.set(null, null);
                zz.setHealth(20);
                float d = hit(zz, DamageSource.causePlayerDamage(v), 7);
                check(d > 0, "алмазный меч из белого списка: изменение здоровья " + f(-d));
                return "алмазный меч в белом списке бьёт мобов Ветеи как свой";
            } finally {
                Config.vetheaWhitelist = old;
                cache.set(null, null);
            }
        });
        t("Ветея - кошмар", "Чужое оружие из конфига", () -> {
            TestPlayer v = player(vd, "TestForeign", 30, 20, 30);
            hold(v, new ItemStack(Items.stick));
            EntityZombie zz = new EntityZombie(vw);
            String[] old = Config.vetheaWeapons;
            java.lang.reflect.Field cache = com.divinerpgextended.vethea.Nightmare.class.getDeclaredField("foreignWeapons");
            cache.setAccessible(true);
            try {
                zz.setHealth(10);
                float plain = hit(zz, DamageSource.causePlayerDamage(v), 1);
                Config.vetheaWeapons = new String[] { "minecraft:stick" };
                cache.set(null, null);
                zz.setHealth(10);
                float listed = hit(zz, DamageSource.causePlayerDamage(v), 1);
                check(plain > 0 && listed < 0, "палка без настройки: " + f(-plain) + ", палка в vetheaWeapons: " + f(-listed));
                return "предмет из vetheaWeapons считается чужим оружием и лечит моба, без настройки - обычный удар";
            } finally {
                Config.vetheaWeapons = old;
                cache.set(null, null);
            }
        });
        t("Сервер", "Без HawkEye", () -> {
            File log = new File(server.getFile("logs"), "dre-audit.log");
            check(log.exists() && log.length() > 0, "журнал аудита пуст");
            return "тестовый сервер без HawkEye: ошибок нет, журнал аудита пишется";
        });

        // ---- приваты ----
        protectionTests(sx, sy, sz, ow);

        // ---- отладка и аудит (несколько тиков) ----
        debugTests(sx, sy, sz, ow);
    }

    private static NetworkManager nmStub() {
        NetworkManager nm = new NetworkManager(false);
        ReflectionHelper.setPrivateValue(NetworkManager.class, nm, new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter()),
            "channel", "field_150746_k");
        return nm;
    }

    private void copyResult(String fromSheet, String fromRow, String sheet, String row) {
        copyResultNote(fromSheet, fromRow, sheet, row, null);
    }

    private void copyResultNote(String fromSheet, String fromRow, String sheet, String row, String note) {
        for (int i = results.size() - 1; i >= 0; i--) {
            String[] r = results.get(i);
            if (r[0].equals(fromSheet) && r[1].equals(fromRow)) {
                results.add(new String[] { sheet, row, r[2], (note != null ? note + "; " : "") + "по тесту \"" + fromRow + "\": " + r[3] });
                return;
            }
        }
    }

    private static int slotOf(Container c, net.minecraft.inventory.IInventory inv, int index) {
        for (int i = 0; i < c.inventorySlots.size(); i++) {
            net.minecraft.inventory.Slot s = (net.minecraft.inventory.Slot) c.inventorySlots.get(i);
            if (s.inventory == inv && s.getSlotIndex() == index) return i;
        }
        return -1;
    }

    private static int accCount(EntityPlayer p, Trinket t) {
        int n = 0;
        Accessories a = Accessories.of(p);
        for (int i = 0; i < Accessories.SIZE; i++) {
            ItemStack s = a.getStackInSlot(i);
            if (s != null && s.getItem() == ModItems.trinket && s.getItemDamage() == t.ordinal()) n++;
        }
        return n;
    }

    private static boolean sells(net.minecraft.village.MerchantRecipeList l, Trinket t) {
        if (l == null) return false;
        for (Object o : l) {
            ItemStack s = ((net.minecraft.village.MerchantRecipe) o).getItemToSell();
            if (s != null && s.getItem() == ModItems.trinket && s.getItemDamage() == t.ordinal()) return true;
        }
        return false;
    }

    private static int chestRate(Object table, Trinket t) {
        Random r = new Random(7);
        int n = 0;
        for (int i = 0; i < 2000; i++) {
            net.minecraft.tileentity.TileEntityChest chest = new net.minecraft.tileentity.TileEntityChest();
            com.divinerpgextended.world.LairLoot.fill(chest, r, (net.minecraft.util.WeightedRandomChestContent[]) table, 8);
            for (int s = 0; s < chest.getSizeInventory(); s++) {
                ItemStack st = chest.getStackInSlot(s);
                if (st != null && st.getItem() == ModItems.trinket && st.getItemDamage() == t.ordinal()) n++;
            }
        }
        return n;
    }

    private static int heads(EntityPlayer p, WorldServer w, int n) {
        EntityZombie z = new EntityZombie(w);
        int got = 0;
        for (int i = 0; i < n; i++) {
            ArrayList<net.minecraft.entity.item.EntityItem> drops = new ArrayList<net.minecraft.entity.item.EntityItem>();
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDropsEvent(z, DamageSource.causePlayerDamage(p), drops, 0, true, 0));
            for (net.minecraft.entity.item.EntityItem it : drops) if (it.getEntityItem().getItem() == Items.skull) got++;
        }
        return got;
    }

    private void protectionTests(final int sx, final int sy, final int sz, final WorldServer ow) {
        prot = new Protection();
        MinecraftForge.EVENT_BUS.register(prot);
        t("Сервер", "Кирка изморози 3x3", () -> {
            WorldServer ice = server.worldServerForDimension(ConfigurationHelper.iceika);
            TestPlayer m = player(ConfigurationHelper.iceika, "TestMiner", 10, 202, 10);
            m.theItemInWorldManager.setGameType(net.minecraft.world.WorldSettings.GameType.SURVIVAL);
            m.rotationPitch = 90;
            ItemStack pick = new ItemStack(ModItems.rimePick);
            hold(m, pick);
            int y = 200;
            for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) ice.setBlock(9 + dx, y, 9 + dz, Blocks.stone);
            prot.dim = ConfigurationHelper.iceika;
            prot.x0 = prot.x1 = 9;
            prot.y0 = prot.y1 = y;
            prot.z0 = 9;
            prot.z1 = 11;
            pick.getItem().onBlockDestroyed(pick, ice, Blocks.stone, 10, y, 10, m);
            int priv = 0, open = 0;
            for (int dz = 0; dz < 3; dz++) if (ice.getBlock(9, y, 9 + dz) == Blocks.stone) priv++;
            for (int dz = 0; dz < 3; dz++) if (dz != 1 && ice.getBlock(11, y, 9 + dz) == Blocks.stone) open++;
            for (int dz = 0; dz < 3; dz++) if (ice.getBlock(10, y, 9 + dz) == Blocks.stone && dz != 1) open++;
            prot.dim = Integer.MIN_VALUE;
            check(priv == 3 && open == 0, "в привате уцелело " + priv + " из 3, вне привата осталось " + open + " из 4 (центр не считаем)");
            return "3 блока в привате целы, 4 соседних вне привата сломаны";
        });
        copyResult("Сервер", "Кирка изморози 3x3", "Сервер", "3x3 на границе привата");
        t("Сервер", "Кошмарный бур 3x3", () -> {
            TestPlayer m = player(0, "TestDrill", sx + 20.5, 202, sz + 20.5);
            m.theItemInWorldManager.setGameType(net.minecraft.world.WorldSettings.GameType.SURVIVAL);
            m.rotationPitch = 90;
            m.rotationYaw = 0;
            m.setPositionAndRotation(sx + 20.5, 202, sz + 20.5, 0, 90);
            ItemStack drill = new ItemStack(ModItems.nightmareDrill);
            hold(m, drill);
            int y = 200, cx = sx + 20, cz = sz + 20;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) ow.setBlock(cx + dx, y, cz + dz, Blocks.stone);
            prot.dim = 0;
            prot.x0 = prot.x1 = cx - 1;
            prot.y0 = prot.y1 = y;
            prot.z0 = cz - 1;
            prot.z1 = cz + 1;
            drill.getItem().onBlockStartBreak(drill, cx, y, cz, m);
            int priv = 0, open = 0;
            for (int dz = -1; dz <= 1; dz++) if (ow.getBlock(cx - 1, y, cz + dz) == Blocks.stone) priv++;
            for (int dx = 0; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (!(dx == 0 && dz == 0) && ow.getBlock(cx + dx, y, cz + dz) == Blocks.stone) open++;
            prot.dim = Integer.MIN_VALUE;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) ow.setBlockToAir(cx + dx, y, cz + dz);
            check(priv == 3 && open == 0, "в привате уцелело " + priv + " из 3, вне привата осталось " + open + " из 5");
            return "бур: в привате 3 блока целы, 5 соседних вне привата сломаны";
        });
    }

    private void debugTests(final int sx, final int sy, final int sz, final WorldServer ow) {
        final TestPlayer d = join(player(0, "TestDebug", sx - 3, sy, sz), false);
        final EntityIronGolem dg = golem(ow, sx - 4, sy, sz);
        final EntityZombie dz = new EntityZombie(ow);
        final com.divinerpgextended.debug.CommandDreDebug cmd = new com.divinerpgextended.debug.CommandDreDebug();
        final String[] logPath = new String[1];
        final int[] mark = new int[4];
        t("Отладка", "Без отладки ничего не пишется", () -> {
            check(!com.divinerpgextended.debug.Debug.any(), "отладка уже включена у кого-то");
            com.divinerpgextended.debug.Debug.note(d, "проверка");
            check(d.chat.isEmpty(), "без отладки пришло в чат: " + d.chat);
            return "пока никто не включил /dredebug - ни чата, ни файла";
        });
        t("Отладка", "Включение", () -> {
            cmd.processCommand(d, new String[] { "on" });
            check(com.divinerpgextended.debug.Debug.any(), "отладка не включилась");
            check(d.heard("Отладка: чат вкл, файл вкл"), "нет статуса в чате: " + d.chat);
            Object log = call(com.divinerpgextended.debug.Debug.class, "log", new Class<?>[0]);
            logPath[0] = ((com.divinerpgextended.debug.DebugLog) log).file().getAbsolutePath();
            return "включилась, статус в чате, файл " + new File(logPath[0]).getName();
        });
        // действия в этом тике, итоги удара - в конце тика
        try {
            d.chat.clear();
            golemHit(dg, d, 1);
            hit(d, DamageSource.causeMobDamage(dz), 4);
            hit(d, DamageSource.causeMobDamage(new EntityEnderSpider(ow)), 4);
            MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(sx - 3, sy - 1, sz, ow, Blocks.dirt, 0, d));
        } catch (Throwable ignored) {}
        // эффект - после первого снимка эффектов отладки (уже висящие при включении она считает исходными)
        at(48, () -> {
            d.addPotionEffect(new net.minecraft.potion.PotionEffect(Potion.regeneration.id, 400, 1));
            d.setHealth(10);
            d.heal(3);
        });
        at(43, () -> {
            t("Отладка", "Твой удар", () -> {
                check(d.heard("[удар] Iron Golem"), "нет строки о своём ударе: " + d.chat);
                return "строка [удар] Iron Golem с разбором";
            });
            t("Отладка", "Урон по тебе", () -> {
                check(d.heard("[урон] -"), "нет строки об уроне по себе: " + d.chat);
                return "строка [урон] с ударом, эффектами и бронёй";
            });
            t("Отладка", "Отменённый урон", () -> {
                check(d.heard("[урон отменён") && d.heard("ранга"), "нет отменённого удара с причиной: " + d.chat);
                return "удар эндер-паука по рангу 0: [урон отменён] и причина про ранг";
            });
            t("Отладка", "Файл действий", () -> {
                com.divinerpgextended.debug.DebugLog log = (com.divinerpgextended.debug.DebugLog) call(com.divinerpgextended.debug.Debug.class, "log",
                    new Class<?>[0]);
                log.flush();
                String all = new String(java.nio.file.Files.readAllBytes(new File(logPath[0]).toPath()), StandardCharsets.UTF_8);
                check(all.contains(";damage_dealt;") && all.contains(";damage_taken;") && all.contains(";block_break;"), "в файле нет нужных строк");
                mark[0] = all.length();
                return "в CSV есть damage_dealt, damage_taken, block_break";
            });
            try {
                cmd.processCommand(d, new String[] { "chat", "off" });
                d.chat.clear();
                golemHit(dg, d, 1);
            } catch (Throwable ignored) {}
        });
        at(46, () -> {
            t("Отладка", "Чат выкл, файл вкл", () -> {
                com.divinerpgextended.debug.DebugLog log = (com.divinerpgextended.debug.DebugLog) call(com.divinerpgextended.debug.Debug.class, "log",
                    new Class<?>[0]);
                log.flush();
                long len = new File(logPath[0]).length();
                check(!d.heard("[удар]"), "в чат пришло при выключенном чате: " + d.chat);
                check(len > mark[0], "файл не вырос");
                return "чат молчит, файл пишется";
            });
            try {
                cmd.processCommand(d, new String[] { "chat", "on" });
                cmd.processCommand(d, new String[] { "mobs", "on" });
                d.chat.clear();
                ow.difficultySetting = net.minecraft.world.EnumDifficulty.EASY;
                hit(d, DamageSource.causeMobDamage(dz), 10);
                hit(dg, DamageSource.causeMobDamage(dz), 3); // моб бьёт моба рядом с отладчиком
                FMLCommonHandler.instance().bus().post(new PlayerEvent.PlayerLoggedInEvent(d));
            } catch (Throwable ignored) {}
        });
        at(49, () -> {
            ow.difficultySetting = net.minecraft.world.EnumDifficulty.NORMAL;
            t("Отладка", "Сложность", () -> {
                check(d.heard("сложность easy"), "нет пометки о сложности: " + d.chat);
                return "на лёгкой сложности в разборе удара есть строка про поправку";
            });
            t("Отладка", "Бои мобов рядом", () -> {
                check(d.heard("[удар] Iron Golem") && d.heard("mob, Zombie"), "нет строки о бое мобов: " + d.chat);
                return "с mobs on видно, как зомби бьёт голема рядом";
            });
            t("Отладка", "Перезаход с отладкой", () -> {
                com.divinerpgextended.debug.DebugLog log = (com.divinerpgextended.debug.DebugLog) call(com.divinerpgextended.debug.Debug.class, "log",
                    new Class<?>[0]);
                log.flush();
                String all = new String(java.nio.file.Files.readAllBytes(new File(logPath[0]).toPath()), StandardCharsets.UTF_8);
                check(all.contains(";login;") && com.divinerpgextended.debug.Debug.any(), "после входа отладка не продолжилась");
                return "после входа отладка продолжает писать (login в файле)";
            });
            t("Отладка", "Аудит /dreinv", () -> {
                TestPlayer target = join(player(0, "TestInvTarget", sx, sy, sz), false);
                new com.divinerpgextended.accessory.CommandDreInv().processCommand(d, new String[] { "TestInvTarget" });
                File log = new File(server.getFile("logs"), "dre-audit.log");
                String all = new String(java.nio.file.Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
                check(all.contains("[TestDebug]") && all.contains("dreinv: TestInvTarget"), "в журнале аудита нет записи /dreinv");
                return "в dre-audit.log: dreinv TestInvTarget - правка";
            });
        });
        // итог лечения отладка пишет раз в 20 тиков сервера - проверяем после ближайшей такой отметки
        at(64, () -> {
            t("Отладка", "Эффекты и лечение", () -> {
                com.divinerpgextended.debug.DebugLog log = (com.divinerpgextended.debug.DebugLog) call(com.divinerpgextended.debug.Debug.class, "log",
                    new Class<?>[0]);
                log.flush();
                String all = new String(java.nio.file.Files.readAllBytes(new File(logPath[0]).toPath()), StandardCharsets.UTF_8);
                check(all.contains(";effect;+potion.regeneration"), "в файле нет записи о регенерации");
                check(all.contains(";heal;"), "в файле нет записи о лечении");
                return "наложенная регенерация и лечение записаны";
            });
            try {
                cmd.processCommand(d, new String[] { "off" });
            } catch (Throwable ignored) {}
            dg.setDead();
        });
    }

    // ---------------- Помощники ----------------

    /** Сытость ровно такая (через сохранение - addStats не ограничен снизу). */
    private static void food(EntityPlayer p, int level, float sat) {
        NBTTagCompound t = new NBTTagCompound();
        p.getFoodStats().writeNBT(t);
        t.setInteger("foodLevel", level);
        t.setFloat("foodSaturationLevel", sat);
        p.getFoodStats().readNBT(t);
    }

    /** Сменить время суток так, чтобы мир сразу знал, день сейчас или ночь. */
    private static void time(WorldServer w, long t) {
        w.setWorldTime(t);
        w.calculateInitialSkylight();
    }

    private static void eat(TestPlayer p, ItemStack s) {
        ItemStack copy = s.copy();
        ItemStack result = copy.getItem().onEaten(copy, p.worldObj, p);
        MinecraftForge.EVENT_BUS.post(new PlayerUseItemEvent.Finish(p, s, 0, result));
    }

    private static ItemStack craft(WorldServer w, Object[] grid) {
        InventoryCrafting inv = new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer p) {
                return true;
            }
        }, 3, 3);
        for (int i = 0; i < 9; i++) {
            Object o = grid[i];
            inv.setInventorySlotContents(i, o == null ? null : o instanceof ItemStack ? (ItemStack) o : new ItemStack((Item) o));
        }
        return CraftingManager.getInstance().findMatchingRecipe(inv, w);
    }

    private void fishingReport() {
        Random r = new Random(42);
        fishing.append("Рыбалка: 20000 подсечек на удочку в зоне OCEAN, без приманки / с приманкой\n");
        for (Rod rod : Rod.values()) {
            for (boolean baited : new boolean[] { false, true }) {
                int crates = 0, fish = 0, other = 0, relics = 0;
                for (int i = 0; i < 20000; i++) {
                    ItemStack s = FishingLoot.roll(r, Zone.OCEAN, rod.power, baited, rod.crateBonus(), rod.rareMultiplier());
                    if (s == null) other++;
                    else if (s.getItem() == ModItems.crate) crates++;
                    else if (s.getItem() == ModItems.fish) fish++;
                    else other++;
                    if (baited && FishingLoot.relic(r, Zone.OCEAN, rod.power) != null) relics++;
                }
                fishing.append(String.format("  %-9s %s: рыба %5.1f%%, ящики %5.2f%%, прочее %5.1f%%%s%n", rod.name(), baited ? "с приманкой" : "без      ",
                    fish / 200.0, crates / 200.0, other / 200.0, baited ? String.format(", реликвий %d", relics) : ""));
            }
        }
    }

    private void write() {
        try {
            File dir = server.getFile("logs");
            dir.mkdirs();
            int ok = 0, bad = 0, err = 0;
            for (String[] r : results) {
                if ("работает".equals(r[2])) ok++;
                else if ("баг".equals(r[2])) bad++;
                else err++;
            }
            PrintWriter txt = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(dir, "dre-selftest.txt")), StandardCharsets.UTF_8));
            txt.println("Самопроверка DivineRPG Extended: работает " + ok + ", баг " + bad + ", сбой теста " + err);
            for (String[] r : results) txt.println("[" + r[2] + "] " + r[0] + " / " + r[1] + ": " + r[3]);
            txt.println();
            txt.print(fishing);
            txt.close();
            PrintWriter csv = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(dir, "dre-selftest.csv")), StandardCharsets.UTF_8));
            for (String[] r : results) csv.println(r[0] + ";" + r[1] + ";" + r[2] + ";" + r[3].replace(';', ','));
            csv.close();
            System.out.println("[dre-selftest] работает " + ok + ", баг " + bad + ", сбой теста " + err);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
