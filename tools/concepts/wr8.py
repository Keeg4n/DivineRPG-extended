"""Концепты брони Странника R8 - за прохождение Ветеи (после Леди Луны). Три облика: спектральный в духе брони призрака Террарии,
лунный (Леди Луна) и сновидец (Ветея - мир снов). Тело на пропорциях Ботании (детали wslim), плащей нет, на спине вещь по теме.

Запуск: python3 wr8.py [ключ ...]  (без ключей - все); картинки в wconcepts.OUT.
"""
import math
import sys

import numpy as np

import wconcepts as w
from wconcepts import P, box, chain, disc, mottle, rect, stripe, sym, trim
from wslim import SIDES, belt, boot, bracer, breastplate, knee, shoulder, thigh, under, waist
from wslim5 import KEYS, bands, diamond, slim
import wr1  # noqa: F401  (детали: глухой шлем, кольцо на спине)
from wr1 import gate, helm

MINE = len(KEYS)


def gdiamond(faces, cu, cv, r, color):
    """Светящийся ромб наклейкой."""
    c = w.C(color)

    def d(p, face, b):
        if face.name not in faces:
            return c, 0.0, False
        u, v = w.fuv(p, face)
        return c, (np.abs(u - cu) + np.abs(v - cv) < r).astype(float), True
    return d


def tatter_alpha(wd, L):
    """Рваная лента вниз: сужается к нижнему концу (низ коробки), край зубцами."""
    def a(p, face, b):
        x = np.abs(p[..., 0] - wd / 2) / (wd / 2)
        y = p[..., 1]
        jag = 0.12 * np.sin(y * 3.1 + wd)
        return (x <= np.clip((L - y) / (L * 0.55), 0, 1) + jag + 0.02).astype(float)
    return a


def tatter(bone, x, y, z, wd, L, paint, rx=0.0, rz=0.0):
    """Призрачная лента: висит от точки (x, y, z) вниз."""
    return box(bone, x - wd / 2, y, z - 0.12, wd, L, 0.24, paint, rot=(rx, 0, rz), pivot=(x, y, z))


def ghost(color, edge, glowc, wd, L):
    """Материал ленты: светлеет и светится к концу."""
    return P(color, rect(('north', 'south'), -1, 99, L * 0.55, 99, glowc, glow=True), trim(0.15, edge), alpha=tatter_alpha(wd, L), bevel=0.0, noise=0.03)


# ---- А. Призрак: светлые латы, рваные призрачные ленты, капюшон с белой маской, огоньки за спиной

SP, SP_L, SP_M, SP_D, SP_K, SP_G = '#8ccff4', '#e2f6ff', '#4f8fd0', '#22407a', '#0c1430', '#8af8ff'


