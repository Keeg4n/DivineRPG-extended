"""Boss summoning altars: 16x16 faces made from real vanilla textures and a custom pattern, cube render."""
import numpy as np
from PIL import Image

from acc_kit import DISC, ELL, RING, RECT, POLY, LINE, PATH, DIAMOND, HEART, GEAR, Ic, block_tex, hexc, Frame
import meshkit as mk
from acc_pal import *

T = block_tex
K = '#1a1320'


def paste(base, tex, x0, y0, x1, y1):
    """A region of a foreign texture copied to the same place (inclusive)."""
    out = base.copy()
    out[y0:y1 + 1, x0:x1 + 1] = tex[y0:y1 + 1, x0:x1 + 1]
    return out


def frame(base, tex, w=2):
    out = tex.copy()
    out[w:16 - w, w:16 - w] = base[w:16 - w, w:16 - w]
    return out


def inset(base, tex, w=3):
    return frame(tex, base, w)


def band(base, tex, y0, y1):
    return paste(base, tex, 0, y0, 15, y1)


def columns(a, b, step=4):
    out = a.copy()
    for x in range(16):
        if (x // step) % 2:
            out[:, x] = b[:, x]
    return out


def checker(a, b, step=4):
    out = a.copy()
    for y in range(16):
        for x in range(16):
            if (x // step + y // step) % 2:
                out[y, x] = b[y, x]
    return out


def mask(base, tex, m):
    """The pattern with shape m is cut out of a foreign texture: cracks, runes."""
    out = base.copy()
    for x, y in m:
        if 0 <= x < 16 and 0 <= y < 16:
            out[y, x] = tex[y, x]
    return out


def over(base, tex):
    """A texture with transparency on top (vines)."""
    out = base.copy()
    m = tex[..., 3] > 128
    out[m] = tex[m]
    return out


def draw(base, fn):
    """A custom pattern on top."""
    c = Ic()
    fn(c)
    out = base.copy()
    for (x, y), col in c.p.items():
        out[y, x] = (*col, 255)
    return out


def drip(base, tex, depth=4, seed=(5, 3, 6, 4, 2, 5, 3, 4, 6, 3, 5, 2, 4, 6, 3, 5)):
    """A cap on top with an uneven edge, like snow or grass on a block."""
    out = base.copy()
    for x in range(16):
        for y in range(min(seed[x], depth + 2)):
            out[y, x] = tex[y, x]
    return out


# patterns
claw = lambda c: (c.f(POLY((3, 13), (6, 13), (8, 10), (6, 8), (3, 10)), ORG), c.f(POLY((7, 10), (11, 10), (13, 8), (13, 10), (11, 12), (7, 12)), ORG), c.f(POLY((5, 8), (4, 5), (7, 3), (11, 3), (13, 5), (10, 5), (8, 5), (8, 8)), ORG))
flake = lambda c: (c.c(LINE(8, 3, 8, 12), '#f4fcff'), c.c(LINE(3, 8, 12, 8), '#f4fcff'), c.c(LINE(5, 5, 10, 10), '#c2e6fa'), c.c(LINE(5, 10, 10, 5), '#c2e6fa'))
crown = lambda c: (c.f(POLY((3, 12), (3, 6), (5.5, 9), (8, 4), (10.5, 9), (13, 6), (13, 12)), SILV), c.d(CYAN[3], 5, 11, 8, 11, 10, 11))
ankh = lambda c: (c.f(RING(8, 5.5, 3, 1.5), GOLD), c.f(RECT(7, 8, 8, 14), GOLD), c.f(RECT(4, 9, 11, 10), GOLD))
sun_eye = lambda c: (c.f(ELL(8, 8, 5.5, 3.4), BONE), c.f(DISC(8, 8, 2.2), LAP), c.d(K, 8, 8, 7, 8))
clock = lambda c: (c.f(DISC(8, 8, 4.6), BONE), c.c(RING(8, 8, 4.6, 1), GOLD[1]), c.l(8, 8, 8, 5, K), c.l(8, 8, 10, 9, K))
horn = lambda c: (c.f(POLY((5, 13), (3, 8), (4, 3), (6, 7), (7, 13)), BONE), c.f(POLY((11, 13), (13, 8), (12, 3), (10, 7), (9, 13)), BONE))
idol = lambda c: (c.f(RECT(4, 3, 11, 13), GOLD), c.c(RECT(5, 6, 6, 7), K), c.c(RECT(9, 6, 10, 7), K), c.c(RECT(6, 10, 9, 11), K), c.d(GOLD[3], 7, 11, 8, 10))
gem = lambda c: (c.f(DIAMOND(8, 8, 4), GRN), c.d(GRN[4], 7, 6, 6, 7))
totem = lambda c: (c.c(RECT(4, 4, 6, 6), K), c.c(RECT(9, 4, 11, 6), K), c.d(GRN[3], 5, 5, 10, 5), c.c(RECT(5, 9, 10, 11), K), c.d(BONE[3], 6, 9, 8, 9, 7, 11, 9, 11))
skull = lambda c: (c.f(RECT(4, 3, 11, 10), BONE), c.f(RECT(6, 11, 9, 13), BONE), c.c(RECT(5, 6, 6, 7), K), c.c(RECT(9, 6, 10, 7), K), c.d(K, 7, 9, 8, 9, 7, 12))
ring_c = lambda c: (c.c(RING(8, 8, 6, 1), '#fce060'), c.d('#fce060', 8, 1, 8, 14, 1, 8, 14, 8))
cracks = PATH((1, 2), (5, 5), (4, 9), (8, 11), (12, 9), (14, 13)) | PATH((10, 1), (8, 5), (11, 8)) | PATH((2, 13), (4, 9))
red_eye = lambda c: (c.f(ELL(8, 8, 5.5, 3.2), RED), c.f(DISC(8, 8, 1.8), K), c.d('#fff080', 7, 7))

ALTARS = [
    ('Carcinus Altar', 'Carcinus', '4 crab claws', 'currently a single carved stone brick', [
        ('Carved stone and claw', 'carved brick, sandstone', lambda: draw(frame(T('stonebrick_carved'), T('sandstone_normal'), 1), claw), lambda: draw(frame(T('sandstone_top'), T('stonebrick_carved'), 2), claw)),
        ('Water in sandstone', 'carved sandstone, water, stone brick', lambda: inset(T('sandstone_carved'), T('water_still'), 4), lambda: frame(T('water_still'), T('stonebrick_carved'), 3)),
        ('Shell plates', 'stone brick, orange clay', lambda: band(band(T('stonebrick'), T('hardened_clay_stained_orange'), 0, 3), T('hardened_clay_stained_orange'), 12, 15), lambda: draw(frame(T('hardened_clay_stained_orange'), T('stonebrick'), 2), lambda c: (c.c(RING(8, 8, 5, 1), ORG[0]), c.d(ORG[0], 8, 8))))]),
    ('Snow Glacon Altar', 'Snow Glacon', 'Shell of the Deep', 'currently a single lapis block', [
        ('Lapis in packed ice', 'lapis, packed ice', lambda: draw(frame(T('lapis_block'), T('ice_packed'), 2), flake), lambda: draw(frame(T('lapis_block'), T('ice_packed'), 3), flake)),
        ('Snow cap', 'lapis, snow', lambda: drip(T('lapis_block'), T('snow')), lambda: draw(T('snow'), lambda c: c.f(DIAMOND(8, 8, 5), LAP))),
        ('Ice pillars', 'lapis, ice', lambda: columns(T('lapis_block'), T('ice_packed')), lambda: mask(T('ice_packed'), T('lapis_block'), RING(8, 8, 6, 2.2)))]),
    ('Frozen Throne Altar', 'Frost Colossus and Lich King', '12 snowflakes', 'currently a single diamond block', [
        ('Diamond in obsidian', 'diamond block, obsidian', lambda: frame(T('diamond_block'), T('obsidian'), 2), lambda: draw(frame(T('diamond_block'), T('obsidian'), 2), crown)),
        ('Quartz and ice', 'carved quartz, diamond, packed ice', lambda: inset(band(T('quartz_block_chiseled'), T('ice_packed'), 13, 15), T('diamond_block'), 5), lambda: inset(T('quartz_block_chiseled_top'), T('diamond_block'), 5)),
        ('Throne of ice', 'packed ice, diamond block', lambda: draw(band(T('ice_packed'), T('diamond_block'), 11, 13), lambda c: c.f(POLY((3, 10), (5, 4), (7, 10)) | POLY((9, 10), (11, 4), (13, 10)), SILV)), lambda: mask(T('ice_packed'), T('diamond_block'), RECT(6, 0, 9, 15) | RECT(0, 6, 15, 9)))]),
    ('Ancient Tomb Altar', 'Ancient Entity', 'Mysterious Clock', 'currently a single gold block', [
        ('Sandstone and ankh', 'carved sandstone, gold block', lambda: draw(frame(T('sandstone_carved'), T('gold_block'), 1), ankh), lambda: frame(T('sandstone_top'), T('gold_block'), 3)),
        ('Gold and lapis', 'gold block, lapis', lambda: draw(columns(T('gold_block'), T('lapis_block'), 3), sun_eye), lambda: draw(T('gold_block'), sun_eye)),
        ('Tomb Clock', 'smooth sandstone, gold block', lambda: draw(band(T('sandstone_smooth'), T('gold_block'), 0, 2), clock), lambda: draw(frame(T('sandstone_top'), T('gold_block'), 2), clock))]),
    ('Sacred Grove Altar', 'World Guardian (Great Cadillion, golem, Warrior King, Wind Lord)', 'Call of One\'s Own World', 'currently a single emerald block, one for all 4 worlds', [
        ('Emerald in moss', 'emerald block, mossy brick', lambda: draw(frame(T('emerald_block'), T('stonebrick_mossy'), 2), horn), lambda: frame(T('emerald_block'), T('stonebrick_mossy'), 3)),
        ('Emerald in wood', 'emerald block, tropical wood', lambda: inset(T('log_jungle'), T('emerald_block'), 5), lambda: mask(T('log_jungle_top'), T('emerald_block'), RING(8, 8, 5, 2))),
        ('Overgrown', 'emerald block, mycelium, vines', lambda: over(drip(T('emerald_block'), T('mycelium_side')), T('vine')), lambda: draw(T('mycelium_top'), horn))]),
    ('Jungle Lair Altar', 'Ancient Dramcryx', 'Ancient Dramcryx Totem', 'currently a single mossy brick', [
        ('Golden idol', 'mossy cobblestone, gold block', lambda: draw(T('cobblestone_mossy'), idol), lambda: mask(T('cobblestone_mossy'), T('gold_block'), RING(8, 8, 6, 1.6))),
        ('In vines', 'mossy brick, vines, tropical planks', lambda: draw(over(T('stonebrick_mossy'), T('vine')), gem), lambda: draw(frame(T('planks_jungle'), T('stonebrick_mossy'), 2), gem)),
        ('Totem pole', 'tropical wood, mossy brick', lambda: draw(band(T('log_jungle'), T('stonebrick_mossy'), 13, 15), totem), lambda: frame(T('log_jungle_top'), T('stonebrick_mossy'), 2))]),
    ('Mortum Arena Altar', '6 Mortum bosses', 'boss crystal', 'currently a single redstone block', [
        ('Nether brick', 'nether brick, redstone block', lambda: draw(frame(T('redstone_block'), T('nether_brick'), 2), skull), lambda: draw(frame(T('redstone_block'), T('nether_brick'), 2), ring_c)),
        ('Cracks in obsidian', 'obsidian, redstone block', lambda: mask(T('obsidian'), T('redstone_block'), cracks), lambda: mask(T('obsidian'), T('redstone_block'), RING(8, 8, 6, 1.6) | cracks)),
        ('Soul sand and eye', 'soul sand, netherrack, redstone, glowstone', lambda: draw(band(band(T('netherrack'), T('soul_sand'), 11, 15), T('redstone_block'), 0, 1), red_eye), lambda: inset(T('soul_sand'), T('glowstone'), 5))]),
]


def block_render(side, top, size=250):
    """Cube in three-quarter view: the same rasterizer as for the armor."""
    atlas = np.zeros((16, 32, 4), np.uint8)
    atlas[:, :16] = side
    atlas[:, 16:] = top
    s = 8.0

    def q(verts, u0, n, flip=False):
        v = np.array(verts, float)
        uv = np.array([(u0 + .5, 0), (u0, 0), (u0, 1), (u0 + .5, 1)], float)
        return (v, uv, np.array(n, float))
    A = (-s, -s, -s)
    faces = [q([(s, -s, -s), (-s, -s, -s), (-s, s, -s), (s, s, -s)], 0, (0, 0, -1)), q([(-s, -s, -s), (-s, -s, s), (-s, s, s), (-s, s, -s)], 0, (-1, 0, 0)),
             q([(-s, -s, s), (s, -s, s), (s, -s, -s), (-s, -s, -s)], .5, (0, -1, 0)), q([(s, -s, s), (s, -s, -s), (s, s, -s), (s, s, s)], 0, (1, 0, 0))]
    W = size * 2
    f = Frame(W, W, W / 32.0, (0, 0, 0), -45, 30, ('#3e3a46', '#26232c'))
    tris = []
    for v, uv, n in faces:
        tris.append((v[[0, 1, 2]], uv[[0, 1, 2]], n))
        tris.append((v[[0, 2, 3]], uv[[0, 2, 3]], n))
    f.tex_tris(tris, atlas)
    return f.image()


def flat_tex(tex, path, zoom=6):
    Image.fromarray(tex, 'RGBA').convert('RGB').resize((16 * zoom, 16 * zoom), Image.NEAREST).save(path, optimize=True)
