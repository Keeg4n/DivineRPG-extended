package com.divinerpgextended.block;

import java.util.List;
import java.util.Random;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Запертый сундук: сундуки Арканы под ключи Divine (слизь, древний кирпич, камень душ, разрушенный кирпич) и реликварии
 * сумеречных миров. Нерушим всегда (и после открытия): не ломается, взрывы не берут, переносчики не двигают (прочность
 * меньше нуля), особыми кирками сломанный ничего не роняет ({@link com.divinerpgextended.boss.AltarGuard}). Открывается
 * своим ключом в руке - ключ тратится, внутри появляется добыча, дальше это обычный сундук. В творческом режиме открывается
 * без ключа. Вид - meta, сторона - в тайле.
 */
public class BlockLockedChest extends BlockContainer {

    /** Вид сундука: стена Арканы или дерево мира, цвет замка и окантовки, ключ. */
    public enum Kind {
        SLUDGE("sludge", "soulSludge", null, "#9aa6b8"), ANCIENT("ancient", "ancientBrick", null, "#33cfd4"), SOUL("soul", "soulStone", null, "#9a86ff"),
        DEGRADED("degraded", "degradedBrick", null, "#5a92f0"), EDEN("eden", null, "#8a6a1a", "#f4cc3a"), WILDWOOD("wildwood", null, "#1f3a6a", "#4a96ff"),
        APALACHIA("apalachia", null, "#4a2066", "#c060ff"), SKYTHERN("skythern", null, "#8a96aa", "#eef6ff"), MORTUM("mortum", null, "#3a1418", "#d43a4a");

        public final String key, wall, wood, trim;

        Kind(String key, String wall, String wood, String trim) {
            this.key = key;
            this.wall = wall;
            this.wood = wood;
            this.trim = trim;
        }

        public boolean arcana() {
            return wall != null;
        }

        public static Kind of(int meta) {
            Kind[] v = values();
            return v[meta >= 0 && meta < v.length ? meta : 0];
        }

        /** Мир реликвария (по порядку TwilightWorld, он же мета ключа мира) или -1 у сундуков Арканы. */
        public int world() {
            return arcana() ? -1 : ordinal() - EDEN.ordinal();
        }

        /** Вид реликвария мира. */
        public static Kind of(com.divinerpgextended.world.twilight.TwilightWorld w) {
            return values()[EDEN.ordinal() + w.ordinal()];
        }

        /** Ключ, которым открывается (null - нет предмета ключа). */
        public ItemStack keyStack() {
            switch (this) {
                case SLUDGE: return stack(net.divinerpg.utils.items.ArcanaItems.sludgeKey, 0);
                case ANCIENT: return stack(net.divinerpg.utils.items.ArcanaItems.ancientKey, 0);
                case SOUL: return stack(net.divinerpg.utils.items.ArcanaItems.soulKey, 0);
                case DEGRADED: return stack(net.divinerpg.utils.items.ArcanaItems.degradedKey, 0);
                default: return stack(com.divinerpgextended.registry.ModItems.worldKey, world());
            }
        }

        private static ItemStack stack(Item i, int meta) {
            return i == null ? null : new ItemStack(i, 1, meta);
        }

        /** Подходит ли этот предмет как ключ. */
        public boolean opens(ItemStack s) {
            ItemStack k = keyStack();
            return s != null && k != null && s.getItem() == k.getItem() && (arcana() || s.getItemDamage() == k.getItemDamage());
        }
    }

    public BlockLockedChest() {
        super(Material.wood);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setStepSound(soundTypeWood);
        setBlockBounds(0.0625F, 0.0F, 0.0625F, 0.9375F, 0.875F, 0.9375F);
        setBlockName("dre.lockedChest");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        return new TileLockedChest();
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    public Item getItemDropped(int meta, Random r, int fortune) {
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (Kind k : Kind.values()) list.add(new ItemStack(item, 1, k.ordinal()));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        blockIcon = reg.registerIcon("minecraft:planks_oak"); // частицы
    }

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileLockedChest)) return;
        int dir = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        ((TileLockedChest) te).facing = new int[] { 2, 5, 3, 4 }[dir];
    }

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileLockedChest)) return true;
        TileLockedChest c = (TileLockedChest) te;
        if (c.locked) {
            Kind k = Kind.of(w.getBlockMetadata(x, y, z));
            ItemStack key = k.keyStack();
            ItemStack held = p.getHeldItem();
            boolean creative = p.capabilities.isCreativeMode;
            if (!creative && !k.opens(held)) {
                say(p, EnumChatFormatting.GRAY, "dre.chest.locked", key == null ? new ChatComponentTranslation("dre.chest.no_key") : key.func_151000_E());
                return true;
            }
            if (!creative) {
                held.stackSize--;
                if (held.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
            }
            c.unlock(w.rand);
            w.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "random.door_open", 1.0F, 0.6F);
            say(p, EnumChatFormatting.GOLD, "dre.chest.unlocked");
        }
        p.displayGUIChest(c);
        return true;
    }

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        ChatComponentTranslation m = new ChatComponentTranslation(key, args);
        m.getChatStyle().setColor(color);
        p.addChatMessage(m);
    }

    /** Предмет сундука: вид по мете, подсказка - каким ключом открывается. */
    public static class ItemLockedChest extends net.minecraft.item.ItemBlock {

        public ItemLockedChest(net.minecraft.block.Block b) {
            super(b);
            setHasSubtypes(true);
            setMaxDamage(0);
        }

        @Override
        public int getMetadata(int meta) {
            return meta;
        }

        @Override
        public String getUnlocalizedName(ItemStack s) {
            return "tile.dre.lockedChest." + Kind.of(s.getItemDamage()).key;
        }

        @Override
        @SideOnly(Side.CLIENT)
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void addInformation(ItemStack s, EntityPlayer p, List l, boolean adv) {
            ItemStack key = Kind.of(s.getItemDamage()).keyStack();
            String name = key != null ? key.getDisplayName() : net.minecraft.util.StatCollector.translateToLocal("dre.chest.no_key");
            l.add(EnumChatFormatting.GRAY + net.minecraft.util.StatCollector.translateToLocalFormatted("dre.tooltip.locked_chest", name));
            l.add(EnumChatFormatting.DARK_GRAY + net.minecraft.util.StatCollector.translateToLocal("dre.tooltip.locked_chest_open"));
        }
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, net.minecraft.block.Block b, int meta) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TileLockedChest) {
            TileLockedChest c = (TileLockedChest) te;
            for (int i = 0; i < c.getSizeInventory(); i++) {
                ItemStack s = c.getStackInSlotOnClosing(i);
                if (s != null) w.spawnEntityInWorld(new EntityItem(w, x + 0.5, y + 0.5, z + 0.5, s));
            }
        }
        super.breakBlock(w, x, y, z, b, meta);
    }
}
