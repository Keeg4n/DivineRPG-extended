package com.divinerpgextended.easter;

import net.divinerpg.utils.items.ArcanaItems;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Easter eggs (a renamed item changes only the look): the item stays the same Divine item
 * with all its properties, the look and sounds follow the name given on the anvil.
 * <ul>
 * <li>Realmite armor named "H.E.V" - the H.E.V. suit from Half-Life: its own model (the helmet has two looks - the Mark IV helmet or
 * Gordon's glasses, switched at the crafting table: the helmet alone in the grid), the suit voice in chat (login,
 * fractures, critical health), morphine at low health no more than once per {@link #MORPHINE_EVERY} ticks; with the sounds of
 * Half-Life, if they are installed ({@link com.divinerpgextended.client.HevSounds}).</li>
 * <li>Realmite sword named "crowbar" - a crowbar.</li>
 * <li>Merik's Rocket Launcher named "rpg" - an RPG-7, the rocket flies as a PG-7 grenade.</li>
 * <li>An Arcana Storm Sword thrown on the ground becomes Musou no Hitotachi when struck by lightning: a 3D katana in hand and on
 * the ground, its own name; all the sword's properties are the same. Lightning does not burn it.</li>
 * </ul>
 * Renamed back - the look is ordinary.
 */
public final class Easter {

    public static final int MORPHINE_EVERY = 6000, MORPHINE_AT = 6, CRITICAL_EVERY = 600;
    static final String WORN = "dreHevWorn", MORPHINE = "dreHevMorphine", CRITICAL = "dreHevCritical";

    private Easter() {}

    /** Easter eggs are on: on the server - from the config, for the player - as the server decided ({@link Toggle}). */
    public static volatile boolean enabled = true;

    public static boolean on() {
        return enabled;
    }

    /** The server tells the player on login whether Easter eggs are on. */
    public static class Toggle implements cpw.mods.fml.common.network.simpleimpl.IMessage {

        boolean on;

        public Toggle() {}

        Toggle(boolean on) {
            this.on = on;
        }

        @Override
        public void fromBytes(io.netty.buffer.ByteBuf buf) {
            on = buf.readBoolean();
        }

        @Override
        public void toBytes(io.netty.buffer.ByteBuf buf) {
            buf.writeBoolean(on);
        }
    }

    public static class ToggleHandler implements cpw.mods.fml.common.network.simpleimpl.IMessageHandler<Toggle, cpw.mods.fml.common.network.simpleimpl.IMessage> {

        @Override
        public cpw.mods.fml.common.network.simpleimpl.IMessage onMessage(Toggle m, cpw.mods.fml.common.network.simpleimpl.MessageContext ctx) {
            enabled = m.on;
            return null;
        }
    }

    public static void registerMessages() {
        com.divinerpgextended.cold.ColdHandler.NET.registerMessage(ToggleHandler.class, Toggle.class, 20, cpw.mods.fml.relauncher.Side.CLIENT);
    }

    static boolean named(ItemStack s, String... names) {
        if (s == null || !s.hasDisplayName()) return false;
        String n = s.getDisplayName().trim();
        for (String k : names) if (n.equalsIgnoreCase(k)) return true;
        return false;
    }

    static boolean realmiteArmor(Item i) {
        return i == VanillaItemsArmor.realmiteHelmet || i == VanillaItemsArmor.realmiteBody || i == VanillaItemsArmor.realmiteLegs || i == VanillaItemsArmor.realmiteBoots;
    }

    /** A part of the H.E.V. suit: Realmite armor named H.E.V. */
    public static boolean hev(ItemStack s) {
        return enabled && s != null && realmiteArmor(s.getItem()) && named(s, "H.E.V", "H.E.V.", "HEV");
    }

    /** The suit helmet in the form of Gordon's glasses (otherwise - the Mark IV helmet). */
    public static boolean goggles(ItemStack s) {
        return hev(s) && s.getItem() == VanillaItemsArmor.realmiteHelmet && s.hasTagCompound() && s.getTagCompound().getBoolean(STYLE);
    }

    public static final String STYLE = "DreHevGoggles";

    /** The suit helmet alone in the crafting grid - the same helmet in another look (Mark IV helmet <-> glasses). */
    public static class StyleRecipe implements net.minecraft.item.crafting.IRecipe {

        static ItemStack single(net.minecraft.inventory.InventoryCrafting inv) {
            ItemStack found = null;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s == null) continue;
                if (found != null) return null;
                found = s;
            }
            return found != null && hev(found) && found.getItem() == VanillaItemsArmor.realmiteHelmet ? found : null;
        }

        @Override
        public boolean matches(net.minecraft.inventory.InventoryCrafting inv, net.minecraft.world.World w) {
            return single(inv) != null;
        }

        @Override
        public ItemStack getCraftingResult(net.minecraft.inventory.InventoryCrafting inv) {
            ItemStack s = single(inv);
            if (s == null) return null;
            ItemStack out = s.copy();
            out.stackSize = 1;
            out.getTagCompound().setBoolean(STYLE, !goggles(s));
            return out;
        }

        @Override
        public int getRecipeSize() {
            return 1;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return null;
        }
    }

    public static void registerRecipes() {
        cpw.mods.fml.common.registry.GameRegistry.addRecipe(new StyleRecipe());
        net.minecraftforge.oredict.RecipeSorter.register("divinerpgextended:hev_style", StyleRecipe.class, net.minecraftforge.oredict.RecipeSorter.Category.SHAPELESS,
            "after:minecraft:shapeless");
    }

    public static boolean fullHev(EntityPlayer p) {
        for (int i = 0; i < 4; i++) if (!hev(p.getCurrentArmor(i))) return false;
        return true;
    }

    /** Crowbar: a Realmite sword named crowbar. */
    public static boolean crowbar(ItemStack s) {
        return enabled && s != null && s.getItem() == VanillaItemsWeapons.realmiteSword && named(s, "crowbar");
    }

    public static final String MUSOU = "DreMusou", MUSOU_NAME = "Musou no Hitotachi";

    /** Musou no Hitotachi: an Arcana Storm Sword transformed by lightning (a mark in the item). */
    public static boolean musou(ItemStack s) {
        return enabled && s != null && s.getItem() == ArcanaItems.stormSword && s.hasTagCompound() && s.getTagCompound().getBoolean(MUSOU);
    }

    /** Transform the Storm Sword: mark and name; true - succeeded (it is a Storm Sword and not yet transformed). */
    public static boolean awaken(ItemStack s) {
        if (!enabled || s == null || s.getItem() != ArcanaItems.stormSword) return false;
        if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
        if (s.getTagCompound().getBoolean(MUSOU)) return false;
        s.getTagCompound().setBoolean(MUSOU, true);
        s.setStackDisplayName(EnumChatFormatting.RESET + MUSOU_NAME); // without the italics of renamed ones
        return true;
    }

    /** RPG-7: Merik's Rocket Launcher named rpg. */
    public static boolean rpg(ItemStack s) {
        return enabled && s != null && s.getItem() == ArcanaItems.meriksMissile && named(s, "rpg", "rpg-7", "rpg7");
    }

    static void say(EntityPlayer p, String key) {
        ChatComponentTranslation m = new ChatComponentTranslation(key);
        m.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD));
        p.addChatMessage(m);
    }

    /** Suit voice and morphine (server). */
    public static class Events {

        /** Lightning on a thrown Storm Sword: does not burn it, but transforms it into Musou no Hitotachi. */
        @SubscribeEvent
        public void onLightning(net.minecraftforge.event.entity.EntityStruckByLightningEvent e) {
            if (!(e.entity instanceof net.minecraft.entity.item.EntityItem) || e.entity.worldObj.isRemote) return;
            net.minecraft.entity.item.EntityItem it = (net.minecraft.entity.item.EntityItem) e.entity;
            ItemStack s = it.getEntityItem();
            if (!enabled || s == null || s.getItem() != ArcanaItems.stormSword) return;
            e.setCanceled(true);
            cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.entity.Entity.class, it, true, "isImmuneToFire", "field_70178_ae");
            it.extinguish();
            if (awaken(s)) {
                it.setEntityItemStack(s);
                ((net.minecraft.world.WorldServer) it.worldObj).func_147487_a("witchMagic", it.posX, it.posY + 0.5, it.posZ, 60, 0.4, 0.6, 0.4, 0.2);
            }
        }

        @SubscribeEvent
        public void onLogin(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent e) {
            if (e.player instanceof net.minecraft.entity.player.EntityPlayerMP)
                com.divinerpgextended.cold.ColdHandler.NET.sendTo(new Toggle(com.divinerpgextended.Config.easterEggs), (net.minecraft.entity.player.EntityPlayerMP) e.player);
        }

        @SubscribeEvent
        public void onTick(TickEvent.PlayerTickEvent e) {
            EntityPlayer p = e.player;
            if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote || p.ticksExisted % 10 != 0) return;
            tick(p);
        }

        /** Suit helmet: which look and how to change it. */
        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent e) {
            if (!hev(e.itemStack) || e.itemStack.getItem() != VanillaItemsArmor.realmiteHelmet) return;
            e.toolTip.add(EnumChatFormatting.GOLD + net.minecraft.util.StatCollector.translateToLocal(goggles(e.itemStack) ? "dre.tooltip.hev_goggles" : "dre.tooltip.hev_helmet"));
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onHurt(LivingHurtEvent e) {
            if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || e.ammount <= 0) return;
            hurt((EntityPlayer) e.entityLiving, e.source, e.ammount);
        }
    }

    /** Once every half second: put on the full suit - login; low health - morphine. */
    public static void tick(EntityPlayer p) {
        ItemStack held = p.getHeldItem(); // a tati transformed before 0.99.56 - the name without italics
        if (musou(held) && MUSOU_NAME.equals(held.getDisplayName())) held.setStackDisplayName(EnumChatFormatting.RESET + MUSOU_NAME);
        NBTTagCompound d = p.getEntityData();
        boolean full = fullHev(p);
        if (full && !d.getBoolean(WORN)) say(p, "dre.hev.logon");
        d.setBoolean(WORN, full);
        if (!full || !p.isEntityAlive() || p.getHealth() > MORPHINE_AT) return;
        long now = p.worldObj.getTotalWorldTime(), last = d.getLong(MORPHINE);
        if (d.hasKey(MORPHINE) && now - last < MORPHINE_EVERY && now >= last) return;
        d.setLong(MORPHINE, now);
        p.addPotionEffect(new PotionEffect(Potion.regeneration.id, 100, 1));
        say(p, "dre.hev.morphine");
    }

    /** A hit on a player in the suit: fractures on falling, critical health. */
    public static void hurt(EntityPlayer p, DamageSource s, float amount) {
        if (!fullHev(p)) return;
        if (s == DamageSource.fall) say(p, amount >= 6 ? "dre.hev.major_fracture" : "dre.hev.minor_fracture");
        if (p.getHealth() - amount <= MORPHINE_AT && p.getHealth() - amount > 0) {
            long now = p.worldObj.getTotalWorldTime(), last = p.getEntityData().getLong(CRITICAL);
            if (!p.getEntityData().hasKey(CRITICAL) || now - last >= CRITICAL_EVERY || now < last) {
                p.getEntityData().setLong(CRITICAL, now);
                say(p, "dre.hev.health_critical");
            }
        }
    }
}
