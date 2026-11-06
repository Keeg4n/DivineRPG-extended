"""Лист концептов вилки R1 (A4): облики брони Странника первой ступени по сету, из которого она скована, по три варианта на сет; божественный - как в игре.
Запуск: python wprint_r1.py <out.html>. Картинки ждёт в img/ рядом с HTML: r1v (wslim5.py) и варианты из wr1.py; кадры "спереди" для обзора собираются здесь."""
import html
import os
import sys

from PIL import Image

from wprint_data import CSS

OUT = sys.argv[1]
e = html.escape
IMG = os.path.join(os.path.dirname(os.path.abspath(OUT)), 'img')

LEDE = ('Первую ступень брони Странника можно сковать не только из божественной брони, а из любого финального сета обычного мира. Какой сет принёс, такой облик и получил, '
        'а его свойство остаётся в броне навсегда. На каждый новый сет по три образа, выбрать можно один: например, Тень Б, Бедрок В, Энд А, Порча Б.')

DIVINE = ('r1v', 'Божественная', 'ближний бой', 'Без изменений, этот облик уже в игре. Серебряная кираса с золотым клином, открытый золотой шлем с крылышками и алым камнем, на спине серебряный щит с золотым крестом.')

SETS = [
    ('Тень', 'скорость', '#474747', [
        ('r1t', 'А', 'Ниндзя', 'Зализанные чёрные латы с серым шевроном. Глухой шлем с узкой белой прорезью, на висках крылышки назад, тонкие планки на плечах, предплечьях и пятках. Спина свободна.'),
        ('r1u', 'Б', 'Ворон', 'Шлем с клювом и круглыми белыми глазами, хохолок и пучки перьев на плечах, на груди чешуя перьев. За спиной наискось большой теневой лук с тетивой.'),
        ('r1w', 'В', 'Призрак', 'Одежда в обмотках, светлый шарф, голова в тёмной повязке и белая маска с прищуром. На поясе подсумки, на бедре чехол. За спиной большой сюрикен. Самый простой силуэт из трёх.')]),
    ('Бедрок', 'стойкость', '#7c7c7c', [
        ('r1k', 'А', 'Голем', 'Латы из крапчатой породы, широкие наплечники в две ступени, массивные сапоги. Глухой шлем с рубленым венцом и белыми глазами. Спина свободна.'),
        ('r1l', 'Б', 'Бастион', 'Латы как кладка крепости: швы между камнями, зубцы на плечах и на шлеме, как у башни. Узкая голубая прорезь, руна на груди. За спиной ростовой щит из каменной плиты с той же руной.'),
        ('r1m', 'В', 'Недра', 'Чёрная порода, в трещинах светится лава. Валуны на плечах, рогатый шлем с раскалённой прорезью. На спине каменные шипы веером и раскалённое ядро. Самый яркий из трёх.')]),
    ('Энд', 'пустотный шаг', '#1674d0', [
        ('r1n', 'А', 'Портал', 'Гладкие синие латы с белыми искрами, как у брони Энда в Divine, белые полосы и подошвы. Шлем с ромбом и глазом Края, плавники вверх на висках. На спине кольцо портала с жемчужинами.'),
        ('r1o', 'Б', 'Ходок Края', 'Почти чёрные латы в звёздах с синими светящимися линиями. Длинные глаза во всё лицо, как у эндермена, длинные светящиеся когти. На спине кристалл Края в рамке ромбом.'),
        ('r1q', 'В', 'Дракон', 'Синяя чешуя и светлые пластины на груди, рогатый шлем с мордой, зубами и пурпурными глазами, шипы на плечах. На спине кожистые крылья, раскрыты вверх и в стороны, а не висят плащом.')]),
    ('Порча', 'дальний бой', '#594d9a', [
        ('r1p', 'А', 'Пушкарь', 'Сине-фиолетовые латы с пурпурными пятнами и прожилками, большой глаз на груди. Шлем с тремя глазами Груи и зашитым ртом, костяные шипы. На спине пушка порчи наискось, дуло светится.'),
        ('r1r', 'Б', 'Охотник', 'Широкополая шляпа с пурпурной лентой и пером, лицо в тени, светятся только глаза, низ закрыт шарфом. Тёмная кожа с патронташем, кобура на бедре. Над плечом колчан со светящимися стрелами.'),
        ('r1x', 'В', 'Грю', 'Тёмная плоть, зубастая пасть на груди и вторая на шлеме под огромными глазами, глаза на плечах. Из спины растут щупальца с глазами на концах. Самый жуткий из трёх.')]),
]

