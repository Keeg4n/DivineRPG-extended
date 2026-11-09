"""Как вещи сидят на теле. Каждая функция: (броня, значок, параметры) -> детали. Посадка считается от поверхности брони: на широкой Divine и на тонкой броне Странника вещь лежит на своём месте."""
import math

import numpy as np

from acc_kit import cube, flat, sym_x


def head_env(a, y=-6.0):
    """Сама коробка шлема на высоте y, без крылышек и шпилей: только большие грани."""
    xs, zs = [], []
    for b, lo, hi in a.surface:
        if b == 'head' and lo[1] - .01 <= y <= hi[1] + .01 and (hi[0] - lo[0] >= 6 or hi[2] - lo[2] >= 6):
            xs += [lo[0], hi[0]]
            zs += [lo[2], hi[2]]
    return (min(xs), max(xs), min(zs), max(zs)) if xs else (-4.5, 4.5, -4.5, 4.5)


def head_top(a):
    tops = [lo[1] for b, lo, hi in a.surface if b == 'head' and hi[0] - lo[0] >= 6 and hi[2] - lo[2] >= 6]
    return min(tops) if tops else -8.5


def chest_z(a, y=3.0):
    return a.env('body', y, xlim=(-1.5, 1.5), zlim=(-9, 0))[2]


def back_z(a, y=3.0):
    return a.env('body', y, xlim=(-1.5, 1.5), zlim=(0, 3.4))[3]


def waist(a, y=10.0):
    x0, x1, z0, z1 = a.env('body', y, zlim=(-9, 3.4))
    return min(x0, -4.2), max(x1, 4.2), min(z0, -2.2), max(z1, 2.2)


def cord(bone, pts, color, t=.45):
    """Шнур из коротких коробок по точкам."""
    out = []
    for (a, b) in zip(pts, pts[1:]):
        a, b = np.array(a, float), np.array(b, float)
        n = int(max(1, np.linalg.norm(b - a) / .6))
        for i in range(n):
            p = a + (b - a) * (i + .5) / n
            out += cube(bone, p[0] - t / 2, p[1] - t / 2, p[2] - t / 2, t, t, t, color)
    return out


def neck_cord(a, y, color, t=.45):
    """Шнур вокруг шеи и вниз на грудь до высоты y."""
    z = chest_z(a, 1.0) - .25
    zb = max(back_z(a, 1.0), 2.2) + .2
    top = -.4
    out = cord('body', [(-3.2, top, z), (-3.2, top, zb), (3.2, top, zb), (3.2, top, z)], color, t)
    zt = chest_z(a, y) - .25
    return out + cord('body', [(-3.2, top, z), (-.3, y, zt)], color, t) + cord('body', [(3.2, top, z), (.3, y, zt)], color, t)


def pendant(a, ic, cord_c='#3a2a1c', y=3.2, px=.3, t=.3):
    """Подвеска, как у Ботании: плоский значок на шнуре; при нагруднике отходит вперёд на толщину брони."""
    z = chest_z(a, y + 2) - .35
    return neck_cord(a, y, cord_c) + flat('body', (0, y + 2.1, z), (1, 0, 0), (0, 1, 0), ic, px, t)


def pin(a, ic, x=-1.9, y=3.0, px=.26, t=.25):
    """Брошь или медаль на груди без шнура."""
    return flat('body', (x, y, chest_z(a, y) - .2), (1, 0, 0), (0, 1, 0), ic, px, t)


def ring(a, band, gem, side='R', big=False):
    """Кольцо у кисти (у Ботании кольца не видны; здесь лента и камень на конце руки)."""
    x0, x1, z0, z1 = (-3.0, 1.0, -2.0, 2.0) if a.name == 'Divine' else a.env('rightArm', 8.0)
    x0, x1, z0, z1 = x0 - .15, x1 + .15, z0 - .15, z1 + .15
    y = 8.2
    out = cube('rightArm', x0, y, z0, x1 - x0, .9, z1 - z0, band)
    s = 1.3 if big else .9
    out += cube('rightArm', (x0 + x1) / 2 - s / 2, y - .2, z0 - .55, s, s + .3, .6, gem)
    return sym_x(out) if side == 'L' else out


def spur(a, ic, px=.3):
    """Вещь на предплечье снаружи: щит, жало."""
    x0 = a.env('rightArm', 6.0)[0]
    return flat('rightArm', (x0 - .25, 5.0, 0), (0, 0, 1), (0, 1, 0), ic, px, .3)


