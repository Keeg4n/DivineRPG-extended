"""Wanderer armor concepts, fifth round: fixes to the fourth (wslim.py). The body was praised, the complaints are about the heads; no capes - they conflict with the capes
on the server, instead an item on the back themed to the world. R2 and R7 are taken from the fourth round as is.

R1 - a shield on the back. R3 - the closed helmet of the previous look with a plus-shaped slit, planks texture, a staff behind the back. R4 - Native American style: a feather headdress, a bead breastplate, patterns, fringe,
a fan of feathers on the back. R5 - a closed helmet with a crest, brighter body, folded wings. R6 - a hood like the Thaum cultists': a 9x9x9 box, narrowing at the back
in steps with increasing slant (0, 13, 20, 33 degrees), cloth in folds, the face in a dark arch; a spine of bones behind the back.

Run: python3 wslim5.py [key ...]  (without keys - all); images go to wconcepts.OUT.
"""
import sys

import numpy as np

import wconcepts as w
from wconcepts import BONE, C, GLOWC, GLOWR, MO, MOD, MOL, P, RED, WWB, WWD, WWL, BARK, box, chain, concept, disc, facets, fuv, mottle, rect, rod, stripe, sym, tip, trim
import wterraria as t
from wround3 import BLUE, SKY, SKYD, SKYL
from wslim import SIDES, belt, boot, bracer, bracer_plate, breastplate, bx, cap, feather as side_feather, knee, shoulder, slim as _slim, thigh, under, waist

KEYS = []


def slim(key, tier, title):
    def reg(fn):
        KEYS.append(key)
        return _slim(key, tier, title)(fn)
    return reg


# ---- decals

def bands(colors, step=1.5, axis=0, faces='all'):
    """Stripes along faces: cloth in folds, planks, rows of beads. axis=0 - vertical stripes, 1 - horizontal."""
    cs = np.array([C(c) for c in colors])

    def d(p, face, b):
        if faces != 'all' and face.name not in faces:
            return cs[0], 0.0, False
        u, v = fuv(p, face)
        i = np.floor((u if axis == 0 else v) / step).astype(int) % len(cs)
        return cs[i], 1.0, False
    return d


def seams(step, color, axis=0, faces='all'):
    """Seams between planks: a dark one-texel line."""
    c = C(color)

    def d(p, face, b):
        if faces != 'all' and face.name not in faces:
            return c, 0.0, False
        u, v = fuv(p, face)
        return c, (((u if axis == 0 else v) % step) < 0.5).astype(float), False
    return d


def zigzag(faces, v0, amp, period, wd, color, glow=False):
    """Zigzag stripe along a face: Native American pattern."""
    c = C(color)

    def d(p, face, b):
        if faces != 'all' and face.name not in faces:
            return c, 0.0, glow
        u, v = fuv(p, face)
        tri = np.abs(((u / period) % 1.0) * 2 - 1) * amp
        return c, (np.abs(v - v0 - tri) < wd).astype(float), glow
    return d


def fringe_alpha(solid=0.35):
    """Fringe: a solid stripe on top, below it threads at every other texel with an uneven bottom."""
    def a(p, face, b):
        u = p[..., 2] if face.name in ('east', 'west') else p[..., 0]
        on = (np.floor(u / 0.5) % 2) == 0
        h = b.size[1]
        jag = 0.9 * (np.sin(u * 5.0) * 0.5 + 0.5)
        return ((on & (p[..., 1] < h - jag)) | (p[..., 1] < h * solid)).astype(float)
    return a


def feather_alpha(wd, L):
    """Feather: tapers toward the tip (the tip is the top of the box)."""
    def a(p, face, b):
        x = np.abs(p[..., 0] - wd / 2) / (wd / 2)
        return (x <= np.clip(p[..., 1] / (L * 0.35), 0, 1) + 0.01).astype(float)
    return a


def feather(bone, start, L, wd, body, tipc, quill, rx=0.0, ry=0.0, rz=0.0, thick=0.25):
    """Flat feather from point start to the tip (up, -y), rotated around start."""
    sx, sy, sz = start
    paint = P(body, rect(('north', 'south'), -1, 99, -1, L * 0.3, tipc), rect(('north', 'south'), wd / 2 - 0.25, wd / 2 + 0.25, L * 0.3, 99, quill),
              alpha=feather_alpha(wd, L), bevel=0.0, noise=0.03)
    return box(bone, sx - wd / 2, sy - L, sz - thick / 2, wd, L, thick, paint, rot=(rx, ry, rz), pivot=(sx, sy, sz))


def hang(bone, x, y, z, wd, L, paint, rz=0.0, rx=0.0, thick=0.3):
    """Flat ribbon or flight feather downward from point (x, y, z)."""
    return box(bone, x - wd / 2, y, z, wd, L, thick, paint, rot=(rx, 0, rz), pivot=(x, y, z))


def leaf(bone, start, L, wd, color, rib, edge, rx=0.0, rz=0.0):
    paint = P(color, rect(('north', 'south'), wd / 2 - 0.25, wd / 2 + 0.25, 0.5, L - 0.5, rib), trim(0.25, edge), alpha=t.ellipse_mask(wd, L), bevel=0.0, noise=0.03)
    return t.blade(bone, start, L, wd, paint, rx=rx, rz=rz, t=0.3)


# ---- R1 Divine: everything as in the fourth round, a shield instead of a cape

