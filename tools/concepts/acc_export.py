"""Выбранные варианты аксессуаров и алтарей -> в мод (запуск в WSL: python3 acc_export.py).

Выбор тестера 09.11 и 10.11: буквы по PDF "Аксессуары, лут, алтари и часы - концепты" (А=0, Б=1, В=2); шарф 22 - посадка А1 из
переделки (хвост по груди поверх брони), алтарь 6 - Г из переделки; ковёр 28 не трогаем (остаётся как в моде).

Пишет:
- textures/items/trinket_<ключ>.png - значки 16x16;
- wear/accessories.txt - посадка на теле: для каждого профиля брони (без брони, обычная броня, 11 обликов Странника) детали
  на костях ModelBiped: коробки (c) и плоские значки с толщиной (f) со своими спрайтами (s). Рисует client/AccessoryWear;
- textures/blocks/altar/<имя>_side.png и _top.png - грани алтарей призыва (из ванильных текстур и своего узора).
"""
import hashlib
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import acc_kit as K  # noqa: E402
import acc_wear as W  # noqa: E402
import acc_items1  # noqa: E402
import acc_items2  # noqa: E402,F401
import acc_altars as AL  # noqa: E402
import acc_redo as RD  # noqa: E402
import wconcepts as w  # noqa: E402
import wslim5  # noqa: E402,F401
import wr1  # noqa: E402,F401
from acc_pal import RED, WHITE  # noqa: E402

RES = os.path.join(HERE, '../../src/main/resources/assets/divinerpgextended')

PICK = {1: 1, 2: 2, 3: 1, 4: 1, 5: 1, 6: 2, 7: 0, 8: 2, 9: 0, 10: 2, 11: 0, 12: 0, 13: 0, 14: 2, 15: 1, 16: 2, 17: 0, 18: 0, 19: 2, 20: 0,
        21: 0, 22: 'A1', 23: 0, 24: 1, 25: 0, 26: 2, 27: 0, 28: None, 29: 0, 30: 0, 31: 0, 32: 0, 33: 1, 34: 2, 35: 0, 36: 1}
for n in range(37, 48):
    PICK[n] = 2
ALTAR_PICK = [('carcinus', 1), ('glacon', 1), ('throne', 1), ('tomb', 0), ('nest', 2), ('jungle', 'G'), ('arena', 1)]
LOOKS = {'r1': 'r1v', 'r2': 'r2s', 'r3': 'r3v', 'r4': 'r4v', 'r5': 'r5v', 'r6': 'r6v', 'r7': 'r7z',
         'r1_shadow': 'r1t', 'r1_bedrock': 'r1k', 'r1_ender': 'r1o', 'r1_corrupted': 'r1r'}

# ------------------------------------------------------------------ запись деталей вместо граней

SPRITES = {}


def sprite_id(ic):
    a = np.asarray(ic, np.uint8)
    h = hashlib.md5(a.tobytes()).hexdigest()[:10]
    SPRITES.setdefault(h, a)
    return h


def hexcol(c):
    if isinstance(c, str):
        return c.lstrip('#').lower()
    a = np.asarray(c, float)
    if a.max() <= 1.0:
        a = a * 255
    return '%02x%02x%02x' % tuple(int(round(v)) for v in a[:3])


def rec_cube(bone, x, y, z, w_, h, d, color, rot=None, pivot=None):
    if rot is not None and pivot is None:
        pivot = (x + w_ / 2, y + h / 2, z + d / 2)
    return [{'t': 'c', 'bone': bone, 'p': (x, y, z), 's': (w_, h, d), 'col': hexcol(color), 'rot': tuple(rot) if rot else None,
             'piv': tuple(pivot) if rot else None}]


def rec_flat(bone, o, ux, uy, ic, px, t=.25):
    ux, uy = np.array(ux, float), np.array(uy, float)
    return [{'t': 'f', 'bone': bone, 'o': tuple(np.array(o, float)), 'ux': tuple(ux / np.linalg.norm(ux)), 'uy': tuple(uy / np.linalg.norm(uy)),
             'sp': sprite_id(ic), 'px': px, 'th': t}]


MB = {'rightArm': 'leftArm', 'leftArm': 'rightArm', 'rightLeg': 'leftLeg', 'leftLeg': 'rightLeg'}


def rec_sym(parts):
    out = []
    for r in parts:
        r = dict(r)
        r['bone'] = MB.get(r['bone'], r['bone'])
        if r['t'] == 'c':
            x, y, z = r['p']
            r['p'] = (-(x + r['s'][0]), y, z)
            if r['rot']:
                rx, ry, rz = r['rot']
                r['rot'] = (rx, -ry, -rz)
                px, py, pz = r['piv']
                r['piv'] = (-px, py, pz)
        else:
            ox, oy, oz = r['o']
            r['o'] = (-ox, oy, oz)
            r['ux'] = (-r['ux'][0], r['ux'][1], r['ux'][2])
            r['uy'] = (-r['uy'][0], r['uy'][1], r['uy'][2])
        out.append(r)
    return out


