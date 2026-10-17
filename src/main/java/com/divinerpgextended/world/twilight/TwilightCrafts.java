package com.divinerpgextended.world.twilight;

import java.util.Iterator;
import java.util.List;

import net.divinerpg.blocks.base.portal.BlockModPortal;
import net.divinerpg.utils.blocks.TwilightBlocks;
import net.divinerpg.utils.items.TwilightItemsArmor;
import net.divinerpg.utils.items.TwilightItemsTools;
import net.divinerpg.utils.items.TwilightItemsWeapons;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.divinerpg.utils.items.VanillaItemsTools;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.OreDictionary;

import com.divinerpgextended.Config;
import com.divinerpgextended.DivineRPGExtended;
import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.world.CadillionWorld;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Крафты сумеречных миров вместо рецептов Divine:
 * <ul>
 * <li>палки мира из досок дерева мира, факелы мира (пыль на палке), стрелы мира на палках мира;</li>
 * <li>броня, оружие и инструменты - цепочкой: вещь мира = такая же вещь прошлого мира + куски мира + материал мобов
 * мира, рукояти из палок мира. Эдем начинается с божественных вещей верхнего мира;</li>
 * <li>рамка портала в следующий мир - камни врат из сердца стража (трофей Великого кадиллиона).</li>
 * </ul>
 */
public final class TwilightCrafts {

    private TwilightCrafts() {}

    /** Материал мобов для брони, оружия и инструментов мира (индекс в {@link TwilightMaterial#ofWorld}). */
    public static final int ARMOR_MAT = 1, WEAPON_MAT = 0, TOOL_MAT = 2;

    public static ItemStack stick(WorldWood w) {
        return new ItemStack(ModItems.worldStick, 1, w.ordinal());
    }

    public static ItemStack mat(TwilightWorld w, int idx) {
        return new ItemStack(ModItems.twilightMaterial, 1, TwilightMaterial.ofWorld(w)[idx].ordinal());
    }

    /** Предыдущая вещь цепочки с любой прочностью. */
    private static ItemStack any(Item i) {
        return new ItemStack(i, 1, OreDictionary.WILDCARD_VALUE);
    }

    private static Item chunk(TwilightWorld w) {
        switch (w) {
            case EDEN: return net.divinerpg.utils.items.TwilightItemsOther.edenChunk;
            case WILDWOOD: return net.divinerpg.utils.items.TwilightItemsOther.wildwoodChunk;
            case APALACHIA: return net.divinerpg.utils.items.TwilightItemsOther.apalachiaChunk;
            case SKYTHERN: return net.divinerpg.utils.items.TwilightItemsOther.skythernChunk;
            default: return net.divinerpg.utils.items.TwilightItemsOther.mortumChunk;
        }
    }

    /** Оружие мира: [0] клинок, [1] лук, [2] фазер, [3] блиц. */
    public static Item[] weapons(TwilightWorld w) {
        switch (w) {
            case EDEN: return new Item[] { TwilightItemsWeapons.edenBlade, TwilightItemsWeapons.edenBow, TwilightItemsWeapons.edenPhaser, TwilightItemsWeapons.edenBlitz };
            case WILDWOOD: return new Item[] { TwilightItemsWeapons.wildwoodBlade, TwilightItemsWeapons.wildwoodBow, TwilightItemsWeapons.wildwoodPhaser, TwilightItemsWeapons.wildwoodBlitz };
            case APALACHIA: return new Item[] { TwilightItemsWeapons.apalachiaBlade, TwilightItemsWeapons.apalachiaBow, TwilightItemsWeapons.apalachiaPhaser, TwilightItemsWeapons.apalachiaBlitz };
            case SKYTHERN: return new Item[] { TwilightItemsWeapons.skythernBlade, TwilightItemsWeapons.skythernBow, TwilightItemsWeapons.skythernPhaser, TwilightItemsWeapons.skythernBlitz };
            default: return new Item[] { TwilightItemsWeapons.mortumBlade, TwilightItemsWeapons.mortumBow, TwilightItemsWeapons.mortumPhaser, TwilightItemsWeapons.mortumBlitz };
        }
    }

    /** Инструменты мира: [0] кирка, [1] лопата, [2] топор. */
    public static Item[] tools(TwilightWorld w) {
        switch (w) {
            case EDEN: return new Item[] { TwilightItemsTools.edenPickaxe, TwilightItemsTools.edenShovel, TwilightItemsTools.edenAxe };
            case WILDWOOD: return new Item[] { TwilightItemsTools.wildwoodPickaxe, TwilightItemsTools.wildwoodShovel, TwilightItemsTools.wildwoodAxe };
            case APALACHIA: return new Item[] { TwilightItemsTools.apalachiaPickaxe, TwilightItemsTools.apalachiaShovel, TwilightItemsTools.apalachiaAxe };
            case SKYTHERN: return new Item[] { TwilightItemsTools.skythernPickaxe, TwilightItemsTools.skythernShovel, TwilightItemsTools.skythernAxe };
            default: return new Item[] { TwilightItemsTools.mortumPickaxe, TwilightItemsTools.mortumShovel, TwilightItemsTools.mortumAxe };
        }
    }

