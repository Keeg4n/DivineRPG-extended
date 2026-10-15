package com.divinerpgextended.vethea;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.divinerpg.utils.blocks.VetheaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.ISpecialArmor;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.fishing.Fish;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.trinket.ItemTrinket;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.registry.GameData;

/**
 * Ветея - мир кошмаров: чужие (не ветейские) вещи работают наоборот. Вместо отдельного инвентаря: принести можно что
 * угодно, но пользоваться этим во сне вредно.
 * <ul>
 * <li>чужое оружие лечит цель вместо урона (по игрокам - просто не бьёт);</li>
 * <li>чужая броня не защищает, а усиливает входящий урон настолько, насколько защищала бы (80% -> x1.8);</li>
 * <li>чужая еда отнимает сытость, чужие зелья дают обратный эффект;</li>
 * <li>чужие инструменты копают в 5 раз медленнее.</li>
 * </ul>
 * Своими считаются предметы и блоки Ветеи из Divine, ветейское от аддона и белый список из конфига.
 */
public class Nightmare {

    private static Set<Item> vethean;
    /** Какие подсказки игрок уже видел за этот заход. */
    private static final Map<UUID, Set<String>> HINTS = new HashMap<UUID, Set<String>>();

    public static boolean inVethea(Entity e) {
        return e != null && e.dimension == ConfigurationHelper.vethea;
    }

    public static boolean active(EntityPlayer p) {
        return Config.vetheaNightmare && inVethea(p) && !p.capabilities.isCreativeMode;
    }

    /** Оператор сервера (или хозяин одиночной игры с читами). */
    public static boolean op(EntityPlayer p) {
        MinecraftServer s = MinecraftServer.getServer();
        return s != null && s.getConfigurationManager().func_152596_g(p.getGameProfile());
    }

    private static synchronized Set<Item> vethean() {
        if (vethean != null) return vethean;
        Set<Item> s = new HashSet<Item>();
        for (Class<?> c : new Class<?>[] { VetheaItems.class, VetheaBlocks.class }) {
            for (Field f : c.getFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                try {
                    Object o = f.get(null);
                    if (o instanceof Item) s.add((Item) o);
                    else if (o instanceof Block && Item.getItemFromBlock((Block) o) != null) s.add(Item.getItemFromBlock((Block) o));
                } catch (Exception ignored) {}
            }
        }
        s.add(ModItems.nightmareDrill);
        for (String name : Config.vetheaWhitelist) {
            Object o = GameData.getItemRegistry().getObject(name.trim());
            if (o instanceof Item) s.add((Item) o);
        }
        return vethean = s;
    }

    /** Своя для Ветеи вещь (пустая рука - тоже). */
    public static boolean vethean(ItemStack s) {
        if (s == null) return true;
        Item i = s.getItem();
        if (i == ModItems.trinket) return ItemTrinket.of(s).vethean();
        if (i == ModItems.fish) {
            int m = s.getItemDamage();
            return m >= 0 && m < Fish.values().length && Fish.values()[m].zone == com.divinerpgextended.fishing.Zone.DREAM;
        }
        return vethean().contains(i);
    }

    private static boolean weapon(ItemStack s) {
        Item i = s.getItem();
        if (i instanceof ItemSword || i instanceof ItemTool || i instanceof ItemBow) return true;
        @SuppressWarnings("rawtypes")
        Collection c = s.getAttributeModifiers().get(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName());
        return c != null && !c.isEmpty();
    }

