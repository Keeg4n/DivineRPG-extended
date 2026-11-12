"""Концепты значков 16x16: материалы мобов сумеречных миров (рога кадиллионов и прочее), по три варианта на материал."""
import math

from acc_kit import DISC, ELL, RING, RECT, POLY, LINE, PATH, SYM, DIAMOND, HEART, Ic
from acc_pal import *

K = '#1a1320'
DROPS = []


def drop(key, name, mob, world, use, *vs):
    """vs - три варианта (название, функция значка)."""
    DROPS.append((key, name, mob, world, use, vs))


CALL = 'Зов кадиллиона и закалка брони и лука мира'
TEMPER = 'закалка брони и лука мира'


def horn(c, base, tip, ramp, w0=2.6, bands=None, band=None, curve=0.0):
    """Рог: конус от основания base к кончику tip, ширина у основания w0; bands - сколько полос спирали."""
    (x0, y0), (x1, y1) = base, tip
    pts_l, pts_r = [], []
    n = 12
    for i in range(n + 1):
        t = i / n
        bend = math.sin(t * math.pi) * curve
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy)
        nx, ny = -dy / ln, dx / ln
        cx, cy = x0 + dx * t + nx * bend, y0 + dy * t + ny * bend
        hw = w0 * (1 - t) ** 0.9 + 0.15
        pts_l.append((cx + nx * hw, cy + ny * hw))
        pts_r.append((cx - nx * hw, cy - ny * hw))
    m = POLY(*(pts_l + pts_r[::-1]))
    c.f(m, ramp)
    if bands:
        for i in range(1, bands + 1):
            t = i / (bands + 1)
            bend = math.sin(t * math.pi) * curve
            dx, dy = x1 - x0, y1 - y0
            ln = math.hypot(dx, dy)
            nx, ny = -dy / ln, dx / ln
            cx, cy = x0 + dx * t + nx * bend, y0 + dy * t + ny * bend
            hw = w0 * (1 - t) ** 0.9 + 0.6
            seg = LINE(cx + nx * hw, cy + ny * hw - 0.6, cx - nx * hw, cy - ny * hw + 0.6) & m
            c.c(seg, band or ramp[0])
    return m


# ------------------------------------------------------------------ Эдем
def feather(c):
    m = POLY((2.5, 13.5), (4, 9), (8, 4.5), (13.5, 1.5), (12, 6), (8.5, 10), (4.5, 13))
    c.f(m, EDEN)
    c.l(4, 12, 12, 3, EDEN[1])
    c.erase({(6, 7), (10, 3), (11, 7), (7, 11)})
    c.l(1, 15, 3, 13, BONE[2])
    c.d(WHITE[4], 11, 3, 9, 5)


def feather_sun(c):
    c.f(DISC(5, 5, 3.6), PUMP)
    for x, y in ((5, 0), (0, 5), (1, 1), (9, 1), (1, 9)):
        c.d(PUMP[3], x, y)
    m = POLY((4, 14), (6, 10), (9, 6.5), (14, 4), (13, 7.5), (9.5, 11), (6, 13.5))
    c.f(m, EDEN)
    c.l(5, 13, 13, 5, EDEN[1])
    c.l(2, 15, 4, 13, BONE[2])
    c.d(WHITE[4], 12, 5)


def feather_flame(c):
    m = POLY((3, 14), (3.5, 9), (6, 5.5), (8, 2), (9, 4.5), (12, 1.5), (11.5, 6), (13, 6.5), (10, 10.5), (6, 13.5))
    c.f(m, HELL)
    c.f(POLY((5, 12.5), (6, 8), (8.5, 5), (10.5, 3.5), (10, 7), (7.5, 10.5)), EDEN)
    c.l(4, 13, 9, 6, HELL[1])
    c.l(1, 15, 3, 13, BONE[2])
    c.d(WHITE[4], 9, 6)


