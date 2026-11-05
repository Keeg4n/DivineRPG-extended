"""Облики брони Странника для игры: концепт ступени (tools/concepts) -> меш, атлас и маска свечения. Значки частей - tools/wanderer_icons.py.

Ступень -> концепт (пятый раунд, concepts/wslim5.py, пропорции как у брони Ботании): R1 божественная со щитом на спине,
R2 лепестки и нимб, R3 деревянные латы с прорезью-плюсом и посохом, R4 убор из перьев и бусы, R5 закрытый шлем и крылья,
R6 капюшон культиста и костяной хребет, R7 хлорофитовая. Коробки делятся по частям брони, как у костюма H.E.V.:
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
import wslim5  # noqa: E402,F401 (регистрирует концепты пятого раунда и R2 из четвёртого)

RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'divinerpgextended')
TIERS = {1: 'r1v', 2: 'r2s', 3: 'r3v', 4: 'r4v', 5: 'r5v', 6: 'r6v', 7: 'r7z'}
K = 2.0  # текселей на единицу модели (у ванильной брони 1)
ATLAS_W = 512
# Второй слой скина раздут на 0.25, а броня Ботании, с которой сняты пропорции, на 0.2: слой торчал бы сквозь неё.
# Поэтому всё, что снаружи конечности, отодвигается ещё на PUSH: броня 0.36, поддоспешник 0.30. Шапка раздута на 0.5,
# капюшон R6 лежал точно на ней и мерцал бы: голове хватает HEAD_PUSH.
PUSH, HEAD_PUSH = 0.16, 0.05
LIMBS = {'head': ((-4, 4), (-8, 0), (-4, 4)), 'body': ((-4, 4), (0, 12), (-2, 2)), 'rightArm': ((-3, 1), (-2, 10), (-2, 2)),
         'leftArm': ((-1, 3), (-2, 10), (-2, 2)), 'rightLeg': ((-2, 2), (0, 12), (-2, 2)), 'leftLeg': ((-2, 2), (0, 12), (-2, 2))}


def push_out(box, verts):
    """Сдвиг наружу по каждой оси, а не раздутие коробок: тонкие детали не толстеют, порядок слоёв не меняется."""
    limb = LIMBS.get(box.bone)
    if limb is None:
        return verts
    amount = HEAD_PUSH if box.bone == 'head' else PUSH
    out = np.array(verts, float)
    for axis, (lo, hi) in enumerate(limb):
        center, half = (lo + hi) / 2.0, (hi - lo) / 2.0
        d = out[:, axis] - center
        out[:, axis] = center + np.where(np.abs(d) >= half, d + np.sign(d) * amount, d * (1 + amount / half))
    if box.bone == 'head' and box.size[1] >= 6:  # оболочка шлема закрывает нижний край шапки на шее
        out[:, 1] = np.where((out[:, 1] > -0.7) & (out[:, 1] < 0.56), 0.56, out[:, 1])
    return out


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
    m = mk.Model('wanderer_r%d' % tier, boxes, atlas_width=ATLAS_W)
    m.warp = push_out
    return m


def main():
    mesh_dir = os.path.join(RES, 'models', 'wanderer')
    tex_dir = os.path.join(RES, 'textures', 'models', 'wanderer')
    for d in (mesh_dir, tex_dir):
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
        print('R%d %s: коробок %d, атлас %dx%d, свечение %s, части %s' % (tier, TIERS[tier], len(m.boxes), m.W, m.H,
                                                                     'да' if glow.any() else 'нет', ','.join(pieces)))


if __name__ == '__main__':
    main()
