package com.divinerpgextended.magic;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.divinerpg.entities.arcana.EntityVatticus;
import net.divinerpg.entities.base.EntityDivineRPGVillager;
import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.event.entity.living.LivingEvent;

import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.trinket.Trinket;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Цены арканного оружия у торговцев Арканы - по полезности и этапу (у Divine от 1 до 30 вперемешку). Ваттикус больше
 * не продаёт зелья арканы (они теперь из сундуков и варятся), а продаёт Арканный щит и Кристалл ясности. Делается на
 * обеих сторонах: список сделок Divine строит и на клиенте.
 */
public class ArcanaPrices {

    /** Вещь -> {валюта: 0 арканиум, 1 жетоны подземелий; сколько}. */
    static final Map<Item, int[]> PRICE = new HashMap<Item, int[]>();
    public static final int WARD_PRICE = 20, CLARITY_PRICE = 25, BREWER_PRICE = 32;

    public static void init() {
        put(ArcanaItems.attractor, 0, 3);
        put(ArcanaItems.reflector, 0, 3);
        put(ArcanaItems.stormSword, 0, 5);
        put(ArcanaItems.firefly, 0, 5);
        put(ArcanaItems.starlight, 0, 6);
        put(ArcanaItems.divineAccumulator, 0, 6);
        put(ArcanaItems.shadowSword, 0, 8);
        put(ArcanaItems.arcaniumSaber, 0, 8);
        put(ArcanaItems.captainsSparkler, 0, 10);
        put(ArcanaItems.enderScepter, 0, 12);
        put(ArcanaItems.wraithbane, 0, 12);
        put(ArcanaItems.staffStarlight, 1, 15);
        put(ArcanaItems.meriksMissile, 1, 15);
        put(ArcanaItems.meteorMash, 1, 17);
        put(ArcanaItems.arcaniteBlaster, 0, 18);
        put(ArcanaItems.generalsStaff, 0, 18);
        put(ArcanaItems.arcaniteBlade, 0, 30);
        put(ArcanaItems.liviciaSword, 1, 30);
        // зелья арканы Divine (дают Ясность) лежат в сундуках; свои зелья варятся на арканной стойке
        for (String c : new String[] { ChestGenHooks.DUNGEON_CHEST, ChestGenHooks.MINESHAFT_CORRIDOR, ChestGenHooks.PYRAMID_DESERT_CHEST,
            ChestGenHooks.PYRAMID_JUNGLE_CHEST, ChestGenHooks.STRONGHOLD_CORRIDOR, ChestGenHooks.STRONGHOLD_CROSSING }) {
            ChestGenHooks.addItem(c, new WeightedRandomChestContent(new ItemStack(ArcanaItems.weakArcanaPotion), 1, 2, 8));
            ChestGenHooks.addItem(c, new WeightedRandomChestContent(new ItemStack(ArcanaItems.strongArcanaPotion), 1, 1, 3));
        }
    }

    private static void put(Item i, int currency, int count) {
        if (i != null) PRICE.put(i, new int[] { currency, count });
    }

    static Item currency(int c) {
        return c == 1 ? ArcanaItems.dungeonTokens : ArcanaItems.arcanium;
    }

    /** Цена в списке сделок: правит покупку, у Ваттикуса заменяет зелья на аксессуары арканы. */
    public static void apply(EntityDivineRPGVillager v, MerchantRecipeList list) {
        boolean ward = false, clarity = false, brewer = false;
        Item brewerItem = Item.getItemFromBlock(com.divinerpgextended.registry.ModBlocks.arcaneBrewer);
        for (Iterator<?> it = list.iterator(); it.hasNext();) {
            MerchantRecipe r = (MerchantRecipe) it.next();
            ItemStack out = r.getItemToSell();
            if (out == null) continue;
            if (v instanceof EntityVatticus && (out.getItem() == ArcanaItems.weakArcanaPotion || out.getItem() == ArcanaItems.strongArcanaPotion)) {
                it.remove();
                continue;
            }
            if (out.getItem() == ModItems.trinket && out.getItemDamage() == Trinket.ARCANE_WARD.ordinal()) ward = true;
            if (out.getItem() == ModItems.trinket && out.getItemDamage() == Trinket.CLARITY_CRYSTAL.ordinal()) clarity = true;
            if (out.getItem() == brewerItem) brewer = true;
            int[] pr = PRICE.get(out.getItem());
            ItemStack buy = r.getItemToBuy();
            if (pr == null || buy == null) continue;
            Item cur = currency(pr[0]);
            if (buy.getItem() != cur) buy.func_150996_a(cur);
            buy.stackSize = pr[1];
            buy.setItemDamage(0);
        }
        if (v instanceof EntityVatticus) {
            if (!ward) list.add(new MerchantRecipe(new ItemStack(ArcanaItems.arcanium, WARD_PRICE), new ItemStack(ModItems.trinket, 1, Trinket.ARCANE_WARD.ordinal())));
            if (!clarity) list.add(new MerchantRecipe(new ItemStack(ArcanaItems.arcanium, CLARITY_PRICE), new ItemStack(ModItems.trinket, 1, Trinket.CLARITY_CRYSTAL.ordinal())));
            if (!brewer) list.add(new MerchantRecipe(new ItemStack(ArcanaItems.arcanium, BREWER_PRICE), new ItemStack(brewerItem)));
        }
    }

    /** И перед самым открытием торговли (список мог только что собраться). */
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOW)
    public void onInteract(net.minecraftforge.event.entity.player.EntityInteractEvent e) {
        if (!(e.target instanceof EntityDivineRPGVillager)) return;
        MerchantRecipeList list = ((EntityDivineRPGVillager) e.target).getRecipes(e.entityPlayer);
        if (list != null) apply((EntityDivineRPGVillager) e.target, list);
    }

    @SubscribeEvent
    public void onMerchantTick(LivingEvent.LivingUpdateEvent e) {
        if (!(e.entityLiving instanceof EntityDivineRPGVillager) || e.entityLiving.ticksExisted % 10 != 1) return;
        MerchantRecipeList list = ((EntityDivineRPGVillager) e.entityLiving).getRecipes(null);
        if (list != null) apply((EntityDivineRPGVillager) e.entityLiving, list);
    }
}
