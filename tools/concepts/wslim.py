"""Концепты брони Странника, четвёртый раунд: стройные, по пропорциям брони Ботании (террасталь, элементий, манасталь).

Прежняя броня раздута: торс и руки на 1.0 от тела, поверх ещё плиты на 1.2, плечи выше шеи - отсюда "холодильник". У Ботании все
коробки раздуты на 0.2: грудь 9x6 сужается в талию 8x5, наруч и бедро ровно по руке и ноге, наплечник на 1 от руки и не выше плеча,
сапог с носком на 1. Броня не сплошная: между наплечником и наручем, бедром и сапогом видно то, что под ней, кисть открыта.
Шлем - шапка, щёки и короткий назатыльник, лицо открыто. Темы ступеней прежние; капюшон остался там, где он и есть образ (R5, R6).

Текстура - как в игре (k=2, wanderer_armor.K). Запуск: python3 wslim.py [ключ ...]  (без ключей - все); картинки в wconcepts.OUT.
"""
import sys

import wconcepts as w
from wconcepts import GLOWG, ED, GOLD, P, BARK, WWB, WWD, WWL, MO, MOD, MOL, RED, GLOWR, box, chain, concept, crystal, disc, facets, mottle, rect, ring, rod, \
    streaks, stripe, sym, tatter, trim
import wterraria as t
from wround3 import BLUE, SKY, SKYD, SKYL

KEYS = []
S = 0.2  # раздутие, как у Ботании
UNDER = 0.06  # поддоспешник: по телу, силуэт не меняет
HEAD_S = 0.55  # голова: иначе сквозь шапку виден второй слой скина (он раздут на 0.5)
SIDES = ('north', 'south', 'east', 'west')


def slim(key, tier, title):
    """Концепт раунда: как concept(), но коробки с текстурой игры (k=2)."""
    def reg(fn):
        def made():
            bs = fn()
            for b in bs:
                b.k = 2.0
            return bs
        KEYS.append(key)
        concept(key, tier, 'С', title)(made)
        return fn
    return reg


def bx(bone, x, y, z, wd, h, d, paint, s=S, **kw):
    """Коробка с раздутием s, как addBox(x, y, z, w, h, d, s) у ModelRenderer."""
    return box(bone, x - s, y - s, z - s, wd + 2 * s, h + 2 * s, d + 2 * s, paint, **kw)


# ---- тело (y вниз, лицо в -Z)

def breastplate(paint, front=-3.2, back=2.4, h=5.8):
    return [bx('body', -4.4, 0, front, 8.8, h, back - front, paint)]


def waist(paint, top=5.8, bottom=10.0):
    return [bx('body', -4, top, -2.3, 8, bottom - top, 4.6, paint)]


def belt(paint, y=9.8, h=1.9):
    return [bx('body', -4.3, y, -2.6, 8.6, h, 5.2, paint)]


def under(arm, leg):
    """Поддоспешник: виден в зазорах брони. Рукав - часть кирасы, штанина - поножей."""
    return sym(bx('rightArm', -3, 1.6, -2, 4, 7.0, 4, arm, s=UNDER), bx('rightLeg', -2, 5.4, -2, 4, 3.4, 4, leg, s=UNDER))


def shoulder(paint, top=-2.0, h=3.8, out=0.9, half=2.6):
    return sym(bx('rightArm', -3 - out, top, -half, 3.4 + out, h, 2 * half, paint))


def bracer(paint, top=3.6, bottom=9.2):
    """Наруч вокруг предплечья; кисть открыта."""
    return sym(bx('rightArm', -3, top, -2, 4, bottom - top, 4, paint))


def bracer_plate(paint, top=3.4, bottom=9.2):
    """Наруч только снаружи руки, как у манастали."""
    return sym(bx('rightArm', -3, top, -2, 2.4, bottom - top, 4, paint))


def thigh(paint, bottom=6.0):
    return sym(bx('rightLeg', -2, 0, -2, 4, bottom, 4, paint))


def knee(paint):
    return sym(box('rightLeg', -1.5, 3.4, -2.55, 3.0, 2.2, 0.35, paint))


