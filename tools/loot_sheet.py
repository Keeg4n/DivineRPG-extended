"""Loot table of chests and crates for editing: icons, amount, weights, chances.

Data is from the selftest "Debug / Chest and crate loot dump" (the server's logs/dre-loot.json). Regular chests - weights as in the code,
chances are computed here; Arcana chests, reliquaries, gifts and fishing crates - from 20000 runs of the code itself.
Run: python3 loot_sheet.py <dre-loot.json> <folder>; loot.html for printing to PDF goes next to the images.
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
TK = zipfile.ZipFile(HOME + '/mc/server-pack/mods/TConstruct-1.7.10-1.8.8.jar')
e = html.escape

# ------------------------------------------------------------------ names
LANG = {}


def load_lang(text):
    for line in text.splitlines():
        if '=' in line and not line.startswith('#'):
            k, v = line.split('=', 1)
            LANG.setdefault(k.strip(), v.strip())


load_lang(open(os.path.join(OURS, 'lang/en_US.lang'), encoding='utf-8').read())
load_lang(DV.read('assets/divinerpg/lang/en_US.lang').decode('utf-8', 'replace'))
load_lang(TC.read('assets/thaumcraft/lang/en_US.lang').decode('utf-8', 'replace'))  # Thaumcraft ores
load_lang(TK.read('assets/tinker/lang/en_US.lang').decode('utf-8', 'replace'))  # Tinkers ores
load_lang(MC.read('assets/minecraft/lang/en_US.lang').decode('utf-8', 'replace'))  # vanilla English is inside the jar

WORLD_RU = {'EDEN': 'Eden', 'WILDWOOD': 'Wildwood', 'APALACHIA': 'Apalachia', 'SKYTHERN': 'Skythern', 'MORTUM': 'Mortum'}
EFFECTS = re.findall(r'^\s+([A-Z_]+)\(0x([0-9A-Fa-f]{6})', open(os.path.join(REPO, 'src/main/java/com/divinerpgextended/magic/ArcaneEffect.java'), encoding='utf-8').read(), re.M)


def name_of(it):
    if it['id'] == 'divinerpgextended:arcanePotion':
        n = LANG.get(it['unloc'] + '.name', it['en'])
        return 'Potion: ' + n + (' (strong)' if it['meta'] & 32 else '')
    if it['id'] == 'minecraft:golden_apple' and it['meta'] == 1:
        return 'Enchanted Golden Apple'
    for k in (it['unloc'] + '.name', it['unloc']):
        if k in LANG:
            return LANG[k]
    return it['en']


# ------------------------------------------------------------------ icons
DYES = ['black', 'red', 'green', 'brown', 'blue', 'purple', 'cyan', 'silver', 'gray', 'pink', 'lime', 'yellow', 'light_blue', 'magenta', 'orange', 'white']


def tex(domain, path):
    try:
        if domain == 'minecraft':
            return Image.open(io.BytesIO(MC.read('assets/minecraft/textures/' + path))).convert('RGBA')
        if domain == 'divinerpg':
            return Image.open(io.BytesIO(DV.read('assets/divinerpg/textures/' + path))).convert('RGBA')
        if domain == 'thaumcraft':
            return Image.open(io.BytesIO(TC.read('assets/thaumcraft/textures/' + path))).convert('RGBA')
        if domain == 'tinker':
            return Image.open(io.BytesIO(TK.read('assets/tinker/textures/' + path))).convert('RGBA')
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
                return im.crop((0, 0, w, w))  # animation - first frame
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
    if i == 'Thaumcraft:blockCustomOre':  # Thaumcraft ores: 0 cinnabar, 1-6 aspect stones, 7 amber
        if m == 0:
            return first('thaumcraft', ['cinnibar'], True)
        if m == 7:
            return first('thaumcraft', ['amberore'], True)
        aspect = [(255, 255, 126), (255, 90, 1), (60, 212, 252), (86, 192, 0), (213, 212, 236), (64, 64, 64)][(m - 1) % 6]
        base, over = first('thaumcraft', ['infusedorestone'], True), first('thaumcraft', ['infusedore'], True)
        base.alpha_composite(tint(over, aspect))
        return base
    if i == 'TConstruct:SearedBrick':  # Tinkers ores: 3 copper, 4 tin, 5 aluminum
        return first('tinker', [{3: 'ore_copper', 4: 'ore_tin', 5: 'ore_aluminum'}.get(m, 'ore_copper')], True)
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
    """A texture that the addon builds at startup from a Divine texture: the source and color replacement."""
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
            print('no icon:', it['id'], it['meta'], it['icon'])
        im.resize((32, 32), Image.NEAREST).save(os.path.join(IMG, k + '.png'))
        ICONS[k] = 'img/%s.png' % k
    return ICONS[k]


# ------------------------------------------------------------------ data
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


TIER_RU = {'common': 'common', 'uncommon': 'uncommon', 'rare': 'rare', 'veryRare': 'very rare'}


def fish_tier(zone, it):
    for tier, lst in FISH.get(zone, {}).items():
        for x in lst:
            if x['id'] == it['id'] and x['meta'] == it['meta']:
                return TIER_RU[tier], str(x['weight'])
    if it['id'].startswith('divinerpgextended:') and 'bait' in it['unloc'].lower():
        return 'bait', '-'
    if it.get('block') and zone in ('CAVE', 'HELL'):  # miner's crates: ore in blocks
        return 'ore', '-'
    return 'gear/bait', '-'


# ------------------------------------------------------------------ pages
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
.t-common { background: #8a8f99; } .t-uncommon { background: #3d8b4a; } .t-rare { background: #2f68c8; } .t-veryrare { background: #9a3fc8; } .t-other { background: #b07a1a; } .t-ore { background: #6b5a4a; }
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
    return ('<h3>%s</h3><p class="note">The chest rolls %s times, total weight %d. "Per roll" is the chance to get an item in a single roll; "in chest" is the chance it appears at least once.</p>'
            '<table><tr><th></th><th>Item</th><th class="n">Qty</th><th class="n">Weight</th><th class="n">Per roll</th><th class="n">In chest</th></tr>%s</table>'
            % (e(title or t['name']), qty(a, b), W, body))


def section_sampled(t, title, note='', zone=None):
    rows = sorted(t['entries'], key=lambda x: -x['chance'])
    body = ''
    for x in rows:
        extra = ''
        if zone is not None:
            tier, w = fish_tier(zone, x)
            cls = {'common': 't-common', 'uncommon': 't-uncommon', 'rare': 't-rare', 'very rare': 't-veryrare', 'ore': 't-ore'}.get(tier, 't-other')
            extra = '<td><span class="chip %s">%s</span></td><td class="n">%s</td>' % (cls, tier, w)
        body += ('<tr><td class="ic"><img src="%s"></td><td>%s</td><td class="n">%s</td><td class="n">%.1f</td>%s<td class="n"><b>%s</b></td></tr>'
                 % (icon_file(x), e(name_of(x)), qty(x['min'], x['max']), x['avg'], extra, pct(x['chance'])))
    head = '<tr><th></th><th>Item</th><th class="n">Qty</th><th class="n">Average</th>%s<th class="n">In chest</th></tr>' % (
        '<th>Group</th><th class="n">Weight</th>' if zone is not None else '')
    return '<h3>%s</h3>%s<table>%s%s</table>' % (e(title), ('<p class="note">%s</p>' % note) if note else '', head, body)


def twilight_combined(kind):
    """Ruins or dungeons: weights are the same in all worlds, only the items are world-specific - a row per entry, an icon per world."""
    ts = [t for t in TABLES if t['group'] == 'Twilight dimensions' and t['name'].startswith(kind)]
    base = ts[0]
    rows, W = weighted_rows(base)
    a, b = base['rolls']
    body = ''
    for n, (x, q, w, p1, pc) in enumerate(rows):
        items = [t['entries'][n] for t in ts]
        same = len(set((i['id'], i['meta']) for i in items)) == 1
        icons = ''.join('<img src="%s" title="%s">' % (icon_file(i), e(WORLD_RU[t['world']])) for i, t in zip(items, ts)) if not same else '<img src="%s">' % icon_file(x)
        label = name_of(x) if same else re.sub(r'\bEden\b', 'world', name_of(x))
        if not same and x['id'] == 'divinerpgextended:sanctumSeal':
            label = 'Sanctum Seal of the world (in Mortum - Mortum stone)'
        if not same and x['id'] == 'divinerpgextended:lairMap':
            label = 'Map to the world lair (nest; in Mortum - to the Demonic Circle)'
        body += ('<tr><td class="ics">%s</td><td>%s</td><td class="n">%s</td><td class="n"><b>%s</b></td><td class="n">%s</td><td class="n"><b>%s</b></td></tr>'
                 % (icons, e(label), q, w, p1, pc))
    return ('<table><tr><th style="width:33mm">Eden, Wildwood, Apalachia, Skythern, Mortum</th><th>Item</th><th class="n">Qty</th><th class="n">Weight</th><th class="n">Per roll</th><th class="n">In chest</th></tr>%s</table>'
            % body, W, (a, b))


# title
parts.append('<h1>Chest and crate loot in DivineRPG Extended</h1>')
parts.append('<div class="box"><p><b>What this is.</b> All chests the addon fills, and fishing crates: what is in them, how many pieces and with what chance. '
             'The numbers were taken from the game by the selftest, not typed by hand.</p>'
             '<p><b>Regular chests</b> (lairs, Iceika, tomb, ruins and dungeons of the worlds) roll items from a table by weight several times: '
             'the higher the weight, the more often. The easiest things to edit are the weight and "qty".</p>'
             '<p><b>Arcana chests, reliquaries, gifts and fishing crates</b> are built by code (some items are always placed, some with a chance); here the chance and quantity '
             'are calculated over 20,000 openings.</p>'
             '<p><b>How to edit:</b> write, for example, "Carcinus Burrow: diamonds weight 2 -> 4, qty 1-2 -> 2-3" or "Caves Crate: remove emeralds".</p></div>')

# lairs and Iceika
parts.append('<h2>Lairs, Iceika and tomb</h2>')
for t in TABLES:
    if t['group'] == 'Lairs and Iceika' and t['kind'] == 'weighted':
        parts.append(section_weighted(t))
gift = next(t for t in TABLES if t['name'].startswith('Gifts'))
parts.append(section_sampled(gift, 'Gifts under Iceika fir trees', 'Always 2-4 candies and snowflakes; then one of: Santa item 30%, snowball 20%, "Sound of Carols" 10%, ice stones 20%, nothing 20%.'))
parts.append('<h3>Added to vanilla chests</h3><p class="note">Dungeons, abandoned mineshafts, desert and jungle temples, strongholds: two entries were added to the vanilla table (the chance depends on the vanilla table).</p>'
             '<table><tr><th></th><th>Item</th><th class="n">Qty</th><th class="n">Weight</th></tr>'
             '<tr><td class="ic"><img src="%s"></td><td>%s</td><td class="n">1-2</td><td class="n"><b>8</b></td></tr>'
             '<tr><td class="ic"><img src="%s"></td><td>%s</td><td class="n">1</td><td class="n"><b>3</b></td></tr></table>'
             % (icon_file({'id': 'divinerpg:weakArcanaPotion', 'meta': 0, 'icon': 'divinerpg:weakArcanaPotion', 'block': False, 'unloc': 'item.weakArcanaPotion', 'en': 'Weak Arcana Potion'}),
                e(LANG.get('item.weakArcanaPotion.name', 'Weak Arcana Potion')),
                icon_file({'id': 'divinerpg:strongArcanaPotion', 'meta': 0, 'icon': 'divinerpg:strongArcanaPotion', 'block': False, 'unloc': 'item.strongArcanaPotion', 'en': 'Strong Arcana Potion'}),
                e(LANG.get('item.strongArcanaPotion.name', 'Strong Arcana Potion'))))

# twilight worlds
parts.append('<h2>Twilight dimensions: ruins, dungeons, ships, Mortum</h2>')
tb, W, (a, b) = twilight_combined('Ruins')
parts.append('<h3>Ruins (and the Skythern ship chest)</h3><p class="note">Ruins roll %s times (small ruins 3-5, ship 4-6), total weight %d. Weights are shared by all worlds, items are specific to each world. Apalachia ruins also always contain a sanctum seal.</p>%s' % (qty(a, b), W, tb))
tb, W, (a, b) = twilight_combined('Dungeons')
parts.append('<h3>Island dungeons (and Mortum chests)</h3><p class="note">Roll %s times (4-8 in Mortum), total weight %d. In addition, every dungeon always contains the world sanctum seal (except Mortum).</p>%s' % (qty(a, b), W, tb))

# Arcana and reliquaries
parts.append('<h2>Arcana chests and reliquaries</h2><p class="note">Opened with a key, the loot is built by code on opening.</p>')
for t in TABLES:
    if t['group'] == 'Arcana Chests':
        parts.append(section_sampled(t, LANG.get('tile.dre.lockedChest.%s.name' % t['name'].lower(), t['name'])))
for t in TABLES:
    if t['group'] == 'Reliquaries':
        parts.append(section_sampled(t, LANG.get('tile.dre.lockedChest.%s.name' % t['name'].lower(), t['name'])))

# fishing crates
parts.append('<h2>Fishing crates</h2><p class="note">A crate gives 4-7 items from the uncommon (3 of 4) and rare (1 of 4) catch of its zone, 1 crate in 8 also contains one very rare item, always one common fish, '
             'half of the crates contain 2-4 Arcana baits; in the twilight dimensions also a world weapon or tool (2%) and an armor piece (0.5%). Weight is within its own catch group (the catch and crate tables are shared). '
             'Miner crates (caves by bobber height tier, and lava) give 5-8 stacks of ore as blocks of their tier, in caves 1 crate in 8 also has a diamond, emerald or rupee.</p>')
for t in TABLES:
    if t['group'] == 'Fishing Crates':
        z = t['name']
        if z.startswith('CAVE_'):  # miner's cave crates by tier
            parts.append(section_sampled(t, LANG.get('item.dre.crate.mine_%s.name' % z[5:].lower(), z), zone='CAVE'))
        else:
            parts.append(section_sampled(t, 'Crate: ' + LANG.get('dre.zone.%s' % z.lower(), z), zone=z))

# ------------------------------------------------------------------ proposal: miner's fishing
def ore(domain, tex, name):
    return {'id': '%s:%s' % (domain, tex), 'meta': 0, 'icon': '%s:%s' % (domain, tex), 'block': True, 'unloc': '', 'en': name}


O = {
    'coal': ore('minecraft', 'coal_ore', 'Coal Ore'), 'iron': ore('minecraft', 'iron_ore', 'Iron Ore'), 'gold': ore('minecraft', 'gold_ore', 'Gold Ore'),
    'lapis': ore('minecraft', 'lapis_ore', 'Lapis Lazuli Ore'), 'redstone': ore('minecraft', 'redstone_ore', 'Redstone Ore'), 'diamond': ore('minecraft', 'diamond_ore', 'Diamond Ore'),
    'emerald': ore('minecraft', 'emerald_ore', 'Emerald Ore'), 'quartz': ore('minecraft', 'quartz_ore', 'Nether Quartz Ore'),
    'realmite': ore('divinerpg', 'realmiteOre', 'Realmite Ore'), 'arlemite': ore('divinerpg', 'arlemiteOre', 'Arlemite Ore'), 'rupee': ore('divinerpg', 'rupeeOre', 'Rupee Ore'),
    'netherite': ore('divinerpg', 'netheriteOre', 'Netherite Ore'), 'bloodgem': ore('divinerpg', 'bloodgemOre', 'Bloodgem Ore'),
    'amber': ore('thaumcraft', 'amberore', 'Amber Ore (Thaumcraft)'), 'cinnabar': ore('thaumcraft', 'cinnibar', 'Cinnabar Ore (Thaumcraft)'),
    'infused': ore('thaumcraft', 'infusedorestone', 'Infused Stones (Thaumcraft, 6 types)'),
}
LAYERS = [
    ('Upper caves', 'bobber at height 33-47', [('coal', 10, '2-4'), ('iron', 8, '1-3'), ('amber', 3, '1-2'), ('infused', 3, '1-2'), ('redstone', 2, '1-2')]),
    ('Middle caves', 'height 17-32', [('iron', 8, '1-3'), ('coal', 5, '2-3'), ('gold', 5, '1-2'), ('lapis', 5, '1-3'), ('redstone', 4, '1-3'), ('realmite', 4, '1-2'), ('cinnabar', 2, '1'), ('infused', 3, '1-2')]),
    ('Depths', 'height 16 and below; full set - with fishing power 40+, weaker - as middle', [('redstone', 6, '1-3'), ('gold', 5, '1-2'), ('realmite', 6, '1-3'), ('lapis', 3, '1-2'), ('arlemite', 3, '1'),
                                                                                        ('rupee', 3, '1'), ('diamond', 2, '1'), ('emerald', 1, '1')]),
    ('Lava', 'fishing in lava (fire bait or Mortum Fishing Rod), "Lava and Nether" crate', [('quartz', 10, '2-4'), ('netherite', 4, '1-2'), ('bloodgem', 3, '1')]),
]


def proposal():
    out = ('<h2>Miner fishing (in the game since 0.99.124, weights are below and in the miner crate tables)</h2>'
           '<div class="box"><p><b>Before:</b> the "Caves" zone was any water below height 48 with no sky overhead, and nothing depended on depth: '
           'the catch and crate gave coal, redstone dust and ingots (iron, gold, rupee, arlemite), rarely diamond and emerald.</p>'
           '<p><b>Now:</b> caves are split into tiers by bobber height and give <b>ore as blocks</b> (smelting and machine doubling work as with mined ore). '
           'A caves crate is a miner crate of its tier: 5-8 stacks of ore, 1 in 8 also has one rare item (diamond, emerald, rupee); an uncommon catch is 1-2 ores of the tier. '
           'A lava crate is also a miner crate, with Nether ore. Ores from modpack mods are picked up automatically via the ore dictionary (for RedServer Magic this is Thaumcraft; '
           'copper, tin, silver and lead - if they are in the modpack).</p>'
           '<p><b>Pace:</b> with good tackle a bite comes every 10-15 s, about every 10th is a crate - that is 4-6 ores a minute, like a leisurely mine. '
           'Bait is spent on every bite, so there is no free farm.</p></div>')
    for title, where, rows in LAYERS:
        W = sum(w for _, w, _ in rows)
        body = ''.join('<tr><td class="ic"><img src="%s"></td><td>%s</td><td class="n">%s</td><td class="n"><b>%d</b></td><td class="n">%s</td></tr>'
                       % (icon_file(O[k]), e(O[k]['en']), q, w, pct(w / W)) for k, w, q in rows)
        out += ('<h3>%s</h3><p class="note">%s; total weight %d</p><table><tr><th></th><th>Ore</th><th class="n">Qty</th><th class="n">Weight</th><th class="n">Per roll</th></tr>%s</table>'
                % (e(title), e(where), W, body))
    return out


parts.append(proposal())

doc = ('<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Chest loot</title>'
       '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&display=block"><style>%s</style></head><body>%s</body></html>' % (CSS, ''.join(parts)))
open(os.path.join(OUT, 'loot.html'), 'w', encoding='utf-8').write(doc)
print('icons', len(ICONS))
