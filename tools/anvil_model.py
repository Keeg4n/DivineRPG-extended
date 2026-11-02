"""Модель Наковальни Странника: тёмное железо, реалмитовые пояса, фиолетовый камень-гнездо, молот на рабочей поверхности.

Координаты: y вниз, пол блока y=24, верх блока y=8, x и z от -8 до 8, лицо (камень) смотрит в -Z, рог - в -X.
Пишет .mesh и .png в ресурсы мода (аргумент - корень репозитория) и превью в папку скрипта.
Группы меша: anvil/body (обычный свет) и anvil/glow (камни, полная яркость).
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import meshkit_lite as mk  # noqa: E402

REPO = sys.argv[1] if len(sys.argv) > 1 else None
HERE = os.path.dirname(os.path.abspath(__file__))

IRON, IRON_D, STEEL = '#3d4048', '#2a2c32', '#a3a9b3'
COP, COPD, BRASS = '#c8642c', '#7a3a18', '#d8b04a'
GEM, GEM_L = '#a86cff', '#efe2ff'
WOOD, LEATHER = '#7a4f2a', '#3a2618'


def fuv(p, face):
    n = face.name
    if n in ('north', 'south'):
        return p[..., 0], p[..., 1]
    if n in ('east', 'west'):
        return p[..., 2], p[..., 1]
    return p[..., 0], p[..., 2]


def P(base, *decals, bevel=0.22, noise=0.05):
    mat = mk.material(base, bevel=bevel, noise=noise)

    def paint(p, face, box):
        col = np.asarray(mat(p, face, box), float)
        for d in decals:
            dc, da = d(p, face, box)
            da = np.broadcast_to(np.asarray(da, float), col.shape[:-1])
            col = col * (1 - da[..., None]) + np.asarray(dc, float) * da[..., None]
        return col
    return paint


def rect(faces, u0, u1, v0, v1, color):
    c = mk.rgb(color)

    def d(p, face, box):
        if faces != 'all' and face.name not in faces:
            return c, 0.0
        u, v = fuv(p, face)
        return c, ((u >= u0) & (u <= u1) & (v >= v0) & (v <= v1)).astype(float)
    return d


def trim(w, color, faces='all'):
    c = mk.rgb(color)

    def d(p, face, box):
        if faces != 'all' and face.name not in faces:
            return c, 0.0
        _, fu, fv = face.texel_points()
        return c, (mk.edge_dist(fu, fv, face) < w).astype(float)
    return d


def scratches(color, amount=0.35):
    """Царапины на рабочей поверхности: тонкие косые штрихи."""
    c = mk.rgb(color)

    def d(p, face, box):
        if face.name != 'up':
            return c, 0.0
        u, v = fuv(p, face)
        s = np.sin(u * 3.1 + v * 1.3) * np.sin(u * 0.7 - v * 2.9)
        return c, (np.abs(s) > 0.93).astype(float) * amount
    return d


def B(x0, y0, z0, x1, y1, z1, paint, bone='body', rot=(0, 0, 0), pivot=None, k=6.0):
    return mk.Box('anvil', bone, x0, y0, z0, x1 - x0, y1 - y0, z1 - z0, paint, rot=rot, pivot=pivot, k=k)


def build():
    iron = P(IRON, noise=0.07)
    cop = P(COP, trim(0.3, COPD))
    bs = [
        B(-5.5, 22.5, -5.0, 7.5, 24.0, 5.0, P(IRON_D, noise=0.08)),                      # плита
        B(-4.5, 20.5, -4.0, 6.5, 22.5, 4.0, iron),                                     # ступня
        B(-4.7, 20.6, -4.2, 6.7, 21.2, 4.2, cop),                                      # пояс ступни
        B(-1.5, 12.5, -2.8, 4.5, 20.5, 2.8, iron),                                     # шея
        B(-1.8, 13.4, -3.1, 4.8, 14.2, 3.1, cop),                                      # пояса шеи
        B(-1.8, 18.6, -3.1, 4.8, 19.4, 3.1, cop),
        B(0.2, 15.0, -3.4, 2.8, 17.8, -2.8, P(BRASS, trim(0.25, '#8a6a20'))),          # оправа камня спереди
        B(0.2, 15.0, 2.8, 2.8, 17.8, 3.4, P(BRASS, trim(0.25, '#8a6a20'))),            # и сзади
        B(-3.5, 10.8, -3.8, 6.5, 12.5, 3.8, iron),                                     # плечи
        B(-3.5, 8.2, -4.4, 7.6, 10.8, 4.4, P(IRON, rect(('up',), -1, 99, -1, 99, STEEL), scratches('#6e737c'),
                                              rect(('up',), 8.6, 9.8, 3.6, 5.2, '#15161a'), noise=0.06)),  # рабочая поверхность
        B(-3.7, 10.0, -4.6, 7.8, 10.8, 4.6, cop),                                      # пояс под поверхностью
        B(-5.6, 8.2, -3.0, -3.5, 10.4, 3.0, P(IRON, rect(('up',), -1, 99, -1, 99, STEEL))),  # рог
        B(-7.0, 8.4, -2.0, -5.6, 9.9, 2.0, P(IRON, rect(('up',), -1, 99, -1, 99, STEEL))),
        B(-8.0, 8.7, -1.1, -7.0, 9.6, 1.1, P(IRON, rect(('up',), -1, 99, -1, 99, STEEL))),
        # камни-гнёзда (светятся)
        B(0.6, 15.4, -3.7, 2.4, 17.4, -3.3, P(GEM, rect(('north',), 0.2, 0.8, 0.2, 0.8, GEM_L), bevel=0.15), bone='glow'),
        B(0.6, 15.4, 3.3, 2.4, 17.4, 3.7, P(GEM, rect(('south',), 0.2, 0.8, 0.2, 0.8, GEM_L), bevel=0.15), bone='glow'),
    ]
    # молот на рабочей поверхности, чуть наискось
    piv = (2.6, 7.6, 0.4)
    rot = (0, 24, 0)
    bs += [
        B(-0.6, 7.25, 0.0, 6.6, 8.0, 0.75, P(WOOD, rect(('north', 'south', 'up', 'down'), 4.6, 7.2, -1, 99, LEATHER), bevel=0.12),
          rot=rot, pivot=piv),                                                          # рукоять с обмоткой
        B(-2.6, 6.2, -0.75, -0.6, 8.2, 1.5, P(IRON_D, rect(('up', 'north', 'south'), -1, 99, -1, 0.5, STEEL)), rot=rot, pivot=piv),  # боёк
        B(-1.95, 6.0, -0.95, -1.25, 8.2, 1.7, cop, rot=rot, pivot=piv),                # реалмитовое кольцо
    ]
    return mk.Model('wanderer_anvil', bs, atlas_width=512)


# ------------------------------------------------------------------ превью

def render(tris, tex, W, H, scale, center, yaw, pitch):
    img = np.zeros((H, W, 3))
    zb = np.full((H, W), np.inf)
    glow = np.zeros((H, W))
    R = mk.rot_matrix(pitch, 0, 0) @ mk.rot_matrix(0, yaw, 0)
    th, tw = tex.shape[:2]
    for v, uv, n, em in tris:
        p = (np.asarray(v) - center) @ R.T
        nn = R @ n
        if nn[2] > 1e-6:
            continue
        sx = p[:, 0] * scale + W / 2
        sy = p[:, 1] * scale + H / 2
        x0, x1 = int(max(0, math.floor(sx.min()))), int(min(W - 1, math.ceil(sx.max())))
        y0, y1 = int(max(0, math.floor(sy.min()))), int(min(H - 1, math.ceil(sy.max())))
        if x0 > x1 or y0 > y1:
            continue
        X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        d = (sy[1] - sy[2]) * (sx[0] - sx[2]) + (sx[2] - sx[1]) * (sy[0] - sy[2])
        if abs(d) < 1e-9:
            continue
        a = ((sy[1] - sy[2]) * (X - sx[2]) + (sx[2] - sx[1]) * (Y - sy[2])) / d
        b = ((sy[2] - sy[0]) * (X - sx[2]) + (sx[0] - sx[2]) * (Y - sy[2])) / d
        c = 1 - a - b
        m = (a >= -1e-6) & (b >= -1e-6) & (c >= -1e-6)
        if not m.any():
            continue
        z = a * p[0, 2] + b * p[1, 2] + c * p[2, 2]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        m &= z < sub
        if not m.any():
            continue
        u = a * uv[0, 0] + b * uv[1, 0] + c * uv[2, 0]
        w = a * uv[0, 1] + b * uv[1, 1] + c * uv[2, 1]
        tx = np.clip((u * tw).astype(int), 0, tw - 1)
        ty = np.clip((w * th).astype(int), 0, th - 1)
        col = tex[ty, tx, :3] / 255.0
        sub[m] = z[m]
        reg = img[y0:y1 + 1, x0:x1 + 1]
        reg[m] = (np.minimum(1.0, col * 1.1) if em else col * mk.shade(nn))[m]
        gl = glow[y0:y1 + 1, x0:x1 + 1]
        gl[m] = 1.0 if em else 0.0
    return img, np.isfinite(zb), glow


def view(model, yaw, pitch, W=900, H=760, scale=36.0, center=(0.5, 15.5, 0), bg=('#2b2f38', '#16181d'), floor=True):
    tris = []
    for piece, bone, v, uv, n in model.quads():
        tris.append((v[[0, 1, 2]], uv[[0, 1, 2]], n, bone == 'glow'))
        tris.append((v[[0, 2, 3]], uv[[0, 2, 3]], n, bone == 'glow'))
    img, mask, glow = render(tris, model.img, W, H, scale, np.array(center, float), yaw, pitch)
    t = np.linspace(0, 1, H)[:, None, None]
    canvas = np.broadcast_to(mk.rgb(bg[0]) * (1 - t) + mk.rgb(bg[1]) * t, (H, W, 3)).copy()
    if floor:
        fy = (24.0 - center[1]) * scale * math.cos(math.radians(pitch)) + H / 2
        yy, xx = np.mgrid[0:H, 0:W]
        e = ((xx - W / 2) / (W * 0.30)) ** 2 + ((yy - fy) / (W * 0.07)) ** 2
        canvas *= (1 - 0.6 * np.clip(1 - e, 0, 1))[..., None]
    canvas[mask] = img[mask]
    g = Image.fromarray((np.clip(img * glow[..., None], 0, 1) * 255).astype(np.uint8))
    bloom = np.asarray(g.filter(ImageFilter.GaussianBlur(16)), float) / 255.0
    out = np.clip(canvas + 1.1 * bloom, 0, 1)
    return Image.fromarray((out * 255).astype(np.uint8)).resize((W // 2, H // 2), Image.LANCZOS)


if __name__ == '__main__':
    m = build()
    if REPO:
        res = os.path.join(REPO, 'src/main/resources/assets/divinerpgextended')
        os.makedirs(os.path.join(res, 'models'), exist_ok=True)
        os.makedirs(os.path.join(res, 'textures/models'), exist_ok=True)
        m.save(os.path.join(res, 'textures/models/wanderer_anvil.png'), os.path.join(res, 'models/wanderer_anvil.mesh'))
        print('mesh + texture saved, atlas', m.W, 'x', m.H, 'boxes', len(m.boxes))
    views = [view(m, -35, 28), view(m, 145, 28), view(m, -90, 8), view(m, 0, 60)]
    sheet = Image.new('RGB', (views[0].width * 2, views[0].height * 2))
    for i, v in enumerate(views):
        sheet.paste(v, ((i % 2) * v.width, (i // 2) * v.height))
    sheet.save(os.path.join(HERE, 'anvil_preview.png'))
    print('preview', os.path.join(HERE, 'anvil_preview.png'))
