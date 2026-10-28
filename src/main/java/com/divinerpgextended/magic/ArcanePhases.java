package com.divinerpgextended.magic;

import net.divinerpg.entities.arcana.EntityDramix;
import net.divinerpg.entities.arcana.EntityParasecta;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.divinerpgextended.boss.MortumArenaBosses;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Фазы боссов, где решает арканный урон.
 * <ul>
 * <li>Драмикс: пока невидим (полцикла), обычный урон проходит на {@link #DRAMIX_OTHER}, арканный - x{@link #DRAMIX_ARCANE}
 * (самонаводящиеся снаряды находят его сами).</li>
 * <li>Парасекта: на 75%, 50% и 25% здоровья закрывается хитиновым коконом до {@link #SHELL_TICKS} тиков - обычный урон
 * x{@link #SHELL_OTHER}; арканный урон кокон раскалывает ({@link #CRACK} на каждого игрока рядом): кокон спадает, она
 * оглушена и {@link #EXPOSED_TICKS} тиков получает x{@link #EXPOSED}.</li>
 * <li>Души Пожирателя (пока живы, он неуязвим): от арканы гибнут втрое быстрее, от обычного урона - вдвое медленнее.</li>
 * </ul>
 */
public class ArcanePhases {

    public static final float DRAMIX_OTHER = 0.2F, DRAMIX_ARCANE = 1.5F, SHELL_OTHER = 0.1F, EXPOSED = 1.5F, CRACK = 60F, SOUL_ARCANE = 3F,
        SOUL_OTHER = 0.5F;
    public static final int SHELL_TICKS = 300, EXPOSED_TICKS = 100;
    static final String SHELL_UNTIL = "dreShellUntil", SHELL_LEFT = "dreShellLeft", SHELL_STAGE = "dreShellStage", EXPOSED_UNTIL = "dreExposedUntil";

    /** Арканный ли это удар (снаряд арканного оружия или посоха Ветеи, удар мечом Арканы). */
    public static boolean arcane(DamageSource s) {
        if (ArcanaCombat.ARCANE.equals(s.damageType)) return true;
        Entity src = s.getSourceOfDamage();
        if (src == null) return false;
        NBTTagCompound t = src.getEntityData();
        return t.hasKey(ArcanaCombat.DMG) || t.getBoolean(ArcanaCombat.MOB) && s.getEntity() instanceof EntityPlayer;
    }

    private static int playersNear(EntityLivingBase b) {
        int n = 0;
        for (Object o : b.worldObj.playerEntities) if (((EntityPlayer) o).getDistanceSqToEntity(b) < 48 * 48) n++;
        return Math.max(1, n);
    }

    private static void say(EntityLivingBase b, String key, EnumChatFormatting c) {
        for (Object o : b.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.getDistanceSqToEntity(b) > 48 * 48) continue;
            ChatComponentTranslation m = new ChatComponentTranslation(key);
            m.getChatStyle().setColor(c);
            p.addChatMessage(m);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase t = e.entityLiving;
        if (t.worldObj.isRemote) return;
        boolean arc = arcane(e.source);
        if (t instanceof EntityDramix && t.isInvisible()) {
            e.ammount *= arc ? DRAMIX_ARCANE : DRAMIX_OTHER;
            return;
        }
        if (t instanceof EntityParasecta) {
            parasecta((EntityParasecta) t, e, arc);
            return;
        }
        if (t.getEntityData().hasKey(MortumArenaBosses.SOUL)) e.ammount *= arc ? SOUL_ARCANE : SOUL_OTHER;
    }

    void parasecta(EntityParasecta b, LivingHurtEvent e, boolean arc) {
        NBTTagCompound d = b.getEntityData();
        long now = b.worldObj.getTotalWorldTime();
        if (now < d.getLong(EXPOSED_UNTIL)) {
            e.ammount *= EXPOSED;
            return;
        }
        if (now < d.getLong(SHELL_UNTIL)) {
            if (!arc) {
                e.ammount *= SHELL_OTHER;
                return;
            }
            float left = d.getFloat(SHELL_LEFT) - e.ammount;
            if (left > 0) {
                d.setFloat(SHELL_LEFT, left);
                e.ammount *= SHELL_OTHER;
                return;
            }
            // кокон раскололся
            d.setLong(SHELL_UNTIL, 0);
            d.setLong(EXPOSED_UNTIL, now + EXPOSED_TICKS);
            b.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 5));
            b.addPotionEffect(new PotionEffect(Potion.weakness.id, EXPOSED_TICKS, 2));
            b.worldObj.playSoundAtEntity(b, "random.anvil_break", 1.2F, 0.6F);
            say(b, "dre.chat.parasecta_cracked", EnumChatFormatting.LIGHT_PURPLE);
            return;
        }
        // порог здоровья: 75%, 50%, 25% - кокон
        float after = (b.getHealth() - e.ammount) / b.getMaxHealth();
        int stage = d.getInteger(SHELL_STAGE), want = after <= 0.25F ? 3 : after <= 0.5F ? 2 : after <= 0.75F ? 1 : 0;
        if (want > stage && b.getHealth() - e.ammount > 0) {
            d.setInteger(SHELL_STAGE, want);
            d.setLong(SHELL_UNTIL, now + SHELL_TICKS);
            d.setFloat(SHELL_LEFT, CRACK * playersNear(b));
            b.worldObj.playSoundAtEntity(b, "mob.silverfish.step", 1.5F, 0.5F);
            say(b, "dre.chat.parasecta_shell", EnumChatFormatting.DARK_GREEN);
        }
    }

    /** Кокон Парасекты виден: частицы панциря вокруг неё. */
    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        EntityLivingBase b = e.entityLiving;
        if (!(b instanceof EntityParasecta) || b.worldObj.isRemote || b.ticksExisted % 5 != 0) return;
        if (b.worldObj.getTotalWorldTime() < b.getEntityData().getLong(SHELL_UNTIL) && b.worldObj instanceof WorldServer) {
            ((WorldServer) b.worldObj).func_147487_a("blockcrack_49_0", b.posX, b.posY + b.height * 0.5, b.posZ, 6, b.width * 0.5, b.height * 0.4,
                b.width * 0.5, 0.02);
        }
    }

    /** Для автотеста: в коконе ли. */
    public static boolean shelled(EntityLivingBase b) {
        return b.worldObj.getTotalWorldTime() < b.getEntityData().getLong(SHELL_UNTIL);
    }
}