@slim('r1v', 1, 'Hallowed: a silver cuirass with a gold wedge, an open gold helmet with wings, a shield with a scarlet stone on the back')
def r1v():
    gold = P(t.HG, trim(0.3, t.HGD), rect(('up',), -1, 99, -1, 99, t.HGL))
    band = P(t.HG, trim(0.3, t.HGD), rect(SIDES, -1, 99, 2.0, 3.0, t.HS))
    plate = P(t.HS, trim(0.3, t.HSD), stripe(('north',), 0.72, 0.7, 0.5, t.HG), stripe(('north',), -0.72, 8.5, 0.5, t.HG),
              disc(('north',), 4.6, 2.4, 1.25, t.HRD), disc(('north',), 4.6, 2.4, 0.8, t.GEMR, glow=True))
    dark = P(t.HDK2, trim(0.3, t.HDK))
    bs = under(dark, dark) + breastplate(plate) + waist(dark) + belt(P(t.HG, trim(0.3, t.HGD), rect(('north',), 3.5, 5.5, 0.4, 1.9, t.HS)))
    bs += shoulder(gold) + bracer(band) + thigh(dark) + knee(gold) + boot(P(t.HS, trim(0.3, t.HSD), rect(SIDES, -1, 99, -1, 0.9, t.HG)))
    bs += cap(P(t.HG, trim(0.3, t.HGD), rect(('up',), 3.6, 5.6, -1, 99, t.HS)))
    bs += [box('head', -1.0, -8.0, -5.2, 2.0, 1.8, 0.4, P(t.GEMR, glow=True))]
    wing, wd = P(t.HG, trim(0.25, t.HGD), rect('all', -1, 99, -1, 0.9, t.HGL)), P(t.HGD, trim(0.25, '#6a4a04'))
    bs += sym(side_feather(-5.6, -1.4, 5.2, 1.8, wing), side_feather(-5.6, 0.2, 4.2, 1.6, wd, rx=-34), side_feather(-5.6, 1.6, 3.2, 1.4, wing, rx=-46))
    # shield: silver, a golden cross and border, narrows toward the bottom in steps
    sh = P(t.HS, trim(0.4, t.HG), rect(('south',), 2.6, 3.6, -1, 99, t.HG), rect(('south',), -1, 99, 2.4, 3.4, t.HG),
           disc(('south',), 3.1, 2.9, 1.25, t.HRD), disc(('south',), 3.1, 2.9, 0.8, t.GEMR, glow=True))
    low = P(t.HS, trim(0.4, t.HG), rect(('south',), -1, 99, -1, 99, t.HS), rect(('south',), 1.7, 2.7, -1, 99, t.HG))
    tipp = P(t.HG, trim(0.3, t.HGD))
    bs += [box('body', -3.1, 0.8, 2.75, 6.2, 6.0, 0.5, sh), box('body', -2.2, 6.8, 2.75, 4.4, 1.6, 0.5, low), box('body', -1.2, 8.4, 2.75, 2.4, 0.5, 0.5, tipp)]
    return bs


# ---- R3 Wildwood: druid

PL = ('#2a72e8', '#2464d4', '#3080f0', '#2060c8')
PL_D = '#0e2f78'
TAN, TAN_D = '#d8c49a', '#6a4a2a'  # light wood: rope and staff
LEAF_A, LEAF_B = '#2f8fe8', '#8ad0ff'


def plus_hole(*rects):
    """Transparent cutout on the front face (u0, u1, v0, v1): the player's skin shows through it."""
    def a(p, face, b):
        if face.name != 'north':
            return np.ones(p.shape[:-1])
        u, v = p[..., 0], p[..., 1]
        hole = np.zeros(u.shape, bool)
        for (u0, u1, v0, v1) in rects:
            hole |= (u >= u0) & (u <= u1) & (v >= v0) & (v <= v1)
        return (~hole).astype(float)
    return a


@slim('r3v', 3, 'Wildwood: boards of blue wood, a closed helmet with a plus-shaped slit and branch antlers, leaves on the shoulders, a staff on the back')
def r3v():
    wood = P(PL[0], bands(PL, 2.0), seams(2.0, PL_D), trim(0.3, PL_D))
    chest = P(PL[0], bands(PL, 2.0), seams(2.0, PL_D), trim(0.3, PL_D), disc(('north',), 4.6, 2.9, 1.8, GLOWC, glow=True, r0=1.2), rect(('north',), 4.35, 4.85, 0.8, 5.2, GLOWC, glow=True))
    cloth = P(BARK, bands(('#24345a', '#1e2c4e', '#2a3c66'), 1.5), trim(0.3, '#121c36'))
    rope = P(TAN, bands((TAN, '#9a7f52'), 1.0), trim(0.2, TAN_D))
    bs = under(cloth, cloth) + breastplate(chest) + waist(cloth) + belt(rope)
    bs += shoulder(wood) + bracer(P(PL[0], bands(PL, 2.0), seams(2.0, PL_D), trim(0.3, PL_D), stripe(SIDES, 0.5, 0.0, 0.3, LEAF_B))) + thigh(wood) + knee(rope) + boot(wood)
    for (z, L, rz, rx) in ((-1.8, 3.2, -30, -12), (0.0, 3.8, -38, 0), (1.8, 3.0, -28, 12)):
        bs += sym(leaf('rightArm', (-3.6, -2.0, z), L, 2.0, LEAF_A, LEAF_B, PL_D, rz=rz, rx=rx))
    # head: the closed helmet of the previous look following the head, a plus-shaped slit at the eyes, branch horns
    helm_ = P(PL[0], bands(PL, 2.0), seams(2.0, PL_D), trim(0.3, PL_D), rect(('north',), 1.2, 7.9, 4.0, 5.9, PL_D), rect(('north',), 3.6, 5.5, 2.0, 8.1, PL_D),
              alpha=plus_hole((1.7, 7.4, 4.5, 5.4), (4.1, 5.0, 2.5, 7.6)))
    bs += [box('head', -4.55, -8.55, -4.75, 9.1, 8.4, 9.3, helm_)]
    ant = P(WWL, trim(0.15, WWB))
    bs += sym(*chain('head', (-3.2, -8.4, -0.4), ((2.4, 0, -30), (2.6, 0, -12), (2.0, 0, 10)), 0.8, ant))
    bs += sym(rod('head', (-4.6, -10.4, -0.4), 1.8, 0.5, ant, rz=-68), rod('head', (-5.2, -12.4, -0.4), 1.4, 0.4, ant, rz=-8))
    # staff behind the back, slanted, the pommel glows
    staff = P('#a8875a', trim(0.15, TAN_D))
    s0, L0, a = (4.0, 11.0, 3.1), 20.0, -33  # above the shoulder and past the head: otherwise the pommel goes into the head
    top = tip(s0, L0, rz=a)
    bs += [rod('body', s0, L0, 0.9, staff, rz=a), box('body', top[0] - 0.9, top[1] - 1.0, top[2] - 0.9, 1.8, 1.8, 1.8, P(GLOWC, trim(0.2, '#ffffff'), glow=True))]
    bs += [rod('body', tuple(tip(s0, L0 - 1.0, rz=a)), 2.0, 0.45, staff, rz=a - 45), rod('body', tuple(tip(s0, L0 - 1.0, rz=a)), 2.0, 0.45, staff, rz=a + 45)]
    bs += [leaf('body', tuple(tip(s0, L0 - 4.0, rz=a)), 2.8, 1.6, LEAF_A, LEAF_B, PL_D, rz=a + 60)]
    return bs


