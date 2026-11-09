"""Лист концептов R8 (A4): ступень за прохождение Ветеи, три облика на выбор; рядом R7 как в игре для сравнения.
Запуск: python wprint_r8.py <out.html>. Картинки ждёт в img/ рядом с HTML: r8a..r8c (wr8.py) и r7z (wslim5.py)."""
import html
import os
import sys

from PIL import Image

from wprint_data import CSS

OUT = sys.argv[1]
e = html.escape
IMG = os.path.join(os.path.dirname(os.path.abspath(OUT)), 'img')

LEDE = ('Восьмая ступень брони Странника - за прохождение Ветеи, после Леди Луны. Только на ней открывается Венец: одно свойство на выбор - '
        'полёт, восстановление здоровья или поток арканы. Полёт полноценный, без ограничений. На R7 этих трёх перков больше нет. '
        'Ниже три облика R8, выбрать один: например, R8 Б.')

R7 = ('r7z', 'R7 сейчас', 'Халит', 'Ступень перед R8, как в игре: хлорофитовая броня с острым шлемом, рогами и чешуёй. Для сравнения силуэта и цвета.')

VARIANTS = [
    ('r8a', 'А', 'Призрак', '#8ccff4', 'В духе спектральной брони Террарии. Бледно-голубые латы со светящимися линиями и ромбом на груди, наплечники со светящейся кромкой. '
     'От пояса - рваные призрачные ленты, светлеют и светятся к концам. Тёмно-синий капюшон, лицо - гладкая белая маска с узкими голубыми прорезями. За спиной три огонька с хвостами.'),
    ('r8b', 'Б', 'Лунная', '#b89ae8', 'Тема Леди Луны. Серебряные латы с сиреневым, на груди светящийся полумесяц, наплечники с серпом-гребнем. '
     'Гладкий шлем с сиреневым забралом, над ним парит полумесяц рожками вверх. За спиной большой серп луны со светящейся кромкой.'),
    ('r8c', 'В', 'Сновидец', '#e3c46a', 'Ветея - мир снов. Индиго в звёздах, золотая нить по краям, на груди ромб с бирюзовым камнем. '
     'Гладкая маска с закрытыми глазами, на лбу открытый третий глаз, на висках золотые пластины. На спине ловец снов: золотое кольцо, нити, бусина и три пера.'),
]

EXTRA = """
.tag { font-family: "Golos Text", sans-serif; font-weight: 600; color: #e3a347; margin-right: 2mm; }
.page .grid .card { flex: 1 1 0; }
.trio { display: grid; grid-template-columns: repeat(3, 1fr); gap: 3mm; margin-top: 5mm; }
.trio .t { border: 1px solid #322c38; border-radius: 2mm; overflow: hidden; background: #1c1920; }
.trio img { width: 100%; display: block; }
.trio .lab { padding: 1.5mm 3mm 2mm; border-top: 1.2mm solid; display: flex; gap: 2mm; align-items: baseline; font-size: 11pt; }
.trio .lab b { color: #e3a347; font-weight: 600; }
.ref { margin-top: 4mm; display: flex; }
.head h2 { font-family: "Golos Text", sans-serif; font-weight: 600; }
.sub { font-family: "Golos Text", sans-serif; font-weight: 600; }
.solo { border: 1px solid #322c38; border-radius: 2mm; overflow: hidden; background: #1c1920; margin-top: 3mm; }
.solo img { width: 100%; display: block; }
.solo .vv { display: grid; grid-template-columns: 1fr 1fr; text-align: center; color: #a39aa6; font-size: 8.5pt; padding: 1.2mm 0 1.6mm; letter-spacing: .08em; text-transform: uppercase; }
.solotext { margin-top: 4mm; padding-left: 3mm; font-size: 11pt; line-height: 1.5; color: #d9d2cc; }

.crown { display: grid; grid-template-columns: repeat(3, 1fr); gap: 3mm; margin-top: 4mm; }
.crown div { border-top: 1mm solid #e3a347; padding-top: 1.5mm; font-size: 9pt; color: #a39aa6; line-height: 1.35; }
.crown b { display: block; color: #ece5da; font-size: 10.5pt; font-weight: 600; }
"""


def front(key):
    """Вид спереди отдельным кадром для обзора."""
    p = Image.open(os.path.join(IMG, key + '.png')).convert('RGB')
    w, h = p.width // 2, p.height
    cut = int(w * 0.13)
    p.crop((cut, int(h * 0.05), w - cut, h)).save(os.path.join(IMG, key + '_f.png'), optimize=True)


def card(key, tag, name, desc):
    return ('<div class="card"><div class="pic"><img src="img/%s.png" alt=""><div class="views"><span>спереди</span><span>сзади</span></div></div>'
            '<div class="body"><div class="title"><span class="tag">%s</span>%s</div><p class="desc">%s</p></div></div>' % (key, e(tag), e(name), e(desc)))


def foot(n):
    return '<div class="foot"><span>Облики брони Странника, R8</span><span>Страница %d из 4</span></div>' % n


for v in VARIANTS:
    front(v[0])
front(R7[0])
parts = ['<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Облики брони Странника - R8</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s%s</style></head><body>' % (CSS, EXTRA)]
parts.append('<section class="page"><h1>Облики брони Странника</h1><div class="sub">R8: за прохождение Ветеи</div><p class="lede">%s</p>' % e(LEDE))
parts.append('<div class="crown"><div><b>Полёт</b>Как в творческом режиме, без ограничений.</div><div><b>Восстановление</b>Здоровье само растёт.</div><div><b>Поток арканы</b>Аркана восстанавливается быстрее.</div></div>')
parts.append('<div class="trio">%s</div>' % ''.join('<div class="t"><img src="img/%s_f.png" alt=""><div class="lab" style="border-color:%s"><b>%s</b>%s</div></div>' % (k, c, e(l), e(n)) for k, l, n, c, _ in VARIANTS))
parts.append('<div class="ref"><div class="t" style="width:52mm;border:1px solid #322c38;border-radius:2mm;overflow:hidden;background:#1c1920"><img src="img/%s_f.png" alt="" style="width:100%%;display:block"></div>'
             '<p class="lede" style="margin:0 0 0 4mm;align-self:center"><b style="color:#e3a347">%s, %s.</b> %s</p></div>' % (R7[0], e(R7[1]), e(R7[2]), e(R7[3])))
parts.append(foot(1) + '</section>')
for n, v in enumerate(VARIANTS, 2):
    parts.append('<section class="page"><div class="head"><h2>Вариант %s: %s</h2></div>'
                 '<div class="solo"><img src="img/%s.png" alt=""><div class="vv"><span>спереди</span><span>сзади</span></div></div>'
                 '<p class="lede solotext" style="border-left:1.2mm solid %s">%s</p>%s</section>' % (e(v[1]), e(v[2]), v[0], v[3], e(v[4]), foot(n)))
parts.append('</body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
