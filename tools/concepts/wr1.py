"""Концепты брони Странника, вилка R1: ступень куётся из финального сета обычного мира, и у каждого сета свой облик. Божественная - r1v из пятого раунда, здесь
Тень, Бедрок, Энд и Порча. Тело на пропорциях Ботании (детали wslim), плащей нет, на спине вещь по теме семейства. Цвета сняты с текстур брони Divine.

Запуск: python wr1.py [ключ ...]  (без ключей - все); картинки в wconcepts.OUT.
"""
import sys

import numpy as np

import wconcepts as w
from wconcepts import P, box, chain, disc, facets, mottle, rect, rod, stripe, sym, tip, trim, veins
from wslim import SIDES, belt, boot, bracer, breastplate, bx, feather as side_feather, knee, shoulder, thigh, under, waist
from wslim5 import KEYS, bands, diamond, feather as flat_feather, feather_alpha, plus_hole, scales, seams, slim, spike, zigzag

MINE = len(KEYS)


def helm(paint):
    """Закрытый шлем по голове, как у r3v и r7z."""
    return box('head', -4.55, -8.55, -4.75, 9.1, 8.4, 9.3, paint)


def blade_mask(wd, L):
    """Клинок: сужается к кончику (кончик - верх коробки)."""
    def a(p, face, b):
        x = np.abs(p[..., 0] - wd / 2) / (wd / 2)
        return (x <= np.clip(p[..., 1] / (L * 0.16), 0, 1) + 0.01).astype(float)
    return a


def fin(bone, start, L, wd, paint, rx=0.0, rz=0.0):
    """Плоский плавник или шип: от точки start к кончику, наклон вокруг неё."""
    sx, sy, sz = start
    return box(bone, sx - 0.15, sy - L, sz - wd / 2, 0.3, L, wd, paint, rot=(rx, 0, rz), pivot=(sx, sy, sz))


def fin_mask(wd, L):
    """Плавник вдоль оси z: узкий вверху, срезан назад."""
    def a(p, face, b):
        z = p[..., 2] / wd
        return (z >= 1.0 - np.clip(p[..., 1] / L, 0, 1) - 0.01).astype(float)
    return a


# ---- Тень: скорость. Чёрное с серым, белая прорезь глаз, всё зализано назад

SH, SH_D, SH_M, SH_L, SH_E = '#2e2e2e', '#101010', '#474747', '#9a9a9a', '#eef4ff'


@slim('r1t', 1, 'Тень: зализанные чёрные латы с серым шевроном, глухой шлем с белой прорезью и плавниками назад, тонкие планки назад на плечах, предплечьях и пятках')
def r1t():
    lean = P(SH, trim(0.25, SH_D))
    light = P(SH_M, trim(0.25, SH_D))
    suit = P(SH_D, trim(0.25, '#000000'))
    bar = P(SH_L, trim(0.12, SH_M), bevel=0.0, noise=0.03)
    # грудь: два шеврона вниз, как стрелка скорости
    chest = P(SH, trim(0.25, SH_D), stripe(('north',), 0.85, 0.3, 0.4, SH_L), stripe(('north',), -0.85, 8.9, 0.4, SH_L), stripe(('north',), 0.85, -1.9, 0.3, SH_M), stripe(('north',), -0.85, 11.1, 0.3, SH_M))
    bs = under(suit, suit) + breastplate(chest, front=-2.9) + waist(suit) + belt(P(SH_M, trim(0.25, SH_D), rect(('north',), 3.7, 5.0, 0.4, 1.9, SH_L)), h=1.4)
    bs += shoulder(light, h=2.4, out=0.5, half=2.3) + bracer(P(SH, trim(0.25, SH_D), rect(SIDES, -1, 99, 1.9, 2.5, SH_L))) + thigh(lean) + knee(light)
    bs += boot(P(SH, trim(0.25, SH_D), rect(SIDES, -1, 99, -1, 0.7, SH_L)), toe=1.2)
    # тонкие планки, зализанные назад: на плечах, предплечьях и пятках
    for (bone, x, y, z, dz, a) in (('rightArm', -3.55, -1.4, 1.6, 3.6, 10), ('rightArm', -3.55, -0.2, 1.6, 2.8, 10), ('rightArm', -3.55, 6.2, 1.8, 3.2, 14), ('rightArm', -3.55, 7.4, 1.8, 2.4, 14),
                                 ('rightLeg', -2.55, 8.6, 1.6, 3.2, 16), ('rightLeg', -2.55, 9.8, 1.6, 2.4, 16)):
        bs += sym(box(bone, x, y, z, 0.3, 0.8, dz, bar, rot=(a, 0, 0), pivot=(x, y + 0.4, z)))
    # шлем: узкая светящаяся прорезь, под ней тёмная маска, на макушке узкий гребень назад
    bs += [helm(P(SH, trim(0.25, SH_D), rect(('north',), 0.7, 8.4, 3.4, 5.9, SH_D), rect(('north',), 1.2, 7.9, 4.3, 5.1, SH_E, glow=True), rect(('north',), 3.8, 5.3, 6.2, 8.4, SH_M), rect(('up',), 3.8, 5.3, -1, 99, SH_L)))]
    wing = P(SH_L, trim(0.15, SH_M), rect('all', -1, 99, -1, 0.8, '#c4c8d0'), bevel=0.0, noise=0.03)
    bs += sym(side_feather(-4.6, -0.6, 5.6, 1.4, wing, rx=-64, rz=-4), side_feather(-3.4, 0.2, 4.6, 1.2, wing, rx=-74, rz=-4), side_feather(-2.2, 1.0, 3.6, 1.0, wing, rx=-84, rz=-4))
    return bs


# ---- Бедрок: стойкость. Крапчатая порода, рубленые плиты, глухой шлем с прорезью

BR, BR_D, BR_L, BR_K, BR_E = '#525252', '#2e2e2e', '#7c7c7c', '#191919', '#c4ccd4'


def stone(*decals):
    return P(BR, mottle(BR_D, 1.0, 0.62), mottle(BR_L, 1.4, 0.8), *decals, trim(0.3, BR_K))


