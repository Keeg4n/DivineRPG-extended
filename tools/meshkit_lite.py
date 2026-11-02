"""Урезанная копия ~/mc/tools/meshkit.py (нет на ноутбуке): коробки -> атлас + .mesh для EasterMesh + программный рендер превью.

Координаты как у моделей Minecraft: единица = 1/16 блока, y вниз. Формат .mesh: "p <часть> <кость>", затем
"q nx ny nz  x y z u v  x y z u v ..." - четырёхугольник; порядок вершин - как у ModelBox (отсечение граней как в ванили).
"""
import math

import numpy as np
from PIL import Image

BONES = {'body': (0, 0, 0), 'glow': (0, 0, 0)}


def rot_matrix(rx, ry, rz):
    a, b, c = (math.radians(t) for t in (rx, ry, rz))
    Rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    Ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    Rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


class Box:
    def __init__(self, piece, bone, x, y, z, w, h, d, paint, rot=(0, 0, 0), pivot=None, k=4.0, name=''):
        self.piece, self.bone = piece, bone
        self.p0 = np.array([x, y, z], float)
        self.size = np.array([w, h, d], float)
        self.paint = paint
        self.rot = rot
        self.pivot = np.array(pivot if pivot is not None else self.p0 + self.size / 2, float)
        self.k = k
        self.name = name
        self.mirrored = False

    def faces(self):
        x1, y1, z1 = 0.0, 0.0, 0.0
        x2, y2, z2 = self.size
        v7, v0, v1, v2 = (x1, y1, z1), (x2, y1, z1), (x2, y2, z1), (x1, y2, z1)
        v3, v4, v5, v6 = (x1, y1, z2), (x2, y1, z2), (x2, y2, z2), (x1, y2, z2)
        w, h, d = self.size
        return [
            ('east', [v4, v0, v1, v5], (1, 0, 0), (d, h)),
            ('west', [v7, v3, v6, v2], (-1, 0, 0), (d, h)),
            ('up', [v4, v3, v7, v0], (0, -1, 0), (w, d)),
            ('down', [v1, v2, v6, v5], (0, 1, 0), (w, d)),
            ('north', [v0, v7, v2, v1], (0, 0, -1), (w, h)),
            ('south', [v3, v4, v5, v6], (0, 0, 1), (w, h)),
        ]

    def to_bone(self, pts):
        R = rot_matrix(*self.rot)
        p = np.asarray(pts, float) + self.p0
        return (p - self.pivot) @ R.T + self.pivot

    def normal_to_bone(self, n):
        return rot_matrix(*self.rot) @ np.asarray(n, float)


class Face:
    def __init__(self, box, name, verts, normal, dims):
        self.box, self.name, self.verts, self.normal = box, name, np.array(verts, float), np.array(normal, float)
        self.wu, self.hu = dims
        self.pw = max(1, int(math.ceil(dims[0] * box.k - 1e-6)))
        self.ph = max(1, int(math.ceil(dims[1] * box.k - 1e-6)))
        self.x = self.y = 0

    def texel_points(self):
        s = (np.arange(self.pw) + 0.5) / self.pw
        t = (np.arange(self.ph) + 0.5) / self.ph
        S, T = np.meshgrid(s, t)
        P0, P1, P2, P3 = self.verts
        top = P1[None, None, :] + S[..., None] * (P0 - P1)[None, None, :]
        bot = P2[None, None, :] + S[..., None] * (P3 - P2)[None, None, :]
        return top + T[..., None] * (bot - top), S * self.wu, T * self.hu


def pack(faces, width):
    order = sorted(faces, key=lambda f: (-f.ph, -f.pw))
    x = y = shelf = 0
    for f in order:
        if x + f.pw + 2 > width:
            x, y, shelf = 0, y + shelf, 0
        f.x, f.y = x + 1, y + 1
        x += f.pw + 2
        shelf = max(shelf, f.ph + 2)
    h = y + shelf
    H = 1
    while H < h:
        H *= 2
    return H


