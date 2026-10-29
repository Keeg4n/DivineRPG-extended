package com.divinerpgextended.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.BlockTorch;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Водный факел зова: потушен - просто стоит; арканиум правой кнопкой - горит минуту (ещё арканиум - дольше, до
 * {@link #MAX_TICKS}), и пока горит, в {@link #RADIUS} блоках вокруг чаще появляются мобы Divine этого биома (не больше
 * {@link #CAP} рядом). Вместо дыма - пузыри. Горящий - отдельный блок со счётчиком ({@link Tile}); догорел - снова потушен.
 */
public class BlockLureTorch extends BlockTorch implements ITileEntityProvider {

    public static final int RADIUS = 24, CAP = 12, PER_ARCANIUM = 1200, MAX_TICKS = 9600, EVERY = 40;
    private final boolean lit;

    public BlockLureTorch(boolean lit) {
        this.lit = lit;
        setLightLevel(lit ? 0.9F : 0.0F);
        setHardness(0.0F);
        setStepSound(soundTypeWood);
        setBlockName("dre.lureTorch");
        setBlockTextureName("divinerpgextended:lure_torch" + (lit ? "_lit" : ""));
        if (!lit) setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        blockIcon = reg.registerIcon(getTextureName());
    }

    @Override
    public Item getItemDropped(int meta, Random r, int fortune) {
        return Item.getItemFromBlock(ModBlocks.lureTorch);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Item getItem(World w, int x, int y, int z) {
        return Item.getItemFromBlock(ModBlocks.lureTorch);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return lit;
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        return lit ? new Tile() : null;
    }

    /** Арканиум: зажечь или подлить горения. */
    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float hx, float hy, float hz) {
        ItemStack held = p.getHeldItem();
        if (held == null || held.getItem() != net.divinerpg.utils.items.ArcanaItems.arcanium) return false;
        if (w.isRemote) return true;
        int meta = w.getBlockMetadata(x, y, z);
        if (!lit) w.setBlock(x, y, z, ModBlocks.lureTorchLit, meta, 3);
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof Tile)) return true;
        Tile t = (Tile) te;
        long now = w.getTotalWorldTime();
        if (t.until - now >= MAX_TICKS) return true; // уже горит на полную - арканиум не тратим
        t.until = Math.min(now + MAX_TICKS, Math.max(t.until, now) + PER_ARCANIUM);
        t.markDirty();
        if (!p.capabilities.isCreativeMode && --held.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
        w.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "random.splash", 0.5F, 1.4F);
        return true;
    }

    /** Пузыри над головкой (только горящий). */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World w, int x, int y, int z, Random r) {
        if (!lit) return;
        int meta = w.getBlockMetadata(x, y, z);
        double px = x + 0.5, py = y + 0.7, pz = z + 0.5, up = 0.22, off = 0.27;
        if (meta == 1) { px -= off; py += up; }
        else if (meta == 2) { px += off; py += up; }
        else if (meta == 3) { pz -= off; py += up; }
        else if (meta == 4) { pz += off; py += up; }
        com.divinerpgextended.client.TorchFX.bubbles(w, px, py, pz, r);
    }

    /** Горящий факел: до какого тика горит; зовёт мобов Divine. */
    public static class Tile extends TileEntity {

        public long until;

        @Override
        public void updateEntity() {
            if (worldObj.isRemote) return;
            long now = worldObj.getTotalWorldTime();
            if (now >= until) { // догорел
                worldObj.setBlock(xCoord, yCoord, zCoord, ModBlocks.lureTorch, worldObj.getBlockMetadata(xCoord, yCoord, zCoord), 3);
                return;
            }
            if (now % EVERY == 0) lure(worldObj, xCoord, yCoord, zCoord, worldObj.rand);
        }

        @Override
        public void readFromNBT(NBTTagCompound t) {
            super.readFromNBT(t);
            until = t.getLong("Until");
        }

        @Override
        public void writeToNBT(NBTTagCompound t) {
            super.writeToNBT(t);
            t.setLong("Until", until);
        }
    }

    /** Мобы Divine рядом (для предела). */
    static int divineNear(World w, int x, int y, int z) {
        int n = 0;
        for (Object o : w.getEntitiesWithinAABB(EntityLiving.class, net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 32, y - 16, z - 32, x + 32, y + 16, z + 32)))
            if (o.getClass().getName().startsWith("net.divinerpg.")) n++;
        return n;
    }

    /** Одна попытка зова: моб Divine из списков этого биома (монстр, а если их нет - любой) в случайном месте вокруг. */
    @SuppressWarnings("unchecked")
    public static EntityLiving lure(World w, int x, int y, int z, Random r) {
        if (divineNear(w, x, y, z) >= CAP) return null;
        int sx = x + r.nextInt(2 * RADIUS + 1) - RADIUS, sz = z + r.nextInt(2 * RADIUS + 1) - RADIUS;
        BiomeGenBase b = w.getBiomeGenForCoords(sx, sz);
        List<BiomeGenBase.SpawnListEntry> pool = new ArrayList<BiomeGenBase.SpawnListEntry>();
        for (EnumCreatureType t : new EnumCreatureType[] { EnumCreatureType.monster, EnumCreatureType.creature, EnumCreatureType.waterCreature })
            for (Object o : b.getSpawnableList(t)) {
                BiomeGenBase.SpawnListEntry e = (BiomeGenBase.SpawnListEntry) o;
                if (e.entityClass.getName().startsWith("net.divinerpg.")) pool.add(e);
            }
        if (pool.isEmpty()) return null;
        BiomeGenBase.SpawnListEntry pick = pool.get(r.nextInt(pool.size()));
        for (int dy = -8; dy <= 8; dy++) {
            int sy = y + dy;
            if (!w.getBlock(sx, sy - 1, sz).getMaterial().isSolid() && pick.entityClass.getName().indexOf("Shark") < 0) continue;
            if (!w.isAirBlock(sx, sy, sz) && w.getBlock(sx, sy, sz).getMaterial() != net.minecraft.block.material.Material.water) continue;
            try {
                EntityLiving m = (EntityLiving) pick.entityClass.getConstructor(World.class).newInstance(w);
                m.setLocationAndAngles(sx + 0.5, sy, sz + 0.5, r.nextFloat() * 360, 0);
                if (!m.getCanSpawnHere()) continue;
                // ранги: моба не по сюжету рядом стоящих игроков факел не зовёт (как и естественный спавн)
                if (com.divinerpgextended.boss.ProgressSpawns.denied(w, m, m.posX, m.posZ)) return null;
                w.spawnEntityInWorld(m);
                m.onSpawnWithEgg(null);
                return m;
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
