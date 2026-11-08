"""Значки частей брони Странника: плоские 16x16, как у обычной брони, а не рендер модели.

Форма у части общая (шаблоны ниже), у ступени - свои цвета и одна-две детали: у шлемов своя форма (открытый, глухой,
капюшон) и то, что отличает облик на теле (нимб, прорезь-плюс, перья, гребень, рога).
Запуск: python tools/wanderer_icons.py. Пишет в src/main/resources/assets/divinerpgextended/textures/items/wanderer.

Символы шаблона: . пусто, o контур, h блик, b основа, s тень, a отделка; детали ступени рисуются поверх любыми символами
своей палитры.

Покраска: рядом с каждым значком маска <облик>_<часть>_dye.png - серым те пиксели, что в игре перекрашивает краситель
(основной материал: по умолчанию h, b, s; где основа на модели другая - DYE_CHARS). В игре это второй проход значка,
умноженный на цвет.
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'divinerpgextended', 'textures', 'items', 'wanderer')

HELM_OPEN = """
................
................
................
....oooooooo....
...ohhhbbbbso...
..ohhbbbbbbbso..
..ohbbbbbbbbso..
..ohbaaaaaabso..
..ohbo....obso..
..ohbo....obso..
..osso....osso..
...oo......oo...
................
................
................
................
"""

HELM_CLOSED = """
................
................
................
....oooooooo....
...ohhhbbbbso...
..ohhbbbbbbbso..
..ohbbbbbbbbso..
..ohbaaaaaabso..
..ohbbbbbbbbso..
..ohbbbbbbbbso..
..ohbbbbbbbbso..
...osssssssso...
....oooooooo....
................
................
................
"""

HOOD = """
................
................
.....oooooo.....
...oohhbbbsoo...
..ohhbbbbbbbso..
..ohbbbbbbbbso..
..ohboooooobso..
..ohbokkkkobso..
..ohboekkeobso..
..ohbokkkkobso..
.ohbbokkkkobbso.
.osssoooooossso.
..oooo....oooo..
................
................
................
"""

CHEST = """
................
...ooo....ooo...
..ohhbo..obbso..
.ohhbbboobbbbso.
.ohbbbbbbbbbbso.
.oosbbbbbbbbsoo.
...ohbbbbbbso...
...ohbbbbbbso...
...ohbbbbbbso...
...ohbbbbbbso...
...ohbbbbbbso...
...ohbbbbbbso...
...ohbbbbbbso...
...osssssssso...
....oooooooo....
................
"""

LEGS = """
................
..oooooooooooo..
..oaaaaaaaaaao..
..ohbbbbbbbbso..
..ohbbbbbbbbso..
..ohbbbssbbbso..
..ohbbo..obbso..
..ohbbo..obbso..
..ohbbo..obbso..
..ohbbo..obbso..
..ohbbo..obbso..
..ohbbo..obbso..
..ohbbo..obbso..
..ohbbo..obbso..
..ooooo..ooooo..
................
"""

HAT = """
................
................
.....oooooo.....
.....obbbbo.....
.....ohbbso.....
.....oaaaao.....
..oooooooooooo..
.ohhbbbbbbbbsso.
..oooooooooooo..
....okkkkkko....
....oekkkkeo....
....obbbbbbo....
....ossssso.....
.....ooooo......
................
................
"""

BOOTS = """
................
................
................
................
................
..ooooo..ooooo..
..ohbbo..obbso..
..ohbbo..obbso..
..ohbbo..obbso..
..oaaao..oaaao..
..ohbbo..obbso..
.ohhbbo..obbsso.
.ohbbbo..obbbso.
.osssso..osssso.
.oooooo..oooooo.
................
"""

GOLD = dict(h='#f4dc78', b='#ccaf3b', s='#927a20', o='#4c3c10')
STEEL = dict(h='#eef0f4', b='#c4c8d0', s='#8c909c', o='#444854')
NAVY = dict(h='#3c3c50', b='#2a2a3a', s='#1c1c28', o='#0c0c14')
RED = dict(h='#d04a30', b='#a82a1a', s='#741a0e', o='#380c06')
BLUE = dict(h='#4a90f4', b='#1a62e0', s='#0f3f9c', o='#08204f')
PURPLE = dict(h='#a838c8', b='#7a1f96', s='#521068', o='#2a0838')
WHITE = dict(h='#ffffff', b='#d8dce4', s='#a8aebc', o='#4a5468')
SKY = dict(h='#d8e4f0', b='#b0c4dc', s='#7f94b0', o='#3a4658')
DARK = dict(h='#3a3a42', b='#26262c', s='#16161a', o='#08080a')
LIME = dict(h='#7cf040', b='#3cd400', s='#2a9400', o='#0c2404')
LIME_BRIGHT = dict(h='#b8ff60', b='#4cff10', s='#3cd400', o='#0c2404')
WOOD = '#d8bc8a'
SHADOW = dict(h='#6a6a6a', b='#2e2e2e', s='#1a1a1a', o='#060606')
ROCK = dict(h='#8e8e8e', b='#525252', s='#343434', o='#141414')
VOID = dict(h='#16233e', b='#0c1426', s='#050a14', o='#0e60aa')
ROT = dict(h='#7a6cc4', b='#4b3b82', s='#2a2150', o='#120c24')
SPECK = [('s', 5, 5), ('h', 9, 6), ('s', 7, 8), ('h', 6, 10), ('s', 10, 11), ('h', 8, 12)]


def rect(ch, x0, y0, x1, y1):
    return [(ch, x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)]


def both(ch, *points):
    """Точки и их отражение на другую сторону значка."""
    out = []
    for x, y in points:
        out += [(ch, x, y), (ch, 15 - x, y)]
    return out


KNEES = rect('a', 4, 9, 5, 10) + rect('a', 10, 9, 11, 10)

ICONS = {
    # R1 Божественная: золото, алый камень, сталь на груди и сапогах, тёмные штаны
    (1, 'helmet'): (HELM_OPEN, dict(GOLD, a='#a88a20', g='#e02030'), rect('g', 7, 7, 8, 7)),
    (1, 'chest'): (CHEST, dict(GOLD, A='#c4c8d0', g='#e02030'), rect('A', 6, 6, 9, 12) + rect('g', 7, 7, 8, 8)),
    (1, 'legs'): (LEGS, dict(NAVY, a='#ccaf3b'), KNEES),
    (1, 'boots'): (BOOTS, dict(STEEL, a='#ccaf3b'), []),
    # R1 с ядром Тени: чёрный, белая прорезь, серый шеврон
    ('r1_shadow', 'helmet'): (HELM_CLOSED, dict(SHADOW, a='#101010', e='#f4f8ff'), rect('a', 3, 7, 12, 9) + rect('e', 4, 8, 11, 8)),
    ('r1_shadow', 'chest'): (CHEST, dict(SHADOW, A='#9a9a9a'), both('A', (5, 6), (6, 7), (7, 8))),
    ('r1_shadow', 'legs'): (LEGS, dict(SHADOW, a='#474747'), KNEES),
    ('r1_shadow', 'boots'): (BOOTS, dict(SHADOW, a='#9a9a9a'), []),
    # R1 с ядром бедрока: крапчатый камень, белые глаза, рубленый венец
    ('r1_bedrock', 'helmet'): (HELM_CLOSED, dict(ROCK, a='#343434', e='#ffffff'), rect('o', 5, 2, 10, 2) + rect('h', 6, 1, 9, 1) + both('e', (5, 8)) + rect('o', 7, 8, 8, 11)),
    ('r1_bedrock', 'chest'): (CHEST, dict(ROCK, A='#191919', g='#8e8e8e'), SPECK[:3] + rect('A', 6, 6, 9, 10) + rect('g', 7, 7, 8, 9)),
    ('r1_bedrock', 'legs'): (LEGS, dict(ROCK, a='#343434', c='#8e8e8e'), SPECK[:2] + rect('c', 4, 9, 5, 10) + rect('c', 10, 9, 11, 10)),
    ('r1_bedrock', 'boots'): (BOOTS, dict(ROCK, a='#191919'), []),
    # R1 с ядром Энда: почти чёрный с синей каймой, глаза эндермена, светлые линии
    ('r1_ender', 'helmet'): (HELM_CLOSED, dict(VOID, a='#0c1426', e='#8fe0ff', g='#ffffff'), rect('e', 3, 7, 6, 8) + rect('e', 9, 7, 12, 8) + rect('g', 4, 8, 5, 8) + rect('g', 10, 8, 11, 8)),
    ('r1_ender', 'chest'): (CHEST, dict(VOID, A='#5ab4f4', g='#6cf0d8'), rect('A', 7, 5, 8, 12) + rect('g', 7, 7, 8, 8) + [('A', 5, 3), ('A', 10, 3)]),
    ('r1_ender', 'legs'): (LEGS, dict(VOID, a='#5ab4f4'), rect('a', 4, 4, 4, 12) + rect('a', 11, 4, 11, 12)),
    ('r1_ender', 'boots'): (BOOTS, dict(VOID, a='#5ab4f4'), []),
    # R1 с ядром порчи: шляпа с пурпурной лентой и глазами в тени, патронташ
    ('r1_corrupted', 'helmet'): (HAT, dict(ROT, a='#c040c0', k='#120c24', e='#e060e0'), []),
    ('r1_corrupted', 'chest'): (CHEST, dict(ROT, A='#120c24', g='#c040c0'), [('A', 5, 5), ('A', 6, 6), ('A', 7, 7), ('A', 8, 8), ('A', 9, 9), ('A', 10, 10), ('g', 6, 6), ('g', 8, 8), ('g', 10, 10)]),
    ('r1_corrupted', 'legs'): (LEGS, dict(ROT, a='#7a6cc4', g='#c040c0'), KNEES + [('g', 4, 5)]),
    ('r1_corrupted', 'boots'): (BOOTS, dict(ROT, a='#7a6cc4'), []),
    # R2 Эдем: золото, нимб, красные поножи
    (2, 'helmet'): (HELM_OPEN, dict(GOLD, a='#a88a20', g='#f08030', e='#fff4a0'), rect('g', 7, 7, 8, 7) + rect('e', 5, 1, 10, 1)),
    (2, 'chest'): (CHEST, dict(GOLD, A='#a82a1a'), rect('A', 6, 6, 9, 12) + [('h', 7, 7), ('h', 8, 9), ('h', 7, 11)]),
    (2, 'legs'): (LEGS, dict(RED, a='#f06a20'), rect('a', 4, 9, 5, 10) + rect('a', 10, 9, 11, 10)),
    (2, 'boots'): (BOOTS, dict(GOLD, a='#927a20'), []),
    # R3 Дикий лес: синие доски, плюс со скином, рога-ветви
    (3, 'helmet'): (HELM_CLOSED, dict(BLUE, a='#1a62e0', e='#d09a70', g='#6fc8ff'),
                    rect('e', 7, 6, 8, 10) + rect('e', 5, 8, 10, 8) + both('g', (3, 2), (3, 1), (2, 1), (4, 2))),
    (3, 'chest'): (CHEST, dict(BLUE, g='#7fe8ff'), rect('s', 6, 6, 6, 12) + rect('s', 9, 6, 9, 12) + rect('g', 7, 7, 8, 8)),
    (3, 'legs'): (LEGS, dict(BLUE, a=WOOD), KNEES),
    (3, 'boots'): (BOOTS, dict(BLUE, a=WOOD), []),
    # R4 Апалачия: пурпур, убор из перьев, бусы
    (4, 'helmet'): (HELM_OPEN, dict(PURPLE, a='#e040e8', A='#d060e8', e='#ffe0ff'),
                    both('A', (3, 3), (3, 2), (5, 2), (5, 1), (7, 2), (7, 1)) + both('e', (2, 1), (5, 0), (7, 0))),
    (4, 'chest'): (CHEST, dict(PURPLE, a='#e040e8', A='#f0a0f0'), rect('A', 6, 6, 9, 6) + rect('a', 6, 7, 9, 7) + rect('A', 6, 8, 9, 8) + rect('a', 6, 9, 9, 9) + rect('A', 6, 10, 9, 10)),
    (4, 'legs'): (LEGS, dict(PURPLE, a='#e040e8'), KNEES),
    (4, 'boots'): (BOOTS, dict(PURPLE, a='#e040e8'), []),
    # R5 Небесный терн: белые латы, закрытый шлем с гребнем, золото и небесный
    (5, 'helmet'): (HELM_CLOSED, dict(WHITE, a='#d4b040', k='#141820', g='#78c8ff'),
                    rect('k', 4, 8, 6, 8) + rect('k', 9, 8, 11, 8) + rect('a', 7, 8, 8, 10) + rect('g', 6, 2, 9, 2)),
    (5, 'chest'): (CHEST, dict(WHITE, a='#d4b040', g='#78c8ff'), both('g', (5, 6), (6, 7), (7, 8)) + rect('a', 7, 9, 8, 11)),
    (5, 'legs'): (LEGS, dict(SKY, a='#d4b040'), rect('a', 4, 9, 5, 10) + rect('a', 10, 9, 11, 10)),
    (5, 'boots'): (BOOTS, dict(WHITE, a='#d4b040', g='#78c8ff'), both('g', (1, 8), (1, 7), (0, 7))),
    # R6 Мортум: чёрный капюшон с красными глазами, красная отделка
    (6, 'helmet'): (HOOD, dict(DARK, k='#000000', e='#ff3020'), []),
    (6, 'chest'): (CHEST, dict(DARK, a='#a82018'), rect('a', 5, 6, 5, 10) + rect('a', 10, 6, 10, 10) + rect('a', 6, 6, 9, 6) + rect('a', 7, 7, 8, 10)),
    (6, 'legs'): (LEGS, dict(DARK, a='#55555e'), KNEES),
    (6, 'boots'): (BOOTS, dict(DARK, a='#a82018'), []),
    # R7 Галит: лайм, шпиль, чёрный плюс с красными глазами, рога
    (7, 'helmet'): (HELM_CLOSED, dict(LIME, a='#10e060', k='#0c2404', e='#ff2020', A='#4c4c4c', g='#b8ff60'),
                    rect('k', 7, 6, 8, 10) + rect('k', 5, 8, 10, 8) + both('e', (5, 8)) + rect('g', 7, 1, 8, 2) + both('A', (2, 4), (1, 4), (1, 3), (2, 2))),
    (7, 'chest'): (CHEST, dict(LIME, a='#10e060', A='#dcfaa6'), rect('A', 6, 9, 9, 12) + both('a', (5, 6), (6, 6), (5, 7), (6, 7))),
    (7, 'legs'): (LEGS, dict(LIME, a='#2a9400', A='#dcfaa6', c='#b8ff60'), rect('A', 6, 3, 9, 5) + rect('c', 4, 9, 5, 10) + rect('c', 10, 9, 11, 10)),
    (7, 'boots'): (BOOTS, dict(LIME_BRIGHT, a='#4c4c4c'), []),
}


# Какие символы значка - основной материал облика (как DYE в wanderer_armor.py); по умолчанию 'hbs'.
DYE_CHARS = {
    (1, 'chest'): 'hbsA', (1, 'legs'): 'a',
    ('r1_shadow', 'legs'): 'hbsa', ('r1_corrupted', 'legs'): 'hbsa', ('r1_corrupted', 'boots'): 'hbsa',
    (3, 'helmet'): 'hbsa',
    (4, 'helmet'): 'hbsaA', (4, 'chest'): 'hbsa', (4, 'legs'): 'hbsa', (4, 'boots'): 'hbsa',
    (6, 'legs'): 'hbsa',
    (7, 'helmet'): 'hbsa', (7, 'chest'): 'hbsa', (7, 'legs'): 'hbsac',
}


def luma(value):
    r, g, b, _ = color(value)
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0


def draw_dye(template, palette, details, chars):
    """Серая маска покраски: яркость пикселя относительно самого светлого из красящихся цветов."""
    grid = [list(r) for r in template.strip().splitlines()]
    for ch, x, y in details:
        grid[y][x] = ch
    lums = {ch: luma(palette[ch]) for ch in chars if ch in palette}
    ref = max(lums.values())
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch in lums:
                g = int(255 * (0.25 + 0.75 * min(1.0, lums[ch] / ref) ** 0.7) + 0.5)
                img.putpixel((x, y), (g, g, g, 255))
    return img


def color(value):
    value = value.lstrip('#')
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def draw(template, palette, details):
    rows = template.strip().splitlines()
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    grid = [list(r) for r in rows]
    for ch, x, y in details:
        grid[y][x] = ch
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y), color(palette[ch]))
    return img


def main():
    for (look, part), (template, palette, details) in ICONS.items():
        name = 'r%d' % look if isinstance(look, int) else look
        draw(template, palette, details).save(os.path.join(OUT, '%s_%s.png' % (name, part)))
        draw_dye(template, palette, details, DYE_CHARS.get((look, part), 'hbs')).save(os.path.join(OUT, '%s_%s_dye.png' % (name, part)))
    print('значков:', len(ICONS))


if __name__ == '__main__':
    main()
