package com.divinerpgextended.perk;

import java.util.Iterator;
import java.util.List;

import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Зубастое оружие и молоты (с 0.99.113, вместе с Лиоплевродоном - поздним боссом):
 * <ul>
 * <li>Зубастый меч - урон {@link #SWORD_DAMAGE} (между клинками Апалачии 29 и Небесного терна 31: Лиоплевродон - между этими
 * мирами; до 0.99.120 было 35, почти как халит 36) и кровотечение: задетый моб {@link #BLEED_SECONDS} с теряет
 * {@link #BLEED_DAMAGE} в секунду сквозь броню (игроков не трогает).</li>
 * <li>Зубастый молот - урон {@link #MAUL_DAMAGE} и удар по площади: мобы того же рода в {@link #WAVE_R} блоках от цели
 * получают {@link #MAUL_DAMAGE} (как волна бедрокового молота, игроков и питомцев не задевает).</li>
 * <li>Все молоты - настоящая перезарядка {@link #MAUL_COOLDOWN} тиков: удары чаще не проходят (клиент не даёт и замахнуться,
 * {@link com.divinerpgextended.client.MaulSwingClient}).</li>
 * </ul>
 * Якоря крабовый и лиоплевродона больше не крафтятся: это призывы, они падают с Карцинуса и Лиоплевродона.
 */
public class AquatoothEffects {

    public static final float SWORD_DAMAGE = 30F, MAUL_DAMAGE = 25F, BLEED_DAMAGE = 4F;
    public static final int BLEED_SECONDS = 5, MAUL_COOLDOWN = 20;
    public static final double WAVE_R = 3.5;
    private static final String BLEED = "dreBleed", BLEED_BY = "dreBleedBy";
    /** Тик последнего удара молотом (данные игрока). */
    public static final String SWING = "dreMaulSwing";
    /** Для самопроверки: без перезарядки (прочие проверки бьют молотом по нескольку раз за тик). */
    public static boolean testNoCooldown;
    public static final DamageSource BLEEDING = new DamageSource("dre.bleed").setDamageBypassesArmor();
    private static boolean inWave;

    /** После регистрации предметов Divine: урон зубастого оружия, без рецептов призывных якорей. */
    public static void postInit() {
        setDamage(VanillaItemsWeapons.aquatoothSword, SWORD_DAMAGE);
        setDamage(VanillaItemsWeapons.aquatoothMaul, MAUL_DAMAGE);
        removeRecipes(VanillaItemsWeapons.crabAnchor);
        removeRecipes(VanillaItemsWeapons.liopleurodonAnchor);
    }

    static void setDamage(Item i, float d) {
        if (!(i instanceof ItemSword)) return;
        try {
            ReflectionHelper.setPrivateValue(ItemSword.class, (ItemSword) i, d, "field_150934_a");
        } catch (Throwable t) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Урон " + i.getUnlocalizedName() + " не изменён: " + t);
        }
    }

    /** Урон меча-основы (для самопроверки). */
    public static float damage(Item i) {
        return i instanceof ItemSword ? (Float) ReflectionHelper.getPrivateValue(ItemSword.class, (ItemSword) i, "field_150934_a") : -1F;
    }

    @SuppressWarnings("unchecked")
    static void removeRecipes(Item item) {
        if (item == null) return;
        for (Iterator<IRecipe> it = CraftingManager.getInstance().getRecipeList().iterator(); it.hasNext();) {
            ItemStack out = it.next().getRecipeOutput();
            if (out != null && out.getItem() == item) it.remove();
        }
    }

    public static boolean maul(ItemStack s) {
        return MeleeRoles.kind(s) == MeleeRoles.Kind.MAUL;
    }

    /** Молот: удар раньше перезарядки не проходит (сервер; клиент и не даёт замахнуться). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(AttackEntityEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (p.worldObj.isRemote || testNoCooldown || !maul(p.getHeldItem())) return;
        long now = p.worldObj.getTotalWorldTime(), last = p.getEntityData().getLong(SWING);
        if (p.getEntityData().hasKey(SWING) && now - last < MAUL_COOLDOWN - 2 && now >= last) {
            e.setCanceled(true);
            return;
        }
        p.getEntityData().setLong(SWING, now);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || inWave) return;
        Entity a = e.source.getEntity();
        if (!(a instanceof EntityPlayer) || e.source.getSourceOfDamage() != a || a == v) return;
        EntityPlayer p = (EntityPlayer) a;
        ItemStack held = p.getHeldItem();
        if (held == null || Protection.spare(p, v)) return;
        if (held.getItem() == VanillaItemsWeapons.aquatoothSword && !(v instanceof EntityPlayer)) {
            v.getEntityData().setLong(BLEED, v.worldObj.getTotalWorldTime() + BLEED_SECONDS * 20);
            v.getEntityData().setString(BLEED_BY, p.getUniqueID().toString());
        }
        if (held.getItem() == VanillaItemsWeapons.aquatoothMaul) wave(p, v);
    }

    /** Удар по площади зубастого молота. */
    static int wave(EntityPlayer p, EntityLivingBase center) {
        @SuppressWarnings("unchecked")
        List<EntityLivingBase> l = center.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, center.boundingBox.expand(WAVE_R, 1.5, WAVE_R));
        int n = 0;
        inWave = true;
        try {
            for (EntityLivingBase o : l) {
                if (o == center || o == p || o instanceof EntityPlayer || o instanceof com.divinerpgextended.dummy.EntityTrainingDummy || !o.isEntityAlive()
                    || o.getDistanceToEntity(center) > WAVE_R + 1) continue;
                if (!ColorGear.sameKind(o, center) || Protection.spare(p, o)) continue;
                o.hurtResistantTime = 0;
                o.attackEntityFrom(DamageSource.causePlayerDamage(p), MAUL_DAMAGE);
                n++;
            }
        } finally {
            inWave = false;
        }
        if (center.worldObj instanceof WorldServer)
            ((WorldServer) center.worldObj).func_147487_a("splash", center.posX, center.posY + 0.5, center.posZ, 40, WAVE_R * 0.5, 0.3, WAVE_R * 0.5, 0.2);
        center.worldObj.playSoundAtEntity(center, "random.splash", 0.8F, 0.8F);
        return n;
    }

    /** Кровотечение: раз в секунду, пока не кончится. */
    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || v.ticksExisted % 20 != 0) return;
        long until = v.getEntityData().getLong(BLEED);
        if (until == 0) return;
        if (v.worldObj.getTotalWorldTime() > until || v instanceof EntityPlayer) {
            v.getEntityData().removeTag(BLEED);
            return;
        }
        v.hurtResistantTime = 0;
        v.attackEntityFrom(BLEEDING, BLEED_DAMAGE);
        if (v.worldObj instanceof WorldServer)
            ((WorldServer) v.worldObj).func_147487_a("reddust", v.posX, v.posY + v.height * 0.6, v.posZ, 8, v.width * 0.4, v.height * 0.3, v.width * 0.4, 0);
    }

    /** Кровоточит ли (для самопроверки). */
    public static boolean bleeding(EntityLivingBase v) {
        return v.getEntityData().getLong(BLEED) > v.worldObj.getTotalWorldTime();
    }
}
