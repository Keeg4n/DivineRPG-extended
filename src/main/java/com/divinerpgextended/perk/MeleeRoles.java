package com.divinerpgextended.perk;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.arcana.WeaponOrigin;
import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.dummy.EntityTrainingDummy;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

import io.netty.buffer.ByteBuf;

/**
 * Подвиды ближнего оружия обычного мира (мечи из VanillaItemsWeapons Divine): у каждого своё окно неуязвимости цели после
 * удара и свой множитель урона, отдельно по мобам и по игрокам (Config, категория melee).
 * <p>
 * Окно: при свежем ударе ваниль пишет hurtResistantTime = maxHurtResistantTime (20), а новый удар проходит целиком, когда
 * значение не выше половины. Записать половину + N - следующий удар через N тиков; maxHurtResistantTime не трогаем: по нему
 * сверяются чужие проверки. Удар внутри окна, как и в ванили, проходит разницей с прошлым.
 * <p>
 * Правила действуют только на удар самого игрока кликом: {@link AttackEntityEvent} взводит метку, первый прямой урон от
 * этого игрока её снимает. Волна бедроковой булавы и прочие удары "от имени игрока" идут мимо, снаряды - тем более.
 * Манекен считается как моб: его удар проходит через призрака-игрока, а окно живёт на самом манекене.
 * <p>
 * Здесь же крит полного сета Terran ({@link #terranCrit}): удар с земли x{@link #TERRAN_CRIT} без прыжка. Прыжковый крит
 * требует отрыва от земли, поэтому они не складываются. Только сам сет, броне Странника не передаётся.
 */
public class MeleeRoles {

    public enum Kind {
        // молот: окно 1 с, урон x1.2 (до 0.99.122 было x2 и x1.5 по игрокам - с площадью выходило сильнее Маула Кароса)
        KNIFE(6, 0.75F, 8, 1F), RAPIER(8, 0.9F, 9, 1F), SWORD(10, 1F, 10, 1F), MAUL(20, 1.2F, 20, 1.2F),
        // Ветея: когти быстрее мечей, мечи как обычно, молоты медленнее, но x1.3
        CLAW(7, 1F, 7, 1F), HAMMER(14, 1.3F, 14, 1.3F);

        /** Окно неуязвимости цели в тиках: по мобам и по игрокам. */
        public int window, windowPvp;
        /** Множитель урона: по мобам и по игрокам. */
        public float damage, damagePvp;

