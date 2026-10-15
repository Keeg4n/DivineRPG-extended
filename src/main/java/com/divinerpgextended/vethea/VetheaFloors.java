package com.divinerpgextended.vethea;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.divinerpg.entities.vethea.EntityHiveQueen;
import net.divinerpg.entities.vethea.EntityKaros;
import net.divinerpg.entities.vethea.EntityLadyLuna;
import net.divinerpg.entities.vethea.EntityQuadro;
import net.divinerpg.entities.vethea.EntityRaglok;
import net.divinerpg.entities.vethea.EntityWreck;
import net.divinerpg.utils.blocks.VetheaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import com.divinerpgextended.Config;
import com.divinerpgextended.accessory.Accessories;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.trinket.Trinket;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Вертикаль Ветеи. Этажи - плиты дримстоуна толщиной 16 блоков через каждые 48 (поверхности на y 16, 64, 112, 160).
 * <ul>
 * <li>Наверх ведут только разломы: шахта сквозь плиту, одна на квадрат 6x6 чанков в каждой плите, с огненными
 * кристаллами по краю. Разломы прорубаются рядом с игроками (и в старых мирах тоже), строить в шахте нельзя.</li>
 * <li>Нижнюю часть плит не сломать (верхние 4 блока - можно: там трава, земля и стройка).</li>
 * <li>Крылья сна (три ступени) - взлёт только в Ветее. Из разлома на этаж N без крыльев ступени N-1 сбрасывает поток.</li>
 * <li>Страж: кто оказался выше дозволенного этажа любым путём (жемчуг, столб, моды) - через 1.5 с сброшен вниз.
 * Дозволено: 1 + наибольшая ступень крыльев, что игрок когда-либо надевал.</li>
 * <li>Обычный полёт в Ветее выключен (кроме творческого режима).</li>
 * <li>Дары сна: за этажи и боссов Ветеи - награда в обычном мире по пробуждении (новые вещи, ничего не переносится).</li>
 * </ul>
 */
public class VetheaFloors {

    public static final int GAP = 48, REGION = 6;
    /** Радиус шахты разлома (квадрат) и зоны потока. */
    private static final int SHAFT_R2 = 5;
    private static final double FLOW_R2 = 6.25;
    private static final String TIER = "dre_wing_tier", GIFT_DONE = "dre_dream_done", GIFT_PENDING = "dre_dream_pending";

    private static class State {

        int over, lastMsg = -1000, lastRift = -1000;
    }

    private static final Map<UUID, State> STATE = new HashMap<UUID, State>();

    // ---------------- Геометрия ----------------

    /** Плита под этажом f (2..4): нижний и верхний y. */
    public static int slabBottom(int f) {
        return GAP * (f - 1) + 1;
    }

    public static int slabTop(int f) {
        return GAP * (f - 1) + 16;
    }

    /** y травы этажа f. */
    public static int surface(int f) {
        return GAP * (f - 1) + 16;
    }

    public static int floorOf(double y) {
        return y >= 161 ? 4 : y >= 113 ? 3 : y >= 65 ? 2 : 1;
    }

    private static int floorDiv(int a, int b) {
        return a >= 0 ? a / b : -((-a + b - 1) / b);
    }

    /** Центр разлома в плите f для региона (rx, rz): {x, z}. */
    public static int[] rift(World w, int rx, int rz, int f) {
        Random r = new Random(w.getSeed() ^ rx * 341873128712L ^ rz * 132897987541L ^ f * 0x5DEECE66DL);
        r.nextInt();
        int cx = rx * REGION + r.nextInt(REGION), cz = rz * REGION + r.nextInt(REGION);
        return new int[] { cx * 16 + 8, cz * 16 + 8 };
    }

    /** В каком разломе точка: номер плиты (2..4) или 0. below/above - запас по высоте вне плиты. */
    public static int riftAt(World w, double x, double y, double z, double r2, int below, int above) {
        for (int f = 2; f <= 4; f++) {
            if (y < slabBottom(f) - below || y > slabTop(f) + above + 1) continue;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            int[] c = rift(w, floorDiv(bx >> 4, REGION), floorDiv(bz >> 4, REGION), f);
            double dx = x - (c[0] + 0.5), dz = z - (c[1] + 0.5);
            if (dx * dx + dz * dz <= r2) return f;
        }
        return 0;
    }

    private static boolean inShaft(World w, int x, int y, int z) {
        return riftAt(w, x + 0.5, y, z + 0.5, SHAFT_R2 + 0.5, 0, -1) != 0;
    }

    /** Неломаемая часть плит (кроме верхних 4 блоков). */
    public static boolean protectedSlab(int y) {
        for (int f = 2; f <= 4; f++) if (y >= slabBottom(f) && y <= slabTop(f) - 4) return true;
        return false;
    }

