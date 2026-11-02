"""Концепты брони Странника в духе Террарии: R1 - освящённая, R4 (Апалачия) - орихалковая, R7 (Галит) - хлорофитовая.
У каждой ступени одно тело и три головы, как в Террарии: шлем - ближний бой, маска - дальний, капюшон - аркана.

Запуск: python3 wterraria.py [ключ ...]  (без ключей - все). Картинки - в wanderer_concepts рядом.
"""
import math
import sys

import numpy as np

import wconcepts as w
from wconcepts import P, C, rect, disc, trim, mottle, facets, stripe, box, sym, rod, chain, chest, belt, upper_arm, vambrace, thigh, boot, helm, \
    pauldron, hood, cloak, crystal, concept, build
from wround3 import open_helm

NEW = []
w.TIER_BG[1] = '#2a2210'


def new(key, tier, line, title):
    NEW.append(key)
    return concept(key, tier, line, title)


def ellipse_mask(wd, ht):
    def a(p, face, b):
        return ((((p[..., 0] - wd / 2) / (wd / 2)) ** 2 + ((p[..., 1] - ht / 2) / (ht / 2)) ** 2) < 1.0).astype(float)
    return a


def blade(bone, start, L, wd, paint, rx=0.0, ry=0.0, rz=0.0, t=0.5):
    """Плоский лист/перо длиной L от точки start вверх (-y)."""
    sx, sy, sz = start
    return box(bone, sx - wd / 2, sy - L, sz - t / 2, wd, L, t, paint, rot=(rx, ry, rz), pivot=(sx, sy, sz))


# ======================================================================== R1 освящённая
HG, HGD, HGL, HS, HSD, HDK, HDK2, HR, HRD, GEMR = ('#f0c41c', '#a87808', '#fff3a0', '#c8c8dc', '#8686a0', '#1c1c26', '#2c2c3a',
                                                  '#a01434', '#5c0a1e', '#ff3048')


def hallowed_body():
    gold = P(HG, trim(0.4, HGD), rect(('up',), -1, 99, -1, 99, HGL))
    plate = P(HS, trim(0.4, HSD), stripe(('north',), 0.62, 0.3, 0.55, HG), stripe(('north',), -0.62, 8.5, 0.55, HG),
              disc(('north',), 4.4, 5.6, 1.15, HRD), disc(('north',), 4.4, 5.6, 0.75, GEMR, glow=True))
    bs = chest(P(HDK2, trim(0.3, HDK))) + [box('body', -4.4, -0.6, -3.5, 8.8, 7.8, 1.2, plate)]
    bs += belt(P(HG, trim(0.3, HGD), rect(('north',), 3.8, 5.6, 0.2, 2.4, HS)))
    bs += pauldron(P(HG, trim(0.45, HGD), rect(('up',), -1, 99, -1, 99, HGL), rect(('west', 'east'), -1, 99, 1.6, 2.4, HS)), tilt=-14, big=1.2)
    bs += upper_arm(P(HDK2, trim(0.3, HDK))) + vambrace(P(HG, trim(0.35, HGD), rect(('north', 'south', 'east', 'west'), -1, 99, 1.8, 2.6, HS)))
    bs += thigh(P(HDK2, trim(0.3, HDK))) + sym(box('rightLeg', -2.7, 3.6, -3.1, 5.4, 3.0, 1.2, gold))
    bs += boot(P(HS, trim(0.35, HSD), rect(('north', 'south', 'east', 'west'), -1, 99, -1, 1.6, HG)), toe=0.6)
    bs += cloak(P(HR, trim(0.4, HRD), grad=0.35), length=21, width=11.4, flare=7)
    bs += [box('body', -5.6, -1.6, 2.6, 11.2, 2.0, 1.2, P(HG, trim(0.3, HGD)))]
    return bs


def hallowed_wings(y=-5.6, x=-5.0):
    """Золотые крылышки по бокам головы: три пера назад-вверх."""
    wing = P(HG, trim(0.25, HGD), rect('all', -1, 99, -1, 1.0, HGL))
    wd = P(HGD, trim(0.25, '#6a4a04'), rect('all', -1, 99, -1, 1.0, HG))
    return sym(blade('head', (x, y, -0.4), 6.0, 1.8, wing, rz=-28, rx=30), blade('head', (x, y + 0.8, 0.8), 5.0, 1.6, wd, rz=-40, rx=40),
               blade('head', (x, y + 1.6, 2.0), 4.0, 1.4, wing, rz=-52, rx=50))


