package com.divinerpgextended.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.divinerpgextended.world.LairData;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Карта логова: правый клик пишет в чат расстояние, направление и глубину до ближайшего логова. Мета 0 - логово
 * Карцинуса, 1 - снежное подземелье. Знает только уже сгенерированные логова; если их нет, показывает ближайший
 * подходящий биом (океан или снежные земли) - биомы известны и в ещё не исследованных краях.
 */
public class ItemLairMap extends Item {

    private static final String[] KEYS = { "carcinus", "snow", "throne", "tomb", "nest", "circle", "watcher", "scorchers", "ayeraco", "twins", "karot",
        "archer", "fiend", "vamacheron", "jungle" };
    private static final int[] COLORS = { 0x4FA3C8, 0xDDEEFF, 0x6FC8FF, 0xE8C060, 0x7FD15A, 0xB0302A, 0x7A2E4E, 0xF07A20, 0xD0C890, 0xE0E0E0, 0x9050C0,
        0xC09040, 0x50A070, 0xD8D0B8, 0x3E8A2E };
    /** Мета карты к храму джунглей с логовом Древнего драмкрикса. */
    public static final int JUNGLE = 14;
    /** Карты к аренам Мортума (кроме Демонического круга - CIRCLE): близнецы, Карот, лучник, Пожиратель, Вамачерон. */
    public static final int ARENA = 9;

    /** Мета карты к арене этого вида. */
    public static int arenaMap(com.divinerpgextended.world.twilight.MortumArenas.Type t) {
        return t == com.divinerpgextended.world.twilight.MortumArenas.Type.DEMON ? CIRCLE : ARENA + t.ordinal() - 1;
    }

    private static com.divinerpgextended.world.twilight.MortumArenas.Type arenaOf(int t) {
        if (t == CIRCLE) return com.divinerpgextended.world.twilight.MortumArenas.Type.DEMON;
        return t >= ARENA && t < JUNGLE ? com.divinerpgextended.world.twilight.MortumArenas.Type.values()[t - ARENA + 1] : null;
    }
    /** Мета карт к логовам Ада и Энда: Смотритель, Король скорчеров, орда Айрако. */
    public static final int WATCHER = 6, SCORCHERS = 7, AYERACO = 8;
    /** Мета карты к гнезду Великого кадиллиона (в его мире) и к Демоническому кругу (Мортум). */
    public static final int NEST = 4, CIRCLE = 5;
    private static final String[] DIRS = { "s", "sw", "w", "nw", "n", "ne", "e", "se" };
    private static final String COOLDOWN = "dreLairMapUsed";

