package com.divinerpgextended.selftest;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.divinerpg.utils.blocks.VetheaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Полное покрытие Ветеи: каждая чужая вещь сборки (оружие, инструменты, вещи с ПКМ, баублы, броня) в комнате Ветеи - до
 * прохождения (кошмар действует) и после победы над всеми боссами Ветеи. Что меряем: урон мобу Ветеи, сломанные и
 * поставленные блоки, сдвиг игрока (телепорты, рывки), призванных существ, новые эффекты, полёт, атрибуты, лечение.
 * До прохождения чужое не должно работать (лечение моба - задуманный "наоборот"); всё, что сработало, - утечка.
 * Плюс площадные инструменты (связанная кирка Blood Magic и подобные) в привате обычного мира: ломать там нельзя ничего.
 * <p>
 * Игрок настоящий (не FakePlayer): Blood Magic и другие моды молчат для фальшивых игроков. Вещам выдаются ресурсы: LP
 * Blood Magic, вис палочкам Таумкрафта (фокусы - на палочке), творческий планшет маны Ботании, стрелы; инструменты
 * Tinkers собираются из железа. Не входит в обычный прогон: только фильтром "покрытие Ветеи". Отчёт - logs/dre-vethea-cover.tsv.
 */
final class VetheaCoverage {

    static boolean enabled(String[] only) {
        for (String o : only) if (o.contains("покрытие")) return true;
        return false;
    }

    /** Настоящий игрок для проверки: ничего не пишет в чат, не умирает и не уходит в другие миры. */
    static class RealPlayer extends EntityPlayerMP {

        RealPlayer(MinecraftServer s, WorldServer w, GameProfile gp) {
            super(s, w, gp, new ItemInWorldManager(w));
        }

        @Override
        public void addChatMessage(net.minecraft.util.IChatComponent c) {}

        @Override
        public void addChatComponentMessage(net.minecraft.util.IChatComponent c) {}

        @Override
        public boolean canAttackPlayer(EntityPlayer p) {
            return true;
        }

        int deaths;

        @Override
        public void onDeath(DamageSource s) {
            ForgeHooks.onLivingDeath(this, s);
            deaths++;
            setHealth(getMaxHealth());
        }

        @Override
        public void travelToDimension(int d) {}

        @Override
        public void addStat(net.minecraft.stats.StatBase s, int n) {}
    }

    /** Кто ранил моба за время замера: тип урона, источник и сколько (для разбора утечек урона). */
    public static final class HurtLog {

        static Entity target;
        static final StringBuilder log = new StringBuilder();

