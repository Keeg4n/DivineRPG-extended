"""Builder of detailed easter-egg models for the Divine addon: boxes (with rotation) -> HD atlas + mesh for the game + preview.

Coordinates are as in Minecraft models: one unit = 1/16 of a block, y down, the player's face looks toward -Z. Each box belongs
to a bone (head, body, rightArm, leftArm, rightLeg, leftLeg or item) and a piece (piece) - the game draws pieces by armor slot.
Painting is a function in the coordinates of the box itself (before rotation): this way the lambda, stripes and panels are drawn in 3D, and the game and the preview
use the same texture. The vertex order of the faces is as in ModelBox (back-face culling matches vanilla).

Mesh (.mesh, text): a line "p <piece> <bone>" starts a group, then "q nx ny nz  x y z u v  x y z u v  ..." is a quadrilateral.
"""
import math

import numpy as np
from PIL import Image

BONES = {  # bone pivot point in the default pose (as in ModelBiped)
    'head': (0, 0, 0), 'body': (0, 0, 0), 'rightArm': (-5, 2, 0), 'leftArm': (5, 2, 0), 'rightLeg': (-1.9, 12, 0),
    'leftLeg': (1.9, 12, 0), 'item': (0, 0, 0),
}


def rot_matrix(rx, ry, rz):
    """Like glRotate Z, then Y, then X in ModelRenderer: v' = Rz * Ry * Rx * v (degrees)."""
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

    def mirror(self):
        """A copy mirrored along X (left side from the right one)."""
        b = Box(self.piece, MIRROR_BONE.get(self.bone, self.bone), -(self.p0[0] + self.size[0]), self.p0[1], self.p0[2],
                *self.size, self.paint, (self.rot[0], -self.rot[1], -self.rot[2]), (-self.pivot[0], self.pivot[1], self.pivot[2]), self.k, self.name)
        b.mirrored = not self.mirrored
        return b

    def faces(self):
        """6 faces: name, 4 vertices (in box coordinates, before rotation), normal, texture size (width, height) in units."""
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
        """Box points -> bone coordinates (shift and rotation around pivot)."""
        R = rot_matrix(*self.rot)
        p = np.asarray(pts, float) + self.p0
        return (p - self.pivot) @ R.T + self.pivot

    def normal_to_bone(self, n):
        return rot_matrix(*self.rot) @ np.asarray(n, float)


MIRROR_BONE = {'rightArm': 'leftArm', 'leftArm': 'rightArm', 'rightLeg': 'leftLeg', 'leftLeg': 'rightLeg'}


class Face:
    """A face in the atlas: a rectangle and texel points in box coordinates."""

    def __init__(self, box, name, verts, normal, dims):
        self.box, self.name, self.verts, self.normal = box, name, np.array(verts, float), np.array(normal, float)
        self.wu, self.hu = dims
        self.pw = max(1, int(math.ceil(dims[0] * box.k - 1e-6)))
        self.ph = max(1, int(math.ceil(dims[1] * box.k - 1e-6)))
        self.x = self.y = 0

    def texel_points(self):
        """(ph, pw, 3): texel center points. Vertices: 0 -> (right, top), 1 -> (left, top), 2 -> (left, bottom), 3 -> (right, bottom)."""
        s = (np.arange(self.pw) + 0.5) / self.pw
        t = (np.arange(self.ph) + 0.5) / self.ph
        S, T = np.meshgrid(s, t)
        P0, P1, P2, P3 = self.verts
        top = P1[None, None, :] + S[..., None] * (P0 - P1)[None, None, :]
        bot = P2[None, None, :] + S[..., None] * (P3 - P2)[None, None, :]
        return top + T[..., None] * (bot - top), S * self.wu, T * self.hu


class Poly:
    """A set of arbitrary quadrilaterals (vertices directly in bone coordinates) with one painting: tubes and the like."""

    def __init__(self, piece, bone, quads, paint, k=8.0, name=''):
        self.piece, self.bone, self.quads, self.paint, self.k, self.name = piece, bone, quads, paint, k, name
        self.mirrored = False
        self.p0 = np.zeros(3)
        self.size = np.ones(3)

    def faces(self):
        out = []
        for i, q in enumerate(self.quads):
            q = np.asarray(q, float)
            n = np.cross(q[1] - q[0], q[2] - q[1])
            n = n / (np.linalg.norm(n) + 1e-12)
            out.append(('q%d' % i, q, n, (max(np.linalg.norm(q[0] - q[1]), 0.05), max(np.linalg.norm(q[2] - q[1]), 0.05))))
        return out

    def to_bone(self, pts):
        return np.asarray(pts, float)

    def normal_to_bone(self, n):
        return np.asarray(n, float)


