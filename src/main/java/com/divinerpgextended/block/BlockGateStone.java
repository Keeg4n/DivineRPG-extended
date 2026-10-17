package com.divinerpgextended.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import com.divinerpgextended.world.CadillionWorld;

/**
 * Камень врат: рамка портала в следующий сумеречный мир (камень врат Эдема - в Дикий лес и т.д.). Собирается из
 * блоков камней мира вокруг сердца стража - его роняет Великий кадиллион этого мира.
 */
public class BlockGateStone extends Block {

    public final CadillionWorld world;

    public BlockGateStone(CadillionWorld world) {
        super(Material.rock);
        this.world = world;
        setHardness(3.0F);
        setResistance(30.0F);
        setLightLevel(0.4F);
        setStepSound(soundTypeStone);
        setBlockName("dre.gateStone." + world.key());
        setBlockTextureName("divinerpgextended:gate_stone_" + world.key());
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }
}
