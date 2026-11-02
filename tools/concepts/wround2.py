"""Второй раунд концептов брони Странника: новые варианты Г/Д/Е для R1 (теперь из божественной брони), R4, R5, R7.

Запуск: python wround2.py [ключ ...]  (без ключей - все новые). Картинки - в wanderer_concepts рядом.
"""
import math
import sys

import numpy as np

import wconcepts as w
from wconcepts import (P, C, rect, disc, trim, mottle, veins, streaks, dots, facets, stripe, tatter, box, sym, rod, chain, ring,
                       chest, belt, upper_arm, vambrace, thigh, boot, helm, pauldron, hood, cloak, crystal, concept, CONCEPTS, build)

NEW = []


def new(key, tier, line, title):
    NEW.append(key)
    return concept(key, tier, line, title)


def vring(bone, center, radius, n, t, paint):
    """Кольцо в плоскости XY (стоит вертикально, лицом вперёд)."""
    out = []
    L = 2 * math.pi * radius / n * 1.08
    cx, cy, cz = center
    for i in range(n):
        a = 2 * math.pi * i / n
        px, py = cx + radius * math.cos(a), cy + radius * math.sin(a)
        out.append(box(bone, px - L / 2, py - t / 2, cz - t / 2, L, t, t, paint, rot=(0, 0, math.degrees(a) + 90), pivot=(px, py, cz)))
    return out


def round_mask(cx, cy, r):
    def a(p, face, b):
        return (((p[..., 0] - cx) ** 2 + (p[..., 1] - cy) ** 2) < r * r).astype(float)
    return a


def zigzag(faces, b, amp, period, w, color, glow=True):
    """Ломаная сверху вниз (молния): u = b +- amp по треугольной волне от v."""
    c = C(color)

    def d(p, face, bx):
        if faces != 'all' and face.name not in faces:
            return c, 0.0, glow
        u, v = w_fuv(p, face)
        tri = 2 * np.abs((v / period) % 1 - 0.5)
        return c, (np.abs(u - (b + amp * (tri - 0.5) * 2)) < w).astype(float), glow
    return d


w_fuv = w.fuv


def peak(bone, x, z, base_y, s, h, rock, snow):
    """Ступенчатый пик: три уступа, верх в снегу."""
    return [box(bone, x - s / 2, base_y - h * 0.45, z - s / 2, s, h * 0.45, s, rock),
            box(bone, x - s * 0.33, base_y - h * 0.8, z - s * 0.33, s * 0.66, h * 0.35, s * 0.66, rock),
            box(bone, x - s * 0.18, base_y - h * 1.1, z - s * 0.18, s * 0.36, h * 0.3, s * 0.36, snow),
            box(bone, x - s * 0.36, base_y - h * 0.82, z - s * 0.36, s * 0.72, 0.6, s * 0.72, snow)]


def skirt(paint, top=9.0, length=12.6, inf=0.9, flare=0.0):
    """Длинные полы (роба, сюртук) на теле - закрывают ноги."""
    return [box('body', -4 - inf, top, -2 - inf, 8 + 2 * inf, length, 4 + 2 * inf, paint)]


def brim_hat(brim, crown, band, r=7.0, h=3.6):
    return [box('head', -r, -8.9, -r, 2 * r, 0.8, 2 * r, brim),
            box('head', -4.5, -8.6 - h, -4.5, 9.0, h, 9.0, crown),
            box('head', -4.6, -9.9, -4.6, 9.2, 1.1, 9.2, band)]


# ---- R1 из божественной брони: золото, тёмно-алые вставки, голубой камень, сталь
DG, DGD, DGL, CRIM, CRIMD, GEM, STEEL, STEELD = '#ccaf3b', '#8b5e16', '#ece184', '#a01c0c', '#5e1100', '#46d6e6', '#80848c', '#4a4e56'
w.TIER_BG[1] = '#2a2210'


