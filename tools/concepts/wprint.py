"""Print version of the Wanderer armor concept gallery (A4 landscape): cover + one sheet per tier. Writes print.html next to img/."""
import html
import sys

OUT = sys.argv[1]

LINES = [
    ('A', 'Wayfarer', 'traveling clothes, hood, cloak. With each armor tier there is more, and the world leaves its marks on it.'),
    ('B', 'World Knight', 'plate armor that gets heavier with each tier. The helmet changes the most.'),
    ('C', 'World Spirit', 'the armor grows through with the material of the world: ore, bark, crystals, bone, light.'),
]
TIERS = [
    (1, 'Realmite', 'from realmite and elite realmite, boss - Ancient Entity', ['#c8642c', '#7a3a18', '#d8b04a'], [
        ('A', 'Wayfarer', 'Leather jacket with copper pauldrons, hood with a cape, a bedroll on the back, a brass compass on the chest.'),
        ('B', 'Knight', 'Brigandine with copper rivets, mail sleeves and greaves, chapel-de-fer helmet with a wide brim and a mail aventail.'),
        ('C', 'Ore armor', 'Armor like a chunk of ore: smoldering orange in the cracks, a core burning on the chest, eyes glowing in the helmet slit.')]),
    (2, 'Eden', 'boss - Great Eden Cadillion', ['#d8401c', '#f0ede6', '#f0c040'], [
        ('A', 'Sunset Cloak', 'White tunic with a red sash, a red hood and a cloak with a white trim, the sun clasp glows.'),
        ('B', 'Sun Knight', 'White plate armor with a red trim, a sun disc on the chest and pauldrons, a crest of golden rays on the helmet.'),
        ('C', 'Petals and halo', 'Golden and scarlet petals on the chest and shoulders, a golden circlet with a gem, a halo glows above the head.')]),
    (3, 'Wildwood', 'boss - Great Wildwood Cadillion', ['#1a62e0', '#0e2f78', '#8ae8ff'], [
        ('A', 'Leaf Cloak', 'Hood and a tattered cloak of leaves, little twig horns, a lantern with fireflies on the belt.'),
        ('B', 'Wooden plate armor', 'Plate armor of blue wood, a closed helmet with branching antlers, roots by the boots.'),
        ('C', 'Treant', 'Bark and moss, blue mushrooms glow on the shoulders and head, eyes burn from the bark.')]),
    (4, 'Apalachia', 'boss - Great Apalachia Cadillion', ['#c020d8', '#5a1078', '#f090ff'], [
        ('A', 'Veil', 'Purple cloak and hood, a pale veil over the face, crystals glow on the bracers.'),
        ('B', 'Faceted plate armor', 'Purple plate armor with facets, sharp pauldrons and fins on the helmet, a crystal in the forehead.'),
        ('C', 'Geodes', 'Dark armor, glowing crystal geodes grow from the shoulders, back and head.')]),
    (5, 'Skythern', 'boss - Great Skythern Cadillion', ['#aeb2ba', '#eef0f4', '#8ab8ec'], [
        ('A', 'Scarf in the wind', 'Silvery hood and mail, a light blue scarf with ends behind the back, a white cloak with a light blue trim.'),
        ('B', 'Winged helmet', 'White streamlined plate armor with a light blue trim, feather wings on the helmet, feathers on the shoulders.'),
        ('C', 'Clouds and rings', 'Clouds on the collar, shoulders and head, glowing rings float around the wrists and shins.')]),
    (6, 'Mortum', 'boss - Twilight Demon', ['#2e2e32', '#121214', '#ff3020'], [
        ('A', 'Hooded Shadow', 'Black tattered cloak and hood, the face in darkness, red eyes, chains on the belt.'),
        ('B', 'Gothic plate armor', 'Dark plate armor with spikes on the shoulders, elbows and knees, a closed helmet with red eyes and curved horns.'),
        ('C', 'Bone armor', 'Ribs and breastbone over dark armor, a spine on the back, a horned skull instead of a helmet.')]),
    (7, 'Halite', 'boss - Karot', ['#50e010', '#1e4a0a', '#c0ff80'], [
        ('A', 'Glass Cloak', 'A cloak of green glass with a glowing edge, a crown of glowing needles above the hood.'),
        ('B', 'Halite crystals', 'Dark green plate armor with glowing seams, crystals grow from the shoulders, back and helmet.'),
        ('C', 'Veins and shards', 'A black frame with glowing green veins, shards float around the shoulders.')]),
]
KEY = {'A': 'a', 'B': 'b', 'C': 'c'}
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

parts = ['<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Wanderer armor looks</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s</style></head><body>' % css]
parts.append('<section class="page"><h1>Wanderer armor looks</h1>')
parts.append('<p class="lede">DivineRPG Extended. Three model variants for each tier R1-R7. Draft models made of blocks on a player figure,'
             'on the same bones as the armor in the game; in each picture the front view is on the left, the back on the right. Textures are simplified, motion'
             '(cloak, scarf, shimmer) is not shown. You can take one line for all tiers or mix.</p>')
parts.append('<div class="lines">' + ''.join('<div class="line"><b>%s - %s</b>%s</div>' % (l, e(n), e(d[0].upper() + d[1:])) for l, n, d in LINES) + '</div>')
parts.append('<div class="toc">' + ''.join('<div style="border-color:%s"><span>R%d</span>%s</div>' % (c[0] if c[0] != '#2e2e32' else c[2], r, e(n))
                                           for r, n, _, c, _ in TIERS) + '</div></section>')
for r, name, meta, chips, cs in TIERS:
    parts.append('<section class="page"><div class="head"><h2><span class="rank">R%d</span> %s</h2><span class="chips">%s</span><span class="meta">%s</span></div>'
                 % (r, e(name), ''.join('<i style="background:%s"></i>' % c for c in chips), e(meta)))
    parts.append('<div class="grid">')
    for ln, title, desc in cs:
        parts.append('<div class="card"><div class="pic"><img src="img/r%d%s.png" alt=""><div class="views"><span>front</span><span>back</span></div></div>'
                     '<div class="body"><div class="title"><span class="ln">%s</span>%s</div><p class="desc">%s</p></div></div>'
                     % (r, KEY[ln], ln, e(title), e(desc)))
    parts.append('</div><div class="foot"><span>Wanderer armor looks</span><span>R%d of 7</span></div></section>' % r)
parts.append('</body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
