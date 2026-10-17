package com.divinerpgextended.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Лампа сумеречного мира - пара к лампе Эдема из Divine (та же мерцающая рамка, цвет мира). Светит в полную силу,
 * крафтится как у Divine: железо вокруг осколка мира. Ею освещены постройки аддона вместо глоустоуна.
 */
public class BlockWorldLamp extends Block {

    public final TwilightWorld world;

    public BlockWorldLamp(TwilightWorld world) {
        super(Material.glass);
        this.world = world;
        setHardness(3.0F);
        setResistance(10.0F);
        setLightLevel(1.0F);
        setStepSound(soundTypeGlass);
        setBlockName("dre.worldLamp." + world.key());
        setBlockTextureName("divinerpgextended:world_lamp_" + world.key());
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }
}
