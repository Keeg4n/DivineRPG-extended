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
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;

import com.divinerpgextended.world.LairData;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Карта логова: правый клик пишет в чат расстояние, направление и глубину до ближайшего логова. Мета 0 - логово
 * Карцинуса, 1 - снежное подземелье. Знает только уже сгенерированные логова; если их нет, показывает ближайший
 * подходящий биом (океан или снежные земли) - биомы известны и в ещё не исследованных краях.
 */
public class ItemLairMap extends Item {

    private static final String[] KEYS = { "carcinus", "snow", "throne", "tomb", "nest", "circle", "watcher", "scorchers", "ayeraco" };
    private static final int[] COLORS = { 0x4FA3C8, 0xDDEEFF, 0x6FC8FF, 0xE8C060, 0x7FD15A, 0xB0302A, 0x7A2E4E, 0xF07A20, 0xD0C890 };
    /** Мета карт к логовам Ада и Энда: Смотритель, Король скорчеров, орда Айрако. */
    public static final int WATCHER = 6, SCORCHERS = 7, AYERACO = 8;
    /** Мета карты к гнезду Великого кадиллиона (в его мире) и к Демоническому кругу (Мортум). */
    public static final int NEST = 4, CIRCLE = 5;
    private static final String[] DIRS = { "s", "sw", "w", "nw", "n", "ne", "e", "se" };

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
        if (t == NEST || t == CIRCLE) return twilight(stack, world, player, t);
        if (t >= WATCHER) return outer(world, player, t);
        // логова - в обычном мире, Ледяной Трон - в Iceika
        int dim = t == LairData.THRONE ? net.divinerpg.utils.config.ConfigurationHelper.iceika : 0;
        if (world.provider.dimensionId != dim) {
            player.addChatMessage(new ChatComponentTranslation(t == LairData.THRONE ? "dre.chat.map_iceika"
                : "dre.chat.map_overworld"));
            return stack;
        }
        // место Трона вычисляется из сида - карта знает его заранее
        int[] lair;
        if (t == LairData.THRONE) {
            int[] c = com.divinerpgextended.world.WorldGenThrone.nearest(world.getSeed(), player.posX, player.posZ);
            lair = c == null ? null
                : new int[] { t, c[0], com.divinerpgextended.world.WorldGenThrone.TOP + 1,
                    c[2] - com.divinerpgextended.world.WorldGenThrone.CITADEL };
        } else {
            lair = LairData.get(world)
                .nearest(t, player.posX, player.posZ);
        }
        if (lair == null) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_none_" + KEYS[t]));
            if (t == LairData.THRONE) return stack; // в Iceika один биом, подсказывать нечего
            ChunkPosition biome = findBiome(world, player, t);
            if (biome == null) {
                player.addChatMessage(new ChatComponentTranslation("dre.chat.map_no_biome_" + KEYS[t]));
            } else {
                player.addChatMessage(new ChatComponentTranslation("dre.chat.map_biome_" + KEYS[t],
                    distance(player, biome.chunkPosX, biome.chunkPosZ), direction(player, biome.chunkPosX, biome.chunkPosZ),
                    biome.chunkPosX, biome.chunkPosZ));
            }
            return stack;
        }
        player.addChatMessage(new ChatComponentTranslation("dre.chat.map_found_" + KEYS[t], distance(player, lair[1], lair[3]),
            direction(player, lair[1], lair[3]), lair[2], lair[1], lair[3]));
        return stack;
    }

    /** Гнёзда и круг - из сида мира, карта знает их заранее (как Трон). */
    private static ItemStack twilight(ItemStack stack, World world, EntityPlayer player, int t) {
        com.divinerpgextended.world.twilight.TwilightWorld tw = com.divinerpgextended.world.twilight.TwilightWorld.of(world.provider.dimensionId);
        boolean ok = tw != null && (t == CIRCLE) == (tw == com.divinerpgextended.world.twilight.TwilightWorld.MORTUM);
        if (!ok) {
            player.addChatMessage(new ChatComponentTranslation(t == CIRCLE ? "dre.chat.map_mortum" : "dre.chat.map_cadillion_world"));
            return stack;
        }
        int[] c = com.divinerpgextended.world.twilight.WorldGenNests.nearest(world.getSeed(), world.provider.dimensionId, player.posX, player.posZ);
        if (c == null) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.map_none_" + KEYS[t]));
            return stack;
        }
        player.addChatMessage(new ChatComponentTranslation("dre.chat.map_found_" + KEYS[t], distance(player, c[0], c[2]),
            direction(player, c[0], c[2]), c[1] + 1, c[0], c[2]));
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

    private static final int SEARCH_STEP = 48, SEARCH_RADIUS = 4000;

    /**
     * Ближайший биом, где может появиться логово. Обходим квадратные кольца вокруг игрока с шагом 48 блоков и
     * спрашиваем биом в каждой точке (через кэш биомов, без генерации чанков). Ванильный findBiomePosition строит
     * карту биомов на всю площадь сразу и на больших радиусах съедает всю память.
     */
    private static ChunkPosition findBiome(World world, EntityPlayer player, int t) {
        WorldChunkManager wcm = world.getWorldChunkManager();
        int x = MathHelper.floor_double(player.posX), z = MathHelper.floor_double(player.posZ);
        for (int ring = 0; ring * SEARCH_STEP <= SEARCH_RADIUS; ring++) {
            ChunkPosition best = null;
            long bestDist = Long.MAX_VALUE;
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.abs(i) != ring && Math.abs(j) != ring) continue; // только периметр кольца
                    int px = x + i * SEARCH_STEP, pz = z + j * SEARCH_STEP;
                    if (!suits(wcm.getBiomeGenAt(px, pz), t)) continue;
                    long d = (long) (px - x) * (px - x) + (long) (pz - z) * (pz - z);
                    if (d < bestDist) {
                        bestDist = d;
                        best = new ChunkPosition(px, 0, pz);
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static boolean suits(BiomeGenBase b, int t) {
        if (b == null) return false;
        boolean ocean = b == BiomeGenBase.ocean || b == BiomeGenBase.deepOcean || b == BiomeGenBase.frozenOcean;
        if (t == 0) return b == BiomeGenBase.beach || b == BiomeGenBase.coldBeach; // нора краба - у берега
        if (t == LairData.TOMB) return b == BiomeGenBase.desert || b == BiomeGenBase.desertHills; // гробница - под пирамидой
        // снежная суша; замёрзшая река не подходит - под ней вода
        return !ocean && b != BiomeGenBase.frozenRiver && b.getEnableSnow();
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, 0));
        list.add(new ItemStack(item, 1, 1));
        list.add(new ItemStack(item, 1, 2));
        list.add(new ItemStack(item, 1, 3));
        list.add(new ItemStack(item, 1, NEST));
        list.add(new ItemStack(item, 1, CIRCLE));
        list.add(new ItemStack(item, 1, WATCHER));
        list.add(new ItemStack(item, 1, SCORCHERS));
        list.add(new ItemStack(item, 1, AYERACO));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.lair_map"));
    }
}
