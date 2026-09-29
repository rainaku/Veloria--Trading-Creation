package com.vcoins;

import java.util.HashSet;

/** Runs without Minecraft: deterministic schedules, correlations, bounds and continuous transitions. */
public final class FarmMetaCycleTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String[] items = {"iron_ingot", "gold_ingot", "copper_ingot", "coal", "redstone",
                "lapis_lazuli", "diamond", "netherite_ingot", "emerald", "oak_log", "stone",
                "sand", "clay_ball", "white_wool", "wheat", "beef", "sugar_cane", "slime_ball",
                "gunpowder", "blaze_rod", "prismarine", "quartz", "ender_pearl", "nether_star"};
        boolean ironSurge = false;
        boolean ironCrash = false;
        boolean rareCrash = false;
        for (long cycle = 0; cycle < 100; cycle++) {
            long start = cycle * FarmMetaCycle.CYCLE_SECONDS;
            long peak = start + FarmMetaCycle.CYCLE_SECONDS / 2;
            var metas = FarmMetaCycle.activeMetas(peak);
            require(metas.size() == 3, "Exactly three farm metas");
            require(new HashSet<>(metas.stream().map(FarmMetaCycle.Meta::family).toList()).size() == 3, "Unique families");
            require(metas.stream().filter(FarmMetaCycle.Meta::surge).count() == 2, "Two surges and one crash");
            require(metas.equals(FarmMetaCycle.activeMetas(start + 1)), "Schedule stable for ten days");
            FarmMetaCycle.activeMetas(start - 1); // Client/server/cache access in a different order.
            require(metas.equals(FarmMetaCycle.activeMetas(peak)), "Schedule reproducible after cache replacement");
            for (String item : items) {
                String key = "minecraft:" + item;
                double direction = FarmMetaCycle.influence(key, peak);
                double value = FarmMetaCycle.apply(key, peak, 1);
                if (direction == 1) require(value >= 3.92, "Surge approaches +300%: " + key);
                if (direction == -1) require(value <= 0.061, "Crash approaches -95%: " + key);
                if (item.equals("diamond") && direction == -1) rareCrash = true;
                require(Math.abs(FarmMetaCycle.apply(key, start - 1, 1)
                        - FarmMetaCycle.apply(key, start, 1)) < 0.002, "No ten-day discontinuity");
                for (int sample = 0; sample < 40; sample++) {
                    long time = start + sample * FarmMetaCycle.CYCLE_SECONDS / 40;
                    for (double baseline : new double[]{0.25, 0.8, 1, 3, 4}) {
                        double quote = FarmMetaCycle.apply(key, time, baseline);
                        require(quote >= 0.05 && quote <= 4 && Double.isFinite(quote), "Hard price bounds");
                    }
                }
            }
            double iron = FarmMetaCycle.influence("minecraft:iron_ingot", peak);
            if (iron != 0) {
                ironSurge |= iron > 0;
                ironCrash |= iron < 0;
                require(FarmMetaCycle.influence("minecraft:iron_block", peak) == iron, "Same family moves together");
                require(FarmMetaCycle.influence("minecraft:iron_pickaxe|minecraft:mending", peak) == iron, "Equipment inherits material meta");
                require(FarmMetaCycle.influence("minecraft:hopper", peak) == iron * 0.6, "Related recipe moves more gently");
                require(FarmMetaCycle.influence("iron:unrelated", peak) == 0, "Namespace cannot match a family");
                require(FarmMetaCycle.influence("minecraft:unrelated|iron", peak) == 0, "Enchantment text cannot match a family");
            }
        }
        require(ironSurge && ironCrash && rareCrash, "Both directions occur, including rare-item crashes");
        for (double baseline : new double[]{0.25, 1, 3, 4}) {
            HashSet<Double> quotes = new HashSet<>();
            for (long second = 0; second < 600; second++) quotes.add(FarmMetaCycle.apply("mod:unrelated", second, baseline));
            require(quotes.size() > 100, "Items outside metas fluctuate on every old plateau");
        }
        System.out.println("Farm meta checks passed: 100 ten-day cycles, family correlations, bounds and continuous quotes.");
    }
}
