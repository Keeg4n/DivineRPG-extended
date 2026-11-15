"""Wanderer armor concepts R8 - for completing Vethea (after Lady Luna). Three looks: spectral, in the spirit of Terraria's wraith armor,
lunar (Lady Luna) and dreamer (Vethea is the world of dreams). Body on Botania proportions (wslim details), no capes, a themed item on the back.

Run: python3 wr8.py [key ...]  (without keys - all); images go to wconcepts.OUT.
"""
import math
import sys

import numpy as np

import wconcepts as w
from wconcepts import P, box, chain, disc, mottle, rect, stripe, sym, trim
from wslim import SIDES, belt, boot, bracer, breastplate, knee, shoulder, thigh, under, waist
from wslim5 import KEYS, bands, diamond, slim
import wr1  # noqa: F401  (parts: closed helmet, ring on the back)
from wr1 import gate, helm

MINE = len(KEYS)


def gdiamond(faces, cu, cv, r, color):
    """A glowing diamond as a decal."""
    c = w.C(color)

    def d(p, face, b):
        if face.name not in faces:
            return c, 0.0, False
        u, v = w.fuv(p, face)
        return c, (np.abs(u - cu) + np.abs(v - cv) < r).astype(float), True
    return d


def tatter_alpha(wd, L):
    """Ragged ribbon hanging down: tapers toward the lower end (the bottom of the box), the edge in teeth."""
    def a(p, face, b):
        x = np.abs(p[..., 0] - wd / 2) / (wd / 2)
        y = p[..., 1]
        jag = 0.12 * np.sin(y * 3.1 + wd)
        return (x <= np.clip((L - y) / (L * 0.55), 0, 1) + jag + 0.02).astype(float)
    return a


def tatter(bone, x, y, z, wd, L, paint, rx=0.0, rz=0.0):
    """Ghostly ribbon: hangs down from point (x, y, z)."""
    return box(bone, x - wd / 2, y, z - 0.12, wd, L, 0.24, paint, rot=(rx, 0, rz), pivot=(x, y, z))


def ghost(color, edge, glowc, wd, L):
    """Ribbon material: gets lighter and glows toward the end."""
    return P(color, rect(('north', 'south'), -1, 99, L * 0.55, 99, glowc, glow=True), trim(0.15, edge), alpha=tatter_alpha(wd, L), bevel=0.0, noise=0.03)


# ---- A. Wraith: light plate, ragged ghostly ribbons, a hood with a white mask, little lights behind the back

SP, SP_L, SP_M, SP_D, SP_K, SP_G = '#8ccff4', '#e2f6ff', '#4f8fd0', '#22407a', '#0c1430', '#8af8ff'


