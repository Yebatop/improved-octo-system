#!/usr/bin/env python3
"""Draws the Studio's training dummy skin: a stuffed burlap sack with stitched X eyes, a painted target on the chest,
rope ties at the neck, waist, wrists and ankles, a patch, and straw poking out of the seams. A 64x64 player skin
(wide arms). Usage: dummy_skin.py out.png"""
import random
import sys

from PIL import Image

W = H = 64
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()
rnd = random.Random(7)

BURLAP = (190, 154, 99)
BURLAP_DARK = (158, 124, 76)
THREAD = (58, 40, 24)
ROPE = (128, 96, 54)
ROPE_HI = (170, 132, 80)
STRAW = (232, 200, 96)
STRAW_DARK = (196, 160, 62)
RED = (184, 46, 38)
CREAM = (236, 226, 204)
PATCH = (112, 128, 150)


def vary(c, amount):
    d = rnd.randint(-amount, amount)
    return tuple(max(0, min(255, v + d)) for v in c) + (255,)


def burlap(x0, y0, w, h, base=BURLAP):
    """Woven cloth: a little noise and a faint over-under weave."""
    for y in range(h):
        for x in range(w):
            weave = 4 if (x + y) % 2 == 0 else -3
            c = tuple(max(0, min(255, v + weave)) for v in base)
            px[x0 + x, y0 + y] = vary(c, 9)


def put(x, y, c):
    px[x, y] = c + (255,) if len(c) == 3 else c


def rope_row(x0, y, w):
    for x in range(w):
        put(x0 + x, y, ROPE_HI if (x % 2 == 0) else ROPE)


def face(x0, y0, w, h, base=BURLAP):
    burlap(x0, y0, w, h, base)


def box(u, v, sx, sy, sz, base=BURLAP):
    """All six faces of a box texture at (u, v) for a part sx wide, sy tall, sz deep."""
    face(u + sz, v, sx, sz, base)            # top
    face(u + sz + sx, v, sx, sz, base)       # bottom
    face(u, v + sz, sz, sy, base)            # right
    face(u + sz, v + sz, sx, sy, base)       # front
    face(u + sz + sx, v + sz, sz, sy, base)  # left
    face(u + sz * 2 + sx, v + sz, sx, sy, base)  # back


def ring_rows(u, v, sx, sy, sz, row):
    """A rope band around a box at {@code row} from its top: across right, front, left and back faces."""
    rope_row(u, v + sz + row, sz * 2 + sx * 2)


# ---- head ----
box(0, 0, 8, 8, 8)
# Eyes: stitched crosses.
for ex in (9, 13):
    for d in range(3):
        put(ex + d, 10 + d, THREAD)
        put(ex + 2 - d, 10 + d, THREAD)
# Mouth: a stitched seam.
for x in range(10, 14):
    put(x, 14, THREAD)
for x in (10, 12):
    put(x + 1, 13, BURLAP_DARK)
    put(x + 1, 15, BURLAP_DARK)
# A back seam.
for y in range(8, 16):
    if y % 2 == 0:
        put(28, y, THREAD)
# Hat layer: straw poking out of the top seam and the neck tie.
for x in range(32 + 8, 32 + 16):
    for y in range(0, 8):
        if rnd.random() < 0.18:
            put(x, y, STRAW if rnd.random() < 0.6 else STRAW_DARK)
for x in range(32, 64):
    put(x, 15, ROPE_HI if x % 2 == 0 else ROPE)
for x in range(32, 64):
    if rnd.random() < 0.12:
        put(x, 8, STRAW)

# ---- body ----
box(16, 16, 8, 12, 4)
# Target on the chest (front face at 20,20, 8x12).
cx, cy = 23.5, 24.5
for y in range(12):
    for x in range(8):
        d = ((x + 20 - cx) ** 2 + (y + 20 - cy) ** 2) ** 0.5
        if d < 1.2:
            put(20 + x, 20 + y, RED)
        elif d < 2.2:
            put(20 + x, 20 + y, CREAM)
        elif d < 3.3:
            put(20 + x, 20 + y, RED)
# A patch on the back, stitched round.
for y in range(25, 30):
    for x in range(34, 38):
        put(x, y, vary(PATCH, 8))
for x in range(33, 39):
    if x % 2:
        put(x, 24, THREAD)
        put(x, 30, THREAD)
# Rope belt.
ring_rows(16, 16, 8, 12, 4, 9)
# Side seams with straw.
for y in range(20, 32, 2):
    put(16, y, THREAD)
    put(31, y, THREAD)
# Jacket layer: straw tufts at the belt.
for x in range(16, 40):
    if rnd.random() < 0.2:
        put(x, 32 + 4 + 10, STRAW)

# ---- arms ----
for (u, v) in ((40, 16), (32, 48)):
    box(u, v, 4, 12, 4)
    ring_rows(u, v, 4, 12, 4, 10)
    ring_rows(u, v, 4, 12, 4, 1)
    # Straw sticking out of the hands.
    for x in range(u + 8, u + 12):
        put(x, v + 1, STRAW if (x % 2) else STRAW_DARK)
        put(x, v + 2, STRAW_DARK if (x % 2) else STRAW)

# ---- legs: darker sacking, roped at the ankles ----
for (u, v) in ((0, 16), (16, 48)):
    box(u, v, 4, 12, 4, BURLAP_DARK)
    ring_rows(u, v, 4, 12, 4, 10)
    for y in range(v + 4, v + 14, 2):
        put(u + 4 + 1, y, THREAD)

img.save(sys.argv[1])
