"""Fifth round of Wanderer armor concepts (A4): what changed after the remarks, before and after per tier, choice of the R7 variant; R2 unchanged.
Run: python wprint_slim5.py <out.html>. Expects images in img/ next to the HTML: r1s..r7s from the fourth round, r1v, r3v, r4v, r5v, r6v, r7x, r7y."""
import html
import sys

from wprint_data import CSS

OUT = sys.argv[1]
e = html.escape

LEDE = ('Fixes to the fourth round: the body is kept, the heads are redone. There are no cloaks anymore, they conflict with the cloaks on the server; instead there is an item on the back that fits the world theme. '
        'Eden is unchanged. Halite is redone after the Chlorophyte armor design.')

TIERS = [
    (1, 'Divine', '#ccaf3b', 'r1s', 'r1v', 'Shield instead of a cloak', 'Everything as it was, only without the cloak: a silver shield with a gold cross and a scarlet stone on the back.'),
    (3, 'Wildwood', '#1a62e0', 'r3s', 'r3v', 'Wooden plate armor', 'The texture is clear now: boards of blue wood with seams. A closed helmet as in the first look, but following the head; the plus-shaped slit is transparent, the skin shows through it. The branch antlers are the same.'
     'Leaves on the shoulders, a sign glows on the chest. Light wood on the belt and knee guards, a staff on the back: the top rises over the shoulder.'),
    (4, 'Apalachia', '#c020d8', 'r4s', 'r4v', 'Native American style', 'An embroidered headband and a headdress of wide feathers in a fan, strings of beads with feathers at the temples. A breastplate of rows of beads, zigzags on the belt and bracers, fringe on the outside of the arms and thighs, '
     'soft boots, feathers on the shoulders. A fan of feathers with a rosette on the back. The world colors are the same.'),
    (5, 'Skythern', '#aeb2ba', 'r5s', 'r5v', 'Helmet and wings', 'A closed helmet instead of a hood: a dome, deep cheeks with a T-shaped opening, a gold brow and nose guard, a light blue crest from forehead to nape, rib-wings at the temples. '
     'A brighter body: white plate armor with a sky trim and gold, a blue undersuit, small wings on the heels. Folded wings on the back.'),
    (6, 'Mortum', '#ff3020', 'r6s', 'r6v', 'Cultist hood', 'The hood is built like the robe of the Thaumcraft cultists: it tapers toward the back in steps with a growing slope, the cloth in folds, the face in a deep dark arch with a red edging, red eyes. '
     'On the back a spine and ribs of bones, red smolders between them.'),
]

HALITE = [
    ('r7s', 'Was', 'Fourth round', 'An open helmet with leaves and thin horns.'),
    ('r7z', 'Now', 'Chlorophyte', 'After the Chlorophyte armor design: a closed helmet with a sharp spire, a black plus with red eyes, big twisted horns. A tall pointed collar, flat pauldrons, '
     'ovals on the chest, scales on the belly, turquoise inlays, a light wrap in front. A disc on the arms, a dark ring with fur and claws, lime diamonds on the knees, fur at the ankles, bright lime boots. Three sharp leaves on the back instead of a cloak.'),
]

EXTRA = """
.tag { font-family: "Pixelify Sans", monospace; color: #e3a347; margin-right: 2mm; }
.grid .card { flex-direction: column; }
.grid .pic { flex: 1 1 0; min-height: 0; height: auto; width: 100%; aspect-ratio: auto; }
.grid .pic img { object-fit: contain; }
.grid .body { flex: none; padding: 3mm 5mm 3.5mm; }
"""


def card(key, tag, title, desc):
    return ('<div class="card"><div class="pic"><img src="img/%s.png" alt=""><div class="views"><span>front</span><span>back</span></div></div>'
            '<div class="body"><div class="title"><span class="tag">%s</span>%s</div><p class="desc">%s</p></div></div>' % (key, e(tag), e(title), e(desc)))


parts = ['<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Wanderer armor looks - fifth round</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s%s</style></head><body>' % (CSS, EXTRA)]
first = True
for r, world, color, old, new, title, desc in TIERS:
    parts.append('<section class="page">')
    if first:
        parts.append('<h1>Wanderer armor looks</h1><div class="sub">Fifth round: helmets and back</div><p class="lede">%s</p><div style="height:5mm"></div>' % e(LEDE))
        first = False
    parts.append('<div class="head"><h2><span class="rank">R%d</span> %s</h2><span class="chips"><i style="background:%s"></i></span></div><div class="grid">' % (r, e(world), color))
    parts.append(card(old, 'Was', 'Fourth round', 'With a cloak or the previous head.' if r != 1 else 'With a cloak.'))
    parts.append(card(new, 'Now', title, desc))
    parts.append('</div><div class="foot"><span>Wanderer armor looks, fifth round</span><span>R%d of 7</span></div></section>' % r)
parts.append('<section class="page"><div class="head"><h2><span class="rank">R7</span> Halite</h2><span class="chips"><i style="background:#50e010"></i></span></div><div class="grid">')
parts += [card(k, tag, t, d) for k, tag, t, d in HALITE]
parts.append('</div><div class="foot"><span>Wanderer armor looks, fifth round</span><span>R7 of 7</span></div></section></body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
