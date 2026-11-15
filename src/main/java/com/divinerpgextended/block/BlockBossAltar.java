package com.divinerpgextended.block;

import java.util.ArrayList;
import java.util.Random;

import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.divinerpgextended.Config;
import com.divinerpgextended.entity.EntityCarcinus;
import com.divinerpgextended.entity.EntityFrostColossus;
import com.divinerpgextended.entity.EntityLichKing;
import com.divinerpgextended.entity.EntitySnowGlacon;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Boss summoning altar in a lair: Carcinus (4 crab claws), Snow Glacon (Shell of the Deep) or Frost Colossus
 * with the Lich King (snowflakes; the altar is at the throne, the Colossus stands in the center of the hall), Ancient Entity (Mysterious Clock;
 * the altar is in the burial hall of the tomb, the boss stands in the center of the hall), Great Cadillion (Call of its world; the altar is on the
 * dais of the nest, the boss stands in the center of the nest), Ayeraco horde (Horde Horn; the altar is in the center of the aerie arena in the End). Unbreakable in
 * survival; if a special tool still broke it - it drops nothing ({@link com.divinerpgextended.boss.AltarGuard}).
 */
public class BlockBossAltar extends Block {

    public static final int CARCINUS = 0, SNOW_GLACON = 1, THRONE = 2, TOMB = 3, NEST = 4, JUNGLE = 5, AYERACO = 6;
    private static final String[] NAMES = { "dre.carcinusAltar", "dre.glaconAltar", "dre.throneAltar", "dre.tombAltar",
        "dre.nestAltar", "dre.jungleAltar", "dre.ayeracoAltar" };
    private static final String[] TEXTURES = { "minecraft:stonebrick_carved", "minecraft:lapis_block",
        "minecraft:diamond_block", "minecraft:gold_block", "minecraft:emerald_block", "minecraft:stonebrick_mossy", "minecraft:quartz_block_chiseled" };
    /** Own faces (textures/blocks/altar/<name>_side and _top); null - the vanilla texture from TEXTURES. */
    private static final String[] FACES = { "carcinus", "glacon", "throne", "tomb", "nest", "jungle", null };
    private final int type;
    @SideOnly(Side.CLIENT)
    private net.minecraft.util.IIcon side, top;

    public BlockBossAltar(int type) {
        super(Material.rock);
        this.type = type;
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setLightLevel(0.6F);
        setBlockName(NAMES[type]);
        // ice altars are bright so they do not get lost among ice and frozen stone
        setBlockTextureName(TEXTURES[type]);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister reg) {
        if (FACES[type] == null) {
            super.registerBlockIcons(reg);
            return;
        }
        side = reg.registerIcon("divinerpgextended:altar/" + FACES[type] + "_side");
        top = reg.registerIcon("divinerpgextended:altar/" + FACES[type] + "_top");
        blockIcon = side;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public net.minecraft.util.IIcon getIcon(int s, int meta) {
        if (FACES[type] == null) return super.getIcon(s, meta);
        return s <= 1 ? top : side;
    }

    /** Snow sparks swirl above the Glacon altar. */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        if (type == CARCINUS) return;
        if (type == AYERACO) { // purple End sparks
            for (int i = 0; i < 2; i++) world.spawnParticle("portal", x + rand.nextDouble(), y + 1.0 + rand.nextDouble() * 0.8, z + rand.nextDouble(), 0, 0.2, 0);
            return;
        }
        if (type == JUNGLE) { // green spores
            world.spawnParticle("happyVillager", x + rand.nextDouble(), y + 1.0 + rand.nextDouble() * 0.6, z + rand.nextDouble(), 0, 0, 0);
            return;
        }
        if (type == NEST) { // green sparks above the nest
            world.spawnParticle("happyVillager", x + rand.nextDouble(), y + 1.0 + rand.nextDouble() * 0.6, z + rand.nextDouble(), 0, 0, 0);
            return;
        }
        if (type == TOMB) { // golden dust
            world.spawnParticle("reddust", x + rand.nextDouble(), y + 1.0 + rand.nextDouble() * 0.6, z + rand.nextDouble(),
                1.0, 0.85, 0.2);
            return;
        }
        for (int i = 0; i < 3; i++) {
            world.spawnParticle(i == 0 ? "fireworksSpark" : "snowshovel", x + rand.nextDouble(),
                y + 1.0 + rand.nextDouble() * 0.6, z + rand.nextDouble(), 0, 0.03, 0);
        }
    }

