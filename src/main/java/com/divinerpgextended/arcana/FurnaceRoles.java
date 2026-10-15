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
 * Роли печей Divine (Даттикон). У каждой печи свой шанс удвоить руду (чем быстрее печь, тем выше), у некоторых свой
 * фирменный материал, который удваивается всегда. Модули Даттикона ставятся в печь (присесть + ПКМ модулем по печи):
 * удвоение +15% за модуль, ускорение - лишний шаг готовки за модуль; по 2 каждого. Сломал печь - модули выпадают.
 */
public class FurnaceRoles {

    public static final int MAX_MODULES = 2;
    public static final float MODULE_CHANCE = 0.15F;

    /** Роль печи по её классу. */
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

        /** Фирменное: этот вход удваивается всегда. */
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

    // ---------------- Модули в печах (хранятся в мире) ----------------

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

        /** [удвоение, ускорение] */
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
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    /** Присесть + ПКМ модулем по печи Divine - поставить модуль. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (e.isCanceled() || e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || !e.entityPlayer.isSneaking()) return;
        ItemStack held = e.entityPlayer.getCurrentEquippedItem();
        if (held == null || held.getItem() != ModItems.furnaceModule) return;
        TileEntity te = e.world.getTileEntity(e.x, e.y, e.z);
        if (te == null || !isFurnace(te)) return;
        if (!e.entityPlayer.canPlayerEdit(e.x, e.y, e.z, e.face, held)) return; // чужая печь в привате
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
     * Сломал печь - модули выпадают. Самым последним и только если ломание не отменено (приват и т.п.): иначе печь
     * осталась бы с модулями, а модули ещё и выпали бы - дюп.
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

    // ---------------- Удвоение и ускорение ----------------

    /** Снимок печи с прошлого тика: что было на входе и сколько. */
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
     * Печь исчезла не ломанием (взрыв, команда, другой мод) - её модули пропадают вместе с ней, а не достаются новой
     * печи на том же месте.
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

    /** Одна переплавка случилась: может, положить ещё один результат. */
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

    // ---------------- Даттикон ----------------

    /** Присесть + ПКМ по Даттикону - справка о печах; без приседания - торговля с модулями в конце списка. */
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
        // окно торговли Divine строит список на клиенте - модули дописываем на обеих сторонах одинаково
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
