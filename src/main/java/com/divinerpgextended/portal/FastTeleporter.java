package com.divinerpgextended.portal;

import net.divinerpg.dimensions.base.DivineTeleporter;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Телепорт порталов Divine (сумеречные миры и выход из них). Divine ищет парный портал перебором 257x257 блоков на всю
 * высоту мира (около 17 млн обращений к миру) и создаёт телепорт заново на каждый вход - кэш пуст, перебор каждый раз,
 * а заодно генерирует все 289 чанков вокруг. Здесь тот же поиск (ближайший блок портала, нижний в столбе, тот же
 * радиус), но по массивам чанков с пропуском пустых секций и только в уже существующих чанках: в несгенерированном
 * портала быть не может. Найденное место кладём в кэш Divine и отдаём его же коду - расстановка игрока прежняя.
 * Подменяется в BlockModPortal ({@link PortalTransformer}).
 */
public class FastTeleporter extends DivineTeleporter {

    private static final int RADIUS = 128;

    public FastTeleporter(WorldServer world, int dimensionID, Block portal, Block frameBlock) {
        super(world, dimensionID, portal, frameBlock);
    }

    @Override
    public boolean placeInExistingPortal(Entity entity, double x, double y, double z, float rotation) {
        int ex = MathHelper.floor_double(entity.posX), ez = MathHelper.floor_double(entity.posZ);
        long key = ChunkCoordIntPair.chunkXZ2Int(ex, ez);
        if (!field_85191_c.containsItem(key)) {
            int[] found;
            try {
                found = find(entity, ex, ez);
            } catch (Throwable t) {
                return super.placeInExistingPortal(entity, x, y, z, rotation); // что-то не так - поиск Divine
            }
            if (found == null) return false;
            field_85191_c.add(key, this.new PortalPosition(found[0], found[1], found[2], field_85192_a.getTotalWorldTime()));
            field_85190_d.add(key);
        }
        return super.placeInExistingPortal(entity, x, y, z, rotation);
    }

    /** Новый портал на этой стороне: его рамку (и площадку) отмечаем - добытая, она ничего не роняет ({@link PortalFrames}). */
    @Override
    public boolean makePortal(Entity entity) {
        int ex = MathHelper.floor_double(entity.posX), ez = MathHelper.floor_double(entity.posZ), r = 20;
        java.util.Set<Long> before = PortalFrames.snapshot(field_85192_a, frameBlock, ex, ez, r);
        boolean made = super.makePortal(entity);
        PortalFrames.mark(field_85192_a, frameBlock, before, ex, ez, r);
        return made;
    }

    /** Ближайший блок портала (нижний в своём столбе) в квадрате радиуса 128, как у Divine; null - нет. */
    int[] find(Entity entity, int ex, int ez) {
        ChunkProviderServer cps = field_85192_a.theChunkProviderServer;
        IChunkLoader loader = ReflectionHelper.getPrivateValue(ChunkProviderServer.class, cps, "currentChunkLoader", "field_73247_e");
        double best = -1;
        int[] at = null;
        for (int cx = (ex - RADIUS) >> 4; cx <= (ex + RADIUS) >> 4; cx++) {
            for (int cz = (ez - RADIUS) >> 4; cz <= (ez + RADIUS) >> 4; cz++) {
                if (!cps.chunkExists(cx, cz)
                    && !(loader instanceof AnvilChunkLoader && ((AnvilChunkLoader) loader).chunkExists(field_85192_a, cx, cz))) continue;
                Chunk c = field_85192_a.getChunkFromChunkCoords(cx, cz);
                ExtendedBlockStorage[] secs = c.getBlockStorageArray();
                for (int si = 0; si < secs.length; si++) {
                    ExtendedBlockStorage s = secs[si];
                    if (s == null || s.isEmpty()) continue;
                    for (int ly = 0; ly < 16; ly++) {
                        for (int lx = 0; lx < 16; lx++) {
                            int bx = (cx << 4) + lx;
                            if (Math.abs(bx - ex) > RADIUS) continue;
                            for (int lz = 0; lz < 16; lz++) {
                                if (s.getBlockByExtId(lx, ly, lz) != portal) continue;
                                int bz = (cz << 4) + lz, by = s.getYLocation() + ly;
                                if (Math.abs(bz - ez) > RADIUS) continue;
                                if (by > 0 && c.getBlock(lx, by - 1, lz) == portal) continue; // не нижний в столбе
                                double dx = bx + 0.5 - entity.posX, dy = by + 0.5 - entity.posY, dz = bz + 0.5 - entity.posZ;
                                double d = dx * dx + dy * dy + dz * dz;
                                if (best < 0 || d < best) {
                                    best = d;
                                    at = new int[] { bx, by, bz };
                                }
                            }
                        }
                    }
                }
            }
        }
        return at;
    }
}
