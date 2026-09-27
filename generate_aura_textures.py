import math
from PIL import Image
import os

def generate_aura_beam(filename_up, filename_down, is_mythic, width=512, height=512):
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    pixels = img.load()

    cx = width / 2.0
    base_y = height - 1.0  # Base contact flare at the very bottom edge of image
    top_y = 8.0
    beam_span = base_y - top_y
    R = 86.0  # Half-width of the beam column at 512px

    for py in range(height):
        y = float(py)
        if y < top_y or y > base_y:
            continue
        v = (base_y - y) / beam_span  # 0 at base, 1 at top
        if v < 0.0 or v > 1.0:
            continue

        # Vertical falloff: strong through lower and mid, tapering smoothly near top
        v_fade = math.pow(max(0.0, 1.0 - v), 1.15)
        if v > 0.88:
            v_fade *= math.pow((1.0 - v) / 0.12, 1.3)

        for px in range(width):
            x = float(px)
            u = (x - cx) / R
            abs_u = abs(u)

            # 1. Main Dense Luminous Column (between boundary lines abs_u <= 1.0)
            if abs_u <= 1.0:
                col_cross = 0.5 * (1.0 + math.cos(abs_u * math.pi * 0.90))
                column_body = 0.40 + 0.60 * col_cross

                # Broad incandescent warm core
                core_sigma = max(0.12, 0.46 * (1.0 - v * 0.65))
                core_val = math.exp(- (u / core_sigma) ** 2) * math.pow(max(0.0, 1.0 - v / 0.82), 1.1)

                # Intense white-hot central spire
                spire_sigma = max(0.06, 0.22 * (1.0 - v * 0.75))
                spire_val = math.exp(- (u / spire_sigma) ** 2) * math.pow(max(0.0, 1.0 - v / 0.65), 1.35)

                inner_beam = column_body * 0.65 + core_val * 0.45 + spire_val * 0.50
            else:
                inner_beam = 0.0
                core_val = 0.0
                spire_val = 0.0

            # 2. Outer Soft Gaussian Bloom (extends smoothly outside boundary lines)
            outer_bloom = math.exp(- (abs_u / 1.38) ** 2) * 0.52 * v_fade

            # 3. Two Crisp Vertical Boundary Lines (Key feature of Image 2!)
            dist_to_boundary = abs(abs_u - 1.0)
            boundary_intensity = math.exp(- (dist_to_boundary / 0.038) ** 2)
            boundary_fade = math.pow(max(0.0, 1.0 - v * 0.88), 1.05)
            boundary_line = boundary_intensity * boundary_fade * 0.98

            # 4. Base Radiant Dome & Contact Flare (where beam meets card)
            dx_base = (x - cx) / R
            dy_base = (base_y - y) / (R * 0.50)
            d_base = math.sqrt(dx_base * dx_base + dy_base * dy_base)
            base_dome = math.exp(- (d_base / 0.95) ** 2) * 0.90

            # Horizontal contact line at the very bottom
            base_line = math.exp(- ((base_y - y) / 5.0) ** 2) * math.exp(- (abs_u / 1.15) ** 2) * 1.0

            # 5. Volumetric Fanning God-Rays (soft sunbeam shafts fanning ~60 degrees)
            dx_px = x - cx
            dy_px = max(1.0, base_y - y)
            theta = math.atan2(dx_px, dy_px)
            r_px = math.sqrt(dx_px * dx_px + dy_px * dy_px)
            ray_val = 0.0
            if abs(theta) < 1.25 and r_px > 8.0:
                ray_pattern = (0.5 + 0.5 * math.cos(theta * 9.0)) ** 3.5
                ray_fade = math.exp(- r_px / 190.0) * math.pow(max(0.0, 1.0 - v * 0.65), 1.1)
                ray_val = ray_pattern * ray_fade * 0.45

            # Total luminance
            total = (inner_beam * v_fade) + outer_bloom + boundary_line + base_dome + base_line + ray_val
            total = min(1.0, total)

            if total < 0.005:
                continue

            # Core factor drives color grading (white-hot center -> saturated mid -> atmospheric glow)
            core_factor = (spire_val * 0.55 + core_val * 0.40) * v_fade + boundary_line * 0.45 + (base_dome + base_line) * 0.40
            core_factor = min(1.0, max(0.0, core_factor))

            if is_mythic:
                # Mythic Volcanic Magma:
                # Blinding incandescent white-gold core -> blazing solar orange -> deep volcanic crimson
                if core_factor > 0.45:
                    t = (core_factor - 0.45) / 0.55
                    r = 255
                    g = int(210 * (1.0 - t) + 255 * t)
                    b = int(75 * (1.0 - t) + 235 * t)
                elif core_factor > 0.14:
                    t = (core_factor - 0.14) / 0.31
                    r = 255
                    g = int(85 * (1.0 - t) + 210 * t)
                    b = int(5 * (1.0 - t) + 75 * t)
                else:
                    t = core_factor / 0.14
                    r = int(220 * (1.0 - t) + 255 * t)
                    g = int(18 * (1.0 - t) + 85 * t)
                    b = int(2 * (1.0 - t) + 5 * t)

                alpha = int(math.pow(total, 0.88) * 255.0)
            else:
                # Legend Divine Imperial Gold:
                # Pure incandescent white core -> brilliant solar gold -> royal amber
                if core_factor > 0.40:
                    t = (core_factor - 0.40) / 0.60
                    r = 255
                    g = int(235 * (1.0 - t) + 255 * t)
                    b = int(130 * (1.0 - t) + 248 * t)
                elif core_factor > 0.12:
                    t = (core_factor - 0.12) / 0.28
                    r = 255
                    g = int(170 * (1.0 - t) + 235 * t)
                    b = int(20 * (1.0 - t) + 130 * t)
                else:
                    t = core_factor / 0.12
                    r = int(230 * (1.0 - t) + 255 * t)
                    g = int(130 * (1.0 - t) + 170 * t)
                    b = int(5 * (1.0 - t) + 20 * t)

                alpha = int(math.pow(total, 0.88) * 252.0)

            if alpha > 0:
                pixels[px, py] = (r, g, b, min(255, alpha))

    # Save upward-pointing beam
    img.save(filename_up, "PNG")
    print(f"Generated {filename_up}")

    # Generate downward-pointing beam (pre-flipped vertically for zero-cost runtime blitting)
    img_down = img.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    img_down.save(filename_down, "PNG")
    print(f"Generated {filename_down}")

