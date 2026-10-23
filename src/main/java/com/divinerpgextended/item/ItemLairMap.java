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
        "archer", "fiend", "vamacheron" };
    private static final int[] COLORS = { 0x4FA3C8, 0xDDEEFF, 0x6FC8FF, 0xE8C060, 0x7FD15A, 0xB0302A, 0x7A2E4E, 0xF07A20, 0xD0C890, 0xE0E0E0, 0x9050C0,
        0xC09040, 0x50A070, 0xD8D0B8 };
    /** Карты к аренам Мортума (кроме Демонического круга - CIRCLE): близнецы, Карот, лучник, Пожиратель, Вамачерон. */
    public static final int ARENA = 9;

    /** Мета карты к арене этого вида. */
    public static int arenaMap(com.divinerpgextended.world.twilight.MortumArenas.Type t) {
        return t == com.divinerpgextended.world.twilight.MortumArenas.Type.DEMON ? CIRCLE : ARENA + t.ordinal() - 1;
    }

    private static com.divinerpgextended.world.twilight.MortumArenas.Type arenaOf(int t) {
        if (t == CIRCLE) return com.divinerpgextended.world.twilight.MortumArenas.Type.DEMON;
        return t >= ARENA ? com.divinerpgextended.world.twilight.MortumArenas.Type.values()[t - ARENA + 1] : null;
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
        int t = type(stack);
        // перезарядка: поиск по карте биомов нагружает сервер
        long now = world.getTotalWorldTime(), last = player.getEntityData().getLong(COOLDOWN);
        long cd = com.divinerpgextended.Config.mapCooldownSeconds * 20L;
        if (cd > 0 && now - last < cd && now >= last) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_cooldown", (cd - (now - last) + 19) / 20));
            return stack;
        }
        player.getEntityData().setLong(COOLDOWN, now);
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
            int[] c = com.divinerpgextended.world.WorldGenThrone.nearest(world.getSeed(), player.posX, player.posZ);
            if (c == null) {
                player.addChatMessage(new ChatComponentTranslation("dre.chat.map_none_" + KEYS[t]));
                return stack;
            }
            int[] lair = new int[] { t, c[0], com.divinerpgextended.world.WorldGenThrone.TOP + 1, c[2] - com.divinerpgextended.world.WorldGenThrone.CITADEL };
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_found_" + KEYS[t], distance(player, lair[1], lair[3]),
                direction(player, lair[1], lair[3]), lair[2], lair[1], lair[3]));
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
            double d = (l[1] - px) * (l[1] - px) + (l[3] - pz) * (l[3] - pz);
            if (d <= (double) skip * skip || d > (double) radius * radius || d >= builtD) continue;
            builtD = d;
            built = l;
        }
        int[] site = com.divinerpgextended.world.LairSites.nearest(world, t, px, pz, radius, skip,
            s -> !data.near(t, s[0], s[1], 96) && !data.near(t + LairData.FAILED, s[0], s[1], 24), deadline);
        if (site == com.divinerpgextended.world.LairSites.UNFINISHED) return false;
        double siteD = site == null ? Double.MAX_VALUE : (site[0] - px) * (site[0] - px) + (site[1] - pz) * (site[1] - pz);
        if (built == null && site == null) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_none_" + KEYS[t], radius));
            return true;
        }
        if (built != null && builtD <= siteD) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_found_" + KEYS[t], distance(player, built[1], built[3]),
                direction(player, built[1], built[3]), built[2], built[1], built[3]));
        } else {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_site_" + KEYS[t], distance(player, site[0], site[1]),
                direction(player, site[0], site[1]), site[0], site[1]));
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
        // арены Мортума - каждая карта к своей (ближайшей арене этого вида)
        int[] c = arena != null ? com.divinerpgextended.world.twilight.MortumArenas.nearestOf(world, arena, player.posX, player.posZ, 8)
            : com.divinerpgextended.world.twilight.WorldGenNests.nearest(world.getSeed(), world.provider.dimensionId, player.posX, player.posZ);
        if (c == null) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_none_" + KEYS[t]));
            return stack;
        }
        int floor = arena != null ? com.divinerpgextended.world.twilight.MortumArenas.floorY(arena) + 1 : c[1] + 1;
        player.addChatMessage(new ChatComponentTranslation("dre.chat.map_found_" + KEYS[t], distance(player, c[0], c[2]),
            direction(player, c[0], c[2]), floor, c[0], c[2]));
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
        int[] c = com.divinerpgextended.world.WorldGenOuterLairs.nearest(world.getSeed(), type, player.posX, player.posZ);
        player.addChatMessage(new ChatComponentTranslation("dre.chat.map_found_" + KEYS[t], distance(player, c[0], c[2]),
            direction(player, c[0], c[2]), c[1] + 1, c[0], c[2]));
        return stack;
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
        int[] order = { 0, 1, 2, 3, WATCHER, SCORCHERS, NEST, CIRCLE };
        for (int m : order) list.add(new ItemStack(item, 1, m));
        for (int i = ARENA; i < KEYS.length; i++) list.add(new ItemStack(item, 1, i));
        list.add(new ItemStack(item, 1, AYERACO));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.lair_map"));
    }
}
