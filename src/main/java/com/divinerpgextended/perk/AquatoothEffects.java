package com.divinerpgextended.perk;

import java.util.Iterator;
import java.util.List;

import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Aquatooth weapons and mauls (since 0.99.113, together with the Liopleurodon - a late boss):
 * <ul>
 * <li>Aquatooth Sword - damage {@link #SWORD_DAMAGE} (between the Apalachia blades at 29 and Skythern at 31: the Liopleurodon sits between those
 * worlds; before 0.99.120 it was 35, almost like halite at 36) and bleeding: a hit mob loses {@link #BLEED_DAMAGE} per second
 * through armor for {@link #BLEED_SECONDS} s (a player - where PvP is allowed).</li>
 * <li>Aquatooth Maul - damage {@link #MAUL_DAMAGE} and an area hit: mobs of the same kind within {@link #WAVE_R} blocks of the target
 * receive {@link #MAUL_DAMAGE} (like the Bedrock Maul wave, players and pets are not affected).</li>
 * <li>All mauls have a real cooldown of {@link #MAUL_COOLDOWN} ticks: earlier hits do not land (the client does not even allow the swing,
 * {@link com.divinerpgextended.client.MaulSwingClient}).</li>
 * </ul>
 * The crab and Liopleurodon anchors are no longer craftable: they are summons, dropped by Carcinus and the Liopleurodon.
 */
public class AquatoothEffects {

    public static final float SWORD_DAMAGE = 30F, MAUL_DAMAGE = 25F, BLEED_DAMAGE = 4F;
    public static final int BLEED_SECONDS = 5, MAUL_COOLDOWN = 20;
    public static final double WAVE_R = 3.5;
    private static final String BLEED = "dreBleed", BLEED_BY = "dreBleedBy";
    /** Tick of the last maul hit (player data). */
    public static final String SWING = "dreMaulSwing";
    /** For the selftest: no cooldown (other checks hit with a maul several times per tick). */
    public static boolean testNoCooldown;
    public static final DamageSource BLEEDING = new DamageSource("dre.bleed").setDamageBypassesArmor();
    private static boolean inWave;

    /** After Divine items are registered: damage of the aquatooth weapons, without recipes for the summon anchors. */
    public static void postInit() {
        setDamage(VanillaItemsWeapons.aquatoothSword, SWORD_DAMAGE);
        setDamage(VanillaItemsWeapons.aquatoothMaul, MAUL_DAMAGE);
        removeRecipes(VanillaItemsWeapons.crabAnchor);
        removeRecipes(VanillaItemsWeapons.liopleurodonAnchor);
    }

    static void setDamage(Item i, float d) {
        if (!(i instanceof ItemSword)) return;
        try {
            ReflectionHelper.setPrivateValue(ItemSword.class, (ItemSword) i, d, "field_150934_a");
        } catch (Throwable t) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Damage of " + i.getUnlocalizedName() + " not changed: " + t);
        }
    }

    /** Damage of the base sword (for the selftest). */
    public static float damage(Item i) {
        return i instanceof ItemSword ? (Float) ReflectionHelper.getPrivateValue(ItemSword.class, (ItemSword) i, "field_150934_a") : -1F;
    }

    @SuppressWarnings("unchecked")
    static void removeRecipes(Item item) {
        if (item == null) return;
        for (Iterator<IRecipe> it = CraftingManager.getInstance().getRecipeList().iterator(); it.hasNext();) {
            ItemStack out = it.next().getRecipeOutput();
            if (out != null && out.getItem() == item) it.remove();
        }
    }

    public static boolean maul(ItemStack s) {
        return MeleeRoles.kind(s) == MeleeRoles.Kind.MAUL;
    }

    /** Maul: a hit before the cooldown does not land (server; the client does not even allow the swing). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(AttackEntityEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (p.worldObj.isRemote || testNoCooldown || !maul(p.getHeldItem())) return;
        long now = p.worldObj.getTotalWorldTime(), last = p.getEntityData().getLong(SWING);
        if (p.getEntityData().hasKey(SWING) && now - last < MAUL_COOLDOWN - 2 && now >= last) {
            e.setCanceled(true);
            return;
        }
        p.getEntityData().setLong(SWING, now);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || inWave) return;
        Entity a = e.source.getEntity();
        if (!(a instanceof EntityPlayer) || e.source.getSourceOfDamage() != a || a == v) return;
        EntityPlayer p = (EntityPlayer) a;
        ItemStack held = p.getHeldItem();
        if (held == null || Protection.spare(p, v)) return;
        if (held.getItem() == VanillaItemsWeapons.aquatoothSword) { // and the player - Protection.spare above already checked PvP
            v.getEntityData().setLong(BLEED, v.worldObj.getTotalWorldTime() + BLEED_SECONDS * 20);
            v.getEntityData().setString(BLEED_BY, p.getUniqueID().toString());
        }
        if (held.getItem() == VanillaItemsWeapons.aquatoothMaul) wave(p, v);
    }

    /** Area hit of the Aquatooth Maul. */
    static int wave(EntityPlayer p, EntityLivingBase center) {
        @SuppressWarnings("unchecked")
        List<EntityLivingBase> l = center.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, center.boundingBox.expand(WAVE_R, 1.5, WAVE_R));
        int n = 0;
        inWave = true;
        try {
            for (EntityLivingBase o : l) {
                if (o == center || o == p || o instanceof EntityPlayer || o instanceof com.divinerpgextended.dummy.EntityTrainingDummy || !o.isEntityAlive()
                    || o.getDistanceToEntity(center) > WAVE_R + 1) continue;
                if (!ColorGear.sameKind(o, center) || Protection.spare(p, o)) continue;
                o.hurtResistantTime = 0;
                o.attackEntityFrom(DamageSource.causePlayerDamage(p), MAUL_DAMAGE);
                n++;
            }
        } finally {
            inWave = false;
        }
        if (center.worldObj instanceof WorldServer)
            ((WorldServer) center.worldObj).func_147487_a("splash", center.posX, center.posY + 0.5, center.posZ, 40, WAVE_R * 0.5, 0.3, WAVE_R * 0.5, 0.2);
        center.worldObj.playSoundAtEntity(center, "random.splash", 0.8F, 0.8F);
        return n;
    }

    /** Bleeding: once a second until it runs out. */
    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || v.ticksExisted % 20 != 0) return;
        long until = v.getEntityData().getLong(BLEED);
        if (until == 0) return;
        if (v.worldObj.getTotalWorldTime() > until || Protection.shielded(v) && !bleedPvp(v)) {
            v.getEntityData().removeTag(BLEED);
            return;
        }
        v.hurtResistantTime = 0;
        v.attackEntityFrom(BLEEDING, BLEED_DAMAGE);
        if (v.worldObj instanceof WorldServer)
            ((WorldServer) v.worldObj).func_147487_a("reddust", v.posX, v.posY + v.height * 0.6, v.posZ, 8, v.width * 0.4, v.height * 0.3, v.width * 0.4, 0);
    }

    /**
     * A player, a villager or an animal bleeds while the attacker is in the world and the region allows him to hurt it (moved into someone else's
     * region - the bleeding stops).
     */
    private static boolean bleedPvp(EntityLivingBase v) {
        String by = v.getEntityData().getString(BLEED_BY);
        EntityPlayer p = null;
        try {
            p = by.isEmpty() ? null : v.worldObj.func_152378_a(java.util.UUID.fromString(by));
        } catch (IllegalArgumentException ignored) {}
        return p != null && !Protection.spare(p, v);
    }

    /** Whether it is bleeding (for the selftest). */
    public static boolean bleeding(EntityLivingBase v) {
        return v.getEntityData().getLong(BLEED) > v.worldObj.getTotalWorldTime();
    }
}
