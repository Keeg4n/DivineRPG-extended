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

import com.divinerpgextended.item.ItemLairMap;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Странник - торговец лагерей сумеречных миров (выглядит как жрец-житель). Меняет осколки и души мира на его камни,
 * продаёт карту к логову, Зов кадиллиона, еду и лечение. Сделки не кончаются и не меняются: свой список вместо
 * ванильных профессий. Не размножается.
 */
public class EntityWanderer extends EntityVillager {

    /** Мир, где разбит лагерь (номер {@link TwilightWorld}); -1 - взять из мира, где стоит. */
    private int home = -1;
    private MerchantRecipeList trades;

    public EntityWanderer(World w) {
        super(w, 2);
    }

    public void setHome(TwilightWorld tw) {
        home = tw.ordinal();
        trades = null;
    }

    private TwilightWorld world() {
        if (home >= 0 && home < TwilightWorld.values().length) return TwilightWorld.values()[home];
        TwilightWorld tw = TwilightWorld.of(worldObj.provider.dimensionId);
        return tw == null ? TwilightWorld.EDEN : tw;
    }

    @Override
    public MerchantRecipeList getRecipes(EntityPlayer player) {
        if (trades == null) trades = trades(world());
        return trades;
    }

    public static MerchantRecipeList trades(TwilightWorld w) {
        MerchantRecipeList l = new MerchantRecipeList();
        l.add(new MerchantRecipe(new ItemStack(w.fragments(), 10), new ItemStack(w.gem(), 1)));
        l.add(new MerchantRecipe(new ItemStack(w.soul(), 4), new ItemStack(w.gem(), 1)));
        l.add(new MerchantRecipe(new ItemStack(w.gem(), 2), new ItemStack(ModItems.lairMap, 1,
            w == TwilightWorld.MORTUM ? ItemLairMap.CIRCLE : ItemLairMap.NEST)));
        // материалы мира - тем, кому не везёт с выпадением (дорого)
        com.divinerpgextended.world.twilight.TwilightMaterial[] m = com.divinerpgextended.world.twilight.TwilightMaterial.ofWorld(w);
        l.add(new MerchantRecipe(new ItemStack(w.gem(), 3), new ItemStack(ModItems.twilightMaterial, 1, m[0].ordinal())));
        l.add(new MerchantRecipe(new ItemStack(w.gem(), 1), new ItemStack(Items.bread, 6)));
        l.add(new MerchantRecipe(new ItemStack(w.gem(), 1), new ItemStack(Items.cooked_beef, 4)));
        l.add(new MerchantRecipe(new ItemStack(w.gem(), 2), new ItemStack(Items.potionitem, 1, 8229))); // лечение II
        l.add(new MerchantRecipe(new ItemStack(w.gem(), 1), new ItemStack(Items.arrow, 16)));
        return l;
    }

    /** Сделка не кончается: после обмена сразу снова доступна. */
    @Override
    public void useRecipe(MerchantRecipe r) {
        r.incrementToolUses();
        r.func_82783_a(1);
        playSound("mob.villager.yes", getSoundVolume(), getSoundPitch());
    }

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
        tag.setInteger("dreHome", home);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        home = tag.hasKey("dreHome") ? tag.getInteger("dreHome") : -1;
        trades = null;
    }
}