@new('r1d', 1, 'Г', 'Путник: дорожный кафтан, божественная кираса, обруч с камнем')
def r1d():
    coat = P('#3e2c1e', trim(0.35, '#22170e'))
    bs = chest(coat) + upper_arm(coat) + vambrace(P(DG, trim(0.35, CRIMD)))
    bs += [box('body', -3.8, 0.0, -3.5, 7.6, 6.6, 1.0, P(DG, trim(0.45, CRIM), mottle(DGL, 1.3, 0.9), disc(('north',), 3.8, 3.0, 1.1, GEM, glow=True)))]
    bs += pauldron(P(DG, trim(0.4, CRIM)), tilt=-10, layers=1, big=0.3)
    bs += belt(P('#22170e', rect(('north',), 4.1, 5.3, 0.4, 2.2, DG))) + [box('body', 2.6, 9.0, -3.8, 2.6, 3.2, 1.4, P('#5a3e26', trim(0.3, '#22170e')))]
    bs += skirt(P('#3e2c1e', trim(0.35, '#22170e'), stripe(('north',), 0.0, 4.95, 0.35, '#22170e')), top=10.4, length=5.6)
    bs += thigh(P('#2c2018')) + boot(P(STEEL, trim(0.35, STEELD), rect(('north', 'south', 'east', 'west'), -1, 99, -1, 1.0, DG)), toe=0.4)
    bs += [box('head', -4.6, -6.6, -4.6, 9.2, 1.2, 9.2, P(DG, trim(0.25, DGD))),
           box('head', -0.9, -7.0, -5.2, 1.8, 1.8, 0.8, P(GEM, glow=True))]
    bs += cloak(P(CRIM, trim(0.45, CRIMD), grad=0.25), length=15, flare=8)
    bs += [box('body', -5.4, -1.4, -3.4, 10.8, 2.0, 6.8, P(CRIM, trim(0.3, CRIMD)))]
    return bs


@new('r1e', 1, 'Д', 'Рыцарь: божественные латы, алые кисти, камень во лбу')
def r1e():
    gold = P(DG, trim(0.45, DGD), mottle(DGL, 1.3, 0.93))
    bs = chest(P(DG, trim(0.5, DGD), mottle(DGL, 1.3, 0.93), rect(('north',), 4.7, 5.3, 0, 99, DGD), disc(('north',), 5, 3.4, 1.4, CRIMD),
                 disc(('north',), 5, 3.4, 0.9, GEM, glow=True)))
    bs += upper_arm(P(DGD, trim(0.3, '#5a3a0c'))) + vambrace(gold) + pauldron(P(DG, trim(0.5, CRIM), mottle(DGL, 1.3, 0.93)), tilt=-14, big=1.0)
    tuft = P(CRIM, streaks(CRIMD, 1.4, 0.5), bevel=0.05)
    bs += sym(box('rightArm', -5.6, -0.6, -3.2, 1.4, 3.0, 6.4, tuft, rot=(0, 0, -14), pivot=(-1, -1, 0)))
    bs += belt(P(CRIMD, rect(('north',), 4.0, 5.4, 0.3, 2.3, DG))) + thigh(gold)
    bs += sym(box('rightLeg', -2.7, -0.6, -3.3, 5.4, 4.2, 1.2, P(DG, trim(0.4, CRIM))))
    bs += boot(P(STEEL, trim(0.4, STEELD), rect(('north', 'south', 'east', 'west'), -1, 99, -1, 1.2, CRIM)), toe=0.6)
    bs += helm(P(DG, trim(0.5, DGD), mottle(DGL, 1.3, 0.93), rect(('north',), 1.6, 8.4, 4.2, 5.0, '#1a1006'), rect(('north',), 4.6, 5.4, 4.2, 8.6, '#1a1006')))
    bs += [box('head', -1.0, -8.4, -5.6, 2.0, 2.0, 0.9, P(GEM, glow=True))]
    bs += [box('head', -0.8, -11.4, -4.0 + i * 1.6, 1.6, 2.6 - abs(i - 2.5) * 0.3, 1.6, tuft) for i in range(6)]
    return bs