    private Item cost() {
        if (type == JUNGLE) return ModItems.jungleTotem;
        if (type == NEST) return ModItems.cadillionCall;
        if (type == TOMB) return VanillaItemsOther.mysteriousClock;
        return type == CARCINUS ? VanillaItemsOther.crabClaw : type == THRONE ? IceikaItems.snowflake : ModItems.deepShell;
    }

    private int costCount() {
        return type == CARCINUS ? Config.carcinusSummonClaws : type == THRONE ? Config.throneSummonSnowflakes : 1;
    }

    /**
     * Great Cadillion nest altar: needs the Call of this world; the cadillion stands in the center of the nest (the altar on the dais is
     * {@link com.divinerpgextended.world.twilight.WorldGenNests#ALTAR_DZ} blocks north of the center).
     */
    private boolean nest(World world, int x, int y, int z, EntityPlayer player) {
        com.divinerpgextended.world.CadillionWorld w = null;
        for (com.divinerpgextended.world.CadillionWorld c : com.divinerpgextended.world.CadillionWorld.values()) {
            if (c.dimension() == world.provider.dimensionId) w = c;
        }
        if (w == null) return true;
        net.minecraft.util.AxisAlignedBB area = net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 48, y - 24, z - 48, x + 48, y + 24, z + 48);
        if (!world.getEntitiesWithinAABB(net.minecraft.entity.boss.IBossDisplayData.class, area).isEmpty()) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.altar_busy"));
            return true;
        }
        ItemStack held = player.getHeldItem();
        ItemStack need = new ItemStack(ModItems.cadillionCall, 1, w.ordinal());
        if (!player.capabilities.isCreativeMode && (held == null || held.getItem() != ModItems.cadillionCall || held.getItemDamage() != w.ordinal())) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.altar_cost", 1, need.getDisplayName()));
            return true;
        }
        net.minecraft.entity.EntityLiving boss = com.divinerpgextended.entity.EntityGroveGuardian.create(world, w);
        com.divinerpgextended.world.twilight.TwilightWorld tw = com.divinerpgextended.world.twilight.TwilightWorld.values()[w.ordinal()];
        int dzOff = com.divinerpgextended.world.twilight.WorldGenNests.altarDz(tw);
        // on the mound the center is taken by the mound - the King stands on the floor beside the altar, past the stairs
        boolean mound = tw == com.divinerpgextended.world.twilight.TwilightWorld.APALACHIA;
        boss.setLocationAndAngles(x + 0.5 + (mound ? 4 : 0), y - 1, z + 0.5 + (mound ? 3 : dzOff), 0, 0);
        if (!com.divinerpgextended.boss.BossArena.roomFor(boss)) { // the spawn spot has been built over
            player.addChatMessage(new ChatComponentTranslation("dre.chat.summon_blocked"));
            return true;
        }
        if (!player.capabilities.isCreativeMode) held.stackSize--;
        com.divinerpgextended.boss.LairSummons.leash(boss, x, y - 1, z + dzOff, com.divinerpgextended.world.twilight.WorldGenNests.leashRadius(tw));
        world.spawnEntityInWorld(boss);
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "mob.wither.spawn", 0.6F, 0.8F);
        com.divinerpgextended.audit.Audit.log(player, "Summon", "Grove Guardian (" + w.key() + ") at the nest altar");
        return true;
    }

    /**
     * Ayeraco horde altar: the Horde Horn (in creative - an empty hand too) works only here. The summon itself is done by the Divine horn - as
     * if it were clicked on the altar: a summoning sign appears above the altar, and the horde flies out of it.
     */
    private boolean horde(World world, int x, int y, int z, EntityPlayer player) {
        Item horn = VanillaItemsOther.hordeHorn;
        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() != horn) {
            if (!player.capabilities.isCreativeMode) {
                player.addChatMessage(new ChatComponentTranslation("dre.chat.altar_cost", 1, new ItemStack(horn).getDisplayName()));
                return true;
            }
            held = new ItemStack(horn);
        }
        net.minecraft.util.AxisAlignedBB area = net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 64, y - 32, z - 64, x + 64, y + 48, z + 64);
        for (Object o : world.getEntitiesWithinAABB(net.divinerpg.entities.vanilla.EntityAyeraco.class, area))
            if (((net.minecraft.entity.Entity) o).isEntityAlive()) {
                player.addChatMessage(new ChatComponentTranslation("dre.chat.altar_busy"));
                return true;
            }
        horn.onItemUse(held, player, world, x, y, z, 1, 0.5F, 1.0F, 0.5F);
        return true;
    }

    // broken by a special tool - vanishes without a trace
    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        return null;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<ItemStack>();
    }

    @Override
    public boolean canSilkHarvest(World world, EntityPlayer player, int x, int y, int z, int meta) {
        return false;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx,
        float hy, float hz) {
        if (world.isRemote) return true;
        if (type == NEST) return nest(world, x, y, z, player);
        if (type == AYERACO) return horde(world, x, y, z, player);
        Class<? extends EntityLiving> bossClass = type == JUNGLE ? com.divinerpgextended.entity.EntityAncientDramcryx.class : type == CARCINUS ? EntityCarcinus.class
            : type == THRONE ? EntityFrostColossus.class
                : type == TOMB ? net.divinerpg.entities.vanilla.EntityAncientEntity.class : EntitySnowGlacon.class;
        net.minecraft.util.AxisAlignedBB area = net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 48, y - 24, z - 48,
            x + 48, y + 24, z + 48);
        if (!world.getEntitiesWithinAABB(bossClass, area)
            .isEmpty()
            || type == THRONE && !world.getEntitiesWithinAABB(EntityLichKing.class, area)
                .isEmpty()) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.altar_busy"));
            return true;
        }
        ItemStack held = player.getHeldItem();
        if (!player.capabilities.isCreativeMode && (held == null || held.getItem() != cost() || held.stackSize < costCount())) {
            player.addChatMessage(new ChatComponentTranslation("dre.chat.altar_cost", costCount(),
                new ItemStack(cost()).getDisplayName()));
            return true;
        }
        int[] leashAt = null;
        EntityLiving boss = type == JUNGLE ? new com.divinerpgextended.entity.EntityAncientDramcryx(world) : type == CARCINUS ? new EntityCarcinus(world)
            : type == THRONE ? new EntityFrostColossus(world)
                : type == TOMB ? new net.divinerpg.entities.vanilla.EntityAncientEntity(world) : new EntitySnowGlacon(world);
        if (type == JUNGLE) {
            // the altar is at the north edge of the dais, the Ancient Dramcryx stands in the center of the hall
            int dz = com.divinerpgextended.world.WorldGenJungleLair.ALTAR_DZ;
            boss.setLocationAndAngles(x + 0.5, y - 1, z + 0.5 + dz, 0, 0);
            leashAt = new int[] { x, y - 1, z + dz, com.divinerpgextended.world.WorldGenJungleLair.BOSS_R - 2 };
        } else if (type == TOMB) {
            // the altar is on the dais at the east wall of the burial hall, the entity stands in the center of the hall
            boss.setLocationAndAngles(x + 0.5 - com.divinerpgextended.world.WorldGenTomb.ALTAR_DX,
                y - com.divinerpgextended.world.WorldGenTomb.ALTAR_DY + 1, z + 0.5, 270, 0);
        } else if (type == THRONE) {
            // the altar stands on the dais at the throne (north), the Colossus rises in the center of the arena
            boss.setLocationAndAngles(x + 0.5, y - com.divinerpgextended.world.WorldGenThrone.ALTAR_DY,
                z + 0.5 - com.divinerpgextended.world.WorldGenThrone.ALTAR_DZ, 180, 0);
            // the arena is the whole dome up to the wall (the Lich King inherits it); before 0.99.126 - only up to the pylons, and the player was pushed back in the middle of the hall
            com.divinerpgextended.boss.BossArena.set(boss, x, y - com.divinerpgextended.world.WorldGenThrone.ALTAR_DY,
                z - com.divinerpgextended.world.WorldGenThrone.ALTAR_DZ, com.divinerpgextended.world.WorldGenThrone.R - 3);
        } else {
            boss.setLocationAndAngles(x + 0.5, y + 1, z + 0.5, world.rand.nextFloat() * 360, 0);
            // the Glacon stands on the altar in the center of the nest: the arena is the whole cave dome up to the wall (before - only the lake, pushing back before the wall)
            if (type == SNOW_GLACON) com.divinerpgextended.boss.BossArena.set(boss, x, y - 3, z, com.divinerpgextended.world.LairDomes.G_R - 3);
        }
        if (!com.divinerpgextended.boss.BossArena.roomFor(boss)) { // the spawn spot has been built over
            player.addChatMessage(new ChatComponentTranslation("dre.chat.summon_blocked"));
            return true;
        }
        if (!player.capabilities.isCreativeMode) held.stackSize -= costCount();
        if (leashAt != null) com.divinerpgextended.boss.LairSummons.leash(boss, leashAt[0], leashAt[1], leashAt[2], leashAt[3]);
        if (type == THRONE) com.divinerpgextended.entity.FrostPowers.tell(boss, "dre.chat.colossus_rises");
        world.spawnEntityInWorld(boss);
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "mob.wither.spawn", 0.6F, type == CARCINUS ? 0.5F : 1.4F);
        return true;
    }
}
