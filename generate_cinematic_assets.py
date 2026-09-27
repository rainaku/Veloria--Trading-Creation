import math
from PIL import Image
import os

out_dir = "src/main/resources/assets/vcoins/textures/gui"
os.makedirs(out_dir, exist_ok=True)

# 1. Tiered Card Hover Glows (256x256)
def generate_card_hover_glow(filename, tier):
    w, h = 256, 256
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pixels = img.load()
    cx, cy = w / 2.0, h / 2.0
    
    # 4:5 card aspect ratio inside 256x256: 120 x 150
    box_w = 120.0
    box_h = 150.0
    corner_r = 10.0
    
    for py in range(h):
        for px in range(w):
            dx = abs(px - cx)
            dy = abs(py - cy)
            
            qx = max(0.0, dx - (box_w / 2.0 - corner_r))
            qy = max(0.0, dy - (box_h / 2.0 - corner_r))
            dist_corner = math.sqrt(qx * qx + qy * qy) - corner_r
            
            if dx <= box_w / 2.0 - corner_r and dy <= box_h / 2.0:
                dist = dy - box_h / 2.0
            elif dy <= box_h / 2.0 - corner_r and dx <= box_w / 2.0:
                dist = dx - box_w / 2.0
            else:
                dist = dist_corner
                
            if dist < 0:
                # Soft inner rim highlight
                intensity = math.exp(- (-dist / 6.0) ** 2) * 0.50
            else:
                # Smooth outer Gaussian falloff
                intensity = math.exp(- (dist / 18.0) ** 1.8)
                
            if intensity < 0.005:
                continue
                
            if tier == "mythic":
                # Searing white-hot inner rim -> blazing lava orange -> crimson
                if intensity > 0.65:
                    t = (intensity - 0.65) / 0.35
                    r, g, b = 255, int(230 * (1 - t) + 255 * t), int(120 * (1 - t) + 240 * t)
                elif intensity > 0.25:
                    t = (intensity - 0.25) / 0.40
                    r, g, b = 255, int(80 * (1 - t) + 230 * t), int(10 * (1 - t) + 120 * t)
                else:
                    t = intensity / 0.25
                    r, g, b = int(220 * (1 - t) + 255 * t), int(15 * (1 - t) + 80 * t), 5
                alpha = int(math.pow(intensity, 0.85) * 240)
            elif tier == "legend":
                # Pure divine white-gold inner rim -> solar gold -> amber
                if intensity > 0.60:
                    t = (intensity - 0.60) / 0.40
                    r, g, b = 255, 255, int(160 * (1 - t) + 255 * t)
                elif intensity > 0.22:
                    t = (intensity - 0.22) / 0.38
                    r, g, b = 255, int(200 * (1 - t) + 255 * t), int(35 * (1 - t) + 160 * t)
                else:
                    t = intensity / 0.22
                    r, g, b = int(235 * (1 - t) + 255 * t), int(140 * (1 - t) + 200 * t), int(15 * (1 - t) + 35 * t)
                alpha = int(math.pow(intensity, 0.85) * 230)
            else:
                # Cyan/Gold crystalline rim for normal/rare/epic
                if intensity > 0.60:
                    t = (intensity - 0.60) / 0.40
                    r, g, b = int(180 * (1 - t) + 255 * t), 255, 255
                elif intensity > 0.25:
                    t = (intensity - 0.25) / 0.35
                    r, g, b = int(60 * (1 - t) + 180 * t), int(210 * (1 - t) + 255 * t), 255
                else:
                    t = intensity / 0.25
                    r, g, b = int(20 * (1 - t) + 60 * t), int(140 * (1 - t) + 210 * t), int(220 * (1 - t) + 255 * t)
                alpha = int(math.pow(intensity, 0.85) * 210)
                
            pixels[px, py] = (r, g, b, min(255, alpha))
            
    img.save(filename, "PNG")
    print(f"Generated {filename}")

