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
 * Карта логова как карта исследователя: найденное логово - обычная карта Minecraft с центром на логове и красной
 * меткой на нём. Местность на ней сначала не нарисована и прорисовывается, когда игрок туда приходит. Метку игра не
 * хранит - возвращаем её на карты в инвентаре ({@link Marks}).
 */
public final class ExplorerMaps {

    /** Тег цели на карте: {x, z}. */
    public static final String TARGET = "dreLairTarget";
    /** Ключ метки в списке значков карты; тип 2 - красная стрелка. */
    private static final String MARK = "dreLair";
    private static final byte RED = 2;
    /** Масштаб 2: 1 пиксель - 4 блока, карта 512 x 512 блоков. */
    private static final byte SCALE = 2;

    /** Идёт клик картой: тратить ту, что в руке, не освобождая слот (игра затрёт его после клика). */
    static boolean inClick;

    private ExplorerMaps() {}

    /** Карта к логову (x, z): лишняя карта логова этого вида тратится; такая карта у игрока уже есть - новой не даём. */
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
        map.setStackDisplayName(net.minecraft.util.EnumChatFormatting.RESET + StatCollector.translateToLocal(name)); // без курсива переименованных
        mark(d, x, z);
        if (!p.capabilities.isCreativeMode) consume(p, type);
        if (!p.inventory.addItemStackToInventory(map)) p.dropPlayerItemWithRandomChoice(map, false);
        p.inventoryContainer.detectAndSendChanges();
    }

    private static void consume(EntityPlayer p, int type) {
        ItemStack held = p.getHeldItem();
        if (inClick && held != null && held.getItem() == com.divinerpgextended.registry.ModItems.lairMap && held.getItemDamage() == type) {
            held.stackSize--; // пустой стек уберёт сама игра после клика; новая карта ляжет в другой слот
            return;
        }
        ItemStack[] inv = p.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] == null || inv[i].getItem() != com.divinerpgextended.registry.ModItems.lairMap || inv[i].getItemDamage() != type) continue;
            if (--inv[i].stackSize <= 0) inv[i] = null;
            return;
        }
    }

    /** Цель карты {x, z} или null. */
    public static int[] target(ItemStack s) {
        if (s == null || s.getItem() != Items.filled_map || !s.hasTagCompound() || !s.getTagCompound().hasKey(TARGET)) return null;
        int[] t = s.getTagCompound().getIntArray(TARGET);
        return t.length == 2 ? t : null;
    }

    /** Метка логова на карте (координаты значка - в пикселях карты от центра, по 2 на пиксель). */
    @SuppressWarnings("unchecked")
    static void mark(MapData d, int x, int z) {
        int k = 1 << d.scale;
        int mx = Math.max(-128, Math.min(127, (int) ((x - d.xCenter) * 2.0F / k))), mz = Math.max(-128, Math.min(127, (int) ((z - d.zCenter) * 2.0F / k)));
        d.playersVisibleOnMap.put(MARK, d.new MapCoord(RED, (byte) mx, (byte) mz, (byte) 8));
    }

    /** Есть ли на карте метка логова (для автотеста). */
    public static boolean marked(MapData d) {
        return d != null && d.playersVisibleOnMap.containsKey(MARK);
    }

    /** Возвращает метку на карты логов в инвентаре: значки игра пересобирает и не сохраняет. */
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
