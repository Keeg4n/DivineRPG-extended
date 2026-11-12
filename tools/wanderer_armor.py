"""Облики брони Странника для игры: концепт ступени (tools/concepts) -> меш, атлас и маска свечения. Значки частей - tools/wanderer_icons.py.

Ступень -> концепт (пятый раунд, concepts/wslim5.py, пропорции как у брони Ботании): R1 божественная со щитом на спине,
R2 лепестки и нимб, R3 деревянные латы с прорезью-плюсом и посохом, R4 убор из перьев и бусы, R5 закрытый шлем и крылья,
R6 капюшон культиста и костяной хребет, R7 хлорофитовая. У первой ступени облик зависит от ядра (concepts/wr1.py): r1_shadow,
r1_bedrock, r1_ender, r1_corrupted. Коробки делятся по частям брони, как у костюма H.E.V.:
helmet - всё на голове, chest - торс и руки, legs - пояс и бёдра (на теле - ниже y=9), boots - голени ниже y=6.5.
Светящиеся тексели (альфа 0.6 в атласе концепта) уходят в отдельную маску r<N>_glow.png: в игре её рисуют вторым
проходом на полной яркости поверх того же меша.
Полупрозрачные облики (TRANSLUCENT, сейчас Призрак R8): основа в атласе с частичной альфой, в игре рисуется со смешиванием.
Покраска (красители в верстаке): маска r<N>_dye.png - серым там, где тексель основного материала облика (цвета из DYE), в игре
её рисуют поверх того же меша, умножив на цвет. Отделка, камни, кожа и свечение остаются своими.

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


def code(c):
    """Цвет '#rrggbb' или массив rgb 0..1 (в том числе по текселям) -> число 0xRRGGBB."""
    if isinstance(c, str):
        return int(c.lstrip('#'), 16)
    q = np.rint(np.asarray(c, float)[..., :3] * 255).astype(np.int64)
    return (q[..., 0] << 16) | (q[..., 1] << 8) | q[..., 2]


def _labeled(P):
    """P концептов с подписью цвета по текселям (основа или наклейка поверх неё) - для маски покраски. Картинку не меняет."""
    def LP(base, *decals, **kw):
        paint = P(base, *decals, **kw)

        def labels(p, face, box):
            shp = p.shape[:-1]
            lab = np.full(shp, code(base), np.int64)
            for d in decals:
                dc, da, _ = d(p, face, box)
                da = np.broadcast_to(np.asarray(da, float), shp)
                lab = np.where(da > 0.5, np.broadcast_to(code(dc), shp), lab)
            return lab
        paint.labels = labels
        return paint
    return LP


w.P = _labeled(w.P)  # до импорта концептов: они берут P из wconcepts при загрузке
import wslim5  # noqa: E402,F401 (регистрирует концепты пятого раунда и R2 из четвёртого)
import wr1  # noqa: E402,F401 (облики первой ступени по ядру)
import wr8  # noqa: E402,F401 (облики R8 по Венцу)

RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'divinerpgextended')
LOOKS = {'r1': 'r1v', 'r2': 'r2s', 'r3': 'r3v', 'r4': 'r4v', 'r5': 'r5v', 'r6': 'r6v', 'r7': 'r7z',
         'r1_shadow': 'r1t', 'r1_bedrock': 'r1k', 'r1_ender': 'r1o', 'r1_corrupted': 'r1r',
         'r8_flight': 'r8m', 'r8_regen': 'r8b', 'r8_arcana': 'r8c'}
# Полупрозрачные облики: непрозрачность основы (свечение и коробки с атрибутом solid - полностью непрозрачные). В игре
# ModelWandererArmor рисует их с прозрачностью.
TRANSLUCENT = {'r8_flight': 0.6}
# Основной материал облика - его перекрашивает краситель. По площади граней: золото и сталь R1, камень бедрока, пурпур порчи,
# синева Энда, чернь Тени, золото R2 (у поножей - алая ткань), синие доски R3, пурпур R4, белые латы R5, тёмная ткань R6,
# лайм R7.
DYE = {
    'r1': ('#f0c41c', '#a87808', '#6a4a04', '#fff3a0', '#c8c8dc', '#8686a0'),
    'r1_bedrock': ('#525252', '#7c7c7c'),
    'r1_corrupted': ('#2a2150', '#4b3b82', '#594d9a', '#7a6cc4'),
    'r1_ender': ('#0c1426', '#16233e'),
    'r1_shadow': ('#2e2e2e', '#474747'),
    'r2': ('#f0c040', '#ffe27a', '#8a6420', '#9a6a20', '#c89a30'),
    'r3': ('#0e2f78', '#2060c8', '#2464d4', '#2a72e8', '#2f8fe8', '#3080f0', '#5aa0ff'),
    'r4': ('#6e2478', '#8e3a98', '#9a3aa0', '#e030d4', '#f590f0'),
    'r5': ('#eef0f4', '#e4e9f1', '#f6f8fb', '#aeb2ba', '#6a6e78'),
    'r6': ('#2a2a31', '#1c1c21', '#34343c', '#232329', '#222226', '#121214', '#2e2e32', '#5a5a60'),
    'r7': ('#3cd400', '#2a9400', '#4cff10', '#10e060', '#8fc83a'),
    'r8_flight': ('#8ccff4', '#e2f6ff'),
    'r8_regen': ('#3a3f52',),
    'r8_arcana': ('#3a2a6a', '#6a58b0'),
}
DYE_PIECE = {('r2', 'legs'): ('#7a2414', '#4a1408', '#7a1a08', '#f0c040', '#9a6a20')}
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


def build_model(name):
    boxes = w.CONCEPTS[LOOKS[name]][3]()
    for b in boxes:
        b.piece = slot(b)
        b.k = K
    m = mk.Model('wanderer_' + name, boxes, atlas_width=ATLAS_W)
    m.warp = push_out
    return m


def luma(rgb):
    return np.asarray(rgb, float)[..., :3] @ np.array([0.299, 0.587, 0.114])


def dye_gray(lum, ref):
    """Яркость маски: тени и блики материала сохраняются, но и тёмный материал красится заметно."""
    return 0.25 + 0.75 * np.clip(lum / ref, 0, 1) ** 0.7


def dye_mask(m, name):
    """Серая маска основного материала по атласу модели (None - нечего красить)."""
    if name not in DYE:
        return None
    out = np.zeros_like(m.img)
    for f in m.faces:
        labels = getattr(f.box.paint, 'labels', None)
        if labels is None:
            continue
        want = DYE_PIECE.get((name, f.box.piece), DYE[name])
        ref = max(luma(mk.rgb(c)) for c in want)
        pts, fu, fv = f.texel_points()
        p = pts.copy()
        fname = f.name
        if f.box.mirrored:  # как в Model: рисуем как у исходной коробки
            p[..., 0] = f.box.size[0] - p[..., 0]
            f.name = {'east': 'west', 'west': 'east'}.get(fname, fname)
        lab = labels(p, f, f.box)
        f.name = fname
        block = m.img[f.y:f.y + f.ph, f.x:f.x + f.pw]
        sel = np.isin(lab, [code(c) for c in want]) & (block[..., 3] > 200)
        v = (dye_gray(luma(block) / 255.0, ref) * 255 + 0.5).astype(np.uint8)
        blk = np.zeros(block.shape, np.uint8)
        blk[..., 0] = blk[..., 1] = blk[..., 2] = v
        blk[..., 3] = np.where(sel, 255, 0)
        out[f.y:f.y + f.ph, f.x:f.x + f.pw] = blk
        out[f.y - 1, f.x:f.x + f.pw] = blk[0]
        out[f.y + f.ph, f.x:f.x + f.pw] = blk[-1]
        out[f.y - 1:f.y + f.ph + 1, f.x - 1] = out[f.y - 1:f.y + f.ph + 1, f.x]
        out[f.y - 1:f.y + f.ph + 1, f.x + f.pw] = out[f.y - 1:f.y + f.ph + 1, f.x + f.pw - 1]
    return out if out[..., 3].any() else None


def main():
    mesh_dir = os.path.join(RES, 'models', 'wanderer')
    tex_dir = os.path.join(RES, 'textures', 'models', 'wanderer')
    for d in (mesh_dir, tex_dir):
        os.makedirs(d, exist_ok=True)
    for name in sorted(LOOKS):
        m = build_model(name)
        a = m.img[..., 3]
        glow = (a > 25) & (a < 200)
        base = m.img.copy()
        base[..., 3] = np.where(a > 25, 255, 0)
        if name in TRANSLUCENT:  # полупрозрачная основа, кроме свечения и непрозрачных коробок
            base[..., 3] = np.where(a > 25, np.where(glow, 255, int(round(255 * TRANSLUCENT[name]))), 0)
            for f in m.faces:
                if getattr(f.box, 'solid', False):
                    blk = base[f.y - 1:f.y + f.ph + 1, f.x - 1:f.x + f.pw + 1, 3]
                    blk[blk > 0] = 255
        m.save(os.path.join(tex_dir, name + '.png'), os.path.join(mesh_dir, name + '.mesh'))
        Image.fromarray(base, 'RGBA').save(os.path.join(tex_dir, name + '.png'))
        gpath = os.path.join(tex_dir, name + '_glow.png')
        if glow.any():
            g = m.img.copy()
            g[..., 3] = np.where(glow, 255, 0)
            Image.fromarray(g, 'RGBA').save(gpath)
        elif os.path.exists(gpath):
            os.remove(gpath)
        dye = dye_mask(m, name)
        dpath = os.path.join(tex_dir, name + '_dye.png')
        if dye is not None:
            Image.fromarray(dye, 'RGBA').save(dpath)
        elif os.path.exists(dpath):
            os.remove(dpath)
        share = 0 if dye is None else (dye[..., 3] > 0).sum() / max(1, (base[..., 3] > 0).sum())
        pieces = sorted({b.piece for b in m.boxes})
        print('%s %s: коробок %d, атлас %dx%d, свечение %s, красится %d%%, части %s' % (name, LOOKS[name], len(m.boxes), m.W, m.H,
                                                                                 'да' if glow.any() else 'нет', round(share * 100), ','.join(pieces)))


if __name__ == '__main__':
    main()