@new('r1f', 1, 'Е', 'Вестник: светлые латы, сложенные золотые крылья за спиной')
def r1f():
    wh = '#efe8d4'
    light = P(wh, trim(0.45, DG))
    bs = chest(P(wh, trim(0.5, DG), stripe(('north',), 0.8, -0.5, 0.8, CRIM), disc(('north',), 5, 3.2, 1.0, GEM, glow=True)))
    bs += upper_arm(P(wh, trim(0.3, '#b8ae94'))) + vambrace(P(DG, trim(0.35, DGD))) + pauldron(P(DG, trim(0.4, DGD)), tilt=-12, layers=1, big=0.6)
    bs += belt(P(DGD, rect(('north',), 4.0, 5.4, 0.3, 2.3, DG))) + thigh(light) + boot(P(DG, trim(0.4, DGD)), toe=0.5)
    bs += helm(P(wh, trim(0.45, DG), rect(('north',), 1.4, 8.6, 3.8, 9.0, '#c8956d'), rect(('north',), 1.4, 8.6, 3.8, 4.4, DG)))
    bs += [box('head', -0.9, -8.2, -5.4, 1.8, 1.8, 0.7, P(GEM, glow=True))]
    # сложенные крылья: плоский силуэт крыла (скруглённый верх над плечом, острый низ у колен), рисунок перьев
    WW, WH_ = 5.8, 23.0  # локальный x=0 - внешний край крыла, x=WW - у позвоночника

    def wing_mask(p, face, b):
        x, y = WW - p[..., 0], p[..., 1]
        lo = np.clip((y - 0.55 * WH_) / (0.45 * WH_), 0, 1)
        mid = np.clip((y - 0.45 * WH_) / (0.55 * WH_), 0, 1)
        xl = 0.3 * WW * lo ** 1.3
        xr = np.where(y < 5.0, WW * np.sqrt(np.clip(y / 5.0, 0, 1)), WW - 0.7 * WW * mid ** 1.2)
        gaps = (y > 0.62 * WH_) & ((x % 1.2) < 0.18)
        return ((x >= xl - 0.05) & (x <= xr + 0.05) & (y <= WH_) & ~gaps).astype(float)

    def upper(dec, vmax):
        def d(p, face, b):
            c, a, g = dec(p, face, b)
            return c, np.asarray(a, float) * (fuv_v(p, face) < vmax), g
        return d
    sides = ('north', 'south')
    wing = P(DG, *[stripe(sides, 0.0, u, 0.12, DGD) for u in (1.2, 2.4, 3.6, 4.8)], upper(rect(sides, -1, 99, -1, 99, DG), 8.5),
             upper(dots(sides, 1.4, 0.42, DGL, off=0.3), 8.0), rect(sides, -1, 99, 8.3, 8.7, DGL), trim(0.2, DGD), alpha=wing_mask, bevel=0.0, noise=0.03)
    bs += sym(box('body', -WW - 0.3, -9.2, 5.6, WW, WH_, 0.6, wing, rot=(5, 0, 0), pivot=(0, -9.2, 5.6)),
              box('body', -3.0, -0.6, 3.0, 2.4, 3.0, 2.8, P(DG, trim(0.3, DGD))))
    return bs


def fuv_v(p, face):
    return w.fuv(p, face)[1]


# ---- R4 Апалачия: пурпур, сирень, зачарование
AP, APD, APL, CRY = '#c020d8', '#5a1078', '#f090ff', '#e070ff'


