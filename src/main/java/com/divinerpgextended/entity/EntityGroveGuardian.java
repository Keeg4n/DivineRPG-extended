package com.divinerpgextended.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.Config;
import com.divinerpgextended.boss.BossDamage;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.world.CadillionWorld;

/**
 * Страж заповедной рощи сумеречного мира (кроме Эдема - там Великий кадиллион): общее у всех - сила по миру (как у
 * кадиллиона этого мира), бой только с поляны рощи, трофеи (шестерня времени, сердце стража, души) и подручные.
 */
public abstract class EntityGroveGuardian extends EntityMob implements IBossDisplayData {

    public static final String ADD_TAG = "dreGuardianAdd";

    public EntityGroveGuardian(World world) {
        super(world);
        experienceValue = 250;
        isImmuneToFire = true;
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 20.0F));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, false));
    }

    /** Мир этого стража. */
    public abstract CadillionWorld world();

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        int v = world().ordinal();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(Config.cadillionHealth[Math.min(v, Config.cadillionHealth.length - 1)]);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(BossDamage.cadillionRawDamage(v));
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(40.0D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0D);
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
    protected void fall(float distance) {}

    @Override
    public String getCommandSenderName() {
        return StatCollector.translateToLocal("dre.entity.guardian." + world().key());
    }

    /** Атакующий стоит вне поляны рощи (стреляет издалека) - урона нет. */
    public boolean outOfLair(Entity attacker) {
        int[] home = com.divinerpgextended.boss.LairSummons.home(this);
        if (home == null || !(attacker instanceof EntityPlayer)) return false;
        double dx = attacker.posX - home[0] - 0.5, dz = attacker.posZ - home[2] - 0.5;
        return dx * dx + dz * dz > (home[3] + 4) * (home[3] + 4) || attacker.posY < home[1] - 4;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (outOfLair(src.getEntity())) {
            EntityPlayer p = (EntityPlayer) src.getEntity();
            if (p.ticksExisted % 20 == 0 || src.isProjectile()) p.addChatMessage(new net.minecraft.util.ChatComponentTranslation("dre.chat.cadillion_out_of_lair"));
            return false;
        }
        return super.attackEntityFrom(src, amount);
    }

    /** Свои живые подручные (по метке). */
    public int addsAlive() {
        int n = 0;
        for (Object o : worldObj.getEntitiesWithinAABB(EntityLiving.class, boundingBox.expand(40, 24, 40))) {
            if (((Entity) o).getEntityData().getInteger(ADD_TAG) == getEntityId()) n++;
        }
        return n;
    }

    /** Позвать подручного рядом с собой (с меткой), null - не нашлось места. */
    public EntityLiving summon(Class<? extends EntityLiving> kind, double radius) {
        double a = rand.nextDouble() * Math.PI * 2;
        return summonAt(kind, posX + Math.cos(a) * radius, posY + 0.5, posZ + Math.sin(a) * radius);
    }

    /** Позвать подручного в точку (с меткой), null - не нашлось места. */
    public EntityLiving summonAt(Class<? extends EntityLiving> kind, double x, double y, double z) {
        try {
            EntityLiving m = kind.getConstructor(World.class).newInstance(worldObj);
            m.setLocationAndAngles(x, y, z, rand.nextFloat() * 360, 0);
            if (!worldObj.getCollidingBoundingBoxes(m, m.boundingBox).isEmpty()) return null;
            m.setAttackTarget(getAttackTarget());
            m.getEntityData().setInteger(ADD_TAG, getEntityId());
            com.divinerpgextended.boss.BossMinions.mark(m, this);
            worldObj.spawnEntityInWorld(m);
            ((WorldServer) worldObj).func_147487_a("portal", m.posX, m.posY + 1, m.posZ, 20, 0.5, 0.8, 0.5, 0.1);
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        entityDropItem(new ItemStack(ModItems.timeGear, 1, world().ordinal()), 0.5F);
        entityDropItem(new ItemStack(ModItems.guardianHeart, 1, world().ordinal()), 0.5F); // камни врат в следующий мир
        entityDropItem(new ItemStack(world().soul(), 4 + rand.nextInt(5 + looting)), 0.5F);
    }

    /** Страж нужного мира для алтаря рощи и Зова: Эдем - Великий кадиллион, Дикий лес - Древний голем, Апалачия - Король-воин, Скайтерн - Повелитель ветров. */
    public static EntityLiving create(World world, CadillionWorld w) {
        if (w == CadillionWorld.WILDWOOD) return new EntityAncientGolem(world);
        if (w == CadillionWorld.APALACHIA) return new EntityWarriorKing(world);
        if (w == CadillionWorld.SKYTHERN) return new EntityWindLord(world);
        EntityGreatCadillion c = new EntityGreatCadillion(world);
        c.setVariant(w.ordinal());
        return c;
    }
}