@slim('r8a', 8, 'Phantom: pale light blue plate armor with glowing lines, ragged ghostly ribbons from the belt, a hood with a smooth white mask, three wisps behind the back')
def r8a():
    suit = P(SP_K, trim(0.25, '#050a1c'))
    plate = P(SP, trim(0.3, SP_D), mottle(SP_L, 1.6, 0.9))
    glowline = (stripe(('north',), 0.0, 2.6, 0.32, SP_G, glow=True), stripe(('north',), 0.0, 6.6, 0.32, SP_G, glow=True))
    chest = P(SP, trim(0.3, SP_D), mottle(SP_L, 1.6, 0.9), *glowline, diamond(('north',), 4.6, 2.6, 1.7, SP_D), gdiamond(('north',), 4.6, 2.6, 1.1, SP_G))
    bs = under(suit, suit) + breastplate(chest) + waist(P(SP_M, trim(0.3, SP_D))) + belt(P(SP_D, trim(0.2, SP_K), rect(('north',), 3.6, 5.4, 0.4, 1.6, SP_G, glow=True)), h=1.5)
    bs += shoulder(P(SP, trim(0.3, SP_G, glow=True), mottle(SP_L, 1.6, 0.9)), h=3.0, out=0.7) + bracer(P(SP, trim(0.3, SP_D), rect(SIDES, -1, 99, 2.4, 3.0, SP_G, glow=True)))
    bs += thigh(suit) + knee(P(SP_L, trim(0.2, SP_M))) + boot(P(SP, trim(0.3, SP_D), rect(SIDES, -1, 99, 2.9, 99, SP_G, glow=True)), toe=1.2)
    # ragged ribbons from the belt: front, sides and back, of different lengths
    for (x, z, wd, L, rx) in ((-2.7, -2.85, 2.6, 7.2, 10), (0.0, -2.85, 2.6, 8.6, 8), (2.7, -2.85, 2.6, 6.6, 10),
                              (-2.7, 2.85, 2.6, 7.8, -10), (0.0, 2.85, 2.6, 9.0, -8), (2.7, 2.85, 2.6, 7.4, -10)):
        bs.append(tatter('body', x, 11.2, z, wd, L, ghost(SP, SP_M, SP_G, wd, L), rx=rx))
    for (z, L) in ((-1.2, 6.8), (1.2, 7.6)):
        bs += sym(tatter('body', -4.45, 11.2, z, 2.2, L, ghost(SP, SP_M, SP_G, 2.2, L), rz=10))
    # hood: dark blue, the face is a smooth white mask with narrow light-blue slits
    hoodp = P(SP_D, trim(0.3, SP_K), stripe(('east', 'west'), 0.0, 4.6, 0.3, SP_M))
    front = P(SP_D, trim(0.3, SP_K), rect(('north',), 1.0, 8.2, 1.4, 99, SP_K), rect(('north',), 1.8, 7.4, 2.2, 8.6, SP_L),
              rect(('north',), 2.4, 4.0, 4.4, 4.9, SP_G, glow=True), rect(('north',), 5.2, 6.8, 4.4, 4.9, SP_G, glow=True), rect(('north',), 4.3, 4.9, 6.4, 7.8, '#b8d4e8'))
    bs += [box('head', -4.6, -9.1, -4.7, 9.2, 9.1, 9.4, front), box('head', -4.0, -9.8, 2.0, 8.0, 8.4, 3.0, hoodp, rot=(-14, 0, 0), pivot=(0, 0, 0)),
           box('head', -3.2, -10.3, 3.6, 6.4, 6.8, 2.6, hoodp, rot=(-28, 0, 0), pivot=(0, 0, 0))]
    # little lights behind the back: three glowing clots in an arc, each with a tail
    wisp = P(SP_G, trim(0.3, SP_L, glow=True), glow=True, bevel=0.0)
    tail = P(SP_L, glow=True, bevel=0.0, noise=0.02)
    for (x, y, s) in ((-4.6, -1.2, 1.5), (0.0, -3.0, 1.8), (4.6, -1.2, 1.5)):
        bs += [box('body', x - s / 2, y - s / 2, 4.6, s, s, s, wisp), box('body', x - s / 4, y + s / 2, 4.85, s / 2, 2.2, s / 2, tail)]
    return bs


# ---- B. Lunar: dark blackened steel with silver trim and lilac glow; a crescent as a mark on the chest and pauldrons
# (12.11: the 3D crescents above the helmet, behind the back and the ridges on the shoulders were removed - "too much", plate is dark instead of silver)

MN, MN_M, MN_D, MN_K, LI, LI_G = '#3a3f52', '#9aa3bb', '#1e2230', '#0b0c16', '#4e3c7a', '#d6bfff'


def crescent(faces, cu, cv, r, color, glow=False):
    """Crescent as a decal: a circle minus a shifted circle (horns pointing right-up)."""
    c = w.C(color)

    def d(p, face, b):
        if face.name not in faces:
            return c, 0.0, False
        u, v = w.fuv(p, face)
        inner = np.hypot(u - cu, v - cv) < r
        cut = np.hypot(u - cu - r * 0.5, v - cv + r * 0.25) < r * 0.82
        return c, (inner & ~cut).astype(float), glow
    return d


def arc(bone, cx, cy, z, r, a0, a1, n, t0, t1, paint, depth=0.8):
    """Arc of bars in the plane of the back, thickness from t0 to t1 (sickle)."""
    out = []
    for i in range(n):
        f = (i + 0.5) / n
        a = math.radians(a0 + (a1 - a0) * f)
        t = t0 + (t1 - t0) * (1 - abs(2 * f - 1))
        L = 2 * math.pi * r * abs(a1 - a0) / 360.0 / n * 1.18
        px, py = cx + r * math.cos(a), cy + r * math.sin(a)
        out.append(box(bone, px - L / 2, py - t / 2, z, L, t, depth, paint, rot=(0, 0, math.degrees(a) + 90), pivot=(px, py, z + depth / 2)))
    return out