@slim('r1k', 1, 'Бедрок: латы из крапчатой породы, широкие рубленые наплечники, глухой шлем с Т-образной прорезью и рубленым венцом')
def r1k():
    suit = P(BR_K, trim(0.3, '#0a0a0a'))
    dark = P(BR_D, mottle(BR_K, 1.0, 0.7), trim(0.3, BR_K))
    # грудь: крупные плиты со швом и вставка из светлого камня
    chest = stone(rect(('north',), 4.3, 4.9, -1, 99, BR_K), rect(('north',), -1, 99, 2.9, 3.4, BR_K), rect(('north',), 3.3, 5.9, 1.4, 4.7, BR_K), rect(('north',), 3.7, 5.5, 1.8, 4.3, BR_L))
    bs = under(suit, suit) + breastplate(chest, front=-3.5, back=2.7) + waist(dark) + belt(P(BR_K, trim(0.2, '#050505'), rect(('north',), 3.1, 6.0, 0.2, 2.1, BR_L), rect(('north',), 3.7, 5.4, 0.7, 1.6, BR_K)), h=2.2)
    # наплечники: две ступени породы, верхняя шире
    bs += shoulder(stone(rect(SIDES, -1, 99, 2.0, 2.5, BR_K)), top=-2.6, h=4.6, out=1.4, half=3.1)
    bs += sym(bx('rightArm', -4.8, -3.2, -2.1, 3.0, 1.0, 4.2, stone()))
    bs += bracer(stone(rect(SIDES, -1, 99, 1.6, 2.1, BR_K)), top=3.0) + thigh(stone(rect(('north', 'south'), 1.7, 2.3, -1, 99, BR_K))) + knee(P(BR_L, mottle(BR_D, 1.2, 0.7), trim(0.25, BR_K)))
    bs += boot(stone(rect(SIDES, -1, 99, 1.8, 2.2, BR_K)), toe=1.4, top=7.8)
    # шлем: глухой, прорезь Т, сверху рубленый венец
    bs += [helm(stone(rect(('north',), 1.4, 7.7, 3.8, 5.0, BR_K), rect(('north',), 3.8, 5.3, 3.8, 8.3, BR_K), rect(('north',), 1.9, 3.3, 4.1, 4.7, BR_E, glow=True), rect(('north',), 5.8, 7.2, 4.1, 4.7, BR_E, glow=True)))]
    crown = stone()
    bs += [box('head', -3.6, -9.8, -3.6, 7.2, 1.3, 7.2, crown), box('head', -1.9, -10.8, -1.9, 3.8, 1.0, 3.8, P(BR_L, mottle(BR_D, 1.2, 0.7), trim(0.25, BR_K)))]
    bs += sym(box('head', -5.2, -6.8, -2.8, 0.7, 3.2, 5.6, stone()))
    return bs


# ---- Энд: пустотный шаг. Синие гладкие латы с белыми искрами, шлем с большим глазом, на спине кольцо портала

EN, EN_D, EN_M, EN_L, EN_W, EN_G, EN_K = '#1674d0', '#0a3a78', '#0e60aa', '#5ab4f4', '#e4ecf4', '#6cf0d8', '#051c40'
SPARK = (mottle(EN_W, 5.0, 0.955),)


def gate(cx, cy, z, r, n, paint):
    """Кольцо в плоскости спины: брусья по кругу."""
    out = []
    L = 2 * np.pi * r / n * 1.12
    for i in range(n):
        a = 2 * np.pi * i / n
        px, py = cx + r * np.cos(a), cy + r * np.sin(a)
        out.append(box('body', px - L / 2, py - 0.45, z, L, 0.9, 0.6, paint, rot=(0, 0, float(np.degrees(a)) + 90), pivot=(px, py, z + 0.3)))
    return out


def circle_mask(wd):
    def a(p, face, b):
        return (np.hypot(p[..., 0] - wd / 2, p[..., 1] - wd / 2) <= wd / 2).astype(float)
    return a


@slim('r1n', 1, 'Энд: гладкие синие латы с белыми искрами и светлой окантовкой, шлем с большим глазом Края и плавниками вверх, на спине кольцо портала с жемчужинами')
def r1n():
    plate = P(EN, *SPARK, trim(0.3, EN_D))
    panel = P(EN_M, *SPARK, trim(0.3, EN_D))
    white = P(EN_W, trim(0.25, EN_L))
    suit = P(EN_K, trim(0.25, EN_D))
    chest = P(EN, *SPARK, trim(0.3, EN_D), stripe(('north',), 0.72, 0.7, 0.42, EN_W), stripe(('north',), -0.72, 8.5, 0.42, EN_W), disc(('north',), 4.6, 2.5, 1.5, EN_K), disc(('north',), 4.6, 2.5, 1.0, EN_G, glow=True), disc(('north',), 4.6, 2.5, 0.42, EN_K))
    bs = under(suit, suit) + breastplate(chest) + waist(panel) + belt(P(EN_W, trim(0.25, EN_L), rect(('north',), 3.5, 5.5, 0.4, 1.9, EN_G, glow=True)))
    bs += shoulder(P(EN, *SPARK, trim(0.3, EN_D), rect(SIDES, -1, 99, 2.2, 3.0, EN_W))) + bracer(P(EN, *SPARK, trim(0.3, EN_D), rect(SIDES, -1, 99, 2.2, 2.9, EN_W))) + thigh(plate) + knee(white)
    bs += boot(P(EN, *SPARK, trim(0.3, EN_D), rect(SIDES, -1, 99, 2.7, 99, EN_W)))
    # шлем: тёмное забрало и большой глаз посередине лба, плавники на висках вверх
    bs += [helm(P(EN, *SPARK, trim(0.3, EN_D), diamond(('north',), 4.55, 4.3, 4.1, EN_W), diamond(('north',), 4.55, 4.3, 3.6, EN_K), disc(('north',), 4.55, 4.3, 1.5, EN_G, glow=True), disc(('north',), 4.55, 4.3, 0.6, EN_K),
                rect(('up',), 3.9, 5.2, -1, 99, EN_W), rect(('south',), 3.9, 5.2, -1, 99, EN_W)))]
    up = P(EN_L, trim(0.2, EN_D), rect('all', -1, 99, -1, 1.1, EN_W), bevel=0.0)
    bs += sym(side_feather(-4.4, -2.4, 6.6, 2.4, up, rx=-16, rz=-16), side_feather(-4.4, 0.0, 4.8, 2.0, up, rx=-30, rz=-16))
    # кольцо портала на спине: синяя оправа, внутри тёмная пустота в искрах, четыре жемчужины на оправе
    wd = 8.8
    bs += [box('body', -wd / 2, 4.6 - wd / 2, 2.95, wd, wd, 0.25, P(EN_K, mottle(EN_W, 5.0, 0.93), mottle(EN_G, 4.0, 0.955), alpha=circle_mask(wd), bevel=0.0, noise=0.02))]
    bs += gate(0.0, 4.6, 3.1, 4.9, 18, P(EN_L, trim(0.15, EN_D), bevel=0.0))
    pearl = P(EN_G, trim(0.2, EN_W), glow=True, bevel=0.1)
    for (dx, dy) in ((0, -4.9), (4.9, 0), (0, 4.9), (-4.9, 0)):
        bs += [box('body', dx - 0.85, 4.6 + dy - 0.85, 2.95, 1.7, 1.7, 1.0, pearl, rot=(0, 0, 45), pivot=(dx, 4.6 + dy, 3.45))]
    return bs