def belt(a, c, trim, buckle, y=9.2, h=2.2, buckle_w=2.6):
    """Пояс как у Ботании (полоса вокруг торса), поверх брони."""
    x0, x1, z0, z1 = waist(a, y + h / 2)
    x0, x1, z0, z1 = x0 - .25, x1 + .25, z0 - .25, z1 + .25
    out = cube('body', x0, y, z0, x1 - x0, h, z1 - z0, c)
    out += cube('body', x0 - .03, y, z0 - .03, x1 - x0 + .06, .4, z1 - z0 + .06, trim)
    out += cube('body', -buckle_w / 2, y - .25, z0 - .35, buckle_w, h + .5, .5, buckle)
    return out


def buckle_icon(a, ic, y=10.3, px=.22):
    z0 = waist(a, y)[2] - .5
    return flat('body', (0, y, z0), (1, 0, 0), (0, 1, 0), ic, px, .25)


def thin_belt(a):
    """Тонкий ремешок, на котором висит вещь."""
    return belt(a, '#4a2c16', '#2a180c', '#9a6c38', y=9.7, h=.9, buckle_w=1.4)


def hip_front(a):
    """Точка спереди бедра: сбоку вещь спрятала бы опущенная рука."""
    x0, x1, z0, z1 = waist(a, 10.5)
    return x0 + 1.5, min(z0, a.env('rightLeg', 1.0)[2]) - .35


def hip(a, ic, side='R', px=.32, y=12.5, t=.45):
    """Вещь на ремешке спереди бедра."""
    x, z = hip_front(a)
    out = flat('body', (x, y, z), (1, 0, 0), (0, 1, 0), ic, px, t)
    return thin_belt(a) + (sym_x(out) if side == 'L' else out)


def back_icon(a, ic, px=.5, x=0.0, y=4.0, rz=0.0, lift=.6, t=.5):
    """Вещь на спине: значок наискось (колчан, панцирь)."""
    ang = math.radians(rz)
    z = back_z(a, y) + lift
    return flat('body', (x, y, z), (-math.cos(ang), -math.sin(ang), 0), (-math.sin(ang), math.cos(ang), 0), ic, px, t)


def wings(a, ic, px=.55, y=3.0, spread=28, gap=.4, lift=.5):
    """Крылья как у тиары Ботании: два плоских значка за спиной, разведены; ic нарисован как правое крыло, корень у левого края значка."""
    z = back_z(a, y) + lift
    out = []
    for s in (-1, 1):
        ang = math.radians(spread)
        ux = np.array((s * math.cos(ang), 0, math.sin(ang)))
        c = np.array((s * gap, y, z)) + ux * (8 * px)
        icn = ic if s > 0 else ic
        out += flat('body', c, ux, (0, 1, 0), icn, px, .22)
    return out


def boot_wing(a, ic, px=.34):
    """Крылышки на сапогах, по одному снаружи каждой ноги."""
    x0 = a.env('rightLeg', 9.0)[0]
    out = flat('rightLeg', (x0 - .25, 8.8, 1.2), (0, 0, 1), (0, 1, 0), ic, px, .2)
    return out + sym_x(out)


def float_at(a, ic, pos=(-8.5, -3.5, -1.5), px=.3, t=.5, face=(1, 0, -.2)):
    """Вещь парит рядом с плечом, как планетка у Ботании."""
    ux = np.array(face, float)
    return flat('body', pos, ux, (0, 1, 0), ic, px, t)


def on_head(a, ic, x=0.0, y=-7.0, px=.28, t=.3):
    """Значок на лбу, спереди шлема."""
    z = head_env(a, y)[2]
    return flat('head', (x, y, z - .2), (1, 0, 0), (0, 1, 0), ic, px, t)


def above_head(a, ic, y=-3.0, px=.32, t=.4):
    """Парит над головой."""
    return flat('head', (0, head_top(a) + y, 0), (1, 0, 0), (0, 1, 0), ic, px, t)


def side_head(a, ic, side='R', y=-4.0, px=.26, t=.3):
    """Значок на виске."""
    x0 = head_env(a, y)[0]
    out = flat('head', (x0 - .2, y, 0), (0, 0, 1), (0, 1, 0), ic, px, t)
    return sym_x(out) if side == 'L' else out


def crown(a, c, tips, gems=None, h=1.2, spikes=2.2, y=None, n=3):
    """Корона из коробок: обруч и зубцы по периметру."""
    x0, x1, z0, z1 = head_env(a, -6.5)
    x0, x1, z0, z1 = x0 - .2, x1 + .2, z0 - .2, z1 + .2
    top = head_top(a) if y is None else y
    y0 = top + .9
    W, D = x1 - x0, z1 - z0
    out = cube('head', x0, y0, z0, W, h, .6, c) + cube('head', x0, y0, z1 - .6, W, h, .6, c)
    out += cube('head', x0, y0, z0, .6, h, D, c) + cube('head', x1 - .6, y0, z0, .6, h, D, c)
    for i in range(n):
        tx = x0 + (i + .5) * W / n - .4
        for z in (z0, z1 - .6):
            out += cube('head', tx, y0 - spikes, z, 1.2, spikes, .6, tips)
        tz = z0 + (i + .5) * D / n - .4
        for x in (x0, x1 - .6):
            out += cube('head', x, y0 - spikes, tz, .6, spikes, 1.2, tips)
    if gems:
        out += cube('head', -.7, y0 + .1, z0 - .25, 1.4, h - .2, .3, gems)
    return out


