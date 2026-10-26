package com.divinerpgextended.vethea;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.divinerpg.utils.items.VetheaItems;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.divinerpgextended.registry.ModItems;
import com.divinerpgextended.trinket.Trinket;

/**
 * Алтарь наполнения Ветеи. У Divine в левый верхний слот кладут глыбы, в нижний - шаблон, и рецепт срабатывал только при
 * точном числе глыб. Здесь (через {@link com.divinerpgextended.asm.VetheaTransformer}): глыб можно положить больше -
 * уйдёт ровно сколько нужно; плюс рецепты аддона: Крылья сна трёх ступеней и лунное оружие (оружие из арксиана +
 * лунные осколки с Леди Луны).
 */
public final class Infusion {

    /** Рецепт: глыбы (слот 0) count штук + шаблон (слот 1) = результат; шаблон тратится. */
    public static class Recipe {

        public final ItemStack lump, template, output;
        public final int count;
        /** Шаблон-крылья: только закалённые / только незакалённые (null - не важно). */
        final Boolean tempered;

        Recipe(ItemStack lump, int count, ItemStack template, ItemStack output, Boolean tempered) {
            this.lump = lump;
            this.count = count;
            this.template = template;
            this.output = output;
            this.tempered = tempered;
        }

        boolean matches(ItemStack l, ItemStack t) {
            if (l == null || t == null || l.stackSize < count || !same(l, lump) || !same(t, template)) return false;
            return tempered == null || tempered == tempered(t);
        }

        ItemStack result(ItemStack t) {
            return output.copy();
        }
    }

    private static final String TEMPERED = "dre_tempered";
    private static List<Recipe> own;
    private static List<Recipe> all;

    private Infusion() {}

    static boolean same(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && (!a.getHasSubtypes() || a.getItemDamage() == b.getItemDamage());
    }

    public static boolean tempered(ItemStack s) {
        return s != null && s.hasTagCompound() && s.getTagCompound().getBoolean(TEMPERED);
    }

    public static ItemStack wings(Trinket t, boolean tempered) {
        ItemStack s = new ItemStack(ModItems.trinket, 1, t.ordinal());
        if (tempered) {
            s.setTagCompound(new NBTTagCompound());
            s.getTagCompound().setBoolean(TEMPERED, true);
        }
        return s;
    }