@slim('r8b', 8, 'Lunar: dark plate armor with a silver edging and a lilac glow, a crescent as a sign on the chest and pauldrons, a smooth helmet with a lilac visor')
def r8b():
    suit = P(MN_K, trim(0.25, '#05050c'))
    silver = P(MN, trim(0.3, MN_M))
    chest = P(MN, trim(0.3, MN_M), crescent(('north',), 4.6, 2.8, 2.0, LI_G, glow=True), stripe(('north',), 0.0, 4.6, 0.25, MN_M))
    bs = under(suit, suit) + breastplate(chest) + waist(P(LI, trim(0.3, MN_D))) + belt(P(MN_M, trim(0.2, MN_D), disc(('north',), 4.5, 0.9, 0.7, LI_G, glow=True)), h=1.6)
    bs += shoulder(P(MN, trim(0.3, MN_M), rect(SIDES, -1, 99, 2.0, 2.5, LI), crescent(('west',), 2.6, 1.0, 0.95, LI_G, glow=True)), top=-2.4, h=3.4, out=1.0)
    bs += bracer(P(MN, trim(0.3, MN_M), rect(SIDES, -1, 99, 2.3, 2.8, LI)))
    bs += thigh(P(LI, trim(0.3, MN_D))) + knee(silver) + boot(P(MN, trim(0.3, MN_M), rect(SIDES, -1, 99, 3.0, 99, LI_G, glow=True)), toe=1.2)
    # helmet: smooth, lilac visor
    bs += [helm(P(MN, trim(0.3, MN_M), rect(('north',), 1.2, 7.9, 3.8, 5.6, MN_K), rect(('north',), 1.7, 7.4, 4.3, 5.1, LI_G, glow=True), rect(('north',), 4.1, 5.0, 0.4, 3.6, MN_M)))]
    return bs


# ---- V. Dreamer: indigo with stars, a mask with closed eyes and a third eye, a dreamcatcher on the back

DR, DR_D, DR_L, DR_K, GOLDD, TEAL = '#3a2a6a', '#1c1438', '#6a58b0', '#0e0a1e', '#e3c46a', '#7ff0d8'
STARS = (mottle('#f4f0ff', 5.0, 0.955), mottle(DR_L, 1.6, 0.78))


@slim('r8c', 8, 'Dreamer: indigo in stars with a gold thread, a smooth mask with closed eyes and a glowing third eye, a dreamcatcher with feathers on the back')
def r8c():
    suit = P(DR_K, trim(0.25, '#05030c'))
    cloth = P(DR, *STARS, trim(0.3, GOLDD))
    chest = P(DR, *STARS, trim(0.3, GOLDD), diamond(('north',), 4.6, 2.6, 2.0, GOLDD), diamond(('north',), 4.6, 2.6, 1.5, DR_D), disc(('north',), 4.6, 2.6, 0.75, TEAL, glow=True))
    bs = under(suit, suit) + breastplate(chest) + waist(P(DR_D, *STARS, trim(0.3, DR))) + belt(P(GOLDD, trim(0.2, '#8a6a20'), disc(('north',), 4.5, 0.9, 0.6, TEAL, glow=True)), h=1.4)
    bs += shoulder(P(DR, *STARS, trim(0.3, GOLDD)), h=3.2, out=0.8) + bracer(P(DR, *STARS, trim(0.3, GOLDD), rect(SIDES, -1, 99, 2.4, 2.9, GOLDD)))
    bs += thigh(cloth) + knee(P(GOLDD, trim(0.2, '#8a6a20'))) + boot(P(DR, *STARS, trim(0.3, GOLDD), rect(SIDES, -1, 99, -1, 0.8, GOLDD)), toe=1.1)
    # mask: smooth, closed eyes as golden arcs, an open third eye on the forehead
    mask = P(DR, *STARS, trim(0.3, GOLDD),
             rect(('north',), 1.8, 3.9, 4.9, 5.3, GOLDD), rect(('north',), 2.1, 3.6, 5.3, 5.6, GOLDD), rect(('north',), 5.2, 7.3, 4.9, 5.3, GOLDD), rect(('north',), 5.5, 7.0, 5.3, 5.6, GOLDD),
             diamond(('north',), 4.55, 2.5, 1.3, GOLDD), disc(('north',), 4.55, 2.5, 0.85, TEAL, glow=True), disc(('north',), 4.55, 2.5, 0.35, DR_K))
    bs += [helm(mask)]
    bs += sym(box('head', -5.0, -8.2, -1.0, 0.45, 4.0, 3.8, P(GOLDD, trim(0.2, '#8a6a20'), disc(('west', 'east'), 1.9, 2.0, 0.7, TEAL, glow=True))))
    # dreamcatcher on the back: a golden ring, a web of threads, a bead in the center, three feathers hanging down
    ring = P(GOLDD, trim(0.15, '#8a6a20'), bevel=0.0)
    bs += gate(0.0, 3.4, 3.0, 3.2, 14, ring)
    thread = P('#efe6c8', bevel=0.0, noise=0.0)
    for a in range(0, 180, 30):
        r = math.radians(a)
        bs.append(box('body', -3.1, 3.4 - 0.08, 3.2, 6.2, 0.16, 0.12, thread, rot=(0, 0, a), pivot=(0.0, 3.4, 3.26)))
    bs += [box('body', -0.6, 2.8, 3.1, 1.2, 1.2, 0.5, P(TEAL, glow=True))]
    feather = P('#efe6f8', rect(('north', 'south'), -1, 99, 3.0, 99, DR_L), trim(0.1, DR), bevel=0.0, noise=0.03)
    for (x, L) in ((-1.6, 4.6), (0.0, 5.6), (1.6, 4.6)):
        bs.append(tatter('body', x, 6.8, 3.4, 1.2, L, P('#efe6f8', rect(('north', 'south'), -1, 99, L * 0.6, 99, DR_L), alpha=tatter_alpha(1.2, L), bevel=0.0, noise=0.03)))
        bs.append(box('body', x - 0.08, 6.4, 3.36, 0.16, 0.5, 0.12, thread))
    del feather
    return bs


