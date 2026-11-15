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
 * Vethea is the world of nightmares: foreign (non-Vethean) items work in reverse. Instead of a separate inventory: you can bring
 * anything, but using it in the dream is harmful.
 * <ul>
 * <li>foreign weapons heal the target instead of damaging (against players - simply do not hit);</li>
 * <li>foreign armor does not protect but amplifies incoming damage by as much as it would have protected (80% -> x1.8);</li>
 * <li>foreign food takes away satiety, foreign potions give the opposite effect;</li>
 * <li>foreign tools dig 5 times slower and only the block under the hand;</li>
 * <li>right-click with a foreign item does not work, except for building, food, potions and bows ({@link NightmareHands});</li>
 * <li>foreign Baubles accessories are silent ({@link NightmareBaubles}).</li>
 * </ul>
 * All this holds until all Vethea bosses are defeated.
 * Vethea items and blocks from Divine, Vethean items from the addon and the config whitelist count as native.
 */
public class Nightmare {

    private static Set<Item> vethean;
    /** Which hints the player has already seen during this session. */
    private static final Map<UUID, Set<String>> HINTS = new HashMap<UUID, Set<String>>();

    public static boolean inVethea(Entity e) {
        return e != null && e.dimension == ConfigurationHelper.vethea;
    }

    public static boolean inVetheaWorld(net.minecraft.world.World w) {
        return w != null && w.provider.dimensionId == ConfigurationHelper.vethea;
    }

    /** The nightmare is in effect: in Vethea, not in creative and until all Vethea bosses are defeated (after that the dream is conquered). */
    public static boolean active(EntityPlayer p) {
        return Config.vetheaNightmare && inVethea(p) && !p.capabilities.isCreativeMode && !VetheaFloors.allBosses(p);
    }

    /** In waking life (outside Vethea) dream items do not work until all Vethea bosses are defeated. */
    public static boolean awake(EntityPlayer p) {
        return Config.vetheaNightmare && !inVethea(p) && !p.capabilities.isCreativeMode && !VetheaFloors.allBosses(p);
    }