@new('r1th', 1, 'Ш', 'Шлем: глухой золотой шлем с тёмным забралом, серебряным гребнем и крылышками')
def r1th():
    face = P(HG, trim(0.45, HGD), rect(('north',), 1.2, 8.8, 3.6, 6.4, HDK), rect(('north', 'up', 'south'), 4.3, 5.7, -1, 99, HS),
             rect(('north',), 1.2, 8.8, 3.6, 4.0, HSD))
    return hallowed_body() + helm(face) + hallowed_wings() + [box('head', -0.6, -10.6, -4.8, 1.2, 1.8, 9.6, P(HS, trim(0.25, HSD), rect(('up',), -1, 99, -1, 99, '#ececf6')))]


@new('r1tm', 1, 'М', 'Маска: серебряная полумаска, золотой открытый шлем с высоким плюмажем')
def r1tm():
    bs = hallowed_body() + open_helm(P(HG, trim(0.35, HGD), rect(('up',), 3.6, 6.2, -1, 99, HS)))
    bs += [box('head', -4.6, -3.2, -4.8, 9.2, 3.4, 0.8, P(HS, trim(0.3, HSD), rect(('north',), 0.6, 8.6, 1.2, 1.6, HSD)))]
    bs += chain('head', (0, -9.0, -1.0), ((2.6, 15, 0), (3.0, 40, 0), (3.2, 70, 0), (2.8, 100, 0)), 1.4, None, taper=0.85, colors=(HG, HGL))
    bs += [box('head', -0.8, -8.0, -5.4, 1.6, 1.6, 0.6, P(GEMR, glow=True))]
    return bs


@new('r1tk', 1, 'К', 'Капюшон: золотой капюшон, обруч с крыльями и красным камнем')
def r1tk():
    bs = hallowed_body() + hood(P(HG, trim(0.35, HGD), rect(('up',), 3.8, 6.6, -1, 99, HS)), shadow='#1a1006', tip_len=3.6)
    bs += [box('head', -5.4, -7.6, -5.6, 10.8, 1.2, 0.6, P(HS, trim(0.2, HSD))),
           box('head', -1.0, -8.0, -6.0, 2.0, 2.0, 0.7, P(GEMR, glow=True))]
    return bs + hallowed_wings(-7.0, -5.6)


# ======================================================================== R4 орихалковая (Апалачия)
OM, OP, OL, OPU, OD = '#e030d4', '#f590f0', '#c49ad8', '#9a3aa0', '#300a30'


def orichalcum_body():
    pl = P(OM, facets('#ffffff', 0.22), trim(0.4, OD))
    lav = P(OL, facets('#ffffff', 0.2), trim(0.35, OPU))
    bs = chest(P(OD, trim(0.3, '#1a041a'))) + [box('body', -4.4, -0.6, -3.5, 8.8, 7.4, 1.2, P(OM, facets('#ffffff', 0.22), trim(0.4, OD),
                                                                                               disc(('north',), 4.4, 3.6, 1.8, OL), disc(('north',), 4.4, 3.6, 1.0, OP)))]
    bs += [box('body', -3.6, 6.8, -3.3, 7.2, 3.4, 0.8, lav)] + belt(P(OPU, trim(0.3, OD)))
    bs += pauldron(P(OM, facets('#ffffff', 0.22), trim(0.45, OD)), tilt=-14, layers=1, big=0.8)
    for (x, z, L, rz, rx) in ((-5.6, -1.6, 3.6, -30, -10), (-6.2, 0.6, 4.4, -42, 5), (-5.4, 2.4, 3.0, -25, 18)):
        bs += sym(crystal('rightArm', (x, -4.0, z), L, 1.0, OP, rx=rx, rz=rz, glow=False, tipc='#ffe0fc'))
    bs += [box('body', -4.4, -0.6, 2.3, 8.8, 9.0, 1.2, P(OM, facets('#ffffff', 0.22), trim(0.4, OD), rect(('south',), 3.8, 5.0, 0.8, 8.2, OL)))]
    bs += upper_arm(P(OD, trim(0.3, OPU))) + vambrace(pl) + thigh(P(OPU, trim(0.3, OD), facets('#ffffff', 0.15))) + boot(pl, toe=0.6)
    bs += sym(box('rightLeg', -2.7, 2.0, -3.1, 5.4, 3.6, 1.0, lav))
    petal = P(OP, trim(0.2, OM), bevel=0.0)
    for (x, y, z, r) in ((-9.0, 3.0, -2.0, 30), (8.4, 6.0, 1.0, -50), (-7.6, 12.0, 2.0, 70), (9.2, -2.0, -1.5, 15), (-8.6, 18.0, -1.0, -35), (7.8, 16.0, 2.6, 55)):
        bs.append(box('body', x, y, z, 1.4, 0.4, 1.0, petal, rot=(r, r * 0.5, 30), pivot=(x + 0.7, y, z + 0.5)))
    return bs


