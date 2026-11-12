"""Таблица добычи сундуков и ящиков для правки: значки, количество, веса, шансы.

Данные - из автотеста "Отладка / Дамп добычи сундуков и ящиков" (logs/dre-loot.json сервера). Обычные сундуки - веса как в коде,
шансы считаются отсюда; сундуки Арканы, реликварии, подарки и ящики рыбалки - по 20000 прогонов самого кода.
Запуск: python3 loot_sheet.py <dre-loot.json> <папка>; рядом с картинками - loot.html для печати в PDF.
"""
import html
import io
import json
import os
import re
import sys
import zipfile

from PIL import Image

SRC, OUT = sys.argv[1], sys.argv[2]
IMG = os.path.join(OUT, 'img')
os.makedirs(IMG, exist_ok=True)
HOME = os.path.expanduser('~')
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OURS = os.path.join(REPO, 'src/main/resources/assets/divinerpgextended')
MC = zipfile.ZipFile(HOME + '/.gradle/caches/minecraft/net/minecraft/minecraft/1.7.10/minecraft-1.7.10.jar')
DV = zipfile.ZipFile(HOME + '/mc/server-pack/mods/DivineRPG-1.4.1.5.jar')
TC = zipfile.ZipFile(HOME + '/mc/server-pack/mods/Thaumcraft-1.7.10-4.2.3.5.jar')
e = html.escape

# ------------------------------------------------------------------ названия
LANG = {}


def load_lang(text):
    for line in text.splitlines():
        if '=' in line and not line.startswith('#'):
            k, v = line.split('=', 1)
            LANG.setdefault(k.strip(), v.strip())


load_lang(open(os.path.join(OURS, 'lang/ru_RU.lang'), encoding='utf-8').read())
load_lang(DV.read('assets/divinerpg/lang/ru_RU.lang').decode('utf-8', 'replace'))
idx = json.load(open(HOME + '/.gradle/caches/minecraft/assets/indexes/1.7.10.json'))
h = idx['objects']['minecraft/lang/ru_RU.lang']['hash']
load_lang(open(HOME + '/.gradle/caches/minecraft/assets/objects/%s/%s' % (h[:2], h), encoding='utf-8').read())

WORLD_RU = {'EDEN': 'Эдем', 'WILDWOOD': 'Дикий лес', 'APALACHIA': 'Апалачия', 'SKYTHERN': 'Небесный терн', 'MORTUM': 'Мортум'}
EFFECTS = re.findall(r'^\s+([A-Z_]+)\(0x([0-9A-Fa-f]{6})', open(os.path.join(REPO, 'src/main/java/com/divinerpgextended/magic/ArcaneEffect.java'), encoding='utf-8').read(), re.M)


def name_of(it):
    if it['id'] == 'divinerpgextended:arcanePotion':
        n = LANG.get(it['unloc'] + '.name', it['en'])
        return 'Зелье: ' + n + (' (сильное)' if it['meta'] & 32 else '')
    if it['id'] == 'minecraft:golden_apple' and it['meta'] == 1:
        return 'Зачарованное золотое яблоко'
    for k in (it['unloc'] + '.name', it['unloc']):
        if k in LANG:
            return LANG[k].replace('Вайлдвуда', 'Дикого леса').replace('Вайлдвуд', 'Дикий лес')
    return it['en']


# ------------------------------------------------------------------ значки
DYES = ['black', 'red', 'green', 'brown', 'blue', 'purple', 'cyan', 'silver', 'gray', 'pink', 'lime', 'yellow', 'light_blue', 'magenta', 'orange', 'white']


def tex(domain, path):
    try:
        if domain == 'minecraft':
            return Image.open(io.BytesIO(MC.read('assets/minecraft/textures/' + path))).convert('RGBA')
        if domain == 'divinerpg':
            return Image.open(io.BytesIO(DV.read('assets/divinerpg/textures/' + path))).convert('RGBA')
        if domain == 'thaumcraft':
            return Image.open(io.BytesIO(TC.read('assets/thaumcraft/textures/' + path))).convert('RGBA')
        p = os.path.join(OURS, 'textures', path)
        return Image.open(p).convert('RGBA') if os.path.exists(p) else derived(path)
    except KeyError:
        return None


def first(domain, names, block):
    for n in names:
        for folder in (('blocks', 'items') if block else ('items', 'blocks')):
            im = tex(domain, '%s/%s.png' % (folder, n))
            if im is not None:
                w = im.size[0]
                return im.crop((0, 0, w, w))  # анимация - первый кадр
    return None


