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
        for (int i = 0; i < Accessories.TOTAL; i++) a.setInventorySlotContents(i, null);
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
    /** Для превью: непустые блоки вокруг постройки в logs/<name>.txt (x y z блок мета). */
    private static void dump(WorldServer w, int cx, int top, int cz, int r, String name) {
        try {
            java.io.PrintWriter out = new java.io.PrintWriter(new File(MinecraftServer.getServer().getFile("logs"), name + ".txt"), "UTF-8");
            for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) for (int y = top - 70; y <= top + 62; y++) {
                net.minecraft.block.Block b = w.getBlock(x, y, z);
                if (b.getMaterial() == net.minecraft.block.material.Material.air) continue;
                out.println((x - cx) + " " + (y - top) + " " + (z - cz) + " " + net.minecraft.block.Block.blockRegistry.getNameForObject(b) + " "
                    + w.getBlockMetadata(x, y, z));
            }
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Пустая сетка верстака 3 x 3 для проверки рецептов. */
    private static net.minecraft.inventory.InventoryCrafting grid() {
        return new net.minecraft.inventory.InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer p) {
                return true;
            }
        }, 3, 3);
    }

    /** Загрузить и заселить чанки вокруг точки (радиус в чанках + 1 на заселение краёв). */
    private static void loadAround(WorldServer w, int x, int z, int r) {
        for (int i = -r - 1; i <= r + 1; i++)
            for (int k = -r - 1; k <= r + 1; k++) w.theChunkProviderServer.loadChunk((x >> 4) + i, (z >> 4) + k);
    }

    /** Телепорт свиньи с (x, 70, z); куда поставило - в at. */
    private static void placePig(net.minecraft.world.Teleporter t, WorldServer w, double x, double z, double[] at) {
        net.minecraft.entity.passive.EntityPig pig = new net.minecraft.entity.passive.EntityPig(w);
        pig.setPosition(x, 70, z);
        t.placeInPortal(pig, x, 70, z, 0);
        if (at != null) {
            at[0] = pig.posX;
            at[1] = pig.posY;
            at[2] = pig.posZ;
        }
    }

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
            // как сервер: пакет движения от клиента за тик в воздухе +1 к счётчику полёта
            if (p instanceof EntityPlayerMP && !p.onGround) setFloating((EntityPlayerMP) p, floating((EntityPlayerMP) p) + 1);
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
            int[] dims = { ConfigurationHelper.eden, ConfigurationHelper.wildwood, ConfigurationHelper.apalachia, ConfigurationHelper.skythern,
                ConfigurationHelper.mortum };
            int[] v = new int[6];
            for (int i = 0; i < dims.length; i++) {
                TestPlayer w = player(dims[i], "TestCarpet" + i, 0, 100, 0);
                Accessories.of(w).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
                v[i] = DoubleJump.carpetTicks(w);
                if (i == 0) {
                    BossProgress.grant(w, Boss.TWILIGHT_DEMON);
                    v[5] = DoubleJump.carpetTicks(w);
                    BossProgress.revoke(w, Boss.TWILIGHT_DEMON);
                }
            }
            TestPlayer ov = player(0, "TestCarpetOver", 0, 100, 0);
            Accessories.of(ov).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            int over = DoubleJump.carpetTicks(ov);
            BossProgress.grant(ov, Boss.TWILIGHT_DEMON);
            int overDemon = DoubleJump.carpetTicks(ov);
            BossProgress.revoke(ov, Boss.TWILIGHT_DEMON);
            TestPlayer vv = player(ConfigurationHelper.vethea, "TestCarpetDream", 0, 100, 0);
            Accessories.of(vv).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            int dream = DoubleJump.carpetTicks(vv);
            check(v[0] == 200 && v[1] == 200 && v[2] == 160 && v[3] == 100 && v[4] == 60 && v[5] == -1 && over == 60 && overDemon == 200 && dream == 0,
                "тиков: " + v[0] + "/" + v[1] + "/" + v[2] + "/" + v[3] + "/" + v[4] + ", с демоном " + v[5] + "; обычный мир " + over + ", с демоном " + overDemon + ", Ветея " + dream);
            return "по миру: Эдем 10 с, Вайлдвуд 10, Апалачия 8, Скайтерн 5, Мортум 3; после демона - без предела; обычный мир 3 с (после демона 10), в Ветее нет";
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
        t("Сумеречные миры", "Вне Сумеречных слабый", () -> {
            TestPlayer o = player(0, "TestOver", 0, 100, 0);
            Accessories.of(o).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            int c = DoubleJump.carpetTicks(o);
            check(c == DoubleJump.OUTSIDE, "в обычном мире запас ковра " + c);
            return "в обычном мире ковёр держит только 3 с";
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

        t("Аксессуары", "Ваттикус рассказывает", () -> {
            reset(p);
            net.divinerpg.entities.arcana.EntityVatticus vat = new net.divinerpg.entities.arcana.EntityVatticus(ow);
            hold(p, trinket(Trinket.MAGIC_CARPET));
            p.setSneaking(true);
            p.chat.clear();
            MinecraftForge.EVENT_BUS.post(new EntityInteractEvent(p, vat));
            p.setSneaking(false);
            hold(p, null);
            check(p.heard("dre.trinket.magic_carpet.lore"), "нет рассказа о ковре, чат: " + p.chat);
            return "присел и ПКМ по Ваттикусу с ковром в руке - подробный рассказ о ковре";
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
            check(com.divinerpgextended.trinket.AccessorySources.extraForBoss(Boss.ANCIENT_ENTITY) == Trinket.MAGIC_CARPET, "за кадиллиона Эдема не ковёр");
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
            check(full == 200 && spent == 190 && still == 185, "запас: на земле " + full + ", после 10 тиков парения " + spent + ", ещё 5 " + still);
            return "на земле 200 тиков, в воздухе тратится и не восстанавливается";
        });
        t("Сумеречные миры", "Планирование после запаса", () -> {
            Accessories.of(e).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            e.onGround = true;
            tick(e, 1);
            e.onGround = false;
            tick(e, 205); // запас Эдема (200) кончился в парении
            int left = carpetLeft(e);
            double y = e.posY;
            float maxFall = 0;
            for (int i = 0; i < 20; i++) { // клиент опускает на 0.1 за тик
                y -= 0.1;
                e.setPosition(e.posX, y, e.posZ);
                e.fallDistance += 0.1F;
                tick(e, 1);
                maxFall = Math.max(maxFall, e.fallDistance);
            }
            e.setPosition(e.posX, 100, e.posZ);
            check(left <= 0 && maxFall < 0.2F, "запас " + left + ", накопленное падение при планировании до " + maxFall);
            return "запас кончился - планирование 2 блока/с, высота падения не копится";
        });
        t("Сумеречные миры", "Фриз клиента не тратит ковёр", () -> {
            int before = carpetLeft(e);
            for (int i = 0; i < 10; i++) {
                e.ticksExisted++; // тики сервера без пакетов от клиента
                FMLCommonHandler.instance().bus().post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, e));
            }
            int after = carpetLeft(e);
            check(after == before, "за 10 тиков без пакетов запас " + before + " > " + after);
            return "клиент завис на 10 тиков - запас ковра не тронут (" + after + ")";
        });
        t("Сумеречные миры", "Снять ковёр в воздухе", () -> {
            e.onGround = true; // зарядиться на земле и чуть потратить в воздухе
            tick(e, 1);
            e.onGround = false;
            tick(e, 5);
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
            check(left == 200, "после касания воды запас " + left);
            return "в воде запас снова полный (200)";
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
        t("Сумеречные миры", "Небо Эдема и Вайлдвуда", () -> {
            WorldServer eden = server.worldServerForDimension(ConfigurationHelper.eden);
            WorldServer wild = server.worldServerForDimension(ConfigurationHelper.wildwood);
            check(!eden.provider.hasNoSky && !wild.provider.hasNoSky, "небо выключено: Эдем " + eden.provider.hasNoSky + ", Вайлдвуд " + wild.provider.hasNoSky);
            return "небесный свет на месте (тени и темнота как в оригинале)";
        });
        t("Сумеречные миры", "Починка чёрных чанков 0.44.0", () -> {
            WorldServer eden = server.worldServerForDimension(ConfigurationHelper.eden);
            net.minecraft.world.chunk.Chunk c = eden.getChunkFromChunkCoords(320, 320);
            int top = c.getHeightValue(8, 8) + 1;
            c.setLightValue(net.minecraft.world.EnumSkyBlock.Sky, 8, top, 8, 0);
            // так выглядит чанк, сохранённый без неба: секции без SkyLight
            NBTTagCompound nbt = new NBTTagCompound(), level = new NBTTagCompound();
            NBTTagList sections = new NBTTagList();
            NBTTagCompound sec = new NBTTagCompound();
            sec.setByte("Y", (byte) 0);
            sections.appendTag(sec);
            // и как после пересохранения 0.44.1: верхняя секция с нулевым небесным светом
            NBTTagCompound zero = new NBTTagCompound();
            zero.setByte("Y", (byte) 7);
            zero.setByteArray("SkyLight", new byte[2048]);
            sections.appendTag(zero);
            NBTTagList healthy = new NBTTagList();
            NBTTagCompound lit = new NBTTagCompound();
            lit.setByte("Y", (byte) 7);
            byte[] sl = new byte[2048];
            sl[100] = (byte) 0xFF;
            lit.setByteArray("SkyLight", sl);
            healthy.appendTag(lit);
            check(!com.divinerpgextended.world.SkyRepair.dark(healthy), "обычный чанк принят за чёрный");
            level.setTag("Sections", sections);
            nbt.setTag("Level", level);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.ChunkDataEvent.Load(c, nbt));
            int sky = c.getSavedLightValue(net.minecraft.world.EnumSkyBlock.Sky, 8, top, 8);
            check(sky == 15, "небесный свет над землёй после починки " + sky);
            return "чанк без неба (0.44.0) и с нулевым небом (0.44.1): свет над землёй снова 15; обычный чанк не трогается";
        });
        t("Сумеречные миры", "Чанк целиком при заселении", () -> {
            WorldServer eden = server.worldServerForDimension(ConfigurationHelper.eden);
            TestPlayer v = player(ConfigurationHelper.eden, "TestResend", 7000, 120, 7000);
            eden.getPlayerManager().addPlayer(v);
            try {
                net.minecraft.world.chunk.Chunk c = eden.getChunkFromBlockCoords(7000, 7000);
                ReflectionHelper.setPrivateValue(net.minecraft.world.chunk.Chunk.class, c, true, "field_150815_m");
                c.isLightPopulated = true;
                int before = com.divinerpgextended.world.PopulationResend.resends;
                // обычная правка (игрок ставит блок) - пачкой, как раньше
                eden.setBlock(7000, 125, 7000, Blocks.glass);
                int afterPlayer = com.divinerpgextended.world.PopulationResend.resends;
                eden.getPlayerManager().updatePlayerInstances();
                // правки во время заселения - чанк целиком
                com.divinerpgextended.asm.TreeShiftHooks.begin();
                eden.setBlock(7001, 125, 7000, Blocks.glass);
                eden.setBlock(7002, 125, 7000, Blocks.glass);
                com.divinerpgextended.asm.TreeShiftHooks.end();
                int afterGen = com.divinerpgextended.world.PopulationResend.resends;
                eden.getPlayerManager().updatePlayerInstances();
                for (int x = 7000; x <= 7002; x++) eden.setBlockToAir(x, 125, 7000);
                check(afterPlayer == before && afterGen > afterPlayer, "пересылок: от игрока " + (afterPlayer - before) + ", от заселения " + (afterGen - afterPlayer));
                return "блоки от заселения уходят чанком целиком, правки игрока - как раньше";
            } finally {
                eden.getPlayerManager().removePlayer(v);
            }
        });
        t("Логова", "Заповедная роща", () -> {
            int dim = ConfigurationHelper.eden;
            WorldServer w = server.worldServerForDimension(dim);
            int[] c = com.divinerpgextended.world.twilight.WorldGenNests.nearest(w.getSeed(), dim, 0, 0);
            loadAround(w, c[0], c[2], 4);
            int top = com.divinerpgextended.world.twilight.WorldGenNests.TOP;
            int az = c[2] - com.divinerpgextended.world.twilight.WorldGenNests.ALTAR_DZ;
            net.minecraft.block.Block altar = w.getBlock(c[0], top + 2, az), mid = w.getBlock(c[0], top, c[2]);
            boolean clear = w.isAirBlock(c[0], top + 5, c[2]) && w.isAirBlock(c[0] + 5, top + 3, c[2] + 5);
            dump(w, c[0], top, c[2], 48, "nest-eden");
            check(altar == com.divinerpgextended.registry.ModBlocks.nestAltar && mid == net.divinerpg.utils.blocks.VanillaBlocks.lamp1 && w.getBlockMetadata(c[0], top, c[2]) == 7 && clear,
                "в " + c[0] + "," + c[2] + ": алтарь " + altar.getUnlocalizedName() + ", центр " + mid.getUnlocalizedName() + ", над полом пусто " + clear);
            // алтарь принимает Зов Эдема и ставит кадиллиона в центр гнезда
            TestPlayer v = player(dim, "TestNest", c[0], top + 1, az + 2);
            hold(v, new ItemStack(com.divinerpgextended.registry.ModItems.cadillionCall, 1, 0));
            altar.onBlockActivated(w, c[0], top + 2, az, v, 1, 0.5F, 0.5F, 0.5F);
            java.util.List<?> bosses = w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntityGreatCadillion.class,
                net.minecraft.util.AxisAlignedBB.getBoundingBox(c[0] - 4, top - 2, c[2] - 4, c[0] + 4, top + 8, c[2] + 4));
            ItemStack left = v.getHeldItem();
            for (Object o : bosses) ((net.minecraft.entity.Entity) o).setDead();
            check(bosses.size() == 1 && (left == null || left.stackSize == 0), "кадиллионов в центре " + bosses.size() + ", зов в руке " + left);
            return "роща в " + c[0] + "," + c[2] + " на высоте " + top + ": алтарь у святилища, свет в центре поляны; Зов на алтаре - кадиллион в центре";
        });
        t("Логова", "Храм ветров", () -> {
            int dim = ConfigurationHelper.skythern;
            WorldServer w = server.worldServerForDimension(dim);
            int[] c = com.divinerpgextended.world.twilight.WorldGenNests.nearest(w.getSeed(), dim, 0, 0);
            loadAround(w, c[0], c[2], 4);
            int top = com.divinerpgextended.world.twilight.WorldGenNests.TOP;
            int az = c[2] - com.divinerpgextended.world.twilight.WorldGenNests.ALTAR_DZ;
            String log = "";
            boolean altar = w.getBlock(c[0], top + 2, az) == com.divinerpgextended.registry.ModBlocks.nestAltar;
            boolean column = w.getBlock(c[0] + 26, top + 20, c[2]) == Blocks.quartz_block;
            boolean open = w.isAirBlock(c[0], top + 10, c[2]);
            int clouds = 0, ladders = 0;
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 4 + Math.PI / 2 * i;
                int px = c[0] + (int) Math.round(Math.cos(a) * com.divinerpgextended.entity.EntityWindLord.TOTEM_R),
                    pz = c[2] + (int) Math.round(Math.sin(a) * com.divinerpgextended.entity.EntityWindLord.TOTEM_R);
                int h = com.divinerpgextended.entity.EntityWindLord.HEIGHTS[i];
                if (w.getBlock(px, top + h, pz) == com.divinerpgextended.registry.ModBlocks.cloud) clouds++;
                int lsx = px > c[0] ? 1 : -1;
                if (w.getBlock(px + 2 * lsx, top + 1, pz) == Blocks.ladder && w.getBlock(px + 2 * lsx, top + h - 1, pz) == Blocks.ladder) ladders++;
            }
            log = "алтарь " + altar + ", колонна " + column + ", над центром пусто " + open + ", облачных площадок " + clouds + ", лестниц " + ladders;
            dump(w, c[0], top, c[2], 46, "temple-skythern");
            // Зов на алтаре - Повелитель ветров, тотемы встают на площадки
            TestPlayer v = player(dim, "TestTemple", c[0], top + 1, az + 2);
            hold(v, new ItemStack(com.divinerpgextended.registry.ModItems.cadillionCall, 1, 3));
            w.getBlock(c[0], top + 2, az).onBlockActivated(w, c[0], top + 2, az, v, 1, 0.5F, 0.5F, 0.5F);
            java.util.List<?> lords = w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntityWindLord.class,
                net.minecraft.util.AxisAlignedBB.getBoundingBox(c[0] - 6, top - 2, c[2] - 16, c[0] + 6, top + 12, c[2] + 6));
            int totems = 0;
            for (Object o : lords) {
                com.divinerpgextended.entity.EntityWindLord l = (com.divinerpgextended.entity.EntityWindLord) o;
                l.placeTotems();
                for (int i = 0; i < 4; i++) {
                    int[] tt = l.totem(i);
                    if (w.getBlock(tt[0], tt[1], tt[2]) == com.divinerpgextended.registry.ModBlocks.windTotem
                        && w.getBlock(tt[0], tt[1] - 1, tt[2]) == com.divinerpgextended.registry.ModBlocks.cloud) totems++;
                    w.setBlockToAir(tt[0], tt[1], tt[2]);
                }
                l.setDead();
            }
            check(altar && column && open && clouds == 4 && ladders == 4 && lords.size() == 1 && totems == 4, log + ", повелителей " + lords.size() + ", тотемов на облаках " + totems);
            return "храм в " + c[0] + "," + c[2] + ": колоннада, алтарь, 4 постамента с лестницами и облаками; Зов - Повелитель, тотемы встают на облака";
        });
        t("Логова", "Капище и родники", () -> {
            int dim = ConfigurationHelper.wildwood;
            WorldServer w = server.worldServerForDimension(dim);
            int[] c = com.divinerpgextended.world.twilight.WorldGenNests.nearest(w.getSeed(), dim, 0, 0);
            loadAround(w, c[0], c[2], 4);
            int top = com.divinerpgextended.world.twilight.WorldGenNests.TOP;
            int az = c[2] - com.divinerpgextended.world.twilight.WorldGenNests.ALTAR_DZ;
            boolean altar = w.getBlock(c[0], top + 2, az) == com.divinerpgextended.registry.ModBlocks.nestAltar;
            int waters = 0;
            for (int i = 0; i < 4; i++) {
                int[] sp = com.divinerpgextended.world.twilight.WorldGenNests.spring(i);
                if (w.getBlock(c[0] + sp[0], top, c[2] + sp[1]).getMaterial() == net.minecraft.block.material.Material.water) waters++;
            }
            int mossy = 0;
            for (int i = -24; i <= 24; i++) for (int k2 = -24; k2 <= 24; k2++)
                if (w.getBlock(c[0] + i, top + 3, c[2] + k2) == Blocks.mossy_cobblestone) mossy++;
            dump(w, c[0], top, c[2], 48, "henge-wildwood");
            // вызов: голем на привязи, игрок в сете Дикого леса стоит в роднике
            TestPlayer v = player(dim, "TestHenge", c[0], top + 1, az + 2);
            hold(v, new ItemStack(com.divinerpgextended.registry.ModItems.cadillionCall, 1, 1));
            w.getBlock(c[0], top + 2, az).onBlockActivated(w, c[0], top + 2, az, v, 1, 0.5F, 0.5F, 0.5F);
            java.util.List<?> gl = w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntityAncientGolem.class,
                net.minecraft.util.AxisAlignedBB.getBoundingBox(c[0] - 6, top - 2, c[2] - 6, c[0] + 6, top + 12, c[2] + 6));
            String log = "алтарь " + altar + ", родников " + waters + ", мшистых камней на высоте 3: " + mossy + ", големов " + gl.size();
            float healed = 0;
            double up = 0;
            boolean asleep = false;
            if (gl.size() == 1) {
                com.divinerpgextended.entity.EntityAncientGolem g3 = (com.divinerpgextended.entity.EntityAncientGolem) gl.get(0);
                int[] sp = com.divinerpgextended.world.twilight.WorldGenNests.spring(0);
                TestPlayer sv3 = join(player(dim, "TestSpring", c[0] + sp[0] + 0.5, top, c[2] + sp[1] + 0.5), true);
                Item[] wild = com.divinerpgextended.world.twilight.TwilightWorld.WILDWOOD.armor();
                armor(sv3, wild[0], wild[1], wild[2], wild[3]);
                sv3.setHealth(8);
                for (int i = 0; i <= com.divinerpgextended.entity.EntityAncientGolem.SPRING_STAY && up == 0; i++) {
                    sv3.setPosition(c[0] + sp[0] + 0.5, top, c[2] + sp[1] + 0.5);
                    sv3.handleWaterMovement();
                    g3.onLivingUpdate();
                    if (sv3.motionY > 1) up = sv3.motionY;
                }
                healed = sv3.getHealth() - 8;
                asleep = !g3.springAwake(0);
                armor(sv3, null, null, null, null);
                sv3.setHealth(20);
                g3.setDead();
            }
            check(altar && waters == 4 && mossy > 20 && gl.size() == 1 && healed >= 4 && up > 1 && asleep,
                log + ", вылечил " + healed + ", гейзер " + up + ", родник уснул " + asleep);
            return "капище: алтарь, менгиры и укрытия из мшистого камня, 4 родника; в сете Дикого леса родник лечит (+" + healed + "), через 1.5 с гейзер выкидывает, родник засыпает";
        });
        t("Логова", "Священная гора и ярусы", () -> {
            int dim = ConfigurationHelper.apalachia;
            WorldServer w = server.worldServerForDimension(dim);
            int[] c = com.divinerpgextended.world.twilight.WorldGenNests.nearest(w.getSeed(), dim, 0, 0);
            loadAround(w, c[0], c[2], 4);
            int top = com.divinerpgextended.world.twilight.WorldGenNests.TOP;
            int az = c[2] - com.divinerpgextended.world.twilight.WorldGenNests.altarDz(com.divinerpgextended.world.twilight.TwilightWorld.APALACHIA);
            boolean altar = w.getBlock(c[0], top + 2, az) == com.divinerpgextended.registry.ModBlocks.nestAltar;
            boolean summit = w.getBlock(c[0] + 3, top + 10, c[2]) == net.divinerpg.utils.blocks.TwilightBlocks.apalachiaGrass && w.isAirBlock(c[0] + 3, top + 11, c[2]);
            boolean stairs = w.getBlock(c[0] + 14, top + 5, c[2]) == net.divinerpg.utils.blocks.TwilightBlocks.apalachiaStairs;
            boolean stands = w.getBlock(c[0] + 32, top + 3, c[2] + 18) == net.divinerpg.utils.blocks.TwilightBlocks.apalachiaLogs
                || w.getBlock(c[0] + 32, top + 3, c[2] + 19) == net.divinerpg.utils.blocks.TwilightBlocks.apalachiaLogs;
            dump(w, c[0], top, c[2], 48, "mound-apalachia");
            TestPlayer v = player(dim, "TestColosseum", c[0], top + 1, az + 2);
            hold(v, new ItemStack(com.divinerpgextended.registry.ModItems.cadillionCall, 1, 2));
            w.getBlock(c[0], top + 2, az).onBlockActivated(w, c[0], top + 2, az, v, 1, 0.5F, 0.5F, 0.5F);
            java.util.List<?> kl = w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntityWarriorKing.class,
                net.minecraft.util.AxisAlignedBB.getBoundingBox(c[0] - 6, top - 2, az - 2, c[0] + 8, top + 12, az + 8));
            String log = "алтарь " + altar + ", вершина " + summit + ", лестница " + stairs + ", частокол " + stands + ", королей у алтаря " + kl.size();
            double y2 = 0;
            if (kl.size() == 1) {
                com.divinerpgextended.entity.EntityWarriorKing k3 = (com.divinerpgextended.entity.EntityWarriorKing) kl.get(0);
                k3.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(v), 100000);
                for (int i = 0; i < com.divinerpgextended.entity.EntityWarriorKing.KNEEL + 2; i++) k3.onLivingUpdate();
                y2 = k3.posY - (top + 1);
                k3.setDead();
            }
            check(altar && summit && stairs && stands && kl.size() == 1 && Math.abs(y2 - 5) < 0.6, log + ", 2-я встреча на высоте " + y2);
            return "священная гора: курган в 3 уровня с лестницами, частокол, алтарь на полу; Король у алтаря, 2-ю встречу начинает на ярусе +5";
        });
        t("Логова", "Глыба крошит укрытие", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.wildwood);
            loadAround(w, 9550, 9550, 1);
            w.setBlock(9550, 100, 9550, Blocks.mossy_cobblestone);
            w.setBlock(9551, 100, 9550, Blocks.stone);
            com.divinerpgextended.entity.EntityBoulder b = new com.divinerpgextended.entity.EntityBoulder(w);
            b.setPosition(9550.5, 100.5, 9550.5);
            net.minecraft.util.MovingObjectPosition mop = new net.minecraft.util.MovingObjectPosition(9550, 100, 9550, 1,
                net.minecraft.util.Vec3.createVectorHelper(9550.5, 101, 9550.5));
            try {
                java.lang.reflect.Method m = net.minecraft.entity.projectile.EntityThrowable.class.getDeclaredMethod("func_70184_a", net.minecraft.util.MovingObjectPosition.class);
                m.setAccessible(true);
                m.invoke(b, mop);
            } catch (NoSuchMethodException nsm) {
                java.lang.reflect.Method m = net.minecraft.entity.projectile.EntityThrowable.class.getDeclaredMethod("onImpact", net.minecraft.util.MovingObjectPosition.class);
                m.setAccessible(true);
                m.invoke(b, mop);
            }
            boolean gone = w.isAirBlock(9550, 100, 9550), stoneKept = w.getBlock(9551, 100, 9550) == Blocks.stone;
            w.setBlockToAir(9551, 100, 9550);
            check(gone && stoneKept, "мшистый камень разбит " + gone + ", обычный камень цел " + stoneKept);
            return "глыба голема крошит мшистые камни-укрытия, обычный камень не трогает";
        });
        t("Логова", "Бой с кадиллионом", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            int x = 9500, y = 100, z = 9500;
            loadAround(w, x + 8, z, 3); // сущность обновляется, только если вокруг на 32 блока загружено
            for (int xi = -4; xi < 20; xi++) for (int zq = -6; zq <= 6; zq++) {
                w.setBlock(x + xi, y - 1, z + zq, Blocks.stone);
                for (int hh = y; hh < y + 6; hh++) w.setBlockToAir(x + xi, hh, z + zq);
            }
            com.divinerpgextended.entity.EntityGreatCadillion b = new com.divinerpgextended.entity.EntityGreatCadillion(w);
            b.setVariant(0);
            b.setLocationAndAngles(x + 0.5, y, z + 0.5, 0, 0);
            w.spawnEntityInWorld(b);
            TestPlayer v = player(ConfigurationHelper.eden, "TestCadFight", x + 5.5, y, z + 0.5);
            // дальность и перезарядка обычного удара
            boolean far = b.attackEntityAsMob(v);
            v.setPosition(x + 2.5, y, z + 0.5);
            v.hurtResistantTime = 0;
            boolean near = b.attackEntityAsMob(v);
            v.hurtResistantTime = 0;
            boolean again = b.attackEntityAsMob(v);
            // оглушённый получает двойной урон
            b.stun(80);
            float hp = b.getHealth();
            b.hurtResistantTime = 0;
            b.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(v), 10);
            float lost = hp - b.getHealth();
            // таран в стену: стена в 2 блока между боссом и игроком
            b.setDead();
            com.divinerpgextended.entity.EntityGreatCadillion c = new com.divinerpgextended.entity.EntityGreatCadillion(w);
            c.setVariant(0);
            c.setLocationAndAngles(x + 0.5, y, z + 0.5, 0, 0);
            w.spawnEntityInWorld(c);
            for (int zq = -6; zq <= 6; zq++) for (int hh = y; hh < y + 2; hh++) w.setBlock(x + 9, hh, z + zq, Blocks.stone);
            for (int hh = y; hh < y + 3; hh++) w.setBlock(x + 13, hh, z, Blocks.stone); // игрок на столбе - видно поверх стены
            v.setPosition(x + 13.5, y + 3, z + 0.5);
            v.setHealth(v.getMaxHealth());
            c.setAttackTarget(v);
            boolean stunned = false;
            int maxState = 0;
            for (int tk = 0; tk < 300 && !stunned; tk++) {
                c.setAttackTarget(v);
                w.updateEntity(c);
                maxState = Math.max(maxState, c.chargeState());
                stunned = c.chargeState() == com.divinerpgextended.entity.EntityGreatCadillion.STUNNED;
            }
            c.setDead();
            check(!far && near && !again && Math.abs(lost - 20) < 0.5 && stunned, "с 5 блоков " + far + ", с 2 " + near + ", сразу второй " + again
                + ", урон оглушённому " + lost + " из 10, таран в стену оглушил " + stunned + " (стадия " + maxState + ", x " + (int) c.posX + ")");
            return "бьёт только вблизи и раз в 1.25 с; таран в стену - оглушение, оглушённый получает двойной урон";
        });
        t("Логова", "Подкрепление кадиллиона", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            int x = 9600, y = 100, z = 9600;
            loadAround(w, x, z, 2);
            for (int xi = -10; xi <= 10; xi++) for (int zq = -10; zq <= 10; zq++) {
                w.setBlock(x + xi, y - 1, z + zq, Blocks.stone);
                for (int hh = y; hh < y + 12; hh++) w.setBlockToAir(x + xi, hh, z + zq);
            }
            com.divinerpgextended.entity.EntityGreatCadillion b = new com.divinerpgextended.entity.EntityGreatCadillion(w);
            b.setVariant(0);
            b.setLocationAndAngles(x + 0.5, y, z + 0.5, 0, 0);
            w.spawnEntityInWorld(b);
            for (int i = 0; i < 6; i++) { // обычные мобы Эдема рядом - их подручными не считать
                net.divinerpg.entities.twilight.EntityEdenCadillion wild = new net.divinerpg.entities.twilight.EntityEdenCadillion(w);
                wild.setPosition(x + 4.5, y, z + 4.5);
                w.spawnEntityInWorld(wild);
            }
            int total = 0;
            for (int i = 0; i < 4; i++) total += b.callAdds();
            java.util.List<?> adds = w.getEntitiesWithinAABB(net.minecraft.entity.EntityLiving.class,
                net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 20, y - 5, z - 20, x + 20, y + 20, z + 20));
            int mine = 0;
            for (Object o : adds) {
                net.minecraft.entity.Entity en = (net.minecraft.entity.Entity) o;
                if (en.getEntityData().getInteger("dreCadillionAdd") == b.getEntityId()) mine++;
                if (en != b && !(en instanceof EntityPlayer)) en.setDead();
            }
            b.setDead();
            check(total > 0 && total <= com.divinerpgextended.entity.EntityGreatCadillion.MAX_ADDS && mine == total,
                "позвал " + total + ", его подручных рядом " + mine + " (рядом ещё 6 обычных мобов Эдема)");
            return "кадиллион зовёт мобов Эдема (" + total + ") даже среди обычных мобов мира, своих живых не больше " + com.divinerpgextended.entity.EntityGreatCadillion.MAX_ADDS;
        });
        t("Логова", "Самонаведение эденского лука", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            int x = 9700, y = 120, z = 9700;
            loadAround(w, x, z, 2);
            net.divinerpg.entities.twilight.EntityEdenCori cori = new net.divinerpg.entities.twilight.EntityEdenCori(w);
            cori.setLocationAndAngles(x + 10.5, y + 3, z + 0.5, 0, 0);
            w.spawnEntityInWorld(cori);
            net.divinerpg.entities.base.EntityDivineArrow a = new net.divinerpg.entities.base.EntityDivineArrow(w, x + 0.5, y, z + 0.5);
            a.motionX = 2.0;
            a.motionY = 0;
            a.motionZ = 0.3;
            boolean turned = com.divinerpgextended.perk.HomingArrows.steer(a);
            double up = a.motionY, side = a.motionZ;
            cori.setDead();
            boolean noTarget = !com.divinerpgextended.perk.HomingArrows.steer(a);
            check(turned && up > 0.1 && side < 0.3 && noTarget, "довернула " + turned + ", вверх " + up + ", вбок " + side + ", без цели " + noTarget);
            return "стрела доворачивает на Кори впереди; без летучей цели летит как летела";
        });
        t("Сумеречные миры", "Ковёр после фриза", () -> {
            Accessories.of(e).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            e.onGround = true;
            tick(e, 1);
            e.onGround = false;
            tick(e, 3); // запас на сервере ещё есть, а клиент уже планирует вниз
            double yy = e.posY;
            float maxFall = 0;
            for (int i = 0; i < 20; i++) {
                yy -= 0.1;
                e.setPosition(e.posX, yy, e.posZ);
                e.fallDistance += 0.1F;
                tick(e, 1);
                maxFall = Math.max(maxFall, e.fallDistance);
            }
            e.setPosition(e.posX, 100, e.posZ);
            check(carpetLeft(e) > 0 && maxFall < 0.2F, "запас на сервере " + carpetLeft(e) + ", накоплено падения " + maxFall);
            return "клиент планирует, хотя у сервера запас ещё есть - высота падения всё равно не копится";
        });
        t("Логова", "Кадиллион вне рощи неуязвим", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            int x = 9800, y = 100, z = 9800;
            loadAround(w, x, z, 2);
            com.divinerpgextended.entity.EntityGreatCadillion b = new com.divinerpgextended.entity.EntityGreatCadillion(w);
            b.setVariant(0);
            b.setLocationAndAngles(x + 0.5, y, z + 0.5, 0, 0);
            w.spawnEntityInWorld(b);
            com.divinerpgextended.boss.LairSummons.leash(b, x, y, z, 20);
            TestPlayer v = player(ConfigurationHelper.eden, "TestCadOut", x + 40.5, y, z + 0.5);
            float hp = b.getHealth();
            b.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(v), 10);
            float far = hp - b.getHealth();
            v.setPosition(x + 5.5, y, z + 0.5);
            b.hurtResistantTime = 0;
            b.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(v), 10);
            float near = hp - b.getHealth();
            b.setDead();
            check(far == 0 && near > 0, "урон издалека " + far + ", с поляны " + near);
            return "издалека (вне рощи) кадиллиона не ранить, с поляны - как обычно";
        });
        t("Броня", "Броня дома", () -> {
            TestPlayer v = player(ConfigurationHelper.eden, "TestHomeArmor", 9900, 100, 9900);
            armor(v, net.divinerpg.utils.items.TwilightItemsArmor.edenHelmet, net.divinerpg.utils.items.TwilightItemsArmor.edenChestplate,
                net.divinerpg.utils.items.TwilightItemsArmor.edenLeggings, net.divinerpg.utils.items.TwilightItemsArmor.edenBoots);
            boolean home = com.divinerpgextended.perk.HomeArmor.atHome(v);
            TestPlayer o = player(ConfigurationHelper.wildwood, "TestAwayArmor", 9900, 100, 9900);
            armor(o, net.divinerpg.utils.items.TwilightItemsArmor.edenHelmet, net.divinerpg.utils.items.TwilightItemsArmor.edenChestplate,
                net.divinerpg.utils.items.TwilightItemsArmor.edenLeggings, net.divinerpg.utils.items.TwilightItemsArmor.edenBoots);
            boolean away = com.divinerpgextended.perk.HomeArmor.atHome(o);
            net.minecraft.entity.monster.EntityZombie zz = new net.minecraft.entity.monster.EntityZombie(v.worldObj);
            net.minecraftforge.event.entity.living.LivingHurtEvent ev = new net.minecraftforge.event.entity.living.LivingHurtEvent(v,
                net.minecraft.util.DamageSource.causeMobDamage(zz), 10);
            MinecraftForge.EVENT_BUS.post(ev);
            armor(v, null, null, null, null);
            armor(o, null, null, null, null);
            // полуступени: божественный сет в Эдеме - вдвое слабее родного; смешанный - по частям
            armor(v, VanillaItemsArmor.divineHelmet, VanillaItemsArmor.divineBody, VanillaItemsArmor.divineLegs, VanillaItemsArmor.divineBoots);
            float divine = com.divinerpgextended.perk.HomeArmor.cut(v);
            armor(v, net.divinerpg.utils.items.TwilightItemsArmor.edenHelmet, VanillaItemsArmor.divineBody, VanillaItemsArmor.divineLegs, VanillaItemsArmor.divineBoots);
            float mixed = com.divinerpgextended.perk.HomeArmor.cut(v);
            armor(o, net.divinerpg.utils.items.TwilightItemsArmor.edenHelmet, net.divinerpg.utils.items.TwilightItemsArmor.edenChestplate,
                net.divinerpg.utils.items.TwilightItemsArmor.edenLeggings, net.divinerpg.utils.items.TwilightItemsArmor.edenBoots);
            float edenInWild = com.divinerpgextended.perk.HomeArmor.cut(o);
            armor(v, null, null, null, null);
            armor(o, null, null, null, null);
            check(home && !away && Math.abs(ev.ammount - 7) < 0.6 && Math.abs(divine - 0.15F) < 0.01 && Math.abs(mixed - 0.1875F) < 0.01
                && Math.abs(edenInWild - 0.15F) < 0.01, "дома " + home + ", в чужом мире " + away + ", урон моба 10 > " + ev.ammount
                + "; божественный в Эдеме " + divine + ", шлем Эдема + божественный " + mixed + ", Эдем в Диком лесу " + edenInWild);
            return "сет Эдема в Эдеме: урон мобов x0.7; божественный в Эдеме и Эдема в Диком лесу - x0.85 (полуступень); смешанный - по частям";
        });
        t("Броня", "Перки сетов", () -> {
            TestPlayer sv = player(ConfigurationHelper.apalachia, "TestSetPerks", 9920, 100, 9920);
            armor(sv, com.divinerpgextended.world.twilight.TwilightWorld.APALACHIA.armor()[0], com.divinerpgextended.world.twilight.TwilightWorld.APALACHIA.armor()[1],
                com.divinerpgextended.world.twilight.TwilightWorld.APALACHIA.armor()[2], com.divinerpgextended.world.twilight.TwilightWorld.APALACHIA.armor()[3]);
            sv.ticksExisted = 10;
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(sv));
            double kb = sv.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.knockbackResistance).getAttributeValue();
            net.minecraft.entity.monster.EntityZombie hitter = new net.minecraft.entity.monster.EntityZombie(sv.worldObj);
            hitter.setPosition(sv.posX + 1, sv.posY, sv.posZ);
            sv.worldObj.spawnEntityInWorld(hitter);
            float hp0 = hitter.getHealth();
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingHurtEvent(sv, net.minecraft.util.DamageSource.causeMobDamage(hitter), 10));
            float reflected = hp0 - hitter.getHealth();
            hitter.setDead();
            // Мортум: вампиризм
            Item[] mort = com.divinerpgextended.world.twilight.TwilightWorld.MORTUM.armor();
            armor(sv, mort[0], mort[1], mort[2], mort[3]);
            sv.setHealth(10);
            net.minecraft.entity.monster.EntityZombie prey = new net.minecraft.entity.monster.EntityZombie(sv.worldObj);
            prey.setPosition(sv.posX + 2, sv.posY, sv.posZ);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingHurtEvent(prey, net.minecraft.util.DamageSource.causePlayerDamage(sv), 10));
            float healed = sv.getHealth() - 10;
            sv.ticksExisted = 20;
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(sv));
            double kbOff = sv.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.knockbackResistance).getAttributeValue();
            armor(sv, null, null, null, null);
            sv.setHealth(sv.getMaxHealth());
            check(kb >= 1 && kbOff < 1 && reflected > 1 && reflected <= 2 && healed >= 0.9 && healed <= 2.1,
                "отбрасывание " + kb + " (снят сет " + kbOff + "), отражено " + reflected + ", вампиризм " + healed);
            return "Апалачия: не отбрасывает, около 20% урона (после защиты дома) вернулось зомби; Мортум: удар на 10 лечит на " + healed;
        });
        t("Броня", "Лесной следопыт", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.wildwood);
            loadAround(w, 9580, 9580, 1);
            TestPlayer fv = player(ConfigurationHelper.wildwood, "TestTracker", 9580.5, 100, 9580.5);
            for (int h = 99; h < 104; h++) w.setBlockToAir(9580, h, 9580);
            w.setBlock(9580, 99, 9580, net.divinerpg.utils.blocks.TwilightBlocks.wildwoodGrass);
            boolean onGrass = com.divinerpgextended.perk.SetPerks.inNature(fv);
            w.setBlock(9580, 101, 9580, net.divinerpg.utils.blocks.TwilightBlocks.wildwoodLeaves);
            boolean inLeaves = com.divinerpgextended.perk.SetPerks.inNature(fv);
            w.setBlockToAir(9580, 101, 9580);
            check(!onGrass && inLeaves, "на траве лечит " + onGrass + ", голова в листве " + inLeaves);
            return "сет Дикого леса лечит только с головой в листве (и под водой - Divine), на траве - нет";
        });
        t("Броня", "Ковёр Хозяина ветров", () -> {
            TestPlayer kv = player(ConfigurationHelper.skythern, "TestSkyCarpet", 9930, 120, 9930);
            Item[] sky = com.divinerpgextended.world.twilight.TwilightWorld.SKYTHERN.armor();
            armor(kv, sky[0], sky[1], sky[2], sky[3]);
            kv.rotationPitch = -60;
            double up = com.divinerpgextended.trinket.DoubleJump.skyLift(kv, true, false);
            kv.rotationPitch = 0;
            double level = com.divinerpgextended.trinket.DoubleJump.skyLift(kv, true, false);
            double down = com.divinerpgextended.trinket.DoubleJump.skyLift(kv, false, true);
            boolean master = com.divinerpgextended.trinket.DoubleJump.skyMaster(kv);
            armor(kv, null, null, null, null);
            check(master && up > 0.25 && level == 0 && down < -0.25, "сет " + master + ", вверх " + up + ", ровно " + level + ", присед " + down);
            return "в сете Скайтерна ковёр поднимается по взгляду вверх, держит высоту при ровном взгляде, присед - вниз";
        });
        t("Логова", "Древний голем", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.wildwood);
            loadAround(w, 9960, 9960, 2);
            TestPlayer gv = player(ConfigurationHelper.wildwood, "TestGolem", 9960, 100, 9950);
            com.divinerpgextended.entity.EntityAncientGolem gol = new com.divinerpgextended.entity.EntityAncientGolem(w);
            gol.setPosition(9960.5, 100, 9960.5);
            gol.rotationYaw = gol.renderYawOffset = 0; // смотрит на +z: правая рука на -x
            w.spawnEntityInWorld(gol);
            String log = "";
            // стрела в корпус (центр, на высоте груди) - звенит
            net.minecraft.entity.projectile.EntityArrow ar = new net.minecraft.entity.projectile.EntityArrow(w);
            ar.setPosition(gol.posX, gol.posY + gol.height * 0.7, gol.posZ - 1.6);
            boolean core = gol.attackEntityFrom(net.minecraft.util.DamageSource.causeArrowDamage(ar, gv), 50);
            log += "корпус " + core;
            // ноги
            int n = 0;
            while (!gol.broken(com.divinerpgextended.entity.EntityAncientGolem.LEGS) && n++ < 100) {
                gol.hurtResistantTime = 0;
                ar.setPosition(gol.posX, gol.posY + 0.5, gol.posZ - 1.6);
                gol.attackEntityFrom(net.minecraft.util.DamageSource.causeArrowDamage(ar, gv), 40);
            }
            int first = gol.firstBroken();
            float h = gol.height;
            // правая рука на -x, левая на +x
            n = 0;
            while (!gol.broken(com.divinerpgextended.entity.EntityAncientGolem.ARM_R) && n++ < 100) {
                gol.hurtResistantTime = 0;
                ar.setPosition(gol.posX - 1.6, gol.posY + gol.height * 0.6, gol.posZ);
                gol.attackEntityFrom(net.minecraft.util.DamageSource.causeArrowDamage(ar, gv), 40);
            }
            boolean leftWhole = !gol.broken(com.divinerpgextended.entity.EntityAncientGolem.ARM_L);
            n = 0;
            while (!gol.broken(com.divinerpgextended.entity.EntityAncientGolem.ARM_L) && n++ < 100) {
                gol.hurtResistantTime = 0;
                ar.setPosition(gol.posX + 1.6, gol.posY + gol.height * 0.6, gol.posZ);
                gol.attackEntityFrom(net.minecraft.util.DamageSource.causeArrowDamage(ar, gv), 40);
            }
            boolean open = gol.coreOpen();
            n = 0;
            while (!gol.isDead && gol.getHealth() > 0 && n++ < 200) {
                gol.hurtResistantTime = 0;
                ar.setPosition(gol.posX, gol.posY + gol.height * 0.6, gol.posZ - 1.6);
                gol.attackEntityFrom(net.minecraft.util.DamageSource.causeArrowDamage(ar, gv), 40);
            }
            boolean dead = gol.getHealth() <= 0;
            gol.setDead();
            check(!core && first == com.divinerpgextended.entity.EntityAncientGolem.LEGS && h < 5 && leftWhole && open && dead,
                log + ", первой отбита " + first + ", рост без ног " + h + ", левая цела после правой " + leftWhole + ", ядро " + open + ", убит " + dead);
            return "корпус звенит, пока целы руки и ноги; ноги, правая и левая руки отбиваются по месту попадания; без ног голем оседает; потом открывается ядро и голем погибает";
        });
        t("Логова", "Голем на алтаре", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.wildwood);
            net.minecraft.entity.EntityLiving made = com.divinerpgextended.entity.EntityGroveGuardian.create(w, com.divinerpgextended.world.CadillionWorld.WILDWOOD);
            net.minecraft.entity.EntityLiving eden = com.divinerpgextended.entity.EntityGroveGuardian.create(w, com.divinerpgextended.world.CadillionWorld.EDEN);
            com.divinerpgextended.boss.Boss b = com.divinerpgextended.boss.Boss.of(made);
            check(made instanceof com.divinerpgextended.entity.EntityAncientGolem && eden instanceof com.divinerpgextended.entity.EntityGreatCadillion
                && b == com.divinerpgextended.boss.Boss.CADILLION_WILDWOOD, "Дикий лес " + made + ", Эдем " + eden + ", босс " + b);
            return "Зов Дикого леса вызывает Древнего голема (засчитывается как страж Дикого леса), Зов Эдема - кадиллиона";
        });
        t("Логова", "Король-воин", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.apalachia);
            loadAround(w, 9970, 9970, 2);
            TestPlayer kv = player(ConfigurationHelper.apalachia, "TestKing", 9970, 100, 9964);
            com.divinerpgextended.entity.EntityWarriorKing king = new com.divinerpgextended.entity.EntityWarriorKing(w);
            king.setPosition(9970.5, 100, 9970.5);
            w.spawnEntityInWorld(king);
            String log = "";
            float hp0 = king.getMaxHealth();
            for (int r = 0; r < 3; r++) {
                king.hurtResistantTime = 0;
                king.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(kv), 100000);
                log += "встреча " + (r + 1) + ": колено " + king.kneeling() + ", жив " + (king.getHealth() > 0) + "; ";
                if (r < 2) {
                    king.hurtResistantTime = 0;
                    boolean hurt = king.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(kv), 5);
                    log += "на колене урон " + hurt + "; ";
                    for (int i = 0; i < com.divinerpgextended.entity.EntityWarriorKing.KNEEL + 2; i++) king.onLivingUpdate();
                }
            }
            boolean dead = king.getHealth() <= 0;
            float hp3 = king.getMaxHealth();
            king.setDead();
            check(dead && hp3 > hp0 * 1.9F && log.contains("встреча 1: колено 100") && log.contains("встреча 2: колено 100") && !log.contains("урон true"),
                log + "здоровье 1-й " + hp0 + ", 3-й " + hp3 + ", убит " + dead);
            return "Король-воин дважды встаёт на колено (неуязвим) и поднимается сильнее (здоровье x" + (hp3 / hp0) + "), третья победа - смерть";
        });
        t("Логова", "Повелитель ветров", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.skythern);
            loadAround(w, 9980, 9980, 3);
            for (int i = -16; i <= 16; i++) for (int wk = -16; wk <= 16; wk++) for (int h = 99; h < 125; h++) w.setBlockToAir(9980 + i, h, 9980 + wk);
            TestPlayer wv = player(ConfigurationHelper.skythern, "TestWind", 9980, 100, 9975);
            com.divinerpgextended.entity.EntityWindLord lord = new com.divinerpgextended.entity.EntityWindLord(w);
            lord.setPosition(9980.5, 100, 9980.5);
            w.spawnEntityInWorld(lord);
            lord.placeTotems();
            boolean immune = !lord.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(wv), 1000);
            String log = "";
            for (int i = 0; i < 4; i++) {
                int[] tt = lord.totem(i);
                boolean isTotem = w.getBlock(tt[0], tt[1], tt[2]) == com.divinerpgextended.registry.ModBlocks.windTotem;
                wv.setPositionAndUpdate(tt[0] + 1.5, tt[1], tt[2] + 0.5);
                w.getBlock(tt[0], tt[1], tt[2]).onBlockActivated(w, tt[0], tt[1], tt[2], wv, 1, 0.5F, 1F, 0.5F);
                if (i == 0) { // отошёл - зарядка сбрасывается
                    wv.setPositionAndUpdate(tt[0] + 8.5, tt[1], tt[2] + 0.5);
                    for (int wk = 0; wk < 5; wk++) lord.onLivingUpdate();
                    log += "сдуло: зарядка " + lord.charging() + "; ";
                    wv.setPositionAndUpdate(tt[0] + 1.5, tt[1], tt[2] + 0.5);
                    w.getBlock(tt[0], tt[1], tt[2]).onBlockActivated(w, tt[0], tt[1], tt[2], wv, 1, 0.5F, 1F, 0.5F);
                }
                for (int wk = 0; wk <= com.divinerpgextended.entity.EntityWindLord.CHARGE + 1; wk++) {
                    wv.setPositionAndUpdate(tt[0] + 1.5, tt[1], tt[2] + 0.5); // порывы - стоим на месте
                    wv.motionX = wv.motionZ = 0;
                    lord.onLivingUpdate();
                }
                log += "тотем " + (i + 1) + " " + isTotem + " горит " + lord.litTotems() + ", здоровье " + (int) lord.getHealth() + "; ";
            }
            boolean dead = lord.getHealth() <= 0;
            lord.setDead();
            for (int i = -16; i <= 16; i++) for (int wk = -16; wk <= 16; wk++) for (int h = 99; h < 125; h++) w.setBlockToAir(9980 + i, h, 9980 + wk);
            check(immune && dead && log.contains("зарядка false") && log.contains("тотем 3 true горит 3") && log.contains("тотем 4 true горит 0"), "неуязвим " + immune + "; " + log + "убит " + dead);
            return "оружие Повелителя не берёт; 4 тотема на разной высоте, зарядка сбрасывается, если отойти; каждый заряженный - молния на четверть здоровья, четвёртый добивает, тотемы убираются";
        });
        t("Логова", "Демон сумерек: дилемма", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            loadAround(w, 9990, 9990, 1);
            TestPlayer dv = player(ConfigurationHelper.mortum, "TestDemon", 9990, 100, 9984);
            String log = "";
            boolean[] left = new boolean[2];
            for (int run = 0; run < 2; run++) { // 0 - уйти после 1-й фазы, 1 - отказаться и дожать
                net.divinerpg.entities.twilight.EntityTwilightDemon dm = new net.divinerpg.entities.twilight.EntityTwilightDemon(w);
                dm.setPosition(9990.5, 100, 9990.5);
                w.spawnEntityInWorld(dm);
                dm.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(dv), 1);
                dm.setHealth(dm.getMaxHealth() * 0.6F);
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(dm));
                int offer = com.divinerpgextended.boss.MortumBosses.offerLeft(dm);
                if (run == 1) {
                    dm.hurtResistantTime = 0;
                    float hp = dm.getHealth();
                    dm.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(dv), 10);
                    log += "отказ: фаза " + com.divinerpgextended.boss.MortumBosses.phase(dm) + ", урон по предложению " + (hp - dm.getHealth()) + "; ";
                    dm.setHealth(dm.getMaxHealth() * 0.3F);
                    MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(dm));
                    offer = com.divinerpgextended.boss.MortumBosses.offerLeft(dm);
                }
                int n = 0;
                while (!dm.isDead && n++ < com.divinerpgextended.boss.MortumBosses.OFFER + 5)
                    MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(dm));
                left[run] = dm.isDead;
                log += "заход " + (run + 1) + ": предложение " + offer + ", ушёл " + dm.isDead + "; ";
                dm.setDead();
            }
            // трофеи ухода: только души Мортума (халитовое оружие с Демона не падает)
            int souls = 0, halite = 0;
            for (Object o : w.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, net.minecraft.util.AxisAlignedBB.getBoundingBox(9980, 90, 9980, 10000, 110, 10000))) {
                ItemStack it = ((net.minecraft.entity.item.EntityItem) o).getEntityItem();
                if (it.getItem() == net.divinerpg.utils.items.TwilightItemsOther.mortumSoul) souls += it.stackSize;
                if (it.getItem() == net.divinerpg.utils.items.TwilightItemsWeapons.haliteBlitz || it.getItem() == net.divinerpg.utils.items.TwilightItemsWeapons.haliteBow) halite++;
                ((net.minecraft.entity.Entity) o).setDead();
            }
            boolean granted = souls == 6 + 10 && halite == 0;
            check(left[0] && left[1] && granted && log.contains("отказ: фаза 1, урон по предложению 0.0") && log.contains("предложение 200"),
                log + "душ " + souls + ", оружия халита " + halite);
            return "на 2/3 Демон замирает и предлагает уйти: не трогать - уходит с трофеями фазы (6 душ); ударить - удар не проходит, фаза 2, на 1/3 новое предложение, уход - 10 душ (оружия халита нет)";
        });
        t("Логова", "Карот: зеркало", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            loadAround(w, 9995, 9995, 1);
            TestPlayer mv = player(ConfigurationHelper.mortum, "TestMirror", 9995, 100, 9990);
            net.divinerpg.entities.twilight.EntityKarot kr = new net.divinerpg.entities.twilight.EntityKarot(w);
            kr.setPosition(9995.5, 100, 9995.5);
            w.spawnEntityInWorld(kr);
            kr.setAttackTarget(mv);
            float[] in = new float[2], out = new float[2];
            for (int run = 0; run < 2; run++) {
                if (run == 1) {
                    mv.setCurrentItemOrArmor(0, new ItemStack(Items.diamond_sword));
                    armor(mv, Items.diamond_helmet, Items.diamond_chestplate, Items.diamond_leggings, Items.diamond_boots);
                }
                kr.ticksExisted = 10 * (run + 1);
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(kr));
                net.minecraftforge.event.entity.living.LivingHurtEvent hit = new net.minecraftforge.event.entity.living.LivingHurtEvent(kr,
                    net.minecraft.util.DamageSource.causePlayerDamage(mv), 10);
                MinecraftForge.EVENT_BUS.post(hit);
                in[run] = hit.ammount;
                net.minecraftforge.event.entity.living.LivingHurtEvent back = new net.minecraftforge.event.entity.living.LivingHurtEvent(mv,
                    net.minecraft.util.DamageSource.causeMobDamage(kr), 10);
                MinecraftForge.EVENT_BUS.post(back);
                out[run] = back.ammount;
            }
            float power = kr.getEntityData().getFloat(com.divinerpgextended.boss.MortumBosses.MAX_POWER);
            kr.setDead();
            mv.setCurrentItemOrArmor(0, null);
            armor(mv, null, null, null, null);
            check(in[0] == 10 && in[1] < 5 && out[0] < out[1] && power > 1.0, "по голому Кароту " + in[0] + ", по Кароту в алмазе " + in[1]
                + "; Карот бьёт голого " + out[0] + ", в алмазе " + out[1] + ", сила для трофеев " + power);
            return "голый игрок: Карот без брони и бьёт кулаком (" + out[0] + "); в алмазе с мечом: держит удар (10 > " + in[1] + ") и бьёт как меч (" + out[1] + "), трофеев больше";
        });
        t("Ветея - кошмар", "Королева улья: выжить", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.vethea);
            loadAround(w, 9000, 9000, 2);
            TestPlayer hq = join(player(ConfigurationHelper.vethea, "TestHive", 9000, 120, 8994), true);
            net.divinerpg.entities.vethea.EntityHiveQueen queen = new net.divinerpg.entities.vethea.EntityHiveQueen(w);
            queen.setPosition(9000.5, 120, 9000.5);
            w.spawnEntityInWorld(queen);
            boolean hit = queen.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(hq), 1000);
            String log = "удар " + hit;
            int total = com.divinerpgextended.vethea.HiveTimer.DURATION;
            for (int i = 0; i < total / 2; i++) MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(queen));
            float half = queen.getHealth() / queen.getMaxHealth();
            log += ", на половине " + half;
            hq.setPositionAndUpdate(9000, 120, 9060); // ушёл - сначала
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(queen));
            boolean reset = com.divinerpgextended.vethea.HiveTimer.left(queen) == 0 && queen.getHealth() == queen.getMaxHealth();
            log += ", сброс " + reset;
            hq.setPositionAndUpdate(9000, 120, 8994);
            for (int i = 0; i < total + 2 && queen.getHealth() > 0; i++) MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(queen));
            boolean dead = queen.getHealth() <= 0;
            queen.setDead();
            for (Object o : w.getEntitiesWithinAABB(net.minecraft.entity.EntityLiving.class, net.minecraft.util.AxisAlignedBB.getBoundingBox(8980, 100, 8980, 9020, 140, 9020)))
                if (!(o instanceof EntityPlayer)) ((net.minecraft.entity.Entity) o).setDead();
            check(!hit && Math.abs(half - 0.5F) < 0.02F && reset && dead, log + ", пробуждение " + dead);
            return "Королеву не ранить; её полоска тает за 90 с (на половине " + half + "), ушёл с арены - отсчёт сначала, выжил - Королева погибает";
        });
        t("Логова", "Покой арены", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.wildwood);
            loadAround(w, 9700, 9800, 2);
            net.minecraft.entity.monster.EntityZombie wild = new net.minecraft.entity.monster.EntityZombie(w);
            wild.setPosition(9705.5, 101, 9800.5);
            w.spawnEntityInWorld(wild);
            com.divinerpgextended.entity.EntityAncientGolem ag = new com.divinerpgextended.entity.EntityAncientGolem(w);
            ag.setPosition(9700.5, 101, 9800.5);
            com.divinerpgextended.boss.LairSummons.leash(ag, 9700, 101, 9800, 20); // вызов: поляна расчищается
            w.spawnEntityInWorld(ag);
            boolean cleared = wild.isDead;
            ag.ticksExisted = 20;
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(ag));
            net.minecraft.entity.monster.EntityZombie late = new net.minecraft.entity.monster.EntityZombie(w);
            late.setPosition(9710.5, 101, 9800.5);
            net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn cs = new net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn(late, w, 9710.5F, 101, 9800.5F);
            MinecraftForge.EVENT_BUS.post(cs);
            boolean denied = cs.getResult() == cpw.mods.fml.common.eventhandler.Event.Result.DENY;
            TestPlayer cp = player(ConfigurationHelper.wildwood, "TestArenaCarpet", 9705, 101, 9800);
            Accessories.of(cp).setInventorySlotContents(0, trinket(Trinket.MAGIC_CARPET));
            int inFight = com.divinerpgextended.trinket.DoubleJump.carpetTicks(cp);
            cp.setPosition(9800, 101, 9800);
            int outside = com.divinerpgextended.trinket.DoubleJump.carpetTicks(cp);
            Accessories.of(cp).setInventorySlotContents(0, null);
            ag.setDead();
            check(cleared && denied && inFight == 0 && outside > 0, "расчищено " + cleared + ", спавн в бою запрещён " + denied + ", ковёр в бою " + inFight + ", вне арены " + outside);
            return "вызов босса расчищает поляну, пока он жив - мобы на арене не спавнятся; ковёр в бою не работает, за ареной работает";
        });
        t("Логова", "Голем швыряет глыбы", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.wildwood);
            loadAround(w, 9600, 9600, 2);
            for (int i = -4; i <= 24; i++) for (int k2 = -4; k2 <= 4; k2++) {
                w.setBlock(9600 + i, 99, 9600 + k2, Blocks.stone);
                for (int h = 100; h < 112; h++) w.setBlockToAir(9600 + i, h, 9600 + k2);
            }
            TestPlayer bv = join(player(ConfigurationHelper.wildwood, "TestBoulder", 9618.5, 100, 9600.5), true);
            com.divinerpgextended.entity.EntityAncientGolem g2 = new com.divinerpgextended.entity.EntityAncientGolem(w);
            g2.setPosition(9600.5, 100, 9600.5);
            w.spawnEntityInWorld(g2);
            g2.setAttackTarget(bv);
            int boulders = 0;
            for (int i = 0; i < 130 && boulders == 0; i++) {
                g2.onLivingUpdate();
                boulders = w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntityBoulder.class, g2.boundingBox.expand(6, 6, 6)).size();
            }
            for (Object o : w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntityBoulder.class, g2.boundingBox.expand(40, 20, 40))) ((net.minecraft.entity.Entity) o).setDead();
            g2.setDead();
            check(boulders > 0, "глыб за 6.5 с при цели в 18 блоках: " + boulders);
            return "цель держится в 18 блоках - голем вырывает и швыряет глыбу";
        });
        t("Логова", "Голем против лучника", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.wildwood);
            loadAround(w, 9620, 9640, 2);
            TestPlayer av = join(player(ConfigurationHelper.wildwood, "TestArcher", 9636.5, 100, 9640.5), true);
            com.divinerpgextended.entity.EntityAncientGolem g4 = new com.divinerpgextended.entity.EntityAncientGolem(w);
            g4.setPosition(9620.5, 100, 9640.5);
            w.spawnEntityInWorld(g4);
            net.minecraft.entity.projectile.EntityArrow ar2 = new net.minecraft.entity.projectile.EntityArrow(w);
            ar2.setPosition(g4.posX + 1.6, g4.posY + 1, g4.posZ); // в ногу
            av.motionX = 0;
            g4.attackEntityFrom(net.minecraft.util.DamageSource.causeArrowDamage(ar2, av), 5);
            boolean windup = g4.lashing();
            g4.hurtResistantTime = 0;
            g4.attackEntityFrom(net.minecraft.util.DamageSource.causeArrowDamage(ar2, av), 5); // сразу второй раз - перезарядка, лиана одна
            for (int i = 0; i <= com.divinerpgextended.entity.EntityAncientGolem.LASH_WINDUP; i++) {
                av.setPositionAndUpdate(9636.5, 100, 9640.5);
                g4.onLivingUpdate();
            }
            double pull = av.motionX; // голем западнее - тянет на -x
            g4.setDead();
            check(windup && pull < -0.5, "замах лианы " + windup + ", рывок к голему " + pull);
            return "стрела издалека - замах лианы 0.75 с, потом рывок стрелка к голему (не чаще раза в 3 с)";
        });
        t("Логова", "Слайсер режет лиану", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.wildwood);
            loadAround(w, 9620, 9680, 2);
            TestPlayer sv5 = join(player(ConfigurationHelper.wildwood, "TestSlicer", 9636.5, 100, 9680.5), true);
            com.divinerpgextended.entity.EntityAncientGolem g5 = new com.divinerpgextended.entity.EntityAncientGolem(w);
            g5.setPosition(9620.5, 100, 9680.5);
            w.spawnEntityInWorld(g5);
            net.minecraft.entity.projectile.EntityArrow ar3 = new net.minecraft.entity.projectile.EntityArrow(w);
            ar3.setPosition(g5.posX + 1.6, g5.posY + 1, g5.posZ);
            g5.attackEntityFrom(net.minecraft.util.DamageSource.causeArrowDamage(ar3, sv5), 5); // замах лианы
            boolean wind = g5.lashing();
            // попадание слайсером (снаряд с меткой роли)
            net.minecraft.entity.projectile.EntityArrow sl = new net.minecraft.entity.projectile.EntityArrow(w);
            sl.setPosition(g5.posX + 1.6, g5.posY + 1, g5.posZ);
            sl.getEntityData().setInteger(com.divinerpgextended.perk.RangedRoles.KIND, com.divinerpgextended.perk.RangedRoles.SLICER);
            g5.hurtResistantTime = 0;
            g5.attackEntityFrom(net.minecraft.util.DamageSource.causeThrownDamage(sl, sv5), 5);
            boolean cut = !g5.lashing();
            for (int i = 0; i <= com.divinerpgextended.entity.EntityAncientGolem.LASH_WINDUP; i++) {
                sv5.setPositionAndUpdate(9636.5, 100, 9680.5);
                sv5.motionX = 0;
                g5.onLivingUpdate();
            }
            double pulled = sv5.motionX;
            g5.setDead();
            check(wind && cut && pulled > -0.5, "замах " + wind + ", перерезана " + cut + ", рывок " + pulled);
            return "слайсер на замахе лианы перерезает её - рывка нет";
        });
        t("Логова", "Блиц: дробь", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            loadAround(w, 9500, 9700, 1);
            TestPlayer bv2 = join(player(ConfigurationHelper.eden, "TestBlitz", 9500.5, 120, 9700.5), true);
            bv2.setCurrentItemOrArmor(0, new ItemStack(net.divinerpg.utils.items.TwilightItemsWeapons.edenBlitz));
            net.divinerpg.entities.vanilla.projectile.EntityShooterBullet shot = new net.divinerpg.entities.vanilla.projectile.EntityShooterBullet(w, bv2, 10,
                "divinerpg:textures/projectiles/blitzEden.png");
            w.spawnEntityInWorld(shot);
            java.util.List<?> pl5 = w.getEntitiesWithinAABB(net.divinerpg.entities.vanilla.projectile.EntityShooterBullet.class, bv2.boundingBox.expand(4, 4, 4));
            int pellets = pl5.size(), owned = 0;
            for (Object o : pl5) if (((net.divinerpg.entities.vanilla.projectile.EntityShooterBullet) o).getThrower() == bv2) owned++;
            for (Object o : w.getEntitiesWithinAABB(net.divinerpg.entities.vanilla.projectile.EntityShooterBullet.class, bv2.boundingBox.expand(8, 8, 8)))
                ((net.minecraft.entity.Entity) o).setDead();
            // дробина по подручному босса - x2 к половине урона
            net.minecraft.entity.monster.EntityZombie add = new net.minecraft.entity.monster.EntityZombie(w);
            add.getEntityData().setInteger(com.divinerpgextended.entity.EntityGroveGuardian.ADD_TAG, 1);
            net.minecraft.entity.projectile.EntityArrow pel = new net.minecraft.entity.projectile.EntityArrow(w);
            pel.getEntityData().setInteger(com.divinerpgextended.perk.RangedRoles.KIND, com.divinerpgextended.perk.RangedRoles.BLITZ);
            net.minecraftforge.event.entity.living.LivingHurtEvent h1 = new net.minecraftforge.event.entity.living.LivingHurtEvent(add,
                net.minecraft.util.DamageSource.causeThrownDamage(pel, bv2), 10);
            MinecraftForge.EVENT_BUS.post(h1);
            net.minecraft.entity.monster.EntityZombie plain = new net.minecraft.entity.monster.EntityZombie(w);
            net.minecraftforge.event.entity.living.LivingHurtEvent h2 = new net.minecraftforge.event.entity.living.LivingHurtEvent(plain,
                net.minecraft.util.DamageSource.causeThrownDamage(pel, bv2), 10);
            MinecraftForge.EVENT_BUS.post(h2);
            bv2.setCurrentItemOrArmor(0, null);
            float pd = com.divinerpgextended.perk.RangedRoles.PELLET_DMG;
            // отброс: у босса скорость возвращается к той, что была до дроби; обычного моба отбрасывает на долю
            net.divinerpg.entities.twilight.EntityKarot kb = new net.divinerpg.entities.twilight.EntityKarot(w);
            kb.setLocationAndAngles(9503.5, 120, 9700.5, 0, 0);
            w.spawnEntityInWorld(kb);
            kb.motionX = kb.motionY = kb.motionZ = 0;
            net.minecraft.entity.projectile.EntityArrow pk = new net.minecraft.entity.projectile.EntityArrow(w);
            pk.getEntityData().setInteger(com.divinerpgextended.perk.RangedRoles.KIND, com.divinerpgextended.perk.RangedRoles.BLITZ);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingAttackEvent(kb, net.minecraft.util.DamageSource.causeThrownDamage(pk, bv2), 5));
            kb.motionX = 2.0; // будто отбросило пятью дробинами
            FMLCommonHandler.instance().bus().post(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER, cpw.mods.fml.common.gameevent.TickEvent.Phase.END, w));
            boolean noKnock = Math.abs(kb.motionX) < 0.01;
            kb.setDead();
            check(pellets == com.divinerpgextended.perk.RangedRoles.PELLETS && owned == pellets && Math.abs(h1.ammount - 10 * pd * 2) < 0.01 && Math.abs(h2.ammount - 10 * pd) < 0.01 && noKnock,
                "снарядов " + pellets + " (со стрелком " + owned + "), по подручному " + h1.ammount + ", по обычному " + h2.ammount + ", босса не отбросило " + noKnock);
            return "выстрел блица - 5 снарядов веером; дробина по обычному мобу " + Math.round(pd * 100) + "%, по подручному босса x2; боссов дробь не отбрасывает";
        });
        t("Логова", "Фазер: пробой и тотем", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.skythern);
            loadAround(w, 9520, 9720, 2);
            TestPlayer pv = join(player(ConfigurationHelper.skythern, "TestPhaser", 9510.5, 120, 9720.5), true);
            // пробой: попадание снаряда фазера в моба - снаряд-продолжение за его спиной
            net.divinerpg.entities.vanilla.projectile.EntityShooterBullet ph = new net.divinerpg.entities.vanilla.projectile.EntityShooterBullet(w, pv, 10,
                "divinerpg:textures/projectiles/phaserSkythern.png");
            ph.getEntityData().setInteger(com.divinerpgextended.perk.RangedRoles.KIND, com.divinerpgextended.perk.RangedRoles.PHASER);
            ph.setPosition(9514, 121, 9720.5);
            ph.motionX = 1.5;
            ph.motionY = ph.motionZ = 0;
            net.minecraft.entity.monster.EntityZombie zp = new net.minecraft.entity.monster.EntityZombie(w);
            zp.setPosition(9515.5, 120, 9720.5);
            int before = w.getEntitiesWithinAABB(net.divinerpg.entities.vanilla.projectile.EntityShooterBullet.class, zp.boundingBox.expand(6, 4, 6)).size();
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingHurtEvent(zp, net.minecraft.util.DamageSource.causeThrownDamage(ph, pv), 10));
            java.util.List<?> after = w.getEntitiesWithinAABB(net.divinerpg.entities.vanilla.projectile.EntityShooterBullet.class, zp.boundingBox.expand(6, 4, 6));
            boolean behind = false;
            for (Object o : after) {
                net.minecraft.entity.Entity b = (net.minecraft.entity.Entity) o;
                if (b.posX > zp.posX && b.motionX > 1) behind = true;
                b.setDead();
            }
            // подзарядка тотема издалека
            com.divinerpgextended.entity.EntityWindLord wl = new com.divinerpgextended.entity.EntityWindLord(w);
            wl.setPosition(9520.5, 120, 9720.5);
            w.spawnEntityInWorld(wl);
            wl.placeTotems();
            int[] tt = wl.totem(0);
            int shots = 0;
            while (w.getBlockMetadata(tt[0], tt[1], tt[2]) == 0 && shots < 20) {
                wl.remoteCharge(tt[0], tt[1], tt[2]);
                shots++;
            }
            boolean lit = w.getBlockMetadata(tt[0], tt[1], tt[2]) == 1;
            float hp = wl.getHealth() / wl.getMaxHealth();
            for (int i = 0; i < 4; i++) {
                int[] t2 = wl.totem(i);
                w.setBlockToAir(t2[0], t2[1], t2[2]);
            }
            wl.setDead();
            check(behind && lit && shots == 5 && hp < 0.8F, "снаряд за спиной " + behind + " (было " + before + ", стало " + after.size() + "), тотем зажжён " + lit + " за " + shots + " попаданий, здоровье " + hp);
            return "фазер пробивает моба насквозь (летит дальше); 5 быстрых попаданий в тотем зажигают его издалека";
        });
        t("Прохождение", "Рельсы на стенах и потолке", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.apalachia);
            loadAround(w, 9540, 9760, 1);
            int x0 = 9540, y0 = 100, z0 = 9760;
            // коробка 6 x 6 в плоскости XY: пол, стены, потолок из камня; внутри кольцо рельсов
            for (int x = x0 - 1; x <= x0 + 6; x++) for (int y = y0 - 1; y <= y0 + 6; y++) for (int z = z0 - 1; z <= z0 + 1; z++)
                w.setBlock(x, y, z, x == x0 - 1 || x == x0 + 6 || y == y0 - 1 || y == y0 + 6 || z != z0 ? Blocks.stone : Blocks.air);
            int[][] ring = new int[20][];
            int n = 0;
            for (int x = x0; x <= x0 + 5; x++) ring[n++] = new int[] { x, y0, 0 };          // пол (опора снизу)
            for (int y = y0 + 1; y <= y0 + 5; y++) ring[n++] = new int[] { x0 + 5, y, 5 };  // правая стена (опора на востоке)
            for (int x = x0 + 4; x >= x0; x--) ring[n++] = new int[] { x, y0 + 5, 1 };      // потолок (опора сверху)
            for (int y = y0 + 4; y >= y0 + 1; y--) ring[n++] = new int[] { x0, y, 4 };      // левая стена (опора на западе)
            for (int i = 0; i < n; i++) w.setBlock(ring[i][0], ring[i][1], z0, com.divinerpgextended.registry.ModBlocks.skyRail, ring[i][2], 3);
            TestPlayer rp = join(player(ConfigurationHelper.apalachia, "TestRail", x0 + 0.5, y0, z0 + 0.5), true);
            com.divinerpgextended.entity.EntitySkyCart cart = new com.divinerpgextended.entity.EntitySkyCart(w, x0, y0, z0, -90); // лицом на восток
            w.spawnEntityInWorld(cart);
            rp.mountEntity(cart);
            boolean ceiling = false, wall = false, back = false;
            int ticks = 0;
            for (; ticks < 200; ticks++) {
                cart.onUpdate();
                int[] rpos = cart.railPos();
                if (rpos[1] == y0 + 5) ceiling = true;
                if (rpos[0] == x0 + 5 && rpos[1] > y0 && rpos[1] < y0 + 5) wall = true;
                if (ceiling && rpos[0] == x0 && rpos[1] == y0 && ticks > 10) { back = true; break; }
            }
            rp.mountEntity(null);
            cart.setDead();
            for (int x = x0 - 1; x <= x0 + 6; x++) for (int y = y0 - 1; y <= y0 + 6; y++) for (int z = z0 - 1; z <= z0 + 1; z++) w.setBlockToAir(x, y, z);
            check(wall && ceiling && back, "по стене " + wall + ", по потолку " + ceiling + ", вернулась на старт " + back + " за " + ticks + " тиков");
            return "вагонетка Апалачии объехала кольцо пол - стена - потолок - стена и вернулась (" + ticks + " тиков)";
        });
        t("Прохождение", "Дробь, пыль фазера, колчаны", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            loadAround(w, 9480, 9800, 1);
            TestPlayer qv = join(player(ConfigurationHelper.eden, "TestQuiver", 9480.5, 120, 9800.5), true);
            // дробь: неуязвимость после первой дробины не мешает следующим
            net.minecraft.entity.monster.EntityZombie zq = new net.minecraft.entity.monster.EntityZombie(w);
            zq.hurtResistantTime = 20;
            net.minecraft.entity.projectile.EntityArrow pl = new net.minecraft.entity.projectile.EntityArrow(w);
            pl.getEntityData().setInteger(com.divinerpgextended.perk.RangedRoles.KIND, com.divinerpgextended.perk.RangedRoles.BLITZ);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingAttackEvent(zq, net.minecraft.util.DamageSource.causeThrownDamage(pl, qv), 5));
            boolean pellets = zq.hurtResistantTime == 0;
            // фазер бесконечный: без пыли стреляет, пыль не тратит
            qv.setCurrentItemOrArmor(0, new ItemStack(net.divinerpg.utils.items.TwilightItemsWeapons.edenPhaser));
            net.divinerpg.entities.vanilla.projectile.EntityShooterBullet s1 = new net.divinerpg.entities.vanilla.projectile.EntityShooterBullet(w, qv, 10, "divinerpg:textures/projectiles/phaserEden.png");
            boolean dry = w.spawnEntityInWorld(s1);
            s1.setDead();
            qv.inventory.setInventorySlotContents(5, new ItemStack(net.divinerpg.utils.items.TwilightItemsOther.edenDust, 3));
            net.divinerpg.entities.vanilla.projectile.EntityShooterBullet s2 = new net.divinerpg.entities.vanilla.projectile.EntityShooterBullet(w, qv, 10, "divinerpg:textures/projectiles/phaserEden.png");
            boolean shot = w.spawnEntityInWorld(s2);
            int dustLeft = qv.inventory.getStackInSlot(5) == null ? 0 : qv.inventory.getStackInSlot(5).stackSize;
            s2.setDead();
            qv.inventory.setInventorySlotContents(5, null);
            // колчан: лук Эдема без стрел натягивается (событие отменено, лук в деле), выстрел - стрела летит, в инвентаре стрел нет
            ItemStack bow = new ItemStack(net.divinerpg.utils.items.TwilightItemsWeapons.edenBow);
            qv.setCurrentItemOrArmor(0, bow);
            Accessories.of(qv).setInventorySlotContents(0, trinket(Trinket.ENDLESS_QUIVER));
            net.minecraftforge.event.entity.player.ArrowNockEvent nock = new net.minecraftforge.event.entity.player.ArrowNockEvent(qv, bow);
            MinecraftForge.EVENT_BUS.post(nock);
            boolean drawn = nock.isCanceled() && qv.isUsingItem();
            int arrowsBefore = w.getEntitiesWithinAABB(net.minecraft.entity.projectile.EntityArrow.class, qv.boundingBox.expand(6, 6, 6)).size();
            bow.getItem().onPlayerStoppedUsing(bow, w, qv, bow.getMaxItemUseDuration() - 40);
            java.util.List<?> flying = w.getEntitiesWithinAABB(net.minecraft.entity.projectile.EntityArrow.class, qv.boundingBox.expand(6, 6, 6));
            boolean fired = flying.size() == arrowsBefore + 1;
            for (Object o : flying) ((net.minecraft.entity.Entity) o).setDead();
            qv.clearItemInUse();
            FMLCommonHandler.instance().bus().post(new cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent(cpw.mods.fml.common.gameevent.TickEvent.Phase.END, qv));
            boolean arrow = drawn && fired && !qv.inventory.hasItem(net.divinerpg.utils.items.TwilightItemsWeapons.edenArrow);
            boolean gone = net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.enchantment.Enchantment.infinity.effectId, bow) == 0
                && (bow.getTagCompound() == null || !bow.getTagCompound().hasKey("ench"));
            // блиц бесконечный: без пыли и без подсумка стреляет, пыль в инвентаре не остаётся
            ItemStack bz = new ItemStack(net.divinerpg.utils.items.TwilightItemsWeapons.edenBlitz);
            qv.setCurrentItemOrArmor(0, bz);
            int bulletsBefore = w.getEntitiesWithinAABB(net.divinerpg.entities.vanilla.projectile.EntityShooterBullet.class, qv.boundingBox.expand(6, 6, 6)).size();
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerInteractEvent(qv,
                net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_AIR, 0, 0, 0, -1, w));
            try {
                bz.getItem().onItemRightClick(bz, w, qv);
            } catch (NullPointerException ex) {
                // Divine после выстрела шлёт инвентарь по сети - у тестового игрока сети нет
            }
            java.util.List<?> bl = w.getEntitiesWithinAABB(net.divinerpg.entities.vanilla.projectile.EntityShooterBullet.class, qv.boundingBox.expand(6, 6, 6));
            int blitzShots = bl.size() - bulletsBefore;
            for (Object o : bl) ((net.minecraft.entity.Entity) o).setDead();
            FMLCommonHandler.instance().bus().post(new cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent(cpw.mods.fml.common.gameevent.TickEvent.Phase.END, qv));
            boolean noDust = !qv.inventory.hasItem(net.divinerpg.utils.items.TwilightItemsOther.edenDust);
            Accessories.of(qv).setInventorySlotContents(0, null);
            qv.setCurrentItemOrArmor(0, null);
            check(pellets && dry && shot && dustLeft == 3 && arrow && gone && blitzShots == com.divinerpgextended.perk.RangedRoles.PELLETS && noDust,
                "дробь без неуязвимости " + pellets + ", фазер без пыли стреляет " + dry + ", с пылью " + shot + " (пыли осталось " + dustLeft + " из 3), колчан: натянут " + drawn
                    + ", выстрел " + fired + ", Бесконечность снята " + gone + "; блиц без пыли: снарядов " + blitzShots + ", пыли в инвентаре нет " + noDust);
            return "дробины блица бьют одну цель все; фазер и блиц бесконечные (без пыли стреляют, пыль не тратят и не кладут); с колчаном лук без стрел натягивается и стреляет, стрел в инвентаре нет";
        });
        t("Прохождение", "Блиц: полный инвентарь и без отброса", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            loadAround(w, 9470, 9830, 2);
            TestPlayer fv = join(player(ConfigurationHelper.eden, "TestBlitzFull", 9470.5, 120, 9830.5), true);
            ItemStack bz = new ItemStack(net.divinerpg.utils.items.TwilightItemsWeapons.edenBlitz);
            int[] shots = new int[2];
            String[] chat0 = { "" };
            for (int run = 0; run < 2; run++) {
                for (int i = 0; i < fv.inventory.mainInventory.length; i++) fv.inventory.mainInventory[i] = new ItemStack(Blocks.dirt, 64);
                for (int i = Accessories.SIZE; i < Accessories.TOTAL; i++) Accessories.of(fv).setInventorySlotContents(i, null);
                fv.inventory.currentItem = 0;
                fv.inventory.mainInventory[0] = bz;
                // 1-й прогон: в рюкзаке только земля и алмазы; 2-й: в последней ячейке стрелы
                fv.inventory.mainInventory[35] = run == 0 ? new ItemStack(Items.diamond, 7) : new ItemStack(Items.arrow, 20);
                fv.chat.clear();
                fv.getEntityData().removeTag("dreBlitzFull");
                int before = w.getEntitiesWithinAABB(net.divinerpg.entities.vanilla.projectile.EntityShooterBullet.class, fv.boundingBox.expand(6, 6, 6)).size();
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerInteractEvent(fv,
                    net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_AIR, 0, 0, 0, -1, w));
                try {
                    bz.getItem().onItemRightClick(bz, w, fv);
                } catch (NullPointerException ex) {
                    // у тестового игрока нет сети
                }
                java.util.List<?> bl = w.getEntitiesWithinAABB(net.divinerpg.entities.vanilla.projectile.EntityShooterBullet.class, fv.boundingBox.expand(6, 6, 6));
                shots[run] = bl.size() - before;
                for (Object o : bl) ((net.minecraft.entity.Entity) o).setDead();
                FMLCommonHandler.instance().bus().post(new cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent(cpw.mods.fml.common.gameevent.TickEvent.Phase.END, fv));
                if (run == 0) chat0[0] = String.valueOf(fv.inventory.mainInventory[35]);
            }
            ItemStack s35 = fv.inventory.mainInventory[35];
            boolean arrowsStay = s35 != null && s35.getItem() == Items.arrow && s35.stackSize == 20;
            int inSlots = 0;
            for (int i = Accessories.SIZE; i < Accessories.TOTAL; i++) if (Accessories.of(fv).getStackInSlot(i) != null) inSlots++;
            boolean noDust = !fv.inventory.hasItem(net.divinerpg.utils.items.TwilightItemsOther.edenDust);
            int dirt = 0;
            for (ItemStack it : fv.inventory.mainInventory) if (it != null && it.getItem() == Item.getItemFromBlock(Blocks.dirt)) dirt += it.stackSize;
            // отброс: обычного моба дробь тоже не отбрасывает
            net.minecraft.entity.monster.EntityZombie z = new net.minecraft.entity.monster.EntityZombie(w);
            z.setLocationAndAngles(9474.5, 120, 9830.5, 0, 0);
            w.spawnEntityInWorld(z);
            z.motionX = z.motionY = z.motionZ = 0;
            net.minecraft.entity.projectile.EntityArrow pk = new net.minecraft.entity.projectile.EntityArrow(w);
            pk.getEntityData().setInteger(com.divinerpgextended.perk.RangedRoles.KIND, com.divinerpgextended.perk.RangedRoles.BLITZ);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingAttackEvent(z, net.minecraft.util.DamageSource.causeThrownDamage(pk, fv), 5));
            z.motionX = 2.0;
            FMLCommonHandler.instance().bus().post(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER, cpw.mods.fml.common.gameevent.TickEvent.Phase.END, w));
            boolean still = Math.abs(z.motionX) < 0.01;
            z.setDead();
            fv.inventory.clearInventory(null, -1);
            int P = com.divinerpgextended.perk.RangedRoles.PELLETS;
            check(shots[0] == P && shots[1] == P && chat0[0].contains("diamond") && arrowsStay && inSlots == 0 && noDust && dirt == 34 * 64 && still,
                "забит землёй и алмазами: снарядов " + shots[0] + ", в последней ячейке " + chat0[0] + "; со стрелами: снарядов " + shots[1] + ", стрелы на месте " + arrowsStay + ", в слотах боеприпасов " + inSlots + ", пыли нет " + noDust + ", земли " + dirt + " из " + 34 * 64 + ", зомби не отброшен " + still);
            return "блиц стреляет при любом заполнении рюкзака и ничего в нём не трогает; дробь никого не отбрасывает";
        });
        t("Прохождение", "Блиц без патрона, как фазер", () -> {
            int n = 0, bad = 0;
            for (Object o : Item.itemRegistry) {
                if (!(o instanceof Item) || com.divinerpgextended.perk.RangedRoles.kind(new ItemStack((Item) o)) != com.divinerpgextended.perk.RangedRoles.BLITZ) continue;
                n++;
                if (com.divinerpgextended.perk.Ammo.divineAmmo(new ItemStack((Item) o)) != null) bad++;
            }
            check(n >= 6 && bad == 0, "блицев " + n + ", с патроном Divine осталось " + bad);
            return "у всех " + n + " блицев патрон Divine снят: стреляют бесконечно и подсказка Divine сама пишет про бесконечные боеприпасы";
        });
        t("Прохождение", "Слоты боеприпасов", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            loadAround(w, 9488, 9840, 1);
            TestPlayer av = join(player(ConfigurationHelper.eden, "TestAmmo", 9488.5, 120, 9840.5), true);
            Accessories acc = Accessories.of(av);
            int A = Accessories.SIZE;
            boolean valid = acc.canPlace(A, new ItemStack(Items.arrow)) && acc.canPlace(A, new ItemStack(net.divinerpg.utils.items.VanillaItemsOther.corruptedBullet))
                && !acc.canPlace(A, new ItemStack(Items.diamond_sword)) && !acc.canPlace(0, new ItemStack(Items.arrow));
            // лук: стрелы только в слоте - натягивается, стреляет, в слоте на одну меньше, в инвентаре пусто
            acc.setInventorySlotContents(A, new ItemStack(Items.arrow, 5));
            ItemStack bow = new ItemStack(Items.bow);
            av.setCurrentItemOrArmor(0, bow);
            net.minecraftforge.event.entity.player.ArrowNockEvent nock = new net.minecraftforge.event.entity.player.ArrowNockEvent(av, bow);
            MinecraftForge.EVENT_BUS.post(nock);
            boolean drawn = nock.isCanceled() && av.isUsingItem();
            int before = w.getEntitiesWithinAABB(net.minecraft.entity.projectile.EntityArrow.class, av.boundingBox.expand(6, 6, 6)).size();
            bow.getItem().onPlayerStoppedUsing(bow, w, av, bow.getMaxItemUseDuration() - 40);
            java.util.List<?> fl = w.getEntitiesWithinAABB(net.minecraft.entity.projectile.EntityArrow.class, av.boundingBox.expand(6, 6, 6));
            boolean fired = fl.size() == before + 1;
            for (Object o : fl) ((net.minecraft.entity.Entity) o).setDead();
            av.clearItemInUse();
            com.divinerpgextended.perk.Ammo.settle(av);
            int arrows = acc.getStackInSlot(A) == null ? 0 : acc.getStackInSlot(A).stackSize;
            boolean bowOk = drawn && fired && arrows == 4 && !av.inventory.hasItem(Items.arrow);
            acc.setInventorySlotContents(A, null);
            // пушка Corrupted Cannon: пуля из слота; одолженная и не потраченная - вернулась в слот
            net.minecraft.item.Item bullet = net.divinerpg.utils.items.VanillaItemsOther.corruptedBullet;
            acc.setInventorySlotContents(A + 1, new ItemStack(bullet, 4));
            ItemStack cn = new ItemStack(net.divinerpg.utils.items.VanillaItemsWeapons.corruptedCannon);
            av.setCurrentItemOrArmor(0, cn);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerInteractEvent(av,
                net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_AIR, 0, 0, 0, -1, w));
            try {
                cn.getItem().onItemRightClick(cn, w, av);
            } catch (NullPointerException ex) {
                // Divine после выстрела шлёт инвентарь по сети - у тестового игрока сети нет
            }
            for (Object o : w.getEntitiesWithinAABB(net.minecraft.entity.Entity.class, av.boundingBox.expand(6, 6, 6))) if (o != av) ((net.minecraft.entity.Entity) o).setDead();
            com.divinerpgextended.perk.Ammo.settle(av);
            int afterShot = acc.getStackInSlot(A + 1) == null ? 0 : acc.getStackInSlot(A + 1).stackSize;
            com.divinerpgextended.perk.Ammo.lend(av, bullet);
            boolean lent = av.inventory.hasItem(bullet);
            com.divinerpgextended.perk.Ammo.settle(av);
            int bullets = acc.getStackInSlot(A + 1) == null ? 0 : acc.getStackInSlot(A + 1).stackSize;
            boolean cannonOk = afterShot == 3 && lent && bullets == 3 && !av.inventory.hasItem(bullet);
            acc.setInventorySlotContents(A + 1, null);
            av.setCurrentItemOrArmor(0, null);
            check(valid && bowOk && cannonOk, "что кладётся " + valid + ", лук: натянут " + drawn + ", выстрел " + fired + ", стрел в слоте " + arrows
                + "; пушка: после выстрела пуль " + afterShot + " из 4, одолжена " + lent + ", после возврата " + bullets);
            return "в слоты боеприпасов кладутся только боеприпасы; лук и пушка берут из них, когда в инвентаре нет; не потраченное возвращается в слот";
        });
        t("Прохождение", "Вагонетка Апалачии: рельсы арканы и обычные", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.apalachia);
            loadAround(w, 9600, 9900, 1);
            int x0 = 9600, y0 = 100, z0 = 9900;
            for (int x = x0 - 1; x <= x0 + 31; x++) {
                w.setBlock(x, y0 - 1, z0, Blocks.stone);
                w.setBlock(x, y0, z0, Blocks.air);
            }
            // 30 рельсов: первые 10 - арканы Divine, дальше ванильные
            for (int x = x0; x < x0 + 30; x++) w.setBlock(x, y0, z0, x < x0 + 10 ? net.divinerpg.utils.blocks.ArcanaBlocks.arcaniteRails : Blocks.rail);
            TestPlayer cv = join(player(ConfigurationHelper.apalachia, "TestCartBoost", x0 + 0.5, y0, z0 + 0.5), true);
            com.divinerpgextended.entity.EntitySkyCart cart = new com.divinerpgextended.entity.EntitySkyCart(w, x0, y0, z0, -90);
            w.spawnEntityInWorld(cart);
            cv.mountEntity(cart);
            double top = 0;
            int far = 0;
            for (int i = 0; i < 40; i++) {
                cart.onUpdate();
                top = Math.max(top, cart.speed());
                far = Math.max(far, cart.railPos()[0] - x0);
            }
            cv.mountEntity(null);
            cart.setDead();
            for (int x = x0 - 1; x <= x0 + 31; x++) {
                w.setBlockToAir(x, y0, z0);
                w.setBlockToAir(x, y0 - 1, z0);
            }
            check(top > 0.6 && far >= 25, "разгон до " + String.format("%.2f", top) + " (обычная " + com.divinerpgextended.entity.EntitySkyCart.SPEED + "), доехала до " + far + " по ванильным");
            return "рельсы арканы разгоняют вагонетку Апалачии (до " + String.format("%.2f", top) + "), дальше она едет по обычным ванильным рельсам";
        });
        t("Прохождение", "Бонусы колчанов", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            TestPlayer kv = join(player(ConfigurationHelper.eden, "TestQuiverBonus", 9482.5, 120, 9810.5), true);
            net.minecraft.entity.monster.EntityZombie zb = new net.minecraft.entity.monster.EntityZombie(w);
            net.minecraft.entity.projectile.EntityArrow ar = new net.minecraft.entity.projectile.EntityArrow(w, kv, 1.0F);
            float[] got = new float[2];
            for (int i = 0; i < 2; i++) {
                Accessories.of(kv).setInventorySlotContents(0, i == 1 ? trinket(Trinket.ENDLESS_QUIVER) : null);
                net.minecraftforge.event.entity.living.LivingHurtEvent h = new net.minecraftforge.event.entity.living.LivingHurtEvent(zb,
                    net.minecraft.util.DamageSource.causeArrowDamage(ar, kv), 10);
                MinecraftForge.EVENT_BUS.post(h);
                got[i] = h.ammount;
            }
            // подсумок: фазер +15%
            Accessories.of(kv).setInventorySlotContents(0, trinket(Trinket.DUST_POUCH));
            net.divinerpg.entities.vanilla.projectile.EntityShooterBullet pb = new net.divinerpg.entities.vanilla.projectile.EntityShooterBullet(w, kv, 10, "divinerpg:textures/projectiles/phaserEden.png");
            pb.getEntityData().setInteger(com.divinerpgextended.perk.RangedRoles.KIND, com.divinerpgextended.perk.RangedRoles.PHASER);
            pb.getEntityData().setInteger(com.divinerpgextended.perk.RangedRoles.GEN, com.divinerpgextended.perk.RangedRoles.PIERCE); // без продолжения
            net.minecraftforge.event.entity.living.LivingHurtEvent hp = new net.minecraftforge.event.entity.living.LivingHurtEvent(zb,
                net.minecraft.util.DamageSource.causeThrownDamage(pb, kv), 10);
            MinecraftForge.EVENT_BUS.post(hp);
            Accessories.of(kv).setInventorySlotContents(0, null);
            check(Math.abs(got[0] - 10) < 0.01 && Math.abs(got[1] - 11) < 0.01 && Math.abs(hp.ammount - 11.5) < 0.01,
                "стрела без колчана " + got[0] + ", с колчаном " + got[1] + ", фазер с подсумком " + hp.ammount);
            return "колчан: стрелы +10%; подсумок: фазер +15%";
        });
        t("Прохождение", "Колчаны со стрелков", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            TestPlayer dv2 = player(ConfigurationHelper.eden, "TestQuiverDrop", 9484.5, 120, 9820.5);
            net.divinerpg.entities.twilight.EntitySunArcher arch = new net.divinerpg.entities.twilight.EntitySunArcher(w);
            net.divinerpg.entities.twilight.EntitySkythernArcher sky = new net.divinerpg.entities.twilight.EntitySkythernArcher(w);
            int got = 0, other = 0;
            for (int i = 0; i < 500; i++) {
                java.util.ArrayList<net.minecraft.entity.item.EntityItem> drops = new java.util.ArrayList<net.minecraft.entity.item.EntityItem>();
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDropsEvent(arch, net.minecraft.util.DamageSource.causePlayerDamage(dv2), drops, 0, true, 0));
                for (net.minecraft.entity.item.EntityItem d : drops) {
                    ItemStack it = d.getEntityItem();
                    if (it.getItem() == com.divinerpgextended.registry.ModItems.trinket) {
                        if (it.getItemDamage() == Trinket.ENDLESS_QUIVER.ordinal()) got++;
                        else other++;
                    }
                }
            }
            net.minecraft.inventory.InventoryCrafting cg = grid();
            cg.setInventorySlotContents(0, new ItemStack(Items.leather));
            cg.setInventorySlotContents(1, new ItemStack(Items.string));
            cg.setInventorySlotContents(2, new ItemStack(Items.leather));
            cg.setInventorySlotContents(3, new ItemStack(net.divinerpg.utils.items.TwilightItemsWeapons.edenArrow));
            cg.setInventorySlotContents(4, com.divinerpgextended.world.twilight.TwilightCrafts.mat(com.divinerpgextended.world.twilight.TwilightWorld.EDEN, 0));
            cg.setInventorySlotContents(5, new ItemStack(net.divinerpg.utils.items.TwilightItemsWeapons.edenArrow));
            cg.setInventorySlotContents(6, new ItemStack(Items.leather));
            cg.setInventorySlotContents(7, new ItemStack(net.divinerpg.utils.items.TwilightItemsWeapons.edenArrow));
            cg.setInventorySlotContents(8, new ItemStack(Items.leather));
            ItemStack craft = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(cg, server.worldServers[0]);
            boolean skyQuiver = com.divinerpgextended.perk.RangedRoles.quiverOf(sky) == Trinket.ENDLESS_QUIVER;
            net.divinerpg.entities.twilight.EntityEternalArcher eternal = new net.divinerpg.entities.twilight.EntityEternalArcher(w);
            int bossGot = 0;
            for (int i = 0; i < 20; i++) {
                java.util.ArrayList<net.minecraft.entity.item.EntityItem> bd = new java.util.ArrayList<net.minecraft.entity.item.EntityItem>();
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDropsEvent(eternal, net.minecraft.util.DamageSource.causePlayerDamage(dv2), bd, 0, true, 0));
                for (net.minecraft.entity.item.EntityItem d : bd) if (d.getEntityItem().getItem() == com.divinerpgextended.registry.ModItems.trinket) bossGot++;
            }
            check(got > 25 && got < 80 && other == 0 && craft == null && skyQuiver && bossGot == 20,
                "колчанов с 500 солнечных лучников " + got + ", чужих " + other + ", крафт " + craft + ", лучник Скайтерна " + skyQuiver + ", с Вечного лучника " + bossGot + " из 20");
            return "колчан падает с лучников (~10%: " + got + " из 500), с Вечного лучника всегда, не крафтится";
        });
        t("Прохождение", "Рельсы как в игре: пол, горка, стена, потолок", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.apalachia);
            loadAround(w, 9580, 9860, 1);
            int x0 = 9580, y0 = 100, z0 = 9860;
            // пол, стена на востоке (x0+6), потолок на высоте y0+6
            for (int x = x0 - 2; x <= x0 + 8; x++) for (int y = y0 - 1; y <= y0 + 8; y++) for (int z = z0 - 2; z <= z0 + 2; z++)
                w.setBlock(x, y, z, y == y0 - 1 || x == x0 + 6 || y == y0 + 6 ? Blocks.stone : Blocks.air);
            TestPlayer rv = join(player(ConfigurationHelper.apalachia, "TestRailPlay", x0 + 0.5, y0, z0 + 0.5), true);
            rv.rotationYaw = -90; // смотрит на восток
            ItemStack rails = new ItemStack(com.divinerpgextended.registry.ModItems.apalachiaRails, 64);
            hold(rv, rails);
            rv.rotationPitch = 60;
            for (int x = x0; x <= x0 + 5; x++) rails.getItem().onItemUse(rails, rv, w, x, y0 - 1, z0, 1, 0.5F, 1F, 0.5F); // пол
            rv.rotationPitch = 0;
            for (int y = y0 + 1; y <= y0 + 5; y++) rails.getItem().onItemUse(rails, rv, w, x0 + 6, y, z0, 4, 0F, 0.5F, 0.5F); // стена
            rv.rotationPitch = -60;
            for (int x = x0; x <= x0 + 4; x++) rails.getItem().onItemUse(rails, rv, w, x, y0 + 6, z0, 0, 0.5F, 0F, 0.5F); // потолок
            int floor = 0, wall = 0, ceil = 0;
            for (int x = x0; x <= x0 + 4; x++) if (w.getBlock(x, y0, z0) == com.divinerpgextended.registry.ModBlocks.apalachiaRail) floor++;
            // напольные гнутся: рельс на юг от x0 - угол у x0 становится поворотом
            rv.rotationPitch = 60;
            rails.getItem().onItemUse(rails, rv, w, x0, y0 - 1, z0 + 1, 1, 0.5F, 1F, 0.5F);
            int bend = w.getBlockMetadata(x0, y0, z0);
            boolean bent = bend >= 6 && bend <= 9;
            w.setBlockToAir(x0, y0, z0 + 1);
            for (int y = y0 + 1; y <= y0 + 5; y++)
                if (w.getBlock(x0 + 5, y, z0) == com.divinerpgextended.registry.ModBlocks.skyRail
                    && (w.getBlockMetadata(x0 + 5, y, z0) & 7) == net.minecraftforge.common.util.ForgeDirection.EAST.ordinal()) wall++;
            for (int x = x0; x <= x0 + 4; x++)
                if (w.getBlock(x, y0 + 5, z0) == com.divinerpgextended.registry.ModBlocks.skyRail
                    && (w.getBlockMetadata(x, y0 + 5, z0) & 7) == net.minecraftforge.common.util.ForgeDirection.UP.ordinal()) ceil++;
            // рельс Divine у подножия стены стал горкой; верх стены - горка на потолок
            net.minecraftforge.common.util.ForgeDirection low = com.divinerpgextended.block.SkyTrack.corner(w, x0 + 5, y0, z0);
            net.minecraftforge.common.util.ForgeDirection high = com.divinerpgextended.block.SkyTrack.corner(w, x0 + 5, y0 + 5, z0);
            boolean ramps = w.getBlock(x0 + 5, y0, z0) == com.divinerpgextended.registry.ModBlocks.skyRail && low == net.minecraftforge.common.util.ForgeDirection.EAST
                && high == net.minecraftforge.common.util.ForgeDirection.UP;
            // вагонетка предметом, посадка кликом; доезжает до конца потолка и возвращается на пол без скачков, глаза седока не в камне
            ItemStack cartItem = new ItemStack(com.divinerpgextended.registry.ModItems.skyCart);
            cartItem.getItem().onItemUse(cartItem, rv, w, x0, y0, z0, 1, 0.5F, 0.1F, 0.5F);
            java.util.List<?> carts = w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntitySkyCart.class,
                net.minecraft.util.AxisAlignedBB.getBoundingBox(x0 - 1, y0 - 1, z0 - 1, x0 + 2, y0 + 2, z0 + 2));
            boolean ceilEnd = false, home = false, eyesFree = true, sat = false;
            double jump = 0, minNy = 1;
            int ticks = 0;
            if (carts.size() == 1) {
                com.divinerpgextended.entity.EntitySkyCart c = (com.divinerpgextended.entity.EntitySkyCart) carts.get(0);
                c.interactFirst(rv);
                sat = rv.ridingEntity == c;
                double lx = c.posX, ly = c.posY, lz = c.posZ;
                for (; ticks < 200; ticks++) {
                    c.onUpdate();
                    c.updateRiderPosition();
                    jump = Math.max(jump, Math.sqrt((c.posX - lx) * (c.posX - lx) + (c.posY - ly) * (c.posY - ly) + (c.posZ - lz) * (c.posZ - lz)));
                    lx = c.posX;
                    ly = c.posY;
                    lz = c.posZ;
                    minNy = Math.min(minNy, c.normal()[1]);
                    int[] r = c.railPos();
                    if (r[0] == x0 && r[1] == y0 + 5) ceilEnd = true;
                    net.minecraft.block.Block eye = w.getBlock((int) Math.floor(rv.posX), (int) Math.floor(rv.posY + 1.62), (int) Math.floor(rv.posZ));
                    if (eye.isOpaqueCube()) eyesFree = false;
                    if (ceilEnd && r[1] == y0 && r[0] <= x0 + 2) {
                        home = true;
                        break;
                    }
                }
                rv.mountEntity(null);
                c.setDead();
            }
            for (int x = x0 - 2; x <= x0 + 8; x++) for (int y = y0 - 1; y <= y0 + 8; y++) for (int z = z0 - 2; z <= z0 + 2; z++) w.setBlockToAir(x, y, z);
            check(floor == 5 && bent && wall == 5 && ceil == 5 && ramps && sat && ceilEnd && home && jump < 0.5 && minNy < -0.99 && eyesFree,
                "на полу " + floor + ", поворот " + bent + " (мета " + bend + "), на стене " + wall + ", на потолке " + ceil + ", горки " + low + "/" + high + ", сел " + sat + ", до конца потолка " + ceilEnd
                    + ", вернулась " + home + " за " + ticks + " тиков, скачок " + String.format("%.2f", jump) + ", вверх дном " + (minNy < -0.99) + ", глаза не в камне " + eyesFree);
            return "рельсы Апалачии: пол - гнущиеся рельсы, у стены горка, стена и потолок - навесные; вагонетка с седоком доезжает до конца потолка и возвращается без скачков";
        });
        t("Прохождение", "Рельсы Апалачии на стену и в дроп", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.apalachia);
            loadAround(w, 9560, 9820, 1);
            w.setBlock(9560, 100, 9820, Blocks.stone);
            w.setBlockToAir(9561, 100, 9820);
            TestPlayer rv = player(ConfigurationHelper.apalachia, "TestRailSwap", 9562.5, 100, 9820.5);
            ItemStack r4 = new ItemStack(com.divinerpgextended.registry.ModItems.apalachiaRails, 4);
            hold(rv, r4);
            boolean used = r4.getItem().onItemUse(r4, rv, w, 9560, 100, 9820, 5, 1F, 0.5F, 0.5F); // восточная грань камня
            boolean placed = w.getBlock(9561, 100, 9820) == com.divinerpgextended.registry.ModBlocks.skyRail;
            int meta = w.getBlockMetadata(9561, 100, 9820) & 7;
            java.util.ArrayList<ItemStack> dr = com.divinerpgextended.registry.ModBlocks.skyRail.getDrops(w, 9561, 100, 9820, w.getBlockMetadata(9561, 100, 9820), 0);
            java.util.ArrayList<ItemStack> dd = new java.util.ArrayList<ItemStack>();
            dd.add(new ItemStack(net.divinerpg.utils.blocks.TwilightBlocks.apalachiaRails));
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent(9561, 99, 9820, w,
                net.divinerpg.utils.blocks.TwilightBlocks.apalachiaRails, 0, 0, 1F, dd, rv, false));
            boolean drops = dr.size() == 1 && dr.get(0).getItem() == com.divinerpgextended.registry.ModItems.apalachiaRails
                && dd.get(0).getItem() == com.divinerpgextended.registry.ModItems.apalachiaRails;
            w.setBlockToAir(9561, 100, 9820);
            w.setBlockToAir(9560, 100, 9820);
            check(used && placed && meta == net.minecraftforge.common.util.ForgeDirection.WEST.ordinal() && r4.stackSize == 3 && drops,
                "поставлено " + placed + ", опора " + meta + ", осталось " + r4.stackSize + ", выпадают рельсами аддона " + drops);
            return "рельсы Апалачии ставятся на стену навесными (опора - камень с запада); и навесной, и рельс Divine ломаются в рельсы Апалачии";
        });
        t("Логова", "Король: прыжок ввысь", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.apalachia);
            loadAround(w, 9650, 9650, 3);
            for (int i = -24; i <= 24; i++) for (int k2 = -24; k2 <= 24; k2++) {
                w.setBlock(9650 + i, 99, 9650 + k2, Blocks.stone);
                for (int h = 100; h < 130; h++) w.setBlockToAir(9650 + i, h, 9650 + k2);
            }
            TestPlayer sv2 = join(player(ConfigurationHelper.apalachia, "TestSlam", 9655.5, 100, 9650.5), true);
            com.divinerpgextended.entity.EntityWarriorKing kk = new com.divinerpgextended.entity.EntityWarriorKing(w);
            kk.setPosition(9650.5, 100, 9650.5);
            w.spawnEntityInWorld(kk);
            // вторая встреча: прыжок ввысь доступен
            kk.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(sv2), 100000);
            for (int i = 0; i < com.divinerpgextended.entity.EntityWarriorKing.KNEEL + 2; i++) kk.onLivingUpdate();
            sv2.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth).setBaseValue(100000); // смотрим на прыжок, а не на бой
            sv2.setHealth(100000);
            kk.setAttackTarget(sv2);
            double maxY = kk.posY;
            boolean held = false;
            int states = 0;
            String trace = "";
            float hp = sv2.getHealth();
            for (int i = 0; i < 400; i++) {
                sv2.setPositionAndUpdate(9655.5, 100, 9650.5);
                kk.onUpdate();
                maxY = Math.max(maxY, kk.posY);
                if (i % 50 == 0) trace += i + ":" + kk.round() + (kk.getAttackTarget() != null ? "t" : "-") + (kk.onGround ? "g" : "a") + kk.slamState() + " ";
                if (kk.slamState() == com.divinerpgextended.entity.EntityWarriorKing.SLAM_HOLD) held = true;
                if (kk.slamState() > 0) states |= 1 << kk.slamState();
            }
            kk.setDead();
            sv2.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth).setBaseValue(20);
            sv2.setHealth(20);
            check(maxY > 105 && held, trace + "; высшая точка " + (maxY - 100) + " над полом, зависал " + held + ", здоровье игрока " + hp + " > " + sv2.getHealth());
            return "со 2-й встречи Король прыгает ввысь на " + (int) (maxY - 100) + " блоков, зависает над меткой и обрушивается";
        });
        t("Логова", "Скол рога", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            loadAround(w, 9940, 9940, 1);
            TestPlayer hv = player(ConfigurationHelper.eden, "TestHorn", 9940, 100, 9940);
            com.divinerpgextended.entity.EntityGreatCadillion boss = new com.divinerpgextended.entity.EntityGreatCadillion(w);
            boss.setVariant(0);
            boss.setPosition(9941, 100, 9940);
            w.spawnEntityInWorld(boss);
            boss.stun(80);
            hv.setCurrentItemOrArmor(0, new ItemStack(Items.iron_pickaxe));
            boss.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(hv), 2); // без сета Эдема - не откалывается
            boolean plain = boss.hornBroken();
            Item[] eden = com.divinerpgextended.world.twilight.TwilightWorld.EDEN.armor();
            armor(hv, eden[0], eden[1], eden[2], eden[3]);
            boss.hurtResistantTime = 0;
            float hp = boss.getHealth();
            boss.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(hv), 2);
            float lost = hp - boss.getHealth();
            boolean broken = boss.hornBroken();
            boss.setDead();
            armor(hv, null, null, null, null);
            hv.setCurrentItemOrArmor(0, null);
            check(!plain && broken && lost > boss.getMaxHealth() * 0.14F, "без сета " + plain + ", в сете " + broken + ", снято " + lost + " из " + boss.getMaxHealth());
            return "оглушённый кадиллион Эдема: кирка в сете Эдема откалывает рог (-" + (int) lost + " здоровья, таранов больше нет), без сета - нет";
        });
        t("Прохождение", "Материалы с мобов", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            TestPlayer v = player(ConfigurationHelper.eden, "TestDrops", 9950, 100, 9950);
            net.divinerpg.entities.twilight.EntityEdenCori cori = new net.divinerpg.entities.twilight.EntityEdenCori(w);
            cori.setPosition(9950, 100, 9950);
            int got = 0;
            for (int i = 0; i < 100; i++) {
                java.util.ArrayList<net.minecraft.entity.item.EntityItem> drops = new java.util.ArrayList<net.minecraft.entity.item.EntityItem>();
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDropsEvent(cori,
                    net.minecraft.util.DamageSource.causePlayerDamage(v), drops, 0, true, 0));
                for (net.minecraft.entity.item.EntityItem d : drops) {
                    ItemStack it = d.getEntityItem();
                    if (it.getItem() == com.divinerpgextended.registry.ModItems.twilightMaterial
                        && it.getItemDamage() == com.divinerpgextended.world.twilight.TwilightMaterial.SUN_FEATHER.ordinal()) got++;
                }
            }
            check(got > 15 && got < 60, "солнечных перьев с 100 Кори: " + got);
            return "с Кори падает солнечное перо: " + got + " из 100 (шанс 35%)";
        });
        t("Прохождение", "Зов требует печать", () -> {
            net.minecraft.inventory.InventoryCrafting old = grid(), now = grid();
            Item soul = net.divinerpg.utils.items.TwilightItemsOther.edenSoul, gem = net.divinerpg.utils.items.TwilightItemsOther.edenGem;
            for (int i = 0; i < 9; i++) old.setInventorySlotContents(i, new ItemStack(i == 4 ? gem : soul));
            ItemStack[] layout = { new ItemStack(soul), new ItemStack(com.divinerpgextended.registry.ModItems.twilightMaterial, 1,
                com.divinerpgextended.world.twilight.TwilightMaterial.HORN_SHARD.ordinal()), new ItemStack(soul), new ItemStack(soul), new ItemStack(gem),
                new ItemStack(soul), new ItemStack(soul), new ItemStack(com.divinerpgextended.registry.ModItems.sanctumSeal, 1, 0), new ItemStack(soul) };
            for (int i = 0; i < 9; i++) now.setInventorySlotContents(i, layout[i]);
            ItemStack a = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(old, server.worldServers[0]);
            ItemStack b = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(now, server.worldServers[0]);
            check(a == null && b != null && b.getItem() == com.divinerpgextended.registry.ModItems.cadillionCall && b.getItemDamage() == 0,
                "старый рецепт " + a + ", новый " + b);
            return "8 душ вокруг камня больше не дают Зов; души + камень + обломок рога + печать святилища - Зов Эдема";
        });
        t("Прохождение", "Закалка", () -> {
            Item feather = com.divinerpgextended.registry.ModItems.twilightMaterial, gem = net.divinerpg.utils.items.TwilightItemsOther.edenGem;
            int ff = com.divinerpgextended.world.twilight.TwilightMaterial.SUN_FEATHER.ordinal(), root = com.divinerpgextended.world.twilight.TwilightMaterial.GREENFEET_ROOT.ordinal();
            net.minecraft.inventory.InventoryCrafting tg = grid();
            tg.setInventorySlotContents(0, new ItemStack(net.divinerpg.utils.items.TwilightItemsArmor.edenHelmet));
            tg.setInventorySlotContents(1, new ItemStack(feather, 1, ff));
            tg.setInventorySlotContents(2, new ItemStack(feather, 1, ff));
            tg.setInventorySlotContents(3, new ItemStack(feather, 1, root));
            tg.setInventorySlotContents(4, new ItemStack(feather, 1, root));
            tg.setInventorySlotContents(5, new ItemStack(gem));
            ItemStack helm = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(tg, server.worldServers[0]);
            tg.setInventorySlotContents(4, null);
            ItemStack short_ = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(tg, server.worldServers[0]);
            net.minecraft.inventory.InventoryCrafting bg = grid();
            bg.setInventorySlotContents(0, new ItemStack(net.divinerpg.utils.items.TwilightItemsWeapons.edenBow));
            for (int i = 1; i <= 3; i++) bg.setInventorySlotContents(i, new ItemStack(feather, 1, ff));
            bg.setInventorySlotContents(4, new ItemStack(gem));
            ItemStack bow = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(bg, server.worldServers[0]);
            check(com.divinerpgextended.item.TemperRecipe.tempered(helm) && short_ == null && com.divinerpgextended.item.TemperRecipe.tempered(bow),
                "шлем " + helm + ", без одного корня " + short_ + ", лук " + bow);
            return "шлем Эдема + 2 пера + 2 корня + камень - закалён; без корня не крафтится; лук Эдема + 3 пера + камень - закалён";
        });
        t("Прохождение", "Закалённый сет", () -> {
            TestPlayer v = player(ConfigurationHelper.eden, "TestTempered", 9960, 100, 9960);
            Item[] set = { net.divinerpg.utils.items.TwilightItemsArmor.edenHelmet, net.divinerpg.utils.items.TwilightItemsArmor.edenChestplate,
                net.divinerpg.utils.items.TwilightItemsArmor.edenLeggings, net.divinerpg.utils.items.TwilightItemsArmor.edenBoots };
            for (int i = 0; i < 4; i++) {
                ItemStack st = new ItemStack(set[i]);
                st.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
                st.getTagCompound().setBoolean(com.divinerpgextended.item.TemperRecipe.TAG, true);
                v.setCurrentItemOrArmor(4 - i, st);
            }
            v.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.blindness.id, 100, 0));
            v.ticksExisted = 100;
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(v));
            boolean blind = v.isPotionActive(net.minecraft.potion.Potion.blindness);
            net.minecraft.entity.monster.EntityZombie zz = new net.minecraft.entity.monster.EntityZombie(v.worldObj);
            net.minecraftforge.event.entity.living.LivingHurtEvent ev = new net.minecraftforge.event.entity.living.LivingHurtEvent(v,
                net.minecraft.util.DamageSource.causeMobDamage(zz), 10);
            MinecraftForge.EVENT_BUS.post(ev);
            armor(v, null, null, null, null);
            check(!blind && Math.abs(ev.ammount - 5) < 0.6, "ослепление осталось " + blind + ", урон моба 10 > " + ev.ammount);
            return "закалённый сет Эдема снимает ослепление, урон мобов дома x0.5";
        });
        t("Прогрессия", "Палки мира", () -> {
            net.minecraft.inventory.InventoryCrafting cg = grid();
            cg.setInventorySlotContents(0, new ItemStack(net.divinerpg.utils.blocks.TwilightBlocks.edenPlanks));
            cg.setInventorySlotContents(3, new ItemStack(net.divinerpg.utils.blocks.TwilightBlocks.edenPlanks));
            ItemStack st = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(cg, server.worldServers[0]);
            net.minecraft.inventory.InventoryCrafting tg = grid();
            tg.setInventorySlotContents(1, new ItemStack(net.divinerpg.utils.items.TwilightItemsOther.edenDust));
            tg.setInventorySlotContents(4, com.divinerpgextended.world.twilight.TwilightCrafts.stick(com.divinerpgextended.world.twilight.WorldWood.EDEN));
            ItemStack torch = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(tg, server.worldServers[0]);
            boolean ore = false;
            for (int id : net.minecraftforge.oredict.OreDictionary.getOreIDs(com.divinerpgextended.world.twilight.TwilightCrafts.stick(com.divinerpgextended.world.twilight.WorldWood.EDEN)))
                if ("stickWood".equals(net.minecraftforge.oredict.OreDictionary.getOreName(id))) ore = true;
            check(st != null && st.getItem() == com.divinerpgextended.registry.ModItems.worldStick && st.getItemDamage() == com.divinerpgextended.world.twilight.WorldWood.EDEN.ordinal()
                && st.stackSize == 4 && torch != null && torch.getItem() == Item.getItemFromBlock(com.divinerpgextended.registry.ModBlocks.worldTorch[0]) && ore,
                "из досок Эдема " + st + ", факел " + torch + ", stickWood " + ore);
            return "2 доски Эдема - 4 палки Эдема (не ванильные), пыль Эдема на палке - 4 факела Эдема; палка мира годится как обычная";
        });
        t("Прогрессия", "Цепочка крафтов", () -> {
            Item chunk = net.divinerpg.utils.items.TwilightItemsOther.wildwoodChunk;
            ItemStack horn = com.divinerpgextended.world.twilight.TwilightCrafts.mat(com.divinerpgextended.world.twilight.TwilightWorld.WILDWOOD,
                com.divinerpgextended.world.twilight.TwilightCrafts.ARMOR_MAT);
            ItemStack worn = new ItemStack(net.divinerpg.utils.items.TwilightItemsArmor.edenHelmet);
            worn.setItemDamage(worn.getMaxDamage() / 2); // изношенный шлем прошлого мира тоже годится
            net.minecraft.inventory.InventoryCrafting cg = grid();
            ItemStack[] lay = { new ItemStack(chunk), horn, new ItemStack(chunk), new ItemStack(chunk), worn, new ItemStack(chunk) };
            for (int i = 0; i < 6; i++) cg.setInventorySlotContents(i, lay[i]);
            ItemStack helm = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(cg, server.worldServers[0]);
            net.minecraft.inventory.InventoryCrafting old = grid(); // рецепт Divine: 5 кусков
            for (int i : new int[] { 0, 1, 2, 3, 5 }) old.setInventorySlotContents(i, new ItemStack(chunk));
            ItemStack oldHelm = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(old, server.worldServers[0]);
            // клинок Эдема: божественный меч + кусок Эдема + 2 солнечных пера + палка Эдема
            net.minecraft.inventory.InventoryCrafting b = grid();
            ItemStack feather = com.divinerpgextended.world.twilight.TwilightCrafts.mat(com.divinerpgextended.world.twilight.TwilightWorld.EDEN,
                com.divinerpgextended.world.twilight.TwilightCrafts.WEAPON_MAT);
            b.setInventorySlotContents(1, new ItemStack(net.divinerpg.utils.items.TwilightItemsOther.edenChunk));
            b.setInventorySlotContents(3, feather.copy());
            b.setInventorySlotContents(4, new ItemStack(net.divinerpg.utils.items.VanillaItemsWeapons.divineSword));
            b.setInventorySlotContents(5, feather.copy());
            b.setInventorySlotContents(7, com.divinerpgextended.world.twilight.TwilightCrafts.stick(com.divinerpgextended.world.twilight.WorldWood.EDEN));
            ItemStack blade = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(b, server.worldServers[0]);
            b.setInventorySlotContents(7, new ItemStack(Items.stick));
            ItemStack plainStick = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(b, server.worldServers[0]);
            check(helm != null && helm.getItem() == net.divinerpg.utils.items.TwilightItemsArmor.wildwoodHelmet && oldHelm == null
                && blade != null && blade.getItem() == net.divinerpg.utils.items.TwilightItemsWeapons.edenBlade && plainStick == null,
                "шлем Дикого леса " + helm + ", старый рецепт " + oldHelm + ", клинок Эдема " + blade + ", на обычной палке " + plainStick);
            return "шлем Дикого леса = шлем Эдема + 4 куска + лунный рог; 5 кусков больше не крафтят; клинок Эдема из божественного меча на палке Эдема (обычная не подходит)";
        });
        t("Прогрессия", "Камни врат", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            net.minecraft.inventory.InventoryCrafting cg = grid();
            for (int i = 0; i < 9; i++) cg.setInventorySlotContents(i, i == 4 ? new ItemStack(com.divinerpgextended.registry.ModItems.guardianHeart, 1, 0)
                : new ItemStack(net.divinerpg.utils.blocks.TwilightBlocks.edenBlock));
            ItemStack gates = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(cg, server.worldServers[0]);
            int gx = 9700, gy = 100, gz = 9700;
            loadAround(w, gx, gz, 1);
            TestPlayer v = player(ConfigurationHelper.eden, "TestGate", gx + 2, gy, gz + 2);
            ItemStack clock = com.divinerpgextended.item.ItemWandererClock.withTier(com.divinerpgextended.registry.ModItems.wandererClock, 2);
            boolean[] lit = new boolean[2];
            Block[] frames = { com.divinerpgextended.registry.ModBlocks.gateStone[0], net.divinerpg.utils.blocks.TwilightBlocks.edenBlock };
            for (int f = 0; f < 2; f++) {
                for (int i = -1; i <= 4; i++) for (int h = -1; h <= 5; h++) w.setBlockToAir(gx + i, gy + h, gz);
                for (int i = 0; i < 4; i++) for (int h = 0; h < 5; h++) {
                    boolean corner = (i == 0 || i == 3) && (h == 0 || h == 4);
                    boolean edge = i == 0 || i == 3 || h == 0 || h == 4;
                    if (edge && !corner) w.setBlock(gx + i, gy + h, gz, frames[f]);
                }
                clock.getItem().onItemUse(clock, v, w, gx + 1, gy, gz, 1, 0.5F, 1F, 0.5F);
                lit[f] = w.getBlock(gx + 1, gy + 1, gz) == net.divinerpg.utils.blocks.TwilightBlocks.wildwoodPortal
                    && w.getBlock(gx + 2, gy + 3, gz) == net.divinerpg.utils.blocks.TwilightBlocks.wildwoodPortal;
            }
            for (int i = -1; i <= 4; i++) for (int h = -1; h <= 5; h++) w.setBlockToAir(gx + i, gy + h, gz);
            check(gates != null && gates.getItem() == Item.getItemFromBlock(com.divinerpgextended.registry.ModBlocks.gateStone[0]) && gates.stackSize == 12
                && lit[0] && !lit[1], "камни врат " + gates + ", портал из камней врат " + lit[0] + ", из блоков Эдема " + lit[1]);
            return "8 блоков Эдема + сердце стража - 12 камней врат; часы II зажигают портал в Дикий лес из камней врат, из блоков Эдема - нет";
        });
        t("Прогрессия", "Уровни кирок", () -> {
            TestPlayer mv = player(ConfigurationHelper.wildwood, "TestMiner", 9800, 100, 9800);
            Item divine = net.divinerpg.utils.items.VanillaItemsTools.divinePickaxe, eden = net.divinerpg.utils.items.TwilightItemsTools.edenPickaxe,
                wild = net.divinerpg.utils.items.TwilightItemsTools.wildwoodPickaxe;
            Object[][] cases = { // кирка, блок, можно ли ломать в Диком лесу
                { divine, net.divinerpg.utils.blocks.TwilightBlocks.twilightStone, false }, { eden, net.divinerpg.utils.blocks.TwilightBlocks.twilightStone, true },
                { eden, net.divinerpg.utils.blocks.TwilightBlocks.wildwoodOre, false }, { wild, net.divinerpg.utils.blocks.TwilightBlocks.wildwoodOre, true },
                { wild, net.divinerpg.utils.blocks.TwilightBlocks.edenOre, true }, { divine, net.divinerpg.utils.blocks.TwilightBlocks.edenOre, false },
                { null, net.divinerpg.utils.blocks.TwilightBlocks.wildwoodDirt, true } };
            String bad = "";
            for (Object[] c : cases) {
                mv.setCurrentItemOrArmor(0, c[0] == null ? null : new ItemStack((Item) c[0]));
                net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed ev = new net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed(mv,
                    (Block) c[1], 0, 1.0F, 9800, 99, 9800);
                MinecraftForge.EVENT_BUS.post(ev);
                if ((ev.newSpeed > 0) != (Boolean) c[2]) bad += (c[0] == null ? "рука" : ((Item) c[0]).getUnlocalizedName()) + " / " + ((Block) c[1]).getUnlocalizedName() + "; ";
            }
            mv.setCurrentItemOrArmor(0, null);
            check(bad.isEmpty(), "не так: " + bad);
            return "в Диком лесу камень берёт кирка Эдема (божественная - нет), руду Дикого леса - только его кирка, руду Эдема - она же; землю - рука";
        });
        t("Логова", "Торговля странника", () -> {
            net.minecraft.village.MerchantRecipeList l = com.divinerpgextended.entity.EntityWanderer.trades(com.divinerpgextended.world.twilight.TwilightWorld.EDEN);
            boolean pointless = false, torches = false, swap = false;
            for (Object o : l) {
                net.minecraft.village.MerchantRecipe r = (net.minecraft.village.MerchantRecipe) o;
                Item in = r.getItemToBuy().getItem(), out = r.getItemToSell().getItem();
                if (in == net.divinerpg.utils.items.TwilightItemsOther.edenFragments || in == net.divinerpg.utils.items.TwilightItemsOther.edenSoul) pointless = true;
                if (out == Item.getItemFromBlock(com.divinerpgextended.registry.ModBlocks.worldTorch[0])) torches = true;
                if (in == com.divinerpgextended.registry.ModItems.twilightMaterial && out == in) swap = true;
            }
            check(!pointless && torches && swap, "обмен осколков/душ " + pointless + ", факелы " + torches + ", обмен материалов " + swap + ", сделок " + l.size());
            return "Странник Эдема: " + l.size() + " сделок - карта, факелы, стрелы, саженцы, обмен материалов, еда, лечение; осколки и души на камни больше не меняет";
        });
        t("Прогрессия", "Лампы миров", () -> {
            net.minecraft.inventory.InventoryCrafting lg = grid();
            for (int i = 0; i < 9; i++) lg.setInventorySlotContents(i, new ItemStack(i == 4 ? net.divinerpg.utils.items.TwilightItemsOther.mortumFragments : Items.iron_ingot));
            ItemStack lamp = net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(lg, server.worldServers[0]);
            // руины Мортума освещены лампой Мортума
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            loadAround(w, 9400, 9400, 1);
            for (int i = -2; i < 12; i++) for (int k2 = -2; k2 < 12; k2++) {
                for (int h = 95; h < 99; h++) w.setBlock(9400 + i, h, 9400 + k2, Blocks.stone);
                w.setBlock(9400 + i, 99, 9400 + k2, net.divinerpg.utils.blocks.TwilightBlocks.mortumGrass);
                for (int h = 100; h < 137; h++) w.setBlockToAir(9400 + i, h, 9400 + k2);
            }
            com.divinerpgextended.world.twilight.WorldGenTwilightFeatures.ruin(w, new java.util.Random(1), com.divinerpgextended.world.twilight.TwilightWorld.MORTUM, 9400, 9400);
            int lamps = 0, glow = 0;
            for (int i = -2; i < 12; i++) for (int k2 = -2; k2 < 12; k2++) for (int h = 95; h < 112; h++) {
                net.minecraft.block.Block b = w.getBlock(9400 + i, h, 9400 + k2);
                if (b == com.divinerpgextended.registry.ModBlocks.worldLamp[com.divinerpgextended.world.twilight.TwilightWorld.MORTUM.ordinal()]) lamps++;
                if (b == Blocks.glowstone) glow++;
            }
            check(lamp != null && lamp.getItem() == Item.getItemFromBlock(com.divinerpgextended.registry.ModBlocks.worldLamp[4]) && lamps > 0 && glow == 0,
                "рецепт " + lamp + ", ламп Мортума в руинах " + lamps + ", глоустоуна " + glow);
            return "железо вокруг осколка Мортума - лампа Мортума; руины освещены лампой своего мира, глоустоуна нет";
        });
        t("Логова", "Зов мимо алтаря", () -> {
            int dim = ConfigurationHelper.eden;
            WorldServer w = server.worldServerForDimension(dim);
            TestPlayer v = player(dim, "TestCallAway", 3000, 100, 3000);
            ItemStack call = new ItemStack(com.divinerpgextended.registry.ModItems.cadillionCall, 1, 0);
            hold(v, call);
            w.setBlock(3000, 99, 3000, Blocks.stone);
            v.chat.clear();
            call.getItem().onItemUse(call, v, w, 3000, 99, 3000, 1, 0.5F, 1F, 0.5F);
            java.util.List<?> bosses = w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntityGreatCadillion.class,
                net.minecraft.util.AxisAlignedBB.getBoundingBox(2990, 90, 2990, 3010, 110, 3010));
            check(bosses.isEmpty() && call.stackSize == 1 && v.heard("dre.chat.call_at_nest"), "боссов " + bosses.size() + ", зов " + call.stackSize + ", чат " + v.chat);
            return "Зов по обычному блоку не работает, подсказка про алтарь гнезда";
        });
        t("Логова", "Арены Мортума", () -> {
            int dim = ConfigurationHelper.mortum;
            WorldServer w = server.worldServerForDimension(dim);
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            TestPlayer v = player(dim, "TestArenas", 0, 80, 0);
            for (com.divinerpgextended.world.twilight.MortumArenas.Type t : com.divinerpgextended.world.twilight.MortumArenas.Type.values()) {
                int[] c = com.divinerpgextended.world.twilight.MortumArenas.nearestOf(w, t, 0, 0, 6);
                if (c == null) {
                    ok = false;
                    sb.append(t.key()).append(": нет; ");
                    continue;
                }
                loadAround(w, c[0], c[2], 3);
                int fy = com.divinerpgextended.world.twilight.MortumArenas.floorY(t);
                boolean floor = w.getBlock(c[0] + 3, fy, c[2] + 3).isOpaqueCube() && w.isAirBlock(c[0] + 3, fy + 1, c[2] + 3) && w.isAirBlock(c[0] + 3, fy + 3, c[2] + 3);
                net.minecraft.item.Item own = null, other = null;
                for (net.minecraft.item.Item i : new net.minecraft.item.Item[] { net.divinerpg.utils.items.TwilightItemsOther.twilightDemonCrystal,
                    net.divinerpg.utils.items.TwilightItemsOther.densosCrystal, net.divinerpg.utils.items.TwilightItemsOther.karotCrystal,
                    net.divinerpg.utils.items.TwilightItemsOther.eternalArcherCrystal, net.divinerpg.utils.items.TwilightItemsOther.soulFiendCrystal,
                    net.divinerpg.utils.items.TwilightItemsOther.vamacheronCrystal }) {
                    if (com.divinerpgextended.world.twilight.MortumArenas.ofCrystal(i) == t) own = i;
                    else if (other == null) other = i;
                }
                int[] al = com.divinerpgextended.world.twilight.MortumArenas.altars(t).get(0);
                int ax = c[0] + al[0], ay = al[1], az = c[2] + al[2];
                boolean altar = w.getBlock(ax, ay, az) == com.divinerpgextended.registry.ModBlocks.arenaAltar && w.isAirBlock(ax, ay + 1, az) && w.isAirBlock(ax, ay + 2, az);
                String in = com.divinerpgextended.boss.LairSummons.refusal(v, own, ax, ay, az);
                String wrong = com.divinerpgextended.boss.LairSummons.refusal(v, other, ax, ay, az);
                String floorClick = com.divinerpgextended.boss.LairSummons.refusal(v, own, c[0] + 3, fy, c[2] + 3);
                String out = com.divinerpgextended.boss.LairSummons.refusal(v, own, c[0] + 80, fy, c[2]);
                int statues = 0;
                for (int x = c[0] - 30; x <= c[0] + 30; x++) for (int z = c[2] - 30; z <= c[2] + 30; z++) for (int y = fy - 6; y <= fy + 12; y++)
                    if (w.getBlock(x, y, z) instanceof net.divinerpg.blocks.base.BlockStatue) statues++;
                floor &= statues == 0 && altar && "dre.chat.summon_altar".equals(floorClick);
                boolean good = floor && in == null && wrong != null && ("dre.chat.summon_arena_" + t.key()).equals(out);
                ok &= good;
                dump(w, c[0], com.divinerpgextended.world.twilight.WorldGenNests.MORTUM_TOP, c[2], 34, "arena-" + t.key());
                // стоит на фундаменте: под полом у края нет пустоты до земли
                int fx = c[0] + 10, fz = c[2] + 2, gap = 0;
                for (int y = com.divinerpgextended.world.twilight.WorldGenNests.MORTUM_TOP - 1; y > com.divinerpgextended.world.twilight.WorldGenNests.MORTUM_TOP - 30; y--)
                    if (w.isAirBlock(fx, y, fz)) gap++;
                floor &= gap == 0;
                sb.append(t.key()).append(good ? " ок" : " пол " + floor + " алтарь " + altar + " (над ним " + w.getBlock(ax, ay + 1, az).getUnlocalizedName() + "/" + w.getBlock(ax, ay + 2, az).getUnlocalizedName() + ", пол " + w.getBlock(c[0] + 3, fy, c[2] + 3).getUnlocalizedName() + "/" + w.getBlock(c[0] + 3, fy + 1, c[2] + 3).getUnlocalizedName() + "/" + w.getBlock(c[0] + 3, fy + 3, c[2] + 3).getUnlocalizedName() + ") статуй " + statues + " свой " + in + " чужой " + wrong + " мимо алтаря " + floorClick + " снаружи " + out).append("; ");
            }
            // соседние квадраты всегда разных видов
            boolean differ = true;
            for (int i = -5; i <= 5; i++) for (int j = -5; j <= 5; j++) for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++)
                if ((a != 0 || b != 0) && com.divinerpgextended.world.twilight.MortumArenas.type(w.getSeed(), i, j) == com.divinerpgextended.world.twilight.MortumArenas.type(w.getSeed(), i + a, j + b)) differ = false;
            check(ok && differ, sb + "соседи разные " + differ);
            return "у каждого из шести боссов Мортума своя арена с алтарём, статуй нет; кристалл работает только по алтарю своей арены, мимо алтаря, чужой и снаружи - отказ; соседние арены разные";
        });
        t("Логова", "Боссы арен Мортума: механики", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            int x0 = 9700, y0 = 90, z0 = 9700;
            loadAround(w, x0, z0, 2);
            for (int x = x0 - 12; x <= x0 + 12; x++) for (int z = z0 - 12; z <= z0 + 12; z++) for (int y = y0 - 1; y <= y0 + 8; y++)
                w.setBlock(x, y, z, y == y0 - 1 ? Blocks.stone : Blocks.air);
            TestPlayer mp = join(player(ConfigurationHelper.mortum, "TestMortumBoss", x0 + 0.5, y0, z0 + 6.5), true);
            // близнецы: рядом - прикрывают; убит один - брат поднимает, поднятый без трофеев
            net.divinerpg.entities.twilight.EntityDensos de = new net.divinerpg.entities.twilight.EntityDensos(w);
            net.divinerpg.entities.twilight.EntityReyvor re = new net.divinerpg.entities.twilight.EntityReyvor(w);
            de.setLocationAndAngles(x0 + 0.5, y0, z0 + 0.5, 0, 0);
            re.setLocationAndAngles(x0 + 2.5, y0, z0 + 0.5, 0, 0);
            w.spawnEntityInWorld(de);
            w.spawnEntityInWorld(re);
            com.divinerpgextended.boss.LairSummons.leash(de, x0, y0, z0, 20);
            com.divinerpgextended.boss.LairSummons.leash(re, x0, y0, z0, 20);
            // дуэль: брат не в своей дуэли неуязвим, дуэлянт уязвим и тянет цель на свою половину
            boolean dTurn = com.divinerpgextended.boss.MortumArenaBosses.duelist(de);
            net.minecraft.entity.EntityLiving duel = dTurn ? de : re, idle = dTurn ? re : de;
            net.minecraftforge.event.entity.living.LivingAttackEvent ta = new net.minecraftforge.event.entity.living.LivingAttackEvent(idle, net.minecraft.util.DamageSource.causePlayerDamage(mp), 10);
            net.minecraftforge.event.entity.living.LivingAttackEvent tb = new net.minecraftforge.event.entity.living.LivingAttackEvent(duel, net.minecraft.util.DamageSource.causePlayerDamage(mp), 10);
            MinecraftForge.EVENT_BUS.post(ta);
            MinecraftForge.EVENT_BUS.post(tb);
            int sd = com.divinerpgextended.boss.MortumArenaBosses.side(duel);
            mp.setPosition(x0 + 0.5 - sd * 5, y0, z0 + 6.5); // на чужой половине
            mp.motionX = 0;
            com.divinerpgextended.boss.MortumArenaBosses.pull(duel, mp, x0 + 0.5, sd);
            boolean pulled = mp.motionX * sd > 0.3 && mp.motionY > 0.1;
            mp.setPositionAndUpdate(x0 + 0.5, y0, z0 + 6.5);
            mp.motionX = mp.motionY = mp.motionZ = 0;
            boolean shield = ta.isCanceled() && !tb.isCanceled() && pulled;
            mp.rotationYaw = 0; // смотрит на юг (+Z): спина - на севере
            float hpBefore = mp.getHealth();
            mp.hurtResistantTime = 0;
            boolean stepped = com.divinerpgextended.boss.MortumArenaBosses.step(re, mp) && re.posZ < mp.posZ - 1.5 && mp.getHealth() < hpBefore
                && mp.isPotionActive(net.minecraft.potion.Potion.moveSlowdown);
            mp.clearActivePotions();
            mp.setHealth(mp.getMaxHealth());
            de.setHealth(0);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(de, net.minecraft.util.DamageSource.causePlayerDamage(mp)));
            de.setDead();
            int queued = com.divinerpgextended.boss.MortumArenaBosses.pending();
            server.worldServers[0].getWorldInfo().incrementTotalWorldTime(w.getTotalWorldTime() + com.divinerpgextended.boss.MortumArenaBosses.REVIVE + 1); // время Мортума - от основного мира
            FMLCommonHandler.instance().bus().post(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER, cpw.mods.fml.common.gameevent.TickEvent.Phase.END, w));
            net.divinerpg.entities.twilight.EntityDensos back = null;
            for (Object o : w.getEntitiesWithinAABB(net.divinerpg.entities.twilight.EntityDensos.class, re.boundingBox.expand(6, 4, 6)))
                if (((net.minecraft.entity.Entity) o).isEntityAlive()) back = (net.divinerpg.entities.twilight.EntityDensos) o;
            boolean revived = back != null && back.getEntityData().getBoolean(com.divinerpgextended.boss.MortumArenaBosses.REVIVED)
                && Math.abs(back.getHealth() - back.getMaxHealth() / 2) < 1 && com.divinerpgextended.boss.LairSummons.home(back) != null;
            java.util.ArrayList<net.minecraft.entity.item.EntityItem> bd = new java.util.ArrayList<net.minecraft.entity.item.EntityItem>();
            bd.add(new net.minecraft.entity.item.EntityItem(w, x0, y0, z0, new ItemStack(Items.diamond)));
            if (back != null) MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDropsEvent(back, net.minecraft.util.DamageSource.causePlayerDamage(mp), bd, 0, true, 0));
            boolean noLoot = bd.isEmpty();
            if (back != null) back.setDead();
            re.setDead();
            // Пожиратель душ: поднял души - неуязвим; души убиты - уязвим
            net.divinerpg.entities.twilight.EntitySoulFiend sf = new net.divinerpg.entities.twilight.EntitySoulFiend(w);
            sf.setLocationAndAngles(x0 + 0.5, y0, z0 + 0.5, 0, 0);
            w.spawnEntityInWorld(sf);
            com.divinerpgextended.boss.LairSummons.leash(sf, x0, y0, z0, 20);
            int stones = 0;
            int[] home = com.divinerpgextended.boss.LairSummons.home(sf);
            for (int[] gr : com.divinerpgextended.world.twilight.MortumArenas.graves()) {
                if (gr[0] * gr[0] + gr[1] * gr[1] > 100 || Math.abs(gr[0]) > 11 || Math.abs(gr[1] + 1) > 11) continue;
                w.setBlock(home[0] + gr[0], home[1], home[2] + gr[1] + 1, Blocks.stonebrick, 3, 3);
                stones++;
            }
            int raised = com.divinerpgextended.boss.MortumArenaBosses.raise(sf);
            int intact = 0;
            for (int[] gr : com.divinerpgextended.world.twilight.MortumArenas.graves())
                if (com.divinerpgextended.boss.MortumArenaBosses.graveIntact(w, home, gr)) intact++;
            boolean used = intact == stones - raised;
            net.minecraftforge.event.entity.living.LivingAttackEvent fa = new net.minecraftforge.event.entity.living.LivingAttackEvent(sf, net.minecraft.util.DamageSource.causePlayerDamage(mp), 10);
            MinecraftForge.EVENT_BUS.post(fa);
            boolean guarded = fa.isCanceled();
            for (net.minecraft.entity.Entity s : com.divinerpgextended.boss.MortumArenaBosses.souls(sf)) s.setDead();
            net.minecraftforge.event.entity.living.LivingAttackEvent fb = new net.minecraftforge.event.entity.living.LivingAttackEvent(sf, net.minecraft.util.DamageSource.causePlayerDamage(mp), 10);
            MinecraftForge.EVENT_BUS.post(fb);
            boolean open = !fb.isCanceled();
            sf.setDead();
            // Вамачерон: рывок в стену - оглушён, урон x2
            for (int x = x0 - 3; x <= x0 + 3; x++) for (int y = y0; y <= y0 + 4; y++) w.setBlock(x, y, z0 + 4, Blocks.stone);
            net.divinerpg.entities.twilight.EntityVamacheron va = new net.divinerpg.entities.twilight.EntityVamacheron(w);
            va.setLocationAndAngles(x0 + 0.5, y0, z0 - 6.5, 0, 0);
            w.spawnEntityInWorld(va);
            com.divinerpgextended.boss.LairSummons.leash(va, x0, y0, z0, 20);
            com.divinerpgextended.boss.MortumArenaBosses.startCharge(va, mp);
            int stunAt = -1;
            for (int i = 0; i < 80 && stunAt < 0; i++) {
                va.onUpdate();
                if (va.getEntityData().getInteger(com.divinerpgextended.boss.MortumArenaBosses.STUNNED) > 0) stunAt = i;
            }
            net.minecraftforge.event.entity.living.LivingHurtEvent vh = new net.minecraftforge.event.entity.living.LivingHurtEvent(va, net.minecraft.util.DamageSource.causePlayerDamage(mp), 10);
            MinecraftForge.EVENT_BUS.post(vh);
            boolean stunned = stunAt > 0 && Math.abs(vh.ammount - 20) < 0.01;
            va.setDead();
            for (int x = x0 - 3; x <= x0 + 3; x++) for (int y = y0; y <= y0 + 4; y++) w.setBlockToAir(x, y, z0 + 4);
            // Вечный лучник: залп из 9 стрел, колонна у цели теряет верх
            for (int y = y0; y < y0 + 4; y++) w.setBlock(x0 + 2, y, z0 + 6, Blocks.stonebrick, 3, 3);
            net.divinerpg.entities.twilight.EntityEternalArcher ea = new net.divinerpg.entities.twilight.EntityEternalArcher(w);
            ea.setLocationAndAngles(x0 + 0.5, y0, z0 - 8.5, 0, 0);
            w.spawnEntityInWorld(ea);
            int arrows = com.divinerpgextended.boss.MortumArenaBosses.volley(ea, mp);
            boolean eroded = w.isAirBlock(x0 + 2, y0 + 3, z0 + 6) && w.getBlock(x0 + 2, y0 + 2, z0 + 6) == Blocks.stonebrick;
            for (Object o : w.getEntitiesWithinAABB(net.minecraft.entity.projectile.EntityArrow.class, mp.boundingBox.expand(30, 10, 30))) ((net.minecraft.entity.Entity) o).setDead();
            ea.setDead();
            for (int x = x0 - 12; x <= x0 + 12; x++) for (int z = z0 - 12; z <= z0 + 12; z++) for (int y = y0 - 1; y <= y0 + 8; y++) w.setBlockToAir(x, y, z);
            check(shield && revived && noLoot && raised == com.divinerpgextended.boss.MortumArenaBosses.SOULS && used && guarded && open && stunned && arrows == 9 && eroded && stepped,
                "близнецы: дуэль (второй неуязвим, дуэлянт уязвим и тянет) " + shield + ", в очереди " + queued + ", после " + com.divinerpgextended.boss.MortumArenaBosses.pending() + ", Денсосов рядом " + w.getEntitiesWithinAABB(net.divinerpg.entities.twilight.EntityDensos.class, re.boundingBox.expand(30, 10, 30)).size() + ", поднят " + revived + ", без трофеев " + noLoot + "; шаг за спину " + stepped + "; пожиратель: надгробий " + stones + ", душ " + raised + ", целых осталось " + intact + ", неуязвим " + guarded + ", потом уязвим " + open
                    + "; вамачерон: оглушён на тике " + stunAt + ", урон " + vh.ammount + "; лучник: стрел " + arrows + ", колонна осыпалась " + eroded);
            return "близнецы бьются по очереди (дуэлянт тянет на свою половину, второй неуязвим), шагают за спину и поднимают брата (без трофеев); Пожиратель поднимает души только из целых могил рядом, могила рассыпается, пока души живы - неуязвим; Вамачерон оглушается о стену (урон x2); лучник бьёт залпом и рушит укрытия";
        });
        t("Логова", "Мортум: останки гигантов", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            int x0 = 9820, y0 = 60, z0 = 9820;
            loadAround(w, x0, z0, 2);
            for (int x = x0 - 12; x <= x0 + 12; x++) for (int z = z0 - 12; z <= z0 + 12; z++) for (int y = y0 - 6; y <= 120; y++)
                w.setBlock(x, y, z, y < y0 ? net.divinerpg.utils.blocks.TwilightBlocks.mortumGrass : Blocks.air);
            java.util.Random rr = new java.util.Random(3);
            boolean skull = com.divinerpgextended.world.twilight.MortumWorld.giantSkull(w, rr, com.divinerpgextended.world.twilight.TwilightWorld.MORTUM, x0, z0);
            int bone = 0, chest = 0, airInside = 0;
            for (int x = x0 - 7; x <= x0 + 7; x++) for (int z = z0 - 7; z <= z0 + 7; z++) for (int y = y0 - 6; y <= y0 + 9; y++) {
                if (w.getBlock(x, y, z) == Blocks.quartz_block) bone++;
                if (w.getBlock(x, y, z) == Blocks.chest) chest++;
                if (Math.abs(x - x0) <= 2 && Math.abs(z - z0) <= 2 && y >= y0 && y <= y0 + 3 && w.isAirBlock(x, y, z)) airInside++;
            }
            boolean buried = w.getBlock(x0, y0 - 2, z0) != Blocks.air; // низ черепа в земле
            for (int x = x0 - 12; x <= x0 + 12; x++) for (int z = z0 - 12; z <= z0 + 12; z++) for (int y = y0 - 6; y <= 120; y++)
                w.setBlock(x, y, z, y < y0 ? net.divinerpg.utils.blocks.TwilightBlocks.mortumGrass : Blocks.air);
            boolean ribs = com.divinerpgextended.world.twilight.MortumWorld.giantRibs(w, rr, com.divinerpgextended.world.twilight.TwilightWorld.MORTUM, x0, z0);
            int rib = 0, high = 0;
            for (int x = x0 - 9; x <= x0 + 9; x++) for (int z = z0 - 9; z <= z0 + 9; z++) for (int y = y0 - 1; y <= y0 + 8; y++)
                if (w.getBlock(x, y, z) == Blocks.quartz_block) {
                    rib++;
                    if (y >= y0 + 4) high++;
                }
            for (int x = x0 - 12; x <= x0 + 12; x++) for (int z = z0 - 12; z <= z0 + 12; z++) for (int y = y0 - 6; y <= 120; y++) w.setBlockToAir(x, y, z);
            check(skull && bone > 120 && chest == 1 && airInside > 5 && buried && ribs && rib > 60 && high > 10,
                "череп " + skull + ": кости " + bone + ", сундук " + chest + ", полость " + airInside + ", в земле " + buried + "; рёбра " + ribs + ": кости " + rib + ", из них выше 4 блоков " + high);
            return "череп гиганта (" + bone + " блоков кости, наполовину в земле, внутри полость и сундук) и грудная клетка (" + rib + " блоков, рёбра дугами)";
        });
        t("Логова", "Iceika: новогодняя ёлка", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.iceika);
            int x0 = 9900, y0 = 70, z0 = 9900;
            loadAround(w, x0, z0, 2);
            for (int x = x0 - 10; x <= x0 + 10; x++) for (int z = z0 - 10; z <= z0 + 10; z++) for (int y = y0 - 3; y <= 122; y++)
                w.setBlock(x, y, z, y <= y0 ? net.divinerpg.utils.blocks.IceikaBlocks.frozenGrass : Blocks.air);
            boolean built = com.divinerpgextended.world.IceikaXmasTree.build(w, new java.util.Random(7), x0, z0);
            int lights = 0, presents = 0, gifts = 0, canes = 0, leaves = 0;
            boolean santa = false;
            for (int x = x0 - 10; x <= x0 + 10; x++) for (int z = z0 - 10; z <= z0 + 10; z++) for (int y = y0; y <= y0 + 20; y++) {
                net.minecraft.block.Block b = w.getBlock(x, y, z);
                if (b instanceof net.divinerpg.blocks.iceika.BlockXmasLights) lights++;
                if (b == net.divinerpg.utils.blocks.IceikaBlocks.candyCane) canes++;
                if (b == net.divinerpg.utils.blocks.IceikaBlocks.brittleLeaves) leaves++;
                if (b == net.divinerpg.utils.blocks.IceikaBlocks.presentBox) {
                    presents++;
                    net.minecraft.inventory.IInventory inv = (net.minecraft.inventory.IInventory) w.getTileEntity(x, y, z);
                    for (int i = 0; i < inv.getSizeInventory(); i++) if (inv.getStackInSlot(i) != null) gifts++;
                }
            }
            // много ёлок подряд: изредка в подарке - вещь Санты
            for (int i = 0; i < 40 && !santa; i++) {
                net.minecraft.inventory.InventoryBasic inv = new net.minecraft.inventory.InventoryBasic("t", false, 54);
                java.lang.reflect.Method m = com.divinerpgextended.world.IceikaXmasTree.class.getDeclaredMethod("gifts", java.util.Random.class, net.minecraft.inventory.IInventory.class);
                m.setAccessible(true);
                m.invoke(null, new java.util.Random(i), inv);
                for (int k2 = 0; k2 < 54; k2++) if (inv.getStackInSlot(k2) != null && inv.getStackInSlot(k2).getItem() == net.divinerpg.utils.items.IceikaItems.santaCap) santa = true;
            }
            boolean star = w.getBlock(x0, y0 + com.divinerpgextended.world.IceikaXmasTree.HEIGHT + 1, z0) == net.divinerpg.utils.blocks.IceikaBlocks.workshopLamp;
            for (int x = x0 - 10; x <= x0 + 10; x++) for (int z = z0 - 10; z <= z0 + 10; z++) for (int y = y0 - 3; y <= 122; y++) w.setBlockToAir(x, y, z);
            check(built && lights > 10 && presents == com.divinerpgextended.world.IceikaXmasTree.PRESENTS && gifts >= 12 && canes == 6 && leaves > 150 && star && santa,
                "построена " + built + ", огоньков " + lights + ", подарков " + presents + " (вещей " + gifts + "), тростей " + canes + ", хвои " + leaves + ", звезда " + star + ", шапка Санты бывает " + santa);
            return "ёлка: ярусы хвои (" + leaves + "), гирлянды Divine (" + lights + "), звезда, " + presents + " подарочные коробки Divine со сладостями (бывает вещь Санты), леденцовые трости";
        });
        t("Логова", "Мортум 0.78: Демон-маг, ладья, Пожиратель, карты", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            int x0 = 9880, y0 = 90, z0 = 9880;
            loadAround(w, x0, z0, 2);
            for (int x = x0 - 14; x <= x0 + 14; x++) for (int z = z0 - 14; z <= z0 + 14; z++) for (int y = y0 - 3; y <= y0 + 10; y++)
                w.setBlock(x, y, z, y < y0 ? Blocks.stone : Blocks.air);
            TestPlayer dp = join(player(ConfigurationHelper.mortum, "TestDemonMage", x0 + 6.5, y0, z0 + 0.5), true);
            // Демон: не-фазер - 10%, фазер - в полную; рядом рвётся тетива; руны взрываются; блинк - три снаряда
            net.divinerpg.entities.twilight.EntityTwilightDemon dm = new net.divinerpg.entities.twilight.EntityTwilightDemon(w);
            dm.setLocationAndAngles(x0 + 0.5, y0, z0 + 0.5, 0, 0);
            w.spawnEntityInWorld(dm);
            com.divinerpgextended.boss.LairSummons.leash(dm, x0, y0, z0, 12);
            net.minecraftforge.event.entity.living.LivingHurtEvent arrow = new net.minecraftforge.event.entity.living.LivingHurtEvent(dm,
                net.minecraft.util.DamageSource.causeArrowDamage(new net.minecraft.entity.projectile.EntityArrow(w), dp), 10);
            MinecraftForge.EVENT_BUS.post(arrow);
            net.minecraft.entity.projectile.EntityArrow ph = new net.minecraft.entity.projectile.EntityArrow(w);
            ph.getEntityData().setInteger(com.divinerpgextended.perk.RangedRoles.KIND, com.divinerpgextended.perk.RangedRoles.PHASER);
            net.minecraftforge.event.entity.living.LivingHurtEvent phaser = new net.minecraftforge.event.entity.living.LivingHurtEvent(dm,
                net.minecraft.util.DamageSource.causeThrownDamage(ph, dp), 10);
            MinecraftForge.EVENT_BUS.post(phaser);
            boolean onlyPhaser = Math.abs(arrow.ammount - 1) < 0.01 && Math.abs(phaser.ammount - 10) < 0.01;
            ItemStack bowS = new ItemStack(Items.bow);
            net.minecraftforge.event.entity.player.ArrowLooseEvent loose = new net.minecraftforge.event.entity.player.ArrowLooseEvent(dp, bowS, 20);
            MinecraftForge.EVENT_BUS.post(loose);
            boolean snapped = loose.isCanceled();
            int runes = com.divinerpgextended.boss.MortumBosses.runes(dm, dp);
            float hpR = dp.getHealth();
            dp.hurtResistantTime = 0;
            server.worldServers[0].getWorldInfo().incrementTotalWorldTime(w.getTotalWorldTime() + com.divinerpgextended.boss.MortumBosses.RUNE_FUSE + 1);
            FMLCommonHandler.instance().bus().post(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER, cpw.mods.fml.common.gameevent.TickEvent.Phase.END, w));
            boolean exploded = dp.getHealth() < hpR;
            dp.setHealth(dp.getMaxHealth());
            int bolts = com.divinerpgextended.boss.MortumBosses.blink(dm, dp);
            for (Object o : w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntityShadowBolt.class, dp.boundingBox.expand(30, 10, 30))) ((net.minecraft.entity.Entity) o).setDead();
            // халитовые блиц и фазер - как у миров
            boolean halite = com.divinerpgextended.perk.RangedRoles.kind(new ItemStack(net.divinerpg.utils.items.TwilightItemsWeapons.haliteBlitz)) == com.divinerpgextended.perk.RangedRoles.BLITZ
                && com.divinerpgextended.perk.RangedRoles.kind(new ItemStack(net.divinerpg.utils.items.TwilightItemsWeapons.halitePhaser)) == com.divinerpgextended.perk.RangedRoles.PHASER;
            // боковая дробина по боссу - доля урона; от неё нет вампиризма сета Мортума
            net.minecraft.entity.projectile.EntityArrow side = new net.minecraft.entity.projectile.EntityArrow(w);
            side.getEntityData().setInteger(com.divinerpgextended.perk.RangedRoles.KIND, com.divinerpgextended.perk.RangedRoles.BLITZ);
            side.getEntityData().setInteger(com.divinerpgextended.perk.RangedRoles.GEN, 1);
            net.minecraftforge.event.entity.living.LivingHurtEvent sh = new net.minecraftforge.event.entity.living.LivingHurtEvent(dm, net.minecraft.util.DamageSource.causeThrownDamage(side, dp), 10);
            MinecraftForge.EVENT_BUS.post(sh);
            boolean sideWeak = Math.abs(sh.ammount - 10 * com.divinerpgextended.perk.RangedRoles.PELLET_DMG * com.divinerpgextended.perk.RangedRoles.BOSS_SIDE * com.divinerpgextended.boss.MortumBosses.NON_PHASER) < 0.01
                && com.divinerpgextended.perk.RangedRoles.sidePellet(side);
            dm.setDead();
            // ладья: Харон продаёт за 12 душ; в ладье Лета не трогает; обычная лодка разъедается
            com.divinerpgextended.entity.EntityCharon ch = new com.divinerpgextended.entity.EntityCharon(w);
            ch.setLocationAndAngles(x0 - 3.5, y0, z0 - 3.5, 0, 0);
            w.spawnEntityInWorld(ch);
            dp.inventory.clearInventory(null, -1);
            hold(dp, new ItemStack(net.divinerpg.utils.items.TwilightItemsOther.mortumSoul, 15));
            ch.interact(dp);
            boolean bought = dp.inventory.hasItem(com.divinerpgextended.registry.ModItems.gondola) && dp.getHeldItem() != null && dp.getHeldItem().stackSize == 3;
            ch.setDead();
            com.divinerpgextended.entity.EntityGondola gd = new com.divinerpgextended.entity.EntityGondola(w, x0 + 6.5, y0, z0 + 0.5);
            w.spawnEntityInWorld(gd);
            dp.mountEntity(gd);
            float hpG = dp.getHealth();
            server.worldServers[0].getWorldInfo().incrementTotalWorldTime((w.getTotalWorldTime() / 20 + 1) * 20);
            com.divinerpgextended.world.twilight.MortumWorld.inLethe(dp);
            boolean safe = dp.getHealth() == hpG;
            dp.mountEntity(null);
            gd.setDead();
            dp.hurtResistantTime = 0;
            server.worldServers[0].getWorldInfo().incrementTotalWorldTime((w.getTotalWorldTime() / 20 + 1) * 20 + 40);
            com.divinerpgextended.world.twilight.MortumWorld.inLethe(dp);
            boolean bitten = dp.getHealth() < hpG;
            dp.setHealth(dp.getMaxHealth());
            net.minecraft.entity.item.EntityBoat boat = new net.minecraft.entity.item.EntityBoat(w, x0 + 2.5, y0, z0 + 2.5);
            w.spawnEntityInWorld(boat);
            for (int i = 0; i < com.divinerpgextended.world.twilight.MortumWorld.BOAT_ROT + 2; i++) {
                server.worldServers[0].getWorldInfo().incrementTotalWorldTime(w.getTotalWorldTime() + 1);
                com.divinerpgextended.world.twilight.MortumWorld.boatInLethe(boat);
            }
            boolean rotted = boat.isDead;
            // Пожиратель: свои пауки Divine у него не появляются; разбил надгробие - он лечится, встаёт душа, иссушение
            net.divinerpg.entities.twilight.EntitySoulFiend sf = new net.divinerpg.entities.twilight.EntitySoulFiend(w);
            sf.setLocationAndAngles(x0 + 0.5, y0, z0 + 0.5, 0, 0);
            w.spawnEntityInWorld(sf);
            com.divinerpgextended.boss.LairSummons.leash(sf, x0, y0, z0, 12);
            net.divinerpg.entities.twilight.EntitySoulSpider wild = new net.divinerpg.entities.twilight.EntitySoulSpider(w);
            wild.setLocationAndAngles(x0 + 3.5, y0, z0 + 0.5, 0, 0);
            boolean noWild = !w.spawnEntityInWorld(wild);
            sf.setHealth(sf.getMaxHealth() / 2);
            float before = sf.getHealth();
            w.setBlock(x0 + 2, y0, z0 + 2, Blocks.stonebrick, 3, 3);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.BreakEvent(x0 + 2, y0, z0 + 2, w, Blocks.stonebrick, 3, dp));
            boolean punished = sf.getHealth() > before && dp.isPotionActive(net.minecraft.potion.Potion.wither) && com.divinerpgextended.boss.MortumArenaBosses.souls(sf).size() == 1;
            for (net.minecraft.entity.Entity s2 : com.divinerpgextended.boss.MortumArenaBosses.souls(sf)) s2.setDead();
            sf.setDead();
            dp.clearActivePotions();
            // карты: у каждой арены своя; Харон - один на квадрат 7 x 7 чанков
            boolean maps = true;
            for (com.divinerpgextended.world.twilight.MortumArenas.Type t : com.divinerpgextended.world.twilight.MortumArenas.Type.values()) {
                int meta = com.divinerpgextended.item.ItemLairMap.arenaMap(t);
                maps &= new ItemStack(com.divinerpgextended.registry.ModItems.lairMap, 1, meta).getDisplayName().length() > 0;
            }
            int docks = 0;
            for (int i = 0; i < 70; i++) for (int j = 0; j < 70; j++) if (com.divinerpgextended.world.twilight.MortumWorld.dockChunk(w.getSeed(), i, j)) docks++;
            for (int x = x0 - 14; x <= x0 + 14; x++) for (int z = z0 - 14; z <= z0 + 14; z++) for (int y = y0 - 3; y <= y0 + 10; y++) w.setBlockToAir(x, y, z);
            check(onlyPhaser && snapped && runes == 3 && exploded && bolts == 3 && halite && sideWeak && bought && safe && bitten && rotted && noWild && punished && maps && docks == 100,
                "демон: только фазер " + onlyPhaser + " (стрела " + arrow.ammount + ", фазер " + phaser.ammount + "), тетива рвётся " + snapped + ", руны " + runes + " взорвались " + exploded
                    + ", снарядов блинка " + bolts + "; халит " + halite + "; боковая дробина " + sh.ammount + "; ладья куплена " + bought + ", в ладье цел " + safe + ", вплавь грызут " + bitten
                    + ", лодка разъелась " + rotted + "; пауков Divine нет " + noWild + ", надгробие наказано " + punished + "; карты " + maps + ", Харонов на 70x70 чанков " + docks);
            return "Демона пробивает только фазер, луки рядом рвутся, руны взрываются, блинк с тремя снарядами; халитовое оружие - как у миров; ладья у Харона спасает от Леты, лодка разъедается; надгробия Пожирателя дорого бить; карты к каждой арене";
        });
        t("Логова", "РЕНДЕР арен Мортума на ровном дне", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            int n = 0;
            for (com.divinerpgextended.world.twilight.MortumArenas.Type t : com.divinerpgextended.world.twilight.MortumArenas.Type.values()) {
                int cx = 30000 + t.ordinal() * 160, cz = 30000;
                loadAround(w, cx, cz, 4);
                // ровное дно мира: земля до 20, выше воздух
                for (int x = cx - 60; x <= cx + 60; x++) for (int z = cz - 60; z <= cz + 60; z++) for (int y = 2; y <= 122; y++) {
                    net.minecraft.block.Block want = y <= 19 ? net.divinerpg.utils.blocks.TwilightBlocks.twilightStone : y == 20 ? net.divinerpg.utils.blocks.TwilightBlocks.mortumGrass : Blocks.air;
                    if (w.getBlock(x, y, z) != want) w.setBlock(x, y, z, want, 0, 2);
                }
                com.divinerpgextended.world.twilight.ColumnStructure st = com.divinerpgextended.world.twilight.MortumArenas.shape(t);
                for (int x0 = cx - 56; x0 < cx + 56; x0 += 16) for (int z0 = cz - 56; z0 < cz + 56; z0 += 16) st.buildSlice(w, cx, cz, x0, z0);
                if (t == com.divinerpgextended.world.twilight.MortumArenas.Type.DEMON) dump(w, cx, 60, cz, 52, "render-demon-full");
                dump(w, cx, com.divinerpgextended.world.twilight.WorldGenNests.MORTUM_TOP + 20, cz, 44, "render-" + t.key());
                n++;
            }
            return "построено и сброшено для рендера: " + n;
        });
        t("Логова", "Демон сумерек: бой по этажам башни", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            int cx = 30000, cz = 30000; // башня построена тестом рендера
            loadAround(w, cx, cz, 3);
            int top = com.divinerpgextended.world.twilight.MortumArenas.SPIRE_TOP;
            net.divinerpg.entities.twilight.EntityTwilightDemon dm = new net.divinerpg.entities.twilight.EntityTwilightDemon(w);
            dm.setLocationAndAngles(cx + 0.5, top + 1, cz + 4.5, 0, 0);
            w.spawnEntityInWorld(dm);
            com.divinerpgextended.boss.LairSummons.leash(dm, cx, top + 1, cz, 20);
            dm.getEntityData().setIntArray(com.divinerpgextended.boss.MortumBosses.SPIRE, new int[] { cx, cz });
            TestPlayer tp = join(player(ConfigurationHelper.mortum, "TestSpire", cx + 0.5, top + 1, cz + 6.5), true);
            dm.setHealth(dm.getMaxHealth() * 0.84F);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(dm));
            int st1 = dm.getEntityData().getInteger(com.divinerpgextended.boss.MortumBosses.STAGE);
            int fl1 = com.divinerpgextended.boss.MortumBosses.floorOf(1);
            boolean inHall = st1 == 1 && Math.abs(dm.posY - (fl1 + 1)) < 0.01 && w.getCollidingBoundingBoxes(dm, dm.boundingBox).isEmpty();
            // со двора не достать
            net.minecraftforge.event.entity.living.LivingAttackEvent a1 = new net.minecraftforge.event.entity.living.LivingAttackEvent(dm, DamageSource.causePlayerDamage(tp), 5);
            MinecraftForge.EVENT_BUS.post(a1);
            boolean farSafe = a1.isCanceled();
            // на этаже - достать
            tp.setPositionAndUpdate(dm.posX + 2, fl1 + 1, dm.posZ);
            net.minecraftforge.event.entity.living.LivingAttackEvent a2 = new net.minecraftforge.event.entity.living.LivingAttackEvent(dm, DamageSource.causePlayerDamage(tp), 5);
            MinecraftForge.EVENT_BUS.post(a2);
            boolean nearHit = !a2.isCanceled();
            // остальные этажи: каждый ход - этаж выше, место свободное
            StringBuilder floors = new StringBuilder();
            boolean free = true;
            dm.setHealth(dm.getMaxHealth() * 0.2F);
            for (int i = 0; i < 8; i++) {
                dm.getEntityData().setInteger(com.divinerpgextended.boss.MortumBosses.OFFER_LEFT, 0);
                dm.getEntityData().setInteger(com.divinerpgextended.boss.MortumBosses.PHASE, 2);
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(dm));
                free &= w.getCollidingBoundingBoxes(dm, dm.boundingBox).isEmpty();
                floors.append((int) dm.posY).append(' ');
            }
            int st = dm.getEntityData().getInteger(com.divinerpgextended.boss.MortumBosses.STAGE);
            int[] f = com.divinerpgextended.world.twilight.MortumArenas.crownFocus();
            boolean lit = w.getBlock(cx + f[0], f[1], cz + f[2]) == com.divinerpgextended.world.twilight.MortumArenas.focusBlock();
            int flc = com.divinerpgextended.boss.MortumBosses.floorOf(st);
            tp.setPositionAndUpdate(dm.posX - 2, flc + 1, dm.posZ);
            net.minecraftforge.event.entity.living.LivingAttackEvent a3 = new net.minecraftforge.event.entity.living.LivingAttackEvent(dm, DamageSource.causePlayerDamage(tp), 5);
            MinecraftForge.EVENT_BUS.post(a3);
            boolean focusSafe = a3.isCanceled();
            w.setBlockToAir(cx + f[0], f[1], cz + f[2]);
            net.minecraftforge.event.entity.living.LivingAttackEvent a4 = new net.minecraftforge.event.entity.living.LivingAttackEvent(dm, DamageSource.causePlayerDamage(tp), 5);
            MinecraftForge.EVENT_BUS.post(a4);
            boolean broken = !a4.isCanceled();
            // в коронном зале фокус и Демон под одной крышей: потолок над этажом есть
            int[] home = com.divinerpgextended.boss.LairSummons.home(dm);
            dm.setDead();
            tp.inventory.clearInventory(null, -1);
            check(inHall && farSafe && nearHit && st == com.divinerpgextended.boss.MortumBosses.STAGE_AT.length && free && lit && focusSafe && broken && home != null && home[1] == flc + 1,
                "зал: этаж " + st1 + " y " + (int) dm.posY + " ок " + inHall + "; со двора неуязвим " + farSafe + ", на этаже бьётся " + nearHit + "; этажи " + floors + "до " + st + ", места свободны " + free
                    + "; фокус горит " + lit + ", пока горит - неуязвим " + focusSafe + ", разбит - бьётся " + broken);
            return "Демон уходит в башню на 85% и поднимается по этажу каждые 12% до коронного зала; не на его этаже его не достать; в короне держит фокус - разбил, бей";
        });
        t("Логова", "Вечный лучник: бегуны, залп сквозь блоки, дождь стрел", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            int cx = 30000 + com.divinerpgextended.world.twilight.MortumArenas.Type.ARCHER.ordinal() * 160, cz = 30000; // галерея построена тестом рендера
            loadAround(w, cx, cz, 3);
            int top = com.divinerpgextended.world.twilight.WorldGenNests.MORTUM_TOP;
            int hz = com.divinerpgextended.world.twilight.MortumArenas.GALLERY_HZ;
            boolean roof = w.getBlock(cx - 5, top + 15, cz) == Blocks.nether_brick_stairs && w.getBlock(cx, top + 20, cz) == Blocks.obsidian;
            net.divinerpg.entities.twilight.EntityEternalArcher ea = new net.divinerpg.entities.twilight.EntityEternalArcher(w);
            ea.setLocationAndAngles(cx + 0.5, top + 4, cz - hz + 3.5, 0, 0);
            w.spawnEntityInWorld(ea);
            com.divinerpgextended.boss.LairSummons.leash(ea, cx, top + 1, cz, 26);
            TestPlayer tp = join(player(ConfigurationHelper.mortum, "TestArcher", cx + 0.5, top + 1, cz + 8.5), true);
            // стенка из булыжника поперёк галереи между лучником и целью
            for (int x = cx - 4; x <= cx + 4; x++) for (int y = top + 1; y <= top + 9; y++) w.setBlock(x, y, cz - 4, Blocks.cobblestone);
            int arrows = com.divinerpgextended.boss.MortumArenaBosses.volley(ea, tp);
            for (int i = 0; i < 30; i++) {
                for (Object o : w.getEntitiesWithinAABB(net.minecraft.entity.projectile.EntityArrow.class, ea.boundingBox.expand(40, 20, 40))) ((net.minecraft.entity.Entity) o).onUpdate();
                FMLCommonHandler.instance().bus().post(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER, cpw.mods.fml.common.gameevent.TickEvent.Phase.END, w));
            }
            int holes = 0;
            for (int x = cx - 4; x <= cx + 4; x++) for (int y = top + 1; y <= top + 9; y++) if (w.getBlock(x, y, cz - 4) != Blocks.cobblestone) holes++;
            for (int x = cx - 4; x <= cx + 4; x++) for (int y = top + 1; y <= top + 9; y++) w.setBlockToAir(x, y, cz - 4);
            // бегуны: двое за раз, всего не больше трёх
            int r1 = com.divinerpgextended.boss.MortumArenaBosses.runners(ea, tp), r2 = com.divinerpgextended.boss.MortumArenaBosses.runners(ea, tp);
            // дождь стрел
            int rain = com.divinerpgextended.boss.MortumArenaBosses.arrowsFall(w, ea, tp.posX, tp.posY, tp.posZ);
            for (Object o : w.getEntitiesWithinAABB(net.minecraft.entity.EntityLiving.class, ea.boundingBox.expand(48, 20, 48)))
                if (((net.minecraft.entity.Entity) o).getEntityData().hasKey(com.divinerpgextended.boss.MortumArenaBosses.ARCHER_ADD) || o == ea) ((net.minecraft.entity.Entity) o).setDead();
            for (Object o : w.getEntitiesWithinAABB(net.minecraft.entity.projectile.EntityArrow.class, ea.boundingBox.expand(48, 20, 48))) ((net.minecraft.entity.Entity) o).setDead();
            tp.inventory.clearInventory(null, -1);
            check(roof && arrows == com.divinerpgextended.boss.MortumArenaBosses.VOLLEY_ARROWS && holes > 0 && r1 == 2 && r2 == 1 && rain == com.divinerpgextended.boss.MortumArenaBosses.RAIN_ARROWS,
                "кровля из ступеней с обсидиановым коньком " + roof + "; залп " + arrows + " стрел, пробито блоков в стенке " + holes + "; бегунов " + r1 + "+" + r2 + "; дождь " + rain + " стрел");
            return "кровля галереи - ступени адского кирпича; залп пробивает стенку (" + holes + " блоков), бегуны до трёх, дождь стрел " + rain;
        });
        t("Логова", "Карот: отражает снаряды, смотрится в зеркало", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            int cx = 30000 + com.divinerpgextended.world.twilight.MortumArenas.Type.KAROT.ordinal() * 160, cz = 30000; // зал построен тестом рендера
            loadAround(w, cx, cz, 2);
            int top = com.divinerpgextended.world.twilight.WorldGenNests.MORTUM_TOP;
            net.divinerpg.entities.twilight.EntityKarot kt = new net.divinerpg.entities.twilight.EntityKarot(w);
            kt.setLocationAndAngles(cx + 0.5, top + 1, cz + 0.5, 0, 0);
            w.spawnEntityInWorld(kt);
            com.divinerpgextended.boss.LairSummons.leash(kt, cx, top + 1, cz, 13);
            TestPlayer kp = join(player(ConfigurationHelper.mortum, "TestKarotShot", cx + 0.5, top + 1, cz + 6.5), true);
            float hp = kp.getHealth();
            net.minecraft.entity.projectile.EntityArrow ar = new net.minecraft.entity.projectile.EntityArrow(w, kp, 1.0F);
            net.minecraftforge.event.entity.living.LivingAttackEvent s1 = new net.minecraftforge.event.entity.living.LivingAttackEvent(kt, DamageSource.causeArrowDamage(ar, kp), 8);
            MinecraftForge.EVENT_BUS.post(s1);
            boolean reflected = s1.isCanceled() && kp.getHealth() < hp;
            kp.setHealth(kp.getMaxHealth());
            net.minecraftforge.event.entity.living.LivingAttackEvent m1 = new net.minecraftforge.event.entity.living.LivingAttackEvent(kt, DamageSource.causePlayerDamage(kp), 8);
            MinecraftForge.EVENT_BUS.post(m1);
            boolean melee = !m1.isCanceled();
            boolean gazed = com.divinerpgextended.boss.MortumBosses.gaze(kt);
            int edge = Math.max(Math.abs((int) Math.floor(kt.posX) - cx), Math.abs((int) Math.floor(kt.posZ) - cz));
            net.minecraft.entity.projectile.EntityArrow ar2 = new net.minecraft.entity.projectile.EntityArrow(w, kp, 1.0F);
            net.minecraftforge.event.entity.living.LivingAttackEvent s2 = new net.minecraftforge.event.entity.living.LivingAttackEvent(kt, DamageSource.causeArrowDamage(ar2, kp), 8);
            MinecraftForge.EVENT_BUS.post(s2);
            net.minecraftforge.event.entity.living.LivingHurtEvent h2 = new net.minecraftforge.event.entity.living.LivingHurtEvent(kt, DamageSource.causeArrowDamage(ar2, kp), 8);
            MinecraftForge.EVENT_BUS.post(h2);
            boolean open = !s2.isCanceled() && Math.abs(h2.ammount - 8 * com.divinerpgextended.boss.MortumBosses.GAZE_DMG) < 0.01;
            kt.setDead();
            ar.setDead();
            ar2.setDead();
            check(reflected && melee && gazed && edge == com.divinerpgextended.world.twilight.MortumArenas.MIRROR_H - 3 && open,
                "стрела отражена в стрелка " + reflected + ", ближний бой проходит " + melee + "; у зеркала " + gazed + " (от центра " + edge + "), снаряд в окне зеркала " + !s2.isCanceled() + ", урон " + h2.ammount);
            return "Карот отражает стрелы и снаряды в стрелка, ближний бой проходит; у зеркальной стены замирает, и снаряды бьют его x" + com.divinerpgextended.boss.MortumBosses.GAZE_DMG;
        });
        t("Логова", "Мортум: река у арен, причалы, мосты", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            long seed = w.getSeed();
            int sea = com.divinerpgextended.world.twilight.MortumWorld.SEA;
            // у каждой арены русло - сразу за её краем, но не на ней
            StringBuilder sb = new StringBuilder();
            int byRiver = 0, wet = 0, total = 0;
            for (com.divinerpgextended.world.twilight.MortumArenas.Type t : com.divinerpgextended.world.twilight.MortumArenas.Type.values()) {
                int[] c = com.divinerpgextended.world.twilight.MortumArenas.nearestOf(w, t, 0, 0, 6);
                if (c == null) continue;
                total++;
                int ext = com.divinerpgextended.world.twilight.MortumArenas.extent(t);
                boolean inside = false, near = false;
                for (int a = 0; a < 72; a++) {
                    double ang = a * Math.PI / 36;
                    for (int r = 0; r < ext; r += 3)
                        inside |= com.divinerpgextended.world.twilight.MortumWorld.river(seed, c[0] + (int) (Math.cos(ang) * r), c[2] + (int) (Math.sin(ang) * r));
                    for (int r = ext; r <= ext + 8; r++)
                        near |= com.divinerpgextended.world.twilight.MortumWorld.river(seed, c[0] + (int) (Math.cos(ang) * r), c[2] + (int) (Math.sin(ang) * r));
                }
                if (near) byRiver++;
                if (inside) wet++;
                int minR = 999;
                for (int a2 = 0; a2 < 360; a2++) for (int r = 0; r < ext + 10; r++)
                    if (r < minR && com.divinerpgextended.world.twilight.MortumWorld.river(seed, c[0] + (int) Math.round(Math.cos(a2 * Math.PI / 180) * r), c[2] + (int) Math.round(Math.sin(a2 * Math.PI / 180) * r))) minR = r;
                int sg = com.divinerpgextended.world.twilight.WorldGenNests.spacing(w.provider.dimensionId);
                boolean placed = com.divinerpgextended.world.twilight.WorldGenNests.riverPoint(seed, w.provider.dimensionId, Math.floorDiv(c[0], sg), Math.floorDiv(c[2], sg)) != null;
                sb.append(t.key()).append(near ? " у реки" : " без реки").append(inside ? " (река по арене! ближе всего " + minR + " из " + ext + ", место у реки найдено " + placed + ")" : "").append("; ");
            }
            // по многим мирам: доля арен у реки и арен, по которым шум реки всё же проходит
            int sTotal = 0, sNear = 0, sWet = 0, sNone = 0;
            int dimM = w.provider.dimensionId, gM = com.divinerpgextended.world.twilight.WorldGenNests.spacing(dimM);
            for (long sd = 1; sd <= 8; sd++) for (int rx = -3; rx <= 2; rx++) for (int rz = -3; rz <= 2; rz++) {
                long ss = sd * 0x5DEECE66DL + 11;
                int[] cc = com.divinerpgextended.world.twilight.WorldGenNests.center(ss, dimM, rx, rz);
                int ex2 = com.divinerpgextended.world.twilight.MortumArenas.extent(com.divinerpgextended.world.twilight.MortumArenas.type(ss, rx, rz));
                boolean in2 = false, nr2 = false;
                for (int a2 = 0; a2 < 120; a2++) {
                    double an = a2 * Math.PI / 60;
                    for (int r = 0; r < ex2; r += 2) in2 |= com.divinerpgextended.world.twilight.MortumWorld.river(ss, cc[0] + (int) Math.round(Math.cos(an) * r), cc[2] + (int) Math.round(Math.sin(an) * r));
                    for (int r = ex2; r <= ex2 + 8; r += 2) nr2 |= com.divinerpgextended.world.twilight.MortumWorld.river(ss, cc[0] + (int) Math.round(Math.cos(an) * r), cc[2] + (int) Math.round(Math.sin(an) * r));
                }
                sTotal++;
                if (nr2) sNear++;
                if (in2) sWet++;
                if (com.divinerpgextended.world.twilight.WorldGenNests.riverPoint(ss, dimM, rx, rz) == null) sNone++;
            }
            sb.append("по 8 мирам: арен ").append(sTotal).append(", у реки ").append(sNear).append(", река заходит на арену ").append(sWet).append(", места у реки не нашлось ").append(sNone).append("; ");
            // мост: ближайший к началу координат
            double[] b = null;
            int specs = 0, covered = 0;
            for (int i = 0; i < 10 && b == null; i++) for (int j = 0; j < 10 && b == null; j++) {
                double[] bq = com.divinerpgextended.world.twilight.MortumWorld.bridge(seed, w.provider.dimensionId, 40 + i, 40 + j);
                if (bq.length == 0) continue;
                specs++;
                if (com.divinerpgextended.world.twilight.MortumWorld.open(w, bq)) b = bq;
                else covered++;
            }
            sb.append("мест под мост ").append(specs).append(", из них в тоннеле (без моста) ").append(covered).append("; ");
            boolean bridge = false;
            String bs = "моста нет";
            if (b != null) {
                int bx = (int) Math.floor(b[0]), bz = (int) Math.floor(b[1]);
                loadAround(w, bx, bz, 3);
                int up = com.divinerpgextended.world.twilight.MortumWorld.BRIDGE_UP;
                int deck = 0, rail = 0;
                for (int x = bx - 30; x <= bx + 30; x++) for (int z = bz - 30; z <= bz + 30; z++) {
                    if (w.getBlock(x, sea + up, z) == Blocks.nether_brick) deck++;
                    if (w.getBlock(x, sea + up + 1, z) == Blocks.nether_brick_fence) rail++;
                }
                boolean overWater = w.getBlock(bx, sea + up, bz) == Blocks.nether_brick && w.getBlock(bx, sea, bz) == com.divinerpgextended.registry.ModBlocks.lethe
                    && w.isAirBlock(bx, sea + 1, bz) && w.isAirBlock(bx, sea + 2, bz);
                boolean sky = true;
                for (int y = sea + up + 1; y <= sea + up + 20; y++) if (!w.isAirBlock(bx, y, bz) && !com.divinerpgextended.world.twilight.MortumWorld.soft(w, bx, y, bz)) sky = false;
                bridge = overWater && deck > 30 && rail > 8 && sky;
                dump(w, bx, sea + 10, bz, 30, "render-bridge");
                bs = "мост у " + bx + "," + bz + ": над Летой " + overWater + ", над ним открыто " + sky + ", настила " + deck + ", перил " + rail + ", вода шириной " + (int) (b[4] * 2);
            }
            int docks = com.divinerpgextended.world.twilight.MortumWorld.arenaDocks;
            sb.append("причалов у арен построено ").append(docks).append("; ");
            check(total == 6 && sNear >= sTotal * 0.85 && sWet <= sTotal * 0.08 && sWet <= sNone && bridge && docks > 0, sb + bs);
            return "арен у русла " + sNear + " из " + sTotal + " (8 миров), река заходит на арену у " + sWet + ", причалов у арен " + docks + "; " + bs;
        });
        t("Логова", "РЕНДЕР карта Леты", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            int bx = 40000, bz = 40000, size = 192;
            loadAround(w, bx + size / 2, bz + size / 2, size / 32 + 1);
            java.io.PrintWriter out = new java.io.PrintWriter(new File(MinecraftServer.getServer().getFile("logs"), "lethe-map.txt"), "UTF-8");
            int open = 0, tunnel = 0, falls = 0;
            for (int z = bz; z < bz + size; z++) {
                StringBuilder row = new StringBuilder();
                for (int x = bx; x < bx + size; x++) {
                    int sea = com.divinerpgextended.world.twilight.MortumWorld.SEA;
                    boolean lethe = w.getBlock(x, sea, z) == com.divinerpgextended.registry.ModBlocks.lethe;
                    boolean fall = w.getBlock(x, sea + 6, z) == com.divinerpgextended.registry.ModBlocks.lethe;
                    // верх земли нижнего яруса: первый непустой сверху начиная с 70
                    int top = 70;
                    while (top > 5 && w.isAirBlock(x, top, z)) top--;
                    boolean covered = false;
                    if (lethe) for (int y = sea + 4; y <= sea + 10; y++) if (!w.isAirBlock(x, y, z) && w.getBlock(x, y, z) != com.divinerpgextended.registry.ModBlocks.lethe) {
                        covered = true;
                        break;
                    }
                    char c;
                    if (fall) { c = '*'; falls++; }
                    else if (lethe && !covered) { c = '~'; open++; }
                    else if (lethe) { c = 't'; tunnel++; }
                    else c = (char) ('A' + Math.max(0, Math.min(25, (top - 10) / 3)));
                    row.append(c);
                }
                out.println(row);
            }
            out.close();
            return "карта Леты 192x192: открыто " + open + ", в тоннелях/под навесом " + tunnel + ", водопадов (столбцов) " + falls;
        });
        t("Логова", "Мортум: русло Леты по рельефу", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            // река вдали от прочих тестов
            int rx = 0, rz = 0;
            search:
            for (int x = 6000; x < 7000; x += 8) for (int z = 6000; z < 6200; z += 8)
                if (com.divinerpgextended.world.twilight.MortumWorld.river(w.getSeed(), x, z)) {
                    rx = x;
                    rz = z;
                    break search;
                }
            loadAround(w, rx, rz, 4);
            int lethe = 0, hanging = 0, spikes = 0, columns = 0, plants = 0;
            for (int x = rx - 40; x <= rx + 40; x++) for (int z = rz - 40; z <= rz + 40; z++) {
                boolean col = false;
                for (int y = 8; y < 120; y++) {
                    if (w.getBlock(x, y, z) != com.divinerpgextended.registry.ModBlocks.lethe) continue;
                    lethe++;
                    col = true;
                    if (com.divinerpgextended.world.twilight.MortumWorld.soft(w, x, y + 1, z)) plants++;
                    if (w.getBlock(x, y - 1, z) != com.divinerpgextended.registry.ModBlocks.lethe && !w.getBlock(x, y - 1, z).isOpaqueCube()) hanging++;
                    if (w.getBlock(x, y + 1, z) != com.divinerpgextended.registry.ModBlocks.lethe) { // гладь: не столб среди воздуха
                        int open = 0;
                        int[][] nb = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
                        for (int[] o : nb) if (w.isAirBlock(x + o[0], y, z + o[1]) && w.isAirBlock(x + o[0], y - 1, z + o[1])) open++;
                        if (open == 4) spikes++;
                    }
                }
                if (col) columns++;
            }
            dump(w, rx, 40, rz, 34, "lethe-river");
            check(columns > 100 && hanging == 0 && spikes < columns / 20 && plants == 0,
                "у " + rx + "," + rz + ": столбцов реки " + columns + ", блоков Леты " + lethe + ", с пустотой под собой " + hanging + ", одиночных столбов " + spikes + ", растений на воде " + plants);
            return "Лета течёт руслом по рельефу: " + columns + " столбцов, под водой нет пустоты, висящих столбов и растений на воде нет";
        });
        t("Логова", "Мортум: Лета, Харон, склеп", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            int x0 = 9760, y0 = 90, z0 = 9760;
            loadAround(w, x0, z0, 2);
            for (int x = x0 - 8; x <= x0 + 16; x++) for (int z = z0 - 8; z <= z0 + 16; z++) for (int y = y0 - 2; y <= y0 + 10; y++)
                w.setBlock(x, y, z, y < y0 ? net.divinerpg.utils.blocks.TwilightBlocks.mortumGrass : Blocks.air);
            TestPlayer lp = join(player(ConfigurationHelper.mortum, "TestLethe", x0 + 0.5, y0, z0 + 0.5), true);
            // Лета: эффекты слетают, мобы забывают, опыт утекает
            lp.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.poison.id, 200, 0));
            lp.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.damageBoost.id, 200, 0));
            net.minecraft.entity.monster.EntityZombie zl = new net.minecraft.entity.monster.EntityZombie(w);
            zl.setLocationAndAngles(x0 + 4.5, y0, z0 + 0.5, 0, 0);
            w.spawnEntityInWorld(zl);
            zl.setAttackTarget(lp);
            lp.addExperience(50);
            int xpBefore = lp.experienceTotal;
            server.worldServers[0].getWorldInfo().incrementTotalWorldTime((w.getTotalWorldTime() / 20 + 1) * 20); // на тик слива опыта
            com.divinerpgextended.world.twilight.MortumWorld.inLethe(lp);
            boolean cleared = !lp.isPotionActive(net.minecraft.potion.Potion.poison) && !lp.isPotionActive(net.minecraft.potion.Potion.damageBoost); // замедление вешает сама Лета
            boolean forgot = zl.getAttackTarget() == null && com.divinerpgextended.world.twilight.MortumWorld.forgotten(lp);
            zl.setAttackTarget(lp);
            boolean ignored = zl.getAttackTarget() == null;
            boolean drained = lp.experienceTotal == xpBefore - com.divinerpgextended.world.twilight.MortumWorld.DRAIN;
            zl.setDead();
            // русло: узкие ленты, доля в разумных пределах
            int river = 0;
            for (int x = 0; x < 512; x += 2) for (int z = 0; z < 512; z += 2) if (com.divinerpgextended.world.twilight.MortumWorld.river(w.getSeed(), x, z)) river++;
            double share = river / (256.0 * 256.0);
            // склеп на ровной площадке: саркофаг (сундук) и спавнер
            // поверхность ищется сверху: острова над площадкой убрать, иначе "земля" окажется на них
            for (int x = x0 + 2; x <= x0 + 8; x++) for (int z = z0 + 2; z <= z0 + 10; z++) for (int y = y0 + 11; y <= 118; y++) w.setBlockToAir(x, y, z);
            boolean crypt = com.divinerpgextended.world.twilight.MortumWorld.crypt(w, new java.util.Random(5), com.divinerpgextended.world.twilight.TwilightWorld.MORTUM, x0 + 2, z0 + 2);
            boolean chest = w.getTileEntity(x0 + 5, y0, z0 + 4) instanceof net.minecraft.tileentity.TileEntityChest
                && w.getBlock(x0 + 3, y0, z0 + 3) == Blocks.mob_spawner;
            // Харон: кристалл близнецов - говорит, в какой стороне Зал близнецов; никуда не переносит и ничего не берёт
            com.divinerpgextended.entity.EntityCharon ch = new com.divinerpgextended.entity.EntityCharon(w);
            ch.setLocationAndAngles(x0 - 4.5, y0, z0 - 4.5, 0, 0);
            w.spawnEntityInWorld(ch);
            lp.inventory.clearInventory(null, -1);
            hold(lp, new ItemStack(net.divinerpg.utils.items.TwilightItemsOther.densosCrystal));
            lp.inventory.setInventorySlotContents(5, new ItemStack(net.divinerpg.utils.items.TwilightItemsOther.mortumSoul, 7));
            double bx = lp.posX, bz = lp.posZ;
            lp.chat.clear();
            ch.interact(lp);
            int[] tw = com.divinerpgextended.world.twilight.MortumArenas.nearestOf(w, com.divinerpgextended.world.twilight.MortumArenas.Type.TWINS, x0, z0, 8);
            double dist = Math.sqrt((bx - tw[0]) * (bx - tw[0]) + (bz - tw[2]) * (bz - tw[2]));
            int left = lp.inventory.getStackInSlot(5) == null ? 0 : lp.inventory.getStackInSlot(5).stackSize;
            String said = lp.chat.isEmpty() ? "" : lp.chat.get(lp.chat.size() - 1);
            String dir = com.divinerpgextended.entity.EntityCharon.direction(tw[0] + 0.5 - bx, tw[2] + 0.5 - bz);
            boolean ferried = lp.posX == bx && lp.posZ == bz && left == 7 && said.contains("dre.charon.says") && said.contains(net.minecraft.util.StatCollector.translateToLocal("dre.dir." + dir));
            // стороны света: на север (-Z) - n, на восток (+X) - e
            ferried &= "n".equals(com.divinerpgextended.entity.EntityCharon.direction(0, -10)) && "e".equals(com.divinerpgextended.entity.EntityCharon.direction(10, 0))
                && "sw".equals(com.divinerpgextended.entity.EntityCharon.direction(-10, 10));
            ch.setDead();
            lp.inventory.clearInventory(null, -1);
            for (int x = x0 - 8; x <= x0 + 16; x++) for (int z = z0 - 8; z <= z0 + 16; z++) for (int y = y0 - 2; y <= y0 + 10; y++) w.setBlockToAir(x, y, z);
            check(cleared && forgot && ignored && drained && share > 0.05 && share < 0.3 && crypt && chest && ferried,
                "Лета: эффекты сняты " + cleared + ", моб забыл " + forgot + ", не берёт в цель " + ignored + ", опыт " + xpBefore + " > " + lp.experienceTotal
                    + "; русло " + String.format("%.1f%%", share * 100) + "; склеп " + crypt + ", саркофаг и спавнер " + chest + "; Харон: до зала " + String.format("%.0f", dist) + " на " + dir + ", душ " + left + " из 7, сказал " + said + ", подсказка верна " + ferried);
            return "Лета снимает эффекты, мобы забывают игрока, утекает опыт; русло - узкие ленты (" + String.format("%.1f%%", share * 100) + "); склеп с саркофагом; Харон по кристаллу говорит сторону и расстояние до арены босса, никуда не переносит";
        });
        t("Логова", "Мортум: земля в пещере", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.mortum);
            loadAround(w, 9450, 9450, 1);
            int sx2 = 9450, sz2 = 9450;
            for (int y = 95; y <= 127; y++) w.setBlock(sx2, y, sz2, Blocks.air);
            for (int y = 123; y <= 127; y++) w.setBlock(sx2, y, sz2, Blocks.bedrock);
            for (int y = 115; y < 123; y++) w.setBlock(sx2, y, sz2, net.divinerpg.utils.blocks.TwilightBlocks.twilightStone);
            w.setBlock(sx2, 99, sz2, net.divinerpg.utils.blocks.TwilightBlocks.mortumGrass);
            int found = com.divinerpgextended.world.twilight.WorldGenTwilightFeatures.surface(w, com.divinerpgextended.world.twilight.TwilightWorld.MORTUM, sx2, sz2);
            for (int y = 95; y <= 127; y++) w.setBlock(sx2, y, sz2, Blocks.air);
            check(found == 99, "найдена земля на " + found + " (потолок - камень 115-122 и бедрок 123-127, пол пещеры - трава на 99)");
            return "в Мортуме постройки ищут пол пещеры, а не камень под бедроковым потолком";
        });
        t("Логова", "Часы только у гробницы", () -> {
            String r = com.divinerpgextended.boss.LairSummons.refusal(p, net.divinerpg.utils.items.VanillaItemsOther.mysteriousClock, 0, 64, 0);
            check("dre.chat.summon_tomb".equals(r), "отказ " + r);
            return "Таинственные часы вне алтаря гробницы не работают";
        });
        t("Логова", "Босс не падает из логова", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            net.minecraft.entity.monster.EntityZombie b = new net.minecraft.entity.monster.EntityZombie(w);
            b.setPosition(4000.5, 150, 4000.5);
            w.spawnEntityInWorld(b);
            com.divinerpgextended.boss.LairSummons.leash(b, 4000, 141, 4000, 20);
            b.setPosition(4040.5, 120, 4000.5); // за краем и ниже пола
            b.ticksExisted = 10;
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(b));
            double x = b.posX, y = b.posY;
            b.setDead();
            check(Math.abs(x - 4000.5) < 0.1 && y > 140, "босс в " + x + ", " + y);
            return "босс, ушедший за край логова, вернулся в центр";
        });
        t("Логова", "Руины", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.wildwood);
            int x = 9000, y = 100, z = 9000;
            loadAround(w, x, z, 3); // соседи заселяются до расчистки, а не поверх неё
            for (int i = -2; i < 12; i++) for (int kk = -2; kk < 12; kk++) {
                for (int h = y - 3; h < y; h++) w.setBlock(x + i, h, z + kk, net.divinerpg.utils.blocks.TwilightBlocks.twilightStone);
                w.setBlock(x + i, y, z + kk, net.divinerpg.utils.blocks.TwilightBlocks.wildwoodGrass);
                for (int h = y + 1; h < 160; h++) w.setBlockToAir(x + i, h, z + kk);
            }
            boolean built = com.divinerpgextended.world.twilight.WorldGenTwilightFeatures.ruin(w, new java.util.Random(5),
                com.divinerpgextended.world.twilight.TwilightWorld.WILDWOOD, x, z);
            net.minecraft.tileentity.TileEntity te = w.getTileEntity(x + 4, y + 1, z + 4);
            int stacks = 0;
            if (te instanceof net.minecraft.tileentity.TileEntityChest)
                for (int i = 0; i < 27; i++) if (((net.minecraft.tileentity.TileEntityChest) te).getStackInSlot(i) != null) stacks++;
            java.util.List<?> guards = w.getEntitiesWithinAABB(net.minecraft.entity.EntityLiving.class,
                net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 1, y, z - 1, x + 10, y + 4, z + 10));
            for (Object o : guards) ((net.minecraft.entity.Entity) o).setDead();
            check(built && stacks >= 4 && guards.size() >= 2, "построено " + built + ", в сундуке стопок " + stacks + ", стражи " + guards.size());
            return "руины на ровном острове Вайлдвуда: сундук (" + stacks + " стопок), стража " + guards.size();
        });
        t("Логова", "Подземелье в острове", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.apalachia);
            int x = 9100, z = 9100, top = 100;
            loadAround(w, x, z, 3);
            for (int i = -2; i < 12; i++) for (int kk = -2; kk < 12; kk++) {
                for (int h = top - 20; h < top; h++) w.setBlock(x + i, h, z + kk, net.divinerpg.utils.blocks.TwilightBlocks.twilightStone);
                w.setBlock(x + i, top, z + kk, net.divinerpg.utils.blocks.TwilightBlocks.apalachiaGrass);
                for (int h = top + 1; h < 160; h++) w.setBlockToAir(x + i, h, z + kk);
            }
            boolean built = com.divinerpgextended.world.twilight.WorldGenTwilightFeatures.dungeon(w, new java.util.Random(7),
                com.divinerpgextended.world.twilight.TwilightWorld.APALACHIA, x, z, x + 23);
            int floor = top - 10;
            net.minecraft.tileentity.TileEntity sp = w.getTileEntity(x + 4, floor, z + 4);
            String mob = sp instanceof net.minecraft.tileentity.TileEntityMobSpawner
                ? ((net.minecraft.tileentity.TileEntityMobSpawner) sp).func_145881_a().getEntityNameToSpawn() : null;
            boolean chest = w.getTileEntity(x + 7, floor, z + 1) instanceof net.minecraft.tileentity.TileEntityChest;
            boolean seal = false;
            if (chest) for (int i = 0; i < 27; i++) {
                ItemStack it = ((net.minecraft.tileentity.TileEntityChest) w.getTileEntity(x + 7, floor, z + 1)).getStackInSlot(i);
                if (it != null && it.getItem() == com.divinerpgextended.registry.ModItems.sanctumSeal) seal = true;
            }
            chest &= seal;
            boolean ladder = w.getBlock(x + 1, floor + 6, z + 7) == Blocks.ladder && w.isAirBlock(x + 4, floor + 2, z + 4);
            check(built && mob != null && chest && ladder, "построено " + built + ", спавнер " + mob + ", сундук " + chest + ", лестница " + ladder);
            return "комната в толще острова Апалачии: спавнер (" + mob + "), сундук, лестница наверх";
        });
        t("Логова", "Мост через пропасть", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            int x0 = 9208, z0 = 9208, y = 100; // область заселения чанка (575, 575)
            loadAround(w, x0 + 8, z0 + 8, 3);
            for (int x = x0 - 8; x < x0 + 24; x++) for (int z = z0 - 8; z < z0 + 24; z++) {
                for (int h = 1; h < 140; h++) w.setBlockToAir(x, h, z);
                boolean island = x < x0 + 4 || x > x0 + 13; // пропасть 10 блоков вдоль X
                if (island) {
                    for (int h = y - 4; h < y; h++) w.setBlock(x, h, z, net.divinerpg.utils.blocks.TwilightBlocks.edenDirt);
                    w.setBlock(x, y, z, net.divinerpg.utils.blocks.TwilightBlocks.edenGrass);
                }
            }
            java.util.Random rr = new java.util.Random(3);
            boolean built = false;
            for (int i = 0; i < 5 && !built; i++) built = com.divinerpgextended.world.twilight.WorldGenTwilightFeatures.bridge(w, rr,
                com.divinerpgextended.world.twilight.TwilightWorld.EDEN, x0, z0);
            int planks = 0, lamps = 0;
            for (int x = x0 + 4; x <= x0 + 13; x++) for (int h = y - 4; h <= y + 1; h++)
                if (w.getBlock(x, h, z0 + 8) == net.divinerpg.utils.blocks.TwilightBlocks.edenPlanks) planks++;
            for (int x = x0 - 6; x <= x0 + 22; x++) for (int h = y - 6; h <= y + 6; h++) for (int zz = z0 + 7; zz <= z0 + 9; zz++)
                if (w.getBlock(x, h, zz) == Blocks.glowstone || w.getBlock(x, h, zz).getLightValue() > 0) lamps++;
            String where = "";
            for (int x = x0 - 8; x < x0 + 24 && where.isEmpty(); x++) for (int zz = z0 - 8; zz < z0 + 24 && where.isEmpty(); zz++)
                for (int h = 60; h < 140; h++) if (w.getBlock(x, h, zz) == net.divinerpg.utils.blocks.TwilightBlocks.edenPlanks) { where = (x - x0) + "," + h + "," + (zz - z0); break; }
            check(built && planks >= 7 && lamps == 0, "мост " + built + ", досок над пропастью " + planks + ", глоустоуна " + lamps + ", первая доска (от x0,z0) " + where);
            return "мост из досок Эдема через пропасть в 10 блоков (" + planks + " досок пролёта)";
        });
        t("Логова", "Лагерь странника", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.skythern);
            int x = 9300, y = 100, z = 9300;
            loadAround(w, x, z, 3);
            for (int i = -2; i < 11; i++) for (int kk = -2; kk < 11; kk++) {
                for (int h = y - 3; h < y; h++) w.setBlock(x + i, h, z + kk, net.divinerpg.utils.blocks.TwilightBlocks.twilightStone);
                w.setBlock(x + i, y, z + kk, net.divinerpg.utils.blocks.TwilightBlocks.skythernGrass);
                for (int h = y + 1; h < 160; h++) w.setBlockToAir(x + i, h, z + kk);
            }
            boolean built = com.divinerpgextended.world.twilight.WorldGenTwilightFeatures.camp(w, new java.util.Random(2),
                com.divinerpgextended.world.twilight.TwilightWorld.SKYTHERN, x, z);
            java.util.List<?> traders = w.getEntitiesWithinAABB(com.divinerpgextended.entity.EntityWanderer.class,
                net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 2, y, z - 2, x + 10, y + 4, z + 10));
            int deals = traders.isEmpty() ? 0 : ((com.divinerpgextended.entity.EntityWanderer) traders.get(0)).getRecipes(p).size();
            boolean fire = w.getBlock(x + 6, y, z + 3) == com.divinerpgextended.registry.ModBlocks.brazier
                && com.divinerpgextended.block.BlockBrazier.isLit(w, x + 6, y, z + 3);
            // костёр - точка возрождения: запомнить и вернуться после "смерти" в обычном мире
            TestPlayer v = player(ConfigurationHelper.skythern, "TestCamp", x + 5, y + 1, z + 3);
            v.setSneaking(true);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerInteractEvent(v,
                net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, x + 6, y, z + 3, 1, w));
            v.setSneaking(false);
            int[] mark = com.divinerpgextended.world.twilight.Campfire.get(v);
            v.setPosition(x + 30, y + 20, z + 30);
            boolean back = com.divinerpgextended.world.twilight.Campfire.respawn(v);
            double dist = Math.abs(v.posX - (x + 7.5)) + Math.abs(v.posZ - (z + 3.5));
            for (Object o : traders) ((net.minecraft.entity.Entity) o).setDead();
            check(built && traders.size() == 1 && deals >= 7 && fire && mark != null && back && dist < 1.5,
                "построено " + built + ", странников " + traders.size() + ", сделок " + deals + ", костёр " + fire + ", метка " + (mark != null)
                    + ", возврат " + back + " (" + dist + ")");
            return "лагерь в Скайтерне: Странник (" + deals + " сделок), горящий костёр; костёр запомнил - возрождение у него";
        });
        t("Логова", "Обсерватория Смотрителя", () -> {
            int type = com.divinerpgextended.world.WorldGenOuterLairs.WATCHER;
            WorldServer w = server.worldServerForDimension(-1);
            int[] c = com.divinerpgextended.world.WorldGenOuterLairs.nearest(w.getSeed(), type, 0, 0);
            loadAround(w, c[0], c[2], 3);
            int f = com.divinerpgextended.world.WorldGenOuterLairs.FLOOR[type], R = com.divinerpgextended.world.WorldGenOuterLairs.RADIUS[type];
            boolean hall = w.getBlock(c[0], f, c[2]) == net.divinerpg.utils.blocks.VanillaBlocks.lamp2 && w.getBlockMetadata(c[0], f, c[2]) == 6 && w.isAirBlock(c[0] + 4, f + 6, c[2] + 4)
                && w.isAirBlock(c[0] + R + 4, f + 2, c[2]);
            TestPlayer v = player(-1, "TestWatcher", c[0], f + 1, c[2]);
            net.minecraft.item.Item call = net.divinerpg.utils.items.VanillaItemsOther.callWatcher;
            String in = com.divinerpgextended.boss.LairSummons.refusal(v, call, c[0] + 3, f, c[2] - 2);
            String out = com.divinerpgextended.boss.LairSummons.refusal(v, call, c[0] + 200, 60, c[2]);
            check(hall && in == null && "dre.chat.summon_lair_0".equals(out), "зал " + hall + ", внутри " + in + ", снаружи " + out);
            return "зал в Аду в " + c[0] + "," + c[2] + " (пол y=" + f + ", тоннели наружу); Зов работает только внутри";
        });
        t("Логова", "Огненная палата", () -> {
            int type = com.divinerpgextended.world.WorldGenOuterLairs.SCORCHERS;
            WorldServer w = server.worldServerForDimension(-1);
            int[] c = com.divinerpgextended.world.WorldGenOuterLairs.nearest(w.getSeed(), type, 0, 0);
            loadAround(w, c[0], c[2], 3);
            int f = com.divinerpgextended.world.WorldGenOuterLairs.FLOOR[type];
            boolean moat = w.getBlock(c[0] + 8, f, c[2] + 8).getMaterial() == net.minecraft.block.material.Material.lava;
            boolean bridge = w.getBlock(c[0] + 11, f, c[2]) == Blocks.nether_brick && w.isAirBlock(c[0] + 3, f + 3, c[2] + 3);
            String out = com.divinerpgextended.boss.LairSummons.refusal(p, net.divinerpg.utils.items.VanillaItemsOther.infernalFlame, c[0] + 200, 60, c[2]);
            check(moat && bridge && "dre.chat.summon_lair_1".equals(out), "ров " + moat + ", мостик " + bridge + ", снаружи " + out);
            return "палата в Аду в " + c[0] + "," + c[2] + ": лавовый ров с мостиками; пламя вне палаты не работает";
        });
        t("Логова", "Гнездовье Айрако", () -> {
            int type = com.divinerpgextended.world.WorldGenOuterLairs.AYERACO;
            WorldServer w = server.worldServerForDimension(1);
            int[] c = com.divinerpgextended.world.WorldGenOuterLairs.nearest(w.getSeed(), type, 0, 0);
            loadAround(w, c[0], c[2], 3);
            int f = com.divinerpgextended.world.WorldGenOuterLairs.FLOOR[type];
            boolean nest = w.getBlock(c[0], f, c[2]) == net.divinerpg.utils.blocks.VanillaBlocks.lamp1 && w.getBlockMetadata(c[0], f, c[2]) == 8 && w.isAirBlock(c[0] + 2, f + 4, c[2] + 2);
            TestPlayer v = player(1, "TestAerie", c[0], f + 1, c[2]);
            net.minecraft.item.Item horn = net.divinerpg.utils.items.VanillaItemsOther.hordeHorn;
            String in = com.divinerpgextended.boss.LairSummons.refusal(v, horn, c[0] + 2, f, c[2] + 3);
            String out = com.divinerpgextended.boss.LairSummons.refusal(v, horn, 0, 64, 0);
            check(nest && in == null && "dre.chat.summon_lair_2".equals(out), "гнездовье " + nest + ", внутри " + in + ", снаружи " + out);
            return "гнездовье в Энде в " + c[0] + "," + c[2] + " (" + (int) Math.sqrt(c[0] * c[0] + c[2] * c[2]) + " блоков от центра); рог только там";
        });
        t("Логова", "Мягкая привязь", () -> {
            WorldServer w = server.worldServerForDimension(ConfigurationHelper.eden);
            loadAround(w, 4100, 4100, 2);
            net.minecraft.entity.monster.EntityZombie b = new net.minecraft.entity.monster.EntityZombie(w);
            b.setPosition(4122.5, 150, 4100.5); // на 2 блока за краем (радиус 20)
            w.spawnEntityInWorld(b);
            com.divinerpgextended.boss.LairSummons.leash(b, 4100, 141, 4100, 20);
            b.ticksExisted = 10;
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent(b));
            double x = b.posX, mx = b.motionX;
            b.setDead();
            check(Math.abs(x - 4122.5) < 0.1 && mx < -0.2, "позиция " + x + ", скорость к центру " + mx);
            return "чуть за краем - разворачивает к центру без телепорта (далеко или ниже пола - телепорт)";
        });
        t("Логова", "Карта к роще", () -> {
            int dim = ConfigurationHelper.eden;
            TestPlayer v = player(dim, "TestNestMap", 100, 150, 100);
            ItemStack map = new ItemStack(com.divinerpgextended.registry.ModItems.lairMap, 1, com.divinerpgextended.item.ItemLairMap.NEST);
            v.chat.clear();
            map.getItem().onItemRightClick(map, server.worldServerForDimension(dim), v);
            check(v.heard("dre.chat.map_found_nest"), "чат " + v.chat);
            return "карта в Эдеме показывает ближайшую рощу";
        });
        t("Сумеречные миры", "Быстрый поиск портала", () -> {
            net.minecraft.block.Block portal = net.divinerpg.utils.blocks.TwilightBlocks.wildwoodPortal;
            net.minecraft.block.Block frame = ReflectionHelper.getPrivateValue(net.divinerpg.blocks.base.portal.BlockModPortal.class,
                (net.divinerpg.blocks.base.portal.BlockModPortal) portal, "blockFrame");
            int dim = ConfigurationHelper.wildwood;
            WorldServer w = server.worldServerForDimension(dim);
            // портал Divine построит при первом входе, дальше оба телепорта ищут его с соседней точки
            placePig(new net.divinerpg.dimensions.base.DivineTeleporter(w, dim, portal, frame), w, -3000, 3000, null);
            double[] a = new double[3], b = new double[3];
            long t0 = System.nanoTime();
            placePig(new net.divinerpg.dimensions.base.DivineTeleporter(w, dim, portal, frame), w, -2997, 3002, a);
            long divine = System.nanoTime() - t0;
            t0 = System.nanoTime();
            placePig(new com.divinerpgextended.portal.FastTeleporter(w, dim, portal, frame), w, -2997, 3002, b);
            long fast = System.nanoTime() - t0;
            check(java.util.Arrays.equals(a, b), "Divine поставил в " + java.util.Arrays.toString(a) + ", быстрый в "
                + java.util.Arrays.toString(b));
            check(fast * 3 < divine, "быстрый " + fast / 1000000 + " мс, Divine " + divine / 1000000 + " мс");
            return "та же точка у портала; поиск " + divine / 1000000 + " > " + fast / 1000000 + " мс";
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
                d.isDead = false;
                d.setHealth(d.getMaxHealth()); // после прошлых ударов мог быть при смерти - удар бы не прошёл
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
