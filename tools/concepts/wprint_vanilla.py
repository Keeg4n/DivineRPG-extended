"""Печать обликов брони Странника как обычной брони Minecraft: по карточке на ступень - персонаж спереди/сзади и обе
текстуры 64x32 крупно. argv: выходная папка (туда же img/ и textures/). Источник - wanderer_concepts/vanilla."""
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
    (1, 'Освящённая', '#f0c41c', 'Золотой капюшон с серебряной полосой и красным камнем, тёмный поддоспешник, серебряная кираса с красным камнем, алый плащ на спине, серебряные сапоги.'),
    (2, 'Эдем: лепестки и нимб', '#d8401c', 'Золотой обруч с камнем, нимб лёг кольцом на верх шлема, золотые и алые лепестки на груди и плечах, алые поножи, золотые сапоги.'),
    (3, 'Дикий лес: деревянные латы', '#1a62e0', 'Латы из синего дерева с волокнами, глухой шлем с прорезью; ветвистые рога и корни в обычную броню не помещаются.'),
    (4, 'Орихалковая', '#e030d4', 'Гранёный малиновый капюшон с розовым камнем, малиновые пластины, сиреневые вставки на поясе и бёдрах.'),
    (5, 'Небесный терн: серый плащ', '#aeb2ba', 'Белый капюшон с голубой застёжкой, серые латы, белый плащ на спине.'),
    (6, 'Мортум: тень в капюшоне', '#ff3020', 'Чёрный капюшон, в темноте под ним красные глаза, чёрная броня с алой полосой, рваный плащ на спине.'),
    (7, 'Хлорофитовая', '#22a818', 'Листовой капюшон с яркими прожилками, листовые пластины, серый металл на груди и поясе.'),
]


def big(src, dst, k=5):
    t = Image.open(src).convert('RGBA')
    bg = Image.new('RGBA', t.size, (0, 0, 0, 0))
    for y in range(t.height):  # шахматка под прозрачным - как в редакторах
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
parts = ['<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Броня Странника - как обычная броня</title>',
         '<link rel="stylesheet" href="fonts/fonts.css">', '<style>%s%s</style></head><body>' % (CSS, extra)]
pages = [TIERS[0:3], TIERS[3:6], TIERS[6:7]]
for n, group in enumerate(pages):
    parts.append('<section class="page">')
    if n == 0:
        parts.append('<h1>Броня Странника</h1><div class="sub">Как обычная броня Minecraft</div>')
        parts.append('<p class="lede">Те же облики ступеней, но без своей модели: обычная модель брони и две текстуры 64x32 '
                     '(layer_1 - шлем, нагрудник, ботинки; layer_2 - поножи), как у ванильной брони. Текстуры сняты с 3D-обликов: '
                     'что торчало (рога, нимб, крылья, кристалл), легло на грани. Файлы текстур - в папке textures рядом.</p>')
    for r, title, color, desc in group:
        parts.append('<div class="vcard"><div class="pic"><img src="img/v_r%d.png" alt=""><div class="views"><span>спереди</span><span>сзади</span></div></div>'
                     '<div class="vbody"><div class="title"><span class="ln" style="color:%s">R%d</span>%s</div><p class="desc">%s</p>'
                     '<div class="tex"><span>layer_1</span><span>layer_2</span><img src="img/v_r%d_l1.png" alt=""><img src="img/v_r%d_l2.png" alt=""></div></div></div>'
                     % (r, color, r, e(title), e(desc), r, r))
    parts.append('<div class="foot"><span>Броня Странника как обычная броня</span><span>%d из %d</span></div></section>' % (n + 1, len(pages)))
parts.append('</body></html>')
open(os.path.join(OUT, 'print-vanilla.html'), 'w', encoding='utf-8').write('\n'.join(parts))
print('ok')
