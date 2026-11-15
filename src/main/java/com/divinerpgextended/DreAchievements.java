package com.divinerpgextended;

import java.util.ArrayList;
import java.util.List;

import net.divinerpg.entities.vanilla.EntityCrab;
import net.divinerpg.entities.vanilla.EntityKingCrab;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.TwilightItemsOther;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;
import net.divinerpg.libs.DivineRPGAchievements;
import net.divinerpg.utils.blocks.ArcanaBlocks;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.divinerpgextended.accessory.Accessories;
import com.divinerpgextended.arcana.ArcanaMerchant;
import com.divinerpgextended.arcana.WarGeneral;
import com.divinerpgextended.asm.ArcanaHooks;
import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossProgress;
import com.divinerpgextended.entity.EntityLairCrab;
import com.divinerpgextended.fishing.FishingEvents;
import com.divinerpgextended.fishing.Rod;
import com.divinerpgextended.item.ArmorData;
import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Addon achievements - on the same page as the Divine achievements (to the right of their tree): the main branch is the path through the bosses, the side ones are
 * the Anvil, fishing, Arcana merchants, Jack. An achievement is granted together with all its parents (otherwise Minecraft will not give it),
 * and what was already done before the achievements appeared is counted on join and every 5 seconds.
 */
public final class DreAchievements {

    public static Achievement crab, carcinus, snowGlacon, lichKing, ancientEntity, watcher, eden, wildwood, apalachia,
        skythern, twilightDemon, karot;
    public static Achievement anvil, perk, temper, rime, frostbite, lockedPortal;
    public static Achievement fishFirst, fishCrate, fishQuest, goldenRod, relic;
    public static Achievement memoryCrystal, reforge, dullReforge, legendary, hallOfFame, corporal, research, petTrainer,
        petCarrier, furnaceModule;
    public static Achievement jackTrade, headHunter, allSlots;

    private static final List<Achievement> ALL = new ArrayList<Achievement>();

    private DreAchievements() {}

    private static Achievement a(String key, int col, int row, ItemStack icon, Achievement parent, boolean special) {
        Achievement ach = new Achievement("achievement.dre_" + key, "dre_" + key, col, row, icon, parent);
        if (parent == null) ach.initIndependentStat();
        if (special) ach.setSpecial();
        ach.registerStat();
        ALL.add(ach);
        return ach;
    }

    private static ItemStack s(Item i) {
        return new ItemStack(i);
    }

    private static ItemStack s(Item i, int meta) {
        return new ItemStack(i, 1, meta);
    }

    public static void register() {
        // The Divine page takes columns 0-9, our block takes columns 11-21. Minecraft draws a link as a corner: along the child's row
        // to the parent's column and up along the parent's column. So children of one parent stand in a "comb" - further right and
        // each on its own row, so that lines do not run under other people's icons. There are no long links to the Divine tree.
        // main branch: path through the bosses
        crab = a("crab", 11, 0, s(VanillaItemsOther.crabClaw), null, false);
        carcinus = a("carcinus", 13, 0, s(ModItems.deepShell), crab, false);
        snowGlacon = a("snow_glacon", 15, 0, s(IceikaItems.snowGlobe), carcinus, false);
        lichKing = a("lich_king", 17, 0, s(ModItems.throneShard), snowGlacon, false);
        ancientEntity = a("ancient_entity", 19, 0, s(VanillaItemsOther.mysteriousClock), lichKing, false);
        watcher = a("watcher", 21, 0, s(ModItems.watcherEye), ancientEntity, false);
        frostbite = a("frostbite", 13, 2, s(IceikaItems.iceShards), snowGlacon, false);
        rime = a("rime", 17, 2, s(ModItems.rimeIngot), snowGlacon, false);
        lockedPortal = a("locked_portal", 21, 2, s(Item.getItemFromBlock(ArcanaBlocks.arcanaPortalFrame)), null, false);
        // twilight worlds
        eden = a("eden", 11, 4, s(TwilightItemsOther.edenSoul), null, false);
        wildwood = a("wildwood", 13, 4, s(TwilightItemsOther.wildwoodSoul), eden, false);
        apalachia = a("apalachia", 15, 4, s(TwilightItemsOther.apalachiaSoul), wildwood, false);
        skythern = a("skythern", 17, 4, s(TwilightItemsOther.skythernSoul), apalachia, false);
        twilightDemon = a("twilight_demon", 19, 4, s(TwilightItemsOther.mortumSoul), skythern, false);
        karot = a("karot", 21, 4, s(TwilightItemsOther.mortumGem), twilightDemon, true);
        // Wanderer Anvil
        anvil = a("anvil", 11, 6, s(Item.getItemFromBlock(ModBlocks.wandererAnvil)), null, false);
        perk = a("perk", 13, 6, s(ModItems.wanderer[1]), anvil, false);
        temper = a("temper", 15, 6, s(TwilightItemsOther.edenGem), perk, true);
        // Jack O Man and accessories
        jackTrade = a("jack_trade", 17, 6, s(VanillaItemsOther.shadowCoins), null, false);
        headHunter = a("head_hunter", 19, 6, s(ModItems.trinket, 0), jackTrade, false);
        allSlots = a("all_slots", 21, 6, s(ModItems.trinket, 4), headHunter, true);
        // fishing
        fishFirst = a("fish_first", 11, 8, s(Items.fishing_rod), null, false);
        fishCrate = a("fish_crate", 13, 8, s(ModItems.crate), fishFirst, false);
        fishQuest = a("fish_quest", 13, 10, s(ModItems.fish, 2), fishFirst, false);
        goldenRod = a("golden_rod", 15, 10, s(ModItems.rods[Rod.GOLDEN.ordinal()]), fishQuest, true);
        relic = a("relic", 13, 12, s(ModItems.nightmareDrill), fishFirst, true);
        // pets
        petTrainer = a("pet_trainer", 17, 8, s(Items.lead), null, false);
        petCarrier = a("pet_carrier", 19, 8, s(ModItems.petCarrier), petTrainer, false);
        // Arcana merchants
        memoryCrystal = a("memory_crystal", 11, 14, s(ModItems.memoryCrystal), null, false);
        reforge = a("reforge", 13, 14, s(Items.iron_sword), memoryCrystal, false);
        dullReforge = a("dull_reforge", 15, 14, s(Items.stone_sword), reforge, false);
        legendary = a("legendary", 15, 16, s(Items.golden_sword), reforge, true);
        hallOfFame = a("hall_of_fame", 13, 18, s(Items.skull), memoryCrystal, false);
        corporal = a("corporal", 15, 18, s(ArcanaItems.reflector), hallOfFame, false);
        research = a("research", 13, 20, s(ArcanaItems.wizardBook), memoryCrystal, false);
        furnaceModule = a("furnace_module", 13, 22, s(ModItems.furnaceModule), memoryCrystal, false);

        // everything on one page with the Divine achievements
        DivineRPGAchievements.page.getAchievements().addAll(ALL);
    }

