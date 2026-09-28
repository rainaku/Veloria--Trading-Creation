package com.vcoins;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class VeloriaCardVfx {
    static final Identifier GLOW = texture("particle_glow_orb");
    static final Identifier SPARKLE = texture("particle_sparkle");
    static final Identifier SUN = texture("cinematic_sunburst_legend");
    static final Identifier VIGNETTE = texture("cinematic_vignette");
    static final Identifier FOIL_SHEEN = texture("card_sheen_foil");

    static final Identifier[] SUNBURST = {
            texture("cinematic_sunburst_legend"),
            texture("cinematic_sunburst_mythic")
    };
    static final Identifier[] FLARE = {
            texture("cinematic_flare_legend"),
            texture("cinematic_flare_mythic")
    };
    static final Identifier[] SHOCKWAVE = {
            texture("cinematic_shockwave_legend"),
            texture("cinematic_shockwave_mythic")
    };
    static final Identifier[] BEAM = {
            texture("cinematic_beam_legend"),
            texture("cinematic_beam_mythic")
    };
    static final Identifier[] BEAM_UP = {
            texture("aura_beam_legend_up"),
            texture("aura_beam_mythic_up")
    };
    static final Identifier[] BEAM_DOWN = {
            texture("aura_beam_legend_down"),
            texture("aura_beam_mythic_down")
    };
    static final Identifier[] ORBIT = {
            texture("card_orbit_legend"),
            texture("card_orbit_mythic")
    };
    static final Identifier[] CORE_STAR = {
            texture("card_core_legend"),
            texture("card_core_mythic")
    };
    static final Identifier[] HALO = {
            texture("aura_halo_legend"),
            texture("aura_halo_mythic")
    };
    static final Identifier[] SPARK = {
            texture("particle_spark_legend"),
            texture("particle_spark_mythic")
    };
    static final Identifier[] HOVER_BORDER = {
            texture("card_hover_legend"),
            texture("card_hover_mythic")
    };
    static final Identifier LIGHTNING_MYTHIC = texture("cinematic_lightning_mythic");

    // Color Constants - Legend (Divine Roman Imperial Gold)
    static final int GOLD_CORE = 0xFFFFFF;
    static final int GOLD_LIGHT = 0xFFFFF0AA;
    static final int GOLD_PRIMARY = 0xFFFFD700;
    static final int GOLD_AMBER = 0xFFFFA000;
    static final int GOLD_DEEP = 0xFFD4AF37;

    // Color Constants - Mythic (Transcendent Cosmic Fire & Volcanic Flame)
    static final int MYTHIC_CORE = 0xFFFFFF;
    static final int MYTHIC_SEARING = 0xFFFFF090;
    static final int MYTHIC_FLAME = 0xFFFF6E14;
    static final int MYTHIC_CRIMSON = 0xFFFF1E00;
    static final int MYTHIC_CYAN = 0xFF63F4FF;

    private static final int BURST_COUNT = 48;
    private static final float[] BURST_X = new float[BURST_COUNT];
    private static final float[] BURST_Y = new float[BURST_COUNT];
    private static final float[] BURST_SPEED = new float[BURST_COUNT];

    static {
        for (int i = 0; i < BURST_COUNT; i++) {
            double angle = i * 2.399963229728653; // Golden angle in radians
            BURST_X[i] = (float) Math.cos(angle);
            BURST_Y[i] = (float) Math.sin(angle);
            BURST_SPEED[i] = 0.65f + 0.55f * (float) Math.sin(i * 1.7 + 0.3);
        }
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("vcoins", "textures/gui/" + name + ".png");
    }

    private VeloriaCardVfx() {}

    static int tint(float opacity, int rgb) {
        return (Math.clamp(Math.round(opacity * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Blits a textured quad centered at (cx, cy) with arbitrary rotation, scaling, and tint. */
    static void layer(GuiGraphicsExtractor g, Identifier texture, float cx, float cy,
                      float width, float height, float angle, float alpha, int rgb, int pixels) {
        if (alpha < 0.012f || width < 1 || height < 1) return;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        if (angle != 0f) g.pose().rotate(angle);
        g.pose().scale(width / pixels, height / pixels);
        g.blit(RenderPipelines.GUI_TEXTURED, texture, -pixels / 2, -pixels / 2,
                0f, 0f, pixels, pixels, pixels, pixels, pixels, pixels, tint(alpha, rgb));
        g.pose().popMatrix();
    }

    static void layer(GuiGraphicsExtractor g, Identifier texture, float cx, float cy,
                      float width, float height, float angle, float alpha, int pixels) {
        layer(g, texture, cx, cy, width, height, angle, alpha, 0xFFFFFF, pixels);
    }

    static void glow(GuiGraphicsExtractor g, float x, float y, float size, float alpha, int rgb) {
        if (alpha < 0.01f || size <= 0) return;
        layer(g, GLOW, x, y, size, size, 0f, alpha, rgb, 64);
    }

    static void sparkle(GuiGraphicsExtractor g, float x, float y, float size, float angle, float alpha, int rgb) {
        if (alpha < 0.01f || size <= 0) return;
        layer(g, SPARKLE, x, y, size, size, angle, alpha, rgb, 128);
    }

    static void spark(GuiGraphicsExtractor g, float x, float y, float size, float angle, float alpha, int rgb, boolean mythic) {
        if (alpha < 0.01f || size <= 0) return;
        layer(g, SPARK[mythic ? 1 : 0], x, y, size, size, angle, alpha, rgb, 64);
    }

    static void coreStar(GuiGraphicsExtractor g, float x, float y, float size, float angle, float alpha, int rgb, boolean mythic) {
        if (alpha < 0.01f || size <= 0) return;
        layer(g, CORE_STAR[mythic ? 1 : 0], x, y, size, size, angle, alpha, rgb, 128);
    }

    static void flare(GuiGraphicsExtractor g, float cx, float cy, float width, float height, float alpha, int rgb, boolean mythic) {
        if (alpha < 0.01f || width <= 0 || height <= 0) return;
        layer(g, FLARE[mythic ? 1 : 0], cx, cy, width, height, 0f, alpha, rgb, 512);
    }

    /** Smooth tapered particle trail orbiting the card in 3D perspective using pure textures (no laggy lines). */
    private static void comet(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry,
                              double angle, double tilt, int direction, float alpha, int rgb, float size, boolean mythic) {
        if (alpha < 0.015f) return;
        double ct = Math.cos(tilt), st = Math.sin(tilt);
        // Bright radiant comet head
        float hx = (float)(Math.cos(angle) * rx), hy = (float)(Math.sin(angle) * ry);
        float headX = cx + (float)(hx * ct - hy * st), headY = cy + (float)(hx * st + hy * ct);
        sparkle(g, headX, headY, size * 10f, (float) angle, alpha, rgb);
        glow(g, headX, headY, size * 14f, alpha * 0.7f, rgb);

        // Smooth decaying tail using clean textured glow steps
        for (int step = 1; step <= 6; step++) {
            double a = angle - direction * step * 0.09;
            float lx = (float)(Math.cos(a) * rx), ly = (float)(Math.sin(a) * ry);
            float x = cx + (float)(lx * ct - ly * st), y = cy + (float)(lx * st + ly * ct);
            float fade = 1.0f - step / 7.0f;
            glow(g, x, y, size * (12f * fade + 2f), alpha * fade * fade * 0.55f, rgb);
            if (step <= 2) {
                spark(g, x, y, size * 4.5f * fade, (float) a, alpha * fade * 0.7f, rgb, mythic);
            }
        }
    }

    /** Dual vertical celestial beams ascending to the heavens and descending to the abyss. */
    static void pillars(GuiGraphicsExtractor g, float cx, float cardTop, float cardBottom,
                        float stage, long elapsed, float envelope, boolean mythic) {
        if (elapsed < 700 || envelope < 0.015f) return;
        int tier = mythic ? 1 : 0;
        float beamFade = Math.clamp((elapsed - 700f) / 280f, 0f, 1f) * envelope;
        float beamW = (mythic ? 68f : 52f) * stage;
        float beamH = 220f * stage;
        int color = mythic ? MYTHIC_SEARING : GOLD_LIGHT;
        int coreColor = 0xFFFFFF;

        // 1. Ascending upper and descending lower outer beams
        layer(g, BEAM_UP[tier], cx, cardTop - beamH / 2f, beamW, beamH, 0f, beamFade * 0.75f, color, 512);
        layer(g, BEAM_DOWN[tier], cx, cardBottom + beamH / 2f, beamW, beamH, 0f, beamFade * 0.75f, color, 512);

        // 2. High-intensity incandescent center core pillar
        layer(g, BEAM_UP[tier], cx, cardTop - beamH / 2f, beamW * 0.32f, beamH, 0f, beamFade * 0.95f, coreColor, 512);
        layer(g, BEAM_DOWN[tier], cx, cardBottom + beamH / 2f, beamW * 0.32f, beamH, 0f, beamFade * 0.95f, coreColor, 512);

        // 3. Fullscreen towering pillar of light (cinematic_beam)
        float pillarH = beamH * 2.2f;
        layer(g, BEAM[tier], cx, (cardTop + cardBottom) / 2f, beamW * 0.85f, pillarH, 0f, beamFade * 0.50f, color, 512);

        // 4. Stardust motes ascending & descending along the pillar
        int moteCount = mythic ? 8 : 5;
        for (int i = 0; i < moteCount; i++) {
            float travel = ((elapsed - 700) / (mythic ? 800f : 1050f) + i * 0.18f) % 1f;
            float alpha = beamFade * (float) Math.sin(travel * Math.PI);
            float offset = (float) Math.sin(i * 2.4 + travel * 4.2) * beamW * 0.28f;
            for (int direction = -1; direction <= 1; direction += 2) {
                float edge = direction < 0 ? cardTop : cardBottom;
                float py = edge + direction * travel * beamH;
                spark(g, cx + offset, py, 9f * stage, travel * 3f, alpha * 0.85f, color, mythic);
                sparkle(g, cx + offset, py, 11f * stage, travel * 2f, alpha * 0.95f, coreColor);
            }
        }

        // 5. Ignition starburst at card attachment points
        float ignition = Math.max(0, 1 - (elapsed - 700) / 600f) * envelope;
        if (ignition > 0.02f) {
            coreStar(g, cx, cardTop, 52 * stage, 0, ignition, color, mythic);
            coreStar(g, cx, cardBottom, 52 * stage, 0, ignition, color, mythic);
            sparkle(g, cx, cardTop, 64 * stage, 0, ignition, coreColor);
            sparkle(g, cx, cardBottom, 64 * stage, 0, ignition, coreColor);
        }
    }

    /** Screen-spanning crown, anamorphic flare, and Mythic cosmic lightning at reveal beats. */
    static void ascension(GuiGraphicsExtractor g, float cx, float cy, float stage,
                          long elapsed, float envelope, boolean mythic) {
        if (envelope < 0.015f) return;
        int tier = mythic ? 1 : 0;
        int color = mythic ? MYTHIC_FLAME : GOLD_PRIMARY;
        int light = mythic ? MYTHIC_SEARING : GOLD_LIGHT;
        float time = elapsed / 1000f;

        // 1. Radiant Solar Sunburst Crown behind the card
        float charge = Math.clamp(elapsed / 700f, 0f, 1f);
        float opening = Math.clamp((elapsed - 700f) / 1000f, 0f, 1f);
        float crownAlpha = envelope * (elapsed < 700 ? charge * 0.45f : 0.65f * (1f - opening * 0.35f));
        float crownSize = (elapsed < 700 ? 120f + charge * 80f : 200f + 140f * (1f - (float) Math.pow(1 - opening, 2))) * stage;
        float rotSun = (float)(time * (mythic ? -0.18 : 0.14));
        layer(g, SUNBURST[tier], cx, cy, crownSize, crownSize, rotSun, crownAlpha, light, 512);

        if (elapsed < 700) return;

        // 2. Anamorphic Horizontal Flare & Cosmic Lightning at impact beats
        for (int beat = 0; beat < (mythic ? 2 : 1); beat++) {
            float age = elapsed - (700 + beat * 550);
            if (age < 0 || age > 820) continue;
            float t = age / 820f;
            float alpha = envelope * (1 - t) * (1 - t);

            // A. Full-screen Anamorphic Lens Flare
            float flareW = (240 + 720 * (float) Math.sqrt(t)) * stage;
            float flareH = (32 + 30 * (1 - t)) * stage;
            layer(g, FLARE[tier], cx, cy, flareW, flareH, 0f, alpha * 0.95f, light, 512);
            layer(g, FLARE[tier], cx, cy, flareW * 0.85f, flareH * 0.35f, 0f, alpha, 0xFFFFFF, 512);

            // B. Flanking radiant satellites sweeping symmetrically outward
            for (int side = -1; side <= 1; side += 2) {
                float sx = cx + side * flareW * 0.38f;
                layer(g, HALO[tier], sx, cy, 38 * stage, 38 * stage, time, alpha * 0.65f, 256);
                sparkle(g, sx, cy, 28 * stage, time * 2f, alpha * 0.95f, 0xFFFFFF);
                spark(g, sx, cy, 20 * stage, -time, alpha * 0.85f, light, mythic);
            }

            // C. Mythic Transcendent Cosmic Plasma Lightning Discharge (Custom Texture)
            if (mythic) {
                float lightningP = Math.clamp(age / 580f, 0f, 1f);
                float lightningAlpha = alpha * (1f - lightningP);
                float lightningSize = (110f + (float) Math.pow(lightningP, 0.45) * 440f) * stage;
                float lightningRot = (beat == 0 ? 1f : -1f) * (float)(time * 0.8 + beat * 1.5);
                layer(g, LIGHTNING_MYTHIC, cx, cy, lightningSize, lightningSize,
                        lightningRot, lightningAlpha * 0.95f, 512);
                if (lightningP < 0.6f) {
                    float innerA = lightningAlpha * (1f - lightningP / 0.6f);
                    layer(g, LIGHTNING_MYTHIC, cx, cy, lightningSize * 0.68f, lightningSize * 0.68f,
                            -lightningRot * 1.3f, innerA * 0.85f, 512);
                }
            }
        }
    }

    /** Ambient aura rendered when card rests on the Black Market or Fortuna table. */
    static void ambient(GuiGraphicsExtractor g, int x, int y, double time, float power, float hover,
                        boolean hidden, boolean mythic) {
        float strength = Math.clamp(power, 0f, 1f);
        float cx = x + 24, cy = y + 30;
        int color = mythic ? MYTHIC_FLAME : GOLD_PRIMARY;
        int tier = mythic ? 1 : 0;

        // Persistent top/bottom beams on the table, also visible on sealed cards.
        float beamPulse = 0.86f + 0.14f * (float) Math.sin(time * 1.7);
        float beamHeight = (mythic ? 40 : 32) + hover * 8;
        float beamWidth = (mythic ? 26 : 22) + hover * 3;
        float beamAlpha = strength * beamPulse * 0.82f;
        layer(g, BEAM_UP[tier], cx, y - beamHeight / 2, beamWidth, beamHeight, 0, beamAlpha, 512);
        layer(g, BEAM_DOWN[tier], cx, y + 60 + beamHeight / 2, beamWidth, beamHeight, 0, beamAlpha, 512);
        glow(g, cx, y, 16 + hover * 3, strength * 0.75f, color);
        glow(g, cx, y + 60, 16 + hover * 3, strength * 0.75f, color);

        glow(g, cx, cy, 64 + hover * 6, strength * 0.30f, color);
        layer(g, HALO[tier], cx, cy, 78 + hover * 8, 92 + hover * 8, 0f, strength * 0.40f, 256);
        layer(g, HOVER_BORDER[tier], cx, cy, 54 + hover * 4, 68 + hover * 4, 0f, strength * 0.80f, 256);

        int tracks = mythic ? 3 : 2;
        for (int i = 0; i < tracks; i++) {
            int direction = i % 2 == 0 ? 1 : -1;
            double angle = time * (mythic ? 1.05 : 0.75) * direction + i * Math.PI;
            comet(g, cx, cy, 20 + hover, mythic ? 12 : 22, angle,
                    mythic ? i * Math.PI / 3 : 0, direction,
                    strength * (hidden ? 0.95f : 0.65f), mythic && i == 2 ? GOLD_LIGHT : color, 1.3f, mythic);
        }

        if (hidden) {
            float pulse = 0.8f + 0.2f * (float) Math.sin(time * 1.8);
            sparkle(g, cx, cy, (mythic ? 32f : 26f) * pulse, (float)(time * 0.4), strength * pulse, color);
            coreStar(g, cx, cy, 28, (float)(time * 0.2), strength * pulse * 0.65f, color, mythic);
        }

        for (int i = 0; i < (mythic ? 8 : 5); i++) {
            double phase = time * (mythic ? 0.22 : 0.14) + i * 0.61803398875;
            float p = (float) (phase - Math.floor(phase));
            float px = x + 4 + (i * 17 % 40) + (float) Math.sin(time * 0.8 + i) * 1.8f;
            float py = y + 56 - p * 52;
            int sparkColor = mythic && i % 3 == 0 ? MYTHIC_SEARING : color;
            sparkle(g, px, py, 6f, (float)(time + i), strength * (float) Math.sin(p * Math.PI) * 0.85f, sparkColor);
        }
    }

    /**
     * Cinematic fullscreen background atmosphere:
     * Deep cosmic darkening, full-screen vignette, and rising stardust/ember motes.
     */
    static void gloryBackground(GuiGraphicsExtractor g, int screenWidth, int screenHeight,
                                float stage, long elapsed, float envelope, boolean mythic) {
        if (envelope < 0.01f) return;
        int tier = mythic ? 1 : 0;
        int bgRgb = mythic ? 0x07040E : 0x090716;

        // 1. Deep cosmic void wash
        g.fill(0, 0, screenWidth, screenHeight, tint(envelope * 0.92f, bgRgb));

        // 2. Full-screen cinematic vignette with custom chromatic rim
        float vignetteAlpha = envelope * 0.95f;
        layer(g, VIGNETTE, screenWidth / 2f, screenHeight / 2f,
                screenWidth * 1.06f, screenHeight * 1.06f, 0f, vignetteAlpha,
                mythic ? 0x220515 : 0x140B22, 512);

        // 3. Ambient stardust & fiery ember motes rising continuously across the screen
        double time = elapsed / 1000.0;
        int count = mythic ? 30 : 22;
        for (int i = 0; i < count; i++) {
            double seed = i * 0.61803398875;
            double speed = mythic ? 0.085 : 0.060;
            double progress = (time * speed + seed) % 1.0;
            float px = (float) ((Math.sin(i * 11.3 + time * 0.45) * 0.5 + 0.5) * (screenWidth - 24) + 12);
            float py = (float) (screenHeight + 12 - progress * (screenHeight + 36));
            float alpha = (float) Math.sin(progress * Math.PI) * envelope * (mythic ? 0.85f : 0.72f);
            int sparkColor = mythic
                    ? (i % 4 == 0 ? MYTHIC_CYAN : (i % 4 == 1 ? MYTHIC_SEARING : (i % 4 == 2 ? MYTHIC_FLAME : MYTHIC_CRIMSON)))
                    : (i % 3 == 0 ? GOLD_LIGHT : (i % 3 == 1 ? GOLD_PRIMARY : GOLD_AMBER));
            float moteSize = (i % 5 == 0 ? 5.5f : 3.8f) * stage;
            glow(g, px, py, moteSize * 2.8f, alpha * 0.55f, sparkColor);
            sparkle(g, px, py, moteSize * 2.2f, (float)(time * 0.8 + i), alpha, sparkColor);
        }
    }

    /**
     * The Grand Glory Climax: Rendered directly behind the hero card.
     */
    static void glory(GuiGraphicsExtractor g, float cx, float cy, float stage, long elapsed,
                      float envelope, boolean mythic) {
        if (envelope < 0.015f) return;
        float charge = Math.clamp(elapsed / 700f, 0f, 1f);
        double time = elapsed / 1000.0;
        int color = mythic ? MYTHIC_FLAME : GOLD_PRIMARY;
        int light = mythic ? MYTHIC_SEARING : GOLD_LIGHT;
        int tier = mythic ? 1 : 0;
        float impact = elapsed < 700 ? 0 : (float) Math.exp(-(elapsed - 700) / 420.0);

        // ════════════════════════════════════════════════════════════════════
        // 1. PRE-IMPACT CONVERGENCE & CHARGING PHASE (0ms - 700ms)
        // ════════════════════════════════════════════════════════════════════
        if (elapsed < 700) {
            // Gravitational Singularity: Energy motes accelerating inward toward card center (pure textures)
            int implodingCount = mythic ? 32 : 24;
            for (int i = 0; i < implodingCount; i++) {
                float seed = (float)(i * 2.399963229728653);
                float implode = (float) Math.pow(charge, 1.8);
                float dist = (1f - implode) * (190f + (i % 6) * 22f) * stage + 8f * stage;
                float spiralAngle = seed + (1f - implode) * 3.4f;
                float px = cx + (float) Math.cos(spiralAngle) * dist;
                float py = cy + (float) Math.sin(spiralAngle) * dist * 0.85f;
                float pAlpha = envelope * charge * (0.45f + 0.55f * (float) Math.sin(i * 1.5 + elapsed * 0.01));
                int pColor = mythic
                        ? (i % 3 == 0 ? MYTHIC_SEARING : (i % 3 == 1 ? MYTHIC_FLAME : MYTHIC_CRIMSON))
                        : (i % 2 == 0 ? GOLD_LIGHT : GOLD_PRIMARY);

                sparkle(g, px, py, 9f * stage, (float) (time * 3.0 + i), pAlpha, pColor);
                spark(g, px, py, 5f * stage, (float)(time * 2.0 + i), pAlpha * 0.8f, pColor, mythic);
            }

            // Charging Radiant Halo: breathing with escalating heartbeat frequency
            float heartRate = 0.008f + charge * 0.024f;
            float pulseScale = 1.0f + 0.14f * (float) Math.sin(elapsed * heartRate);
            layer(g, HALO[tier], cx, cy, (200 * pulseScale) * stage, (230 * pulseScale) * stage,
                    0f, envelope * (0.35f + charge * 0.55f), 256);

            // Charging Orbital Seal: Accelerating spin and condensing inward
            float chargingOrbitSize = (250f - 85f * (float) Math.pow(charge, 1.4)) * stage;
            float orbitSpin = (float) (time * 0.85 + charge * charge * 2.8);
            layer(g, ORBIT[tier], cx, cy, chargingOrbitSize, chargingOrbitSize, orbitSpin,
                    envelope * (0.35f + charge * 0.60f), 256);

            // Inner counter-rotating ring
            layer(g, ORBIT[tier], cx, cy, chargingOrbitSize * 0.72f, chargingOrbitSize * 0.72f, -orbitSpin * 1.35f,
                    envelope * (0.25f + charge * 0.50f), 256);

            // Charging Core Starburst peeking through
            float coreChargeSize = (50f + charge * 52f) * stage;
            coreStar(g, cx, cy, coreChargeSize, (float)(time * 0.3),
                    envelope * (0.3f + charge * 0.7f), mythic ? MYTHIC_SEARING : GOLD_LIGHT, mythic);
            sparkle(g, cx, cy, coreChargeSize * 1.2f, (float)(-time * 0.5),
                    envelope * (0.4f + charge * 0.6f), 0xFFFFFF);

            // Pre-ignition Implosion Dip (630ms - 695ms): Brief gathering of light right before detonation
            if (elapsed >= 630) {
                float dip = (700f - elapsed) / 70f;
                glow(g, cx, cy, 140 * stage * dip, envelope * dip * 0.8f, 0xFFFFFF);
            }
            return;
        }

        // ════════════════════════════════════════════════════════════════════
        // 2. THE SUPERNOVA DETONATION & SUSTAINED GLORY (700ms+)
        // ════════════════════════════════════════════════════════════════════

        // A. Blinding Supernova Flash (White-hot burst + horizontal anamorphic flare)
        float flashProgress = Math.clamp((elapsed - 700f) / 320f, 0f, 1f);
        if (flashProgress < 1f) {
            float flashAlpha = (1f - flashProgress) * (1f - flashProgress);
            float flashSize = (140f + (float) Math.pow(flashProgress, 0.55) * (mythic ? 440f : 340f)) * stage;
            glow(g, cx, cy, flashSize * 0.75f, envelope * flashAlpha * 0.95f, 0xFFFFFF);
            glow(g, cx, cy, flashSize, envelope * flashAlpha * 0.75f, light);
            coreStar(g, cx, cy, flashSize * 0.9f, (float)(time * 0.8),
                    envelope * flashAlpha * 0.90f, 0xFFFFFF, mythic);
            sparkle(g, cx, cy, flashSize * 1.1f, (float)(-time * 1.2),
                    envelope * flashAlpha, 0xFFFFFF);
        }

        // B. Colossal Multi-Stage Expanding Shockwaves (Custom Textures)
        int waveCount = mythic ? 3 : 2;
        for (int w = 0; w < waveCount; w++) {
            long waveStart = 700L + w * (mythic ? 110L : 140L);
            if (elapsed >= waveStart) {
                float wp = Math.clamp((elapsed - waveStart) / (920f + w * 140f), 0f, 1f);
                if (wp < 1f) {
                    float waveScale = (40f + (float) Math.pow(wp, 0.62) * (mythic ? (320f + w * 70f) : (280f + w * 50f))) * stage;
                    float waveAlpha = envelope * (1f - wp) * (1f - wp) * (0.95f - w * 0.12f);
                    float waveRot = (w % 2 == 0 ? 1f : -1f) * (float)(time * 0.38 + w * 1.15);
                    int waveTint = mythic
                            ? (w == 0 ? MYTHIC_SEARING : (w == 1 ? MYTHIC_FLAME : MYTHIC_CRIMSON))
                            : (w == 0 ? GOLD_LIGHT : GOLD_PRIMARY);
                    layer(g, SHOCKWAVE[tier], cx, cy, waveScale, waveScale, waveRot, waveAlpha, waveTint, 512);
                }
            }
        }

        // C. Dual Counter-Rotating Radiant Sunburst Mandalas (Custom Textures)
        float burstOpen = Math.clamp((elapsed - 700f) / 600f, 0f, 1f);
        float burstEase = 1f - (1f - burstOpen) * (1f - burstOpen);
        float sunAlpha = envelope * (0.75f + 0.15f * (float) Math.sin(time * 2.5)) * burstEase;
        float sun1Size = (mythic ? 320f : 290f) * stage;
        float sun2Size = sun1Size * 0.75f;
        layer(g, SUNBURST[tier], cx, cy, sun1Size, sun1Size, (float)(time * 0.15), sunAlpha * 0.70f, light, 512);
        layer(g, SUNBURST[tier], cx, cy, sun2Size, sun2Size, -(float)(time * 0.22), sunAlpha * 0.50f, color, 512);

        // D. Mythic Special: Transcendent Second Ignition (1250ms)
        if (mythic && elapsed >= 1250) {
            float secondProgress = Math.clamp((elapsed - 1250f) / 720f, 0f, 1f);
            if (secondProgress < 1f) {
                float secondWaveSize = (60f + (float) Math.pow(secondProgress, 0.58) * 360f) * stage;
                float secondWaveAlpha = envelope * (1f - secondProgress) * (1f - secondProgress) * 0.90f;
                layer(g, SHOCKWAVE[1], cx, cy, secondWaveSize, secondWaveSize,
                        -(float)(time * 0.55), secondWaveAlpha, MYTHIC_CRIMSON, 512);
            }
        }

        // E. Continuous Sacred Celestial Orbit Rings
        float orbit1Size = (mythic ? 245f : 215f) * stage;
        float orbit1Rot = (float)(time * 0.16);
        float orbit1Alpha = envelope * (0.80f + 0.15f * (float) Math.sin(time * 2.2));
        layer(g, ORBIT[tier], cx, cy, orbit1Size, orbit1Size, orbit1Rot, orbit1Alpha, 256);

        float orbit2Size = (mythic ? 180f : 155f) * stage;
        float orbit2Rot = -(float)(time * 0.26);
        float orbit2Alpha = envelope * 0.65f;
        layer(g, ORBIT[tier], cx, cy, orbit2Size, orbit2Size, orbit2Rot, orbit2Alpha, 256);

        // F. Central Core Starburst behind card
        float coreSize = (80f + 16f * (float) Math.sin(time * 3.2) + impact * 70f) * stage;
        coreStar(g, cx, cy, coreSize, (float)(time * 0.12),
                envelope * (0.45f + 0.45f * impact), light, mythic);
        sparkle(g, cx, cy, coreSize * 1.3f, (float)(-time * 0.20),
                envelope * (0.50f + 0.45f * impact), 0xFFFFFF);
        glow(g, cx, cy, coreSize * 1.8f, envelope * 0.38f, color);

        // G. Radial Explosive Sparks Burst (Clean Textured Sprites)
        int burstCount = mythic ? 38 : 28;
        for (int i = 0; i < burstCount; i++) {
            float sparkElapsed = elapsed - 700f - (i % 6) * 18f;
            if (sparkElapsed < 0) continue;
            float p = Math.clamp(sparkElapsed / 1350f, 0f, 1f);
            float ease = 1f - (1f - p) * (1f - p);
            float radius = (18f + ease * 165f * BURST_SPEED[i]) * stage;
            float sparkAlpha = envelope * (1f - p) * (1f - p);
            if (sparkAlpha < 0.015f) continue;

            float px = cx + BURST_X[i] * radius;
            float py = cy + BURST_Y[i] * radius * 0.82f;
            int sparkColor = mythic
                    ? (i % 4 == 0 ? MYTHIC_CYAN : (i % 4 == 1 ? MYTHIC_SEARING : (i % 4 == 2 ? MYTHIC_FLAME : MYTHIC_CRIMSON)))
                    : (i % 3 == 0 ? GOLD_LIGHT : (i % 3 == 1 ? GOLD_PRIMARY : GOLD_AMBER));

            float sSize = (i % 3 == 0 ? 8.5f : 6.0f) * stage;
            sparkle(g, px, py, sSize, (float)(time * 2.5 + i), sparkAlpha, sparkColor);
            spark(g, px, py, sSize * 0.75f, (float)(time * 1.5 + i), sparkAlpha * 0.85f, sparkColor, mythic);
        }

        // H. Orbiting 3D Tilted Comets (Pure Textures)
        int cometTracks = mythic ? 3 : 2;
        for (int i = 0; i < cometTracks; i++) {
            int direction = i % 2 == 0 ? 1 : -1;
            double cometAngle = time * (mythic ? 0.95 : 0.75) * direction + i * 2.1;
            double tilt = mythic ? i * Math.PI / 3.0 : i * Math.PI / 4.0;
            int cometColor = mythic
                    ? (i == 2 ? MYTHIC_CYAN : (i == 1 ? MYTHIC_SEARING : MYTHIC_FLAME))
                    : (i == 1 ? GOLD_LIGHT : GOLD_PRIMARY);
            comet(g, cx, cy, (mythic ? 88f : 76f) * stage, (mythic ? 50f : 44f) * stage,
                    cometAngle, tilt, direction, envelope * 0.85f, cometColor, 1.8f * stage, mythic);
        }
    }

    /**
     * Card Face Luxury FX:
     * - 45-degree holographic diagonal foil sheen sweep across the card face
     * - Prismatic border glow using high-resolution custom card hover texture
     */
    static void gloryCardFace(GuiGraphicsExtractor g, float cx, float cy, float scaleX, float scaleY,
                             float stage, long elapsed, float envelope, boolean mythic) {
        if (elapsed < 700 || envelope < 0.015f) return;
        int tier = mythic ? 1 : 0;
        int lightColor = mythic ? MYTHIC_SEARING : GOLD_LIGHT;

        // 1. Holographic Foil Sheen Sweep across the card surface
        float sweepProgress;
        if (elapsed < 1300) {
            sweepProgress = Math.clamp((elapsed - 720f) / 560f, 0f, 1f);
        } else {
            sweepProgress = ((elapsed - 1300) % 2400) / 750f;
        }

        if (sweepProgress >= 0f && sweepProgress <= 1f) {
            float sheenAlpha = (float) Math.sin(sweepProgress * Math.PI) * envelope * 0.85f;
            float travelX = (-32f + sweepProgress * 64f) * scaleX;
            float travelY = (40f - sweepProgress * 80f) * scaleY;
            float sheenSize = 78f * scaleY;
            layer(g, FOIL_SHEEN, cx + travelX, cy + travelY, sheenSize, sheenSize,
                    (float) Math.PI / 4f, sheenAlpha, lightColor, 128);
        }

        // 2. Prismatic Edge Luster: High-res custom textured border glow
        float edgeBreathe = 0.70f + 0.30f * (float) Math.sin((elapsed - 700) * 0.004);
        float borderW = 54f * scaleX;
        float borderH = 68f * scaleY;
        layer(g, HOVER_BORDER[tier], cx, cy, borderW, borderH, 0f,
                envelope * 0.85f * edgeBreathe, 256);
    }

    /**
     * Item Radiance Core: Rendered directly behind the floating 3D relic item.
     */
    static void gloryItemCore(GuiGraphicsExtractor g, float cx, float cy, float itemScale,
                              float stage, long elapsed, float envelope, boolean mythic) {
        if (elapsed < 700 || envelope < 0.015f) return;
        int coreColor = mythic ? MYTHIC_SEARING : GOLD_LIGHT;
        int glowColor = mythic ? MYTHIC_FLAME : GOLD_PRIMARY;

        float coreSize = (42f + 8f * (float) Math.sin((elapsed - 700) * 0.0035)) * itemScale;
        coreStar(g, cx, cy, coreSize, (float)((elapsed - 700) * 0.0012),
                envelope * 0.90f, coreColor, mythic);
        sparkle(g, cx, cy, coreSize * 1.2f, (float)((elapsed - 700) * -0.0018),
                envelope * 0.95f, 0xFFFFFF);
        glow(g, cx, cy, coreSize * 1.6f, envelope * 0.45f, glowColor);
    }

    /**
     * Foreground Ascending Embers & Stardust:
     * Drifts upward gracefully in front of the card to create immersive depth.
     */
    static void gloryForeground(GuiGraphicsExtractor g, float cx, float cy, float stage,
                                long elapsed, float envelope, boolean mythic) {
        if (elapsed < 700 || envelope < 0.015f) return;
        double time = (elapsed - 700) / 1000.0;
        int count = mythic ? 18 : 12;

        for (int i = 0; i < count; i++) {
            double seed = i * 0.61803398875;
            double progress = (time * (mythic ? 0.32 : 0.24) + seed) % 1.0;
            float spreadX = ((i * 19 % 44) - 22f) * stage;
            float px = cx + spreadX + (float) Math.sin(time * 1.5 + i) * 8f * stage;
            float py = cy + 34f * stage - (float) progress * 80f * stage;
            float alpha = (float) Math.sin(progress * Math.PI) * envelope * (mythic ? 0.85f : 0.72f);
            if (alpha < 0.02f) continue;

            int sparkColor = mythic
                    ? (i % 3 == 0 ? MYTHIC_SEARING : (i % 3 == 1 ? MYTHIC_FLAME : MYTHIC_CRIMSON))
                    : (i % 2 == 0 ? GOLD_LIGHT : GOLD_PRIMARY);
            sparkle(g, px, py, (i % 4 == 0 ? 8.5f : 6.0f) * stage,
                    (float)(time * 2.0 + i), alpha, sparkColor);
        }
    }

    /**
     * Cinematic animated VFX for the Glory Reveal Title.
     * Features:
     * - Elastic scale entrance with horizontal anamorphic lens flare blade
     * - Flanking celestial star crests with sacred stars and halo auras
     * - High performance 2-pass crisp drop-shadow and illuminated text
     * - Specular glint shimmer gliding across the title
     * - Ascending stardust motes drifting gracefully off the title
     */
    static void gloryTitle(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font,
                           Component heading, float cx, float cy, float stage,
                           long elapsed, float envelope, boolean mythic) {
        if (elapsed < 720 || envelope < 0.015f) return;
        float entrance = Math.clamp((elapsed - 720f) / 320f, 0f, 1f);
        float textFade = entrance * envelope;
        if (textFade < 0.015f) return;

        double time = elapsed / 1000.0;
        int primaryColor = mythic ? MYTHIC_FLAME : GOLD_PRIMARY;
        int lightColor = mythic ? MYTHIC_SEARING : GOLD_LIGHT;
        int shadowColor = mythic ? 0x991000 : 0x775200;
        int tier = mythic ? 1 : 0;

        // 1. Elastic Entrance Slam & Scale
        float slamProgress = Math.clamp((elapsed - 720f) / 380f, 0f, 1f);
        float slamPop = (1.0f - slamProgress) * (float) Math.cos(slamProgress * Math.PI * 1.5) * 0.35f;
        float secondPulse = (mythic && elapsed >= 1250 && elapsed < 1650)
                ? (float) Math.exp(-(elapsed - 1250) / 220.0) * 0.18f : 0f;
        float baseScale = Math.min((mythic ? 1.85f : 1.55f) * stage,
                (g.guiWidth() - 36f) / Math.max(1, font.width(heading)));
        float currentScale = baseScale * (1.0f + slamPop + secondPulse);

        int textWidth = font.width(heading);
        float halfWidth = textWidth * 0.5f;

        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(currentScale, currentScale);

        // 2. Background Radiant Glow Plate
        float plateW = textWidth + 80f;
        glow(g, 0, 0, plateW, textFade * (mythic ? 0.42f : 0.35f), mythic ? 0xFF2805 : 0xDDAA00);

        // 3. Anamorphic Horizontal Core Light Blade (Custom Flare Texture)
        float bladeW = textWidth + 140f;
        float bladeH = 24f;
        float flashBoost = (1f - slamProgress) * 1.8f;
        layer(g, FLARE[tier], 0, 0, bladeW * (1f + flashBoost * 0.35f), bladeH * (1f + flashBoost * 0.5f),
                0f, textFade * Math.min(1f, 0.85f + flashBoost * 0.15f), lightColor, 512);

        // 4. Flanking Sacred Celestial Star Crests
        float crestDist = halfWidth + 24f;
        float crestRot = (float) (time * 0.65);
        // Left Crest
        coreStar(g, -crestDist, 0, 26f, crestRot, textFade * 0.95f, lightColor, mythic);
        sparkle(g, -crestDist, 0, 24f, -crestRot * 1.5f, textFade, 0xFFFFFF);
        layer(g, HALO[tier], -crestDist, 0, 36f, 36f, 0f, textFade * 0.50f, 256);
        // Right Crest
        coreStar(g, crestDist, 0, 26f, -crestRot, textFade * 0.95f, lightColor, mythic);
        sparkle(g, crestDist, 0, 24f, crestRot * 1.5f, textFade, 0xFFFFFF);
        layer(g, HALO[tier], crestDist, 0, 36f, 36f, 0f, textFade * 0.50f, 256);

        // 5. Clean, High-Performance Title Rendering (Crisp and Beautiful, No per-character font lag)
        float charY = -9 / 2f;
        int renderY = Math.round(charY);
        int renderX = Math.round(-halfWidth);

        // Mythic cosmic cyan chromatic aberration offset
        if (mythic) {
            g.text(font, heading, renderX - 1, renderY, tint(textFade * 0.40f, MYTHIC_CYAN), false);
        }
        // Glowing Drop Shadow
        g.text(font, heading, renderX, renderY + 1, tint(textFade * 0.70f, shadowColor), false);
        // Illuminated crisp foreground text
        g.text(font, heading, renderX, renderY, tint(textFade, lightColor), false);

        // Specular Sparkle traveling across the title
        float shimmerPhase = (float) ((elapsed * 0.0018) % 2.5);
        float shimmerPos = (shimmerPhase - 0.75f) * textWidth;
        if (shimmerPos >= -halfWidth && shimmerPos <= halfWidth) {
            sparkle(g, shimmerPos, 0, 22f, (float)(time * 2.5), textFade * 0.90f, 0xFFFFFF);
        }

        // 6. Ascending Stardust Drifting Off the Title
        int titleMotes = mythic ? 8 : 5;
        for (int m = 0; m < titleMotes; m++) {
            double moteSeed = m * 0.61803398875;
            double moteProgress = (time * (mythic ? 0.40 : 0.30) + moteSeed) % 1.0;
            float mx = (float) ((m * 19 % textWidth) - halfWidth + Math.sin(time * 2.0 + m) * 4f);
            float my = charY - (float) moteProgress * 22f;
            float mAlpha = (float) Math.sin(moteProgress * Math.PI) * textFade * (mythic ? 0.90f : 0.75f);
            int mColor = mythic ? (m % 2 == 0 ? MYTHIC_SEARING : MYTHIC_CRIMSON) : GOLD_LIGHT;
            sparkle(g, mx, my, 7f, (float)(time * 2.0 + m), mAlpha, mColor);
        }

        g.pose().popMatrix();
    }
}
