package com.customers.client;

import com.customers.net.ActionPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class RestaurantScreen extends Screen {
    private final CompoundTag d;
    private final long pos;
    private final List<String> filters = new ArrayList<>();
    private EditBox nameBox;
    private CycleButton<String> filterBtn;
    private CycleButton<Integer> rateBtn;
    private IntSlider patience, max, radius;

    public RestaurantScreen(CompoundTag data) {
        super(Component.translatable("customers.ui.title"));
        this.d = data;
        this.pos = data.getLong("pos");
        for (Tag t : data.getList("filters", Tag.TAG_STRING)) filters.add(t.getAsString());
        if (filters.isEmpty()) filters.add("any");
    }

    private static Component filterName(String s) {
        return s.equals("any") ? Component.translatable("customers.ui.filter_any") : Component.literal(s);
    }

    @Override
    protected void init() {
        int colW = 150, gap = 10, left = width / 2 - colW - gap / 2, right = width / 2 + gap / 2;
        int y = Math.max(34, height / 2 - 70);

        nameBox = new EditBox(font, left, y, colW * 2 + gap, 20, Component.translatable("customers.ui.name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(d.getString("name"));
        nameBox.setHint(Component.translatable("customers.ui.name"));
        addRenderableWidget(nameBox);

        y += 24;
        String cur = d.getString("filter");
        filterBtn = addRenderableWidget(CycleButton.<String>builder(RestaurantScreen::filterName)
                .withValues(filters).withInitialValue(filters.contains(cur) ? cur : "any")
                .create(left, y, colW, 20, Component.translatable("customers.ui.menu"), (b, v) -> { }));
        rateBtn = addRenderableWidget(CycleButton.<Integer>builder(i -> Component.translatable(
                        i == 0 ? "customers.ui.rate.high" : i == 2 ? "customers.ui.rate.low" : "customers.ui.rate.medium"))
                .withValues(List.of(0, 1, 2)).withInitialValue(Math.max(0, Math.min(2, d.getInt("rate"))))
                .create(right, y, colW, 20, Component.translatable("customers.ui.rate"), (b, v) -> { }));

        y += 24;
        patience = addRenderableWidget(new IntSlider(left, y, colW, 20, 1, 20, clamp(d.getInt("patience"), 1, 20),
                v -> Component.translatable("customers.ui.patience").append(" ").append(Component.translatable("customers.ui.minutes", v))));
        max = addRenderableWidget(new IntSlider(right, y, colW, 20, 1, 12, clamp(d.getInt("max"), 1, 12),
                v -> Component.translatable("customers.ui.limit").append(" " + v)));

        y += 24;
        radius = addRenderableWidget(new IntSlider(left, y, colW * 2 + gap, 20, 8, 64, clamp(d.getInt("radius"), 8, 64),
                v -> Component.translatable("customers.ui.radius").append(" ").append(Component.translatable("customers.ui.blocks", v))));

        y += 30;
        int w3 = (colW * 2 + gap - 2 * 6) / 3;
        addRenderableWidget(Button.builder(Component.translatable("customers.ui.save"), b -> save()).bounds(left, y, w3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("customers.ui.toggle_open"), b -> toggle()).bounds(left + w3 + 6, y, w3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("customers.ui.exit"), b -> onClose()).bounds(left + 2 * (w3 + 6), y, w3, 20).build());
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v == 0 ? lo : v));
    }

    private void save() {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos);
        t.putString("name", nameBox.getValue());
        t.putString("filter", filterBtn.getValue());
        t.putInt("patience", patience.get());
        t.putInt("max", max.get());
        t.putInt("rate", rateBtn.getValue());
        t.putInt("radius", radius.get());
        PacketDistributor.sendToServer(new ActionPayload("save_restaurant", t));
        onClose();
    }

    private void toggle() {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos);
        PacketDistributor.sendToServer(new ActionPayload("toggle_open", t));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, Component.literal(d.getString("name")).withStyle(s -> s.withBold(true)), width / 2, 10, 0xFFFFFF);
        g.drawCenteredString(font, title, width / 2, 22, 0xAAAAAA);
        int yStats = Math.max(34, height / 2 - 70) + 24 * 4 + 30 + 26;
        g.drawCenteredString(font, Component.translatable("customers.ui.stats", d.getInt("rep"), d.getInt("services"), d.getInt("fails")),
                width / 2, yStats, 0xFFD27F);
        g.drawCenteredString(font, Component.translatable("customers.ui.state",
                Component.translatable(d.getBoolean("open") ? "customers.ui.state_open" : "customers.ui.state_closed")), width / 2, yStats + 12, 0xFFFFFF);
    }
}