def outward(q, out_dir):
    """Vertex order such that (P1-P0)x(P2-P1) points outward (as in ModelBox - otherwise the face is culled)."""
    q = [np.asarray(v, float) for v in q]
    n = np.cross(q[1] - q[0], q[2] - q[1])
    return q if np.dot(n, out_dir) >= 0 else [q[1], q[0], q[3], q[2]]


def tube(piece, bone, pts, radii, paints, sides=8, k=10.0, up=(0, 0, 1)):
    """A solid tube along a polyline in the XY plane: octagon rings (rn - across in the plane, rz - along the up axis), end caps.
    paints[i] - painting of segment i (between points i and i+1); end caps are like the end segments. No overlaps - no shimmering."""
    pts = [np.asarray(p, float) for p in pts]
    up = np.asarray(up, float)
    N = len(pts)
    rings, axes = [], []
    for i in range(N):
        a, b = pts[max(0, i - 1)], pts[min(N - 1, i + 1)]
        t = b - a
        t /= np.linalg.norm(t)
        n = np.cross(up, t)
        n /= np.linalg.norm(n)
        rn, rz = radii[i]
        ring = []
        for j in range(sides):
            ang = math.radians(180.0 / sides + j * 360.0 / sides)
            ring.append(pts[i] + n * rn * math.cos(ang) + up * rz * math.sin(ang))
        rings.append(ring)
        axes.append(t)
    groups = {}
    for i in range(N - 1):
        c = (pts[i] + pts[i + 1]) / 2
        for j in range(sides):
            jn = (j + 1) % sides
            q = [rings[i][j], rings[i][jn], rings[i + 1][jn], rings[i + 1][j]]
            mid = sum(q) / 4
            groups.setdefault(i, []).append(outward(q, mid - c))
    caps = [(0, -axes[0], 0), (N - 1, axes[-1], N - 2)]
    for idx, d, seg in caps:
        r = rings[idx]
        for a, b, c2, e in ((0, 1, 2, 3), (0, 3, 4, 7), (4, 5, 6, 7)) if sides == 8 else ():
            groups.setdefault(seg, []).append(outward([r[a], r[b], r[c2], r[e]], d))
    return [Poly(piece, bone, qs, paints[i], k) for i, qs in sorted(groups.items())]


def check_overlaps(boxes, eps=0.02):
    """Coinciding faces (same plane, same side, overlapping) of unrotated boxes of one bone - will shimmer."""
    faces = []
    for b in boxes:
        if not isinstance(b, Box) or any(abs(r) > 1e-6 for r in b.rot):
            continue
        lo, hi = b.p0, b.p0 + b.size
        for ax in range(3):
            for sign, coord in ((-1, lo[ax]), (1, hi[ax])):
                o = [i for i in range(3) if i != ax]
                faces.append((b, ax, sign, coord, lo[o[0]], hi[o[0]], lo[o[1]], hi[o[1]]))
    bad = []
    for i in range(len(faces)):
        b1, ax, s, c, a0, a1, c0, c1 = faces[i]
        for j in range(i + 1, len(faces)):
            b2, ax2, s2, cc, d0, d1, e0, e1 = faces[j]
            if b2 is b1 or b2.bone != b1.bone or ax2 != ax or s2 != s or abs(cc - c) > eps:
                continue
            ov = max(0, min(a1, d1) - max(a0, d0)) * max(0, min(c1, e1) - max(c0, e0))
            if ov > 0.01:
                bad.append((b1.piece + ':' + b1.name, b2.piece + ':' + b2.name, 'xyz'[ax], round(c, 3)))
    return bad


def pack(faces, width):
    """Shelf packing: tall faces first, 1 texel padding."""
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
        self.warp = None  # optional vertex tweak: warp(box, vertices in bone) -> vertices (does not change the atlas)
        self.W = atlas_width
        self.H = pack(self.faces, self.W)
        self.img = np.zeros((self.H, self.W, 4), np.uint8)
        for f in self.faces:
            pts, fu, fv = f.texel_points()
            p = pts.copy()
            name = f.name
            if f.box.mirrored:  # paint as for the original (right) box: mirror x inside the box, east <-> west
                p[..., 0] = f.box.size[0] - p[..., 0]
                f.name = {'east': 'west', 'west': 'east'}.get(name, name)
            col = f.box.paint(p, f, f.box)
            f.name = name
            col = np.clip(np.asarray(col, float), 0, 1)
            rgba = np.concatenate([col[..., :3], np.ones(col.shape[:2] + (1,))], -1) if col.shape[-1] == 3 else col
            block = (rgba * 255 + 0.5).astype(np.uint8)
            self.img[f.y:f.y + f.ph, f.x:f.x + f.pw] = block
            # 1 texel edge - in the same color (no seams)
            self.img[f.y - 1, f.x:f.x + f.pw] = block[0]
            self.img[f.y + f.ph, f.x:f.x + f.pw] = block[-1]
            self.img[f.y - 1:f.y + f.ph + 1, f.x - 1] = self.img[f.y - 1:f.y + f.ph + 1, f.x]
            self.img[f.y - 1:f.y + f.ph + 1, f.x + f.pw] = self.img[f.y - 1:f.y + f.ph + 1, f.x + f.pw - 1]

    def quads(self, piece=None):
        """[(piece, bone, verts(4,3) in bone, uv(4,2), normal)]"""
        out = []
        for f in self.faces:
            b = f.box
            if piece is not None and b.piece != piece:
                continue
            L, T, R, B = f.x / self.W, f.y / self.H, (f.x + f.pw) / self.W, (f.y + f.ph) / self.H
            uv = np.array([(R, T), (L, T), (L, B), (R, B)])
            v = b.to_bone(f.verts)
            if self.warp is not None:
                v = self.warp(b, v)
            out.append((b.piece, b.bone, v, uv, b.normal_to_bone(f.normal)))
        return out

    def save(self, png, mesh):
        Image.fromarray(self.img, 'RGBA').save(png)
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