    static boolean vethea(World w) {
        return w.provider.dimensionId == ConfigurationHelper.vethea;
    }

    // ---------------- Разломы: прорубить один раз ----------------

    public static class Rifts extends WorldSavedData {

        static final String NAME = "dre_rifts";
        final Set<String> carved = new HashSet<String>();

        public Rifts(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound t) {
            NBTTagList l = t.getTagList("c", 8);
            for (int i = 0; i < l.tagCount(); i++) carved.add(l.getStringTagAt(i));
        }

        @Override
        public void writeToNBT(NBTTagCompound t) {
            NBTTagList l = new NBTTagList();
            for (String s : carved) l.appendTag(new NBTTagString(s));
            t.setTag("c", l);
        }

        static Rifts get(World w) {
            Rifts r = (Rifts) w.perWorldStorage.loadData(Rifts.class, NAME);
            if (r == null) {
                r = new Rifts(NAME);
                w.perWorldStorage.setData(NAME, r);
            }
            return r;
        }
    }

    private static void carveNear(EntityPlayer p) {
        World w = p.worldObj;
        Rifts rifts = Rifts.get(w);
        int rx0 = floorDiv(((int) Math.floor(p.posX)) >> 4, REGION), rz0 = floorDiv(((int) Math.floor(p.posZ)) >> 4, REGION);
        for (int rx = rx0 - 1; rx <= rx0 + 1; rx++) {
            for (int rz = rz0 - 1; rz <= rz0 + 1; rz++) {
                for (int f = 2; f <= 4; f++) {
                    String key = rx + "," + rz + "," + f;
                    if (rifts.carved.contains(key)) continue;
                    int[] c = rift(w, rx, rz, f);
                    if (!w.getChunkProvider().chunkExists(c[0] >> 4, c[1] >> 4)) continue;
                    // только в населённом чанке: иначе деревья и постройки Divine потом перезапишут край (а то и шахту)
                    if (!w.getChunkFromChunkCoords(c[0] >> 4, c[1] >> 4).isTerrainPopulated) continue;
                    carve(w, c[0], c[1], f);
                    com.divinerpgextended.debug.Debug.note(p, "Разлом прорублен: плита " + f + ", центр " + c[0] + " " + c[1]);
                    rifts.carved.add(key);
                    rifts.markDirty();
                }
            }
        }
    }

