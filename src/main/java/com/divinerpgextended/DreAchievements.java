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
 * Ачивки аддона - на одной странице с ачивками Divine (правее их дерева): главная ветка - путь по боссам, боковые -
 * Наковальня, рыбалка, торговцы Арканы, Джек. Ачивка выдаётся вместе со всеми родителями (иначе Minecraft её не даст),
 * а уже сделанное до появления ачивок засчитывается при входе и раз в 5 секунд.
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
        // Страница Divine занимает столбцы 0-9: наши ветки - правее, а связи с её ачивками там, где это одно и то же место
        // в прогрессии (вход в Аркану, Эдем, Мортум, Джек, питомец Зелуса).
        // главная ветка: путь по боссам
        crab = a("crab", 11, 0, s(VanillaItemsOther.crabClaw), null, false);
        carcinus = a("carcinus", 11, 2, s(ModItems.deepShell), crab, false);
        snowGlacon = a("snow_glacon", 13, 2, s(IceikaItems.snowGlobe), carcinus, false);
        lichKing = a("lich_king", 15, 2, s(ModItems.throneShard), snowGlacon, false);
        ancientEntity = a("ancient_entity", 17, 2, s(VanillaItemsOther.mysteriousClock), lichKing, false);
        watcher = a("watcher", 19, 2, s(ModItems.watcherEye), ancientEntity, false);
        rime = a("rime", 13, 0, s(ModItems.rimeIngot), snowGlacon, false);
        frostbite = a("frostbite", 15, 0, s(IceikaItems.iceShards), snowGlacon, false);
        lockedPortal = a("locked_portal", 19, 0, s(Item.getItemFromBlock(ArcanaBlocks.arcanaPortalFrame)), null, false);
        // сумеречные миры: от входа в Эдем и в Мортум (ачивки Divine)
        eden = a("eden", 11, 4, s(TwilightItemsOther.edenSoul), DivineRPGAchievements.possibilities, false);
        wildwood = a("wildwood", 13, 4, s(TwilightItemsOther.wildwoodSoul), eden, false);
        apalachia = a("apalachia", 15, 4, s(TwilightItemsOther.apalachiaSoul), wildwood, false);
        skythern = a("skythern", 17, 4, s(TwilightItemsOther.skythernSoul), apalachia, false);
        twilightDemon = a("twilight_demon", 11, 6, s(TwilightItemsOther.mortumSoul), DivineRPGAchievements.darkAnotherDay, false);
        karot = a("karot", 13, 6, s(TwilightItemsOther.mortumGem), twilightDemon, true);
        // Наковальня Странника
        anvil = a("anvil", 15, 6, s(Item.getItemFromBlock(ModBlocks.wandererAnvil)), null, false);
        perk = a("perk", 17, 6, s(ModItems.wanderer[1]), anvil, false);
        temper = a("temper", 19, 6, s(TwilightItemsOther.edenGem), perk, true);
        // торговцы Арканы: от входа в Аркану (ачивка Divine)
        memoryCrystal = a("memory_crystal", 11, 8, s(ModItems.memoryCrystal), DivineRPGAchievements.whatLiesWithin, false);
        reforge = a("reforge", 13, 8, s(Items.iron_sword), memoryCrystal, false);
        dullReforge = a("dull_reforge", 13, 10, s(Items.stone_sword), reforge, false);
        legendary = a("legendary", 15, 10, s(Items.golden_sword), reforge, true);
        hallOfFame = a("hall_of_fame", 15, 8, s(Items.skull), memoryCrystal, false);
        corporal = a("corporal", 17, 8, s(ArcanaItems.reflector), hallOfFame, false);
        research = a("research", 11, 10, s(ArcanaItems.wizardBook), memoryCrystal, false);
        furnaceModule = a("furnace_module", 17, 10, s(ModItems.furnaceModule), memoryCrystal, false);
        petTrainer = a("pet_trainer", 19, 8, s(Items.lead), DivineRPGAchievements.littleCreature, false);
        petCarrier = a("pet_carrier", 19, 10, s(ModItems.petCarrier), petTrainer, false);
        // рыбалка
        fishFirst = a("fish_first", 21, 0, s(Items.fishing_rod), null, false);
        fishCrate = a("fish_crate", 23, 0, s(ModItems.crate), fishFirst, false);
        fishQuest = a("fish_quest", 21, 2, s(ModItems.fish, 2), fishFirst, false);
        goldenRod = a("golden_rod", 23, 2, s(ModItems.rods[Rod.GOLDEN.ordinal()]), fishQuest, true);
        relic = a("relic", 21, 4, s(ModItems.nightmareDrill), fishFirst, true);
        // Джек о'Мэн и аксессуары: от разговора с Джеком (ачивка Divine)
        jackTrade = a("jack_trade", 21, 6, s(VanillaItemsOther.shadowCoins), DivineRPGAchievements.halloweenSpirit, false);
        headHunter = a("head_hunter", 23, 6, s(ModItems.trinket, 0), jackTrade, false);
        allSlots = a("all_slots", 23, 8, s(ModItems.trinket, 4), headHunter, true);

        // всё на одну страницу с ачивками Divine
        DivineRPGAchievements.page.getAchievements().addAll(ALL);
    }

    /** Выдать ачивку (сначала все её родители - Minecraft не даёт ачивку без родителя). */
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

    /** Обработчик: краб, проверка уже сделанного при входе и раз в 5 секунд. */
    public static class Handler {

        @SubscribeEvent
        public void onKill(LivingDeathEvent e) {
            if (!(e.source.getEntity() instanceof EntityPlayer)) return;
            if (e.entityLiving instanceof EntityCrab || e.entityLiving instanceof EntityKingCrab || e.entityLiving instanceof EntityLairCrab) {
                give((EntityPlayer) e.source.getEntity(), crab);
            }
        }

        /** Окоченел: обморожение от холода Айсики. */
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

        /** Всё, что можно узнать по сохранённому прогрессу. */
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