@new('r4d', 4, 'Г', 'Чародей: мантия с рунами, остроконечная шляпа, руническое кольцо за спиной')
def r4d():
    robe = P('#3a1458', trim(0.3, APL, glow=True), mottle(APD, 1.2, 0.75))
    bs = chest(P('#3a1458', trim(0.3, APL, glow=True), rect(('north',), 4.5, 5.5, 0, 99, AP))) + upper_arm(P('#3a1458', trim(0.25, APD)))
    bs += sym(box('rightArm', -4.2, 3.8, -3.2, 6.4, 6.6, 6.4, robe))
    bs += belt(P(AP, rect(('north',), 4.0, 5.4, 0.3, 2.3, APL)))
    sides = ('north', 'south', 'east', 'west')
    hem = P('#3a1458', mottle(APD, 1.2, 0.8), rect(sides, -1, 99, 9.6, 10.4, APL, glow=True),
            *[rect(sides, u, u + 0.45, 7.3, 8.9, APL, glow=True) for u in (1.2, 3.6, 6.0, 8.4)],
            *[rect(sides, u, u + 1.1, 7.3, 7.75, APL, glow=True) for u in (1.2, 6.0)],
            *[rect(sides, u + 0.45, u + 1.1, 8.0, 8.4, APL, glow=True) for u in (3.6, 8.4)])
    bs += skirt(hem, top=10.6, length=11.4, inf=1.2)
    bs += boot(P('#2a0c3c', trim(0.3, AP)), top=9.6, toe=0.6)
    hat = P('#2e0e48', trim(0.2, '#1a0628'), noise=0.03)
    bs += [box('head', -7.2, -8.8, -7.2, 14.4, 0.8, 14.4, hat),
           box('head', -4.4, -11.6, -4.4, 8.8, 2.8, 8.8, hat),
           box('head', -4.5, -10.0, -4.5, 9.0, 1.0, 9.0, P(AP, trim(0.2, APL, glow=True))),
           box('head', -3.2, -14.2, -2.6, 6.4, 2.8, 6.4, hat),
           box('head', -2.2, -16.6, -1.2, 4.4, 2.6, 4.4, hat),
           box('head', -1.2, -18.8, 0.4, 2.4, 2.4, 2.4, hat),
           box('head', -0.6, -21.0, 2.0, 1.2, 2.6, 1.2, hat, rot=(-35, 0, 0), pivot=(0, -18.6, 1.6))]
    bs += vring('body', (0, 3.0, 7.0), 6.2, 20, 0.7, P(APL, glow=True, bevel=0.1))
    bs += vring('body', (0, 3.0, 7.0), 4.4, 14, 0.45, P(CRY, glow=True, bevel=0.1))
    return bs


@new('r4e', 4, 'Д', 'Зачарованный воин: сталь с сиреневым отблеском, пурпурный табар, призрачный щит')
def r4e():
    gl = [stripe('all', 1.0, b, 0.35, APL, glow=True) for b in (-6.0, 1.0, 8.0)]
    steel = P('#a8aeb8', trim(0.4, '#5c6270'), *gl)
    bs = chest(steel) + upper_arm(P('#8a909c', trim(0.3, '#5c6270'))) + vambrace(steel)
    bs += pauldron(P('#a8aeb8', trim(0.45, '#5c6270'), *gl), tilt=-14, big=1.0)
    bs += [box('body', -2.6, 0.4, -3.4, 5.2, 13.6, 0.5, P(AP, trim(0.4, '#e8c860'), disc(('north',), 2.6, 4.2, 1.4, APL)))]
    bs += belt(P('#3a3e48', rect(('north',), 4.0, 5.4, 0.3, 2.3, '#e8c860'))) + thigh(P('#8a909c', trim(0.3, '#5c6270'))) + boot(steel, toe=0.6)
    bs += helm(P('#a8aeb8', trim(0.45, '#5c6270'), *gl, rect(('north',), 1.6, 8.4, 4.0, 4.8, '#0e0a14'), rect(('north',), 4.5, 5.5, 4.0, 8.2, '#0e0a14')))
    bs += chain('head', (0, -9.0, -1.5), ((2.4, 25, 0), (3.2, 50, 0), (3.6, 80, 0), (3.6, 110, 0), (3.0, 135, 0)), 1.9, None, taper=0.86,
                colors=(AP, APL))
    sh = P('#6a2088', disc('all', 5.0, 5.0, 4.8, APL, r0=4.1, glow=True), disc('all', 5.0, 5.0, 2.6, CRY, r0=2.1, glow=True),
           disc('all', 5.0, 5.0, 0.9, APL, glow=True), alpha=round_mask(5.0, 5.0, 5.0), bevel=0.0, noise=0.02)
    bs += [box('body', -5.0, -0.5, 3.4, 10.0, 10.0, 0.5, sh, rot=(4, 0, 0), pivot=(0, 0, 3.4))]
    return bs