def horn_spiral(c):
    m = horn(c, (3.5, 12.5), (13.5, 1.5), BONE, 3.0, bands=4, band=EDEN[2])
    c.erase({(1, 12), (2, 14), (4, 15), (2, 13), (3, 15)})
    c.d(EDEN[1], 2, 11, 3, 14, 5, 14)
    c.d(WHITE[4], 12, 3)


def horn_crystal(c):
    m = POLY((8, 0.5), (12.5, 6), (10, 15), (6, 15), (3.5, 6))
    c.f(m, BONE)
    c.c(LINE(8, 1, 8, 14), BONE[1])
    c.c(LINE(4, 6, 8, 9), BONE[3])
    c.c(LINE(12, 6, 8, 9), BONE[1])
    c.c(PATH((5, 12), (7, 10), (9, 11), (11, 8), w=1) & m, EDEN[2])
    c.d(WHITE[4], 7, 3, 6, 5)


def horn_pieces(c):
    horn(c, (2.5, 14), (7.5, 4), BONE, 2.2, bands=2, band=EDEN[2])
    horn(c, (8.5, 14.5), (14, 6.5), BONE, 2.3, bands=2, band=EDEN[2])
    c.d(EDEN[3], 1, 3, 12, 2, 6, 1)
    c.d(WHITE[4], 7, 5, 13, 7)


def root_gnarled(c):
    c.c(PATH((3, 14), (5, 11), (5, 8), (8, 6), (11, 7), (13, 10), w=2), WOOD[2])
    c.c(PATH((5, 11), (3, 9)), WOOD[1])
    c.c(PATH((11, 7), (12, 4)), WOOD[1])
    c.c(PATH((13, 10), (14, 13)), WOOD[1])
    c.l(4, 13, 6, 9, WOOD[4])
    c.f(ELL(7.5, 3.5, 2.6, 1.6), GRN)
    c.c(LINE(8, 5, 8, 6), GRN[1])
    c.d(LIME[3], 6, 3, 10, 2)


def root_mandrake(c):
    c.f(ELL(8, 9, 3.6, 4.2), WOOD)
    c.c(PATH((6, 12), (5, 15)), WOOD[1])
    c.c(PATH((10, 12), (11, 15)), WOOD[1])
    c.c(PATH((5, 9), (2, 11)), WOOD[1])
    c.c(PATH((11, 9), (14, 11)), WOOD[1])
    c.d(K, 7, 8, 9, 8)
    c.l(7, 10, 9, 10, WOOD[0])
    c.f(POLY((8, 5.5), (4, 1), (7, 3), (8, 0.5), (9, 3), (12, 1)), GRN)
    c.d(LIME[3], 5, 2, 11, 2)


def root_bundle(c):
    for a, b, d in (((1.5, 11), (12.5, 1.5), WOOD[2]), ((3, 14), (14, 4.5), WOOD[3]), ((6, 15), (15, 7.5), WOOD[2])):
        c.c(PATH(a, (8.5, 8.5), b, w=2), d)
    c.c(LINE(6, 6, 10, 11, 2), BONE[2])
    c.c(LINE(6, 6, 10, 11), BONE[3])
    c.f(ELL(13, 2, 1.8, 1.2) | ELL(14.5, 5, 1.2, 1.6), GRN)
    c.d(LIME[3], 12, 1, 15, 4)
    c.d(WOOD[4], 3, 11, 5, 13)



# ------------------------------------------------------------------ Дикий лес
def fang(c):
    m = POLY((3.5, 1), (11.5, 1), (12.5, 5), (11, 10), (7, 15), (7.5, 9), (5.5, 5))
    c.f(m, BONE)
    c.f(POLY((4, 1), (11, 1), (10.5, 3.5), (5, 3.5)), BRN)
    c.l(5, 2, 10, 2, BRN[3])
    c.l(10, 5, 8.5, 11, BONE[1])
    c.d(WHITE[4], 6, 5, 7, 6)
    c.d(WILD[3], 8, 13, 7, 14)


