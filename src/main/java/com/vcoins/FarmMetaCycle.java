package com.vcoins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Stateless wall-clock simulation: identical on clients, servers and after a restart. */
public final class FarmMetaCycle {
    public static final long CYCLE_SECONDS = 10L * 86_400L;
    public static final double MIN_MULTIPLIER = 0.05;
    public static final double MAX_MULTIPLIER = 4.0;

    // Tokens match complete underscore-delimited words/sequences, never namespace or enchantments.
    private record Family(String name, String[] primary, String[] related) {}
    private static Family family(String name, String primary, String related) {
        return new Family(name, primary.split(","), related.split(","));
    }

    private static final List<Family> FAMILIES = List.of(
            family("iron", "iron", "anvil,chain,hopper,bucket,shears"),
            family("gold", "gold,golden", "powered_rail,clock"),
            family("copper", "copper", "lightning_rod,spyglass"),
            family("coal", "coal,charcoal", "torch,campfire"),
            family("redstone", "redstone", "repeater,comparator,piston,observer,dispenser,dropper"),
            family("lapis", "lapis", "enchanting_table,enchanted_book"),
            family("diamond", "diamond", "enchanting_table,jukebox"),
            family("netherite", "netherite,ancient_debris", "smithing_table"),
            family("emerald", "emerald", "bell"),
            family("timber", "log,wood,stem,hyphae,planks,oak,spruce,birch,jungle,acacia,mangrove,cherry,pale_oak,bamboo,crimson,warped", "stick,chest,barrel,crafting_table"),
            family("stone", "stone,cobblestone,deepslate", "furnace,stonecutter"),
            family("sand", "sand,sandstone", "glass,glass_pane"),
            family("clay", "clay,clay_ball,brick,bricks,terracotta", "flower_pot"),
            family("wool", "wool", "carpet,bed,banner"),
            family("crops", "wheat,carrot,potato,beetroot", "bread,hay_block,cookie,cake"),
            family("meat", "beef,porkchop,chicken,mutton,rabbit", "leather,feather,rabbit_hide"),
            family("sugar", "sugar,sugar_cane", "paper,book,cake"),
            family("slime", "slime,slime_ball,slime_block", "sticky_piston,magma_cream"),
            family("explosives", "gunpowder,tnt", "firework_rocket,firework_star"),
            family("brewing", "nether_wart,blaze_rod,blaze_powder", "potion,splash_potion,lingering_potion,tipped_arrow,brewing_stand"),
            family("ocean", "prismarine,sea_lantern", "sponge,heart_of_the_sea,conduit"),
            family("quartz", "quartz", "daylight_detector,comparator"),
            family("end", "ender_pearl,chorus,shulker", "end_stone,purpur,elytra,ender_eye"),
            family("wither", "wither_skeleton_skull,nether_star", "beacon,soul_sand,soul_soil")
    );

    public record Meta(String family, boolean surge) {}
    private record Schedule(long cycle, List<Integer> families) {}
    private static volatile Schedule cachedSchedule;

    private FarmMetaCycle() {}

    private static Schedule schedule(long epochSecond) {
        long cycle = Math.floorDiv(epochSecond, CYCLE_SECONDS);
        Schedule cached = cachedSchedule;
        if (cached != null && cached.cycle() == cycle) return cached;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < FAMILIES.size(); i++) indices.add(i);
        Collections.shuffle(indices, new Random(cycle * 0x9E3779B97F4A7C15L ^ 0x56454C4F524941L));
        Schedule result = new Schedule(cycle, List.copyOf(indices.subList(0, 3)));
        cachedSchedule = result;
        return result;
    }

    /** Two demand metas and one supply crash per ten real days. */
    public static List<Meta> activeMetas(long epochSecond) {
        Schedule schedule = schedule(epochSecond);
        List<Meta> result = new ArrayList<>();
        for (int i = 0; i < schedule.families().size(); i++) {
            result.add(new Meta(FAMILIES.get(schedule.families().get(i)).name(), i != 2));
        }
        return List.copyOf(result);
    }

    private static boolean matches(String path, String[] tokens) {
        String padded = "_" + path + "_";
        for (String token : tokens) if (padded.contains("_" + token + "_")) return true;
        return false;
    }

    /** Strongest matching relationship wins, so overlapping families never exceed the limits. */
    static double influence(String key, long epochSecond) {
        String id = key.split("\\|", 2)[0];
        String path = id.substring(id.indexOf(':') + 1);
        Schedule schedule = schedule(epochSecond);
        double strongest = 0;
        for (int i = 0; i < schedule.families().size(); i++) {
            Family family = FAMILIES.get(schedule.families().get(i));
            double weight = matches(path, family.primary()) ? 1.0
                    : matches(path, family.related()) ? 0.60 : 0.0;
            if (weight > Math.abs(strongest)) strongest = i == 2 ? -weight : weight;
        }
        return strongest;
    }

    public static double apply(String key, long epochSecond, double baseline) {
        long hash = Integer.toUnsignedLong(key.hashCode());
        // Short waves keep every quoted item moving, even on the old multi-day plateaus.
        double phase = Math.floorMod(epochSecond + hash, 113L + hash % 307L)
                / (double) (113L + hash % 307L) * Math.PI * 2;
        double pulse = (1 + Math.sin(phase)) / 2;
        double bounded = Math.clamp(baseline, MIN_MULTIPLIER, MAX_MULTIPLIER);
        double ordinary = Math.clamp(bounded * (0.96 + 0.02 * pulse) + 0.02 * pulse,
                MIN_MULTIPLIER, MAX_MULTIPLIER);
        double influence = influence(key, epochSecond);
        double progress = Math.floorMod(epochSecond, CYCLE_SECONDS) / (double) CYCLE_SECONDS;
        // Zero slope at both boundaries avoids price jumps when the selected families change.
        double envelope = Math.pow(Math.sin(Math.PI * progress), 2);
        double strength = Math.abs(influence) * envelope;
        // At the apex, short waves approach the 4x/0.05x limits without a flat plateau.
        double target = influence < 0 ? MIN_MULTIPLIER + 0.01 * pulse
                : MAX_MULTIPLIER - 0.08 * pulse;
        return Math.clamp(ordinary + (target - ordinary) * strength, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }
}