def fan(bone, y, z, color, n=5, spread=70, L0=4.2):
    out = []
    for i in range(n):
        a = -spread / 2 + spread * i / (n - 1)
        out.append(crystal(bone, (a * 0.04, y, z), L0 - abs(a) * 0.02, 0.9, color, rz=a, rx=-10, glow=False, tipc='#ffe0fc'))
    return out


@new('r4th', 4, 'Ш', 'Шлем: глухой гранёный шлем с веером сиреневых шипов')
def r4th():
    face = P(OM, facets('#ffffff', 0.22), trim(0.45, OD), rect(('north',), 1.4, 8.6, 4.0, 5.0, '#14041c'), rect(('north',), 4.4, 5.6, 5.0, 8.4, OL))
    return orichalcum_body() + helm(face) + fan('head', -9.0, 0.0, OP)


@new('r4tm', 4, 'М', 'Маска: сиреневая полумаска, открытый шлем с изогнутым рогом-гребнем')
def r4tm():
    bs = orichalcum_body() + open_helm(P(OM, facets('#ffffff', 0.22), trim(0.35, OD)))
    bs += [box('head', -4.6, -3.2, -4.8, 9.2, 3.4, 0.8, P(OL, facets('#ffffff', 0.2), trim(0.3, OPU)))]
    bs += chain('head', (0, -9.0, -2.0), ((3.0, 10, 0), (3.4, 35, 0), (3.4, 70, 0), (3.0, 105, 0), (2.4, 135, 0)), 1.8, None, taper=0.82, colors=(OM, OP))
    return bs


@new('r4tk', 4, 'К', 'Капюшон: гранёный малиновый капюшон с кристаллами на макушке')
def r4tk():
    bs = orichalcum_body() + hood(P(OM, facets('#ffffff', 0.22), trim(0.35, OD)), shadow='#1a041a', tip_len=3.4,
                                  capelet=P(OM, facets('#ffffff', 0.22), trim(0.35, OD)))
    bs += [box('head', -1.0, -8.2, -6.2, 2.0, 2.0, 0.7, P(OP, glow=True))]
    for (x, L, rz) in ((-2.2, 2.6, -25), (0.0, 3.4, 0), (2.2, 2.6, 25)):
        bs += [crystal('head', (x, -9.6, 1.0), L, 0.9, OP, rz=rz, rx=-15, glow=False, tipc='#ffe0fc')]
    return bs


# ======================================================================== R7 хлорофитовая (Галит)
CG, CGB, CGL, CGD, CGO, CM, CMD = '#22a818', '#9ef000', '#a8d870', '#0e4a0c', '#063006', '#727272', '#3e3e3e'


def leaf(bone, start, L, wd, rx=0.0, ry=0.0, rz=0.0, color=CG):
    paint = P(color, rect(('north', 'south'), wd / 2 - 0.2, wd / 2 + 0.2, 0.6, L - 0.4, CGB), trim(0.2, CGO), alpha=ellipse_mask(wd, L), bevel=0.0, noise=0.03)
    return blade(bone, start, L, wd, paint, rx=rx, ry=ry, rz=rz)


