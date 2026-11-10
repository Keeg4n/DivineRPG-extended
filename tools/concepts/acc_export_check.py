"""Проверка выгрузки посадки: грани из wear/accessories.txt (собранные заново как в игре) против граней концепта."""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import acc_kit as K  # noqa: E402
import acc_items1  # noqa: E402
import acc_items2  # noqa: E402,F401
import acc_redo as RD  # noqa: E402
import wconcepts as w  # noqa: E402
import wslim5  # noqa: E402,F401
import wr1  # noqa: E402,F401
import meshkit as mk  # noqa: E402
from acc_pal import RED, WHITE  # noqa: E402

DATA = os.path.join(HERE, '../../src/main/resources/assets/divinerpgextended/wear/accessories.txt')
sprites, data = {}, {}
key = prof = None
for line in open(DATA, encoding='utf-8'):
    f = line.split()
    if not f or f[0].startswith('#'):
        continue
    if f[0] == 's':
        pal = [(int(c[2:4], 16), int(c[4:6], 16), int(c[6:8], 16)) for c in f[2].split(',')]
        ic = np.zeros((16, 16, 4), np.uint8)
        for i, ch in enumerate(f[3]):
            if ch != '.':
                ic[i // 16, i % 16] = pal['0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'.index(ch)] + (255,)
        sprites[f[1]] = ic
    elif f[0] == 'a':
        key = f[1]
        data[key] = {}
    elif f[0] == 'p':
        prof = f[1]
        data[key][prof] = []
    else:
        data[key][prof].append(f)


def rebuild(rows):
    out = []
    for f in rows:
        if f[0] == 'c':
            v = [float(x) for x in f[2:8]]
            col = '#' + f[8]
            rot = piv = None
            if len(f) > 9:
                r = [float(x) for x in f[9:15]]
                rot, piv = r[:3], r[3:]
            out += K.cube(f[1], *v, col, rot=rot, pivot=piv)
        else:
            v = [float(x) for x in f[2:11]]
            out += K.flat(f[1], v[0:3], v[3:6], v[6:9], sprites[f[11]], float(f[12]), float(f[13]))
    return out


def deviation(want, got):
    """Наибольшее расхождение координат: грани сгруппированы по кости и цвету, координаты отсортированы (выгрузка округляет до 0.001)."""
    def groups(quads):
        g = {}
        for bone, q, col, n in quads:
            g.setdefault((bone, tuple(np.round(np.asarray(col, float)[:3], 2))), []).append(np.asarray(q, float).ravel())
        return {k: np.sort(np.concatenate(v)) for k, v in g.items()}
    a, b = groups(want), groups(got)
    if a.keys() != b.keys() or any(len(a[k]) != len(b[k]) for k in a):
        return float('inf')
    return max(np.abs(a[k] - b[k]).max() for k in a) if a else 0.0


LOOKS = {'r1': 'r1v', 'r2': 'r2s', 'r3': 'r3v', 'r4': 'r4v', 'r5': 'r5v', 'r6': 'r6v', 'r7': 'r7z',
         'r1_shadow': 'r1t', 'r1_bedrock': 'r1k', 'r1_ender': 'r1o', 'r1_corrupted': 'r1r'}
import acc_export as X  # noqa: E402  (только PICK и профиль без брони; функции записи подменяет у себя, поэтому импорт - после чтения)

arms = {'bare': X.PROFILES[0][1], 'divine': X.PROFILES[1][1]}
for look, k in LOOKS.items():
    arms[look] = dict(X.PROFILES)[look]
# вернуть настоящие функции граней
import importlib  # noqa: E402
for m in ('acc_kit', 'acc_wear', 'acc_items1', 'acc_items2', 'acc_redo'):
    importlib.reload(sys.modules[m])
import acc_kit as K  # noqa: E402,F811
import acc_items1  # noqa: E402,F811
import acc_redo as RD  # noqa: E402,F811
bad = 0
total = 0
worst = 0.0
for n, (k, name, slot, vs) in enumerate(acc_items1.ITEMS, 1):
    pick = X.PICK.get(n)
    if pick is None:
        continue
    for prof, a in arms.items():
        if pick == 'A1':
            want = RD.scarf_fit(a, RED[2], WHITE[3], 'front')
        else:
            want = vs[pick][2](a, K.icon(vs[pick][1]))
        got = rebuild(data[k][prof])
        total += 1
        dev = deviation(want, got)
        worst = max(worst, dev)
        if dev > 0.02:
            bad += 1
            print('РАСХОЖДЕНИЕ', k, prof, len(want), len(got), dev)
print('проверено', total, 'расхождений больше 0.02 пикселя', bad, 'наибольшее', round(worst, 4))

# контрольные суммы граней концепта по костям: автотест мода сверяет с ними геометрию, которую строит Java (WearData)
sums = ['# ключ профиль кость граней сумма_x сумма_y сумма_z (по всем вершинам граней концепта)']
for n, (k, name, slot, vs) in enumerate(acc_items1.ITEMS, 1):
    pick = X.PICK.get(n)
    if pick is None:
        continue
    for prof, a in arms.items():
        want = RD.scarf_fit(a, RED[2], WHITE[3], 'front') if pick == 'A1' else vs[pick][2](a, K.icon(vs[pick][1]))
        by = {}
        for bone, q, col, nn in want:
            c, sx = by.get(bone, (0, np.zeros(3)))
            by[bone] = (c + 1, sx + np.asarray(q, float).sum(axis=0))
        for bone, (c, sx) in sorted(by.items()):
            sums.append('%s %s %s %d %.3f %.3f %.3f' % (k, prof, bone, c, sx[0], sx[1], sx[2]))
NL = chr(10)
open(os.path.join(HERE, '../../src/main/resources/assets/divinerpgextended/wear/accessories_check.txt'), 'w', encoding='utf-8').write(NL.join(sums) + NL)
print('контрольных сумм', len(sums) - 1)

