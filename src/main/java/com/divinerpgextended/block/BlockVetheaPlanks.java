package com.divinerpgextended.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * Доски из дерева Ветеи (у Divine у брёвен Ветеи досок нет): доска в 4 из бревна, в рудном словаре plankWood - из них
 * всё, что из досок (палки, верстак, сундук, деревянные инструменты).
 */
public class BlockVetheaPlanks extends Block {

    /** Дерево Ветеи: ключ текстуры и бревно Divine. */
    public enum Wood {

        DREAMWOOD, HYREWOOD, FIREWOOD, MINTWOOD;

        public String key() {
            return name().toLowerCase();
        }

        public Block log() {
            switch (this) {
                case HYREWOOD: return net.divinerpg.utils.blocks.VetheaBlocks.hyrewood;
                case FIREWOOD: return net.divinerpg.utils.blocks.VetheaBlocks.firewood;
                case MINTWOOD: return net.divinerpg.utils.blocks.VetheaBlocks.mintwood;
                default: return net.divinerpg.utils.blocks.VetheaBlocks.dreamWoodLog;
            }
        }
    }

    public final Wood wood;

    public BlockVetheaPlanks(Wood wood) {
        super(Material.wood);
        this.wood = wood;
        setHardness(2.0F);
        setResistance(5.0F);
        setStepSound(soundTypeWood);
        setHarvestLevel("axe", 0);
        setBlockName("dre.vetheaPlanks." + wood.key());
        setBlockTextureName("divinerpgextended:vethea_planks_" + wood.key());
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }
}