# ---- Wraith, second round (09.11): body and ribbons as in A, hood in the armor color, the face differs


def ghost_body():
    """Wraith body (r8a) without the head and back."""
    suit = P(SP_K, trim(0.25, '#050a1c'))
    glowline = (stripe(('north',), 0.0, 2.6, 0.32, SP_G, glow=True), stripe(('north',), 0.0, 6.6, 0.32, SP_G, glow=True))
    chest = P(SP, trim(0.3, SP_D), mottle(SP_L, 1.6, 0.9), *glowline, diamond(('north',), 4.6, 2.6, 1.7, SP_D), gdiamond(('north',), 4.6, 2.6, 1.1, SP_G))
    bs = under(suit, suit) + breastplate(chest) + waist(P(SP_M, trim(0.3, SP_D))) + belt(P(SP_D, trim(0.2, SP_K), rect(('north',), 3.6, 5.4, 0.4, 1.6, SP_G, glow=True)), h=1.5)
    bs += shoulder(P(SP, trim(0.3, SP_G, glow=True), mottle(SP_L, 1.6, 0.9)), h=3.0, out=0.7) + bracer(P(SP, trim(0.3, SP_D), rect(SIDES, -1, 99, 2.4, 3.0, SP_G, glow=True)))
    bs += thigh(suit) + knee(P(SP_L, trim(0.2, SP_M))) + boot(P(SP, trim(0.3, SP_D), rect(SIDES, -1, 99, 2.9, 99, SP_G, glow=True)), toe=1.2)
    for (x, z, wd, L, rx) in ((-2.7, -2.85, 2.6, 7.2, 10), (0.0, -2.85, 2.6, 8.6, 8), (2.7, -2.85, 2.6, 6.6, 10),
                              (-2.7, 2.85, 2.6, 7.8, -10), (0.0, 2.85, 2.6, 9.0, -8), (2.7, 2.85, 2.6, 7.4, -10)):
        bs.append(tatter('body', x, 11.2, z, wd, L, ghost(SP, SP_M, SP_G, wd, L), rx=rx))
    for (z, L) in ((-1.2, 6.8), (1.2, 7.6)):
        bs += sym(tatter('body', -4.45, 11.2, z, 2.2, L, ghost(SP, SP_M, SP_G, 2.2, L), rz=10))
    return bs


def ghost_wisps():
    wisp = P(SP_G, trim(0.3, SP_L, glow=True), glow=True, bevel=0.0)
    tail = P(SP_L, glow=True, bevel=0.0, noise=0.02)
    out = []
    for (x, y, sz) in ((-4.6, -1.2, 1.5), (0.0, -3.0, 1.8), (4.6, -1.2, 1.5)):
        out += [box('body', x - sz / 2, y - sz / 2, 4.6, sz, sz, sz, wisp), box('body', x - sz / 4, y + sz / 2, 4.85, sz / 2, 2.2, sz / 2, tail)]
    return out


HOOD = (mottle(SP_L, 1.6, 0.9),)


def hood_back():
    """Hood steps going back, in the armor color."""
    hp = P(SP, trim(0.3, SP_D), *HOOD, stripe(('east', 'west'), 0.0, 4.6, 0.3, SP_G, glow=True))
    return [box('head', -4.0, -9.8, 2.0, 8.0, 8.4, 3.0, hp, rot=(-14, 0, 0), pivot=(0, 0, 0)), box('head', -3.2, -10.3, 3.6, 6.4, 6.8, 2.6, hp, rot=(-28, 0, 0), pivot=(0, 0, 0))]


@slim('r8d', 8, 'Phantom D: a hood in the armor color with a glowing edge, no face - a dark void and two light blue eyes')
def r8d():
    front = P(SP, trim(0.3, SP_D), *HOOD, rect(('north',), 0.8, 8.4, 1.2, 99, SP_G, glow=True), rect(('north',), 1.2, 8.0, 1.6, 99, '#050914'),
              rect(('north',), 2.3, 3.7, 4.5, 5.2, SP_G, glow=True), rect(('north',), 5.4, 6.8, 4.5, 5.2, SP_G, glow=True))
    return ghost_body() + [box('head', -4.6, -9.1, -4.7, 9.2, 9.1, 9.4, front)] + hood_back() + ghost_wisps()


