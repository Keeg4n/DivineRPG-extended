package com.divinerpgextended.magic;

import java.util.List;

import net.divinerpg.utils.events.ArcanaHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;

import com.divinerpgextended.asm.ArcanaHooks;
import com.divinerpgextended.cold.ColdHandler;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Эффекты арканных зелий у игрока: хранятся в сохраняемых данных игрока (до какого тика верхнего мира и уровень),
 * переживают перезаход и смерть не переживают. Клиенту отправляется список для значков у полоски арканы.
 */
public class ArcaneEffects {

    static final String TAG = "dreArcEffects";

    private static long now() {
        MinecraftServer s = MinecraftServer.getServer();
        return s != null && s.worldServers.length > 0 ? s.worldServers[0].getTotalWorldTime() : 0;
    }

    private static NBTTagCompound data(EntityPlayer p) {
        NBTTagCompound ps = p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!p.getEntityData().hasKey(EntityPlayer.PERSISTED_NBT_TAG)) p.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, ps);
        if (!ps.hasKey(TAG)) ps.setTag(TAG, new NBTTagCompound());
        return ps.getCompoundTag(TAG);
    }

    /** Уровень эффекта (1 обычное, 2 сильное) или 0, если его нет. На клиенте - по присланному списку. */
    public static int level(EntityPlayer p, ArcaneEffect e) {
        if (p == null) return 0;
        if (p.worldObj.isRemote) return com.divinerpgextended.client.ArcaneEffectsHud.level(e);
        NBTTagCompound d = data(p);
        return d.getLong(e.key()) > now() ? Math.max(1, d.getInteger(e.key() + "L")) : 0;
    }

    public static boolean has(EntityPlayer p, ArcaneEffect e) {
        return level(p, e) > 0;
    }

    public static int left(EntityPlayer p, ArcaneEffect e) {
        return (int) Math.max(0, data(p).getLong(e.key()) - now());
    }

    /** Наложить эффект (дольше или сильнее уже действующего - заменяет). */
    public static void apply(EntityPlayer p, ArcaneEffect e, int level, int ticks) {
        if (p.worldObj.isRemote) return;
        NBTTagCompound d = data(p);
        if (has(p, e) && level(p, e) > level && left(p, e) > ticks) return;
        d.setLong(e.key(), now() + ticks);
        d.setInteger(e.key() + "L", level);
        sync(p);
    }

    public static void clear(EntityPlayer p) {
        data(p).func_150296_c().clear();
        sync(p);
    }

    public static void sync(EntityPlayer p) {
        if (!(p instanceof EntityPlayerMP) || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        ArcaneEffect[] all = ArcaneEffect.values();
        int[] ticks = new int[all.length], lv = new int[all.length];
        for (ArcaneEffect e : all) {
            ticks[e.ordinal()] = left(p, e);
            lv[e.ordinal()] = level(p, e);
        }
        ColdHandler.NET.sendTo(new com.divinerpgextended.arcana.MerchantMessages.Effects(ticks, lv), (EntityPlayerMP) p);
    }

    /** Выпить зелье: эффект себе (у общего - и игрокам в 8 блоках) или мгновенное действие. */
    public static void drink(EntityPlayer p, ArcaneEffect e, boolean strong, boolean longer, boolean shared) {
        if (p.worldObj.isRemote) return;
        List<EntityPlayer> who = new java.util.ArrayList<EntityPlayer>();
        who.add(p);
        if (shared) for (Object o : p.worldObj.playerEntities) if (o != p && ((EntityPlayer) o).getDistanceSqToEntity(p) <= 64) who.add((EntityPlayer) o);
        int lvl = strong && e.leveled() ? 2 : 1;
        int ticks = e.seconds * 20 * (longer ? 2 : 1);
        for (EntityPlayer t : who) {
            if (e == ArcaneEffect.WAY_HOME) home(t);
            else if (e == ArcaneEffect.CLARITY) ArcanaRules.buff(t, lvl + 1, ticks);
            else apply(t, e, lvl, ticks);
        }
    }

    /** Путь домой: к кровати в этом мире, иначе - к кровати (или точке появления) верхнего мира. */
    static void home(EntityPlayer p) {
        if (!(p instanceof EntityPlayerMP)) return;
        EntityPlayerMP mp = (EntityPlayerMP) p;
        int dim = p.dimension;
        ChunkCoordinates bed = p.getBedLocation(dim);
        if (bed == null) {
            dim = 0;
            bed = p.getBedLocation(0);
        }
        WorldServer ws = mp.mcServer.worldServerForDimension(dim);
        ChunkCoordinates at = bed != null ? EntityPlayer.verifyRespawnCoordinates(ws, bed, p.isSpawnForced(dim)) : null;
        if (at == null) at = ws.getSpawnPoint();
        if (dim != p.dimension) {
            mp.mcServer.getConfigurationManager().transferPlayerToDimension(mp, dim, new NoPortal(ws));
        }
        int y = Math.max(at.posY, ws.getTopSolidOrLiquidBlock(at.posX, at.posZ));
        mp.playerNetServerHandler.setPlayerLocation(at.posX + 0.5, y, at.posZ + 0.5, p.rotationYaw, p.rotationPitch);
        mp.fallDistance = 0;
        ws.playSoundEffect(at.posX + 0.5, y, at.posZ + 0.5, "mob.endermen.portal", 1F, 1.2F);
    }

    /** Перенос между мирами без портала. */
    static class NoPortal extends net.minecraft.world.Teleporter {
        NoPortal(WorldServer w) {
            super(w);
        }

        @Override
        public void placeInPortal(Entity e, double x, double y, double z, float yaw) {}

        @Override
        public boolean placeInExistingPortal(Entity e, double x, double y, double z, float yaw) {
            return true;
        }

        @Override
        public boolean makePortal(Entity e) {
            return true;
        }

        @Override
        public void removeStalePortalLocations(long t) {}
    }

    // ---------------- действие эффектов ----------------

    /** Множитель цены выстрела (Собранность). */
    public static float costFactor(EntityPlayer p) {
        int l = level(p, ArcaneEffect.COMPOSURE);
        return l == 0 ? 1F : l == 1 ? 0.75F : 0.6F;
    }

    static boolean melee(DamageSource s) {
        return s.getEntity() != null && s.getEntity() == s.getSourceOfDamage() && !s.isProjectile() && !s.isMagicDamage() && !s.isExplosion()
            && !ArcanaCombat.ARCANE.equals(s.damageType);
    }

    /** По игроку: оберег от чар, каменная кожа, отвод снарядов, стойкость, колючки. До защиты арканой. */
    @SubscribeEvent
    public void onHurtTaken(LivingHurtEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        float k = 1F;
        int l;
        if (ArcanaCombat.incoming(e.source) && (l = level(p, ArcaneEffect.CHARM_WARD)) > 0) k *= l == 1 ? 0.7F : 0.55F;
        boolean melee = melee(e.source);
        if (melee && (l = level(p, ArcaneEffect.STONE_SKIN)) > 0) k *= l == 1 ? 0.85F : 0.75F;
        if (e.source.isProjectile() && (l = level(p, ArcaneEffect.DEFLECTION)) > 0) k *= l == 1 ? 0.65F : 0.5F;
        if ((l = level(p, ArcaneEffect.FORTITUDE)) > 0) k *= l == 1 ? 0.9F : 0.85F;
        if (melee && (l = level(p, ArcaneEffect.BRAMBLE_HIDE)) > 0 && e.source.getEntity() instanceof EntityLivingBase && !"thorns".equals(e.source.damageType)) {
            e.source.getEntity().attackEntityFrom(DamageSource.causeThornsDamage(p), e.ammount * (l == 1 ? 0.3F : 0.45F));
        }
        e.ammount *= k;
    }

    /** От игрока: чародейская мощь, боевой раж, твёрдая рука, голод арканы. После правил арканного урона. */
    @SubscribeEvent
    public void onHurtDealt(LivingHurtEvent e) {
        if (e.entityLiving.worldObj.isRemote || !(e.source.getEntity() instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) e.source.getEntity();
        Entity src = e.source.getSourceOfDamage();
        boolean arcane = src != null && src.getEntityData().hasKey(ArcanaCombat.DMG) || ArcanaCombat.ARCANE.equals(e.source.damageType);
        int l;
        if (arcane && (l = level(p, ArcaneEffect.SORCERY)) > 0) e.ammount *= l == 1 ? 1.2F : 1.35F;
        if (melee(e.source) && (l = level(p, ArcaneEffect.BATTLE_FERVOR)) > 0) e.ammount *= l == 1 ? 1.15F : 1.25F;
        if (e.source.isProjectile() && !arcane && (l = level(p, ArcaneEffect.STEADY_HAND)) > 0) e.ammount *= l == 1 ? 1.15F : 1.25F;
        if (arcane && (l = level(p, ArcaneEffect.ARCANE_HUNGER)) > 0) {
            ArcanaHelper h = ArcanaHelper.getProperties(p);
            if (h != null) h.setBarValue(Math.min(ArcanaHooks.max(p), h.getBarValue() + e.ammount * (l == 1 ? 0.05F : 0.08F)));
        }
    }

    /** Твёрдая рука: шанс, что стрела вернётся. */
    @SubscribeEvent
    public void onLoose(ArrowLooseEvent e) {
        EntityPlayer p = e.entityPlayer;
        int l = level(p, ArcaneEffect.STEADY_HAND);
        if (l == 0 || p.worldObj.isRemote || p.capabilities.isCreativeMode) return;
        if (p.worldObj.rand.nextFloat() < (l == 1 ? 0.2F : 0.3F)) p.inventory.addItemStackToInventory(new ItemStack(Items.arrow));
    }

    /** Затишье: враждебные мобы рядом с таким игроком появляются реже. */
    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn e) {
        if (e.world.isRemote || !(e.entityLiving instanceof IMob)) return;
        for (Object o : e.world.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            int l = level(p, ArcaneEffect.STILLNESS);
            if (l == 0 || p.getDistanceSq(e.x, e.y, e.z) > 48 * 48) continue;
            if (e.world.rand.nextFloat() < (l == 1 ? 0.7F : 0.9F)) e.setResult(Event.Result.DENY);
            return;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        EntityPlayer p = e.player;
        if (e.phase != TickEvent.Phase.END || p.worldObj.isRemote) return;
        int l = level(p, ArcaneEffect.CALL_TO_ARMS);
        if (l > 0 && p.ticksExisted % (l == 1 ? 40 : 20) == 0) callToArms(p);
        if (p.ticksExisted % 100 == 0 && !data(p).func_150296_c().isEmpty()) sync(p);
    }

    /** Зов битвы: лишняя попытка появления враждебного моба этого места в 16-32 блоках (пределы и ранги как обычно). */
    @SuppressWarnings("unchecked")
    static void callToArms(EntityPlayer p) {
        World w = p.worldObj;
        int near = 0;
        for (Object o : w.getEntitiesWithinAABB(EntityLiving.class, p.boundingBox.expand(32, 16, 32))) if (o instanceof IMob) near++;
        if (near >= 16) return;
        double a = w.rand.nextDouble() * Math.PI * 2, d = 16 + w.rand.nextDouble() * 16;
        int x = MathHelper.floor_double(p.posX + Math.cos(a) * d), z = MathHelper.floor_double(p.posZ + Math.sin(a) * d);
        BiomeGenBase b = w.getBiomeGenForCoords(x, z);
        List<BiomeGenBase.SpawnListEntry> pool = b.getSpawnableList(EnumCreatureType.monster);
        if (pool == null || pool.isEmpty()) return;
        BiomeGenBase.SpawnListEntry pick = pool.get(w.rand.nextInt(pool.size()));
        for (int y = (int) p.posY + 8; y > (int) p.posY - 8; y--) {
            if (!w.getBlock(x, y - 1, z).getMaterial().isSolid() || !w.isAirBlock(x, y, z) || !w.isAirBlock(x, y + 1, z)) continue;
            try {
                EntityLiving m = (EntityLiving) pick.entityClass.getConstructor(World.class).newInstance(w);
                m.setLocationAndAngles(x + 0.5, y, z + 0.5, w.rand.nextFloat() * 360, 0);
                Event.Result r = net.minecraftforge.event.ForgeEventFactory.canEntitySpawn(m, w, (float) m.posX, (float) m.posY, (float) m.posZ);
                if (r == Event.Result.DENY || r == Event.Result.DEFAULT && !m.getCanSpawnHere()) return;
                w.spawnEntityInWorld(m);
                m.onSpawnWithEgg(null);
            } catch (Throwable ignored) {}
            return;
        }
    }

    /** Холод Айсики не копится под Внутренним жаром (для {@link ColdHandler}). */
    public static boolean warm(EntityPlayer p) {
        return has(p, ArcaneEffect.INNER_HEAT);
    }

    /** Удача рыбака: множитель ожидания поклёвки и редкой добычи (для удочек аддона). */
    public static float biteFactor(EntityPlayer p) {
        int l = level(p, ArcaneEffect.FISHERS_FORTUNE);
        return l == 0 ? 1F : l == 1 ? 0.6F : 0.45F;
    }

    public static float rareFactor(EntityPlayer p) {
        int l = level(p, ArcaneEffect.FISHERS_FORTUNE);
        return l == 0 ? 1F : l == 1 ? 1.5F : 2F;
    }
}