    /** Grant an achievement (all its parents first - Minecraft does not give an achievement without its parent). */
    public static void give(EntityPlayer p, Achievement a) {
        if (a == null || p == null || p.worldObj.isRemote || !(p instanceof EntityPlayerMP)) return;
        EntityPlayerMP mp = (EntityPlayerMP) p;
        if (mp.func_147099_x().hasAchievementUnlocked(a)) return;
        if (a.parentAchievement != null) give(p, a.parentAchievement);
        p.triggerAchievement(a);
    }

    public static Achievement forBoss(Boss b) {
        switch (b) {
            case CARCINUS: return carcinus;
            case SNOW_GLACON: return snowGlacon;
            case LICH_KING: return lichKing;
            case ANCIENT_ENTITY: return ancientEntity;
            case WATCHER: return watcher;
            case CADILLION_EDEN: return eden;
            case CADILLION_WILDWOOD: return wildwood;
            case CADILLION_APALACHIA: return apalachia;
            case CADILLION_SKYTHERN: return skythern;
            case TWILIGHT_DEMON: return twilightDemon;
            case KAROT: return karot;
            default: return null;
        }
    }

    /** Handler: crab, check of what was already done on join and every 5 seconds. */
    public static class Handler {

        @SubscribeEvent
        public void onKill(LivingDeathEvent e) {
            if (!(e.source.getEntity() instanceof EntityPlayer)) return;
            if (e.entityLiving instanceof EntityCrab || e.entityLiving instanceof EntityKingCrab || e.entityLiving instanceof EntityLairCrab) {
                give((EntityPlayer) e.source.getEntity(), crab);
            }
        }

        /** Numbed: frostbite from the cold of Iceika. */
        @SubscribeEvent
        public void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
            if (e.entityLiving instanceof EntityPlayer && e.source == com.divinerpgextended.cold.ColdHandler.FROSTBITE) {
                give((EntityPlayer) e.entityLiving, frostbite);
            }
        }

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            check(e.player);
        }

        @SubscribeEvent
        public void onTick(TickEvent.PlayerTickEvent e) {
            if (e.phase == TickEvent.Phase.END && !e.player.worldObj.isRemote && e.player.ticksExisted % 100 == 0) check(e.player);
        }

        /** Everything that can be learned from the saved progress. */
        private static void check(EntityPlayer p) {
            for (Boss b : Boss.values()) if (BossProgress.has(p, b)) give(p, forBoss(b));
            if (p.dimension == ConfigurationHelper.arcana) give(p, DivineRPGAchievements.whatLiesWithin);
            if (FishingEvents.quests(p) >= 1) give(p, fishQuest);
            if (FishingEvents.quests(p) >= 10) give(p, goldenRod);
            if (ArcanaMerchant.metMask(p) != 0) give(p, memoryCrystal);
            if (WarGeneral.statueCount(p) >= 1) give(p, hallOfFame);
            if (WarGeneral.ordersDone(p) >= 3) give(p, corporal);
            if (ArcanaHooks.tasks(p) >= 1) give(p, research);
            if (Accessories.unlocked(p) >= Accessories.SIZE) give(p, allSlots);
            if (p.inventory.hasItem(ModItems.rimeIngot)) give(p, rime);
            int tier = ArmorData.equippedTier(p);
            if (tier >= 2) give(p, temper);
            for (ItemStack s : p.inventory.armorInventory) {
                if (s != null && !ArmorData.perks(s).isEmpty()) give(p, perk);
            }
        }

        @SubscribeEvent
        public void onCraft(PlayerEvent.ItemCraftedEvent e) {
            if (e.crafting != null && e.crafting.getItem() == Item.getItemFromBlock(ModBlocks.wandererAnvil)) give(e.player, anvil);
        }
    }
}