# ---- R4 Apalachia: Native American style in the world's colors

LEATHER, LEATHER_D, LEATHER_L = '#6e2478', '#2c0a34', '#8e3a98'
BEAD_L = '#f2e4f8'


@slim('r4v', 4, 'Apalachia, Native American style: a feather headdress with an embroidered headband, a breastplate of beads, patterns and fringe, a fan of feathers on the back')
def r4v():
    leather = P(LEATHER, trim(0.3, LEATHER_D))
    beads = P(LEATHER, trim(0.3, LEATHER_D), bands((BEAD_L, t.OP, BEAD_L, t.OM), 1.0, axis=1, faces=('north',)), rect(('north',), 4.1, 5.1, -1, 99, LEATHER_D), trim(0.3, LEATHER_D))
    zig = P(LEATHER, trim(0.3, LEATHER_D), zigzag(SIDES, 0.5, 1.0, 2.0, 0.3, t.OP))
    bs = under(P(LEATHER_D, trim(0.3, '#180420')), P(LEATHER_D, trim(0.3, '#180420'))) + breastplate(beads) + waist(leather)
    bs += belt(P(t.OM, trim(0.3, LEATHER_D), zigzag(('north', 'south', 'east', 'west'), 0.4, 1.0, 2.0, 0.3, BEAD_L)))
    bs += shoulder(P(LEATHER_L, trim(0.3, LEATHER_D), rect(('up',), -1, 99, 2.0, 3.0, t.OP))) + bracer(zig) + thigh(leather) + knee(P(t.OM, trim(0.25, LEATHER_D)))
    bs += boot(P(LEATHER_L, trim(0.3, LEATHER_D), rect(SIDES, -1, 99, -1, 0.9, t.OP)), toe=0.7)
    # fringe: on the outside of the arm and thigh
    fr = P(t.OL, alpha=fringe_alpha(), bevel=0.0)
    bs += sym(box('rightArm', -3.45, 4.0, -1.6, 0.2, 6.6, 3.2, fr), box('rightLeg', -2.45, 0.6, -1.6, 0.2, 6.6, 3.2, fr))
    # tufts of feathers on the shoulders
    for (z, L, rz, rx) in ((-1.5, 3.0, -28, -10), (0.0, 3.6, -36, 0), (1.5, 2.8, -26, 10)):
        bs += sym(feather('rightArm', (-3.5, -2.1, z), L, 1.5, t.OM, BEAD_L, LEATHER_D, rx=rx, rz=rz))
    # head: an embroidered band, a stone, a feather headdress fanned around the crown
    band_ = P(t.OM, trim(0.25, LEATHER_D), zigzag(('north', 'south', 'east', 'west'), 0.3, 1.0, 2.0, 0.28, BEAD_L))
    bs += [box('head', -4.55, -7.6, -4.75, 9.1, 1.9, 9.3, band_), box('head', -1.0, -7.5, -5.0, 2.0, 1.7, 0.3, P(t.OP, trim(0.2, '#ffffff'), glow=True))]
    for i, a in enumerate((-76, -57, -38, -19, 0, 19, 38, 57, 76)):
        r = np.radians(a)
        L = 7.4 - abs(a) * 0.025
        bs.append(feather('head', (4.4 * np.sin(r), -7.3 - 0.6 * np.cos(r), 1.2 + 0.1 * (i % 2)), L, 2.4, t.OM if i % 2 else t.OP, BEAD_L, t.OPU, rx=-24, rz=float(a)))
    # pendants at the temples: a string of beads and a feather hanging down
    bs += sym(rod('head', (-4.8, -2.6, -2.2), 3.4, 0.5, P(BEAD_L, bands((BEAD_L, t.OM), 0.5, axis=1), bevel=0.0)), hang('head', -4.8, -2.8, -2.35, 1.3, 4.0,
              P(t.OM, rect(('north', 'south'), -1, 99, 2.6, 99, BEAD_L), rect(('north', 'south'), 0.4, 0.9, -1, 99, LEATHER_D), bevel=0.0)))
    # fan of feathers on the lower back (tail), a rosette with a stone in the center
    for i, a in enumerate((-90, -67.5, -45, -22.5, 0, 22.5, 45, 67.5, 90)):
        r = np.radians(a)
        bs.append(feather('body', (1.2 * np.sin(r), 5.4 - 1.2 * np.cos(r), 2.8 + 0.1 * (i % 2)), 7.2, 2.6, t.OP if i % 2 else t.OM, BEAD_L, t.OPU, rx=8, rz=float(a)))
    bs += [box('body', -1.5, 3.9, 3.15, 3.0, 3.0, 0.4, P(LEATHER, trim(0.3, LEATHER_D), disc(('south',), 1.5, 1.5, 1.0, t.OP), disc(('south',), 1.5, 1.5, 0.5, BEAD_L)))]
    return bs