    public ItemLairMap() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
        setUnlocalizedName("dre.lairMap");
        setTextureName("minecraft:map_filled");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    private static int type(ItemStack stack) {
        return Math.max(0, Math.min(KEYS.length - 1, stack.getItemDamage()));
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "item.dre.lairMap." + KEYS[type(stack)];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        return COLORS[type(stack)];
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) return stack;
        ExplorerMaps.inClick = true;
        try {
            return use(stack, world, player);
        } finally {
            ExplorerMaps.inClick = false;
        }
    }

    private ItemStack use(ItemStack stack, World world, EntityPlayer player) {
        int t = type(stack);
        // перезарядка: поиск по карте биомов нагружает сервер
        long now = world.getTotalWorldTime(), last = player.getEntityData().getLong(COOLDOWN);
        long cd = com.divinerpgextended.Config.mapCooldownSeconds * 20L;
        if (cd > 0 && player.getEntityData().hasKey(COOLDOWN) && now - last < cd && now >= last) { // ещё не пользовался - ждать нечего
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_cooldown", (cd - (now - last) + 19) / 20));
            return stack;
        }
        player.getEntityData().setLong(COOLDOWN, now);
        if (t == JUNGLE) return jungle(stack, world, player);
        if (t == NEST || t == CIRCLE || t >= ARENA) return twilight(stack, world, player, t);
        if (t >= WATCHER) return outer(world, player, t);
        // логова - в обычном мире, Ледяной Трон - в Iceika
        int dim = t == LairData.THRONE ? net.divinerpg.utils.config.ConfigurationHelper.iceika : 0;
        if (world.provider.dimensionId != dim) {
            player.addChatMessage(new ChatComponentTranslation(t == LairData.THRONE ? "dre.chat.map_iceika"
                : "dre.chat.map_overworld"));
            return stack;
        }
        // место Трона вычисляется из сида - карта знает его заранее
        if (t == LairData.THRONE) {
            final long seed = world.getSeed();
            int[] c = pick(com.divinerpgextended.Config.throneSpacing, player.posX, player.posZ, 3,
                (rx, rz) -> com.divinerpgextended.world.WorldGenThrone.center(seed, rx, rz));
            if (c == null) {
                player.addChatMessage(new ChatComponentTranslation("dre.chat.map_none_" + KEYS[t]));
                return stack;
            }
            int[] lair = new int[] { t, c[0], com.divinerpgextended.world.WorldGenThrone.TOP + 1, c[2] - com.divinerpgextended.world.WorldGenThrone.CITADEL };
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_found_" + KEYS[t], distance(player, lair[1], lair[3]),
                direction(player, lair[1], lair[3]), lair[2], lair[1], lair[3]));
            ExplorerMaps.give(world, player, t, "item.dre.lairMap." + KEYS[t] + ".name", lair[1], lair[3]);
            return stack;
        }
        return overworld(stack, world, player, t);
    }

    /**
     * Карцинус, ледяная пещера, гробница: ближайшее из построенных логов и мест из сида мира, где логово ещё будет
     * ({@link com.divinerpgextended.world.LairSites}). Ближе {@link com.divinerpgextended.Config#mapSkipNear} блоков место
     * считается найденным - карта ведёт к следующему; места, где построить не удалось, пропускаются.
     */
    /** Поиск на тик: не дольше, чтобы игра не подвисала (в одиночной игре сервер в том же процессе). */
    private static final long BUDGET_NS = 20_000_000L;

    /** Недосчитанные поиски: игрок -> {тип, x, z} места, где развернули карту. */
    private static final java.util.Map<EntityPlayer, double[]> UNFOLDING = new java.util.WeakHashMap<EntityPlayer, double[]>();

    private static ItemStack overworld(ItemStack stack, World world, EntityPlayer player, final int t) {
        if (!answer(world, player, t, player.posX, player.posZ, System.nanoTime() + BUDGET_NS)) {
            UNFOLDING.put(player, new double[] { t, player.posX, player.posZ });
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_unfolding"));
        }
        return stack;
    }

    /** Досчитывает начатые поиски: общий бюджет на тик, по очереди. */
    public static class Unfold {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onTick(cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent e) {
            if (e.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END || UNFOLDING.isEmpty()) return;
            long deadline = System.nanoTime() + BUDGET_NS;
            for (java.util.Iterator<java.util.Map.Entry<EntityPlayer, double[]>> it = UNFOLDING.entrySet().iterator(); it.hasNext() && System.nanoTime() < deadline;) {
                java.util.Map.Entry<EntityPlayer, double[]> q = it.next();
                EntityPlayer p = q.getKey();
                if (p.isDead || p.worldObj.provider.dimensionId != 0) {
                    it.remove();
                    continue;
                }
                double[] a = q.getValue();
                if (answer(p.worldObj, p, (int) a[0], a[1], a[2], deadline)) it.remove();
            }
        }
    }

    /**
     * Карцинус, ледяная пещера, гробница: ближайшее из построенных логов и мест из сида мира, где логово ещё будет
     * ({@link com.divinerpgextended.world.LairSites}). Ближе {@link com.divinerpgextended.Config#mapSkipNear} блоков место
     * считается найденным - карта ведёт к следующему; места, где построить не удалось, пропускаются. false - не успел до
     * deadline, досчитать позже.
     */
    static boolean answer(World world, EntityPlayer player, final int t, double px, double pz, long deadline) {
        int radius = com.divinerpgextended.Config.mapSearchRadius, skip = com.divinerpgextended.Config.mapSkipNear;
        final LairData data = LairData.get(world);
        int[] built = null;
        double builtD = Double.MAX_VALUE;
        for (int[] l : data.all(t)) {
            if (!inBounds(l[1], l[3])) continue;
            double d = (l[1] - px) * (l[1] - px) + (l[3] - pz) * (l[3] - pz);
            if (d <= (double) skip * skip || d > (double) radius * radius || d >= builtD) continue;
            builtD = d;
            built = l;
        }
        int[] site = com.divinerpgextended.world.LairSites.nearest(world, t, px, pz, radius, skip,
            s -> inBounds(s[0], s[1]) && !data.near(t, s[0], s[1], 96) && !data.near(t + LairData.FAILED, s[0], s[1], 24)
                && !com.divinerpgextended.world.LairSites.stale(world, t, s[0], s[1]), deadline);
        if (site == com.divinerpgextended.world.LairSites.UNFINISHED) return false;
        double siteD = site == null ? Double.MAX_VALUE : (site[0] - px) * (site[0] - px) + (site[1] - pz) * (site[1] - pz);
        if (built == null && site == null) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_none_" + KEYS[t], radius));
            return true;
        }
        if (built != null && builtD <= siteD) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_found_" + KEYS[t], distance(player, built[1], built[3]),
                direction(player, built[1], built[3]), built[2], built[1], built[3]));
            ExplorerMaps.give(world, player, t, "item.dre.lairMap." + KEYS[t] + ".name", built[1], built[3]);
        } else {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_site_" + KEYS[t], distance(player, site[0], site[1]),
                direction(player, site[0], site[1]), site[0], site[1]));
            ExplorerMaps.give(world, player, t, "item.dre.lairMap." + KEYS[t] + ".name", site[0], site[1]);
        }
        return true;
    }

    /** Гнёзда и круг - из сида мира, карта знает их заранее (как Трон). */
    private static ItemStack twilight(ItemStack stack, World world, EntityPlayer player, int t) {
        com.divinerpgextended.world.twilight.TwilightWorld tw = com.divinerpgextended.world.twilight.TwilightWorld.of(world.provider.dimensionId);
        com.divinerpgextended.world.twilight.MortumArenas.Type arena = arenaOf(t);
        boolean ok = tw != null && (arena != null) == (tw == com.divinerpgextended.world.twilight.TwilightWorld.MORTUM);
        if (!ok) {
            player.addChatMessage(new ChatComponentTranslation(arena != null ? "dre.chat.map_mortum" : "dre.chat.map_cadillion_world"));
            return stack;
        }
        // арены Мортума - каждая карта к своей (ближайшей арене этого вида); найденное (ближе mapSkipNear) пропускаем
        final long seed = world.getSeed();
        final int dim = world.provider.dimensionId;
        final com.divinerpgextended.world.twilight.MortumArenas.Type want = arena;
        int[] c = pick(com.divinerpgextended.world.twilight.WorldGenNests.spacing(dim), player.posX, player.posZ, arena != null ? 8 : 3,
            (rx, rz) -> want != null && com.divinerpgextended.world.twilight.MortumArenas.type(seed, rx, rz) != want ? null
                : com.divinerpgextended.world.twilight.WorldGenNests.center(seed, dim, rx, rz));
        if (c == null) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_none_" + KEYS[t]));
            return stack;
        }
        int floor = arena != null ? com.divinerpgextended.world.twilight.MortumArenas.floorY(arena) + 1 : c[1] + 1;
        player.addChatMessage(new ChatComponentTranslation("dre.chat.map_found_" + KEYS[t], distance(player, c[0], c[2]),
            direction(player, c[0], c[2]), floor, c[0], c[2]));
        ExplorerMaps.give(world, player, t, "item.dre.lairMap." + KEYS[t] + ".name", c[0], c[2]);
        return stack;
    }

    /** Есть ли у спавна джунгли (считается раз на мир: иначе храмы джунглей встают в тёплых зелёных землях). */
    private static final java.util.Map<World, Boolean> JUNGLES = new java.util.WeakHashMap<World, Boolean>();

    /**
     * Храм джунглей с логовом Древнего драмкрикса: места храмов - из сида (сетка ванильного генератора), вид постройки - по
     * биому места ({@link com.divinerpgextended.world.ScatterGen#kind}); биомы известны и в ещё не созданных краях.
     */
    private static ItemStack jungle(ItemStack stack, World world, EntityPlayer player) {
        if (world.provider.dimensionId != 0) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_overworld"));
            return stack;
        }
        Boolean has = JUNGLES.get(world);
        if (has == null) JUNGLES.put(world, has = !com.divinerpgextended.Config.scatterInModBiomes || com.divinerpgextended.world.ScatterGen.hasJungles(world));
        final boolean jungles = has;
        final net.minecraft.world.biome.WorldChunkManager wcm = world.getWorldChunkManager();
        int[] c = pick(16 * 32, player.posX, player.posZ, 8, (rx, rz) -> {
            int[] s = com.divinerpgextended.world.ScatterGen.spot(world, rx, rz);
            int x = s[0] * 16 + 8, z = s[1] * 16 + 8;
            return com.divinerpgextended.world.ScatterGen.kind(wcm.getBiomeGenAt(x, z), jungles) == com.divinerpgextended.world.ScatterGen.JUNGLE
                ? new int[] { x, 64, z } : null;
        });
        if (c == null) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_none_jungle"));
            return stack;
        }
        player.addChatMessage(new ChatComponentTranslation("dre.chat.map_site_jungle", distance(player, c[0], c[2]), direction(player, c[0], c[2]), c[0], c[2]));
        ExplorerMaps.give(world, player, JUNGLE, "item.dre.lairMap.jungle.name", c[0], c[2]);
        return stack;
    }

    /** Логова Ада и Энда - из сида. */
    private static ItemStack outer(World world, EntityPlayer player, int t) {
        int type = t - WATCHER;
        ItemStack stack = player.getHeldItem();
        if (!com.divinerpgextended.world.WorldGenOuterLairs.enabled(type)) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_none_" + KEYS[t]));
            return stack;
        }
        if (world.provider.dimensionId != com.divinerpgextended.world.WorldGenOuterLairs.dim(type)) {
            player.addChatMessage(new ChatComponentTranslation(type == com.divinerpgextended.world.WorldGenOuterLairs.AYERACO ? "dre.chat.map_end"
                : "dre.chat.map_nether"));
            return stack;
        }
        final long seed = world.getSeed();
        int[] c = type == com.divinerpgextended.world.WorldGenOuterLairs.AYERACO ? com.divinerpgextended.world.WorldGenOuterLairs.nearest(seed, type, player.posX, player.posZ)
            : pick(com.divinerpgextended.Config.netherLairSpacing, player.posX, player.posZ, 3, (rx, rz) -> com.divinerpgextended.world.WorldGenOuterLairs.center(seed, type, rx, rz));
        if (c == null || !inBounds(c[0], c[2])) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_none_" + KEYS[t]));
            return stack;
        }
        // логово Смотрителя под лавой: карта ведёт ко входу - причалу на берегу, откуда тоннель уходит вниз
        if (type == com.divinerpgextended.world.WorldGenOuterLairs.WATCHER)
            c = new int[] { c[0] + com.divinerpgextended.world.WatcherVault.tunnelEnd() + 2, com.divinerpgextended.world.WatcherVault.TUNNEL_Y - 1, c[2] };
        player.addChatMessage(new ChatComponentTranslation("dre.chat.map_found_" + KEYS[t], distance(player, c[0], c[2]),
            direction(player, c[0], c[2]), c[1] + 1, c[0], c[2]));
        ExplorerMaps.give(world, player, t, "item.dre.lairMap." + KEYS[t] + ".name", c[0], c[2]);
        return stack;
    }

    /**
     * Ближайшее место из сида (по регионам size, кольцами до rings): дальше mapSkipNear блоков (у найденного - следующее) и
     * в границах карт ({@link #inBounds}); null - нет. at(rx, rz) - место региона {x, y, z} или null.
     */
    static int[] pick(int size, double x, double z, int rings, java.util.function.BiFunction<Integer, Integer, int[]> at) {
        if (size <= 0) return null;
        int skip = com.divinerpgextended.Config.mapSkipNear;
        int rx = (int) Math.floor(x / size), rz = (int) Math.floor(z / size);
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int i = -rings; i <= rings; i++) for (int k = -rings; k <= rings; k++) {
            int[] c = at.apply(rx + i, rz + k);
            if (c == null || !inBounds(c[0], c[2])) continue;
            double d = (c[0] - x) * (c[0] - x) + (c[2] - z) * (c[2] - z);
            if (d <= (double) skip * skip || d >= bd) continue;
            bd = d;
            best = c;
        }
        return best;
    }

    /** Карты показывают логова только в квадрате mapWorldSize с центром в нуле мира (0 - без границ). */
    public static boolean inBounds(int x, int z) {
        int half = com.divinerpgextended.Config.mapWorldSize / 2;
        return com.divinerpgextended.Config.mapWorldSize <= 0 || Math.abs(x) <= half && Math.abs(z) <= half;
    }

    private static int distance(EntityPlayer player, int x, int z) {
        double dx = x - player.posX, dz = z - player.posZ;
        return (int) Math.sqrt(dx * dx + dz * dz);
    }

    private static ChatComponentTranslation direction(EntityPlayer player, int x, int z) {
        double dx = x - player.posX, dz = z - player.posZ;
        // угол как у yaw в Minecraft: 0 - юг, 90 - запад, 180 - север, 270 - восток
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        int sector = MathHelper.floor_double(((yaw + 360 + 22.5) % 360) / 45.0) % 8;
        return new ChatComponentTranslation("dre.dir." + DIRS[sector]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        // в порядке прохождения: Карцинус, снежная пещера, Трон, гробница, Ад, рощи, Мортум, Айрако
        int[] order = { 0, 1, 2, 3, JUNGLE, WATCHER, SCORCHERS, NEST, CIRCLE };
        for (int m : order) list.add(new ItemStack(item, 1, m));
        for (int i = ARENA; i < JUNGLE; i++) list.add(new ItemStack(item, 1, i));
        list.add(new ItemStack(item, 1, AYERACO));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.lair_map"));
        String boss = "dre.tooltip.map_boss." + KEYS[type(stack)]; // кто там призывается
        if (StatCollector.canTranslate(boss)) list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal(boss));
    }
}
