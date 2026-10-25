package com.divinerpgextended.fishing;

import io.netty.buffer.ByteBuf;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Поплавок рыбалки мода. Повторяет ванильный (полёт, покачивание, поклёвка, леска рисуется ванильным RenderFish), но:
 * сила рыбалки (удочка + приманка) сокращает ожидание и решает улов, улов берётся из таблиц зоны, умеет лаву (с
 * огненной приманкой или лавовой удочкой), тратит приманку из инвентаря. Работает с любой удочкой, включая ванильную.
 */
public class EntityDreHook extends EntityFishHook implements IEntityAdditionalSpawnData {

    private Rod rod = Rod.VANILLA;
    /** Огненная снасть: лавовая удочка или огненная приманка первой в инвентаре. Вода работает всегда. */
    private boolean lavaOk;
    /** Сейчас поплавок в лаве (и снасть это позволяет). */
    private boolean lava;

    private int xTile = -1, yTile = -1, zTile = -1;
    private Block inTile;
    private boolean inGround;
    private int ticksInGround, ticksInAir, ticksCatchable, ticksCaughtDelay, ticksCatchableDelay;
    private float fishApproachAngle;
    private int interpSteps;
    private double interpX, interpY, interpZ, interpYaw, interpPitch;
    @SideOnly(Side.CLIENT)
    private double velX, velY, velZ;

    public Rod rod() {
        return rod;
    }

    public EntityDreHook(World w) {
        super(w);
    }

    public EntityDreHook(World w, EntityPlayer angler, Rod rod) {
        super(w, angler);
        this.rod = rod;
        lavaOk = rod.lava() || Bait.slotOf(angler, Bait.FIRESTOCK) >= 0;
        isImmuneToFire = lavaOk;
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        buf.writeInt(field_146042_b == null ? -1 : field_146042_b.getEntityId());
        buf.writeByte(rod.ordinal());
        buf.writeBoolean(lavaOk);
    }

    @Override
    public void readSpawnData(ByteBuf buf) {
        Entity e = worldObj.getEntityByID(buf.readInt());
        rod = Rod.values()[buf.readByte()];
        lavaOk = buf.readBoolean();
        isImmuneToFire = lavaOk;
        if (e instanceof EntityPlayer) {
            field_146042_b = (EntityPlayer) e;
            field_146042_b.fishEntity = this;
        }
    }

