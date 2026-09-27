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

    static void removeDebugLines(List<Component> lines, net.minecraft.world.item.ItemStack stack) {
        if (lines == null || lines.isEmpty()) return;
        lines.removeIf(line -> isDebugLine(line, stack));
    }

    static boolean isDebugLine(Component line, net.minecraft.world.item.ItemStack stack) {
        if (line == null) return false;
        if (line.getContents() instanceof TranslatableContents tc && "item.components".equals(tc.getKey())) {
            return true;
        }
        String str = line.getString().trim();
        if (str.matches("(?i)\\d+\\s+component\\(s\\)") || str.matches("(?i)\\d+\\s+thành phần")) {
            return true;
        }
        if (stack != null && !stack.isEmpty()) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && str.equals(id.toString())) {
                return true;
            }
        }
        if (str.matches("^[a-z0-9_.-]+:[a-z0-9_.-]+$")) {
            return true;
        }
        return false;
    }
}
