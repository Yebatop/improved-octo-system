# Draws the effect sprite atlas (white shapes on transparent, tinted in game) and the Java enum that indexes it:
#   python3 tools/fx/atlas.py src/main/resources/assets/skirmish/textures/fx/atlas.png src/main/java/dev/skirmish/fx/FxSprite.java
# Needs numpy and pillow. Every sprite is drawn at twice its size and scaled down for smooth edges.
import sys, math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

CELL = 128
ATLAS = 2048
SS = 2
rng = np.random.default_rng(1234)

def grid(w, h):
    y, x = np.mgrid[0:h, 0:w]
    return (x + 0.5) / w * 2 - 1, (y + 0.5) / h * 2 - 1

def clamp(a):
    return np.clip(a, 0, 1)

def smooth(e0, e1, x):
    span = np.where(np.abs(np.asarray(e1 - e0, dtype=float)) < 1e-6, 1e-6, e1 - e0)
    t = clamp((x - e0) / span)
    return t * t * (3 - 2 * t)

def value_noise(w, h, scale, seed, octaves=4):
    r = np.random.default_rng(seed)
    out = np.zeros((h, w))
    amp = 1.0
    total = 0
    for o in range(octaves):
        gw = int(scale * 2 ** o) + 2
        g = r.random((gw, gw))
        img = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        out += np.asarray(img) / 255.0 * amp
        total += amp
        amp *= 0.5
    return out / total

def rgba(lum, alpha):
    lum = clamp(lum)
    alpha = clamp(alpha)
    a = np.dstack([lum, lum, lum, alpha])
    return Image.fromarray((a * 255).astype(np.uint8), 'RGBA')

def white(alpha):
    return rgba(np.ones_like(alpha), alpha)

# ---- sprite painters: (w, h) in pixels at SS scale -> RGBA image ----

def glow(w, h):
    x, y = grid(w, h)
    d = np.sqrt(x * x + y * y)
    return white(np.exp(-(d / 0.42) ** 2) * smooth(1.0, 0.85, d))

def dust(w, h):
    x, y = grid(w, h)
    d = np.sqrt(x * x + y * y)
    return white(np.exp(-(d / 0.22) ** 2) + 0.25 * np.exp(-(d / 0.6) ** 2) * smooth(1, 0.8, d))

def flare(w, h):
    x, y = grid(w, h)
    d = np.sqrt(x * x + y * y)
    core = np.exp(-(d / 0.12) ** 2)
    halo = 0.45 * np.exp(-(d / 0.45) ** 2)
    streak = 0.7 * np.exp(-(y / 0.035) ** 2) * np.exp(-(np.abs(x) / 0.75) ** 1.5)
    rays = 0.5 * np.exp(-(x / 0.03) ** 2) * np.exp(-(np.abs(y) / 0.5) ** 1.5)
    return white((core + halo + streak + rays) * smooth(1.0, 0.9, np.maximum(np.abs(x), np.abs(y))))

def star(points, sharp=0.06, inner=0.18):
    def paint(w, h):
        x, y = grid(w, h)
        d = np.sqrt(x * x + y * y) + 1e-6
        a = np.arctan2(y, x)
        k = np.cos(a * points / 2) ** 2
        ray = np.exp(-(d * (1 - k) / sharp) ** 1.4) * smooth(0.98, 0.3, d)
        core = np.exp(-(d / inner) ** 2)
        halo = 0.35 * np.exp(-(d / 0.5) ** 2)
        return white((ray + core + halo) * smooth(1, 0.9, d))
    return paint

def starburst(w, h):
    x, y = grid(w, h)
    d = np.sqrt(x * x + y * y) + 1e-6
    a = np.arctan2(y, x)
    total = np.zeros_like(d)
    for i in range(14):
        ang = i * 2 * math.pi / 14 + rng.uniform(-0.1, 0.1)
        length = rng.uniform(0.55, 0.98)
        da = np.angle(np.exp(1j * (a - ang)))
        total += np.exp(-(da * d / 0.025) ** 2) * smooth(length, length * 0.4, d)
    return white(total + np.exp(-(d / 0.18) ** 2) + 0.3 * np.exp(-(d / 0.45) ** 2))

def spark(w, h):
    x, y = grid(w, h)
    head = smooth(1.0, 0.75, x)
    tail = smooth(-1.0, 0.7, x)
    width = 0.08 + 0.12 * (x + 1) / 2
    body = np.exp(-(y / width) ** 2) * tail * head
    tip = np.exp(-((x - 0.72) / 0.12) ** 2 - (y / 0.2) ** 2)
    lum = 0.6 + 0.4 * (x + 1) / 2
    return rgba(lum + tip, clamp(body + tip * 0.8))

