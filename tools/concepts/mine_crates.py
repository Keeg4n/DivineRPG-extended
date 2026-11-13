"""Значки шахтёрских ящиков: ящик пещер + две кучки руды своего яруса на крышке (плоские 16x16).

python3 tools/concepts/mine_crates.py [папка превью]
"""
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'divinerpgextended', 'textures', 'items')
BASE = os.path.join(ROOT, 'crate_cave.png')

# руда: (светлая крапина, тёмная крапина)
ORES = {
    'coal': ((70, 70, 70), (24, 24, 24)),
    'iron': ((226, 192, 170), (175, 142, 119)),
    'gold': ((252, 238, 75), (204, 150, 22)),
    'lapis': ((60, 100, 210), (24, 54, 140)),
    'diamond': ((120, 240, 240), (38, 170, 180)),
    'redstone': ((255, 40, 40), (160, 0, 0)),
}
TIERS = {'mine_upper': ('coal', 'iron'), 'mine_middle': ('gold', 'lapis'), 'mine_deep': ('diamond', 'redstone')}
STONE_LIGHT, STONE, STONE_DARK = (150, 150, 150), (122, 122, 122), (90, 90, 90)


def heap(px, x, ore):
    """Кучка руды на крышке: два ряда над краем ящика, камень с крапинами."""
    light, dark = ORES[ore]
    px[x + 1, 1] = STONE_LIGHT + (255,)
    px[x + 2, 1] = light + (255,)
    px[x, 2] = STONE_DARK + (255,)
    px[x + 1, 2] = dark + (255,)
    px[x + 2, 2] = STONE + (255,)
    px[x + 3, 2] = light + (255,)


def main():
    prev = sys.argv[1] if len(sys.argv) > 1 else None
    base = Image.open(BASE).convert('RGBA')
    sheet = Image.new('RGBA', (16 * 4 * 8 + 24, 16 * 8 + 16), (40, 40, 40, 255))
    sheet.paste(base.resize((128, 128), Image.NEAREST), (8, 8))
    for k, (name, (a, b)) in enumerate(TIERS.items()):
        im = base.copy()
        px = im.load()
        heap(px, 3, a)
        heap(px, 9, b)
        im.save(os.path.join(ROOT, 'crate_%s.png' % name))
        sheet.paste(im.resize((128, 128), Image.NEAREST), (8 + (k + 1) * 136, 8))
    if prev:
        sheet.save(os.path.join(prev, 'mine_crates_preview.png'))
    print('готово:', ', '.join(TIERS))


if __name__ == '__main__':
    main()