# ---------------------------------------------------------------- preview (software rasterizer)

LIGHTS = [np.array([0.2, -1.0, -0.7]), np.array([-0.2, -1.0, 0.7])]  # as in Minecraft (y down)
LIGHTS = [l / np.linalg.norm(l) for l in LIGHTS]


def shade(n):
    s = 0.4 + sum(0.6 * max(0.0, float(np.dot(n, l))) for l in LIGHTS)
    return min(1.0, s)


def render(tris, tex, size, scale, center, yaw=0.0, pitch=0.0, bg=(30, 30, 34)):
    """tris: [(verts(3,3) in scene space, uv(3,2), normal)]; orthographic camera from the front (from -Z), x right, y down."""
    img = np.zeros((size[1], size[0], 3), float)
    img[:] = np.array(bg) / 255.0
    zb = np.full((size[1], size[0]), np.inf)
    R = rot_matrix(pitch, 0, 0) @ rot_matrix(0, yaw, 0)  # first the model rotation, then the camera tilt
    th, tw = tex.shape[:2]
    for v, uv, n in tris:
        p = (np.asarray(v) - center) @ R.T
        nn = R @ n
        if nn[2] > 1e-6:  # faces away from the camera
            continue
        sx = p[:, 0] * scale + size[0] / 2
        sy = p[:, 1] * scale + size[1] / 2
        x0, x1 = int(max(0, math.floor(sx.min()))), int(min(size[0] - 1, math.ceil(sx.max())))
        y0, y1 = int(max(0, math.floor(sy.min()))), int(min(size[1] - 1, math.ceil(sy.max())))
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
        alpha = tex[ty, tx, 3] > 25
        m &= alpha
        sub[m] = z[m]
        reg = img[y0:y1 + 1, x0:x1 + 1]
        reg[m] = col[m] * shade(nn)
    return (np.clip(img, 0, 1) * 255).astype(np.uint8)


def scene_tris(quads, pose=None):
    """Bone quadrilaterals -> scene triangles (bones in their places)."""
    tris = []
    for piece, bone, v, uv, n in quads:
        off = np.array(BONES[bone], float)
        rot = (pose or {}).get(bone)
        vv = v @ rot_matrix(*rot).T + off if rot else v + off
        nn = rot_matrix(*rot) @ n if rot else n
        tris.append((vv[[0, 1, 2]], uv[[0, 1, 2]], nn))
        tris.append((vv[[0, 2, 3]], uv[[0, 2, 3]], nn))
    return tris


# ---------------------------------------------------------------- painting

def rgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def hash_noise(p, scale=1.0, seed=0):
    """Deterministic noise 0..1 at a point (for texture)."""
    q = np.floor(p * scale * 4.0)
    v = np.sin(q[..., 0] * 12.9898 + q[..., 1] * 78.233 + q[..., 2] * 37.719 + seed * 4.1) * 43758.5453
    return v - np.floor(v)


def edge_dist(fu, fv, face):
    """Distance (in units) to the edge of the face."""
    return np.minimum(np.minimum(fu, face.wu - fu), np.minimum(fv, face.hu - fv))


def material(color, bevel=0.22, bevel_dark=0.72, noise=0.05, top_light=1.06, grad=0.0):
    """Solid material: dark bevel along the face edges, light noise, top faces slightly lighter; grad - darkening toward the bottom."""
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


def with_decal(base_paint, decal):
    """decal(p, face, box) -> (rgb (..,3), alpha (..)) on top of the material."""
    def paint(p, face, box):
        col = base_paint(p, face, box)
        dc, da = decal(p, face, box)
        return col * (1 - da[..., None]) + dc * da[..., None]
    return paint