def generate_radial_halo(filename, is_mythic, width=256, height=256):
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    pixels = img.load()
    cx = width / 2.0
    cy = height / 2.0
    max_r = width / 2.0 - 4.0

    for py in range(height):
        for px in range(width):
            dx = (px - cx)
            dy = (py - cy) * 0.80  # Stretch to match 4:5 card aspect ratio
            dist = math.sqrt(dx * dx + dy * dy)
            if dist > max_r:
                continue

            t = dist / max_r
            alpha_factor = math.exp(- (dist / (max_r * 0.48)) ** 2)

            if is_mythic:
                r = int(255 * (1.0 - t * 0.20))
                g = int(125 * (1.0 - t) + 20)
                b = int(15 * (1.0 - t))
                alpha = int(alpha_factor * 180.0)
            else:
                r = 255
                g = int(220 * (1.0 - t * 0.20))
                b = int(45 * (1.0 - t))
                alpha = int(alpha_factor * 170.0)

            if alpha > 0:
                pixels[px, py] = (r, g, b, min(255, alpha))

    img.save(filename, "PNG")
    print(f"Generated {filename}")

if __name__ == "__main__":
    out_dir = "src/main/resources/assets/vcoins/textures/gui"
    os.makedirs(out_dir, exist_ok=True)
    generate_aura_beam(
        os.path.join(out_dir, "aura_beam_mythic_up.png"),
        os.path.join(out_dir, "aura_beam_mythic_down.png"),
        is_mythic=True
    )
    generate_aura_beam(
        os.path.join(out_dir, "aura_beam_legend_up.png"),
        os.path.join(out_dir, "aura_beam_legend_down.png"),
        is_mythic=False
    )
    # Also save aura_beam_mythic.png and aura_beam_legend.png as aliases to up
    img_m = Image.open(os.path.join(out_dir, "aura_beam_mythic_up.png"))
    img_m.save(os.path.join(out_dir, "aura_beam_mythic.png"))
    img_l = Image.open(os.path.join(out_dir, "aura_beam_legend_up.png"))
    img_l.save(os.path.join(out_dir, "aura_beam_legend.png"))

    generate_radial_halo(os.path.join(out_dir, "aura_halo_mythic.png"), is_mythic=True)
    generate_radial_halo(os.path.join(out_dir, "aura_halo_legend.png"), is_mythic=False)
