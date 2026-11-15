package com.divinerpgextended.arcana;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import net.divinerpg.blocks.base.tileentity.TileEntityInfiniteFurnace;
import net.divinerpg.blocks.base.tileentity.TileEntityModFurnace;
import net.divinerpg.entities.arcana.EntityDatticon;
import net.divinerpg.entities.base.InfiniteTrade;
import net.divinerpg.utils.blocks.VanillaBlocks;
import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.oredict.OreDictionary;

import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Roles of Divine furnaces (Datticon). Each furnace has its own chance to double ore (the faster the furnace, the higher), some have their own
 * signature material that is always doubled. Datticon modules are installed into a furnace (sneak + right click the furnace with a module):
 * doubling +15% per module, speed - an extra cooking step per module; 2 of each. If the furnace is broken, the modules drop.
 */
public class FurnaceRoles {

    public static final int MAX_MODULES = 2;
    public static final float MODULE_CHANCE = 0.15F;

    /** Furnace role by its class. */
    public enum Role {
        COALSTONE("TileEntityCoalstoneFurnace", 0.10F),
        MOLTEN("TileEntityMoltenFurnace", 0.15F),
        OCEANFIRE("TileEntityOceanfireFurnace", 0.20F),
        WHITEFIRE("TileEntityWhitefireFurnace", 0.25F),
        DEMON("TileEntityDemonFurnace", 0.30F),
        GREENLIGHT("TileEntityGreenlightFurnace", 0.35F),
        MOONLIGHT("TileEntityMoonlightFurnace", 0.45F);

        final String cls;
        public final float chance;

        Role(String cls, float chance) {
            this.cls = cls;
            this.chance = chance;
        }

        public String key() {
            return name().toLowerCase();
        }

        /** Signature: this input is always doubled. */
        boolean signature(ItemStack in, ItemStack out) {
            Item i = in.getItem();
            switch (this) {
                case COALSTONE:
                    return i == Item.getItemFromBlock(ModBlocks.rimeOre);
                case MOLTEN:
                    return i == Item.getItemFromBlock(VanillaBlocks.bloodgemOre) || i == Item.getItemFromBlock(VanillaBlocks.netheriteOre)
                        || i == Item.getItemFromBlock(Blocks.quartz_ore);
                case OCEANFIRE:
                    return out.getItem() instanceof ItemFood;
                case WHITEFIRE:
                    return i == Item.getItemFromBlock(VanillaBlocks.realmiteOre) || i == Item.getItemFromBlock(VanillaBlocks.arlemiteOre)
                        || i == Item.getItemFromBlock(VanillaBlocks.rupeeOre);
                case DEMON:
                    return i == Item.getItemFromBlock(Blocks.gold_ore) || i == Item.getItemFromBlock(Blocks.diamond_ore)
                        || i == Item.getItemFromBlock(Blocks.emerald_ore);
                default:
                    return false;
            }
        }

        static Role of(TileEntity te) {
            String n = te.getClass().getSimpleName();
            for (Role r : values()) if (r.cls.equals(n)) return r;
            return null;
        }
    }

    // ---------------- Modules in furnaces (stored in the world) ----------------

    public static class Modules extends WorldSavedData {

        static final String NAME = "dre_furnace_modules";
        final NBTTagCompound data = new NBTTagCompound();

        public Modules(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            for (Object k : tag.func_150296_c()) data.setIntArray((String) k, tag.getIntArray((String) k));
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            for (Object k : data.func_150296_c()) tag.setIntArray((String) k, data.getIntArray((String) k));
        }

        static Modules get(World w) {
            Modules m = (Modules) w.perWorldStorage.loadData(Modules.class, NAME);
            if (m == null) {
                m = new Modules(NAME);
                w.perWorldStorage.setData(NAME, m);
            }
            return m;
        }

        static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }

        /** [doubling, speed] */
        int[] at(int x, int y, int z) {
            int[] a = data.getIntArray(key(x, y, z));
            return a.length == 2 ? a : new int[2];
        }