def boot(paint, toe=1.0, top=8.2):
    return sym(bx('rightLeg', -2, top, -2 - toe, 4, 12 - top, 4 + toe, paint))


def cape(paint, length=16.0, wd=7.6, flare=6.0, top=-0.2, z=2.65, thick=0.3):
    return [box('body', -wd / 2, top, z, wd, length, thick, paint, rot=(flare, 0, 0), pivot=(0, top, z))]


def collar(paint):
    """Воротник вместо прежней накидки на плечах: не шире груди."""
    return [box('body', -4.65, -0.5, -2.85, 9.3, 1.4, 5.5, paint)]


# ---- голова

def cap(paint, cheeks=1.2, cheek_d=4.8, nape=2.4):
    """Открытый шлем: шапка, щёки спереди, короткий назатыльник. Лицо и низ затылка видны."""
    s = HEAD_S
    return ([box('head', -4 - s, -8 - s, -4 - s - 0.2, 8 + 2 * s, 2.6 + s, 8 + 2 * s + 0.2, paint), box('head', -4 - s, -5.4, 4, 8 + 2 * s, nape, s, paint)]
            + sym(box('head', -4 - s, -5.4, -4 - s - 0.2, cheeks, 3.8, cheek_d, paint)))


def cowl(paint, shadow='#140f0c', tip=2.2):
    """Капюшон по голове: стенка 0.6 вместо прежних 1.6, сзади небольшой хвостик."""
    s = 0.6
    bs = [box('head', -4 - s, -8 - s, -4.1 - s, 8 + 2 * s, s, 8.1 + 2 * s, paint),  # верх
          box('head', -4 - s, -8, 4, 8 + 2 * s, 8.3, s, paint),  # затылок
          box('head', -4 - s, -8, -4.7, 8 + 2 * s, 1.0, 0.6, paint),  # козырёк
          box('head', -4.0, -7.0, -4.15, 8.0, 1.1, 0.1, P(shadow, noise=0.0, bevel=0))]  # тень на лбу
    bs += sym(box('head', -4 - s, -8, -4.7, s, 8.3, 8.7, paint))  # бока
    bs.append(box('head', -1.2, -8.8, 3.6, 2.4, tip, 1.2, paint, rot=(38, 0, 0), pivot=(0, -8.6, 4.0)))
    return bs


def feather(y, z, h, d, paint, rx=-22.0, rz=-14.0):
    """Плоское перо сбоку шапки, плоскостью наружу (как крылышки шлема элементия). Правая сторона, зеркалить через sym."""
    return box('head', -5.0, y - h, z, 0.4, h, d, paint, rot=(rx, 0, rz), pivot=(-4.8, y, z + d / 2))


def leaf(bone, start, L, wd, rx=0.0, ry=0.0, rz=0.0, color=t.CG):
    paint = P(color, rect(('north', 'south'), wd / 2 - 0.25, wd / 2 + 0.25, 0.5, L - 0.5, t.CGB), trim(0.25, t.CGO), alpha=t.ellipse_mask(wd, L), bevel=0.0, noise=0.03)
    return t.blade(bone, start, L, wd, paint, rx=rx, ry=ry, rz=rz, t=0.3)


# ---- ступени

