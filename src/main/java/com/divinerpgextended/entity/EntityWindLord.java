package com.divinerpgextended.entity;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.registry.ModBlocks;
import com.divinerpgextended.world.CadillionWorld;

/**
 * Skythern Wind Lord, the grove guardian - a puzzle boss. Itself invulnerable: it hovers over the glade, blows you away with gusts, shoots
 * and calls help to the arena floor. Around, at different heights ({@link #HEIGHTS}), 4 Wind Totems appear on cloud
 * platforms (in the wind temple - on pedestals with a staircase).
 * A totem charges for {@link #CHARGE} ticks while a player stands next to it (right click - start): the Lord hits
 * exactly that one with gusts. A charged totem strikes the Lord with lightning for a quarter of his health. After the fourth he is left with 1 health:
 * he descends to the ground, the wind dies down, and he can be finished off with anything (so the victory is credited to those who hit him). The high
 * totems are easy to reach with the Wind Master's carpet (Skythern set).
 */
public class EntityWindLord extends EntityGroveGuardian {

    public static final int[] HEIGHTS = { 6, 12, 18, 24 };
    public static final int TOTEM_R = 15, CHARGE = 60, HOVER = 12, MAX_ADDS = 5;
    public static final float SCALE = 2.5F;
    /** Its own damage: only from the totem's lightning. */
    public static final DamageSource TOTEM = new DamageSource("dreWindTotem").setDamageBypassesArmor();

    private int cx, cy, cz;
    private boolean placed;
    private int chargeX, chargeY, chargeZ, chargeTime = -1;
    private EntityPlayer charger;
    private int gustTimer = 100, shotTimer = 50, fiendTimer = 200;
    private double orbit;
    /** All totems are lit: knocked to the ground, vulnerable to everything. */
    private boolean grounded;

    public EntityWindLord(World world) {
        super(world);
        setSize(1.6F, 4.6F);
    }

