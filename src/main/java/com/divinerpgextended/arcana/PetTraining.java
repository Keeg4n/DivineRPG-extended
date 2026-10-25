package com.divinerpgextended.arcana;

import java.util.List;
import java.util.UUID;

import net.divinerpg.entities.arcana.EntityFyracryx;
import net.divinerpg.entities.arcana.EntityGolemOfRejuv;
import net.divinerpg.entities.arcana.EntityParatiku;
import net.divinerpg.entities.arcana.EntitySeimer;
import net.divinerpg.entities.arcana.EntityZelus;
import net.divinerpg.entities.base.EntityDivineRPGTameable;
import net.divinerpg.entities.vanilla.EntityEhu;
import net.divinerpg.entities.vanilla.EntityGrizzle;
import net.divinerpg.entities.vanilla.EntityHusk;
import net.divinerpg.entities.vanilla.EntityLivestockMerchant;
import net.divinerpg.entities.vanilla.EntitySmelter;
import net.divinerpg.entities.vanilla.EntitySnapper;
import net.divinerpg.entities.vanilla.EntityStoneGolem;
import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Дрессировка питомцев у Зелуса. Присесть + ПКМ по Зелусу: ближайший твой питомец Divine (до 10 блоков) получает
 * следующий уровень за арканиум (и жетоны). Каждый уровень +25% здоровья и +20% урона. Умения:
 * <ul>
 * <li>Paratiku: 1 - летает за хозяином, 2 - собирает предметы вокруг себя и приносит хозяину (8 блоков), 3 - радиус 14;</li>
 * <li>Голем омоложения: 2 - хозяин рядом регенерирует, 3 - регенерация II;</li>
 * <li>Фиракрикс: 2 - хозяин рядом не горит, 3 - его удары поджигают;</li>
 * <li>Сеймер: 2 - хозяин рядом видит в темноте, 3 - и дышит под водой.</li>
 * </ul>
 */
public class PetTraining {

    public static final int MAX_LEVEL = 3;
    private static final String LEVEL = "dreLevel";
    private static final UUID HP = UUID.fromString("2b8f4c61-9d3e-4a7b-8c5f-1e6d2a9b7c34");

    public static int level(Entity e) {
        return e.getEntityData().getInteger(LEVEL);
    }

    private static int[] cost(int nextLevel) {
        switch (nextLevel) {
            case 1: return new int[] { 8, 0 };
            case 2: return new int[] { 16, 4 };
            default: return new int[] { 32, 8 };
        }
    }

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        com.divinerpgextended.audit.Audit.say(p, key, args);
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    private static boolean owns(EntityDivineRPGTameable pet, EntityPlayer p) {
        return pet.isTamed() && pet.getOwner() == p;
    }

