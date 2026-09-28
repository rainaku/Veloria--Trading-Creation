package com.vcoins;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringUtil;

/** Converts legacy name formatting into ordinary, persistent vanilla text components. */
public final class VeloriaAnvilNames {
    private VeloriaAnvilNames() {}

    public static String filter(String input) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\u00a7' || StringUtil.isAllowedChatCharacter(c)) result.append(c);
        }
        return result.toString();
    }

    public static MutableComponent parse(String input) {
        if (input.indexOf('\u00a7') < 0) return Component.literal(input);
        MutableComponent result = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            ChatFormatting format = c == '\u00a7' && i + 1 < input.length()
                    ? ChatFormatting.getByCode(Character.toLowerCase(input.charAt(i + 1))) : null;
            if (format == null) {
                part.append(c);
                continue;
            }
            if (!part.isEmpty()) {
                result.append(Component.literal(part.toString()).setStyle(style));
                part.setLength(0);
            }
            style = style.applyLegacyFormat(format);
            i++;
        }
        if (!part.isEmpty()) result.append(Component.literal(part.toString()).setStyle(style));
        return result;
    }
}