def tint(im, rgb):
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            px[x, y] = (r * rgb[0] // 255, g * rgb[1] // 255, b * rgb[2] // 255, a)
    return im


def icon_of(it):
    i, m, ic = it['id'], it['meta'], it['icon']
    dom, name = i.split(':', 1)
    if i == 'minecraft:dye':
        return first('minecraft', ['dye_powder_' + DYES[m % 16]], False)
    if i == 'minecraft:fish':
        return first('minecraft', [['fish_cod_raw', 'fish_salmon_raw', 'fish_clownfish_raw', 'fish_pufferfish_raw'][m % 4]], False)
    if i == 'minecraft:vine':
        return tint(first('minecraft', ['vine'], True), (72, 181, 24))
    if i == 'divinerpgextended:arcanePotion':
        eff = EFFECTS[(m & 31) % len(EFFECTS)]
        col = int(eff[1], 16)
        over = tint(first('minecraft', ['potion_overlay'], False), ((col >> 16) & 255, (col >> 8) & 255, col & 255))
        base = first('minecraft', ['potion_bottle_drinkable'], False)
        base.alpha_composite(over)
        return base
    key = it['unloc'].split('.')[-1]
    if i == 'divinerpgextended:fish':
        return first('ours', ['fish_' + key], False)
    if i == 'divinerpgextended:trinket':
        return first('ours', ['trinket_' + key], False)
    if i == 'divinerpgextended:sanctumSeal':
        return first('ours', ['sanctum_seal_' + key], False)
    if i == 'divinerpgextended:crate':
        return first('ours', ['crate_' + key], False)
    if ic:
        d, n = ic.split(':', 1) if ':' in ic else ('minecraft', ic)
        d = 'ours' if d == 'divinerpgextended' else d
        im = first(d, [n, n + '_0', n + '_side', n + '_front', n + '_top'], it['block'])
        if im is not None:
            return im
    d = 'ours' if dom == 'divinerpgextended' else dom
    extra = {'divinerpg:CandyCane': ['redCandyCane'], 'divinerpgextended:stormBow': ['storm_bow_standby']}.get(i, [])
    return first(d, [name, name + '_0', name + '_1', name + '_standby'] + extra, it['block'])


DERIVED = {}
for line in open(os.path.join(OURS, 'derived_textures.txt'), encoding='utf-8'):
    if '|' in line and not line.startswith('#'):
        f = line.rstrip().split('|')
        DERIVED[f[0]] = f


def derived(path):
    """Текстура, которую аддон собирает при запуске из текстуры Divine: исходник и замена цветов."""
    f = DERIVED.get('divinerpgextended:textures/' + path)
    if not f:
        return None
    src = f[1].split(';')[0]
    try:
        im = Image.open(io.BytesIO(DV.read(src))).convert('RGBA')
    except KeyError:
        return None
    cmap = {}
    for pair in f[4].split(','):
        if '>' in pair:
            a, b = pair.split('>')
            cmap[int(a, 16) & 0xffffff] = int(b, 16) & 0xffffff
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            c = cmap.get((r << 16) | (g << 8) | b)
            if c is not None and a:
                px[x, y] = ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)
    return im.crop((0, 0, im.size[0], im.size[0]))


ICONS = {}


def icon_file(it):
    k = '%s_%d' % (it['id'].replace(':', '_'), it['meta'])
    if k not in ICONS:
        im = icon_of(it)
        if im is None:
            im = Image.new('RGBA', (16, 16), (200, 0, 200, 255))
            print('нет значка:', it['id'], it['meta'], it['icon'])
        im.resize((32, 32), Image.NEAREST).save(os.path.join(IMG, k + '.png'))
        ICONS[k] = 'img/%s.png' % k
    return ICONS[k]


# ------------------------------------------------------------------ данные
txt = open(SRC, encoding='utf-8').read().strip()
DATA = json.loads(re.sub(r',\s*\]$', ']', txt))
FISH = next(t for t in DATA if t['kind'] == 'fish')['zones']
TABLES = [t for t in DATA if t['kind'] != 'fish']


def pct(x):
    if x <= 0:
        return '-'
    if x >= 0.9995:
        return '100%'
    return ('%.1f%%' % (x * 100)).replace('.0%', '%') if x >= 0.01 else '%.2f%%' % (x * 100)


def qty(a, b):
    return str(a) if a == b else '%d-%d' % (a, b)