def fang_jaw(c):
    m = (ELL(8, 9, 7, 6) - ELL(8, 6.5, 5, 5)) & {(x, y) for x in range(16) for y in range(7, 16)}
    c.f(m, BONE)
    c.c(LINE(3, 12, 12, 12) & m, BONE[1])
    for x, h in ((3, 3.5), (12, 3.5)):
        c.f(POLY((x - 1.6, 9), (x + 1.6, 9), (x, h)), WHITE)
    for x in (6, 8, 10):
        c.f(POLY((x - 0.9, 12.5), (x + 0.9, 12.5), (x, 10)), WHITE)
    c.d(WILD[3], 2, 9, 13, 9)


def fang_frost(c):
    m = POLY((3, 1.5), (9, 1.5), (10, 6), (8, 11), (4, 15), (6, 8), (4.5, 4.5))
    c.f(m, BONE)
    c.f(POLY((8, 11), (5, 13.5), (4, 15), (6, 10)), ICE)
    c.f(RECT(3, 1, 9, 2), WILD)
    for x, y in ((12, 9), (13, 12), (11, 13), (12, 4)):
        c.c({(x, y), (x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)}, ICE[3])
    c.d(WHITE[4], 5, 5)


def moon_crescent(c):
    m = DISC(7.5, 8, 6.4) - DISC(10.5, 6.5, 5.4)
    c.f(m, WILD)
    c.c(LINE(3, 10, 6, 13) & m, WILD[4])
    c.d(WHITE[4], 2, 7, 3, 11)
    c.d(SKY[4], 13, 12, 14, 13, 12, 13)


def moon_spiral(c):
    horn(c, (4, 13), (13, 2), WILD, 2.8, bands=4, band=WILD[0])
    c.f(DISC(4, 13, 2.2) - DISC(5, 12, 1.6), SILV)
    c.d(WHITE[4], 12, 3)


def moon_gem(c):
    horn(c, (6, 11), (13.5, 1.5), SKYT, 2.4, bands=3, band=WILD[2])
    c.f(RECT(2, 10, 9, 12), SILV)
    c.f(DIAMOND(5, 13, 2.6), WILD)
    c.d(WHITE[4], 4, 12)


def moss_stone(c):
    m = DISC(8, 8.5, 6.4)
    c.f(m, IRON)
    c.c((ELL(6, 4.5, 4, 2.2) | ELL(11.5, 10, 2, 3) | ELL(4, 11, 2, 1.6)) & m, GRN[2])
    c.d(GRN[3], 5, 3, 7, 3, 12, 8, 3, 10)
    c.d(GRN[1], 8, 6, 11, 12, 5, 12)
    c.d(SILV[3], 5, 7, 6, 8)


def moss_rune(c):
    top = POLY((8, 2), (14, 5), (8, 8), (2, 5))
    left = POLY((2, 5), (8, 8), (8, 15), (2, 12))
    right = POLY((8, 8), (14, 5), (14, 12), (8, 15))
    c.f(left, IRON, grad=False)
    c.f(right, IRON[0:3], grad=False)
    c.f(top, GRN)
    c.c(PATH((4, 8), (6, 10), (4, 12)), WILD[3])
    c.c(PATH((10, 9), (12, 9), (11, 12)), WILD[3])
    c.d(LIME[3], 7, 4, 10, 5)


def moss_crack(c):
    m = POLY((2, 9), (4, 4), (9, 2), (13.5, 5), (14, 11), (10, 14.5), (4, 14))
    c.f(m, IRON)
    c.c(ELL(7, 3.5, 4.5, 1.8) & m, GRN[2])
    c.d(GRN[3], 5, 3, 9, 3)
    c.c(PATH((5, 8), (8, 9), (10, 7), (12, 10)), WILD[4])
    c.c(PATH((8, 9), (7, 12)), WILD[3])
    c.d(SKY[4], 8, 9)


# ------------------------------------------------------------------ Апалачия
def heart_cut(c):
    m = HEART(8, 8.5, 6.6)
    c.f(m, APAL)
    c.c(LINE(8, 4, 8, 14) & m, APAL[1])
    c.c(LINE(2, 6, 8, 9) & m, APAL[3])
    c.c(LINE(14, 6, 8, 9) & m, APAL[1])
    c.d(WHITE[4], 4, 4, 5, 3)