# ---- R5 Skythern: closed helmet with a crest, folded wings

SUIT5, SUIT5_D = '#2a4088', '#16244e'
GOLD5 = '#e8c860'


def wing_feather(x, y, z, L, wd, color, rz, rx=5.0):
    """Flight feather downward from point (x, y, z), the tip is pointed and light blue."""
    def a(p, face, b):
        return (np.abs(p[..., 0] - wd / 2) / (wd / 2) <= np.clip((L - p[..., 1]) / (L * 0.22), 0, 1) + 0.01).astype(float)
    paint = P(color, rect(('north', 'south'), -1, 99, L * 0.72, 99, BLUE), rect(('north', 'south'), wd / 2 - 0.25, wd / 2 + 0.25, -1, L * 0.72, '#b6c6dc'), alpha=a, bevel=0.0, noise=0.03)
    return box('body', x - wd / 2, y, z, wd, L, 0.3, paint, rot=(rx, 0, rz), pivot=(x, y, z))


@slim('r5v', 5, 'Skythern: white plate armor with a sky trim, a closed helmet with a light blue crest and rib-wings, folded wings on the back')
def r5v():
    white = P(SKYL, trim(0.3, BLUE))
    silver = P(SKY, trim(0.3, BLUE), rect(SIDES, -1, 99, 4.8, 5.3, BLUE))
    suit = P(SUIT5, trim(0.3, SUIT5_D))
    chest = P(SKYL, trim(0.3, BLUE), stripe(('north',), 0.72, 0.7, 0.45, BLUE), stripe(('north',), -0.72, 8.5, 0.45, BLUE), disc(('north',), 4.6, 2.3, 1.2, GOLD5), disc(('north',), 4.6, 2.3, 0.65, '#ffffff'))
    bs = under(suit, suit) + breastplate(chest) + waist(suit) + belt(P(SKY, trim(0.3, SKYD), rect(('north',), 3.5, 5.5, 0.4, 1.9, GOLD5)))
    bs += shoulder(P(SKYL, trim(0.3, BLUE), rect(SIDES, -1, 99, 1.8, 2.5, BLUE))) + bracer_plate(P(SKYL, trim(0.3, BLUE), rect(SIDES, -1, 99, 2.0, 2.6, GOLD5)))
    bs += thigh(silver) + knee(white) + boot(P(SKYL, trim(0.3, BLUE), rect(SIDES, -1, 99, -1, 0.9, GOLD5)))
    # little wings on the heels
    bs += sym(box('rightLeg', -2.55, 8.0, 0.2, 0.3, 1.2, 2.8, P(BLUE, trim(0.2, SUIT5_D)), rot=(16, 0, 0), pivot=(-2.4, 8.6, 1.0)),
              box('rightLeg', -2.55, 9.0, 0.6, 0.3, 1.0, 2.2, P(SKYL, trim(0.2, BLUE)), rot=(16, 0, 0), pivot=(-2.4, 9.5, 1.4)))
    # helmet: a dome, deep cheeks with a T-shaped cutout, a nose guard, a brow, a full back of the head
    s = 0.55
    dome = P(SKYL, trim(0.3, BLUE))
    bs += [box('head', -4 - s, -8 - s, -4.75, 9.1, 3.2, 9.3, dome), box('head', -4 - s, -5.35, 0.3, 9.1, 5.0, 4.25, dome),
           box('head', -4 - s - 0.1, -5.9, -5.0, 9.3, 0.9, 0.5, P(GOLD5, trim(0.2, '#9a7a20'))), box('head', -0.8, -5.2, -5.0, 1.6, 3.2, 0.4, P(GOLD5, trim(0.2, '#9a7a20')))]
    bs += sym(box('head', -4 - s, -5.35, -4.75, 2.9, 5.0, 5.05, dome))
    # crest from the forehead to the back of the head and down
    crest = P(BLUE, trim(0.2, SUIT5_D), rect(('up',), -1, 99, -1, 99, '#8ab8ec'))
    bs += [box('head', -0.55, -10.0, -4.2, 1.1, 1.5, 9.2, crest), box('head', -0.55, -9.0, 5.0, 1.1, 4.8, 0.9, crest)]
    # rib-wings on the temples
    rib = P(SKY, trim(0.2, BLUE))
    bs += sym(box('head', -5.0, -8.0, -1.8, 0.4, 0.9, 5.8, rib), box('head', -5.0, -7.0, -0.8, 0.4, 0.9, 4.8, rib), box('head', -5.0, -6.0, 0.2, 0.4, 0.9, 3.8, rib))
    # folded wings on the back: pointed flight feathers overlapping, short coverts on top, a golden wing shoulder above
    bs += sym(wing_feather(-1.2, 0.4, 2.85, 13.5, 2.3, '#e4e9f1', 3), wing_feather(-2.1, 0.4, 2.95, 12.5, 2.3, SKYL, 7), wing_feather(-3.0, 0.4, 3.05, 11.0, 2.3, '#e4e9f1', 11),
              wing_feather(-3.8, 0.4, 3.15, 9.0, 2.3, SKYL, 16), wing_feather(-1.5, 0.2, 3.3, 5.6, 3.0, '#f6f8fb', 6), wing_feather(-3.4, 0.2, 3.4, 4.6, 2.6, '#f6f8fb', 14),
              box('body', -4.5, -0.3, 2.75, 4.3, 1.3, 0.9, P(GOLD5, trim(0.25, '#9a7a20'))))
    return bs


# ---- R6 Mortum: cultist hood, spine of bones

CL = ('#2a2a31', '#1c1c21', '#34343c', '#232329')


