package com.customers.client;

import com.customers.data.MenuManager;
import com.customers.net.ActionPayload;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class RestaurantScreen extends Screen {
    private final CompoundTag d;
    private final long pos;

    // values survive opening the menu editor (init() runs again when we come back)
    private String nameVal;
    private int patienceVal, maxVal, rateVal, radiusVal;
    private Set<String> disabled = new HashSet<>();

    private EditBox nameBox;
    private CycleButton<Integer> rateBtn;
    private IntSlider patience, max, radius;

    public RestaurantScreen(CompoundTag data) {
        super(Component.translatable("customers.ui.title"));
        this.d = data;
        this.pos = data.getLong("pos");
        nameVal = data.getString("name");
        patienceVal = clamp(data.getInt("patience"), 1, 20);
        maxVal = clamp(data.getInt("max"), 1, 12);
        rateVal = Math.max(0, Math.min(2, data.getInt("rate")));
        radiusVal = clamp(data.getInt("radius"), 8, 64);
        for (Tag t : data.getList("disabled", Tag.TAG_STRING)) disabled.add(t.getAsString());
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v == 0 ? lo : v));
    }

    private int top() {
        return Math.max(34, height / 2 - 78);
    }

    private long enabledCount() {
        MenuManager.ensure();
        return MenuManager.ALL.stream().filter(e -> !disabled.contains(e.id().toString())).count();
    }

    @Override
    protected void init() {
        int colW = 150, gap = 10, full = colW * 2 + gap;
        int left = width / 2 - colW - gap / 2, right = width / 2 + gap / 2;
        int y = top();

        nameBox = new EditBox(font, left, y, full, 20, Component.translatable("customers.ui.name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(nameVal);
        nameBox.setHint(Component.translatable("customers.ui.name"));
        addRenderableWidget(nameBox);

        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("customers.menu.edit", enabledCount(), MenuManager.ALL.size()), b -> {
            snap();
            minecraft.setScreen(FoodGridScreen.edit(this, disabled, set -> disabled = set));
        }).bounds(left, y, full, 20).build());

        y += 24;
        rateBtn = addRenderableWidget(CycleButton.<Integer>builder(i -> Component.translatable(
                        i == 0 ? "customers.ui.rate.high" : i == 2 ? "customers.ui.rate.low" : "customers.ui.rate.medium"))
                .withValues(List.of(0, 1, 2)).withInitialValue(rateVal)
                .create(left, y, colW, 20, Component.translatable("customers.ui.rate"), (b, v) -> { }));
        max = addRenderableWidget(new IntSlider(right, y, colW, 20, 1, 12, maxVal,
                v -> Component.translatable("customers.ui.limit").append(" " + v)));

        y += 24;
        patience = addRenderableWidget(new IntSlider(left, y, colW, 20, 1, 20, patienceVal,
                v -> Component.translatable("customers.ui.patience").append(" ").append(Component.translatable("customers.ui.minutes", v))));
        radius = addRenderableWidget(new IntSlider(right, y, colW, 20, 8, 64, radiusVal,
                v -> Component.translatable("customers.ui.radius").append(" ").append(Component.translatable("customers.ui.blocks", v))));

        y += 30;
        int w3 = (full - 12) / 3;
        addRenderableWidget(Button.builder(Component.translatable("customers.ui.save"), b -> save()).bounds(left, y, w3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("customers.ui.toggle_open"), b -> toggle()).bounds(left + w3 + 6, y, w3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("customers.ui.exit"), b -> onClose()).bounds(left + 2 * (w3 + 6), y, w3, 20).build());
    }

    private void snap() {
        nameVal = nameBox.getValue();
        patienceVal = patience.get();
        maxVal = max.get();
        rateVal = rateBtn.getValue();
        radiusVal = radius.get();
    }

    private void save() {
        snap();
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos);
        t.putString("name", nameVal);
        t.putInt("patience", patienceVal);
        t.putInt("max", maxVal);
        t.putInt("rate", rateVal);
        t.putInt("radius", radiusVal);
        ListTag dis = new ListTag();
        for (String id : disabled) dis.add(StringTag.valueOf(id));
        t.put("disabled", dis);
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
        int yStats = top() + 24 * 4 + 30 + 26;
        g.drawCenteredString(font, Component.translatable("customers.ui.stats", d.getInt("rep"), d.getInt("services"), d.getInt("fails")),
                width / 2, yStats, 0xFFD27F);
        g.drawCenteredString(font, Component.translatable("customers.ui.state",
                Component.translatable(d.getBoolean("open") ? "customers.ui.state_open" : "customers.ui.state_closed")), width / 2, yStats + 12, 0xFFFFFF);
    }
}