@slim('r8e', 8, 'Phantom E: a hood in the armor color, the face is a clump of light with dark eyes and a dark mouth, a stone on the brow')
def r8e():
    front = P(SP, trim(0.3, SP_D), *HOOD, rect(('north',), 1.0, 8.2, 1.4, 99, SP_D), rect(('north',), 1.4, 7.8, 1.8, 99, '#9feaff', glow=True),
              rect(('north',), 2.2, 7.0, 2.2, 99, '#d8fbff', glow=True),
              rect(('north',), 1.9, 3.9, 4.1, 5.6, '#06122a'), rect(('north',), 5.3, 7.3, 4.1, 5.6, '#06122a'), rect(('north',), 3.4, 5.8, 6.8, 7.5, '#123a6a'))
    gem = P(SP_G, trim(0.2, SP_L, glow=True), glow=True, bevel=0.0)
    return ghost_body() + [box('head', -4.6, -9.1, -4.7, 9.2, 9.1, 9.4, front), box('head', -0.7, -8.6, -5.0, 1.4, 1.4, 0.4, gem)] + hood_back() + ghost_wisps()


@slim('r8f', 8, 'Phantom F: an open face, a hood in the armor color with a wisp circlet on the brow, the lower face covered by a scarf in the armor color with a light blue edge')
def r8f():
    hp = P(SP, trim(0.3, SP_D), *HOOD)
    brow = P(SP, trim(0.3, SP_D), *HOOD, rect(('north',), -1, 99, 0.6, 1.0, SP_G, glow=True))
    scarf = P(SP, trim(0.25, SP_D), *HOOD, rect(('north', 'east', 'west'), -1, 99, -1, 0.35, SP_G, glow=True))
    bs = ghost_body()
    bs += [box('head', -4.6, -9.1, -4.7, 9.2, 1.6, 9.4, brow)]  # hood cap above the forehead
    bs += sym(box('head', -4.6, -7.5, -4.7, 0.8, 7.5, 9.4, hp))  # hood sides
    bs += [box('head', -4.6, -7.5, 3.8, 9.2, 7.5, 0.9, hp)]  # back of the head
    bs += [box('head', -4.7, -2.6, -4.85, 9.4, 2.6, 1.4, scarf)]  # scarf on the lower half of the face, front only
    bs += [box('head', -0.6, -8.3, -4.95, 1.2, 1.2, 0.35, P(SP_G, glow=True))]  # little light on the hoop
    return bs + hood_back() + ghost_wisps()


# ---- Wraith, third round (09.11): deep hood - the brim sticks out forward, the cutout is rounded, darkness and eyes in the depths


def hole_mask(kind, W=9.2, H=9.1, half=3.2):
    """Cutout in the front wall of the hood (0 - hole) on the front faces only. kind: arch - a semicircle on top and straight sides down to the bottom,
    gothic - a pointed arch, oval - an oval with a chin at the bottom."""
    cu = W / 2

    def inside(u, v):
        if kind == 'arch':
            a, cv = half, 4.6
            return (v >= cv) & (np.abs(u - cu) < a) | (((u - cu) / a) ** 2 + ((v - cv) / 3.4) ** 2 < 1)
        if kind == 'gothic':
            a, cv, top = 3.1, 4.8, 1.0
            k = np.clip((v - top) / (cv - top), 0, 1) ** 0.6
            return (v >= cv) & (np.abs(u - cu) < a) | (v >= top) & (v < cv) & (np.abs(u - cu) < a * k)
        a, b, cv = 3.1, 3.7, 4.9
        return ((u - cu) / a) ** 2 + ((v - cv) / b) ** 2 < 1

    def m(p, face, b):
        if face.name not in ('north', 'south'):
            return np.ones(p.shape[:-1])
        return (~inside(p[..., 0], p[..., 1])).astype(float)

    def rim(p, face, b):
        """Glowing edge along the rim of the cutout."""
        c = w.C(SP_G)
        if face.name not in ('north',):
            return c, 0.0, False
        u, v = p[..., 0], p[..., 1]
        near = np.zeros(u.shape, bool)
        for du, dv in ((0.45, 0), (-0.45, 0), (0, 0.45), (0, -0.45)):
            near |= inside(u + du, v + dv)
        return c, (near & ~inside(u, v)).astype(float), True
    return m, rim