        void set(int x, int y, int z, int[] a) {
            if (a[0] == 0 && a[1] == 0) data.removeTag(key(x, y, z));
            else data.setIntArray(key(x, y, z), a);
            markDirty();
        }
    }

    private static boolean isFurnace(TileEntity te) {
        return te instanceof TileEntityInfiniteFurnace || te instanceof TileEntityModFurnace;
    }

    private static int outSlot(TileEntity te) {
        return te instanceof TileEntityModFurnace ? 2 : 1;
    }

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        com.divinerpgextended.audit.Audit.say(p, key, args);
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    /** Sneak + right click a Divine furnace with a module - install the module. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (e.isCanceled() || e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || !e.entityPlayer.isSneaking()) return;
        ItemStack held = e.entityPlayer.getCurrentEquippedItem();
        if (held == null || held.getItem() != ModItems.furnaceModule) return;
        TileEntity te = e.world.getTileEntity(e.x, e.y, e.z);
        if (te == null || !isFurnace(te)) return;
        if (!e.entityPlayer.canPlayerEdit(e.x, e.y, e.z, e.face, held)) return; // another player's furnace in a protected region
        e.setCanceled(true);
        if (e.world.isRemote) return;
        Role role = Role.of(te);
        Modules m = Modules.get(e.world);
        int[] a = m.at(e.x, e.y, e.z);
        int type = held.getItemDamage() == 1 ? 1 : 0;
        if (a[type] >= MAX_MODULES) {
            say(e.entityPlayer, EnumChatFormatting.GRAY, "dre.furnace.full");
            return;
        }
        a[type]++;
        m.set(e.x, e.y, e.z, a);
        if (!e.entityPlayer.capabilities.isCreativeMode) held.stackSize--;
        e.world.playSoundEffect(e.x + 0.5, e.y + 0.5, e.z + 0.5, "random.anvil_use", 0.5F, 1.6F);
        int chance = Math.round(((role == null ? 0 : role.chance) + a[0] * MODULE_CHANCE) * 100);
        com.divinerpgextended.DreAchievements.give(e.entityPlayer, com.divinerpgextended.DreAchievements.furnaceModule);
        say(e.entityPlayer, EnumChatFormatting.GREEN, "dre.furnace.installed", String.valueOf(a[0]), String.valueOf(a[1]),
            String.valueOf(chance), String.valueOf(1 + a[1]));
    }

    /**
     * Breaking a furnace - the modules drop. Last of all and only if the break is not cancelled (protected region etc.): otherwise the furnace
     * would keep the modules and the modules would drop as well - a dupe.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent e) {
        if (e.isCanceled()) return;
        if (e.world.isRemote) return;
        TileEntity te = e.world.getTileEntity(e.x, e.y, e.z);
        if (te == null || !isFurnace(te)) return;
        Modules m = Modules.get(e.world);
        int[] a = m.at(e.x, e.y, e.z);
        for (int t = 0; t < 2; t++) {
            if (a[t] > 0) e.world.spawnEntityInWorld(new EntityItem(e.world, e.x + 0.5, e.y + 0.5, e.z + 0.5,
                new ItemStack(ModItems.furnaceModule, a[t], t)));
        }
        m.set(e.x, e.y, e.z, new int[2]);
    }

    // ---------------- Doubling and speed ----------------

    /** Snapshot of the furnace from the previous tick: what the input was and how much. */
    private static final Map<TileEntity, ItemStack> LAST_IN = new WeakHashMap<TileEntity, ItemStack>();

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        Modules mods = null;
        for (Iterator<?> it = e.world.loadedTileEntityList.iterator(); it.hasNext();) {
            TileEntity te = (TileEntity) it.next();
            if (!isFurnace(te) || te.isInvalid()) continue;
            if (mods == null) mods = Modules.get(e.world);
            int[] a = mods.at(te.xCoord, te.yCoord, te.zCoord);
            if (a[1] > 0) speedUp(te, a[1]);
            IInventory inv = (IInventory) te;
            ItemStack in = inv.getStackInSlot(0);
            ItemStack last = LAST_IN.get(te);
            if (last != null) {
                int smelted = in == null ? last.stackSize : in.isItemEqual(last) ? last.stackSize - in.stackSize : 0;
                for (int i = 0; i < smelted; i++) bonus(te, inv, last, a[0]);
            }
            LAST_IN.put(te, in == null ? null : in.copy());
        }
        if (e.world.getTotalWorldTime() % 200 == 0) cleanup(e.world);
    }

    /**
     * The furnace vanished without being broken (explosion, command, another mod) - its modules vanish with it instead of going to
     * a new furnace in the same place.
     */
    private static void cleanup(World w) {
        Modules m = Modules.get(w);
        for (Object k : new java.util.ArrayList<Object>(m.data.func_150296_c())) {
            String[] xyz = ((String) k).split(",");
            int x = Integer.parseInt(xyz[0]), y = Integer.parseInt(xyz[1]), z = Integer.parseInt(xyz[2]);
            if (!w.blockExists(x, y, z)) continue;
            TileEntity te = w.getTileEntity(x, y, z);
            if (te == null || !isFurnace(te)) m.set(x, y, z, new int[2]);
        }
    }

    private static void speedUp(TileEntity te, int n) {
        if (te instanceof TileEntityInfiniteFurnace) {
            TileEntityInfiniteFurnace f = (TileEntityInfiniteFurnace) te;
            if (f.furnaceCookTime > 0) f.furnaceCookTime = Math.min(f.speed - 1, f.furnaceCookTime + n);
        } else {
            TileEntityModFurnace f = (TileEntityModFurnace) te;
            if (f.furnaceCookTime > 0) f.furnaceCookTime = Math.min(f.speed - 1, f.furnaceCookTime + n);
        }
    }

    static boolean ore(ItemStack in) {
        for (int id : OreDictionary.getOreIDs(in)) if (OreDictionary.getOreName(id).startsWith("ore")) return true;
        Block b = Block.getBlockFromItem(in.getItem());
        return b != null && b != Blocks.air && b.getClass().getSimpleName().toLowerCase().contains("ore");
    }

    /** One smelt happened: maybe put one more result. */
    private static void bonus(TileEntity te, IInventory inv, ItemStack input, int modules) {
        ItemStack result = FurnaceRecipes.smelting().getSmeltingResult(input);
        Role role = Role.of(te);
        if (result == null || role == null) return;
        boolean sig = role.signature(input, result);
        if (!sig) {
            if (!ore(input)) return;
            if (te.getWorldObj().rand.nextFloat() >= role.chance + modules * MODULE_CHANCE) return;
        }
        int slot = outSlot(te);
        ItemStack out = inv.getStackInSlot(slot);
        if (out == null) {
            inv.setInventorySlotContents(slot, result.copy());
        } else if (out.isItemEqual(result) && out.stackSize + result.stackSize <= out.getMaxStackSize()) {
            out.stackSize += result.stackSize;
        } else {
            return;
        }
        te.markDirty();
    }

    // ---------------- Datticon ----------------

    /** Sneak + right click on Datticon - furnace help; without sneaking - trading with the modules at the end of the list. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(EntityInteractEvent e) {
        if (!(e.target instanceof EntityDatticon)) return;
        EntityPlayer p = e.entityPlayer;
        if (p.isSneaking()) {
            e.setCanceled(true);
            if (p.worldObj.isRemote) return;
            say(p, EnumChatFormatting.GOLD, "dre.furnace.datticon");
            for (Role r : Role.values()) {
                say(p, EnumChatFormatting.YELLOW, "dre.furnace.role", new ChatComponentTranslation("dre.furnace." + r.key()),
                    String.valueOf(Math.round(r.chance * 100)), new ChatComponentTranslation("dre.furnace.sig." + r.key()));
            }
            say(p, EnumChatFormatting.GRAY, "dre.furnace.modules_hint");
            return;
        }
        // the Divine trading window builds the list on the client - we append the modules identically on both sides
        MerchantRecipeList list = ((EntityDatticon) e.target).getRecipes(p);
        if (list == null) return;
        for (Iterator<?> it = list.iterator(); it.hasNext();) {
            ItemStack sell = ((MerchantRecipe) it.next()).getItemToSell();
            if (sell != null && sell.getItem() == ModItems.furnaceModule) it.remove();
        }
        list.add(new InfiniteTrade(new ItemStack(ArcanaItems.arcanium, 12), new ItemStack(ArcanaItems.dungeonTokens, 8),
            new ItemStack(ModItems.furnaceModule, 1, 0)));
        list.add(new InfiniteTrade(new ItemStack(ArcanaItems.arcanium, 10), new ItemStack(ArcanaItems.chargedCollector, 1),
            new ItemStack(ModItems.furnaceModule, 1, 1)));
        for (Iterator<?> it = list.iterator(); it.hasNext();) {
            ItemStack sell = ((MerchantRecipe) it.next()).getItemToSell();
            if (sell != null && sell.getItem() == ModItems.trinket) it.remove();
        }
        list.add(new InfiniteTrade(new ItemStack(ArcanaItems.arcanium, 16), new ItemStack(ArcanaItems.chargedCollector, 1),
            new ItemStack(ModItems.trinket, 1, com.divinerpgextended.trinket.Trinket.POCKET_MAGNET.ordinal())));
    }
}