def weighted_rows(t):
    W = sum(x['weight'] for x in t['entries'])
    a, b = t['rolls']
    rows = []
    for x in t['entries']:
        p = x['weight'] / W
        inchest = sum(1 - (1 - p) ** c for c in range(a, b + 1)) / (b - a + 1)
        rows.append((x, qty(x['min'], x['max']), str(x['weight']), pct(p), pct(inchest)))
    return rows, W


TIER_RU = {'common': 'обычное', 'uncommon': 'необычное', 'rare': 'редкое', 'veryRare': 'очень редкое'}


def fish_tier(zone, it):
    for tier, lst in FISH.get(zone, {}).items():
        for x in lst:
            if x['id'] == it['id'] and x['meta'] == it['meta']:
                return TIER_RU[tier], str(x['weight'])
    if it['id'].startswith('divinerpgextended:') and 'bait' in it['unloc'].lower():
        return 'приманка', '-'
    return 'снаряжение/приманка', '-'


# ------------------------------------------------------------------ страницы
CSS = """
@page { size: A4 portrait; margin: 10mm 10mm 12mm; }
* { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body { margin: 0; font-family: "Golos Text", "Segoe UI", sans-serif; color: #1d1a22; font-size: 3.1mm; }
h1 { font-size: 7mm; margin: 0 0 2mm; }
h2 { font-size: 5mm; margin: 6mm 0 1.5mm; padding-top: 1mm; border-top: .5mm solid #d9d2e2; break-after: avoid; }
h3 { font-size: 3.9mm; margin: 3.5mm 0 1mm; break-after: avoid; }
p { margin: 0 0 2mm; line-height: 1.45; }
.note { color: #5b5466; font-size: 2.9mm; margin: 0 0 1.4mm; }
table { border-collapse: collapse; width: 100%; margin: 0 0 2mm; break-inside: auto; }
tr { break-inside: avoid; }
th { text-align: left; font-weight: 600; font-size: 2.7mm; color: #5b5466; border-bottom: .35mm solid #bdb4c8; padding: .8mm 1.4mm; }
td { border-bottom: .2mm solid #ebe6f0; padding: .5mm 1.4mm; vertical-align: middle; }
td.ic { width: 8mm; padding: .3mm .6mm; }
td.ic img { width: 6.4mm; height: 6.4mm; image-rendering: pixelated; display: block; }
td.ics { width: 33mm; white-space: nowrap; }
td.ics img { width: 5mm; height: 5mm; image-rendering: pixelated; display: inline-block; margin-right: .5mm; vertical-align: middle; }
td.n, th.n { text-align: right; white-space: nowrap; width: 15mm; }
td.n b { font-weight: 600; }
.chip { display: inline-block; font-size: 2.5mm; color: #fff; border-radius: .8mm; padding: .1mm 1.2mm; }
.t-common { background: #8a8f99; } .t-uncommon { background: #3d8b4a; } .t-rare { background: #2f68c8; } .t-veryrare { background: #9a3fc8; } .t-other { background: #b07a1a; }
.box { background: #f4f1f7; border-radius: 1.6mm; padding: 2.4mm 3mm; margin: 0 0 2.4mm; }
.box b { font-weight: 600; }
.cols2 { columns: 2; column-gap: 6mm; }
.cols2 > div { break-inside: avoid; }
"""

parts = []


def section_weighted(t, title=None):
    rows, W = weighted_rows(t)
    a, b = t['rolls']
    body = ''.join('<tr><td class="ic"><img src="%s"></td><td>%s</td><td class="n">%s</td><td class="n"><b>%s</b></td><td class="n">%s</td><td class="n"><b>%s</b></td></tr>'
                   % (icon_file(x), e(name_of(x)), q, w, p1, pc) for x, q, w, p1, pc in sorted(rows, key=lambda r: -r[0]['weight']))
    return ('<h3>%s</h3><p class="note">Сундук тянет %s раз, сумма весов %d. "За раз" - шанс вытянуть предмет одним броском; "в сундуке" - что он есть хотя бы раз.</p>'
            '<table><tr><th></th><th>Предмет</th><th class="n">Штук</th><th class="n">Вес</th><th class="n">За раз</th><th class="n">В сундуке</th></tr>%s</table>'
            % (e(title or t['name']), qty(a, b), W, body))


