package com.divinerpgextended.arcana;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.divinerpg.entities.arcana.EntityWarGeneral;
import net.divinerpg.utils.blocks.VanillaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.ArcanaItems;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import com.divinerpgextended.boss.Boss;
import com.divinerpgextended.boss.BossProgress;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Военный генерал. Присесть и ПКМ:
 * <ul>
 * <li>со статуей босса Divine в руке - отдать её в Зал славы: каждая разная статуя навсегда +1 сердце (12 статуй);</li>
 * <li>без статуи - приказ на охоту (взять, узнать прогресс, сдать). Выполненные приказы повышают звание, за
 * каждое звание Генерал дарит вещь из своего арсенала.</li>
 * </ul>
 */
public class WarGeneral {

    private static final String TAG = "dre_general";
    private static final UUID HEARTS = UUID.fromString("6f3e2a7c-5b1d-4e9a-9c2f-7d8e1a4b3c21");

    // ---------------- Зал славы ----------------

    private static Block[] statues() {
        return new Block[] { VanillaBlocks.ancientEntityStatue, VanillaBlocks.watcherStatue, VanillaBlocks.kosStatue,
            VanillaBlocks.ayeracoStatue, VanillaBlocks.dramixStatue, VanillaBlocks.densosStatue, VanillaBlocks.reyvorStatue,
            VanillaBlocks.eternalArcherStatue, VanillaBlocks.soulFiendStatue, VanillaBlocks.vamacheronStatue,
            VanillaBlocks.twilightDemonStatue, VanillaBlocks.karotStatue };
    }

    private static int statueIndex(ItemStack s) {
        if (s == null) return -1;
        Block[] st = statues();
        for (int i = 0; i < st.length; i++) if (s.getItem() == Item.getItemFromBlock(st[i])) return i;
        return -1;
    }

    // ---------------- Приказы ----------------

    /** Приказ: кого бить (по имени класса или по миру), сколько, после какого босса доступен. */
    private enum Order {
        ICE_BEASTS(15, Boss.CARCINUS, null, "Glacon", "Frost"),
        SEA_PATROL(10, Boss.CARCINUS, null, "Shark", "Whale", "Liopleurodon"),
        HELLFIRE(20, Boss.CARCINUS, null, "Wildfire", "HellSpider"),
        ICEIKA(25, Boss.SNOW_GLACON, "iceika"),
        SCORCHERS(15, Boss.LICH_KING, null, "Scorcher"),
        CAVES(25, Boss.LICH_KING, null, "Cyclops", "Kobblin", "Miner", "CaveCrawler", "Rotatick", "TheEye", "TheGrue",
            "Caveclops", "EnthralledDramcryx"),
        DESERT(15, Boss.LICH_KING, null, "DesertCrawler", "AridWarrior", "SaguaroWorm"),
        JUNGLE(15, Boss.LICH_KING, null, "JungleBat", "JungleSpider", "JungleDramcryx"),
        ENDER(15, Boss.ANCIENT_ENTITY, null, "EnderSpider", "EnderTriplets", "EnderWatcher"),
        ARCANA(25, Boss.WATCHER, "arcana"),
        EDEN(30, Boss.WATCHER, "eden"),
        WILDWOOD(30, Boss.CADILLION_EDEN, "wildwood"),
        APALACHIA(30, Boss.CADILLION_WILDWOOD, "apalachia"),
        SKYTHERN(30, Boss.CADILLION_APALACHIA, "skythern"),
        MORTUM(30, Boss.CADILLION_SKYTHERN, "mortum"),
        VETHEA(30, Boss.KAROT, "vethea");

        final int count;
        final Boss gate;
        final String world;
        final String[] names;

        Order(int count, Boss gate, String world, String... names) {
            this.count = count;
            this.gate = gate;
            this.world = world;
            this.names = names;
        }

        String key() {
            return name().toLowerCase();
        }

