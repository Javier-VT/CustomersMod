package com.customers.client;

import com.customers.net.ActionPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public class BellScreen extends Screen {
    private final CompoundTag d;
    private final long pos;

    public BellScreen(CompoundTag data) {
        super(Component.translatable("customers.bell.title", data.getString("name")));
        this.d = data;
        this.pos = data.getLong("pos");
    }

    private static Component itemName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? Component.literal(id) : new ItemStack(item).getHoverName();
    }

    private void send(String action, CompoundTag t) {
        t.putLong("pos", pos);
        PacketDistributor.sendToServer(new ActionPayload(action, t));
        onClose();
    }

    @Override
    protected void init() {
        int w = 220, x = width / 2 - w / 2, y = Math.max(50, height / 2 - 50);
        boolean hasOwn = !d.getString("own").isEmpty();
        int orders = d.getList("orders", Tag.TAG_COMPOUND).size();

        addRenderableWidget(Button.builder(Component.translatable(hasOwn ? "customers.bell.change" : "customers.bell.order"),
                b -> minecraft.setScreen(orderScreen())).bounds(x, y, w, 20).build());
        y += 24;
        Button serve = Button.builder(Component.translatable("customers.bell.serve", orders),
                b -> minecraft.setScreen(serveScreen())).bounds(x, y, w, 20).build();
        serve.active = orders > 0;
        addRenderableWidget(serve);
        y += 24;
        Button cancel = Button.builder(Component.translatable("customers.bell.cancel"),
                b -> send("cancel_order", new CompoundTag())).bounds(x, y, w, 20).build();
        cancel.active = hasOwn;
        addRenderableWidget(cancel);
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("customers.ui.exit"), b -> onClose()).bounds(x, y, w, 20).build());
    }

    private Screen orderScreen() {
        Set<String> disabled = new HashSet<>();
        for (Tag t : d.getList("disabled", Tag.TAG_STRING)) disabled.add(t.getAsString());
        return FoodGridScreen.pick(this, disabled, id -> {
            CompoundTag c = new CompoundTag();
            c.putString("item", id);
            send("order", c);
        });
    }

    private Screen serveScreen() {
        List<ListPickScreen.Entry> entries = new ArrayList<>();
        for (Tag t : d.getList("orders", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) t;
            String buyer = o.getString("buyer");
            entries.add(new ListPickScreen.Entry(
                    Component.literal("§e" + o.getString("name") + " §f- ").append(itemName(o.getString("item"))),
                    () -> {
                        CompoundTag c = new CompoundTag();
                        c.putString("buyer", buyer);
                        send("serve", c);
                    }));
        }
        return new ListPickScreen(Component.translatable("customers.bell.serve_title"), this, entries);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, Math.max(14, height / 2 - 90), 0xFFFFFF);
        int y = Math.max(14, height / 2 - 90) + 14;
        g.drawCenteredString(font, Component.translatable("customers.bell.info", d.getInt("waiting"), d.getList("orders", Tag.TAG_COMPOUND).size()), width / 2, y, 0xAAAAAA);
        if (!d.getString("own").isEmpty()) {
            g.drawCenteredString(font, Component.translatable("customers.bell.your_order", itemName(d.getString("own"))), width / 2, y + 12, 0xFFD27F);
        }
        g.drawCenteredString(font, Component.translatable(d.getBoolean("open") ? "customers.ui.state_open" : "customers.ui.state_closed"), width / 2, y + 24, 0xFFFFFF);
    }
}
