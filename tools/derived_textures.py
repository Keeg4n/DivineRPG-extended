"""Таблицы производных текстур: наш рисунок = текстура Divine + замена цветов (+ отдельные пиксели).
Пишет assets/divinerpgextended/derived_textures.txt и проверяет точную сборку. Файлы-оригиналы не трогает."""
import os, hashlib
from PIL import Image
import numpy as np

D = '//wsl.localhost/Ubuntu/tmp/dvtex/assets'
O = '//wsl.localhost/Ubuntu/root/mc/dre/src/main/resources/assets'
OUT = O + '/divinerpgextended/derived_textures.txt'
ORIG = 'orig_derived'  # сохранённые наши картинки (из jar они удалены)

OWN_ART = ('spiritSpark_', 'worldSpirit_', 'mortumTrophy_twins')
# если исходник не тот (другая версия DivineRPG): перекраска запасного по палитре
FALLBACK = {'divinerpgextended/textures/items/haliteMultitool.png': ['divinerpg/textures/items/divineShickaxe.png'],
            'divinerpgextended/textures/items/trinket_clarity_crystal.png': ['divinerpg/textures/items/baseSpawnCrystal.png'],
            'divinerpgextended/textures/items/trinket_arcane_ward.png': ['divinerpg/textures/items/arcaniumAttractor.png']}
FORCED = {
    'divinerpg/textures/armor/tormented_1.png': 'divinerpg/textures/armor/tormented_1.png',
    'divinerpg/textures/armor/tormented_2.png': 'divinerpg/textures/armor/tormented_2.png',
    'divinerpgextended/textures/blocks/jungleOre.png': 'divinerpg/textures/blocks/rupeeOre.png',
}


def argb(path):
    a = np.array(Image.open(path).convert('RGBA')).astype(np.uint32)
    v = (a[:, :, 3] << 24) | (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
    v[a[:, :, 3] == 0] = 0
    return v


def md5(v):
    return hashlib.md5(v.astype('>u4').tobytes()).hexdigest()


# кандидаты по маске прозрачности
div = {}
for root, _, files in os.walk(D):
    for f in files:
        if f.endswith('.png'):
            p = os.path.join(root, f)
            try:
                v = argb(p)
            except Exception:
                continue
            m = v != 0
            if m.all() or m.sum() < 9:
                continue
            div.setdefault((v.shape, hashlib.md5(m.tobytes()).hexdigest()), []).append(p)

targets = {}
for root, _, files in os.walk(ORIG):
    for f in files:
        if not f.endswith('.png'):
            continue
        p = os.path.join(root, f)
        rel = os.path.relpath(p, ORIG).replace(os.sep, '/')
        if any(t in f for t in OWN_ART):
            continue
        v = argb(p)
        m = v != 0
        if rel in FORCED:
            targets[rel] = [D + '/' + FORCED[rel]]
            continue
        if m.all():
            continue
        k = (v.shape, hashlib.md5(m.tobytes()).hexdigest())
        if k in div:
            targets[rel] = div[k]


def recipe(ours, src):
    flat_o = ours.reshape(-1)
    flat_s = src.reshape(-1)
    votes = {}
    for s, o in zip(flat_s, flat_o):
        if s == 0:
            continue
        votes.setdefault(int(s), {}).setdefault(int(o), 0)
        votes[int(s)][int(o)] += 1
    cmap = {s: max(c.items(), key=lambda kv: kv[1])[0] for s, c in votes.items()}
    px = {}
    for i, (s, o) in enumerate(zip(flat_s, flat_o)):
        if s == 0:
            if o != 0:
                return None
            continue
        if cmap[int(s)] != int(o):
            px[i] = int(o)
    cmap = {s: d for s, d in cmap.items() if s != d}
    return cmap, px


def build(src, cmap, px):
    flat = src.reshape(-1).copy()
    out = np.zeros_like(flat)
    for i, s in enumerate(flat):
        if s == 0:
            continue
        out[i] = px.get(i, cmap.get(int(s), int(s)))
    return out.reshape(src.shape)


def lum(c):
    return 0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)


def palette(ours):
    # наши цвета (непрозрачные) по яркости с числом пикселей - для перекраски чужой версии текстуры
    vals, counts = np.unique(ours[ours != 0], return_counts=True)
    pairs = sorted(zip(vals.tolist(), counts.tolist()), key=lambda vc: (lum(vc[0]), vc[0]))
    return ','.join('%08xx%d' % (v, c) for v, c in pairs)


lines = ['# Производные текстуры: собираются при запуске из текстур установленного DivineRPG (в jar их нет).',
         '# выход|исходники Divine через ;|md5 результата|md5 исходника|цвет>цвет,...|пиксель>цвет,...|палитра цвет x число по яркости']
total_px = 0
for rel in sorted(targets):
    ours = argb(ORIG + '/' + rel)
    best = None
    for cand in targets[rel]:
        src = argb(cand)
        if src.shape != ours.shape:
            continue
        r = recipe(ours, src)
        if r is None:
            continue
        if best is None or len(r[1]) < len(best[2][1]):
            best = (cand, src, r)
    assert best, rel
    cand, src, (cmap, px) = best
    assert (build(src, cmap, px) == ours).all(), rel
    total_px += len(px)
    dom, path = rel.split('/', 1)
    srcrel = 'assets/' + cand.replace(D + '/', '').replace(D + '\\', '').replace('\\', '/')
    srcs = [srcrel] + ['assets/' + x for x in FALLBACK.get(rel, [])]
    lines.append('%s:%s|%s|%s|%s|%s|%s|%s' % (dom, path, ';'.join(srcs), md5(ours), md5(src),
                                        ','.join('%08x>%08x' % (s, d) for s, d in sorted(cmap.items())),
                                        ','.join('%d>%08x' % (i, c) for i, c in sorted(px.items())), palette(ours)))
    print('%-60s <- %-50s map %3d px %4d' % (rel, srcrel, len(cmap), len(px)))
open(OUT, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')
print('textures', len(targets), 'override pixels', total_px, 'bytes', os.path.getsize(OUT))
open('derived_list.txt', 'w').write('\n'.join(sorted(targets)) + '\n')
