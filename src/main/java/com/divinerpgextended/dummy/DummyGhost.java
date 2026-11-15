package com.divinerpgextended.dummy;

import java.lang.reflect.Method;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * A hidden copy of the player for the dummy: the same damage path as the player's (armor and its sets, accessories, potions, arcana, all
 * Divine and addon damage events), but outside the world: does not spawn, is not saved, does not die and drops nothing. The player
 * snapshot ({@link #snapshot}) is only what is worn, the player data (accessories, effects, progress), potion effects and arcana;
 * the inventory and the item in hand are not copied.
 */
public class DummyGhost extends FakePlayer {

    /** The dummy of this copy: delayed hits on the copy (the arcana strike of Arcana swords) go to it, otherwise it will not see them. */
    public EntityTrainingDummy owner;

    /** The copy's health reserve: so that any hit is measured in full. */
    static final float HEALTH = 100000F;
    private static Method damageEntity;

    DummyGhost(WorldServer w, UUID id) {
        super(w, new GameProfile(id, "dre_dummy"));
        // a dead connection: the copy's ordinary packets (chat, effects) go nowhere instead of crashing the server
        new Deaf(w.func_73046_m(), this);
    }

    /** The copy's connection: sends nothing. */
    static class Deaf extends net.minecraft.network.NetHandlerPlayServer {
        Deaf(net.minecraft.server.MinecraftServer s, DummyGhost g) {
            super(s, new net.minecraft.network.NetworkManager(false), g);
        }

        @Override
        public void sendPacket(net.minecraft.network.Packet p) {}
    }

    /** The copy's arcana - straight into the field (Divine's setBarValue sends a packet over its own network, and the copy has no connection). */
    public static void setBar(net.divinerpg.utils.events.ArcanaHelper h, float v) {
        try {
            ReflectionHelper.setPrivateValue(net.divinerpg.utils.events.ArcanaHelper.class, h, v, "barValue");
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isEntityInvulnerable() {
        return false;
    }

    @Override
    public void onDeath(DamageSource s) {}

    /** A player snapshot for the dummy. */
    public static NBTTagCompound snapshot(EntityPlayer p) {
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("Data", p.getEntityData().copy());
        NBTTagList armor = new NBTTagList();
        for (int i = 0; i < 4; i++) {
            NBTTagCompound c = new NBTTagCompound();
            if (p.inventory.armorInventory[i] != null) p.inventory.armorInventory[i].writeToNBT(c);
            armor.appendTag(c);
        }
        t.setTag("Armor", armor);
        NBTTagList fx = new NBTTagList();
        for (Object o : p.getActivePotionEffects()) fx.appendTag(((PotionEffect) o).writeCustomPotionEffectToNBT(new NBTTagCompound()));
        t.setTag("Potions", fx);
        t.setDouble("MaxHealth", p.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth).getBaseValue());
        net.divinerpg.utils.events.ArcanaHelper h = net.divinerpg.utils.events.ArcanaHelper.getProperties(p);
        t.setFloat("Arcana", h == null ? 0 : h.getBarValue());
        t.setString("Name", p.getCommandSenderName());
        t.setString("UUID", p.getUniqueID().toString());
        return t;
    }

    /** A copy of the player from a snapshot. */
    static DummyGhost build(WorldServer w, UUID id, NBTTagCompound snap, int dim, double x, double y, double z) {
        DummyGhost g = new DummyGhost(w, id);
        g.dimension = dim;
        g.setPosition(x, y, z);
        NBTTagCompound data = snap.getCompoundTag("Data");
        for (Object k : data.func_150296_c()) g.getEntityData().setTag((String) k, data.getTag((String) k).copy());
        NBTTagList armor = snap.getTagList("Armor", 10);
        for (int i = 0; i < 4 && i < armor.tagCount(); i++) {
            NBTTagCompound c = armor.getCompoundTagAt(i);
            g.inventory.armorInventory[i] = c.hasNoTags() ? null : net.minecraft.item.ItemStack.loadItemStackFromNBT(c);
        }
        NBTTagList fx = snap.getTagList("Potions", 10);
        for (int i = 0; i < fx.tagCount(); i++) g.addPotionEffect(PotionEffect.readCustomPotionEffectFromNBT(fx.getCompoundTagAt(i)));
        return g;
    }

    /** The copy's arcana before the last hit and how much of it the arcane ward removed. */
    float arcanaBefore, arcanaSpent;

    /** How much damage would reach the player from this hit (0 - dodged or the hit was cancelled). */
    /** The arcana regeneration pause after the last hit (set by the arcane ward). */
    int arcanaDelay;

    float measure(DamageSource s, float amount, float arcana, int delay) {
        net.minecraft.entity.ai.attributes.IAttributeInstance hp = getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth);
        hp.setBaseValue(HEALTH);
        setHealth(HEALTH);
        setAbsorptionAmount(0);
        hurtResistantTime = 0;
        net.divinerpg.utils.events.ArcanaHelper h = net.divinerpg.utils.events.ArcanaHelper.getProperties(this);
        if (h != null) {
            setBar(h, arcana); // how much arcana the dummy has now: the ward spends it instead of taking it anew every time
            h.regenDelay = delay; // and the regeneration pause is the one the dummy has going
        }
        arcanaBefore = h != null ? h.getBarValue() : 0;
        arcanaDelay = delay;
        arcanaSpent = 0;
        try {
            if (net.minecraftforge.common.ForgeHooks.onLivingAttack(this, s, amount)) return 0;
            if (damageEntity == null) damageEntity = ReflectionHelper.findMethod(net.minecraft.entity.EntityLivingBase.class, this,
                new String[] { "damageEntity", "func_70665_d" }, DamageSource.class, float.class);
            damageEntity.invoke(this, s, amount);
            if (h != null) {
                arcanaSpent = Math.max(0, arcanaBefore - h.getBarValue());
                arcanaDelay = h.regenDelay;
            }
        } catch (Throwable t) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Dummy: hit not counted as a player hit, shown as is: " + t);
            return amount;
        }
        return HEALTH - getHealth();
    }
}
