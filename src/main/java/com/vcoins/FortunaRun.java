package com.vcoins;

/** Persistent, server-owned state. No client input supplies outcomes or rewards. */
public final class FortunaRun {
    public static final int[] CHANCES = {85, 65, 45, 25, 5};
    public boolean active;
    public int tier, charm, anchor = -1, fragments, result, revision;
    public boolean used;
    public int[] choices = new int[6];
    public transient long nextAction;

    public void start(int selectedCharm, java.util.random.RandomGenerator random) {
        active = true; tier = 0; anchor = -1; used = false; charm = selectedCharm; result = 1;
        for (int i = 0; i < choices.length; i++) choices[i] = random.nextInt(3);
        revision++;
    }

    public boolean risk(int roll) {
        if (!active || tier >= 5 || roll < 0 || roll >= 100) return false;
        boolean won = roll < CHANCES[tier];
        if (won) { tier++; result = 2; }
        else { active = false; fragments = Math.min(30000, fragments + 2 + tier * 2); result = 3; }
        revision++;
        return won;
    }

    public boolean claim() {
        if (!active) return false;
        active = false; result = 4; revision++; return true;
    }

    public boolean useCharm(java.util.random.RandomGenerator random) {
        if (!active || used || tier >= 5) return false;
        used = true; revision++; result = 5;
        if (charm == 0) anchor = tier;
        if (charm == 2 && tier + 1 < choices.length) choices[tier + 1] = (choices[tier + 1] + 1 + random.nextInt(2)) % 3;
        return true;
    }

    public boolean redeem(int cost) {
        if (active || cost <= 0 || fragments < cost) return false;
        fragments -= cost; result = 6; revision++; return true;
    }
}
