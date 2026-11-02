"""Концепты моделей брони Странника R1-R7: по 3 на ступень (А - Путник, Б - Рыцарь миров, В - Дух мира).

Модели собираются из коробок meshkit на костях игрока (как будет в игре), рендер - свой растеризатор со свечением.
Запуск: python3 wconcepts.py [ключ ...]  (ключ вида r1a; без ключей - все). Картинки - в OUT.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import meshkit as mk  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'wanderer_concepts')
os.makedirs(OUT, exist_ok=True)

# ------------------------------------------------------------------ раскраска

def fuv(p, face):
    n = face.name
    if n in ('north', 'south'):
        return p[..., 0], p[..., 1]
    if n in ('east', 'west'):
        return p[..., 2], p[..., 1]
    return p[..., 0], p[..., 2]


def P(base, *decals, bevel=0.2, noise=0.05, glow=False, alpha=None, grad=0.0):
    """Материал + наклейки. Наклейка: f(p, face, box) -> (цвет, доля 0..1, светится). alpha(p, face, box) -> 0/1 (дыры)."""
    mat = mk.material(base, bevel=bevel, noise=noise, grad=grad)

    def paint(p, face, box):
        col = np.asarray(mat(p, face, box), float)
        shp = col.shape[:-1]
        g = np.full(shp, 1.0 if glow else 0.0)
        for d in decals:
            dc, da, dg = d(p, face, box)
            da = np.broadcast_to(np.asarray(da, float), shp)
            col = col * (1 - da[..., None]) + np.asarray(dc, float) * da[..., None]
            if dg:
                g = np.where(da > 0.5, 1.0, g)
        a = np.ones(shp) if alpha is None else np.broadcast_to(alpha(p, face, box), shp)
        A = np.where(a < 0.5, 0.0, np.where(g > 0, 0.6, 1.0))
        return np.concatenate([col, A[..., None]], -1)
    return paint


def C(h):
    return mk.rgb(h)


def rect(faces, u0, u1, v0, v1, color, glow=False):
    c = C(color)

    def d(p, face, box):
        if faces != 'all' and face.name not in faces:
            return c, 0.0, glow
        u, v = fuv(p, face)
        return c, ((u >= u0) & (u <= u1) & (v >= v0) & (v <= v1)).astype(float), glow
    return d


def disc(faces, cu, cv, r, color, glow=False, r0=0.0):
    c = C(color)

    def d(p, face, box):
        if faces != 'all' and face.name not in faces:
            return c, 0.0, glow
        u, v = fuv(p, face)
        rr = np.hypot(u - cu, v - cv)
        return c, ((rr <= r) & (rr >= r0)).astype(float), glow
    return d


def trim(w, color, glow=False, faces='all'):
    c = C(color)

    def d(p, face, box):
        if faces != 'all' and face.name not in faces:
            return c, 0.0, glow
        _, fu, fv = face.texel_points()
        return c, (mk.edge_dist(fu, fv, face) < w).astype(float), glow
    return d


def mottle(color, scale=1.0, thr=0.6, seed=0, glow=False, amount=1.0):
    c = C(color)

    def d(p, face, box):
        q = p + box.p0
        # клетка ~1/scale единицы - "пиксель" Minecraft, а не рябь
        return c, (mk.hash_noise(q, scale * 0.25, seed) > thr).astype(float) * amount, glow
    return d


def veins(color, scale=0.8, width=0.25, glow=True, seed=0.0):
    c = C(color)

    def d(p, face, box):
        q = (p + box.p0) * scale + seed
        v = (np.sin(1.3 * q[..., 0] + 2.1 * np.sin(0.8 * q[..., 1])) + np.sin(1.1 * q[..., 1] + 1.7 * np.sin(0.9 * q[..., 2]))
             + np.sin(0.9 * q[..., 2] + 1.5 * np.sin(1.2 * q[..., 0])))
        return c, (np.abs(v) < width).astype(float), glow
    return d


def streaks(color, scale=1.0, thr=0.55, seed=1):
    """Волокна вдоль высоты (кора, дерево)."""
    c = C(color)

    def d(p, face, box):
        q = p + box.p0
        s = np.stack([q[..., 0] * 1.6, q[..., 1] * 0.12, q[..., 2] * 1.6], -1)
        return c, (mk.hash_noise(s, scale * 0.3, seed) > thr).astype(float), False
    return d


def dots(faces, step, r, color, off=0.0):
    c = C(color)

    def d(p, face, box):
        if faces != 'all' and face.name not in faces:
            return c, 0.0, False
        u, v = fuv(p, face)
        du = (u + off) % step - step / 2
        dv = (v + off) % step - step / 2
        return c, (du * du + dv * dv < r * r).astype(float), False
    return d


def facets(color, amount=0.35):
    """Грани кристалла: диагональ делит каждую сторону на светлую и тёмную половины."""
    c = C(color)

    def d(p, face, box):
        _, fu, fv = face.texel_points()
        return c, ((fu / max(face.wu, 1e-6) + fv / max(face.hu, 1e-6)) < 1.0).astype(float) * amount, False
    return d


def stripe(faces, k, b, w, color, glow=False):
    """Наклонная полоса |u - k*v - b| < w (перевязь, блик)."""
    c = C(color)

    def d(p, face, box):
        if faces != 'all' and face.name not in faces:
            return c, 0.0, glow
        u, v = fuv(p, face)
        return c, (np.abs(u - k * v - b) < w).astype(float), glow
    return d


def tatter(depth=2.5, freq=2.1, seed=0.0):
    """Рваный низ: дыры у нижнего края коробки."""
    def a(p, face, box):
        u = p[..., 0] + p[..., 2]
        h = box.size[1]
        jag = depth * (0.5 + 0.5 * np.sin(u * freq + seed)) * (0.6 + 0.8 * mk.hash_noise(np.stack([u, u * 0, u * 0], -1), 0.7, 3))
        return (p[..., 1] < h - jag).astype(float)
    return a


# ------------------------------------------------------------------ коробки

def box(bone, x, y, z, w, h, d, paint, rot=(0, 0, 0), pivot=None, k=4.0):
    return mk.Box('a', bone, x, y, z, w, h, d, paint, rot=rot, pivot=pivot, k=k)


def sym(*bs):
    out = []
    for b in bs:
        out += [b, b.mirror()]
    return out


def rod(bone, start, L, t, paint, rx=0.0, rz=0.0, ry=0.0, k=4.0):
    """Брусок длиной L от точки start вверх (-y), повёрнутый вокруг start."""
    sx, sy, sz = start
    return box(bone, sx - t / 2, sy - L, sz - t / 2, t, L, t, paint, rot=(rx, ry, rz), pivot=(sx, sy, sz), k=k)


def tip(start, L, rx=0.0, rz=0.0, ry=0.0):
    return np.asarray(start, float) + mk.rot_matrix(rx, ry, rz) @ np.array([0.0, -L, 0.0])


def chain(bone, start, segs, t0, paint, taper=0.75, colors=None):
    """Рог / ветка: сегменты (длина, rx, rz) друг за другом, тоньше к концу; colors=(от, до) - цвет плавно к кончику."""
    out, s, t = [], np.asarray(start, float), t0
    for i, (L, rx, rz) in enumerate(segs):
        if colors:
            f = i / max(1, len(segs) - 1)
            paint = P(tuple(C(colors[0]) * (1 - f) + C(colors[1]) * f), noise=0.06)
        out.append(rod(bone, tuple(s), L, t, paint, rx=rx, rz=rz))
        s = tip(s, L * 0.92, rx=rx, rz=rz)
        t = max(0.35, t * taper)
    return out


def ring(bone, center, radius, n, t, paint, y_axis=True):
    out = []
    L = 2 * math.pi * radius / n * 1.08
    cx, cy, cz = center
    for i in range(n):
        a = 2 * math.pi * i / n
        px, pz = cx + radius * math.cos(a), cz + radius * math.sin(a)
        b = -(math.degrees(a) + 90)
        out.append(box(bone, px - L / 2, cy - t / 2, pz - t / 2, L, t, t, paint, rot=(0, b, 0), pivot=(px, cy, pz)))
    return out


# ---- части брони (кости игрока; y вниз, лицо в -Z)

def chest(paint, top=-1.0, bottom=11.0, inf=1.0):
    return [box('body', -4 - inf, top, -2 - inf, 8 + 2 * inf, bottom - top, 4 + 2 * inf, paint)]


def belt(paint, y=9.8, h=2.6):
    return [box('body', -4.7, y, -2.7, 9.4, h, 5.4, paint)]


def upper_arm(paint, top=-3.0, bottom=5.0, inf=1.0):
    return sym(box('rightArm', -3 - inf, top, -2 - inf, 4 + 2 * inf, bottom - top, 4 + 2 * inf, paint))


def vambrace(paint, top=5.0, bottom=9.3, inf=0.7):
    return sym(box('rightArm', -3 - inf, top, -2 - inf, 4 + 2 * inf, bottom - top, 4 + 2 * inf, paint))


def thigh(paint, top=-0.4, bottom=7.2, inf=0.6):
    return sym(box('rightLeg', -2 - inf, top, -2 - inf, 4 + 2 * inf, bottom - top, 4 + 2 * inf, paint))


def boot(paint, top=7.0, inf=0.8, toe=0.0):
    return sym(box('rightLeg', -2 - inf, top, -2 - inf - toe, 4 + 2 * inf, 12.4 - top, 4 + 2 * inf + toe, paint))


def helm(paint, inf=1.0, top_extra=0.0):
    return [box('head', -4 - inf, -8 - inf - top_extra, -4 - inf, 8 + 2 * inf, 8 + 2 * inf + top_extra, 8 + 2 * inf, paint)]


def pauldron(paint, tilt=-12.0, layers=2, big=1.0):
    bs = [box('rightArm', -4.8 - big, -3.9 - big * 0.4, -3.6 - big * 0.3, 6.4 + big, 3.0 + big * 0.4, 7.2 + big * 0.6, paint,
              rot=(0, 0, tilt), pivot=(-1, -3, 0))]
    if layers > 1:
        bs.append(box('rightArm', -4.9 - big * 0.6, -1.4, -3.4 - big * 0.2, 4.8 + big * 0.6, 2.2, 6.8 + big * 0.4, paint,
                      rot=(0, 0, tilt - 6), pivot=(-1, -1, 0)))
    return bs + [b.mirror() for b in bs]


def hood(paint, shadow='#140f0c', tip_len=3.2, capelet=None):
    bs = [
        box('head', -5.2, -9.8, -5.2, 10.4, 1.8, 10.8, paint),           # верх
        box('head', -5.2, -8.0, 4.2, 10.4, 8.8, 1.4, paint),             # затылок
        box('head', -4.4, -8.6, -6.0, 8.8, 1.3, 1.3, paint),             # козырёк
        box('head', -4.0, -8.0, -4.2, 8.0, 2.0, 0.15, P(shadow, noise=0.0, bevel=0)),  # тень на лбу
        box('head', -1.9, -9.6, 5.0, 3.8, tip_len, 2.6, paint, rot=(30, 0, 0), pivot=(0, -9.6, 5.0)),  # хвостик
    ]
    bs += sym(box('head', -5.6, -8.0, -5.2, 1.6, 8.8, 9.4, paint))      # бока
    if capelet:
        bs += [box('body', -5.8, -1.6, -3.6, 11.6, 3.8, 7.2, capelet)]
        bs += sym(box('rightArm', -4.6, -3.9, -3.5, 6.4, 3.6, 7.0, capelet, rot=(0, 0, -14), pivot=(-1, -3, 0)))
    return bs


def cloak(paint, length=20.0, width=10.8, flare=8.0, z=3.1, top=-1.2, thick=0.8):
    return [box('body', -width / 2, top, z, width, length, thick, paint, rot=(flare, 0, 0), pivot=(0, top, z))]


def crystal(bone, start, L, t, color, rx=0.0, rz=0.0, ry=0.0, glow=True, tipc='#ffffff'):
    paint = P(color, rect('all', -1, 99, -1, min(1.2, L * 0.25), tipc, glow=glow), facets('#ffffff', 0.25), glow=glow, bevel=0.1, noise=0.02)
    return rod(bone, start, L, t, paint, rx=rx, rz=rz, ry=ry)


def mannequin():
    skin, suit, pants = '#c8956d', '#3a3a44', '#2c2c34'
    face = P(skin, rect(('north',), 1.4, 3.0, 4.0, 5.0, '#f4f4f4'), rect(('north',), 2.2, 3.0, 4.0, 5.0, '#30509a'),
             rect(('north',), 5.0, 6.6, 4.0, 5.0, '#f4f4f4'), rect(('north',), 5.0, 5.8, 4.0, 5.0, '#30509a'),
             rect(('north',), 3.0, 5.0, 6.2, 6.8, '#8a5a40'), rect(('north', 'east', 'west', 'south'), -1, 99, -1, 1.6, '#4a3020'),
             rect(('up',), -1, 99, -1, 99, '#4a3020'), rect(('south',), -1, 99, -1, 5, '#4a3020'), bevel=0.0, noise=0.03)
    arm = P(suit, rect(('north', 'south', 'east', 'west'), -1, 99, 10.2, 99, skin), rect(('down',), -1, 99, -1, 99, skin), bevel=0)
    return ([mk.Box('m', 'head', -4, -8, -4, 8, 8, 8, face, k=4), mk.Box('m', 'body', -4, 0, -2, 8, 12, 4, P(suit, bevel=0), k=2)]
            + sym(mk.Box('m', 'rightArm', -3, -2, -2, 4, 12, 4, arm, k=2), mk.Box('m', 'rightLeg', -2, 0, -2, 4, 12, 4, P(pants, bevel=0), k=2)))


# ------------------------------------------------------------------ концепты

CONCEPTS = {}


def concept(key, tier, line, title):
    def reg(fn):
        CONCEPTS[key] = (tier, line, title, fn)
        return fn
    return reg


# ---- R1 Реалмит
COP, COPD, COPL, LEA, LEAD, CLOTH, BRASS, MAIL = '#c8642c', '#7a3a18', '#f0a050', '#6b4a2e', '#4a321e', '#7e5f40', '#d8b04a', '#8a8c90'


@concept('r1a', 1, 'А', 'Путник: куртка, капюшон, компас')
def r1a():
    lea = P(LEA, trim(0.45, LEAD))
    bs = chest(P(LEA, trim(0.45, LEAD), rect(('north',), 4.8, 5.2, 0, 99, LEAD), disc(('north',), 5, 4.2, 1.9, BRASS),
                 disc(('north',), 5, 4.2, 1.3, '#efe6c8'), rect(('north',), 4.85, 5.15, 3.0, 4.2, '#c02020'), rect(('north',), 4.85, 5.15, 4.2, 5.4, '#303030')))
    bs += upper_arm(lea) + vambrace(P(LEAD, rect(('north', 'south', 'east', 'west'), -1, 99, -1, 0.9, COP)))
    bs += pauldron(P(COP, trim(0.4, COPD), dots('all', 2.2, 0.32, BRASS)), tilt=-10, layers=1, big=0.4)
    bs += belt(P(LEAD, rect(('north',), 4.1, 5.3, 0.4, 2.2, BRASS))) + [box('body', 2.4, 9.4, -3.9, 2.8, 3.0, 1.6, P(LEA, trim(0.3, LEAD)))]
    bs += thigh(P(CLOTH, mottle('#6e5236', 1.0, 0.7))) + boot(P(LEA, rect(('north',), -1, 99, 3.4, 99, COP), trim(0.35, LEAD)), toe=0.4)
    bs += hood(P('#7a5a3a', trim(0.35, '#5a4028')), capelet=P('#7a5a3a', trim(0.35, '#5a4028')))
    bs += [box('body', -5.6, -0.4, 3.0, 11.2, 3.4, 3.4, P('#9a7a50', rect('all', 2.2, 2.9, -1, 99, LEAD), rect('all', 8.3, 9.0, -1, 99, LEAD)))]
    return bs


@concept('r1b', 1, 'Б', 'Рыцарь: бригантина, шапель, бармица')
def r1b():
    cop = P(COP, trim(0.45, COPD), dots(('north', 'south', 'east', 'west'), 2.0, 0.3, BRASS, off=1.0))
    mail = P(MAIL, dots('all', 0.8, 0.28, '#5a5c60'), noise=0.08, bevel=0.1)
    bs = chest(cop) + upper_arm(mail) + vambrace(P(COP, trim(0.4, COPD))) + pauldron(P(COP, trim(0.45, COPD)), tilt=-14)
    bs += belt(P(LEAD, rect(('north',), 4.1, 5.3, 0.4, 2.2, BRASS))) + thigh(mail)
    bs += sym(box('rightLeg', -2.7, 5.2, -3.3, 5.4, 2.8, 1.4, P(COP, trim(0.4, COPD))))
    bs += boot(P(COP, trim(0.45, COPD)), toe=0.3)
    bs += [box('head', -4.9, -9.9, -4.9, 9.8, 2.9, 9.8, P(COP, trim(0.4, COPD))),
           box('head', -3.6, -10.9, -3.6, 7.2, 1.2, 7.2, P(COP, trim(0.4, COPD))),
           box('head', -6.9, -7.3, -6.9, 13.8, 0.9, 13.8, P(COP, trim(0.6, COPD))),
           box('head', -4.9, -7.0, 4.1, 9.8, 7.6, 0.9, mail)]
    bs += sym(box('head', -5.1, -7.0, -3.6, 0.9, 7.6, 7.9, mail))
    return bs


@concept('r1c', 1, 'В', 'Дух мира: рудная броня с тлеющими трещинами')
def r1c():
    ore = P('#4a3c34', veins('#ff8a2a', scale=0.9, width=0.22), mottle('#5e4c40', 1.4, 0.7), noise=0.12)
    bs = chest(ore) + upper_arm(ore) + vambrace(ore) + thigh(ore) + boot(ore, toe=0.4) + belt(P('#3a2e28', noise=0.1))
    bs += [box('body', -5.6, -1.6, -3.7, 5.2, 5.0, 1.6, ore, rot=(0, 0, 9), pivot=(-3, 1, -3)),
           box('body', 0.6, 3.0, -3.8, 5.0, 4.4, 1.6, ore, rot=(0, 0, -7), pivot=(3, 5, -3)),
           box('body', -2.0, 1.4, -4.2, 4.0, 4.0, 1.0, P('#ff9a3c', disc(('north',), 2, 2, 1.2, '#fff0b0', glow=True), glow=True, bevel=0.1))]
    bs += sym(box('rightArm', -5.4, -4.8, -3.9, 6.8, 4.4, 7.8, ore, rot=(6, 0, -10), pivot=(-1, -3, 0)))
    bs += helm(P('#4a3c34', veins('#ff8a2a', scale=0.9, width=0.22), rect(('north',), 2.0, 8.0, 4.3, 5.2, '#ffb050', glow=True), noise=0.12))
    bs += [box('head', -3.2, -10.8, -3.0, 6.0, 2.0, 6.2, ore, rot=(0, 22, 0))]
    return bs


# ---- R2 Эдем
ED, EDD, WH, GOLD, GLOWG = '#d8401c', '#8a2010', '#f0ede6', '#f0c040', '#ffe27a'


@concept('r2a', 2, 'А', 'Путник: плащ заката, застёжка-солнце')
def r2a():
    red = P(ED, trim(0.5, WH))
    bs = chest(P(WH, stripe(('north',), 0.75, -1.0, 1.1, ED), stripe(('south',), -0.75, 10.0, 1.1, ED), trim(0.35, '#c8c4bc')))
    bs += upper_arm(P(WH, trim(0.3, '#c8c4bc'))) + vambrace(P(ED, rect(('north', 'south', 'east', 'west'), -1, 99, -1, 0.8, GOLD)))
    bs += belt(P('#7a3a20', rect(('north',), 4.0, 5.4, 0.3, 2.3, GOLD))) + thigh(P(WH)) + boot(P(ED, trim(0.4, WH)), toe=0.3)
    bs += hood(P(ED, trim(0.4, WH)), shadow='#2a0c06', capelet=P(ED, trim(0.45, WH)))
    bs += cloak(P(ED, trim(0.55, WH), grad=0.35), length=21, flare=10)
    bs += [box('body', -1.8, -0.4, -4.2, 3.6, 3.6, 0.9, P(GOLD, disc(('north',), 1.8, 1.8, 1.0, '#fff6c0', glow=True), glow=True))]
    bs += [rod('body', (0, 1.4, -4.4), 2.8, 0.5, P(GOLD, glow=True), rx=-90, rz=a) for a in (0, 45, 90, 135, 180, 225, 270, 315)]
    return bs


@concept('r2b', 2, 'Б', 'Рыцарь: светлые латы, гребень из лучей')
def r2b():
    plate = P(WH, trim(0.5, ED))
    bs = chest(P(WH, trim(0.5, ED), disc(('north',), 5, 4.5, 2.2, GOLD), disc(('north',), 5, 4.5, 1.2, ED)))
    bs += upper_arm(P(WH, trim(0.4, ED))) + vambrace(plate) + pauldron(P(WH, trim(0.5, ED), disc(('west',), 3.6, 1.6, 1.3, GOLD)), tilt=-16, big=1.2)
    bs += belt(P(ED, rect(('north',), 4.0, 5.4, 0.3, 2.3, GOLD))) + thigh(P(WH, trim(0.4, ED))) + boot(plate, toe=0.5)
    bs += helm(P(WH, trim(0.5, ED), rect(('north',), 1.6, 8.4, 4.0, 4.9, '#1a1010'), rect(('north',), 4.6, 5.4, 4.0, 8.0, '#1a1010')))
    bs += [rod('head', (0, -9.0, 0.5), L, 0.7, P(GOLD, glow=(a == 0)), rz=a) for a, L in
           ((-60, 3.4), (-40, 4.4), (-20, 5.4), (0, 6.2), (20, 5.4), (40, 4.4), (60, 3.4))]
    return bs


@concept('r2c', 2, 'В', 'Дух мира: золотые лепестки, нимб')
def r2c():
    base = P('#b8862a', trim(0.3, '#7a5418'))
    bs = chest(base) + upper_arm(base) + vambrace(P(GOLD, trim(0.3, '#9a6a20'))) + belt(P(ED)) + thigh(P('#a0301a')) + boot(P(GOLD, trim(0.3, '#9a6a20')))
    pet_g, pet_r = P(GOLD, trim(0.25, '#fff0a0')), P(ED, trim(0.25, '#ffb090'))
    for row, y in enumerate((0.0, 3.2, 6.4)):
        for i, x in enumerate((-4.6, -1.5, 1.6)):
            bs.append(box('body', x, y, -3.9 - row * 0.1, 3.1, 3.8, 0.7, pet_g if (row + i) % 2 == 0 else pet_r, rot=(-12, 0, (i - 1) * 8),
                          pivot=(x + 1.5, y, -3.6)))
    for j, (zz, a) in enumerate(((-3.0, -25), (-0.6, -32), (1.8, -25))):
        bs += sym(box('rightArm', -5.2, -4.6, zz, 4.2, 5.4, 2.6, pet_g if j != 1 else pet_r, rot=(0, 0, a), pivot=(-1, -3, zz + 1)))
    bs += [box('head', -4.7, -6.8, -4.7, 9.4, 1.4, 9.4, P(GOLD, trim(0.3, '#9a6a20'))),
           box('head', -0.8, -7.0, -5.1, 1.6, 1.8, 0.6, P('#ff5020', glow=True))]
    bs += ring('head', (0, -12.6, 0), 4.6, 16, 0.7, P(GLOWG, glow=True))
    return bs


# ---- R3 Дикий лес
WWB, WWD, WWL, BARK, MOSS, LEAFD, GLOWC = '#1a62e0', '#0e2f78', '#5aa0ff', '#24345a', '#2f8f80', '#123c8a', '#8ae8ff'


def leaves():
    return P(WWB, mottle(LEAFD, 1.2, 0.55), mottle(WWL, 1.7, 0.86), noise=0.08)


@concept('r3a', 3, 'А', 'Путник: лиственный плащ, фонарь со светлячками')
def r3a():
    dark = P('#262c3a', trim(0.35, '#151a24'))
    bs = chest(dark) + upper_arm(dark) + vambrace(P(BARK, streaks(WWD))) + belt(P('#3a2a1c')) + thigh(P('#2a3040')) + boot(P(BARK, streaks(WWD)), toe=0.3)
    bs += hood(leaves(), shadow='#05101e', capelet=leaves())
    bs += cloak(P(WWB, mottle(LEAFD, 1.2, 0.55), mottle(WWL, 1.7, 0.86), alpha=tatter(2.8, 2.6), noise=0.08), length=21, flare=9)
    tw = P(BARK, streaks(WWD))
    bs += sym(*chain('head', (-3.2, -9.4, -1.0), ((2.6, -10, -28), (2.0, -10, -5)), 0.9, tw))
    bs += [box('body', -4.6, 10.6, -4.6, 2.6, 3.4, 2.6, P('#30343c', rect(('north', 'south', 'east', 'west'), 0.45, 2.15, 0.6, 2.8, '#7affd8', glow=True))),
           box('body', -3.9, 9.9, -3.9, 1.2, 0.8, 1.2, P('#30343c'))]
    return bs


@concept('r3b', 3, 'Б', 'Рыцарь: латы из синего дерева, рога-ветви')
def r3b():
    wood = P(WWB, streaks(WWD, 1.0, 0.5), trim(0.4, WWD))
    bs = chest(wood) + upper_arm(wood) + vambrace(wood) + pauldron(P(WWB, streaks(WWD, 1.0, 0.5), trim(0.45, WWD)), tilt=-14, big=1.0)
    bs += belt(P(BARK)) + thigh(P(WWD, streaks('#0a2050'))) + boot(wood, toe=0.4)
    bs += helm(P(WWB, streaks(WWD, 1.0, 0.5), trim(0.4, WWD), rect(('north',), 1.8, 8.2, 4.1, 5.0, '#06122a')))
    ant = P(WWL, streaks(WWB, 1.0, 0.6))
    bs += sym(*chain('head', (-3.6, -9.0, 0), ((3.0, 0, -35), (3.4, 0, -15), (2.6, 0, 10)), 1.1, ant))
    bs += sym(rod('head', (-5.8, -12.0, 0), 2.6, 0.6, ant, rz=-70), rod('head', (-6.6, -14.4, 0), 2.0, 0.5, ant, rz=-5))
    rootp = P(BARK, streaks(WWD))
    bs += sym(rod('rightLeg', (-2.6, 12.0, -1.5), 3.2, 0.9, rootp, rx=-110, rz=-30), rod('rightLeg', (-2.6, 12.0, 1.6), 3.0, 0.9, rootp, rx=110, rz=-35))
    return bs


@concept('r3c', 3, 'В', 'Дух мира: древень с мхом и светящимися грибами')
def r3c():
    bark = P(BARK, streaks(WWD, 1.0, 0.45), mottle(MOSS, 1.1, 0.8))
    bs = chest(bark) + upper_arm(bark) + vambrace(bark) + belt(P('#1a2238')) + thigh(bark) + boot(bark, toe=0.4)
    bs += sym(box('rightArm', -5.0, -4.2, -3.6, 6.6, 2.0, 7.2, P(MOSS, mottle('#3fae8e', 1.3, 0.6), noise=0.1), rot=(0, 0, -8), pivot=(-1, -3, 0)))
    bs += helm(P(BARK, streaks(WWD, 1.0, 0.45), rect(('north',), 2.0, 3.6, 4.0, 5.2, GLOWC, glow=True), rect(('north',), 6.4, 8.0, 4.0, 5.2, GLOWC, glow=True)))
    bs += [box('head', -5.2, -9.8, -5.2, 10.4, 1.0, 10.4, P(MOSS, mottle('#3fae8e', 1.3, 0.6)))]
    cap, stem = P(GLOWC, dots(('up',), 1.6, 0.3, '#ffffff'), glow=True, bevel=0.15), P('#d8f0ff', bevel=0)
    for bone, (x, y, z), s in (('rightArm', (-3.2, -4.2, -1.0), 1.0), ('rightArm', (-1.0, -4.2, 1.8), 0.7), ('head', (-2.6, -9.8, 1.6), 0.9),
                               ('head', (2.0, -9.8, -1.4), 0.7), ('leftArm', (2.6, -4.2, 0.6), 0.9)):
        bs += [box(bone, x - 0.35 * s, y - 2.0 * s, z - 0.35 * s, 0.7 * s, 2.0 * s, 0.7 * s, stem),
               box(bone, x - 1.5 * s, y - 2.8 * s, z - 1.5 * s, 3.0 * s, 0.9 * s, 3.0 * s, cap)]
    return bs


# ---- R4 Апалачия
AP, APD, APL, CRY = '#c020d8', '#5a1078', '#f090ff', '#e070ff'


@concept('r4a', 4, 'А', 'Путник: пурпурный плащ, вуаль, кристаллы на наручах')
def r4a():
    lea = P(APD, trim(0.35, '#3a0850'))
    bs = chest(P(APD, trim(0.4, AP), rect(('north',), 4.6, 5.4, 0, 99, AP))) + upper_arm(lea) + vambrace(P('#3a0850', trim(0.3, AP)))
    bs += belt(P('#c8c8d0')) + thigh(P('#4a1060')) + boot(lea, toe=0.3)
    bs += hood(P(AP, trim(0.4, APD)), shadow='#1a0420', capelet=P(AP, trim(0.4, APD)))
    bs += [box('head', -4.4, -3.9, -4.7, 8.8, 4.3, 0.5, P(APL, trim(0.3, AP), mottle('#ffffff', 1.5, 0.85, amount=0.4), bevel=0))]
    bs += cloak(P(AP, trim(0.5, APD), grad=0.3), length=21, flare=9)
    bs += sym(crystal('rightArm', (-3.9, 8.0, -0.8), 3.2, 1.0, CRY, rz=-35), crystal('rightArm', (-3.9, 8.4, 1.2), 2.4, 0.8, CRY, rz=-55, rx=15))
    return bs


@concept('r4b', 4, 'Б', 'Рыцарь: гранёные латы, кристалл во лбу')
def r4b():
    fac = P(AP, facets('#ffffff', 0.25), facets('#000000', 0.0), trim(0.4, APD))
    bs = chest(P(AP, facets('#ffffff', 0.25), trim(0.45, APD), rect(('north',), 4.2, 5.8, 1.0, 9.0, APD))) + upper_arm(fac) + vambrace(fac)
    bs += belt(P(APD)) + thigh(P(APD, facets('#ffffff', 0.15))) + boot(fac, toe=0.6)
    bs += sym(box('rightArm', -5.6, -4.8, -3.8, 4.4, 4.4, 7.6, fac, rot=(0, 0, -38), pivot=(-1, -3, 0)),
              box('rightArm', -7.4, -5.4, -1.2, 3.2, 3.2, 2.4, fac, rot=(0, 0, -50), pivot=(-5.5, -4, 0)))
    bs += helm(P(AP, facets('#ffffff', 0.25), trim(0.45, APD), rect(('north',), 1.8, 8.2, 4.2, 5.0, '#14041c')))
    bs += [crystal('head', (0, -6.8, -5.4), 3.6, 1.4, CRY, rx=-20)]
    bs += sym(box('head', -6.4, -6.0, -3.0, 1.4, 4.0, 6.0, fac, rot=(0, 0, 18), pivot=(-5, -6, 0)))
    return bs


@concept('r4c', 4, 'В', 'Дух мира: друзы кристаллов на плечах и спине')
def r4c():
    dark = P('#3a1050', trim(0.3, '#220830'), mottle(APD, 1.2, 0.7))
    bs = chest(dark) + upper_arm(dark) + vambrace(dark) + belt(P('#220830')) + thigh(dark) + boot(dark, toe=0.4)
    bs += helm(P('#3a1050', trim(0.3, '#220830'), rect(('north',), 1.8, 8.2, 4.2, 5.0, CRY, glow=True)))
    for (x, z, L, rx, rz) in ((-3.0, -1.0, 4.6, -8, -20), (-1.6, 1.4, 3.6, 18, -8), (-4.2, 0.6, 3.0, 5, -45), (-2.0, -2.6, 2.6, -30, -15)):
        bs += sym(crystal('rightArm', (x, -3.0, z), L, 1.3, CRY, rx=rx, rz=rz))
    for (x, y, L, rx, rz) in ((-2.0, 2.0, 5.0, 60, -15), (1.4, 1.2, 4.0, 55, 20), (0, 5.0, 3.2, 70, 0), (-3.4, 5.6, 2.4, 65, -35), (3.2, 6.0, 2.6, 65, 30)):
        bs += [crystal('body', (x, y, 3.0), L, 1.4, CRY, rx=rx, rz=rz)]
    for (x, z, L, rz, rx) in ((-2.4, 0.0, 3.6, -15, 0), (0.6, -1.0, 4.4, 5, -10), (2.6, 1.4, 3.0, 25, 10)):
        bs += [crystal('head', (x, -9.0, z), L, 1.2, CRY, rz=rz, rx=rx)]
    return bs


# ---- R5 Небесный терн
SKY, SKYL, SKYD, BLUE = '#aeb2ba', '#eef0f4', '#6a6e78', '#8ab8ec'


@concept('r5a', 5, 'А', 'Путник: серебряный плащ, длинный шарф на ветру')
def r5a():
    sil = P(SKY, trim(0.35, SKYD))
    bs = chest(P(SKY, dots('all', 0.9, 0.3, '#8a8e98'), trim(0.4, SKYD))) + upper_arm(sil) + vambrace(P(SKYL, trim(0.3, BLUE)))
    bs += belt(P(SKYD, rect(('north',), 4.0, 5.4, 0.3, 2.3, BLUE))) + thigh(P('#8a8e98')) + boot(P(SKYL, trim(0.35, SKYD)), toe=0.3)
    bs += hood(P(SKYL, trim(0.35, SKY)), shadow='#1a1e28')
    bs += cloak(P(SKYL, trim(0.5, BLUE), grad=0.15), length=21, flare=14)
    sc = P(BLUE, trim(0.3, '#5a88c8'))
    bs += [box('body', -5.3, -1.6, -3.6, 10.6, 2.8, 7.2, sc),
           box('body', -3.4, 1.0, -3.7, 2.4, 6.5, 0.6, sc, rot=(0, 0, 8), pivot=(-2.2, 1.0, -3.4)),
           box('body', -2.6, -0.6, 3.0, 2.6, 15, 0.6, sc, rot=(62, 8, 6), pivot=(-1.3, -0.6, 3.0)),
           box('body', 0.4, -0.6, 3.0, 2.2, 12, 0.6, sc, rot=(54, -10, -8), pivot=(1.5, -0.6, 3.0))]
    return bs


@concept('r5b', 5, 'Б', 'Рыцарь: обтекаемые латы, крылатый шлем, перья на плечах')
def r5b():
    plate = P(SKYL, trim(0.45, BLUE))
    bs = chest(P(SKYL, trim(0.45, BLUE), stripe(('north',), 0.0, 5.0, 0.5, BLUE))) + upper_arm(P(SKY, trim(0.3, SKYD))) + vambrace(plate)
    bs += belt(P(SKYD)) + thigh(P(SKY, trim(0.3, SKYD))) + boot(plate, toe=0.6)
    bs += helm(P(SKYL, trim(0.45, BLUE), rect(('north',), 1.6, 8.4, 4.0, 4.8, '#10141c')))
    fe = P('#ffffff', rect('all', -1, 99, -1, 1.2, BLUE), bevel=0.1)
    for i, (rx, rz, L) in enumerate(((-30, -55, 5.4), (-45, -45, 4.6), (-60, -38, 3.8))):
        bs += sym(box('head', -5.6, -6.6 - L, 0.4 + i * 0.9, 0.6, L, 1.6, fe, rot=(rx, 0, rz), pivot=(-5.6, -6.6, 0.8 + i * 0.9)))
    for i, (zz, rz) in enumerate(((-2.6, -60), (-0.4, -70), (1.8, -80))):
        bs += sym(box('rightArm', -4.4, -8.0, zz, 1.8, 6.0, 1.6, fe, rot=(0, 0, rz), pivot=(-3.6, -2.8, zz)))
    return bs


@concept('r5c', 5, 'В', 'Дух мира: облачный воротник, парящие кольца')
def r5c():
    base = P(SKY, trim(0.3, SKYD))
    bs = chest(base) + upper_arm(base) + vambrace(P(SKYL)) + belt(P(SKYD)) + thigh(base) + boot(P(SKYL, trim(0.3, SKY)), toe=0.3)
    bs += helm(P(SKYL, trim(0.35, SKY), rect(('north',), 1.4, 8.6, 3.6, 6.0, '#1a2030'), rect(('north',), 2.2, 3.4, 4.2, 5.0, BLUE, glow=True),
                 rect(('north',), 6.6, 7.8, 4.2, 5.0, BLUE, glow=True)))
    cl = P('#ffffff', mottle('#dde6f4', 1.3, 0.6), bevel=0.35, noise=0.03)
    rnd = np.random.RandomState(5)
    for _ in range(14):
        a = rnd.uniform(0, 2 * math.pi)
        s = rnd.uniform(2.2, 3.6)
        x, z = 5.2 * math.cos(a), 3.6 * math.sin(a)
        bs.append(box('body', x - s / 2, -1.6 - s / 2 + rnd.uniform(-0.6, 0.6), z - s / 2, s, s * 0.8, s, cl))
    for _ in range(6):
        s = rnd.uniform(2.0, 3.2)
        bs += sym(box('rightArm', -3.4 - s / 2 + rnd.uniform(-1, 0.6), -3.6 - s / 2, rnd.uniform(-2.4, 2.4) - s / 2, s, s * 0.8, s, cl))
    for _ in range(5):
        s = rnd.uniform(2.0, 3.0)
        a = rnd.uniform(0, 2 * math.pi)
        bs.append(box('head', 4.0 * math.cos(a) - s / 2, -9.6 - s / 2, 4.0 * math.sin(a) - s / 2, s, s * 0.7, s, cl))
    rg = P(BLUE, glow=True, bevel=0.1)
    bs += sym(*ring('rightArm', (-1.0, 7.4, 0), 3.9, 12, 0.55, rg))
    bs += sym(*ring('rightLeg', (0.0, 9.0, 0), 3.7, 12, 0.5, rg))
    return bs


# ---- R6 Мортум
MO, MOD, MOL, BONE, RED, GLOWR = '#2e2e32', '#121214', '#5a5a60', '#d8d0b8', '#b01818', '#ff3020'


@concept('r6a', 6, 'А', 'Путник: рваный плащ, глаза в тени капюшона, цепи')
def r6a():
    lea = P(MO, trim(0.35, MOD))
    bs = chest(P(MO, trim(0.4, RED))) + upper_arm(lea) + vambrace(P(MOD, trim(0.3, MOL))) + thigh(P('#222226')) + boot(P(MOD, trim(0.3, MOL)), toe=0.3)
    bs += belt(P(MOD, rect(('north',), 4.0, 5.4, 0.3, 2.3, MOL)))
    bs += hood(P('#1c1c20', trim(0.3, MOD), alpha=None), shadow='#000000', capelet=P('#1c1c20', alpha=tatter(1.2, 3.0, 1.0)))
    bs += [box('head', -4.05, -8.0, -4.25, 8.1, 8.0, 0.2, P('#050505', rect(('north',), 1.4, 2.9, 3.8, 4.7, GLOWR, glow=True),
                                                            rect(('north',), 5.2, 6.7, 3.8, 4.7, GLOWR, glow=True), bevel=0, noise=0))]
    bs += cloak(P('#1a1a1e', alpha=tatter(3.5, 2.3, 0.4), noise=0.08), length=22, flare=9)
    lk = P(MOL, trim(0.15, '#2a2a30'), bevel=0.1)
    for i in range(7):
        x = -4.2 + i * 1.2
        y = 12.3 + 1.6 * math.sin(math.pi * i / 6)
        bs.append(box('body', x, y, -3.1, 1.2, 0.6 if i % 2 else 1.0, 0.6 if i % 2 else 0.25, lk))
    return bs


@concept('r6b', 6, 'Б', 'Рыцарь: готические латы с шипами, рогатый шлем')
def r6b():
    plate = P(MO, trim(0.4, MOL), stripe(('north',), 0.0, 5.0, 0.3, MOL))
    sp = P(MOL, rect('all', -1, 99, -1, 0.8, '#a0a0a8'), bevel=0.1)
    bs = chest(P(MO, trim(0.45, MOL), rect(('north',), 4.7, 5.3, 0, 99, MOL))) + upper_arm(P(MO, trim(0.35, MOL))) + vambrace(plate)
    bs += pauldron(P(MO, trim(0.45, MOL)), tilt=-18, big=1.3) + belt(P(MOD)) + thigh(P(MOD, trim(0.3, MOL))) + boot(plate, toe=0.7)
    for (z, L) in ((-2.4, 3.0), (0.0, 3.8), (2.4, 3.0)):
        bs += sym(rod('rightArm', (-4.4, -4.4, z), L, 0.9, sp, rz=-20))
    bs += sym(rod('rightArm', (-3.7, 7.0, 0), 2.4, 0.7, sp, rz=-90), rod('rightLeg', (0, 6.0, -2.9), 2.2, 0.8, sp, rx=-90))
    bs += helm(P(MO, trim(0.45, MOL), rect(('north',), 2.0, 8.0, 4.3, 4.9, '#000000'), rect(('north',), 2.8, 4.0, 4.4, 4.8, GLOWR, glow=True),
                 rect(('north',), 6.0, 7.2, 4.4, 4.8, GLOWR, glow=True)))
    bs += sym(*chain('head', (-4.6, -7.0, -0.5), ((3.2, -10, -80), (3.2, -15, -45), (3.0, -20, -10), (2.4, -30, 20)), 1.8, None, taper=0.74,
                     colors=('#161618', BONE)))
    return bs


@concept('r6c', 6, 'В', 'Дух мира: костяной доспех, рогатый череп')
def r6c():
    dark = P(MOD, trim(0.3, '#000000'))
    bn = P(BONE, mottle('#b8ae94', 1.4, 0.7), bevel=0.25)
    bs = chest(dark) + upper_arm(dark) + vambrace(dark) + belt(P('#0a0a0c')) + thigh(dark) + boot(P(MOD), toe=0.3)
    for i, y in enumerate((0.6, 2.4, 4.2, 6.0, 7.8)):
        bs += sym(box('body', -5.2, y, -3.6, 4.6, 1.0, 1.0, bn, rot=(0, -14, 6), pivot=(-0.6, y, -3.4)))
    bs += [box('body', -0.6, 0.0, -3.9, 1.2, 9.0, 1.0, bn)]
    bs += [box('body', -0.8, -0.6 + i * 1.9, 3.0, 1.6, 1.4, 1.2, bn) for i in range(7)]
    bs += sym(box('rightArm', -4.9, -4.0, -3.4, 5.8, 1.6, 6.8, bn, rot=(0, 0, -12), pivot=(-1, -3, 0)))
    bs += sym(box('rightLeg', -0.5, 0.5, -3.2, 1.0, 6.4, 1.0, bn), box('rightLeg', -1.0, 0.0, -3.4, 2.0, 1.0, 1.4, bn))
    bs += helm(P(BONE, mottle('#b8ae94', 1.4, 0.7), rect(('north',), 1.4, 3.8, 3.4, 5.6, '#0a0606'), rect(('north',), 6.2, 8.6, 3.4, 5.6, '#0a0606'),
                 rect(('north',), 2.2, 3.0, 4.2, 4.9, GLOWR, glow=True), rect(('north',), 7.0, 7.8, 4.2, 4.9, GLOWR, glow=True),
                 rect(('north',), 4.5, 5.5, 5.8, 6.8, '#0a0606'), rect(('north',), 2.6, 7.4, 7.2, 8.8, '#0a0606'),
                 *[rect(('north',), u, u + 0.7, 7.2, 8.2, '#efe8d4') for u in (2.9, 4.1, 5.3, 6.5)], bevel=0.2))
    bs += sym(*chain('head', (-5.0, -6.5, 1.0), ((3.0, 30, -95), (3.0, 60, -110), (2.6, 95, -120), (2.0, 130, -100)), 1.8, None, taper=0.78,
                     colors=(BONE, '#8a8070')))
    return bs


# ---- R7 Галит
HA, HAD, HAM, HAL, BLK, GLOWH = '#50e010', '#1e4a0a', '#3a8a10', '#c0ff80', '#141a14', '#9aff40'


@concept('r7a', 7, 'А', 'Путник: плащ из зелёного стекла, корона из игл')
def r7a():
    lea = P(HAD, trim(0.35, '#0e2405'))
    bs = chest(P(HAD, trim(0.4, HA))) + upper_arm(lea) + vambrace(P(HAD, trim(0.3, HA))) + belt(P('#0e2405')) + thigh(P('#183a0a'))
    bs += boot(P(HAD, trim(0.35, HA)), toe=0.3)
    bs += hood(P(HAM, trim(0.35, HAD)), shadow='#020a02', capelet=P(HAM, trim(0.35, HA)))
    bs += cloak(P('#46c018', stripe(('south', 'north'), 0.45, 1.0, 0.7, '#d8ffb0'), facets('#ffffff', 0.18), trim(0.4, HAL, glow=True), noise=0.02, bevel=0.1),
                length=21, flare=11, thick=1.0)
    nd = P(HAL, glow=True, bevel=0.1)
    for a in (-55, -35, -15, 0, 15, 35, 55):
        bs.append(rod('head', (0, -9.6, 1.2), 3.0 + (2.2 if a == 0 else 1.2 if abs(a) == 15 else 0.4), 0.55, nd, rz=a, rx=-8))
    return bs


@concept('r7b', 7, 'Б', 'Рыцарь: латы, из которых растут кристаллы галита')
def r7b():
    plate = P(HAD, trim(0.45, HA))
    bs = chest(P(HAD, trim(0.45, HA), rect(('north',), 4.6, 5.4, 0, 99, HA, glow=True))) + upper_arm(P(HAD, trim(0.35, HAM))) + vambrace(plate)
    bs += pauldron(P(HAD, trim(0.45, HA)), tilt=-14, big=1.0) + belt(P(BLK)) + thigh(P(HAD, trim(0.3, HAM))) + boot(plate, toe=0.6)
    bs += helm(P(HAD, trim(0.45, HA), rect(('north',), 1.8, 8.2, 4.2, 4.9, GLOWH, glow=True)))
    for (x, z, L, rx, rz) in ((-3.4, -0.6, 4.4, -10, -25), (-2.0, 1.6, 3.4, 20, -10), (-4.6, 1.0, 2.6, 5, -55)):
        bs += sym(crystal('rightArm', (x, -4.0, z), L, 1.2, HA, rx=rx, rz=rz, tipc='#e8ffd0'))
    for (x, y, L, rx, rz) in ((-1.8, 1.6, 4.6, 60, -12), (1.6, 2.4, 3.8, 58, 18), (0, 6.0, 2.8, 70, 0)):
        bs += [crystal('body', (x, y, 3.0), L, 1.3, HA, rx=rx, rz=rz, tipc='#e8ffd0')]
    for (x, L, rz) in ((-2.2, 3.0, -18), (0.0, 4.0, 0), (2.2, 3.0, 18)):
        bs += [crystal('head', (x, -9.0, 0.4), L, 1.0, HA, rz=rz, tipc='#e8ffd0')]
    return bs


@concept('r7c', 7, 'В', 'Дух мира: тёмный каркас со светящимися жилами, парящие осколки')
def r7c():
    fr = P(BLK, veins(GLOWH, scale=0.9, width=0.2), trim(0.3, '#000000'))
    bs = chest(fr) + upper_arm(fr) + vambrace(fr) + belt(P('#0a0e0a')) + thigh(fr) + boot(fr, toe=0.4)
    bs += pauldron(P(BLK, veins(GLOWH, scale=0.9, width=0.2), trim(0.35, HAM)), tilt=-12, big=0.6)
    bs += helm(P(BLK, veins(GLOWH, scale=0.9, width=0.2), rect(('north',), 1.0, 9.0, 4.3, 4.9, GLOWH, glow=True)))
    rnd = np.random.RandomState(11)
    for side in (-1, 1):
        for i in range(4):
            a = rnd.uniform(-1.0, 1.0)
            x = side * (10.5 + rnd.uniform(-0.8, 1.2))
            y = -2.5 + i * 2.6 + rnd.uniform(-0.6, 0.6)
            z = rnd.uniform(-3.5, 3.5)
            bs.append(crystal('body', (x, y, z), rnd.uniform(1.6, 2.8), rnd.uniform(0.6, 1.0), HA, rx=rnd.uniform(-60, 60), rz=rnd.uniform(-60, 60),
                              ry=rnd.uniform(0, 90), tipc='#e8ffd0'))
    return bs


# ------------------------------------------------------------------ рендер

POSE = {'rightArm': (-4, 0, 9), 'leftArm': (4, 0, -9), 'rightLeg': (-3, 0, 2.5), 'leftLeg': (3, 0, -2.5)}
TIER_BG = {1: '#2a1a12', 2: '#2a1410', 3: '#0e1a30', 4: '#22102c', 5: '#1c2230', 6: '#3a3a46', 7: '#0e1e0c'}


def render(tris, tex, W, H, scale, center, yaw, pitch):
    img = np.zeros((H, W, 3))
    zb = np.full((H, W), np.inf)
    glow = np.zeros((H, W))
    R = mk.rot_matrix(pitch, 0, 0) @ mk.rot_matrix(0, yaw, 0)
    th, tw = tex.shape[:2]
    for v, uv, n in tris:
        p = (np.asarray(v) - center) @ R.T
        nn = R @ n
        if nn[2] > 1e-6:
            continue
        sx = p[:, 0] * scale + W / 2
        sy = p[:, 1] * scale + H / 2
        x0, x1 = int(max(0, math.floor(sx.min()))), int(min(W - 1, math.ceil(sx.max())))
        y0, y1 = int(max(0, math.floor(sy.min()))), int(min(H - 1, math.ceil(sy.max())))
        if x0 > x1 or y0 > y1:
            continue
        X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        d = (sy[1] - sy[2]) * (sx[0] - sx[2]) + (sx[2] - sx[1]) * (sy[0] - sy[2])
        if abs(d) < 1e-9:
            continue
        a = ((sy[1] - sy[2]) * (X - sx[2]) + (sx[2] - sx[1]) * (Y - sy[2])) / d
        b = ((sy[2] - sy[0]) * (X - sx[2]) + (sx[0] - sx[2]) * (Y - sy[2])) / d
        c = 1 - a - b
        m = (a >= -1e-6) & (b >= -1e-6) & (c >= -1e-6)
        if not m.any():
            continue
        z = a * p[0, 2] + b * p[1, 2] + c * p[2, 2]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        m &= z < sub
        if not m.any():
            continue
        u = a * uv[0, 0] + b * uv[1, 0] + c * uv[2, 0]
        w = a * uv[0, 1] + b * uv[1, 1] + c * uv[2, 1]
        tx = np.clip((u * tw).astype(int), 0, tw - 1)
        ty = np.clip((w * th).astype(int), 0, th - 1)
        col = tex[ty, tx, :3] / 255.0
        al = tex[ty, tx, 3]
        m &= al > 25
        sub[m] = z[m]
        em = al < 200
        cc = np.where(em[..., None], np.minimum(1.0, col * 1.12), col * mk.shade(nn))
        reg = img[y0:y1 + 1, x0:x1 + 1]
        reg[m] = cc[m]
        gl = glow[y0:y1 + 1, x0:x1 + 1]
        gl[m] = em[m]
    return img, np.isfinite(zb), glow


def view(model, yaw, pitch, bg, W=780, H=1040, scale=22.5, center=(0, 5.4, 0)):
    tris = mk.scene_tris(model.quads(), POSE)
    img, mask, glow = render(tris, model.img, W, H, scale, np.array(center, float), yaw, pitch)
    top, bot = C(bg) * 1.5, C(bg) * 0.6
    t = np.linspace(0, 1, H)[:, None, None]
    canvas = top * (1 - t) + bot * t
    canvas = np.broadcast_to(canvas, (H, W, 3)).copy()
    # тень на земле
    fy = (24.4 - center[1]) * scale * math.cos(math.radians(pitch)) + H / 2
    yy, xx = np.mgrid[0:H, 0:W]
    e = ((xx - W / 2) / (W * 0.24)) ** 2 + ((yy - fy) / (W * 0.05)) ** 2
    canvas *= (1 - 0.55 * np.clip(1 - e, 0, 1))[..., None]
    canvas[mask] = img[mask]
    gimg = Image.fromarray((np.clip(img * glow[..., None], 0, 1) * 255).astype(np.uint8))
    bloom = np.asarray(gimg.filter(ImageFilter.GaussianBlur(14)), float) / 255.0
    bloom2 = np.asarray(gimg.filter(ImageFilter.GaussianBlur(4)), float) / 255.0
    out = np.clip(canvas + 0.9 * bloom + 0.35 * bloom2, 0, 1)
    im = Image.fromarray((out * 255).astype(np.uint8))
    return im.resize((W // 2, H // 2), Image.LANCZOS)


def build(key):
    tier, line, title, fn = CONCEPTS[key]
    model = mk.Model(key, mannequin() + fn(), atlas_width=1024)
    bg = TIER_BG[tier]
    front = view(model, -32, 10, bg)
    back = view(model, 148, 10, bg)
    sheet = Image.new('RGB', (front.width * 2, front.height))
    sheet.paste(front, (0, 0))
    sheet.paste(back, (front.width, 0))
    path = os.path.join(OUT, key + '.png')
    sheet.save(path, optimize=True)
    print(key, title, '->', path, 'коробок', len(model.boxes))


if __name__ == '__main__':
    keys = sys.argv[1:] or list(CONCEPTS)
    for k in keys:
        build(k)