def cult_hood():
    """Like the Thaum cultist robe: a 9x9x9 box and three narrowing steps at the back with increasing slant; the face in a dark arch."""
    cloth = P(CL[0], bands(CL, 1.5), bevel=0.0, noise=0.03)
    front = P(CL[0], bands(CL, 1.5), rect(('north',), 0.9, 8.1, 1.2, 99, '#4a0c0c'), rect(('north',), 1.4, 7.6, 1.7, 99, '#160606'), rect(('north',), 1.9, 7.1, 2.2, 99, '#020202'),
              rect(('north',), 2.5, 3.9, 4.6, 5.4, GLOWR, glow=True), rect(('north',), 5.1, 6.5, 4.6, 5.4, GLOWR, glow=True), bevel=0.0, noise=0.03)
    return [box('head', -4.5, -9.0, -4.6, 9.0, 9.0, 9.2, front), box('head', -4.0, -9.7, 2.0, 8.0, 9.0, 3.0, cloth, rot=(-13, 0, 0), pivot=(0, 0, 0)),
            box('head', -3.5, -10.0, 3.5, 7.0, 8.0, 3.0, cloth, rot=(-20, 0, 0), pivot=(0, 0, 0)), box('head', -3.0, -10.7, 3.5, 6.0, 7.0, 3.0, cloth, rot=(-33, 0, 0), pivot=(0, 0, 0))]


@slim('r6v', 6, 'Mortum: a cultist hood with red eyes in a dark arch, a dark cuirass with a red trim, a chain, a spine of bones on the back')
def r6v():
    lea = P(MO, trim(0.3, MOD))
    suit = P('#222226', trim(0.3, MOD))
    bs = under(suit, suit) + breastplate(P(MO, trim(0.3, RED), rect(('north',), 4.1, 5.1, 0.4, 5.6, RED))) + waist(suit) + belt(P(MOD, rect(('north',), 3.7, 5.3, 0.4, 1.9, MOL)))
    bs += shoulder(lea) + bracer_plate(P(MOD, trim(0.3, MOL))) + thigh(suit) + knee(P(MOD, trim(0.3, MOL))) + boot(P(MOD, trim(0.3, MOL)))
    bs += cult_hood()
    lk = P(MOL, trim(0.15, '#2a2a30'), bevel=0.1)
    for i in range(7):
        bs.append(box('body', -4.2 + i * 1.2, 11.9 + 0.9 * (1 - abs(i - 3) / 3.0), -2.95, 1.2, 0.5, 0.3, lk))
    # spine and ribs of bones, red smoulders between the upper ribs
    bone = P(BONE, trim(0.2, '#8a8270'), bevel=0.0)
    for y in (0.8, 2.3, 3.8, 5.3, 6.8, 8.0):
        bs.append(box('body', -0.75, y, 2.75, 1.5, 1.0, 0.8, bone))
    for y, L, rz in ((1.2, 4.2, -66), (2.7, 4.0, -70), (4.2, 3.6, -74), (5.7, 3.0, -78)):
        bs += sym(hang('body', -0.7, y, 2.85, 0.7, L, bone, rz=-rz, thick=0.4))
    bs += [box('body', -0.6, 1.6, 3.5, 1.2, 1.2, 0.2, P(GLOWR, glow=True))]
    return bs


# ---- R7 Halite: two variants with a new helmet

BRANCH, BRANCH_D = '#6a4424', '#3a2410'


def crest_leaf(bone, start, L, wd, color, rib, edge, rx=0.0):
    """Crest leaf: a plane along the head (width along z), tilted back around x."""
    sx, sy, sz = start

    def a(p, face, b):
        return (((p[..., 2] - wd / 2) / (wd / 2)) ** 2 + ((p[..., 1] - L / 2) / (L / 2)) ** 2 < 1.0).astype(float)
    paint = P(color, rect(('east', 'west'), wd / 2 - 0.25, wd / 2 + 0.25, 0.5, L - 0.5, rib), trim(0.25, edge), alpha=a, bevel=0.0, noise=0.03)
    return box(bone, sx - 0.15, sy - L, sz - wd / 2, 0.3, L, wd, paint, rot=(rx, 0, 0), pivot=(sx, sy, sz))