    private Material liquid() {
        return lava ? Material.lava : Material.water;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int steps) {
        interpX = x;
        interpY = y;
        interpZ = z;
        interpYaw = yaw;
        interpPitch = pitch;
        interpSteps = steps;
        motionX = velX;
        motionY = velY;
        motionZ = velZ;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setVelocity(double x, double y, double z) {
        velX = motionX = x;
        velY = motionY = y;
        velZ = motionZ = z;
    }

    @Override
    public void onUpdate() {
        onEntityUpdate(); // базовое обновление Entity (ванильный onUpdate поплавка не вызываем)
        if (interpSteps > 0) {
            double nx = posX + (interpX - posX) / interpSteps, ny = posY + (interpY - posY) / interpSteps,
                nz = posZ + (interpZ - posZ) / interpSteps;
            rotationYaw = (float) (rotationYaw + MathHelper.wrapAngleTo180_double(interpYaw - rotationYaw) / interpSteps);
            rotationPitch = (float) (rotationPitch + (interpPitch - rotationPitch) / interpSteps);
            interpSteps--;
            setPosition(nx, ny, nz);
            setRotation(rotationYaw, rotationPitch);
            return;
        }
        if (!worldObj.isRemote) {
            EntityPlayer p = field_146042_b;
            ItemStack held = p == null ? null : p.getCurrentEquippedItem();
            if (p == null || p.isDead || !p.isEntityAlive() || held == null || !(held.getItem() instanceof ItemFishingRod)
                || getDistanceSqToEntity(p) > 1024.0) {
                setDead();
                return;
            }
            if (field_146043_c != null) {
                if (!field_146043_c.isDead) {
                    posX = field_146043_c.posX;
                    posY = field_146043_c.boundingBox.minY + field_146043_c.height * 0.8;
                    posZ = field_146043_c.posZ;
                    return;
                }
                field_146043_c = null;
            }
            // в лаве без огненной снасти поплавок сгорает
            if (!lavaOk && worldObj.isAABBInMaterial(boundingBox, Material.lava)) {
                playSound("random.fizz", 0.5F, 1.5F);
                setDead();
                return;
            }
        }
        if (field_146044_a > 0) field_146044_a--;
        if (inGround) {
            if (worldObj.getBlock(xTile, yTile, zTile) == inTile) {
                if (++ticksInGround == 1200) setDead();
                return;
            }
            inGround = false;
            motionX *= rand.nextFloat() * 0.2F;
            motionY *= rand.nextFloat() * 0.2F;
            motionZ *= rand.nextFloat() * 0.2F;
            ticksInGround = 0;
            ticksInAir = 0;
        } else {
            ticksInAir++;
        }
        Vec3 from = Vec3.createVectorHelper(posX, posY, posZ), to = Vec3.createVectorHelper(posX + motionX, posY + motionY,
            posZ + motionZ);
        MovingObjectPosition hit = worldObj.rayTraceBlocks(from, to);
        from = Vec3.createVectorHelper(posX, posY, posZ);
        to = Vec3.createVectorHelper(posX + motionX, posY + motionY, posZ + motionZ);
        if (hit != null) to = Vec3.createVectorHelper(hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord);
        Entity target = null;
        @SuppressWarnings("unchecked")
        List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox.addCoord(motionX, motionY, motionZ)
            .expand(1.0, 1.0, 1.0));
        double best = 0;
        for (Entity e : list) {
            if (!e.canBeCollidedWith() || e == field_146042_b && ticksInAir < 5) continue;
            AxisAlignedBB bb = e.boundingBox.expand(0.3, 0.3, 0.3);
            MovingObjectPosition m = bb.calculateIntercept(from, to);
            if (m == null) continue;
            double d = from.distanceTo(m.hitVec);
            if (d < best || best == 0) {
                target = e;
                best = d;
            }
        }
        if (target != null) hit = new MovingObjectPosition(target);
        if (hit != null) {
            if (hit.entityHit != null) {
                if (hit.entityHit.attackEntityFrom(DamageSource.causeThrownDamage(this, field_146042_b), 0.0F)) {
                    field_146043_c = hit.entityHit;
                }
            } else {
                inGround = true;
            }
        }
        if (inGround) return;
        moveEntity(motionX, motionY, motionZ);
        float h = MathHelper.sqrt_double(motionX * motionX + motionZ * motionZ);
        rotationYaw = (float) (Math.atan2(motionX, motionZ) * 180.0 / Math.PI);
        rotationPitch = (float) (Math.atan2(motionY, h) * 180.0 / Math.PI);
        while (rotationPitch - prevRotationPitch < -180.0F) prevRotationPitch -= 360.0F;
        while (rotationPitch - prevRotationPitch >= 180.0F) prevRotationPitch += 360.0F;
        while (rotationYaw - prevRotationYaw < -180.0F) prevRotationYaw -= 360.0F;
        while (rotationYaw - prevRotationYaw >= 180.0F) prevRotationYaw += 360.0F;
        rotationPitch = prevRotationPitch + (rotationPitch - prevRotationPitch) * 0.2F;
        rotationYaw = prevRotationYaw + (rotationYaw - prevRotationYaw) * 0.2F;
        float drag = onGround || isCollidedHorizontally ? 0.5F : 0.92F;
        lava = lavaOk && worldObj.isAABBInMaterial(boundingBox.expand(0, 0.2, 0), Material.lava);
        int parts = 5;
        double submerged = 0;
        for (int j = 0; j < parts; j++) {
            double y0 = boundingBox.minY + (boundingBox.maxY - boundingBox.minY) * j / parts;
            double y1 = boundingBox.minY + (boundingBox.maxY - boundingBox.minY) * (j + 1) / parts;
            AxisAlignedBB slice = AxisAlignedBB.getBoundingBox(boundingBox.minX, y0, boundingBox.minZ, boundingBox.maxX, y1,
                boundingBox.maxZ);
            if (worldObj.isAABBInMaterial(slice, liquid())) submerged += 1.0 / parts;
        }
        if (!worldObj.isRemote && submerged > 0) bite((WorldServer) worldObj);
        // вода мечтаний (висячее озеро Ветеи) перевёрнута: у её глади (снизу) поплавок держится, всплывая вниз
        if (!lava && dreamWaterAbove()) motionY -= 0.04F * (submerged * 2.0 - 1.0);
        else motionY += 0.04F * (submerged * 2.0 - 1.0);
        if (submerged > 0) {
            drag *= 0.9;
            motionY *= 0.8;
        }
        motionX *= drag;
        motionY *= drag;
        motionZ *= drag;
        setPosition(posX, posY, posZ);
    }