for mod in (K, W, acc_items1, acc_items2, RD):
    if hasattr(mod, 'cube'):
        mod.cube = rec_cube
    if hasattr(mod, 'flat'):
        mod.flat = rec_flat
    if hasattr(mod, 'sym_x'):
        mod.sym_x = rec_sym

# ------------------------------------------------------------------ профили брони


def bare_armor():
    """Без брони: коробки скина ModelBiped."""
    s = [('head', np.array((-4, -8, -4.)), np.array((4, 0, 4.))), ('body', np.array((-4, 0, -2.)), np.array((4, 12, 2.))),
         ('rightArm', np.array((-3, -2, -2.)), np.array((1, 10, 2.))), ('leftArm', np.array((-1, -2, -2.)), np.array((3, 10, 2.))),
         ('rightLeg', np.array((-2, 0, -2.)), np.array((2, 12, 2.))), ('leftLeg', np.array((-2, 0, -2.)), np.array((2, 12, 2.)))]
    return K.Armor('bare', [], s)


PROFILES = [('bare', bare_armor()), ('divine', K.divine_armor(w.POSE))]
for look, key in sorted(LOOKS.items()):
    K._arm.pop(key, None)
    a = K.wanderer_armor(w.POSE, key)
    PROFILES.append((look, a))


def scarf_a1(a, ic):
    return RD.scarf_fit(a, RED[2], WHITE[3], 'front')


def fmt(v):
    s = ('%.3f' % v).rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s


def main():
    items = acc_items1.ITEMS
    lines = ['# Посадка аксессуаров на теле (tools/concepts/acc_export.py). Координаты - пиксели модели в кости ModelBiped, y вниз.',
             '# s <спрайт> <палитра AARRGGBB через запятую> <256 знаков: индекс палитры, . - пусто>',
             '# a <ключ>; p <профиль>; c <кость> x y z w h d RRGGBB [rx ry rz px py pz]; f <кость> ox oy oz uxx uxy uxz uyx uyy uyz <спрайт> px t']
    body = []
    icons_dir = os.path.join(RES, 'textures/items')
    for n, (key, name, slot, vs) in enumerate(items, 1):
        pick = PICK.get(n)
        if pick is None:
            print('%2d %-18s - не трогаем' % (n, key))
            continue
        if pick == 'A1':
            label, fn, wear = vs[0][0] + ' (посадка А1)', vs[0][1], scarf_a1
        else:
            label, fn, wear = vs[pick][0], vs[pick][1], vs[pick][2]
        ic = K.icon(fn)
        Image.fromarray(ic, 'RGBA').save(os.path.join(icons_dir, 'trinket_%s.png' % key))
        body.append('a %s' % key)
        for prof, arm in PROFILES:
            parts = wear(arm, ic)
            body.append('p %s' % prof)
            for r in parts:
                if r['t'] == 'c':
                    s = 'c %s %s %s %s' % (r['bone'], ' '.join(fmt(v) for v in r['p'] + r['s']), r['col'], '')
                    if r['rot']:
                        s += ' '.join(fmt(v) for v in r['rot'] + r['piv'])
                    body.append(s.rstrip())
                else:
                    body.append('f %s %s %s %s' % (r['bone'], ' '.join(fmt(v) for v in r['o'] + r['ux'] + r['uy']), r['sp'], ' '.join(fmt(v) for v in (r['px'], r['th']))))
        print('%2d %-18s %s' % (n, key, label))
    for h, a in SPRITES.items():
        pal, idx = [], []
        for y in range(16):
            for x in range(16):
                px = a[y, x]
                if px[3] < 128:
                    idx.append('.')
                    continue
                c = '%02x%02x%02x%02x' % (255, px[0], px[1], px[2])
                if c not in pal:
                    pal.append(c)
                idx.append('0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'[pal.index(c)])
        assert len(pal) <= 62, h
        lines.append('s %s %s %s' % (h, ','.join(pal), ''.join(idx)))
    os.makedirs(os.path.join(RES, 'wear'), exist_ok=True)
    open(os.path.join(RES, 'wear/accessories.txt'), 'w', encoding='utf-8').write('\n'.join(lines + body) + '\n')
    sys.path.insert(0, os.path.join(HERE, '..'))
    import relic_icons  # noqa: E402
    relic_icons.append_halo(os.path.join(RES, 'wear/accessories.txt'))  # Солнечный нимб (реликварий Эдема): не из концептов, кольцо над головой
    print('спрайтов', len(SPRITES), 'строк', len(body))
    # алтари
    ad = os.path.join(RES, 'textures/blocks/altar')
    os.makedirs(ad, exist_ok=True)
    for i, (name, pick) in enumerate(ALTAR_PICK):
        if pick == 'G':
            title, _, side, top = RD.ALT6[0]
        else:
            title, _, side, top = AL.ALTARS[i][4][pick]
        for part, fn in (('side', side), ('top', top)):
            arr = np.asarray(fn(), np.uint8)
            Image.fromarray(arr if arr.shape[2] == 4 else arr[..., :3], 'RGBA' if arr.shape[2] == 4 else 'RGB').save(os.path.join(ad, '%s_%s.png' % (name, part)))
        print('алтарь %-9s %s' % (name, title))


if __name__ == '__main__':
    main()
