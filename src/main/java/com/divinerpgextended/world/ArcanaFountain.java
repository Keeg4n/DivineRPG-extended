package com.divinerpgextended.world;

import java.util.Random;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * Фонтанный зал Арканы: в Аркане каждый чанк - четыре этажа комнат 16 x 16 высотой 8. Изредка ({@link Config#arcanaFountainChance})
 * одна обычная комната (без сундуков и спавнеров - не тайник и не логово босса) в новом чанке перестраивается: стены и проходы
 * остаются, внутри - чистый пол и фонтан с чашей арканного концентрата ({@link com.divinerpgextended.block.BlockArcaneConcentrate}):
 * в нём восстанавливается аркана и ловится рыба Арканы.
 */
public class ArcanaFountain implements IWorldGenerator {

    public static final int LEVELS = 4, H = 8, IN0 = 2, IN1 = 13;
    /** Для самопроверки: сколько залов построено. */
    public static int built;

    @Override
    public void generate(Random rand, int cx, int cz, World w, IChunkProvider gen, IChunkProvider prov) {
        if (w.provider.dimensionId != ConfigurationHelper.arcana || Config.arcanaFountainChance <= 0) return;
        Random r = new Random(w.getSeed() ^ cx * 341873128712L ^ cz * 132897987541L ^ 0xA3CA4AL);
        if (r.nextInt(Config.arcanaFountainChance) != 0) return;
        int level = r.nextInt(LEVELS);
        build(w, cx * 16, level * H, cz * 16);
    }

    /** Комната без сундуков и спавнеров (тайники и логова боссов не трогаем). */
    static boolean plain(World w, int x0, int y0, int z0) {
        for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) for (int y = y0; y < y0 + H; y++)
            if (w.getTileEntity(x, y, z) != null || w.getBlock(x, y, z) == net.divinerpg.utils.blocks.ArcanaBlocks.dramixAltar
                || w.getBlock(x, y, z) == net.divinerpg.utils.blocks.ArcanaBlocks.parasectaAltar) return false;
        return true;
    }

    /** Перестроить комнату (x0, y0 - пол, z0) в фонтанный зал. false - комната не подошла. */
    public static boolean build(World w, int x0, int y0, int z0) {
        if (!plain(w, x0, y0, z0)) return false;
        Block floor = w.getBlock(x0 + 8, y0, z0 + 8);
        if (floor.isAir(w, x0 + 8, y0, z0 + 8)) floor = net.divinerpg.utils.blocks.ArcanaBlocks.ancientTile;
        Block rim = net.divinerpg.utils.blocks.ArcanaBlocks.ancientBrick, lamp = net.divinerpg.utils.blocks.ArcanaBlocks.dungenLamp;
        // внутри - пусто, пол ровный (проходы в стенах не трогаем)
        for (int x = x0 + IN0; x <= x0 + IN1; x++) for (int z = z0 + IN0; z <= z0 + IN1; z++) {
            w.setBlock(x, y0, z, floor, 0, 2);
            for (int y = y0 + 1; y < y0 + H - 1; y++) w.setBlock(x, y, z, Blocks.air, 0, 2);
        }
        // чаша 8 x 8: бортик, внутри концентрат; в центре - столб с огнём арканы
        int c0 = x0 + 4, c1 = x0 + 11, d0 = z0 + 4, d1 = z0 + 11;
        for (int x = c0; x <= c1; x++) for (int z = d0; z <= d1; z++) {
            boolean edge = x == c0 || x == c1 || z == d0 || z == d1;
            boolean pillar = (x == x0 + 7 || x == x0 + 8) && (z == z0 + 7 || z == z0 + 8);
            if (edge) w.setBlock(x, y0 + 1, z, rim, 0, 2);
            else if (pillar) {
                for (int y = y0 + 1; y <= y0 + 3; y++) w.setBlock(x, y, z, rim, 0, 2);
                w.setBlock(x, y0 + 4, z, lamp, 0, 2);
            } else w.setBlock(x, y0 + 1, z, ModBlocks.arcaneConcentrate, 0, 2);
        }
        // огни по углам зала
        for (int[] c : new int[][] { { IN0, IN0 }, { IN0, IN1 }, { IN1, IN0 }, { IN1, IN1 } }) w.setBlock(x0 + c[0], y0 + 1, z0 + c[1], lamp, 0, 2);
        built++;
        return true;
    }
}
