"""Печатная версия галереи концептов брони Странника (A4 альбом): обложка + по листу на ступень. Пишет print.html рядом с img/."""
import html
import sys

OUT = sys.argv[1]

LINES = [
    ('А', 'Путник', 'дорожная одежда, капюшон, плащ. С каждой ступенью брони больше, и мир оставляет на ней свои следы.'),
    ('Б', 'Рыцарь миров', 'латы, которые с каждой ступенью тяжелеют. Сильнее всего меняется шлем.'),
    ('В', 'Дух мира', 'броня прорастает материалом мира: рудой, корой, кристаллами, костью, светом.'),
]
TIERS = [
    (1, 'Реалмит', 'из реалмита и элитного реалмита, босс - Древняя сущность', ['#c8642c', '#7a3a18', '#d8b04a'], [
        ('А', 'Путник', 'Кожаная куртка с медными наплечниками, капюшон с накидкой, скатка на спине, латунный компас на груди.'),
        ('Б', 'Рыцарь', 'Бригантина с медными заклёпками, кольчужные рукава и поножи, шлем-шапель с широкими полями и кольчужная бармица.'),
        ('В', 'Рудная броня', 'Броня как кусок руды: в трещинах тлеет оранжевым, на груди горит ядро, в прорези шлема светятся глаза.')]),
    (2, 'Эдем', 'босс - Великий кадиллион Эдема', ['#d8401c', '#f0ede6', '#f0c040'], [
        ('А', 'Плащ заката', 'Белая туника с красной перевязью, красный капюшон и плащ с белой каймой, светится застёжка-солнце.'),
        ('Б', 'Солнечный рыцарь', 'Белые латы с красной каймой, солнечный диск на груди и наплечниках, на шлеме гребень из золотых лучей.'),
        ('В', 'Лепестки и нимб', 'Золотые и алые лепестки на груди и плечах, золотой обруч с камнем, над головой светится нимб.')]),
    (3, 'Дикий лес', 'босс - Великий кадиллион Дикого леса', ['#1a62e0', '#0e2f78', '#8ae8ff'], [
        ('А', 'Лиственный плащ', 'Капюшон и рваный плащ из листьев, рожки-веточки, на поясе фонарь со светлячками.'),
        ('Б', 'Деревянные латы', 'Латы из синего дерева, глухой шлем с ветвистыми рогами, корни у ботинок.'),
        ('В', 'Древень', 'Кора и мох, на плечах и голове светятся синие грибы, глаза горят из коры.')]),
    (4, 'Апалачия', 'босс - Великий кадиллион Апалачии', ['#c020d8', '#5a1078', '#f090ff'], [
        ('А', 'Вуаль', 'Пурпурный плащ и капюшон, светлая вуаль на лице, на наручах светятся кристаллы.'),
        ('Б', 'Гранёные латы', 'Пурпурные латы с гранями, острые наплечники и плавники на шлеме, кристалл во лбу.'),
        ('В', 'Друзы', 'Тёмная броня, из плеч, спины и головы растут светящиеся друзы кристаллов.')]),
    (5, 'Небесный терн', 'босс - Великий кадиллион Небесного терна', ['#aeb2ba', '#eef0f4', '#8ab8ec'], [
        ('А', 'Шарф на ветру', 'Серебристый капюшон и кольчуга, голубой шарф с концами за спиной, белый плащ с голубой каймой.'),
        ('Б', 'Крылатый шлем', 'Белые обтекаемые латы с голубой каймой, крылья из перьев на шлеме, перья на плечах.'),
        ('В', 'Облака и кольца', 'Облака на воротнике, плечах и голове, вокруг запястий и голеней парят светящиеся кольца.')]),
    (6, 'Мортум', 'босс - Сумеречный демон', ['#2e2e32', '#121214', '#ff3020'], [
        ('А', 'Тень в капюшоне', 'Чёрный рваный плащ и капюшон, лицо в темноте, красные глаза, цепи на поясе.'),
        ('Б', 'Готические латы', 'Тёмные латы с шипами на плечах, локтях и коленях, глухой шлем с красными глазами и изогнутыми рогами.'),
        ('В', 'Костяной доспех', 'Рёбра и грудина поверх тёмной брони, позвоночник на спине, рогатый череп вместо шлема.')]),
    (7, 'Галит', 'босс - Карот', ['#50e010', '#1e4a0a', '#c0ff80'], [
        ('А', 'Стеклянный плащ', 'Плащ из зелёного стекла со светящейся кромкой, над капюшоном корона из светящихся игл.'),
        ('Б', 'Кристаллы галита', 'Тёмно-зелёные латы со светящимися швами, из плеч, спины и шлема растут кристаллы.'),
        ('В', 'Жилы и осколки', 'Чёрный каркас со светящимися зелёными жилами, вокруг плеч парят осколки.')]),
]
KEY = {'А': 'a', 'Б': 'b', 'В': 'c'}
e = html.escape

