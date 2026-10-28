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
 * Манекен для битья. Копия игрока, который его поставил (или сделал копию себя: присесть + ПКМ): скин, надетая броня,
 * аксессуары, эффекты зелий и аркана на момент копии. Удар по нему считается так, как если бы били этого игрока
 * ({@link DummyGhost}), над головой - урон последнего удара и урон в секунду. В руках ничего нет.
 * <p>
 * Без дюпов и выгоды: снять с него ничего нельзя (броня - только вид), он не умирает и ничего не роняет (ни вещей, ни
 * опыта); здоровье, аркана, сытость и поглощение бьющего игрока после удара возвращаются как были (никакой вампиризм не
 * лечит о манекен). Убрать может только поставивший: присесть и ударить - предмет возвращается один раз.
 */
public class EntityTrainingDummy extends EntityLiving {

    private static final int DW_NAME = 20, DW_LAST = 21, DW_DPS = 22, DW_UUID = 23;
    /** Урон от окружения и эффектов: манекен его не показывает. */
    private static final java.util.Set<String> ENVIRONMENT = new java.util.HashSet<String>(java.util.Arrays.asList("inFire", "onFire", "lava", "cactus",
        "fall", "drown", "inWall", "starve", "wither", "magic", "anvil", "fallingBlock"));
    /** Окно для урона в секунду. */
    static final int WINDOW = 100;

    private UUID placer;
    private NBTTagCompound snap = new NBTTagCompound();
    private DummyGhost ghost;
    private final ArrayDeque<long[]> hits = new ArrayDeque<long[]>();
    private boolean removed;

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
    }

    /** UUID того, чья это копия (для скина и модели как у живого игрока); пусто у манекенов до 0.99.30. */
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

    /** Стать копией игрока (снимок надетого, данных, эффектов и арканы). */
    public void copy(EntityPlayer p) {
        snap = DummyGhost.snapshot(p);
        ghost = null;
        dataWatcher.updateObject(DW_NAME, p.getCommandSenderName());
        dataWatcher.updateObject(DW_UUID, p.getUniqueID().toString());
        for (int i = 0; i < 4; i++) {
            ItemStack a = p.inventory.armorInventory[i];
            setCurrentItemOrArmor(i + 1, a == null ? null : a.copy()); // только вид: слотов у манекена нет
        }
        setCurrentItemOrArmor(0, null);
        resetStats();
    }

    public void setPlacer(EntityPlayer p) {
        placer = p.getUniqueID();
    }

    void resetStats() {
        hits.clear();
        dataWatcher.updateObject(DW_LAST, Float.valueOf(0));
        dataWatcher.updateObject(DW_DPS, Float.valueOf(0));
    }

    private DummyGhost ghost() {
        if (ghost == null && worldObj instanceof WorldServer) {
            ghost = DummyGhost.build((WorldServer) worldObj, UUID.nameUUIDFromBytes(("dre_dummy" + getUniqueID()).getBytes()), snap, dimension, posX, posY, posZ);
        }
        if (ghost != null) {
            ghost.setPosition(posX, posY, posZ);
            ghost.dimension = dimension;
        }
        return ghost;
    }

    /** Состояние бьющего игрока: после удара по манекену возвращается как было. */
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
            try { // у FoodStats нет серверных сеттеров
                cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.util.FoodStats.class, p.getFoodStats(), food, "foodLevel", "field_75127_a");
                cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.util.FoodStats.class, p.getFoodStats(), sat, "foodSaturationLevel", "field_75125_b");
            } catch (Throwable ignored) {}
            net.divinerpg.utils.events.ArcanaHelper h = net.divinerpg.utils.events.ArcanaHelper.getProperties(p);
            if (h != null && h.getBarValue() > arcana) h.setBarValue(arcana); // трата арканы на выстрел остаётся, прибыль - нет
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
        // огонь, лава, кактус, падение - не в счёт; взрывы (бомбы, метеоры, пушки модов: у взрыва в 1.7.10 нет атакующего) и
        // прочий урон оружия без атакующего - в счёт
        if (attacker == null && !s.isExplosion() && ENVIRONMENT.contains(s.damageType)) return false;
        if (attacker instanceof EntityPlayer && s.getSourceOfDamage() == attacker && ((EntityPlayer) attacker).isSneaking()) {
            EntityPlayer p = (EntityPlayer) attacker;
            if (placer != null && placer.equals(p.getUniqueID())) pickUp(p);
            else p.addChatMessage(new ChatComponentTranslation("dre.dummy.not_yours"));
            return false;
        }
        DummyGhost g = ghost();
        if (g == null) return false;
        Guard guard = attacker instanceof EntityPlayer ? new Guard((EntityPlayer) attacker) : null;
        float dealt;
        try {
            dealt = g.measure(s, amount, snap);
        } finally {
            if (guard != null) guard.restore();
        }
        record(dealt);
        worldObj.setEntityState(this, (byte) 2); // вздрогнуть
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

    /** Убрать манекен: предмет возвращается один раз. */
    void pickUp(EntityPlayer p) {
        if (removed || isDead) return;
        removed = true;
        setDead();
        ItemStack it = new ItemStack(com.divinerpgextended.registry.ModItems.trainingDummy);
        if (!p.inventory.addItemStackToInventory(it)) entityDropItem(it, 0.5F);
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

    // неподвижный, не умирает, ничего не роняет

    @Override
    public void onLivingUpdate() {
        motionX = motionZ = 0;
        if (motionY > 0) motionY = 0;
        super.onLivingUpdate();
        if (!worldObj.isRemote && getHealth() < getMaxHealth()) setHealth(getMaxHealth());
        if (!worldObj.isRemote && !hits.isEmpty() && worldObj.getTotalWorldTime() - hits.peekLast()[0] > WINDOW) resetStats();
    }

    @Override
    protected boolean isAIEnabled() {
        return false;
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

    /** Даже /kill: просто исчезает - без событий смерти, добычи, опыта и засчитанных убийств. */
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
        if (placer != null) t.setString("DrePlacer", placer.toString());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound t) {
        super.readEntityFromNBT(t);
        snap = t.getCompoundTag("DreSnapshot");
        dataWatcher.updateObject(DW_NAME, snap.getString("Name"));
        dataWatcher.updateObject(DW_UUID, snap.getString("UUID"));
        try {
            placer = t.hasKey("DrePlacer") ? UUID.fromString(t.getString("DrePlacer")) : null;
        } catch (IllegalArgumentException e) {
            placer = null;
        }
        ghost = null;
    }

    /** Для проверки снимка (автотест). */
    NBTTagList snapshotArmor() {
        return snap.getTagList("Armor", 10);
    }
}