@slim('r7x', 7, 'Halite, like Terraria Chlorophyte: a closed mask with a glowing visor and a crest of leaves, leaf pauldrons, a floating crystal')
def r7x():
    vein = (mottle(t.CGD, 1.0, 0.82), mottle(t.CGB, 1.4, 0.95))  # light leaf texture
    lf = P(t.CG, trim(0.3, t.CGO), *vein, rect(('up',), -1, 99, -1, 99, t.CGL))
    lime = P(t.CGB, trim(0.25, t.CG))
    suit = P(t.CGD, trim(0.3, t.CGO))
    metal = P(t.CM, trim(0.3, t.CMD))
    chest = P(t.CG, trim(0.3, t.CGO), *vein, stripe(('north',), 0.72, 0.7, 0.5, t.CGB), stripe(('north',), -0.72, 8.5, 0.5, t.CGB), disc(('north',), 4.6, 2.3, 1.1, t.CGO), disc(('north',), 4.6, 2.3, 0.7, t.CGB, glow=True))
    bs = under(suit, suit) + breastplate(chest) + waist(suit) + belt(metal) + shoulder(lf) + bracer(P(t.CG, trim(0.3, t.CGO), *vein, rect(SIDES, -1, 99, 2.0, 2.9, t.CGB)))
    bs += thigh(P(t.CG, trim(0.3, t.CGO), *vein)) + knee(lime) + boot(P(t.CG, trim(0.3, t.CGO), *vein, rect(SIDES, -1, 99, -1, 0.9, t.CGB)))
    for (z, L, rz, rx) in ((-1.8, 3.4, -38, -14), (0.0, 4.2, -46, 0), (1.8, 3.2, -36, 14)):
        bs += sym(leaf('rightArm', (-3.6, -2.0, z), L, 2.1, t.CG, t.CGB, t.CGO, rz=rz, rx=rx))
    # mask: closed, a dark visor across the eyes, glowing eyes, a gray chin
    mask = P(t.CG, trim(0.3, t.CGO), *vein, rect(('up',), -1, 99, -1, 99, t.CGL), rect(('north',), 1.0, 8.1, 4.0, 5.9, '#031603'), rect(('north',), 1.8, 3.6, 4.5, 5.4, t.CGB, glow=True),
             rect(('north',), 5.5, 7.3, 4.5, 5.4, t.CGB, glow=True), rect(('north',), 2.5, 6.6, 6.6, 8.4, t.CM), rect(('north',), 2.5, 6.6, 7.2, 7.6, t.CMD))
    bs += [box('head', -4.55, -8.55, -4.75, 9.1, 8.4, 9.3, mask)]
    # crest of leaves going back (flat side sideways so it reads from the side) and leaf-ears on the temples
    bs += [crest_leaf('head', (0.0, -8.5, z), L, 3.0, t.CG, t.CGB, t.CGO, rx=rx) for (z, L, rx) in ((-3.0, 5.0, -22), (-0.4, 6.0, -38), (2.2, 5.4, -56))]
    bs += sym(leaf('head', (-4.5, -6.2, 0.4), 5.0, 2.4, t.CG, t.CGB, t.CGO, rz=-70, rx=-12), leaf('head', (-4.5, -4.8, 1.2), 3.8, 2.0, t.CGD, t.CGB, t.CGO, rz=-92, rx=-16))
    # back: three leaves hanging down; a leaf crystal hovers above the right shoulder, like the set in Terraria
    for (x, L, rx) in ((-2.4, 5.0, -162), (0.0, 6.0, -158), (2.4, 5.0, -162)):
        bs += [leaf('body', (x, 0.6, 2.75), L, 2.4, t.CG, t.CGB, t.CGO, rx=rx, rz=x * 3)]
    cry = P(t.CGB, facets('#ffffff', 0.3), trim(0.15, '#e8ffd0'), glow=True, bevel=0.1)
    bs += [box('body', -10.0, -6.6, -1.0, 2.0, 2.8, 2.0, cry, rot=(0, 45, 0), pivot=(-9.0, -5.2, 0)), box('body', -9.5, -7.6, -0.5, 1.0, 1.0, 1.0, cry, rot=(0, 45, 0), pivot=(-9.0, -7.1, 0)),
           box('body', -9.5, -3.8, -0.5, 1.0, 1.0, 1.0, cry, rot=(0, 45, 0), pivot=(-9.0, -3.3, 0))]
    return bs


@slim('r7y', 7, 'Halite, like Botania Terrasteel: an open helmet with a ridge above the nose, a fan of leaves and branches, branches on the shoulders and boots')
def r7y():
    plate = P(t.CG, trim(0.3, t.CGO), mottle(t.CGD, 1.0, 0.78), mottle(t.CGB, 1.4, 0.92), rect(('up',), -1, 99, -1, 99, t.CGL))
    suit = P(t.CGD, trim(0.3, t.CGO))
    wood = P(BRANCH, trim(0.2, BRANCH_D), bevel=0.0)
    chest = P(t.CG, trim(0.3, t.CGO), mottle(t.CGD, 1.0, 0.78), mottle(t.CGB, 1.4, 0.92), disc(('north',), 4.6, 2.5, 1.2, BRANCH_D), disc(('north',), 4.6, 2.5, 0.8, t.CGB, glow=True))
    bs = under(suit, suit) + breastplate(chest) + waist(suit) + belt(P(BRANCH, trim(0.3, BRANCH_D), rect(('north',), 3.5, 5.5, 0.4, 1.9, t.CGB))) + shoulder(plate) + bracer(plate)
    bs += thigh(plate) + knee(wood) + boot(plate)
    # branches: two on the shoulder pointing up, one on the boot pointing out, with leaflets
    bs += sym(rod('rightArm', (-3.0, -2.0, -1.0), 3.6, 0.8, wood, rz=-16), rod('rightArm', (-2.2, -2.0, 1.2), 3.0, 0.8, wood, rz=-8),
              leaf('rightArm', (-4.0, -5.4, -1.0), 2.6, 1.6, t.CG, t.CGB, t.CGO, rz=-26), box('rightLeg', -3.4, 9.0, -2.0, 1.3, 0.9, 4.2, wood),
              leaf('rightLeg', (-3.0, 9.2, 1.4), 2.6, 1.5, t.CG, t.CGB, t.CGO, rx=-30, rz=-30))
    # helmet: cap and cheeks, a protrusion above the nose, branches along the cap, a fan of leaves on the crown
    bs += cap(plate, cheeks=1.6) + [box('head', -0.9, -8.4, -5.9, 1.8, 5.4, 1.2, P(t.CG, trim(0.3, t.CGO), rect(('north',), 0.5, 1.3, 0.6, 4.8, t.CGB)))]
    bs += sym(box('head', -2.6, -9.6, -3.8, 0.9, 1.1, 7.6, wood), box('head', -4.9, -7.6, -3.0, 0.5, 1.0, 6.8, wood))
    bs += [leaf('head', (x, -8.6, -1.2 + abs(x) * 0.5), L, 2.2, t.CG, t.CGB, t.CGO, rx=-14, rz=rz) for (x, L, rz) in ((-3.0, 4.2, -30), (-1.5, 5.2, -14), (0.0, 6.2, 0), (1.5, 5.2, 14), (3.0, 4.2, 30))]
    # back: a branch with three leaves instead of a cape
    bs += [box('body', -0.5, 0.2, 2.8, 1.0, 8.0, 0.6, wood)]
    bs += sym(rod('body', (-0.2, 3.4, 3.0), 3.4, 0.6, wood, rz=-62), leaf('body', (-3.1, 1.8, 3.0), 3.6, 2.1, t.CG, t.CGB, t.CGO, rz=-40), rod('body', (-0.2, 6.2, 3.0), 2.8, 0.6, wood, rz=-70),
              leaf('body', (-2.8, 5.2, 3.0), 3.0, 1.9, t.CG, t.CGB, t.CGO, rz=-55))
    bs += [leaf('body', (0.0, 0.6, 3.0), 3.6, 2.2, t.CG, t.CGB, t.CGO)]
    return bs