def circlet(a, c, gem_ic=None, y=-6.2, px=.24):
    """Тонкий обруч с камнем на лбу."""
    x0, x1, z0, z1 = head_env(a, y)
    x0, x1, z0, z1 = x0 - .15, x1 + .15, z0 - .15, z1 + .15
    out = cube('head', x0, y - .4, z0, x1 - x0, .8, z1 - z0, c)
    if gem_ic is not None:
        out += flat('head', (0, y, z0 - .15), (1, 0, 0), (0, 1, 0), gem_ic, px, .25)
    return out


def pauldron(a, c, trim, side='R', big=False, spikes=None):
    """Наплечник из коробок поверх плеча."""
    x0, x1, z0, z1 = (-4.0, 2.0, -3.0, 3.0) if a.name == 'Divine' else a.env('rightArm', -.5)
    grow = .7 if big else .4
    x0, x1, z0, z1 = x0 - grow, (x0 + x1) / 2 + 1.2, z0 - grow * .6, z1 + grow * .6
    top = min(lo[1] for b, lo, hi in a.surface if b == 'rightArm' and hi[0] - lo[0] >= 3) - .4
    h = 3.8 if big else 3.0
    out = cube('rightArm', x0, top, z0, x1 - x0, h, z1 - z0, c)
    out += cube('rightArm', x0 - .05, top + h - .6, z0 - .05, x1 - x0 + .1, .6, z1 - z0 + .1, trim)
    out += cube('rightArm', x0 + .5, top - .8, z0 + .7, x1 - x0 - .9, .9, z1 - z0 - 1.4, c)
    if spikes:
        for i in range(3):
            sz = z0 + (i + .5) * (z1 - z0) / 3 - .4
            out += cube('rightArm', x0 + .6, top - 1.6 + i % 2 * .5, sz, .9, 1.7 - i % 2 * .5, .8, spikes)
    return sym_x(out) if side == 'L' else out


def scarf(a, c1, c2, tail=True, fat=False, fur=None):
    """Шарф: воротник вокруг шеи и хвост на груди."""
    h = 2.3 if fat else 1.6
    # не выше низа головы (y=0): у Странника шлем кончается выше подбородка, и воротник на его высоте прятала голова
    bottom = max([hi[1] for b, lo, hi in a.surface if b == 'head'] + [-1.7])
    top = max(-.1, bottom - .15)
    # огибающая по всей высоте воротника: самая выступающая броня у шеи и на плечах
    xs, z0s, z1s = [], [], []
    for k in range(int(h / .25) + 1):
        y = top + k * .25
        ex = a.env('body', y, zlim=(-9, 3.4))
        xs.append(max(-ex[0], ex[1]))
        z0s.append(min(chest_z(a, y), ex[2]))
        z1s.append(max(back_z(a, y), ex[3]))
    x = max(max(xs), 4.3) + .25
    z0 = min(z0s) - .3
    z1 = max(max(z1s), 2.3) + .3
    col = fur or c1
    out = cube('body', -x, top, z0, x * 2, h, z1 - z0, col)
    for i in range(0, 6, 2):
        px = -x + (i + .5) * x * 2 / 6 - .35
        out += cube('body', px, top - .03, z0 - .03, .9, h + .06, .1, c2 if not fur else '#e8e0d0')
    if tail:
        for i in range(5):
            yy = top + h + i * 1.1
            out += cube('body', -2.9, yy, min(chest_z(a, yy + .5) - .35, z0 + .1), 2.2, 1.1, .5, c1 if i % 2 == 0 else c2)  # поверх нагрудника
    return out


def carpet(a, ic, px=.95, y=24.0, tilt=0.0):
    """Ковёр под ногами: лежит плашмя, значок сверху."""
    return flat('body', (0, y, 0), (1, 0, 0), (0, 0, 1), ic, px, .45)


def flame_feet(a, ic, px=.26):
    """Огоньки у сапог, по одному на ногу, спереди."""
    z = a.env('rightLeg', 10.5)[2]
    out = flat('rightLeg', (0, 9.8, z - .3), (1, 0, 0), (0, 1, 0), ic, px, .2)
    return out + sym_x(out)