@slim('r8a', 8, 'Призрак: бледно-голубые латы со светящимися линиями, рваные призрачные ленты от пояса, капюшон с гладкой белой маской, за спиной три огонька')
def r8a():
    suit = P(SP_K, trim(0.25, '#050a1c'))
    plate = P(SP, trim(0.3, SP_D), mottle(SP_L, 1.6, 0.9))
    glowline = (stripe(('north',), 0.0, 2.6, 0.32, SP_G, glow=True), stripe(('north',), 0.0, 6.6, 0.32, SP_G, glow=True))
    chest = P(SP, trim(0.3, SP_D), mottle(SP_L, 1.6, 0.9), *glowline, diamond(('north',), 4.6, 2.6, 1.7, SP_D), gdiamond(('north',), 4.6, 2.6, 1.1, SP_G))
    bs = under(suit, suit) + breastplate(chest) + waist(P(SP_M, trim(0.3, SP_D))) + belt(P(SP_D, trim(0.2, SP_K), rect(('north',), 3.6, 5.4, 0.4, 1.6, SP_G, glow=True)), h=1.5)
    bs += shoulder(P(SP, trim(0.3, SP_G, glow=True), mottle(SP_L, 1.6, 0.9)), h=3.0, out=0.7) + bracer(P(SP, trim(0.3, SP_D), rect(SIDES, -1, 99, 2.4, 3.0, SP_G, glow=True)))
    bs += thigh(suit) + knee(P(SP_L, trim(0.2, SP_M))) + boot(P(SP, trim(0.3, SP_D), rect(SIDES, -1, 99, 2.9, 99, SP_G, glow=True)), toe=1.2)
    # рваные ленты от пояса: спереди, по бокам и сзади, разной длины
    for (x, z, wd, L, rx) in ((-2.7, -2.85, 2.6, 7.2, 10), (0.0, -2.85, 2.6, 8.6, 8), (2.7, -2.85, 2.6, 6.6, 10),
                              (-2.7, 2.85, 2.6, 7.8, -10), (0.0, 2.85, 2.6, 9.0, -8), (2.7, 2.85, 2.6, 7.4, -10)):
        bs.append(tatter('body', x, 11.2, z, wd, L, ghost(SP, SP_M, SP_G, wd, L), rx=rx))
    for (z, L) in ((-1.2, 6.8), (1.2, 7.6)):
        bs += sym(tatter('body', -4.45, 11.2, z, 2.2, L, ghost(SP, SP_M, SP_G, 2.2, L), rz=10))
    # капюшон: тёмно-синий, лицо - гладкая белая маска с узкими голубыми прорезями
    hoodp = P(SP_D, trim(0.3, SP_K), stripe(('east', 'west'), 0.0, 4.6, 0.3, SP_M))
    front = P(SP_D, trim(0.3, SP_K), rect(('north',), 1.0, 8.2, 1.4, 99, SP_K), rect(('north',), 1.8, 7.4, 2.2, 8.6, SP_L),
              rect(('north',), 2.4, 4.0, 4.4, 4.9, SP_G, glow=True), rect(('north',), 5.2, 6.8, 4.4, 4.9, SP_G, glow=True), rect(('north',), 4.3, 4.9, 6.4, 7.8, '#b8d4e8'))
    bs += [box('head', -4.6, -9.1, -4.7, 9.2, 9.1, 9.4, front), box('head', -4.0, -9.8, 2.0, 8.0, 8.4, 3.0, hoodp, rot=(-14, 0, 0), pivot=(0, 0, 0)),
           box('head', -3.2, -10.3, 3.6, 6.4, 6.8, 2.6, hoodp, rot=(-28, 0, 0), pivot=(0, 0, 0))]
    # огоньки за спиной: три светящихся сгустка дугой, у каждого хвост
    wisp = P(SP_G, trim(0.3, SP_L, glow=True), glow=True, bevel=0.0)
    tail = P(SP_L, glow=True, bevel=0.0, noise=0.02)
    for (x, y, s) in ((-4.6, -1.2, 1.5), (0.0, -3.0, 1.8), (4.6, -1.2, 1.5)):
        bs += [box('body', x - s / 2, y - s / 2, 4.6, s, s, s, wisp), box('body', x - s / 4, y + s / 2, 4.85, s / 2, 2.2, s / 2, tail)]
    return bs


# ---- Б. Лунная: серебро и сирень, полумесяц над шлемом, большой полумесяц за спиной

MN, MN_M, MN_D, MN_K, LI, LI_G = '#dfe4ee', '#a9b2c6', '#5a6278', '#1a1c34', '#b89ae8', '#dcc4ff'


def crescent(faces, cu, cv, r, color, glow=False):
    """Полумесяц наклейкой: круг без сдвинутого круга (рожки вправо-вверх)."""
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
    """Дуга из брусков в плоскости спины, толщина от t0 к t1 (серп)."""
    out = []
    for i in range(n):
        f = (i + 0.5) / n
        a = math.radians(a0 + (a1 - a0) * f)
        t = t0 + (t1 - t0) * (1 - abs(2 * f - 1))
        L = 2 * math.pi * r * abs(a1 - a0) / 360.0 / n * 1.18
        px, py = cx + r * math.cos(a), cy + r * math.sin(a)
        out.append(box(bone, px - L / 2, py - t / 2, z, L, t, depth, paint, rot=(0, 0, math.degrees(a) + 90), pivot=(px, py, z + depth / 2)))
    return out


