package com.vcoins;

import com.google.gson.Gson;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.core.component.DataComponents;

/** Boundary and persistence regressions for the paid minigame. */
public final class FortunaTest {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Random random = new Random(17);
        for (int tier = 0; tier < 5; tier++) {
            int successes = 0;
            for (int roll = 0; roll < 100; roll++) {
                FortunaRun r = new FortunaRun(); r.start(0, random); r.tier = tier;
                boolean won = r.risk(roll);
                if (won) successes++;
                require(r.tier == tier + (won ? 1 : 0), "Tier advances exactly once on success");
                require(r.active == won, "Failure ends run");
                if (!won) {
                    require(r.fragments == 2 + tier * 2, "Correct consolation reward");
                    int fragments = r.fragments, revision = r.revision;
                    require(!r.risk(0) && !r.claim(), "Cannot risk or claim ended run");
                    require(r.fragments == fragments && r.revision == revision, "Replay must not mutate state");
                }
            }
            require(successes == FortunaRun.CHANCES[tier], "Exact advertised probability at every gate");
        }
        FortunaRun r = new FortunaRun(); r.start(0, random);
        r.risk(0); r.useCharm(random);
        require(r.anchor == 1 && !r.useCharm(random), "Anchor locks once");
        r.risk(0); r.risk(99);
        require(r.anchor == 1 && !r.active, "Failure preserves anchored tier");
        r.start(2, random);
        int before = r.choices[1]; r.useCharm(random);
        require(r.choices[1] != before && !r.useCharm(random), "Reroll changes upcoming reward exactly once");
        r.start(1, random); r.useCharm(random);
        r.nextAction = 999;
        Gson gson = new Gson(); r = gson.fromJson(gson.toJson(r), FortunaRun.class);
        require(r.active && r.charm == 1 && r.used && r.nextAction == 0, "Save preserves run but not monotonic clock");
        for (int i = 0; i < 5; i++) require(r.risk(0), "Can clear five gates");
        int revision = r.revision;
        require(!r.risk(0) && r.revision == revision && r.active, "No sixth gate or accidental settlement");
        require(r.claim() && !r.claim(), "Claim settles once");
        r.fragments = 79; require(!r.redeem(80), "Cannot redeem insufficient fragments");
        r.fragments = 80; require(r.redeem(80) && r.fragments == 0 && !r.redeem(80), "Redemption debits exactly once");
        r.fragments = 80; r.start(0, random);
        require(!r.redeem(80) && r.fragments == 80, "Cannot redeem during a run");
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var registries = VanillaRegistries.createWorldLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(p -> p.apply());
        for (int tier = 0; tier <= 5; tier++) for (int variant = 0; variant < 3; variant++) {
            r.choices[tier] = variant;
            var stack = VFortuna.reward(r, tier, registries);
            require(VFortuna.isReward(stack) && VFortuna.isReward(stack.copy()), "Bound marker survives copies");
            require(!VCoinsPricing.isTradeable(stack) && VCoinsPricing.getSellPrice(stack) == 0, "Rewards cannot be sold");
            require(VDuplicatePricing.getCoinCost(stack) == 0, "Rewards cannot be duplicated");
            if (tier == 4 || (tier == 5 && (variant == 0 || variant == 2))) require(stack.isEnchanted(), "Enchanted rewards have real enchantments");
            if (tier == 5 && variant == 1) require(!stack.isEnchanted() && stack.isDamaged(), "Netherite final reward is unenchanted with damaged durability");
            require(!stack.has(DataComponents.UNBREAKABLE), "Fortuna never grants unbreakable gear");
        }
        System.out.println("Fortuna regression checks passed: " + checks);
    }
}