@new('r4f', 4, 'Е', 'Горы Апалачии: камень с пурпурным мхом, плечи-пики со снежными шапками')
def r4f():
    RK = '#76648a'
    sides = ('north', 'south', 'east', 'west')
    grass = [rect(sides, -1, 99, -1, 1.6, AP), mottle(APL, 1.3, 0.93)]
    rock = P(RK, mottle('#5e4e72', 1.0, 0.72), trim(0.3, '#46385a'), noise=0.06)
    moss = P(AP, mottle(APD, 1.0, 0.6), mottle(APL, 1.3, 0.9), bevel=0.05)
    bs = chest(P(RK, mottle('#5e4e72', 1.0, 0.72), trim(0.3, '#46385a'), rect(('north', 'south'), -1, 99, 9.0, 99, AP), noise=0.06))
    bs += upper_arm(rock) + vambrace(P('#64547a', trim(0.3, '#46385a'), *grass)) + thigh(P(RK, mottle('#5e4e72', 1.0, 0.72), *grass, noise=0.06))
    bs += boot(P(RK, trim(0.3, '#46385a'), *grass, noise=0.06), toe=0.5)
    bs += belt(moss) + [box('body', -5.4, -1.8, -3.4, 10.8, 1.8, 6.8, moss)]
    snow = P('#f4f0fa', mottle('#d8d0e8', 1.0, 0.75), bevel=0.25)
    bs += sym(box('rightArm', -5.6, -4.0, -3.8, 7.0, 1.4, 7.6, moss))
    bs += sym(*peak('rightArm', -2.6, -0.4, -3.8, 6.6, 10.0, rock, snow), *peak('rightArm', -4.8, 2.6, -3.8, 3.6, 5.4, rock, snow),
              *peak('rightArm', -4.4, -2.8, -3.8, 3.0, 4.2, rock, snow))
    bs += helm(P(RK, mottle('#5e4e72', 1.0, 0.72), trim(0.3, '#46385a'), rect(('north',), 1.6, 8.4, 4.0, 5.0, '#140c1c'), noise=0.06))
    bs += [box('head', -5.1, -9.6, -5.1, 10.2, 0.8, 10.2, moss)]
    bs += peak('head', 0.0, 0.8, -9.6, 6.0, 8.0, rock, snow) + peak('head', -3.4, -1.0, -9.6, 3.2, 4.2, rock, snow) + peak('head', 3.2, -1.4, -9.6, 3.0, 3.6, rock, snow)
    return bs


# ---- R5 Небесный терн: белый, серебро, голубой
SKY, SKYL, SKYD, BLUE = '#aeb2ba', '#eef0f4', '#6a6e78', '#8ab8ec'


@new('r5d', 5, 'Г', 'Мегалит: каменные плиты со светящимися швами, шлем-монолит')
def r5d():
    stone = P('#c8ccd4', mottle('#aab0ba', 1.2, 0.65), trim(0.25, BLUE, glow=True), noise=0.08)
    bs = chest(P('#c8ccd4', mottle('#aab0ba', 1.2, 0.65), trim(0.25, BLUE, glow=True), disc(('north',), 5, 4.6, 2.4, BLUE, glow=True, r0=1.8),
                 disc(('north',), 5, 4.6, 0.8, '#ffffff', glow=True), noise=0.08), inf=1.4)
    bs += upper_arm(stone) + vambrace(stone, inf=1.0) + belt(P('#7a808c')) + thigh(stone) + boot(stone, toe=0.8, inf=1.1)
    bs += sym(box('rightArm', -6.8, -5.6, -4.2, 7.4, 5.4, 8.4, stone, rot=(0, 0, -6), pivot=(-1, -3, 0)))
    bs += helm(P('#c8ccd4', mottle('#aab0ba', 1.2, 0.65), trim(0.25, BLUE, glow=True), rect(('north',), 1.4, 8.6, 6.4, 7.2, BLUE, glow=True), noise=0.08),
               inf=1.1, top_extra=3.4)
    return bs


