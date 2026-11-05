package com.divinerpgextended.perk;

import java.util.IdentityHashMap;
import java.util.Map;

import net.divinerpg.utils.blocks.TwilightBlocks;
import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.world.twilight.TwilightWorld;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Блоки с правилами добычи (руды по уровням кирок, блоки Арканы) для всех неломаемые, настоящая прочность - только для
 * ломки игроком: на неё указывает {@link com.divinerpgextended.asm.MiningGuardTransformer} в ForgeHooks.blockStrength, а
 * какой инструмент подходит, решают события ломки. Иначе правила обходит всё, что копает без этих событий и смотрит только
 * на прочность: связанная кирка Blood Magic, бур Thaumcraft, поршни и другие.
 */
public final class MiningGuard {

    private static final Map<Block, Float> REAL = new IdentityHashMap<Block, Float>();

    private MiningGuard() {}

    /** Настоящая прочность блока (для всех остальных блоков - обычная). */
    public static float real(Block b, World w, int x, int y, int z) {
        Float f = REAL.get(b);
        return f != null ? f : b.getBlockHardness(w, x, y, z);
    }

    /** Защитить блок: real - прочность при ломке игроком. */
    public static void guard(Block b, float real) {
        if (b != null) REAL.put(b, real);
    }

    private static float field(Block b) {
        return (Float) ReflectionHelper.getPrivateValue(Block.class, b, "blockHardness", "field_149782_v");
    }

    /** Руды и камень с правилами аддона: прочность снимается родная. Блоки Арканы добавляет {@link com.divinerpgextended.item.ItemArcanePick}. */
    public static void guardOres() {
        guard(ModBlocks.jungleOre, field(ModBlocks.jungleOre));
        if (!Config.miningTiers) return;
        for (TwilightWorld w : TwilightWorld.values()) if (w.ore() != null) guard(w.ore(), field(w.ore()));
        guard(TwilightBlocks.twilightStone, field(TwilightBlocks.twilightStone));
    }

    /**
     * Поставить неломаемость при запуске сервера (и встроенного): копают чужие инструменты только на сервере, а у клиента
     * прочность остаётся родной, и подсказка Waila в сетевой игре не пишет про руду "неломаемая".
     */
    public static void apply() {
        for (Block b : REAL.keySet()) ReflectionHelper.setPrivateValue(Block.class, b, -1F, "blockHardness", "field_149782_v");
    }
}
