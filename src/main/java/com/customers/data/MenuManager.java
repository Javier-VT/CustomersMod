package com.customers.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.neoforged.fml.ModList;

/**
 * Food catalogue. Every registered item that is really edible (has the FOOD component) from ANY mod is
 * a candidate; it is classified (category + price tier) from common tags, use animation, nutrition and
 * name words. Restaurants only store which foods are DISABLED, so foods from newly added mods are
 * automatically on the menu. Used on both the server and the client (same registries).
 */
public final class MenuManager {
    private MenuManager() {}

    public enum Category {
        MEAL("meal"), BAKERY("bakery"), DRINK("drink"), PRODUCE("produce"), MEAT_FISH("meat_fish"), OTHER("other");
        public final String key;
        Category(String key) { this.key = key; }
        public Component label() { return Component.translatable("customers.cat." + key); }
    }

    public record Entry(ResourceLocation id, String mod, Category cat, int tier, int nutrition) {
        public Item item() { return BuiltInRegistries.ITEM.get(id); }
        public ItemStack stack() { return new ItemStack(item()); }
        public Component name() { return stack().getHoverName(); }
        public String modName() { return MenuManager.modName(mod); }
    }

    public static final List<Entry> ALL = new ArrayList<>();
    /** namespace -> display name, only mods that have food. */
    public static final Map<String, String> MODS = new TreeMap<>();
    private static final Map<String, Deque<String>> RECENT = new HashMap<>();
    private static boolean built;

    // ------------------------------------------------------------------ classification data
    private static Set<String> words(String s) { return new HashSet<>(Arrays.asList(s.split(" "))); }

    private static final Set<String> EXCLUDE_WORDS = words("rotten spider poisonous poison pufferfish spoiled uncooked raw suspicious");
    private static final Set<String> EXCLUDE_IDS = Set.of("minecraft:chorus_fruit", "minecraft:enchanted_golden_apple");
    private static final Set<String> DRINK_WORDS = words("juice tea coffee cider smoothie shake milkshake soda lemonade cocoa wine beer ale mead brew latte espresso drink nectar punch eggnog");
    private static final Set<String> MEAL_WORDS = words("soup stew salad burger hamburger cheeseburger sandwich pizza pasta noodle noodles spaghetti macaroni rice risotto lasagna sushi taco tacos burrito quesadilla enchilada curry omelet omelette roast casserole dumpling dumplings bowl plate meal feast dinner lunch breakfast combo kebab skewer chili chowder bisque goulash wrap fries hotdog stir fry paella ramen pot pie_meat");
    private static final Set<String> BAKERY_WORDS = words("cake pie cookie cookies biscuit bread toast donut doughnut muffin pastry croissant bagel brownie pancake pancakes waffle waffles tart cupcake pudding flan candy chocolate caramel toffee fudge marshmallow macaron churro crepe tiramisu cheesecake shortcake strudel cobbler parfait sundae mousse icecream gelato sorbet bun roll scone pretzel popcorn cracker wafer jam jelly honey");
    private static final Set<String> MEAT_WORDS = words("steak chop chops bacon sausage ribs ham nugget nuggets fillet filet beef pork mutton chicken cod salmon fish lamb venison cooked jerky meatball meatballs");
    private static final Set<String> PRODUCE_WORDS = words("apple carrot potato beetroot melon berries berry tomato onion cabbage lettuce corn pumpkin grapes grape banana orange lemon cherry peach pear mango pineapple avocado cucumber pepper garlic mushroom kelp fruit vegetable salad_ingredient radish turnip");

