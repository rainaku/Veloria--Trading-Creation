package com.vcoins;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class TextureGenerator {
    // Color Palette matching Image 2 reference artwork
    // Skin tones
    static final int SKIN_BASE = 0xFFB98260;
    static final int SKIN_LIGHT = 0xFFC98864;
    static final int SKIN_SHADOW = 0xFFA86D4C;
    static final int SKIN_DEEP = 0xFF8D5536;
    static final int BROW = 0xFF3D2112;
    static final int EYE_WHITE = 0xFFF8F6F0;
    static final int EYE_GREEN = 0xFF28B548;
    static final int EYE_PUPIL = 0xFF0D4A1E;
    static final int MOUTH = 0xFF7A472E;

    // Royal Purple (Hood, Mantle, Cape, Sash)
    static final int PURPLE_HI = 0xFF5D3A7C;
    static final int PURPLE_MID = 0xFF492B64;
    static final int PURPLE_BASE = 0xFF361E4C;
    static final int PURPLE_DARK = 0xFF261338;
    static final int PURPLE_DEEP = 0xFF190C25;

    // Emerald Teal Cloth (Coat, Sleeves, Trousers)
    static final int TEAL_HI = 0xFF22998C;
    static final int TEAL_LIGHT = 0xFF188075;
    static final int TEAL_BASE = 0xFF115E55;
    static final int TEAL_DARK = 0xFF0B433D;
    static final int TEAL_DEEP = 0xFF062A26;

    // Antique Gold (Trims, Brooch, Buckles, Star Emblems)
    static final int GOLD_HI = 0xFFFCE166;
    static final int GOLD_BASE = 0xFFE5B834;
    static final int GOLD_MID = 0xFFC89523;
    static final int GOLD_SHADOW = 0xFF9E6E12;
    static final int GOLD_DEEP = 0xFF634305;

    // Amber Gemstone
    static final int GEM_HI = 0xFFFFF0A6;
    static final int GEM_BASE = 0xFFFFAE19;
    static final int GEM_DARK = 0xFFCC7500;

    // Leather (Belt, Satchel, Boots)
    static final int LEATHER_HI = 0xFF754C32;
    static final int LEATHER_BASE = 0xFF583722;
    static final int LEATHER_DARK = 0xFF3F2516;
    static final int LEATHER_DEEP = 0xFF26150C;
    static final int LEATHER_STITCH = 0xFF8C5F3E;

    public static BufferedImage generate() {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);

        // Fill background with transparent
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                img.setRGB(x, y, 0x00000000);
            }
        }

        // =====================================================================
        // 1. HEAD (8x10x8) at (0, 0)
        // UV width: 32, height: 18
        // =====================================================================
        fillSkinBox(img, 0, 0, 8, 10, 8);
        // Custom front face details at (8, 8):
        int fx = 8, fy = 8;
        // Forehead shadow & highlights
        setRow(img, fx, fy + 0, 8, SKIN_SHADOW);
        setRow(img, fx, fy + 1, 8, SKIN_BASE);
        img.setRGB(fx + 3, fy + 1, SKIN_LIGHT);
        img.setRGB(fx + 4, fy + 1, SKIN_LIGHT);
        // Unibrow across forehead (x: 0..7)
        setRow(img, fx, fy + 2, 8, BROW);
        img.setRGB(fx + 0, fy + 2, SKIN_SHADOW);
        img.setRGB(fx + 7, fy + 2, SKIN_SHADOW);
        // Eyes & nose bridge
        img.setRGB(fx + 0, fy + 3, SKIN_BASE);
        img.setRGB(fx + 1, fy + 3, EYE_WHITE);
        img.setRGB(fx + 2, fy + 3, EYE_GREEN);
        img.setRGB(fx + 3, fy + 3, SKIN_SHADOW);
        img.setRGB(fx + 4, fy + 3, SKIN_SHADOW);
        img.setRGB(fx + 5, fy + 3, EYE_GREEN);
        img.setRGB(fx + 6, fy + 3, EYE_WHITE);
        img.setRGB(fx + 7, fy + 3, SKIN_BASE);
        // Pupil bottom
        img.setRGB(fx + 1, fy + 4, EYE_WHITE);
        img.setRGB(fx + 2, fy + 4, EYE_PUPIL);
        img.setRGB(fx + 5, fy + 4, EYE_PUPIL);
        img.setRGB(fx + 6, fy + 4, EYE_WHITE);
        // Cheeks
        img.setRGB(fx + 1, fy + 5, SKIN_LIGHT);
        img.setRGB(fx + 6, fy + 5, SKIN_LIGHT);
        // Philtrum / upper lip
        setRow(img, fx, fy + 7, 8, SKIN_BASE);
        img.setRGB(fx + 3, fy + 7, SKIN_SHADOW);
        img.setRGB(fx + 4, fy + 7, SKIN_SHADOW);
        // Mouth
        setRow(img, fx, fy + 8, 8, SKIN_BASE);
        img.setRGB(fx + 2, fy + 8, MOUTH);
        img.setRGB(fx + 3, fy + 8, MOUTH);
        img.setRGB(fx + 4, fy + 8, MOUTH);
        img.setRGB(fx + 5, fy + 8, MOUTH);
        // Chin
        setRow(img, fx, fy + 9, 8, SKIN_SHADOW);
        img.setRGB(fx + 3, fy + 9, SKIN_BASE);
        img.setRGB(fx + 4, fy + 9, SKIN_BASE);

        // =====================================================================
        // 2. NOSE (~3x5x3) at (32, 0)
        // =====================================================================
        fillSkinBox(img, 32, 0, 3, 5, 3);
        int nx = 32 + 3, ny = 0 + 3; // front face
        img.setRGB(nx + 1, ny + 0, SKIN_LIGHT);
        img.setRGB(nx + 1, ny + 4, SKIN_SHADOW);

        // =====================================================================
        // 3. HOOD TRIMS & STAR & MANTLE GOLD (Y = 0..15)
        // =====================================================================
        // Hood trim left: (112, 0) - ~1x10x1
        fillGoldBox(img, 112, 0, 1, 10, 1);
        // Hood trim right: (116, 0) - ~1x10x1
        fillGoldBox(img, 116, 0, 1, 10, 1);
        // Hood trim top: (120, 0) - ~8x1x1
        fillGoldBox(img, 120, 0, 8, 1, 1);
        // Step trims: (136, 0), (142, 0) - ~2x1x1
        fillGoldBox(img, 136, 0, 2, 1, 1);
        fillGoldBox(img, 142, 0, 2, 1, 1);

        // 3D Star cubes: (150, 0), (154, 0), (158, 0), (162, 0), (166, 0)
        fillGoldBox(img, 150, 0, 1, 1, 1);
        fillGoldBox(img, 154, 0, 1, 1, 1);
        fillGoldBox(img, 158, 0, 1, 1, 1);
        fillGoldBox(img, 162, 0, 1, 1, 1);
        fillGoldBox(img, 166, 0, 1, 1, 1);

        // Mantle gold lines: (176, 0), (192, 0) - ~6x1x7
        fillGoldBox(img, 176, 0, 6, 1, 7);
        fillGoldBox(img, 192, 0, 6, 1, 7);

        // =====================================================================
        // 4. BROOCH & COAT GOLD CENTER / HEMS (Y = 16..31)
        // =====================================================================
        // Brooch outer gold frame: (128, 16) - ~3x3x1
        fillGoldBox(img, 128, 16, 3, 3, 1);
        // Brooch gem amber center: (140, 16) - ~2x2x1
        fillGemBox(img, 140, 16, 2, 2, 1);

        // Coat gold center trim: (176, 16) - ~1x12x1
        fillGoldBox(img, 176, 16, 1, 12, 1);
        // Coat hem left: (184, 16) - ~4x1x7
        fillGoldBox(img, 184, 16, 4, 1, 7);
        // Coat hem right: (204, 16) - ~4x1x7
        fillGoldBox(img, 204, 16, 4, 1, 7);

        // =====================================================================
        // 5. HOOD CLOTH PIECES & WAIST BELT (Y = 32..47)
        // =====================================================================
        // Hood top: (0, 32) - ~10x2x9
        fillPurpleBox(img, 0, 32, 10, 2, 9);
        // Hood left: (40, 32) - ~2x10x9
        fillPurpleBox(img, 40, 32, 2, 10, 9);
        // Hood right: (64, 32) - ~2x10x9
        fillPurpleBox(img, 64, 32, 2, 10, 9);
        // Hood back: (88, 32) - ~10x10x2
        fillPurpleBox(img, 88, 32, 10, 10, 2);

        // Belt: (128, 32) - ~9x2x7
        fillLeatherBox(img, 128, 32, 9, 2, 7);
        // Belt buckle: (160, 32) - ~2x2x1
        fillGoldBox(img, 160, 32, 2, 2, 1);
        // Buckle center hole
        img.setRGB(160 + 1 + 0, 32 + 1 + 1, LEATHER_DARK);

        // =====================================================================
        // 6. HOOD CROWN / TAIL & FRONT SASH (Y = 48..79)
        // =====================================================================
        // Hood crown: (0, 48) - ~8x4x6
        fillPurpleBox(img, 0, 48, 8, 4, 6);
        // Hood tail: (32, 48) - ~8x9x2
        fillPurpleBox(img, 32, 48, 8, 9, 2);

        // Front sash: (160, 48) - ~3x10x1
        fillPurpleBox(img, 160, 48, 3, 10, 1);
        // Add gold side border & diamond emblem on front face of sash
        int sfx = 160 + 1, sfy = 48 + 1;
        for (int y = 0; y < 10; y++) {
            img.setRGB(sfx + 0, sfy + y, (y % 2 == 0) ? GOLD_HI : GOLD_BASE);
            img.setRGB(sfx + 2, sfy + y, (y % 2 == 0) ? GOLD_HI : GOLD_BASE);
        }
        img.setRGB(sfx + 1, sfy + 3, GOLD_HI); // gold star in center of sash
        img.setRGB(sfx + 1, sfy + 4, GOLD_BASE);
        img.setRGB(sfx + 1, sfy + 8, GOLD_HI); // golden chevron tip
        img.setRGB(sfx + 1, sfy + 9, GOLD_BASE);

        // Sash tip: (172, 48) - ~2x2x1
        fillPurpleBox(img, 172, 48, 2, 2, 1);
        img.setRGB(172 + 1, 48 + 1, GOLD_HI);

        // =====================================================================
        // 7. BODY, JACKET, MANTLE (Y = 80..103)
        // =====================================================================
        // Villager torso: (0, 80) - 8x12x6
        fillTealBox(img, 0, 80, 8, 12, 6);

        // Jacket upper shell: (32, 80) - ~9x11x7
        fillTealBox(img, 32, 80, 9, 11, 7);

        // Shoulder mantle: (64, 80) - ~12x4x8
        fillPurpleBox(img, 64, 80, 12, 4, 8);
        // Mantle left drop: (104, 80) - ~4x5x7
        fillPurpleBox(img, 104, 80, 4, 5, 7);
        // Mantle right drop: (128, 80) - ~4x5x7
        fillPurpleBox(img, 128, 80, 4, 5, 7);

        // =====================================================================
        // 8. COAT PANELS & CAPE (Y = 104..127)
        // =====================================================================
        // Coat left flap: (0, 104) - ~4x12x7
        fillTealBox(img, 0, 104, 4, 12, 7);
        // Coat right flap: (32, 104) - ~4x12x7
        fillTealBox(img, 32, 104, 4, 12, 7);

        // Cape upper: (128, 104) - ~11x9x1
        fillPurpleBox(img, 128, 104, 11, 9, 1);
        // Cape lower: (160, 104) - ~10x10x1
        fillPurpleBox(img, 160, 104, 10, 10, 1);
        // Gold edge trim on back face of cape lower: (160+1, 104+1), 10x10
        int cfx = 160 + 1, cfy = 104 + 1;
        for (int y = 0; y < 10; y++) {
            img.setRGB(cfx + 0, cfy + y, (y % 2 == 0) ? GOLD_HI : GOLD_BASE);
            img.setRGB(cfx + 9, cfy + y, (y % 2 == 0) ? GOLD_HI : GOLD_BASE);
        }
        for (int x = 0; x < 10; x++) {
            img.setRGB(cfx + x, cfy + 9, (x % 2 == 0) ? GOLD_HI : GOLD_BASE);
        }

        // =====================================================================
        // 9. SATCHEL (Y = 128..143)
        // =====================================================================
        // Satchel bag: (128, 128) - ~4x5x3
        fillLeatherBox(img, 128, 128, 4, 5, 3);
        // Satchel flap: (148, 128) - ~4x2x1
        fillLeatherBox(img, 148, 128, 4, 2, 1);
        // Satchel buckle: (166, 128) - ~1x1x1
        fillGoldBox(img, 166, 128, 1, 1, 1);

        // =====================================================================
        // 10. ARMS / SLEEVES / CUFFS (Y = 144..175)
        // =====================================================================
        // Left sleeve: (0, 144) - ~7x5x5
        fillTealBox(img, 0, 144, 7, 5, 5);
        // Right sleeve: (32, 144) - ~7x5x5
        fillTealBox(img, 32, 144, 7, 5, 5);

        // Left cuff: (72, 144) - ~2x5x5
        fillCuffBox(img, 72, 144, 2, 5, 5);
        // Right cuff: (88, 144) - ~2x5x5
        fillCuffBox(img, 88, 144, 2, 5, 5);

        // Crossed forearms: (104, 144) - ~7x4x5
        fillTealBox(img, 104, 144, 7, 4, 5);
        // Center hands skin patch on forearms front: (104 + 5, 144 + 5), 7x4
        int hx = 104 + 5, hy = 144 + 5;
        img.setRGB(hx + 2, hy + 1, SKIN_BASE);
        img.setRGB(hx + 3, hy + 1, SKIN_LIGHT);
        img.setRGB(hx + 4, hy + 1, SKIN_BASE);
        img.setRGB(hx + 2, hy + 2, SKIN_SHADOW);
        img.setRGB(hx + 3, hy + 2, SKIN_BASE);
        img.setRGB(hx + 4, hy + 2, SKIN_SHADOW);

        // =====================================================================
        // 11. LEGS & BOOTS (Y = 176..255)
        // =====================================================================
        // Right leg: (0, 176) - 4x12x4
        fillTealBox(img, 0, 176, 4, 12, 4);
        // Left leg: (16, 176) - 4x12x4
        fillTealBox(img, 16, 176, 4, 12, 4);

        // Right boot: (40, 176) - ~5x6x6
        fillBootBox(img, 40, 176, 5, 6, 6);
        // Left boot: (64, 176) - ~5x6x6
        fillBootBox(img, 64, 176, 5, 6, 6);

        // Right boot cuff: (88, 176) - ~4x1x5
        fillLeatherBox(img, 88, 176, 4, 1, 5);
        // Left boot cuff: (108, 176) - ~4x1x5
        fillLeatherBox(img, 108, 176, 4, 1, 5);

        return img;
    }

    // -------------------------------------------------------------------------
    // BOX FILL HELPERS (Standard Minecraft Box UV Layout)
    // Total UV width:  2 * dz + 2 * dx
    // Total UV height: dz + dy
    // -------------------------------------------------------------------------

    private static void fillSkinBox(BufferedImage img, int u, int v, int dx, int dy, int dz) {
        int w = 2 * dz + 2 * dx;
        int h = dz + dy;
        fillRect(img, u, v, w, h, SKIN_BASE);
        noiseRect(img, u, v, w, h, SKIN_BASE, SKIN_LIGHT, SKIN_SHADOW);
    }

    private static void fillPurpleBox(BufferedImage img, int u, int v, int dx, int dy, int dz) {
        int w = 2 * dz + 2 * dx;
        int h = dz + dy;
        fillPurpleCloth(img, u, v, w, h);
    }

    private static void fillTealBox(BufferedImage img, int u, int v, int dx, int dy, int dz) {
        int w = 2 * dz + 2 * dx;
        int h = dz + dy;
        fillTealCloth(img, u, v, w, h);
    }

    private static void fillGoldBox(BufferedImage img, int u, int v, int dx, int dy, int dz) {
        int w = 2 * dz + 2 * dx;
        int h = dz + dy;
        fillRect(img, u, v, w, h, GOLD_BASE);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                if (py == 0 || py == h - 1 || px == 0 || px == w - 1) {
                    img.setRGB(u + px, v + py, GOLD_MID);
                } else if ((px + py) % 2 == 0) {
                    img.setRGB(u + px, v + py, GOLD_HI);
                } else {
                    img.setRGB(u + px, v + py, GOLD_BASE);
                }
            }
        }
    }

    private static void fillGemBox(BufferedImage img, int u, int v, int dx, int dy, int dz) {
        int w = 2 * dz + 2 * dx;
        int h = dz + dy;
        fillRect(img, u, v, w, h, GEM_BASE);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                if (px == 1 && py == 1) {
                    img.setRGB(u + px, v + py, GEM_HI);
                } else if (px == w - 1 || py == h - 1) {
                    img.setRGB(u + px, v + py, GEM_DARK);
                }
            }
        }
    }

    private static void fillLeatherBox(BufferedImage img, int u, int v, int dx, int dy, int dz) {
        int w = 2 * dz + 2 * dx;
        int h = dz + dy;
        fillRect(img, u, v, w, h, LEATHER_BASE);
        noiseRect(img, u, v, w, h, LEATHER_BASE, LEATHER_HI, LEATHER_DARK);
    }

    private static void fillCuffBox(BufferedImage img, int u, int v, int dx, int dy, int dz) {
        int w = 2 * dz + 2 * dx;
        int h = dz + dy;
        fillRect(img, u, v, w, h, GOLD_BASE);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                if (py == 0 || py == h - 1) {
                    img.setRGB(u + px, v + py, GOLD_MID);
                } else if (py == 1 || py == h - 2) {
                    img.setRGB(u + px, v + py, (px % 2 == 0) ? GOLD_HI : GOLD_BASE);
                } else {
                    img.setRGB(u + px, v + py, (px % 3 == 0) ? GOLD_HI : TEAL_DARK);
                }
            }
        }
    }

    private static void fillBootBox(BufferedImage img, int u, int v, int dx, int dy, int dz) {
        int w = 2 * dz + 2 * dx;
        int h = dz + dy;
        fillRect(img, u, v, w, h, LEATHER_BASE);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                if (py >= h - 2) {
                    img.setRGB(u + px, v + py, LEATHER_DEEP); // Thick boot sole
                } else if (py == 2) {
                    img.setRGB(u + px, v + py, (px % 3 == 0) ? GOLD_HI : LEATHER_DARK); // Gold strap
                } else {
                    img.setRGB(u + px, v + py, ((px + py) % 2 == 0) ? LEATHER_BASE : LEATHER_HI);
                }
            }
        }
    }

    private static void fillPurpleCloth(BufferedImage img, int x, int y, int w, int h) {
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int shade = ((px * 3 + py * 7) % 7);
                int col = PURPLE_BASE;
                if (shade == 0 || shade == 1) col = PURPLE_HI;
                else if (shade == 2 || shade == 3) col = PURPLE_MID;
                else if (shade == 4) col = PURPLE_DARK;
                else if (shade == 5) col = PURPLE_DEEP;
                img.setRGB(x + px, y + py, col);
            }
        }
    }

    private static void fillTealCloth(BufferedImage img, int x, int y, int w, int h) {
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                boolean grid = ((px + py) % 2 == 0);
                int base = grid ? TEAL_BASE : TEAL_LIGHT;
                if ((px * 5 + py * 11) % 13 == 0) base = TEAL_HI;
                else if ((px * 3 + py * 7) % 11 == 0) base = TEAL_DARK;
                img.setRGB(x + px, y + py, base);
            }
        }
    }

    private static void fillRect(BufferedImage img, int x, int y, int w, int h, int color) {
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                img.setRGB(x + px, y + py, color);
            }
        }
    }

    private static void noiseRect(BufferedImage img, int x, int y, int w, int h, int base, int light, int shadow) {
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int n = (px * 13 + py * 7) % 5;
                if (n == 0) img.setRGB(x + px, y + py, light);
                else if (n == 4) img.setRGB(x + px, y + py, shadow);
            }
        }
    }

    private static void setRow(BufferedImage img, int x, int y, int w, int color) {
        for (int px = 0; px < w; px++) {
            img.setRGB(x + px, y, color);
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedImage img = generate();
        File out = new File("src/main/resources/assets/vcoins/textures/entity/veloria_materials.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Generated texture: " + out.getAbsolutePath() + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }
}
