package com.divinerpgextended.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.divinerpgextended.perk.Perk;
import com.divinerpgextended.world.ArmorTier;

import net.minecraft.item.Item;

/** Ступень, перки и ядро брони Странника хранятся в NBT каждой части. */
public final class ArmorData {

    private static final String ROOT = "dre";

    private ArmorData() {}

    private static NBTTagCompound root(ItemStack stack, boolean create) {
        if (stack.getTagCompound() == null) {
            if (!create) return null;
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(ROOT)) {
            if (!create) return null;
            tag.setTag(ROOT, new NBTTagCompound());
        }
        return tag.getCompoundTag(ROOT);
    }

    public static int tier(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        return r == null ? 1 : Math.max(1, r.getInteger("tier"));
    }

    public static void setTier(ItemStack stack, int tier) {
        root(stack, true).setInteger("tier", tier);
    }

    /** Ядро части: старая броня без записи - божественная. */
    public static ArmorCore core(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        return r == null ? ArmorCore.DIVINE : ArmorCore.byName(r.getString("core"));
    }

    public static void setCore(ItemStack stack, ArmorCore core) {
        root(stack, true).setString("core", core.name());
    }

    public static List<Perk> perks(ItemStack stack) {
        List<Perk> out = new ArrayList<Perk>();
        NBTTagCompound r = root(stack, false);
        if (r == null) return out;
        NBTTagList list = r.getTagList("perks", 8);
        for (int i = 0; i < list.tagCount(); i++) {
            Perk p = Perk.byName(list.getStringTagAt(i));
            if (p != null) out.add(p);
        }
        return out;
    }

    public static void setPerks(ItemStack stack, List<Perk> perks) {
        NBTTagList list = new NBTTagList();
        for (Perk p : perks) list.appendTag(new NBTTagString(p.name()));
        root(stack, true).setTag("perks", list);
    }

    /** Владелец части (UUID строкой) или null - её ещё никто не надевал. */
    public static String owner(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        return r == null || !r.hasKey("owner") ? null : r.getString("owner");
    }

    public static String ownerName(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        return r == null ? null : r.getString("ownerName");
    }

    /** Привязать часть к игроку: защита, перки, ядро и гнёзда дальше работают только на нём. */
    public static void bind(ItemStack stack, EntityPlayer p) {
        NBTTagCompound r = root(stack, true);
        r.setString("owner", p.getUniqueID().toString());
        r.setString("ownerName", p.getCommandSenderName());
    }

    public static void unbind(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        if (r == null) return;
        r.removeTag("owner");
        r.removeTag("ownerName");
    }

    /**
     * Работает ли часть на игроке: своя или ничья (ничью привяжет первый же тик на нём). На сервере сверяется UUID; на клиенте
     * ещё и ник: у клиента без входа в аккаунт UUID может не совпасть с серверным, а подсказке и полоске брони нужен ответ.
     */
    public static boolean owns(ItemStack stack, EntityPlayer p) {
        String o = owner(stack);
        if (o == null) return true;
        if (p == null) return false;
        if (o.equals(p.getUniqueID().toString())) return true;
        return p.worldObj != null && p.worldObj.isRemote && p.getCommandSenderName().equals(ownerName(stack));
    }

    /** Цвет покраски 0xRRGGBB или -1 - не крашена. Только облик. */
    public static int color(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        return r == null || !r.hasKey("color") ? -1 : r.getInteger("color") & 0xFFFFFF;
    }

    public static void setColor(ItemStack stack, int color) {
        root(stack, true).setInteger("color", color & 0xFFFFFF);
    }

    public static void clearColor(ItemStack stack) {
        NBTTagCompound r = root(stack, false);
        if (r != null) r.removeTag("color");
    }

    /**
     * Полный сет Странника на игроке.
     *
     * @return минимальная ступень сета или 0, если сет не полный или чужой ({@link #owns})
     */
    public static int equippedTier(EntityPlayer player) {
        int tier = Integer.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            ItemStack s = player.getCurrentArmor(i);
            if (s == null || !(s.getItem() instanceof ItemWandererArmor) || !owns(s, player)) return 0;
            tier = Math.min(tier, tier(s));
        }
        return tier;
    }

    /**
     * Ступень брони игрока для боссов: сет Странника - своя ступень; полные сеты Divine считаются эквивалентом
     * (реалмит и элитный реалмит R0, финальные сеты обычного мира R1, Eden R2 ... Halite R7); всё остальное - R0.
     */
    public static int effectiveTier(EntityPlayer player) {
        int wanderer = equippedTier(player);
        if (wanderer > 0) return wanderer;
        // R1 - финальные сеты ({@link ArmorCore}), из них куётся Странник; элитный реалмит - лишь улучшенный R0
        if (ArmorCore.wornSet(player) != null) return 1;
        int best = 0;
        for (ArmorTier t : ArmorTier.values()) if (wearsSet(player, t.donorSet())) best = Math.max(best, t.tier);
        return best;
    }

    /** set = [шлем, нагрудник, поножи, ботинки]; слоты игрока: 0 ботинки, 1 поножи, 2 нагрудник, 3 шлем. */
    private static boolean wearsSet(EntityPlayer player, Item[] set) {
        for (int i = 0; i < 4; i++) {
            ItemStack s = player.getCurrentArmor(i);
            if (s == null || s.getItem() != set[3 - i]) return false;
        }
        return true;
    }

    /** Перк действует, только если он есть на всех четырёх частях надетого сета. */
    public static boolean hasEquippedPerk(EntityPlayer player, Perk perk) {
        if (equippedTier(player) == 0) return false;
        for (int i = 0; i < 4; i++) {
            if (!perks(player.getCurrentArmor(i)).contains(perk)) return false;
        }
        return true;
    }
}
