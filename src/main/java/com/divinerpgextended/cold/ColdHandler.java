package com.divinerpgextended.cold;

import java.util.HashMap;
import java.util.Map;

import net.divinerpg.utils.blocks.IceikaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.IceikaItems;
import net.divinerpg.utils.items.ItemsFood;
import net.divinerpg.utils.items.VanillaItemsArmor;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import com.divinerpgextended.block.BlockBrazier;
import com.divinerpgextended.item.ArmorData;
import com.divinerpgextended.perk.Perk;
import com.divinerpgextended.perk.PerkEffects;
import com.divinerpgextended.registry.ModBlocks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * Холод Iceika. Шкала 0-100 растёт от нуля до полного замерзания примерно за 4 минуты (в воде вдвое быстрее, в
 * сете Frozen вдвое медленнее). Пороги: 25 - замедление, 50 - сильнее и медленнее копаешь, 75 - слабость и голод,
 * 100 - игрок примерзает: не может двигаться и получает урон, пока не согреется. Огонь, лава или горящая печь в
 * радиусе 4 блоков греют, а 5 секунд у огня дают "Согрет" на минуту. Горячая еда снимает часть холода. Сет Санты
 * полностью защищает и отогревает.
 */
public class ColdHandler {

    public static final SimpleNetworkWrapper NET = NetworkRegistry.INSTANCE.newSimpleChannel("dre_cold");

    private static final String KEY = "dreCold", WARM = "dreWarm", HEAT = "dreHeat", SENT = "dreColdSent";
    public static final float RATE = 100F / 240F; // в секунду
    private static final int WARM_SECONDS = 60, HEAT_SECONDS = 5;
    public static final DamageSource FROSTBITE = new DamageSource("dre.frostbite").setDamageBypassesArmor();

    private static final Map<Item, Integer> FOOD = new HashMap<Item, Integer>();

    public static void init() {
        NET.registerMessage(ColdMessage.Handler.class, ColdMessage.class, 0, Side.CLIENT);
        FOOD.put(ItemsFood.tomato, 25);
        FOOD.put(ItemsFood.hotPie, 50);
        FOOD.put(ItemsFood.advMushroomStew, 40);
        FOOD.put(IceikaItems.eggNog, 35);
        FOOD.put(IceikaItems.fruitCake, 30);
        FOOD.put(IceikaItems.chocolateLog, 20);
        FOOD.put(IceikaItems.peppermints, 10);
        FOOD.put(Items.mushroom_stew, 20);
        FOOD.put(Items.baked_potato, 10);
    }

    private static final Item[] SANTA = { IceikaItems.santaCap, IceikaItems.santaTunic, IceikaItems.santaPants,
        IceikaItems.santaBoots };
    private static final Item[] FROZEN = { VanillaItemsArmor.frozenHelmet, VanillaItemsArmor.frozenBody,
        VanillaItemsArmor.frozenLegs, VanillaItemsArmor.frozenBoots };

