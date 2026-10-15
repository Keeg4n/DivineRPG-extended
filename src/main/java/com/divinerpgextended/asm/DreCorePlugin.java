package com.divinerpgextended.asm;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Coremod аддона: чинит "бесконечные" печи Divine (угольная печь Iceika и печи Molten, Oceanfire, Whitefire,
 * Demon), чтобы с ними работали воронки и трубы ({@link FurnaceTransformer}), и снимает зашитый максимум арканы 200
 * ({@link ArcanaTransformer}).
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("DivineRPG Extended Core")
@IFMLLoadingPlugin.TransformerExclusions({ "com.divinerpgextended.asm" })
public class DreCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { FurnaceTransformer.class.getName(), ArcanaTransformer.class.getName() };
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
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
