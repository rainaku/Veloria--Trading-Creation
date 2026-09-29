package com.vcoins;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VMarketSimulator — Ghost trader + Meta event engine.
 *
 * Background order simulation, complemented by {@link FarmMetaCycle} in every quote:
 *
 * ① Ghost traders (200 ms tick)
 *   Depth-scaled buy / sell orders every 200 ms, visiting up to 300 different
 *   catalogue entries per tick in rotation. Each item has its own 5–15-min
 *   sentiment phase. Overbought / oversold clamps limit order pressure only.
 *
 * ② Meta event engine (rolls once per minute)
 *   Autonomously spawns named market events — shortage, surplus, mania,
 *   crash — that target groups of related items and override ghost-trader
 *   direction for their duration.  Events are tiered:
 *
 *     COMMON   — spawn ~every 5 min, last 10–20 min, bias ±0.22–0.28
 *     UNCOMMON — spawn ~every 12 min, last 15–40 min, bias ±0.28–0.35
 *     EPIC     — spawn ~every 50 min, last 25–60 min, bias ±0.38–0.55
 *
 *   Multiple events stack — if "Iron Shortage" and "Armor Craze" are both
 *   active, iron-family items receive double upward pressure, mimicking a
 *   real market mania.  Opposing events (e.g., Shortage + Oversupply) cancel,
 *   keeping the market balanced over time.
 *
 * ③ Ten-day farm metas and continuous quotes
 *   FarmMetaCycle supplies deterministic short price waves and three commodity
 *   family events per ten real days. This layer is applied by VMarketEngine
 *   after ordinary pressure limits, allowing 0.05x–4x prices even for rare items.
 *   It survives restarts without scheduler replay or additional save data.
 *
 * Performance
 * ───────────
 * Single daemon thread at MIN_PRIORITY. ~1 500 recordBuy/Sell calls/second
 * via ConcurrentHashMap.compute — well under any meaningful load.
 */
final class VMarketSimulator {

    private static final Logger LOGGER = LoggerFactory.getLogger("vcoins-sim");

    // ── Ghost-trade tunables ──────────────────────────────────────────────────

    private static final int    PHANTOM_PLAYER_COUNT = 10_000;
    private static final long   TICK_INTERVAL_MS     = 200L;
    private static final int    ITEMS_PER_TICK        = 300;
    private static final int    DEPTH_DIVISOR         = 12;
    private static final int    MAX_UNITS_CAP         = 400;
    private static final double SENTIMENT_AMP         = 0.20;
    private static final double OVERBOUGHT_THRESHOLD  = 1.60;
    private static final double BUY_CAP_OVERBOUGHT    = 0.28;
    private static final double OVERSOLD_THRESHOLD    = 0.65;
    private static final double BUY_FLOOR_OVERSOLD    = 0.72;

    // ── Meta event tunables ───────────────────────────────────────────────────

    private static final int    META_MAX_ACTIVE    = 5;
    private static final double META_PROB_COMMON   = 0.20; // ≈1 new event/5 min
    private static final double META_PROB_UNCOMMON = 0.08; // ≈1 new event/12 min
    private static final double META_PROB_EPIC     = 0.02; // ≈1 new event/50 min

    // ── Meta event catalogue ──────────────────────────────────────────────────

    /**
     * A reusable market event archetype.
     *
     * @param name       display label (written to server log on spawn/expiry)
     * @param patterns   item key must contain at least one of these substrings
     * @param biasShift  added to buy probability: +N = scarcity / demand,
     *                   −N = abundance / oversupply
     * @param minDurMin  minimum event duration (minutes)
     * @param maxDurMin  maximum event duration (minutes)
     * @param tier       0 = common, 1 = uncommon, 2 = epic
     */
    private record MetaTemplate(
            String   name,
            String[] patterns,
            double   biasShift,
            int      minDurMin,
            int      maxDurMin,
            int      tier
    ) {}

