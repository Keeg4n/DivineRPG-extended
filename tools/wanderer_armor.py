"""Облики брони Странника для игры: концепт ступени (tools/concepts) -> меш, атлас, маска свечения и иконки частей.

Ступень -> концепт: R1 освящённая (капюшон), R2 лепестки и нимб, R3 деревянные латы, R4 орихалковая (капюшон),
R5 серый плащ, R6 тень в капюшоне, R7 хлорофитовая (капюшон). Коробки делятся по частям брони, как у костюма H.E.V.:
helmet - всё на голове, chest - торс и руки, legs - пояс и бёдра (на теле - ниже y=9), boots - голени ниже y=6.5.
Светящиеся тексели (альфа 0.6 в атласе концепта) уходят в отдельную маску r<N>_glow.png: в игре её рисуют вторым
проходом на полной яркости поверх того же меша.

Запуск: python3 tools/wanderer_armor.py  (из корня репо). Пишет в src/main/resources/assets/divinerpgextended.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'concepts'))

import meshkit as mk  # noqa: E402
import wconcepts as w  # noqa: E402
import wterraria  # noqa: E402,F401 (регистрирует концепты всех раундов)

RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'divinerpgextended')
TIERS = {1: 'r1tk', 2: 'r2c', 3: 'r3b', 4: 'r4tk', 5: 'r5i', 6: 'r6a', 7: 'r7tk'}
SLOTS = ('helmet', 'chest', 'legs', 'boots')  # порядок = armorType вещи
K = 2.0  # текселей на единицу модели (у ванильной брони 1)
ATLAS_W = 512
ICON = 32


def slot(b):
    if b.bone == 'head':
        return 'helmet'
    if b.bone in ('rightArm', 'leftArm'):
        return 'chest'
    if b.bone == 'body':
        return 'legs' if b.p0[1] >= 9.0 else 'chest'
    return 'boots' if b.p0[1] >= 6.5 else 'legs'


def build_model(tier):
    boxes = w.CONCEPTS[TIERS[tier]][3]()
    for b in boxes:
        b.piece = slot(b)
        b.k = K
    return mk.Model('wanderer_r%d' % tier, boxes, atlas_width=ATLAS_W)


def icon(model, piece, path):
    """Значок части: вид 3/4 спереди, подогнан под квадрат, сглажен до ICON."""
    quads = [q for q in model.quads() if q[0] == piece]
    # ноги и руки чуть врозь, чтобы на значке читались две штанины / два сапога / рукава
    tris = mk.scene_tris(quads, {'rightLeg': (0, 0, 9), 'leftLeg': (0, 0, -9), 'rightArm': (0, 0, 12), 'leftArm': (0, 0, -12)})
    pts = np.concatenate([np.asarray(v) for v, _, _ in tris])
    yaw, pitch = -20, 10
    R = mk.rot_matrix(pitch, 0, 0) @ mk.rot_matrix(0, yaw, 0)
    pr = pts @ R.T
    lo, hi = pr.min(0), pr.max(0)
    S = 256
    scale = S * 0.9 / max(hi[0] - lo[0], hi[1] - lo[1])
    center = ((lo + hi) / 2) @ R  # обратно в координаты сцены (R ортогональна)
    img, mask, glow = w.render(tris, model.img, S, S, scale, center, yaw, pitch)
    rgba = np.concatenate([np.clip(img, 0, 1), mask[..., None].astype(float)], -1)
    pre = rgba.copy()
    pre[..., :3] *= pre[..., 3:4]
    small = np.asarray(Image.fromarray((pre * 255).astype(np.uint8), 'RGBA').resize((ICON, ICON), Image.LANCZOS), float) / 255.0
    a = small[..., 3:4]
    col = np.where(a > 1e-3, small[..., :3] / np.maximum(a, 1e-3), 0)
    out = np.concatenate([np.clip(col, 0, 1), (a > 0.35).astype(float)], -1)  # жёсткий край, как у ванильных значков
    Image.fromarray((out * 255).astype(np.uint8), 'RGBA').save(path)


def main():
    mesh_dir = os.path.join(RES, 'models', 'wanderer')
    tex_dir = os.path.join(RES, 'textures', 'models', 'wanderer')
    icon_dir = os.path.join(RES, 'textures', 'items', 'wanderer')
    for d in (mesh_dir, tex_dir, icon_dir):
        os.makedirs(d, exist_ok=True)
    for tier in sorted(TIERS):
        m = build_model(tier)
        a = m.img[..., 3]
        glow = (a > 25) & (a < 200)
        base = m.img.copy()
        base[..., 3] = np.where(a > 25, 255, 0)
        m.save(os.path.join(tex_dir, 'r%d.png' % tier), os.path.join(mesh_dir, 'r%d.mesh' % tier))
        Image.fromarray(base, 'RGBA').save(os.path.join(tex_dir, 'r%d.png' % tier))
        gpath = os.path.join(tex_dir, 'r%d_glow.png' % tier)
        if glow.any():
            g = m.img.copy()
            g[..., 3] = np.where(glow, 255, 0)
            Image.fromarray(g, 'RGBA').save(gpath)
        elif os.path.exists(gpath):
            os.remove(gpath)
        pieces = sorted({b.piece for b in m.boxes})
        for s in SLOTS:
            icon(m, s, os.path.join(icon_dir, 'r%d_%s.png' % (tier, s)))
        print('R%d %s: коробок %d, атлас %dx%d, свечение %s, части %s' % (tier, TIERS[tier], len(m.boxes), m.W, m.H,
                                                                     'да' if glow.any() else 'нет', ','.join(pieces)))


if __name__ == '__main__':
    main()
