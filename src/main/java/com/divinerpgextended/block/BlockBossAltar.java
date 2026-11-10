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
 * Алтарь призыва босса в логове: Карцинус (4 крабовые клешни), Снежный Глакон (Панцирь глубин) или Ледяной Колосс
 * с Королём-личом (снежинки; алтарь у трона, Колосс встаёт в центре зала), Древняя сущность (Таинственные часы;
 * алтарь в погребальном зале гробницы, босс встаёт в центре зала), Великий кадиллион (Зов своего мира; алтарь на
 * помосте гнезда, босс встаёт в центре гнезда), орда Айрако (Рог орды; алтарь в центре арены гнездовья в Энде). Неразрушим в
 * выживании; если его всё же сломал особый инструмент - ничего не роняет ({@link com.divinerpgextended.boss.AltarGuard}).
 */
public class BlockBossAltar extends Block {

    public static final int CARCINUS = 0, SNOW_GLACON = 1, THRONE = 2, TOMB = 3, NEST = 4, JUNGLE = 5, AYERACO = 6;
    private static final String[] NAMES = { "dre.carcinusAltar", "dre.glaconAltar", "dre.throneAltar", "dre.tombAltar",
        "dre.nestAltar", "dre.jungleAltar", "dre.ayeracoAltar" };
    private static final String[] TEXTURES = { "minecraft:stonebrick_carved", "minecraft:lapis_block",
        "minecraft:diamond_block", "minecraft:gold_block", "minecraft:emerald_block", "minecraft:stonebrick_mossy", "minecraft:quartz_block_chiseled" };
    /** Свои грани (textures/blocks/altar/<имя>_side и _top); null - ванильная текстура из TEXTURES. */
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
        // ледяные алтари яркие, чтобы не терялись среди льда и мёрзлого камня
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

    /** Над алтарём Глакона кружат снежные искры. */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        if (type == CARCINUS) return;
        if (type == AYERACO) { // фиолетовые искры Края
            for (int i = 0; i < 2; i++) world.spawnParticle("portal", x + rand.nextDouble(), y + 1.0 + rand.nextDouble() * 0.8, z + rand.nextDouble(), 0, 0.2, 0);
            return;
        }
        if (type == JUNGLE) { // зелёные споры
            world.spawnParticle("happyVillager", x + rand.nextDouble(), y + 1.0 + rand.nextDouble() * 0.6, z + rand.nextDouble(), 0, 0, 0);
            return;
        }
        if (type == NEST) { // зелёные искры над гнездом
            world.spawnParticle("happyVillager", x + rand.nextDouble(), y + 1.0 + rand.nextDouble() * 0.6, z + rand.nextDouble(), 0, 0, 0);
            return;
        }
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
        if (type == JUNGLE) return ModItems.jungleTotem;
        if (type == NEST) return ModItems.cadillionCall;
        if (type == TOMB) return VanillaItemsOther.mysteriousClock;
        return type == CARCINUS ? VanillaItemsOther.crabClaw : type == THRONE ? IceikaItems.snowflake : ModItems.deepShell;
    }

    private int costCount() {
        return type == CARCINUS ? Config.carcinusSummonClaws : type == THRONE ? Config.throneSummonSnowflakes : 1;
    }

    /**
     * Алтарь гнезда Великого кадиллиона: нужен Зов этого мира; кадиллион встаёт в центре гнезда (алтарь на помосте в
     * {@link com.divinerpgextended.world.twilight.WorldGenNests#ALTAR_DZ} блоках к северу от центра).
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
        // на кургане центр занят курганом - Король встаёт на пол сбоку от алтаря, мимо лестницы
        boolean mound = tw == com.divinerpgextended.world.twilight.TwilightWorld.APALACHIA;
        boss.setLocationAndAngles(x + 0.5 + (mound ? 4 : 0), y - 1, z + 0.5 + (mound ? 3 : dzOff), 0, 0);
        if (!com.divinerpgextended.boss.BossArena.roomFor(boss)) { // место появления застроили
            player.addChatMessage(new ChatComponentTranslation("dre.chat.summon_blocked"));
            return true;
        }
        if (!player.capabilities.isCreativeMode) held.stackSize--;
        com.divinerpgextended.boss.LairSummons.leash(boss, x, y - 1, z + dzOff, com.divinerpgextended.world.twilight.WorldGenNests.leashRadius(tw));
        world.spawnEntityInWorld(boss);
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "mob.wither.spawn", 0.6F, 0.8F);
        com.divinerpgextended.audit.Audit.log(player, "Summon", "Страж рощи (" + w.key() + ") на алтаре гнезда");
        return true;
    }

    /**
     * Алтарь орды Айрако: Рог орды (в творческом - и пустой рукой) работает только здесь. Сам вызов делает рог Divine - как
     * если бы им щёлкнули по алтарю: над алтарём появляется знак призыва, из него вылетает орда.
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

    // сломанный особым инструментом - пропадает без следа
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
            // алтарь у северного края помоста, Древний драмкрикс встаёт в центре зала
            int dz = com.divinerpgextended.world.WorldGenJungleLair.ALTAR_DZ;
            boss.setLocationAndAngles(x + 0.5, y - 1, z + 0.5 + dz, 0, 0);
            leashAt = new int[] { x, y - 1, z + dz, com.divinerpgextended.world.WorldGenJungleLair.BOSS_R - 2 };
        } else if (type == TOMB) {
            // алтарь на помосте у восточной стены погребального зала, сущность встаёт в центре зала
            boss.setLocationAndAngles(x + 0.5 - com.divinerpgextended.world.WorldGenTomb.ALTAR_DX,
                y - com.divinerpgextended.world.WorldGenTomb.ALTAR_DY + 1, z + 0.5, 270, 0);
        } else if (type == THRONE) {
            // алтарь стоит на помосте у трона (север), Колосс поднимается в центре арены
            boss.setLocationAndAngles(x + 0.5, y - com.divinerpgextended.world.WorldGenThrone.ALTAR_DY,
                z + 0.5 - com.divinerpgextended.world.WorldGenThrone.ALTAR_DZ, 180, 0);
            // арена - купол до кольца пилонов (на них прыгает Король-лич, он арену унаследует)
            com.divinerpgextended.boss.BossArena.set(boss, x, y - com.divinerpgextended.world.WorldGenThrone.ALTAR_DY,
                z - com.divinerpgextended.world.WorldGenThrone.ALTAR_DZ, 80);
        } else {
            boss.setLocationAndAngles(x + 0.5, y + 1, z + 0.5, world.rand.nextFloat() * 360, 0);
            // Глакон встаёт на алтарь в центре гнезда: арена - гнездо и замёрзшее озеро вокруг
            if (type == SNOW_GLACON) com.divinerpgextended.boss.BossArena.set(boss, x, y - 3, z, com.divinerpgextended.world.LairDomes.G_LAKE + 2);
        }
        if (!com.divinerpgextended.boss.BossArena.roomFor(boss)) { // место появления застроили
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
