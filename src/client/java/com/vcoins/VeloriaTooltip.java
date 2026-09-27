package com.vcoins;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

final class VeloriaTooltip {
    private VeloriaTooltip() {}
    static List<Component> withoutPrices(List<Component> source) {
        List<Component> result = new ArrayList<>(source);
        result.removeIf(line -> line.getContents() instanceof TranslatableContents text
                && (text.getKey().equals("vcoins.tooltip.buy_price")
                || text.getKey().equals("vcoins.tooltip.sell_price")
                || text.getKey().equals("vcoins.black_market.buy_price")
                || text.getKey().equals("vcoins.black_market.discount")));
        while (!result.isEmpty() && result.getLast().getString().isEmpty()) result.removeLast();
        return result;
    }
}
