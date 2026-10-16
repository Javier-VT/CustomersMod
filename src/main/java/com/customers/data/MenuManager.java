package com.customers.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Builds the list of orderable food automatically from every registered item (vanilla + other mods). */
public final class MenuManager {
    private MenuManager() {}

    public record Entry(ResourceLocation id, String addon) {
        public Item item() {
            return BuiltInRegistries.ITEM.get(id);
        }
        public Component name() {
            return new ItemStack(item()).getHoverName();
        }
    }

    public static final List<Entry> ITEMS = new ArrayList<>();
    public static final List<String> FILTERS = new ArrayList<>(List.of("any", "Vanilla"));

    private static final Set<String> VANILLA_FOOD = Set.of(
            "minecraft:apple", "minecraft:bread", "minecraft:cooked_beef", "minecraft:cooked_chicken",
            "minecraft:cooked_porkchop", "minecraft:pumpkin_pie", "minecraft:baked_potato", "minecraft:cookie",
            "minecraft:golden_apple", "minecraft:melon_slice", "minecraft:mushroom_stew", "minecraft:beetroot_soup",
            "minecraft:rabbit_stew");

    private static final Set<String> BANNED = words(
            "table chair stool counter cabinet sink stove oven fridge freezer stand display shelf rack showcase case dome booth lamp light "
          + "sign banner register cash till pos terminal cart trolley wagon picnic umbrella parasol hamper basket blanket cloth mat towel "
          + "skillet knife board spatula cutlery plate cup glass tool sword bucket book machine maker mill mixer whisk roller slicer tongs "
          + "peeler grater grinder blender tray platter bowl dish mold mould box crate bag bundle seed seeds sack jar bottle tub barrel "
          + "raw dough flour sugar salt oil butter yeast batter mix wood plank log leaves bush tree sapling fence door window "
          + "crop plant root sprout stem vine wild unripe fresh hat apron uniform chef outfit costume boots gloves jacket wear");

    private static final Set<String> DISH = words(
            "cake pie burger hamburger cheeseburger sandwich soup salad stew broth tea coffee juice drink smoothie shake soda cider lemonade "
          + "slice tart pudding cookie biscuit donut doughnut muffin pastry bread toast pancake waffle croissant bagel cupcake brownie eclair "
          + "pizza pasta noodle noodles spaghetti macaroni rice risotto lasagna roast steak chop bacon sausage hotdog ham ribs nugget nuggets "
          + "taco tacos burrito quesadilla enchilada sushi roll dumpling omelet omelette meal feast dinner lunch breakfast combo snack platter bowl "
          + "curry goulash chili chowder bisque casserole kebab skewer fries chips tortilla pretzel popcorn candy caramel chocolate toffee "
          + "fudge marshmallow gelatin jello flan wafer macaron churro crepe tiramisu cheesecake shortcake strudel cobbler parfait sundae mousse empanada "
          + "cooked baked fried grilled boiled smoked roasted glazed steamed toasted mashed stuffed candied pickled cured seasoned "
          + "sauce dip jam jelly syrup cream icecream gelato sorbet yogurt");

    private static Set<String> words(String s) {
        return new HashSet<>(Arrays.asList(s.split(" ")));
    }

    public static String addonName(String namespace) {
        if (namespace.equals("minecraft")) return "Vanilla";
        StringBuilder sb = new StringBuilder();
        for (String p : namespace.split("[_-]")) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public static void generate() {
        ITEMS.clear();
        Set<String> addons = new TreeSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String full = id.toString();
            if (VANILLA_FOOD.contains(full)) {
                ITEMS.add(new Entry(id, "Vanilla"));
                continue;
            }
            if (id.getNamespace().equals("minecraft")) continue;
            try {
                if (!new ItemStack(item).has(DataComponents.FOOD)) continue;
            } catch (Exception e) {
                continue;
            }
            String[] parts = full.split("[:_]");
            boolean banned = false, dish = false;
            for (String p : parts) {
                if (BANNED.contains(p)) banned = true;
                if (DISH.contains(p)) dish = true;
            }
            if (!banned && dish) {
                String addon = addonName(id.getNamespace());
                ITEMS.add(new Entry(id, addon));
                addons.add(addon);
            }
        }
        if (ITEMS.isEmpty()) ITEMS.add(new Entry(ResourceLocation.withDefaultNamespace("apple"), "Vanilla"));
        FILTERS.clear();
        FILTERS.add("any");
        FILTERS.add("Vanilla");
        FILTERS.addAll(addons);
    }

    public static List<Entry> menuFor(String filter) {
        if (filter == null || filter.equals("any") || filter.isEmpty()) return ITEMS;
        List<Entry> out = new ArrayList<>();
        for (Entry e : ITEMS) if (e.addon().equalsIgnoreCase(filter)) out.add(e);
        return out.isEmpty() ? ITEMS : out;
    }

    public static Entry find(String id) {
        for (Entry e : ITEMS) if (e.id().toString().equals(id)) return e;
        return null;
    }
}