def heart_druse(c):
    c.f(ELL(8, 13, 6, 2.2), IRON)
    for (bx, tx, ty, w) in ((4.5, 3, 5, 1.8), (8, 8, 1.5, 2.2), (11.5, 13, 5, 1.8), (6.5, 6, 7, 1.4), (10, 10.5, 7, 1.4)):
        c.f(POLY((bx - w, 12.5), (bx + w, 12.5), (tx + 0.5, ty)), APAL)
    c.d(WHITE[4], 7, 4, 3, 7, 12, 7)


def heart_stone(c):
    m = HEART(8, 8.5, 6.6)
    c.f(m, IRON)
    c.c(PATH((4, 6), (6, 8), (8, 7), (10, 10), (12, 8)) & m, APAL[3])
    c.c(PATH((8, 7), (8, 12)) & m, APAL[2])
    c.f(DISC(8, 8.5, 1.6), APAL)
    c.d(MAG[4], 8, 8)


def heart_cube(c):
    """Аметистовый куб с сердцем-руной: как рунный куб мшистого ядра (6Б), только целиком из аметиста."""
    top = POLY((8, 2), (14, 5), (8, 8), (2, 5))
    left = POLY((2, 5), (8, 8), (8, 15), (2, 12))
    right = POLY((8, 8), (14, 5), (14, 12), (8, 15))
    c.f(left, OBS[2:5], grad=False)
    c.f(right, OBS[1:4], grad=False)
    c.f(top, APAL[2:])
    c.c(LINE(8, 8, 8, 14), OBS[0])
    c.c({(4, 8), (6, 8), (3, 9), (4, 9), (5, 9), (6, 9), (7, 9), (4, 10), (5, 10), (6, 10), (5, 11)}, MAG[3])
    c.d(WHITE[4], 4, 9)
    c.c(PATH((10, 9), (12, 9), (11, 12)), MAG[3])
    c.d(WHITE[4], 8, 4, 10, 5)


def dusk_spiral(c):
    horn(c, (3.5, 12.5), (13.5, 1.5), APAL, 3.4, bands=4, band=APAL[1])
    c.d(WHITE[4], 12, 3, 6, 8)


def dusk_gradient(c):
    m = horn(c, (3, 13.5), (12.5, 1.5), APAL, 3.4, curve=-2.2)
    c.f({p for p in m if 4 <= p[1] < 8}, PUR)
    c.f({p for p in m if p[1] < 4}, LAP)
    c.d(WHITE[4], 8, 5, 11, 2, 5, 10)


def dusk_stars(c):
    m = horn(c, (3.5, 12.5), (13.5, 1.5), OBS, 3.4, curve=1.2)
    c.d(APAL[4], 5, 10, 7, 9, 9, 6, 11, 4, 6, 12)
    c.d(WHITE[4], 8, 8, 12, 3)
    c.c((DISC(3.5, 12.5, 3.6) - DISC(3.5, 12.5, 2.6)) & m, APAL[2])


def sigil_shield(c):
    m = POLY((2, 2), (14, 2), (14, 8), (8, 15), (2, 8))
    c.f(m, APAL)
    c.c(m - POLY((3, 3), (13, 3), (13, 8), (8, 14), (3, 8)), SILV[3])
    c.c(LINE(8, 3, 8, 11), SILV[3])
    c.c(LINE(5, 5, 11, 5), SILV[2])
    c.d(GOLD[3], 8, 4)


def sigil_medal(c):
    m = DISC(8, 8, 6.6)
    c.f(m, SILV)
    c.f(DISC(8, 8, 4.8), APAL)
    c.c(LINE(5, 5, 11, 11), SILV[4])
    c.c(LINE(11, 5, 5, 11), SILV[4])
    c.d(GOLD[3], 8, 8)
    c.d(WHITE[4], 4, 4)