@new('r5e', 5, 'Д', 'Звездочёт: ночное небо - тёмно-синий плащ со звёздами, серп луны')
def r5e():
    navy = '#1c2450'
    stars = mottle('#ffffff', 2.4, 0.95, glow=True)
    cloth = P(navy, stars, trim(0.35, '#c8d0e8'))
    bs = chest(cloth) + upper_arm(P(navy, stars)) + vambrace(P('#c8d0e8', trim(0.3, '#7a84a8')))
    bs += belt(P('#c8d0e8', rect(('north',), 4.0, 5.4, 0.3, 2.3, BLUE))) + thigh(P('#141a3c')) + boot(P('#c8d0e8', trim(0.3, '#7a84a8')), toe=0.4)
    bs += hood(P(navy, stars, trim(0.35, '#c8d0e8')), shadow='#05081a', tip_len=5.0, capelet=P(navy, stars, trim(0.35, '#c8d0e8')))
    bs += cloak(P(navy, stars, trim(0.5, '#c8d0e8'), grad=0.2), length=22, flare=10)
    moon = P('#fff4c0', glow=True, bevel=0.1, alpha=lambda p, f, b: ((((p[..., 0] - 1.8) ** 2 + (p[..., 1] - 1.8) ** 2) < 3.2)
                                                                        & (((p[..., 0] - 2.6) ** 2 + (p[..., 1] - 1.4) ** 2) > 2.0)).astype(float))
    bs += [box('body', -1.8, -0.8, -4.0, 3.6, 3.6, 0.5, moon)]
    orb = P('#dfe8ff', glow=True, bevel=0.2)
    bs += [box('body', x, y, z, s, s, s, orb, rot=(45, 45, 0), pivot=(x + s / 2, y + s / 2, z + s / 2))
           for (x, y, z, s) in ((-9.0, -3.0, -1.0, 1.2), (8.0, -1.0, 1.4, 1.0), (-7.4, -6.6, 2.0, 0.8))]
    return bs


@new('r5f', 5, 'Е', 'Буревестник: грозовые тучи на плечах, молнии, шлем с клювом')
def r5f():
    storm = '#4a5262'
    sides = ('north', 'south')

    def plate_(*extra):
        return P(storm, zigzag(sides, 3.0, 1.3, 3.6, 0.32, '#d8f0ff'), trim(0.35, '#2c323e'), *extra)
    bs = chest(plate_()) + upper_arm(P('#3a404e', trim(0.3, '#2c323e'))) + vambrace(P(storm, trim(0.35, '#2c323e')))
    bs += belt(P('#2c323e', rect(('north',), 4.0, 5.4, 0.3, 2.3, '#d8f0ff'))) + thigh(P('#3a404e')) + boot(P(storm, trim(0.35, '#2c323e')), toe=0.6)
    cloud = P('#8a92a4', mottle('#6a7284', 1.0, 0.62), mottle('#c8d0dc', 1.2, 0.86), bevel=0.35, noise=0.04)
    rnd = np.random.RandomState(7)
    for _ in range(10):
        s = rnd.uniform(3.2, 4.8)
        bs += sym(box('rightArm', -4.4 - s / 2 + rnd.uniform(-1.8, 1.0), -3.6 - s / 2 + rnd.uniform(-0.8, 0.6), rnd.uniform(-3.0, 3.0) - s / 2, s, s * 0.72, s,
                      cloud))
    bolt = P('#e8f6ff', glow=True, bevel=0.0)
    for zz, segs in ((-1.4, ((2.6, 0, -165), (2.2, 0, -200), (2.6, 0, -160), (2.0, 0, -195))), (1.8, ((2.2, 0, -195), (2.4, 0, -162), (1.8, 0, -198)))):
        bs += sym(*chain('rightArm', (-7.0, -1.8, zz), segs, 0.55, bolt, taper=0.9))
    bs += helm(plate_(rect(('north',), 1.4, 8.6, 3.2, 4.2, '#0a0c12'), rect(('north',), 2.0, 3.4, 3.4, 4.0, '#bfe4ff', glow=True),
                      rect(('north',), 6.6, 8.0, 3.4, 4.0, '#bfe4ff', glow=True)))
    fe = P('#2e3440', rect(('north', 'south', 'east', 'west'), -1, 99, -1, 1.0, '#e8eef8'), bevel=0.1)
    beak = P('#9aa4b4', trim(0.2, '#4a5262'), bevel=0.15)
    bs += [box('head', -1.2, -5.0, -7.0, 2.4, 2.2, 2.4, beak), box('head', -0.7, -4.4, -8.8, 1.4, 1.4, 2.2, beak, rot=(-20, 0, 0), pivot=(0, -3.8, -7.0))]
    for x in (-1.4, 0.0, 1.4):
        bs += chain('head', (x, -9.0, 1.0), ((2.4, 70, 0), (2.6, 95, 0), (2.2, 115, 0)), 1.0, None, taper=0.8, colors=('#2e3440', '#e8eef8'))
    return bs


