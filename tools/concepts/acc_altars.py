"""Алтари призыва боссов: грани 16x16 из настоящих ванильных текстур и своего узора, рендер куба."""
import numpy as np
from PIL import Image

from acc_kit import DISC, ELL, RING, RECT, POLY, LINE, PATH, DIAMOND, HEART, GEAR, Ic, block_tex, hexc, Frame
import meshkit as mk
from acc_pal import *

T = block_tex
K = '#1a1320'


def paste(base, tex, x0, y0, x1, y1):
    """Участок чужой текстуры на то же место (включительно)."""
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
    """Узор формой m вырезан из чужой текстуры: трещины, руны."""
    out = base.copy()
    for x, y in m:
        if 0 <= x < 16 and 0 <= y < 16:
            out[y, x] = tex[y, x]
    return out


def over(base, tex):
    """Текстура с прозрачностью поверх (лианы)."""
    out = base.copy()
    m = tex[..., 3] > 128
    out[m] = tex[m]
    return out


def draw(base, fn):
    """Свой узор поверх."""
    c = Ic()
    fn(c)
    out = base.copy()
    for (x, y), col in c.p.items():
        out[y, x] = (*col, 255)
    return out


def drip(base, tex, depth=4, seed=(5, 3, 6, 4, 2, 5, 3, 4, 6, 3, 5, 2, 4, 6, 3, 5)):
    """Шапка сверху с неровным краем, как снег или трава на блоке."""
    out = base.copy()
    for x in range(16):
        for y in range(min(seed[x], depth + 2)):
            out[y, x] = tex[y, x]
    return out