def section_sampled(t, title, note='', zone=None):
    rows = sorted(t['entries'], key=lambda x: -x['chance'])
    body = ''
    for x in rows:
        extra = ''
        if zone is not None:
            tier, w = fish_tier(zone, x)
            cls = {'обычное': 't-common', 'необычное': 't-uncommon', 'редкое': 't-rare', 'очень редкое': 't-veryrare'}.get(tier, 't-other')
            extra = '<td><span class="chip %s">%s</span></td><td class="n">%s</td>' % (cls, tier, w)
        body += ('<tr><td class="ic"><img src="%s"></td><td>%s</td><td class="n">%s</td><td class="n">%.1f</td>%s<td class="n"><b>%s</b></td></tr>'
                 % (icon_file(x), e(name_of(x)), qty(x['min'], x['max']), x['avg'], extra, pct(x['chance'])))
    head = '<tr><th></th><th>Предмет</th><th class="n">Штук</th><th class="n">В среднем</th>%s<th class="n">В сундуке</th></tr>' % (
        '<th>Группа</th><th class="n">Вес</th>' if zone is not None else '')
    return '<h3>%s</h3>%s<table>%s%s</table>' % (e(title), ('<p class="note">%s</p>' % note) if note else '', head, body)


def twilight_combined(kind):
    """Руины или подземелья: веса одинаковые во всех мирах, свои у мира только предметы - строка на запись, по значку на мир."""
    ts = [t for t in TABLES if t['group'] == 'Сумеречные миры' and t['name'].startswith(kind)]
    base = ts[0]
    rows, W = weighted_rows(base)
    a, b = base['rolls']
    body = ''
    for n, (x, q, w, p1, pc) in enumerate(rows):
        items = [t['entries'][n] for t in ts]
        same = len(set((i['id'], i['meta']) for i in items)) == 1
        icons = ''.join('<img src="%s" title="%s">' % (icon_file(i), e(WORLD_RU[t['world']])) for i, t in zip(items, ts)) if not same else '<img src="%s">' % icon_file(x)
        label = name_of(x) if same else re.sub(r'\b(Эдема|Эдем)\b', 'мира', name_of(x))
        if not same and x['id'] == 'divinerpgextended:sanctumSeal':
            label = 'Печать святилища мира (в Мортуме - камень Мортума)'
        if not same and x['id'] == 'divinerpgextended:lairMap':
            label = 'Карта к логову мира (гнездо; в Мортуме - к Демоническому кругу)'
        body += ('<tr><td class="ics">%s</td><td>%s</td><td class="n">%s</td><td class="n"><b>%s</b></td><td class="n">%s</td><td class="n"><b>%s</b></td></tr>'
                 % (icons, e(label), q, w, p1, pc))
    return ('<table><tr><th style="width:33mm">Эдем, Дикий лес, Апалачия, Терн, Мортум</th><th>Предмет</th><th class="n">Штук</th><th class="n">Вес</th><th class="n">За раз</th><th class="n">В сундуке</th></tr>%s</table>'
            % body, W, (a, b))


# титул
parts.append('<h1>Добыча сундуков и ящиков DivineRPG Extended</h1>')
parts.append('<div class="box"><p><b>Что здесь.</b> Все сундуки, которые наполняет аддон, и ящики рыбалки: что в них лежит, сколько штук и с каким шансом. '
             'Числа сняты с игры автотестом, а не переписаны руками.</p>'
             '<p><b>Обычные сундуки</b> (логова, Айсика, гробница, руины и подземелья миров) тянут предметы из таблицы по весу несколько раз: '
             'чем больше вес, тем чаще. Править проще всего вес и "штук".</p>'
             '<p><b>Сундуки Арканы, реликварии, подарки и ящики рыбалки</b> собирает код (часть вещей кладётся всегда, часть - с шансом); здесь шанс и количество '
             'посчитаны по 20 000 открытиям.</p>'
             '<p><b>Как править:</b> напиши, например, "Нора Карцинуса: алмазы вес 2 -> 4, штук 1-2 -> 2-3" или "Ящик пещер: убрать изумруды".</p></div>')

# логова и Айсика
parts.append('<h2>Логова, Айсика и гробница</h2>')
for t in TABLES:
    if t['group'] == 'Логова и Айсика' and t['kind'] == 'weighted':
        parts.append(section_weighted(t))