    private static final MetaTemplate[] META_CATALOG = {

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // COMMON — frequent pulses on basic commodities
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        new MetaTemplate("Iron Shortage",
            new String[]{"iron_ingot","raw_iron","iron_block","iron_nugget"},       +0.28, 10, 20, 0),
        new MetaTemplate("Iron Oversupply",
            new String[]{"iron_ingot","raw_iron"},                                   -0.25,  8, 18, 0),
        new MetaTemplate("Coal Demand",
            new String[]{"coal","charcoal"},                                         +0.22,  8, 16, 0),
        new MetaTemplate("Coal Surplus",
            new String[]{"coal","charcoal"},                                         -0.25,  8, 15, 0),
        new MetaTemplate("Lumber Demand",
            new String[]{"_log","_stem","_wood","_hyphae"},                          +0.22, 10, 20, 0),
        new MetaTemplate("Plank Glut",
            new String[]{"_planks"},                                                 -0.20,  8, 15, 0),
        new MetaTemplate("Gold Rush",
            new String[]{"gold_ingot","raw_gold","gold_block","gold_nugget"},       +0.28,  8, 18, 0),
        new MetaTemplate("Gold Crash",
            new String[]{"gold_ingot","raw_gold"},                                   -0.22, 10, 18, 0),
        new MetaTemplate("Stone Bust",
            new String[]{"cobblestone","netherrack","gravel"},                       -0.20, 10, 15, 0),
        new MetaTemplate("Copper Hype",
            new String[]{"copper_ingot","raw_copper","copper_block"},                +0.22,  8, 16, 0),
        new MetaTemplate("Food Scarcity",
            new String[]{"_beef","_porkchop","_chicken","_mutton","bread"},         +0.28,  8, 16, 0),
        new MetaTemplate("Food Glut",
            new String[]{"_beef","_chicken","bread","potato"},                       -0.22,  8, 15, 0),
        new MetaTemplate("Sand Frenzy",
            new String[]{"sand","sandstone"},                                        +0.20,  8, 15, 0),
        new MetaTemplate("Building Boom",
            new String[]{"brick","terracotta","concrete"},                           +0.18, 10, 18, 0),
        new MetaTemplate("Gravel Glut",
            new String[]{"gravel","flint"},                                          -0.18,  8, 14, 0),

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // UNCOMMON — mid-frequency events on crafted or specialized items
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        new MetaTemplate("Redstone Revolution",
            new String[]{"redstone","repeater","comparator","piston","observer"},   +0.32, 18, 35, 1),
        new MetaTemplate("Redstone Recession",
            new String[]{"redstone","comparator","repeater"},                        -0.28, 15, 30, 1),
        new MetaTemplate("Potion Mania",
            new String[]{"potion","blaze_rod","blaze_powder","nether_wart"},        +0.32, 15, 30, 1),
        new MetaTemplate("Tool Shortage",
            new String[]{"_pickaxe","_sword","_axe","_shovel"},                      +0.30, 15, 30, 1),
        new MetaTemplate("Armor Craze",
            new String[]{"_helmet","_chestplate","_leggings","_boots"},              +0.25, 18, 35, 1),
        new MetaTemplate("Enchanting Wave",
            new String[]{"enchanted_book","lapis"},                                  +0.30, 20, 40, 1),
        new MetaTemplate("Lapis Squeeze",
            new String[]{"lapis_lazuli","lapis_block"},                              +0.35, 15, 28, 1),
        new MetaTemplate("Gunpowder Panic",
            new String[]{"tnt","firework_rocket","firework_star"},                   +0.30, 12, 25, 1),
        new MetaTemplate("Glass Demand",
            new String[]{"glass"},                                                   +0.25, 15, 28, 1),
        new MetaTemplate("Slime Bubble",
            new String[]{"slime_ball","sticky_piston","slime_block"},                +0.28, 12, 22, 1),
        new MetaTemplate("Quartz Craze",
            new String[]{"quartz"},                                                  +0.30, 15, 28, 1),
        new MetaTemplate("Prismarine Trend",
            new String[]{"prismarine","sea_lantern"},                                +0.28, 15, 30, 1),
        new MetaTemplate("End Stone Surge",
            new String[]{"end_stone","purpur"},                                      +0.25, 12, 25, 1),
        new MetaTemplate("Wool Weave",
            new String[]{"_wool","_carpet"},                                         +0.22, 15, 28, 1),
        new MetaTemplate("Dye Dump",
            new String[]{"_dye"},                                                    -0.25, 12, 22, 1),

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // EPIC — rare, high-impact, long-duration events on endgame items
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        new MetaTemplate("Diamond Standard",
            new String[]{"diamond"},                                                 +0.45, 30, 60, 2),
        new MetaTemplate("Diamond Correction",
            new String[]{"diamond"},                                                 -0.35, 20, 45, 2),
        new MetaTemplate("Netherite Mania",
            new String[]{"netherite","ancient_debris"},                              +0.50, 25, 55, 2),
        new MetaTemplate("Netherite Crash",
            new String[]{"netherite"},                                               -0.40, 20, 40, 2),
        new MetaTemplate("Elytra Frenzy",
            new String[]{"elytra"},                                                  +0.55, 30, 60, 2),
        new MetaTemplate("Beacon Bubble",
            new String[]{"beacon","nether_star"},                                    +0.45, 28, 55, 2),
        new MetaTemplate("Emerald Era",
            new String[]{"emerald"},                                                 +0.40, 25, 50, 2),
        new MetaTemplate("Shulker Squeeze",
            new String[]{"shulker_box"},                                             +0.38, 25, 50, 2),
        new MetaTemplate("End Market Crash",
            new String[]{"elytra","shulker_box"},                                    -0.38, 20, 40, 2),
        new MetaTemplate("Wither Rally",
            new String[]{"wither_skeleton_skull","nether_star"},                     +0.42, 25, 50, 2),
        new MetaTemplate("Heavy Core Panic",
            new String[]{"heavy_core","mace"},                                       +0.55, 30, 55, 2),
        new MetaTemplate("Trident Season",
            new String[]{"trident"},                                                 +0.48, 25, 50, 2),
    };

