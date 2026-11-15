package com.divinerpgextended.boss;

import java.util.Map;
import java.util.WeakHashMap;

import net.divinerpg.entities.iceika.projectile.EntityCarol;
import net.divinerpg.entities.iceika.projectile.EntityMusic;
import net.divinerpg.entities.vanilla.projectile.EntityShooterBullet;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.divinerpg.utils.items.VanillaItemsWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Nether ranged weapons - each has its own role.
 * <ul>
 * <li>Ghast cannon: a heavy shot. Knocks down the Watcher's charge and windup ({@link WatcherHunt}); against the Ayeraco horde dragon -
 * fire breath and the attack approach (it goes around for another circle).</li>
 * <li>Maelstrom: a burst; the projectile pierces up to {@link #PIERCE} targets and passes through the shield of the blue Ayeraco.</li>
 * <li>Sound of Music and Sound of Carols: no more than once per {@link #MUSIC_EVERY} ticks (the sound no longer overlaps, an extra
 * click does not spend a charge), the notes fly without falling and bounce off walls up to {@link #BOUNCES} times. Music deals double against boss
 * minions; Carols slows the target, and against the Frost Colossus and Snow Glacon deals 1.5 times.</li>
 * <li>King of Scorchers - see {@link KingRace} (Maelstrom and Sound of Music against him are described there too).</li>
 * <li>Reload ({@link Reload}): ghast cannon + ghast tears, Maelstrom + purple clots - {@link #RELOAD}
 * shots per piece; only for those who defeated the King of Scorchers.</li>
 * </ul>
 */
public class NetherArsenal {

    public static final int PIERCE = 3, MUSIC_EVERY = 8, BOUNCES = 2, RELOAD = 50;
    public static final float MINION_MULT = 2F, ICE_MULT = 1.5F;
    static final String PIERCED = "drePierced", BOUNCED = "dreBounced";

    // ---------------------------------------------------------------- what shot with what

    static boolean bulletOf(DamageSource src, String tex, Item gun) {
        Entity bullet = src.getSourceOfDamage(), shooter = src.getEntity();
        if (!(bullet instanceof EntityShooterBullet)) return false;
        String t = ((EntityShooterBullet) bullet).getTextureName();
        if (t != null) return t.toLowerCase().contains(tex);
        return shooter instanceof EntityPlayer && ((EntityPlayer) shooter).getHeldItem() != null && ((EntityPlayer) shooter).getHeldItem().getItem() == gun;
    }

    /** A ghast cannon bullet. */
    public static boolean ghastCannon(DamageSource src) {
        return bulletOf(src, "ghast", VanillaItemsWeapons.ghastCannon);
    }

    /** A Maelstrom projectile. */
    public static boolean maelstrom(DamageSource src) {
        return bulletOf(src, "maelstrom", VanillaItemsWeapons.maelstrom);
    }

    // ---------------------------------------------------------------- damage

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote) return;
        Entity proj = e.source.getSourceOfDamage();
        if (proj instanceof EntityMusic && BossMinions.isMinion(v)) e.ammount *= MINION_MULT;
        if (proj instanceof EntityCarol) {
            v.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 1));
            if (v instanceof com.divinerpgextended.entity.EntityFrostColossus || v instanceof com.divinerpgextended.entity.EntitySnowGlacon) e.ammount *= ICE_MULT;
        }
        // horde dragon: the ghast cannon knocks down the breath and the approach
        if (ghastCannon(e.source) && AyeracoHorde.member(v)) {
            long now = v.worldObj.getTotalWorldTime();
            v.getEntityData().setLong(AyeracoHorde.BREATH, 0);
            AyeracoHorde.phase(v, AyeracoHorde.RETREAT, now + AyeracoHorde.RETREAT_TICKS);
            v.worldObj.playSoundAtEntity(v, "random.break", 1.5F, 0.6F);
        }
        if (maelstrom(e.source)) pierce((EntityShooterBullet) proj, v);
    }

    /** A Maelstrom projectile pierces the target: the next flies on the same, until it has pierced {@link #PIERCE}. */
    static void pierce(EntityShooterBullet b, EntityLivingBase hit) {
        int n = b.getEntityData().getInteger(PIERCED) + 1;
        if (n >= PIERCE || !(b.getThrower() instanceof EntityLivingBase)) return;
        double sp = Math.sqrt(b.motionX * b.motionX + b.motionY * b.motionY + b.motionZ * b.motionZ);
        if (sp < 0.05) return;
        double k = (hit.width + 0.8) / sp;
        EntityShooterBullet nb = b.getTextureName() != null ? new EntityShooterBullet(b.worldObj, b.getThrower(), damage(b), b.getTextureName())
            : new EntityShooterBullet(b.worldObj, b.getThrower(), damage(b), VanillaItemsWeapons.maelstrom);
        nb.setPosition(b.posX + b.motionX * k, b.posY + b.motionY * k, b.posZ + b.motionZ * k);
        nb.motionX = b.motionX;
        nb.motionY = b.motionY;
        nb.motionZ = b.motionZ;
        nb.getEntityData().setInteger(PIERCED, n);
        b.worldObj.spawnEntityInWorld(nb);
    }

    private static java.lang.reflect.Field bulletDamage;

    static float damage(EntityShooterBullet b) {
        try {
            if (bulletDamage == null) bulletDamage = ReflectionHelper.findField(EntityShooterBullet.class, "damage");
            return bulletDamage.getFloat(b);
        } catch (Throwable t) {
            return 12F;
        }
    }

    // ---------------------------------------------------------------- music weapons

    /** The last shot with a music weapon (world tick); the player whose extra shot was just cancelled. */
    static final Map<EntityPlayer, Long> LAST_NOTE = new WeakHashMap<EntityPlayer, Long>();
    static final Map<EntityPlayer, Long> MUTE = new WeakHashMap<EntityPlayer, Long>();

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof EntityMusic || e.entity instanceof EntityCarol)) return;
        if (!(((EntityThrowable) e.entity).getThrower() instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) ((EntityThrowable) e.entity).getThrower();
        long now = e.world.getTotalWorldTime();
        Long last = LAST_NOTE.get(p);
        if (last != null && now - last < MUSIC_EVERY && now >= last) {
            e.setCanceled(true);
            MUTE.put(p, now);
            ItemStack held = p.getHeldItem();
            if (held != null && held.getItemDamage() > 0) held.setItemDamage(held.getItemDamage() - 1); // the charge will come back: Divine will remove it later
            return;
        }
        LAST_NOTE.put(p, now);
    }

    /** The sound of a cancelled music weapon shot should not play either. */
    @SubscribeEvent
    public void onSound(PlaySoundAtEntityEvent e) {
        if (!(e.entity instanceof EntityPlayer) || e.entity.worldObj.isRemote || e.name == null) return;
        Long m = MUTE.get(e.entity);
        if (m != null && m == e.entity.worldObj.getTotalWorldTime() && (e.name.contains("soundOfMusic") || e.name.contains("soundOfCarols"))) e.setCanceled(true);
    }

    /** Notes without falling and with bouncing off walls. */
    @SubscribeEvent
    public void onTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        for (Object o : e.world.loadedEntityList) {
            if (!(o instanceof EntityMusic || o instanceof EntityCarol)) continue;
            EntityThrowable n = (EntityThrowable) o;
            if (n.isDead) continue;
            n.motionY += 0.03 * 0.99; // throw gravity - back
            bounce(e.world, n);
        }
    }

    /** A note bouncing off a block in its way this tick (no more than {@link #BOUNCES} times). */
    public static void bounce(World w, EntityThrowable n) {
        int b = n.getEntityData().getInteger(BOUNCED);
        if (b >= BOUNCES) return;
        Vec3 from = Vec3.createVectorHelper(n.posX, n.posY, n.posZ), to = Vec3.createVectorHelper(n.posX + n.motionX, n.posY + n.motionY, n.posZ + n.motionZ);
        MovingObjectPosition hit = w.func_147447_a(from, to, false, true, false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        switch (hit.sideHit) {
            case 0: case 1: n.motionY = -n.motionY; break;
            case 2: case 3: n.motionZ = -n.motionZ; break;
            default: n.motionX = -n.motionX;
        }
        n.setPosition(hit.hitVec.xCoord - (to.xCoord - from.xCoord) * 0.05, hit.hitVec.yCoord - (to.yCoord - from.yCoord) * 0.05, hit.hitVec.zCoord - (to.zCoord - from.zCoord) * 0.05);
        n.velocityChanged = true;
        n.getEntityData().setInteger(BOUNCED, b + 1);
        w.playSoundAtEntity(n, "note.pling", 0.5F, 1.2F + b * 0.3F);
    }

    // ---------------------------------------------------------------- reload

    /** Ghast cannon + ghast tears or Maelstrom + purple clots: {@link #RELOAD} shots per piece. */
    public static class Reload implements IRecipe {

        static Item ammo(Item gun) {
            return gun == VanillaItemsWeapons.ghastCannon ? Items.ghast_tear : gun == VanillaItemsWeapons.maelstrom ? VanillaItemsOther.purpleBlaze : null;
        }

        /** The cannon in the grid and the number of rounds, or null. */
        static Object[] parse(InventoryCrafting inv) {
            ItemStack gun = null;
            int n = 0;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s == null) continue;
                if (ammo(s.getItem()) != null) {
                    if (gun != null) return null;
                    gun = s;
                }
            }
            if (gun == null || gun.getItemDamage() == 0) return null;
            Item a = ammo(gun.getItem());
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s == null || s == gun) continue;
                if (s.getItem() != a) return null;
                n++;
            }
            return n == 0 ? null : new Object[] { gun, n };
        }

        @Override
        public boolean matches(InventoryCrafting inv, World w) {
            if (parse(inv) == null) return false;
            EntityPlayer p = crafter(inv);
            return p != null && BossProgress.has(p, Boss.KING_OF_SCORCHERS);
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            Object[] g = parse(inv);
            if (g == null) return null;
            ItemStack out = ((ItemStack) g[0]).copy();
            out.stackSize = 1;
            out.setItemDamage(Math.max(0, out.getItemDamage() - RELOAD * (Integer) g[1]));
            return out;
        }

        @Override
        public int getRecipeSize() {
            return 2;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return null;
        }
    }

    /** Who crafts: the player at the result slot of the crafting table (or inventory). */
    static EntityPlayer crafter(InventoryCrafting inv) {
        try {
            Container c = ReflectionHelper.getPrivateValue(InventoryCrafting.class, inv, "eventHandler", "field_70465_c");
            if (c == null) return null;
            for (Object o : c.inventorySlots)
                if (o instanceof SlotCrafting) return ReflectionHelper.getPrivateValue(SlotCrafting.class, (SlotCrafting) o, "thePlayer", "field_75238_b");
        } catch (Throwable t) {
            // someone else's container (autocrafting) - we don't know the player
        }
        return null;
    }

    /** For tests: reload in the 3x3 grid of this player (null - not being crafted). */
    public static ItemStack reloadFor(EntityPlayer p, ItemStack gun, ItemStack ammo, int count) {
        net.minecraft.inventory.ContainerWorkbench c = new net.minecraft.inventory.ContainerWorkbench(p.inventory, p.worldObj, 0, 0, 0) {

            @Override
            public boolean canInteractWith(EntityPlayer pl) {
                return true;
            }
        };
        c.craftMatrix.setInventorySlotContents(0, gun);
        for (int i = 0; i < count; i++) c.craftMatrix.setInventorySlotContents(i + 1, new ItemStack(ammo.getItem()));
        Reload r = new Reload();
        return r.matches(c.craftMatrix, p.worldObj) ? r.getCraftingResult(c.craftMatrix) : null;
    }
}