def sigil_rune(c):
    m = POLY((3, 1), (13, 2), (14, 14), (2, 14.5))
    c.f(m, IRON)
    c.c(PATH((8, 3), (8, 12)), APAL[4])
    c.c(PATH((5, 5), (8, 8), (11, 5)), APAL[3])
    c.c(PATH((5, 12), (8, 10), (11, 12)), APAL[3])
    c.d(MAG[4], 8, 8)


# ------------------------------------------------------------------ Небесный терн
def essence_vial(c):
    m = ELL(8, 10.5, 4.6, 4.4) | RECT(6, 3, 9, 6)
    c.f(m, WHITE)
    c.c(ELL(8, 11, 3.4, 3.2), SKY[2])
    c.c(PATH((6, 12), (8, 9), (10, 11), (9, 13)), WHITE[4])
    c.f(RECT(6, 1, 9, 3), WOOD)
    c.d(WHITE[4], 5, 8)


def essence_wisp(c):
    m = DISC(9, 7, 4.6) | POLY((5, 9), (2, 14), (7, 11))
    c.f(m, SKYT)
    c.f(DISC(9, 7, 2.4), SKY)
    c.d(WHITE[4], 8, 6, 9, 6)
    c.d(SKY[3], 3, 12, 13, 2, 14, 11)


def essence_crystal(c):
    m = POLY((8, 0.5), (13, 6), (10, 15), (6, 15), (3, 6))
    c.f(m, ICE)
    c.c(ELL(8, 9, 3, 2.2) | ELL(7, 7, 2, 1.6), WHITE[4])
    c.c(LINE(8, 1, 8, 4), ICE[4])
    c.c(LINE(12, 6, 10, 14) & m, ICE[1])


def string_coil(c):
    for cx, col in ((6.5, SILV[2]), (8, SILV[3]), (9.5, SILV[4])):
        c.c(ELL(cx, 8, 5.2, 4.2) - ELL(cx, 8, 4.3, 3.3), col)
    c.f(RECT(7, 3, 8, 13), SKY)
    c.c(PATH((12, 11), (13, 13), (15, 15)), SILV[3])
    c.d(WHITE[4], 4, 5, 11, 4)



def string_spool(c):
    c.f(RECT(4, 2, 11, 3), WOOD)
    c.f(RECT(4, 12, 11, 13), WOOD)
    c.f(RECT(5, 4, 10, 11), SKYT)
    for y in (5, 7, 9):
        c.l(5, y, 10, y + 1, SKY[3])
    c.c(PATH((10, 9), (13, 11), (14, 14)), SKY[4])
    c.d(WHITE[4], 6, 5)


def string_bow(c):
    c.c(PATH((3, 2), (6, 7), (8, 9), (13, 13)), SKYT[3])
    c.f(DISC(3, 2.5, 1.6), SILV)
    c.f(DISC(13, 13, 1.6), SILV)
    c.d(WHITE[4], 6, 7, 10, 11)
    c.d(SKY[4], 11, 5, 4, 11, 13, 7)


def cloud_core(c):
    m = DISC(5, 9, 3.6) | DISC(9.5, 7, 4.4) | DISC(12, 10, 3) | RECT(4, 9, 13, 12)
    c.f(m, WHITE)
    c.f(DISC(9, 9.5, 2.2), SKY)
    c.d(WHITE[4], 9, 9)
    c.d(SKY[4], 3, 5, 13, 3)


def cloud_sphere(c):
    c.f(DISC(8, 8, 5), SKYT)
    c.f(DISC(8, 8, 2.4), SKY)
    c.c(ELL(8, 10, 7.4, 1.8) - DISC(8, 8, 4.5), WHITE[3])
    c.c(ELL(8, 10, 7.4, 1.8) & {(x, y) for x in range(16) for y in range(11, 16)}, WHITE[2])
    c.d(WHITE[4], 6, 5)