@slim('r8b', 8, 'Лунная: серебряные латы с сиреневым, полумесяц на груди, гладкий шлем с сиреневым забралом и рогами полумесяца, за спиной большой серп луны')
def r8b():
    suit = P(MN_K, trim(0.25, '#0a0a18'))
    silver = P(MN, trim(0.3, MN_M))
    chest = P(MN, trim(0.3, MN_M), crescent(('north',), 4.6, 2.8, 2.0, LI_G, glow=True), stripe(('north',), 0.0, 4.6, 0.25, MN_M))
    bs = under(suit, suit) + breastplate(chest) + waist(P(LI, trim(0.3, MN_D))) + belt(P(MN_M, trim(0.2, MN_D), disc(('north',), 4.5, 0.9, 0.7, LI_G, glow=True)), h=1.6)
    bs += shoulder(P(MN, trim(0.3, MN_M), rect(SIDES, -1, 99, 2.0, 2.5, LI)), top=-2.4, h=3.4, out=1.0) + bracer(P(MN, trim(0.3, MN_M), rect(SIDES, -1, 99, 2.3, 2.8, LI)))
    bs += thigh(P(LI, trim(0.3, MN_D))) + knee(silver) + boot(P(MN, trim(0.3, MN_M), rect(SIDES, -1, 99, 3.0, 99, LI_G, glow=True)), toe=1.2)
    # наплечники: серп-гребень вверх
    bs += sym(*chain('rightArm', (-3.9, -2.4, 0.0), ((1.6, 0, 20), (1.6, 0, 40), (1.4, 0, 62)), 0.8, P(MN, trim(0.2, MN_M)), taper=0.7))
    # шлем: гладкий, сиреневое забрало, рога полумесяца от висков вверх и внутрь
    bs += [helm(P(MN, trim(0.3, MN_M), rect(('north',), 1.2, 7.9, 3.8, 5.6, MN_K), rect(('north',), 1.7, 7.4, 4.3, 5.1, LI_G, glow=True), rect(('north',), 4.1, 5.0, 0.4, 3.6, MN_M)))]
    # над шлемом - полумесяц рожками вверх, светящаяся кромка
    bs += arc('head', 0.0, -12.9, -0.5, 3.8, 12, 168, 11, 0.4, 1.9, P(MN, trim(0.2, LI_G, glow=True)), depth=1.0)
    bs += [box('head', -0.8, -9.3, -4.95, 1.6, 1.6, 0.4, P(LI_G, glow=True))]
    # большой серп за спиной: светлый, толстый в середине, концы тонкие
    bs += arc('body', -0.6, 4.0, 4.0, 5.4, 75, 285, 14, 0.4, 2.6, P(MN, trim(0.2, LI, glow=True), mottle(MN_M, 1.4, 0.85)))
    return bs


# ---- В. Сновидец: индиго в звёздах, маска с закрытыми глазами и третьим глазом, ловец снов на спине

DR, DR_D, DR_L, DR_K, GOLDD, TEAL = '#3a2a6a', '#1c1438', '#6a58b0', '#0e0a1e', '#e3c46a', '#7ff0d8'
STARS = (mottle('#f4f0ff', 5.0, 0.955), mottle(DR_L, 1.6, 0.78))