def chlorophyte_body():
    lf = P(CG, trim(0.4, CGO), mottle(CGB, 1.0, 0.86), rect(('up',), -1, 99, -1, 99, CGL))
    bs = chest(P(CGD, trim(0.3, CGO))) + [box('body', -4.4, -0.6, -3.5, 8.8, 7.6, 1.2, P(CG, trim(0.4, CGO), mottle(CGB, 1.0, 0.86),
                                                                                       rect(('north',), 3.2, 5.6, 0.6, 7.0, CM), rect(('north',), 3.6, 5.2, 1.0, 6.6, CMD)))]
    bs += belt(P(CM, trim(0.3, CMD))) + upper_arm(P(CGD, trim(0.3, CGO))) + vambrace(lf) + thigh(P(CG, trim(0.35, CGO), mottle(CGB, 1.0, 0.88)))
    bs += boot(P(CG, trim(0.35, CGO), rect(('north',), -1, 99, 3.4, 99, CM)), toe=0.6)
    bs += pauldron(P(CG, trim(0.45, CGO), mottle(CGB, 1.0, 0.86)), tilt=-12, layers=1, big=0.6)
    for (z, L, rz, rx) in ((-2.0, 5.0, -55, -15), (0.2, 6.0, -65, 0), (2.4, 4.6, -50, 15)):
        bs += sym(leaf('rightArm', (-5.0, -4.0, z), L, 2.2, rz=rz, rx=rx))
    for (x, L, rx) in ((-2.6, 7.0, -55), (0.0, 8.0, -50), (2.6, 7.0, -55)):
        bs += [leaf('body', (x, 1.0, 3.4), L, 2.6, rx=rx, rz=x * 6)]
    cry = P(CGB, facets('#ffffff', 0.3), trim(0.15, '#e8ffd0'), glow=True, bevel=0.1)
    bs += [box('head', -1.1, -17.6, -1.1, 2.2, 2.8, 2.2, cry, rot=(0, 45, 0), pivot=(0, -16.2, 0)),
           box('head', -0.6, -18.8, -0.6, 1.2, 1.2, 1.2, cry, rot=(0, 45, 0), pivot=(0, -18.2, 0)),
           box('head', -0.6, -15.0, -0.6, 1.2, 1.2, 1.2, cry, rot=(0, 45, 0), pivot=(0, -14.4, 0))]
    return bs


def horns(bone, start, segs, t0=1.4):
    return sym(*chain(bone, start, segs, t0, None, taper=0.78, colors=(CMD, '#a8a8a8')))


@new('r7th', 7, 'Ш', 'Шлем: глухой листовой шлем, серые рога в стороны, сверху парит листовой кристалл')
def r7th():
    face = P(CG, trim(0.45, CGO), mottle(CGB, 1.0, 0.86), rect(('north',), 1.4, 8.6, 4.0, 5.0, '#031603'), rect(('north',), 4.4, 5.6, 5.0, 8.6, CM))
    bs = chlorophyte_body() + helm(face)
    bs += horns('head', (-4.8, -6.0, 0.4), ((3.0, 10, -75), (3.2, 15, -95), (2.8, 20, -115), (2.2, 25, -135)), t0=1.9)
    bs += [leaf('head', (0, -9.0, -1.0), 5.0, 2.6, rx=-10), leaf('head', (0, -9.0, 1.4), 4.0, 2.2, rx=25)]
    return bs


@new('r7tm', 7, 'М', 'Маска: листовая полумаска, открытый шлем с листовым гребнем и рогом')
def r7tm():
    bs = chlorophyte_body() + open_helm(P(CG, trim(0.35, CGO), mottle(CGB, 1.0, 0.86)))
    bs += [box('head', -4.6, -3.2, -4.8, 9.2, 3.4, 0.8, P(CGL, trim(0.3, CG), rect(('north',), 0.6, 8.6, 1.2, 1.6, CG)))]
    bs += [leaf('head', (x, -9.0, z), L, 2.2, rx=rx, rz=rz) for (x, z, L, rx, rz) in ((-2.2, -1.0, 5.0, 10, -20), (0.0, 0.0, 6.0, 20, 0), (2.2, 1.0, 4.6, 30, 20))]
    bs += chain('head', (-4.8, -6.0, 0.0), ((2.6, 0, -60), (2.6, 0, -35), (2.0, 0, -10)), 1.4, None, taper=0.78, colors=(CMD, '#a8a8a8'))
    return bs


@new('r7tk', 7, 'К', 'Капюшон: листовой капюшон, загнутые серые рога')
def r7tk():
    bs = chlorophyte_body() + hood(P(CG, trim(0.35, CGO), mottle(CGB, 1.0, 0.86)), shadow='#021002', tip_len=3.0,
                                   capelet=P(CG, trim(0.35, CGO), mottle(CGB, 1.0, 0.86)))
    bs += horns('head', (-4.6, -8.0, 1.0), ((2.6, 10, -40), (2.6, 30, -80), (2.4, 60, -130), (2.0, 95, -170)), t0=1.6)
    bs += [box('head', -1.0, -8.2, -6.2, 2.0, 2.0, 0.7, P(CGB, glow=True))]
    return bs


if __name__ == '__main__':
    for k in sys.argv[1:] or NEW:
        build(k)
