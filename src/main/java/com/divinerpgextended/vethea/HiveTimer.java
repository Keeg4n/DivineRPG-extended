package com.divinerpgextended.vethea;

import java.util.List;

import net.divinerpg.entities.vethea.EntityHiveQueen;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import com.divinerpgextended.boss.BossMinions;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Королева улья (первый босс Ветеи) бессмертна: убить нужно её рой. Как только игрок подходит ({@link #ENGAGE} блоков),
 * Королева выпускает рой; полоска её здоровья - сколько роя осталось перебить ({@link #need}: 20 и по 6 за каждого
 * игрока сверх первого). В последней трети рой гуще и сон давит (слабость и тошнота). Все ушли с арены - всё сначала.
 * Рой перебит - Королева гибнет (обычные трофеи с глыбами цермила для Крыльев II, засчитана тем, кто был рядом).
 */
public class HiveTimer {

    public static final String LEFT = "dreSwarmLeft", NEED = "dreSwarmNeed", WAKE = "dreWake";
    public static final int BASE = 20, PER_PLAYER = 6, ENGAGE = 16, LEAVE = 32, AREA = 48;

    public static int left(EntityHiveQueen q) {
        return q.getEntityData().getInteger(LEFT);
    }

    public static int need(int players) {
        return BASE + PER_PLAYER * Math.max(0, players - 1);
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (!(e.entityLiving instanceof EntityHiveQueen) || e.entityLiving.worldObj.isRemote) return;
        EntityHiveQueen q = (EntityHiveQueen) e.entityLiving;
        if (q.getHealth() <= 0 || q.getEntityData().getBoolean(WAKE)) return;
        NBTTagCompound tag = q.getEntityData();
        int left = tag.getInteger(LEFT);
        List<EntityPlayer> near = players(q, left > 0 ? LEAVE : ENGAGE);
        if (near.isEmpty()) {
            if (left > 0) { // все ушли - сон отпустил, всё сначала
                tag.setInteger(LEFT, 0);
                q.setHealth(q.getMaxHealth());
            }
            return;
        }
        if (left == 0) {
            left = need(near.size());
            tag.setInteger(LEFT, left);
            tag.setInteger(NEED, left);
            q.worldObj.playSoundAtEntity(q, "mob.wither.spawn", 1.0F, 1.6F);
        }
        int need = Math.max(1, tag.getInteger(NEED));
        q.setHealth(Math.max(1, q.getMaxHealth() * left / need)); // полоска = сколько роя осталось
        boolean last = left * 3 <= need;
        WorldServer ws = (WorldServer) q.worldObj;
        int cap = 4 + 2 * near.size();
        if (q.ticksExisted % (last ? 30 : 50) == 0 && BossMinions.count(q) < cap) swarm(q, last ? 2 : 1);
        if (last && q.ticksExisted % 100 == 0) {
            for (EntityPlayer p : near) {
                p.addPotionEffect(new PotionEffect(Potion.weakness.id, 80, 0));
                p.addPotionEffect(new PotionEffect(Potion.confusion.id, 80, 0));
            }
        }
        if (q.ticksExisted % 20 == 0) ws.func_147487_a("witchMagic", q.posX, q.posY + 2, q.posZ, 6, 1, 1, 1, 0);
    }

    /** Убит её приспешник (игроком) - роя меньше; последний - Королева гибнет. */
    @SubscribeEvent
    public void onMinionDeath(LivingDeathEvent e) {
        EntityLivingBase m = e.entityLiving;
        if (m.worldObj.isRemote || !BossMinions.isMinion(m) || !(e.source.getEntity() instanceof EntityPlayer)) return;
        String owner = m.getEntityData().getString(BossMinions.OWNER);
        for (Object o : m.worldObj.getEntitiesWithinAABB(EntityHiveQueen.class, m.boundingBox.expand(96, 48, 96))) {
            EntityHiveQueen q = (EntityHiveQueen) o;
            if (!q.getUniqueID().toString().equals(owner) || q.getHealth() <= 0) continue;
            int left = left(q);
            if (left <= 0) return;
            q.getEntityData().setInteger(LEFT, --left);
            com.divinerpgextended.debug.Debug.note(e.source.getEntity(), "Королева улья: рой -1, осталось " + left);
            if (left == 0) wake(q, (EntityPlayer) e.source.getEntity());
            return;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayer> players(EntityHiveQueen q, double r) {
        return q.worldObj.getEntitiesWithinAABB(EntityPlayer.class, q.boundingBox.expand(r, r / 2, r));
    }

    private static void swarm(EntityHiveQueen q, int n) {
        for (int i = 0; i < n; i++) {
            EntityLiving m = q.worldObj.rand.nextBoolean() ? new net.divinerpg.entities.vethea.EntityHoverStinger(q.worldObj)
                : new net.divinerpg.entities.vethea.EntityHiveSoldier(q.worldObj);
            m.setLocationAndAngles(q.posX + q.worldObj.rand.nextGaussian() * 2, q.posY + 1, q.posZ + q.worldObj.rand.nextGaussian() * 2,
                q.worldObj.rand.nextFloat() * 360, 0);
            BossMinions.mark(m, q);
            q.worldObj.spawnEntityInWorld(m);
        }
    }

    /** Рой перебит: Королева гибнет от "пробуждения" - удар от лица добившего (засчитывание и трофеи как обычно). */
    private static void wake(EntityHiveQueen q, EntityPlayer p) {
        q.getEntityData().setBoolean(WAKE, true);
        q.hurtResistantTime = 0;
        q.attackEntityFrom(DamageSource.causePlayerDamage(p), q.getMaxHealth() * 10);
        q.worldObj.playSoundAtEntity(q, "random.levelup", 1.0F, 0.6F);
    }

    static void give(EntityPlayer p, ItemStack s) {
        if (!p.inventory.addItemStackToInventory(s)) p.worldObj.spawnEntityInWorld(new EntityItem(p.worldObj, p.posX, p.posY + 0.5, p.posZ, s));
        p.inventoryContainer.detectAndSendChanges();
    }

    /** Королеву не ранить (кроме пробуждения). */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (!(e.entityLiving instanceof EntityHiveQueen) || e.entityLiving.worldObj.isRemote) return;
        if (e.source == DamageSource.outOfWorld || e.entityLiving.getEntityData().getBoolean(WAKE)) return;
        e.setCanceled(true);
    }
}