@slim('r8c', 8, 'Сновидец: индиго в звёздах с золотой нитью, гладкая маска с закрытыми глазами и светящимся третьим глазом, на спине ловец снов с перьями')
def r8c():
    suit = P(DR_K, trim(0.25, '#05030c'))
    cloth = P(DR, *STARS, trim(0.3, GOLDD))
    chest = P(DR, *STARS, trim(0.3, GOLDD), diamond(('north',), 4.6, 2.6, 2.0, GOLDD), diamond(('north',), 4.6, 2.6, 1.5, DR_D), disc(('north',), 4.6, 2.6, 0.75, TEAL, glow=True))
    bs = under(suit, suit) + breastplate(chest) + waist(P(DR_D, *STARS, trim(0.3, DR))) + belt(P(GOLDD, trim(0.2, '#8a6a20'), disc(('north',), 4.5, 0.9, 0.6, TEAL, glow=True)), h=1.4)
    bs += shoulder(P(DR, *STARS, trim(0.3, GOLDD)), h=3.2, out=0.8) + bracer(P(DR, *STARS, trim(0.3, GOLDD), rect(SIDES, -1, 99, 2.4, 2.9, GOLDD)))
    bs += thigh(cloth) + knee(P(GOLDD, trim(0.2, '#8a6a20'))) + boot(P(DR, *STARS, trim(0.3, GOLDD), rect(SIDES, -1, 99, -1, 0.8, GOLDD)), toe=1.1)
    # маска: гладкая, закрытые глаза золотыми дугами, на лбу открытый третий глаз
    mask = P(DR, *STARS, trim(0.3, GOLDD),
             rect(('north',), 1.8, 3.9, 4.9, 5.3, GOLDD), rect(('north',), 2.1, 3.6, 5.3, 5.6, GOLDD), rect(('north',), 5.2, 7.3, 4.9, 5.3, GOLDD), rect(('north',), 5.5, 7.0, 5.3, 5.6, GOLDD),
             diamond(('north',), 4.55, 2.5, 1.3, GOLDD), disc(('north',), 4.55, 2.5, 0.85, TEAL, glow=True), disc(('north',), 4.55, 2.5, 0.35, DR_K))
    bs += [helm(mask)]
    bs += sym(box('head', -5.0, -8.2, -1.0, 0.45, 4.0, 3.8, P(GOLDD, trim(0.2, '#8a6a20'), disc(('west', 'east'), 1.9, 2.0, 0.7, TEAL, glow=True))))
    # ловец снов на спине: золотое кольцо, паутина нитями, бусина в центре, три пера вниз
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


# ---- Призрак, второй раунд (09.11): тело и ленты как у А, капюшон цвета брони, лицо по-разному


def ghost_body():
    """Тело Призрака (r8a) без головы и спины."""
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
    """Ступени капюшона назад, в цвет брони."""
    hp = P(SP, trim(0.3, SP_D), *HOOD, stripe(('east', 'west'), 0.0, 4.6, 0.3, SP_G, glow=True))
    return [box('head', -4.0, -9.8, 2.0, 8.0, 8.4, 3.0, hp, rot=(-14, 0, 0), pivot=(0, 0, 0)), box('head', -3.2, -10.3, 3.6, 6.4, 6.8, 2.6, hp, rot=(-28, 0, 0), pivot=(0, 0, 0))]


@slim('r8d', 8, 'Призрак Г: капюшон цвета брони со светящейся кромкой, лица нет - тёмная пустота и два голубых глаза')
def r8d():
    front = P(SP, trim(0.3, SP_D), *HOOD, rect(('north',), 0.8, 8.4, 1.2, 99, SP_G, glow=True), rect(('north',), 1.2, 8.0, 1.6, 99, '#050914'),
              rect(('north',), 2.3, 3.7, 4.5, 5.2, SP_G, glow=True), rect(('north',), 5.4, 6.8, 4.5, 5.2, SP_G, glow=True))
    return ghost_body() + [box('head', -4.6, -9.1, -4.7, 9.2, 9.1, 9.4, front)] + hood_back() + ghost_wisps()


