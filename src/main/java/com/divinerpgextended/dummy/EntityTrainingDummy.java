package com.divinerpgextended.dummy;

import java.util.ArrayDeque;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Training Dummy. A copy of the player who placed it (or made a copy of themselves: sneak + right-click): skin, worn armor,
 * accessories, potion effects and arcana at the moment of the copy. A hit on it is counted as if this player were being hit
 * ({@link DummyGhost}), above the head are the last hit's damage and damage per second. Nothing in its hands.
 * <p>
 * No dupes and no gain: nothing can be taken from it (the armor is only a look), it does not die and drops nothing (neither items nor
 * experience); the hitting player's health, arcana, saturation and absorption return to what they were after the hit (no vampirism
 * heals off the dummy). Only the one who placed it can remove it: sneak and hit - the item is returned once.
 * <p>
 * As with a player, it is invulnerable for half a second after a hit (a bigger hit during that time is counted by the difference), the hitting
 * player's pets hit the dummy together with them. On the server - no more than {@link DummyLimit#MAX} dummies.
 */
public class EntityTrainingDummy extends EntityLiving {

    private static final int DW_NAME = 20, DW_LAST = 21, DW_DPS = 22, DW_UUID = 23, DW_ARC = 24, DW_ARC_SPENT = 25;
    /** Damage from the environment and effects: the dummy does not show it. */
    private static final java.util.Set<String> ENVIRONMENT = new java.util.HashSet<String>(java.util.Arrays.asList("inFire", "onFire", "lava", "cactus",
        "fall", "drown", "inWall", "starve", "wither", "magic", "anvil", "fallingBlock"));
    /** Window for damage per second. */
    static final int WINDOW = 100;

    private UUID placer;
    private NBTTagCompound snap = new NBTTagCompound();
    /** The dummy's arcana: from the player snapshot, spent by the ward, regenerates like a Divine player's (+1 per tick after a pause, up to 200). */
    float arcana = -1;
    int arcanaDelay;
    public static final float ARCANA_MAX = 200F, ARCANA_REGEN = 1F;
    private DummyGhost ghost;
    private final ArrayDeque<long[]> hits = new ArrayDeque<long[]>();
    private boolean removed, listed;
    /** Pets sent at the dummy, and when their owner last hit. */
    private final java.util.Set<Integer> pets = new java.util.HashSet<Integer>();
    private long ownerHit;

