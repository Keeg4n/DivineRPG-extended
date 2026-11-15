package com.divinerpgextended.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The lair map as an explorer map: a found lair is a regular Minecraft map centered on the lair with a red
 * marker on it. The terrain on it is not drawn at first and gets drawn as the player arrives there. The game does not
 * store the marker - we put it back on the maps in the inventory ({@link Marks}).
 */
public final class ExplorerMaps {

    /** Target tag on the map: {x, z}. */
    public static final String TARGET = "dreLairTarget";
    /** Key of the marker in the map's icon list; type 2 - a red arrow. */
    private static final String MARK = "dreLair";
    private static final byte RED = 2;
    /** Scale 2: 1 pixel - 4 blocks, the map is 512 x 512 blocks. */
    private static final byte SCALE = 2;

    /** A click with the map is in progress: spend the one in hand without freeing the slot (the game will wipe it after the click). */
    static boolean inClick;

    private ExplorerMaps() {}

    /** A map to the lair (x, z): an extra map of this lair kind is spent; if the player already has such a map - we do not give a new one. */
    public static void give(World w, EntityPlayer p, int type, String name, int x, int z) {
        for (ItemStack s : p.inventory.mainInventory) if (target(s) != null && target(s)[0] == x && target(s)[1] == z) return;
        int id = w.getUniqueDataId("map");
        MapData d = new MapData("map_" + id);
        d.xCenter = x;
        d.zCenter = z;
        d.scale = SCALE;
        d.dimension = (byte) w.provider.dimensionId;
        d.markDirty();
        w.setItemData("map_" + id, d);
        ItemStack map = new ItemStack(Items.filled_map, 1, id);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setIntArray(TARGET, new int[] { x, z });
        map.setTagCompound(tag);
        map.setStackDisplayName(net.minecraft.util.EnumChatFormatting.RESET + StatCollector.translateToLocal(name)); // without the italics of renamed ones
        mark(d, x, z);
        if (!p.capabilities.isCreativeMode) consume(p, type);
        if (!p.inventory.addItemStackToInventory(map)) p.dropPlayerItemWithRandomChoice(map, false);
        p.inventoryContainer.detectAndSendChanges();
    }

    private static void consume(EntityPlayer p, int type) {
        ItemStack held = p.getHeldItem();
        if (inClick && held != null && held.getItem() == com.divinerpgextended.registry.ModItems.lairMap && held.getItemDamage() == type) {
            held.stackSize--; // the game itself will remove the empty stack after the click; the new map will go to another slot
            return;
        }
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] == null || inv[i].getItem() != com.divinerpgextended.registry.ModItems.lairMap || inv[i].getItemDamage() != type) continue;
            if (--inv[i].stackSize <= 0) inv[i] = null;
            return;
        }
    }

    /** The map's target {x, z} or null. */
    public static int[] target(ItemStack s) {
        if (s == null || s.getItem() != Items.filled_map || !s.hasTagCompound() || !s.getTagCompound().hasKey(TARGET)) return null;
        int[] t = s.getTagCompound().getIntArray(TARGET);
        return t.length == 2 ? t : null;
    }

    /** The lair marker on the map (icon coordinates are in map pixels from the center, 2 per pixel). */
    @SuppressWarnings("unchecked")
    static void mark(MapData d, int x, int z) {
        int k = 1 << d.scale;
        int mx = Math.max(-128, Math.min(127, (int) ((x - d.xCenter) * 2.0F / k))), mz = Math.max(-128, Math.min(127, (int) ((z - d.zCenter) * 2.0F / k)));
        d.playersVisibleOnMap.put(MARK, d.new MapCoord(RED, (byte) mx, (byte) mz, (byte) 8));
    }

    /** Whether the map has a lair marker (for the autotest). */
    public static boolean marked(MapData d) {
        return d != null && d.playersVisibleOnMap.containsKey(MARK);
    }

    /** Puts the marker back on the lair maps in the inventory: the game rebuilds icons and does not save them. */
    public static class Marks {

        @SubscribeEvent
        public void onTick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote || e.player.ticksExisted % 10 != 0) return;
            for (ItemStack s : e.player.inventory.mainInventory) {
                int[] t = target(s);
                if (t == null) continue;
                MapData d = Items.filled_map.getMapData(s, e.player.worldObj);
                if (d != null && !d.playersVisibleOnMap.containsKey(MARK)) mark(d, t[0], t[1]);
            }
        }
    }
}