        @cpw.mods.fml.common.eventhandler.SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
        public void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
            if (e.entityLiving != target || e.isCanceled()) return;
            DamageSource s = e.source;
            if (log.length() > 0) log.append(", ");
            log.append(s.damageType).append(' ').append(String.format("%.1f", e.ammount));
            if (s.getEntity() != null) log.append(" от ").append(s.getEntity().getClass().getSimpleName());
            if (s.getSourceOfDamage() != null && s.getSourceOfDamage() != s.getEntity()) log.append(" через ").append(s.getSourceOfDamage().getClass().getSimpleName());
            // кто нанёс: кадры за attackEntityFrom (сам вызов урона - в мобе и его предках)
            StackTraceElement[] st = new Throwable().getStackTrace();
            int k = 0;
            while (k < st.length && !st[k].getMethodName().equals("func_70097_a") && !st[k].getMethodName().equals("attackEntityFrom")) k++;
            while (k < st.length && (st[k].getMethodName().equals("func_70097_a") || st[k].getMethodName().equals("attackEntityFrom"))) k++;
            StringBuilder who = new StringBuilder();
            for (int n = 0; n < 3 && k < st.length; n++, k++) {
                String c = st[k].getClassName();
                who.append(n == 0 ? " [" : " < ").append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(st[k].getMethodName()).append(':').append(st[k].getLineNumber());
            }
            if (who.length() > 0) log.append(who).append(']');
        }
    }

    /** Что изменилось после действия. */
    static final class Fx {

        int blocks, summons, shots;
        /** Игрок погиб (полное здоровье после - не лечение). */
        boolean died;
        /** Мобы самой Ветеи, появившиеся сами (энты при рубке деревьев сна): не призыв вещи. */
        String dream = "";
        float mob, heal, hurt = Float.NaN;
        double moved;
        boolean fly, attrs;
        /** В какой мир унесло игрока (MIN_VALUE - остался в Ветее). */
        int world = Integer.MIN_VALUE;
        String attrWhy = "", hurtBy = "", who = "";
        List<String> effects = new ArrayList<String>();
        String error;

        /** Сработало ли что-то (лечение моба - не в счёт: это и есть "наоборот"). */
        boolean any() {
            return blocks > 0 || other();
        }

        /** Сработало что-то, кроме изменения блоков. */
        boolean other() {
            return mob < -0.01F || moved > 1.5 || summons > 0 || !effects.isEmpty() || fly || attrs || heal > 0.5F && !died || world != Integer.MIN_VALUE;
        }

        @Override
        public String toString() {
            if (error != null) return "сбой: " + error;
            StringBuilder b = new StringBuilder();
            if (world != Integer.MIN_VALUE) b.append("уносит в мир ").append(world).append(' ');
            if (blocks > 0) b.append("блоков ").append(blocks).append(' ');
            if (Math.abs(mob) > 0.01F) b.append(mob < 0 ? "урон " : "лечит моба ").append(String.format("%.1f", Math.abs(mob))).append(mob < 0 && !hurtBy.isEmpty() ? " (" + hurtBy + ")" : "").append(' ');
            if (moved > 1.5) b.append("сдвиг ").append(String.format("%.1f", moved)).append(' ');
            if (summons > 0) b.append("существ ").append(summons).append(" (").append(who).append(") ");
            if (shots > 0) b.append("снарядов ").append(shots).append(' ');
            if (!effects.isEmpty()) b.append("эффекты ").append(effects).append(' ');
            if (fly) b.append("полёт ");
            if (attrs) b.append("атрибуты").append(attrWhy).append(' ');
            if (died) b.append("игрок погиб ");
            else if (heal > 0.5F) b.append("лечит игрока ").append(String.format("%.1f", heal)).append(' ');
            if (!dream.isEmpty()) b.append("сами пришли: ").append(dream).append(' ');
            if (!Float.isNaN(hurt)) b.append("удар 6 -> ").append(String.format("%.1f", hurt)).append(' ');
            return b.length() == 0 ? "-" : b.toString().trim();
        }
    }

    private final MinecraftServer server = MinecraftServer.getServer();
    private final WorldServer w;
    private final int x0, y0, z0;
    private static final int R = 8, H = 8;
    private final Map<Long, int[]> template = new HashMap<Long, int[]>();
    private RealPlayer p;
    private EntityLiving mob;
    final List<String> leaks = new ArrayList<String>();
    int items, okCount, neutral, crashes, hung;

    // отчёт пишется по ходу: при обрыве прогона сделанное остаётся; ход - в logs/dre-vethea-progress.txt
    private java.io.Writer out;
    private java.io.PrintWriter prog;
    private final long t0 = System.currentTimeMillis();
    /** Сторож: вещь думает дольше HANG_DUMP - стек в журнал хода, дольше HANG_KILL - действие прерывается. */
    private static final long HANG_DUMP = 10000, HANG_KILL = 30000;
    private volatile Thread main;
    private volatile String current;
    private volatile long actStart;
    private volatile boolean guarded, finished;
    private final List<String> slow = new ArrayList<String>();

    private void emit(String s) {
        try {
            out.write(s);
            out.flush();
        } catch (Exception ignored) {}
    }

    private void log(String s) {
        prog.println(String.format("[%6.1f мин] ", (System.currentTimeMillis() - t0) / 60000.0) + s);
        prog.flush();
    }

    private void startWatchdog() {
        Thread wd = new Thread(() -> {
            String dumped = null;
            while (!finished) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                String c = current;
                long dt = System.currentTimeMillis() - actStart;
                if (!guarded || c == null) continue;
                if (dt > HANG_DUMP && !c.equals(dumped)) {
                    dumped = c;
                    StringBuilder b = new StringBuilder("думает ").append(dt / 1000).append(" с: ").append(c);
                    for (StackTraceElement e : main.getStackTrace()) b.append("\n    at ").append(e);
                    log(b.toString());
                }
                if (dt > HANG_KILL && guarded) {
                    log("прерываю: " + c);
                    stopMain();
                    actStart = System.currentTimeMillis(); // следующий удар - не раньше чем через HANG_KILL
                }
            }
        }, "dre-vethea-watchdog");
        wd.setDaemon(true);
        wd.start();
    }

    @SuppressWarnings("deprecation")
    private void stopMain() {
        main.stop(); // ThreadDeath в потоке сервера ловит measure; зависшая вещь получает пометку "завис"
    }

    VetheaCoverage() {
        w = server.worldServerForDimension(ConfigurationHelper.vethea);
        x0 = 6000;
        y0 = 30; // первый этаж: выше без прохождения не пускает страж этажей (сбросил бы игрока - ложный "сдвиг")
        z0 = 6000;
    }

    // ---------------------------------------------------------------- комната

    private static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 28) | ((long) (y & 0xFF) << 20) | (z & 0xFFFFF);
    }

    private void loadChunks() {
        for (int cx = (x0 - R - 16) >> 4; cx <= (x0 + R + 16) >> 4; cx++)
            for (int cz = (z0 - R - 16) >> 4; cz <= (z0 + R + 16) >> 4; cz++) w.theChunkProviderServer.loadChunk(cx, cz);
    }

    /** Комната из камня снов: пол, стены, потолок; внутри - цели: блок перед игроком, столбик, дерево снов, земля снов. */
    private void build() {
        loadChunks();
        for (int x = x0 - R; x <= x0 + R; x++) for (int y = y0 - 1; y <= y0 + H; y++) for (int z = z0 - R; z <= z0 + R; z++) {
            boolean shell = x == x0 - R || x == x0 + R || z == z0 - R || z == z0 + R || y == y0 - 1 || y == y0 + H;
            w.setBlock(x, y, z, shell ? VetheaBlocks.dreamStone : Blocks.air, 0, 2);
        }
        w.setBlock(x0 + 1, y0, z0 + 1, VetheaBlocks.dreamStone, 0, 2);
        for (int y = y0; y < y0 + 4; y++) w.setBlock(x0 - 2, y, z0 + 2, VetheaBlocks.dreamStone, 0, 2);
        for (int y = y0; y < y0 + 4; y++) w.setBlock(x0 + 3, y, z0 + 3, VetheaBlocks.dreamWoodLog, 0, 2);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.setBlock(x0 + 3 + dx, y0 + 4, z0 + 3 + dz, VetheaBlocks.dreamWoodLeaves, 0, 2);
        for (int dx = -1; dx <= 1; dx++) for (int dz = 4; dz <= 5; dz++) w.setBlock(x0 + dx, y0 - 1, z0 + dz, dx == 0 ? VetheaBlocks.dreamGrass : VetheaBlocks.dreamDirt, 0, 2);
        template.clear();
        for (int x = x0 - R; x <= x0 + R; x++) for (int y = y0 - 1; y <= y0 + H; y++) for (int z = z0 - R; z <= z0 + R; z++)
            template.put(key(x, y, z), new int[] { Block.getIdFromBlock(w.getBlock(x, y, z)), w.getBlockMetadata(x, y, z), x, y, z });
    }

    /** Сколько блоков комнаты отличается от заготовки; restore - вернуть их. */
    private int diff(boolean restore) {
        int n = 0;
        for (int[] t : template.values()) {
            Block b = w.getBlock(t[2], t[3], t[4]);
            if (Block.getIdFromBlock(b) == t[0] && w.getBlockMetadata(t[2], t[3], t[4]) == t[1]) continue;
            n++;
            if (restore) {
                w.removeTileEntity(t[2], t[3], t[4]);
                w.setBlock(t[2], t[3], t[4], Block.getBlockById(t[0]), t[1], 2);
            }
        }
        return n;
    }

    private static Method onRemoved;

    /**
     * Убрать мёртвые сущности из мира сразу. Проверка идёт внутри одного тика сервера, а мир вычищает мёртвых только на
     * следующем: без уборки за сотни вещей копятся тысячи "убитых" мобов и снарядов, и молния, бьющая всех вокруг, с
     * поиском приспешников на каждую смерть, думает квадратично долго.
     */
    static void purge(WorldServer w) {
        List<Entity> dead = new ArrayList<Entity>();
        for (Object o : w.loadedEntityList) {
            Entity e = (Entity) o;
            if (e.isDead && !(e instanceof EntityPlayer)) dead.add(e);
        }
        if (dead.isEmpty()) return;
        try {
            if (onRemoved == null) onRemoved = ReflectionHelper.findMethod(net.minecraft.world.World.class, null, new String[] { "onEntityRemoved", "func_72847_b" }, Entity.class);
        } catch (Throwable ignored) {}
        Set<Entity> gone = new HashSet<Entity>(dead);
        w.loadedEntityList.removeAll(gone);
        for (Entity e : dead) {
            if (e.addedToChunk && w.getChunkProvider().chunkExists(e.chunkCoordX, e.chunkCoordZ)) w.getChunkFromChunkCoords(e.chunkCoordX, e.chunkCoordZ).removeEntity(e);
            try {
                if (onRemoved != null) onRemoved.invoke(w, e);
            } catch (Throwable ignored) {}
        }
    }

    private AxisAlignedBB box() {
        return AxisAlignedBB.getBoundingBox(x0 - R - 4, y0 - 4, z0 - R - 4, x0 + R + 5, y0 + H + 5, z0 + R + 5);
    }

    // ---------------------------------------------------------------- игрок и моб

    private RealPlayer makePlayer() {
        RealPlayer rp = new RealPlayer(server, w, new GameProfile(UUID.nameUUIDFromBytes("dre-vethea-cover".getBytes(StandardCharsets.UTF_8)), "TestDreamCover"));
        NetworkManager nm = new NetworkManager(false);
        ReflectionHelper.setPrivateValue(NetworkManager.class, nm, new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter()), "channel", "field_150746_k");
        new NetHandlerPlayServer(server, nm, rp);
        ReflectionHelper.setPrivateValue(EntityPlayerMP.class, rp, 0, "field_147101_bU");
        rp.setLocationAndAngles(x0 + 0.5, y0, z0 + 0.5, 0, 0);
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> list = server.getConfigurationManager().playerEntityList;
        list.add(rp);
        w.spawnEntityInWorld(rp);
        return rp;
    }

    private void dropPlayer() {
        if (p == null) return;
        server.getConfigurationManager().playerEntityList.remove(p);
        ((WorldServer) p.worldObj).getPlayerManager().removePlayer(p);
        p.worldObj.removeEntity(p);
    }

    /** Ступень прохождения: false - кошмар действует, true - все боссы Ветеи побеждены. */
    private void progress(boolean done) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).setInteger("dre_dream_done", done ? 0x7FFFFFFF : 0);
    }

    /** Вещь унесла игрока в другой мир (кристалл телепортации и подобные мимо travelToDimension): вернуть в Ветею новым. */
    private void bringBack() {
        if (p.worldObj == w && p.dimension == w.provider.dimensionId) return;
        log("игрок унесён в мир " + p.dimension + " (" + current + ") - возвращаю в Ветею");
        WorldServer was = (WorldServer) p.worldObj;
        server.getConfigurationManager().playerEntityList.remove(p);
        was.getPlayerManager().removePlayer(p);
        was.removeEntity(p);
        boolean done = p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getInteger("dre_dream_done") != 0;
        p = makePlayer();
        progress(done);
    }

    private void resetPlayer() {
        bringBack();
        p.closeScreen();
        if (p.isUsingItem()) p.clearItemInUse();
        for (int i = 0; i < p.inventory.mainInventory.length; i++) p.inventory.mainInventory[i] = null;
        for (int i = 0; i < 4; i++) p.inventory.armorInventory[i] = null;
        IInventoryHolder.clearBaubles(p);
        p.clearActivePotions();
        p.capabilities.allowFlying = false;
        p.capabilities.isFlying = false;
        p.capabilities.isCreativeMode = false;
        p.extinguish();
        p.fallDistance = 0;
        p.motionX = p.motionY = p.motionZ = 0;
        if (p.ridingEntity != null) p.mountEntity(null);
        p.setPositionAndUpdate(x0 + 0.5, y0, z0 + 0.5);
        p.rotationYaw = p.rotationYawHead = 0;
        p.rotationPitch = 0;
        p.setHealth(10);
        p.getFoodStats().addStats(20, 20F);
        p.hurtResistantTime = 0;
        p.inventory.currentItem = 0;
        provision();
        // тик: прибавки к атрибутам от вещей прошлого замера снимаются только тиком (иначе ложное "атрибуты" потом)
        tickPlayer(1);
        p.setPositionAndUpdate(x0 + 0.5, y0, z0 + 0.5);
        p.setHealth(10);
        p.hurtResistantTime = 0;
    }

    private void respawnMob() {
        if (mob != null) mob.setDead();
        mob = new net.divinerpg.entities.vethea.EntityCymesoid(w);
        mob.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(1000);
        mob.setHealth(500);
        mob.setLocationAndAngles(x0 + 0.5, y0, z0 + 3.5, 180, 0);
        w.spawnEntityInWorld(mob);
    }

    private void tickPlayer(int n) {
        for (int i = 0; i < n; i++) {
            p.onUpdate();
            p.onUpdateEntity();
        }
    }

    // ---------------------------------------------------------------- ресурсы модов

    /** LP, планшет маны, стрелы - в инвентарь каждый раз (вещь в руке - слот 0). */
    private void provision() {
        try {
            Class<?> sn = Class.forName("WayofTime.alchemicalWizardry.api.soulNetwork.SoulNetworkHandler");
            sn.getMethod("setMaxOrbToMax", String.class, int.class).invoke(null, p.getCommandSenderName(), 6);
            sn.getMethod("setCurrentEssence", String.class, int.class).invoke(null, p.getCommandSenderName(), 10000000);
        } catch (Throwable ignored) {}
        try {
            Class<?> mi = Class.forName("vazkii.botania.common.item.ModItems");
            Item tablet = (Item) mi.getField("manaTablet").get(null);
            ItemStack t = new ItemStack(tablet);
            Class.forName("vazkii.botania.common.item.ItemManaTablet").getMethod("setStackCreative", ItemStack.class).invoke(null, t);
            p.inventory.mainInventory[8] = t;
        } catch (Throwable ignored) {}
        p.inventory.mainInventory[7] = new ItemStack(Items.arrow, 64);
    }

    /** Подготовить вещь: привязка и включение Blood Magic, вис палочке, фокус - на палочку. null - не умеем сделать. */
    private ItemStack prepare(ItemStack s) {
        String cn = s.getItem().getClass().getName();
        try {
            if (cn.startsWith("WayofTime")) {
                Class.forName("WayofTime.alchemicalWizardry.common.items.EnergyItems").getMethod("checkAndSetItemOwner", ItemStack.class, EntityPlayer.class).invoke(null, s, p);
                for (Method m : s.getItem().getClass().getMethods())
                    if (m.getName().equals("setActivated") && m.getParameterTypes().length == 2) m.invoke(s.getItem(), s, true);
            }
            Class<?> focus = classOrNull("thaumcraft.api.wands.ItemFocusBasic"), wand = classOrNull("thaumcraft.common.items.wands.ItemWandCasting");
            if (focus != null && focus.isInstance(s.getItem())) {
                Item wi = (Item) Item.itemRegistry.getObject("Thaumcraft:WandCasting");
                if (wi == null) return s;
                ItemStack ws = new ItemStack(wi);
                wand.getMethod("setFocus", ItemStack.class, ItemStack.class).invoke(wi, ws, s);
                charge(ws);
                return ws;
            }
            if (wand != null && wand.isInstance(s.getItem())) charge(s);
        } catch (Throwable ignored) {}
        return s;
    }

    private void charge(ItemStack wandStack) throws Exception {
        Class<?> asp = Class.forName("thaumcraft.api.aspects.Aspect"), al = Class.forName("thaumcraft.api.aspects.AspectList");
        Object list = al.newInstance();
        for (Object a : (List<?>) asp.getMethod("getPrimalAspects").invoke(null)) al.getMethod("add", asp, int.class).invoke(list, a, 100000);
        wandStack.getItem().getClass().getMethod("storeAllVis", ItemStack.class, al).invoke(wandStack.getItem(), wandStack, list);
    }

    private static Class<?> classOrNull(String n) {
        try {
            return Class.forName(n);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Инструмент Tinkers из железа (без частей он пустой). */
    private ItemStack tinkers(Item i) {
        try {
            Class<?> tc = Class.forName("tconstruct.library.tools.ToolCore");
            if (!tc.isInstance(i)) return null;
            List<ItemStack> out = new ArrayList<ItemStack>();
            tc.getMethod("buildTool", int.class, String.class, List.class).invoke(i, 2, "Cover", out);
            return out.isEmpty() ? null : out.get(0);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Баублы: Baubles читаем рефлексией (в сборке аддона его нет). */
    static final class IInventoryHolder {

        static net.minecraft.inventory.IInventory baubles(EntityPlayer p) {
            try {
                // настоящий инвентарь (BaublesApi во сне отдаёт вид без чужих аксессуаров)
                return (net.minecraft.inventory.IInventory) Class.forName("baubles.common.lib.PlayerHandler").getMethod("getPlayerBaubles", EntityPlayer.class).invoke(null, p);
            } catch (Throwable t) {
                return null;
            }
        }

        static boolean isBauble(Item i) {
            Class<?> b = classOrNull("baubles.api.IBauble");
            return b != null && b.isInstance(i);
        }

        static void clearBaubles(EntityPlayer p) {
            net.minecraft.inventory.IInventory inv = baubles(p);
            if (inv == null) return;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s != null) call(s, "onUnequipped", p);
                inv.setInventorySlotContents(i, null);
            }
        }

        static boolean wear(EntityPlayer p, ItemStack s) {
            net.minecraft.inventory.IInventory inv = baubles(p);
            if (inv == null) return false;
            int slot = 0;
            try {
                Object type = s.getItem().getClass().getMethod("getBaubleType", ItemStack.class).invoke(s.getItem(), s);
                String n = String.valueOf(type);
                slot = n.equals("RING") ? 1 : n.equals("BELT") ? 3 : 0;
            } catch (Throwable ignored) {}
            inv.setInventorySlotContents(slot, s);
            call(s, "onEquipped", p);
            return true;
        }

        private static void call(ItemStack s, String m, EntityPlayer p) {
            try {
                s.getItem().getClass().getMethod(m, ItemStack.class, EntityLivingBase.class).invoke(s.getItem(), s, p);
            } catch (Throwable ignored) {}
        }
    }

    // ---------------------------------------------------------------- действия

    private interface Act {

        void run() throws Exception;
    }

    private Fx measure(ItemStack stack, boolean bauble, boolean armor, Act act) {
        Fx fx = new Fx();
        resetPlayer();
        respawnMob();
        float mob0 = mob.getHealth(), hp0 = p.getHealth();
        int deaths0 = p.deaths;
        double px = p.posX, py = p.posY, pz = p.posZ;
        Set<Integer> fx0 = new HashSet<Integer>();
        for (Object o : p.getActivePotionEffects()) fx0.add(((PotionEffect) o).getPotionID());
        double speed0 = p.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue(), max0 = p.getMaxHealth(),
            dmg0 = p.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue();
        Set<Entity> before = new HashSet<Entity>();
        for (Object o : w.getEntitiesWithinAABB(Entity.class, box())) before.add((Entity) o);
        HurtLog.target = mob;
        HurtLog.log.setLength(0);
        actStart = System.currentTimeMillis();
        guarded = true;
        try {
            try {
                ItemStack held = stack == null ? null : prepare(stack.copy());
                if (held != null && !bauble && !armor) p.inventory.mainInventory[0] = held;
                if (bauble) IInventoryHolder.wear(p, held);
                if (armor) p.inventory.armorInventory[3 - ((ItemArmor) held.getItem()).armorType] = held;
                act.run();
            } catch (Throwable t) {
                fx.error = t instanceof ThreadDeath ? "завис" : t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
                if (t.getCause() != null) fx.error += " <- " + t.getCause();
            }
            // новые сущности: снаряды и призывы - даём им 40 тиков
            List<Entity> fresh = new ArrayList<Entity>();
            for (Object o : w.getEntitiesWithinAABB(Entity.class, box())) {
                Entity e = (Entity) o;
                if (!before.contains(e) && e != p && e != mob && !(e instanceof EntityItem) && !(e instanceof EntityXPOrb)) fresh.add(e);
            }
            for (Entity e : fresh) {
                if (e instanceof EntityLiving && e.getClass().getName().startsWith("net.divinerpg.entities.vethea.")) {
                    if (!fx.dream.contains(e.getClass().getSimpleName())) fx.dream += (fx.dream.isEmpty() ? "" : " ") + e.getClass().getSimpleName();
                } else if (e instanceof EntityLiving) {
                    fx.summons++;
                    if (!fx.who.contains(e.getClass().getSimpleName())) fx.who += (fx.who.isEmpty() ? "" : " ") + e.getClass().getSimpleName();
                } else fx.shots++;
            }
            for (int t = 0; t < 40 && !fresh.isEmpty(); t++) for (Entity e : fresh) if (!e.isDead) try {
                e.onUpdate();
            } catch (ThreadDeath d) {
                throw d;
            } catch (Throwable ignored) {}
        } catch (ThreadDeath d) {
            if (fx.error == null) fx.error = "завис";
        } finally {
            guarded = false;
        }
        if ("завис".equals(fx.error)) hung++;
        if (p.worldObj != w) fx.world = p.dimension;
        fx.died = p.deaths > deaths0;
        fx.blocks = diff(false);
        fx.mob = mob.isDead ? -mob0 : mob.getHealth() - mob0;
        fx.moved = Math.sqrt((p.posX - px) * (p.posX - px) + (p.posY - py) * (p.posY - py) + (p.posZ - pz) * (p.posZ - pz));
        for (Object o : p.getActivePotionEffects()) {
            PotionEffect pe = (PotionEffect) o;
            if (!fx0.contains(pe.getPotionID())) fx.effects.add(net.minecraft.potion.Potion.potionTypes[pe.getPotionID()] == null ? "#" + pe.getPotionID()
                : net.minecraft.potion.Potion.potionTypes[pe.getPotionID()].getName());
        }
        fx.fly = p.capabilities.allowFlying || p.capabilities.isFlying;
        double dSpeed = p.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue() - speed0, dMax = p.getMaxHealth() - max0,
            dDmg = p.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue() - dmg0;
        fx.attrs = Math.abs(dSpeed) > 1e-4 || Math.abs(dMax) > 0.01 || (bauble || armor) && Math.abs(dDmg) > 1e-4;
        if (fx.attrs) fx.attrWhy = (Math.abs(dSpeed) > 1e-4 ? String.format(" скорость %+.3f", dSpeed) : "") + (Math.abs(dMax) > 0.01 ? String.format(" здоровье %+.1f", dMax) : "")
            + (Math.abs(dDmg) > 1e-4 ? String.format(" урон %+.1f", dDmg) : "");
        fx.hurtBy = HurtLog.log.toString();
        HurtLog.target = null;
        fx.heal = p.getHealth() - hp0;
        if (armor && fx.error == null) { // удар моба Ветеи по игроку в этой броне
            p.setHealth(20);
            p.hurtResistantTime = 0;
            float h = p.getHealth();
            p.attackEntityFrom(DamageSource.causeMobDamage(mob), 6F);
            fx.hurt = h - p.getHealth();
            p.setHealth(10);
        }
        // уборка
        diff(true);
        for (Object o : w.getEntitiesWithinAABB(Entity.class, box())) if (o != p && o != mob) ((Entity) o).setDead();
        if (p.isUsingItem()) p.clearItemInUse();
        IInventoryHolder.clearBaubles(p);
        mob.setDead();
        purge(w);
        return fx;
    }

    private Fx melee(ItemStack s) {
        return measure(s, false, false, () -> p.attackTargetEntityWithCurrentItem(mob));
    }

    private Fx rmbAir(ItemStack s) {
        return measure(s, false, false, () -> {
            PlayerInteractEvent ev = new PlayerInteractEvent(p, PlayerInteractEvent.Action.RIGHT_CLICK_AIR, 0, 0, 0, -1, w);
            MinecraftForge.EVENT_BUS.post(ev);
            if (!ev.isCanceled() && ev.useItem != Event.Result.DENY && p.getHeldItem() != null) p.theItemInWorldManager.tryUseItem(p, w, p.getHeldItem());
            for (int t = 0; t < 30 && p.isUsingItem(); t++) tickPlayer(1);
            if (p.isUsingItem()) p.stopUsingItem();
        });
    }

    private Fx rmbBlock(ItemStack s) {
        return measure(s, false, false, () -> {
            if (p.getHeldItem() != null) p.theItemInWorldManager.activateBlockOrUseItem(p, w, p.getHeldItem(), x0, y0 - 1, z0 + 1, 1, 0.5F, 1F, 0.5F);
            for (int t = 0; t < 30 && p.isUsingItem(); t++) tickPlayer(1);
            if (p.isUsingItem()) p.stopUsingItem();
        });
    }

    private Fx harvest(ItemStack s, int x, int y, int z) {
        return measure(s, false, false, () -> dig(p, w, x, y, z));
    }

    /** Ломка как у игрока: клик по блоку (сервер запоминает, что начали ломать), затем сама ломка. */
    private static void dig(EntityPlayerMP p, WorldServer w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        p.theItemInWorldManager.onBlockClicked(x, y, z, 1);
        if (w.getBlock(x, y, z) == b) p.theItemInWorldManager.tryHarvestBlock(x, y, z);
    }

    /** Во сколько раз вещь копает камень снов быстрее руки. */
    private float speed(ItemStack s) {
        resetPlayer();
        p.inventory.mainInventory[0] = null;
        float hand = p.getBreakSpeed(VetheaBlocks.dreamStone, false, 0, x0 + 1, y0, z0 + 1);
        actStart = System.currentTimeMillis();
        guarded = true;
        try {
            p.inventory.mainInventory[0] = prepare(s.copy());
            float tool = p.getBreakSpeed(VetheaBlocks.dreamStone, false, 0, x0 + 1, y0, z0 + 1);
            return hand <= 0 ? 0 : tool / hand;
        } catch (Throwable t) {
            return -1;
        } finally {
            guarded = false;
        }
    }

    private Fx wear(ItemStack s, boolean bauble) {
        return measure(s, bauble, !bauble, () -> tickPlayer(40));
    }

    // ---------------------------------------------------------------- отбор вещей

    private static final String[] USE = { "onItemRightClick", "func_77659_a", "onItemUse", "func_77648_a", "onItemUseFirst", "onPlayerStoppedUsing", "func_77615_a",
        "onUsingTick" };

    private static boolean overrides(Item i) {
        for (Class<?> c = i.getClass(); c != null && c != Item.class && c != net.minecraft.item.ItemTool.class && c != ItemSword.class; c = c.getSuperclass()) {
            if ("ItemMod".equals(c.getSimpleName())) continue; // общий класс Divine ловит ПКМ у всех своих (лечение - через healAmount)
            Method[] ms;
            try {
                ms = c.getDeclaredMethods();
            } catch (Throwable t) {
                return true; // в сигнатурах клиентские классы (на сервере их нет) - проверяем ПКМ на всякий случай
            }
            for (Method m : ms) for (String u : USE) if (u.equals(m.getName())) return true;
        }
        return false;
    }

    private static boolean weapon(ItemStack s) {
        Item i = s.getItem();
        if (i instanceof ItemSword || i instanceof ItemTool || i instanceof net.minecraft.item.ItemBow) return true;
        try {
            @SuppressWarnings("rawtypes")
            java.util.Collection c = s.getAttributeModifiers().get(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName());
            return c != null && !c.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean tool(ItemStack s) {
        try {
            return s.getItem() instanceof ItemTool || !s.getItem().getToolClasses(s).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Варианты по мете: у вещей с подтипами - пока меняется имя (до 16). */
    private static List<ItemStack> variants(Item i) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        out.add(new ItemStack(i, 1, 0));
        if (!i.getHasSubtypes()) return out;
        Set<String> names = new HashSet<String>();
        try {
            names.add(i.getUnlocalizedName(out.get(0)));
            for (int m = 1; m < 16; m++) {
                ItemStack s = new ItemStack(i, 1, m);
                String n = i.getUnlocalizedName(s);
                if (n == null || !names.add(n)) continue;
                out.add(s);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    // ---------------------------------------------------------------- прогон

    String run() throws Exception {
        File logs = server.getFile("logs");
        out = new java.io.OutputStreamWriter(new java.io.FileOutputStream(new File(logs, "dre-vethea-cover.tsv")), StandardCharsets.UTF_8);
        prog = new java.io.PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(new File(logs, "dre-vethea-progress.txt")), StandardCharsets.UTF_8));
        main = Thread.currentThread();
        startWatchdog();
        HurtLog hurtLog = new HurtLog();
        MinecraftForge.EVENT_BUS.register(hurtLog);
        // предел времени на вещи (минуты): дальше - только площадные в привате и итог
        long budget = Long.getLong("dre.cover.budget", 150) * 60000L;
        Map<String, Integer> byMod = new LinkedHashMap<String, Integer>();
        boolean cut = false;
        // без естественной регенерации: иначе сытый игрок сам лечится и это выглядит как действие вещи
        net.minecraft.world.GameRules rules = server.worldServerForDimension(0).getGameRules();
        String regen = rules.getGameRuleStringValue("naturalRegeneration");
        rules.setOrCreateGameRule("naturalRegeneration", "false");
        try {
            build();
            p = makePlayer();
            emit("id\tмета\tимя\tвиды\tфаза\tудар\tПКМ в воздух\tПКМ по блоку\tкопка камня\tрубка дерева\tскорость копки x\tношение\tитог\tмс\n");
            List<Object[]> todo = new ArrayList<Object[]>();
            for (Object o : Item.itemRegistry) {
                Item i = (Item) o;
                if (i == null || i instanceof ItemBlock || i instanceof ItemFood || i instanceof ItemPotion || i instanceof ItemRecord) continue;
                String id = Item.itemRegistry.getNameForObject(i);
                if (id == null) continue;
                try {
                    for (ItemStack base : variants(i)) {
                        ItemStack s = base;
                        ItemStack tk = tinkers(i);
                        if (tk != null) s = tk;
                        if (com.divinerpgextended.vethea.Nightmare.vethean(s)) continue;
                        boolean wp = weapon(s), tl = tool(s), use = overrides(i), bb = IInventoryHolder.isBauble(i), ar = i instanceof ItemArmor;
                        if (!(wp || tl || use || bb || ar)) continue;
                        todo.add(new Object[] { id, s, wp, tl, use, bb, ar });
                    }
                } catch (Throwable t) {
                    log("не разобрал " + id + ": " + t);
                }
            }
            log("вещей к проверке: " + todo.size());
            int n = 0;
            for (Object[] it : todo) {
                n++;
                String id = (String) it[0];
                ItemStack s = (ItemStack) it[1];
                if (System.currentTimeMillis() - t0 > budget) {
                    cut = true;
                    log("предел времени: проверено " + (n - 1) + " из " + todo.size());
                    break;
                }
                current = id + "#" + s.getItemDamage();
                long a = System.currentTimeMillis();
                try {
                    check(id, s, (Boolean) it[2], (Boolean) it[3], (Boolean) it[4], (Boolean) it[5], (Boolean) it[6], n, todo.size());
                } catch (Throwable t) { // ThreadDeath между действиями или сбой самой проверки
                    crashes++;
                    emit(id + "\t" + s.getItemDamage() + "\t\t\t\tсбой проверки: " + t + "\n");
                    log("сбой проверки " + current + ": " + t);
                }
                long dt = System.currentTimeMillis() - a;
                items++;
                byMod.merge(id.substring(0, id.indexOf(':')), 1, Integer::sum);
                if (dt > 2000) slow.add(current + " " + dt + " мс");
                if (n % 50 == 0 || dt > 2000) log(n + "/" + todo.size() + " " + current + " " + dt + " мс; утечек " + leaks.size());
            }
            current = "площадные в привате";
            boundEverywhere();
        } finally {
            current = null;
            finished = true;
            MinecraftForge.EVENT_BUS.unregister(hurtLog);
            rules.setOrCreateGameRule("naturalRegeneration", regen.isEmpty() ? "true" : regen);
            try {
                dropPlayer();
                if (mob != null) mob.setDead();
                build(); // комнату - как была
            } finally {
                log("готово; долгие вещи (>2 с): " + slow);
                out.close();
                prog.close();
            }
        }
        return "вещей " + items + " " + byMod + (cut ? " (не все: предел времени)" : "") + "; работает только после прохождения " + okCount + ", без заметного действия "
            + neutral + ", утечек " + leaks.size() + ", сбоев " + crashes + ", зависаний " + hung + "; поправки: площадная ломка "
            + com.divinerpgextended.asm.AreaBreakTransformer.patched.size() + " классов, Baubles " + com.divinerpgextended.asm.BaublesTransformer.patched + " из 2, тик брони "
            + (com.divinerpgextended.asm.ArmorTickTransformer.patched ? "да" : "НЕТ") + " (logs/dre-vethea-cover.tsv)";
    }

    private void check(String id, ItemStack s, boolean wp, boolean tl, boolean use, boolean bb, boolean ar, int n, int total) {
        long a = System.currentTimeMillis();
        String kinds = (wp ? "оружие " : "") + (tl ? "инструмент " : "") + (use ? "ПКМ " : "") + (bb ? "баубл " : "") + (ar ? "броня " : "");
        String name;
        try {
            name = s.getDisplayName();
        } catch (Throwable t) {
            name = id;
        }
        boolean axe = tl && (s.getItem() instanceof net.minecraft.item.ItemAxe || toolClass(s, "axe"));
        // 0 удар, 1 ПКМ в воздух, 2 ПКМ по блоку, 3 копка камня сна, 4 рубка дерева сна, 5 ношение
        Fx[] pre = new Fx[6], post = new Fx[6];
        float[] spd = new float[2];
        for (int phase = 0; phase < 2; phase++) {
            progress(phase == 1);
            Fx[] r = phase == 0 ? pre : post;
            if (wp) r[0] = melee(s);
            if (use || wp || tl) {
                r[1] = rmbAir(s);
                r[2] = rmbBlock(s);
            }
            if (tl) {
                r[3] = harvest(s, x0 + 1, y0, z0 + 1);
                spd[phase] = speed(s);
            }
            if (axe) r[4] = harvest(s, x0 + 3, y0, z0 + 3);
            if (bb || ar) r[5] = wear(s, bb);
        }
        boolean leak = false, works = false, crash = false, places = false;
        StringBuilder why = new StringBuilder();
        for (int k = 0; k < 6; k++) {
            if (pre[k] != null && pre[k].error != null || post[k] != null && post[k].error != null) crash = true;
            if (pre[k] == null) continue;
            boolean pa;
            if (k == 3 || k == 4) pa = pre[k].blocks > 1 || pre[k].other(); // один блок под рукой ломается и чужим
            else if (k == 2 && pre[k].blocks > 0 && !pre[k].other() && com.divinerpgextended.vethea.Nightmare.placer(s.getItem())) {
                pa = false; // строительство разрешено
                places = true;
            } else pa = pre[k].any();
            if (pa) {
                leak = true;
                why.append(ACTS[k]).append(": ").append(pre[k]).append("; ");
            }
            if (post[k] != null && post[k].any()) works = true;
        }
        if (crash) crashes++;
        String verdict = leak ? "УТЕЧКА" : places ? "ставит блок" : works ? "ок" : "нет действия";
        if (leak) leaks.add(id + "#" + s.getItemDamage() + " (" + name + ") " + kinds.trim() + " - " + why);
        else if (works || places) okCount++;
        else neutral++;
        StringBuilder tsv = new StringBuilder();
        long ms = System.currentTimeMillis() - a;
        for (int phase = 0; phase < 2; phase++) {
            Fx[] r = phase == 0 ? pre : post;
            tsv.append(id).append('\t').append(s.getItemDamage()).append('\t').append(name).append('\t').append(kinds.trim()).append('\t').append(phase == 0 ? "до" : "после");
            for (int k = 0; k < 5; k++) tsv.append('\t').append(r[k] == null ? "" : r[k].toString());
            tsv.append('\t').append(tl ? String.format("%.2f", spd[phase]) : "").append('\t').append(r[5] == null ? "" : r[5].toString()).append('\t')
                .append(phase == 0 ? verdict : "").append('\t').append(phase == 0 ? String.valueOf(ms) : "").append('\n');
        }
        emit(tsv.toString());
    }

    private static final String[] ACTS = { "удар", "ПКМ", "ПКМ по блоку", "копка", "рубка", "ношение" };

    private static boolean toolClass(ItemStack s, String c) {
        try {
            return s.getItem().getToolClasses(s).contains(c);
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------- площадные инструменты в привате

    /** Комната в обычном мире внутри привата (симулятор отменяет ломку): площадные инструменты не должны ломать ничего. */
    private void boundEverywhere() {
        WorldServer ow = server.worldServerForDimension(0);
        int bx = ow.getSpawnPoint().posX + 700, by = 200, bz = ow.getSpawnPoint().posZ;
        for (int cx = (bx - 24) >> 4; cx <= (bx + 24) >> 4; cx++) for (int cz = (bz - 24) >> 4; cz <= (bz + 24) >> 4; cz++) ow.theChunkProviderServer.loadChunk(cx, cz);
        SelfTest.Protection prot = new SelfTest.Protection();
        prot.dim = 0;
        prot.x0 = bx - 10;
        prot.x1 = bx + 10;
        prot.y0 = by - 10;
        prot.y1 = by + 12;
        prot.z0 = bz - 10;
        prot.z1 = bz + 10;
        MinecraftForge.EVENT_BUS.register(prot);
        RealPlayer op = new RealPlayer(server, ow, new GameProfile(UUID.nameUUIDFromBytes("dre-vethea-cover-ow".getBytes(StandardCharsets.UTF_8)), "TestDreamCoverOw"));
        NetworkManager nm = new NetworkManager(false);
        ReflectionHelper.setPrivateValue(NetworkManager.class, nm, new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter()), "channel", "field_150746_k");
        new NetHandlerPlayServer(server, nm, op);
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> list = server.getConfigurationManager().playerEntityList;
        list.add(op);
        ow.spawnEntityInWorld(op);
        RealPlayer keep = p;
        emit("\n# площадные инструменты и вещи с ПКМ в привате обычного мира: сколько блоков из каменного куба 9x9x9 сломано (должно быть 0)\n");
        try {
            // площадные: связанные инструменты Blood Magic, молот/экскаватор/лесоруб Tinkers, Кирка Терры, стихийные Таумкрафта; алмазный топор - для Treecapitator
            Set<String> area = new HashSet<String>(java.util.Arrays.asList("BoundPickaxe", "BoundAxe", "BoundShovel", "Hammer", "Excavator", "LumberAxe", "ItemTerraPick",
                "ItemElementalPickaxe", "ItemElementalShovel", "ItemElementalAxe"));
            List<ItemStack> tools = new ArrayList<ItemStack>();
            for (Object o : Item.itemRegistry) {
                Item it = (Item) o;
                if (it == null || !area.contains(it.getClass().getSimpleName()) && it != Items.diamond_axe) continue;
                ItemStack tk = tinkers(it);
                tools.add(tk != null ? tk : new ItemStack(it));
            }
            p = op;
            for (ItemStack s : tools) {
                int broken = 0;
                for (int mode = 0; mode < 3; mode++) {
                    for (int x = bx - 4; x <= bx + 4; x++) for (int y = by - 1; y <= by + 7; y++) for (int z = bz - 4; z <= bz + 4; z++)
                        ow.setBlock(x, y, z, Math.abs(x - bx) <= 1 && Math.abs(z - bz) <= 1 && y >= by && y <= by + 1 ? Blocks.air : y == by + 2 ? Blocks.log : Blocks.stone, 0, 2);
                    for (int i = 0; i < p.inventory.mainInventory.length; i++) p.inventory.mainInventory[i] = null;
                    provision();
                    p.setLocationAndAngles(bx + 0.5, by, bz + 0.5, 0, 0);
                    p.setPositionAndUpdate(bx + 0.5, by, bz + 0.5);
                    p.inventory.currentItem = 0;
                    current = "в привате " + Item.itemRegistry.getNameForObject(s.getItem()) + " режим " + mode;
                    actStart = System.currentTimeMillis();
                    guarded = true;
                    try {
                        p.inventory.mainInventory[0] = prepare(s.copy());
                        if (mode == 0) {
                            PlayerInteractEvent ev = new PlayerInteractEvent(p, PlayerInteractEvent.Action.RIGHT_CLICK_AIR, 0, 0, 0, -1, ow);
                            MinecraftForge.EVENT_BUS.post(ev);
                            if (!ev.isCanceled() && ev.useItem != Event.Result.DENY) p.theItemInWorldManager.tryUseItem(p, ow, p.getHeldItem());
                        } else if (mode == 1) p.theItemInWorldManager.activateBlockOrUseItem(p, ow, p.getHeldItem(), bx, by - 1, bz + 2, 1, 0.5F, 1F, 0.5F);
                        else dig(p, ow, bx, by + 2, bz + 2);
                        for (int t = 0; t < 30 && p.isUsingItem(); t++) tickPlayer(1); // удержание ПКМ: топор потока и подобные
                        if (p.isUsingItem()) p.stopUsingItem();
                    } catch (Throwable ignored) {} finally {
                        guarded = false;
                    }
                    for (Object o : ow.getEntitiesWithinAABB(EntityItem.class, AxisAlignedBB.getBoundingBox(bx - 8, by - 8, bz - 8, bx + 8, by + 12, bz + 8))) ((Entity) o).setDead();
                    purge(ow);
                    for (int x = bx - 4; x <= bx + 4; x++) for (int y = by - 1; y <= by + 7; y++) for (int z = bz - 4; z <= bz + 4; z++) {
                        boolean air = Math.abs(x - bx) <= 1 && Math.abs(z - bz) <= 1 && y >= by && y <= by + 1;
                        if (!air && ow.isAirBlock(x, y, z)) broken++;
                    }
                }
                String id = Item.itemRegistry.getNameForObject(s.getItem());
                emit(id + "\t" + s.getDisplayName() + "\tсломано в привате: " + broken + "\n");
                if (broken > 0) leaks.add(id + " (" + s.getDisplayName() + ") - ломает в привате обычного мира: " + broken + " блоков");
            }
        } finally {
            p = keep;
            MinecraftForge.EVENT_BUS.unregister(prot);
            server.getConfigurationManager().playerEntityList.remove(op);
            ow.removeEntity(op);
            for (int x = bx - 4; x <= bx + 4; x++) for (int y = by - 1; y <= by + 7; y++) for (int z = bz - 4; z <= bz + 4; z++) ow.setBlockToAir(x, y, z);
            for (Object o : ow.getEntitiesWithinAABB(EntityItem.class, AxisAlignedBB.getBoundingBox(bx - 8, by - 8, bz - 8, bx + 8, by + 12, bz + 8))) ((Entity) o).setDead();
        }
    }
}