def ring(width):
    def paint(w, h):
        x, y = grid(w, h)
        d = np.sqrt(x * x + y * y)
        return white(np.exp(-((d - 0.85) / width) ** 2))
    return paint

def shockwave(w, h):
    x, y = grid(w, h)
    d = np.sqrt(x * x + y * y)
    edge = np.exp(-((d - 0.86) / 0.05) ** 2)
    inner = 0.45 * smooth(0.2, 0.86, d) * smooth(0.95, 0.86, d)
    noise = value_noise(w, h, 6, 7, 3)
    return white((edge + inner) * (0.75 + 0.25 * noise))

def halo(w, h):
    x, y = grid(w, h)
    d = np.sqrt(x * x + y * y)
    band = np.exp(-((d - 0.78) / 0.09) ** 2)
    highlight = 0.6 * np.exp(-((d - 0.78) / 0.03) ** 2) * (0.5 + 0.5 * np.cos(np.arctan2(y, x) * 2))
    return rgba(0.7 + highlight, band + highlight * 0.5)

def flame_frame(i, frames, soul=False):
    def paint(w, h):
        x, y = grid(w, h)
        t = i / frames
        yy = (y + 1) / 2  # 0 top, 1 bottom
        big = value_noise(w, h * 4, 3, 99 if not soul else 77, 5)
        n1 = np.roll(big, -int(t * h * 3), axis=0)[:h]
        n2 = np.roll(value_noise(w, h * 4, 6, 123 if not soul else 321, 4), -int(t * h * 3), axis=0)[:h]
        rise = np.clip(1 - yy, 0, 1)
        xd = x + (n1 - 0.5) * (0.55 if not soul else 0.75) * rise ** 1.3
        prof = np.sqrt(np.clip((yy - 0.02) / 0.98, 0, 1))
        bottom = np.sqrt(np.clip(1 - ((yy - 0.72) / 0.28) ** 2, 0, 1))
        r = (0.5 if not soul else 0.4) * prof * np.where(yy > 0.72, bottom, 1.0)
        body = smooth(r + 0.04, r - 0.2, np.abs(xd))
        breakup = smooth(0.15, 0.55, yy + (n2 - 0.5) * 0.7)
        a = body * breakup
        lum = 0.45 + 0.55 * smooth(r, 0.0, np.abs(xd)) * smooth(0.25, 0.85, yy)
        if soul:
            eyes = np.exp(-((((np.abs(xd) - 0.1) / 0.045) ** 2) + ((yy - 0.62) / 0.05) ** 2))
            a = a * (1 - 0.8 * eyes)
        return rgba(lum, a)
    return paint

def smoke(seed):
    def paint(w, h):
        x, y = grid(w, h)
        n = value_noise(w, h, 4, seed, 5)
        d = np.sqrt(x * x + y * y) + (n - 0.5) * 0.45
        a = smooth(0.95, 0.25, d) * (0.45 + 0.55 * n)
        return rgba(0.75 + 0.25 * n, a * 0.85)
    return paint

def petal(variant):
    def paint(w, h):
        x, y = grid(w, h)
        t = (1 - y) / 2  # 0 base (bottom), 1 tip (top)
        width = (0.4 if variant == 0 else 0.33) * np.sin(np.clip(t, 0, 1) * math.pi * 0.92) ** 0.75
        tip = 0.97 - (0.16 if variant == 0 else 0.06) * np.clip(1 - np.abs(x) / 0.13, 0, 1)
        a = smooth(width + 0.02, width - 0.02, np.abs(x)) * smooth(tip + 0.01, tip - 0.02, t) * smooth(0.0, 0.04, t)
        lum = 0.72 + 0.28 * smooth(0.0, 0.8, t) - 0.12 * np.exp(-(x / 0.035) ** 2) * smooth(0.6, 0.0, t)
        return rgba(lum, a)
    return paint

