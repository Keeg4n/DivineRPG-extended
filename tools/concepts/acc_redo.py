"""Переделка 09.11 (запуск: python3 acc_redo.py <папка>; потом acc_redo_html.py <папка>): алтарь логова джунглей (3 новых), посадка шарфа 22А на обеих бронях, ковёр 28 (сейчас, А и 2 новых)."""
import os
import sys

import numpy as np
from PIL import Image

C = '/root/mc/dre/tools/concepts'
sys.path.insert(0, C)
import acc_kit as K  # noqa: E402
import wconcepts as w  # noqa: E402
import acc_wear as W  # noqa: E402
import acc_altars as AL  # noqa: E402
from acc_kit import DISC, ELL, RING, RECT, POLY, LINE, PATH, DIAMOND, Ic, cube  # noqa: E402
from acc_pal import *  # noqa: E402,F401,F403
import acc_items1  # noqa: E402
import acc_items2  # noqa: E402,F401

OUT = (sys.argv[1] if len(sys.argv) > 1 else '/tmp/acc_redo') + '/img'
os.makedirs(OUT, exist_ok=True)
T = AL.T

# ------------------------------------------------------------------ алтарь логова джунглей


def jaws(c):
    """Пасть драмкрикса: глаза и зубы."""
    c.c(RECT(3, 4, 5, 5), '#4cf07a').c(RECT(10, 4, 12, 5), '#4cf07a')
    c.d('#d8ffe0', 3, 4, 10, 4)
    for x in range(3, 13, 2):
        c.d('#efe8d6', x, 10).d('#efe8d6', x + 1, 13)


def emerald_veins():
    return PATH((0, 3), (3, 5), (5, 4), (8, 7), (7, 10), (10, 12), (15, 11)) | PATH((12, 0), (11, 3), (13, 6), (15, 6)) | PATH((2, 15), (4, 12), (7, 10))


def steps_top():
    t = T('cobblestone_mossy').copy()
    t = AL.frame(T('stonebrick'), t, 2)
    t = AL.frame(T('stonebrick_carved'), t, 4)
    return AL.inset(t, T('emerald_block'), 6)


ALT6 = [
    ('Изумруд в храмовом камне', 'резной каменный кирпич, изумрудный блок, мох сверху',
     lambda: AL.drip(AL.inset(T('stonebrick_carved'), T('emerald_block'), 4), T('stonebrick_mossy'), 3, (2, 3, 4, 2, 3, 1, 2, 3, 4, 3, 2, 1, 3, 4, 2, 3)),
     lambda: AL.inset(AL.frame(T('stonebrick_mossy'), T('stonebrick_carved'), 2), T('emerald_block'), 5)),
    ('Ступени храма', 'мшистый булыжник, каменный кирпич, резной кирпич, изумруд',
     lambda: AL.draw(AL.band(AL.band(T('stonebrick'), T('cobblestone_mossy'), 11, 15), T('stonebrick_carved'), 0, 3),
                     lambda c: (c.f(DIAMOND(8, 7, 2.6), GRN), c.d(GRN[4], 7, 6))),
     steps_top),
    ('Изумрудные жилы', 'каменный кирпич, изумрудный блок, лианы',
     lambda: AL.over(AL.mask(T('stonebrick'), T('emerald_block'), emerald_veins()), _vine_low()),
     lambda: AL.mask(T('stonebrick'), T('emerald_block'), RING(8, 8, 6, 1.6) | emerald_veins())),
]


def _vine_low():
    """Лианы только внизу грани."""
    v = T('vine').copy()
    v[:9, :, 3] = 0
    return v


# ------------------------------------------------------------------ шарф: посадка по огибающей брони на всей высоте воротника


def span_env(a, y0, y1, step=.25):
    """Огибающая тела на всём отрезке высот: самые выступающие грани (броня на плечах и вороте тоже)."""
    xs0, xs1, zs0, zs1 = [], [], [], []
    y = y0
    while y <= y1 + 1e-6:
        x0, x1, z0, z1 = a.env('body', y, zlim=(-9, 3.4))
        xs0.append(x0)
        xs1.append(x1)
        zs0.append(min(W.chest_z(a, y), z0))
        zs1.append(max(W.back_z(a, y), z1))
        y += step
    return min(xs0), max(xs1), min(zs0), max(zs1)