def cloud_cube(c):
    top = POLY((8, 2), (14, 5), (8, 8), (2, 5))
    left = POLY((2, 5), (8, 8), (8, 15), (2, 12))
    right = POLY((8, 8), (14, 5), (14, 12), (8, 15))
    c.f(left, WHITE, grad=False)
    c.f(right, SKYT[1:4], grad=False)
    c.f(top, WHITE)
    c.c(PATH((4, 9), (6, 8), (6, 11)), SKY[3])
    c.c(PATH((10, 10), (12, 9), (12, 12)), SKY[2])
    c.d(SKY[4], 8, 4)


# ------------------------------------------------------------------ Мортум
def dark_twisted(c):
    horn(c, (3.5, 12.5), (13.5, 1.5), BLK, 3.0, bands=4, band=OBS[3], curve=1.0)
    c.d(RED[3], 13, 2, 12, 3)
    c.d(OBS[4], 5, 10)


def dark_cracked(c):
    m = horn(c, (3.5, 12.5), (13.5, 1.5), OBS, 3.0)
    c.c(PATH((4, 11), (6, 10), (7, 8), (9, 7), (11, 4)) & m, HELL[3])
    c.c(PATH((6, 10), (6, 12)) & m, HELL[2])
    c.d(HELL[4], 7, 8)


def dark_bone(c):
    horn(c, (5, 10.5), (13.5, 1.5), BLK, 2.4, bands=3, band=MORT[3])
    c.f(ELL(4.5, 12, 3.4, 2.4), BONE)
    c.d(BONE[1], 3, 13, 6, 13)
    c.d(MORT[4], 12, 3)


def demon_horned(c):
    m = HEART(8, 9.5, 6.2)
    c.f(m, RED)
    c.c(PATH((5, 7), (7, 10), (6, 13)) & m, RED[0])
    c.c(PATH((10, 7), (9, 11)) & m, RED[0])
    c.f(POLY((3, 5), (5, 4.5), (2, 0.5)), BLK)
    c.f(POLY((11, 4.5), (13, 5), (14, 0.5)), BLK)
    c.d(RED[4], 5, 6)


def demon_flame(c):
    c.f(POLY((2, 13), (1, 6), (4, 8), (4, 2), (7, 5), (8, 0.5), (10, 5), (12, 2), (12, 8), (15, 6), (14, 13), (8, 15.5)), PUR)
    m = HEART(8, 9.5, 5)
    c.f(m, RED)
    c.d(RED[4], 6, 7)
    c.d(PUR[4], 8, 2, 4, 4, 12, 4)


def demon_stone(c):
    m = HEART(8, 8.5, 6.6)
    c.f(m, BLK)
    c.c(PATH((4, 5), (7, 8), (6, 10), (9, 13)) & m, HELL[3])
    c.c(PATH((7, 8), (11, 7)) & m, HELL[2])
    c.d(HELL[4], 7, 8)
    c.d(BLK[4], 4, 4)


def silk_ball(c):
    m = DISC(7.5, 7.5, 5.8)
    c.f(m, TEAL)
    for a in (0.3, 1.2, 2.2):
        c.c(LINE(7.5 + math.cos(a) * 5.5, 7.5 + math.sin(a) * 5.5, 7.5 - math.cos(a) * 5.5, 7.5 - math.sin(a) * 5.5) & m, TEAL[4])
    c.c(PATH((12, 11), (13, 14), (15, 15)), TEAL[3])
    c.d(WHITE[4], 5, 4)


def silk_cloth(c):
    m = POLY((2, 4), (12, 2), (14, 10), (11, 14), (3, 13))
    c.f(m, TEAL)
    c.c(PATH((3, 8), (8, 6), (13, 6)) & m, TEAL[4])
    c.c(PATH((4, 11), (9, 10), (12, 11)) & m, TEAL[1])
    c.c(POLY((11, 14), (14, 10), (15, 14)), TEAL[3])
    c.d(WHITE[4], 4, 5, 6, 4)


