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
 * Wanderer - the trader of the twilight dimension camps: a traveler in a cloak the color of the dimension, with a backpack and a staff. For world stones
 * sells what cannot be crafted or takes long to find: a map to the lair, saplings, torches and arrows of the dimension, food and healing;
 * trades the materials of the dimension's mobs for one another. Trades never run out and never change. Does not breed.
 */
public class EntityWanderer extends EntityVillager {

    /** The dimension where the camp is set up (number of {@link TwilightWorld}); -1 - take from the world it stands in. Synced to the client (skin). */
    private static final int DW_HOME = 23;
    private MerchantRecipeList trades;

    /** Wanderer's health (a villager has 20: in the camp any random mob killed him). */
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

    /** A Wanderer without a dimension (spawned by egg outside the native biome of a twilight dimension): green robe, no trades. */
    public static final int STRAY = -2;

    public void setHome(TwilightWorld tw) {
        dataWatcher.updateObject(DW_HOME, Byte.valueOf((byte) tw.ordinal()));
        trades = null;
    }

    public void setStray() {
        dataWatcher.updateObject(DW_HOME, Byte.valueOf((byte) STRAY));
        trades = null;
    }

    public boolean stray() {
        return dataWatcher.getWatchableObjectByte(DW_HOME) == STRAY;
    }

    /** Without a dimension there is nothing to trade: the trade window does not open. */
    @Override
    public boolean interact(EntityPlayer p) {
        if (!stray()) return super.interact(p);
        if (!worldObj.isRemote) playSound("mob.villager.no", 0.6F, 0.8F);
        return true;
    }

    public TwilightWorld world() {
        int home = dataWatcher.getWatchableObjectByte(DW_HOME);
        if (home >= 0 && home < TwilightWorld.values().length) return TwilightWorld.values()[home];
        TwilightWorld tw = TwilightWorld.of(worldObj.provider.dimensionId);
        return tw == null ? TwilightWorld.EDEN : tw;
    }

    @Override
    public MerchantRecipeList getRecipes(EntityPlayer player) {
        if (trades == null) trades = stray() ? new MerchantRecipeList() : trades(world());
        return trades;
    }

    /** Price of the sanctum seal in world stones. */
    public static final int SEAL_PRICE = 4;

    public static MerchantRecipeList trades(TwilightWorld w) {
        MerchantRecipeList l = new MerchantRecipeList();
        Item gem = w.gem();
        if (w == TwilightWorld.MORTUM) { // a map to every Mortum arena
            for (com.divinerpgextended.world.twilight.MortumArenas.Type t : com.divinerpgextended.world.twilight.MortumArenas.Type.values())
                l.add(new MerchantRecipe(new ItemStack(gem, 2), new ItemStack(ModItems.lairMap, 1, ItemLairMap.arenaMap(t))));
        } else {
            l.add(new MerchantRecipe(new ItemStack(gem, 2), new ItemStack(ModItems.lairMap, 1, ItemLairMap.NEST)));
            // sanctum seal: in the island dungeons it is always there, but in dimensions with thin islands (Apalachia) there are few of them
            l.add(new MerchantRecipe(new ItemStack(gem, SEAL_PRICE), new ItemStack(ModItems.sanctumSeal, 1, w.ordinal())));
        }
        l.add(new MerchantRecipe(new ItemStack(gem, 1), new ItemStack(ModBlocks.worldTorch[w.ordinal()], 8)));
        if (w.arrow() != null) l.add(new MerchantRecipe(new ItemStack(gem, 1), new ItemStack(w.arrow(), 16)));
        l.add(new MerchantRecipe(new ItemStack(gem, 1), new ItemStack(w.sapling(), 2)));
        // materials of the dimension's mobs for one another: unlucky with one mob - make up with another (2 to 1)
        TwilightMaterial[] m = TwilightMaterial.ofWorld(w);
        for (int i = 0; i < 3; i++) {
            l.add(new MerchantRecipe(new ItemStack(ModItems.twilightMaterial, 2, m[i].ordinal()),
                new ItemStack(ModItems.twilightMaterial, 1, m[(i + 1) % 3].ordinal())));
        }
        l.add(new MerchantRecipe(new ItemStack(gem, 1), new ItemStack(Items.cooked_beef, 4)));
        l.add(new MerchantRecipe(new ItemStack(gem, 2), new ItemStack(Items.potionitem, 1, 8229))); // Healing II
        return l;
    }

    /** The trade does not run out: after an exchange it is immediately available again. Jingles like a coin, not with a villager's voice. */
    @Override
    public void useRecipe(MerchantRecipe r) {
        r.incrementToolUses();
        r.func_82783_a(1);
        playSound("random.orb", 0.5F, 0.8F + rand.nextFloat() * 0.3F);
    }

    // the Wanderer is not a village villager: without the villager's "hmm"
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

    /** A villager grumbles when an item is put into the trade window - the Wanderer does not. */
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
        // Wanderers already standing (saved with 20 health) - up to full
        net.minecraft.entity.ai.attributes.IAttributeInstance hp = getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth);
        if (hp.getBaseValue() < HEALTH) {
            hp.setBaseValue(HEALTH);
            setHealth(getMaxHealth());
        }
        dataWatcher.updateObject(DW_HOME, Byte.valueOf((byte) (tag.hasKey("dreHome") ? tag.getInteger("dreHome") : -1)));
        trades = null;
    }
}