gift = next(t for t in TABLES if t['name'].startswith('Подарки'))
parts.append(section_sampled(gift, 'Подарки под ёлками Айсики', 'Всегда 2-4 сладости и снежинки; дальше одно из: вещь Санты 30%, снежный шар 20%, "Звук колядок" 10%, ледяные камни 20%, ничего 20%.'))
parts.append('<h3>Добавлено в ванильные сундуки</h3><p class="note">Подземелья, заброшенные шахты, пирамиды пустыни и джунглей, крепости: к ванильной таблице добавлены две записи (шанс зависит от ванильной таблицы).</p>'
             '<table><tr><th></th><th>Предмет</th><th class="n">Штук</th><th class="n">Вес</th></tr>'
             '<tr><td class="ic"><img src="%s"></td><td>%s</td><td class="n">1-2</td><td class="n"><b>8</b></td></tr>'
             '<tr><td class="ic"><img src="%s"></td><td>%s</td><td class="n">1</td><td class="n"><b>3</b></td></tr></table>'
             % (icon_file({'id': 'divinerpg:weakArcanaPotion', 'meta': 0, 'icon': 'divinerpg:weakArcanaPotion', 'block': False, 'unloc': 'item.weakArcanaPotion', 'en': 'Weak Arcana Potion'}),
                e(LANG.get('item.weakArcanaPotion.name', 'Слабое зелье арканы')),
                icon_file({'id': 'divinerpg:strongArcanaPotion', 'meta': 0, 'icon': 'divinerpg:strongArcanaPotion', 'block': False, 'unloc': 'item.strongArcanaPotion', 'en': 'Strong Arcana Potion'}),
                e(LANG.get('item.strongArcanaPotion.name', 'Сильное зелье арканы'))))

# сумеречные миры
parts.append('<h2>Сумеречные миры: руины, подземелья, корабли, Мортум</h2>')
tb, W, (a, b) = twilight_combined('Руины')
parts.append('<h3>Руины (и сундук терновых кораблей)</h3><p class="note">Руины тянут %s раз (малые руины 3-5, корабль 4-6), сумма весов %d. Веса общие для всех миров, предметы - своего мира. В руинах Апалачии ещё всегда кладётся печать святилища.</p>%s' % (qty(a, b), W, tb))
tb, W, (a, b) = twilight_combined('Подземелья')
parts.append('<h3>Подземелья островов (и сундуки Мортума)</h3><p class="note">Тянут %s раз (в Мортуме 4-8), сумма весов %d. Кроме этого в каждом подземелье всегда лежит печать святилища мира (кроме Мортума).</p>%s' % (qty(a, b), W, tb))

# Аркана и реликварии
parts.append('<h2>Сундуки Арканы и реликварии</h2><p class="note">Открываются ключом, добыча собирается кодом при открытии.</p>')
for t in TABLES:
    if t['group'] == 'Сундуки Арканы':
        parts.append(section_sampled(t, LANG.get('tile.dre.lockedChest.%s.name' % t['name'].lower(), t['name'])))
for t in TABLES:
    if t['group'] == 'Реликварии':
        parts.append(section_sampled(t, LANG.get('tile.dre.lockedChest.%s.name' % t['name'].lower(), t['name']).replace('Вайлдвуда', 'Дикого леса')))

# ящики рыбалки
parts.append('<h2>Ящики рыбалки</h2><p class="note">Ящик даёт 4-7 вещей из необычного (3 из 4) и редкого (1 из 4) улова своей зоны, в 1 ящике из 8 - ещё одну очень редкую, всегда одну обычную рыбу, '
             'в половине ящиков - 2-4 приманки Арканы; в сумеречных мирах ещё 2% оружие или инструмент мира и 0.5% часть брони. Вес - внутри своей группы улова (общие таблицы улова и ящика).</p>')
for t in TABLES:
    if t['group'] == 'Рыболовные ящики':
        z = t['name']
        parts.append(section_sampled(t, 'Ящик: ' + LANG.get('dre.zone.%s' % z.lower(), z).replace('Вайлдвуд', 'Дикий лес'), zone=z))

# ------------------------------------------------------------------ предложение: шахтёрская рыбалка
def ore(domain, tex, name):
    return {'id': '%s:%s' % (domain, tex), 'meta': 0, 'icon': '%s:%s' % (domain, tex), 'block': True, 'unloc': '', 'en': name}