@slim('r8e', 8, 'Призрак Д: капюшон цвета брони, лицо - сгусток света, в нём тёмные глаза и тёмный рот, на лбу камень')
def r8e():
    front = P(SP, trim(0.3, SP_D), *HOOD, rect(('north',), 1.0, 8.2, 1.4, 99, SP_D), rect(('north',), 1.4, 7.8, 1.8, 99, '#9feaff', glow=True),
              rect(('north',), 2.2, 7.0, 2.2, 99, '#d8fbff', glow=True),
              rect(('north',), 1.9, 3.9, 4.1, 5.6, '#06122a'), rect(('north',), 5.3, 7.3, 4.1, 5.6, '#06122a'), rect(('north',), 3.4, 5.8, 6.8, 7.5, '#123a6a'))
    gem = P(SP_G, trim(0.2, SP_L, glow=True), glow=True, bevel=0.0)
    return ghost_body() + [box('head', -4.6, -9.1, -4.7, 9.2, 9.1, 9.4, front), box('head', -0.7, -8.6, -5.0, 1.4, 1.4, 0.4, gem)] + hood_back() + ghost_wisps()


@slim('r8f', 8, 'Призрак Е: открытое лицо, капюшон цвета брони с обручем-огоньком на лбу, низ лица закрыт шарфом в цвет брони с голубой кромкой')
def r8f():
    hp = P(SP, trim(0.3, SP_D), *HOOD)
    brow = P(SP, trim(0.3, SP_D), *HOOD, rect(('north',), -1, 99, 0.6, 1.0, SP_G, glow=True))
    scarf = P(SP, trim(0.25, SP_D), *HOOD, rect(('north', 'east', 'west'), -1, 99, -1, 0.35, SP_G, glow=True))
    bs = ghost_body()
    bs += [box('head', -4.6, -9.1, -4.7, 9.2, 1.6, 9.4, brow)]  # шапка капюшона надо лбом
    bs += sym(box('head', -4.6, -7.5, -4.7, 0.8, 7.5, 9.4, hp))  # бока капюшона
    bs += [box('head', -4.6, -7.5, 3.8, 9.2, 7.5, 0.9, hp)]  # затылок
    bs += [box('head', -4.7, -2.6, -4.85, 9.4, 2.6, 1.4, scarf)]  # шарф на нижней половине лица, только спереди
    bs += [box('head', -0.6, -8.3, -4.95, 1.2, 1.2, 0.35, P(SP_G, glow=True))]  # огонёк на обруче
    return bs + hood_back() + ghost_wisps()


# ---- Призрак, третий раунд (09.11): глубокий капюшон - козырёк выступает вперёд, вырез скруглён, тьма и глаза в глубине


