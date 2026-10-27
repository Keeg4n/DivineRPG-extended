package com.divinerpgextended.block;

import java.util.ArrayList;
import java.util.Random;

import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCarrot;
import net.minecraft.block.BlockMelon;
import net.minecraft.block.BlockStem;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Грядка мечтаний: морковь мечтаний сажается на пашню и ведёт себя как ванильная (пашня пересохла или вытоптана - ломается;
 * урожай растёт с Удачей), арбуз мечтаний - из семечек:
 * стебель, рядом вырастает блок арбуза, с него падают дольки (с Шёлковым касанием - сам блок).
 */
public final class DreamCrops {

    private DreamCrops() {}

    /** Морковь мечтаний на грядке: 4 стадии, как у моркови; созревшая - 1-4 моркови (+1 бросок за уровень Удачи). */
    public static class Carrot extends BlockCarrot {

        @SideOnly(Side.CLIENT)
        private IIcon[] stages;

        public Carrot() {
            setBlockName("dre.dreamCarrots");
            setBlockTextureName("divinerpgextended:dream_carrots");
        }

        /**
         * Посадка (как у ванильной моркови): Forge узнаёт посадки по ссылке на ванильный блок, наш считал "травой" - и земля
         * его держала: пересохшая или вытоптанная пашня не ломала морковь.
         */
        @Override
        public net.minecraftforge.common.EnumPlantType getPlantType(net.minecraft.world.IBlockAccess w, int x, int y, int z) {
            return net.minecraftforge.common.EnumPlantType.Crop;
        }

        @Override
        protected Item func_149866_i() { // семя
            return VetheaItems.dreamCarrot;
        }

        @Override
        protected Item func_149865_P() { // урожай
            return VetheaItems.dreamCarrot;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public Item getItem(World w, int x, int y, int z) {
            return VetheaItems.dreamCarrot;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IIcon getIcon(int side, int meta) {
            if (meta < 7) {
                if (meta == 6) meta = 5;
                return stages[meta >> 1];
            }
            return stages[3];
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void registerBlockIcons(IIconRegister r) {
            stages = new IIcon[4];
            for (int i = 0; i < 4; i++) stages[i] = r.registerIcon(getTextureName() + "_stage_" + i);
        }
    }

    /** Блок арбуза мечтаний: 3-7 долек (с Удачей больше, не больше 9). */
    public static class Melon extends BlockMelon {

        @SideOnly(Side.CLIENT)
        private IIcon top;

        public Melon() {
            setHardness(1.0F);
            setStepSound(soundTypeWood);
            setBlockName("dre.dreamMelon");
            setBlockTextureName("divinerpgextended:dream_melon");
            setCreativeTab(net.minecraft.creativetab.CreativeTabs.tabBlock);
        }

        @Override
        public Item getItemDropped(int meta, Random r, int fortune) {
            return VetheaItems.dreamMelon;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IIcon getIcon(int side, int meta) {
            return side == 1 || side == 0 ? top : blockIcon;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void registerBlockIcons(IIconRegister r) {
            blockIcon = r.registerIcon(getTextureName() + "_side");
            top = r.registerIcon(getTextureName() + "_top");
        }
    }

    /** Стебель арбуза мечтаний: растёт и плодоносит, как арбузный; ломается в свои семечки. */
    public static class Stem extends BlockStem {

        public Stem(Block fruit) {
            super(fruit);
            setHardness(0.0F);
            setStepSound(soundTypeWood);
            setBlockName("dre.dreamMelonStem");
            setBlockTextureName("melon_stem");
        }

        /** Посадка, как стебель ванильного арбуза (см. Carrot.getPlantType). */
        @Override
        public net.minecraftforge.common.EnumPlantType getPlantType(net.minecraft.world.IBlockAccess w, int x, int y, int z) {
            return net.minecraftforge.common.EnumPlantType.Crop;
        }

        @Override
        public ArrayList<ItemStack> getDrops(World w, int x, int y, int z, int meta, int fortune) {
            ArrayList<ItemStack> l = new ArrayList<ItemStack>();
            for (int i = 0; i < 3; i++) if (w.rand.nextInt(15) <= meta) l.add(new ItemStack(ModItems.dreamMelonSeeds));
            return l;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public Item getItem(World w, int x, int y, int z) {
            return ModItems.dreamMelonSeeds;
        }

        /** Бирюзовый, как ботва моркови мечтаний (у ванильного - от зелёного к жёлтому). */
        @Override
        @SideOnly(Side.CLIENT)
        public int getRenderColor(int meta) {
            int g = 120 + meta * 12, b = 110 + meta * 14;
            return 20 << 16 | g << 8 | b;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public int colorMultiplier(IBlockAccess w, int x, int y, int z) {
            return getRenderColor(w.getBlockMetadata(x, y, z));
        }
    }

    /** Морковь мечтаний - еда Divine без посадки: сажаем кликом по пашне и не даём в тот же клик её съесть. */
    public static class Planting {

        private static final String TICK = "dreDreamPlant";

        @SubscribeEvent
        public void onInteract(PlayerInteractEvent e) {
            if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.face != 1) return;
            EntityPlayer p = e.entityPlayer;
            ItemStack held = p.getHeldItem();
            if (held == null || held.getItem() != VetheaItems.dreamCarrot) return;
            World w = p.worldObj;
            if (!w.isAirBlock(e.x, e.y + 1, e.z) || !w.getBlock(e.x, e.y, e.z).canSustainPlant(w, e.x, e.y, e.z, net.minecraftforge.common.util.ForgeDirection.UP,
                (net.minecraftforge.common.IPlantable) ModBlocks.dreamCarrots)) return;
            p.getEntityData().setLong(TICK, w.getTotalWorldTime());
            if (w.isRemote) return;
            if (!p.canPlayerEdit(e.x, e.y + 1, e.z, 1, held)) return;
            w.setBlock(e.x, e.y + 1, e.z, ModBlocks.dreamCarrots);
            if (!p.capabilities.isCreativeMode && --held.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
            e.setCanceled(true);
        }

        @SubscribeEvent
        public void onEat(PlayerUseItemEvent.Start e) {
            if (e.item != null && e.item.getItem() == VetheaItems.dreamCarrot
                && e.entityPlayer.worldObj.getTotalWorldTime() - e.entityPlayer.getEntityData().getLong(TICK) <= 1) e.setCanceled(true);
        }
    }
}