class Model:
    def __init__(self, name, boxes, atlas_width=512):
        self.name = name
        self.boxes = boxes
        self.faces = [Face(b, *f) for b in boxes for f in b.faces()]
        self.W = atlas_width
        self.H = pack(self.faces, self.W)
        self.img = np.zeros((self.H, self.W, 4), np.uint8)
        for f in self.faces:
            pts, fu, fv = f.texel_points()
            col = np.clip(np.asarray(f.box.paint(pts.copy(), f, f.box), float), 0, 1)
            rgba = np.concatenate([col[..., :3], np.ones(col.shape[:2] + (1,))], -1) if col.shape[-1] == 3 else col
            block = (rgba * 255 + 0.5).astype(np.uint8)
            self.img[f.y:f.y + f.ph, f.x:f.x + f.pw] = block
            self.img[f.y - 1, f.x:f.x + f.pw] = block[0]
            self.img[f.y + f.ph, f.x:f.x + f.pw] = block[-1]
            self.img[f.y - 1:f.y + f.ph + 1, f.x - 1] = self.img[f.y - 1:f.y + f.ph + 1, f.x]
            self.img[f.y - 1:f.y + f.ph + 1, f.x + f.pw] = self.img[f.y - 1:f.y + f.ph + 1, f.x + f.pw - 1]

    def quads(self):
        out = []
        for f in self.faces:
            b = f.box
            L, T, R, B = f.x / self.W, f.y / self.H, (f.x + f.pw) / self.W, (f.y + f.ph) / self.H
            uv = np.array([(R, T), (L, T), (L, B), (R, B)])
            out.append((b.piece, b.bone, b.to_bone(f.verts), uv, b.normal_to_bone(f.normal)))
        return out

    def save(self, png, mesh):
        img = self.img.copy()
        img[..., 3] = 255  # в игре свечение - отдельная группа меша, альфа не нужна
        Image.fromarray(img, 'RGBA').save(png)
        groups = {}
        for q in self.quads():
            groups.setdefault((q[0], q[1]), []).append(q)
        with open(mesh, 'w', encoding='utf-8', newline='\n') as fh:
            fh.write('# %s: atlas %dx%d, boxes %d, faces %d\n' % (self.name, self.W, self.H, len(self.boxes), len(self.faces)))
            for (piece, bone), qs in groups.items():
                fh.write('p %s %s\n' % (piece, bone))
                for _, _, v, uv, n in qs:
                    fh.write('q %.4f %.4f %.4f' % tuple(n))
                    for i in range(4):
                        fh.write('  %.4f %.4f %.4f %.6f %.6f' % (v[i][0], v[i][1], v[i][2], uv[i][0], uv[i][1]))
                    fh.write('\n')


LIGHTS = [np.array([0.2, -1.0, -0.7]), np.array([-0.2, -1.0, 0.7])]
LIGHTS = [l / np.linalg.norm(l) for l in LIGHTS]


def shade(n):
    return min(1.0, 0.4 + sum(0.6 * max(0.0, float(np.dot(n, l))) for l in LIGHTS))


def rgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def hash_noise(p, scale=1.0, seed=0):
    q = np.floor(p * scale * 4.0)
    v = np.sin(q[..., 0] * 12.9898 + q[..., 1] * 78.233 + q[..., 2] * 37.719 + seed * 4.1) * 43758.5453
    return v - np.floor(v)


def edge_dist(fu, fv, face):
    return np.minimum(np.minimum(fu, face.wu - fu), np.minimum(fv, face.hu - fv))


def material(color, bevel=0.22, bevel_dark=0.72, noise=0.05, top_light=1.06, grad=0.0):
    base = rgb(color) if isinstance(color, str) else np.asarray(color, float)

    def paint(p, face, box):
        pts, fu, fv = face.texel_points()
        col = np.broadcast_to(base, p.shape).copy()
        e = edge_dist(fu, fv, face)
        f = np.where(e < bevel, bevel_dark + (1 - bevel_dark) * (e / max(bevel, 1e-6)), 1.0) if bevel > 0 else np.ones(e.shape)
        n = 1 + (hash_noise(p, 1.0) - 0.5) * 2 * noise
        if face.name == 'up':
            n = n * top_light
        if grad:
            n = n * (1 - grad * np.clip(p[..., 1] / max(box.size[1], 1e-6), 0, 1))
        return col * (f * n)[..., None]
    return paint