def strap(a, c, buckle=None, t=.9):
    """Перевязь наискось через грудь и спину."""
    out = []
    z0 = chest_z(a, 5.0) - .15
    z1 = max(back_z(a, 5.0), 2.2) + .15
    for i in range(12):
        y = i
        x = -3.6 + i * .66
        out += cube('body', x - t / 2, y, min(chest_z(a, y) - .15, z0 + .3), t, 1.0, .2, c)
        out += cube('body', -x - t / 2, y, z1 - .05, t, 1.0, .2, c)
    return out


def lantern(a, frame, fire, y=10.8, side='R', h=3.0):
    """Фонарь из коробок спереди бедра."""
    x, z = hip_front(a)
    z -= 1.1
    out = cube('body', x - .9, y, z - .9, 1.8, h, 1.8, fire)
    out += cube('body', x - 1.05, y - .3, z - 1.05, 2.1, .5, 2.1, frame) + cube('body', x - 1.05, y + h - .2, z - 1.05, 2.1, .5, 2.1, frame)
    for dx, dz in ((-1.05, -1.05), (.75, -1.05), (-1.05, .75), (.75, .75)):
        out += cube('body', x + dx, y, z + dz, .3, h, .3, frame)
    out += cube('body', x - .2, y - 1.0, z - .2, .4, .8, .4, frame)
    return thin_belt(a) + (sym_x(out) if side == 'L' else out)


def box_at_hip(a, c, trim, side='R', w=2.4, h=2.8, d=4.2, y=10.6):
    """Ящик или сумка спереди бедра."""
    x, z = hip_front(a)
    span, deep = d * .8, w * .75
    out = cube('body', x - span / 2, y, z - deep, span, h, deep, c)
    out += cube('body', x - span / 2 - .03, y, z - deep - .03, span + .06, .6, deep + .06, trim)
    return thin_belt(a) + (sym_x(out) if side == 'L' else out)


def quiver(a, c, trim, feather, rz=22.0, n=3):
    """Колчан на спине наискось: труба и оперение стрел."""
    z = back_z(a, 5.0) + .2
    piv = (0, 5, z + 1.1)
    rot = (0, 0, rz)
    out = cube('body', -1.2, -.5, z, 2.4, 10, 2.2, c, rot=rot, pivot=piv)
    out += cube('body', -1.35, -.5, z - .1, 2.7, .8, 2.4, trim, rot=rot, pivot=piv)
    out += cube('body', -1.35, 7.5, z - .1, 2.7, .8, 2.4, trim, rot=rot, pivot=piv)
    for i in range(n):
        out += cube('body', -.9 + i * .8, -3.0 + (i % 2) * .5, z + .7, .35, 3.0, .35, '#7a5a38', rot=rot, pivot=piv)
        out += cube('body', -1.05 + i * .8, -3.6 + (i % 2) * .5, z + .55, .65, 1.2, .65, feather, rot=rot, pivot=piv)
    return out


def scabbard(a, c, trim, hilt, side='R', L=11.0, w=1.2):
    """Ножны на бедре наискось, рукоять вверх."""
    x = waist(a, 10.5)[0] - .8
    piv = (x, 9.0, 0)
    rot = (-38, 0, 0)
    out = cube('body', x - w / 2, 9.0, -w / 2, w, L, w, c, rot=rot, pivot=piv)
    out += cube('body', x - w / 2 - .1, 9.0, -w / 2 - .1, w + .2, .7, w + .2, trim, rot=rot, pivot=piv)
    out += cube('body', x - w / 2 - .1, 9 + L - .9, -w / 2 - .1, w + .2, .9, w + .2, trim, rot=rot, pivot=piv)
    out += cube('body', x - .3, 6.2, -.3, .6, 2.8, .6, hilt, rot=rot, pivot=piv)
    out += cube('body', x - .9, 8.6, -.3, 1.8, .5, .6, trim, rot=rot, pivot=piv)
    return sym_x(out) if side == 'L' else out


def plate_back(a, ic, px=.62, y=4.5, lift=.45, t=.8):
    """Панцирь на спине: крупный значок плашмя."""
    return back_icon(a, ic, px=px, y=y, lift=lift, t=t)


def halfwing(ic):
    """Правая половина парных крыльев значка: корень у левого края, как ждёт wings()."""
    out = np.zeros_like(ic)
    out[:, :8] = ic[:, 8:]
    return out


def side_plate(a, ic, side='R', px=.42):
    """Плоский значок на наплечнике снаружи."""
    x0 = (-4.0 if a.name == 'Divine' else a.env('rightArm', -.5)[0]) - 1.0
    out = flat('rightArm', (x0, -.2, 0), (0, 0, 1), (0, 1, 0), ic, px, .25)
    return sym_x(out) if side == 'L' else out