    /** Server operator (or the owner of a single-player game with cheats). */
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
        s.add(ModItems.dreamItem);
        s.add(Item.getItemFromBlock(com.divinerpgextended.registry.ModBlocks.vetheaTorch));
        for (String name : Config.vetheaWhitelist) {
            Object o = GameData.getItemRegistry().getObject(name.trim());
            if (o instanceof Item) s.add((Item) o);
        }
        return vethean = s;
    }

    /** An item native to Vethea (an empty hand too). */
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

    /**
     * Only places a block: doors, beds, signs, redstone, repeaters, pots, heads, seeds, paintings. In the dream they
     * can be used for building like ordinary blocks.
     */
    public static boolean placer(Item i) {
        return i instanceof net.minecraft.item.ItemBlock || i instanceof net.minecraft.item.ItemReed || i instanceof net.minecraft.item.ItemDoor
            || i instanceof net.minecraft.item.ItemBed || i instanceof net.minecraft.item.ItemSign || i instanceof net.minecraft.item.ItemRedstone
            || i instanceof net.minecraft.item.ItemSkull || i instanceof net.minecraft.item.ItemSeeds || i instanceof net.minecraft.item.ItemHangingEntity;
    }

    /**
     * Right-click with a foreign item in the dream before completion works for: building ({@link #placer}), food and potions (the nightmare
     * reverses them), bows (their arrows heal dream mobs). Everything else foreign (rods, foci, sigils, summons, teleports,
     * area tools, block with a sword) does not work.
     */
    public static boolean usable(ItemStack s) {
        if (s == null || vethean(s)) return true;
        Item i = s.getItem();
        return placer(i) || i instanceof ItemFood || i instanceof ItemPotion || i instanceof ItemBow;
    }

    /** Tinkers' Construct, Tainted Magic (katana, disassembler), Blood Magic (bound multi-tool). */
    private static final Set<String> FOREIGN_WEAPONS = new HashSet<String>(java.util.Arrays.asList(
        "tconstruct.library.tools.ToolCore",
        "taintedmagic.common.items.tools.ItemKatana",
        "taintedmagic.common.items.tools.ItemThaumicDisassembler",
        "WayofTime.alchemicalWizardry.api.items.ItemSpellMultiTool"));
    private static Set<Item> foreignWeapons;

    private static Set<Item> foreignWeapons() {
        if (foreignWeapons != null) return foreignWeapons;
        Set<Item> s = new HashSet<Item>();
        for (String name : Config.vetheaWeapons) {
            Object o = GameData.getItemRegistry().getObject(name.trim());
            if (o instanceof Item) s.add((Item) o);
        }
        return foreignWeapons = s;
    }

    static boolean weapon(ItemStack s) {
        Item i = s.getItem();
        if (i instanceof ItemSword || i instanceof ItemTool || i instanceof ItemBow) return true;
        // weapons of other mods without a vanilla base and a damage attribute (they compute damage themselves) - recognized by class
        for (Class<?> c = i.getClass(); c != null; c = c.getSuperclass()) {
            if (FOREIGN_WEAPONS.contains(c.getName())) return true;
        }
        if (foreignWeapons().contains(i)) return true;
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

    // ---------------- Damage: foreign weapons and foreign armor ----------------

    /** Damage reissued through the armor (foreign armor in Vethea does not protect). */
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
        if (e.isCanceled() || e.entityLiving.worldObj.isRemote || !Config.vetheaNightmare) return;
        if (!inVethea(e.entityLiving)) {
            awakeAttack(e);
            return;
        }
        if (e.source instanceof Unarmored || e.source instanceof UnarmoredEntity) return;
        Entity src = e.source.getEntity();
        // a foreign weapon (or a shot while a foreign item is in hand) heals
        if (src instanceof EntityPlayer && active((EntityPlayer) src)) {
            EntityPlayer p = (EntityPlayer) src;
            ItemStack held = p.getHeldItem();
            boolean ranged = e.source.getSourceOfDamage() != src;
            if (nativeTarget(e.entityLiving) && held != null && !vethean(held) && (ranged || weapon(held))) {
                e.setCanceled(true);
                soothe(p, e.entityLiving, e.ammount);
                return;
            }
        }
        // foreign armor: does not protect and amplifies the hit
        if (e.entityLiving instanceof EntityPlayer && active((EntityPlayer) e.entityLiving) && !e.source.isUnblockable()) {
            EntityPlayer p = (EntityPlayer) e.entityLiving;
            // the more a foreign armor would have protected, the more it attracts the hit: 80% protection -> x1.8 damage
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
            // note - before the repeated hit, otherwise debugging attaches it to it, not to the cancelled one
            com.divinerpgextended.debug.Debug.note(p, "Nightmare: foreign armor, hit replaced with x" + String.format("%.2f", 1 + Math.min(guard, 1.0))
                + " through armor");
            DamageSource again = e.source.getEntity() != null || e.source.getSourceOfDamage() != null ? new UnarmoredEntity(e.source)
                : new Unarmored(e.source);
            p.attackEntityFrom(again, (float) (e.ammount * (1 + Math.min(guard, 1.0))));
            hint(p, "armor");
        }
    }

    /**
     * Explosion of a foreign item's projectile (rockets, grenades, fireballs from foreign weapons) in the dream before completion: does not
     * hurt dream mobs but heals them, does not break dream blocks.
     */
    @SubscribeEvent
    public void onDetonate(net.minecraftforge.event.world.ExplosionEvent.Detonate e) {
        if (e.world.isRemote || !inVetheaWorld(e.world) || !Config.vetheaNightmare) return;
        EntityPlayer p = owner(e.explosion);
        if (p == null || !active(p) || vethean(p.getHeldItem())) return;
        e.getAffectedBlocks().clear();
        @SuppressWarnings("unchecked")
        List<Entity> list = e.getAffectedEntities();
        for (java.util.Iterator<Entity> it = list.iterator(); it.hasNext();) {
            Entity t = it.next();
            if (t instanceof EntityPlayer || !nativeTarget(t) || !(t instanceof EntityLivingBase)) continue;
            it.remove();
            soothe(p, (EntityLivingBase) t, e.explosion.explosionSize * 2);
        }
    }

    /** Whose explosion: the projectile's thrower, the shooter, the igniter. */
    static EntityPlayer owner(net.minecraft.world.Explosion x) {
        Entity ex = x.exploder, o = null;
        if (ex instanceof net.minecraft.entity.projectile.EntityThrowable) o = ((net.minecraft.entity.projectile.EntityThrowable) ex).getThrower();
        else if (ex instanceof net.minecraft.entity.projectile.EntityArrow) o = ((net.minecraft.entity.projectile.EntityArrow) ex).shootingEntity;
        else if (ex instanceof net.minecraft.entity.projectile.EntityFireball) o = ((net.minecraft.entity.projectile.EntityFireball) ex).shootingEntity;
        else if (ex instanceof EntityPlayer) o = ex;
        if (o == null) o = x.getExplosivePlacedBy();
        return o instanceof EntityPlayer ? (EntityPlayer) o : null;
    }

    /** A target native to the dream: Vethea's own mobs and players. Vanilla and foreign mobs are hit by foreign weapons as usual. */
    static boolean nativeTarget(Entity t) {
        return t instanceof EntityPlayer || t != null && t.getClass().getName().startsWith("net.divinerpg.entities.vethea.");
    }

    /** A foreign weapon in the dream: instead of damage - healing (players are simply not hit). */
    static void soothe(EntityPlayer p, EntityLivingBase t, float amount) {
        if (!(t instanceof EntityPlayer)) {
            t.heal(amount);
            ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("heart", t.posX, t.posY + t.height, t.posZ, 2, 0.3, 0.2, 0.3, 0);
        }
        com.divinerpgextended.debug.Debug.note(p, "Nightmare: foreign weapon heals instead of damaging (" + amount + ")");
        hint(p, "weapon");
    }

    /** In waking life until all Vethea bosses are defeated: dream weapons do not hurt, dream armor does not protect. */
    private static void awakeAttack(LivingAttackEvent e) {
        if (e.source instanceof Unarmored || e.source instanceof UnarmoredEntity) return;
        Entity src = e.source.getEntity();
        if (src instanceof EntityPlayer && awake((EntityPlayer) src) && WeaponKind.of(e.source) != null) {
            e.setCanceled(true);
            com.divinerpgextended.debug.Debug.note(src, "Awake: dream weapon does not hurt (not all Vethea bosses defeated)");
            hint((EntityPlayer) src, "awake_weapon");
            return;
        }
        if (!(e.entityLiving instanceof EntityPlayer) || e.source.isUnblockable()) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        if (!awake(p)) return;
        boolean dream = false;
        for (int slot = 0; slot < 4; slot++) {
            ItemStack a = p.inventory.armorInventory[slot];
            if (a != null && a.getItem() instanceof net.minecraft.item.ItemArmor && vethean(a)) dream = true;
        }
        if (!dream) return;
        e.setCanceled(true);
        com.divinerpgextended.debug.Debug.note(p, "Awake: dream armor does not protect, hit goes through armor");
        DamageSource again = e.source.getEntity() != null || e.source.getSourceOfDamage() != null ? new UnarmoredEntity(e.source) : new Unarmored(e.source);
        p.attackEntityFrom(again, e.ammount);
        hint(p, "awake_armor");
    }

    /** What fraction of damage the item would normally absorb: special armor (Divine etc.) - by its properties, ordinary - 4% per point. */
    private static double protection(EntityPlayer p, ItemStack a, DamageSource s, float amount, int slot) {
        Item i = a.getItem();
        try {
            if (i instanceof ISpecialArmor) return Math.max(0, ((ISpecialArmor) i).getProperties(p, a, s, amount, slot).AbsorbRatio);
        } catch (Throwable ignored) {}
        return i instanceof ItemArmor ? ((ItemArmor) i).damageReduceAmount * 0.04 : 0;
    }

    // ---------------- Food and potions ----------------

    @SubscribeEvent
    public void onEaten(PlayerUseItemEvent.Finish e) {
        EntityPlayer p = e.entityPlayer;
        if (p.worldObj.isRemote || !active(p) || e.item == null || vethean(e.item)) return;
        Item i = e.item.getItem();
        if (i instanceof ItemFood) {
            ItemFood f = (ItemFood) i;
            int heal = Math.max(1, f.func_150905_g(e.item));
            p.removePotionEffect(23); // Saturation: Botania's Mana Cookie feeds with it (it has almost no satiety of its own)
            // satiety has already been added - we take away double: the result is as if the food took away as much
            NBTTagCompound t = new NBTTagCompound();
            p.getFoodStats().writeNBT(t);
            t.setInteger("foodLevel", Math.max(0, t.getInteger("foodLevel") - 2 * heal));
            t.setFloat("foodSaturationLevel", 0F);
            p.getFoodStats().readNBT(t);
            p.addPotionEffect(new PotionEffect(Potion.hunger.id, 300, 1));
            com.divinerpgextended.debug.Debug.note(p, "Nightmare: foreign food, saturation -" + 2 * heal);
            hint(p, "food");
        } else if (i instanceof ItemPotion) {
            List<?> effects = ((ItemPotion) i).getEffects(e.item);
            if (effects == null) return;
            for (Object o : effects) {
                PotionEffect pe = (PotionEffect) o;
                Potion pot = Potion.potionTypes[pe.getPotionID()];
                if (pot == null || bad(pot)) continue;
                p.removePotionEffect(pe.getPotionID());
                if (pe.getPotionID() == Potion.heal.id) {
                    p.attackEntityFrom(DamageSource.magic, 2 * (4 << pe.getAmplifier())); // it already healed - we take away double
                    continue;
                }
                Potion inv = inverse(pot);
                if (inv != null) p.addPotionEffect(new PotionEffect(inv.id, Math.max(pe.getDuration(), 100), pe.getAmplifier()));
                else if (pot == Potion.fireResistance) p.setFire(Math.min(pe.getDuration() / 20, 10));
            }
            com.divinerpgextended.debug.Debug.note(p, "Nightmare: foreign potion inverted");
            hint(p, "potion");
        }
    }

    /**
     * Whether an effect is bad. Potion.isBadEffect() in Minecraft exists only on the client - on the server the call fails, so
     * we read the field itself.
     */
    public static boolean bad(Potion p) {
        try {
            return cpw.mods.fml.relauncher.ReflectionHelper.<Boolean, Potion>getPrivateValue(Potion.class, p, "isBadEffect", "field_76418_K");
        } catch (Throwable t) {
            return false;
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

    // ---------------- Armor tick ----------------

    /**
     * Called from InventoryPlayer.decrementAnimations instead of Item.onArmorTick ({@link com.divinerpgextended.asm.ArmorTickTransformer}):
     * in the dream before completion foreign armor does not tick - its potions, speed, flight and so on do not apply.
     */
    public static void armorTick(Item i, net.minecraft.world.World w, EntityPlayer p, ItemStack s) {
        if (active(p) && !vethean(s)) return;
        i.onArmorTick(w, p, s);
    }

    // ---------------- Tools ----------------

    @SubscribeEvent
    public void onBreakSpeed(BreakSpeed e) {
        EntityPlayer p = e.entityPlayer;
        if (!Config.vetheaNightmare || !inVethea(p) || p.capabilities.isCreativeMode || VetheaFloors.allBosses(p)) return;
        ItemStack held = p.getHeldItem();
        if (held == null || vethean(held)) return;
        if (held.getItem() instanceof ItemTool || held.getItem().getDigSpeed(held, e.block, e.metadata) > 1.0F) {
            e.newSpeed *= 0.2F;
            if (!p.worldObj.isRemote) hint(p, "tool");
        }
    }

}
