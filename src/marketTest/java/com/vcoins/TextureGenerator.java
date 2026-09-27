package com.vcoins;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class TextureGenerator {
    // Color Palette matching Image 2
    // Skin
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

    // Emerald Teal Cloth (Coat, Sleeves)
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
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

        // Fill background with transparent
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                img.setRGB(x, y, 0x00000000);
            }
        }

        // ==========================================
        // 1. HEAD (w=8, h=10, d=8) at (0, 0)
        // UV width: 32, height: 18
        // ==========================================
        int hx = 0, hy = 0;
        // Top: (hx+8, hy), 8x8
        fillRect(img, hx + 8, hy, 8, 8, SKIN_BASE);
        noiseRect(img, hx + 8, hy, 8, 8, SKIN_BASE, SKIN_LIGHT, SKIN_SHADOW);
        // Bottom: (hx+16, hy), 8x8
        fillRect(img, hx + 16, hy, 8, 8, SKIN_SHADOW);
        // Right face: (hx, hy+8), 8x10
        fillRect(img, hx, hy + 8, 8, 10, SKIN_BASE);
        noiseRect(img, hx, hy + 8, 8, 10, SKIN_BASE, SKIN_LIGHT, SKIN_SHADOW);
        // Left face: (hx+16, hy+8), 8x10
        fillRect(img, hx + 16, hy + 8, 8, 10, SKIN_BASE);
        noiseRect(img, hx + 16, hy + 8, 8, 10, SKIN_BASE, SKIN_LIGHT, SKIN_SHADOW);
        // Back face: (hx+24, hy+8), 8x10
        fillRect(img, hx + 24, hy + 8, 8, 10, SKIN_BASE);
        noiseRect(img, hx + 24, hy + 8, 8, 10, SKIN_BASE, SKIN_SHADOW, SKIN_DEEP);

        // FRONT FACE (hx+8, hy+8), 8x10:
        int fx = hx + 8, fy = hy + 8;
        fillRect(img, fx, fy, 8, 10, SKIN_BASE);
        // Row 0: forehead shadow
        setRow(img, fx, fy + 0, 8, SKIN_SHADOW);
        // Row 1: forehead skin
        setRow(img, fx, fy + 1, 8, SKIN_BASE);
        img.setRGB(fx + 3, fy + 1, SKIN_LIGHT);
        img.setRGB(fx + 4, fy + 1, SKIN_LIGHT);
        // Row 2: Unibrow across forehead (x: 0..7)
        setRow(img, fx, fy + 2, 8, BROW);
        img.setRGB(fx + 0, fy + 2, SKIN_SHADOW); // softer at edge
        img.setRGB(fx + 7, fy + 2, SKIN_SHADOW);
        // Row 3: Eyes & nose bridge
        img.setRGB(fx + 0, fy + 3, SKIN_BASE);
        img.setRGB(fx + 1, fy + 3, EYE_WHITE);
        img.setRGB(fx + 2, fy + 3, EYE_GREEN);
        img.setRGB(fx + 3, fy + 3, SKIN_SHADOW); // nose bridge
        img.setRGB(fx + 4, fy + 3, SKIN_SHADOW);
        img.setRGB(fx + 5, fy + 3, EYE_GREEN);
        img.setRGB(fx + 6, fy + 3, EYE_WHITE);
        img.setRGB(fx + 7, fy + 3, SKIN_BASE);
        // Row 4: Pupil bottom & cheek
        img.setRGB(fx + 1, fy + 4, EYE_WHITE);
        img.setRGB(fx + 2, fy + 4, EYE_PUPIL);
        img.setRGB(fx + 5, fy + 4, EYE_PUPIL);
        img.setRGB(fx + 6, fy + 4, EYE_WHITE);
        // Row 5, 6: Cheeks
        img.setRGB(fx + 0, fy + 5, SKIN_BASE);
        img.setRGB(fx + 1, fy + 5, SKIN_LIGHT);
        img.setRGB(fx + 6, fy + 5, SKIN_LIGHT);
        img.setRGB(fx + 7, fy + 5, SKIN_BASE);
        img.setRGB(fx + 0, fy + 6, SKIN_BASE);
        img.setRGB(fx + 1, fy + 6, SKIN_BASE);
        img.setRGB(fx + 6, fy + 6, SKIN_BASE);
        img.setRGB(fx + 7, fy + 6, SKIN_BASE);
        // Row 7: Under nose / philtrum
        setRow(img, fx, fy + 7, 8, SKIN_BASE);
        img.setRGB(fx + 3, fy + 7, SKIN_SHADOW);
        img.setRGB(fx + 4, fy + 7, SKIN_SHADOW);
        // Row 8: Mouth line
        setRow(img, fx, fy + 8, 8, SKIN_BASE);
        img.setRGB(fx + 2, fy + 8, MOUTH);
        img.setRGB(fx + 3, fy + 8, MOUTH);
        img.setRGB(fx + 4, fy + 8, MOUTH);
        img.setRGB(fx + 5, fy + 8, MOUTH);
        // Row 9: Chin
        setRow(img, fx, fy + 9, 8, SKIN_SHADOW);
        img.setRGB(fx + 3, fy + 9, SKIN_BASE);
        img.setRGB(fx + 4, fy + 9, SKIN_BASE);

        // ==========================================
        // 2. NOSE (w=2, h=4, d=2) at (32, 0)
        // UV width: 8, height: 6
        // ==========================================
        int nx = 32, ny = 0;
        // Top: (nx+2, ny), 2x2
        fillRect(img, nx + 2, ny, 2, 2, SKIN_LIGHT);
        // Bottom: (nx+4, ny), 2x2
        fillRect(img, nx + 4, ny, 2, 2, SKIN_DEEP);
        // Right: (nx, ny+2), 2x4
        fillRect(img, nx, ny + 2, 2, 4, SKIN_SHADOW);
        // Left: (nx+4, ny+2), 2x4
        fillRect(img, nx + 4, ny + 2, 2, 4, SKIN_SHADOW);
        // Back: (nx+6, ny+2), 2x4
        fillRect(img, nx + 6, ny + 2, 2, 4, SKIN_DEEP);
        // Front: (nx+2, ny+2), 2x4
        fillRect(img, nx + 2, ny + 2, 2, 4, SKIN_BASE);
        img.setRGB(nx + 2, ny + 2, SKIN_LIGHT);
        img.setRGB(nx + 3, ny + 2, SKIN_LIGHT);
        img.setRGB(nx + 2, ny + 5, SKIN_SHADOW); // nose tip shadow
        img.setRGB(nx + 3, ny + 5, SKIN_SHADOW);

        // ==========================================
        // 3. HOOD MAIN (w=10, h=11, d=10) at (0, 18)
        // UV width: 40, height: 21
        // ==========================================
        int hdx = 0, hdy = 18;
        // Top: (hdx+10, hdy), 10x10
        fillPurpleCloth(img, hdx + 10, hdy, 10, 10);
        // Bottom: (hdx+20, hdy), 10x10
        fillPurpleCloth(img, hdx + 20, hdy, 10, 10);
        // Back: (hdx+30, hdy+10), 10x11
        fillPurpleCloth(img, hdx + 30, hdy + 10, 10, 11);
        // Right side: (hdx, hdy+10), 10x11
        fillPurpleCloth(img, hdx, hdy + 10, 10, 11);
        drawStarEmblem(img, hdx + 4, hdy + 14); // 4-point star on right
        // Left side: (hdx+20, hdy+10), 10x11
        fillPurpleCloth(img, hdx + 20, hdy + 10, 10, 11);
        drawStarEmblem(img, hdx + 24, hdy + 14); // 4-point star on left

        // FRONT FACE: (hdx+10, hdy+10), 10x11
        // Hollow opening for the villager face, framed by antique gold diamond checker trim!
        int hfx = hdx + 10, hfy = hdy + 10;
        // Clear inside
        for (int y = 0; y < 11; y++) {
            for (int x = 0; x < 10; x++) {
                img.setRGB(hfx + x, hfy + y, 0x00000000);
            }
        }
        // Top hood rim (rows 0, 1)
        for (int x = 0; x < 10; x++) {
            img.setRGB(hfx + x, hfy + 0, (x % 2 == 0) ? PURPLE_BASE : PURPLE_MID);
            img.setRGB(hfx + x, hfy + 1, (x % 2 == 0) ? GOLD_HI : GOLD_BASE);
        }
        // Left & right golden borders framing the face
        for (int y = 1; y < 11; y++) {
            // Left edge
            img.setRGB(hfx + 0, hfy + y, PURPLE_BASE);
            img.setRGB(hfx + 1, hfy + y, (y % 2 == 0) ? GOLD_HI : GOLD_MID);
            // Right edge
            img.setRGB(hfx + 8, hfy + y, (y % 2 == 0) ? GOLD_HI : GOLD_MID);
            img.setRGB(hfx + 9, hfy + y, PURPLE_BASE);
        }

        // ==========================================
        // 4. HOOD PEAK (w=8, h=3, d=6) at (40, 18)
        // UV width: 28, height: 9
        // ==========================================
        int pkx = 40, pky = 18;
        fillPurpleCloth(img, pkx, pky, 28, 9);
        // Highlight along top ridge
        for (int x = 0; x < 8; x++) {
            img.setRGB(pkx + 6 + x, pky + 2, PURPLE_HI);
        }

        // ==========================================
        // 5. HOOD TIP (w=6, h=4, d=3) at (68, 18)
        // UV width: 18, height: 7
        // ==========================================
        fillPurpleCloth(img, 68, 18, 18, 7);

        // ==========================================
        // 6. MANTLE / COWL (w=13, h=5, d=9) at (0, 39)
        // UV width: 44, height: 14
        // ==========================================
        int mx = 0, my = 39;
        // Top: (mx+9, my), 13x9
        fillPurpleCloth(img, mx + 9, my, 13, 9);
        // Bottom: (mx+22, my), 13x9
        fillPurpleCloth(img, mx + 22, my, 13, 9);
        // Right side: (mx, my+9), 9x5
        fillPurpleCloth(img, mx, my + 9, 9, 5);
        // Left side: (mx+22, my+9), 9x5
        fillPurpleCloth(img, mx + 22, my + 9, 9, 5);
        // Back: (mx+31, my+9), 13x5
        fillPurpleCloth(img, mx + 31, my + 9, 13, 5);
        // Front: (mx+9, my+9), 13x5
        fillPurpleCloth(img, mx + 9, my + 9, 13, 5);

        // Gold trim along bottom of mantle on ALL 4 sides!
        for (int x = 0; x < 44; x++) {
            img.setRGB(mx + x, my + 13, (x % 2 == 0) ? GOLD_HI : GOLD_BASE);
            img.setRGB(mx + x, my + 12, (x % 2 == 0) ? GOLD_BASE : GOLD_MID);
        }
        // Front chest golden chain / embroidery loops
        int mfx = mx + 9, mfy = my + 9;
        img.setRGB(mfx + 4, mfy + 2, GOLD_HI);
        img.setRGB(mfx + 5, mfy + 2, GOLD_BASE);
        img.setRGB(mfx + 7, mfy + 2, GOLD_BASE);
        img.setRGB(mfx + 8, mfy + 2, GOLD_HI);
        img.setRGB(mfx + 5, mfy + 3, GOLD_HI);
        img.setRGB(mfx + 6, mfy + 3, GOLD_HI);
        img.setRGB(mfx + 7, mfy + 3, GOLD_HI);

        // ==========================================
        // 7. CLASP / BROOCH (w=3, h=3, d=1) at (44, 39)
        // UV width: 8, height: 4
        // ==========================================
        int cx = 44, cy = 39;
        fillRect(img, cx, cy, 8, 4, GOLD_BASE);
        // Front face: (cx+1, cy+1), 3x3
        int cfx = cx + 1, cfy = cy + 1;
        // Ring of gold with amber gem in center!
        img.setRGB(cfx + 0, cfy + 0, GOLD_SHADOW);
        img.setRGB(cfx + 1, cfy + 0, GOLD_HI);
        img.setRGB(cfx + 2, cfy + 0, GOLD_SHADOW);
        img.setRGB(cfx + 0, cfy + 1, GOLD_HI);
        img.setRGB(cfx + 1, cfy + 1, GEM_BASE); // Amber gem center!
        img.setRGB(cfx + 2, cfy + 1, GOLD_BASE);
        img.setRGB(cfx + 0, cfy + 2, GOLD_SHADOW);
        img.setRGB(cfx + 1, cfy + 2, GOLD_MID);
        img.setRGB(cfx + 2, cfy + 2, GOLD_DEEP);

        // ==========================================
        // 8. COAT / TORSO (w=9, h=19, d=6) at (0, 53)
        // UV width: 30, height: 25
        // ==========================================
        int tx = 0, ty = 53;
        // Top: (tx+6, ty), 9x6
        fillTealCloth(img, tx + 6, ty, 9, 6);
        // Bottom: (tx+15, ty), 9x6
        fillTealCloth(img, tx + 15, ty, 9, 6);
        // Right side: (tx, ty+6), 6x19
        fillTealCloth(img, tx, ty + 6, 6, 19);
        // Left side: (tx+15, ty+6), 6x19
        fillTealCloth(img, tx + 15, ty + 6, 6, 19);
        // Back: (tx+21, ty+6), 9x19
        fillTealCloth(img, tx + 21, ty + 6, 9, 19);

        // Front Face: (tx+6, ty+6), 9x19
        int tfx = tx + 6, tfy = ty + 6;
        fillTealCloth(img, tfx, tfy, 9, 19);
        // Split open coat showing center purple tunic & vertical gold borders
        for (int y = 0; y < 19; y++) {
            // Gold trim left flap
            img.setRGB(tfx + 3, tfy + y, (y % 2 == 0) ? GOLD_HI : GOLD_BASE);
            // Center purple under-tunic
            img.setRGB(tfx + 4, tfy + y, (y % 2 == 0) ? PURPLE_BASE : PURPLE_MID);
            // Gold trim right flap
            img.setRGB(tfx + 5, tfy + y, (y % 2 == 0) ? GOLD_HI : GOLD_BASE);
        }
        // Gold bottom hem band across coat
        for (int x = 0; x < 9; x++) {
            img.setRGB(tfx + x, tfy + 18, (x % 2 == 0) ? GOLD_HI : GOLD_BASE);
            img.setRGB(tfx + x, tfy + 17, (x % 2 == 0) ? GOLD_BASE : GOLD_MID);
        }
        // Geometric corner frets (staircase pixel motifs) from Image 2!
        // Left flap corner:
        img.setRGB(tfx + 1, tfy + 16, GOLD_HI);
        img.setRGB(tfx + 2, tfy + 16, GOLD_BASE);
        img.setRGB(tfx + 2, tfy + 15, GOLD_HI);
        // Right flap corner:
        img.setRGB(tfx + 7, tfy + 16, GOLD_HI);
        img.setRGB(tfx + 6, tfy + 16, GOLD_BASE);
        img.setRGB(tfx + 6, tfy + 15, GOLD_HI);

        // Continue bottom gold trim on sides and back:
        for (int x = 0; x < 30; x++) {
            img.setRGB(tx + x, ty + 24, (x % 2 == 0) ? GOLD_HI : GOLD_BASE);
        }

        // ==========================================
        // 9. CAPE (w=12, h=19, d=1) at (30, 53)
        // UV width: 26, height: 20
        // ==========================================
        int cpx = 30, cpy = 53;
        fillPurpleCloth(img, cpx, cpy, 26, 20);
        // Back face (outward): (cpx+13, cpy+1), 12x19
        int cbfx = cpx + 13, cbfy = cpy + 1;
        // Outer gold borders
        for (int y = 0; y < 19; y++) {
            img.setRGB(cbfx + 0, cbfy + y, (y % 2 == 0) ? GOLD_HI : GOLD_BASE);
            img.setRGB(cbfx + 11, cbfy + y, (y % 2 == 0) ? GOLD_HI : GOLD_BASE);
        }
        for (int x = 0; x < 12; x++) {
            img.setRGB(cbfx + x, cbfy + 18, (x % 2 == 0) ? GOLD_HI : GOLD_BASE);
            img.setRGB(cbfx + x, cbfy + 17, (x % 2 == 0) ? GOLD_BASE : GOLD_MID);
        }
        // Corner geometric motifs on cape hem
        img.setRGB(cbfx + 1, cbfy + 16, GOLD_HI);
        img.setRGB(cbfx + 2, cbfy + 16, GOLD_BASE);
        img.setRGB(cbfx + 2, cbfy + 15, GOLD_HI);
        img.setRGB(cbfx + 10, cbfy + 16, GOLD_HI);
        img.setRGB(cbfx + 9, cbfy + 16, GOLD_BASE);
        img.setRGB(cbfx + 9, cbfy + 15, GOLD_HI);

        // ==========================================
        // 10. BELT (w=10, h=2, d=7) at (56, 53)
        // UV width: 34, height: 9
        // ==========================================
        int bx = 56, by = 53;
        // Fill leather belt around waist
        fillRect(img, bx, by, 34, 9, LEATHER_BASE);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 34; x++) {
                img.setRGB(bx + x, by + y, (y % 2 == 0) ? LEATHER_HI : LEATHER_BASE);
            }
        }
        // Front face: (bx+7, by+7), 10x2
        int bfx = bx + 7, bfy = by + 7;
        // Gold buckle in center (x: 4, 5)
        img.setRGB(bfx + 3, bfy + 0, GOLD_HI);
        img.setRGB(bfx + 4, bfy + 0, GOLD_HI);
        img.setRGB(bfx + 5, bfy + 0, GOLD_HI);
        img.setRGB(bfx + 6, bfy + 0, GOLD_HI);
        img.setRGB(bfx + 3, bfy + 1, GOLD_BASE);
        img.setRGB(bfx + 4, bfy + 1, LEATHER_DARK); // buckle prong hole
        img.setRGB(bfx + 5, bfy + 1, LEATHER_DARK);
        img.setRGB(bfx + 6, bfy + 1, GOLD_BASE);

        // ==========================================
        // 11. FRONT SASH / TABARD (w=4, h=8, d=1) at (56, 62)
        // UV width: 10, height: 9
        // ==========================================
        int sx = 56, sy = 62;
        fillPurpleCloth(img, sx, sy, 10, 9);
        // Front face: (sx+1, sy+1), 4x8
        int sfx = sx + 1, sfy = sy + 1;
        // Left & right gold borders
        for (int y = 0; y < 8; y++) {
            img.setRGB(sfx + 0, sfy + y, (y % 2 == 0) ? GOLD_HI : GOLD_BASE);
            img.setRGB(sfx + 3, sfy + y, (y % 2 == 0) ? GOLD_HI : GOLD_BASE);
        }
        // 4-point gold star emblem on sash! (y: 3..5)
        img.setRGB(sfx + 1, sfy + 3, PURPLE_BASE);
        img.setRGB(sfx + 2, sfy + 3, PURPLE_BASE);
        img.setRGB(sfx + 1, sfy + 4, GOLD_HI);
        img.setRGB(sfx + 2, sfy + 4, GOLD_BASE);
        img.setRGB(sfx + 1, sfy + 5, PURPLE_BASE);
        img.setRGB(sfx + 2, sfy + 5, PURPLE_BASE);
        // Golden Chevron point at bottom (rows 6, 7)
        img.setRGB(sfx + 1, sfy + 6, GOLD_HI);
        img.setRGB(sfx + 2, sfy + 6, GOLD_HI);
        img.setRGB(sfx + 1, sfy + 7, GOLD_BASE);
        img.setRGB(sfx + 2, sfy + 7, GOLD_BASE);

        // ==========================================
        // 12. SATCHEL (w=3, h=5, d=4) at (66, 62)
        // UV width: 14, height: 9
        // ==========================================
        int stx = 66, sty = 62;
        fillRect(img, stx, sty, 14, 9, LEATHER_BASE);
        noiseRect(img, stx, sty, 14, 9, LEATHER_BASE, LEATHER_HI, LEATHER_DARK);
        // Front face: (stx+4, sty+4), 3x5
        int stfx = stx + 4, stfy = sty + 4;
        // Top flap with gold clasp
        img.setRGB(stfx + 0, stfy + 0, LEATHER_DARK);
        img.setRGB(stfx + 1, stfy + 0, LEATHER_DARK);
        img.setRGB(stfx + 2, stfy + 0, LEATHER_DARK);
        img.setRGB(stfx + 0, stfy + 1, GOLD_BASE);
        img.setRGB(stfx + 1, stfy + 1, GOLD_HI); // Gold clasp
        img.setRGB(stfx + 2, stfy + 1, GOLD_BASE);

        // ==========================================
        // 13. ARMS: SLEEVES, HANDS, CUFFS
        // Left Sleeve: (0, 78), 18x8
        // Right Sleeve: (18, 78), 18x8
        // Hands: (36, 78), 10x6
        // Left Cuff: (46, 78), 14x8
        // Right Cuff: (60, 78), 14x8
        // ==========================================
        fillTealCloth(img, 0, 78, 18, 8);
        fillTealCloth(img, 18, 78, 18, 8);

        // Hands: skin tone
        fillRect(img, 36, 78, 10, 6, SKIN_BASE);
        noiseRect(img, 36, 78, 10, 6, SKIN_BASE, SKIN_LIGHT, SKIN_SHADOW);
        // Finger crease
        img.setRGB(36 + 4, 78 + 3, SKIN_SHADOW);
        img.setRGB(36 + 5, 78 + 3, SKIN_SHADOW);

        // Cuffs with rich golden fret / teeth pattern!
        drawCuff(img, 46, 78, 14, 8);
        drawCuff(img, 60, 78, 14, 8);

        // ==========================================
        // 14. LEGS & BOOTS
        // Left Leg: (0, 86), 16x14
        // Left Boot: (16, 86), 18x10
        // Right Leg: (34, 86), 16x14
        // Right Boot: (52, 86), 18x10
        // ==========================================
        fillTealCloth(img, 0, 86, 16, 14);
        fillTealCloth(img, 34, 86, 16, 14);

        drawBoot(img, 16, 86, 18, 10);
        drawBoot(img, 52, 86, 18, 10);

        return img;
    }

    private static void drawCuff(BufferedImage img, int u, int v, int w, int h) {
        fillRect(img, u, v, w, h, GOLD_BASE);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (y == 0 || y == h - 1) {
                    img.setRGB(u + x, v + y, GOLD_MID);
                } else if (y == 1 || y == h - 2) {
                    img.setRGB(u + x, v + y, (x % 2 == 0) ? GOLD_HI : GOLD_BASE);
                } else {
                    // Middle teeth / fret pattern
                    img.setRGB(u + x, v + y, (x % 3 == 0) ? GOLD_HI : TEAL_DARK);
                }
            }
        }
    }

    private static void drawBoot(BufferedImage img, int u, int v, int w, int h) {
        fillRect(img, u, v, w, h, LEATHER_BASE);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (y < 2) {
                    // Folded boot cuff at top
                    img.setRGB(u + x, v + y, (y == 0) ? LEATHER_HI : LEATHER_BASE);
                } else if (y == 3) {
                    // Gold strap/buckle
                    img.setRGB(u + x, v + y, (x % 3 == 0) ? GOLD_HI : LEATHER_DARK);
                } else if (y >= h - 2) {
                    // Thick dark leather sole
                    img.setRGB(u + x, v + y, LEATHER_DEEP);
                } else {
                    img.setRGB(u + x, v + y, LEATHER_BASE);
                }
            }
        }
    }

    private static void drawStarEmblem(BufferedImage img, int cx, int cy) {
        // 4-point diamond star
        img.setRGB(cx, cy - 2, GOLD_HI);
        img.setRGB(cx, cy - 1, GOLD_HI);
        img.setRGB(cx - 2, cy, GOLD_HI);
        img.setRGB(cx - 1, cy, GOLD_HI);
        img.setRGB(cx, cy, GOLD_BASE); // center
        img.setRGB(cx + 1, cy, GOLD_HI);
        img.setRGB(cx + 2, cy, GOLD_HI);
        img.setRGB(cx, cy + 1, GOLD_HI);
        img.setRGB(cx, cy + 2, GOLD_HI);
        // Outer dark accents
        img.setRGB(cx - 1, cy - 1, GOLD_SHADOW);
        img.setRGB(cx + 1, cy - 1, GOLD_SHADOW);
        img.setRGB(cx - 1, cy + 1, GOLD_SHADOW);
        img.setRGB(cx + 1, cy + 1, GOLD_SHADOW);
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
                // Subtle woven textile grid
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
