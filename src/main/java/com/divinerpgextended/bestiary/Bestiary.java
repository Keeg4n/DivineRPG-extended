package com.divinerpgextended.bestiary;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import net.divinerpg.entities.base.InfiniteTrade;
import net.divinerpg.entities.vanilla.EntityLivestockMerchant;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.divinerpg.utils.items.VanillaItemsOther;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import com.divinerpgextended.accessory.Accessories;
import com.divinerpgextended.cold.ColdHandler;
import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.trinket.Trinket;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * Бестиарий: главы по мирам, в каждой - мобы Divine этого мира (из настоящих списков спавна биомов; у Арканы - мобы
 * её спавнеров), боссов нет. Моб изучен, когда убит {@link #need} раз; глава закрыта, когда изучены все. Прогресс - в
 * постоянных данных игрока (книга только показывает его: потеря книги ничего не стоит). Книгу продаёт торговец скотом,
 * он же за каждую закрытую главу один раз выдаёт знак охотника этого мира ({@link #MARK_DMG}).
 */
public final class Bestiary {

    public enum Chapter {
        OVERWORLD(10), NETHER(10), END(5), ICEIKA(10), EDEN(10), WILDWOOD(10), APALACHIA(10), SKYTHERN(10), MORTUM(10), ARCANA(5), VETHEA(5);

        /** Сколько убить обычного (частого) моба главы. */
        public final int base;

        Chapter(int base) {
            this.base = base;
        }

        public String key() {
            return name().toLowerCase();
        }

        public int dim() {
            switch (this) {
                case OVERWORLD: return 0;
                case NETHER: return -1;
                case END: return 1;
                case ICEIKA: return ConfigurationHelper.iceika;
                case EDEN: return ConfigurationHelper.eden;
                case WILDWOOD: return ConfigurationHelper.wildwood;
                case APALACHIA: return ConfigurationHelper.apalachia;
                case SKYTHERN: return ConfigurationHelper.skythern;
                case MORTUM: return ConfigurationHelper.mortum;
                case ARCANA: return ConfigurationHelper.arcana;
                default: return ConfigurationHelper.vethea;
            }
        }

        /** Знак охотника этой главы. */
        public Trinket mark() {
            return Trinket.valueOf("HUNTER_" + name());
        }
    }

    /** Знак охотника: урон по мобам +MARK_DMG (в своём мире +MARK_DMG_HOME), урон от мобов -MARK_DEF (дома -MARK_DEF_HOME). */
    public static final float MARK_DMG = 0.06F, MARK_DMG_HOME = 0.15F, MARK_DEF = 0.05F, MARK_DEF_HOME = 0.12F;
    public static final int PRICE_COINS = 5;
    static final String KILLS = "dreBestiary", CLAIMED = "dreBestiaryClaimed";
    /** Враги Арканы живут в её подземельях - их ставят спавнеры, в списке спавна биома их нет. */
    static final String[] ARCANA_MOBS = { "DRPGDeathHound", "DRPGDeathcryx", "DRPGDungeonPrisoner", "DRPGLivingStatue", "DRPGRazorback", "DRPGRoamer" };

    private static Map<Chapter, List<String>> chapters;
    private static Map<String, Chapter> chapterOf;
    private static Map<String, Integer> need;

    public Bestiary() {} // обработчик событий

    // ---------------------------------------------------------------- главы

    /** Глава биома по его классу (null - не наш мир). */
    static Chapter chapterOfBiome(BiomeGenBase b) {
        String c = b.getClass().getName();
        if (c.contains(".iceika.")) return Chapter.ICEIKA;
        if (c.contains(".eden.")) return Chapter.EDEN;
        if (c.contains(".wildwood.")) return Chapter.WILDWOOD;
        if (c.contains(".apalachia.")) return Chapter.APALACHIA;
        if (c.contains(".skythern.")) return Chapter.SKYTHERN;
        if (c.contains(".mortum.")) return Chapter.MORTUM;
        if (c.contains(".vethea.")) return Chapter.VETHEA;
        if (c.contains(".arcana.")) return null; // своя ручная глава
        // по типу биома: Biomes O' Plenty подменяет класс ванильного Ада и добавляет свои биомы Ада и обычного мира
        if (com.divinerpgextended.world.Biomes.nether(b)) return Chapter.NETHER;
        if (com.divinerpgextended.world.Biomes.end(b)) return Chapter.END;
        if (c.startsWith("net.divinerpg.") || c.startsWith("com.divinerpgextended.")) return null;
        return Chapter.OVERWORLD;
    }

    static boolean boss(Class<?> c) {
        return net.minecraft.entity.boss.IBossDisplayData.class.isAssignableFrom(c) || net.divinerpg.entities.base.EntityDivineRPGBoss.class.isAssignableFrom(c);
    }

    /** Собрать главы из списков спавна (один раз; на сервере и на клиенте одинаково). */
    public static synchronized void build() {
        if (chapters != null) return;
        Map<String, Chapter> of = new HashMap<String, Chapter>();
        Map<String, Integer> weight = new HashMap<String, Integer>();
        for (BiomeGenBase b : BiomeGenBase.getBiomeGenArray()) {
            if (b == null) continue;
            Chapter ch = chapterOfBiome(b);
            if (ch == null) continue;
            for (net.minecraft.entity.EnumCreatureType t : net.minecraft.entity.EnumCreatureType.values())
                for (Object o : b.getSpawnableList(t)) {
                    BiomeGenBase.SpawnListEntry s = (BiomeGenBase.SpawnListEntry) o;
                    if (!s.entityClass.getName().startsWith("net.divinerpg.") || boss(s.entityClass)) continue;
                    String name = (String) EntityList.classToStringMapping.get(s.entityClass);
                    if (name == null) continue;
                    Chapter was = of.get(name);
                    // моб в нескольких мирах - в главе самого дальнего (паук-эндер - в Энде, а не в обычном мире)
                    if (was == null || ch != Chapter.OVERWORLD && (was == Chapter.OVERWORLD || ch == Chapter.END)) of.put(name, ch);
                    Integer w = weight.get(name);
                    weight.put(name, Math.max(w == null ? 0 : w, s.itemWeight));
                }
        }
        for (String n : ARCANA_MOBS) if (EntityList.stringToClassMapping.containsKey(n)) {
            of.put(n, Chapter.ARCANA);
            weight.put(n, 1);
        }
        Map<Chapter, List<String>> chs = new EnumMap<Chapter, List<String>>(Chapter.class);
        for (Chapter c : Chapter.values()) chs.put(c, new ArrayList<String>());
        for (Map.Entry<String, Chapter> e : of.entrySet()) chs.get(e.getValue()).add(e.getKey());
        Map<String, Integer> nd = new HashMap<String, Integer>();
        for (Chapter c : Chapter.values()) {
            List<String> l = chs.get(c);
            java.util.Collections.sort(l);
            int max = 1;
            for (String n : l) max = Math.max(max, weight.get(n));
            for (String n : l) {
                double r = weight.get(n) / (double) max;
                nd.put(n, r >= 0.4 ? c.base : r >= 0.1 ? Math.max(3, c.base / 2) : Math.max(2, c.base / 4));
            }
        }
        need = nd;
        chapterOf = of;
        chapters = chs;
    }

    public static List<String> mobs(Chapter c) {
        build();
        return chapters.get(c);
    }

    public static Chapter chapterOf(String mob) {
        build();
        return chapterOf.get(mob);
    }

    /** Сколько раз убить моба, чтобы он считался изученным. */
    public static int need(String mob) {
        build();
        Integer n = need.get(mob);
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------- прогресс игрока

    static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static NBTTagCompound kills(EntityPlayer p) {
        NBTTagCompound pd = persisted(p);
        if (!pd.hasKey(KILLS)) pd.setTag(KILLS, new NBTTagCompound());
        return pd.getCompoundTag(KILLS);
    }

    public static int claimed(EntityPlayer p) {
        return persisted(p).getInteger(CLAIMED);
    }

    /** Сколько мобов главы изучено (по данным kills). */
    public static int studied(NBTTagCompound kills, Chapter c) {
        int n = 0;
        for (String m : mobs(c)) if (kills.getInteger(m) >= need(m)) n++;
        return n;
    }

    public static boolean complete(NBTTagCompound kills, Chapter c) {
        return !mobs(c).isEmpty() && studied(kills, c) == mobs(c).size();
    }

    /** Убийство засчитать (не больше нужного). @return true, если этим убийством закрылась глава */
    public static boolean count(EntityPlayer p, String mob) {
        Chapter c = chapterOf(mob);
        if (c == null) return false;
        NBTTagCompound k = kills(p);
        int had = k.getInteger(mob), n = need(mob);
        if (had >= n) return false;
        boolean wasDone = complete(k, c);
        k.setInteger(mob, had + 1);
        return !wasDone && complete(k, c);
    }

    /** Подручные боссов и поднятые боссами - не в счёт (иначе бестиарий фармится на арене). */
    static boolean summoned(Entity e) {
        NBTTagCompound d = e.getEntityData();
        return com.divinerpgextended.perk.RangedRoles.bossAdd(e) || d.hasKey(com.divinerpgextended.boss.MortumArenaBosses.ARCHER_ADD)
            || d.hasKey(com.divinerpgextended.boss.MortumArenaBosses.SOUL) || d.getBoolean(com.divinerpgextended.boss.MortumArenaBosses.REVIVED);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote || !(e.source.getEntity() instanceof EntityPlayer) || v instanceof EntityPlayer || summoned(v)) return;
        String name = EntityList.getEntityString(v);
        if (name == null) return;
        EntityPlayer p = (EntityPlayer) e.source.getEntity();
        if (count(p, name)) {
            Chapter c = chapterOf(name);
            ChatComponentTranslation m = new ChatComponentTranslation("dre.bestiary.chapter_done", new ChatComponentTranslation("dre.bestiary.chapter." + c.key()));
            m.getChatStyle().setColor(EnumChatFormatting.GOLD);
            p.addChatMessage(m);
        }
    }

    // ---------------------------------------------------------------- знаки охотника

    /** Лучший надетый знак: {урон по мобам, защита от мобов}; знаки не складываются. */
    public static float[] mark(EntityPlayer p) {
        float dmg = 0, def = 0;
        for (Chapter c : Chapter.values()) {
            if (!Accessories.wears(p, c.mark())) continue;
            boolean home = p.dimension == c.dim();
            dmg = Math.max(dmg, home ? MARK_DMG_HOME : MARK_DMG);
            def = Math.max(def, home ? MARK_DEF_HOME : MARK_DEF);
        }
        return new float[] { dmg, def };
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        EntityLivingBase v = e.entityLiving;
        if (v.worldObj.isRemote) return;
        Entity src = e.source.getEntity();
        if (src instanceof EntityPlayer && !(v instanceof EntityPlayer)) {
            float b = mark((EntityPlayer) src)[0];
            if (b > 0) e.ammount *= 1 + b;
        } else if (v instanceof EntityPlayer && src instanceof EntityLivingBase && !(src instanceof EntityPlayer)) {
            float d = mark((EntityPlayer) v)[1];
            if (d > 0) e.ammount *= 1 - d;
        }
    }

    // ---------------------------------------------------------------- торговец скотом

    /** Торговец скотом: продаёт книгу; с книгой в руке - выдаёт знаки за закрытые главы. */
    @SubscribeEvent
    public void onInteract(EntityInteractEvent e) {
        if (!(e.target instanceof EntityLivestockMerchant)) return;
        EntityPlayer p = e.entityPlayer;
        ItemStack held = p.getCurrentEquippedItem();
        if (held != null && held.getItem() == ModItems.bestiary) {
            e.setCanceled(true); // с книгой - разговор о главах, не торговля
            if (!p.worldObj.isRemote) claim(p);
            return;
        }
        if (p.isSneaking()) return;
        // окно торговли Divine строит список на клиенте - дописываем на обеих сторонах
        MerchantRecipeList list = ((EntityLivestockMerchant) e.target).getRecipes(p);
        if (list == null) return;
        for (java.util.Iterator<?> it = list.iterator(); it.hasNext();) {
            ItemStack sell = ((MerchantRecipe) it.next()).getItemToSell();
            if (sell != null && sell.getItem() == ModItems.bestiary) it.remove();
        }
        list.add(new InfiniteTrade(new ItemStack(Items.book), new ItemStack(VanillaItemsOther.shadowCoins, PRICE_COINS), new ItemStack(ModItems.bestiary)));
    }

    /** Выдать знаки за закрытые и ещё не награждённые главы. @return сколько выдано */
    public static int claim(EntityPlayer p) {
        NBTTagCompound k = kills(p);
        int mask = claimed(p), given = 0;
        for (Chapter c : Chapter.values()) {
            if ((mask & 1 << c.ordinal()) != 0 || !complete(k, c)) continue;
            mask |= 1 << c.ordinal();
            ItemStack mark = new ItemStack(ModItems.trinket, 1, c.mark().ordinal());
            if (!p.inventory.addItemStackToInventory(mark) || mark.stackSize > 0) p.entityDropItem(mark, 0.5F);
            ChatComponentTranslation m = new ChatComponentTranslation("dre.bestiary.reward", new ChatComponentTranslation("dre.bestiary.chapter." + c.key()));
            m.getChatStyle().setColor(EnumChatFormatting.GOLD);
            p.addChatMessage(m);
            com.divinerpgextended.audit.Audit.log(p, "Bestiary", "знак охотника: " + c.key());
            given++;
        }
        persisted(p).setInteger(CLAIMED, mask);
        if (given == 0) {
            // ближе всего к закрытию
            Chapter best = null;
            double br = -1;
            for (Chapter c : Chapter.values()) {
                if ((mask & 1 << c.ordinal()) != 0 || mobs(c).isEmpty()) continue;
                double r = studied(k, c) / (double) mobs(c).size();
                if (r > br) {
                    br = r;
                    best = c;
                }
            }
            if (best == null) p.addChatMessage(new ChatComponentTranslation("dre.bestiary.all_done"));
            else p.addChatMessage(new ChatComponentTranslation("dre.bestiary.nothing", new ChatComponentTranslation("dre.bestiary.chapter." + best.key()),
                studied(k, best), mobs(best).size()));
        } else p.inventoryContainer.detectAndSendChanges();
        return given;
    }

    // ---------------------------------------------------------------- сеть: открыть книгу

    public static void registerMessages() {
        ColdHandler.NET.registerMessage(OpenHandler.class, Open.class, 11, Side.CLIENT);
    }

    /** Открыть книгу у игрока: свежий прогресс с сервера. */
    public static void open(EntityPlayer p) {
        if (p instanceof EntityPlayerMP && !(p instanceof net.minecraftforge.common.util.FakePlayer))
            ColdHandler.NET.sendTo(new Open(kills(p), claimed(p)), (EntityPlayerMP) p);
    }

    /** Сервер -> клиент: убийства по мобам и награждённые главы. */
    public static class Open implements IMessage {

        public NBTTagCompound kills;
        public int claimed;

        public Open() {}

        public Open(NBTTagCompound kills, int claimed) {
            this.kills = kills;
            this.claimed = claimed;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            kills = ByteBufUtils.readTag(buf);
            claimed = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeTag(buf, kills);
            buf.writeInt(claimed);
        }
    }

    public static class OpenHandler implements IMessageHandler<Open, IMessage> {

        @Override
        public IMessage onMessage(Open msg, MessageContext ctx) {
            com.divinerpgextended.client.GuiBestiary.open(msg.kills == null ? new NBTTagCompound() : msg.kills, msg.claimed);
            return null;
        }
    }
}
