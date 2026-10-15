package com.divinerpgextended.block;

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
 * Алтарь призыва босса в логове: Карцинус (4 крабовые клешни), Снежный Глакон (Панцирь глубин) или Ледяной Колосс
 * с Королём-личом (снежинки; алтарь у трона, Колосс встаёт в центре зала), Древняя сущность (Таинственные часы;
 * алтарь в погребальном зале гробницы, босс встаёт в центре зала). Неразрушим в выживании.
 */
public class BlockBossAltar extends Block {

    public static final int CARCINUS = 0, SNOW_GLACON = 1, THRONE = 2, TOMB = 3;
    private static final String[] NAMES = { "dre.carcinusAltar", "dre.glaconAltar", "dre.throneAltar", "dre.tombAltar" };
    private static final String[] TEXTURES = { "minecraft:stonebrick_carved", "minecraft:lapis_block",
        "minecraft:diamond_block", "minecraft:gold_block" };
    private final int type;

    public BlockBossAltar(int type) {
        super(Material.rock);
        this.type = type;
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setLightLevel(0.6F);
        setBlockName(NAMES[type]);
        // ледяные алтари яркие, чтобы не терялись среди льда и мёрзлого камня
        setBlockTextureName(TEXTURES[type]);
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
    }

    /** Над алтарём Глакона кружат снежные искры. */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        if (type == CARCINUS) return;
        if (type == TOMB) { // золотая пыль
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
        if (type == TOMB) return VanillaItemsOther.mysteriousClock;
        return type == CARCINUS ? VanillaItemsOther.crabClaw : type == THRONE ? IceikaItems.snowflake : ModItems.deepShell;
    }

    private int costCount() {
        return type == CARCINUS ? Config.carcinusSummonClaws : type == THRONE ? Config.throneSummonSnowflakes : 1;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx,
        float hy, float hz) {
        if (world.isRemote) return true;
        Class<? extends EntityLiving> bossClass = type == CARCINUS ? EntityCarcinus.class
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
        if (!player.capabilities.isCreativeMode) held.stackSize -= costCount();

        EntityLiving boss = type == CARCINUS ? new EntityCarcinus(world)
            : type == THRONE ? new EntityFrostColossus(world)
                : type == TOMB ? new net.divinerpg.entities.vanilla.EntityAncientEntity(world) : new EntitySnowGlacon(world);
        if (type == TOMB) {
            // алтарь на помосте у восточной стены погребального зала, сущность встаёт в центре зала
            boss.setLocationAndAngles(x + 0.5 - com.divinerpgextended.world.WorldGenTomb.ALTAR_DX,
                y - com.divinerpgextended.world.WorldGenTomb.ALTAR_DY + 1, z + 0.5, 270, 0);
        } else if (type == THRONE) {
            // алтарь стоит на помосте у трона (север), Колосс поднимается в центре арены
            boss.setLocationAndAngles(x + 0.5, y - com.divinerpgextended.world.WorldGenThrone.ALTAR_DY,
                z + 0.5 - com.divinerpgextended.world.WorldGenThrone.ALTAR_DZ, 180, 0);
            com.divinerpgextended.entity.FrostPowers.tell(boss, "dre.chat.colossus_rises");
        } else {
            boss.setLocationAndAngles(x + 0.5, y + 1, z + 0.5, world.rand.nextFloat() * 360, 0);
        }
        world.spawnEntityInWorld(boss);
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "mob.wither.spawn", 0.6F, type == CARCINUS ? 0.5F : 1.4F);
        return true;
    }
}
