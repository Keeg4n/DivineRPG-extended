package com.divinerpgextended.trinket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.entities.base.InfiniteTrade;
import net.divinerpg.entities.vanilla.EntityJackOMan;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.ProgressSync;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Джек о'Мэн:
 * <ul>
 * <li>скупает ночной хлам за теневые монеты (сделки в конце списка);</li>
 * <li>охота за головами: мобы роняют головы (2% + 1% за уровень Добычи), Джек на заказ делает из голов, материала и
 * монет бижутерию. Присесть + ПКМ с головой (или тыквой) в руке - цена, ещё раз за 5 секунд - заказ.</li>
 * </ul>
 */
public class JackOMan {

    private static final Map<UUID, long[]> PENDING = new HashMap<UUID, long[]>();

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    // ---------------- Головы ----------------

    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        if (e.entityLiving.worldObj.isRemote || !e.recentlyHit || !(e.source.getEntity() instanceof EntityPlayer)) return;
        int meta;
        if (e.entityLiving instanceof EntitySkeleton) meta = ((EntitySkeleton) e.entityLiving).getSkeletonType() == 1 ? 1 : 0;
        else if (e.entityLiving instanceof EntityZombie && !(e.entityLiving instanceof EntityPigZombie)) meta = 2;
        else if (e.entityLiving instanceof EntityCreeper) meta = 4;
        else return;
        int looting = EnchantmentHelper.getLootingModifier((EntityPlayer) e.source.getEntity());
        if (e.entityLiving.worldObj.rand.nextFloat() >= 0.02F + 0.01F * looting) return;
        e.drops.add(new EntityItem(e.entityLiving.worldObj, e.entityLiving.posX, e.entityLiving.posY, e.entityLiving.posZ,
            new ItemStack(Items.skull, 1, meta)));
    }

    // ---------------- Джек ----------------

    /**
     * Этап Джека по прогрессу: 0 - с начала (броня Skeleman); 1 - Карцинус (броня Jack o'Man, тыквенная удочка, скупка
     * хлама); 2 - Король-лич (броня Wither Reaper, первые три вещи из голов); 3 - Древняя сущность (кольцо иссушения,
     * тыквенный амулет).
     */
    static int tier(EntityPlayer p) {
        if (ProgressSync.has(p, Boss.ANCIENT_ENTITY)) return 3;
        if (ProgressSync.has(p, Boss.LICH_KING)) return 2;
        if (ProgressSync.has(p, Boss.CARCINUS)) return 1;
        return 0;
    }

    private static final Boss[] TIER_BOSS = { null, Boss.CARCINUS, Boss.LICH_KING, Boss.ANCIENT_ENTITY };

    static int trinketTier(Trinket t) {
        return t == Trinket.WITHER_RING || t == Trinket.JACK_AMULET ? 3 : 2;
    }

    /** Этап, на котором Джек начинает продавать эту вещь из своего списка Divine. */
    private static int tradeTier(ItemStack sell) {
        Item i = sell.getItem();
        if (i == VanillaItemsArmor.witherReaperHelmet || i == VanillaItemsArmor.witherReaperBody || i == VanillaItemsArmor.witherReaperLegs
            || i == VanillaItemsArmor.witherReaperBoots) return 2;
        if (i == VanillaItemsArmor.jackOManHelmet || i == VanillaItemsArmor.jackOManBody || i == VanillaItemsArmor.jackOManLegs
            || i == VanillaItemsArmor.jackOManBoots) return 1;
        return 0;
    }

    @SubscribeEvent
    public void onInteract(EntityInteractEvent e) {
        if (!(e.target instanceof EntityJackOMan)) return;
        EntityPlayer p = e.entityPlayer;
        // Джек торгует только ночью (проверка одинакова на клиенте и сервере - время мира общее)
        if (p.worldObj.isDaytime()) {
            e.setCanceled(true);
            if (!p.worldObj.isRemote) say(p, EnumChatFormatting.GRAY, "dre.jack.day");
            return;
        }
        if (p.isSneaking()) {
            e.setCanceled(true);
            if (!p.worldObj.isRemote) order(p);
            return;
        }
        // Окно торговли Divine строит список на клиенте: пересобираем его на обеих сторонах одинаково, по этапу игрока
        // (клиент знает этап из ProgressSync). Берём исходный список самого Джека и оставляем то, что уже открыто.
        EntityJackOMan jack = (EntityJackOMan) e.target;
        MerchantRecipeList list = jack.getRecipes(p);
        if (list == null) return;
        MerchantRecipeList orig = new MerchantRecipeList();
        jack.addRecipies(orig);
        int tier = tier(p);
        list.clear();
        for (Object o : orig) {
            MerchantRecipe r = (MerchantRecipe) o;
            if (tradeTier(r.getItemToSell()) <= tier) {
                list.add(new InfiniteTrade(r.getItemToBuy(), r.getSecondItemToBuy(), r.getItemToSell()));
            }
        }
        if (tier >= 1) {
            list.add(new InfiniteTrade(new ItemStack(Items.bone, 40), new ItemStack(Blocks.pumpkin, 16),
                new ItemStack(ModItems.rods[com.divinerpgextended.fishing.Rod.JACK.ordinal()])));
        }
    }

    // ---------------- Скупка хлама ----------------

    /** Сколько хлама Джек берёт за 1 теневую монету. Монеты дорогие (6 штук крафтятся из рупия и арлемита). */
    private static int junkRate(Item i) {
        if (i == Items.bone || i == Items.rotten_flesh) return 32;
        if (i == Items.string) return 24;
        if (i == Items.gunpowder) return 16;
        if (i == Items.spider_eye) return 12;
        return 0;
    }

    /** Больше этого за одну ночь Джек одному игроку не платит. */
    private static final int NIGHT_CAP = 6;

    private static void sellJunk(EntityPlayer p, ItemStack held) {
        int rate = junkRate(held.getItem());
        long night = p.worldObj.getWorldTime() / 24000L;
        net.minecraft.nbt.NBTTagCompound d = p.getEntityData();
        int sold = d.getLong("dreJackNight") == night ? d.getInteger("dreJackSold") : 0;
        int left = NIGHT_CAP - sold;
        if (left <= 0) {
            say(p, EnumChatFormatting.GRAY, "dre.jack.junk_cap");
            return;
        }
        int coins = Math.min(left, held.stackSize / rate);
        if (coins <= 0) {
            say(p, EnumChatFormatting.YELLOW, "dre.jack.junk_rate", String.valueOf(rate), held.getDisplayName());
            return;
        }
        held.stackSize -= coins * rate;
        if (held.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
        d.setLong("dreJackNight", night);
        d.setInteger("dreJackSold", sold + coins);
        ItemStack pay = new ItemStack(VanillaItemsOther.shadowCoins, coins);
        if (!p.inventory.addItemStackToInventory(pay)) p.worldObj.spawnEntityInWorld(new EntityItem(p.worldObj, p.posX, p.posY + 0.5, p.posZ, pay));
        p.inventoryContainer.detectAndSendChanges();
        com.divinerpgextended.DreAchievements.give(p, com.divinerpgextended.DreAchievements.jackTrade);
        say(p, EnumChatFormatting.GREEN, "dre.jack.junk_done", String.valueOf(coins), String.valueOf(left - coins));
    }

    private static int count(EntityPlayer p, Item item, int meta) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            if (s != null && s.getItem() == item && (meta < 0 || s.getItemDamage() == meta)) n += s.stackSize;
        }
        return n;
    }

    private static void take(EntityPlayer p, Item item, int meta, int n) {
        for (int i = 0; i < p.inventory.mainInventory.length && n > 0; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s == null || s.getItem() != item || meta >= 0 && s.getItemDamage() != meta) continue;
            int t = Math.min(n, s.stackSize);
            s.stackSize -= t;
            n -= t;
            if (s.stackSize <= 0) p.inventory.mainInventory[i] = null;
        }
    }

    private static IChatComponent price(Trinket t) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.heads.length; i++) {
            if (t.heads[i] == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.heads[i]).append(" x ").append(new ItemStack(Items.skull, 1, Trinket.HEAD_META[i]).getDisplayName());
        }
        sb.append(", ").append(t.materialCount).append(" x ").append(new ItemStack(t.material()).getDisplayName());
        sb.append(", ").append(t.coins).append(" x ").append(new ItemStack(VanillaItemsOther.shadowCoins).getDisplayName());
        return new ChatComponentTranslation("%s", sb.toString());
    }

    private static boolean has(EntityPlayer p, Trinket t) {
        for (int i = 0; i < t.heads.length; i++) if (count(p, Items.skull, Trinket.HEAD_META[i]) < t.heads[i]) return false;
        return count(p, t.material(), -1) >= t.materialCount && count(p, VanillaItemsOther.shadowCoins, -1) >= t.coins;
    }

    /** Заказ: с головой (или тыквой) в руке - цена, повтор за 5 секунд - сделать. Без них - список заказов. */
    private static void order(EntityPlayer p) {
        int tier = tier(p);
        ItemStack held = p.getCurrentEquippedItem();
        if (held != null && junkRate(held.getItem()) > 0) {
            if (tier < 1) say(p, EnumChatFormatting.GRAY, "dre.jack.locked", held.getDisplayName(),
                new ChatComponentTranslation(TIER_BOSS[1].langKey()));
            else sellJunk(p, held);
            return;
        }
        Trinket t = Trinket.forHeld(held);
        if (t == null) {
            say(p, EnumChatFormatting.GOLD, "dre.jack.menu");
            if (tier >= 1) say(p, EnumChatFormatting.YELLOW, "dre.jack.junk_menu", String.valueOf(NIGHT_CAP));
            for (Trinket x : Trinket.values()) {
                ChatComponentTranslation name = new ChatComponentTranslation("item.dre.trinket." + x.key() + ".name");
                if (trinketTier(x) <= tier) say(p, EnumChatFormatting.YELLOW, "dre.jack.menu_line", name, price(x));
                else say(p, EnumChatFormatting.DARK_GRAY, "dre.jack.locked", name,
                    new ChatComponentTranslation(TIER_BOSS[trinketTier(x)].langKey()));
            }
            return;
        }
        if (trinketTier(t) > tier) {
            say(p, EnumChatFormatting.GRAY, "dre.jack.locked", new ChatComponentTranslation("item.dre.trinket." + t.key() + ".name"),
                new ChatComponentTranslation(TIER_BOSS[trinketTier(t)].langKey()));
            return;
        }
        long now = p.worldObj.getTotalWorldTime();
        long[] pend = PENDING.get(p.getUniqueID());
        if (pend == null || now - pend[0] > 100 || pend[1] != t.ordinal()) {
            PENDING.put(p.getUniqueID(), new long[] { now, t.ordinal() });
            say(p, EnumChatFormatting.YELLOW, "dre.jack.offer", new ChatComponentTranslation("item.dre.trinket." + t.key() + ".name"),
                price(t));
            return;
        }
        PENDING.remove(p.getUniqueID());
        if (!p.capabilities.isCreativeMode) {
            if (!has(p, t)) {
                say(p, EnumChatFormatting.RED, "dre.jack.not_enough", price(t));
                return;
            }
            for (int i = 0; i < t.heads.length; i++) take(p, Items.skull, Trinket.HEAD_META[i], t.heads[i]);
            take(p, t.material(), -1, t.materialCount);
            take(p, VanillaItemsOther.shadowCoins, -1, t.coins);
        }
        ItemStack made = new ItemStack(ModItems.trinket, 1, t.ordinal());
        if (!p.inventory.addItemStackToInventory(made)) p.worldObj.spawnEntityInWorld(new EntityItem(p.worldObj, p.posX, p.posY + 0.5, p.posZ, made));
        p.inventoryContainer.detectAndSendChanges();
        p.worldObj.playSoundAtEntity(p, "mob.wither.idle", 0.4F, 1.6F);
        com.divinerpgextended.DreAchievements.give(p, com.divinerpgextended.DreAchievements.headHunter);
        say(p, EnumChatFormatting.GOLD, "dre.jack.done", new ChatComponentTranslation("item.dre.trinket." + t.key() + ".name"));
    }
}