    private static NBTTagCompound data(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static boolean inIceika(Entity e) {
        return isIceika(e.worldObj);
    }

    public static boolean isIceika(net.minecraft.world.World w) {
        return w.provider.dimensionId == ConfigurationHelper.iceika;
    }

    /** Огонь, лава или горящая печь рядом. */
    private static boolean nearHeat(EntityPlayer p) {
        int px = (int) Math.floor(p.posX), py = (int) Math.floor(p.posY), pz = (int) Math.floor(p.posZ);
        for (int x = px - 4; x <= px + 4; x++) for (int y = py - 2; y <= py + 2; y++) for (int z = pz - 4; z <= pz + 4; z++) {
            Block b = p.worldObj.getBlock(x, y, z);
            Material m = b.getMaterial();
            if (m == Material.fire || m == Material.lava || b == Blocks.lit_furnace
                || b == IceikaBlocks.coalstoneFurnaceOn || b == ModBlocks.hearth
                || b == ModBlocks.brazier && BlockBrazier.isLit(p.worldObj, x, y, z)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer p = event.player;
        if (event.phase != TickEvent.Phase.END || p.worldObj.isRemote) return;
        Blizzard storm = inIceika(p) ? Blizzard.get(p.worldObj) : null;
        boolean blizzard = storm != null && storm.phase == Blizzard.STORM && Blizzard.exposed(p)
            && !p.capabilities.isCreativeMode;
        if (blizzard && p.ticksExisted % 5 == 0) {
            // ветер сносит
            p.addVelocity(storm.windX * 0.06, 0, storm.windZ * 0.06);
            p.velocityChanged = true;
        }
        if (p.ticksExisted % 20 != 0) return;
        NBTTagCompound d = data(p);
        float cold = d.getFloat(KEY), before = cold;
        int warm = d.getInteger(WARM), heat = d.getInteger(HEAT);

        if (!inIceika(p) || p.capabilities.isCreativeMode) {
            cold = Math.max(0, cold - 5);
            warm = 0;
            heat = 0;
        } else {
            boolean nearHeat = nearHeat(p);
            heat = nearHeat ? heat + 1 : 0;
            if (heat >= HEAT_SECONDS) warm = WARM_SECONDS;
            if (PerkEffects.wears(p, SANTA) || ArmorData.hasEquippedPerk(p, Perk.WARMTH)) cold -= 3; // Санта и "Тепло" отогревают
            else if (nearHeat) cold -= 4;
            else if (warm > 0) cold -= 1;
            else {
                float r = RATE;
                if (p.isInWater()) r *= 2;
                if (blizzard) r *= 3;
                if (PerkEffects.wears(p, FROZEN)) r *= 0.5F;
                if (com.divinerpgextended.trinket.TrinketEffects.wears(p, com.divinerpgextended.trinket.Trinket.WARM_SCARF)) r *= 0.5F;
                if (com.divinerpgextended.trinket.TrinketEffects.wears(p, com.divinerpgextended.trinket.Trinket.ICE_BROOCH)) r *= 0.67F;
                cold += r;
            }
            if (warm > 0) warm--;
            cold = Math.max(0, Math.min(100, cold));
            applyEffects(p, cold);
            if (before < 50 && cold >= 50) p.addChatMessage(new ChatComponentTranslation("dre.chat.cold_freezing"));
            if (before < 100 && cold >= 100) p.addChatMessage(new ChatComponentTranslation("dre.chat.cold_frozen"));
            if (before < 100 && heat == HEAT_SECONDS) p.addChatMessage(new ChatComponentTranslation("dre.chat.cold_warm"));
        }
        d.setFloat(KEY, cold);
        d.setInteger(WARM, warm);
        d.setInteger(HEAT, heat);
        sync(p, d, cold, warm);
    }

    private static void sync(EntityPlayer p, NBTTagCompound d, float cold, int warm) {
        if (!(p instanceof EntityPlayerMP)) return;
        // в Iceika шлём каждую секунду (там же фаза вьюги), вне её - только пока холод не сошёл на нет
        float sent = d.getFloat(SENT);
        if (!inIceika(p) && Math.abs(sent - cold) < 0.01F && p.ticksExisted % 100 != 0) return;
        d.setFloat(SENT, cold);
        Blizzard b = inIceika(p) ? Blizzard.get(p.worldObj) : null;
        NET.sendTo(new ColdMessage(cold, warm, b == null ? Blizzard.CALM : b.phase, b == null ? 0 : b.windX,
            b == null ? 0 : b.windZ), (EntityPlayerMP) p);
    }

    private static void effect(EntityPlayer p, Potion potion, int amp) {
        p.addPotionEffect(new PotionEffect(potion.id, 40, amp, true));
    }

    private static void applyEffects(EntityPlayer p, float cold) {
        if (cold >= 100) {
            // примёрз: стоит на месте, не прыгает, урон раз в 2 секунды
            effect(p, Potion.moveSlowdown, 6);
            effect(p, Potion.jump, 128);
            effect(p, Potion.digSlowdown, 2);
            if (p.ticksExisted % 40 == 0) p.attackEntityFrom(FROSTBITE, 1.0F);
        } else if (cold >= 75) {
            effect(p, Potion.moveSlowdown, 1);
            effect(p, Potion.digSlowdown, 0);
            effect(p, Potion.weakness, 0);
            effect(p, Potion.hunger, 0);
        } else if (cold >= 50) {
            effect(p, Potion.moveSlowdown, 1);
            effect(p, Potion.digSlowdown, 0);
        } else if (cold >= 25) {
            effect(p, Potion.moveSlowdown, 0);
        }
    }

    /** Добавить холод (способности боссов). Не действует в творческом режиме. */
    public static void addCold(EntityPlayer p, float amount) {
        if (p.capabilities.isCreativeMode) return;
        NBTTagCompound d = data(p);
        float cold = Math.max(0, Math.min(100, d.getFloat(KEY) + amount));
        d.setFloat(KEY, cold);
        sync(p, d, cold, d.getInteger(WARM));
    }

    /** Горячая еда снимает часть холода. */
    @SubscribeEvent
    public void onEat(PlayerUseItemEvent.Finish event) {
        EntityPlayer p = event.entityPlayer;
        if (p.worldObj.isRemote || event.item == null) return;
        Integer warmth = FOOD.get(event.item.getItem());
        if (warmth == null) return;
        NBTTagCompound d = data(p);
        float cold = Math.max(0, d.getFloat(KEY) - warmth);
        d.setFloat(KEY, cold);
        sync(p, d, cold, d.getInteger(WARM));
    }

    /** Сет Frozen в Iceika: -10% урона от местных мобов. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer) || !inIceika(event.entityLiving)) return;
        Entity attacker = event.source.getEntity();
        if (attacker == null || !attacker.getClass()
            .getName()
            .startsWith("net.divinerpg.entities.iceika")) return;
        if (PerkEffects.wears((EntityPlayer) event.entityLiving, FROZEN)) {
            event.ammount *= 0.9F;
            com.divinerpgextended.debug.Debug.note(event.entityLiving, "Сет Frozen: от мобов Айсики x0.9");
        }
    }
}
