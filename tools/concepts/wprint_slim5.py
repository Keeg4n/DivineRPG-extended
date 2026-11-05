"""Пятый раунд концептов брони Странника (A4): что изменилось по замечаниям, было и стало по ступеням, выбор варианта R7; R2 без изменений.
Запуск: python wprint_slim5.py <out.html>. Картинки ждёт в img/ рядом с HTML: r1s..r7s из четвёртого раунда, r1v, r3v, r4v, r5v, r6v, r7x, r7y."""
import html
import sys

from wprint_data import CSS

OUT = sys.argv[1]
e = html.escape

LEDE = ('Правки к четвёртому раунду: тело оставлено, переделаны головы. Плащей больше нет, они конфликтуют с плащами на сервере; вместо них на спине вещь по теме мира. '
        'Эдем не менялся. Галит переделан по рисунку хлорофитовой брони.')

TIERS = [
    (1, 'Божественная', '#ccaf3b', 'r1s', 'r1v', 'Щит вместо плаща', 'Всё как было, только плаща нет: на спине серебряный щит с золотым крестом и алым камнем.'),
    (3, 'Дикий лес', '#1a62e0', 'r3s', 'r3v', 'Деревянные латы', 'Текстура теперь понятная: доски из синего дерева со швами. Глухой шлем как в первом облике, но уже по голове; прорезь-плюс прозрачная, сквозь неё виден скин. Рога-ветви те же.'
     'Листья на плечах, на груди светится знак. Светлое дерево на поясе и наколенниках, за спиной посох: навершие выходит над плечом.'),
    (4, 'Апалачия', '#c020d8', 'r4s', 'r4v', 'Индейский стиль', 'Вышитая налобная лента и убор из широких перьев веером, у висков нити бус с перьями. Нагрудник рядами бус, зигзаги на поясе и наручах, бахрома снаружи рук и бёдер, '
     'мягкие сапоги, перья на плечах. На спине веер перьев с розеткой. Цвета мира те же.'),
    (5, 'Небесный терн', '#aeb2ba', 'r5s', 'r5v', 'Шлем и крылья', 'Вместо капюшона закрытый шлем: купол, глубокие щёки с Т-образным вырезом, золотые бровь и наносник, голубой гребень от лба до затылка, рёбра-крылья на висках. '
     'Тело ярче: белые латы с небесной каймой и золотом, синий поддоспешник, крылышки на пятках. На спине сложенные крылья.'),
    (6, 'Мортум', '#ff3020', 'r6s', 'r6v', 'Капюшон культиста', 'Капюшон устроен как у робы культистов Таума: сзади сужается ступенями с нарастающим наклоном, ткань в складках, лицо в глубокой тёмной арке с красной окантовкой, красные глаза. '
     'На спине хребет и рёбра из костей, между ними тлеет красное.'),
]

HALITE = [
    ('r7s', 'Было', 'Четвёртый раунд', 'Открытый шлем с листьями и тонкими рогами.'),
    ('r7z', 'Стало', 'Хлорофитовая', 'По рисунку хлорофитовой брони: закрытый шлем с острым шпилем, чёрный плюс с красными глазами, большие закрученные рога. Высокий заострённый воротник, плоские наплечники, '
     'овалы на груди, чешуя на животе, бирюзовые вставки, светлая повязка спереди. На руках кругляш, тёмное кольцо с мехом и когти, на коленях лаймовые ромбы, у щиколоток мех, сапоги ярко-лаймовые. На спине три острых листа вместо плаща.'),
]

EXTRA = """
.tag { font-family: "Pixelify Sans", monospace; color: #e3a347; margin-right: 2mm; }
.grid .card { flex-direction: column; }
.grid .pic { flex: 1 1 0; min-height: 0; height: auto; width: 100%; aspect-ratio: auto; }
.grid .pic img { object-fit: contain; }
.grid .body { flex: none; padding: 3mm 5mm 3.5mm; }
"""


def card(key, tag, title, desc):
    return ('<div class="card"><div class="pic"><img src="img/%s.png" alt=""><div class="views"><span>спереди</span><span>сзади</span></div></div>'
            '<div class="body"><div class="title"><span class="tag">%s</span>%s</div><p class="desc">%s</p></div></div>' % (key, e(tag), e(title), e(desc)))


parts = ['<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Облики брони Странника - пятый раунд</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s%s</style></head><body>' % (CSS, EXTRA)]
first = True
for r, world, color, old, new, title, desc in TIERS:
    parts.append('<section class="page">')
    if first:
        parts.append('<h1>Облики брони Странника</h1><div class="sub">Пятый раунд: шлемы и спина</div><p class="lede">%s</p><div style="height:5mm"></div>' % e(LEDE))
        first = False
    parts.append('<div class="head"><h2><span class="rank">R%d</span> %s</h2><span class="chips"><i style="background:%s"></i></span></div><div class="grid">' % (r, e(world), color))
    parts.append(card(old, 'Было', 'Четвёртый раунд', 'С плащом или прежней головой.' if r != 1 else 'С плащом.'))
    parts.append(card(new, 'Стало', title, desc))
    parts.append('</div><div class="foot"><span>Облики брони Странника, пятый раунд</span><span>R%d из 7</span></div></section>' % r)
parts.append('<section class="page"><div class="head"><h2><span class="rank">R7</span> Галит</h2><span class="chips"><i style="background:#50e010"></i></span></div><div class="grid">')
parts += [card(k, tag, t, d) for k, tag, t, d in HALITE]
parts.append('</div><div class="foot"><span>Облики брони Странника, пятый раунд</span><span>R7 из 7</span></div></section></body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