    /** Вещи перед Эдемом: божественная броня, меч и инструменты верхнего мира, ванильный лук; фазера и блица нет. */
    public static Item[] prevArmor(TwilightWorld w) {
        if (w == TwilightWorld.EDEN) return new Item[] { VanillaItemsArmor.divineHelmet, VanillaItemsArmor.divineBody, VanillaItemsArmor.divineLegs, VanillaItemsArmor.divineBoots };
        return TwilightWorld.values()[w.ordinal() - 1].armor();
    }

    private static Item[] prevWeapons(TwilightWorld w) {
        if (w == TwilightWorld.EDEN) return new Item[] { VanillaItemsWeapons.divineSword, Items.bow, null, null };
        return weapons(TwilightWorld.values()[w.ordinal() - 1]);
    }

    private static Item[] prevTools(TwilightWorld w) {
        if (w == TwilightWorld.EDEN) return new Item[] { VanillaItemsTools.divinePickaxe, VanillaItemsTools.divineShovel, VanillaItemsTools.divineAxe };
        return tools(TwilightWorld.values()[w.ordinal() - 1]);
    }

    /** Убрать рецепты Divine и поставить свои. Вызывать в postInit (после рецептов Divine). */
    public static void postInit() {
        rails();
        if (Config.worldCrafts) {
            sticks();
            for (TwilightWorld w : TwilightWorld.values()) world(w);
            divineTier();
        }
        if (Config.gateStones) gates();
    }

    /** Рельсы Divine в руке ставятся как рельсы аддона (на пол, стену, потолок) - их уже могли накопать или взять в творческом. */
    public static class DivineRailSwap {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onUse(net.minecraftforge.event.entity.player.PlayerInteractEvent e) {
            if (e.action != net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
            ItemStack held = e.entityPlayer.getHeldItem();
            if (held == null || held.getItem() != Item.getItemFromBlock(TwilightBlocks.apalachiaRails)) return;
            e.setCanceled(true);
            ItemStack tmp = new ItemStack(ModBlocks.skyRail);
            if (tmp.getItem().onItemUse(tmp, e.entityPlayer, e.world, e.x, e.y, e.z, e.face, 0.5F, 0.5F, 0.5F) && tmp.stackSize == 0
                && !e.entityPlayer.capabilities.isCreativeMode) held.stackSize--;
        }
    }

    /** Рельсы Апалачии (на пол, стены, потолок) вместо бесполезных рельсов Divine; вагонетка Апалачии к ним. */
    private static void rails() {
        Item old = Item.getItemFromBlock(TwilightBlocks.apalachiaRails);
        remove(old);
        GameRegistry.addRecipe(new ItemStack(ModBlocks.skyRail, 16), "F F", "FSF", "F F", 'F', net.divinerpg.utils.items.TwilightItemsOther.apalachiaFragments, 'S',
            stick(WorldWood.APALACHIA));
        GameRegistry.addShapelessRecipe(new ItemStack(ModBlocks.skyRail), old); // старые рельсы Divine - в новые
        GameRegistry.addShapelessRecipe(new ItemStack(ModItems.skyCart), Items.minecart, net.divinerpg.utils.items.TwilightItemsOther.apalachiaGem,
            net.divinerpg.utils.items.TwilightItemsOther.apalachiaGem);
    }

    private static void sticks() {
        List<IRecipe> list = recipes();
        for (WorldWood w : WorldWood.values()) {
            // свои палки раньше ванильного рецепта палок из любых досок
            IRecipe r = GameRegistry.addShapedRecipe(new ItemStack(ModItems.worldStick, 4, w.ordinal()), "P", "P", 'P', w.planks());
            list.remove(r);
            list.add(0, r);
        }
        for (TwilightWorld w : TwilightWorld.values()) {
            if (ModBlocks.worldLamp[w.ordinal()] != null)
                GameRegistry.addRecipe(new ItemStack(ModBlocks.worldLamp[w.ordinal()]), "III", "IFI", "III", 'I', Items.iron_ingot, 'F', w.fragments());
            GameRegistry.addRecipe(new ItemStack(ModBlocks.worldTorch[w.ordinal()], 4), "D", "S", 'D', w.dust(), 'S', stick(WorldWood.of(w)));
            if (w.arrow() != null) {
                remove(w.arrow());
                GameRegistry.addRecipe(new ItemStack(w.arrow(), 8), "F", "S", "Z", 'F', w.fragments(), 'S', stick(WorldWood.of(w)), 'Z', Items.feather);
            }
        }
    }