@slim('r1s', 1, 'Освящённая: серебряная кираса с золотым клином и алым камнем, открытый золотой шлем с крылышками, тонкий алый плащ')
def r1s():
    gold = P(t.HG, trim(0.3, t.HGD), rect(('up',), -1, 99, -1, 99, t.HGL))
    band = P(t.HG, trim(0.3, t.HGD), rect(SIDES, -1, 99, 2.0, 3.0, t.HS))
    plate = P(t.HS, trim(0.3, t.HSD), stripe(('north',), 0.72, 0.7, 0.5, t.HG), stripe(('north',), -0.72, 8.5, 0.5, t.HG),
              disc(('north',), 4.6, 2.4, 1.25, t.HRD), disc(('north',), 4.6, 2.4, 0.8, t.GEMR, glow=True))
    dark = P(t.HDK2, trim(0.3, t.HDK))
    bs = under(dark, dark) + breastplate(plate) + waist(dark) + belt(P(t.HG, trim(0.3, t.HGD), rect(('north',), 3.5, 5.5, 0.4, 1.9, t.HS)))
    bs += shoulder(gold) + bracer(band) + thigh(dark) + knee(gold) + boot(P(t.HS, trim(0.3, t.HSD), rect(SIDES, -1, 99, -1, 0.9, t.HG)))
    bs += cape(P(t.HR, trim(0.35, t.HRD), grad=0.35)) + [box('body', -4.2, -0.4, 2.3, 8.4, 0.9, 0.6, gold)]
    bs += cap(P(t.HG, trim(0.3, t.HGD), rect(('up',), 3.6, 5.6, -1, 99, t.HS)))
    bs += [box('head', -1.0, -8.0, -5.2, 2.0, 1.8, 0.4, P(t.GEMR, glow=True))]
    wing, wd = P(t.HG, trim(0.25, t.HGD), rect('all', -1, 99, -1, 0.9, t.HGL)), P(t.HGD, trim(0.25, '#6a4a04'))
    bs += sym(feather(-5.6, -1.4, 5.2, 1.8, wing), feather(-5.6, 0.2, 4.2, 1.6, wd, rx=-34), feather(-5.6, 1.6, 3.2, 1.4, wing, rx=-46))
    return bs


@slim('r2s', 2, 'Эдем: лепестки на груди и плечах, золотой венец с огненным камнем, нимб')
def r2s():
    gold, red = P(GOLD, trim(0.3, '#9a6a20')), P(ED, trim(0.3, '#7a1a08'))
    base = P('#8a6420', trim(0.3, '#5a3e12'))
    suit = P('#7a2414', trim(0.3, '#4a1408'))
    bs = under(suit, suit) + breastplate(base, front=-2.7) + waist(P('#a0301a', trim(0.3, '#5c1408'))) + belt(red)
    pet_g, pet_r = P(GOLD, trim(0.3, '#c89a30'), rect(('north',), 1.0, 2.0, 0.3, 2.2, '#fff0a0')), P(ED, trim(0.3, '#8a2410'), rect(('north',), 1.0, 2.0, 0.3, 2.2, '#ffb090'))
    for row, y in enumerate((-0.2, 1.8, 3.8)):
        for i, x in enumerate((-4.5, -1.5, 1.5)):
            bs.append(box('body', x, y, -3.3, 3.0, 2.6, 0.3, pet_g if (row + i) % 2 == 0 else pet_r, rot=(-8, 0, 0), pivot=(x + 1.5, y, -3.1)))
    for j, (zz, a) in enumerate(((-2.8, -8), (-0.9, -12), (1.0, -8))):
        bs += sym(box('rightArm', -3.9, -2.4, zz, 3.4, 4.4, 1.9, pet_g if j != 1 else pet_r, rot=(0, 0, a), pivot=(-1, -2, zz + 1)))
    bs += bracer_plate(gold) + thigh(suit) + knee(gold) + boot(gold)
    bs += [box('head', -4.6, -6.8, -4.8, 9.2, 1.2, 9.4, gold), box('head', -0.9, -7.2, -5.2, 1.8, 2.0, 0.5, P('#ff5020', glow=True))]
    bs += sym(feather(-6.6, -3.2, 2.6, 1.6, pet_g, rx=-6, rz=-10), feather(-6.6, -0.8, 3.0, 1.8, pet_r, rx=-6, rz=-10), feather(-6.6, 1.8, 2.6, 1.6, pet_g, rx=-6, rz=-10))
    bs += ring('head', (0, -11.2, 0), 3.7, 16, 0.5, P(GLOWG, glow=True))
    return bs