    private static final Map<String, TagKey<Item>> TAGS = new HashMap<>();
    private static TagKey<Item> tag(String path) {
        return TAGS.computeIfAbsent(path, p -> TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", p)));
    }

    private static boolean hasAny(Set<String> set, String[] parts) {
        for (String p : parts) if (set.contains(p)) return true;
        return false;
    }

    // ------------------------------------------------------------------ building
    public static String modName(String namespace) {
        if (namespace.equals("minecraft")) return "Vanilla";
        String shown = ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(null);
        if (shown != null && !shown.isBlank()) return shown;
        StringBuilder sb = new StringBuilder();
        for (String p : namespace.split("[_-]")) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static boolean excluded(ResourceLocation id, ItemStack st, FoodProperties fp, String[] parts) {
        if (EXCLUDE_IDS.contains(id.toString())) return true;
        if (hasAny(EXCLUDE_WORDS, parts)) return true;
        if (st.is(tag("foods/raw_meat")) || st.is(tag("foods/raw_fish")) || st.is(tag("foods/food_poisoning"))) return true;
        for (FoodProperties.PossibleEffect pe : fp.effects()) {
            if (pe.probability() >= 0.3F && pe.effect().getEffect().value().getCategory() == MobEffectCategory.HARMFUL) return true;
        }
        return false;
    }

    private static Category classify(ItemStack st, FoodProperties fp, String[] parts) {
        if (st.getUseAnimation() == UseAnim.DRINK || hasAny(DRINK_WORDS, parts)) return Category.DRINK;
        if (st.is(tag("foods/soup")) || hasAny(MEAL_WORDS, parts)) return Category.MEAL;
        if (st.is(tag("foods/pie")) || st.is(tag("foods/cookie")) || st.is(tag("foods/bread")) || st.is(tag("foods/candy"))
                || st.is(tag("foods/dessert")) || hasAny(BAKERY_WORDS, parts)) return Category.BAKERY;
        if (st.is(tag("foods/cooked_meat")) || st.is(tag("foods/cooked_fish")) || hasAny(MEAT_WORDS, parts)) return Category.MEAT_FISH;
        if (st.is(tag("foods/fruit")) || st.is(tag("foods/vegetable")) || st.is(tag("foods/berry")) || hasAny(PRODUCE_WORDS, parts)) return Category.PRODUCE;
        return fp.nutrition() >= 7 ? Category.MEAL : Category.OTHER;
    }

    public static synchronized void ensure() {
        if (!built || ALL.isEmpty()) generate();
    }

    public static synchronized void generate() {
        ALL.clear();
        MODS.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            ItemStack st;
            FoodProperties fp;
            try {
                st = new ItemStack(item);
                if (st.isEmpty()) continue;
                fp = st.get(DataComponents.FOOD);
            } catch (Exception e) {
                continue;
            }
            if (fp == null) continue;
            String[] parts = id.getPath().split("[_/]");
            try {
                if (excluded(id, st, fp, parts)) continue;
                Category cat = classify(st, fp, parts);
                int n = fp.nutrition();
                int tier = n <= 3 ? 1 : n <= 7 ? 2 : 3;
                ALL.add(new Entry(id, id.getNamespace(), cat, tier, n));
                MODS.computeIfAbsent(id.getNamespace(), MenuManager::modName);
            } catch (Exception e) {
                // an exotic item that fails to classify is simply skipped
            }
        }
        if (ALL.isEmpty()) {
            ResourceLocation apple = ResourceLocation.withDefaultNamespace("apple");
            ALL.add(new Entry(apple, "minecraft", Category.PRODUCE, 2, 4));
            MODS.put("minecraft", "Vanilla");
        }
        ALL.sort((a, b) -> {
            int c = a.mod().compareTo(b.mod());
            return c != 0 ? c : a.id().getPath().compareTo(b.id().getPath());
        });
        built = true;
    }

    public static Entry find(String id) {
        ensure();
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        for (Entry e : ALL) if (e.id().equals(rl)) return e;
        return null;
    }

    // ------------------------------------------------------------------ restaurant menu
    /** Foods the restaurant serves: everything except what the owner disabled. */
    public static List<Entry> allowed(RestaurantData.Restaurant r) {
        ensure();
        if (r == null || r.disabled.isEmpty()) return ALL;
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL) if (!r.disabled.contains(e.id().toString())) out.add(e);
        return out.isEmpty() ? ALL : out;
    }

    private static double timeFactor(Category c, long dayTime) {
        boolean morning = dayTime < 3500, noon = dayTime >= 3500 && dayTime < 8500;
        return switch (c) {
            case BAKERY -> morning ? 2.2 : noon ? 0.8 : 1.2;
            case DRINK -> morning ? 1.8 : noon ? 1.0 : 1.3;
            case PRODUCE -> morning ? 1.3 : 1.0;
            case MEAT_FISH -> morning ? 0.6 : noon ? 1.5 : 1.4;
            case MEAL -> morning ? 0.7 : noon ? 2.0 : 1.5;
            default -> 0.8;
        };
    }

    /**
     * Weighted random order: depends on time of day (breakfast pastries/drinks, lunch meals...), on the
     * restaurant's reputation (better reputation = fancier orders) and avoids repeating recent orders.
     */
    public static Entry pick(RestaurantData.Restaurant r, long dayTime, RandomSource rnd) {
        List<Entry> pool = allowed(r);
        if (pool.isEmpty()) return null;
        int rep = r == null ? 50 : r.rep;
        Deque<String> recent = r == null ? null : RECENT.computeIfAbsent(r.key(), k -> new ArrayDeque<>());
        double[] w = new double[pool.size()];
        double sum = 0;
        for (int i = 0; i < w.length; i++) {
            Entry e = pool.get(i);
            double x = timeFactor(e.cat(), dayTime);
            if (e.tier() == 3) x *= 0.35 + rep / 100.0 * 1.3;
            else if (e.tier() == 1) x *= 1.4 - rep / 100.0 * 0.7;
            if (recent != null && recent.contains(e.id().toString())) x *= 0.15;
            w[i] = Math.max(0.01, x);
            sum += w[i];
        }
        double t = rnd.nextDouble() * sum;
        Entry chosen = pool.get(pool.size() - 1);
        for (int i = 0; i < w.length; i++) {
            t -= w[i];
            if (t <= 0) { chosen = pool.get(i); break; }
        }
        if (recent != null) {
            recent.addLast(chosen.id().toString());
            while (recent.size() > 6) recent.removeFirst();
        }
        return chosen;
    }
}