def snowflake(variant):
    def paint(w, h):
        img = Image.new('L', (w, h), 0)
        dr = ImageDraw.Draw(img)
        cx, cy = w / 2, h / 2
        R = w * 0.44
        lw = max(2, int(w / 40))
        for i in range(6):
            a = i * math.pi / 3
            ex, ey = cx + math.cos(a) * R, cy + math.sin(a) * R
            dr.line([(cx, cy), (ex, ey)], fill=255, width=lw)
            for f in ((0.45, 0.32), (0.7, 0.22)) if variant == 0 else ((0.35, 0.25), (0.6, 0.3), (0.82, 0.15)):
                bx, by = cx + math.cos(a) * R * f[0], cy + math.sin(a) * R * f[0]
                for s in (-1, 1):
                    b = a + s * math.pi / 4
                    dr.line([(bx, by), (bx + math.cos(b) * R * f[1], by + math.sin(b) * R * f[1])], fill=255, width=lw)
        base = np.asarray(img) / 255.0
        bloom = np.asarray(img.filter(ImageFilter.GaussianBlur(w / 30))) / 255.0
        return white(clamp(base + bloom * 0.8))
    return paint

def heart(w, h):
    """A clean heart from its signed distance (rounded lobes, a sharp tip), soft-edged by a pixel and a half."""
    x, y = grid(w, h)
    s = 1.49
    px = np.abs(x) / s
    py = (-y + 0.8) / s
    lobe = np.sqrt((px - 0.25) ** 2 + (py - 0.75) ** 2) - math.sqrt(2.0) / 4.0
    m = np.maximum(px + py, 0.0) * 0.5
    tip = np.sqrt(np.minimum(px ** 2 + (py - 1.0) ** 2, (px - m) ** 2 + (py - m) ** 2)) * np.sign(px - py)
    d = np.where(px + py > 1.0, lobe, tip)
    e = 1.5 * 2.0 / w / s
    a = smooth(e, -e, d)
    hl = 0.35 * np.exp(-(((x + 0.38) / 0.16) ** 2 + ((y + 0.3) / 0.13) ** 2))
    return rgba(0.82 + hl + 0.18 * smooth(0.6, -0.4, y), a)

