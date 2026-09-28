package com.vcoins;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Vanilla text components persist this fallback even without the mod's language pack. */
public final class VeloriaTextFallbacks {
    private static final Map<String, String> TEXT = load();
    private VeloriaTextFallbacks() {}

    public static String fallback(String key, String existing) {
        if (existing != null || !(key.startsWith("vcoins.") || key.startsWith("fortuna."))) return existing;
        return TEXT.get(key);
    }

    private static Map<String, String> load() {
        Map<String, String> result = new HashMap<>();
        try (var input = VeloriaTextFallbacks.class.getResourceAsStream("/assets/vcoins/lang/en_us.json")) {
            if (input == null) throw new IllegalStateException("Missing Veloria fallback translations");
            try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                        .forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot load Veloria fallback translations", error);
        }
        return Map.copyOf(result);
    }
}
