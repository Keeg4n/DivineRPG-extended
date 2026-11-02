"""Облики брони Странника как обычная броня Minecraft: две текстуры 64x32 на ступень (layer_1 - шлем, нагрудник, ботинки;
layer_2 - поножи) для стандартной модели брони (ModelBiped 1.0 и 0.5) + превью персонажа в этой броне.

Текстура "запекается" с 3D-облика (tools/wanderer_armor.py): для каждого пикселя оболочки брони луч идёт снаружи внутрь по
нормали грани и берёт цвет первой детали облика той же кости и части (4x4 луча на пиксель, усреднение; пиксель
прозрачен, если покрыт меньше чем наполовину). Выступающие детали (рога, нимб, крылья) ложатся на грань проекцией.
Раскладка UV - как у ModelBox (порядок вершин граней meshkit совпадает с TexturedQuad).

Запуск: python3 tools/wanderer_vanilla.py [ступень ...]. Пишет в tools/concepts/wanderer_concepts/vanilla.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'concepts'))
sys.path.insert(0, HERE)

import meshkit as mk  # noqa: E402
import wconcepts as w  # noqa: E402
from wanderer_armor import TIERS, slot  # noqa: E402

OUT = os.path.join(HERE, 'concepts', 'wanderer_concepts', 'vanilla')
SS = 4  # лучей на пиксель по каждой оси

# (слой, кость, часть брони, смещение текстуры, размеры w h d, угол коробки ModelBiped, раздутие)
REGIONS = [
    (1, 'head', 'helmet', (0, 0), (8, 8, 8), (-4, -8, -4), 1.0),
    (1, 'body', 'chest', (16, 16), (8, 12, 4), (-4, 0, -2), 1.0),
    (1, 'rightArm', 'chest', (40, 16), (4, 12, 4), (-3, -2, -2), 1.0),
    (1, 'rightLeg', 'boots', (0, 16), (4, 12, 4), (-2, 0, -2), 1.0),
    (2, 'body', 'legs', (16, 16), (8, 12, 4), (-4, 0, -2), 0.5),
    (2, 'rightLeg', 'legs', (0, 16), (4, 12, 4), (-2, 0, -2), 0.5),
]


def uv_rect(face, u, v, w_, h, d):
    """Прямоугольник грани в текстуре (u1, v1, u2, v2) - как в ModelBox."""
    return {
        'east': (u + d + w_, v + d, u + d + w_ + d, v + d + h),
        'west': (u, v + d, u + d, v + d + h),
        'up': (u + d, v, u + d + w_, v + d),
        'down': (u + d + w_, v + d, u + d + w_ + w_, v),
        'north': (u + d, v + d, u + d + w_, v + d + h),
        'south': (u + d + w_ + d, v + d, u + d + w_ + d + w_, v + d + h),
    }[face]


def face_verts(size):
    """Грани коробки размера size в её координатах: имя -> (verts(4,3), нормаль)."""
    b = mk.Box('x', 'body', 0, 0, 0, *size, None)
    return {n: (np.array(v, float), np.array(nn, float)) for n, v, nn, _ in b.faces()}


ENTER = {(0, True): 'west', (0, False): 'east', (1, True): 'up', (1, False): 'down', (2, True): 'north', (2, False): 'south'}


def cast(model, boxes, O, D, tlim=np.inf):
    """Первое попадание лучей O + t*D (координаты кости) в коробки облика не дальше tlim: цвета RGBA (N,4) и признак
    попадания. tlim - поверхность тела под бронёй: глубже луч прошёл бы в вырез капюшона и взял его заднюю стенку."""
    N = len(O)
    best_t = np.full(N, float(tlim))
    col = np.zeros((N, 4))
    faces = {}
    for f in model.faces:
        faces[(id(f.box), f.name)] = f
    for b in boxes:
        R = mk.rot_matrix(*b.rot)
        Ol = (O - b.pivot) @ R + b.pivot - b.p0
        Dl = D @ R
        tmin = np.full(N, -np.inf)
        tmax = np.full(N, np.inf)
        axis = np.zeros(N, int)
        for i in range(3):
            if abs(Dl[i]) < 1e-9:
                out = (Ol[:, i] < 0) | (Ol[:, i] > b.size[i])
                tmin[out] = np.inf
                continue
            t1 = (0 - Ol[:, i]) / Dl[i]
            t2 = (b.size[i] - Ol[:, i]) / Dl[i]
            lo = np.minimum(t1, t2)
            hi = np.maximum(t1, t2)
            upd = lo > tmin
            axis[upd] = i
            tmin = np.maximum(tmin, lo)
            tmax = np.minimum(tmax, hi)
        hit = (tmin <= tmax) & (tmin > 0) & (tmin < best_t)
        if not hit.any():
            continue
        idx = np.nonzero(hit)[0]
        P = Ol[idx] + tmin[idx, None] * Dl
        fv = face_verts(b.size)
        for i in range(3):
            sel = axis[idx] == i
            if not sel.any():
                continue
            name = ENTER[(i, Dl[i] > 0)]
            f = faces[(id(b), name)]
            P0, P1, P2, _ = fv[name][0]
            q = P[sel] - P1
            S = np.clip(q @ (P0 - P1) / max(1e-9, np.dot(P0 - P1, P0 - P1)), 0, 0.9999)
            T = np.clip(q @ (P2 - P1) / max(1e-9, np.dot(P2 - P1, P2 - P1)), 0, 0.9999)
            px = (f.x + np.floor(S * f.pw)).astype(int)
            py = (f.y + np.floor(T * f.ph)).astype(int)
            c = model.img[py, px] / 255.0
            ok = c[:, 3] > 0.1  # дыра в детали - луч идёт дальше
            ii = idx[sel][ok]
            best_t[ii] = tmin[ii]
            col[ii] = np.concatenate([c[ok, :3], np.ones((ok.sum(), 1))], 1)
    return col, best_t < tlim


def bake(tier):
    boxes = w.CONCEPTS[TIERS[tier]][3]()
    for b in boxes:
        b.piece = slot(b)
    model = mk.Model('bake', boxes, atlas_width=1024)
    layers = {1: np.zeros((32, 64, 4)), 2: np.zeros((32, 64, 4))}
    for layer, bone, piece, (u, v), (w_, h, d), corner, g in REGIONS:
        cand = [b for b in boxes if b.bone == bone and b.piece == piece]
        size = np.array([w_, h, d], float) + 2 * g
        origin = np.array(corner, float) - g
        for name, (verts, normal) in face_verts(size).items():
            u1, v1, u2, v2 = uv_rect(name, u, v, w_, h, d)
            nu, nv = int(abs(u2 - u1)), int(abs(v2 - v1))
            P0, P1, P2, _ = verts + origin
            s = (np.arange(nu * SS) + 0.5) / (nu * SS)
            t = (np.arange(nv * SS) + 0.5) / (nv * SS)
            Sg, Tg = np.meshgrid(s, t)
            pts = P1 + Sg[..., None] * (P0 - P1) + Tg[..., None] * (P2 - P1)
            O = pts.reshape(-1, 3) + normal * 30.0
            col, hit = cast(model, cand, O, -normal, 30.0 + g + 0.3) if cand else (np.zeros((len(O), 4)), np.zeros(len(O), bool))
            col = col.reshape(nv, SS, nu, SS, 4).transpose(0, 2, 1, 3, 4).reshape(nv, nu, SS * SS, 4)
            cover = col[..., 3].mean(-1)
            rgb = (col[..., :3] * col[..., 3:4]).sum(-2) / np.maximum(col[..., 3:4].sum(-2), 1e-6)
            tex = np.concatenate([rgb, (cover >= 0.5)[..., None].astype(float)], -1)
            # строки текстуры идут от v1 к v2 (у нижней грани v2 < v1 - переворот)
            if v2 < v1:
                tex = tex[::-1]
            if u2 < u1:
                tex = tex[:, ::-1]
            layers[layer][min(v1, v2):min(v1, v2) + nv, min(u1, u2):min(u1, u2) + nu] = tex
    return [(np.clip(layers[k], 0, 1) * 255 + 0.5).astype(np.uint8) for k in (1, 2)]


def tex_paint(img, u, v, w_, h, d):
    """Раскраска коробки превью из текстуры брони (ближайший пиксель, как в игре); отражённые коробки - отражённой текстурой."""
    def paint(p, face, box):
        name = face.name
        P0, P1, P2, _ = face_verts(box.size)[name][0]
        q = p - P1
        S = np.clip(q @ (P0 - P1) / np.dot(P0 - P1, P0 - P1), 0, 0.9999)
        T = np.clip(q @ (P2 - P1) / np.dot(P2 - P1, P2 - P1), 0, 0.9999)
        u1, v1, u2, v2 = uv_rect(name, u, v, w_, h, d)
        px = np.floor(u1 + S * (u2 - u1) - (1e-6 if u2 < u1 else 0)).astype(int)
        py = np.floor(v1 + T * (v2 - v1) - (1e-6 if v2 < v1 else 0)).astype(int)
        px = np.clip(px, min(u1, u2), max(u1, u2) - 1)
        py = np.clip(py, min(v1, v2), max(v1, v2) - 1)
        return img[py, px] / 255.0
    return paint


def armor_boxes(l1, l2):
    out = []
    for layer, bone, piece, (u, v), (w_, h, d), corner, g in REGIONS:
        img = l1 if layer == 1 else l2
        b = mk.Box('v', bone, corner[0] - g, corner[1] - g, corner[2] - g, w_ + 2 * g, h + 2 * g, d + 2 * g, tex_paint(img, u, v, w_, h, d), k=4)
        out.append(b)
        if bone in ('rightArm', 'rightLeg'):
            out.append(b.mirror())
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    tiers = [int(a) for a in sys.argv[1:]] or sorted(TIERS)
    for tier in tiers:
        l1, l2 = bake(tier)
        Image.fromarray(l1, 'RGBA').save(os.path.join(OUT, 'wanderer_r%d_layer_1.png' % tier))
        Image.fromarray(l2, 'RGBA').save(os.path.join(OUT, 'wanderer_r%d_layer_2.png' % tier))
        model = mk.Model('v', w.mannequin() + armor_boxes(l1, l2), atlas_width=1024)
        bg = w.TIER_BG[tier]
        front, back = w.view(model, -32, 10, bg), w.view(model, 148, 10, bg)
        sheet = Image.new('RGB', (front.width * 2, front.height))
        sheet.paste(front, (0, 0))
        sheet.paste(back, (front.width, 0))
        sheet.save(os.path.join(OUT, 'v_r%d.png' % tier), optimize=True)
        print('R%d %s: layer_1 %d px, layer_2 %d px' % (tier, TIERS[tier], (l1[..., 3] > 0).sum(), (l2[..., 3] > 0).sum()))


if __name__ == '__main__':
    main()