# ---- Порча: дальний бой и тьма. Сине-фиолетовая плоть с пурпурными прожилками, шлем с глазами Груи, шипы, на спине пушка порчи

CO, CO_D, CO_M, CO_L, CO_P, CO_W, CO_K = '#594d9a', '#2a2150', '#4b3b82', '#7a6cc4', '#c040c0', '#d8d8d8', '#120c24'
FLESH = (mottle(CO_P, 2.0, 0.91), mottle(CO_K, 2.2, 0.945))
BONE_C = '#b4accc'


def eye(cu, cv, r):
    """Глаз Груи: белок, пурпурная радужка, тёмный зрачок."""
    return (disc(('north',), cu, cv, r * 1.35, CO_K), disc(('north',), cu, cv, r, CO_W), disc(('north',), cu, cv, r * 0.72, CO_P, glow=True), disc(('north',), cu, cv, r * 0.3, CO_K))


@slim('r1p', 1, 'Порча: сине-фиолетовые латы с пурпурными прожилками и глазом на груди, шлем с тремя глазами Груи и шипами, шипы на плечах, на спине пушка порчи')
def r1p():
    plate = P(CO, *FLESH, trim(0.3, CO_D))
    dark = P(CO_M, mottle(CO_K, 2.2, 0.9), trim(0.3, CO_D))
    suit = P(CO_K, veins(CO_P, 0.7, 0.16, glow=False), trim(0.25, '#06040e'))
    bone_ = P(CO_W, trim(0.15, CO_L), alpha=feather_alpha(1.5, 3.4), bevel=0.0, noise=0.03)
    chest = P(CO, *FLESH, veins(CO_P, 0.6, 0.14, glow=False), trim(0.3, CO_D), stripe(('north',), 0.72, 1.2, 0.45, CO_K), *eye(4.6, 2.6, 1.8))
    bs = under(suit, suit) + breastplate(chest) + waist(dark) + belt(P(CO_K, trim(0.25, CO_M), rect(('north',), 3.5, 5.5, 0.4, 1.9, CO_L), disc(('north',), 4.5, 1.15, 0.5, CO_P, glow=True)))
    bs += shoulder(P(CO, *FLESH, trim(0.3, CO_D), rect(('up',), 1.0, 3.8, 1.0, 4.2, CO_L))) + bracer(P(CO, *FLESH, trim(0.3, CO_D), zigzag(SIDES, 2.2, 0.8, 1.6, 0.25, CO_P))) + thigh(plate) + knee(P(CO_L, trim(0.25, CO_D)))
    bs += boot(P(CO, *FLESH, trim(0.3, CO_D), rect(SIDES, -1, 99, -1, 0.8, CO_K)))
    # шипы из кости на плечах и вдоль предплечья
    for (z, L, rz, rx) in ((-1.6, 2.6, -26, -12), (0.0, 3.4, -34, 0), (1.6, 2.4, -24, 12)):
        bs += sym(spike('rightArm', (-3.4, -2.0, z), L, 1.5, BONE_C, rx=rx, rz=rz))
    for z in (-1.0, 1.0):
        bs += sym(spike('rightArm', (-3.2, 5.6, z), 1.9, 1.1, BONE_C, rz=-74))
    # шлем: закрытый, в тёмной маске три глаза, сверху и у висков шипы
    bs += [helm(P(CO, *FLESH, trim(0.3, CO_D), *eye(4.55, 2.2, 1.15), *eye(2.4, 4.6, 1.05), *eye(6.7, 4.6, 1.05), rect(('north',), 2.6, 6.5, 7.0, 7.5, CO_K), rect(('north',), 3.3, 3.7, 6.6, 8.0, CO_K), rect(('north',), 5.4, 5.8, 6.6, 8.0, CO_K)))]
    for (x, L, rz) in ((-1.5, 3.2, -14), (1.5, 3.2, 14)):
        bs += [spike('head', (x, -8.4, -0.8), L, 1.6, BONE_C, rx=-22, rz=rz)]
    bs += sym(*chain('head', (-4.6, -6.4, 0.2), ((2.2, 10, -26), (2.0, 20, -8), (1.6, 30, 12)), 1.1, P(BONE_C, trim(0.1, CO_D), bevel=0.0), taper=0.72))
    # ремень через грудь и пушка наискось за спиной: дуло над плечом светится
    bs += [box('body', -4.7, -0.4, 2.55, 9.4, 1.0, 0.2, P(CO_K), rot=(0, 0, -36), pivot=(0, 4.8, 2.6))]
    s0, L, a = (4.8, 11.4, 4.0), 17.0, -26
    barrel = P(CO_L, trim(0.15, CO_D), rect(SIDES, -1, 99, 2.2, 3.0, CO_K), rect(SIDES, -1, 99, 8.0, 8.8, CO_K), rect(SIDES, -1, 99, 13.0, 13.8, CO_K), bevel=0.0)
    top = tip(s0, L, rz=a)
    bs += [rod('body', s0, L, 2.8, barrel, rz=a),
           box('body', top[0] - 2.1, top[1] - 1.0, top[2] - 2.1, 4.2, 2.0, 4.2, P(CO_L, trim(0.2, CO_K), rect(('up',), 0.9, 3.3, 0.9, 3.3, CO_P, glow=True)), rot=(0, 0, a), pivot=tuple(top)),
           box('body', top[0] - 1.1, top[1] - 1.6, top[2] - 1.1, 2.2, 0.8, 2.2, P(CO_P, glow=True), rot=(0, 0, a), pivot=tuple(top))]
    grip = tip(s0, 2.4, rz=a)
    bs += [box('body', grip[0] - 2.1, grip[1] - 1.1, grip[2] - 1.5, 4.2, 2.2, 3.0, P(CO_D, trim(0.2, CO_K), disc(('south',), 2.1, 1.1, 0.6, CO_P, glow=True)), rot=(0, 0, a), pivot=tuple(grip))]
    return bs


# ---- варианты Б и В: другие образы тех же сетов

