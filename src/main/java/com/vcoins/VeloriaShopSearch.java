package com.vcoins;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Server-side search aliases work even when the server has no client language selected. */
public final class VeloriaShopSearch {
    private static final Map<String, String> VI = loadVietnamese();
    private VeloriaShopSearch() {}

    public static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('_', ' ').strip();
    }

    public static boolean matches(ItemStack stack, String query) {
        String needle = normalize(query);
        if (needle.isEmpty()) return true;
        if (contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), needle)
                || contains(stack.getHoverName().getString(), needle)) return true;
        for (var entry : VCoinsPricing.getEnchantmentValues(stack).entrySet()) {
            String id = entry.getKey();
            String key = "enchantment." + id.replace(':', '.');
            if (named(id, key, needle) || contains(entry.getValue().enchantment().value().description().getString(), needle))
                return true;
        }
        var potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            if (potion.potion().isPresent() && contains(potion.potion().get().getRegisteredName(), needle)) return true;
            for (var instance : potion.getAllEffects()) {
                var effect = instance.getEffect();
                if (named(effect.getRegisteredName(), effect.value().getDescriptionId(), needle)) return true;
            }
        }
        return false;
    }

    private static boolean named(String id, String key, String needle) {
        return contains(id, needle) || contains(Component.translatable(key).getString(), needle)
                || contains(VI.getOrDefault(key, ""), needle);
    }

    private static boolean contains(String text, String needle) {
        return normalize(text).contains(needle);
    }

    private static Map<String, String> loadVietnamese() {
        try (var input = VeloriaShopSearch.class.getResourceAsStream("/assets/vcoins/search_vi.json")) {
            if (input == null) throw new IllegalStateException("Missing Vietnamese search aliases");
            try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                Map<String, String> result = new HashMap<>();
                JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                        .forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
                return Map.copyOf(result);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot load search aliases", error);
        }
    }
}