def silk_web(c):
    for a in range(8):
        ang = a * math.pi / 4
        c.c(LINE(8, 8, 8 + math.cos(ang) * 7, 8 + math.sin(ang) * 7), TEAL[3])
    for r in (2.6, 5):
        c.c(RING(8, 8, r, 0.9) - DISC(8, 8, r - 0.95), TEAL[2])
    c.f(DISC(8, 8, 1.6), CYAN)
    c.d(WHITE[4], 8, 8)


drop('sun_feather', 'Солнечное перо', 'Слабая кори', 'Эдем', TEMPER,
     ('Перо', feather), ('Перо на фоне солнца', feather_sun), ('Огненное перо', feather_flame))
drop('horn_shard', 'Обломок рога Эдема', 'Единорог Эдема (и Великий кадиллион Эдема)', 'Эдем', CALL,
     ('Спиральный обломок', horn_spiral), ('Кристалл рога', horn_crystal), ('Два осколка', horn_pieces))
drop('greenfeet_root', 'Корень зеленонога', 'Зеленоног', 'Эдем', TEMPER,
     ('Узловатый корень с ростком', root_gnarled), ('Корень-человечек', root_mandrake), ('Связка корней', root_bundle))
drop('verek_fang', 'Клык верека', 'Верек', 'Дикий лес', TEMPER,
     ('Клык', fang), ('Челюсть с клыками', fang_jaw), ('Морозный клык', fang_frost))
drop('moon_horn', 'Лунный рог', 'Единорог Дикого леса', 'Дикий лес', CALL,
     ('Рог-полумесяц', moon_crescent), ('Спиральный рог в кольце', moon_spiral), ('Рог с самоцветом', moon_gem))
drop('moss_core', 'Мшистое ядро', 'Голем Дикого леса', 'Дикий лес', TEMPER,
     ('Камень во мху', moss_stone), ('Рунный куб', moss_rune), ('Треснувший камень со светом', moss_crack))
drop('golem_heart', 'Аметистовое сердце', 'Голем Апалачии', 'Апалачия', TEMPER,
     ('Огранённое сердце', heart_cut), ('Друза', heart_druse), ('Каменное сердце с жилами', heart_stone))
drop('dusk_horn', 'Сумрачный рог', 'Единорог Апалачии', 'Апалачия', CALL,
     ('Спиральный', dusk_spiral), ('Изогнутый, от сирени к ночи', dusk_gradient), ('Звёздный', dusk_stars))
drop('warrior_sigil', 'Знак воина Апалачии', 'Зачарованный воин', 'Апалачия', TEMPER,
     ('Щит с мечом', sigil_shield), ('Медальон со скрещёнными мечами', sigil_medal), ('Рунный камень', sigil_rune))
drop('sky_essence', 'Небесная эссенция', 'Демон Небесного терна', 'Небесный терн', CALL,
     ('Флакон', essence_vial), ('Огонёк', essence_wisp), ('Кристалл с облаком', essence_crystal))
drop('sky_string', 'Небесная тетива', 'Лучник Небесного терна', 'Небесный терн', TEMPER,
     ('Моток', string_coil), ('Катушка', string_spool), ('Тетива с ушками', string_bow))
drop('cloud_core', 'Облачное ядро', 'Голем Небесного терна', 'Небесный терн', TEMPER,
     ('Облако со светом внутри', cloud_core), ('Шар в кольце облаков', cloud_sphere), ('Куб сжатого облака', cloud_cube))
drop('dark_horn', 'Тёмный рог', 'Единорог Мортума', 'Мортум', TEMPER,
     ('Витой', dark_twisted), ('Расколотый, с жаром внутри', dark_cracked), ('На костяном основании', dark_bone))
drop('demon_heart', 'Демоническое сердце', 'Демон тьмы', 'Мортум', TEMPER,
     ('Рогатое сердце', demon_horned), ('В тёмном пламени', demon_flame), ('Окаменевшее с трещиной', demon_stone))
drop('soul_silk', 'Призрачный шёлк', 'Паук душ', 'Мортум', TEMPER,
     ('Клубок', silk_ball), ('Отрез ткани', silk_cloth), ('Паутина с огоньком', silk_web))