# ---- R7 Halite after the chlorophyte armor design: spire, plus with red eyes, twisted horns, collar, scales, fur, claws

CH_G, CH_D, CH_O, CH_L, CH_T, CH_P, CH_K = '#3cd400', '#2a9400', '#8fc83a', '#4cff10', '#10e060', '#dcfaa6', '#0c2404'
CH_GR, CH_GD = '#4c4c4c', '#2c2c2c'


def scales(faces, box_uv, cols, s=1.0):
    """Scales: rows offset by half a scale, colors mixed."""
    u0, u1, v0, v1 = box_uv
    cs = np.array([C(c) for c in cols])

    def d(p, face, b):
        if face.name not in faces:
            return cs[0], 0.0, False
        u, v = fuv(p, face)
        row = np.floor((v - v0) / s)
        col = np.floor((u - u0 + (row % 2) * s / 2) / s)
        i = ((row * 2 + col * 3).astype(int)) % len(cs)
        return cs[i], ((u >= u0) & (u <= u1) & (v >= v0) & (v <= v1)).astype(float), False
    return d


def diamond(faces, cu, cv, r, color):
    c = C(color)

    def d(p, face, b):
        if face.name not in faces:
            return c, 0.0, False
        u, v = fuv(p, face)
        return c, (np.abs(u - cu) + np.abs(v - cv) < r).astype(float), False
    return d


def neck_v(cu, h, k, wd, color):
    """Wedge at the neck: two lines from the top to the center, only in the upper h units of the face."""
    c = C(color)

    def d(p, face, b):
        if face.name != 'north':
            return c, 0.0, False
        u, v = fuv(p, face)
        return c, ((np.abs(np.abs(u - cu) - (h - v) * k) < wd) & (v < h)).astype(float), False
    return d


def wedge(wd, h, color):
    """Wedge inside a sharp plate: the same triangle, smaller."""
    c = C(color)

    def d(p, face, b):
        if face.name not in ('north', 'south'):
            return c, 0.0, False
        u, v = fuv(p, face)
        return c, ((np.abs(u - wd / 2) / (wd / 2) <= np.clip(v / h, 0, 1) - 0.28) & (v < h - 0.5)).astype(float), False
    return d


def spike(bone, start, L, wd, color, rx=0.0, rz=0.0):
    """Tuft of fur or a spike: flat, tapers toward the tip."""
    sx, sy, sz = start
    paint = P(color, alpha=feather_alpha(wd, L), bevel=0.0, noise=0.03)
    return box(bone, sx - wd / 2, sy - L, sz - 0.15, wd, L, 0.3, paint, rot=(rx, 0, rz), pivot=(sx, sy, sz))