    private static void carve(World w, int x, int z, int f) {
        int b = slabBottom(f), t = slabTop(f);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int d = dx * dx + dz * dz;
                if (d <= SHAFT_R2) {
                    for (int y = b; y <= t; y++) {
                        if (w.getTileEntity(x + dx, y, z + dz) == null) w.setBlock(x + dx, y, z + dz, Blocks.air, 0, 2);
                    }
                } else if (d <= 10) {
                    rim(w, x + dx, t, z + dz);
                    rim(w, x + dx, b, z + dz);
                }
            }
        }
    }

    private static void rim(World w, int x, int y, int z) {
        // любой обычный твёрдый блок плиты (в полах Ветеи бывают кристаллы и прочее), но не воздух, не сундук и не бедрок
        Block bl = w.getBlock(x, y, z);
        if (bl.isAir(w, x, y, z) || w.getTileEntity(x, y, z) != null || bl.getBlockHardness(w, x, y, z) < 0) return;
        w.setBlock(x, y, z, VetheaBlocks.fireCrystal, 0, 2);
    }

    // ---------------- Крылья и дозволенный этаж ----------------

    private static final Trinket[] WINGS = { Trinket.DREAM_WINGS_1, Trinket.DREAM_WINGS_2, Trinket.DREAM_WINGS_3 };

    /** Ступень надетых крыльев (0 - нет). */
    public static int wingTier(EntityPlayer p) {
        for (int t = WINGS.length; t >= 1; t--) if (Accessories.wears(p, WINGS[t - 1])) return t;
        return 0;
    }

    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static int allowedFloor(EntityPlayer p) {
        return 1 + Math.max(persisted(p).getInteger(TIER), wingTier(p));
    }

    private static void say(EntityPlayer p, EnumChatFormatting color, String key, Object... args) {
        com.divinerpgextended.audit.Audit.say(p, key, args);
        ChatComponentTranslation c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(color));
        p.addChatMessage(c);
    }

    private static void sendDown(EntityPlayerMP p, int floor, int need) {
        World w = p.worldObj;
        int x = (int) Math.floor(p.posX), z = (int) Math.floor(p.posZ), base = surface(floor) + 1, ty = base;
        for (int y = base; y < base + 30; y++) {
            if (w.isAirBlock(x, y, z) && w.isAirBlock(x, y + 1, z) && !w.isAirBlock(x, y - 1, z)) {
                ty = y;
                break;
            }
        }
        p.setPositionAndUpdate(x + 0.5, ty, z + 0.5);
        p.fallDistance = 0;
        p.motionY = 0;
        say(p, EnumChatFormatting.DARK_PURPLE, "dre.vethea.guard", String.valueOf(need));
        com.divinerpgextended.debug.Debug.note(p, "Страж этажей: сброшен на этаж " + floor + " (нужна ступень " + need + ")");
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.worldObj.isRemote) return;
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        if (!vethea(p.worldObj)) {
            if (p.ticksExisted % 40 == 0) deliverGifts(p);
            return;
        }
        if (!Config.vetheaFloors) return;
        State s = STATE.get(p.getUniqueID());
        if (s == null) STATE.put(p.getUniqueID(), s = new State());
        int tier = wingTier(p);
        if (tier > persisted(p).getInteger(TIER)) persisted(p).setInteger(TIER, tier);
        if (p.ticksExisted % 20 == 0) carveNear(p);
        if (p.capabilities.isCreativeMode) return;
        // обычный полёт во сне не работает
        if (p.capabilities.allowFlying || p.capabilities.isFlying) {
            p.capabilities.allowFlying = false;
            p.capabilities.isFlying = false;
            p.sendPlayerAbilities();
            Nightmare.hint(p, "flight");
        }
        // поток в разломе: без крыльев нужной ступени - вниз
        int f = riftAt(p.worldObj, p.posX, p.posY, p.posZ, FLOW_R2, 8, 1);
        if (f > 0) {
            s.lastRift = p.ticksExisted;
            if (tier < f - 1) {
                p.motionY = -0.8;
                if (p.ticksExisted % 20 == 0) com.divinerpgextended.debug.Debug.note(p, "Поток разлома: вниз, ступень крыльев " + tier + " < " + (f - 1));
                p.velocityChanged = true;
                if (p.ticksExisted - s.lastMsg > 100) {
                    s.lastMsg = p.ticksExisted;
                    say(p, EnumChatFormatting.DARK_PURPLE, "dre.vethea.downdraft", String.valueOf(f - 1));
                }
            }
        }
        // страж этажей
        int floor = floorOf(p.posY), allowed = allowedFloor(p);
        if (floor > allowed) {
            if (++s.over > 30) {
                s.over = 0;
                sendDown(p, allowed, floor - 1);
            }
        } else {
            s.over = 0;
            if (floor >= 2) mark(p, Gift.values()[floor - 2]);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        STATE.remove(e.player.getUniqueID());
    }

    /** Крылья гасят падение; через разлом тоже можно спрыгнуть вниз без урона. */
    @SubscribeEvent
    public void onFall(LivingFallEvent e) {
        if (!(e.entityLiving instanceof EntityPlayer) || e.entityLiving.worldObj.isRemote || !vethea(e.entityLiving.worldObj)) return;
        EntityPlayer p = (EntityPlayer) e.entityLiving;
        State s = STATE.get(p.getUniqueID());
        if (wingTier(p) > 0 || s != null && p.ticksExisted - s.lastRift < 100) {
            e.setCanceled(true);
            com.divinerpgextended.debug.Debug.note(p, "Падение не ранит: " + (wingTier(p) > 0 ? "крылья сна" : "прыжок через разлом"));
        }
    }

    // ---------------- Плиты и шахты ----------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent e) {
        if (!Config.vetheaFloors || !vethea(e.world) || e.getPlayer() == null || e.getPlayer().capabilities.isCreativeMode) return;
        if (!protectedSlab(e.y)) return;
        e.setCanceled(true);
        com.divinerpgextended.debug.Debug.note(e.getPlayer(), "Основа этажа: ломать нельзя (y " + e.y + ")");
        Nightmare.hint(e.getPlayer(), "slab");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlace(BlockEvent.PlaceEvent e) {
        if (!Config.vetheaFloors || !vethea(e.world) || e.player == null || e.player.capabilities.isCreativeMode) return;
        if (!inShaft(e.world, e.x, e.y, e.z)) return;
        e.setCanceled(true);
        com.divinerpgextended.debug.Debug.note(e.player, "Шахта разлома: строить нельзя");
        Nightmare.hint(e.player, "rift_place");
    }

    @SubscribeEvent
    public void onExplode(ExplosionEvent.Detonate e) {
        if (!Config.vetheaFloors || !vethea(e.world)) return;
        for (Iterator<ChunkPosition> it = e.getAffectedBlocks().iterator(); it.hasNext();) {
            ChunkPosition c = it.next();
            if (protectedSlab(c.chunkPosY)) it.remove();
        }
    }

    // ---------------- Дары сна ----------------

    public enum Gift {
        FLOOR_2(200, new ItemStack(VanillaItemsOther.realmiteIngot, 12), new ItemStack(Items.diamond, 6)),
        FLOOR_3(400, new ItemStack(VanillaItemsOther.arlemiteIngot, 12), new ItemStack(VanillaItemsOther.healingStone, 4)),
        FLOOR_4(800, new ItemStack(VanillaItemsOther.rupeeIngot, 16), new ItemStack(Items.nether_star)),
        HIVE_QUEEN(300, new ItemStack(VanillaItemsOther.bloodgem, 8)),
        QUADRO(500, new ItemStack(VanillaItemsOther.divineStone, 4)),
        KAROS(500, new ItemStack(VanillaItemsOther.divineStone, 4)),
        RAGLOK(800, new ItemStack(VanillaItemsOther.legendaryEnderEye)),
        WRECK(800, new ItemStack(VanillaItemsOther.shadowBar, 16)),
        LADY_LUNA(1000, new ItemStack(Items.nether_star, 2));

        final int xp;
        final ItemStack[] items;

        Gift(int xp, ItemStack... items) {
            this.xp = xp;
            this.items = items;
        }

        String key() {
            return "dre.gift." + name().toLowerCase();
        }

        static Gift forBoss(Entity e) {
            if (e instanceof EntityHiveQueen) return HIVE_QUEEN;
            if (e instanceof EntityQuadro) return QUADRO;
            if (e instanceof EntityKaros) return KAROS;
            if (e instanceof EntityRaglok) return RAGLOK;
            if (e instanceof EntityWreck) return WRECK;
            if (e instanceof EntityLadyLuna) return LADY_LUNA;
            return null;
        }
    }

    static void mark(EntityPlayer p, Gift g) {
        NBTTagCompound d = persisted(p);
        int bit = 1 << g.ordinal(), done = d.getInteger(GIFT_DONE);
        if ((done & bit) != 0) return;
        d.setInteger(GIFT_DONE, done | bit);
        d.setInteger(GIFT_PENDING, d.getInteger(GIFT_PENDING) | bit);
        com.divinerpgextended.debug.Debug.note(p, "Дар сна отмечен: " + g.name());
        say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.vethea.gift_marked", new ChatComponentTranslation(g.key()));
    }

    private static void deliverGifts(EntityPlayer p) {
        NBTTagCompound d = persisted(p);
        int pending = d.getInteger(GIFT_PENDING);
        if (pending == 0) return;
        d.setInteger(GIFT_PENDING, 0);
        for (Gift g : Gift.values()) {
            if ((pending & 1 << g.ordinal()) == 0) continue;
            for (ItemStack s : g.items) {
                ItemStack c = s.copy();
                if (!p.inventory.addItemStackToInventory(c)) p.worldObj.spawnEntityInWorld(new EntityItem(p.worldObj, p.posX, p.posY + 0.5, p.posZ, c));
            }
            p.addExperience(g.xp);
            com.divinerpgextended.debug.Debug.note(p, "Дар сна выдан: " + g.name());
            say(p, EnumChatFormatting.LIGHT_PURPLE, "dre.vethea.gift", new ChatComponentTranslation(g.key()));
        }
        p.inventoryContainer.detectAndSendChanges();
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.entityLiving.worldObj.isRemote || !vethea(e.entityLiving.worldObj)) return;
        Gift g = Gift.forBoss(e.entityLiving);
        if (g == null) return;
        @SuppressWarnings("unchecked")
        List<EntityPlayer> near = e.entityLiving.worldObj.getEntitiesWithinAABB(EntityPlayer.class, e.entityLiving.boundingBox.expand(48, 32, 48));
        for (EntityPlayer p : near) mark(p, g);
    }

    // ---------------- Рецепты крыльев ----------------

    public static void registerRecipes() {
        GameRegistry.addRecipe(wings(Trinket.DREAM_WINGS_1), "P P", "PTP", "PAP", 'P', VetheaItems.dirtyPearls, 'T',
            VetheaItems.teakerLump, 'A', VetheaItems.amthirmisLump);
        GameRegistry.addRecipe(wings(Trinket.DREAM_WINGS_2), "C C", "CWC", "DHD", 'C', VetheaItems.cleanPearls, 'W',
            wings(Trinket.DREAM_WINGS_1), 'D', VetheaItems.darvenLump, 'H', VetheaItems.cermileLump);
        GameRegistry.addRecipe(wings(Trinket.DREAM_WINGS_3), "P P", "PWP", "QKQ", 'P', VetheaItems.polishedPearls, 'W',
            wings(Trinket.DREAM_WINGS_2), 'Q', VetheaItems.quadroticLump, 'K', VetheaItems.karosLump);
    }

    private static ItemStack wings(Trinket t) {
        return new ItemStack(ModItems.trinket, 1, t.ordinal());
    }
}
