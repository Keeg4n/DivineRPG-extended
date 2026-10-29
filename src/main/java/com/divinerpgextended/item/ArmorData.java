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

import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.item.Item;

/** Ступень и перки брони Странника хранятся в NBT каждой части. */
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

    /**
     * Полный сет Странника на игроке.
     *
     * @return минимальная ступень сета или 0, если сет не полный
     */
    public static int equippedTier(EntityPlayer player) {
        int tier = Integer.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            ItemStack s = player.getCurrentArmor(i);
            if (s == null || !(s.getItem() instanceof ItemWandererArmor)) return 0;
            tier = Math.min(tier, tier(s));
        }
        return tier;
    }

    /**
     * Ступень брони игрока для боссов: сет Странника - своя ступень; полные сеты Divine считаются эквивалентом
     * (реалмит и элитный реалмит R0, божественная R1, Eden R2 ... Halite R7); всё остальное - R0.
     */
    public static int effectiveTier(EntityPlayer player) {
        int wanderer = equippedTier(player);
        if (wanderer > 0) return wanderer;
        // R1 - божественная броня (её фармят, как и портал в Эдем); элитный реалмит - лишь улучшенный R0 и начало брони Странника
        if (wearsSet(player, new Item[] { VanillaItemsArmor.divineHelmet, VanillaItemsArmor.divineBody,
            VanillaItemsArmor.divineLegs, VanillaItemsArmor.divineBoots })) return 1;
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