        int dim() {
            if ("iceika".equals(world)) return ConfigurationHelper.iceika;
            if ("arcana".equals(world)) return ConfigurationHelper.arcana;
            if ("eden".equals(world)) return ConfigurationHelper.eden;
            if ("wildwood".equals(world)) return ConfigurationHelper.wildwood;
            if ("apalachia".equals(world)) return ConfigurationHelper.apalachia;
            if ("skythern".equals(world)) return ConfigurationHelper.skythern;
            if ("mortum".equals(world)) return ConfigurationHelper.mortum;
            if ("vethea".equals(world)) return ConfigurationHelper.vethea;
            return Integer.MIN_VALUE;
        }

        boolean matches(Entity dead) {
            if (world != null) return dead instanceof IMob && dead.worldObj.provider.dimensionId == dim();
            String n = dead.getClass().getSimpleName();
            for (String s : names) if (n.equals("Entity" + s)) return true;
            return false;
        }

        boolean open(EntityPlayer p) {
            return BossProgress.has(p, gate);
        }
    }

    /** Звания по числу выполненных приказов и подарок за каждое. */
    private static final int[] RANK_AT = { 0, 3, 7, 12, 20, 30 };

    private static int rank(int done) {
        int r = 0;
        for (int i = 0; i < RANK_AT.length; i++) if (done >= RANK_AT[i]) r = i;
        return r;
    }

    private static ItemStack rankGift(int rank) {
        switch (rank) {
            case 1: return new ItemStack(ArcanaItems.reflector);
            case 2: return new ItemStack(ArcanaItems.divineAccumulator);
            case 3: return new ItemStack(ArcanaItems.meteorMash);
            case 4: return new ItemStack(ArcanaItems.arcaniteBlade);
            case 5: return new ItemStack(ArcanaItems.generalsStaff);
            default: return null;
        }
    }

    // ---------------- Данные игрока ----------------