    private static void world(TwilightWorld w) {
        Item c = chunk(w);
        ItemStack s = stick(WorldWood.of(w)), am = mat(w, ARMOR_MAT), wm = mat(w, WEAPON_MAT), tm = mat(w, TOOL_MAT);
        Item[] armor = w.armor(), pa = prevArmor(w);
        for (Item i : armor) remove(i);
        GameRegistry.addRecipe(new ItemStack(armor[0]), "CMC", "CPC", 'C', c, 'M', am, 'P', any(pa[0]));
        GameRegistry.addRecipe(new ItemStack(armor[1]), "CPC", "MCM", "CCC", 'C', c, 'M', am, 'P', any(pa[1]));
        GameRegistry.addRecipe(new ItemStack(armor[2]), "CPC", "M M", "C C", 'C', c, 'M', am, 'P', any(pa[2]));
        GameRegistry.addRecipe(new ItemStack(armor[3]), "MPM", "C C", 'C', c, 'M', am, 'P', any(pa[3]));

        Item[] wp = weapons(w), pw = prevWeapons(w);
        for (Item i : wp) remove(i);
        GameRegistry.addRecipe(new ItemStack(wp[0]), " C ", "MPM", " S ", 'C', c, 'M', wm, 'P', any(pw[0]), 'S', s);
        GameRegistry.addRecipe(new ItemStack(wp[1]), "CSX", "MPX", "CSX", 'C', c, 'M', wm, 'P', any(pw[1]), 'S', s, 'X', Items.string);
        GameRegistry.addRecipe(new ItemStack(wp[2]), " C ", "CPC", " S ", 'C', c, 'P', pw[2] == null ? wm : any(pw[2]), 'S', s);
        GameRegistry.addRecipe(new ItemStack(wp[3]), "C C", "MPM", "CSC", 'C', c, 'M', wm, 'P', pw[3] == null ? wm : any(pw[3]), 'S', s);

        Item[] tl = tools(w), pt = prevTools(w);
        for (Item i : tl) remove(i);
        GameRegistry.addRecipe(new ItemStack(tl[0]), "CMC", " P ", " S ", 'C', c, 'M', tm, 'P', any(pt[0]), 'S', s);
        GameRegistry.addRecipe(new ItemStack(tl[1]), "MCM", " P ", " S ", 'C', c, 'M', tm, 'P', any(pt[1]), 'S', s);
        GameRegistry.addRecipe(new ItemStack(tl[2]), "CM ", "CP ", " S ", 'C', c, 'M', tm, 'P', any(pt[2]), 'S', s);
    }

    /** Божественный меч и инструменты верхнего мира - на палках из божественного дерева. */
    private static void divineTier() {
        ItemStack s = stick(WorldWood.DIVINE);
        Item st = VanillaItemsOther.divineStone;
        remove(VanillaItemsWeapons.divineSword);
        remove(VanillaItemsTools.divinePickaxe);
        remove(VanillaItemsTools.divineShovel);
        remove(VanillaItemsTools.divineAxe);
        GameRegistry.addRecipe(new ItemStack(VanillaItemsWeapons.divineSword), " I ", "III", " S ", 'I', st, 'S', s);
        GameRegistry.addRecipe(new ItemStack(VanillaItemsTools.divinePickaxe), "III", " S ", " S ", 'I', st, 'S', s);
        GameRegistry.addRecipe(new ItemStack(VanillaItemsTools.divineShovel), " I ", " S ", " S ", 'I', st, 'S', s);
        GameRegistry.addRecipe(new ItemStack(VanillaItemsTools.divineAxe), "II ", "IS ", " S ", 'I', st, 'S', s);
    }

    /** Порталы Дикого леса..Мортума: рамка из камней врат вместо блоков камней прошлого мира. */
    private static void gates() {
        Block[] portals = { TwilightBlocks.wildwoodPortal, TwilightBlocks.apalachiaPortal, TwilightBlocks.skythernPortal, TwilightBlocks.mortumPortal };
        try {
            java.lang.reflect.Field f = BlockModPortal.class.getDeclaredField("blockFrame");
            f.setAccessible(true);
            for (CadillionWorld w : CadillionWorld.values()) f.set(portals[w.ordinal()], ModBlocks.gateStone[w.ordinal()]);
        } catch (Exception e) {
            DivineRPGExtended.LOG.error("Не удалось заменить рамки порталов на камни врат", e);
            return;
        }
        for (CadillionWorld w : CadillionWorld.values()) {
            TwilightWorld tw = TwilightWorld.values()[w.ordinal()];
            GameRegistry.addRecipe(new ItemStack(ModBlocks.gateStone[w.ordinal()], 12), "BBB", "BHB", "BBB", 'B', tw.gemBlock(), 'H',
                new ItemStack(ModItems.guardianHeart, 1, w.ordinal()));
        }
    }

    /** Портал, рамка которого - этот камень врат, или null. */
    public static BlockModPortal portalOf(Block gate) {
        Block[] portals = { TwilightBlocks.wildwoodPortal, TwilightBlocks.apalachiaPortal, TwilightBlocks.skythernPortal, TwilightBlocks.mortumPortal };
        for (int i = 0; i < 4; i++) if (ModBlocks.gateStone[i] == gate) return (BlockModPortal) portals[i];
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<IRecipe> recipes() {
        return CraftingManager.getInstance().getRecipeList();
    }

    private static void remove(Item item) {
        for (Iterator<IRecipe> it = recipes().iterator(); it.hasNext();) {
            ItemStack out = it.next().getRecipeOutput();
            if (out != null && out.getItem() == item) it.remove();
        }
    }
}