        Kind(int window, float damage, int windowPvp, float damagePvp) {
            this.window = window;
            this.damage = damage;
            this.windowPvp = windowPvp;
            this.damagePvp = damagePvp;
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    /** Окно в обычном Minecraft: такой подвид окно цели не трогает. */
    public static final int VANILLA_WINDOW = 10;
    /** Крит сета Terran: столько же, сколько обычный крит в прыжке. */
    public static final float TERRAN_CRIT = 1.5F;

    private static final String[] KNIVES = { "oceanKnife", "jungleKnife", "aquaticDagger", "terranDagger", "terranKnife", "dualClaw" };
    private static final String[] RAPIERS = { "rupeeRapier", "jungleRapier", "arlemiteStabber" };
    private static final String[] MAULS = { "aquaMaul", "bedrockMaul", "crabclawMaul", "furyMaul", "corruptedMaul", "terranMaul", "aquatoothMaul" };

    private static final Map<Item, Kind> KINDS = new HashMap<Item, Kind>();

    private static EntityPlayer striker;
    private static Entity struck;
    private static long strikeTick;

    /** Подвиды по именам предметов Divine: чего нет в её версии, то просто не попадает в список. @return сколько нашлось */
    public static int setup() {
        KINDS.clear();
        put(Kind.KNIFE, KNIVES);
        put(Kind.RAPIER, RAPIERS);
        put(Kind.MAUL, MAULS);
        return KINDS.size();
    }

    private static void put(Kind k, String[] names) {
        for (String n : names) {
            Item i = GameRegistry.findItem("divinerpg", n);
            if (i != null) KINDS.put(i, k);
        }
    }

    /** Подвид оружия; остальные мечи обычного мира и Ада - меч; когти, мечи и молоты Ветеи; null - оружие других миров и всё прочее. */
    public static Kind kind(ItemStack s) {
        if (s == null) return null;
        Kind k = KINDS.get(s.getItem());
        if (k != null) return k;
        com.divinerpgextended.vethea.WeaponKind v = com.divinerpgextended.vethea.WeaponKind.ofItem(s);
        if (v == com.divinerpgextended.vethea.WeaponKind.CLAW) return Kind.CLAW;
        if (v == com.divinerpgextended.vethea.WeaponKind.HAMMER) return Kind.HAMMER;
        if (v == com.divinerpgextended.vethea.WeaponKind.SWORD) return Kind.SWORD;
        WeaponOrigin o = WeaponOrigin.of(s);
        return o == WeaponOrigin.OVERWORLD || o == WeaponOrigin.NETHER ? Kind.SWORD : null;
    }

    /** Крит без прыжка: полный сет Terran, на земле; как и обычный крит - не верхом и не в слепоте. В кошмаре Ветеи чужие сеты молчат. */
    static boolean terranCrit(EntityPlayer p) {
        return p.onGround && p.ridingEntity == null && !p.isPotionActive(net.minecraft.potion.Potion.blindness)
            && PerkEffects.wears(p, new Item[] { net.divinerpg.utils.items.VanillaItemsArmor.terranHelmet, net.divinerpg.utils.items.VanillaItemsArmor.terranBody,
                net.divinerpg.utils.items.VanillaItemsArmor.terranLegs, net.divinerpg.utils.items.VanillaItemsArmor.terranBoots })
            && !com.divinerpgextended.vethea.Nightmare.active(p);
    }

    /** Отменённый другими удар сюда не приходит. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSwing(AttackEntityEvent e) {
        if (e.entityPlayer.worldObj.isRemote) return;
        striker = e.entityPlayer;
        struck = e.target;
        strikeTick = e.entityPlayer.worldObj.getTotalWorldTime();
    }

    /** Раньше прочих: проценты брони, аксессуаров и боссов считаются уже от урона подвида, волна булавы - тоже. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHurt(LivingHurtEvent e) {
        EntityPlayer p = striker;
        Entity target = struck;
        if (p == null || e.source.getEntity() != p || e.source.getSourceOfDamage() != p) return;
        striker = null;
        struck = null;
        if (p.worldObj.getTotalWorldTime() != strikeTick) return;
        boolean dummy = target instanceof EntityTrainingDummy;
        EntityLivingBase v = e.entityLiving;
        boolean pvp = !dummy && v instanceof EntityPlayer;
        if (pvp && (!Config.meleeRolesPvp || Protection.spare(p, v))) return;
        if (terranCrit(p)) {
            e.ammount *= TERRAN_CRIT;
            p.onCriticalHit(target);
        }
        Kind k = Config.meleeRoles ? kind(p.getHeldItem()) : null;
        if (k == null) return;
        e.ammount *= pvp ? k.damagePvp : k.damage;
        int window = pvp ? k.windowPvp : k.window;
        EntityLivingBase holder = dummy ? (EntityLivingBase) target : v;
        // равно максимуму - только что записала ваниль (или манекен), удар свежий; разницей внутри окна окно не продлеваем
        if (window != VANILLA_WINDOW && holder.hurtResistantTime == holder.maxHurtResistantTime) holder.hurtResistantTime = holder.maxHurtResistantTime / 2 + window;
    }

    // ---------------- подсказка: числа сервера, а не свой конфиг клиента ----------------

    /** На клиенте - что прислал сервер при входе (null - ещё ничего). Свой конфиг клиента в подсказке врал бы. */
    public static Sync shown;

    public static void registerMessages() {
        ColdHandler.NET.registerMessage(SyncHandler.class, Sync.class, 21, Side.CLIENT);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) ColdHandler.NET.sendTo(new Sync(Config.meleeRoles, Config.meleeRolesPvp), (EntityPlayerMP) e.player);
    }

    public static class Sync implements IMessage {

        private static final int KINDS_N = Kind.values().length;
        public boolean on, pvp;
        public final int[] window = new int[KINDS_N], windowPvp = new int[KINDS_N];
        public final float[] damage = new float[KINDS_N], damagePvp = new float[KINDS_N];

        public Sync() {}

        Sync(boolean on, boolean pvp) {
            this.on = on;
            this.pvp = pvp;
            for (Kind k : Kind.values()) {
                window[k.ordinal()] = k.window;
                windowPvp[k.ordinal()] = k.windowPvp;
                damage[k.ordinal()] = k.damage;
                damagePvp[k.ordinal()] = k.damagePvp;
            }
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            on = buf.readBoolean();
            pvp = buf.readBoolean();
            for (int i = 0; i < KINDS_N; i++) {
                window[i] = buf.readInt();
                windowPvp[i] = buf.readInt();
                damage[i] = buf.readFloat();
                damagePvp[i] = buf.readFloat();
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(on);
            buf.writeBoolean(pvp);
            for (int i = 0; i < KINDS_N; i++) {
                buf.writeInt(window[i]);
                buf.writeInt(windowPvp[i]);
                buf.writeFloat(damage[i]);
                buf.writeFloat(damagePvp[i]);
            }
        }
    }

    public static class SyncHandler implements IMessageHandler<Sync, IMessage> {

        @Override
        public IMessage onMessage(Sync m, MessageContext ctx) {
            shown = m;
            return null;
        }
    }
}