@slim('r7z', 7, 'Halite, Chlorophyte armor: a sharp helmet with a black plus and red eyes, twisted horns, a tall collar, scales, fur and claws')
def r7z():
    g = P(CH_G, trim(0.3, CH_K))
    dark = P(CH_GD, trim(0.2, '#141414'))
    # torso: ovals on the chest, a dark wedge at the neck, scales in the middle, turquoise sides
    chest = P(CH_G, trim(0.3, CH_K), rect(('east', 'west'), 1.0, 99, 0.6, 99, CH_T), disc(('north',), 2.45, 3.1, 1.95, CH_K), disc(('north',), 6.75, 3.1, 1.95, CH_K), disc(('north',), 2.45, 3.1, 1.55, CH_O), disc(('north',), 6.75, 3.1, 1.55, CH_O),
              neck_v(4.6, 1.5, 1.5, 0.5, CH_GD), scales(('north',), (3.9, 5.3, 4.0, 7.0), (CH_GR, CH_O, CH_L)))
    mid = P(CH_T, trim(0.3, CH_K), rect(('north',), 2.2, 6.2, -1, 99, CH_K), scales(('north',), (2.6, 5.8, -1, 99), (CH_GR, CH_O, CH_L)), rect(('south',), 2.2, 6.2, -1, 99, CH_D))
    bs = breastplate(chest) + waist(mid) + belt(P(CH_D, trim(0.3, CH_K), rect(('north',), 3.5, 5.1, 0.4, 1.9, CH_L)))
    # light band at the front
    bs += [box('body', -2.4, 11.3, -3.0, 4.8, 5.8, 0.3, P(CH_P, trim(0.3, CH_O)))]
    # pauldron is flat, with a sharp end pointing outward; collar - pointed flaps at the cheeks
    sh = P(CH_G, trim(0.35, CH_K), rect(('up',), 0.9, 4.1, 0.9, 4.9, CH_O))
    bs += sym(box('rightArm', -4.4, -2.5, -2.9, 5.0, 1.5, 5.8, sh, rot=(0, 0, 10), pivot=(-1, -2, 0)), box('rightArm', -5.6, -2.3, -1.4, 1.4, 1.0, 2.8, P(CH_L, trim(0.3, CH_K)), rot=(0, 0, 10), pivot=(-1, -2, 0)))
    flap = P(CH_L, trim(0.3, CH_K), rect(('north', 'south'), 1.6, 3.2, 3.2, 99, CH_G), alpha=feather_alpha(4.8, 7.0), bevel=0.0)
    bs += sym(box('body', -7.6, -6.2, -1.8, 4.8, 7.0, 0.4, flap, rot=(10, 0, -16), pivot=(-5.2, 0.8, -1.6)))
    # arm: shoulder and forearm with inserts, a dark ring, a glove, a disc on the wrist, fur, claws
    arm = P(CH_G, trim(0.3, CH_K), rect(('west', 'east'), 1.4, 3.0, 0.5, 99, CH_O), rect(('north', 'south'), 0.8, 1.6, 0.5, 99, CH_T))
    bs += sym(bx('rightArm', -3, -2.0, -2, 4, 5.6, 4, arm, s=0.14), bx('rightArm', -3, 3.6, -2, 4, 3.8, 4, arm), bx('rightArm', -3, 7.4, -2, 4, 0.9, 4, dark), bx('rightArm', -3, 8.3, -2, 4, 1.7, 4, g))
    gem = P(CH_D, trim(0.2, CH_K), disc(('west', 'east'), 1.3, 1.3, 1.2, CH_T), rect(('west', 'east'), 1.05, 1.55, 0.3, 2.3, CH_L), rect(('west', 'east'), 0.3, 2.3, 1.05, 1.55, CH_L))
    bs += sym(box('rightArm', -3.55, 4.5, -1.3, 0.35, 2.6, 2.6, gem))
    for (z, L, rz) in ((-1.4, 2.2, -112), (0.0, 2.8, -96), (1.4, 2.2, -80)):
        bs += sym(spike('rightArm', (-3.1, 8.0, z), L, 1.2, CH_GR, rz=rz))
    bs += sym(*[box('rightArm', x, 10.1, -1.9, 0.6, 1.2, 0.6, dark) for x in (-2.7, -1.5, -0.3)])
    # leg: thigh with inserts, a knee guard with a lime diamond, shin, a dark ankle with fur, a bright lime boot
    leg = P(CH_G, trim(0.3, CH_K), rect(('north', 'south'), 0.6, 1.3, 0.5, 99, CH_O), rect(('north', 'south'), 2.6, 3.6, 0.5, 99, CH_T), rect(('east', 'west'), 1.4, 3.0, 0.5, 99, CH_O))
    shin = P(CH_G, trim(0.3, CH_K), rect(SIDES, 1.5, 2.9, 0.3, 99, CH_O))
    kn = P(CH_D, trim(0.3, CH_K), diamond(('north',), 2.2, 1.2, 1.15, CH_L))
    bs += sym(bx('rightLeg', -2, 0, -2, 4, 5.4, 4, leg), box('rightLeg', -2.2, 5.0, -2.75, 4.4, 2.4, 0.55, kn), bx('rightLeg', -2, 5.6, -2, 4, 1.0, 4, dark, s=0.14), bx('rightLeg', -2, 6.9, -2, 4, 2.5, 4, shin),
              bx('rightLeg', -2, 9.4, -2, 4, 0.8, 4, dark), bx('rightLeg', -2, 10.3, -3.1, 4, 1.7, 5.1, P(CH_L, trim(0.3, CH_D), rect(('up',), -1, 99, 3.0, 99, CH_G))))
    bs += sym(spike('rightLeg', (-2.1, 9.8, 0.6), 2.8, 1.3, CH_GR, rz=-100), spike('rightLeg', (-2.1, 9.8, 1.8), 2.4, 1.2, CH_GR, rz=-74))
    # helmet: closed, four inserts around a black plus, red eyes
    helm_ = P(CH_G, trim(0.3, CH_K), rect(('north',), 0.8, 3.7, 0.8, 8.0, CH_K), rect(('north',), 5.4, 8.3, 0.8, 8.0, CH_K), rect(('north',), 1.1, 3.4, 1.1, 7.7, CH_O), rect(('north',), 5.7, 8.0, 1.1, 7.7, CH_O),
              rect(('north',), 1.2, 7.9, 4.2, 5.5, '#050505'), rect(('north',), 3.9, 5.2, 0.7, 8.2, '#050505'), rect(('north',), 1.9, 3.3, 4.5, 5.2, '#ff1010', glow=True), rect(('north',), 5.8, 7.2, 4.5, 5.2, '#ff1010', glow=True),
              disc(('east', 'west'), 4.7, 4.2, 3.0, CH_K), disc(('east', 'west'), 4.7, 4.2, 2.7, CH_O), rect(('up',), 3.3, 5.8, -1, 99, CH_T))
    bs += [box('head', -4.55, -8.55, -4.75, 9.1, 8.4, 9.3, helm_)]
    # spire like a mitre: sharp plates one behind another, a wedge insert at the front
    def peak(wd, h, z, base, inset):
        def full(p, face, b):
            return (np.abs(p[..., 0] - wd / 2) / (wd / 2) <= np.clip(p[..., 1] / h, 0, 1) + 0.02).astype(float)
        pp = P(base, trim(0.3, CH_K, faces=('east', 'west', 'up', 'down')), *([wedge(wd, h, inset)] if inset else []), alpha=full, bevel=0.0)
        return box('head', -wd / 2, -8.3 - h, z, wd, h, 0.9, pp, rot=(-12, 0, 0), pivot=(0, -8.3, z))
    bs += [peak(8.6, 7.4, -4.4, CH_L, CH_T), peak(7.4, 6.2, -3.5, CH_G, CH_D), peak(6.0, 5.0, -2.6, CH_G, None), peak(4.4, 3.6, -1.7, CH_D, None)]
    # horns: thick, ringed, curling outward and down; a lime setting at the base
    horn = P(CH_GR, bands((CH_GR, CH_GD), 1.0, axis=1), bevel=0.0, noise=0.03)
    bs += sym(*chain('head', (-4.7, -6.6, 0.4), ((2.8, 8, -12), (2.8, 10, -42), (2.8, 12, -76), (2.6, 12, -112), (2.4, 10, -148), (2.2, 8, -184), (1.8, 6, -218)), 2.3, horn, taper=0.86))
    bs += sym(box('head', -5.15, -8.0, -1.3, 0.6, 3.4, 3.4, P(CH_L, trim(0.25, CH_K))))
    # back: three sharp leaves hanging down
    for (x, L, rx, rz) in ((-2.4, 5.4, -162, -7), (0.0, 6.6, -158, 0), (2.4, 5.4, -162, 7)):
        bs += [leaf('body', (x, 0.6, 2.75), L, 2.5, CH_G, CH_L, CH_K, rx=rx, rz=rz)]
    return bs


if __name__ == '__main__':
    for k in sys.argv[1:] or KEYS:
        w.build(k)