# ---- R7 Галит: зелень, финал пути
HA, HAD, HAM, HAL, BLK, GLOWH, GOLDH = '#50e010', '#1e4a0a', '#3a8a10', '#c0ff80', '#141a14', '#9aff40', '#e0b84a'
WORLD_GEMS = (GEM, '#ff7a2a', '#2a7aff', '#d040ff', '#bfe4ff', '#ff3020')  # R1-R6


@new('r7d', 7, 'Г', 'Венец странствий: на груди и в короне камни всех пройденных миров')
def r7d():
    plate = P(HAD, trim(0.45, GOLDH), mottle(HAM, 1.2, 0.92))
    bs = chest(P(HAD, trim(0.5, GOLDH), mottle(HAM, 1.2, 0.92), disc(('north',), 5, 4.4, 4.3, GOLDH, r0=3.7))) + upper_arm(P(HAD, trim(0.3, HAM)))
    bs += vambrace(plate) + pauldron(P(HAD, trim(0.5, GOLDH), disc(('west',), 3.6, 1.6, 1.0, HA)), tilt=-14, big=1.0)
    bs += belt(P(BLK, rect(('north',), 4.0, 5.4, 0.3, 2.3, GOLDH))) + thigh(P(HAD, trim(0.3, HAM))) + boot(plate, toe=0.6)
    bs += [box('body', -1.3, 2.1, -3.7, 2.6, 2.6, 0.8, P(HA, glow=True, bevel=0.1))]
    for i, c in enumerate(WORLD_GEMS):
        a = math.radians(-90 + i * 60)
        x, y = 4.0 * math.cos(a), 3.4 + 4.0 * math.sin(a)
        bs.append(box('body', x - 0.65, y - 0.65, -3.6, 1.3, 1.3, 0.7, P(c, glow=True, bevel=0.15)))
    bs += helm(P(HAD, trim(0.45, GOLDH), rect(('north',), 1.6, 8.4, 4.2, 5.0, '#071204'), rect(('north',), 4.6, 5.4, 4.2, 8.0, '#071204')))
    crown = P(GOLDH, trim(0.25, '#9a7420'))
    bs += [box('head', -5.2, -10.6, -5.2, 10.4, 1.6, 10.4, crown)]
    for i, c in enumerate(WORLD_GEMS):
        x = -4.2 + i * 1.68
        bs += [box('head', x - 0.5, -12.6, -5.3, 1.0, 2.2, 0.6, crown), box('head', x - 0.45, -11.0, -5.6, 0.9, 0.9, 0.4, P(c, glow=True))]
    bs += cloak(P(HAD, trim(0.6, GOLDH), grad=0.3), length=21, flare=9)
    return bs