def deep_hood(kind, eyes='round', peak=False, glow_rim=True, gap=1.0, tip=False, half=3.2, depth=0.0):
    """Hood in the armor color: a brim with a cutout 2 forward from the face, a dark lining, and in the depths a dark plane with eyes."""
    shell = P(SP, trim(0.3, SP_D), *HOOD)
    m, rim = hole_mask(kind, half=half)
    brim = P(SP, trim(0.3, SP_D), *HOOD, *((rim,) if glow_rim else ()), alpha=m, bevel=0.0)
    dark = P('#0a1226', bevel=0.0, noise=0.02)
    h = gap / 2  # from the middle of the plane (3.6) to the inner edge of the eye
    if eyes == 'round':
        e1, e2 = rect(('north',), 3.6 - h - 1.2, 3.6 - h, 3.6, 4.4, SP_G, glow=True), rect(('north',), 3.6 + h, 3.6 + h + 1.2, 3.6, 4.4, SP_G, glow=True)
    else:  # narrow slits
        e1 = rect(('north',), 3.6 - h - 1.5, 3.6 - h, 3.9, 4.3, SP_G, glow=True)
        e2 = rect(('north',), 3.6 + h, 3.6 + h + 1.5, 3.9, 4.3, SP_G, glow=True)
    face = P('#03060f', e1, e2, bevel=0.0, noise=0.0)
    bs = [box('head', -4.6, -9.1, -6.0 + depth, 9.2, 9.1, 0.7, brim)]              # brim with a cutout (depth - closer to the face)
    bs += [box('head', -4.6, -9.1, -5.3, 9.2, 1.2, 10.0, shell)]                    # top
    bs += sym(box('head', -4.6, -7.9, -5.3, 1.0, 7.9, 10.0, shell))                 # sides
    bs += [box('head', -4.6, -7.9, 3.7, 9.2, 7.9, 1.0, shell)]                      # back of the head
    bs += [box('head', -3.8, -9.7, -5.6, 7.6, 0.6, 9.6, shell)]                     # crown rounded with a step
    # dark lining inside the brim and a plane in the depths (in front of the player's face)
    bs += [box('head', -3.6, -7.9, -5.3, 7.2, 0.25, 1.25, dark)] + sym(box('head', -3.6, -7.9, -5.3, 0.25, 7.9, 1.25, dark))
    bs += [box('head', -3.6, -7.9, -4.15, 7.2, 7.9, 0.1, face)]
    if tip:  # sharp hood tail going back and up, like a Nazgul
        bs += chain('head', (0.0, -8.4, 3.0), ((3.2, -78, 0), (3.0, -112, 0), (2.6, -140, 0), (2.0, -160, 0)), 3.4, shell, taper=0.72)
    if peak:  # canopy brim on top, slightly down
        bs += [box('head', -4.6, -9.6, -6.9, 9.2, 0.8, 1.6, shell, rot=(-12, 0, 0), pivot=(0, -9.2, -6.0))]
    return bs


@slim('r8g', 8, 'Phantom G: a deep hood in the armor color, an arched opening with a glowing edge, darkness and round eyes deep inside')
def r8g():
    return ghost_body() + deep_hood('arch') + hood_back() + ghost_wisps()


@slim('r8h', 8, 'Phantom H: a deep hood, a pointed opening without an edge, darkness and narrow eyes deep inside')
def r8h():
    return ghost_body() + deep_hood('gothic', eyes='slit', glow_rim=False) + hood_back() + ghost_wisps()


@slim('r8i', 8, 'Phantom I: a deep hood with an overhang on top, an oval opening with a glowing edge, darkness and round eyes deep inside')
def r8i():
    return ghost_body() + deep_hood('oval', peak=True) + hood_back() + ghost_wisps()


# ---- Wraith, fourth round (09.11): hood Zh with wider eyes, the top is a robe (inspired by Abaddon from Dota 2: a Nazgul in a robe)

FOLDS = (SP, '#7fc2ea', SP, '#78b8e0')


