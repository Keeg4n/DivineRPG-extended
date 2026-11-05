"""Значки частей брони Странника: плоские 16x16, как у обычной брони, а не рендер модели.

Форма у части общая (шаблоны ниже), у ступени - свои цвета и одна-две детали: у шлемов своя форма (открытый, глухой,
капюшон) и то, что отличает облик на теле (нимб, прорезь-плюс, перья, гребень, рога).
Запуск: python tools/wanderer_icons.py. Пишет в src/main/resources/assets/divinerpgextended/textures/items/wanderer.

Символы шаблона: . пусто, o контур, h блик, b основа, s тень, a отделка; детали ступени рисуются поверх любыми символами
своей палитры.
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
    for (tier, part), (template, palette, details) in sorted(ICONS.items()):
        draw(template, palette, details).save(os.path.join(OUT, 'r%d_%s.png' % (tier, part)))
    print('значков:', len(ICONS))


if __name__ == '__main__':
    main()