    /** Поплавок в воде мечтаний или вплотную под ней. */
    private boolean dreamWaterAbove() {
        int x = MathHelper.floor_double(posX), z = MathHelper.floor_double(posZ);
        for (int y = MathHelper.floor_double(boundingBox.minY); y <= MathHelper.floor_double(boundingBox.maxY + 0.6); y++)
            if (worldObj.getBlock(x, y, z) instanceof com.divinerpgextended.block.BlockDreamWater) return true;
        return false;
    }

    /** Сила рыбалки сейчас: удочка + приманка + снаряжение рыбака (+ бонусы особенностей). */
    private int power(Bait bait, Zone zone) {
        return FishingGear.power(worldObj, field_146042_b, rod, bait, zone);
    }

    private int baitSlot() {
        return field_146042_b == null ? -1 : Bait.find(field_146042_b, lava && !rod.lava());
    }

    private Bait currentBait() {
        int slot = baitSlot();
        return slot < 0 ? null : Bait.of(field_146042_b.inventory.mainInventory[slot].getItem());
    }

    /** Ванильная логика поклёвки, ожидание сокращено силой рыбалки. */
    private void bite(WorldServer ws) {
        int k = 1;
        int bx = MathHelper.floor_double(posX), by = MathHelper.floor_double(posY), bz = MathHelper.floor_double(posZ);
        if (!lava && rand.nextFloat() < 0.25F && worldObj.canLightningStrikeAt(bx, by + 1, bz)) k = 2;
        if (!lava && rand.nextFloat() < 0.5F && !worldObj.canBlockSeeTheSky(bx, by + 1, bz)) k--;
        String splash = lava ? "lava" : "splash", bubble = lava ? "flame" : "bubble";
        if (ticksCatchable > 0) {
            if (--ticksCatchable <= 0) {
                ticksCaughtDelay = 0;
                ticksCatchableDelay = 0;
            }
        } else if (ticksCatchableDelay > 0) {
            ticksCatchableDelay -= k;
            if (ticksCatchableDelay <= 0) {
                motionY += dreamWaterAbove() ? 0.2F : -0.2F; // рыба тянет поплавок в воду
                playSound(lava ? "liquid.lavapop" : "random.splash", 0.25F, 1.0F + (rand.nextFloat() - rand.nextFloat()) * 0.4F);
                float fy = MathHelper.floor_double(boundingBox.minY);
                ws.func_147487_a(bubble, posX, fy + 1.0F, posZ, (int) (1.0F + width * 20.0F), width, 0.0, width, 0.2F);
                ws.func_147487_a(lava ? "smoke" : "wake", posX, fy + 1.0F, posZ, (int) (1.0F + width * 20.0F), width, 0.0,
                    width, 0.2F);
                ticksCatchable = MathHelper.getRandomIntegerInRange(rand, 10, 30);
                com.divinerpgextended.debug.Debug.note(field_146042_b, "Рыбалка: поклёвка, подсекать " + ticksCatchable + " тиков");
            } else {
                fishApproachAngle = (float) (fishApproachAngle + rand.nextGaussian() * 4.0);
                float a = fishApproachAngle * ((float) Math.PI / 180F);
                float s = MathHelper.sin(a), c = MathHelper.cos(a);
                double x = posX + s * ticksCatchableDelay * 0.1F, y = MathHelper.floor_double(boundingBox.minY) + 1.0F,
                    z = posZ + c * ticksCatchableDelay * 0.1F;
                if (rand.nextFloat() < 0.15F) ws.func_147487_a(bubble, x, y - 0.1F, z, 1, s, 0.1, c, 0.0);
                ws.func_147487_a(lava ? "smoke" : "wake", x, y, z, 0, c * 0.04F, 0.01, -s * 0.04F, 1.0);
                ws.func_147487_a(lava ? "smoke" : "wake", x, y, z, 0, -c * 0.04F, 0.01, s * 0.04F, 1.0);
            }
        } else if (ticksCaughtDelay > 0) {
            ticksCaughtDelay -= k;
            float chance = 0.15F;
            if (ticksCaughtDelay < 20) chance += (20 - ticksCaughtDelay) * 0.05F;
            else if (ticksCaughtDelay < 40) chance += (40 - ticksCaughtDelay) * 0.02F;
            else if (ticksCaughtDelay < 60) chance += (60 - ticksCaughtDelay) * 0.01F;
            if (rand.nextFloat() < chance) {
                float a = MathHelper.randomFloatClamp(rand, 0.0F, 360.0F) * ((float) Math.PI / 180F);
                float r = MathHelper.randomFloatClamp(rand, 25.0F, 60.0F);
                ws.func_147487_a(splash, posX + MathHelper.sin(a) * r * 0.1F, MathHelper.floor_double(boundingBox.minY) + 1.0F,
                    posZ + MathHelper.cos(a) * r * 0.1F, 2 + rand.nextInt(2), 0.1F, 0.0, 0.1F, 0.0);
            }
            if (ticksCaughtDelay <= 0) {
                fishApproachAngle = MathHelper.randomFloatClamp(rand, 0.0F, 360.0F);
                ticksCatchableDelay = MathHelper.getRandomIntegerInRange(rand, 20, 80);
            }
        } else {
            Bait bait = currentBait();
            Zone zone = Zone.at(worldObj, posX, posY, posZ, lava);
            int p = power(bait, zone);
            float mult = 60F / (60F + p) * rod.waitMultiplier() * (bait == null ? 1.0F : bait.waitMultiplier());
            ticksCaughtDelay = (int) (MathHelper.getRandomIntegerInRange(rand, 100, 900) * mult);
            ticksCaughtDelay -= EnchantmentHelper.func_151387_h(field_146042_b) * 20 * 5;
            ticksCaughtDelay = Math.max(20, ticksCaughtDelay);
            com.divinerpgextended.debug.Debug.note(field_146042_b, "Рыбалка: ждём ~" + ticksCaughtDelay / 20 + " с | зона " + zone + ", удочка " + rod + ", приманка "
                + (bait == null ? "нет" : bait) + ", сила " + p + (lava ? ", лава" : ""));
        }
        if (ticksCatchable > 0) motionY -= rand.nextFloat() * rand.nextFloat() * rand.nextFloat() * 0.2;
    }

