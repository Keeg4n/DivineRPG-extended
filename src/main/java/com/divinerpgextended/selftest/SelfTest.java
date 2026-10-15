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

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || done || ++ticks < 40) return;
        done = true;
        try {
            runAll();
        } catch (Throwable t) {
            results.add(new String[] { "-", "-", "СБОЙ", "самопроверка упала: " + t });
        }
        write();
        MinecraftServer.getServer().initiateShutdown();
    }

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
            check(!ConfigurationHelper.vetheaInventory, "отдельный инвентарь Divine включён");
            return "отдельный инвентарь Divine выключен аддоном";
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
            vw.getChunkFromBlockCoords(c[0], c[1]);
            call(VetheaFloors.class, "carve", new Class<?>[] { net.minecraft.world.World.class, int.class, int.class, int.class }, vw, c[0], c[1], 2);
            boolean air = vw.isAirBlock(c[0], 50, c[1]) && vw.isAirBlock(c[0], 57, c[1]) && vw.isAirBlock(c[0], 64, c[1]);
            int rim = 0;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) if (vw.getBlock(c[0] + dx, 64, c[1] + dz) == VetheaBlocks.fireCrystal) rim++;
            int at = VetheaFloors.riftAt(vw, c[0] + 0.5, 55, c[1] + 0.5, 6.25, 8, 1);
            check(air && rim > 0 && at == 2, "дыра сквозная " + air + ", огненных кристаллов на краю " + rim + ", riftAt " + at);
            return "шахта 49-64 пустая, кристаллов по краю " + rim;
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
