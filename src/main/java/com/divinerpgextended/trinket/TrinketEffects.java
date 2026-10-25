package com.divinerpgextended.trinket;

import java.util.List;
import java.util.UUID;

import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.events.ArcanaHelper;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;

import com.divinerpgextended.arcana.WeaponOrigin;
import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Эффекты аксессуаров. Работают только надетые в слоты аксессуаров (в рюкзаке - нет). */
public class TrinketEffects {

    private static final String DEAD_CD = "dreDeadAmuletCd", ANKH_CD = "dreAnkhCd";
    private static final UUID PAULDRON = UUID.fromString("9c1e5a2d-3f47-4b8e-a6d1-5e2c7f9b0a13");

    public static boolean wears(EntityPlayer p, Trinket t) {
        return com.divinerpgextended.accessory.Accessories.wears(p, t);
    }

    // ---------------- До урона: уклонение и иммунитеты ----------------

    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        if (e.source.isFireDamage() && wears(p, Trinket.BLUE_FLAME)) {
            e.setCanceled(true);
            com.divinerpgextended.debug.Debug.note(p, "Синее пламя: огонь не ранит");
            return;
        }
        // Древний оберег: 8% шанс увернуться от любого удара (кроме падения в пустоту)
        if (e.source != DamageSource.outOfWorld && wears(p, Trinket.ANCIENT_CHARM) && p.worldObj.rand.nextFloat() < 0.08F) {
            e.setCanceled(true);
            p.worldObj.playSoundAtEntity(p, "random.bow", 0.4F, 1.8F);
            com.divinerpgextended.debug.Debug.note(p, "Древний оберег: уворот");
        }
    }

    // ---------------- Урон ----------------

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        Entity src = e.source.getEntity();
        boolean melee = src != null && e.source.getSourceOfDamage() == src && !e.source.isProjectile();
        // бонусы аксессуаров - только против мобов: по игрокам бьёт лишь обычный урон (приваты с запретом PvP не обойти)
        if (src instanceof EntityPlayer && !src.worldObj.isRemote && !(e.entityLiving instanceof EntityPlayer) && !com.divinerpgextended.perk.Protection.spare(src, e.entityLiving)) {
            EntityPlayer p = (EntityPlayer) src;
            if (e.source.isProjectile() && wears(p, Trinket.BONE_RING)) {
                e.ammount *= 1.2F;
                com.divinerpgextended.debug.Debug.note(p, "Кольцо костяного стрелка: стрелы x1.2");
            }
            if (melee && wears(p, Trinket.WITHER_RING) && p.worldObj.rand.nextFloat() < 0.2F) {
                e.entityLiving.addPotionEffect(new PotionEffect(Potion.wither.id, 60, 1));
                com.divinerpgextended.debug.Debug.note(p, "Кольцо иссушения: иссушение цели");
            }
            if (!p.worldObj.isDaytime() && wears(p, Trinket.JACK_AMULET)) {
                e.ammount *= 1.15F;
                com.divinerpgextended.debug.Debug.note(p, "Тыквенный амулет: ночью x1.15");
            }
            if (melee && wears(p, Trinket.PARASECTA_STING)) {
                e.entityLiving.addPotionEffect(new PotionEffect(Potion.poison.id, 60, 0));
                com.divinerpgextended.debug.Debug.note(p, "Жало Парасекты: яд цели");
            }
            if (melee && wears(p, Trinket.ETERNAL_ICE_RING) && p.worldObj.rand.nextFloat() < 0.1F) {
                e.entityLiving.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 20, 4));
                e.entityLiving.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, 20, 2));
                com.divinerpgextended.debug.Debug.note(p, "Кольцо вечного льда: заморозка цели");
            }
            if (melee && WeaponOrigin.of(p.getCurrentEquippedItem()) != null && wears(p, Trinket.MASTER_SCABBARD)) {
                e.ammount *= 1.1F;
                com.divinerpgextended.debug.Debug.note(p, "Ножны мастера: меч Divine x1.1");
            }
            if (e.entityLiving instanceof IBossDisplayData && wears(p, Trinket.VALOR_MEDAL)) {
                e.ammount *= 1.1F;
                com.divinerpgextended.debug.Debug.note(p, "Медаль за отвагу: по боссу x1.1");
            }
        }
        if (e.entityLiving instanceof EntityPlayer && !e.entityLiving.worldObj.isRemote) {
            EntityPlayer v = (EntityPlayer) e.entityLiving;
            if (e.source.isExplosion() && wears(v, Trinket.BLAST_BELT)) {
                e.ammount *= 0.25F;
                com.divinerpgextended.debug.Debug.note(v, "Пояс укротителя криперов: взрыв x0.25");
            }
            if (!e.source.isUnblockable() && wears(v, Trinket.COLOSSUS_PAULDRON)) {
                e.ammount *= 0.88F; // как +3 брони
                com.divinerpgextended.debug.Debug.note(v, "Наплечник Колосса: x0.88");
            }
            if (src != null && src.getClass().getName().startsWith("net.divinerpg.entities.arcana") && wears(v, Trinket.DUNGEON_LAMP)) {
                e.ammount *= 0.85F;
                com.divinerpgextended.debug.Debug.note(v, "Лампа подземелий: моб Арканы x0.85");
            }
            // Панцирь Карцинуса: 15% - удар ближнего боя отражается обратно (шипы не отражаются, чтобы не было пинг-понга)
            if (melee && src instanceof EntityLivingBase && !(src instanceof EntityPlayer) && !"thorns".equals(e.source.damageType)
                && wears(v, Trinket.CARCINUS_SHELL)
                && v.worldObj.rand.nextFloat() < 0.15F) {
                src.attackEntityFrom(DamageSource.causeThornsDamage(v), e.ammount);
                e.ammount = 0;
                com.divinerpgextended.debug.Debug.note(v, "Панцирь Карцинуса: удар отражён");
            }
        }
    }

    /** Анх фараона: раз в 10 минут вместо смерти - половина здоровья. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDeath(LivingDeathEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || e.source == DamageSource.outOfWorld) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        long now = p.worldObj.getTotalWorldTime();
        if (!wears(p, Trinket.PHARAOH_ANKH) || now < p.getEntityData().getLong(ANKH_CD)) return;
        e.setCanceled(true);
        p.setHealth(p.getMaxHealth() / 2);
        com.divinerpgextended.debug.Debug.note(p, "Анх фараона: спас от смерти");
        p.getEntityData().setLong(ANKH_CD, now + 12000);
        p.worldObj.playSoundAtEntity(p, "mob.zombie.unfect", 1.0F, 1.2F);
        ChatComponentTranslation c = new ChatComponentTranslation("dre.chat.ankh");
        c.getChatStyle().setColor(EnumChatFormatting.GOLD);
        p.addChatMessage(c);
    }

    /** Костяное кольцо: 25% шанс, что стрела вернётся. */
    @SubscribeEvent
    public void onLoose(ArrowLooseEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (p.worldObj.isRemote || p.capabilities.isCreativeMode || !wears(p, Trinket.BONE_RING)) return;
        if (p.worldObj.rand.nextFloat() < 0.25F) p.inventory.addItemStackToInventory(new ItemStack(Items.arrow));
    }

    /** Амулет Джека: ночью враги не замечают носителя дальше 8 блоков. */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent e) {
        if (!(e.target instanceof EntityPlayer) || !(e.entityLiving instanceof EntityLiving) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.target;
        if (p.worldObj.isDaytime() || !wears(p, Trinket.JACK_AMULET) || e.entityLiving.getDistanceSqToEntity(p) <= 64) return;
        ((EntityLiving) e.entityLiving).setAttackTarget(null);
    }

    // ---------------- Постоянные эффекты ----------------

    private static void keep(EntityPlayer p, Potion potion, int amp) {
        PotionEffect cur = p.getActivePotionEffect(potion);
        if (cur == null || cur.getDuration() < 220) p.addPotionEffect(new PotionEffect(potion.id, 300, amp, true));
    }

    private static void clear(EntityPlayer p, Potion potion) {
        if (p.isPotionActive(potion)) p.removePotionEffect(potion.id);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote) return;
        if (p.ticksExisted % 2 == 0 && wears(p, Trinket.POCKET_MAGNET)) magnet(p);
        if (p.ticksExisted % 10 != 0) return;
        if (wears(p, Trinket.WITHER_RING)) clear(p, Potion.wither);
        if (wears(p, Trinket.DEAD_AMULET)) {
            clear(p, Potion.hunger);
            long now = p.worldObj.getTotalWorldTime();
            if (p.getHealth() < p.getMaxHealth() * 0.3F && now >= p.getEntityData().getLong(DEAD_CD)) {
                p.addPotionEffect(new PotionEffect(Potion.regeneration.id, 100, 1));
                com.divinerpgextended.debug.Debug.note(p, "Амулет мертвеца: регенерация II");
                p.getEntityData().setLong(DEAD_CD, now + 600);
            }
        }
        if (!p.worldObj.isDaytime() && wears(p, Trinket.JACK_AMULET)) keep(p, Potion.nightVision, 0);
        if (wears(p, Trinket.ICE_BROOCH)) clear(p, Potion.moveSlowdown);
        if (wears(p, Trinket.WATCHER_EYE)) {
            clear(p, Potion.blindness);
            clear(p, Potion.confusion);
        }
        if (wears(p, Trinket.BLUE_FLAME)) {
            keep(p, Potion.fireResistance, 0);
            p.extinguish();
        }
        if (wears(p, Trinket.PARASECTA_STING)) clear(p, Potion.poison);
        if (p.ticksExisted % 600 == 0 && wears(p, Trinket.LICH_CROWN) && !p.isPotionActive(Potion.field_76444_x)) {
            p.addPotionEffect(new PotionEffect(Potion.field_76444_x.id, 600, 1, true)); // поглощение: щит на 4 сердца
            com.divinerpgextended.debug.Debug.note(p, "Корона лича: щит на 4 сердца");
        }
        if (wears(p, Trinket.DEEP_SHELL) && p.isInsideOfMaterial(Material.water)) {
            p.setAir(Math.min(300, p.getAir() + 5)); // воздух уходит вдвое медленнее
            keep(p, Potion.nightVision, 0);
        }
        if (wears(p, Trinket.DUNGEON_LAMP) && p.dimension == ConfigurationHelper.arcana) keep(p, Potion.nightVision, 0);
        if (wears(p, Trinket.DRAMIX_SEAL)) {
            ArcanaHelper h = ArcanaHelper.getProperties(p);
            if (h != null && h.regenDelay == 0) h.forceRegen(10.0F); // ещё столько же, сколько Divine восстанавливает за 10 тиков
        }
        pauldron(p, wears(p, Trinket.COLOSSUS_PAULDRON));
        // старая Бездонная сумка приманок (работала из рюкзака) превращается в аксессуар - Ящик для снастей
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s != null && s.getItem() == ModItems.baitBag) {
                p.inventory.mainInventory[i] = new ItemStack(ModItems.trinket, 1, Trinket.TACKLE_BOX.ordinal());
            }
        }
    }

    /** Наплечник Колосса: почти не отбрасывает (модификатор держим, пока надет). */
    private static void pauldron(EntityPlayer p, boolean on) {
        IAttributeInstance kb = p.getEntityAttribute(SharedMonsterAttributes.knockbackResistance);
        AttributeModifier cur = kb.getModifier(PAULDRON);
        if (on && cur == null) kb.applyModifier(new AttributeModifier(PAULDRON, "Наплечник Колосса", 0.8, 0).setSaved(false));
        else if (!on && cur != null) kb.removeModifier(cur);
    }

    /** Карманный магнит: предметы в 6 блоках летят к игроку. */
    private static void magnet(EntityPlayer p) {
        @SuppressWarnings("unchecked")
        List<EntityItem> items = p.worldObj.getEntitiesWithinAABB(EntityItem.class, p.boundingBox.expand(6, 3, 6));
        for (EntityItem it : items) {
            if (it.isDead || it.delayBeforeCanPickup > 0) continue;
            double dx = p.posX - it.posX, dy = p.posY + 0.5 - it.posY, dz = p.posZ - it.posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 0.5) continue;
            it.motionX = dx / d * 0.35;
            it.motionY = dy / d * 0.35;
            it.motionZ = dz / d * 0.35;
        }
    }
}
