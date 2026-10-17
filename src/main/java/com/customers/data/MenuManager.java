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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.neoforged.fml.ModList;

/**
 * Food catalogue. Detection uses NO name keywords: an item is a food when it has the FOOD component, is eaten or
 * drunk (use animation EAT/DRINK, which covers drinks that are not FOOD items) or sits in the common
 * c:drinks / c:foods/edible_when_placed tags. Only dangerous foods are skipped (raw meat/fish, poison, rotten...).
 * Name words are used ONLY as a last-resort fallback to pick the category icon filter.
 * Restaurants store the foods they DISABLED, so foods from newly added mods are automatically on the menu, and
 * optionally only foods the players have already crafted/obtained ("discovered") are served.
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
    private static final Map<String, Entry> INDEX = new HashMap<>();
    private static final Map<String, Deque<String>> RECENT = new HashMap<>();
    private static boolean built;

    // ------------------------------------------------------------------ classification data
    private static Set<String> words(String s) { return new HashSet<>(Arrays.asList(s.split(" "))); }

    private static final Set<String> EXCLUDE_IDS = Set.of("minecraft:chorus_fruit", "minecraft:enchanted_golden_apple", "minecraft:suspicious_stew", "minecraft:ominous_bottle");
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

    private static final java.util.List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> LETHAL =
            java.util.List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.HUNGER);

    private static boolean has(Set<String> tags, String path) { return tags.contains(path); }

    private static boolean tree(Set<String> tags, String root) {
        for (String t : tags) if (t.equals(root) || t.startsWith(root + "/")) return true;
        return false;
    }

    private static boolean excluded(ResourceLocation id, FoodProperties fp, Set<String> tags) {
        if (EXCLUDE_IDS.contains(id.toString())) return true;
        if (has(tags, "foods/raw_meat") || has(tags, "foods/raw_fish") || has(tags, "foods/food_poisoning")) return true;
        if (fp != null) {
            for (FoodProperties.PossibleEffect pe : fp.effects()) {
                if (pe.probability() >= 0.3F && LETHAL.contains(pe.effect().getEffect())) return true;
            }
        }
        return false;
    }

    private static Category classify(Item item, ItemStack st, FoodProperties fp, Set<String> tags, boolean drinkAnim, String[] parts) {
        int n = fp == null ? 4 : fp.nutrition();
        if (drinkAnim || tree(tags, "drinks")) return Category.DRINK;
        if (has(tags, "foods/soup") || has(tags, "foods/cooked_meal")) return Category.MEAL;
        if (has(tags, "foods/pie") || has(tags, "foods/cookie") || has(tags, "foods/bread") || has(tags, "foods/candy")
                || has(tags, "foods/dessert") || has(tags, "foods/edible_when_placed")) return Category.BAKERY;
        if (has(tags, "foods/cooked_meat") || has(tags, "foods/cooked_fish")) return Category.MEAT_FISH;
        if (has(tags, "foods/fruit") || has(tags, "foods/vegetable") || has(tags, "foods/berry")
                || tree(tags, "crops") || tree(tags, "fruits") || tree(tags, "vegetables") || item instanceof ItemNameBlockItem) return Category.PRODUCE;
        // fallback for mods that do not tag their food: name hints only choose the filter category
        if (hasAny(DRINK_WORDS, parts)) return Category.DRINK;
        if (hasAny(MEAL_WORDS, parts)) return Category.MEAL;
        if (hasAny(BAKERY_WORDS, parts)) return Category.BAKERY;
        if (hasAny(MEAT_WORDS, parts)) return Category.MEAT_FISH;
        if (hasAny(PRODUCE_WORDS, parts)) return Category.PRODUCE;
        return n >= 7 || (n >= 5 && st.getMaxStackSize() == 1) ? Category.MEAL : Category.OTHER;
    }

    public static synchronized void ensure() {
        if (!built || ALL.isEmpty()) generate();
    }

    public static synchronized void generate() {
        ALL.clear();
        MODS.clear();
        INDEX.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            try {
                ItemStack st = new ItemStack(item);
                if (st.isEmpty()) continue;
                FoodProperties fp = st.get(DataComponents.FOOD);
                UseAnim anim = st.getUseAnimation();
                Set<String> tags = new HashSet<>();
                st.getTags().forEach(t -> { if (t.location().getNamespace().equals("c")) tags.add(t.location().getPath()); });
                boolean drinkAnim = anim == UseAnim.DRINK;
                boolean edible = fp != null || anim == UseAnim.EAT || drinkAnim || tree(tags, "drinks") || has(tags, "foods/edible_when_placed");
                if (!edible) continue;
                if (item instanceof PotionItem || st.has(DataComponents.POTION_CONTENTS)) continue;
                if (excluded(id, fp, tags)) continue;
                String[] parts = id.getPath().split("[_/]");
                Category cat = classify(item, st, fp, tags, drinkAnim, parts);
                int n = fp == null ? 4 : fp.nutrition();
                int tier = n <= 3 ? 1 : n <= 7 ? 2 : 3;
                Entry en = new Entry(id, id.getNamespace(), cat, tier, n);
                ALL.add(en);
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
        for (Entry e : ALL) INDEX.put(e.id().toString(), e);
        built = true;
    }

    public static Entry find(String id) {
        ensure();
        return id == null ? null : INDEX.get(id);
    }

    /** Shared rule (server and client): is this food on the menu? */
    public static boolean onMenu(String id, Set<String> disabled, boolean auto, Set<String> discovered) {
        return !disabled.contains(id) && (!auto || discovered.contains(id));
    }

    /** Called when a player crafts, picks up, smelts or holds an item. @return true when it is a new food. */
    public static boolean discover(RestaurantData d, ItemStack st) {
        if (st.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        if (!INDEX.containsKey(id)) return false;
        return d.discovered.add(id) && markDirty(d);
    }

    private static boolean markDirty(RestaurantData d) { d.setDirty(); return true; }

    // ------------------------------------------------------------------ restaurant menu
    /** Minimum size of a discovery-only menu; below it the restaurant falls back to all enabled foods. */
    public static final int MIN_AUTO_MENU = 4;

    /** Foods the restaurant serves: not disabled by the owner and, in auto mode, already discovered by the players. */
    public static List<Entry> allowed(RestaurantData d, RestaurantData.Restaurant r) {
        ensure();
        if (r == null) return ALL;
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL) if (onMenu(e.id().toString(), r.disabled, r.auto, d == null ? Set.of() : d.discovered)) out.add(e);
        if (out.size() < MIN_AUTO_MENU && r.auto) {
            // too little discovered yet: serve every food the owner has not disabled
            out.clear();
            for (Entry e : ALL) if (!r.disabled.contains(e.id().toString())) out.add(e);
        }
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
    public static Entry pick(RestaurantData d, RestaurantData.Restaurant r, long dayTime, RandomSource rnd) {
        List<Entry> pool = allowed(d, r);
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
