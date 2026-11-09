"""Лист R8, второй раунд Призрака (A4): тело и ленты как у А, капюшон цвета брони, три лица. Рядом А как было.
Запуск: python wprint_r8b.py <out.html>. Картинки ждёт в img/ рядом с HTML: r8a, r8d..r8f (wr8.py)."""
import html
import os
import sys

from PIL import Image

from wprint_data import CSS

OUT = sys.argv[1]
e = html.escape
IMG = os.path.join(os.path.dirname(os.path.abspath(OUT)), 'img')

LEDE = ('Призрак понравился, но маска странная, а капюшон должен быть в цвет брони. Тело, ленты и огоньки за спиной оставлены как у А, '
        'капюшон теперь бледно-голубой, как латы. Различается только лицо. Выбрать букву: Г, Д или Е.')

WAS = ('r8a', 'А', 'Как было', 'Тёмно-синий капюшон, белая гладкая маска с прорезями.')
VARIANTS = [
    ('r8d', 'Г', 'Пустота с глазами', 'Капюшон в цвет брони со светящейся кромкой по краю. Лица нет: внутри тёмная пустота, в ней два голубых горящих глаза. Ближе всего к спектральному капюшону Террарии.'),
    ('r8e', 'Д', 'Лицо из света', 'Капюшон в цвет брони, лицо - светящийся сгусток, на нём тёмные глаза и рот, как у призрака. Над лбом голубой камень. Обратное Г: светлое лицо, тёмные глаза.'),
    ('r8f', 'Е', 'Открытое лицо', 'Капюшон в цвет брони открыт спереди: видно лицо игрока. На лбу светящийся обруч с огоньком, низ лица закрыт шарфом в цвет брони с голубой кромкой.'),
]

EXTRA = """
.four { display: grid; grid-template-columns: repeat(4, 1fr); gap: 2.5mm; margin-top: 5mm; }
.four .t { border: 1px solid #322c38; border-radius: 2mm; overflow: hidden; background: #1c1920; }
.four img { width: 100%; display: block; }
.four .lab { padding: 1.4mm 2.4mm 2mm; border-top: 1.2mm solid #8ccff4; font-size: 10pt; }
.four .lab b { color: #e3a347; font-weight: 600; margin-right: 1.5mm; }
.four .lab div { color: #a39aa6; font-size: 8pt; line-height: 1.3; margin-top: .6mm; }
.four .was { opacity: .75; }
.four .was .lab { border-top-color: #4a4458; }
.head h2 { font-family: "Golos Text", sans-serif; font-weight: 600; }
.sub { font-family: "Golos Text", sans-serif; font-weight: 600; }
.solo { border: 1px solid #322c38; border-radius: 2mm; overflow: hidden; background: #1c1920; margin-top: 3mm; }
.solo img { width: 100%; display: block; }
.solo .vv { display: grid; grid-template-columns: 1fr 1fr; text-align: center; color: #a39aa6; font-size: 8.5pt; padding: 1.2mm 0 1.6mm; letter-spacing: .08em; text-transform: uppercase; }
.solotext { margin-top: 4mm; padding-left: 3mm; font-size: 11pt; line-height: 1.5; color: #d9d2cc; border-left: 1.2mm solid #8ccff4; }
"""


def front(key):
    p = Image.open(os.path.join(IMG, key + '.png')).convert('RGB')
    w, h = p.width // 2, p.height
    cut = int(w * 0.13)
    p.crop((cut, int(h * 0.05), w - cut, h)).save(os.path.join(IMG, key + '_f.png'), optimize=True)


def foot(n):
    return '<div class="foot"><span>Облики брони Странника, R8 - Призрак, второй раунд</span><span>Страница %d из 4</span></div>' % n


for v in [WAS] + VARIANTS:
    front(v[0])
parts = ['<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Облики брони Странника - R8, Призрак</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s%s</style></head><body>' % (CSS, EXTRA)]
cells = ''.join('<div class="t%s"><img src="img/%s_f.png" alt=""><div class="lab"><b>%s</b>%s<div>%s</div></div></div>' % (' was' if k == 'r8a' else '', k, e(l), e(n), e(d))
                for k, l, n, d in [WAS] + VARIANTS)
parts.append('<section class="page"><h1>Облики брони Странника</h1><div class="sub">R8 - Призрак, второй раунд</div><p class="lede">%s</p><div class="four">%s</div>%s</section>' % (e(LEDE), cells, foot(1)))
for n, (k, l, name, d) in enumerate(VARIANTS, 2):
    parts.append('<section class="page"><div class="head"><h2>Вариант %s: %s</h2></div><div class="solo"><img src="img/%s.png" alt=""><div class="vv"><span>спереди</span><span>сзади</span></div></div>'
                 '<p class="lede solotext">%s</p>%s</section>' % (e(l), e(name), k, e(d), foot(n)))
parts.append('</body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
