package com.customers.client;

import com.customers.data.MenuManager;
import com.customers.data.MenuManager.Category;
import com.customers.data.MenuManager.Entry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Icon grid with search + category + mod filters. Used both to edit a restaurant's menu (click toggles a food
 * on/off) and to pick a takeaway order (click chooses the food).
 */
public class FoodGridScreen extends Screen {
    private static final int COLS = 9, ROWS = 4, CELL = 22;

    private final Screen parent;
    private final boolean edit;
    private final Set<String> disabled;
    /** pick mode: the foods the restaurant serves right now. */
    private final Set<String> menu;
    private final Set<String> discovered;
    private final boolean auto;
    private final Consumer<Set<String>> onDone;
    private final Consumer<String> onPick;

    private final List<Entry> shown = new ArrayList<>();
    private EditBox search;
    private String query = "";
    private int catIdx = -1;          // -1 = all categories
    private String modSel = "";       // "" = all mods
    private int page;
    private int gx, gy;

    private FoodGridScreen(Screen parent, boolean edit, Set<String> disabled, Set<String> menu, boolean auto, Set<String> discovered,
                           Consumer<Set<String>> onDone, Consumer<String> onPick) {
        super(Component.translatable(edit ? "customers.menu.title" : "customers.bell.pick_title"));
        this.parent = parent;
        this.edit = edit;
        this.disabled = new HashSet<>(disabled);
        this.menu = menu;
        this.auto = auto;
        this.discovered = discovered;
        this.onDone = onDone;
        this.onPick = onPick;
    }

    public static FoodGridScreen edit(Screen parent, Set<String> disabled, boolean auto, Set<String> discovered, Consumer<Set<String>> onDone) {
        return new FoodGridScreen(parent, true, disabled, Set.of(), auto, discovered, onDone, null);
    }

    public static FoodGridScreen pick(Screen parent, Set<String> menu, Consumer<String> onPick) {
        return new FoodGridScreen(parent, false, Set.of(), menu, false, Set.of(), null, onPick);
    }

    /** 0 = on the menu, 1 = enabled but waiting to be discovered, 2 = removed by the owner. */
    private int stateOf(Entry e) {
        String id = e.id().toString();
        if (disabled.contains(id)) return 2;
        if (auto && !discovered.contains(id)) return 1;
        return 0;
    }

    private long onCount() {
        long n = 0;
        for (Entry e : MenuManager.ALL) if (stateOf(e) == 0) n++;
        return n;
    }

    // ------------------------------------------------------------------ layout
    @Override
    protected void init() {
        MenuManager.ensure();
        int w = COLS * CELL;
        gx = (width - w) / 2;
        gy = 56;

        search = new EditBox(font, gx, 30, 72, 18, Component.translatable("customers.menu.search"));
        search.setHint(Component.translatable("customers.menu.search"));
        search.setValue(query);
        search.setResponder(s -> {
            query = s.toLowerCase(Locale.ROOT).trim();
            page = 0;
            refilter();
        });
        addRenderableWidget(search);

        List<Integer> cats = new ArrayList<>();
        cats.add(-1);
        for (int i = 0; i < Category.values().length; i++) cats.add(i);
        addRenderableWidget(CycleButton.<Integer>builder(i -> i < 0 ? Component.translatable("customers.menu.all_cats") : Category.values()[i].label())
                .withValues(cats).withInitialValue(catIdx).displayOnlyValue()
                .create(gx + 74, 30, 62, 18, Component.empty(), (b, v) -> { catIdx = v; page = 0; refilter(); }));

        List<String> mods = new ArrayList<>();
        mods.add("");
        mods.addAll(MenuManager.MODS.keySet());
        addRenderableWidget(CycleButton.<String>builder(s -> s.isEmpty() ? Component.translatable("customers.menu.all_mods") : Component.literal(MenuManager.MODS.get(s)))
                .withValues(mods).withInitialValue(mods.contains(modSel) ? modSel : "").displayOnlyValue()
                .create(gx + 138, 30, w - 138, 18, Component.empty(), (b, v) -> { modSel = v; page = 0; refilter(); }));

        int by = gy + ROWS * CELL + 6;
        addRenderableWidget(Button.builder(Component.translatable("customers.bell.prev"), b -> { page--; clampPage(); }).bounds(gx, by, 50, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("customers.bell.next"), b -> { page++; clampPage(); }).bounds(gx + w - 50, by, 50, 18).build());

        int ay = by + 22;
        if (edit) {
            int w3 = (w - 8) / 3;
            addRenderableWidget(Button.builder(Component.translatable("customers.menu.enable_shown"), b -> {
                for (Entry e : shown) disabled.remove(e.id().toString());
            }).bounds(gx, ay, w3, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("customers.menu.disable_shown"), b -> {
                for (Entry e : shown) disabled.add(e.id().toString());
            }).bounds(gx + w3 + 4, ay, w3, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("customers.menu.done"), b -> {
                onDone.accept(disabled);
                minecraft.setScreen(parent);
            }).bounds(gx + 2 * (w3 + 4), ay, w3, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("customers.bell.back"), b -> minecraft.setScreen(parent)).bounds(gx, ay, w, 20).build());
        }
        refilter();
    }