    static void hint(EntityPlayer p, String key) {
        Set<String> seen = HINTS.get(p.getUniqueID());
        if (seen == null) HINTS.put(p.getUniqueID(), seen = new HashSet<String>());
        if (!seen.add(key)) return;
        ChatComponentTranslation c = new ChatComponentTranslation("dre.nightmare." + key);
        c.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_PURPLE).setItalic(true));
        p.addChatMessage(c);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        HINTS.remove(e.player.getUniqueID());
    }

    @SubscribeEvent
    public void onEnter(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.toDim == ConfigurationHelper.vethea && Config.vetheaNightmare) {
            Set<String> seen = HINTS.get(e.player.getUniqueID());
            if (seen != null) seen.remove("enter");
            hint(e.player, "enter");
        }
    }

    // ---------------- Урон: чужое оружие и чужая броня ----------------

    /** Урон, перевыпущенный сквозь броню (чужая броня в Ветее не защищает). */
    static class Unarmored extends DamageSource {

        Unarmored(DamageSource orig) {
            super(orig.damageType);
            setDamageBypassesArmor();
            if (orig.isFireDamage()) setFireDamage();
            if (orig.isProjectile()) setProjectile();
            if (orig.isExplosion()) setExplosion();
            if (orig.isMagicDamage()) setMagicDamage();
        }
    }

    static class UnarmoredEntity extends EntityDamageSourceIndirect {

        UnarmoredEntity(DamageSource orig) {
            super(orig.damageType, orig.getSourceOfDamage() != null ? orig.getSourceOfDamage() : orig.getEntity(), orig.getEntity());
            setDamageBypassesArmor();
            if (orig.isFireDamage()) setFireDamage();
            if (orig.isProjectile()) setProjectile();
            if (orig.isExplosion()) setExplosion();
            if (orig.isMagicDamage()) setMagicDamage();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAttack(LivingAttackEvent e) {
        if (e.isCanceled() || e.entityLiving.worldObj.isRemote || !inVethea(e.entityLiving) || !Config.vetheaNightmare) return;
        if (e.source instanceof Unarmored || e.source instanceof UnarmoredEntity) return;
        Entity src = e.source.getEntity();
        // чужое оружие (или выстрел, пока в руке чужая вещь) лечит
        if (src instanceof EntityPlayer && active((EntityPlayer) src)) {
            EntityPlayer p = (EntityPlayer) src;
            ItemStack held = p.getHeldItem();
            boolean ranged = e.source.getSourceOfDamage() != src;
            if (held != null && !vethean(held) && (ranged || weapon(held))) {
                e.setCanceled(true);
                if (!(e.entityLiving instanceof EntityPlayer)) {
                    e.entityLiving.heal(e.ammount);
                    ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("heart", e.entityLiving.posX,
                        e.entityLiving.posY + e.entityLiving.height, e.entityLiving.posZ, 2, 0.3, 0.2, 0.3, 0);
                }
                hint(p, "weapon");
                return;
            }
        }
        // чужая броня: не защищает и усиливает удар
        if (e.entityLiving instanceof EntityPlayer && active((EntityPlayer) e.entityLiving) && !e.source.isUnblockable()) {
            EntityPlayer p = (EntityPlayer) e.entityLiving;
            // чем сильнее чужая броня защищала бы, тем сильнее она притягивает удар: защита 80% -> урон x1.8
            double guard = 0;
            boolean alien = false;
            for (int slot = 0; slot < 4; slot++) {
                ItemStack a = p.inventory.armorInventory[slot];
                if (a == null || vethean(a)) continue;
                alien = true;
                guard += protection(p, a, e.source, e.ammount, slot);
            }
            if (!alien) return;
            e.setCanceled(true);
            DamageSource again = e.source.getEntity() != null || e.source.getSourceOfDamage() != null ? new UnarmoredEntity(e.source)
                : new Unarmored(e.source);
            p.attackEntityFrom(again, (float) (e.ammount * (1 + Math.min(guard, 1.0))));
            hint(p, "armor");
        }
    }

    /** Какую долю урона вещь сняла бы обычно: особая броня (Divine и др.) - по её свойствам, обычная - 4% за очко. */
    private static double protection(EntityPlayer p, ItemStack a, DamageSource s, float amount, int slot) {
        Item i = a.getItem();
        try {
            if (i instanceof ISpecialArmor) return Math.max(0, ((ISpecialArmor) i).getProperties(p, a, s, amount, slot).AbsorbRatio);
        } catch (Throwable ignored) {}
        return i instanceof ItemArmor ? ((ItemArmor) i).damageReduceAmount * 0.04 : 0;
    }

    // ---------------- Еда и зелья ----------------

    @SubscribeEvent
    public void onEaten(PlayerUseItemEvent.Finish e) {
        EntityPlayer p = e.entityPlayer;
        if (p.worldObj.isRemote || !active(p) || e.item == null || vethean(e.item)) return;
        Item i = e.item.getItem();
        if (i instanceof ItemFood) {
            ItemFood f = (ItemFood) i;
            int heal = f.func_150905_g(e.item);
            // сытость уже прибавилась - отнимаем вдвое: итог как будто еда отняла столько же
            NBTTagCompound t = new NBTTagCompound();
            p.getFoodStats().writeNBT(t);
            t.setInteger("foodLevel", Math.max(0, t.getInteger("foodLevel") - 2 * heal));
            t.setFloat("foodSaturationLevel", 0F);
            p.getFoodStats().readNBT(t);
            p.addPotionEffect(new PotionEffect(Potion.hunger.id, 300, 1));
            hint(p, "food");
        } else if (i instanceof ItemPotion) {
            List<?> effects = ((ItemPotion) i).getEffects(e.item);
            if (effects == null) return;
            for (Object o : effects) {
                PotionEffect pe = (PotionEffect) o;
                Potion pot = Potion.potionTypes[pe.getPotionID()];
                if (pot == null || pot.isBadEffect()) continue;
                p.removePotionEffect(pe.getPotionID());
                if (pe.getPotionID() == Potion.heal.id) {
                    p.attackEntityFrom(DamageSource.magic, 2 * (4 << pe.getAmplifier())); // уже вылечило - снимаем вдвое
                    continue;
                }
                Potion inv = inverse(pot);
                if (inv != null) p.addPotionEffect(new PotionEffect(inv.id, Math.max(pe.getDuration(), 100), pe.getAmplifier()));
                else if (pot == Potion.fireResistance) p.setFire(Math.min(pe.getDuration() / 20, 10));
            }
            hint(p, "potion");
        }
    }

    private static Potion inverse(Potion p) {
        if (p == Potion.regeneration) return Potion.poison;
        if (p == Potion.moveSpeed || p == Potion.jump) return Potion.moveSlowdown;
        if (p == Potion.damageBoost) return Potion.weakness;
        if (p == Potion.digSpeed) return Potion.digSlowdown;
        if (p == Potion.nightVision) return Potion.blindness;
        if (p == Potion.invisibility || p == Potion.waterBreathing) return Potion.confusion;
        if (p == Potion.resistance || p == Potion.field_76434_w || p == Potion.field_76444_x) return Potion.wither;
        if (p == Potion.field_76443_y) return Potion.hunger;
        return null;
    }

    // ---------------- Инструменты ----------------

    @SubscribeEvent
    public void onBreakSpeed(BreakSpeed e) {
        EntityPlayer p = e.entityPlayer;
        if (!Config.vetheaNightmare || !inVethea(p) || p.capabilities.isCreativeMode) return;
        ItemStack held = p.getHeldItem();
        if (held == null || vethean(held)) return;
        if (held.getItem() instanceof ItemTool || held.getItem().getDigSpeed(held, e.block, e.metadata) > 1.0F) {
            e.newSpeed *= 0.2F;
            if (!p.worldObj.isRemote) hint(p, "tool");
        }
    }

}