# узоры
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
    ('Алтарь Карцинуса', 'Карцинус', '4 крабовые клешни', 'сейчас целиком резной каменный кирпич', [
        ('Резной камень и клешня', 'резной кирпич, песчаник', lambda: draw(frame(T('stonebrick_carved'), T('sandstone_normal'), 1), claw), lambda: draw(frame(T('sandstone_top'), T('stonebrick_carved'), 2), claw)),
        ('Вода в песчанике', 'резной песчаник, вода, каменный кирпич', lambda: inset(T('sandstone_carved'), T('water_still'), 4), lambda: frame(T('water_still'), T('stonebrick_carved'), 3)),
        ('Панцирные плиты', 'каменный кирпич, оранжевая глина', lambda: band(band(T('stonebrick'), T('hardened_clay_stained_orange'), 0, 3), T('hardened_clay_stained_orange'), 12, 15), lambda: draw(frame(T('hardened_clay_stained_orange'), T('stonebrick'), 2), lambda c: (c.c(RING(8, 8, 5, 1), ORG[0]), c.d(ORG[0], 8, 8))))]),
    ('Алтарь Снежного Глакона', 'Снежный Глакон', 'Панцирь глубин', 'сейчас целиком блок лазурита', [
        ('Лазурит в паковом льду', 'лазурит, паковый лёд', lambda: draw(frame(T('lapis_block'), T('ice_packed'), 2), flake), lambda: draw(frame(T('lapis_block'), T('ice_packed'), 3), flake)),
        ('Снежная шапка', 'лазурит, снег', lambda: drip(T('lapis_block'), T('snow')), lambda: draw(T('snow'), lambda c: c.f(DIAMOND(8, 8, 5), LAP))),
        ('Ледяные колонны', 'лазурит, лёд', lambda: columns(T('lapis_block'), T('ice_packed')), lambda: mask(T('ice_packed'), T('lapis_block'), RING(8, 8, 6, 2.2)))]),
    ('Алтарь Ледяного Трона', 'Ледяной Колосс и Король-лич', '12 снежинок', 'сейчас целиком алмазный блок', [
        ('Алмаз в обсидиане', 'алмазный блок, обсидиан', lambda: frame(T('diamond_block'), T('obsidian'), 2), lambda: draw(frame(T('diamond_block'), T('obsidian'), 2), crown)),
        ('Кварц и лёд', 'резной кварц, алмаз, паковый лёд', lambda: inset(band(T('quartz_block_chiseled'), T('ice_packed'), 13, 15), T('diamond_block'), 5), lambda: inset(T('quartz_block_chiseled_top'), T('diamond_block'), 5)),
        ('Трон изо льда', 'паковый лёд, алмазный блок', lambda: draw(band(T('ice_packed'), T('diamond_block'), 11, 13), lambda c: c.f(POLY((3, 10), (5, 4), (7, 10)) | POLY((9, 10), (11, 4), (13, 10)), SILV)), lambda: mask(T('ice_packed'), T('diamond_block'), RECT(6, 0, 9, 15) | RECT(0, 6, 15, 9)))]),
    ('Алтарь Древней гробницы', 'Древняя сущность', 'Таинственные часы', 'сейчас целиком золотой блок', [
        ('Песчаник и анх', 'резной песчаник, золотой блок', lambda: draw(frame(T('sandstone_carved'), T('gold_block'), 1), ankh), lambda: frame(T('sandstone_top'), T('gold_block'), 3)),
        ('Золото и лазурит', 'золотой блок, лазурит', lambda: draw(columns(T('gold_block'), T('lapis_block'), 3), sun_eye), lambda: draw(T('gold_block'), sun_eye)),
        ('Гробничные часы', 'гладкий песчаник, золотой блок', lambda: draw(band(T('sandstone_smooth'), T('gold_block'), 0, 2), clock), lambda: draw(frame(T('sandstone_top'), T('gold_block'), 2), clock))]),
    ('Алтарь заповедной рощи', 'Страж мира (Великий кадиллион, голем, Король-воин, Повелитель ветров)', 'Зов своего мира', 'сейчас целиком изумрудный блок, один на все 4 мира', [
        ('Изумруд во мху', 'изумрудный блок, мшистый кирпич', lambda: draw(frame(T('emerald_block'), T('stonebrick_mossy'), 2), horn), lambda: frame(T('emerald_block'), T('stonebrick_mossy'), 3)),
        ('Изумруд в дереве', 'изумрудный блок, тропическая древесина', lambda: inset(T('log_jungle'), T('emerald_block'), 5), lambda: mask(T('log_jungle_top'), T('emerald_block'), RING(8, 8, 5, 2))),
        ('Заросший', 'изумрудный блок, мицелий, лианы', lambda: over(drip(T('emerald_block'), T('mycelium_side')), T('vine')), lambda: draw(T('mycelium_top'), horn))]),
    ('Алтарь логова джунглей', 'Древний драмкрикс', 'Тотем Древнего драмкрикса', 'сейчас целиком мшистый кирпич', [
        ('Золотой идол', 'мшистый булыжник, золотой блок', lambda: draw(T('cobblestone_mossy'), idol), lambda: mask(T('cobblestone_mossy'), T('gold_block'), RING(8, 8, 6, 1.6))),
        ('В лианах', 'мшистый кирпич, лианы, тропические доски', lambda: draw(over(T('stonebrick_mossy'), T('vine')), gem), lambda: draw(frame(T('planks_jungle'), T('stonebrick_mossy'), 2), gem)),
        ('Тотемный столб', 'тропическая древесина, мшистый кирпич', lambda: draw(band(T('log_jungle'), T('stonebrick_mossy'), 13, 15), totem), lambda: frame(T('log_jungle_top'), T('stonebrick_mossy'), 2))]),
    ('Алтарь арены Мортума', '6 боссов Мортума', 'кристалл босса', 'сейчас целиком блок красного камня', [
        ('Адский кирпич', 'адский кирпич, блок красного камня', lambda: draw(frame(T('redstone_block'), T('nether_brick'), 2), skull), lambda: draw(frame(T('redstone_block'), T('nether_brick'), 2), ring_c)),
        ('Трещины в обсидиане', 'обсидиан, блок красного камня', lambda: mask(T('obsidian'), T('redstone_block'), cracks), lambda: mask(T('obsidian'), T('redstone_block'), RING(8, 8, 6, 1.6) | cracks)),
        ('Песок душ и глаз', 'песок душ, адский камень, красный камень, светокамень', lambda: draw(band(band(T('netherrack'), T('soul_sand'), 11, 15), T('redstone_block'), 0, 1), red_eye), lambda: inset(T('soul_sand'), T('glowstone'), 5))]),
]


def block_render(side, top, size=250):
    """Куб в три четверти: тот же растеризатор, что у брони."""
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
