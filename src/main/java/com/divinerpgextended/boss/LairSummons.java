package com.divinerpgextended.boss;

import net.divinerpg.items.twilight.ItemTwilightSpawner;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.block.BlockBossAltar;
import com.divinerpgextended.world.WorldGenOuterLairs;
import com.divinerpgextended.world.twilight.WorldGenNests;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Bosses are summoned in their lairs, not anywhere (summonOnlyInLairs). We do not touch Divine's summon items - we cancel their
 * use outside a lair, while a click on a lair altar still works (the altar itself accepts the item).
 * <ul>
 * <li>Twilight boss crystals (Twilight Demon, Karot, Eternal Archer, Densos and Reyvor, Soul Fiend,
 * Vamacheron) - only in their own Mortum arena ({@link com.divinerpgextended.world.twilight.MortumArenas}).</li>
 * <li>Mysterious Clock (Ancient Entity) - only on the tomb altar.</li>
 * <li>Call of the Watcher, Infernal Flame - only in their own lairs in the Nether; Ayeraco Horde Horn - only on the altar in the center of the aerie
 * arena in the End ({@link WorldGenOuterLairs}).</li>
 * </ul>
 * The rules are the same for everyone, including creative mode (otherwise bosses ended up anywhere during tests).
 */
public class LairSummons {

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.world.isRemote || !Config.summonOnlyInLairs) return;
        EntityPlayer p = e.entityPlayer;
        ItemStack held = p.getHeldItem();
        if (held == null) return;
        if (e.world.getBlock(e.x, e.y, e.z) instanceof BlockBossAltar) return; // the altar decides itself
        String refuse = refusal(p, held.getItem(), e.x, e.y, e.z);
        if (refuse == null) {
            // big arena bosses: the crystal places them in a free spot of the arena, not right next to the altar
            com.divinerpgextended.world.twilight.MortumArenas.Type t = held.getItem() instanceof ItemTwilightSpawner
                ? com.divinerpgextended.world.twilight.MortumArenas.ofCrystal(held.getItem()) : null;
            int[] c = t == null ? null : com.divinerpgextended.world.twilight.MortumArenas.nearest(e.world, e.x, e.z);
            int[] s = c == null ? null : com.divinerpgextended.world.twilight.MortumArenas.summonBlock(e.world, t, c);
            if (s != null && (s[0] != e.x || s[1] != e.y || s[2] != e.z)) {
                e.useItem = Event.Result.DENY;
                held.getItem().onItemUse(held, p, e.world, s[0], s[1], s[2], 1, 0.5F, 1.0F, 0.5F);
            }
            return;
        }
        e.useItem = Event.Result.DENY;
        p.addChatMessage(new ChatComponentTranslation(refuse));
        ((net.minecraft.world.WorldServer) e.world).func_147487_a("smoke", e.x + 0.5, e.y + 1.2, e.z + 0.5, 12, 0.2, 0.2, 0.2, 0.02);
        e.world.playSoundEffect(e.x + 0.5, e.y + 1, e.z + 0.5, "random.fizz", 0.6F, 1.4F); // the crystal goes out
    }

    // ---------------------------------------------------------------- lair leash

    private static final String HOME = "dreLairHome";

    /** Home of the boss lair [x, floor, z, radius] or null. */
    public static int[] home(net.minecraft.entity.Entity boss) {
        int[] h = boss.getEntityData().getIntArray(HOME);
        return h.length == 4 ? h : null;
    }

    /** The boss was summoned in a lair above a chasm: it does not go past the edge (center, radius, floor). */
    public static void leash(net.minecraft.entity.Entity boss, int cx, int floorY, int cz, int radius) {
        if (!boss.worldObj.isRemote) ArenaCalm.clear(boss.worldObj, cx, floorY, cz, radius);
        boss.getEntityData().setIntArray(HOME, new int[] { cx, floorY, cz, radius });
    }

    /** Bosses summoned by a crystal in a Mortum arena are put on a leash to the arena (the archer - straight onto his platform). */
    @SubscribeEvent
    public void onJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof net.minecraft.entity.EntityLiving) || e.entity.getEntityData().hasKey(HOME)) return;
        if (!(e.entity instanceof net.minecraft.entity.boss.IBossDisplayData || e.entity instanceof net.divinerpg.entities.base.EntityDivineRPGBoss
            || e.entity instanceof net.divinerpg.entities.vanilla.EntityAyeraco)) return;
        if (com.divinerpgextended.world.twilight.TwilightWorld.of(e.world.provider.dimensionId) == com.divinerpgextended.world.twilight.TwilightWorld.MORTUM) {
            int[] c = WorldGenNests.nearest(e.world.getSeed(), e.world.provider.dimensionId, e.entity.posX, e.entity.posZ);
            if (c != null) {
                com.divinerpgextended.world.twilight.MortumArenas.Type t = com.divinerpgextended.world.twilight.MortumArenas.typeAt(e.world, c);
                if (com.divinerpgextended.world.twilight.MortumArenas.inArena(e.world, t, e.entity.posX, e.entity.posY, e.entity.posZ)) {
                    int floor = com.divinerpgextended.world.twilight.MortumArenas.floorY(t) + 1;
                    leash(e.entity, c[0], floor, c[2], t.leash);
                    int[] spot = com.divinerpgextended.world.twilight.MortumArenas.bossSpot(t);
                    if (spot != null) e.entity.setPosition(c[0] + 0.5, floor + spot[1], c[2] + spot[2] + 0.5);
                    return;
                }
            }
        }
        for (int type = 0; type < 3; type++) {
            if (!WorldGenOuterLairs.inside(e.world, type, e.entity.posX, e.entity.posY, e.entity.posZ)) continue;
            int[] c = WorldGenOuterLairs.nearest(e.world.getSeed(), type, e.entity.posX, e.entity.posZ);
            if (type == WorldGenOuterLairs.WATCHER && e.entity instanceof net.divinerpg.entities.vanilla.EntityTheWatcher) {
                // a lair of the old kind (with a rubble pile) - first into the new one; the Watcher himself - above the eye in the center
                com.divinerpgextended.boss.WatcherHunt.upgrade(e.world, c);
                e.entity.setPosition(c[0] + 0.5, com.divinerpgextended.world.WatcherVault.bossY() - e.entity.height / 2, c[2] + 0.5);
            }
            // King of Scorchers in the new chamber - on the finish strip, he starts from there
            if (type == WorldGenOuterLairs.SCORCHERS && e.entity instanceof net.divinerpg.entities.vanilla.EntityKingOfScorchers
                && com.divinerpgextended.world.ScorcherPit.isNew(e.world, c[0], c[2])) {
                com.divinerpgextended.world.WatcherVault.seal(e.world, c[0], c[2]); // chamber windows before 0.99.56 - without leaks
                e.entity.setPosition(c[0] + com.divinerpgextended.boss.KingRace.PIT_DX + 0.5, com.divinerpgextended.world.WatcherVault.FLOOR + 1, c[2] + 0.5);
            }
            // the horde circles over the whole island (a circle wider than the island never happens) - the leash is wider than the hall
            leash(e.entity, c[0], WorldGenOuterLairs.floor(e.world, type, c) + 1, c[2], WorldGenOuterLairs.RADIUS[type] + (type == WorldGenOuterLairs.AYERACO ? 6 : -3));
            return;
        }
    }

    @SubscribeEvent
    public void onUpdate(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent e) {
        net.minecraft.entity.EntityLivingBase m = e.entityLiving;
        if (m.worldObj.isRemote || m.ticksExisted % 2 != 0) return;
        int[] h = m.getEntityData().getIntArray(HOME);
        if (h.length != 4) return;
        if (m.ticksExisted % 20 == 0 && m.isEntityAlive()) ArenaCalm.mark(m, h);
        double dx = m.posX - h[0] - 0.5, dz = m.posZ - h[2] - 0.5, d = Math.sqrt(dx * dx + dz * dz);
        if (d <= h[3] && m.posY >= h[1] - 2) return;
        if (d <= h[3] + 6 && m.posY >= h[1] - 2) {
            // stepped slightly past the edge - turn back to the center, without teleporting
            if (m instanceof net.minecraft.entity.EntityLiving) ((net.minecraft.entity.EntityLiving) m).getNavigator().clearPathEntity();
            m.motionX = -dx / d * 0.45;
            m.motionZ = -dz / d * 0.45;
            m.velocityChanged = true;
            return;
        }
        m.fallDistance = 0;
        m.motionX = m.motionY = m.motionZ = 0;
        m.setPositionAndUpdate(h[0] + 0.5, h[1] + 0.5, h[2] + 0.5);
        com.divinerpgextended.debug.Debug.note(m, "The boss left the lair edge - returned to the center");
    }

    /** The summon with an item takes place in its lair (the Demonic Circle and Mortum arenas, Nether and End lairs). */
    public static boolean inLair(EntityPlayer p, Item item, int x, int y, int z) {
        if (item instanceof ItemTwilightSpawner) {
            com.divinerpgextended.world.twilight.MortumArenas.Type t = com.divinerpgextended.world.twilight.MortumArenas.ofCrystal(item);
            return t == null ? WorldGenNests.inCircle(p.worldObj, x + 0.5, y + 1, z + 0.5)
                : com.divinerpgextended.world.twilight.MortumArenas.inArena(p.worldObj, t, x + 0.5, y + 1, z + 0.5);
        }
        int type = item instanceof net.divinerpg.items.vanilla.ItemCallOfTheWatcher ? WorldGenOuterLairs.WATCHER
            : item instanceof net.divinerpg.items.vanilla.ItemInfernalFlame ? WorldGenOuterLairs.SCORCHERS
                : item instanceof net.divinerpg.items.vanilla.ItemHordeHorn ? WorldGenOuterLairs.AYERACO : -1;
        return type >= 0 && WorldGenOuterLairs.enabled(type) && WorldGenOuterLairs.inside(p.worldObj, type, x + 0.5, y + 1, z + 0.5);
    }

    /** Refusal key or null if summoning is allowed here. */
    public static String refusal(EntityPlayer p, Item item, int x, int y, int z) {
        if (item instanceof ItemTwilightSpawner) {
            com.divinerpgextended.world.twilight.MortumArenas.Type t = com.divinerpgextended.world.twilight.MortumArenas.ofCrystal(item);
            if (t == null) return WorldGenNests.inCircle(p.worldObj, x + 0.5, y + 1, z + 0.5) ? null : "dre.chat.summon_circle";
            if (!com.divinerpgextended.world.twilight.MortumArenas.inArena(p.worldObj, t, x + 0.5, y + 1, z + 0.5)) return "dre.chat.summon_arena_" + t.key();
            return p.worldObj.getBlock(x, y, z) == com.divinerpgextended.registry.ModBlocks.arenaAltar ? null : "dre.chat.summon_altar";
        }
        if (item == VanillaItemsOther.mysteriousClock) return "dre.chat.summon_tomb";
        int type = item instanceof net.divinerpg.items.vanilla.ItemCallOfTheWatcher ? WorldGenOuterLairs.WATCHER
            : item instanceof net.divinerpg.items.vanilla.ItemInfernalFlame ? WorldGenOuterLairs.SCORCHERS
                : item instanceof net.divinerpg.items.vanilla.ItemHordeHorn ? WorldGenOuterLairs.AYERACO : -1;
        if (type < 0 || !WorldGenOuterLairs.enabled(type)) return null;
        boolean inside = WorldGenOuterLairs.inside(p.worldObj, type, x + 0.5, y + 1, z + 0.5);
        if (type == WorldGenOuterLairs.AYERACO && inside)
            return p.worldObj.getBlock(x, y, z) == com.divinerpgextended.registry.ModBlocks.ayeracoAltar ? null : "dre.chat.summon_altar_ayeraco";
        return inside ? null : "dre.chat.summon_lair_" + type;
    }
}
