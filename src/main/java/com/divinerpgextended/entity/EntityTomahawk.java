package com.divinerpgextended.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.divinerpgextended.item.ItemTomahawk;
import com.divinerpgextended.registry.ModItems;

/**
 * Брошенный томагавк ({@link ItemTomahawk}). Летит прямо до {@link #RANGE} блоков; в цели бьёт уроном удара и сворачивает к
 * ближайшей следующей (не дальше {@link #CHAIN} блоков), всего до {@link #TARGETS} целей; упёршись в блок, долетев или исчерпав цели -
 * возвращается к бросившему сквозь стены. Первая цель дальше {@link #STUN_RANGE} блоков от бросившего оглушается на
 * {@link #STUN_TICKS} тиков (замедление X и слабость). Игроков бьёт только при включённом PvP, питомцев бросившего не бьёт.
 * Не сохраняется: после перезагрузки мира исчезает, а метка броска на предмете снимается по времени.
 */
public class EntityTomahawk extends Entity {

    public static final float RANGE = 24F, CHAIN = 8F, STUN_RANGE = 8F, SPEED = 1.3F, BACK_SPEED = 1.5F;
    public static final int TARGETS = 3, STUN_TICKS = 50, LIFE = 200;

    private EntityPlayer owner;
    private final Set<Integer> hit = new HashSet<Integer>();
    private boolean back;
    private double flown;
    /** Для самопроверки: сколько целей задето, оглушена ли первая. */
    public int hits;
    public boolean stunned;

    public EntityTomahawk(World w) {
        super(w);
        setSize(0.5F, 0.5F);
    }

    public EntityTomahawk(World w, EntityPlayer p) {
        this(w);
        owner = p;
        Vec3 look = p.getLookVec();
        setPosition(p.posX + look.xCoord * 0.8, p.posY + p.getEyeHeight() - 0.2 + look.yCoord * 0.8, p.posZ + look.zCoord * 0.8);
        motionX = look.xCoord * SPEED;
        motionY = look.yCoord * SPEED;
        motionZ = look.zCoord * SPEED;
    }

    public EntityPlayer owner() {
        return owner;
    }

    @Override
    protected void entityInit() {}

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) {
            setPosition(posX + motionX, posY + motionY, posZ + motionZ);
            return;
        }
        if (owner == null || owner.isDead || owner.worldObj != worldObj || ticksExisted > LIFE) {
            finish();
            return;
        }
        if (back) {
            Vec3 to = Vec3.createVectorHelper(owner.posX - posX, owner.posY + owner.getEyeHeight() * 0.7 - posY, owner.posZ - posZ);
            double d = to.lengthVector();
            if (d < 1.6) {
                finish();
                return;
            }
            to = to.normalize();
            motionX = to.xCoord * BACK_SPEED;
            motionY = to.yCoord * BACK_SPEED;
            motionZ = to.zCoord * BACK_SPEED;
            setPosition(posX + motionX, posY + motionY, posZ + motionZ);
            return;
        }
        Vec3 from = Vec3.createVectorHelper(posX, posY, posZ), next = Vec3.createVectorHelper(posX + motionX, posY + motionY, posZ + motionZ);
        MovingObjectPosition block = worldObj.rayTraceBlocks(from, next);
        EntityLivingBase target = null;
        double best = Double.MAX_VALUE;
        AxisAlignedBB sweep = boundingBox.addCoord(motionX, motionY, motionZ).expand(0.6, 0.6, 0.6);
        for (Object o : worldObj.getEntitiesWithinAABBExcludingEntity(this, sweep)) {
            if (!(o instanceof EntityLivingBase) || !valid((EntityLivingBase) o)) continue;
            double d = ((Entity) o).getDistanceSqToEntity(this);
            if (d < best) {
                best = d;
                target = (EntityLivingBase) o;
            }
        }
        if (target != null) {
            strike(target);
            return;
        }
        if (block != null && block.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            back = true;
            return;
        }
        setPosition(posX + motionX, posY + motionY, posZ + motionZ);
        flown += Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        if (flown >= RANGE) back = true;
    }

    /** Цель годится: живая, не бросивший, ещё не задета, не его питомец, игрок - только при PvP. */
    private boolean valid(EntityLivingBase e) {
        if (e == owner || e.isDead || e.getHealth() <= 0 || hit.contains(e.getEntityId())) return false;
        if (e instanceof EntityTameable && ((EntityTameable) e).getOwner() == owner) return false;
        if (e instanceof EntityPlayer) return net.minecraft.server.MinecraftServer.getServer() != null && net.minecraft.server.MinecraftServer.getServer().isPVPEnabled();
        return true;
    }

    private void strike(EntityLivingBase t) {
        boolean first = hits == 0;
        boolean far = first && owner.getDistanceToEntity(t) > STUN_RANGE;
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causeThrownDamage(this, owner), ItemTomahawk.throwDamage());
        hit.add(t.getEntityId());
        hits++;
        if (far) {
            t.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, STUN_TICKS, 9));
            t.addPotionEffect(new PotionEffect(Potion.weakness.id, STUN_TICKS, 4));
            stunned = true;
        }
        worldObj.playSoundAtEntity(t, "random.break", 0.6F, 1.6F);
        if (hits >= TARGETS) {
            back = true;
            return;
        }
        // к ближайшей следующей цели
        EntityLivingBase next = null;
        double best = CHAIN * CHAIN;
        List<?> near = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, t.boundingBox.expand(CHAIN, CHAIN / 2, CHAIN));
        for (Object o : near) {
            EntityLivingBase e = (EntityLivingBase) o;
            if (!valid(e) || !(e instanceof net.minecraft.entity.monster.IMob) && !(e instanceof net.minecraft.entity.EntityLiving && ((net.minecraft.entity.EntityLiving) e).getAttackTarget() == owner))
                continue;
            double d = e.getDistanceSqToEntity(t);
            if (d < best && t.canEntityBeSeen(e)) {
                best = d;
                next = e;
            }
        }
        if (next == null) {
            back = true;
            return;
        }
        Vec3 to = Vec3.createVectorHelper(next.posX - posX, next.posY + next.height / 2 - posY, next.posZ - posZ).normalize();
        motionX = to.xCoord * SPEED;
        motionY = to.yCoord * SPEED;
        motionZ = to.zCoord * SPEED;
        flown = 0;
    }

    /** Вернулся (или пропал): снять метку броска с томагавка у бросившего. */
    private void finish() {
        setDead();
        if (owner == null) return;
        for (ItemStack s : owner.inventory.mainInventory)
            if (s != null && s.getItem() == ModItems.tomahawk && s.hasTagCompound() && s.getTagCompound().hasKey(ItemTomahawk.OUT)) ItemTomahawk.setOut(s, 0);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound t) {
        setDead(); // не переживает перезагрузку
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound t) {}
}
