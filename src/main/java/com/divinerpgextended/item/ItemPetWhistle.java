package com.divinerpgextended.item;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Свисток питомцев (питомцы Divine, аддона и призраки кости призрака).
 * <ul>
 * <li>ПКМ - все твои питомцы в загруженной части мира (кроме сидящих) прыгают к тебе и бросают цель.</li>
 * <li>Удар свистком по своему питомцу - он уходит. Кто был из яйца (гризли, фиракрикс, сеймер, голем омоложения, паратику),
 * возвращается яйцом, и в яйце он сам ({@link #PET}: имя, здоровье, дрессировка) - из такого яйца выходит он же. Призрак
 * рассеивается, прочие приручённые просто уходят.</li>
 * </ul>
 * Питомцы одного хозяина друг друга не трогают ({@link Events}): не берут в цель и не ранят, даже если хозяин задел одного.
 */
public class ItemPetWhistle extends Item {

    /** Сохранённый в яйце питомец. */
    public static final String PET = "DrePet";
    public static final int COOLDOWN = 40;
    private static final String USED = "dreWhistleAt";

    public ItemPetWhistle() {
        setUnlocalizedName("dre.petWhistle");
        setTextureName("divinerpgextended:petWhistle");
        setCreativeTab(com.divinerpgextended.DreTab.TAB);
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        if (w.isRemote) return s;
        long now = w.getTotalWorldTime();
        long at = p.getEntityData().getLong(USED);
        if (now - at < COOLDOWN && now >= at) return s;
        p.getEntityData().setLong(USED, now);
        int n = call(p);
        w.playSoundAtEntity(p, "note.pling", 1.0F, 2.0F);
        p.addChatMessage(new ChatComponentTranslation(n > 0 ? "dre.chat.whistle" : "dre.chat.whistle_none", n));
        return s;
    }

    /** Позвать: питомцы хозяина к нему, без целей. @return сколько пришло */
    public static int call(EntityPlayer p) {
        int n = 0;
        com.divinerpgextended.magic.ArcaneSummons.forget(p);
        for (Entity e : pets(p)) {
            calm(e);
            if (e instanceof EntityTameable && ((EntityTameable) e).isSitting()) continue;
            double a = p.getRNG().nextDouble() * Math.PI * 2;
            e.setLocationAndAngles(p.posX + Math.cos(a) * 1.5, p.posY, p.posZ + Math.sin(a) * 1.5, e.rotationYaw, e.rotationPitch);
            e.fallDistance = 0;
            if (e instanceof EntityLiving) ((EntityLiving) e).getNavigator().clearPathEntity();
            if (p.worldObj instanceof WorldServer) ((WorldServer) p.worldObj).func_147487_a("portal", e.posX, e.posY + e.height / 2, e.posZ, 12, 0.3, 0.4, 0.3, 0.3);
            n++;
        }
        return n;
    }

    /** Бросить цель. */
    static void calm(Entity e) {
        if (e instanceof EntityLiving) ((EntityLiving) e).setAttackTarget(null);
        if (e instanceof EntityCreature) ((EntityCreature) e).setTarget(null);
        if (e instanceof net.minecraft.entity.EntityLivingBase) ((net.minecraft.entity.EntityLivingBase) e).setRevengeTarget(null);
    }

    /** Питомцы игрока в его мире (загруженные). */
    public static List<Entity> pets(EntityPlayer p) {
        List<Entity> out = new ArrayList<Entity>();
        for (Object o : p.worldObj.loadedEntityList.toArray()) {
            Entity e = (Entity) o;
            if (e.isEntityAlive() && pet(e) && owner(e) == p) out.add(e);
        }
        return out;
    }

    /** Питомец Divine или аддона (приручённый) или призрак кости призрака. */
    public static boolean pet(Entity e) {
        if (e instanceof com.divinerpgextended.magic.EntityArcaneWraith) return true;
        if (!(e instanceof EntityTameable) || !((EntityTameable) e).isTamed()) return false;
        String cls = e.getClass().getName();
        return cls.startsWith("net.divinerpg.") || cls.startsWith("com.divinerpgextended.");
    }

    /**
     * Хозяин питомца или null. Divine записывает владельцем имя игрока, ванильные - UUID; призрак знает хозяина сам.
     */
    public static EntityPlayer owner(Entity e) {
        if (e instanceof com.divinerpgextended.magic.EntityArcaneWraith) return ((com.divinerpgextended.magic.EntityArcaneWraith) e).owner();
        if (!(e instanceof EntityTameable)) return null;
        String s = ((EntityTameable) e).func_152113_b();
        if (s == null || s.isEmpty()) return null;
        try {
            EntityPlayer byId = e.worldObj.func_152378_a(UUID.fromString(s));
            if (byId != null) return byId;
        } catch (IllegalArgumentException notUuid) {}
        return e.worldObj.getPlayerEntityByName(s);
    }

    /** Строка хозяина (для сравнения, когда хозяин не в сети). */
    static String ownerKey(Entity e) {
        EntityPlayer p = owner(e);
        if (p != null) return p.getUniqueID().toString();
        return e instanceof EntityTameable ? ((EntityTameable) e).func_152113_b() : null;
    }

    /** Оба - питомцы одного хозяина. */
    public static boolean kin(Entity a, Entity b) {
        if (a == null || b == null || a == b || !pet(a) || !pet(b)) return false;
        String ka = ownerKey(a), kb = ownerKey(b);
        return ka != null && ka.equals(kb);
    }

    /** Яйцо, из которого вышел питомец (null - не из яйца). */
    public static ItemStack eggOf(Entity e) {
        if (e instanceof net.divinerpg.entities.vanilla.EntityGrizzle)
            return new ItemStack(net.divinerpg.utils.items.VanillaItemsOther.grizzleEgg, 1, ((net.divinerpg.entities.vanilla.EntityGrizzle) e).getColor());
        if (e instanceof net.divinerpg.entities.arcana.EntityFyracryx) return new ItemStack(net.divinerpg.utils.items.ArcanaItems.fyracryxSpawner);
        if (e instanceof net.divinerpg.entities.arcana.EntitySeimer) return new ItemStack(net.divinerpg.utils.items.ArcanaItems.seimerSpawner);
        if (e instanceof net.divinerpg.entities.arcana.EntityGolemOfRejuv) return new ItemStack(net.divinerpg.utils.items.ArcanaItems.golemSpawner);
        if (e instanceof net.divinerpg.entities.arcana.EntityParatiku) return new ItemStack(net.divinerpg.utils.items.ArcanaItems.paratikuSpawner);
        return null;
    }

    /** Отпустить питомца свистком: яйцо (с ним самим внутри) - хозяину, призрак рассеивается, прочие уходят. */
    public static ItemStack dismiss(EntityPlayer p, Entity e) {
        ItemStack egg = eggOf(e);
        if (egg != null) {
            NBTTagCompound t = new NBTTagCompound();
            if (e.writeToNBTOptional(t)) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setTag(PET, t);
                tag.setString("DrePetName", e.getCommandSenderName());
                egg.setTagCompound(tag);
            }
            if (!p.inventory.addItemStackToInventory(egg)) p.entityDropItem(egg, 0.5F);
        }
        if (p.worldObj instanceof WorldServer) ((WorldServer) p.worldObj).func_147487_a("portal", e.posX, e.posY + e.height / 2, e.posZ, 30, 0.4, 0.5, 0.4, 0.5);
        p.worldObj.playSoundAtEntity(e, "mob.endermen.portal", 0.8F, 1.6F);
        e.setDead();
        return egg;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean adv) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("dre.tooltip.whistle"));
    }

    public static class Events {

        /** Яйцо с питомцем: кто внутри. */
        @SubscribeEvent
        public void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent e) {
            if (e.itemStack == null || !e.itemStack.hasTagCompound() || !e.itemStack.getTagCompound().hasKey(PET)) return;
            e.toolTip.add(1, EnumChatFormatting.LIGHT_PURPLE + com.divinerpgextended.client.TooltipShift.KEEP
                + StatCollector.translateToLocalFormatted("dre.tooltip.egg_pet", e.itemStack.getTagCompound().getString("DrePetName")));
        }

        /** Удар свистком по своему питомцу - отпустить. */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onAttack(AttackEntityEvent e) {
            ItemStack held = e.entityPlayer.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemPetWhistle) || !pet(e.target)) return;
            e.setCanceled(true);
            if (e.entityPlayer.worldObj.isRemote) return;
            if (owner(e.target) != e.entityPlayer) {
                e.entityPlayer.addChatMessage(new ChatComponentTranslation("dre.chat.whistle_not_yours"));
                return;
            }
            dismiss(e.entityPlayer, e.target);
        }

        /** Питомцы одного хозяина не берут друг друга в цель. */
        @SubscribeEvent
        public void onTarget(LivingSetAttackTargetEvent e) {
            if (e.target == null || e.entityLiving.worldObj.isRemote || !kin(e.entityLiving, e.target)) return;
            calm(e.entityLiving);
        }

        /** ...и не ранят. */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onHurt(LivingAttackEvent e) {
            if (e.entityLiving.worldObj.isRemote) return;
            Entity src = e.source.getEntity();
            if (src != null && kin(src, e.entityLiving)) {
                e.setCanceled(true);
                calm(src);
            }
        }

        /** Яйцо с питомцем внутри: выходит он сам, а не новый (вместо призыва Divine). */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onUseEgg(PlayerInteractEvent e) {
            if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
            ItemStack held = e.entityPlayer.getHeldItem();
            if (held == null || !held.hasTagCompound() || !held.getTagCompound().hasKey(PET)) return;
            e.useItem = cpw.mods.fml.common.eventhandler.Event.Result.DENY;
            e.setCanceled(true);
            if (e.world.isRemote) return;
            Entity pet = EntityList.createEntityFromNBT(held.getTagCompound().getCompoundTag(PET), e.world);
            if (pet == null) return;
            pet.setLocationAndAngles(e.x + 0.5, e.y + 1, e.z + 0.5, e.entityPlayer.rotationYaw + 180F, 0F);
            pet.fallDistance = 0;
            if (!e.world.spawnEntityInWorld(pet)) return;
            if (!e.entityPlayer.capabilities.isCreativeMode && --held.stackSize <= 0)
                e.entityPlayer.inventory.setInventorySlotContents(e.entityPlayer.inventory.currentItem, null);
        }
    }
}