    /** Предмет Ветеи по имени поля или по концу внутреннего имени (в разных версиях Divine поля названы по-разному). */
    public static Item vethea(String... names) {
        for (String n : names) {
            try {
                Object o = VetheaItems.class.getField(n).get(null);
                if (o instanceof Item) return (Item) o;
            } catch (Throwable ignored) {}
        }
        for (Field f : VetheaItems.class.getFields()) {
            try {
                Object o = f.get(null);
                if (!(o instanceof Item)) continue;
                String u = ((Item) o).getUnlocalizedName();
                for (String n : names) if (u != null && u.endsWith("." + n)) return (Item) o;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** Лунное оружие: [оружие из арксиана, оружие Луны]. */
    public static Item[][] lunar() {
        return new Item[][] { { vethea("arksianeSword", "arksianeWrathsword"), vethea("everlight") },
            { vethea("arksianeClaw", "arksianeWrathclaw"), vethea("everbright") },
            { vethea("arksianeBow", "arksianeWrathbow"), vethea("everfright") },
            { vethea("arksianeStaff", "arksianeWrathstaff"), vethea("evernight") },
            { vethea("arksianeCannon", "arksianeWrathcannon"), vethea("eversight") } };
    }

    public static final int LUNAR_SHARDS = 4;

    public static synchronized List<Recipe> own() {
        if (own != null) return own;
        List<Recipe> l = new ArrayList<Recipe>();
        // крылья: шаблоны у Голода; I - амтирмис, II - цермил (с Королевы улья), III - закалка каросом, затем квадротик
        l.add(new Recipe(new ItemStack(VetheaItems.amthirmisLump), 8, DreamItem.stack(DreamItem.WINGS_TEMPLATE),
            wings(Trinket.DREAM_WINGS_1, false), null));
        l.add(new Recipe(new ItemStack(VetheaItems.cermileLump), 12, DreamItem.stack(DreamItem.QUEEN_TEMPLATE),
            wings(Trinket.DREAM_WINGS_2, false), null));
        l.add(new Recipe(new ItemStack(VetheaItems.karosLump), 16, wings(Trinket.DREAM_WINGS_2, false),
            wings(Trinket.DREAM_WINGS_2, true), false));
        l.add(new Recipe(new ItemStack(VetheaItems.quadroticLump), 16, wings(Trinket.DREAM_WINGS_2, true),
            wings(Trinket.DREAM_WINGS_3, false), true));
        for (Item[] p : lunar()) {
            if (p[0] == null || p[1] == null) continue;
            l.add(new Recipe(DreamItem.stack(DreamItem.LUNAR_SHARD), LUNAR_SHARDS, new ItemStack(p[0]), new ItemStack(p[1]), null));
        }
        return own = Collections.unmodifiableList(l);
    }

    /** Рецепты Divine (из его таблицы: глыбы, шаблон, сколько глыб). */
    @SuppressWarnings("unchecked")
    static List<Recipe> divine() {
        List<Recipe> l = new ArrayList<Recipe>();
        try {
            Class<?> rc = Class.forName("net.divinerpg.utils.recipes.RecipesInfusionTable");
            Map<Object, Item> map = (Map<Object, Item>) rc.getField("recipes").get(null);
            // таблицу Divine заполнял сам алтарь при каждом тике (а его тик теперь наш)
            if (map.isEmpty()) rc.getMethod("addRecipes").invoke(null);
            Class<?> kc = Class.forName("net.divinerpg.utils.recipes.RecipesInfusionTable$InfusionRecipe");
            Field a = kc.getDeclaredField("lump"), b = kc.getDeclaredField("template"), n = kc.getDeclaredField("lumpCount");
            a.setAccessible(true);
            b.setAccessible(true);
            n.setAccessible(true);
            for (Map.Entry<Object, Item> e : map.entrySet()) {
                Item lump = (Item) a.get(e.getKey()), template = (Item) b.get(e.getKey());
                if (lump == null || template == null || e.getValue() == null) continue;
                l.add(new Recipe(new ItemStack(lump), n.getInt(e.getKey()), new ItemStack(template), new ItemStack(e.getValue()), null));
            }
        } catch (Throwable t) {
            com.divinerpgextended.DivineRPGExtended.LOG.warn("Алтарь наполнения: рецепты Divine не прочитаны - " + t);
        }
        return l;
    }

    /** Все рецепты алтаря (Divine и аддона) - для подсказок и NEI. */
    public static synchronized List<Recipe> all() {
        if (all != null) return all;
        List<Recipe> l = new ArrayList<Recipe>(own());
        List<Recipe> d = divine();
        l.addAll(d);
        if (d.isEmpty()) return l; // Divine ещё не готов - не запоминаем
        return all = l;
    }

    public static Recipe match(ItemStack lump, ItemStack template) {
        if (lump == null || template == null) return null;
        for (Recipe r : all()) if (r.matches(lump, template)) return r;
        return null;
    }

    /** Каждый тик алтаря: что лежит в слоте результата. */
    public static void update(ItemStack[] inv) {
        if (inv == null || inv.length < 3) return;
        Recipe r = match(inv[0], inv[1]);
        inv[2] = r == null ? null : r.result(inv[1]);
    }

    /** Результат забрали: глыб - ровно сколько нужно, шаблон - один. */
    public static void take(IInventory inv) {
        Recipe r = match(inv.getStackInSlot(0), inv.getStackInSlot(1));
        if (r == null) return;
        inv.decrStackSize(0, r.count);
        inv.decrStackSize(1, 1);
    }

    /** Рецепты, где вещь - шаблон (для подсказки на шаблонах). */
    public static List<Recipe> byTemplate(ItemStack t) {
        List<Recipe> l = new ArrayList<Recipe>();
        for (Recipe r : all()) if (same(t, r.template) && (r.tempered == null || r.tempered == tempered(t))) l.add(r);
        return l;
    }
}
