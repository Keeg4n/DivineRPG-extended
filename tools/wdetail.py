"""Detailed textures for the Wanderer armor looks: a pass over the finished painting of every box (material and decals together).

The kind of material is taken from the base color of P, its decals and its bevel (OVERRIDES fixes wrong guesses per look):
- metal (gold, steel, white plate): a crisp light top edge and a cool shadow at the bottom, plate seams on tall faces, rivets, scratches;
- cloth (dark or dull colors): vertical folds and a weave (the weave only in double resolution);
- wood (seams or streaks decals): grain lines and a knot; stone (mottle): coarse grain and cracks;
- crystal (bright saturated colors): a diagonal glint;
- organic (bevel=0: feathers, leaves, horns, fur): only a soft gradient and grain.
Used by tools/wanderer_armor.py for the game (K 2) and for the optional double resolution resource pack (K 4).
Shadows shift toward cool, highlights toward warm, as in pixel art. Glowing and transparent texels stay as they are.
Mirrored boxes get the same detail mirrored. install(w) replaces wconcepts.P - call it before importing the concept modules."""
import colorsys
import math

import numpy as np

FACE_ID = {'east': 1, 'west': 2, 'up': 3, 'down': 4, 'north': 5, 'south': 6}
SHADOW = np.array([0.80, 0.82, 0.93])
WARM = np.array([1.0, 0.97, 0.86])


def _hash(a, b, seed):
    v = np.sin(a * 12.9898 + b * 78.233 + seed * 0.3719) * 43758.5453
    return v - np.floor(v)


def _seed(face, box):
    cx = abs(box.p0[0] + box.size[0] / 2)  # the same for a box and its mirror
    return (cx * 3.1 + box.p0[1] * 5.7 + box.p0[2] * 7.3 + float(np.dot(box.size, (2.3, 4.9, 6.1))) + FACE_ID[face.name] * 13.0) % 997.0


# Per look: the material where the color guess is wrong (by kind or by base color). The look being built is set in LOOK.
OVERRIDES = {
    'r1t': {'cloth': 'metal'},  # Shadow: black plate, not cloth
    'r1o': {'cloth': 'metal'},  # End: dark plate in stars
    'r1k': {'metal': 'stone', 'cloth': 'stone'},  # bedrock: speckled rock
    'r8b': {'#3a3f52': 'metal', '#1e2230': 'metal'},  # Moon: dark silver plates, the lilac stays cloth
}
LOOK = None


def kind(base, bevel, alpha, decals=()):
    names = [getattr(d, '__qualname__', '') for d in decals]
    if bevel == 0.0 or alpha is not None:
        return 'organic'
    if any(n.startswith(('seams', 'streaks')) for n in names):
        return 'wood'
    if any(n.startswith('mottle') for n in names):
        return 'stone'
    h = base.lstrip('#')
    r, g, b = (int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))
    hu, sa, va = colorsys.rgb_to_hsv(r, g, b)
    hu *= 360
    if (sa < 0.22 and va > 0.45) or (30 <= hu <= 62 and sa > 0.35 and va > 0.5):
        return 'metal'
    if va < 0.38 or (sa < 0.45 and va < 0.6):
        return 'cloth'
    if sa > 0.55 and va > 0.55:
        return 'crystal'
    return 'plate'


def _segments(length, step):
    n = max(1, int(round(length / step)))
    return [length * i / n for i in range(1, n)]


