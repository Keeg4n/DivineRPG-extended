package com.divinerpgextended.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * Planks from Vethea wood (Divine's Vethea logs have no planks): a plank in 4 from a log, in the ore dictionary as plankWood - from them
 * everything made from planks (sticks, crafting table, chest, wooden tools).
 */
public class BlockVetheaPlanks extends Block {

    /** Vethea wood: texture key and Divine log. */
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