def robe(len_y=12.4, mantle=False, sleeves='wide', gauntlets=False):
    """Robe: loose cloth in folds from shoulders to thighs, a rope belt, sleeves; hem ribbons are as for the Wraith."""
    suit = P(SP_K, trim(0.25, '#050a1c'))
    cloth = P(SP, bands(FOLDS, 1.7), trim(0.25, SP_M), bevel=0.0, noise=0.03)
    front = P(SP, bands(FOLDS, 1.7), trim(0.25, SP_M), rect(('north',), 3.9, 4.9, 0.0, 99, SP_M), gdiamond(('north',), 4.4, 1.2, 0.9, SP_G), bevel=0.0, noise=0.03)
    bs = under(suit, suit)
    bs += [box('body', -4.45, -0.15, -2.8, 8.9, len_y, 5.6, front)]
    rope = P('#d6eefa', trim(0.15, SP_M), bevel=0.0, noise=0.02)
    bs += [box('body', -4.65, 7.4, -3.0, 9.3, 0.7, 6.0, rope), box('body', -1.6, 8.1, -3.05, 0.5, 3.2, 0.5, rope), box('body', -0.6, 8.1, -3.05, 0.5, 2.4, 0.5, rope)]
    bs += [box('body', -1.8, 11.0, -3.15, 0.9, 0.9, 0.9, P(SP_G, glow=True))]  # little light at the end of the rope
    if sleeves == 'wide':  # wide bell sleeves, glowing edge
        bs += sym(box('rightArm', -3.35, -2.1, -2.35, 4.7, 6.0, 4.7, cloth),
                  box('rightArm', -3.75, 3.9, -2.75, 5.5, 5.4, 5.5, P(SP, bands(FOLDS, 1.7), trim(0.25, SP_M), rect(SIDES, -1, 99, 4.7, 99, SP_G, glow=True), bevel=0.0, noise=0.03)))
    else:  # sleeve to the elbow
        bs += sym(box('rightArm', -3.45, -2.1, -2.45, 4.9, 6.2, 4.9, P(SP, bands(FOLDS, 1.7), trim(0.25, SP_M), rect(SIDES, -1, 99, 5.5, 99, SP_G, glow=True), bevel=0.0, noise=0.03)))
    if gauntlets:  # plate gloves from under the cloth
        bs += bracer(P(SP_D, trim(0.3, SP_K), rect(SIDES, -1, 99, 2.4, 3.0, SP_G, glow=True)), top=4.2)
    if mantle:  # mantle on the shoulders: two layers, short (not a cape), ragged glowing edge
        m1 = P(SP_M, bands((SP_M, SP_D, SP_M, '#3f7cbd'), 1.3), trim(0.2, SP_D), rect(SIDES, -1, 99, 2.6, 99, SP_G, glow=True), bevel=0.0, noise=0.03)
        bs += [box('body', -5.3, -0.6, -3.3, 10.6, 3.1, 6.6, m1), box('body', -4.9, -0.9, -3.0, 9.8, 1.3, 6.0, P(SP, trim(0.2, SP_M)))]
    bs += knee(P(SP_L, trim(0.2, SP_M))) + boot(P(SP_D if gauntlets else SP, trim(0.3, SP_K if gauntlets else SP_D), rect(SIDES, -1, 99, 2.9, 99, SP_G, glow=True)), toe=1.2)
    bs += thigh(suit)
    for (x, z, wd, L, rx) in ((-2.7, -2.95, 2.6, 7.6, 10), (0.0, -2.95, 2.6, 9.0, 8), (2.7, -2.95, 2.6, 7.0, 10),
                              (-2.7, 2.95, 2.6, 8.2, -10), (0.0, 2.95, 2.6, 9.4, -8), (2.7, 2.95, 2.6, 7.8, -10)):
        bs.append(tatter('body', x, len_y - 1.4, z, wd, L, ghost(SP, SP_M, SP_G, wd, L), rx=rx))
    for (z, L) in ((-1.2, 7.2), (1.2, 8.0)):
        bs += sym(tatter('body', -4.55, len_y - 1.4, z, 2.2, L, ghost(SP, SP_M, SP_G, 2.2, L), rz=10))
    return bs


def mist():
    """Mist at the hem and shoulders, like Abaddon's: small glowing wisps."""
    puff = P(SP_L, glow=True, bevel=0.0, noise=0.02)
    out = []
    for (bone, x, y, z, sz) in (('body', -5.2, 13.4, -2.2, 0.9), ('body', 4.6, 14.6, 1.6, 0.8), ('body', -3.4, 15.8, 2.8, 0.7), ('body', 2.6, 13.0, -3.3, 0.7),
                                ('body', 5.4, 0.6, -0.6, 0.6), ('body', -5.6, 1.4, 0.8, 0.7), ('rightLeg', -2.8, 9.6, -2.6, 0.6), ('rightLeg', 1.8, 10.4, 2.4, 0.5)):
        out.append(box(bone, x, y, z, sz, sz, sz, puff))
    return out


@slim('r8j', 8, 'Phantom J: the G hood, eyes wider; a robe in folds down to the thighs, a rope belt with a wisp, wide sleeves with a glowing edge')
def r8j():
    return robe() + deep_hood('arch', gap=2.2, half=3.6, depth=0.4) + hood_back() + ghost_wisps()


@slim('r8k', 8, 'Phantom K: the G hood, eyes wider; a robe with a short cape on the shoulders, sleeves to the elbow, dark plate gauntlets and boots')
def r8k():
    return robe(mantle=True, sleeves='short', gauntlets=True) + deep_hood('arch', gap=2.2, half=3.6, depth=0.4) + hood_back() + ghost_wisps()


@slim('r8l', 8, 'Phantom L (Abaddon style): a sharp hood tail to the back, eyes wider; a robe with a cape, wide sleeves, plate gauntlets, mist at the hem and shoulders')
def r8l():
    return robe(mantle=True, gauntlets=True) + deep_hood('arch', gap=2.2, half=3.6, depth=0.4, tip=True) + hood_back() + mist()


