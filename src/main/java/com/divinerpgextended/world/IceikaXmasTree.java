package com.divinerpgextended.world;

import java.util.Random;

import net.divinerpg.utils.blocks.IceikaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.IceikaItems;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Новогодняя ёлка Iceika - редкая находка (примерно одна на {@link #RARITY} чанков): ель из мёрзлого бревна и ломкой
 * листвы ярусами, две спирали гирлянд Divine (красные, зелёные, синие, жёлтые, фиолетовые), на макушке звезда (лампа
 * мастерской на золоте), вокруг - леденцовые трости и снег, под ёлкой - подарочные коробки Divine с подарками: сладости
 * Iceika всегда, редко - вещь из костюма Санты, снежный шар, "Звук колядок".
 */
public class IceikaXmasTree implements IWorldGenerator {

    public static final int RARITY = 400, HEIGHT = 15, BASE_R = 6, PRESENTS = 4;
    /** Для автотеста и замеров. */
    public static int trees;
    private static final Block[] LIGHTS = { IceikaBlocks.redXmasLights, IceikaBlocks.greenXmasLights, IceikaBlocks.blueXmasLights,
        IceikaBlocks.yellowXmasLights, IceikaBlocks.purpleXmasLights };

    @Override
    public void generate(Random rand, int chunkX, int chunkZ, World world, IChunkProvider gen, IChunkProvider prov) {
        if (world.provider.dimensionId != ConfigurationHelper.iceika) return;
        Random r = new Random(world.getSeed() ^ chunkX * 341873128712L ^ chunkZ * 132897987541L ^ 0x0C0FFEEL);
        if (r.nextInt(RARITY) != 0) return;
        int x = chunkX * 16 + 16, z = chunkZ * 16 + 16; // середина области заселения: ёлка целиком в загруженных чанках
        // расчистка вокруг ёлки (радиус BASE_R+2, высота HEIGHT+3) выгрызла бы стену купола Ледяного Трона
        if (WorldGenThrone.near(world, x, z, BASE_R + 4)) return;
        if (build(world, r, x, z)) trees++;
    }

    /** Верх снежной земли (мёрзлая трава/земля, снег поверх не считаем) или -1. */
    static int ground(World w, int x, int z) {
        for (int y = 120; y > 30; y--) {
            Block b = w.getBlock(x, y, z);
            if (b == IceikaBlocks.frozenGrass || b == IceikaBlocks.frozenDirt || b == IceikaBlocks.frozenStone) return y;
        }
        return -1;
    }

    /** Ёлка с центром в (x, z); false - место неровное или нет земли. */
    public static boolean build(World w, Random r, int x, int z) {
        int y0 = ground(w, x, z);
        if (y0 < 0) return false;
        for (int i = -BASE_R; i <= BASE_R; i += 3) for (int k = -BASE_R; k <= BASE_R; k += 3) {
            int g = ground(w, x + i, z + k);
            if (g < 0 || Math.abs(g - y0) > 2) return false;
        }
        // площадка: снег поверх, выше - расчистить
        for (int i = -BASE_R - 2; i <= BASE_R + 2; i++) for (int k = -BASE_R - 2; k <= BASE_R + 2; k++) {
            if (i * i + k * k > (BASE_R + 2) * (BASE_R + 2)) continue;
            for (int y = y0 + 1; y <= y0 + HEIGHT + 3; y++) if (!w.isAirBlock(x + i, y, z + k)) w.setBlock(x + i, y, z + k, Blocks.air, 0, 2);
            w.setBlock(x + i, y0, z + k, IceikaBlocks.frozenGrass, 0, 2);
            if (r.nextInt(3) != 0) w.setBlock(x + i, y0 + 1, z + k, Blocks.snow_layer, r.nextInt(2), 2);
        }
        // ствол
        for (int y = y0 + 1; y <= y0 + HEIGHT - 1; y++) w.setBlock(x, y, z, IceikaBlocks.frozenWood, 0, 2);
        // ярусы: каждый ярус шире внизу, к верху конус сужается
        for (int l = 0; l < HEIGHT - 3; l++) {
            int y = y0 + 3 + l;
            double rad = (BASE_R + 0.4) * (1 - l / (double) (HEIGHT - 2)) - (l % 3) * 0.7;
            for (int i = -BASE_R; i <= BASE_R; i++) for (int k = -BASE_R; k <= BASE_R; k++) {
                if ((i != 0 || k != 0) && i * i + k * k <= rad * rad) w.setBlock(x + i, y, z + k, IceikaBlocks.brittleLeaves, 0, 2);
            }
        }
        w.setBlock(x, y0 + HEIGHT - 1, z, IceikaBlocks.brittleLeaves, 0, 2);
        // звезда
        w.setBlock(x, y0 + HEIGHT, z, Blocks.gold_block, 0, 2);
        w.setBlock(x, y0 + HEIGHT + 1, z, IceikaBlocks.workshopLamp, 0, 2);
        // гирлянды: две спирали по внешнему краю ярусов
        int lights = 0;
        for (int l = 0; l < HEIGHT - 4; l++) {
            int y = y0 + 3 + l;
            for (int s = 0; s < 2; s++) {
                double ang = l * 0.95 + s * Math.PI;
                lights += light(w, x, y, z, ang, LIGHTS[(l + s * 2) % LIGHTS.length]) ? 1 : 0;
            }
        }
        // подарки под ёлкой, трости вокруг
        int presents = 0;
        for (int i = 0; i < PRESENTS; i++) {
            double ang = i * Math.PI * 2 / PRESENTS + 0.4;
            int px = x + (int) Math.round(Math.cos(ang) * 2.2), pz = z + (int) Math.round(Math.sin(ang) * 2.2);
            w.setBlock(px, y0 + 1, pz, IceikaBlocks.presentBox, r.nextInt(4), 2);
            if (w.getTileEntity(px, y0 + 1, pz) instanceof IInventory) {
                gifts(r, (IInventory) w.getTileEntity(px, y0 + 1, pz));
                presents++;
            }
        }
        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI / 3;
            int cx = x + (int) Math.round(Math.cos(ang) * (BASE_R + 1.5)), cz = z + (int) Math.round(Math.sin(ang) * (BASE_R + 1.5));
            w.setBlock(cx, y0 + 1, cz, IceikaBlocks.candyCane, i % 4, 2);
        }
        return presents > 0 && lights > 10;
    }

    /**
     * Огонёк гирлянды на краю яруса под углом ang: ставится снаружи листвы и цепляется к ней (как лестница). Если в этой
     * стороне листвы нет - пропуск.
     */
    private static boolean light(World w, int x, int y, int z, double ang, Block light) {
        double c = Math.cos(ang), s = Math.sin(ang);
        for (int d = BASE_R + 1; d >= 1; d--) {
            int lx = x + (int) Math.round(c * d), lz = z + (int) Math.round(s * d);
            if (w.getBlock(lx, y, lz) != IceikaBlocks.brittleLeaves) continue;
            // клетка снаружи - по главной оси направления
            boolean alongX = Math.abs(c) >= Math.abs(s);
            int ox = alongX ? (c > 0 ? 1 : -1) : 0, oz = alongX ? 0 : (s > 0 ? 1 : -1);
            if (!w.isAirBlock(lx + ox, y, lz + oz)) return false;
            // мета лестницы: 2 - держится за блок с юга (z+1), 3 - с севера, 4 - с востока (x+1), 5 - с запада
            int meta = ox < 0 ? 4 : ox > 0 ? 5 : oz < 0 ? 2 : 3;
            w.setBlock(lx + ox, y, lz + oz, light, meta, 2);
            return true;
        }
        return false;
    }

    /** Подарок: сладости Iceika всегда, редко - вещь Санты, снежный шар или "Звук колядок". */
    static void gifts(Random r, IInventory inv) {
        Item[] sweets = { IceikaItems.peppermints, IceikaItems.chocolateLog, IceikaItems.fruitCake, IceikaItems.eggNog, IceikaItems.snowCones, IceikaItems.winterberry };
        int slot = 0;
        for (int i = 0; i < 2 + r.nextInt(3); i++) inv.setInventorySlotContents(slot++, new ItemStack(sweets[r.nextInt(sweets.length)], 1 + r.nextInt(4)));
        inv.setInventorySlotContents(slot++, new ItemStack(IceikaItems.snowflake, 2 + r.nextInt(6)));
        int roll = r.nextInt(10);
        if (roll < 3) {
            Item[] santa = { IceikaItems.santaCap, IceikaItems.santaTunic, IceikaItems.santaPants, IceikaItems.santaBoots };
            inv.setInventorySlotContents(slot++, new ItemStack(santa[r.nextInt(santa.length)]));
        } else if (roll < 5) inv.setInventorySlotContents(slot++, new ItemStack(IceikaItems.snowGlobe));
        else if (roll == 5) inv.setInventorySlotContents(slot++, new ItemStack(IceikaItems.soundOfCarols));
        else if (roll < 8) inv.setInventorySlotContents(slot++, new ItemStack(IceikaItems.iceStone, 1 + r.nextInt(3)));
    }
}