    /** Подсечка: улов из таблиц зоны, опыт, трата приманки. Возвращает урон удочке. */
    @Override
    public int func_146034_e() {
        if (worldObj.isRemote) return 0;
        int dmg = 0;
        EntityPlayer p = field_146042_b;
        if (field_146043_c != null) {
            double dx = p.posX - posX, dy = p.posY - posY, dz = p.posZ - posZ, d = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
            field_146043_c.motionX += dx * 0.1;
            field_146043_c.motionY += dy * 0.1 + MathHelper.sqrt_double(d) * 0.08;
            field_146043_c.motionZ += dz * 0.1;
            dmg = 3;
        } else if (ticksCatchable > 0) {
            int slot = baitSlot();
            Bait bait = slot < 0 ? null : Bait.of(p.inventory.mainInventory[slot].getItem());
            Zone zone = Zone.at(worldObj, posX, posY, posZ, lava);
            int power = power(bait, zone);
            float crate = rod.crateBonus() + (bait == null ? 0 : bait.crateBonus());
            int count = rand.nextFloat() < rod.doubleChance() ? 2 : 1;
            StringBuilder caught = new StringBuilder();
            for (int i = 0; i < count; i++) {
                ItemStack loot = FishingLoot.roll(rand, zone, power, bait != null, crate, rod.rareMultiplier());
                caught.append(caught.length() == 0 ? "" : ", ").append(loot == null ? "ничего" : loot.getDisplayName()
                    + (loot.stackSize > 1 ? " x" + loot.stackSize : ""));
                if (loot != null) toss(p, loot);
                if (loot != null) com.divinerpgextended.DreAchievements.give(p, com.divinerpgextended.DreAchievements.fishFirst);
            }
            ItemStack relic = bait == null ? null : FishingLoot.relic(rand, zone, power);
            com.divinerpgextended.debug.Debug.note(p, "Рыбалка: улов " + caught + (relic != null ? " + РЕЛИКВИЯ " + relic.getDisplayName() : "") + " | зона " + zone
                + ", удочка " + rod + ", приманка " + (bait == null ? "нет" : bait) + ", сила " + power + ", бонус ящика " + crate
                + (count > 1 ? ", двойной улов" : ""));
            if (relic != null) {
                toss(p, relic);
                FishingGear.announceRelic(p, relic);
                com.divinerpgextended.DreAchievements.give(p, com.divinerpgextended.DreAchievements.relic);
            }
            worldObj.spawnEntityInWorld(new EntityXPOrb(p.worldObj, p.posX, p.posY + 0.5, p.posZ + 0.5, rand.nextInt(6) + 1));
            if (bait != null && !p.capabilities.isCreativeMode && rand.nextFloat() >= rod.baitSave()
                && !(FishingGear.hasBaitBag(p) && rand.nextFloat() < FishingGear.BAG_SAVE)) {
                p.inventory.decrStackSize(slot, 1);
                p.inventoryContainer.detectAndSendChanges();
                com.divinerpgextended.debug.Debug.note(p, "Рыбалка: приманка " + bait + " потрачена");
            } else if (bait != null) {
                com.divinerpgextended.debug.Debug.note(p, "Рыбалка: приманка " + bait + " сохранена" + (p.capabilities.isCreativeMode ? " (творческий)" : ""));
            }
            dmg = 1;
        }
        else if (!inGround) com.divinerpgextended.debug.Debug.note(p, "Рыбалка: подсечка мимо - поклёвки не было");
        if (inGround) dmg = 2;
        setDead();
        return dmg;
    }

    private void toss(EntityPlayer p, ItemStack loot) {
        if (lava || dreamWaterAbove()) { // из лавы улов сразу в руки (предмет в полёте над лавой сгорел бы); из озера в потолке - упал бы на пол
            // в творческом игра молча удаляет то, чему нет места в инвентаре: кладём под ноги, подберёт сам
            if (p.capabilities.isCreativeMode || !p.inventory.addItemStackToInventory(loot)) {
                EntityItem drop = new EntityItem(worldObj, p.posX, p.posY + 0.5, p.posZ, loot);
                drop.delayBeforeCanPickup = 0;
                worldObj.spawnEntityInWorld(drop);
            }
            playSound("random.pop", 0.3F, 1.0F);
            return;
        }
        EntityItem item = new EntityItem(worldObj, posX, posY, posZ, loot);
        double dx = p.posX - posX, dy = p.posY - posY, dz = p.posZ - posZ, d = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
        item.motionX = dx * 0.1;
        item.motionY = dy * 0.1 + MathHelper.sqrt_double(d) * 0.08;
        item.motionZ = dz * 0.1;
        worldObj.spawnEntityInWorld(item);
    }
}
