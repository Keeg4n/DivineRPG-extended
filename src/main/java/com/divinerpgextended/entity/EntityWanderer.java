package com.divinerpgextended.entity;

import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.World;

import net.minecraft.item.Item;

import com.divinerpgextended.item.ItemLairMap;
import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.world.twilight.TwilightMaterial;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Странник - торговец лагерей сумеречных миров: путник в плаще цвета мира, с рюкзаком и посохом. За камни мира
 * продаёт то, что не скрафтить или долго искать: карту к логову, саженцы, факелы и стрелы мира, еду и лечение;
 * меняет материалы мобов мира друг на друга. Сделки не кончаются и не меняются. Не размножается.
 */
public class EntityWanderer extends EntityVillager {

    /** Мир, где разбит лагерь (номер {@link TwilightWorld}); -1 - взять из мира, где стоит. Синхронизируется на клиент (скин). */
    private static final int DW_HOME = 23;
    private MerchantRecipeList trades;

    /** Здоровье Странника (у жителя - 20: в лагере его убивал любой случайный моб). */
    public static final double HEALTH = 1000;

    public EntityWanderer(World w) {
        super(w, 2);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth).setBaseValue(HEALTH);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_HOME, Byte.valueOf((byte) -1));
    }

    public void setHome(TwilightWorld tw) {
        dataWatcher.updateObject(DW_HOME, Byte.valueOf((byte) tw.ordinal()));
        trades = null;
    }

    public TwilightWorld world() {
        int home = dataWatcher.getWatchableObjectByte(DW_HOME);
        if (home >= 0 && home < TwilightWorld.values().length) return TwilightWorld.values()[home];
        TwilightWorld tw = TwilightWorld.of(worldObj.provider.dimensionId);
        return tw == null ? TwilightWorld.EDEN : tw;
    }

    @Override
    public MerchantRecipeList getRecipes(EntityPlayer player) {
        if (trades == null) trades = trades(world());
        return trades;
    }

    /** Цена печати святилища в камнях мира. */
    public static final int SEAL_PRICE = 4;

    public static MerchantRecipeList trades(TwilightWorld w) {
        MerchantRecipeList l = new MerchantRecipeList();
        Item gem = w.gem();
        if (w == TwilightWorld.MORTUM) { // карта к каждой арене Мортума
            for (com.divinerpgextended.world.twilight.MortumArenas.Type t : com.divinerpgextended.world.twilight.MortumArenas.Type.values())
                l.add(new MerchantRecipe(new ItemStack(gem, 2), new ItemStack(ModItems.lairMap, 1, ItemLairMap.arenaMap(t))));
        } else {
            l.add(new MerchantRecipe(new ItemStack(gem, 2), new ItemStack(ModItems.lairMap, 1, ItemLairMap.NEST)));
            // печать святилища: в подземельях островов она лежит всегда, но в мирах с тонкими островами (Апалачия) их мало
            l.add(new MerchantRecipe(new ItemStack(gem, SEAL_PRICE), new ItemStack(ModItems.sanctumSeal, 1, w.ordinal())));
        }
        l.add(new MerchantRecipe(new ItemStack(gem, 1), new ItemStack(ModBlocks.worldTorch[w.ordinal()], 8)));
        if (w.arrow() != null) l.add(new MerchantRecipe(new ItemStack(gem, 1), new ItemStack(w.arrow(), 16)));
        l.add(new MerchantRecipe(new ItemStack(gem, 1), new ItemStack(w.sapling(), 2)));
        // материалы мобов мира друг на друга: не везёт с одним мобом - добери другим (2 к 1)
        TwilightMaterial[] m = TwilightMaterial.ofWorld(w);
        for (int i = 0; i < 3; i++) {
            l.add(new MerchantRecipe(new ItemStack(ModItems.twilightMaterial, 2, m[i].ordinal()),
                new ItemStack(ModItems.twilightMaterial, 1, m[(i + 1) % 3].ordinal())));
        }
        l.add(new MerchantRecipe(new ItemStack(gem, 1), new ItemStack(Items.cooked_beef, 4)));
        l.add(new MerchantRecipe(new ItemStack(gem, 2), new ItemStack(Items.potionitem, 1, 8229))); // лечение II
        return l;
    }

    /** Сделка не кончается: после обмена сразу снова доступна. Звенит монетой, а не голосом жителя. */
    @Override
    public void useRecipe(MerchantRecipe r) {
        r.incrementToolUses();
        r.func_82783_a(1);
        playSound("random.orb", 0.5F, 0.8F + rand.nextFloat() * 0.3F);
    }

    // странник - не житель деревни: без "хмм" жителя
    @Override
    protected String getLivingSound() {
        return null;
    }

    @Override
    protected String getHurtSound() {
        return "game.neutral.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "game.neutral.die";
    }

    /** Житель ворчит, когда в окне торговли кладут предмет, - странник нет. */
    @Override
    public void func_110297_a_(ItemStack s) {}

    @Override
    public EntityVillager createChild(EntityAgeable other) {
        return null;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public String getCommandSenderName() {
        return hasCustomNameTag() ? getCustomNameTag() : StatCollector.translateToLocal("entity.divinerpgextended.Wanderer.name");
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("dreHome", dataWatcher.getWatchableObjectByte(DW_HOME));
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        // уже стоящие Странники (сохранены с 20 здоровья) - до полного
        net.minecraft.entity.ai.attributes.IAttributeInstance hp = getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth);
        if (hp.getBaseValue() < HEALTH) {
            hp.setBaseValue(HEALTH);
            setHealth(getMaxHealth());
        }
        dataWatcher.updateObject(DW_HOME, Byte.valueOf((byte) (tag.hasKey("dreHome") ? tag.getInteger("dreHome") : -1)));
        trades = null;
    }
}
