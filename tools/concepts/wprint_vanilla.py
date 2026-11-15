"""Printing Wanderer armor looks as regular Minecraft armor: a card per tier - the character from front/back and both
64x32 textures enlarged. argv: output folder (img/ and textures/ go there too). Source - wanderer_concepts/vanilla."""
import html
import os
import shutil
import sys

from PIL import Image

from wprint_data import CSS

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'wanderer_concepts', 'vanilla')
OUT = sys.argv[1]
e = html.escape
TIERS = [
    (1, 'Hallowed', '#f0c41c', 'A gold cowl with a silver stripe and a red stone, a dark undersuit, a silver cuirass with a red stone, a scarlet cloak on the back, silver boots.'),
    (2, 'Eden: petals and halo', '#d8401c', 'A gold circlet with a stone, the halo lies as a ring on top of the helmet, gold and scarlet petals on the chest and shoulders, scarlet leggings, gold boots.'),
    (3, 'Wildwood: wooden plate armor', '#1a62e0', 'Plate armor of blue wood with grain, a closed helmet with a slit; branching antlers and roots do not fit into ordinary armor.'),
    (4, 'Orichalcum', '#e030d4', 'A faceted crimson cowl with a pink stone, crimson plates, lilac inlays on the belt and thighs.'),
    (5, 'Skythern: gray cloak', '#aeb2ba', 'A white hood with a light blue clasp, gray plate armor, a white cloak on the back.'),
    (6, 'Mortum: hooded shadow', '#ff3020', 'A black hood, red eyes in the darkness under it, black armor with a scarlet stripe, a tattered cloak on the back.'),
    (7, 'Chlorophyte', '#22a818', 'A leaf cowl with bright veins, leaf plates, gray metal on the chest and belt.'),
]


def big(src, dst, k=5):
    t = Image.open(src).convert('RGBA')
    bg = Image.new('RGBA', t.size, (0, 0, 0, 0))
    for y in range(t.height):  # checkerboard under transparency - as in editors
        for x in range(t.width):
            c = 58 if (x + y) % 2 else 70
            bg.putpixel((x, y), (c, c, c + 6, 255))
    bg.alpha_composite(t)
    bg.resize((t.width * k, t.height * k), Image.NEAREST).save(dst)


os.makedirs(os.path.join(OUT, 'img'), exist_ok=True)
os.makedirs(os.path.join(OUT, 'textures'), exist_ok=True)
for r, *_ in TIERS:
    shutil.copy(os.path.join(SRC, 'v_r%d.png' % r), os.path.join(OUT, 'img', 'v_r%d.png' % r))
    for L in (1, 2):
        name = 'wanderer_r%d_layer_%d.png' % (r, L)
        shutil.copy(os.path.join(SRC, name), os.path.join(OUT, 'textures', name))
        big(os.path.join(SRC, name), os.path.join(OUT, 'img', 'v_r%d_l%d.png' % (r, L)))

extra = """
.vcard { background: #1c1920; border: 1px solid #322c38; border-radius: 2mm; overflow: hidden; display: flex; flex: none; margin-top: 4mm; }
.vcard { height: 72mm; }
.vcard .pic { width: 108mm; height: 72mm; position: relative; flex: none; }
.page .foot { margin-top: auto; }
.vcard .pic img { height: 100%; width: 100%; display: block; object-fit: cover; }
.vbody { padding: 4mm 5mm; display: flex; flex-direction: column; gap: 2mm; min-width: 0; }
.tex { display: grid; grid-template-columns: 1fr 1fr; gap: 1mm 2mm; margin-top: auto; }
.tex img { width: 100%; image-rendering: pixelated; border-radius: 1mm; }
.tex span { font-size: 7pt; color: #6e6674; letter-spacing: .06em; text-transform: uppercase; }
"""
parts = ['<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Wanderer armor - like ordinary armor</title>',
         '<link rel="stylesheet" href="fonts/fonts.css">', '<style>%s%s</style></head><body>' % (CSS, extra)]
pages = [TIERS[0:3], TIERS[3:6], TIERS[6:7]]
for n, group in enumerate(pages):
    parts.append('<section class="page">')
    if n == 0:
        parts.append('<h1>Wanderer armor</h1><div class="sub">Like ordinary Minecraft armor</div>')
        parts.append('<p class="lede">The same tier looks, but without a custom model: the ordinary armor model and two 64x32 textures '
                     '(layer_1 - helmet, chestplate, boots; layer_2 - leggings), like vanilla armor. The textures are taken from the 3D looks: '
                     'whatever stuck out (horns, halo, wings, crystal) was laid onto the faces. The texture files are in the textures folder nearby.</p>')
    for r, title, color, desc in group:
        parts.append('<div class="vcard"><div class="pic"><img src="img/v_r%d.png" alt=""><div class="views"><span>front</span><span>back</span></div></div>'
                     '<div class="vbody"><div class="title"><span class="ln" style="color:%s">R%d</span>%s</div><p class="desc">%s</p>'
                     '<div class="tex"><span>layer_1</span><span>layer_2</span><img src="img/v_r%d_l1.png" alt=""><img src="img/v_r%d_l2.png" alt=""></div></div></div>'
                     % (r, color, r, e(title), e(desc), r, r))
    parts.append('<div class="foot"><span>Wanderer armor like ordinary armor</span><span>%d of %d</span></div></section>' % (n + 1, len(pages)))
parts.append('</body></html>')
open(os.path.join(OUT, 'print-vanilla.html'), 'w', encoding='utf-8').write('\n'.join(parts))
print('ok')
