"""Четвёртый раунд концептов брони Странника (A4): эталон Ботании и размеры, затем по странице на ступень - было и стало.
Запуск: python wprint_slim.py <out.html>. Картинки ждёт в img/ рядом с HTML: r1s..r7s, прежние облики, bot_terrasteel, bot_manasteel, bot_elementium."""
import html
import sys

from wprint_data import CSS

OUT = sys.argv[1]
e = html.escape

LEDE = ('Прежняя броня раздута: торс и руки шире тела на целую единицу с каждой стороны, поверх ещё плиты, наплечники торчат выше шеи. Отсюда холодильник. '
        'Здесь те же образы ступеней на пропорциях Ботании: броня идёт по телу, грудь сужается в талию, между наплечником и наручем и над сапогом '
        'виден поддоспешник, кисть открыта. Шлем - шапка, щёки и короткий назатыльник, лицо видно. Капюшоны оставлены там, где они и есть образ: R5 и R6. '
        'Текстура как будет в игре; слева вид спереди, справа сзади.')

BOTANIA = [
    ('bot_terrasteel', 'Терасталь', 'Грудь, талия, пояс, наруч во всё предплечье; ветки и листья на шлеме, плечах и сапогах.'),
    ('bot_manasteel', 'Манасталь', 'Самая лёгкая: наруч только снаружи руки, зазоры на руке и над сапогом, шапка со щёчками.'),
    ('bot_elementium', 'Элементий', 'Открытый шлем с плоскими крылышками наружу, лёгкие крылья за спиной.'),
]

SIZES = [  # деталь, было, стало, Ботания (единицы модели; тело игрока: торс 8 x 4, рука и нога 4 x 4, голова 8)
    ('Грудь, ширина x толщина', '10 x 6.5', '9.2 x 6', '9.4 x 6.4'),
    ('Талия', '10 x 6, без сужения', '8.4 x 5', '8.4 x 5.4'),
    ('Наплечник: выступ от руки', '3', '1.1', '1.2'),
    ('Наплечник: выше плеча', '2.4', '0.2', '0.2'),
    ('Предплечье', '5.4 x 5.4', '4.4 x 4.4', '4.4 x 4.4'),
    ('Бедро', '5.2 x 5.2', '4.4 x 4.4', '4.4 x 4.4'),
    ('Сапог', '5.6 x 5.6', '4.4 x 5.4', '4.4 x 5.4'),
    ('Голова, ширина', '11.2 (капюшон)', '9.1 (шлем), 9.2 (капюшон)', '8.4'),
]

TIERS = [
    (1, 'Божественная', '#ccaf3b', 'r1tk', 'r1s', 'Освящённая', 'Серебряная кираса с золотым клином и алым камнем, тёмная талия и поддоспешник, золотые наплечники и наручи. '
     'Вместо капюшона открытый золотой шлем с серебряным гребнем, плоскими крылышками и камнем на лбу. Тонкий алый плащ.'),
    (2, 'Эдем', '#d8401c', 'r2c', 'r2s', 'Лепестки и нимб', 'Золотые и алые лепестки лежат на груди и плечах плоско, а не торчат. Алый поддоспешник, золотые наручи только снаружи руки. '
     'Золотой венец с лепестками и огненным камнем, нимб ближе к голове.'),
    (3, 'Дикий лес', '#1a62e0', 'r3b', 'r3s', 'Деревянные латы', 'Латы из синего дерева по телу, тёмный поддоспешник. Глухой шлем заменён открытым, рога-ветви тоньше. Корни у сапог убраны.'),
    (4, 'Апалачия', '#c020d8', 'r4tk', 'r4s', 'Орихалковая', 'Гранёная малиновая кираса с сиреневым камнем, тёмная талия, короткие кристаллы на плечах. '
     'Вместо капюшона открытый шлем с веером кристаллов. Парящие лепестки вокруг тела убраны.'),
    (5, 'Небесный терн', '#aeb2ba', 'r5i', 'r5s', 'Серый плащ', 'Серые латы и наручи снаружи руки. Белый капюшон по голове и узкий воротник вместо накидки на плечах, белый плащ с голубой каймой и застёжка.'),
    (6, 'Мортум', '#ff3020', 'r6a', 'r6s', 'Тень в капюшоне', 'Капюшон по голове, лицо в темноте, красные глаза. Тёмная кираса с красной каймой, рваный плащ уже и короче, цепь на поясе.'),
    (7, 'Галит', '#50e010', 'r7tk', 'r7s', 'Хлорофитовая', 'Листовые латы с серыми вставками, тёмная талия, листья на плечах и за спиной лежат ближе к телу. '
     'Вместо капюшона открытый шлем с листьями, тонкие серые рога и камень на лбу. Парящий кристалл убран.'),
]

EXTRA = """
.bot { display: flex; flex-direction: column; gap: 2.5mm; }
.bot .card { height: 42mm; flex: none; }
table { border-collapse: collapse; width: 100%; font-size: 10pt; margin-top: 1mm; }
th { text-align: left; color: #e3a347; font-weight: 600; padding: 1.2mm 2mm; border-bottom: 1px solid #322c38; }
td { padding: 1.1mm 2mm; border-bottom: 1px solid #26222b; color: #a39aa6; }
td:first-child { color: #ece5da; }
td.new { color: #ece5da; font-weight: 600; }
.tag { font-family: "Pixelify Sans", monospace; color: #e3a347; margin-right: 2mm; }
.grid .card { flex-direction: column; }
.grid .pic { flex: 1 1 0; min-height: 0; height: auto; width: 100%; aspect-ratio: auto; }
.grid .pic img { object-fit: contain; }
.grid .body { flex: none; padding: 3mm 5mm 3.5mm; }
"""


def card(key, tag, title, desc):
    return ('<div class="card"><div class="pic"><img src="img/%s.png" alt=""><div class="views"><span>спереди</span><span>сзади</span></div></div>'
            '<div class="body"><div class="title"><span class="tag">%s</span>%s</div><p class="desc">%s</p></div></div>' % (key, e(tag), e(title), e(desc)))


parts = ['<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Облики брони Странника - четвёртый раунд</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s%s</style></head><body>' % (CSS, EXTRA)]
parts.append('<section class="page"><h1>Облики брони Странника</h1><div class="sub">Четвёртый раунд: стройные, как в Ботании</div><p class="lede">%s</p>' % e(LEDE))
parts.append('<h3>Эталон: настоящие модели брони Ботании</h3><div class="bot">')
parts += [card(k, '', t, d) for k, t, d in BOTANIA]
parts.append('</div><h3>Размеры</h3><table><tr><th>Деталь</th><th>Было</th><th>Стало</th><th>Ботания</th></tr>')
parts += ['<tr><td>%s</td><td>%s</td><td class="new">%s</td><td>%s</td></tr>' % tuple(e(c) for c in row) for row in SIZES]
parts.append('</table></section>')
for r, world, color, old, new, title, desc in TIERS:
    parts.append('<section class="page"><div class="head"><h2><span class="rank">R%d</span> %s</h2><span class="chips"><i style="background:%s"></i></span></div><div class="grid">' % (r, e(world), color))
    parts.append(card(old, 'Было', title, 'Как сейчас в игре.'))
    parts.append(card(new, 'Стало', title, desc))
    parts.append('</div><div class="foot"><span>Облики брони Странника, четвёртый раунд</span><span>R%d из 7</span></div></section>' % r)
parts.append('</body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
