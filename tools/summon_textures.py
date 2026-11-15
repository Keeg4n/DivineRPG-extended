"""Textures for the summons: the Cyclops Wand eye (64x32 entity texture for ModelSummonEye) and the Frost flying snowflake (32x32 for ModelSummonFrost) and flat 16x16 icons of the
Scorcher Brand, the Ayeraco Prism and the Ayeraco Scale. Run from the repo root: python3 tools/summon_textures.py"""
import math, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "divinerpgextended", "textures")


def rgb(h, a=255):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (a,)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3],)


# ------------------------------------------------------------------ eye entity texture
SCLERA, VEIN, IRIS, PUPIL = rgb("#ece6dc"), rgb("#b8323a"), rgb("#d8402a"), rgb("#120806")
NERVE, WING = rgb("#9a2c34"), rgb("#5a2030")


def veins(seed):
    rnd = random.Random(seed)
    v = set()
    for _ in range(5):
        x, y = rnd.choice([(0, rnd.randint(0, 7)), (7, rnd.randint(0, 7)), (rnd.randint(0, 7), 0), (rnd.randint(0, 7), 7)])
        for _ in range(3):
            v.add((x, y))
            x += 1 if x < 4 else -1
            y = max(0, min(7, y + rnd.choice((-1, 0, 1))))
    return v


def box_faces(u, v, w, h, d):
    """Minecraft box UV layout: name -> (x, y, width, height)."""
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


def eye_texture():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    for i, (name, (x0, y0, w, h)) in enumerate(box_faces(0, 0, 8, 8, 8).items()):
        vs = veins(3 + i)
        for a in range(w):
            for b in range(h):
                if name == "front":
                    d = math.hypot(a - 3.5, b - 3.5)
                    if d < 1.0:
                        c = PUPIL
                    elif d < 2.6:
                        c = shade(IRIS, 1.15 - 0.25 * d / 2.6)
                    elif (a, b) in veins(3):
                        c = VEIN
                    else:
                        c = SCLERA
                    if (a, b) == (2, 2):
                        c = rgb("#fff8f0")  # glint
                elif name == "back":
                    c = shade(SCLERA, 0.85) if math.hypot(a - 3.5, b - 3.5) > 1.6 else NERVE  # the nerve grows out of the back
                else:
                    c = VEIN if (a, b) in vs else shade(SCLERA, 0.92 + 0.08 * ((a * 7 + b * 3) % 3) / 2)
                px[x0 + a, y0 + b] = c
    rnd = random.Random(5)
    for (u, v, w, h, d) in ((32, 0, 3, 3, 2), (32, 6, 2, 2, 2), (32, 11, 1, 1, 2)):
        for name, (x0, y0, fw, fh) in box_faces(u, v, w, h, d).items():
            for a in range(fw):
                for b in range(fh):
                    px[x0 + a, y0 + b] = shade(NERVE, 0.88 + rnd.random() * 0.24)
    for name, (x0, y0, fw, fh) in box_faces(44, 0, 3, 1, 3).items():
        for a in range(fw):
            for b in range(fh):
                px[x0 + a, y0 + b] = shade(WING, 0.85 + rnd.random() * 0.3)
    return img


# ------------------------------------------------------------------ 16x16 icons
def icon(rows, palette):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = palette[ch]
    return img


BRAND = icon([
    "...........yy...",
    "..........yoOy..",
    ".........yoOOy..",
    "........yoORy...",
    ".......kyoRy....",
    "......kkyy......",
    ".....kkk........",
    "....bkk.........",
    "...bbk..........",
    "..bbb...........",
    ".bbb............",
    "bbb.............",
    "bb..............",
    "................",
    "................",
    "................",
], {"y": rgb("#ffd34a"), "o": rgb("#ff8a1c"), "O": rgb("#ffb43a"), "R": rgb("#d8401a"), "k": rgb("#3a2a2a"), "b": rgb("#8a5a2e")})

PRISM = icon([
    ".......w........",
    "......wpw.......",
    ".....wppbw......",
    "....wpppbbw.....",
    "...wppppbbbw....",
    "..wpppppbbbgw...",
    ".wrrrrrpbbggyw..",
    ".wrrrrrrbgggyw..",
    "..wrrrrrrggyw...",
    "...wrrrrrgyw....",
    "....wrrrryw.....",
    ".....wrryw......",
    "......wyw.......",
    ".......w........",
    "................",
    "................",
], {"w": rgb("#f0e8ff"), "p": rgb("#9b4ad8"), "b": rgb("#3a6ae8"), "g": rgb("#3ac860"), "y": rgb("#f0d040"), "r": rgb("#d83a3a")})

SCALE = icon([
    "................",
    "......dddd......",
    "....ddmmmmdd....",
    "...dmmllllmmd...",
    "..dmmlllhllmmd..",
    "..dmllllhlllmd..",
    ".dmmllllhllllmd.",
    ".dmllllhhlllmmd.",
    ".dmlllhhlllllmd.",
    ".dmmlllhlllmmd..",
    "..dmmllhllmmd...",
    "...dmmlhlmmd....",
    "....ddmhmdd.....",
    "......ddd.......",
    "................",
    "................",
], {"d": rgb("#2a1a40"), "m": rgb("#5a3a8a"), "l": rgb("#8a5ac8"), "h": rgb("#d0b0ff")})


# ------------------------------------------------------------------ Frost (flying snowflake) entity texture
def frost_texture():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(9)
    core, eye, ice, tip = rgb("#a8dcff"), rgb("#1a3a6a"), rgb("#cfefff"), rgb("#ffffff")
    for name, (x0, y0, w, h) in box_faces(0, 0, 3, 3, 2).items():
        for a in range(w):
            for b in range(h):
                c = shade(core, 0.92 + rnd.random() * 0.16)
                if name == "front" and b == 1 and a in (0, 2):
                    c = eye
                px[x0 + a, y0 + b] = c
    for name, (x0, y0, w, h) in box_faces(0, 6, 1, 12, 1).items():
        for a in range(w):
            for b in range(h):
                px[x0 + a, y0 + b] = tip if name in ("right", "front", "left", "back") and b in (0, h - 1) else shade(ice, 0.9 + rnd.random() * 0.12)
    for name, (x0, y0, w, h) in box_faces(12, 0, 3, 1, 1).items():
        for a in range(w):
            for b in range(h):
                px[x0 + a, y0 + b] = shade(ice, 0.95 + rnd.random() * 0.08)
    return img


if __name__ == "__main__":
    eye_texture().save(os.path.join(ROOT, "entity", "summon_eye.png"))
    frost_texture().save(os.path.join(ROOT, "entity", "summon_frost.png"))
    BRAND.save(os.path.join(ROOT, "items", "scorcherBrand.png"))
    PRISM.save(os.path.join(ROOT, "items", "ayeracoPrism.png"))
    SCALE.save(os.path.join(ROOT, "items", "ayeracoScale.png"))
    print("saved")