    private static NBTTagCompound data(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        NBTTagCompound ps = d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!ps.hasKey(TAG)) {
            NBTTagCompound g = new NBTTagCompound();
            g.setInteger("order", -1);
            ps.setTag(TAG, g);
        }
        return ps.getCompoundTag(TAG);
    }

    public static int statueCount(EntityPlayer p) {
        return Integer.bitCount(data(p).getInteger("statues"));
    }

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    private static void give(EntityPlayer p, ItemStack s) {
        if (!p.inventory.addItemStackToInventory(s)) p.worldObj.spawnEntityInWorld(new EntityItem(p.worldObj, p.posX, p.posY + 0.5,
            p.posZ, s));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(EntityInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (!(e.target instanceof EntityWarGeneral) || !p.isSneaking()) return;
        e.setCanceled(true);
        if (p.worldObj.isRemote) return;
        NBTTagCompound g = data(p);
        ItemStack held = p.getCurrentEquippedItem();
        int si = statueIndex(held);
        if (si >= 0) {
            int mask = g.getInteger("statues");
            if ((mask & 1 << si) != 0) {
                say(p, EnumChatFormatting.GRAY, "dre.general.statue_known", held.getDisplayName());
                return;
            }
            g.setInteger("statues", mask | 1 << si);
            if (!p.capabilities.isCreativeMode && --held.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
            applyHearts(p);
            p.heal(2.0F);
            p.worldObj.playSoundAtEntity(p, "random.levelup", 0.8F, 0.8F);
            say(p, EnumChatFormatting.GOLD, "dre.general.statue_taken", held.getDisplayName(), String.valueOf(statueCount(p)),
                String.valueOf(statues().length));
            return;
        }
        int oi = g.getInteger("order");
        if (oi < 0 || oi >= Order.values().length) {
            Order o = pick(p);
            if (o == null) {
                say(p, EnumChatFormatting.GRAY, "dre.general.no_orders");
                return;
            }
            g.setInteger("order", o.ordinal());
            g.setInteger("progress", 0);
            say(p, EnumChatFormatting.YELLOW, "dre.general.order_new", new ChatComponentTranslation("dre.order." + o.key()),
                String.valueOf(o.count));
            return;
        }
        Order o = Order.values()[oi];
        int prog = g.getInteger("progress");
        if (prog < o.count) {
            say(p, EnumChatFormatting.YELLOW, "dre.general.order_progress", new ChatComponentTranslation("dre.order." + o.key()),
                String.valueOf(prog), String.valueOf(o.count));
            return;
        }
        // сдача приказа
        int before = rank(g.getInteger("done"));
        int done = g.getInteger("done") + 1;
        g.setInteger("done", done);
        g.setInteger("order", -1);
        int tier = 1 + o.ordinal() / 3;
        give(p, new ItemStack(ArcanaItems.dungeonTokens, 3 + tier + p.worldObj.rand.nextInt(3)));
        give(p, new ItemStack(ArcanaItems.arcanium, 2 + tier + p.worldObj.rand.nextInt(3)));
        p.worldObj.playSoundAtEntity(p, "random.levelup", 0.6F, 1.1F);
        say(p, EnumChatFormatting.GREEN, "dre.general.order_done", String.valueOf(done));
        int after = rank(done);
        if (after > before) {
            ItemStack gift = rankGift(after);
            if (gift != null) give(p, gift);
            say(p, EnumChatFormatting.GOLD, "dre.general.promoted", new ChatComponentTranslation("dre.rank." + after),
                gift == null ? "" : gift.getDisplayName());
        }
        if (after + 1 < RANK_AT.length) {
            say(p, EnumChatFormatting.GRAY, "dre.general.next_rank", new ChatComponentTranslation("dre.rank." + (after + 1)),
                String.valueOf(RANK_AT[after + 1] - done));
        }
    }

    /** Новый приказ: из открытых, чаще - последние по прогрессу (чтобы звали дальше, а не назад). */
    private static Order pick(EntityPlayer p) {
        List<Order> open = new ArrayList<Order>();
        for (Order o : Order.values()) if (o.open(p)) open.add(o);
        if (open.isEmpty()) return null;
        int total = 0;
        for (int i = 0; i < open.size(); i++) total += i + 1;
        int n = p.worldObj.rand.nextInt(total);
        for (int i = 0; i < open.size(); i++) {
            n -= i + 1;
            if (n < 0) return open.get(i);
        }
        return open.get(open.size() - 1);
    }

    /** Счёт убийств для текущего приказа. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.entityLiving.worldObj.isRemote || !(e.source.getEntity() instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) e.source.getEntity();
        NBTTagCompound g = data(p);
        int oi = g.getInteger("order");
        if (oi < 0 || oi >= Order.values().length) return;
        Order o = Order.values()[oi];
        if (!o.matches(e.entityLiving)) return;
        int prog = g.getInteger("progress");
        if (prog >= o.count) return;
        g.setInteger("progress", ++prog);
        if (prog == o.count) say(p, EnumChatFormatting.GREEN, "dre.general.order_ready");
        else if (prog % 5 == 0) say(p, EnumChatFormatting.GRAY, "dre.general.order_tick", String.valueOf(prog), String.valueOf(o.count));
    }

    // ---------------- Сердца ----------------


    /** +2 здоровья за каждую отданную статую - модификатором, который держим на игроке (после смерти тоже). */
    public static void applyHearts(EntityPlayer p) {
        IAttributeInstance hp = p.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        double want = 2.0 * statueCount(p);
        AttributeModifier cur = hp.getModifier(HEARTS);
        if (cur != null && cur.getAmount() == want) return;
        if (cur != null) hp.removeModifier(cur);
        if (want > 0) hp.applyModifier(new AttributeModifier(HEARTS, "Зал славы", want, 0).setSaved(false));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote || e.player.ticksExisted % 40 != 0) return;
        applyHearts(e.player);
    }
}