def hole_mask(kind, W=9.2, H=9.1, half=3.2):
    """Вырез в передней стенке капюшона (0 - дыра) только на лицевых гранях. kind: arch - полукруг сверху и прямые бока до низа,
    gothic - стрельчатая арка, oval - овал с подбородком снизу."""
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
        """Светящаяся кромка вдоль края выреза."""
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
    """Капюшон в цвет брони: козырёк с вырезом на 2 вперёд от лица, тёмная подкладка, в глубине - тёмная плоскость с глазами."""
    shell = P(SP, trim(0.3, SP_D), *HOOD)
    m, rim = hole_mask(kind, half=half)
    brim = P(SP, trim(0.3, SP_D), *HOOD, *((rim,) if glow_rim else ()), alpha=m, bevel=0.0)
    dark = P('#0a1226', bevel=0.0, noise=0.02)
    h = gap / 2  # от середины плоскости (3.6) до внутреннего края глаза
    if eyes == 'round':
        e1, e2 = rect(('north',), 3.6 - h - 1.2, 3.6 - h, 3.6, 4.4, SP_G, glow=True), rect(('north',), 3.6 + h, 3.6 + h + 1.2, 3.6, 4.4, SP_G, glow=True)
    else:  # узкие щели
        e1 = rect(('north',), 3.6 - h - 1.5, 3.6 - h, 3.9, 4.3, SP_G, glow=True)
        e2 = rect(('north',), 3.6 + h, 3.6 + h + 1.5, 3.9, 4.3, SP_G, glow=True)
    face = P('#03060f', e1, e2, bevel=0.0, noise=0.0)
    bs = [box('head', -4.6, -9.1, -6.0 + depth, 9.2, 9.1, 0.7, brim)]              # козырёк с вырезом (depth - ближе к лицу)
    bs += [box('head', -4.6, -9.1, -5.3, 9.2, 1.2, 10.0, shell)]                    # верх
    bs += sym(box('head', -4.6, -7.9, -5.3, 1.0, 7.9, 10.0, shell))                 # бока
    bs += [box('head', -4.6, -7.9, 3.7, 9.2, 7.9, 1.0, shell)]                      # затылок
    bs += [box('head', -3.8, -9.7, -5.6, 7.6, 0.6, 9.6, shell)]                     # скругление макушки ступенькой
    # тёмная подкладка внутри козырька и плоскость в глубине (перед лицом игрока)
    bs += [box('head', -3.6, -7.9, -5.3, 7.2, 0.25, 1.25, dark)] + sym(box('head', -3.6, -7.9, -5.3, 0.25, 7.9, 1.25, dark))
    bs += [box('head', -3.6, -7.9, -4.15, 7.2, 7.9, 0.1, face)]
    if tip:  # острый хвост капюшона назад и вверх, как у назгула
        bs += chain('head', (0.0, -8.4, 3.0), ((3.2, -78, 0), (3.0, -112, 0), (2.6, -140, 0), (2.0, -160, 0)), 3.4, shell, taper=0.72)
    if peak:  # козырёк-навес сверху, чуть вниз
        bs += [box('head', -4.6, -9.6, -6.9, 9.2, 0.8, 1.6, shell, rot=(-12, 0, 0), pivot=(0, -9.2, -6.0))]
    return bs


@slim('r8g', 8, 'Призрак Ж: глубокий капюшон в цвет брони, вырез аркой со светящейся кромкой, тьма и круглые глаза в глубине')
def r8g():
    return ghost_body() + deep_hood('arch') + hood_back() + ghost_wisps()


@slim('r8h', 8, 'Призрак З: глубокий капюшон, стрельчатый вырез без кромки, тьма и узкие глаза в глубине')
def r8h():
    return ghost_body() + deep_hood('gothic', eyes='slit', glow_rim=False) + hood_back() + ghost_wisps()


@slim('r8i', 8, 'Призрак И: глубокий капюшон с навесом сверху, овальный вырез со светящейся кромкой, тьма и круглые глаза в глубине')
def r8i():
    return ghost_body() + deep_hood('oval', peak=True) + hood_back() + ghost_wisps()


# ---- Призрак, четвёртый раунд (09.11): капюшон Ж с глазами шире, верх - балахон (по мотивам Абаддона из Dota 2: назгул в балахоне)

FOLDS = (SP, '#7fc2ea', SP, '#78b8e0')


