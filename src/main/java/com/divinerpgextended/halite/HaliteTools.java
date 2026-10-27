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
 * Халитовые инструменты: кирка, топор, лопата чуть быстрее инструментов Мортума; мультиинструмент (кирка, топор,
 * лопата и мотыга в одном: правой кнопкой пашет) - так же быстр, как отдельные халитовые. Руду джунглей берут только
 * халитовая кирка и мультиинструмент. Не ломаются.
 */
public final class HaliteTools {

    /** Скорость копки: у кирки Мортума 55, у божественного шикакса 20. */
    public static final float SPEED = 60F, MULTI_SPEED = 60F;
    public static final Item.ToolMaterial HALITE = EnumHelper.addToolMaterial("DRE_HALITE", 100, 8000, SPEED, 6F, 22);
    public static final Item.ToolMaterial MULTI = EnumHelper.addToolMaterial("DRE_HALITE_MULTI", 100, 12000, MULTI_SPEED, 7F, 22);

    private HaliteTools() {}

    public static class Pickaxe extends ItemPickaxe {
        public Pickaxe() {
            super(HALITE);
            setMaxDamage(0);
            setUnlocalizedName("dre.halitePickaxe");
            setTextureName("divinerpgextended:halitePickaxe");
            setCreativeTab(com.divinerpgextended.DreTab.TAB);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void addInformation(ItemStack s, net.minecraft.entity.player.EntityPlayer p, java.util.List list, boolean adv) {
            com.divinerpgextended.item.ToolInfo.add(list, s, efficiencyOnProperMaterial);
        }

        /** Мотыга: как ванильная (трава и земля - в пашню, событие UseHoeEvent для других модов). */
        @Override
        public boolean onItemUse(ItemStack s, net.minecraft.entity.player.EntityPlayer p, net.minecraft.world.World w, int x, int y, int z, int side,
            float hx, float hy, float hz) {
            if (!p.canPlayerEdit(x, y, z, side, s)) return false;
            net.minecraftforge.event.entity.player.UseHoeEvent ev = new net.minecraftforge.event.entity.player.UseHoeEvent(p, s, w, x, y, z);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(ev)) return false;
            if (ev.getResult() == cpw.mods.fml.common.eventhandler.Event.Result.ALLOW) return true;
            Block b = w.getBlock(x, y, z);
            if (side == 0 || !w.getBlock(x, y + 1, z).isAir(w, x, y + 1, z) || b != net.minecraft.init.Blocks.grass && b != net.minecraft.init.Blocks.dirt)
                return false;
            Block farm = net.minecraft.init.Blocks.farmland;
            w.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, farm.stepSound.getStepResourcePath(), (farm.stepSound.getVolume() + 1) / 2,
                farm.stepSound.getPitch() * 0.8F);
            if (!w.isRemote) w.setBlock(x, y, z, farm);
            return true;
        }
    }

    public static class Axe extends ItemAxe {
        public Axe() {
            super(HALITE);
            setMaxDamage(0);
            setUnlocalizedName("dre.haliteAxe");
            setTextureName("divinerpgextended:haliteAxe");
            setCreativeTab(com.divinerpgextended.DreTab.TAB);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void addInformation(ItemStack s, net.minecraft.entity.player.EntityPlayer p, java.util.List list, boolean adv) {
            com.divinerpgextended.item.ToolInfo.add(list, s, efficiencyOnProperMaterial);
        }

        /** Мотыга: как ванильная (трава и земля - в пашню, событие UseHoeEvent для других модов). */
        @Override
        public boolean onItemUse(ItemStack s, net.minecraft.entity.player.EntityPlayer p, net.minecraft.world.World w, int x, int y, int z, int side,
            float hx, float hy, float hz) {
            if (!p.canPlayerEdit(x, y, z, side, s)) return false;
            net.minecraftforge.event.entity.player.UseHoeEvent ev = new net.minecraftforge.event.entity.player.UseHoeEvent(p, s, w, x, y, z);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(ev)) return false;
            if (ev.getResult() == cpw.mods.fml.common.eventhandler.Event.Result.ALLOW) return true;
            Block b = w.getBlock(x, y, z);
            if (side == 0 || !w.getBlock(x, y + 1, z).isAir(w, x, y + 1, z) || b != net.minecraft.init.Blocks.grass && b != net.minecraft.init.Blocks.dirt)
                return false;
            Block farm = net.minecraft.init.Blocks.farmland;
            w.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, farm.stepSound.getStepResourcePath(), (farm.stepSound.getVolume() + 1) / 2,
                farm.stepSound.getPitch() * 0.8F);
            if (!w.isRemote) w.setBlock(x, y, z, farm);
            return true;
        }
    }

    public static class Shovel extends ItemSpade {
        public Shovel() {
            super(HALITE);
            setMaxDamage(0);
            setUnlocalizedName("dre.haliteShovel");
            setTextureName("divinerpgextended:haliteShovel");
            setCreativeTab(com.divinerpgextended.DreTab.TAB);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void addInformation(ItemStack s, net.minecraft.entity.player.EntityPlayer p, java.util.List list, boolean adv) {
            com.divinerpgextended.item.ToolInfo.add(list, s, efficiencyOnProperMaterial);
        }

        /** Мотыга: как ванильная (трава и земля - в пашню, событие UseHoeEvent для других модов). */
        @Override
        public boolean onItemUse(ItemStack s, net.minecraft.entity.player.EntityPlayer p, net.minecraft.world.World w, int x, int y, int z, int side,
            float hx, float hy, float hz) {
            if (!p.canPlayerEdit(x, y, z, side, s)) return false;
            net.minecraftforge.event.entity.player.UseHoeEvent ev = new net.minecraftforge.event.entity.player.UseHoeEvent(p, s, w, x, y, z);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(ev)) return false;
            if (ev.getResult() == cpw.mods.fml.common.eventhandler.Event.Result.ALLOW) return true;
            Block b = w.getBlock(x, y, z);
            if (side == 0 || !w.getBlock(x, y + 1, z).isAir(w, x, y + 1, z) || b != net.minecraft.init.Blocks.grass && b != net.minecraft.init.Blocks.dirt)
                return false;
            Block farm = net.minecraft.init.Blocks.farmland;
            w.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, farm.stepSound.getStepResourcePath(), (farm.stepSound.getVolume() + 1) / 2,
                farm.stepSound.getPitch() * 0.8F);
            if (!w.isRemote) w.setBlock(x, y, z, farm);
            return true;
        }
    }

    /** Мультиинструмент: кирка, топор и лопата (как шикакс Divine). */
    public static class Multitool extends ItemPickaxe {
        public Multitool() {
            super(MULTI);
            setHarvestLevel("pickaxe", 100);
            setHarvestLevel("axe", 100);
            setHarvestLevel("shovel", 100);
            setMaxDamage(0);
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

        @Override
        @SuppressWarnings("rawtypes")
        public void addInformation(ItemStack s, net.minecraft.entity.player.EntityPlayer p, java.util.List list, boolean adv) {
            com.divinerpgextended.item.ToolInfo.add(list, s, efficiencyOnProperMaterial);
        }

        /** Мотыга: как ванильная (трава и земля - в пашню, событие UseHoeEvent для других модов). */
        @Override
        public boolean onItemUse(ItemStack s, net.minecraft.entity.player.EntityPlayer p, net.minecraft.world.World w, int x, int y, int z, int side,
            float hx, float hy, float hz) {
            if (!p.canPlayerEdit(x, y, z, side, s)) return false;
            net.minecraftforge.event.entity.player.UseHoeEvent ev = new net.minecraftforge.event.entity.player.UseHoeEvent(p, s, w, x, y, z);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(ev)) return false;
            if (ev.getResult() == cpw.mods.fml.common.eventhandler.Event.Result.ALLOW) return true;
            Block b = w.getBlock(x, y, z);
            if (side == 0 || !w.getBlock(x, y + 1, z).isAir(w, x, y + 1, z) || b != net.minecraft.init.Blocks.grass && b != net.minecraft.init.Blocks.dirt)
                return false;
            Block farm = net.minecraft.init.Blocks.farmland;
            w.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, farm.stepSound.getStepResourcePath(), (farm.stepSound.getVolume() + 1) / 2,
                farm.stepSound.getPitch() * 0.8F);
            if (!w.isRemote) w.setBlock(x, y, z, farm);
            return true;
        }
    }

    /** Берёт руду джунглей: только халитовая кирка и мультиинструмент. */
    public static boolean minesJungle(ItemStack s) {
        return s != null && (s.getItem() instanceof Pickaxe || s.getItem() instanceof Multitool);
    }
}