EXTRA = """
.tag { font-family: "Golos Text", sans-serif; font-weight: 600; color: #e3a347; margin-right: 2mm; }
.page .grid .card { flex: 1 1 0; }
.blocks { display: grid; grid-template-columns: 1fr 1fr; gap: 4mm; margin-top: 5mm; }
.block { border: 1px solid #322c38; border-radius: 2mm; overflow: hidden; background: #1c1920; }
.block .row { display: grid; grid-template-columns: repeat(3, 1fr); }
.block img { width: 100%; display: block; }
.block .lab { padding: 1.5mm 3mm 2mm; border-top: 1.2mm solid; display: flex; justify-content: space-between; align-items: baseline; }
.block .lab span { font-family: "Pixelify Sans", monospace; color: #ece5da; font-size: 12.5pt; }
.block .lab em { font-style: normal; color: #a39aa6; font-size: 9pt; }
.letters { display: grid; grid-template-columns: repeat(3, 1fr); text-align: center; color: #e3a347; font-weight: 600; font-size: 9.5pt; padding-top: 1mm; }
.divine { flex: 1 1 0; margin-top: 4mm; }
.divine .tag { display: block; margin: 0 0 1mm; font-size: 10pt; }
.head h2, .block .lab span { font-family: "Golos Text", sans-serif; font-weight: 600; }
"""


def front(key):
    """Вид спереди отдельным кадром для обзора."""
    p = Image.open(os.path.join(IMG, key + '.png')).convert('RGB')
    w, h = p.width // 2, p.height
    cut = int(w * 0.13)
    p.crop((cut, int(h * 0.05), w - cut, h)).save(os.path.join(IMG, key + '_f.png'), optimize=True)


def card(m, tag=None):
    key, letter, name, desc = m
    return ('<div class="card"><div class="pic"><img src="img/%s.png" alt=""><div class="views"><span>спереди</span><span>сзади</span></div></div>'
            '<div class="body"><div class="title"><span class="tag">%s</span>%s</div><p class="desc">%s</p></div></div>' % (key, e(tag or letter), e(name), e(desc)))


def foot(n):
    return '<div class="foot"><span>Облики брони Странника, вилка R1</span><span>Страница %d из 5</span></div>' % n


for _, _, _, vs in SETS:
    for v in vs:
        front(v[0])
parts = ['<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Облики брони Странника - вилка R1</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s%s</style></head><body>' % (CSS, EXTRA)]
parts.append('<section class="page"><h1>Облики брони Странника</h1><div class="sub">Первая ступень: пять сетов и варианты</div><p class="lede">%s</p><div class="blocks">' % e(LEDE))
for name, role, color, vs in SETS:
    parts.append('<div class="block"><div class="row">%s</div><div class="letters">%s</div><div class="lab" style="border-color:%s"><span>%s</span><em>%s</em></div></div>' % (
        ''.join('<img src="img/%s_f.png" alt="">' % v[0] for v in vs), ''.join('<div>%s</div>' % e(v[1]) for v in vs), color, e(name), e(role)))
parts.append('</div><div class="card divine">%s</div>' % card((DIVINE[0], '', DIVINE[1], DIVINE[3]), tag=DIVINE[2]).replace('<div class="card">', '<div style="display:flex;width:100%;min-height:0">', 1))
parts.append(foot(1) + '</section>')
for n, (name, role, color, vs) in enumerate(SETS, 2):
    parts.append('<section class="page"><div class="head"><h2>%s</h2><span class="meta">%s</span><span class="chips"><i style="background:%s"></i></span></div><div class="grid">' % (e(name), e(role), color))
    parts += [card(v) for v in vs]
    parts.append('</div>' + foot(n) + '</section>')
parts.append('</body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
