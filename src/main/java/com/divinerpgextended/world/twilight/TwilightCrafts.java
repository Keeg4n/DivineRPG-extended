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
 * Twilight dimension crafts instead of Divine recipes:
 * <ul>
 * <li>world sticks from world wood planks, world torches (dust on a stick), world arrows on world sticks;</li>
 * <li>world armor = world pieces + world mob material + the time gear of the previous world ({@link #gear}, not consumed in the crafting table,
 * Eden has none): the previous world's set no longer goes into the craft;</li>
 * <li>weapons and tools - as a chain: a world item = the same item of the previous world + world pieces + world mob material, handles
 * from world sticks. Eden starts with divine items of the overworld;</li>
 * <li>Wildwood additionally asks for sea items: a whale fin into armor, a shark anchor into a blade, Liopleurodon teeth into bows and
 * cannons, a crab claw into tools;</li>
 * <li>the portal frame to the next world - gate stones from the guardian heart (a trophy of the Great Cadillion).</li>
 * </ul>
 */
public final class TwilightCrafts {

    private TwilightCrafts() {}

    /** Mob material for world armor, weapons and tools (index in {@link TwilightMaterial#ofWorld}). */
    public static final int ARMOR_MAT = 1, WEAPON_MAT = 0, TOOL_MAT = 2;

    public static ItemStack stick(WorldWood w) {
        return new ItemStack(ModItems.worldStick, 1, w.ordinal());
    }

    public static ItemStack mat(TwilightWorld w, int idx) {
        return new ItemStack(ModItems.twilightMaterial, 1, TwilightMaterial.ofWorld(w)[idx].ordinal());
    }

    /** The previous item of the chain with any durability. */
    private static ItemStack any(Item i) {
        return new ItemStack(i, 1, OreDictionary.WILDCARD_VALUE);
    }

    public static Item chunk(TwilightWorld w) {
        switch (w) {
            case EDEN: return net.divinerpg.utils.items.TwilightItemsOther.edenChunk;
            case WILDWOOD: return net.divinerpg.utils.items.TwilightItemsOther.wildwoodChunk;
            case APALACHIA: return net.divinerpg.utils.items.TwilightItemsOther.apalachiaChunk;
            case SKYTHERN: return net.divinerpg.utils.items.TwilightItemsOther.skythernChunk;
            default: return net.divinerpg.utils.items.TwilightItemsOther.mortumChunk;
        }
    }

    /** World weapons: [0] blade, [1] bow, [2] phaser, [3] blitz. */
    public static Item[] weapons(TwilightWorld w) {
        switch (w) {
            case EDEN: return new Item[] { TwilightItemsWeapons.edenBlade, TwilightItemsWeapons.edenBow, TwilightItemsWeapons.edenPhaser, TwilightItemsWeapons.edenBlitz };
            case WILDWOOD: return new Item[] { TwilightItemsWeapons.wildwoodBlade, TwilightItemsWeapons.wildwoodBow, TwilightItemsWeapons.wildwoodPhaser, TwilightItemsWeapons.wildwoodBlitz };
            case APALACHIA: return new Item[] { TwilightItemsWeapons.apalachiaBlade, TwilightItemsWeapons.apalachiaBow, TwilightItemsWeapons.apalachiaPhaser, TwilightItemsWeapons.apalachiaBlitz };
            case SKYTHERN: return new Item[] { TwilightItemsWeapons.skythernBlade, TwilightItemsWeapons.skythernBow, TwilightItemsWeapons.skythernPhaser, TwilightItemsWeapons.skythernBlitz };
            default: return new Item[] { TwilightItemsWeapons.mortumBlade, TwilightItemsWeapons.mortumBow, TwilightItemsWeapons.mortumPhaser, TwilightItemsWeapons.mortumBlitz };
        }
    }

    /** World tools: [0] pickaxe, [1] shovel, [2] axe. */
    public static Item[] tools(TwilightWorld w) {
        switch (w) {
            case EDEN: return new Item[] { TwilightItemsTools.edenPickaxe, TwilightItemsTools.edenShovel, TwilightItemsTools.edenAxe };
            case WILDWOOD: return new Item[] { TwilightItemsTools.wildwoodPickaxe, TwilightItemsTools.wildwoodShovel, TwilightItemsTools.wildwoodAxe };
            case APALACHIA: return new Item[] { TwilightItemsTools.apalachiaPickaxe, TwilightItemsTools.apalachiaShovel, TwilightItemsTools.apalachiaAxe };
            case SKYTHERN: return new Item[] { TwilightItemsTools.skythernPickaxe, TwilightItemsTools.skythernShovel, TwilightItemsTools.skythernAxe };
            default: return new Item[] { TwilightItemsTools.mortumPickaxe, TwilightItemsTools.mortumShovel, TwilightItemsTools.mortumAxe };
        }
    }

    /** The time gear of the previous world - the key to world w armor (before Eden is the overworld, there is no gear - null). */
    public static ItemStack gear(TwilightWorld w) {
        return w == TwilightWorld.EDEN ? null : new ItemStack(ModItems.timeGear, 1, w.ordinal() - 1);
    }

    /**
     * The previous world's set of the chain (before Eden - divine): no longer goes into the armor craft, at home it gives a half-step
     * ({@link com.divinerpgextended.perk.HomeArmor}).
     */
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

    /** Remove Divine recipes and set our own. Call in postInit (after Divine recipes). */
    public static void postInit() {
        rails();
        if (Config.worldCrafts) {
            sticks();
            for (TwilightWorld w : TwilightWorld.values()) world(w);
            divineTier();
        }
        if (Config.gateStones) gates();
    }

    /**
     * Apalachia and Arcana rails of Divine are item-blocks: the client itself checks the floor under the rail and a click on a wall does not reach the server,
     * so without editing Divine bytecode they cannot be placed on a wall. So instead there are the addon's rails (the same
     * rails go on the floor, hanging ones on walls and ceiling), and Divine rails are removed from the game: no craft, not in the tab, broken ones
     * drop addon rails, those bought from Datticon and found ones become them right in the inventory.
     */
    public static class RailDrops {

        public static void hideDivine() {
            TwilightBlocks.apalachiaRails.setCreativeTab(null);
            net.divinerpg.utils.blocks.ArcanaBlocks.arcaniteRails.setCreativeTab(null);
        }

        /** Addon rails instead of Divine rails (null - not them). */
        public static Item replacement(Item i) {
            if (i == null) return null;
            if (i == Item.getItemFromBlock(TwilightBlocks.apalachiaRails)) return ModItems.apalachiaRails;
            if (i == Item.getItemFromBlock(net.divinerpg.utils.blocks.ArcanaBlocks.arcaniteRails)) return ModItems.arcanaRails;
            return null;
        }

        /** Divine rails in the inventory - in their place the same number of addon rails (the slot is not freed even for a moment). */
        public static int convert(net.minecraft.entity.player.EntityPlayer p) {
            int n = 0;
            ItemStack[] inv = p.inventory.mainInventory;
            for (int i = 0; i < inv.length; i++) {
                Item r = inv[i] == null ? null : replacement(inv[i].getItem());
                if (r == null) continue;
                inv[i] = new ItemStack(r, inv[i].stackSize);
                n++;
            }
            if (n > 0) p.inventoryContainer.detectAndSendChanges();
            return n;
        }

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onTick(cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent e) {
            if (e.phase == cpw.mods.fml.common.gameevent.TickEvent.Phase.END && !e.player.worldObj.isRemote && e.player.ticksExisted % 20 == 0) convert(e.player);
        }

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onDrops(net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent e) {
            Item mine = e.block == TwilightBlocks.apalachiaRails ? ModItems.apalachiaRails
                : e.block == net.divinerpg.utils.blocks.ArcanaBlocks.arcaniteRails ? ModItems.arcanaRails : null;
            if (mine == null) return;
            Item old = Item.getItemFromBlock(e.block);
            for (int i = 0; i < e.drops.size(); i++) {
                ItemStack d = e.drops.get(i);
                if (d != null && d.getItem() == old) e.drops.set(i, new ItemStack(mine, d.stackSize));
            }
        }
    }

    /** Divine Apalachia rails (ordinary on the floor, hanging ones on walls and ceiling) and the Apalachia Cart for them. */
    private static void rails() {
        Item old = Item.getItemFromBlock(TwilightBlocks.apalachiaRails);
        remove(old);
        RailDrops.hideDivine();
        // Apalachia rails: on the floor - real Divine rails, on a wall and ceiling - hanging ones; old Divine rails are converted into them
        GameRegistry.addShapelessRecipe(new ItemStack(ModItems.apalachiaRails), old);
        // Divine Arcana rails (from Datticon) - into addon Arcana rails: they accelerate the cart, and can also be placed on walls
        GameRegistry.addShapelessRecipe(new ItemStack(ModItems.arcanaRails), net.divinerpg.utils.blocks.ArcanaBlocks.arcaniteRails);
        GameRegistry.addRecipe(new ItemStack(ModItems.apalachiaRails, 16), "F F", "FSF", "F F", 'F', net.divinerpg.utils.items.TwilightItemsOther.apalachiaFragments, 'S',
            stick(WorldWood.APALACHIA));
        GameRegistry.addShapelessRecipe(new ItemStack(ModItems.skyCart), Items.minecart, net.divinerpg.utils.items.TwilightItemsOther.apalachiaGem,
            net.divinerpg.utils.items.TwilightItemsOther.apalachiaGem);
    }

    private static void sticks() {
        List<IRecipe> list = recipes();
        for (WorldWood w : WorldWood.values()) {
            // our own sticks before the vanilla recipe of sticks from any planks
            IRecipe r = GameRegistry.addShapedRecipe(new ItemStack(ModItems.worldStick, 4, w.ordinal()), "P", "P", 'P', w.planks());
            list.remove(r);
            list.add(0, r);
        }
        noVanillaSticks(list);
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

    /**
     * Ordinary sticks are not made from world planks (and are absent in NEI): recipes of sticks only from world planks are removed, from
     * ore dictionary recipes (planks of any wood) world planks are struck out.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void noVanillaSticks(List<IRecipe> list) {
        java.util.Set<Item> world = new java.util.HashSet<Item>();
        for (WorldWood w : WorldWood.values()) world.add(Item.getItemFromBlock(w.planks()));
        for (Iterator<IRecipe> it = list.iterator(); it.hasNext();) {
            IRecipe r = it.next();
            ItemStack out = r.getRecipeOutput();
            if (out == null || out.getItem() != Items.stick) continue;
            Object[] in = r instanceof net.minecraftforge.oredict.ShapedOreRecipe ? ((net.minecraftforge.oredict.ShapedOreRecipe) r).getInput()
                : r instanceof net.minecraftforge.oredict.ShapelessOreRecipe ? ((net.minecraftforge.oredict.ShapelessOreRecipe) r).getInput().toArray()
                : r instanceof net.minecraft.item.crafting.ShapedRecipes ? ((net.minecraft.item.crafting.ShapedRecipes) r).recipeItems
                : r instanceof net.minecraft.item.crafting.ShapelessRecipes ? ((net.minecraft.item.crafting.ShapelessRecipes) r).recipeItems.toArray() : null;
            if (in == null) continue;
            boolean worldOnly = false;
            for (int k = 0; k < in.length; k++) {
                if (in[k] instanceof ItemStack && world.contains(((ItemStack) in[k]).getItem())) worldOnly = true;
                if (!(in[k] instanceof List)) continue;
                List filtered = new java.util.ArrayList();
                for (Object o : (List) in[k]) if (!(o instanceof ItemStack && world.contains(((ItemStack) o).getItem()))) filtered.add(o);
                if (filtered.size() == ((List) in[k]).size()) continue;
                if (r instanceof net.minecraftforge.oredict.ShapedOreRecipe) in[k] = filtered;
                else { // for a shapeless recipe getInput is the recipe's own list
                    List live = ((net.minecraftforge.oredict.ShapelessOreRecipe) r).getInput();
                    live.set(live.indexOf(in[k]), filtered);
                }
            }
            if (worldOnly) it.remove();
        }
    }

    /** Whether there is an ordinary sticks recipe accepting these planks (for selftest). */
    public static boolean vanillaSticksFrom(net.minecraft.world.World w, Block planks) {
        net.minecraft.inventory.InventoryCrafting g = new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container() {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p) {
                return false;
            }
        }, 3, 3);
        g.setInventorySlotContents(0, new ItemStack(planks));
        g.setInventorySlotContents(3, new ItemStack(planks));
        for (IRecipe r : recipes()) if (r.getRecipeOutput() != null && r.getRecipeOutput().getItem() == Items.stick && r.matches(g, w)) return true;
        return false;
    }

    private static void world(TwilightWorld w) {
        Item c = chunk(w);
        ItemStack s = stick(WorldWood.of(w)), am = mat(w, ARMOR_MAT), wm = mat(w, WEAPON_MAT), tm = mat(w, TOOL_MAT);
        // other worlds have the same world pieces in these places
        boolean sea = w == TwilightWorld.WILDWOOD;
        ItemStack cs = new ItemStack(c), fin = sea ? new ItemStack(VanillaItemsOther.whaleFin) : cs, teeth = sea ? new ItemStack(VanillaItemsOther.sharkFin) : cs, // Liopleurodon teeth since 0.99.113 - only from the late boss
            claw = sea ? new ItemStack(VanillaItemsOther.crabClaw) : cs, hook = sea ? any(VanillaItemsWeapons.sharkAnchor) : cs;
        // armor: in place of the previous set's piece - the time gear of the previous world (stays in the crafting table); Eden's place is empty
        Item[] armor = w.armor();
        ItemStack gear = gear(w);
        for (Item i : armor) remove(i);
        String[][] shapes = { { "CMC", "FPF" }, { "CPC", "MCM", "FCF" }, { "CPC", "M M", "F F" }, { "MPM", "F F" } };
        for (int k = 0; k < 4; k++) {
            List<Object> in = new java.util.ArrayList<Object>();
            for (String row : shapes[k]) in.add(gear == null ? row.replace('P', ' ') : row);
            in.addAll(java.util.Arrays.asList('C', c, 'F', fin, 'M', am));
            if (gear != null) in.addAll(java.util.Arrays.asList('P', gear));
            GameRegistry.addRecipe(new ItemStack(armor[k]), in.toArray());
        }

        Item[] wp = weapons(w), pw = prevWeapons(w);
        for (Item i : wp) remove(i);
        GameRegistry.addRecipe(new ItemStack(wp[0]), " H ", "MPM", " S ", 'H', hook, 'M', wm, 'P', any(pw[0]), 'S', s);
        GameRegistry.addRecipe(new ItemStack(wp[1]), "TSX", "MPX", "TSX", 'T', teeth, 'M', wm, 'P', any(pw[1]), 'S', s, 'X', Items.string);
        GameRegistry.addRecipe(new ItemStack(wp[2]), " T ", "CPC", " S ", 'T', teeth, 'C', c, 'P', pw[2] == null ? wm : any(pw[2]), 'S', s);
        GameRegistry.addRecipe(new ItemStack(wp[3]), "T T", "MPM", "CSC", 'T', teeth, 'C', c, 'M', wm, 'P', pw[3] == null ? wm : any(pw[3]), 'S', s);

        Item[] tl = tools(w), pt = prevTools(w);
        for (Item i : tl) remove(i);
        GameRegistry.addRecipe(new ItemStack(tl[0]), "KMK", " P ", " S ", 'K', claw, 'M', tm, 'P', any(pt[0]), 'S', s);
        GameRegistry.addRecipe(new ItemStack(tl[1]), "MKM", " P ", " S ", 'K', claw, 'M', tm, 'P', any(pt[1]), 'S', s);
        GameRegistry.addRecipe(new ItemStack(tl[2]), "KM ", "KP ", " S ", 'K', claw, 'M', tm, 'P', any(pt[2]), 'S', s);
    }

    /** The divine sword and tools of the overworld - on sticks of divine wood. */
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

    /** Wildwood..Mortum portals: a frame of gate stones instead of blocks of the previous world's stones. */
    private static void gates() {
        Block[] portals = { TwilightBlocks.wildwoodPortal, TwilightBlocks.apalachiaPortal, TwilightBlocks.skythernPortal, TwilightBlocks.mortumPortal };
        try {
            java.lang.reflect.Field f = BlockModPortal.class.getDeclaredField("blockFrame");
            f.setAccessible(true);
            for (CadillionWorld w : CadillionWorld.values()) f.set(portals[w.ordinal()], ModBlocks.gateStone[w.ordinal()]);
        } catch (Exception e) {
            DivineRPGExtended.LOG.error("Could not replace portal frames with gate stones", e);
            return;
        }
        for (CadillionWorld w : CadillionWorld.values()) {
            TwilightWorld tw = TwilightWorld.values()[w.ordinal()];
            // 10 pieces - exactly for a 4 x 5 portal frame
            GameRegistry.addRecipe(new ItemStack(ModBlocks.gateStone[w.ordinal()], 10), "BBB", "BHB", "BBB", 'B', tw.gemBlock(), 'H',
                new ItemStack(ModItems.guardianHeart, 1, w.ordinal()));
        }
    }

    /** The portal whose frame is this gate stone, or null. */
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