css = """
@page { size: A4 portrait; margin: 0; }
* { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
html, body { margin: 0; background: #131116; color: #ece5da; font-family: "Golos Text", "Segoe UI", sans-serif; }
.page { width: 210mm; height: 297mm; padding: 12mm 12mm 10mm; page-break-after: always; break-after: page; display: flex; flex-direction: column; overflow: hidden; }
.page:last-child { page-break-after: auto; break-after: auto; }
h1, h2 { font-family: "Pixelify Sans", "Courier New", monospace; font-weight: 700; margin: 0; letter-spacing: .02em; }
h1 { font-size: 30pt; line-height: 1.1; }
.lede { color: #a39aa6; font-size: 11.5pt; max-width: 200mm; margin: 5mm 0 0; line-height: 1.45; }
.lines { display: grid; grid-template-columns: 1fr; gap: 4mm; margin-top: 8mm; }
.line { background: #1c1920; border: 1px solid #322c38; border-radius: 2mm; padding: 4mm 5mm; font-size: 11pt; line-height: 1.4; }
.line b { font-family: "Pixelify Sans", monospace; color: #e3a347; font-size: 15pt; display: block; margin-bottom: 1.5mm; }
.toc { margin-top: auto; display: grid; grid-template-columns: repeat(7, 1fr); gap: 3mm; }
.toc div { border-top: 1.2mm solid; padding-top: 2mm; font-size: 10pt; color: #a39aa6; }
.toc span { display: block; font-family: "Pixelify Sans", monospace; color: #ece5da; font-size: 13pt; }
.head { display: flex; align-items: baseline; gap: 5mm; border-bottom: 1px solid #322c38; padding-bottom: 3mm; }
.head h2 { font-size: 22pt; }
.head .rank { color: #e3a347; }
.head .meta { color: #a39aa6; font-size: 10.5pt; }
.chips { display: inline-flex; gap: 1.5mm; }
.chips i { width: 4mm; height: 4mm; border-radius: .8mm; display: inline-block; border: 1px solid #00000066; }
.grid { display: flex; flex-direction: column; gap: 4mm; margin-top: 5mm; flex: 1; min-height: 0; }
.card { background: #1c1920; border: 1px solid #322c38; border-radius: 2mm; overflow: hidden; display: flex; flex: 1; min-height: 0; }
.pic { position: relative; height: 100%; aspect-ratio: 3 / 2; flex: none; }
.pic img { height: 100%; width: 100%; display: block; }
.views { position: absolute; top: 2mm; left: 3mm; right: 3mm; display: flex; justify-content: space-between; font-size: 7.5pt; letter-spacing: .08em; text-transform: uppercase; color: #ffffffa0; }
.body { padding: 5mm 5mm; min-width: 0; }
.title { font-weight: 600; font-size: 12.5pt; }
.title .ln { font-family: "Pixelify Sans", monospace; color: #e3a347; margin-right: 2mm; }
.desc { color: #a39aa6; font-size: 10pt; line-height: 1.4; margin: 1.5mm 0 0; }
.foot { margin-top: 3mm; display: flex; justify-content: space-between; color: #6e6674; font-size: 8.5pt; }
"""

parts = ['<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Облики брони Странника</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s</style></head><body>' % css]
parts.append('<section class="page"><h1>Облики брони Странника</h1>')
parts.append('<p class="lede">DivineRPG Extended. По три варианта модели на каждую ступень R1-R7. Черновые модели из блоков на фигурке игрока, '
             'на тех же костях, что и броня в игре; на каждой картинке слева вид спереди, справа сзади. Текстуры упрощены, движение '
             '(плащ, шарф, мерцание) не показано. Можно взять одну линию на все ступени или смешивать.</p>')
parts.append('<div class="lines">' + ''.join('<div class="line"><b>%s - %s</b>%s</div>' % (l, e(n), e(d[0].upper() + d[1:])) for l, n, d in LINES) + '</div>')
parts.append('<div class="toc">' + ''.join('<div style="border-color:%s"><span>R%d</span>%s</div>' % (c[0] if c[0] != '#2e2e32' else c[2], r, e(n))
                                           for r, n, _, c, _ in TIERS) + '</div></section>')
for r, name, meta, chips, cs in TIERS:
    parts.append('<section class="page"><div class="head"><h2><span class="rank">R%d</span> %s</h2><span class="chips">%s</span><span class="meta">%s</span></div>'
                 % (r, e(name), ''.join('<i style="background:%s"></i>' % c for c in chips), e(meta)))
    parts.append('<div class="grid">')
    for ln, title, desc in cs:
        parts.append('<div class="card"><div class="pic"><img src="img/r%d%s.png" alt=""><div class="views"><span>спереди</span><span>сзади</span></div></div>'
                     '<div class="body"><div class="title"><span class="ln">%s</span>%s</div><p class="desc">%s</p></div></div>'
                     % (r, KEY[ln], ln, e(title), e(desc)))
    parts.append('</div><div class="foot"><span>Облики брони Странника</span><span>R%d из 7</span></div></section>' % r)
parts.append('</body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