def shard(seed):
    def paint(w, h):
        r = np.random.default_rng(seed)
        n = int(r.integers(3, 6))
        base = r.uniform(0, 2 * math.pi)
        angs = [base + k * 2 * math.pi / n + r.uniform(-0.35, 0.35) for k in range(n)]
        pts = [((0.5 + math.cos(a) * r.uniform(0.32, 0.47)) * w, (0.5 + math.sin(a) * r.uniform(0.32, 0.47)) * h) for a in angs]
        img = Image.new('L', (w, h), 0)
        ImageDraw.Draw(img).polygon(pts, fill=255)
        edge = Image.new('L', (w, h), 0)
        ImageDraw.Draw(edge).line(pts + [pts[0]], fill=255, width=max(2, w // 45))
        a = np.asarray(img) / 255.0
        e = np.asarray(edge) / 255.0
        x, y = grid(w, h)
        shine = 0.6 * smooth(0.22, 0.0, np.abs(x + y * 0.6 + 0.1))
        return rgba(0.55 + 0.3 * smooth(1, -1, y) + shine + e * 0.5, clamp(a * 0.6 + e * 0.9 + shine * a))
    return paint

def drop(seed):
    def paint(w, h):
        x, y = grid(w, h)
        s = 0.85 + 0.12 * (seed % 3)
        xx = x / s
        cy = 0.28
        rad = 0.34
        circle = np.sqrt(xx * xx + (y - cy) ** 2)
        cone_w = rad * np.clip((y + 0.72) / (cy + 0.72), 0, 1) ** 1.3
        inside = np.where(y > cy, smooth(rad + 0.02, rad - 0.02, circle), smooth(cone_w + 0.02, cone_w - 0.02, np.abs(xx)))
        a = inside * smooth(-0.75, -0.68, y)
        hl = 0.55 * np.exp(-(((xx + 0.12) / 0.07) ** 2 + ((y - 0.18) / 0.11) ** 2))
        return rgba(0.68 + hl + 0.15 * smooth(0.6, -0.4, y), a)
    return paint

def splat(w, h):
    x, y = grid(w, h)
    a_ang = np.arctan2(y, x)
    n = value_noise(w, h, 5, 31, 4)
    d = np.sqrt(x * x + y * y)
    rad = 0.45 + 0.18 * np.sin(a_ang * 7 + 1) * np.cos(a_ang * 3) + (n - 0.5) * 0.25
    a = smooth(rad + 0.03, rad - 0.03, d)
    img = rgba(0.7 + 0.2 * n, a)
    dr = ImageDraw.Draw(img)
    r = np.random.default_rng(5)
    for k in range(9):
        ang = r.uniform(0, 2 * math.pi)
        dist = r.uniform(0.62, 0.85) * w / 2
        rr = r.uniform(0.02, 0.05) * w
        cx, cy = w / 2 + math.cos(ang) * dist, h / 2 + math.sin(ang) * dist
        dr.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(200, 200, 200, 255))
    return img

def note(w, h):
    img = Image.new('L', (w, h), 0)
    dr = ImageDraw.Draw(img)
    dr.ellipse([w * 0.18, h * 0.62, w * 0.5, h * 0.86], fill=255)
    dr.rectangle([w * 0.44, h * 0.16, w * 0.5, h * 0.74], fill=255)
    dr.polygon([(w * 0.44, h * 0.16), (w * 0.8, h * 0.3), (w * 0.8, h * 0.42), (w * 0.5, h * 0.3)], fill=255)
    a = np.asarray(img.filter(ImageFilter.GaussianBlur(w / 200))) / 255.0
    bloom = np.asarray(img.filter(ImageFilter.GaussianBlur(w / 25))) / 255.0
    return white(clamp(a + bloom * 0.5))

def feather(w, h):
    x, y = grid(w, h)
    t = (y + 1) / 2
    width = 0.32 * np.sin(np.clip(t, 0, 1) * math.pi) ** 0.7 * smooth(0.0, 0.15, t)
    curve = 0.12 * (t - 0.5) ** 2
    xx = x - curve
    vane = smooth(width + 0.02, width - 0.02, np.abs(xx)) * smooth(0.96, 0.9, t)
    barbs = 0.75 + 0.25 * np.sin((t * 60 + np.abs(xx) * 30))
    rach = np.exp(-(xx / 0.02) ** 2) * smooth(1.0, 0.93, t)
    return rgba(0.8 + rach * 0.2, clamp(vane * barbs + rach))

def bubble(w, h):
    x, y = grid(w, h)
    d = np.sqrt(x * x + y * y)
    rim = np.exp(-((d - 0.8) / 0.07) ** 2) + 0.12 * smooth(0.85, 0.5, d)
    hl = np.exp(-(((x + 0.35) / 0.13) ** 2 + ((y + 0.35) / 0.09) ** 2))
    return rgba(0.85 + hl, clamp(rim + hl))

def ember(w, h):
    x, y = grid(w, h)
    d = np.sqrt(x * x + (y * 0.8) ** 2)
    return white(np.exp(-(d / 0.18) ** 2) + 0.4 * np.exp(-(d / 0.4) ** 2))

def slash(w, h):
    x, y = grid(w, h)
    # A crescent: the band between two offset circles, thick in the middle, tapering to points.
    d1 = np.sqrt(x * x + (y - 0.05) ** 2)
    d2 = np.sqrt((x) ** 2 + (y - 0.42) ** 2)
    band = smooth(0.9, 0.86, d1) * smooth(0.82, 0.9, d2) * smooth(0.25, -0.1, y)
    edge = np.exp(-((d1 - 0.87) / 0.025) ** 2) * smooth(0.25, -0.2, y)
    a = np.arctan2(x, -(y - 0.05))
    fade = smooth(1.45, 0.4, np.abs(a))
    return rgba(0.65 + 0.35 * edge, clamp((band * 0.8 + edge) * fade))

def beam(w, h):
    x, y = grid(w, h)
    core = np.exp(-(x / 0.12) ** 2)
    soft = 0.5 * np.exp(-(x / 0.45) ** 2)
    ends = smooth(1.0, 0.7, np.abs(y))
    return rgba(0.7 + 0.3 * core, (core + soft) * ends)

def ribbon(w, h):
    x, y = grid(w, h)
    core = np.exp(-(y / 0.18) ** 2)
    soft = 0.55 * np.exp(-(y / 0.55) ** 2)
    return rgba(0.7 + 0.3 * core, clamp(core + soft))

def confetti(w, h):
    x, y = grid(w, h)
    a = smooth(0.75, 0.7, np.maximum(np.abs(x) * 0.9, np.abs(y) * 1.4))
    return rgba(0.85 + 0.15 * smooth(1, -1, y), a)

def rune(seed):
    def paint(w, h):
        r = np.random.default_rng(seed)
        img = Image.new('L', (w, h), 0)
        dr = ImageDraw.Draw(img)
        lw = max(3, w // 22)
        pts = [(0.5, 0.12), (0.5, 0.88)]
        dr.line([(p[0] * w, p[1] * h) for p in pts], fill=255, width=lw)
        for k in range(r.integers(2, 4)):
            y0 = r.uniform(0.15, 0.75)
            side = r.choice([-1, 1])
            dr.line([(0.5 * w, y0 * h), ((0.5 + side * r.uniform(0.18, 0.3)) * w, (y0 + r.uniform(0.08, 0.2)) * h)], fill=255, width=lw)
        if r.random() < 0.5:
            dr.ellipse([0.36 * w, 0.4 * h, 0.64 * w, 0.62 * h], outline=255, width=lw)
        base = np.asarray(img) / 255.0
        bloom = np.asarray(img.filter(ImageFilter.GaussianBlur(w / 18))) / 255.0
        return white(clamp(base + bloom * 0.9))
    return paint

def magic_circle(w, h):
    img = Image.new('L', (w, h), 0)
    dr = ImageDraw.Draw(img)
    c = w / 2
    lw = max(3, w // 160)
    def circ(r, width=lw):
        dr.ellipse([c - r, c - r, c + r, c + r], outline=255, width=width)
    circ(w * 0.48, lw * 2)
    circ(w * 0.44)
    circ(w * 0.33, lw * 2)
    circ(w * 0.3)
    circ(w * 0.12)
    r = np.random.default_rng(9)
    # Glyph band between the two outer rings.
    for k in range(36):
        a = k * 2 * math.pi / 36
        rr0, rr1 = w * 0.355, w * 0.425
        x0, y0 = c + math.cos(a) * rr0, c + math.sin(a) * rr0
        x1, y1 = c + math.cos(a) * rr1, c + math.sin(a) * rr1
        if k % 3 == 0:
            dr.line([(x0, y0), (x1, y1)], fill=255, width=lw)
        else:
            mx, my = (x0 + x1) / 2, (y0 + y1) / 2
            s = w * 0.012
            dr.line([(mx - s, my - s), (mx + s, my + s)], fill=255, width=lw)
            dr.line([(mx - s, my + s), (mx + s, my - s)], fill=255, width=lw)
    # Hexagram.
    for off in (0, math.pi / 3):
        pts = [(c + math.cos(off + i * 2 * math.pi / 3 - math.pi / 2) * w * 0.3, c + math.sin(off + i * 2 * math.pi / 3 - math.pi / 2) * w * 0.3) for i in range(3)]
        dr.polygon(pts, outline=255, width=lw)
    for i in range(6):
        a = i * math.pi / 3 - math.pi / 2
        px, py = c + math.cos(a) * w * 0.3, c + math.sin(a) * w * 0.3
        rr = w * 0.025
        dr.ellipse([px - rr, py - rr, px + rr, py + rr], outline=255, width=lw)
    base = np.asarray(img) / 255.0
    bloom = np.asarray(img.filter(ImageFilter.GaussianBlur(w / 80))) / 255.0
    wide = np.asarray(img.filter(ImageFilter.GaussianBlur(w / 25))) / 255.0
    return white(clamp(base + bloom * 0.9 + wide * 0.5))

def leaf(w, h):
    x, y = grid(w, h)
    t = (y + 1) / 2
    width = 0.42 * np.sin(np.clip(t, 0, 1) * math.pi) ** 0.9
    a = smooth(width + 0.02, width - 0.02, np.abs(x)) * smooth(0.98, 0.94, t)
    vein = np.exp(-(x / 0.025) ** 2) * 0.35
    return rgba(0.8 - vein + 0.2 * smooth(1, -1, y), a)

def bolt_glow(w, h):
    return ribbon(w, h)

# ---- layout ----

sprites = []  # (name, painter, cells_w, cells_h, frames)
sprites.append(('MAGIC_CIRCLE', magic_circle, 4, 4, 1))
sprites.append(('SLASH', slash, 2, 2, 1))
sprites.append(('SHOCKWAVE', shockwave, 2, 2, 1))
sprites.append(('HALO', halo, 2, 2, 1))
sprites.append(('SPLAT', splat, 2, 2, 1))
sprites.append(('STARBURST', starburst, 2, 2, 1))
sprites.append(('BEAM', beam, 1, 2, 1))
sprites.append(('GLOW', glow, 1, 1, 1))
sprites.append(('DUST', dust, 1, 1, 1))
sprites.append(('FLARE', flare, 1, 1, 1))
sprites.append(('STAR4', star(4, 0.05, 0.12), 1, 1, 1))
sprites.append(('STAR6', star(6, 0.06, 0.14), 1, 1, 1))
sprites.append(('TWINKLE', star(4, 0.025, 0.07), 1, 1, 1))
sprites.append(('SPARK', spark, 1, 1, 1))
sprites.append(('RING', ring(0.05), 1, 1, 1))
sprites.append(('THIN_RING', ring(0.025), 1, 1, 1))
sprites.append(('FLAME', None, 1, 1, 8))
sprites.append(('SOUL', None, 1, 1, 6))
sprites.append(('SMOKE', None, 1, 1, 4))
sprites.append(('PETAL', None, 1, 1, 2))
sprites.append(('SNOWFLAKE', None, 1, 1, 2))
sprites.append(('HEART', heart, 1, 1, 1))
sprites.append(('SHARD', None, 1, 1, 4))
sprites.append(('DROP', None, 1, 1, 3))
sprites.append(('NOTE', note, 1, 1, 1))
sprites.append(('FEATHER', feather, 1, 1, 1))
sprites.append(('BUBBLE', bubble, 1, 1, 1))
sprites.append(('EMBER', ember, 1, 1, 1))
sprites.append(('RIBBON', ribbon, 1, 1, 1))
sprites.append(('CONFETTI', confetti, 1, 1, 1))
sprites.append(('RUNE', None, 1, 1, 6))
sprites.append(('LEAF', leaf, 1, 1, 1))

frame_painters = {
    'FLAME': lambda i: flame_frame(i, 8),
    'SOUL': lambda i: flame_frame(i, 6, soul=True),
    'SMOKE': lambda i: smoke(40 + i),
    'PETAL': lambda i: petal(i),
    'SNOWFLAKE': lambda i: snowflake(i),
    'SHARD': lambda i: shard(61 + i * 7),
    'DROP': lambda i: drop(i),
    'RUNE': lambda i: rune(80 + i),
}

atlas = Image.new('RGBA', (ATLAS, ATLAS), (0, 0, 0, 0))
cols = ATLAS // CELL
used = np.zeros((cols, cols), bool)
entries = []

def place(cw, ch, count):
    for gy in range(cols):
        for gx in range(cols - cw * count + 1):
            if not used[gy:gy + ch, gx:gx + cw * count].any():
                used[gy:gy + ch, gx:gx + cw * count] = True
                return gx, gy
    raise SystemExit('atlas full')

for name, painter, cw, ch, frames in sprites:
    gx, gy = place(cw, ch, frames)
    for f in range(frames):
        p = painter if frames == 1 else frame_painters[name](f)
        w, h = cw * CELL, ch * CELL
        img = p(w * SS, h * SS).resize((w, h), Image.LANCZOS)
        # Keep a 2 px clear border so linear filtering never bleeds into neighbours.
        mask = Image.new('L', (w, h), 0)
        ImageDraw.Draw(mask).rectangle([2, 2, w - 3, h - 3], fill=255)
        clear = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        img = Image.composite(img, clear, mask)
        atlas.paste(img, ((gx + f * cw) * CELL, gy * CELL))
    entries.append((name, gx * CELL, gy * CELL, cw * CELL, ch * CELL, frames))

atlas.save(sys.argv[1], optimize=True)

lines = []
lines.append('package dev.skirmish.fx;')
lines.append('')
lines.append('/**')
lines.append(' * Sprites of the effect atlas ({@code textures/fx/atlas.png}), made by {@code tools/fx/atlas.py}: white shapes tinted')
lines.append(' * by the particle colour. Animated ones are frames side by side. Generated; edit the script, not this file.')
lines.append(' */')
lines.append('public enum FxSprite {')
body = []
for name, x, y, w, h, frames in entries:
    body.append(f'    {name}({x}, {y}, {w}, {h}, {frames})')
lines.append(',\n'.join(body) + ';')
lines.append('')
lines.append(f'    public static final float SIZE = {ATLAS}f;')
lines.append('    public final float u0;')
lines.append('    public final float v0;')
lines.append('    public final float du;')
lines.append('    public final float dv;')
lines.append('    public final int frames;')
lines.append('')
lines.append('    FxSprite(int x, int y, int w, int h, int frames) {')
lines.append('        this.u0 = x / SIZE;')
lines.append('        this.v0 = y / SIZE;')
lines.append('        this.du = w / SIZE;')
lines.append('        this.dv = h / SIZE;')
lines.append('        this.frames = frames;')
lines.append('    }')
lines.append('}')
open(sys.argv[2], 'w').write('\n'.join(lines) + '\n')
print(len(entries), 'sprites')