    private void refilter() {
        shown.clear();
        List<Entry> src = MenuManager.ALL;
        List<String[]> keyed = new ArrayList<>();
        for (Entry e : src) {
            if (!edit && !menu.contains(e.id().toString())) continue;
            if (catIdx >= 0 && e.cat() != Category.values()[catIdx]) continue;
            if (!modSel.isEmpty() && !e.mod().equals(modSel)) continue;
            if (!query.isEmpty()) {
                String hay = (e.name().getString() + " " + e.modName() + " " + e.id()).toLowerCase(Locale.ROOT);
                if (!hay.contains(query)) continue;
            }
            shown.add(e);
        }
        shown.sort((a, b) -> a.name().getString().compareToIgnoreCase(b.name().getString()));
        clampPage();
    }

    private int pages() {
        return Math.max(1, (shown.size() + COLS * ROWS - 1) / (COLS * ROWS));
    }

    private void clampPage() {
        page = Math.max(0, Math.min(pages() - 1, page));
    }

    // ------------------------------------------------------------------ input
    private int cellAt(double mx, double my) {
        int cx = (int) Math.floor((mx - gx) / CELL), cy = (int) Math.floor((my - gy) / CELL);
        if (mx < gx || my < gy || cx < 0 || cx >= COLS || cy < 0 || cy >= ROWS) return -1;
        int idx = page * COLS * ROWS + cy * COLS + cx;
        return idx < shown.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int idx = cellAt(mx, my);
        if (idx < 0) return false;
        Entry e = shown.get(idx);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        String id = e.id().toString();
        if (edit) {
            if (!disabled.remove(id)) disabled.add(id);
        } else {
            onPick.accept(id);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        page += sy < 0 ? 1 : -1;
        clampPage();
        return true;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        int hovered = cellAt(mx, my);
        int start = page * COLS * ROWS;
        for (int i = 0; i < COLS * ROWS && start + i < shown.size(); i++) {
            Entry e = shown.get(start + i);
            int x = gx + (i % COLS) * CELL, y = gy + (i / COLS) * CELL;
            int stt = edit ? stateOf(e) : 0;
            int bg = !edit ? 0xAA303030 : stt == 2 ? 0xAA7A2020 : stt == 1 ? 0xAA3A4466 : 0xAA2F7A2F;
            g.fill(x, y, x + CELL - 1, y + CELL - 1, bg);
            g.renderItem(e.stack(), x + 3, y + 3);
            if (stt == 2) g.fill(x, y, x + CELL - 1, y + CELL - 1, 0x80000000);
            else if (stt == 1) g.fill(x, y, x + CELL - 1, y + CELL - 1, 0x55000000);
            if (start + i == hovered) g.renderOutline(x, y, CELL - 1, CELL - 1, 0xFFFFFFFF);
        }
        if (shown.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("customers.menu.none"), width / 2, gy + ROWS * CELL / 2 - 4, 0xAAAAAA);
        }

        int by = gy + ROWS * CELL + 6;
        g.drawCenteredString(font, Component.translatable("customers.bell.page", page + 1, pages()), width / 2, by + 5, 0xAAAAAA);
        if (edit) {
            long pending = MenuManager.ALL.stream().filter(e -> stateOf(e) == 1).count();
            g.drawCenteredString(font, Component.translatable("customers.menu.count", onCount(), MenuManager.ALL.size()), width / 2, by + 46, 0xFFD27F);
            if (auto) g.drawCenteredString(font, Component.translatable("customers.menu.pending", pending), width / 2, by + 57, 0x9FB0FF);
        }

        if (hovered >= 0) {
            Entry e = shown.get(hovered);
            ItemStack st = e.stack();
            List<Component> tip = new ArrayList<>();
            tip.add(st.getHoverName());
            tip.add(Component.translatable("customers.menu.tip_mod", e.modName()).withStyle(s -> s.withColor(0xAAAAAA)));
            tip.add(Component.translatable("customers.menu.tip_cat", e.cat().label()).withStyle(s -> s.withColor(0xAAAAAA)));
            tip.add(Component.translatable("customers.menu.tip_tier", "★".repeat(e.tier()) + "☆".repeat(3 - e.tier())).withStyle(s -> s.withColor(0xFFD27F)));
            if (edit) {
                int stt = stateOf(e);
                tip.add(Component.translatable(stt == 2 ? "customers.menu.tip_off" : stt == 1 ? "customers.menu.tip_pending" : "customers.menu.tip_on")
                        .withStyle(s -> s.withColor(stt == 2 ? 0xFF5555 : stt == 1 ? 0x9FB0FF : 0x55FF55)));
            }
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