# ---- Wraith for the game (11.11): the Crown of Flight look on R8. Body as in A, hood Zh (arch cutout with a glowing edge), wider eyes;
# without the hood tail at the back and without flecks on the hood; inside the hood a smoothed arch tunnel from the brim to the darkness, the darkness and eyes
# in the depths. In the game the plate is translucent (TRANSLUCENT in tools/wanderer_armor.py), the tunnel and darkness are not (boxes with solid).


def arch_inside(cu, cv, half, rv):
    def inside(u, v):
        return (v >= cv) & (np.abs(u - cu) < half) | (((u - cu) / half) ** 2 + ((v - cv) / rv) ** 2 < 1)
    return inside


def hole_alpha(inside):
    def m(p, face, b):
        if face.name not in ('north', 'south'):
            return np.ones(p.shape[:-1])
        return (~inside(p[..., 0], p[..., 1])).astype(float)
    return m


def solid(b):
    b.solid = True
    return b


def ghost_hood(half=3.4, gap=2.2, layers=5):
    """Wraith hood for the game: a shell without flecks, a brim with an arch cutout and edge, a tunnel inward, darkness with eyes."""
    shell = P(SP, trim(0.3, SP_D))
    m, rim = hole_mask('arch', half=half)
    brim = P(SP, trim(0.3, SP_D), rim, alpha=m, bevel=0.0)
    bs = [box('head', -4.6, -9.1, -6.0, 9.2, 9.1, 0.7, brim)]                      # brim with a cutout
    bs += [box('head', -4.6, -9.1, -5.3, 9.2, 1.2, 10.0, shell)]                    # top
    bs += sym(box('head', -4.6, -7.9, -5.3, 1.0, 7.9, 10.0, shell))                 # sides
    bs += [box('head', -4.6, -7.9, 3.7, 9.2, 7.9, 1.0, shell)]                      # back of the head
    bs += [box('head', -3.8, -9.7, -5.6, 7.6, 0.6, 9.6, shell)]                     # crown rounded with a step
    # tunnel: thin plates inside the shell (x -3.6..3.6, y -7.9..0) with the same cutout, toward the depth the cutout narrows and darkens -
    # the walls form a smooth arch without the square corners of the lining
    z0, z1 = -5.3, -4.25
    t = (z1 - z0) / layers
    shades = ('#1d3a66', '#152c50', '#0f2040', '#0a1630', '#070f22', '#050a18')
    for i in range(layers):
        f = i / max(1, layers - 1)
        inside = arch_inside(3.6, 4.6 - 1.2 + 0.4 * f, half - 0.7 * f, 3.4 - 0.4 * f)
        bs.append(solid(box('head', -3.6, -7.9, z0 + i * t, 7.2, 7.9, t, P(shades[min(i, len(shades) - 1)], alpha=hole_alpha(inside), bevel=0.0, noise=0.0))))
    h = gap / 2
    e1 = rect(('north',), 3.6 - h - 1.2, 3.6 - h, 3.6, 4.4, SP_G, glow=True)
    e2 = rect(('north',), 3.6 + h, 3.6 + h + 1.2, 3.6, 4.4, SP_G, glow=True)
    bs.append(solid(box('head', -3.6, -7.9, -4.15, 7.2, 7.9, 0.1, P('#03060f', e1, e2, bevel=0.0, noise=0.0))))  # darkness with eyes
    return bs


@slim('r8m', 8, 'Phantom for the game: body A, the G hood without a tail and speckles, eyes wider and deeper, a smoothed arched tunnel inside; semi-transparent in the game')
def r8m():
    return ghost_body() + ghost_hood() + ghost_wisps()


MODELS = KEYS[MINE:]
BG = {'r8a': '#2a3654', 'r8b': '#30304a', 'r8c': '#2a2448', 'r8d': '#2a3654', 'r8e': '#2a3654', 'r8f': '#2a3654', 'r8g': '#2a3654', 'r8h': '#2a3654', 'r8i': '#2a3654', 'r8j': '#2a3654', 'r8k': '#2a3654', 'r8l': '#26304c', 'r8m': '#2a3654'}


def build(key):
    """Like wconcepts.build, but each model has its own background."""
    _, _, title, fn = w.CONCEPTS[key]
    model = w.mk.Model(key, w.mannequin() + fn(), atlas_width=1024)
    front, back = w.view(model, -32, 10, BG[key]), w.view(model, 148, 10, BG[key])
    sheet = w.Image.new('RGB', (front.width * 2, front.height))
    sheet.paste(front, (0, 0))
    sheet.paste(back, (front.width, 0))
    sheet.save(w.os.path.join(w.OUT, key + '.png'), optimize=True)
    print(key, title)


if __name__ == '__main__':
    for k in sys.argv[1:] or MODELS:
        build(k)
