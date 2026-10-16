package com.customers.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Simple paged list of buttons. */
public class ListPickScreen extends Screen {
    public record Entry(Component label, Runnable onPick) {}

    private static final int PER_PAGE = 6;
    private final Screen parent;
    private final List<Entry> entries;
    private int page;

    public ListPickScreen(Component title, Screen parent, List<Entry> entries) {
        super(title);
        this.parent = parent;
        this.entries = entries;
    }

    private int pages() {
        return Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected void init() {
        int w = 220, x = width / 2 - w / 2, y = Math.max(32, height / 2 - 80);
        int from = page * PER_PAGE;
        for (int i = from; i < Math.min(entries.size(), from + PER_PAGE); i++) {
            Entry e = entries.get(i);
            addRenderableWidget(Button.builder(e.label(), b -> e.onPick().run()).bounds(x, y, w, 20).build());
            y += 24;
        }
        y = Math.max(32, height / 2 - 80) + PER_PAGE * 24 + 4;
        int w3 = (w - 12) / 3;
        Button prev = Button.builder(Component.translatable("customers.bell.prev"), b -> { page--; rebuildWidgets(); }).bounds(x, y, w3, 20).build();
        Button back = Button.builder(Component.translatable("customers.bell.back"), b -> minecraft.setScreen(parent)).bounds(x + w3 + 6, y, w3, 20).build();
        Button next = Button.builder(Component.translatable("customers.bell.next"), b -> { page++; rebuildWidgets(); }).bounds(x + 2 * (w3 + 6), y, w3, 20).build();
        prev.active = page > 0;
        next.active = page < pages() - 1;
        addRenderableWidget(prev);
        addRenderableWidget(back);
        addRenderableWidget(next);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("customers.bell.page", page + 1, pages()), width / 2, 24, 0xAAAAAA);
    }
}
