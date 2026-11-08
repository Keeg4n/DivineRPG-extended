"""Концепты аксессуаров, лута, алтарей и часов: значки 16x16 и вещи на модели брони.

Значки - плоские пиксели (см. память проекта: рендер 3D-модели в значок отвергнут). Вещи на теле - как у фенечек Ботании: плоский значок с толщиной,
раздутая полоса на торсе или маленькая модель из коробок на кости игрока. Съёмка - свой растеризатор: база (манекен и броня) считается один раз на кадр,
поверх неё дорисовывается только вещь.
"""
import io
import math
import os
import sys
import zipfile

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import meshkit as mk  # noqa: E402

HOME = os.path.expanduser('~')
MC_JAR = HOME + '/.gradle/caches/minecraft/net/minecraft/minecraft/1.7.10/minecraft-1.7.10.jar'
DV_JAR = HOME + '/mc/server-pack/mods/DivineRPG-1.4.1.5.jar'


def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ------------------------------------------------------------------ формы: множества пикселей

def DISC(cx, cy, r):
    return {(x, y) for x in range(16) for y in range(16) if (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 <= r * r}


def ELL(cx, cy, rx, ry):
    return {(x, y) for x in range(16) for y in range(16) if ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2 <= 1}


def RING(cx, cy, r, t=1.5):
    return DISC(cx, cy, r) - DISC(cx, cy, r - t)


def RECT(x0, y0, x1, y1):
    return {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) if 0 <= x < 16 and 0 <= y < 16}


def POLY(*pts):
    out = set()
    n = len(pts)
    for y in range(16):
        yc = y + .5
        xs = []
        for i in range(n):
            (x0, y0), (x1, y1) = pts[i], pts[(i + 1) % n]
            if (y0 <= yc < y1) or (y1 <= yc < y0):
                xs.append(x0 + (yc - y0) * (x1 - x0) / (y1 - y0))
        xs.sort()
        for a, b in zip(xs[::2], xs[1::2]):
            for x in range(16):
                if a <= x + .5 <= b:
                    out.add((x, y))
    return out


def LINE(x0, y0, x1, y1, w=1):
    out = set()
    steps = int(max(abs(x1 - x0), abs(y1 - y0))) * 2 + 1
    for i in range(steps + 1):
        t = i / steps
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        for dx in range(w):
            for dy in range(w):
                px, py = int(math.floor(x + .5)) + dx, int(math.floor(y + .5)) + dy
                if 0 <= px < 16 and 0 <= py < 16:
                    out.add((px, py))
    return out


def PATH(*pts, w=1):
    out = set()
    for a, b in zip(pts, pts[1:]):
        out |= LINE(a[0], a[1], b[0], b[1], w)
    return out


def SYM(m):
    return set(m) | {(15 - x, y) for x, y in m}


def DIAMOND(cx, cy, r):
    return {(x, y) for x in range(16) for y in range(16) if abs(x + .5 - cx) + abs(y + .5 - cy) <= r}


def HEART(cx, cy, s):
    """Сердце: две доли и клин. s - полуширина."""
    r = s * .55
    top = cy - s * .35
    return DISC(cx - s * .48, top, r) | DISC(cx + s * .48, top, r) | POLY((cx - s, top + r * .1), (cx + s, top + r * .1), (cx, cy + s))


def GEAR(cx, cy, r, teeth=8, h=1.6):
    out = DISC(cx, cy, r - h * .55)
    for i in range(teeth):
        a = 2 * math.pi * i / teeth
        out |= DISC(cx + math.cos(a) * (r - .7), cy + math.sin(a) * (r - .7), h * .72)
    return out


# ------------------------------------------------------------------ холст значка

OUTLINE = '#1a1320'


