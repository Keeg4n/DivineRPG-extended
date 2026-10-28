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
 * Скрытая копия игрока для манекена: тот же путь урона, что у игрока (броня и её сеты, аксессуары, зелья, аркана, все
 * события урона Divine и аддона), но вне мира: не появляется, не сохраняется, не умирает и ничего не роняет. Снимок
 * игрока ({@link #snapshot}) - только надетое, данные игрока (аксессуары, эффекты, прогресс), эффекты зелий и аркана;
 * инвентарь и вещь в руке не копируются.
 */
public class DummyGhost extends FakePlayer {

    /** Запас здоровья копии: чтобы любой удар измерялся целиком. */
    static final float HEALTH = 100000F;
    private static Method damageEntity;

    DummyGhost(WorldServer w, UUID id) {
        super(w, new GameProfile(id, "dre_dummy"));
        // глухое соединение: обычные пакеты копии (чат, эффекты) уходят в никуда, а не роняют сервер
        new Deaf(w.func_73046_m(), this);
    }

    /** Соединение копии: ничего не отправляет. */
    static class Deaf extends net.minecraft.network.NetHandlerPlayServer {
        Deaf(net.minecraft.server.MinecraftServer s, DummyGhost g) {
            super(s, new net.minecraft.network.NetworkManager(false), g);
        }

        @Override
        public void sendPacket(net.minecraft.network.Packet p) {}
    }

    /** Аркана копии - прямо в поле (setBarValue Divine шлёт пакет своей сетью, а у копии соединения нет). */
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

    /** Снимок игрока для манекена. */
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
        return t;
    }

    /** Копия игрока по снимку. */
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

    /** Сколько урона дошло бы до игрока от этого удара (0 - уклонился или удар отменён). */
    float measure(DamageSource s, float amount, NBTTagCompound snap) {
        net.minecraft.entity.ai.attributes.IAttributeInstance hp = getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth);
        hp.setBaseValue(HEALTH);
        setHealth(HEALTH);
        setAbsorptionAmount(0);
        hurtResistantTime = 0;
        net.divinerpg.utils.events.ArcanaHelper h = net.divinerpg.utils.events.ArcanaHelper.getProperties(this);
        if (h != null) setBar(h, snap.getFloat("Arcana")); // каждый удар - с той аркановой защитой, что была у игрока
        try {
            if (net.minecraftforge.common.ForgeHooks.onLivingAttack(this, s, amount)) return 0;
            if (damageEntity == null) damageEntity = ReflectionHelper.findMethod(net.minecraft.entity.EntityLivingBase.class, this,
                new String[] { "damageEntity", "func_70665_d" }, DamageSource.class, float.class);
            damageEntity.invoke(this, s, amount);
        } catch (Throwable t) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Манекен: удар не посчитан как по игроку, показан как есть: " + t);
            return amount;
        }
        return HEALTH - getHealth();
    }
}