def scarf_fit(a, c1, c2, tail='front', h=1.7):
    heads = [hi[1] for b, lo, hi in a.surface if b == 'head']
    # чуть заходит под край шлема, но не выше низа головы (y=0): у Странника шлем кончается выше подбородка, и воротник
    # на высоте шлема прятала сама голова
    top = max(-.1, max(heads + [-1.7]) - .15)
    x0, x1, z0, z1 = span_env(a, top, top + h)
    x = max(-x0, x1, 4.3) + .25
    z0, z1 = z0 - .3, max(z1, 2.3) + .3
    out = cube('body', -x, top, z0, x * 2, h, z1 - z0, c1)
    for i in range(0, 6, 2):  # белые полосы спереди и сзади
        px = -x + (i + .5) * x * 2 / 6 - .35
        out += cube('body', px, top - .03, z0 - .03, .9, h + .06, .1, c2)
        out += cube('body', px, top - .03, z1 - .07, .9, h + .06, .1, c2)
    y = top + h
    for i in range(5):
        col = c1 if i % 2 == 0 else c2
        yy = y + i * 1.1
        if tail == 'front':  # хвост по груди - поверх нагрудника на своей высоте
            zf = W.chest_z(a, yy + .5) - .35
            out += cube('body', -2.9, yy, min(zf, z0 + .1), 2.2, 1.1, .45, col)
        elif tail == 'back':  # закинут за плечо - по спине
            zb = max(W.back_z(a, yy + .5), 2.2) + .05
            out += cube('body', .8, yy, max(zb, z1 - .55), 2.2, 1.1, .45, col)
        elif tail == 'two' and i < 3:  # два коротких хвоста узлом сбоку
            zf = W.chest_z(a, yy + .5) - .35
            out += cube('body', -x + .2, yy, min(zf, z0 + .1), 1.6, 1.1, .45, col)
            out += cube('body', -x + 2.0, yy + .4, min(zf, z0 + .1), 1.6, 1.1, .45, c2 if col == c1 else c1)
    if tail == 'two':
        out += cube('body', -x - .2, y - .3, z0 - .2, 1.8, 1.4, 1.2, c1)  # узел
    return out


FITS = [('Хвост по груди (как было, но поверх брони)', 'front'), ('Хвост закинут за спину', 'back'), ('Узел сбоку, два коротких хвоста', 'two')]

# ------------------------------------------------------------------ ковёр


def carpet_wave(c):
    c.f(POLY((1, 5), (5, 3), (10, 5), (14, 3), (15, 4), (15, 11), (10, 13), (5, 11), (1, 13), (0, 12), (0, 6)), RED)
    c.c(PATH((1, 6), (5, 4), (10, 6), (14, 4)), GOLD[3]).c(PATH((1, 11), (5, 9), (10, 11), (14, 9)), GOLD[3])
    c.f(DIAMOND(7, 8, 2.2), LAP)
    c.d(GOLD[3], 0, 13, 15, 12)


def carpet_stars(c):
    """Ночной: тёмно-синий, золотая кайма и звёзды, кисти по краям."""
    c.f(RECT(1, 3, 14, 12), LAP)
    c.c(RECT(1, 3, 14, 3) | RECT(1, 12, 14, 12) | RECT(1, 3, 1, 12) | RECT(14, 3, 14, 12), GOLD[2])
    c.d(GOLD[4], 4, 6, 9, 5, 11, 9, 6, 10, 12, 6)
    c.d(GOLD[3], 7, 8, 8, 7, 8, 9, 9, 8).d('#fff4c0', 8, 8)
    for x in (0, 15):
        for y in (4, 6, 8, 10, 11):
            c.d(GOLD[3], x, y)


def current_icon():
    im = Image.open('/root/mc/dre/src/main/resources/assets/divinerpgextended/textures/items/trinket_magic_carpet.png').convert('RGBA')
    return np.asarray(im.resize((16, 16), Image.NEAREST)).copy()


def main():
    dv, wa = K.divine_armor(w.POSE), K.wanderer_armor(w.POSE)
    # алтари
    for j, (name, blocks, side, top) in enumerate(ALT6):
        s, t = side(), top()
        AL.block_render(s, t).save(OUT + '/alt6_%d.jpg' % j, quality=92)
        AL.flat_tex(s, OUT + '/alt6_%d_s.png' % j)
        AL.flat_tex(t, OUT + '/alt6_%d_t.png' % j)
    # шарф 22А: значок как был; посадки
    items = {it[0]: it for it in acc_items1.ITEMS}
    sc = items['warm_scarf'][3][0]
    K.save_icon(K.icon(sc[1]), OUT + '/scarf_icon.png', 6)
    old = sc[2]
    K.shot(wa, old(wa, None), 'chest', 'front', w.POSE).save(OUT + '/scarf_old_w.jpg', quality=90)
    for j, (label, kind) in enumerate(FITS):
        for an, arm in (('d', dv), ('w', wa)):
            parts = scarf_fit(arm, RED[2], WHITE[3], kind)
            K.shot(arm, parts, 'chest', 'front', w.POSE).save(OUT + '/scarf_%d_%s.jpg' % (j, an), quality=90)
            K.shot(arm, parts, 'chest', 'back' if kind == 'back' else 'side', w.POSE).save(OUT + '/scarf_%d_%s2.jpg' % (j, an), quality=90)
    # ковёр: сейчас, А и два новых
    cp = items['magic_carpet'][3][0]
    icons = [('Сейчас в игре', current_icon()), ('А. Алый с золотом', K.icon(cp[1])), ('Г. Волна в полёте', K.icon(carpet_wave)), ('Д. Ночной со звёздами', K.icon(carpet_stars))]
    for j, (label, ic) in enumerate(icons):
        K.save_icon(ic, OUT + '/carpet_%d.png' % j, 6)
        for an, arm in (('d', dv), ('w', wa)):
            K.shot(arm, W.carpet(arm, ic), 'feet', 'high', w.POSE).save(OUT + '/carpet_%d_%s.jpg' % (j, an), quality=90)
    print('ok')


if __name__ == '__main__':
    main()
