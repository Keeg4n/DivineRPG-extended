package com.divinerpgextended.entity;

import java.util.List;

import net.divinerpg.utils.items.VanillaItemsOther;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.divinerpgextended.Config;
import com.divinerpgextended.registry.ModItems;

/**
 * Карцинус, Владыка глубин. Панцирь: обычное оружие наносит только часть урона, крабовое - полный. Фаза 2 (меньше
 * половины здоровья): Короли крабов и уход в панцирь, который раскалывает удар крабовым молотом. Фаза 3 (меньше 20%):
 * затягивает игроков и слепит песком.
 */
public class EntityCarcinus extends EntityMob implements IBossDisplayData {

    private static final int DW_SHELL = 20, DW_VORTEX = 21;
    private static final int VORTEX_CHARGE = 30;
    private static final double VORTEX_RADIUS = 6.0D;
    private int crabTimer = 120, kingTimer = 200, shellTimer = 260, rageTimer = 100, shellLeft;
    private int vortexTimer = 160, vortexLeft;

    public EntityCarcinus(World world) {
        super(world);
        setSize(3.0F, 2.2F);
        experienceValue = 150;
        tasks.addTask(2, new EntityAIAttackOnCollide(this, EntityPlayer.class, 1.0D, false));
        tasks.addTask(5, new EntityAIWander(this, 0.6D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 16.0F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_SHELL, Byte.valueOf((byte) 0));
        dataWatcher.addObject(DW_VORTEX, Byte.valueOf((byte) 0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(Config.carcinusHealth);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(Config.carcinusDamage);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.27D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(32.0D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0D);
    }

    /** Карцинус закручивает песчаный вихрь: стоит на месте, через полторы секунды бьёт всех вокруг. */
    public boolean chargingVortex() {
        return dataWatcher.getWatchableObjectByte(DW_VORTEX) != 0;
    }

    public boolean inShell() {
        return dataWatcher.getWatchableObjectByte(DW_SHELL) != 0;
    }

    private void setShell(boolean on) {
        dataWatcher.updateObject(DW_SHELL, Byte.valueOf((byte) (on ? 1 : 0)));
    }

    /** Крабовое оружие из Divine: им панцирь пробивается полностью. */
    public static boolean isCrabWeapon(Item item) {
        return item == VanillaItemsWeapons.crabclawMaul || item == VanillaItemsWeapons.dualClaw
            || item == VanillaItemsWeapons.crabAnchor || item == VanillaItemsWeapons.crabclawCannon;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        Item weapon = null;
        if (attacker instanceof EntityPlayer && ((EntityPlayer) attacker).getHeldItem() != null) {
            weapon = ((EntityPlayer) attacker).getHeldItem()
                .getItem();
        }
        boolean crab = isCrabWeapon(weapon);
        if (inShell()) {
            if (weapon == VanillaItemsWeapons.crabclawMaul) {
                setShell(false); // молот раскалывает панцирь
                shellLeft = 0;
                worldObj.playSoundAtEntity(this, "random.break", 2.0F, 0.6F);
                tell("dre.chat.carcinus_shell_broken");
                amount *= 1.5F;
            } else {
                worldObj.playSoundAtEntity(this, "random.anvil_land", 0.6F, 1.5F);
                return false;
            }
        } else if (!crab && !source.isUnblockable()) {
            amount *= (float) Config.carcinusNonCrabDamageFactor;
        }
        return super.attackEntityFrom(source, amount);
    }

    @Override
    public boolean isAIEnabled() {
        return true;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public String getCommandSenderName() {
        return StatCollector.translateToLocal("dre.entity.carcinus");
    }

    @Override
    protected String getLivingSound() {
        return "mob.silverfish.say";
    }

    @Override
    protected String getHurtSound() {
        return "mob.silverfish.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.silverfish.kill";
    }

    @Override
    protected float getSoundPitch() {
        return 0.4F;
    }

    private void spawnMinion(EntityLiving minion) {
        minion.getEntityData().setBoolean(EntityLairCrab.CARCINUS_ADD, true); // в бою - без клешней и опыта
        minion.setLocationAndAngles(posX + (rand.nextDouble() - 0.5) * 6, posY, posZ + (rand.nextDouble() - 0.5) * 6,
            rand.nextFloat() * 360, 0);
        worldObj.spawnEntityInWorld(minion);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote) {
            if (inShell()) {
                for (int i = 0; i < 3; i++) {
                    worldObj.spawnParticle("crit", posX + (rand.nextDouble() - 0.5) * width, posY + rand.nextDouble() * height,
                        posZ + (rand.nextDouble() - 0.5) * width, 0, 0.1, 0);
                }
            }
            if (chargingVortex()) {
                // песок крутится по всему кругу поражения: видно, куда не надо стоять
                for (int i = 0; i < 20; i++) {
                    double a = rand.nextDouble() * Math.PI * 2, r = Math.sqrt(rand.nextDouble()) * VORTEX_RADIUS;
                    worldObj.spawnParticle("blockcrack_12_0", posX + Math.cos(a) * r, posY + 0.2 + rand.nextDouble(),
                        posZ + Math.sin(a) * r, -Math.sin(a) * 0.4, 0.25, Math.cos(a) * 0.4);
                }
            }
            return;
        }
        if (shellLeft > 0 && --shellLeft == 0) setShell(false);
        if (vortexLeft > 0) {
            getNavigator().clearPathEntity();
            motionX = motionZ = 0;
            if (vortexLeft % 10 == 0) worldObj.playSoundAtEntity(this, "dig.sand", 2.0F, 0.5F + vortexLeft / 60.0F);
            if (--vortexLeft == 0) releaseVortex();
            return;
        }
        // фазы идут, пока рядом игрок (в том числе в творческом, когда цели у краба нет)
        if (getAttackTarget() == null && worldObj.getClosestPlayerToEntity(this, 24) == null) return;
        float hp = getHealth() / getMaxHealth();

        if (--vortexTimer <= 0 && !inShell()) {
            vortexLeft = VORTEX_CHARGE;
            vortexTimer = hp <= 0.5F ? 180 : 240;
            dataWatcher.updateObject(DW_VORTEX, Byte.valueOf((byte) 1));
            tell("dre.chat.carcinus_vortex");
        }
        if (--crabTimer <= 0) {
            for (int i = 0; i < 2; i++) spawnMinion(new EntityLairCrab(worldObj));
            crabTimer = 200;
        }
        if (hp <= 0.5F) {
            if (--kingTimer <= 0) {
                spawnMinion(new EntityLairCrab.King(worldObj));
                kingTimer = 300;
            }
            if (--shellTimer <= 0 && !inShell()) {
                setShell(true);
                shellLeft = 100;
                shellTimer = 300;
                worldObj.playSoundAtEntity(this, "random.door_close", 2.0F, 0.5F);
                tell("dre.chat.carcinus_shell");
            }
        }
        if (hp <= 0.2F && --rageTimer <= 0) {
            @SuppressWarnings("unchecked")
            List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(16, 6, 16));
            for (EntityPlayer p : near) {
                p.motionX += (posX - p.posX) * 0.12D;
                p.motionZ += (posZ - p.posZ) * 0.12D;
                p.velocityChanged = true;
                p.addPotionEffect(new PotionEffect(Potion.blindness.id, 60, 0));
            }
            worldObj.playSoundAtEntity(this, "dig.sand", 2.0F, 0.5F);
            if (!near.isEmpty()) tell("dre.chat.carcinus_pull");
            rageTimer = 160;
        }
    }

    /** Удар вихря: урон сквозь любую броню (и водную тоже) и замедление всем, кто не успел отбежать. */
    private void releaseVortex() {
        dataWatcher.updateObject(DW_VORTEX, Byte.valueOf((byte) 0));
        worldObj.playSoundAtEntity(this, "random.explode", 1.5F, 0.6F);
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class,
            boundingBox.expand(VORTEX_RADIUS, 4, VORTEX_RADIUS));
        for (EntityPlayer p : near) {
            double dx = p.posX - posX, dz = p.posZ - posZ;
            if (dx * dx + dz * dz > VORTEX_RADIUS * VORTEX_RADIUS) continue;
            p.attackEntityFrom(new EntityDamageSource("dre.sandVortex", this).setDamageBypassesArmor(),
                (float) Config.carcinusVortexDamage);
            p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 1));
        }
    }

    /** Подсказка всем игрокам рядом с боем. */
    private void tell(String key) {
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(24, 12, 24));
        for (EntityPlayer p : near) p.addChatMessage(new ChatComponentTranslation(key));
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        entityDropItem(new ItemStack(ModItems.deepShell), 0.5F);
        entityDropItem(new ItemStack(VanillaItemsOther.crabClaw, 4 + rand.nextInt(5 + looting)), 0.5F);
        entityDropItem(new ItemStack(VanillaItemsOther.aquaticPellets, 8 + rand.nextInt(9 + looting)), 0.5F);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setBoolean("Shell", inShell());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        setShell(false);
    }
}
