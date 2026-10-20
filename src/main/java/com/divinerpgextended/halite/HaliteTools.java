package com.divinerpgextended.halite;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.EnumHelper;

/**
 * Халитовые инструменты: кирка, топор, лопата чуть быстрее инструментов Мортума; мультиинструмент (кирка, топор и
 * лопата в одном) - на 10 быстрее божественного шикакса, но медленнее отдельных халитовых. Руду джунглей берут только
 * халитовая кирка и мультиинструмент.
 */
public final class HaliteTools {

    /** Скорость копки: у кирки Мортума 55, у божественного шикакса 20. */
    public static final float SPEED = 60F, MULTI_SPEED = 30F;
    public static final Item.ToolMaterial HALITE = EnumHelper.addToolMaterial("DRE_HALITE", 100, 8000, SPEED, 6F, 22);
    public static final Item.ToolMaterial MULTI = EnumHelper.addToolMaterial("DRE_HALITE_MULTI", 100, 12000, MULTI_SPEED, 7F, 22);

    private HaliteTools() {}

    public static class Pickaxe extends ItemPickaxe {
        public Pickaxe() {
            super(HALITE);
            setUnlocalizedName("dre.halitePickaxe");
            setTextureName("divinerpgextended:halitePickaxe");
            setCreativeTab(com.divinerpgextended.DreTab.TAB);
        }
    }

    public static class Axe extends ItemAxe {
        public Axe() {
            super(HALITE);
            setUnlocalizedName("dre.haliteAxe");
            setTextureName("divinerpgextended:haliteAxe");
            setCreativeTab(com.divinerpgextended.DreTab.TAB);
        }
    }

    public static class Shovel extends ItemSpade {
        public Shovel() {
            super(HALITE);
            setUnlocalizedName("dre.haliteShovel");
            setTextureName("divinerpgextended:haliteShovel");
            setCreativeTab(com.divinerpgextended.DreTab.TAB);
        }
    }

    /** Мультиинструмент: кирка, топор и лопата (как шикакс Divine). */
    public static class Multitool extends ItemPickaxe {
        public Multitool() {
            super(MULTI);
            setHarvestLevel("pickaxe", 100);
            setHarvestLevel("axe", 100);
            setHarvestLevel("shovel", 100);
            setUnlocalizedName("dre.haliteMultitool");
            setTextureName("divinerpgextended:haliteMultitool");
            setCreativeTab(com.divinerpgextended.DreTab.TAB);
        }

        static boolean diggable(Block b) {
            Material m = b.getMaterial();
            return m == Material.rock || m == Material.iron || m == Material.anvil || m == Material.wood || m == Material.plants || m == Material.vine
                || m == Material.gourd || m == Material.ground || m == Material.grass || m == Material.sand || m == Material.snow
                || m == Material.craftedSnow || m == Material.clay || m == Material.leaves || m == Material.ice || m == Material.packedIce;
        }

        @Override
        public float func_150893_a(ItemStack s, Block b) {
            return diggable(b) ? efficiencyOnProperMaterial : super.func_150893_a(s, b);
        }

        @Override
        public float getDigSpeed(ItemStack s, Block b, int meta) {
            return diggable(b) ? efficiencyOnProperMaterial : super.getDigSpeed(s, b, meta);
        }

        @Override
        public boolean func_150897_b(Block b) {
            return diggable(b) || super.func_150897_b(b);
        }
    }

    /** Берёт руду джунглей: только халитовая кирка и мультиинструмент. */
    public static boolean minesJungle(ItemStack s) {
        return s != null && (s.getItem() instanceof Pickaxe || s.getItem() instanceof Multitool);
    }
}
