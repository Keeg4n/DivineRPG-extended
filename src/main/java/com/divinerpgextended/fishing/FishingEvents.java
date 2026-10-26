package com.divinerpgextended.fishing;

import java.util.Iterator;
import java.util.List;

import net.divinerpg.entities.arcana.EntityLeorna;
import net.divinerpg.entities.base.EntityDivineRPGVillager;
import net.divinerpg.entities.base.InfiniteTrade;
import net.divinerpg.entities.vanilla.EntityJackOMan;
import net.divinerpg.entities.vethea.EntityTheHunger;
import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Рыбалка: замена ванильного поплавка, удочки у торговцев, подсказки приманок, задания Леорны. */
public class FishingEvents {

    private static final String QUESTS = "dre_fish_quests", QUEST_DAY = "dre_fish_quest_day";

    /** Ванильная удочка тоже рыбачит по нашим правилам (сила 5, приманки, зоны). */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || e.entity.getClass() != EntityFishHook.class) return;
        EntityPlayer angler = ((EntityFishHook) e.entity).field_146042_b;
        e.setCanceled(true);
        if (angler != null) e.world.spawnEntityInWorld(new EntityDreHook(e.world, angler, Rod.VANILLA));
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        Bait b = Bait.of(e.itemStack.getItem());
        if (b == null) return;
        e.toolTip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("dre.tooltip.bait_power", b.power));
        String special = "dre.tooltip.bait." + b.key();
        if (StatCollector.canTranslate(special)) e.toolTip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal(special));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(EntityInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (!(e.target instanceof EntityDivineRPGVillager)) return;
        EntityDivineRPGVillager v = (EntityDivineRPGVillager) e.target;
        // Окно торговли Divine строит список на клиенте сам (с сервера его не получает), поэтому удочки
        // дописываем на обеих сторонах одинаково: в конец списка, номера сделок совпадают.
        if (p.worldObj.isRemote) {
            if (v instanceof EntityLeorna && (p.isSneaking() || holdsFish(p))) return; // это задание, не торговля
        } else if (v instanceof EntityLeorna && quest(p)) {
            e.setCanceled(true);
            return;
        }
        if (v instanceof EntityLeorna) {
            trade(v, p, new InfiniteTrade(new ItemStack(ArcanaItems.arcanium, 12), rod(Rod.RUPEE), rod(Rod.ARCANA)));
        } else if (v instanceof EntityJackOMan) {
            trade(v, p, new InfiniteTrade(new ItemStack(Items.bone, 40), new ItemStack(Blocks.pumpkin, 16), rod(Rod.JACK)));
        } else if (v instanceof EntityTheHunger) {
            hunger(v, p);
            trade(v, p, new InfiniteTrade(new ItemStack(VetheaItems.polishedPearls, 16), rod(Rod.MORTUM), rod(Rod.HUNGER)));
        }
    }

    public static final int HEIVA_BELT_PRICE = 30, WINGS_TEMPLATE_PRICE = 8, MIDNIGHT_TEMPLATE_PRICE = 8;

    /**
     * Голод: Пояс хейвы - за 30 красного (полированного) жемчуга вместо 25 фиолетового (сделка на своём месте, номера не
     * сдвигаются), шаблоны Крыльев сна (8 серого жемчуга) и Крыльев полуночи (8 голубого) - в конец, как удочки.
     */
    private static void hunger(EntityDivineRPGVillager v, EntityPlayer p) {
        MerchantRecipeList list = v.getRecipes(p);
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            ItemStack sell = ((MerchantRecipe) list.get(i)).getItemToSell();
            if (sell != null && sell.getItem() == VetheaItems.heivaBelt)
                list.set(i, new InfiniteTrade(new ItemStack(VetheaItems.polishedPearls, HEIVA_BELT_PRICE), null, new ItemStack(VetheaItems.heivaBelt)));
        }
        for (Iterator<?> it = list.iterator(); it.hasNext();) {
            ItemStack sell = ((MerchantRecipe) it.next()).getItemToSell();
            if (sell != null && sell.getItem() == ModItems.dreamItem) it.remove();
        }
        list.add(new InfiniteTrade(new ItemStack(VetheaItems.dirtyPearls, WINGS_TEMPLATE_PRICE), null,
            com.divinerpgextended.vethea.DreamItem.stack(com.divinerpgextended.vethea.DreamItem.WINGS_TEMPLATE)));
        list.add(new InfiniteTrade(new ItemStack(VetheaItems.cleanPearls, MIDNIGHT_TEMPLATE_PRICE), null,
            com.divinerpgextended.vethea.DreamItem.stack(com.divinerpgextended.vethea.DreamItem.QUEEN_TEMPLATE)));
    }

    private static boolean holdsFish(EntityPlayer p) {
        ItemStack held = p.getCurrentEquippedItem();
        return held != null && held.getItem() == ModItems.fish;
    }

    private static ItemStack rod(Rod r) {
        return new ItemStack(ModItems.rods[r.ordinal()]);
    }

    /**
     * Удочку в список торговца кладём заново при каждом разговоре: список у Divine хранится в NBT обычными сделками
     * (с лимитом использований), а так она всегда бесконечная и с актуальной ценой.
     */
    private static void trade(EntityDivineRPGVillager v, EntityPlayer p, MerchantRecipe offer) {
        MerchantRecipeList list = v.getRecipes(p);
        if (list == null) return;
        for (Iterator<?> it = list.iterator(); it.hasNext();) {
            ItemStack sell = ((MerchantRecipe) it.next()).getItemToSell();
            if (sell != null && sell.getItem() instanceof ItemDreRod) it.remove();
        }
        list.add(offer);
    }

    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static long day(World w) {
        return w.getWorldTime() / 24000L;
    }

    /** Рыба дня: одна на мир, меняется каждые сутки. */
    public static Fish questFish(World w) {
        List<Fish> l = FishingLoot.questFish();
        long d = day(w);
        int h = (int) ((d * 0x9E3779B1L) >>> 7);
        return l.get(Math.abs(h) % l.size());
    }

    /** Присев - узнать задание; с рыбой дня в руке - сдать. Возвращает true, если Леорна занята заданием. */
    private static boolean quest(EntityPlayer p) {
        World w = p.worldObj;
        Fish f = questFish(w);
        ItemStack held = p.getCurrentEquippedItem();
        boolean holdsFish = held != null && held.getItem() == ModItems.fish;
        if (!p.isSneaking() && !holdsFish) return false;
        NBTTagCompound tag = persisted(p);
        boolean doneToday = tag.hasKey(QUEST_DAY) && tag.getLong(QUEST_DAY) == day(w);
        String name = StatCollector.translateToLocal("item.dre.fish." + f.key() + ".name");
        String zone = StatCollector.translateToLocal("dre.zone." + f.zone.key());
        if (doneToday) {
            say(p, EnumChatFormatting.GRAY, "dre.quest.done_today");
            com.divinerpgextended.debug.Debug.note(p, "Леорна: задание на сегодня уже сдано (день " + day(w) + ")");
            return true;
        }
        if (!holdsFish || held.getItemDamage() != f.ordinal()) {
            say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.quest.ask", name, zone);
            com.divinerpgextended.debug.Debug.note(p, "Леорна: рыба дня " + f + " (" + f.zone + ")" + (holdsFish ? ", в руке не та рыба" : ""));
            progress(p);
            return true;
        }
        if (!p.capabilities.isCreativeMode) held.stackSize--;
        if (held.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
        int n = tag.getInteger(QUESTS) + 1;
        tag.setInteger(QUESTS, n);
        tag.setLong(QUEST_DAY, day(w));
        give(p, new ItemStack(ArcanaItems.arcanium, 2 + w.rand.nextInt(3)));
        Bait b = Bait.randomArcana(w.rand);
        give(p, new ItemStack(b.item(), 3 + w.rand.nextInt(4)));
        say(p, EnumChatFormatting.GREEN, "dre.quest.thanks", String.valueOf(n));
        com.divinerpgextended.debug.Debug.note(p, "Леорна: задание сдано, всего " + n + ", награда арканиум и приманка " + b
            + (n % 5 == 0 ? " + ящик" : "") + (milestone(n) != null ? " + веха " + milestone(n).getDisplayName() : ""));
        if (n % 5 == 0) {
            ItemStack crate = new ItemStack(ModItems.crate, 1, f.zone.ordinal());
            give(p, crate.copy());
            say(p, EnumChatFormatting.GOLD, "dre.quest.reward", name(crate));
        }
        ItemStack m = milestone(n);
        if (m != null) {
            give(p, m.copy());
            say(p, EnumChatFormatting.GOLD, "dre.quest.reward", name(m));
        }
        progress(p);
        w.playSoundAtEntity(p, "random.levelup", 0.6F, 1.2F);
        p.inventoryContainer.detectAndSendChanges();
        return true;
    }

    /** Лестница наград Леорны: номер задания -> подарок (плюс ящик за каждое 5-е и арканиум с приманками за каждое). */
    public static final int[] MILESTONES = { 3, 10, 15, 20, 25, 30, 50 };

    public static ItemStack milestone(int n) {
        switch (n) {
            case 3: return rod(Rod.ARCANA);
            case 10: return rod(Rod.GOLDEN);
            case 15: return new ItemStack(ModItems.angler[0]);
            case 20: return new ItemStack(ModItems.angler[1]);
            case 25: return new ItemStack(ModItems.angler[2]);
            case 30: return new ItemStack(ModItems.fishGuide);
            case 50: return new ItemStack(ModItems.trinket, 1, com.divinerpgextended.trinket.Trinket.TACKLE_BOX.ordinal());
            default: return null;
        }
    }

    public static int quests(EntityPlayer p) {
        return persisted(p).getInteger(QUESTS);
    }

    /** "Выполнено N, следующая награда - X на M-м задании". */
    public static void progress(EntityPlayer p) {
        int n = quests(p);
        for (int m : MILESTONES) {
            if (m > n) {
                say(p, EnumChatFormatting.YELLOW, "dre.quest.progress", String.valueOf(n), name(milestone(m)), String.valueOf(m));
                return;
            }
        }
        say(p, EnumChatFormatting.YELLOW, "dre.quest.progress_all", String.valueOf(n));
    }

    private static ChatComponentTranslation name(ItemStack s) {
        return new ChatComponentTranslation(s.getUnlocalizedName() + ".name");
    }

    private static void give(EntityPlayer p, ItemStack s) {
        if (!p.inventory.addItemStackToInventory(s)) p.worldObj.spawnEntityInWorld(new EntityItem(p.worldObj, p.posX, p.posY + 0.5,
            p.posZ, s));
    }

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        com.divinerpgextended.audit.Audit.say(p, key, args);
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }
}