def between(bone, a, b, t, paint):
    """Брусок между двумя точками в одной плоскости z."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    v = b - a
    L = float(np.linalg.norm(v))
    ang = float(np.degrees(np.arctan2(v[0], -v[1])))
    rz = ang if np.allclose(tip(a, L, rz=ang), b, atol=0.01) else -ang
    return rod(bone, tuple(a), L, t, paint, rz=rz)


def path(bone, start, segs, t, paint, taper=1.0):
    """Ломаная из брусков: (длина, rz). Возвращает коробки и конец."""
    out, s = [], np.asarray(start, float)
    for (L, rz) in segs:
        out.append(rod(bone, tuple(s), L, t, paint, rz=rz))
        s = tip(s, L * 0.95, rz=rz)
        t = max(0.4, t * taper)
    return out, s


def tri_mask(wd, L):
    """Треугольник: кончик наверху коробки."""
    def a(p, face, b):
        return (np.abs(p[..., 0] - wd / 2) / (wd / 2) <= np.clip(p[..., 1] / L, 0, 1) + 0.01).astype(float)
    return a


# Тень Б: ворон. Перья, клюв, теневой лук за спиной

@slim('r1u', 1, 'Тень, ворон: чёрная чешуя перьев на груди, шлем с клювом и круглыми белыми глазами, перья на плечах и затылке, за спиной теневой лук')
def r1u():
    lean = P(SH, trim(0.25, SH_D))
    suit = P(SH_D, trim(0.25, '#000000'))
    chest = P(SH, trim(0.25, SH_D), scales(('north',), (0.6, 8.2, 0.4, 5.4), (SH, SH_M, '#585858', '#3a3a3a'), s=1.5))
    bs = under(suit, suit) + breastplate(chest, front=-2.9) + waist(suit) + belt(P(SH_M, trim(0.25, SH_D), rect(('north',), 3.7, 5.0, 0.4, 1.7, SH_L)), h=1.4)
    bs += shoulder(lean, h=2.6, out=0.6, half=2.4) + bracer(P(SH, trim(0.25, SH_D), rect(SIDES, -1, 99, 1.9, 2.5, SH_M))) + thigh(lean) + knee(P(SH_M, trim(0.2, SH_D)))
    bs += boot(P(SH, trim(0.25, SH_D), rect(SIDES, -1, 99, -1, 0.7, SH_M)), toe=1.2)
    for (z, L, rz, rx) in ((-1.6, 3.4, -36, -10), (0.0, 4.4, -46, 0), (1.6, 3.2, -34, 10)):
        bs += sym(flat_feather('rightArm', (-3.5, -1.9, z), L, 1.8, SH_M, SH_L, SH_D, rx=rx, rz=rz))
    bs += [helm(P(SH, trim(0.25, SH_D), disc(('north',), 2.5, 3.5, 1.15, SH_D), disc(('north',), 6.6, 3.5, 1.15, SH_D), disc(('north',), 2.5, 3.5, 0.72, SH_E, glow=True), disc(('north',), 6.6, 3.5, 0.72, SH_E, glow=True)))]
    beak, beak_d = P('#9a9ea6', trim(0.2, SH_D)), P('#6e727a', trim(0.2, SH_D))
    bk = dict(rot=(20, 0, 0), pivot=(0, -3.6, -4.75))
    bs += [box('head', -1.9, -5.2, -6.1, 3.8, 3.2, 1.5, beak, **bk), box('head', -1.4, -4.8, -7.5, 2.8, 2.4, 1.6, beak, **bk), box('head', -0.9, -4.4, -8.9, 1.8, 1.7, 1.6, beak_d, **bk), box('head', -0.45, -4.0, -10.1, 0.9, 1.1, 1.4, beak_d, **bk)]
    for (x, L, rx, rz) in ((-3.0, 5.0, -44, -12), (-1.0, 6.2, -38, -4), (1.0, 6.2, -38, 4), (3.0, 5.0, -44, 12)):
        bs.append(flat_feather('head', (x, -8.3, 1.6), L, 2.2, SH_L, '#c4c8d0', SH_D, rx=rx, rz=rz))
    # лук наискось: рукоять посередине спины, плечи изгибаются, тетива прямая
    wood = P(SH_M, bands((SH_M, '#383838'), 1.2, axis=1), trim(0.1, SH_D), bevel=0.0)
    c, t0 = (0.0, 5.0, 3.1), -30.0
    a = (-24.0, -10.0, 6.0, 26.0)
    up, top = path('body', c, [(5.2, t0 + x) for x in a], 1.0, wood)
    low, bot = path('body', c, [(5.2, t0 + 180 - x) for x in a], 1.0, wood)
    bs += up + low + [between('body', bot, top, 0.28, P('#d4d8e0', bevel=0.0, noise=0.0))]
    bs += [box('body', c[0] - 0.9, c[1] - 1.5, c[2] - 0.8, 1.8, 3.0, 1.6, P(SH_L, trim(0.15, SH_D)), rot=(0, 0, t0), pivot=c)]
    return bs


# Тень В: призрак. Белая маска, ткань в обмотках, большой сюрикен

def shuriken(bone, cx, cy, z, r, wd, steel, hub):
    out = []
    paint = P(steel, rect(('north', 'south'), wd / 2 - 0.2, wd / 2 + 0.2, -1, 99, '#50545c'), alpha=tri_mask(wd, r), bevel=0.0, noise=0.03)
    for a in (45, 135, 225, 315):
        out.append(box(bone, cx - wd / 2, cy - r, z, wd, r, 0.3, paint, rot=(0, 0, a), pivot=(cx, cy, z + 0.15)))
    h = wd * 0.62
    out.append(box(bone, cx - h / 2, cy - h / 2, z - 0.1, h, h, 0.5, P(hub, disc(('south', 'north'), h / 2, h / 2, h * 0.26, SH_D), trim(0.15, SH_D), bevel=0.0), rot=(0, 0, 45), pivot=(cx, cy, z + 0.15)))
    return out


@slim('r1w', 1, 'Тень, призрак: одежда в обмотках с ремнями крест-накрест, голова в тёмной повязке и белая маска без лица, подсумки, за спиной большой сюрикен')
def r1w():
    wrap = ('#2e2e2e', '#262626', '#383838')
    cloth = P(wrap[0], bands(wrap, 1.0, axis=1), trim(0.2, SH_D))
    metal = P('#8a8e96', trim(0.2, SH_D))
    chest = P(wrap[0], bands(wrap, 1.0, axis=1), trim(0.2, SH_D), stripe(('north',), 0.9, 0.3, 0.42, SH_D), stripe(('north',), -0.9, 8.9, 0.42, SH_D), disc(('north',), 4.6, 2.75, 0.8, '#8a8e96'))
    bs = under(cloth, cloth) + breastplate(chest, front=-2.8) + waist(P('#1c1c1c', trim(0.2, SH_D))) + belt(P(SH_D, trim(0.2, '#000000'), rect(('north',), 3.6, 5.1, 0.3, 1.6, SH_L)), h=1.5)
    bs += shoulder(cloth, h=2.2, out=0.4, half=2.2) + bracer(P(wrap[0], bands(wrap, 1.0, axis=1), trim(0.2, SH_D), rect(SIDES, -1, 99, 1.6, 2.4, '#8a8e96'))) + thigh(cloth) + knee(metal) + boot(P(wrap[0], bands(wrap, 1.0, axis=1), trim(0.2, SH_D)), toe=0.8)
    bs += sym(box('body', -3.9, 10.2, -3.15, 1.8, 2.0, 0.8, P(SH_M, trim(0.2, SH_D), rect(('north',), 0.5, 1.3, 0.3, 0.8, SH_L))), box('rightLeg', -2.65, 1.0, -1.2, 0.6, 2.4, 2.4, P(SH_M, trim(0.2, SH_D))))
    bs += [helm(P(wrap[0], bands(wrap, 1.0, axis=1), trim(0.2, SH_D)))]
    bs += [box('head', -3.6, -7.8, -5.0, 7.2, 7.2, 0.3, P('#e2e6ec', trim(0.15, '#9aa0aa'), rect(('north',), 0.9, 2.0, 2.3, 2.9, '#0c0c0c'), rect(('north',), 0.9, 2.9, 2.9, 3.5, '#0c0c0c'), rect(('north',), 5.2, 6.3, 2.3, 2.9, '#0c0c0c'), rect(('north',), 4.3, 6.3, 2.9, 3.5, '#0c0c0c'),
                                                         rect(('north',), 1.7, 2.1, 3.4, 5.2, '#b0b6c0'), rect(('north',), 5.1, 5.5, 3.4, 5.2, '#b0b6c0'), rect(('north',), 2.8, 4.4, 5.8, 6.1, '#7a808a'), bevel=0.0, noise=0.02))]
    scarf = P('#8e929a', bands(('#8e929a', '#7a7e86'), 1.0, axis=1), trim(0.2, SH_D))
    bs += [box('body', -4.7, -0.8, -3.2, 9.4, 1.7, 6.0, scarf), box('body', -3.0, 0.8, -3.15, 1.9, 4.6, 0.3, scarf, rot=(0, 0, 8), pivot=(-2, 0.8, -3)), box('body', -1.3, 0.8, -3.1, 1.6, 3.2, 0.3, scarf, rot=(0, 0, -6), pivot=(-0.5, 0.8, -3))]
    bs += shuriken('body', 0.0, 4.7, 2.95, 7.2, 3.8, '#a4a8b0', '#6a6e76')
    return bs


# Бедрок Б: бастион. Кладка, зубцы, ростовой щит, холодное свечение рун

RUNE = '#8fd8ff'


def masonry(*decals):
    return P('#686868', mottle(BR_D, 2.0, 0.86), mottle(BR_L, 2.2, 0.86), seams(3.0, BR_K, axis=1), *decals, trim(0.3, BR_K))


@slim('r1l', 1, 'Бедрок, бастион: латы как кладка крепости с зубцами на плечах и шлеме, узкая светящаяся прорезь, руна на груди, за спиной ростовой щит из плиты')
def r1l():
    suit = P(BR_K, trim(0.3, '#0a0a0a'))
    dark = P(BR_D, mottle(BR_K, 2.0, 0.8), trim(0.3, BR_K))
    chest = P('#686868', mottle(BR_D, 2.0, 0.86), mottle(BR_L, 2.2, 0.86), trim(0.3, BR_K), rect(('north',), 2.7, 6.5, 0.8, 4.6, BR_K), rect(('north',), 3.1, 6.1, 1.2, 4.2, RUNE, glow=True), rect(('north',), 3.6, 5.6, 1.7, 3.7, BR_K), rect(('north',), 4.3, 4.9, 2.4, 3.0, RUNE, glow=True))
    bs = under(suit, suit) + breastplate(chest, front=-3.4, back=2.7) + waist(dark) + belt(P(BR_K, trim(0.2, '#050505'), rect(('north',), 3.4, 5.7, 0.4, 1.8, BR_L)), h=2.0)
    bs += shoulder(masonry(), top=-2.2, h=4.2, out=1.2, half=2.9)
    merlon = masonry()
    for z in (-2.9, -0.7, 1.5):
        bs += sym(box('rightArm', -4.4, -3.5, z, 1.5, 1.3, 1.4, merlon), box('rightArm', -1.5, -3.5, z, 1.5, 1.3, 1.4, merlon))
    bs += bracer(masonry(), top=3.0) + thigh(masonry()) + knee(P(BR_L, mottle(BR_D, 2.0, 0.8), trim(0.25, BR_K))) + boot(masonry(), toe=1.2, top=7.8)
    bs += [helm(P('#686868', mottle(BR_D, 2.0, 0.86), mottle(BR_L, 2.2, 0.86), seams(3.0, BR_K, axis=1, faces=('east', 'west', 'south')), trim(0.3, BR_K), rect(('north',), 1.1, 8.0, 3.7, 5.0, BR_K), rect(('north',), 1.6, 7.5, 4.1, 4.6, RUNE, glow=True)))]
    for (x, z) in ((-4.5, -4.7), (-1.2, -4.7), (2.1, -4.7), (-4.5, -1.3), (2.1, -1.3), (-4.5, 2.1), (-1.2, 2.1), (2.1, 2.1)):
        bs.append(box('head', x, -9.8, z, 2.4, 1.4, 2.4, merlon))
    # щит: плита с окантовкой и руной, за спиной почти во весь рост
    plate = P('#5e5e5e', mottle(BR_D, 2.0, 0.84), mottle(BR_L, 2.2, 0.88), trim(0.55, BR_K), rect(('south',), 1.1, 8.5, 1.1, 1.8, BR_L), rect(('south',), 1.1, 8.5, 12.2, 12.9, BR_L), rect(('south',), 1.1, 1.8, 1.1, 12.9, BR_L), rect(('south',), 7.8, 8.5, 1.1, 12.9, BR_L),
              rect(('south',), 2.8, 6.8, 4.5, 9.5, RUNE, glow=True), rect(('south',), 3.5, 6.1, 5.2, 8.8, '#2e2e2e'), rect(('south',), 4.4, 5.2, 6.6, 7.4, RUNE, glow=True), rect(('south',), 4.5, 5.1, 2.6, 4.0, RUNE, glow=True), rect(('south',), 4.5, 5.1, 10.0, 11.4, RUNE, glow=True))
    bs += [box('body', -4.8, -0.8, 2.95, 9.6, 14.0, 1.1, plate)]
    return bs


# Бедрок В: недра. Чёрная порода, лава в трещинах, рога, каменные шипы и раскалённое ядро на спине

ROCK, ROCK_D, ROCK_M, LAVA, LAVA_L = '#2c2a2a', '#121010', '#403c3a', '#ff7a1c', '#ffc24a'


def rock(*decals):
    return P(ROCK, mottle(ROCK_M, 2.0, 0.78), veins(LAVA, 0.7, 0.13, glow=True), *decals, trim(0.3, ROCK_D))


@slim('r1m', 1, 'Бедрок, недра: чёрная порода с лавой в трещинах, валуны на плечах, рогатый шлем с раскалённой прорезью, на спине каменные шипы и ядро')
def r1m():
    dark = P(ROCK_D, trim(0.25, '#050404'))
    bs = under(dark, dark) + breastplate(rock(disc(('north',), 4.6, 2.8, 1.5, ROCK_D), disc(('north',), 4.6, 2.8, 1.05, LAVA_L, glow=True)), front=-3.4, back=2.6) + waist(rock()) + belt(P(ROCK_D, trim(0.2, '#050404'), rect(('north',), 3.5, 5.6, 0.3, 1.9, LAVA, glow=True)), h=2.0)
    bs += shoulder(rock(), top=-2.2, h=4.0, out=1.2, half=2.9) + bracer(rock(), top=3.0) + thigh(rock()) + knee(P(ROCK_M, trim(0.25, ROCK_D))) + boot(rock(), toe=1.3, top=7.8)
    bs += sym(box('rightArm', -5.4, -4.6, -1.9, 3.6, 3.0, 3.8, rock(), rot=(10, 18, -16), pivot=(-3.6, -3, 0)))
    bs += [helm(rock(rect(('north',), 1.1, 8.0, 3.6, 5.1, ROCK_D), rect(('north',), 1.6, 3.6, 4.0, 4.8, LAVA_L, glow=True), rect(('north',), 5.5, 7.5, 4.0, 4.8, LAVA_L, glow=True)))]
    horn = P(ROCK_M, trim(0.15, ROCK_D), rect('all', -1, 99, -1, 0.8, LAVA, glow=True), bevel=0.0)
    bs += sym(*chain('head', (-4.6, -7.2, 0.0), ((2.8, 6, -34), (2.6, 12, -8), (2.2, 18, 12)), 1.8, horn, taper=0.72))
    shard = '#3a3634'
    for (x, L, rz) in ((-3.6, 9.5, -40), (-2.0, 11.5, -22), (2.0, 11.5, 22), (3.6, 9.5, 40)):
        bs.append(w.crystal('body', (x, 8.2, 3.0), L, 1.9, shard, rz=rz, glow=False, tipc=LAVA))
    bs += [box('body', -2.3, 1.6, 2.75, 4.6, 6.6, 0.8, P(ROCK, mottle(ROCK_M, 2.0, 0.78), trim(0.3, ROCK_D))), box('body', -1.4, 2.8, 3.2, 2.8, 4.2, 0.6, P(LAVA_L, rect('all', 0.7, 2.1, 1.0, 3.2, '#fff0c0', glow=True), glow=True))]
    return bs


# Энд Б: ходок Края. Почти чёрное тело, длинные глаза как у эндермена, светящиеся линии, кристалл Края на спине

VO, VO_D, VO_M = '#0c1426', '#04070e', '#16233e'
VSTAR = (mottle(EN_W, 5.0, 0.975),)


def void(*decals):
    return P(VO, *VSTAR, *decals, trim(0.25, EN_M))


@slim('r1o', 1, 'Энд, ходок Края: почти чёрные латы в звёздах с синими светящимися линиями, длинные глаза как у эндермена, длинные когти, на спине кристалл Края в рамке')
def r1o():
    suit = P(VO_D, trim(0.2, VO))
    line = lambda a, b: rect(SIDES, a, b, -1, 99, EN_L, glow=True)
    chest = void(rect(('north',), 4.3, 4.9, -1, 99, EN_L, glow=True), disc(('north',), 4.6, 2.6, 1.5, VO_D), disc(('north',), 4.6, 2.6, 1.05, EN_G, glow=True))
    bs = under(suit, suit) + breastplate(chest, front=-2.9) + waist(void()) + belt(P(VO_M, trim(0.2, EN_M), rect(('north',), 3.5, 5.5, 0.5, 1.5, EN_L, glow=True)), h=1.6)
    bs += shoulder(void(rect(SIDES, -1, 99, 2.3, 2.7, EN_L, glow=True)), h=2.8, out=0.6, half=2.3) + bracer(void(line(1.8, 2.2))) + thigh(void(line(1.8, 2.2))) + knee(P(VO_M, trim(0.2, EN_M))) + boot(void(rect(SIDES, -1, 99, -1, 0.5, EN_L, glow=True)), toe=1.4)
    # глаза во всю ширину лица, как у эндермена
    bs += [helm(P(VO, *VSTAR, trim(0.25, EN_M), rect(('north',), 0.7, 3.9, 3.7, 5.0, EN_L, glow=True), rect(('north',), 5.2, 8.4, 3.7, 5.0, EN_L, glow=True), rect(('north',), 1.5, 3.2, 4.1, 4.7, EN_W, glow=True), rect(('north',), 5.9, 7.6, 4.1, 4.7, EN_W, glow=True)))]
    claw = P(VO_D, rect('all', -1, 99, 1.9, 99, EN_L, glow=True), bevel=0.0)
    bs += sym(*[box('rightArm', x, 9.4, -1.5 + i * 1.3, 0.6, 3.2, 0.6, claw) for i, x in enumerate((-2.6, -1.0, 0.4))])
    # кристалл: рамка ромбом, внутри светится куб, над ним жемчужина
    fr, r0 = P(EN_L, trim(0.15, EN_D), bevel=0.0), dict(rot=(0, 0, 45), pivot=(0, 4.2, 3.3))
    bs += [box('body', -3.6, 0.2, 3.0, 7.2, 0.8, 0.6, fr, **r0), box('body', -3.6, 7.4, 3.0, 7.2, 0.8, 0.6, fr, **r0), box('body', -3.6, 0.6, 3.0, 0.8, 7.2, 0.6, fr, **r0), box('body', 2.8, 0.6, 3.0, 0.8, 7.2, 0.6, fr, **r0)]
    bs += [box('body', -1.8, 2.4, 2.95, 3.6, 3.6, 2.0, P(EN_G, facets('#ffffff', 0.3), trim(0.15, '#ffffff'), glow=True, bevel=0.1), rot=(0, 0, 45), pivot=(0, 4.2, 3.95))]
    return bs


# Энд В: дракон. Синяя чешуя, рогатый шлем с пастью, кожистые крылья

DR_E = '#e848f0'


def fan_mask(wd, L):
    """Сектор крыла: острый угол внизу коробки, у лопатки."""
    def a(p, face, b):
        return (np.abs(p[..., 0] - wd / 2) / (wd / 2) <= 1.0 - np.clip(p[..., 1] / L, 0, 1) + 0.01).astype(float)
    return a


@slim('r1q', 1, 'Энд, дракон: синяя чешуя, рогатый шлем с пастью и пурпурными глазами как у дракона Края, шипы на плечах, на спине раскрытые кожистые крылья')
def r1q():
    sc = (EN_M, EN, '#0b4f96', '#1a6cc0')
    scaly = P(EN_M, scales(SIDES, (-1, 99, -1, 99), sc, s=1.3), trim(0.3, EN_K))
    suit = P(EN_K, trim(0.25, EN_D))
    chest = P(EN_M, scales(('north', 'south', 'east', 'west'), (-1, 99, -1, 99), sc, s=1.3), trim(0.3, EN_K), rect(('north',), 2.9, 6.3, 0.6, 5.6, EN_W), bands((EN_W, '#b4c0d0'), 1.2, axis=1, faces=()), rect(('north',), 3.3, 5.9, 1.0, 1.4, '#9aa8bc'), rect(('north',), 3.3, 5.9, 2.4, 2.8, '#9aa8bc'), rect(('north',), 3.3, 5.9, 3.8, 4.2, '#9aa8bc'))
    bs = under(suit, suit) + breastplate(chest) + waist(scaly) + belt(P(EN_K, trim(0.2, EN_D), rect(('north',), 3.6, 5.4, 0.4, 1.7, DR_E, glow=True)), h=1.7)
    bs += shoulder(scaly) + bracer(scaly) + thigh(scaly) + knee(P(EN_W, trim(0.25, '#8a98ac'))) + boot(scaly, toe=1.4)
    for (z, L, rz, rx) in ((-1.7, 2.8, -24, -12), (0.0, 3.8, -30, 0), (1.7, 2.8, -22, 12)):
        bs += sym(spike('rightArm', (-3.4, -2.2, z), L, 1.5, EN_W, rx=rx, rz=rz))
    bs += sym(spike('rightArm', (-3.2, 5.2, 0.0), 2.2, 1.3, EN_W, rz=-76), spike('rightLeg', (-2.1, 8.8, 1.0), 2.2, 1.3, EN_W, rz=-80))
    # шлем: чешуя, пурпурные глаза, вперёд выдаётся морда с зубами, рога назад
    bs += [helm(P(EN_M, scales(('east', 'west', 'south', 'up'), (-1, 99, -1, 99), sc, s=1.3), trim(0.3, EN_K), rect(('north',), 0.9, 8.2, 2.9, 4.6, EN_K), rect(('north',), 1.5, 3.5, 3.2, 4.2, DR_E, glow=True), rect(('north',), 5.6, 7.6, 3.2, 4.2, DR_E, glow=True)))]
    bs += [box('head', -3.0, -4.3, -6.5, 6.0, 2.0, 1.9, P(EN_M, trim(0.25, EN_K), rect(('up',), 2.5, 3.5, -1, 99, EN_W))), box('head', -3.0, -2.1, -6.3, 6.0, 1.6, 1.7, P(EN_K, trim(0.2, EN_D), zigzag(('north',), 0.1, 0.8, 1.2, 0.26, EN_W)))]
    bone_c = P(EN_W, trim(0.1, '#8a98ac'), bevel=0.0)
    bs += sym(box('head', -4.7, -6.2, -5.1, 3.8, 0.8, 0.5, bone_c, rot=(0, 0, 12), pivot=(-2.8, -5.8, -5)))
    bs += sym(*chain('head', (-3.6, -8.2, 0.6), ((3.0, -44, -14), (3.0, -60, -10), (2.6, -78, -6), (2.0, -92, -2)), 1.6, bone_c, taper=0.78))
    # крылья: веером вверх и в стороны от лопатки, четыре кости и три сектора перепонки
    root = (-1.0, 3.4, 3.1)
    ribs = (-100, -72, -44, -16)
    Ls = (12.5, 14.5, 14.0, 11.5)
    for i in range(3):
        mid = (ribs[i] + ribs[i + 1]) / 2.0
        L = min(Ls[i], Ls[i + 1]) * 0.98
        wd = 2 * L * float(np.tan(np.radians((ribs[i + 1] - ribs[i]) / 2.0)))
        skin = P(EN_D, mottle(EN_K, 2.0, 0.8), rect(('north', 'south'), -1, 99, -1, 1.0, EN_M), alpha=fan_mask(wd, L), bevel=0.0, noise=0.03)
        bs += sym(box('body', root[0] - wd / 2, root[1] - L, root[2], wd, L, 0.2, skin, rot=(0, 0, mid), pivot=(root[0], root[1], root[2] + 0.1)))
    for rz, L in zip(ribs, Ls):
        bs += sym(rod('body', root, L, 0.6, bone_c, rz=rz), spike('body', tuple(tip(root, L, rz=rz)), 2.0, 1.0, EN_W, rz=rz))
    bs += sym(box('body', root[0] - 1.1, root[1] - 1.1, root[2] - 0.4, 2.2, 2.2, 1.0, bone_c))
    return bs


# Порча Б: охотник. Широкополая шляпа, глаза в тени, патронташ, колчан со светящимися стрелами

@slim('r1r', 1, 'Порча, охотник: широкополая шляпа с пурпурной лентой, лицо в тени и светятся только глаза, тёмная кожа с патронташем, за спиной колчан со светящимися стрелами')
def r1r():
    coat = P(CO_M, trim(0.3, CO_D))
    felt = P(CO_D, trim(0.25, CO_K))
    suit = P(CO_K, trim(0.25, '#06040e'))
    shells = [disc(('north',), 1.6 + i * 1.5, 0.9 + i * 1.07, 0.42, CO_P, glow=True) for i in range(5)]
    chest = P(CO_M, trim(0.3, CO_D), stripe(('north',), 1.4, 0.3, 0.75, CO_K), *shells)
    bs = under(suit, suit) + breastplate(chest, front=-2.9) + waist(P(CO, trim(0.3, CO_D))) + belt(P(CO_K, trim(0.2, CO_D), rect(('north',), 3.2, 5.9, 0.2, 1.9, CO_L), rect(('north',), 3.8, 5.3, 0.7, 1.4, CO_K)), h=1.9)
    bs += shoulder(P(CO, trim(0.3, CO_D), rect(SIDES, -1, 99, 2.2, 3.0, CO_L)), h=3.0, out=0.6, half=2.4) + bracer(P(CO_M, trim(0.3, CO_D), rect(SIDES, -1, 99, 1.0, 1.5, CO_L), rect(SIDES, -1, 99, 3.6, 4.1, CO_L))) + thigh(coat) + knee(P(CO_L, trim(0.25, CO_D)))
    bs += boot(P(CO_D, trim(0.25, CO_K), rect(SIDES, -1, 99, 1.0, 1.6, CO_L)), toe=1.0)
    bs += [box('rightLeg', -2.75, 1.2, -1.5, 0.8, 3.6, 3.0, P(CO_K, trim(0.2, CO_D), disc(('west', 'east'), 1.5, 1.8, 0.7, CO_P, glow=True)))]
    # голова: шляпа с широкими полями, под ней тень, глаза, снизу лицо закрыто шарфом
    bs += [helm(P(CO_K, trim(0.2, CO_D), rect(('north',), 1.6, 3.5, 3.1, 4.0, CO_P, glow=True), rect(('north',), 5.6, 7.5, 3.1, 4.0, CO_P, glow=True), rect(('north', 'east', 'west', 'south'), -1, 99, 5.0, 99, CO_M), rect(('north', 'east', 'west', 'south'), -1, 99, 5.0, 5.5, CO_L)))]
    bs += [box('head', -7.4, -7.6, -7.4, 14.8, 0.8, 14.8, P(CO_D, trim(0.4, CO_K), rect(('up',), 2.8, 12.0, 2.8, 12.0, CO_D))),
           box('head', -4.7, -12.2, -4.7, 9.4, 4.8, 9.4, P(CO_D, trim(0.25, CO_K), rect(SIDES, -1, 99, 3.2, 4.4, CO_P, glow=True)))]
    bs += [flat_feather('head', (4.9, -8.0, 0.8), 6.0, 2.2, CO_L, CO_P, CO_K, rx=-30, rz=14)]
    # колчан наискось, стрелы над плечом
    s0, L, a = (-0.8, 9.8, 3.6), 10.5, -26
    bs += [rod('body', s0, L, 3.0, P(CO_D, trim(0.2, CO_K), rect(SIDES, -1, 99, 1.0, 1.7, CO_L), rect(SIDES, -1, 99, 8.4, 9.1, CO_L)), rz=a)]
    top = tip(s0, L - 0.5, rz=a)
    shaft = P(CO_K, bevel=0.0, noise=0.0)
    for (dx, da, n) in ((-0.8, -8, 6.4), (0.1, 0, 7.4), (1.0, 8, 6.2)):
        st = (top[0] + dx, top[1], top[2])
        end = tip(st, n, rz=a + da)
        bs += [rod('body', st, n, 0.45, shaft, rz=a + da), box('body', end[0] - 0.9, end[1] - 1.1, end[2] - 0.15, 1.8, 2.2, 0.3, P(CO_P, trim(0.1, '#ffffff'), glow=True, bevel=0.0), rot=(0, 0, a + da), pivot=tuple(end))]
    return bs


# Порча В: Грю. Пасть на груди, огромные глаза, щупальца с глазами на спине

@slim('r1x', 1, 'Порча, Грю: тёмная плоть с зубастой пастью на груди, шлем-голова с огромными глазами и второй пастью, глаза на плечах, из спины растут щупальца с глазами')
def r1x():
    fl = P(CO_D, mottle(CO_M, 2.0, 0.72), mottle(CO_P, 2.2, 0.93), trim(0.3, CO_K))
    suit = P(CO_K, trim(0.25, '#06040e'))
    chest = P(CO_D, mottle(CO_M, 2.0, 0.72), trim(0.3, CO_K), rect(('north',), 1.0, 8.2, 1.1, 5.0, CO_K), rect(('north',), 2.0, 7.2, 2.6, 3.6, CO_P, glow=True),
              zigzag(('north',), 1.1, 1.0, 1.4, 0.34, CO_W), zigzag(('north',), 3.9, 1.0, 1.4, 0.34, CO_W))
    bs = under(suit, suit) + breastplate(chest) + waist(fl) + belt(P(CO_K, trim(0.2, CO_D), rect(('north',), 3.5, 5.5, 0.4, 1.7, CO_M)), h=1.7)
    side_eye = (disc(('west', 'east'), 2.6, 1.9, 1.35, CO_K), disc(('west', 'east'), 2.6, 1.9, 1.0, CO_W), disc(('west', 'east'), 2.6, 1.9, 0.72, CO_P, glow=True), disc(('west', 'east'), 2.6, 1.9, 0.3, CO_K))
    bs += shoulder(P(CO_D, mottle(CO_M, 2.0, 0.72), trim(0.3, CO_K), *side_eye)) + bracer(fl) + thigh(fl) + knee(P(CO_M, trim(0.25, CO_K))) + boot(fl, toe=1.2)
    bs += [helm(P(CO_D, mottle(CO_M, 2.0, 0.72), trim(0.3, CO_K), *eye(2.5, 2.9, 1.5), *eye(6.6, 2.9, 1.5), rect(('north',), 1.0, 8.1, 5.0, 7.6, CO_K), zigzag(('north',), 5.0, 0.8, 1.2, 0.3, CO_W), zigzag(('north',), 6.5, 0.8, 1.2, 0.3, CO_W)))]
    # щупальца: по два с каждой стороны, сужаются, на конце глаз
    tent = P(CO_M, bands((CO_M, CO_D), 1.0, axis=1), trim(0.1, CO_K), bevel=0.0)
    ball = P(CO_W, trim(0.1, CO_K), disc(('south', 'north', 'west', 'east'), 1.0, 1.0, 0.75, CO_P, glow=True), disc(('south', 'north', 'west', 'east'), 1.0, 1.0, 0.3, CO_K), bevel=0.0)
    for (start, segs) in (((-1.4, 1.6, 3.1), ((3.2, -34), (3.0, -58), (2.8, -40), (2.4, -8))), ((-1.4, 5.0, 3.1), ((3.2, -78), (3.0, -100), (2.8, -118), (2.4, -92)))):
        arm, end = path('body', start, segs, 1.5, tent, taper=0.84)
        bs += sym(*arm, box('body', end[0] - 1.0, end[1] - 1.0, end[2] - 1.0, 2.0, 2.0, 2.0, ball))
    return bs


MODELS = KEYS[MINE:]
BG = {'r1t': '#585c6c', 'r1u': '#585c6c', 'r1w': '#585c6c', 'r1k': '#585c6c', 'r1l': '#585c6c', 'r1m': '#4a4c5c', 'r1n': '#403c54', 'r1o': '#403c54', 'r1q': '#403c54', 'r1p': '#4a4c5c', 'r1r': '#4a4c5c', 'r1x': '#4a4c5c'}


def build(key):
    """Как wconcepts.build, но фон у каждой модели свой."""
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