    @Override
    public CadillionWorld world() {
        return CadillionWorld.SKYTHERN;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3D);
    }

    /** Arena center: the grove tether or the spawn point. */
    private void center() {
        int[] home = com.divinerpgextended.boss.LairSummons.home(this);
        if (home != null) {
            cx = home[0];
            cy = home[1];
            cz = home[2];
        } else {
            cx = (int) Math.floor(posX);
            cy = (int) Math.floor(posY);
            cz = (int) Math.floor(posZ);
        }
    }

    public int[] totem(int i) {
        double a = Math.PI / 4 + Math.PI / 2 * i;
        return new int[] { cx + (int) Math.round(Math.cos(a) * TOTEM_R), cy + HEIGHTS[i], cz + (int) Math.round(Math.sin(a) * TOTEM_R) };
    }

    /** Air, leaves, grass, snow - what the arena may take; anything built stays (it could be someone's base on the island). */
    private boolean soft(int x, int y, int z) {
        net.minecraft.block.Block b = worldObj.getBlock(x, y, z);
        return worldObj.isAirBlock(x, y, z) || b.isLeaves(worldObj, x, y, z) || b.isReplaceable(worldObj, x, y, z) && worldObj.getTileEntity(x, y, z) == null;
    }

    /** Place totems on the 3x3 cloud platforms (empty above them). Only into air, leaves and grass: built blocks are never cleared. */
    public void placeTotems() {
        center();
        for (int i = 0; i < 4; i++) {
            int[] t = totem(i);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (worldObj.isAirBlock(t[0] + dx, t[1] - 1, t[2] + dz) || worldObj.getBlock(t[0] + dx, t[1] - 1, t[2] + dz).isLeaves(worldObj, t[0] + dx, t[1] - 1, t[2] + dz))
                    worldObj.setBlock(t[0] + dx, t[1] - 1, t[2] + dz, ModBlocks.cloud, 0, 3);
                for (int h = 0; h <= 3; h++) {
                    int y = t[1] + h;
                    if (!(dx == 0 && dz == 0 && h == 0) && !worldObj.isAirBlock(t[0] + dx, y, t[2] + dz) && soft(t[0] + dx, y, t[2] + dz))
                        worldObj.setBlockToAir(t[0] + dx, y, t[2] + dz);
                }
            }
            if (soft(t[0], t[1], t[2])) worldObj.setBlock(t[0], t[1], t[2], ModBlocks.windTotem, 0, 3);
        }
        placed = true;
    }

    public int litTotems() {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            int[] t = totem(i);
            if (worldObj.getBlock(t[0], t[1], t[2]) == ModBlocks.windTotem && worldObj.getBlockMetadata(t[0], t[1], t[2]) == 1) n++;
        }
        return n;
    }

    private void removeTotems() {
        for (int i = 0; i < 4; i++) {
            int[] t = totem(i);
            if (worldObj.getBlock(t[0], t[1], t[2]) == ModBlocks.windTotem) worldObj.setBlockToAir(t[0], t[1], t[2]);
        }
    }

    /** Totem charge from slicers at a distance: accumulates with hits, decays over time. */
    private final float[] remote = new float[4];
    public static final float REMOTE_HIT = 12, REMOTE_DECAY = 0.5F;

    /** A slicer hit the totem: recharge; when CHARGE is reached - the totem lights up. */
    public void remoteCharge(int x, int y, int z) {
        for (int i = 0; i < 4; i++) {
            int[] t = totem(i);
            if (t[0] != x || t[1] != y || t[2] != z) continue;
            remote[i] += REMOTE_HIT;
            ((WorldServer) worldObj).func_147487_a("magicCrit", x + 0.5, y + 1, z + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
            worldObj.playSoundEffect(x + 0.5, y + 1, z + 0.5, "random.orb", 0.6F, 1.6F);
            if (remote[i] >= CHARGE) {
                remote[i] = 0;
                lightTotem(x, y, z);
            }
        }
    }

    public float remoteProgress(int i) {
        return remote[i];
    }

    /** A player began charging the totem (click on it). A repeated click on the same totem does not reset the countdown. */
    public void startCharge(int x, int y, int z, EntityPlayer p) {
        if (charging() && chargeX == x && chargeY == y && chargeZ == z) {
            charger = p;
            return;
        }
        chargeX = x;
        chargeY = y;
        chargeZ = z;
        chargeTime = 0;
        charger = p;
        setAttackTarget(p);
        worldObj.playSoundEffect(x + 0.5, y + 1, z + 0.5, "portal.trigger", 0.4F, 1.8F);
    }

    public boolean charging() {
        return chargeTime >= 0;
    }

    public boolean grounded() {
        return grounded;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (!worldObj.isRemote && src.getEntity() instanceof net.minecraft.entity.player.EntityPlayer) fighters.add(src.getEntity().getUniqueID());
        if (src == TOTEM || src == DamageSource.outOfWorld || grounded) return super.attackEntityFrom(src, amount);
        if (!worldObj.isRemote && src.getEntity() instanceof EntityPlayer) {
            // invulnerable: the hit is dispersed by the wind
            ((WorldServer) worldObj).func_147487_a("cloud", posX, posY + height * 0.5, posZ, 10, 0.5, 0.8, 0.5, 0.05);
            worldObj.playSoundAtEntity(this, "mob.enderdragon.wings", 0.6F, 1.8F);
        }
        return false;
    }

    @Override
    public void onLivingUpdate() {
        if (grounded) { // knocked down: stands on the ground, no wind, no arrows, no help
            super.onLivingUpdate();
            fallDistance = 0;
            motionX *= 0.5;
            motionZ *= 0.5;
            if (!worldObj.isRemote && ticksExisted % 10 == 0)
                ((WorldServer) worldObj).func_147487_a("cloud", posX, posY + 0.5, posZ, 4, 0.6, 0.2, 0.6, 0.01);
            return;
        }
        motionY = motionY * 0.6 + 0.08; // hovers: cancel gravity
        super.onLivingUpdate();
        fallDistance = 0;
        if (worldObj.isRemote) return;
        if (!placed) placeTotems();
        WorldServer ws = (WorldServer) worldObj;
        // circles over the glade at height HOVER
        orbit += 0.01;
        double tx = cx + 0.5 + Math.cos(orbit) * 6, tz = cz + 0.5 + Math.sin(orbit) * 6, ty = cy + HOVER;
        motionX += (tx - posX) * 0.02;
        motionZ += (tz - posZ) * 0.02;
        motionY += (ty - posY) * 0.04;
        if (ticksExisted % 3 == 0) ws.func_147487_a("cloud", posX, posY, posZ, 2, 0.4, 0.1, 0.4, 0.01);
        tickCharge(ws);
        for (int i = 0; i < 4; i++) remote[i] = Math.max(0, remote[i] - REMOTE_DECAY);
        EntityLivingBase t = getAttackTarget();
        if (t == null || !t.isEntityAlive()) {
            EntityPlayer p = worldObj.getClosestVulnerablePlayerToEntity(this, 40);
            if (p != null) setAttackTarget(t = p);
            else return;
        }
        getLookHelper().setLookPositionWithEntity(t, 30, 30);
        if (--gustTimer <= 0) {
            gustTimer = charging() ? 40 : 100;
            gust(charging() && charger != null ? charger : t);
        }
        if (--shotTimer <= 0 && canEntityBeSeen(t)) {
            shotTimer = 50 + rand.nextInt(20);
            shoot(t);
        }
        if (--fiendTimer <= 0) {
            // from below - melee: Skythern golems and fiends on the arena floor, up to MAX_ADDS
            fiendTimer = 300;
            for (int i = 0; i < 3 && addsAlive() < MAX_ADDS; i++) {
                double a = rand.nextDouble() * Math.PI * 2, d = 4 + rand.nextDouble() * 8;
                summonAt(i == 0 ? net.divinerpg.entities.twilight.EntitySkythernFiend.class : net.divinerpg.entities.twilight.EntitySkythernGolem.class,
                    cx + 0.5 + Math.cos(a) * d, cy + 0.2, cz + 0.5 + Math.sin(a) * d);
            }
        }
    }

    private void tickCharge(WorldServer ws) {
        if (chargeTime < 0) return;
        boolean near = charger != null && charger.isEntityAlive() && charger.worldObj == worldObj
            && charger.getDistanceSq(chargeX + 0.5, chargeY + 0.5, chargeZ + 0.5) < 3.0 * 3.0;
        if (!near || worldObj.getBlock(chargeX, chargeY, chargeZ) != ModBlocks.windTotem) {
            chargeTime = -1; // blown away - start over
            worldObj.playSoundEffect(chargeX + 0.5, chargeY + 1, chargeZ + 0.5, "random.fizz", 0.5F, 1.2F);
            return;
        }
        chargeTime++;
        double up = (double) chargeTime / CHARGE;
        ws.func_147487_a("magicCrit", chargeX + 0.5, chargeY + 1 + up * 2, chargeZ + 0.5, 4, 0.3, 0.2, 0.3, 0.05);
        if (chargeTime < CHARGE) return;
        chargeTime = -1;
        lightTotem(chargeX, chargeY, chargeZ);
    }

    /** The totem lit up: lightning at the Lord for a quarter of his health (the fourth one finishes him off). */
    private void lightTotem(int x, int y, int z) {
        worldObj.setBlockMetadataWithNotify(x, y, z, 1, 3);
        // lightning strikes the Lord: flash and thunder for everyone, damage - with its own source
        worldObj.addWeatherEffect(EntitySafeLightning.boss(worldObj, posX, posY, posZ)); // without fire on blocks
        int lit = litTotems();
        hurtResistantTime = 0; // the totem's lightning always goes through
        if (lit >= 4) { // the last totem: 1 health, falls to the ground - finish him off with anything
            if (getHealth() > 1) super.attackEntityFrom(TOTEM, getHealth() - 1);
            grounded = true;
            motionY = -0.5;
            setAttackTarget(null);
            worldObj.playSoundAtEntity(this, "mob.enderdragon.growl", 2.0F, 0.6F);
            FrostPowers.tell(this, "dre.chat.wind_lord_grounded");
        } else super.attackEntityFrom(TOTEM, getMaxHealth() * 0.25F);
        com.divinerpgextended.debug.Debug.note(this, "Wind Lord: totem charged, total " + lit);
    }

    /** Gust: blows the target and everyone near it away from the Lord (Stone Wall holds you). */
    private void gust(EntityLivingBase t) {
        worldObj.playSoundAtEntity(t, "mob.enderdragon.wings", 2.0F, 0.7F);
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = worldObj.getEntitiesWithinAABB(EntityPlayer.class, t.boundingBox.expand(4, 3, 4));
        for (EntityPlayer p : near) {
            ((WorldServer) worldObj).func_147487_a("cloud", p.posX, p.posY + 1, p.posZ, 15, 0.6, 0.6, 0.6, 0.15);
            if (p.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).getAttributeValue() >= 1.0) continue;
            double dx = p.posX - posX, dz = p.posZ - posZ, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            p.motionX += dx / d * 1.6;
            p.motionZ += dz / d * 1.6;
            p.motionY += 0.35;
            p.velocityChanged = true;
        }
    }

    /** Wind arrow at the target. */
    private void shoot(EntityLivingBase t) {
        EntityArrow a = new EntityArrow(worldObj, this, t, 1.8F, 4.0F);
        a.setDamage(getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue() * 0.25);
        worldObj.spawnEntityInWorld(a);
        worldObj.playSoundAtEntity(this, "random.bow", 1.0F, 0.6F);
    }

    /** Who hit the Lord (they get the victory sound). */
    private final java.util.Set<java.util.UUID> fighters = new java.util.HashSet<java.util.UUID>();
    /** Victory sound: the client plays the dragon death voice for the whole world, so we send it only to those who hit. */
    public static final String VICTORY = "mob.enderdragon.end";

    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (worldObj.isRemote) return;
        removeTotems();
        if (src.getEntity() instanceof net.minecraft.entity.player.EntityPlayer) fighters.add(src.getEntity().getUniqueID());
        for (Object o : worldObj.playerEntities) {
            net.minecraft.entity.player.EntityPlayerMP p = (net.minecraft.entity.player.EntityPlayerMP) o;
            if (!fighters.contains(p.getUniqueID()) || p.playerNetServerHandler == null) continue;
            p.playerNetServerHandler.sendPacket(new net.minecraft.network.play.server.S29PacketSoundEffect(VICTORY, p.posX, p.posY, p.posZ, 1.0F, 1.0F));
        }
    }

    /** How many players will hear the victory sound (for the selftest). */
    public int fighterCount() {
        return fighters.size();
    }

    @Override
    protected void fall(float d) {}

    @Override
    protected String getLivingSound() {
        return "mob.blaze.breathe";
    }

    @Override
    protected String getHurtSound() {
        return "mob.blaze.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.blaze.death"; // the dragon voice - only for the winners (onDeath)
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setIntArray("Center", new int[] { cx, cy, cz });
        tag.setBoolean("Placed", placed);
        tag.setBoolean("Grounded", grounded);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        int[] c = tag.getIntArray("Center");
        if (c.length == 3) {
            cx = c[0];
            cy = c[1];
            cz = c[2];
        }
        placed = tag.getBoolean("Placed");
        grounded = tag.getBoolean("Grounded");
    }
}