class Ic:
    """Значок 16x16. Цвет - hex; ramp - список от тёмного к светлому: свет слева сверху, градиент и фаска считаются сами."""

    def __init__(self):
        self.p = {}

    def f(self, m, ramp, grad=True):
        """Залить форму по рампе."""
        m = set(p for p in m if 0 <= p[0] < 16 and 0 <= p[1] < 16)
        if not m:
            return self
        if isinstance(ramp, str):
            ramp = [ramp]
        xs, ys = [p[0] for p in m], [p[1] for p in m]
        x0, y0, sp = min(xs), min(ys), max(1, (max(xs) - min(xs)) + (max(ys) - min(ys)))
        n = len(ramp) - 1
        for x, y in m:
            lv = (1 - ((x - x0) + (y - y0)) / sp) * n * .55 + n * .2 if grad else n * .5
            if (x - 1, y) not in m or (x, y - 1) not in m:
                lv += n * .32
            if (x + 1, y) not in m or (x, y + 1) not in m:
                lv -= n * .32
            self.p[(x, y)] = hexc(ramp[int(round(max(0, min(n, lv))))])
        return self

    def c(self, m, color):
        """Одним цветом."""
        col = hexc(color)
        for p in m:
            if 0 <= p[0] < 16 and 0 <= p[1] < 16:
                self.p[p] = col
        return self

    def d(self, color, *xy):
        """Точки: d('#fff', 3, 4, 5, 6) - пиксели (3,4) и (5,6)."""
        col = hexc(color)
        for i in range(0, len(xy), 2):
            self.p[(xy[i], xy[i + 1])] = col
        return self

    def l(self, x0, y0, x1, y1, color, w=1):
        return self.c(LINE(x0, y0, x1, y1, w), color)

    def erase(self, m):
        for p in m:
            self.p.pop(p, None)
        return self

    def a(self, x, y, rows, pal):
        """Кусок пиксель-арта: rows - строки, символы по pal, точка и пробел - пропуск."""
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in '. ':
                    continue
                self.p[(x + i, y + j)] = hexc(pal[ch])
        return self

    def outline(self, color=OUTLINE):
        col = hexc(color)
        add = {}
        for (x, y) in self.p:
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in self.p:
                    add[(nx, ny)] = col
        self.p.update(add)
        return self

    def rgba(self):
        a = np.zeros((16, 16, 4), np.uint8)
        for (x, y), c in self.p.items():
            a[y, x] = (c[0], c[1], c[2], 255)
        return a


def icon(fn, outline=True):
    """fn(Ic) -> готовый массив 16x16x4; контур снаружи силуэта добавляется сам."""
    c = Ic()
    fn(c)
    if outline:
        c.outline()
    return c.rgba()


def tint(rgba, ramp_from, ramp_to):
    """Перекрасить значок: цвета из одной рампы в другую, остальное как есть (знаки охотника, сердца, шестерни по мирам)."""
    a = rgba.copy()
    mp = {hexc(f): hexc(t) for f, t in zip(ramp_from, ramp_to)}
    for y in range(16):
        for x in range(16):
            k = tuple(int(v) for v in a[y, x, :3])
            if a[y, x, 3] and k in mp:
                a[y, x, :3] = mp[k]
    return a


def save_icon(rgba, path, zoom=8):
    Image.fromarray(rgba, 'RGBA').resize((16 * zoom, 16 * zoom), Image.NEAREST).save(path, optimize=True)


# ------------------------------------------------------------------ текстуры игры

_jars = {}


def jar_png(jar, name):
    z = _jars.setdefault(jar, zipfile.ZipFile(jar))
    return Image.open(io.BytesIO(z.read(name))).convert('RGBA')


def block_tex(name):
    """Ванильная текстура блока, 16x16 (у анимированных - первый кадр)."""
    im = jar_png(MC_JAR, 'assets/minecraft/textures/blocks/%s.png' % name)
    return np.asarray(im.crop((0, 0, 16, 16))).copy()


# ------------------------------------------------------------------ геометрия вещей (сплошной цвет)

def _quad_n(q):
    n = np.cross(q[1] - q[0], q[2] - q[1])
    ln = np.linalg.norm(n)
    return n / ln if ln > 1e-9 else np.array([0, 0, 1.0])