    private static int count(EntityPlayer p, net.minecraft.item.Item item) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) if (s != null && s.getItem() == item) n += s.stackSize;
        return n;
    }

    private static void take(EntityPlayer p, net.minecraft.item.Item item, int n) {
        for (int i = 0; i < p.inventory.mainInventory.length && n > 0; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s == null || s.getItem() != item) continue;
            int t = Math.min(n, s.stackSize);
            s.stackSize -= t;
            n -= t;
            if (s.stackSize <= 0) p.inventory.mainInventory[i] = null;
        }
    }

    private static String kind(Entity pet) {
        if (pet instanceof EntityParatiku) return "paratiku";
        if (pet instanceof EntityGolemOfRejuv) return "golem";
        if (pet instanceof EntityFyracryx) return "fyracryx";
        if (pet instanceof EntitySeimer) return "seimer";
        if (pet instanceof EntitySmelter) return "smelter";
        if (pet instanceof EntityStoneGolem) return "stonegolem";
        if (pet instanceof EntityGrizzle) return "grizzle";
        if (pet instanceof EntitySnapper) return "snapper";
        if (pet instanceof EntityEhu) return "ehu";
        if (pet instanceof EntityHusk) return "husk";
        return "other";
    }

    /** Питомцы Торговца скотом - их учит он (за теневые монеты), остальных - Зелус. */
    static boolean livestock(Entity pet) {
        return pet instanceof EntitySmelter || pet instanceof EntityStoneGolem || pet instanceof EntityGrizzle
            || pet instanceof EntitySnapper || pet instanceof EntityEhu || pet instanceof EntityHusk;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(EntityInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        boolean zelus = e.target instanceof EntityZelus, farmer = e.target instanceof EntityLivestockMerchant;
        if (!zelus && !farmer || !p.isSneaking()) return;
        e.setCanceled(true);
        if (p.worldObj.isRemote) return;
        String who = zelus ? "zelus" : "livestock";
        EntityDivineRPGTameable pet = null;
        double best = 100;
        @SuppressWarnings("unchecked")
        List<Entity> near = p.worldObj.getEntitiesWithinAABB(EntityDivineRPGTameable.class, p.boundingBox.expand(10, 5, 10));
        for (Entity o : near) {
            EntityDivineRPGTameable t = (EntityDivineRPGTameable) o;
            double d = t.getDistanceSqToEntity(p);
            if (owns(t, p) && livestock(t) == farmer && d < best) {
                pet = t;
                best = d;
            }
        }
        if (pet == null) {
            say(p, EnumChatFormatting.GRAY, "dre." + who + ".no_pet");
            return;
        }
        int lvl = level(pet);
        String name = pet.getCommandSenderName();
        if (lvl >= MAX_LEVEL) {
            say(p, EnumChatFormatting.GRAY, "dre." + who + ".max", name);
            return;
        }
        int[] c = cost(lvl + 1);
        if (farmer) c = new int[] { 8 << lvl, 0 }; // 8, 16, 32 теневых монет
        net.minecraft.item.Item money = zelus ? ArcanaItems.arcanium : net.divinerpg.utils.items.VanillaItemsOther.shadowCoins;
        if (!p.capabilities.isCreativeMode) {
            if (count(p, money) < c[0] || count(p, ArcanaItems.dungeonTokens) < c[1]) {
                say(p, EnumChatFormatting.YELLOW, "dre." + who + ".price", name, String.valueOf(lvl + 1), String.valueOf(c[0]),
                    String.valueOf(c[1]));
                return;
            }
            take(p, money, c[0]);
            take(p, ArcanaItems.dungeonTokens, c[1]);
        }
        pet.getEntityData().setInteger(LEVEL, lvl + 1);
        applyStats(pet);
        pet.heal(pet.getMaxHealth());
        p.inventoryContainer.detectAndSendChanges();
        p.worldObj.playSoundAtEntity(pet, "random.levelup", 0.7F, 1.3F);
        if (p.worldObj instanceof WorldServer) {
            ((WorldServer) p.worldObj).func_147487_a("happyVillager", pet.posX, pet.posY + pet.height, pet.posZ, 20, 0.4, 0.4, 0.4, 0.1);
        }
        say(p, EnumChatFormatting.GREEN, "dre." + who + ".trained", name, String.valueOf(lvl + 1));
        com.divinerpgextended.DreAchievements.give(p, com.divinerpgextended.DreAchievements.petTrainer);
        say(p, EnumChatFormatting.AQUA, "dre.zelus.skill." + kind(pet) + "." + (lvl + 1));
    }

    /** Свой обученный питомец этого вида рядом с хозяином (уровень, иначе 0). */
    static int nearPet(EntityPlayer owner, Class<? extends EntityDivineRPGTameable> type, double r) {
        int best = 0;
        @SuppressWarnings("unchecked")
        List<Entity> near = owner.worldObj.getEntitiesWithinAABB(type, owner.boundingBox.expand(r, r / 2, r));
        for (Entity o : near) if (owns((EntityDivineRPGTameable) o, owner)) best = Math.max(best, level(o));
        return best;
    }

    /** Переплавка для Smelter: руда - в слитки (3 - иногда вдвойне); null - не руда. */
    static ItemStack smelt(net.minecraft.world.World w, ItemStack s, int lvl) {
        if (s == null || !FurnaceRoles.ore(s)) return null;
        ItemStack r = net.minecraft.item.crafting.FurnaceRecipes.smelting().getSmeltingResult(s);
        if (r == null) return null;
        int n = s.stackSize * r.stackSize;
        if (lvl >= 3) for (int i = 0; i < s.stackSize; i++) if (w.rand.nextFloat() < 0.25F) n += r.stackSize;
        ItemStack out = r.copy();
        out.stackSize = Math.min(n, out.getMaxStackSize());
        return out;
    }

    /**
     * Smelter 2+: руда, выпавшая из блока рядом с хозяином, плавится сразу на земле - огонь, дым, шипение, смелтер
     * вспыхивает. Выброшенное самим игроком (у него задержка подбора 40) не трогаем.
     */
    @SubscribeEvent
    public void onItemJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof net.minecraft.entity.item.EntityItem)) return;
        net.minecraft.entity.item.EntityItem it = (net.minecraft.entity.item.EntityItem) e.entity;
        if (it.delayBeforeCanPickup >= 40 || !FurnaceRoles.ore(it.getEntityItem())) return;
        int lvl = 0;
        EntitySmelter pet = null;
        for (Object o : e.world.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.getDistanceSqToEntity(it) > 12 * 12) continue;
            @SuppressWarnings("unchecked")
            List<Entity> near = e.world.getEntitiesWithinAABB(EntitySmelter.class, p.boundingBox.expand(10, 5, 10));
            for (Entity s : near) if (owns((EntityDivineRPGTameable) s, p) && level(s) > lvl) {
                lvl = level(s);
                pet = (EntitySmelter) s;
            }
        }
        if (lvl < 2) return;
        ItemStack out = smelt(e.world, it.getEntityItem(), lvl);
        if (out == null) return;
        it.setEntityItemStack(out);
        WorldServer ws = (WorldServer) e.world;
        ws.func_147487_a("flame", it.posX, it.posY + 0.3, it.posZ, 12, 0.2, 0.2, 0.2, 0.03);
        ws.func_147487_a("smoke", it.posX, it.posY + 0.4, it.posZ, 8, 0.2, 0.2, 0.2, 0.02);
        e.world.playSoundEffect(it.posX, it.posY, it.posZ, "random.fizz", 0.5F, 1.6F);
        if (pet != null) ws.func_147487_a("lava", pet.posX, pet.posY + pet.height, pet.posZ, 4, 0.3, 0.2, 0.3, 0);
    }

    /** Smelter 2+: руда, подобранная рядом и ещё не расплавленная на земле (выпала далеко), переплавляется при подборе. */
    @SubscribeEvent
    public void onPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (p.worldObj.isRemote) return;
        int lvl = nearPet(p, EntitySmelter.class, 10);
        if (lvl < 2) return;
        ItemStack out = smelt(p.worldObj, e.item.getEntityItem(), lvl);
        if (out != null) e.item.setEntityItemStack(out);
    }

    /** Каменный голем 2+: хозяин рядом получает вдвое (3 - вчетверо) меньше урона от взрывов. */
    @SubscribeEvent
    public void onOwnerHurt(LivingHurtEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || !e.source.isExplosion()) return;
        int lvl = nearPet((EntityPlayer) e.entityLiving, EntityStoneGolem.class, 10);
        if (lvl >= 2) {
            e.ammount *= lvl >= 3 ? 0.25F : 0.5F;
            com.divinerpgextended.debug.Debug.note(e.entityLiving, "Каменный голем рядом: взрыв x" + (lvl >= 3 ? "0.25" : "0.5"));
        }
    }

    /** Snapper 3: под водой копаешь без замедления. */
    @SubscribeEvent
    public void onBreakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed e) {
        EntityPlayer p = e.entityPlayer;
        if (!p.isInsideOfMaterial(net.minecraft.block.material.Material.water)) return;
        if (nearPet(p, EntitySnapper.class, 10) < 3) return;
        float speed = e.newSpeed;
        if (!net.minecraft.enchantment.EnchantmentHelper.getAquaAffinityModifier(p)) speed *= 5.0F;
        if (!p.onGround) speed *= 5.0F;
        e.newSpeed = speed;
    }

    /** Умения оверворлдных питомцев, которые работают по времени. */
    private static void livestockSkills(EntityDivineRPGTameable pet, EntityPlayer owner, int lvl) {
        if (lvl < 2 || pet.ticksExisted % 20 != 0) return;
        boolean close = owner.getDistanceSqToEntity(pet) <= 100;
        if (pet instanceof EntityGrizzle) {
            // Вожак: враги, нацеленные на хозяина, переключаются на гризли
            @SuppressWarnings("unchecked")
            List<Entity> mobs = pet.worldObj.getEntitiesWithinAABB(net.minecraft.entity.EntityLiving.class, pet.boundingBox.expand(8, 4, 8));
            for (Entity m : mobs) {
                net.minecraft.entity.EntityLiving ml = (net.minecraft.entity.EntityLiving) m;
                if (ml instanceof net.minecraft.entity.monster.IMob && ml.getAttackTarget() == owner) ml.setAttackTarget(pet);
            }
            if (lvl >= 3) pet.addPotionEffect(new PotionEffect(Potion.resistance.id, 40, 1, true));
        } else if (pet instanceof EntitySnapper && close && owner.isInsideOfMaterial(net.minecraft.block.material.Material.water)) {
            owner.setAir(300);
        } else if (pet instanceof EntityEhu && pet.ticksExisted % 40 == 0) {
            // Пастух: детёныши растут и животные готовы к новому приплоду быстрее
            double r = lvl >= 3 ? 12 : 8;
            int step = lvl >= 3 ? 400 : 200;
            @SuppressWarnings("unchecked")
            List<Entity> animals = pet.worldObj.getEntitiesWithinAABB(net.minecraft.entity.passive.EntityAnimal.class,
                pet.boundingBox.expand(r, 4, r));
            for (Entity a : animals) {
                net.minecraft.entity.passive.EntityAnimal an = (net.minecraft.entity.passive.EntityAnimal) a;
                int age = an.getGrowingAge();
                if (age < 0) an.setGrowingAge(Math.min(0, age + step));
                else if (age > 0) an.setGrowingAge(Math.max(0, age - step));
            }
        } else if (pet instanceof EntityHusk && close) {
            if (owner.isPotionActive(Potion.hunger)) owner.removePotionEffect(Potion.hunger.id);
            if (owner.isPotionActive(Potion.poison)) owner.removePotionEffect(Potion.poison.id);
            if (lvl >= 3) {
                @SuppressWarnings("unchecked")
                List<Entity> mobs = pet.worldObj.getEntitiesWithinAABB(net.minecraft.entity.EntityLiving.class, owner.boundingBox.expand(6, 3, 6));
                for (Entity m : mobs) {
                    if (m instanceof net.minecraft.entity.monster.IMob) ((EntityLivingBase) m).addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 1));
                }
            }
        }
    }

    /** +25% здоровья за уровень (модификатор не сохраняется - ставим заново при каждой загрузке). */
    private static void applyStats(EntityLivingBase pet) {
        IAttributeInstance hp = pet.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        AttributeModifier old = hp.getModifier(HP);
        if (old != null) hp.removeModifier(old);
        int lvl = level(pet);
        if (lvl > 0) hp.applyModifier(new AttributeModifier(HP, "Дрессировка", 0.25 * lvl, 1).setSaved(false));
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (!e.world.isRemote && e.entity instanceof EntityDivineRPGTameable && level(e.entity) > 0) {
            applyStats((EntityLivingBase) e.entity);
        }
    }

    /** +20% урона за уровень; Фиракрикс 3 поджигает. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        Entity src = e.source.getEntity();
        if (!(src instanceof EntityDivineRPGTameable) || src.worldObj.isRemote || e.entityLiving instanceof EntityPlayer
            || com.divinerpgextended.perk.Protection.spare(src, e.entityLiving)) return;
        int lvl = level(src);
        if (lvl <= 0) return;
        e.ammount *= 1.0F + 0.2F * lvl;
        com.divinerpgextended.debug.Debug.note(src, "Питомец уровня " + lvl + ": урон x" + (1.0F + 0.2F * lvl));
        if (src instanceof EntityFyracryx && lvl >= 3) e.entityLiving.setFire(4);
    }

    /** Летающий Paratiku не разбивается. */
    @SubscribeEvent
    public void onFall(LivingFallEvent e) {
        if (e.entityLiving instanceof EntityParatiku && level(e.entityLiving) >= 1) e.distance = 0;
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        if (!(e.entityLiving instanceof EntityDivineRPGTameable) || e.entityLiving.worldObj.isRemote) return;
        EntityDivineRPGTameable pet = (EntityDivineRPGTameable) e.entityLiving;
        int lvl = level(pet);
        if (lvl <= 0 || !pet.isTamed() || !(pet.getOwner() instanceof EntityPlayer)) return;
        EntityPlayer owner = (EntityPlayer) pet.getOwner();
        if (owner.worldObj != pet.worldObj) return;
        if (pet instanceof EntityParatiku) paratiku(pet, owner, lvl);
        if (livestock(pet)) livestockSkills(pet, owner, lvl);
        if (pet.ticksExisted % 40 != 0 || owner.getDistanceSqToEntity(pet) > 64 || lvl < 2) return;
        if (pet instanceof EntityGolemOfRejuv) owner.addPotionEffect(new PotionEffect(Potion.regeneration.id, 60, lvl >= 3 ? 1 : 0, true));
        if (pet instanceof EntityFyracryx) owner.addPotionEffect(new PotionEffect(Potion.fireResistance.id, 60, 0, true));
        if (pet instanceof EntitySeimer) {
            owner.addPotionEffect(new PotionEffect(Potion.nightVision.id, 260, 0, true));
            if (lvl >= 3) owner.addPotionEffect(new PotionEffect(Potion.waterBreathing.id, 60, 0, true));
        }
    }

    /** Paratiku: держится на высоте хозяина (летает), с уровня 2 - собирает предметы и приносит их хозяину. */
    private static void paratiku(EntityDivineRPGTameable pet, EntityPlayer owner, int lvl) {
        if (pet.isSitting()) return;
        double targetY = owner.posY + 1.5;
        if (pet.posY < targetY - 0.5 || pet.getDistanceSqToEntity(owner) > 9) {
            pet.motionY = Math.max(pet.motionY, Math.min(0.3, (targetY - pet.posY) * 0.1));
        }
        pet.fallDistance = 0;
        if (pet.getDistanceSqToEntity(owner) > 24 * 24) pet.setLocationAndAngles(owner.posX, owner.posY + 1.5, owner.posZ,
            pet.rotationYaw, 0);
        if (lvl < 2 || pet.ticksExisted % 10 != 0) return;
        double r = lvl >= 3 ? 14 : 8;
        @SuppressWarnings("unchecked")
        List<EntityItem> items = pet.worldObj.getEntitiesWithinAABB(EntityItem.class, pet.boundingBox.expand(r, r / 2, r));
        for (EntityItem it : items) {
            if (it.isDead || it.delayBeforeCanPickup > 0) continue;
            ItemStack s = it.getEntityItem();
            int before = s.stackSize;
            if (owner.inventory.addItemStackToInventory(s)) {
                it.setDead();
            } else if (s.stackSize == before) {
                continue; // инвентарь полон - оставляем лежать
            }
            pet.worldObj.playSoundAtEntity(owner, "random.pop", 0.2F, 1.6F);
        }
    }
}
