package com.vcoins;

/** Dependency-free simulation checks, run by Gradle check. */
public final class MarketCycleTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int crashes = 0;
        int samples = 0;
        for (boolean rare : new boolean[]{false, true}) {
            double floor = rare ? 0.80 : 0.25;
            double ceiling = rare ? 3.0 : 4.0;
            for (int item = 0; item < 100; item++) {
                String id = "test:item_" + item;
                long hash = Integer.toUnsignedLong(id.hashCode());
                long duration = (6 + hash % 5) * 86400L;
                long offset = hash % duration;
                for (int wave = 0; wave < 100; wave++) {
                    long start = wave * duration - offset;
                    double previous = MarketCycle.multiplier(id, rare, start);
                    for (int step = 1; step <= 100; step++) {
                        long time = start + duration * step / 100;
                        double value = MarketCycle.multiplier(id, rare, time);
                        require(value >= floor && value <= ceiling, "Price bounds");
                        if (step <= 35) require(value >= previous - 1e-10, "Rising phase");
                        if (step > 35 && step <= 50) require(Math.abs(value - previous) < 1e-10, "Peak plateau");
                        if (step > 50 && step <= 90) require(value <= previous + 1e-10, "Falling phase");
                        if (step > 90) require(Math.abs(value - previous) < 1e-10, "Trough plateau");
                        for (double pressure : new double[]{-100, -0.65, 0, 1.35, 100}) {
                            double adjusted = MarketCycle.withPressure(value, pressure, rare);
                            require(adjusted >= floor && adjusted <= ceiling, "Pressure bounds");
                            if (value > floor && pressure < 0) require(adjusted > floor, "No forced floor");
                        }
                        previous = value;
                    }
                    double trough = MarketCycle.multiplier(id, rare, start + duration * 95 / 100);
                    if (Math.abs(trough - floor) < 1e-10) crashes++;
                    samples++;
                    double before = MarketCycle.multiplier(id, rare, start - 1);
                    double after = MarketCycle.multiplier(id, rare, start);
                    require(Math.abs(before - after) < 1e-8, "Continuous wave boundary");
                    long midnight = Math.floorDiv(start + duration / 3, 86400L) * 86400L;
                    require(Math.abs(MarketCycle.multiplier(id, rare, midnight - 1)
                            - MarketCycle.multiplier(id, rare, midnight)) < 0.0001, "Continuous midnight");
                }
            }
        }
        double rate = crashes / (double) samples;
        require(rate > 0.002 && rate < 0.008, "Rare crash frequency: " + rate);
        System.out.println("Passed 20,000 waves; crash troughs: " + crashes + " (" + rate * 100 + "%).");
    }
}