# 2. Cinematic Sunburst & Lens Flare (512x512)
def generate_cinematic_sunburst(filename):
    w, h = 512, 512
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pixels = img.load()
    cx, cy = w / 2.0, h / 2.0
    max_r = w / 2.0 - 4.0
    
    for py in range(h):
        for px in range(w):
            dx = float(px - cx)
            dy = float(py - cy)
            dist = math.sqrt(dx * dx + dy * dy)
            if dist >= max_r:
                continue
                
            angle = math.atan2(dy, dx)
            
            ray1 = (0.5 + 0.5 * math.cos(angle * 12.0)) ** 6.0
            ray2 = (0.5 + 0.5 * math.cos(angle * 24.0 + 0.5)) ** 4.0
            ray3 = (0.5 + 0.5 * math.cos(angle * 4.0)) ** 14.0
            
            ray_combined = ray1 * 0.40 + ray2 * 0.20 + ray3 * 0.70
            core = math.exp(- (dist / 36.0) ** 2) * 1.6
            
            edge_cutoff = math.pow(max(0.0, 1.0 - (dist / max_r)), 2.2)
            ray_fade = math.exp(- dist / 110.0) * edge_cutoff
            
            total = (core + (ray_combined * ray_fade)) * edge_cutoff
            total = min(1.0, total)
            if total < 0.004:
                continue
                
            if total > 0.55:
                t = (total - 0.55) / 0.45
                r, g, b = 255, 255, int(190 * (1 - t) + 255 * t)
            elif total > 0.20:
                t = (total - 0.20) / 0.35
                r, g, b = 255, int(195 * (1 - t) + 255 * t), int(40 * (1 - t) + 190 * t)
            else:
                t = total / 0.20
                r, g, b = int(240 * (1 - t) + 255 * t), int(130 * (1 - t) + 195 * t), int(10 * (1 - t) + 40 * t)
                
            alpha = int(math.pow(total, 0.85) * 255)
            pixels[px, py] = (r, g, b, min(255, alpha))
            
    img.save(filename, "PNG")
    print(f"Generated {filename}")

# 3. Cinematic Shockwave Ring (512x512) - High Luminescence
def generate_cinematic_shockwave(filename, is_mythic):
    w, h = 512, 512
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pixels = img.load()
    cx, cy = w / 2.0, h / 2.0
    ring_radius = 165.0
    ring_thickness = 38.0
    
    for py in range(h):
        for px in range(w):
            dx = float(px - cx)
            dy = float(py - cy)
            dist = math.sqrt(dx * dx + dy * dy)
            dr = abs(dist - ring_radius)
            if dr > ring_thickness * 2.2:
                continue
                
            if dist >= ring_radius:
                wave = math.exp(- (dr / (ring_thickness * 0.45)) ** 2)
            else:
                wave = math.exp(- (dr / (ring_thickness * 0.85)) ** 1.8)
                
            angle = math.atan2(dy, dx)
            ripple = 0.90 + 0.10 * math.cos(angle * 12.0)
            intensity = min(1.0, wave * ripple)
            
            if intensity < 0.005:
                continue
                
            if is_mythic:
                if intensity > 0.60:
                    t = (intensity - 0.60) / 0.40
                    r, g, b = 255, int(225 * (1 - t) + 255 * t), int(110 * (1 - t) + 240 * t)
                elif intensity > 0.20:
                    t = (intensity - 0.20) / 0.40
                    r, g, b = 255, int(85 * (1 - t) + 225 * t), int(10 * (1 - t) + 110 * t)
                else:
                    t = intensity / 0.20
                    r, g, b = int(220 * (1 - t) + 255 * t), int(20 * (1 - t) + 85 * t), 5
            else:
                if intensity > 0.55:
                    t = (intensity - 0.55) / 0.45
                    r, g, b = 255, 255, int(160 * (1 - t) + 255 * t)
                elif intensity > 0.18:
                    t = (intensity - 0.18) / 0.37
                    r, g, b = 255, int(205 * (1 - t) + 255 * t), int(40 * (1 - t) + 160 * t)
                else:
                    t = intensity / 0.18
                    r, g, b = int(235 * (1 - t) + 255 * t), int(145 * (1 - t) + 205 * t), int(15 * (1 - t) + 40 * t)
                    
            alpha = int(math.pow(intensity, 0.85) * 255)
            pixels[px, py] = (r, g, b, min(255, alpha))
            
    img.save(filename, "PNG")
    print(f"Generated {filename}")

# 4. Cinematic Vignette (512x512)
def generate_cinematic_vignette(filename):
    w, h = 512, 512
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pixels = img.load()
    cx, cy = w / 2.0, h / 2.0
    max_d = math.sqrt(cx * cx + cy * cy)
    
    for py in range(h):
        for px in range(w):
            d = math.sqrt((px - cx) ** 2 + (py - cy) ** 2) / max_d
            alpha = int(math.pow(d, 1.7) * 230)
            pixels[px, py] = (3, 3, 8, min(255, alpha))
            
    img.save(filename, "PNG")
    print(f"Generated {filename}")

if __name__ == "__main__":
    generate_card_hover_glow(os.path.join(out_dir, "card_hover_mythic.png"), "mythic")
    generate_card_hover_glow(os.path.join(out_dir, "card_hover_legend.png"), "legend")
    generate_card_hover_glow(os.path.join(out_dir, "card_hover_regular.png"), "regular")
    generate_cinematic_sunburst(os.path.join(out_dir, "cinematic_sunburst.png"))
    generate_cinematic_shockwave(os.path.join(out_dir, "cinematic_shockwave_mythic.png"), is_mythic=True)
    generate_cinematic_shockwave(os.path.join(out_dir, "cinematic_shockwave_legend.png"), is_mythic=False)
    generate_cinematic_vignette(os.path.join(out_dir, "cinematic_vignette.png"))
