"""Превью концептов дропа мобов: строка на материал - нынешний значок и три варианта. Запуск: python3 mob_drops_preview.py <png>"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import acc_kit as K  # noqa: E402
import mob_drops as M  # noqa: E402

TEX = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../src/main/resources/assets/divinerpgextended/textures/items')
Z = 6
cell = 16 * Z + 8
W = Image.new('RGBA', (cell * 4 + 8, cell * len(M.DROPS) + 8), (34, 30, 38, 255))
for n, (key, name, mob, world, use, vs) in enumerate(M.DROPS):
    now = Image.open(os.path.join(TEX, 'twilight_mat_%s.png' % key)).convert('RGBA').resize((16 * Z, 16 * Z), Image.NEAREST)
    W.paste(now, (8, 8 + n * cell), now)
    for j, (label, fn) in enumerate(vs):
        im = Image.fromarray(K.icon(fn), 'RGBA').resize((16 * Z, 16 * Z), Image.NEAREST)
        W.paste(im, (8 + (j + 1) * cell, 8 + n * cell), im)
W.save(sys.argv[1])
print('ok', len(M.DROPS))
