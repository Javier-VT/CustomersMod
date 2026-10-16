package com.customers.client;

import java.util.function.IntFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public class IntSlider extends AbstractSliderButton {
    private final int min, max;
    private final IntFunction<Component> label;

    public IntSlider(int x, int y, int w, int h, int min, int max, int initial, IntFunction<Component> label) {
        super(x, y, w, h, label.apply(initial), (initial - min) / (double) (max - min));
        this.min = min;
        this.max = max;
        this.label = label;
    }

    public int get() {
        return min + (int) Math.round(value * (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(label.apply(get()));
    }

    @Override
    protected void applyValue() {
        value = (get() - min) / (double) (max - min); // snap to whole numbers
    }
}