def cube(bone, x, y, z, w, h, d, color, rot=None, pivot=None):
    """Коробка на кости: [(кость, 4 вершины, цвет, нормаль)]. rot - градусы вокруг pivot."""
    col = np.array(hexc(color), float) / 255 if isinstance(color, str) else color
    x2, y2, z2 = x + w, y + h, z + d
    faces = [((x2, y, z2), (x2, y, z), (x2, y2, z), (x2, y2, z2), (1, 0, 0)), ((x, y, z), (x, y, z2), (x, y2, z2), (x, y2, z), (-1, 0, 0)),
             ((x2, y, z2), (x, y, z2), (x, y, z), (x2, y, z), (0, -1, 0)), ((x2, y2, z), (x, y2, z), (x, y2, z2), (x2, y2, z2), (0, 1, 0)),
             ((x2, y, z), (x, y, z), (x, y2, z), (x2, y2, z), (0, 0, -1)), ((x, y, z2), (x2, y, z2), (x2, y2, z2), (x, y2, z2), (0, 0, 1))]
    out = []
    R = mk.rot_matrix(*rot) if rot else None
    pv = np.array(pivot if pivot is not None else (x + w / 2, y + h / 2, z + d / 2), float)
    for a, b, c, e, n in faces:
        q = np.array([a, b, c, e], float)
        nn = np.array(n, float)
        if R is not None:
            q = (q - pv) @ R.T + pv
            nn = R @ nn
        out.append((bone, q, col, nn))
    return out


def sym_x(parts):
    """Зеркальная копия вещи: кости право <-> лево, x наоборот."""
    out = []
    for bone, q, col, n in parts:
        qq = q.copy()
        qq[:, 0] = -qq[:, 0]
        nn = n.copy()
        nn[0] = -nn[0]
        out.append((mk.MIRROR_BONE.get(bone, bone), qq[[1, 0, 3, 2]], col, nn))
    return out


def flat(bone, o, ux, uy, ic, px, t=.25):
    """Плоский значок с толщиной (ItemRenderer.renderItemIn2D): o - центр, ux и uy - оси пикселя, px - размер пикселя в единицах модели."""
    o, ux, uy = np.array(o, float), np.array(ux, float), np.array(uy, float)
    ux, uy = ux / np.linalg.norm(ux), uy / np.linalg.norm(uy)
    n = np.cross(ux, uy)
    out = []
    for y in range(16):
        x = 0
        while x < 16:
            if ic[y, x, 3] < 128:
                x += 1
                continue
            x2 = x
            col = ic[y, x, :3]
            while x2 + 1 < 16 and ic[y, x2 + 1, 3] >= 128 and (ic[y, x2 + 1, :3] == col).all():
                x2 += 1
            c = np.array(col, float) / 255
            a0, a1, b0, b1 = (x - 8) * px, (x2 + 1 - 8) * px, (y - 8) * px, (y + 1 - 8) * px

            def P(a, b, s):
                return o + ux * a + uy * b + n * (s * t / 2)
            out.append((bone, np.array([P(a1, b0, -1), P(a0, b0, -1), P(a0, b1, -1), P(a1, b1, -1)]), c, -n))
            out.append((bone, np.array([P(a0, b0, 1), P(a1, b0, 1), P(a1, b1, 1), P(a0, b1, 1)]), c, n))
            out.append((bone, np.array([P(a0, b0, -1), P(a1, b0, -1), P(a1, b0, 1), P(a0, b0, 1)]), c, -uy))
            out.append((bone, np.array([P(a1, b1, -1), P(a0, b1, -1), P(a0, b1, 1), P(a1, b1, 1)]), c, uy))
            out.append((bone, np.array([P(a0, b1, -1), P(a0, b0, -1), P(a0, b0, 1), P(a0, b1, 1)]), c, -ux))
            out.append((bone, np.array([P(a1, b0, -1), P(a1, b1, -1), P(a1, b1, 1), P(a1, b0, 1)]), c, ux))
            x = x2 + 1
    return out