    public EntityTrainingDummy(World w) {
        super(w);
        setSize(0.6F, 1.8F);
        for (int i = 0; i < 5; i++) setEquipmentDropChance(i, 0F);
        isImmuneToFire = true;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_NAME, "");
        dataWatcher.addObject(DW_LAST, Float.valueOf(0));
        dataWatcher.addObject(DW_DPS, Float.valueOf(0));
        dataWatcher.addObject(DW_UUID, "");
        dataWatcher.addObject(DW_ARC, Float.valueOf(0));
        dataWatcher.addObject(DW_ARC_SPENT, Float.valueOf(0));
    }

    /** The copy's arcana (as the player's at the time of the copy) and how much of it the arcane ward removed with the last hit. */
    public float arcana() {
        return dataWatcher.getWatchableObjectFloat(DW_ARC);
    }

    public float arcanaSpent() {
        return dataWatcher.getWatchableObjectFloat(DW_ARC_SPENT);
    }

    /** UUID of the one this is a copy of (for the skin and model like a live player); empty for dummies from before 0.99.30. */
    public String skinId() {
        return dataWatcher.getWatchableObjectString(DW_UUID);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0);
    }

    public String skinName() {
        return dataWatcher.getWatchableObjectString(DW_NAME);
    }

    public float lastHit() {
        return dataWatcher.getWatchableObjectFloat(DW_LAST);
    }

    public float dps() {
        return dataWatcher.getWatchableObjectFloat(DW_DPS);
    }

    public UUID placer() {
        return placer;
    }

    /** Become a copy of the player (a snapshot of what is worn, data, effects and arcana). */
    public void copy(EntityPlayer p) {
        snap = DummyGhost.snapshot(p);
        ghost = null;
        arcana = snap.getFloat("Arcana");
        arcanaDelay = 0;
        dataWatcher.updateObject(DW_ARC, Float.valueOf(arcana));
        dataWatcher.updateObject(DW_NAME, p.getCommandSenderName());
        dataWatcher.updateObject(DW_UUID, p.getUniqueID().toString());
        for (int i = 0; i < 4; i++) {
            ItemStack a = p.inventory.armorInventory[i];
            setCurrentItemOrArmor(i + 1, a == null ? null : a.copy()); // only a look: the dummy has no slots
        }
        setCurrentItemOrArmor(0, null);
        resetStats();
    }

    public void setPlacer(EntityPlayer p) {
        placer = p.getUniqueID();
    }

    void resetStats() {
        hits.clear();
        hurtResistantTime = 0;
        lastDamage = 0;
        dataWatcher.updateObject(DW_LAST, Float.valueOf(0));
        dataWatcher.updateObject(DW_DPS, Float.valueOf(0));
    }

    private DummyGhost ghost() {
        if (ghost == null && worldObj instanceof WorldServer) {
            ghost = DummyGhost.build((WorldServer) worldObj, UUID.nameUUIDFromBytes(("dre_dummy" + getUniqueID()).getBytes()), snap, dimension, posX, posY, posZ);
            if (ghost != null) ghost.owner = this;
        }
        if (ghost != null) {
            ghost.setPosition(posX, posY, posZ);
            ghost.dimension = dimension;
        }
        return ghost;
    }

    /** State of the hitting player: after a hit on the dummy it returns to what it was. */
    static final class Guard {
        final EntityPlayer p;
        final float health, absorb, sat, arcana;
        final int food, delay;

        Guard(EntityPlayer p) {
            this.p = p;
            health = p.getHealth();
            absorb = p.getAbsorptionAmount();
            food = p.getFoodStats().getFoodLevel();
            sat = p.getFoodStats().getSaturationLevel();
            net.divinerpg.utils.events.ArcanaHelper h = net.divinerpg.utils.events.ArcanaHelper.getProperties(p);
            arcana = h == null ? 0 : h.getBarValue();
            delay = h == null ? 0 : h.regenDelay;
        }

        void restore() {
            if (p.isDead) return;
            p.setHealth(health);
            p.setAbsorptionAmount(absorb);
            try { // FoodStats has no server setters
                cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.util.FoodStats.class, p.getFoodStats(), food, "foodLevel", "field_75127_a");
                cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.util.FoodStats.class, p.getFoodStats(), sat, "foodSaturationLevel", "field_75125_b");
            } catch (Throwable ignored) {}
            net.divinerpg.utils.events.ArcanaHelper h = net.divinerpg.utils.events.ArcanaHelper.getProperties(p);
            if (h != null && h.getBarValue() > arcana) h.setBarValue(arcana); // the arcana spent on a shot stays spent, the gain does not
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource s, float amount) {
        if (worldObj.isRemote || isDead || removed) return false;
        if (s == DamageSource.outOfWorld) {
            setDead();
            return true;
        }
        Entity attacker = s.getEntity();
        // fire, lava, cactus, falling do not count; explosions (bombs, meteors, mod cannons: an explosion in 1.7.10 has no attacker) and
        // other weapon damage without an attacker count
        if (attacker == null && !s.isExplosion() && ENVIRONMENT.contains(s.damageType)) return false;
        // sneaked and hit with a hand - take it: decided by the player's own hit ({@link Pickup}), not by damage - otherwise the dummy was picked up
        // a hit by a pet (or a ghost) "on behalf of" the sneaking owner
        if (attacker instanceof EntityPlayer && ((EntityPlayer) attacker).isSneaking() && s.getSourceOfDamage() == attacker) return false;
        DummyGhost g = ghost();
        if (g == null) return false;
        // invulnerability after a hit is like a player's: within half a second only how much stronger the new hit is counts
        float counted = amount;
        if (hurtResistantTime > maxHurtResistantTime / 2F) {
            if (amount <= lastDamage) return false;
            counted = amount - lastDamage;
        } else {
            hurtResistantTime = maxHurtResistantTime;
        }
        lastDamage = amount;
        Guard guard = attacker instanceof EntityPlayer ? new Guard((EntityPlayer) attacker) : null;
        float dealt;
        try {
            if (arcana < 0) arcana = snap.getFloat("Arcana");
            dealt = g.measure(s, counted, arcana, arcanaDelay);
            net.divinerpg.utils.events.ArcanaHelper gh = net.divinerpg.utils.events.ArcanaHelper.getProperties(g);
            if (gh != null) {
                arcana = gh.getBarValue();
                arcanaDelay = g.arcanaDelay;
            }
        } finally {
            if (guard != null) guard.restore();
        }
        record(dealt);
        dataWatcher.updateObject(DW_ARC, Float.valueOf(arcana));
        dataWatcher.updateObject(DW_ARC_SPENT, Float.valueOf(g.arcanaSpent));
        if (attacker instanceof EntityPlayer) callPets((EntityPlayer) attacker);
        worldObj.setEntityState(this, (byte) 2); // flinch
        playSound("dig.cloth", 0.8F, 1.0F);
        return true;
    }

    void record(float dealt) {
        long now = worldObj.getTotalWorldTime();
        hits.addLast(new long[] { now, Float.floatToIntBits(dealt) });
        while (!hits.isEmpty() && now - hits.peekFirst()[0] > WINDOW) hits.removeFirst();
        float sum = 0;
        for (long[] h : hits) sum += Float.intBitsToFloat((int) h[1]);
        long span = Math.max(20, now - hits.peekFirst()[0] + 1);
        dataWatcher.updateObject(DW_LAST, Float.valueOf(dealt));
        dataWatcher.updateObject(DW_DPS, Float.valueOf(sum * 20F / span));
    }

    /** The player's pets nearby (not sitting) hit the dummy while the owner hits. */
    void callPets(EntityPlayer p) {
        ownerHit = worldObj.getTotalWorldTime();
        for (Object o : worldObj.getEntitiesWithinAABB(net.minecraft.entity.passive.EntityTameable.class, boundingBox.expand(24, 12, 24))) {
            net.minecraft.entity.passive.EntityTameable t = (net.minecraft.entity.passive.EntityTameable) o;
            if (!t.isTamed() || t.isSitting() || !owns(p, t)) continue;
            t.setAttackTarget(this); // new AI
            t.setTarget(this); // old AI
            pets.add(t.getEntityId());
        }
        com.divinerpgextended.magic.ArcaneSummons.mark(p, this); // ghosts of the ghost bone (a hit on the dummy does not go through the attack event)
        com.divinerpgextended.magic.AnchorSummons.mark(p, this); // slimes, crabs, the eye, sharks
    }

    /** Divine writes the player name into the owner field, vanilla writes the UUID. */
    private static boolean owns(EntityPlayer p, net.minecraft.entity.passive.EntityTameable t) {
        String n = t.func_152113_b();
        return n != null && (n.equals(p.getUniqueID().toString()) || n.equals(p.getCommandSenderName()) || n.equals(p.getDisplayName()));
    }

    /** The owner stopped hitting - the pets fall behind. */
    private void releasePets() {
        for (Integer id : pets) {
            Entity e = worldObj.getEntityByID(id);
            if (!(e instanceof net.minecraft.entity.EntityCreature)) continue;
            net.minecraft.entity.EntityCreature c = (net.minecraft.entity.EntityCreature) e;
            if (c.getAttackTarget() == this) c.setAttackTarget(null);
            if (c.getEntityToAttack() == this) c.setTarget(null);
        }
        pets.clear();
    }

    @Override
    public void setDead() {
        super.setDead();
        if (!worldObj.isRemote) {
            releasePets();
            DummyLimit.get().remove(getUniqueID());
        }
    }

    /** Remove the dummy: the item is returned once. */
    void pickUp(EntityPlayer p) {
        if (removed || isDead) return;
        removed = true;
        setDead();
        ItemStack it = new ItemStack(com.divinerpgextended.registry.ModItems.trainingDummy);
        if (!p.inventory.addItemStackToInventory(it)) entityDropItem(it, 0.5F);
    }

    /** Pick up the dummy: the player sneaked and hit it himself (the player's hit event, not damage). */
    public static class Pickup {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onAttack(net.minecraftforge.event.entity.player.AttackEntityEvent e) {
            if (!(e.target instanceof EntityTrainingDummy) || e.entityPlayer.worldObj.isRemote || !e.entityPlayer.isSneaking()) return;
            EntityTrainingDummy d = (EntityTrainingDummy) e.target;
            e.setCanceled(true);
            if (d.isDead || d.removed) return;
            if (d.placer != null && d.placer.equals(e.entityPlayer.getUniqueID())) d.pickUp(e.entityPlayer);
            else e.entityPlayer.addChatMessage(new ChatComponentTranslation("dre.dummy.not_yours"));
        }
    }

    @Override
    public boolean interact(EntityPlayer p) {
        if (worldObj.isRemote) return true;
        if (p.isSneaking()) {
            copy(p);
            p.addChatMessage(new ChatComponentTranslation("dre.dummy.copied"));
        } else {
            resetStats();
        }
        return true;
    }

    // motionless, does not die, drops nothing

    @Override
    public void onLivingUpdate() {
        motionX = motionZ = 0;
        if (motionY > 0) motionY = 0;
        super.onLivingUpdate();
        if (!worldObj.isRemote && getHealth() < getMaxHealth()) setHealth(getMaxHealth());
        if (!worldObj.isRemote && arcana >= 0 && arcana < ARCANA_MAX) {
            if (arcanaDelay > 0) arcanaDelay--;
            else arcana = Math.min(ARCANA_MAX, arcana + ARCANA_REGEN);
            if (ticksExisted % 5 == 0 || arcana >= ARCANA_MAX) dataWatcher.updateObject(DW_ARC, Float.valueOf(arcana));
        }
        if (!worldObj.isRemote && !hits.isEmpty() && worldObj.getTotalWorldTime() - hits.peekLast()[0] > WINDOW) resetStats();
        if (!worldObj.isRemote && !pets.isEmpty() && worldObj.getTotalWorldTime() - ownerHit > WINDOW) releasePets();
        if (!worldObj.isRemote && !listed && !isDead) { // those placed before 0.99.33 are counted on the first tick
            listed = true;
            DummyLimit.get().add(getUniqueID());
        }
    }

    @Override
    protected boolean isAIEnabled() {
        return false;
    }

    /** The old AI "jumps" in water (floats up), and the jump flag is not reset afterwards - and on land the dummy keeps jumping. */
    @Override
    protected void updateEntityActionState() {
        super.updateEntityActionState();
        isJumping = false;
    }

    // water does not affect the dummy: the current does not carry it, it does not drown (drowning damage would throw off the hit count)

    @Override
    public boolean isPushedByWater() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public void addVelocity(double x, double y, double z) {}

    @Override
    public void knockBack(Entity e, float f, double x, double z) {}

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected void collideWithEntity(Entity e) {}

    @Override
    protected boolean canDespawn() {
        return false;
    }

    /** Even /kill: it just disappears - without death events, loot, experience and counted kills. */
    @Override
    public void onDeath(DamageSource s) {
        removed = true;
        setDead();
    }

    @Override
    protected void dropEquipment(boolean hit, int looting) {}

    @Override
    protected void dropFewItems(boolean hit, int looting) {}

    @Override
    protected int getExperiencePoints(EntityPlayer p) {
        return 0;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    protected String getLivingSound() {
        return null;
    }

    @Override
    protected String getHurtSound() {
        return null;
    }

    @Override
    protected String getDeathSound() {
        return null;
    }

    @Override
    public ItemStack getPickedResult(net.minecraft.util.MovingObjectPosition t) {
        return new ItemStack(com.divinerpgextended.registry.ModItems.trainingDummy);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound t) {
        super.writeEntityToNBT(t);
        t.setTag("DreSnapshot", snap);
        t.setFloat("DreArcana", arcana);
        t.setInteger("DreArcanaDelay", arcanaDelay);
        if (placer != null) t.setString("DrePlacer", placer.toString());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound t) {
        super.readEntityFromNBT(t);
        snap = t.getCompoundTag("DreSnapshot");
        arcana = t.hasKey("DreArcana") ? t.getFloat("DreArcana") : snap.getFloat("Arcana");
        arcanaDelay = t.getInteger("DreArcanaDelay");
        dataWatcher.updateObject(DW_ARC, Float.valueOf(arcana));
        dataWatcher.updateObject(DW_NAME, snap.getString("Name"));
        dataWatcher.updateObject(DW_UUID, snap.getString("UUID"));
        try {
            placer = t.hasKey("DrePlacer") ? UUID.fromString(t.getString("DrePlacer")) : null;
        } catch (IllegalArgumentException e) {
            placer = null;
        }
        ghost = null;
    }

    /** For snapshot checking (selftest). */
    NBTTagList snapshotArmor() {
        return snap.getTagList("Armor", 10);
    }
}