@slim('r3s', 3, 'Дикий лес: латы из синего дерева, открытый шлем, тонкие рога-ветви')
def r3s():
    wood = P(WWB, streaks(WWD, 1.0, 0.5), trim(0.35, WWD))
    bark = P(BARK, streaks(WWD))
    suit = P('#142a60', streaks('#0a2050'), trim(0.3, '#081838'))
    bs = under(suit, suit) + breastplate(wood) + waist(suit) + belt(bark) + shoulder(wood) + bracer(wood)
    bs += thigh(wood) + knee(bark) + boot(wood)
    bs += cap(wood)
    ant = P(WWL, streaks(WWB, 1.0, 0.6))
    bs += sym(*chain('head', (-3.2, -8.4, -0.4), ((2.4, 0, -30), (2.6, 0, -12), (2.0, 0, 10)), 0.8, ant))
    bs += sym(rod('head', (-4.6, -10.4, -0.4), 1.8, 0.5, ant, rz=-68), rod('head', (-5.2, -12.4, -0.4), 1.4, 0.4, ant, rz=-8))
    return bs


@slim('r4s', 4, 'Апалачия: гранёная малиновая кираса с сиреневым камнем, открытый шлем с веером кристаллов, кристаллы на плечах')
def r4s():
    pl = P(t.OM, facets('#ffffff', 0.22), trim(0.3, t.OD))
    lav = P(t.OL, facets('#ffffff', 0.2), trim(0.3, t.OPU))
    suit = P(t.OD, trim(0.3, '#1a041a'))
    bs = under(suit, suit) + breastplate(P(t.OM, facets('#ffffff', 0.22), trim(0.3, t.OD), disc(('north',), 4.6, 2.9, 1.7, t.OL), disc(('north',), 4.6, 2.9, 1.0, t.OP)))
    bs += waist(suit) + belt(P(t.OPU, trim(0.3, t.OD))) + shoulder(pl) + bracer(P(t.OM, facets('#ffffff', 0.22), trim(0.3, t.OD), rect(SIDES, -1, 99, 2.2, 3.2, t.OL)))
    bs += thigh(P(t.OPU, trim(0.3, t.OD), facets('#ffffff', 0.15))) + knee(lav) + boot(pl)
    for (z, L, rz, rx) in ((-1.6, 2.2, -22, -10), (0.0, 2.8, -28, 0), (1.6, 2.0, -20, 12)):
        bs += sym(crystal('rightArm', (-3.2, -2.1, z), L, 0.7, t.OP, rx=rx, rz=rz, glow=False, tipc='#ffe0fc'))
    bs += cap(pl) + [box('head', -1.0, -8.0, -5.2, 2.0, 1.8, 0.4, P(t.OP, glow=True))]
    for (x, L, rz) in ((-2.6, 2.2, -26), (-0.9, 3.0, -8), (0.9, 3.0, 8), (2.6, 2.2, 26)):
        bs += [crystal('head', (x, -8.4, 0.4), L, 0.7, t.OP, rz=rz, rx=-12, glow=False, tipc='#ffe0fc')]
    return bs


@slim('r5s', 5, 'Небесный терн: серые латы, белый капюшон, белый плащ с голубой каймой и застёжкой')
def r5s():
    plate = P(SKY, trim(0.3, SKYD))
    suit = P('#8a8e98', trim(0.3, '#5e626c'))
    bs = under(suit, suit) + breastplate(plate) + waist(P(SKYD, trim(0.3, '#5e626c'))) + belt(P(SKYD, trim(0.3, '#5e626c'), rect(('north',), 3.5, 5.5, 0.4, 1.9, BLUE)))
    bs += shoulder(P(SKY, trim(0.3, SKYD), rect(('up',), -1, 99, -1, 99, SKYL))) + bracer_plate(P(SKYL, trim(0.3, SKYD))) + thigh(suit) + knee(plate) + boot(plate)
    bs += cowl(P(SKYL, trim(0.3, SKY)), shadow='#1a1e28') + collar(P(SKYL, trim(0.3, BLUE)))
    bs += cape(P(SKYL, trim(0.4, BLUE), grad=0.15), length=17) + [box('body', -1.1, -0.2, -3.45, 2.2, 2.0, 0.3, P(BLUE, trim(0.3, '#5a88c8')))]
    return bs


