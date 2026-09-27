package com.vcoins;

import java.util.Random;

/** Deterministic real-time waves shared by the server and clients. */
public final class MarketCycle {
    private static final long DAY_SECONDS = 86400L;
    public static final double CRASH_PROBABILITY = 0.005; // 0.5% of wave troughs

    private MarketCycle() {}

    public static double multiplier(String itemId, boolean rare, long epochSecond) {
        long hash = Integer.toUnsignedLong(itemId.hashCode());
        long duration = (6 + hash % 5) * DAY_SECONDS; // 6–10 days per wave
        long shifted = epochSecond + hash % duration;
        long wave = Math.floorDiv(shifted, duration);
        double phase = Math.floorMod(shifted, duration) / (double) duration;
        double trough = trough(hash, wave, rare);
        double nextTrough = trough(hash, wave + 1, rare);
        Random random = random(hash, wave, 73L);
        double peak = 1.0 + (rare ? 2.0 : 3.0) * (0.05 + 0.95 * Math.pow(random.nextDouble(), 2));
        if (phase < 0.35) return interpolate(trough, peak, phase / 0.35);
        if (phase < 0.50) return peak;
        if (phase < 0.90) return interpolate(peak, nextTrough, (phase - 0.50) / 0.40);
        return nextTrough;
    }

    private static double trough(long hash, long wave, boolean rare) {
        Random random = random(hash, wave, 19L);
        double downside = rare ? 0.20 : 0.75;
        if (random.nextDouble() < CRASH_PROBABILITY) return rare ? 0.80 : 0.25;
        // Most declines are mild; ordinary waves never approach the hard floor.
        return 1.0 - downside * (0.04 + 0.56 * Math.pow(random.nextDouble(), 3));
    }

    private static Random random(long hash, long wave, long salt) {
        return new Random(hash * 1013904223L ^ wave * 3123456789L ^ salt);
    }

    private static double interpolate(double start, double end, double progress) {
        double smooth = progress * progress * (3.0 - 2.0 * progress);
        return start + (end - start) * smooth;
    }

    public static double withPressure(double baseline, double volume, boolean rare) {
        double minimum = rare ? 0.80 : 0.25;
        double maximum = rare ? 3.0 : 4.0;
        // Oversupply consumes at most 80% of remaining headroom, so ordinary
        // trading cannot pin every item to its floor. Buying never boosts sell payouts.
        double adjustment = volume < 0
                ? (baseline - minimum) * Math.max(-0.80, volume)
                : volume;
        return Math.max(minimum, Math.min(maximum, baseline + adjustment));
    }
}
