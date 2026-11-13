package com.divinerpgextended.vethea;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Во сне до прохождения чужие вещи не срабатывают:
 * <ul>
 * <li>ПКМ (в воздух, по блоку, по существу) чужой вещью ничего не делает, кроме строительства, еды, зелий и луков
 * ({@link Nightmare#usable}); сундуки и двери открываются как обычно;</li>
 * <li>чужой инструмент ломает только блок, который игрок начал ломать: площадь (молоты, экскаваторы, лесорубы,
 * Крушитель Терры, фокусы) не идёт.</li>
 * </ul>
 * Работает и на клиенте (рывки и прыжки вещей считает клиент), прохождение он знает из
 * {@link com.divinerpgextended.boss.ProgressSync}.
 */
public class NightmareHands {

    private static final Map<Class<?>, Boolean> USE_FIRST = new HashMap<Class<?>, Boolean>(), ON_ENTITY = new HashMap<Class<?>, Boolean>(),
        ON_HIT = new HashMap<Class<?>, Boolean>();
    /** Блок, который игрок начал ломать (клик по блоку): его чужой инструмент сломать может, остальное - нет. */
    private static final Map<UUID, int[]> DIGGING = new HashMap<UUID, int[]>();

    /** Переопределяет ли вещь (или её предки до Item) метод с одним из имён. */
    private static boolean overrides(Item i, Map<Class<?>, Boolean> cache, String... names) {
        Boolean b = cache.get(i.getClass());
        if (b != null) return b;
        boolean r = false;
        outer: for (Class<?> c = i.getClass(); c != null && c != Item.class; c = c.getSuperclass()) {
            Method[] ms;
            try {
                ms = c.getDeclaredMethods();
            } catch (Throwable t) {
                r = true; // в сигнатурах клиентские классы - считаем, что переопределяет
                break;
            }
            for (Method m : ms) for (String n : names) if (n.equals(m.getName())) {
                r = true;
                break outer;
            }
        }
        cache.put(i.getClass(), r);
        return r;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (e.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            if (!p.worldObj.isRemote) DIGGING.put(p.getUniqueID(), new int[] { e.x, e.y, e.z });
            return;
        }
        if (!Nightmare.active(p)) return;
        ItemStack h = p.getHeldItem();
        if (Nightmare.usable(h)) return;
        e.useItem = Event.Result.DENY; // onItemUse и onItemRightClick
        // onItemUseFirst вызывается и при отказе - его останавливает только отмена (тогда и блок не откроется)
        boolean air = e.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR;
        if (air || overrides(h.getItem(), USE_FIRST, "onItemUseFirst")) e.setCanceled(true);
        if (air && !p.worldObj.isRemote) Nightmare.hint(p, "use");
        com.divinerpgextended.debug.Debug.note(p, "Кошмар: ПКМ чужой вещью не срабатывает (" + Item.itemRegistry.getNameForObject(h.getItem()) + ")");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityInteract(EntityInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (!Nightmare.active(p)) return;
        ItemStack h = p.getHeldItem();
        if (Nightmare.usable(h) || !overrides(h.getItem(), ON_ENTITY, "itemInteractionForEntity", "func_111207_a")) return;
        e.setCanceled(true);
        if (!p.worldObj.isRemote) Nightmare.hint(p, "use");
    }

    /**
     * Удар чужим оружием по мобу сна останавливается ещё до удара: моб лечится на силу удара, а свойства оружия при
     * ударе (особые эффекты Divine, молнии, эффекты Tinkers при попадании, перки аддона) не срабатывают.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMelee(net.minecraftforge.event.entity.player.AttackEntityEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (p.worldObj.isRemote || !Nightmare.active(p) || !Nightmare.nativeTarget(e.target)) return;
        ItemStack h = p.getHeldItem();
        if (h == null || Nightmare.vethean(h)) return;
        if (!Nightmare.weapon(h) && !overrides(h.getItem(), ON_HIT, "onLeftClickEntity", "hitEntity", "func_77644_a")) return;
        e.setCanceled(true);
        if (!(e.target instanceof net.minecraft.entity.EntityLivingBase)) return;
        net.minecraft.entity.EntityLivingBase t = (net.minecraft.entity.EntityLivingBase) e.target;
        float f = (float) p.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.attackDamage).getAttributeValue()
            + net.minecraft.enchantment.EnchantmentHelper.getEnchantmentModifierLiving(p, t);
        Nightmare.soothe(p, t, f);
    }

    /** Чужой инструмент во сне ломает только начатый блок: лишние блоки площади отменяются. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent e) {
        EntityPlayer p = e.getPlayer();
        if (p == null || p instanceof FakePlayer || p.worldObj.isRemote || !Nightmare.active(p)) return;
        ItemStack h = p.getHeldItem();
        if (h == null || Nightmare.vethean(h)) return;
        int[] d = DIGGING.get(p.getUniqueID());
        if (d != null && d[0] == e.x && d[1] == e.y && d[2] == e.z) return;
        e.setCanceled(true);
        Nightmare.hint(p, "area");
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        DIGGING.remove(e.player.getUniqueID());
    }
}