# ------------------------------------------------------------------ съёмка

class Frame:
    """Кадр: цвет и глубина; база считается один раз, вещи рисуются на копии."""

    def __init__(self, W, H, scale, center, yaw, pitch, bg):
        self.W, self.H, self.scale = W, H, scale
        self.center = np.array(center, float)
        self.R = mk.rot_matrix(pitch, 0, 0) @ mk.rot_matrix(0, yaw, 0)
        t = np.linspace(0, 1, H)[:, None, None]
        top, bot = np.array(hexc(bg[0]), float) / 255, np.array(hexc(bg[1]), float) / 255
        self.img = np.broadcast_to(top * (1 - t) + bot * t, (H, W, 3)).copy()
        self.z = np.full((H, W), np.inf)

    def copy(self):
        f = Frame.__new__(Frame)
        f.__dict__.update(self.__dict__)
        f.img, f.z = self.img.copy(), self.z.copy()
        return f

    def _tri(self, v, n):
        p = (np.asarray(v) - self.center) @ self.R.T
        sx = p[:, 0] * self.scale + self.W / 2
        sy = p[:, 1] * self.scale + self.H / 2
        x0, x1 = int(max(0, math.floor(sx.min()))), int(min(self.W - 1, math.ceil(sx.max())))
        y0, y1 = int(max(0, math.floor(sy.min()))), int(min(self.H - 1, math.ceil(sy.max())))
        if x0 > x1 or y0 > y1:
            return None
        X, Y = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
        d = (sy[1] - sy[2]) * (sx[0] - sx[2]) + (sx[2] - sx[1]) * (sy[0] - sy[2])
        if abs(d) < 1e-9:
            return None
        a = ((sy[1] - sy[2]) * (X - sx[2]) + (sx[2] - sx[1]) * (Y - sy[2])) / d
        b = ((sy[2] - sy[0]) * (X - sx[2]) + (sx[0] - sx[2]) * (Y - sy[2])) / d
        c = 1 - a - b
        m = (a >= -1e-6) & (b >= -1e-6) & (c >= -1e-6)
        if not m.any():
            return None
        z = a * p[0, 2] + b * p[1, 2] + c * p[2, 2]
        sub = self.z[y0:y1 + 1, x0:x1 + 1]
        m &= z < sub
        if not m.any():
            return None
        return x0, y0, y1, x1, a, b, c, m, z, sub

    def tex_tris(self, tris, tex):
        th, tw = tex.shape[:2]
        for v, uv, n in tris:
            nn = self.R @ n
            r = self._tri(v, nn)
            if r is None:
                continue
            x0, y0, y1, x1, a, b, c, m, z, sub = r
            u = a * uv[0, 0] + b * uv[1, 0] + c * uv[2, 0]
            w = a * uv[0, 1] + b * uv[1, 1] + c * uv[2, 1]
            tx = np.clip((u * tw).astype(int), 0, tw - 1)
            ty = np.clip((w * th).astype(int), 0, th - 1)
            m &= tex[ty, tx, 3] > 25
            if not m.any():
                continue
            sub[m] = z[m]
            reg = self.img[y0:y1 + 1, x0:x1 + 1]
            reg[m] = tex[ty, tx, :3][m] / 255.0 * mk.shade(nn)

    def solid(self, parts, pose):
        for bone, q, col, n in parts:
            off = np.array(mk.BONES[bone], float)
            rot = (pose or {}).get(bone)
            if rot:
                Rb = mk.rot_matrix(*rot)
                qq, nb = q @ Rb.T + off, Rb @ n
            else:
                qq, nb = q + off, n
            nn = self.R @ nb
            s = mk.shade(nn)
            for tri in (qq[[0, 1, 2]], qq[[0, 2, 3]]):
                r = self._tri(tri, nn)
                if r is None:
                    continue
                x0, y0, y1, x1, a, b, c, m, z, sub = r
                sub[m] = z[m]
                self.img[y0:y1 + 1, x0:x1 + 1][m] = col * s

    def image(self, down=2):
        im = Image.fromarray((np.clip(self.img, 0, 1) * 255).astype(np.uint8))
        return im.resize((self.W // down, self.H // down), Image.LANCZOS)


# ------------------------------------------------------------------ броня

def tex_box(bone, x, y, z, w, h, d, u, v, inf, tw, th, mirror=False):
    """Коробка ModelBox с настоящей раскладкой текстуры: [(кость, вершины, uv, нормаль)]."""
    x1, y1, z1, x2, y2, z2 = x - inf, y - inf, z - inf, x + w + inf, y + h + inf, z + d + inf
    if mirror:
        x1, x2 = x2, x1
    v0, v1, v2, v3 = (x2, y1, z1), (x2, y2, z1), (x1, y2, z1), (x1, y1, z2)
    v4, v5, v6, v7 = (x2, y1, z2), (x2, y2, z2), (x1, y2, z2), (x1, y1, z1)
    rows = [((v4, v0, v1, v5), u + d + w, v + d, u + d + w + d, v + d + h, (1, 0, 0)), ((v7, v3, v6, v2), u, v + d, u + d, v + d + h, (-1, 0, 0)),
            ((v4, v3, v7, v0), u + d, v, u + d + w, v + d, (0, -1, 0)), ((v1, v2, v6, v5), u + d + w, v + d, u + d + w + w, v, (0, 1, 0)),
            ((v0, v7, v2, v1), u + d, v + d, u + d + w, v + d + h, (0, 0, -1)), ((v3, v4, v5, v6), u + d + w + d, v + d, u + d + w + d + w, v + d + h, (0, 0, 1))]
    out = []
    for vs, U1, V1, U2, V2, n in rows:
        uv = np.array([(U2, V1), (U1, V1), (U1, V2), (U2, V2)], float) / np.array([tw, th], float)
        nn = np.array(n, float)
        if mirror and nn[0] != 0:
            nn = -nn
        out.append((bone, np.array(vs, float), uv, nn))
    return out


def to_tris(quads, pose):
    tris = []
    for bone, v, uv, n in quads:
        off = np.array(mk.BONES[bone], float)
        rot = (pose or {}).get(bone)
        vv = v @ mk.rot_matrix(*rot).T + off if rot else v + off
        nn = mk.rot_matrix(*rot) @ n if rot else n
        tris.append((vv[[0, 1, 2]], uv[[0, 1, 2]], nn))
        tris.append((vv[[0, 2, 3]], uv[[0, 2, 3]], nn))
    return tris


class Armor:
    """Манекен и броня: слои (треугольники, текстура) и грубое описание поверхности для посадки вещей."""

    def __init__(self, name, layers, surface):
        self.name, self.layers, self.surface = name, layers, surface

    def env(self, bone, y, xlim=(-99, 99), zlim=(-99, 99)):
        """Огибающая кости на высоте y: (xmin, xmax, zmin, zmax) по граням, попавшим в пределы."""
        xs, zs = [], []
        for b, lo, hi in self.surface:
            if b != bone or not (lo[1] - .01 <= y <= hi[1] + .01):
                continue
            if hi[0] < xlim[0] or lo[0] > xlim[1] or lo[2] < zlim[0] or hi[2] > zlim[1]:
                continue
            xs += [lo[0], hi[0]]
            zs += [lo[2], hi[2]]
        if not xs:
            return (-4, 4, -2, 2)
        return min(xs), max(xs), min(zs), max(zs)


def _quads_box(q):
    return q.min(axis=0), q.max(axis=0)


_arm = {}


def divine_armor(pose):
    """Стандартная броня Divine: родные текстуры divine_1 и divine_2 на ModelBiped (раздутие 1.0 и 0.5, как в игре)."""
    if 'divine' in _arm:
        return _arm['divine']
    import wconcepts as w
    m = mk.Model('m', w.mannequin(), atlas_width=256)
    t1 = np.asarray(jar_png(DV_JAR, 'assets/divinerpg/textures/armor/divine_1.png'))
    t2 = np.asarray(jar_png(DV_JAR, 'assets/divinerpg/textures/armor/divine_2.png'))
    tw, th = t1.shape[1], t1.shape[0]
    q1 = (tex_box('head', -4, -8, -4, 8, 8, 8, 0, 0, 1.0, tw, th) + tex_box('head', -4, -8, -4, 8, 8, 8, 32, 0, 1.5, tw, th)
          + tex_box('body', -4, 0, -2, 8, 12, 4, 16, 16, 1.0, tw, th) + tex_box('rightArm', -3, -2, -2, 4, 12, 4, 40, 16, 1.0, tw, th)
          + tex_box('leftArm', -1, -2, -2, 4, 12, 4, 40, 16, 1.0, tw, th, True) + tex_box('rightLeg', -2, 0, -2, 4, 12, 4, 0, 16, 1.0, tw, th)
          + tex_box('leftLeg', -2, 0, -2, 4, 12, 4, 0, 16, 1.0, tw, th, True))
    q2 = (tex_box('body', -4, 0, -2, 8, 12, 4, 16, 16, .5, tw, th) + tex_box('rightLeg', -2, 0, -2, 4, 12, 4, 0, 16, .5, tw, th)
          + tex_box('leftLeg', -2, 0, -2, 4, 12, 4, 0, 16, .5, tw, th, True))
    layers = [(mk.scene_tris(m.quads(), pose), m.img), (to_tris(q1, pose), t1), (to_tris(q2, pose), t2)]
    surface = [(b, *_quads_box(v)) for b, v, _, _ in q1 + q2 if b in mk.BONES]
    _arm['divine'] = Armor('Divine', layers, surface)
    return _arm['divine']


def wanderer_armor(pose, key='r1v'):
    """Броня Странника первой ступени: те же коробки, что стоят в игре."""
    if key in _arm:
        return _arm[key]
    import wconcepts as w
    import wslim5  # noqa: F401 - регистрирует облики
    fn = w.CONCEPTS[key][3]
    m = mk.Model(key, w.mannequin() + fn(), atlas_width=1024)
    surface = []
    for piece, bone, v, uv, n in m.quads():
        if piece == 'm':
            continue
        surface.append((bone, *_quads_box(v)))
    _arm[key] = Armor('Странник', [(mk.scene_tris(m.quads(), pose), m.img)], surface)
    return _arm[key]


FRAMES = {  # (центр y, высота кадра в единицах)
    'head': (-3.0, 21), 'chest': (4.0, 23), 'waist': (10.5, 22), 'hand': (7.5, 17, -7.0), 'handL': (7.5, 17, 7.0), 'feet': (19.0, 26), 'full': (8.0, 38), 'up': (3.0, 30),
}
VIEWS = {'front': (-28, 8), 'side': (-62, 8), 'rside': (58, 8), 'back': (152, 8), 'back2': (-152, 8), 'high': (-28, 30), 'hand': (-30, 8)}
_base = {}
BGS = {'Divine': ('#51506a', '#2c2b3c'), 'Странник': ('#5a5560', '#322e38')}


def base_frame(armor, frame, view, W=460, H=540):
    key = (armor.name, frame, view)
    if key not in _base:
        spec = FRAMES[frame]
        cy, h = spec[0], spec[1]
        cx = spec[2] if len(spec) > 2 else 0
        yaw, pitch = VIEWS[view]
        f = Frame(W, H, H / h, (cx, cy, 0), yaw, pitch, BGS[armor.name])
        for tris, tex in armor.layers:
            f.tex_tris(tris, tex)
        _base[key] = f
    return _base[key]


def shot(armor, parts, frame, view, pose):
    f = base_frame(armor, frame, view).copy()
    f.solid(parts, pose)
    return f.image()