def robe(len_y=12.4, mantle=False, sleeves='wide', gauntlets=False):
    """Балахон: свободная ткань складками от плеч до бёдер, верёвочный пояс, рукава; ленты подола - как у Призрака."""
    suit = P(SP_K, trim(0.25, '#050a1c'))
    cloth = P(SP, bands(FOLDS, 1.7), trim(0.25, SP_M), bevel=0.0, noise=0.03)
    front = P(SP, bands(FOLDS, 1.7), trim(0.25, SP_M), rect(('north',), 3.9, 4.9, 0.0, 99, SP_M), gdiamond(('north',), 4.4, 1.2, 0.9, SP_G), bevel=0.0, noise=0.03)
    bs = under(suit, suit)
    bs += [box('body', -4.45, -0.15, -2.8, 8.9, len_y, 5.6, front)]
    rope = P('#d6eefa', trim(0.15, SP_M), bevel=0.0, noise=0.02)
    bs += [box('body', -4.65, 7.4, -3.0, 9.3, 0.7, 6.0, rope), box('body', -1.6, 8.1, -3.05, 0.5, 3.2, 0.5, rope), box('body', -0.6, 8.1, -3.05, 0.5, 2.4, 0.5, rope)]
    bs += [box('body', -1.8, 11.0, -3.15, 0.9, 0.9, 0.9, P(SP_G, glow=True))]  # огонёк на конце верёвки
    if sleeves == 'wide':  # широкие рукава колоколом, светящийся край
        bs += sym(box('rightArm', -3.35, -2.1, -2.35, 4.7, 6.0, 4.7, cloth),
                  box('rightArm', -3.75, 3.9, -2.75, 5.5, 5.4, 5.5, P(SP, bands(FOLDS, 1.7), trim(0.25, SP_M), rect(SIDES, -1, 99, 4.7, 99, SP_G, glow=True), bevel=0.0, noise=0.03)))
    else:  # рукав до локтя
        bs += sym(box('rightArm', -3.45, -2.1, -2.45, 4.9, 6.2, 4.9, P(SP, bands(FOLDS, 1.7), trim(0.25, SP_M), rect(SIDES, -1, 99, 5.5, 99, SP_G, glow=True), bevel=0.0, noise=0.03)))
    if gauntlets:  # латные перчатки из-под ткани
        bs += bracer(P(SP_D, trim(0.3, SP_K), rect(SIDES, -1, 99, 2.4, 3.0, SP_G, glow=True)), top=4.2)
    if mantle:  # пелерина на плечах: два слоя, короткая (не плащ), рваный светящийся край
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
    """Туман у подола и плеч, как у Абаддона: мелкие светящиеся клочья."""
    puff = P(SP_L, glow=True, bevel=0.0, noise=0.02)
    out = []
    for (bone, x, y, z, sz) in (('body', -5.2, 13.4, -2.2, 0.9), ('body', 4.6, 14.6, 1.6, 0.8), ('body', -3.4, 15.8, 2.8, 0.7), ('body', 2.6, 13.0, -3.3, 0.7),
                                ('body', 5.4, 0.6, -0.6, 0.6), ('body', -5.6, 1.4, 0.8, 0.7), ('rightLeg', -2.8, 9.6, -2.6, 0.6), ('rightLeg', 1.8, 10.4, 2.4, 0.5)):
        out.append(box(bone, x, y, z, sz, sz, sz, puff))
    return out


@slim('r8j', 8, 'Призрак К: капюшон Ж, глаза шире; балахон складками до бёдер, верёвочный пояс с огоньком, широкие рукава со светящимся краем')
def r8j():
    return robe() + deep_hood('arch', gap=2.2, half=3.6, depth=0.4) + hood_back() + ghost_wisps()


@slim('r8k', 8, 'Призрак Л: капюшон Ж, глаза шире; балахон с короткой пелериной на плечах, рукава до локтя, тёмные латные перчатки и сапоги')
def r8k():
    return robe(mantle=True, sleeves='short', gauntlets=True) + deep_hood('arch', gap=2.2, half=3.6, depth=0.4) + hood_back() + ghost_wisps()


@slim('r8l', 8, 'Призрак М (по Абаддону): острый хвост капюшона назад, глаза шире; балахон с пелериной, широкие рукава, латные перчатки, туман у подола и плеч')
def r8l():
    return robe(mantle=True, gauntlets=True) + deep_hood('arch', gap=2.2, half=3.6, depth=0.4, tip=True) + hood_back() + mist()


MODELS = KEYS[MINE:]
BG = {'r8a': '#2a3654', 'r8b': '#30304a', 'r8c': '#2a2448', 'r8d': '#2a3654', 'r8e': '#2a3654', 'r8f': '#2a3654', 'r8g': '#2a3654', 'r8h': '#2a3654', 'r8i': '#2a3654', 'r8j': '#2a3654', 'r8k': '#2a3654', 'r8l': '#26304c'}


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
