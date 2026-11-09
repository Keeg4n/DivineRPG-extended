"""Лист R8, третий раунд Призрака (A4): глубокий капюшон, три формы выреза. Рядом Г как было.
Запуск: python wprint_r8c.py <out.html>. Картинки ждёт в img/ рядом с HTML: r8a, r8d, r8g..r8i (wr8.py)."""
import html
import os
import sys

from PIL import Image

from wprint_data import CSS

OUT = sys.argv[1]
e = html.escape
IMG = os.path.join(os.path.dirname(os.path.abspath(OUT)), 'img')

LEDE = ('Г ближе всего, но тьма с глазами стояла на краю капюшона, а вырез был квадратным. Теперь капюшон глубокий: козырёк выступает вперёд, '
        'внутри тёмная подкладка, а тьма и глаза - в глубине, и при повороте головы видно, что они далеко от края. Вырез скруглён. Различается форма выреза. Выбрать букву: Ж, З или И.')

WAS = ('r8d', 'Г', 'Как было', 'Тьма и глаза вровень с краем, вырез квадратный.')
VARIANTS = [
    ('r8g', 'Ж', 'Арка', 'Вырез аркой: полукруг сверху, прямые бока до низа. По краю выреза светящаяся кромка. В глубине тьма и два круглых голубых глаза.'),
    ('r8h', 'З', 'Стрельчатая арка', 'Вырез острой аркой, как в готическом окне, без светящейся кромки - капюшон строже. В глубине тьма и два узких глаза-щели.'),
    ('r8i', 'И', 'Овал', 'Вырез овалом, снизу закрыт подбородком капюшона, по краю светящаяся кромка, сверху небольшой навес. В глубине тьма и два круглых глаза.'),
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
    return '<div class="foot"><span>Облики брони Странника, R8 - Призрак, третий раунд</span><span>Страница %d из 4</span></div>' % n


for v in [WAS] + VARIANTS:
    front(v[0])
parts = ['<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Облики брони Странника - R8, Призрак</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s%s</style></head><body>' % (CSS, EXTRA)]
cells = ''.join('<div class="t%s"><img src="img/%s_f.png" alt=""><div class="lab"><b>%s</b>%s<div>%s</div></div></div>' % (' was' if k == WAS[0] else '', k, e(l), e(n), e(d))
                for k, l, n, d in [WAS] + VARIANTS)
parts.append('<section class="page"><h1>Облики брони Странника</h1><div class="sub">R8 - Призрак, третий раунд</div><p class="lede">%s</p><div class="four">%s</div>%s</section>' % (e(LEDE), cells, foot(1)))
for n, (k, l, name, d) in enumerate(VARIANTS, 2):
    parts.append('<section class="page"><div class="head"><h2>Вариант %s: %s</h2></div><div class="solo"><img src="img/%s.png" alt=""><div class="vv"><span>спереди</span><span>сзади</span></div></div>'
                 '<p class="lede solotext">%s</p>%s</section>' % (e(l), e(name), k, e(d), foot(n)))
parts.append('</body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
