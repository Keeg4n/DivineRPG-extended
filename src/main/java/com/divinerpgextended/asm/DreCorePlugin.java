package com.divinerpgextended.asm;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Addon coremod: fixes the "infinite" Divine furnaces (Iceika coal furnace and the Molten, Oceanfire, Whitefire,
 * Demon furnaces) so that hoppers and pipes work with them ({@link FurnaceTransformer}), and removes the hardcoded arcana maximum of 200
 * ({@link ArcanaTransformer}), fixes full armor set properties ({@link ArmorSetTransformer}), turns off the Vethea inventory
 * swap in Divine without the vetheaInventory setting ({@link VetheaSwapTransformer}), speeds up the search for the paired
 * portal in the twilight worlds ({@link PortalTransformer}), places their trees inside the populated area
 * ({@link TreeShiftTransformer}) and marks the time of their population (for resending chunks whole), tilts the view in the Apalachia
 * Cart ({@link CameraTransformer}), keeps Ayeraco without siblings from crashing the save ({@link AyeracoTransformer}), keeps Iceika population out of the Frozen Throne, gives Divine bosses boss status ({@link BossStatusTransformer})
 * ({@link IceikaThroneTransformer}), keeps Vethea generation from creating neighboring chunks in a chain ({@link VetheaGenTransformer}), fixes biome selection by the Blood Magic Gaia ritual ({@link BiomeRitualTransformer}), drives the infusion altar, L Heiva and the Vethea Miner's Amulet ({@link VetheaTransformer}), paints Eden water gold ({@link WaterTransformer}), routes arcana spending through the addon rules ({@link ArcanaUseTransformer}), keeps Morph from throwing the player out of the world on Divine models ({@link MorphModelTransformer}), chooses the mob kind for spawning from those allowed by rank ({@link SpawnTransformer}), lets bows and shooters fire with ammo loaded in the weapon itself ({@link AmmoTransformer}), hides the real durability of protected blocks from other mods' miners ({@link MiningGuardTransformer}), routes other mods' area breaking through the break event ({@link AreaBreakTransformer}), silences other mods' Baubles accessories ({@link BaublesTransformer}) and the tick of other mods' armor ({@link ArmorTickTransformer}) in the dream; before
 * mods load it separates Divine biome IDs from Biomes O Plenty ({@link BiomeIdGuard}).
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("DivineRPG Extended Core")
@IFMLLoadingPlugin.TransformerExclusions({ "com.divinerpgextended.asm" })
public class DreCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { FurnaceTransformer.class.getName(), ArcanaTransformer.class.getName(),
            ArmorSetTransformer.class.getName(), VetheaSwapTransformer.class.getName(),
            PortalTransformer.class.getName(), TreeShiftTransformer.class.getName(), CameraTransformer.class.getName(),
            AyeracoTransformer.class.getName(), IceikaThroneTransformer.class.getName(), BossStatusTransformer.class.getName(),
            NearChatTransformer.class.getName(), VetheaGenTransformer.class.getName(), BiomeRitualTransformer.class.getName(),
            VetheaTransformer.class.getName(), WaterTransformer.class.getName(), ArcanaUseTransformer.class.getName(),
            MorphModelTransformer.class.getName(), TeleportTransformer.class.getName(), BiomeMutationTransformer.class.getName(), EasterArmorTransformer.class.getName(), WatcherTransformer.class.getName(),
            SpawnTransformer.class.getName(), AmmoTransformer.class.getName(), MiningGuardTransformer.class.getName(), GogglesTransformer.class.getName(), MoverTransformer.class.getName(),
            AmmoCountTransformer.class.getName(), ThrowableTransformer.class.getName(), LightningTransformer.class.getName(), AttractorTransformer.class.getName(), DiskTransformer.class.getName(),
            AreaBreakTransformer.class.getName(), BaublesTransformer.class.getName(), ArmorTickTransformer.class.getName() };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // before mods load: Divine biome IDs must not coincide with biomes of other mods (Biomes O Plenty)
        Object dir = data.get("mcLocation");
        if (dir instanceof java.io.File) BiomeIdGuard.run((java.io.File) dir);
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