@slim('r6s', 6, 'Мортум: тень в капюшоне с красными глазами, тёмная кираса с красной каймой, рваный плащ, цепь на поясе')
def r6s():
    lea = P(MO, trim(0.3, MOD))
    suit = P('#222226', trim(0.3, MOD))
    bs = under(suit, suit) + breastplate(P(MO, trim(0.3, RED), rect(('north',), 4.1, 5.1, 0.4, 5.6, RED))) + waist(suit) + belt(P(MOD, rect(('north',), 3.7, 5.3, 0.4, 1.9, MOL)))
    bs += shoulder(lea) + bracer_plate(P(MOD, trim(0.3, MOL))) + thigh(suit) + knee(P(MOD, trim(0.3, MOL))) + boot(P(MOD, trim(0.3, MOL)))
    bs += cowl(P('#1c1c20', trim(0.3, MOD)), shadow='#000000')
    bs += [box('head', -4.05, -7.0, -4.2, 8.1, 7.2, 0.1, P('#050505', rect(('north',), 1.2, 3.0, 2.8, 3.8, GLOWR, glow=True), rect(('north',), 5.1, 6.9, 2.8, 3.8, GLOWR, glow=True), bevel=0, noise=0))]
    bs += cape(P('#1a1a1e', alpha=tatter(3.5, 2.3, 0.4), noise=0.08), length=19, flare=7)
    lk = P(MOL, trim(0.15, '#2a2a30'), bevel=0.1)
    for i in range(7):
        bs.append(box('body', -4.2 + i * 1.2, 11.9 + 0.9 * (1 - abs(i - 3) / 3.0), -2.95, 1.2, 0.5, 0.3, lk))
    return bs


@slim('r7s', 7, 'Галит: листовые латы, серые вставки, листья на плечах и за спиной, открытый шлем с листьями и тонкими рогами')
def r7s():
    lf = P(t.CG, trim(0.3, t.CGO), mottle(t.CGB, 1.0, 0.86), rect(('up',), -1, 99, -1, 99, t.CGL))
    suit = P(t.CGD, trim(0.3, t.CGO))
    metal = P(t.CM, trim(0.3, t.CMD))
    bs = under(suit, suit) + breastplate(P(t.CG, trim(0.3, t.CGO), mottle(t.CGB, 1.0, 0.86), rect(('north',), 3.4, 5.8, 0.6, 5.2, t.CM), rect(('north',), 3.9, 5.3, 1.0, 4.8, t.CMD)))
    bs += waist(suit) + belt(metal) + shoulder(lf) + bracer(lf) + thigh(P(t.CG, trim(0.3, t.CGO), mottle(t.CGB, 1.0, 0.88)))
    bs += knee(metal) + boot(P(t.CG, trim(0.3, t.CGO), rect(('north',), -1, 99, 2.2, 99, t.CM)))
    for (z, L, rz, rx) in ((-1.6, 3.0, -24, -12), (0.2, 3.8, -30, 0), (1.8, 2.8, -22, 12)):
        bs += sym(leaf('rightArm', (-3.4, -2.0, z), L, 1.7, rz=rz, rx=rx))
    for (x, L, rx) in ((-2.4, 5.0, -162), (0.0, 6.0, -158), (2.4, 5.0, -162)):
        bs += [leaf('body', (x, 0.6, 2.75), L, 2.2, rx=rx, rz=x * 3)]
    bs += cap(lf) + [box('head', -1.0, -8.0, -5.2, 2.0, 1.8, 0.4, P(t.CGB, glow=True))]
    bs += [leaf('head', (x, -8.4, z), L, 1.9, rx=rx, rz=rz) for (x, z, L, rx, rz) in ((-1.8, 0.4, 3.4, -14, -18), (0.0, 0.0, 4.2, -18, 0), (1.8, 0.4, 3.4, -14, 18))]
    bs += sym(*chain('head', (-4.5, -6.6, 1.0), ((2.0, 10, -55), (2.0, 25, -30), (1.8, 40, -5)), 1.0, None, taper=0.75, colors=(t.CMD, '#a8a8a8')))
    return bs


if __name__ == '__main__':
    for k in sys.argv[1:] or KEYS:
        w.build(k)