O = {
    'coal': ore('minecraft', 'coal_ore', 'Угольная руда'), 'iron': ore('minecraft', 'iron_ore', 'Железная руда'), 'gold': ore('minecraft', 'gold_ore', 'Золотая руда'),
    'lapis': ore('minecraft', 'lapis_ore', 'Лазуритовая руда'), 'redstone': ore('minecraft', 'redstone_ore', 'Красная руда'), 'diamond': ore('minecraft', 'diamond_ore', 'Алмазная руда'),
    'emerald': ore('minecraft', 'emerald_ore', 'Изумрудная руда'), 'quartz': ore('minecraft', 'quartz_ore', 'Кварцевая руда'),
    'realmite': ore('divinerpg', 'realmiteOre', 'Реалмитовая руда'), 'arlemite': ore('divinerpg', 'arlemiteOre', 'Арлемитовая руда'), 'rupee': ore('divinerpg', 'rupeeOre', 'Рупиевая руда'),
    'netherite': ore('divinerpg', 'netheriteOre', 'Незеритовая руда'), 'bloodgem': ore('divinerpg', 'bloodgemOre', 'Руда кровавого камня'),
    'amber': ore('thaumcraft', 'amberore', 'Янтарная руда (Таумкрафт)'), 'cinnabar': ore('thaumcraft', 'cinnibar', 'Киноварная руда (Таумкрафт)'),
    'infused': ore('thaumcraft', 'infusedorestone', 'Камни с аспектами (Таумкрафт, 6 видов)'),
}
LAYERS = [
    ('Верхние пещеры', 'поплавок на высоте 33-47', [('coal', 10, '2-4'), ('iron', 8, '1-3'), ('amber', 3, '1-2'), ('infused', 3, '1-2'), ('redstone', 2, '1-2')]),
    ('Средние пещеры', 'высота 17-32', [('iron', 8, '1-3'), ('coal', 5, '2-3'), ('gold', 5, '1-2'), ('lapis', 5, '1-3'), ('redstone', 4, '1-3'), ('realmite', 4, '1-2'), ('cinnabar', 2, '1'), ('infused', 3, '1-2')]),
    ('Глубины', 'высота 16 и ниже; полный набор - с силой рыбалки 40+, слабее - как средние', [('redstone', 6, '1-3'), ('gold', 5, '1-2'), ('realmite', 6, '1-3'), ('lapis', 3, '1-2'), ('arlemite', 3, '1'),
                                                                                        ('rupee', 3, '1'), ('diamond', 2, '1'), ('emerald', 1, '1')]),
    ('Лава в Аду', 'рыбалка в лаве (огненная приманка или удочка Мортума)', [('quartz', 10, '2-4'), ('netherite', 4, '1-2'), ('bloodgem', 3, '1')]),
]


def proposal():
    out = ('<h2>Предложение: шахтёрская рыбалка (ещё не в игре)</h2>'
           '<div class="box"><p><b>Сейчас</b> зона "Пещеры" - любая вода ниже высоты 48 без неба над головой, и от глубины ничего не зависит: '
           'улов и ящик дают уголь, красную пыль и слитки (железо, золото, рупий, арлемит), редко алмаз и изумруд (таблица "Ящик: Пещеры" выше).</p>'
           '<p><b>Предлагаю</b> делить пещеры на ярусы по высоте поплавка и давать <b>руду блоками</b> (переплавка и удвоение машинами работают как с добытой). '
           'Ящик пещер становится шахтёрским: 5-8 стопок руды своего яруса, в 1 из 8 - ещё одна редкая (алмаз, изумруд, рупий); необычный улов - 1-2 руды яруса. '
           'Руды модов сборки берутся по рудному словарю сами (у RedServer Magic это Таумкрафт).</p>'
           '<p><b>Темп:</b> с хорошей снастью поклёвка раз в 10-15 с, ящик примерно каждая 10-я - выходит 4-6 руды в минуту, как неспешная шахта. '
           'Приманка тратится на каждую поклёвку, так что бесплатной фермы нет.</p></div>')
    for title, where, rows in LAYERS:
        W = sum(w for _, w, _ in rows)
        body = ''.join('<tr><td class="ic"><img src="%s"></td><td>%s</td><td class="n">%s</td><td class="n"><b>%d</b></td><td class="n">%s</td></tr>'
                       % (icon_file(O[k]), e(O[k]['en']), q, w, pct(w / W)) for k, w, q in rows)
        out += ('<h3>%s</h3><p class="note">%s; сумма весов %d</p><table><tr><th></th><th>Руда</th><th class="n">Штук</th><th class="n">Вес</th><th class="n">За раз</th></tr>%s</table>'
                % (e(title), e(where), W, body))
    return out


parts.append(proposal())

doc = ('<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Добыча сундуков</title>'
       '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&display=block"><style>%s</style></head><body>%s</body></html>' % (CSS, ''.join(parts)))
open(os.path.join(OUT, 'loot.html'), 'w', encoding='utf-8').write(doc)
print('значков', len(ICONS))