def detail(rgba, p, face, box, cls, s=1.0):
    rgba = np.array(rgba, float)
    col, a = rgba[..., :3], rgba[..., 3]
    _, fu, fv = face.texel_points()
    k = face.pw / max(face.wu, 1e-6)
    if box.mirrored:
        fu = face.wu - fu
    iu, iv = np.floor(fu * k), np.floor(fv * k)
    W, H = face.pw, face.ph
    seed = _seed(face, box)
    side = face.name not in ('up', 'down')
    t = 1.0 / k  # one texel in units
    light = np.zeros(fu.shape)
    shade = np.zeros(fu.shape)  # 0..1 toward the cool shadow

    hard = cls in ('metal', 'plate', 'crystal', 'stone')
    if cls != 'organic':
        bw = max(1, int(round(k / 2)))  # band width in texels
        if H > 2 * bw:
            light += np.where(iv < bw, 0.30 if hard else 0.16, np.where(iv < 2 * bw, 0.08, 0.0)) * s
            shade += np.where(iv >= H - bw, 0.55 if hard else 0.40, np.where(iv >= H - 2 * bw, 0.18, 0.0)) * s
        if W > 2 * bw:
            light += np.where(iu < bw, 0.12, 0.0) * s
            shade += np.where(iu >= W - bw, 0.25, 0.0) * s
    if face.name == 'up':
        light += 0.06 * s
    elif face.name == 'down':
        shade += 0.30 * s
    if side and face.hu > 1.0:
        g = 0.5 - fv / face.hu
        light += np.clip(g, 0, None) * (0.14 if cls != 'organic' else 0.10) * s
        shade += np.clip(-g, 0, None) * 0.35 * s
    # grain
    n = (_hash(iu, iv, seed) - 0.5) * 0.06 + (_hash(np.floor(iu / 2), np.floor(iv / 2), seed + 31.0) - 0.5) * 0.06
    light += np.clip(n, 0, None) * s
    shade += np.clip(-n, 0, None) * 2.0 * s

    if cls == 'metal' and side:
        # plates: seams across tall faces, a highlight under each seam, rivets at the plate corners
        cuts = _segments(face.hu, 3.0) if face.hu >= 4.5 else []
        for c in cuts:
            row = np.floor(c * k)
            shade += np.where(iv == row, 0.75, 0.0) * s
            light += np.where(iv == row + 1, 0.28, 0.0) * s
        if face.wu >= 2.5 and face.hu >= 2.5:
            ys = [0.0] + cuts + [face.hu]
            rv = []
            for y0, y1 in zip(ys[:-1], ys[1:]):
                if y1 - y0 >= 1.8:
                    rv += [(0.75, y0 + 0.75), (face.wu - 0.75, y0 + 0.75)]
            for (ru, rvv) in rv:
                du, dv = fu - ru, fv - rvv
                r = np.hypot(du, dv)
                rad = max(0.42, 0.8 * t)
                hit = r < rad
                light += np.where(hit & (du + dv < 0), 0.45, 0.0) * s
                shade += np.where(hit & (du + dv >= 0), 0.55, 0.0) * s
        # scratches
        area = face.wu * face.hu
        if area >= 5.0 and min(face.wu, face.hu) >= 1.5:
            rng = np.random.RandomState(int(seed * 1000) % (2 ** 31))
            for _ in range(int(round(area / 12.0))):
                u0, v0 = rng.uniform(0.2, 0.8) * face.wu, rng.uniform(0.2, 0.8) * face.hu
                ang = math.radians(rng.choice([rng.uniform(20, 60), rng.uniform(120, 160)]))
                L = rng.uniform(0.8, 1.8)
                du, dv = math.cos(ang), math.sin(ang)
                cu, cv = (iu + 0.5) * t - u0, (iv + 0.5) * t - v0
                tt = np.clip(cu * du + cv * dv, 0, L)
                d = np.hypot(cu - tt * du, cv - tt * dv) / t
                light += np.where(d < 0.5, 0.26, 0.0) * s
    elif cls == 'cloth' and side:
        # vertical folds: a dark line with a light one next to it, slightly wavy; a weave at fine resolution
        step = 1.6
        x = fu + 0.25 * np.sin(fv * 1.3 + seed)
        ph = (x / step) % 1.0
        shade += np.where(ph < 0.12, 0.38, 0.0) * s
        light += np.where((ph >= 0.12) & (ph < 0.24), 0.12, 0.0) * s
        if k >= 4:
            weave = ((iu + iv) % 2 == 0)
            shade += np.where(weave, 0.08, 0.0) * s
    elif cls == 'wood' and side:
        # grain: thin dark lines along the board, uneven spacing and length, a rare knot
        col_id = np.floor(fu / 0.7)
        off = (_hash(col_id, 3.0, seed) - 0.5) * 0.3
        x = (fu / 0.7 - col_id) + off
        run = _hash(col_id, np.floor(fv / 1.3), seed + 9.0) > 0.25
        shade += np.where((np.abs(x - 0.5) < 0.5 * t / 0.7 + 0.02) & run, 0.30, 0.0) * s
        if face.wu * face.hu >= 4.0:
            rng = np.random.RandomState(int(seed * 777) % (2 ** 31))
            ku, kv = rng.uniform(0.25, 0.75) * face.wu, rng.uniform(0.25, 0.75) * face.hu
            rr = np.hypot((fu - ku) * 1.6, fv - kv)
            shade += np.where(rr < 0.35, 0.45, np.where(rr < 0.6, 0.15, 0.0)) * s
    elif cls == 'stone' and side:
        # rock: stronger grain and a few dark cracks
        n2 = _hash(np.floor(iu / 3), np.floor(iv / 3), seed + 51.0) - 0.5
        light += np.clip(n2, 0, None) * 0.10 * s
        shade += np.clip(-n2, 0, None) * 0.30 * s
        area = face.wu * face.hu
        if area >= 4.0:
            rng = np.random.RandomState(int(seed * 555) % (2 ** 31))
            for _ in range(max(1, int(round(area / 14.0)))):
                u0, v0 = rng.uniform(0.2, 0.8) * face.wu, rng.uniform(0.1, 0.5) * face.hu
                for _seg in range(3):
                    ang = math.radians(rng.uniform(55, 125))
                    L = rng.uniform(0.6, 1.3)
                    du, dv = math.cos(ang), math.sin(ang)
                    cu, cv = fu - u0, fv - v0
                    tt = np.clip(cu * du + cv * dv, 0, L)
                    d = np.hypot(cu - tt * du, cv - tt * dv) / t
                    shade += np.where(d < 0.5, 0.55, 0.0) * s
                    u0, v0 = u0 + du * L, v0 + dv * L
    elif cls == 'crystal' and side and face.wu * face.hu >= 2.0:
        # a diagonal glint across the face
        dd = (fu / max(face.wu, 1e-6) + fv / max(face.hu, 1e-6)) / 2.0
        glint = np.abs(dd - 0.42) < 0.06
        light += np.where(glint, 0.35, np.where(np.abs(dd - 0.42) < 0.10, 0.12, 0.0)) * s

    shade = np.clip(shade, 0, 0.9)
    out = col * (1 - shade[..., None] * (1 - SHADOW))  # toward the cool shadow color
    out = out * (1 - shade[..., None] * 0.35)
    li = np.clip(light, 0, 0.7)[..., None]
    out = out + (WARM - out) * li * (0.35 + 0.65 * out)
    keep = a < 0.99  # glowing (0.6) and transparent (0) texels stay
    col2 = np.where(keep[..., None], col, np.clip(out, 0, 1))
    return np.concatenate([col2, a[..., None]], -1)


def install(w, s=1.0):
    orig = w.P

    def P(base, *decals, **kw):
        paint = orig(base, *decals, **kw)
        cls = kind(base, kw.get('bevel', 0.2), kw.get('alpha'), decals)
        ov = OVERRIDES.get(LOOK, {})
        cls = ov.get(base.lower() if isinstance(base, str) else base, ov.get(cls, cls))

        def dp(p, face, box):
            return detail(paint(p, face, box), p, face, box, cls, s)
        if hasattr(paint, 'labels'):
            dp.labels = paint.labels
        dp.kind = cls
        return dp
    w.P = P
    w.P_plain = orig