@new('r7e', 7, 'Д', 'Парящие латы: пластины галита висят вокруг тела, между ними светится ядро')
def r7e():
    suit = P(BLK, rect(('north', 'south', 'east', 'west'), -1, 99, 5.6, 6.4, GLOWH, glow=True), trim(0.2, GLOWH, glow=True))
    bs = chest(suit, inf=0.3) + upper_arm(P(BLK, trim(0.2, HAM)), inf=0.3) + thigh(P(BLK, trim(0.2, HAM)), inf=0.3)
    pl = P(HA, facets('#ffffff', 0.18), trim(0.35, HAD), bevel=0.1)
    bs += [box('body', -4.6, -0.6, -4.8, 9.2, 5.6, 1.0, pl), box('body', -3.6, 6.0, -4.6, 7.2, 4.0, 1.0, pl),
           box('body', -4.6, -0.2, 3.6, 9.2, 9.0, 1.0, pl), box('body', -1.6, 4.8, -4.4, 3.2, 1.4, 0.6, P(HAL, glow=True))]
    bs += sym(box('rightArm', -6.6, -6.4, -3.4, 6.4, 1.6, 6.8, pl, rot=(0, 0, -16), pivot=(-1, -5, 0)),
              box('rightArm', -5.4, 4.6, -3.0, 1.2, 4.4, 6.0, pl), box('rightLeg', -2.8, 1.0, -4.0, 5.6, 4.4, 1.0, pl),
              box('rightLeg', -3.0, 8.0, -3.4, 6.0, 4.6, 6.8, pl))
    bs += [box('head', -4.6, -9.6, -4.6, 9.2, 1.2, 9.2, pl), box('head', -5.4, -7.8, -5.6, 4.2, 6.6, 1.0, pl),
           box('head', 1.2, -7.8, -5.6, 4.2, 6.6, 1.0, pl), box('head', -1.0, -7.8, -5.2, 2.0, 6.6, 0.4, P(HAL, glow=True)),
           box('head', -5.6, -7.8, -4.0, 1.0, 6.6, 8.6, pl), box('head', 4.6, -7.8, -4.0, 1.0, 6.6, 8.6, pl),
           box('head', -4.6, -8.0, 4.6, 9.2, 7.4, 1.0, pl)]
    bs += vring('body', (0, 4.0, 5.6), 5.2, 18, 0.45, P(GLOWH, glow=True))
    return bs


@new('r7f', 7, 'Е', 'Странник у цели: длинный сюртук, шляпа с пером, галитовый фонарь')
def r7f():
    coat = P('#24401a', trim(0.35, '#0e1e08'), rect(('north',), 4.7, 5.3, 0, 99, '#0e1e08'))
    bs = chest(P('#24401a', trim(0.35, '#0e1e08'), rect(('north',), 4.7, 5.3, 0, 99, '#0e1e08'),
                 *[disc(('north',), 3.6, y, 0.45, HAL, glow=True) for y in (2.0, 4.6, 7.2)], *[disc(('north',), 6.4, y, 0.45, HAL, glow=True) for y in (2.0, 4.6, 7.2)]))
    bs += upper_arm(P('#24401a', trim(0.3, '#0e1e08'))) + vambrace(P('#3a2a1c', rect(('north', 'south', 'east', 'west'), -1, 99, -1, 1.0, HA)))
    bs += belt(P('#3a2a1c', rect(('north',), 4.0, 5.4, 0.3, 2.3, GOLDH)))
    bs += skirt(P('#24401a', trim(0.35, '#0e1e08'), stripe(('north',), 0.0, 5.3, 0.4, '#0e1e08')), top=10.6, length=9.6, inf=1.1)
    bs += thigh(P('#2c2418')) + boot(P('#3a2a1c', trim(0.3, '#1e140c')), top=8.0, toe=0.5)
    bs += [box('body', -5.4, -1.6, -3.4, 10.8, 2.4, 6.8, P(HA, trim(0.25, HAD)))]
    bs += brim_hat(P('#1a2e12', trim(0.3, '#0a1606')), P('#1a2e12'), P(GOLDH, rect(('north',), 4.0, 5.2, -1, 99, HA, glow=True)))
    bs += chain('head', (3.8, -10.0, 1.0), ((3.6, 30, 35), (3.8, 55, 25), (3.4, 80, 15), (2.6, 100, 5)), 1.6, None, taper=0.82, colors=(HAM, HAL))
    bs += [box('body', -6.8, 11.0, -1.6, 2.6, 3.4, 2.6, P('#2a2a22', rect(('north', 'south', 'east', 'west'), 0.45, 2.15, 0.5, 2.9, GLOWH, glow=True))),
           box('body', -6.2, 10.2, -1.0, 1.4, 0.9, 1.4, P(GOLDH))]
    return bs


if __name__ == '__main__':
    for k in sys.argv[1:] or NEW:
        build(k)
