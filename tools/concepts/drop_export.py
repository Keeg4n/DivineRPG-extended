"""Выбранные значки (12.11.2026) - в текстуры игры: материалы мобов сумеречных миров, лут с боссов, Часы Странника (вариант А).

Запуск: python3 drop_export.py [превью.png] - пишет PNG 16x16 в src/main/resources/assets/divinerpgextended/textures/items и, если задано, превью "было/стало".
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import acc_kit as K  # noqa: E402
import acc_loot as L  # noqa: E402
import mob_drops as M  # noqa: E402
from acc_kit import POLY, ELL  # noqa: E402
from acc_pal import RED, ORG, PUMP  # noqa: E402

TEX = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../src/main/resources/assets/divinerpgextended/textures/items')

# материалы мобов: номер в листе - функция значка
MOB = {'sun_feather': M.feather_flame, 'horn_shard': M.horn_spiral, 'greenfeet_root': M.root_gnarled, 'verek_fang': M.fang, 'moon_horn': M.moon_spiral,
       'moss_core': M.moss_rune, 'golem_heart': M.heart_cube, 'dusk_horn': M.dusk_stars, 'warrior_sigil': M.sigil_medal, 'sky_essence': M.essence_crystal,
       'sky_string': M.string_spool, 'cloud_core': M.cloud_cube, 'dark_horn': M.dark_cracked, 'demon_heart': M.demon_flame, 'soul_silk': M.silk_web}


def deep_shell_red(c):
    """Панцирь глубин, вариант Б в красных тонах (Карцинус - краб)."""
    for x in (1.5, 6.5, 11.5):
        c.f(POLY((x, 6), (x + 3, 6), (x + 1.5, 0)), ORG)
    c.f(ELL(8, 10, 7.6, 5), RED)
    for x0, y0, x1, y1 in ((8, 6, 8, 14), (4, 8, 4, 13), (12, 8, 12, 13)):
        c.l(x0, y0, x1, y1, RED[0])
    c.d(PUMP[4], 3, 7, 4, 6)


def loot(key, j):
    for k, name, src, vs in L.LOOT:
        if k == key:
            return vs[j][1]
    raise KeyError(key)


WORLDS = ['eden', 'wildwood', 'apalachia', 'skythern']
OUT = {}  # файл -> функция значка
for key, fn in MOB.items():
    OUT['twilight_mat_%s' % key] = fn
for n, w in enumerate(WORLDS):
    OUT['guardian_heart_%s' % w] = loot('guardian_heart', 1)[n]  # 1Б
    OUT['time_gear_%s' % w] = loot('time_gear', 0)[n]  # 2А
OUT.update({
    'mortumTrophy_demon': loot('trophy_demon', 0),  # 4А
    'mortumTrophy_archer': loot('trophy_archer', 0),  # 5А
    'mortumTrophy_twins': loot('trophy_twins', 0),  # 6А
    'mortumTrophy_fiend': loot('trophy_fiend', 1),  # 7Б
    'mortumTrophy_vamacheron': loot('trophy_vamacheron', 1),  # 8Б
    'mortumTrophy_karot': loot('trophy_karot', 1),  # 9Б
    'deep_shell': deep_shell_red,  # 10Б красный
    'frozenHeart': loot('frozen_heart', 2),  # 11В
    'throneShard': loot('throne_shard', 2),  # 12В
    'watcherEye': loot('watcher_eye_loot', 0),  # 13А
    'dreamItem_lunar_shard': loot('lunar_shard', 0),  # 15А
})
for t in range(1, 6):
    OUT['wanderer_clock_%d' % t] = L.clock_pocket(t)  # часы А

for name, fn in OUT.items():
    Image.fromarray(K.icon(fn), 'RGBA').save(os.path.join(TEX, name + '.png'))
print('значков', len(OUT))

if len(sys.argv) > 1:
    names = list(OUT)
    Z, cell = 5, 16 * 5 + 6
    W = Image.new('RGBA', (cell * 10 + 6, cell * ((len(names) + 9) // 10) + 6), (34, 30, 38, 255))
    for i, name in enumerate(names):
        im = Image.open(os.path.join(TEX, name + '.png')).convert('RGBA').resize((16 * Z, 16 * Z), Image.NEAREST)
        W.paste(im, (6 + (i % 10) * cell, 6 + (i // 10) * cell), im)
    W.save(sys.argv[1])