    // ── Active event tracking (simulator thread only — no concurrent access) ─

    private static final List<ActiveMeta> activeMetas = new ArrayList<>();
    private static long lastMetaRollEpoch = 0L;

    private static final class ActiveMeta {
        final MetaTemplate template;
        long endEpochSecond;

        ActiveMeta(MetaTemplate template, long end) {
            this.template       = template;
            this.endEpochSecond = end;
        }

        /** True if this event exerts pressure on the given item key. */
        boolean affects(String itemKey) {
            for (String p : template.patterns()) {
                if (itemKey.contains(p)) return true;
            }
            return false;
        }
    }

    // ── Core state ────────────────────────────────────────────────────────────

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile ScheduledExecutorService EXECUTOR;
    private static volatile String[] itemKeys = new String[0];
    private static volatile int      keyCount  = 0;
    private static int nextItemIndex;
    private static long lastFarmCycle = Long.MIN_VALUE;

    private static final ThreadLocal<Random> THREAD_RNG =
            ThreadLocal.withInitial(() -> new Random(System.nanoTime()));

    private VMarketSimulator() {}

    // ── Lifecycle (VCoinsMod only) ────────────────────────────────────────────

    static synchronized void start(MinecraftServer server) {
        if (RUNNING.get()) return;

        VCoinsPricing.ensureInitialized();
        List<String> keys = new ArrayList<>();
        VCoinsPricing.getAllPrices().forEach((id, price) -> {
            if (price != null && price > 0L) keys.add(id);
        });
        itemKeys = keys.toArray(new String[0]);
        keyCount  = itemKeys.length;
        nextItemIndex = 0;
        lastMetaRollEpoch = 0;
        lastFarmCycle = Long.MIN_VALUE;
        activeMetas.clear();

        if (keyCount == 0) {
            LOGGER.warn("[VMarketSim] price catalogue empty — not started");
            return;
        }

        EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vcoins-market-sim");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

        RUNNING.set(true);
        ScheduledExecutorService owner = EXECUTOR;
        EXECUTOR.scheduleWithFixedDelay(
                () -> tick(owner),
                TICK_INTERVAL_MS,
                TICK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        LOGGER.info("[VMarketSim] running — {} items | {} phantom players | {}ms tick | {} meta archetypes",
                keyCount, PHANTOM_PLAYER_COUNT, TICK_INTERVAL_MS, META_CATALOG.length);
    }

    static synchronized void stop() {
        if (!RUNNING.getAndSet(false)) return;
        if (EXECUTOR != null) {
            EXECUTOR.shutdownNow();
            EXECUTOR = null;
        }
        activeMetas.clear();
        itemKeys = new String[0];
        keyCount = 0;
        LOGGER.info("[VMarketSim] stopped");
    }

    // ── Core tick ─────────────────────────────────────────────────────────────

    private static synchronized void tick(ScheduledExecutorService owner) {
        try {
            if (owner != EXECUTOR || !RUNNING.get() || keyCount == 0 || Thread.currentThread().isInterrupted()) return;
            Random rng = THREAD_RNG.get();
            long   now = Instant.now().getEpochSecond();
            long farmCycle = Math.floorDiv(now, FarmMetaCycle.CYCLE_SECONDS);
            if (lastFarmCycle != farmCycle) {
                lastFarmCycle = farmCycle;
                LOGGER.info("[VMarketSim] 10-day farm metas: {}", FarmMetaCycle.activeMetas(now));
            }

            // Meta event management runs once per minute
            if (now - lastMetaRollEpoch >= 60L) {
                lastMetaRollEpoch = now;
                tickMetas(rng, now);
            }

            int n = Math.min(ITEMS_PER_TICK, keyCount);
            for (int i = 0; i < n; i++) {
                ghostTrade(itemKeys[nextItemIndex], rng, now);
                nextItemIndex = (nextItemIndex + 1) % keyCount;
            }
        } catch (Exception e) {
            LOGGER.debug("[VMarketSim] tick suppressed: {}", e.getMessage());
        }
    }

    // ── Ghost trade ───────────────────────────────────────────────────────────

    private static void ghostTrade(String key, Random rng, long epochSecond) {
        long base = VCoinsPricing.getBasePrice(key);
        if (base <= 0L) return;

        // Depth-proportional volume: cheap items need more units to move the needle.
        int depth    = VMarketEngine.calculateMarketDepth(base);
        int maxUnits = Math.max(2, Math.min(MAX_UNITS_CAP, depth / DEPTH_DIVISOR));
        int units    = 1 + (int) (Math.pow(rng.nextDouble(), 1.2) * maxUnits);

        // Layer 1: Personal 5–15-min sine-wave sentiment
        double buyBias = itemSentiment(key, epochSecond);

        // Layer 2: Active meta events — accumulate all matching shifts
        for (ActiveMeta meta : activeMetas) {
            if (meta.affects(key)) {
                buyBias += meta.template.biasShift();
            }
        }

        // Layer 3: Overbought / oversold auto-reversal clamp
        // Reverse excess ghost pressure without fighting the deliberate ten-day farm meta.
        double mult = 1.0 + VMarketEngine.getVolumeModifier(key, base, epochSecond);
        if (mult > OVERBOUGHT_THRESHOLD) {
            buyBias = Math.min(buyBias, BUY_CAP_OVERBOUGHT);
        } else if (mult < OVERSOLD_THRESHOLD) {
            buyBias = Math.max(buyBias, BUY_FLOOR_OVERSOLD);
        }

        // Final probability clamp
        buyBias = Math.max(0.05, Math.min(0.95, buyBias));

        if (rng.nextDouble() < buyBias) {
            VMarketEngine.recordBuy(key, units);
        } else {
            VMarketEngine.recordSell(key, units);
        }
    }

    // ── Sentiment wave (base layer) ───────────────────────────────────────────

    /**
     * Deterministic per-item buy probability from a personal sine wave.
     * Period: 5–15 min (derived from key hash).
     * Range: [0.30, 0.70].
     */
    private static double itemSentiment(String key, long epochSecond) {
        long hash     = Integer.toUnsignedLong(key.hashCode());
        long cycleSec = (5L + hash % 11L) * 60L;
        double phase  = Math.floorMod(epochSecond + hash, cycleSec) / (double) cycleSec;
        double wave   = Math.sin(phase * 2.0 * Math.PI);
        return 0.50 + SENTIMENT_AMP * wave;
    }

    // ── Meta event engine ─────────────────────────────────────────────────────

    /** Called once per minute; expires old events and probabilistically spawns new ones. */
    private static void tickMetas(Random rng, long now) {
        // 1. Expire finished events
        activeMetas.removeIf(m -> {
            if (m.endEpochSecond <= now) {
                LOGGER.info("[VMarketSim] [META END]   {} ({})",
                        m.template.name(),
                        m.template.biasShift() > 0 ? "demand wave faded" : "supply glut cleared");
                return true;
            }
            return false;
        });

        // 2. Try to spawn new events if below cap
        if (activeMetas.size() < META_MAX_ACTIVE) {
            maybeSpawn(rng, now, 0, META_PROB_COMMON);
            maybeSpawn(rng, now, 1, META_PROB_UNCOMMON);
            maybeSpawn(rng, now, 2, META_PROB_EPIC);
        }
    }

    /**
     * Rolls whether to spawn a new meta event of the given tier.
     * Prevents duplicate events (same template name already active).
     */
    private static void maybeSpawn(Random rng, long now, int tier, double prob) {
        if (activeMetas.size() >= META_MAX_ACTIVE) return;
        if (rng.nextDouble() >= prob) return;

        // Collect eligible templates (same tier, not already active)
        List<MetaTemplate> candidates = new ArrayList<>();
        outer:
        for (MetaTemplate t : META_CATALOG) {
            if (t.tier() != tier) continue;
            for (ActiveMeta active : activeMetas) {
                if (active.template.name().equals(t.name())) continue outer;
            }
            candidates.add(t);
        }
        if (candidates.isEmpty()) return;

        MetaTemplate chosen = candidates.get(rng.nextInt(candidates.size()));
        int durRange = Math.max(1, chosen.maxDurMin() - chosen.minDurMin() + 1);
        int durationSec = (chosen.minDurMin() + rng.nextInt(durRange)) * 60;

        activeMetas.add(new ActiveMeta(chosen, now + durationSec));

        String direction = chosen.biasShift() > 0 ? "SURGE ↑" : "CRASH ↓";
        String tierLabel = switch (tier) { case 0 -> "COMMON"; case 1 -> "UNCOMMON"; default -> "EPIC"; };
        LOGGER.info("[VMarketSim] [META {}] [{}] {} — {} min | bias {}{}",
                direction, tierLabel, chosen.name(),
                durationSec / 60,
                chosen.biasShift() > 0 ? "+" : "",
                String.format("%.2f", chosen.biasShift()));
    }
}
