package com.divinerpgextended.asm;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Coremod аддона: чинит "бесконечные" печи Divine (угольная печь Iceika и печи Molten, Oceanfire, Whitefire,
 * Demon), чтобы с ними работали воронки и трубы ({@link FurnaceTransformer}), и снимает зашитый максимум арканы 200
 * ({@link ArcanaTransformer}), чинит свойства полных сетов брони ({@link ArmorSetTransformer}), выключает обмен
 * инвентаря Ветеи и в Divine без настройки vetheaInventory ({@link VetheaSwapTransformer}), ускоряет поиск парного
 * портала в сумеречных мирах ({@link PortalTransformer}), ставит их деревья внутрь заселяемой области
 * ({@link TreeShiftTransformer}) и отмечает время их заселения (для пересылки чанков целиком), наклоняет вид в вагонетке
 * Апалачии ({@link CameraTransformer}), не даёт Айрако без братьев ронять сохранение ({@link AyeracoTransformer}); до загрузки
 * модов разводит ID биомов Divine с Biomes O Plenty ({@link BiomeIdGuard}).
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
            AyeracoTransformer.class.getName() };
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
        // до загрузки модов: ID биомов Divine не должны совпадать с биомами других модов (Biomes O Plenty)
        Object dir = data.get("mcLocation");
        if (dir instanceof java.io.File) BiomeIdGuard.run((java.io.File) dir);
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
