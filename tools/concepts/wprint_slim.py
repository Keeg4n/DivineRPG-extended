"""Fourth round of Wanderer armor concepts (A4): the Botania reference and sizes, then a page per tier - before and after.
Run: python wprint_slim.py <out.html>. Expects images in img/ next to the HTML: r1s..r7s, previous looks, bot_terrasteel, bot_manasteel, bot_elementium."""
import html
import sys

from wprint_data import CSS

OUT = sys.argv[1]
e = html.escape

LEDE = ('The previous armor is bloated: the torso and arms are a whole unit wider than the body on each side, with plates on top, and the pauldrons stick out above the neck. Hence the refrigerator look. '
        'Here are the same tier looks on Botania proportions: the armor follows the body, the chest narrows into a waist, the undersuit is visible between the pauldron and the bracer and above the boot, '
        'the hand is open. The helmet is a cap, cheeks and a short neck guard, the face is visible. Hoods are kept where they are the look itself: R5 and R6. '
        'The texture is as it will be in the game; front view on the left, back view on the right.')

BOTANIA = [
    ('bot_terrasteel', 'Terrasteel', 'Chest, waist, belt, a bracer over the whole forearm; branches and leaves on the helmet, shoulders and boots.'),
    ('bot_manasteel', 'Manasteel', 'The lightest: a bracer only on the outside of the arm, gaps on the arm and above the boot, a cap with cheeks.'),
    ('bot_elementium', 'Elementium', 'An open helmet with flat wings pointing outward, light wings on the back.'),
]

SIZES = [  # part, before, after, Botania (model units; player body: torso 8 x 4, arm and leg 4 x 4, head 8)
    ('Chest, width x thickness', '10 x 6.5', '9.2 x 6', '9.4 x 6.4'),
    ('Waist', '10 x 6, no taper', '8.4 x 5', '8.4 x 5.4'),
    ('Pauldron: protrusion from the arm', '3', '1.1', '1.2'),
    ('Pauldron: above the shoulder', '2.4', '0.2', '0.2'),
    ('Forearm', '5.4 x 5.4', '4.4 x 4.4', '4.4 x 4.4'),
    ('Thigh', '5.2 x 5.2', '4.4 x 4.4', '4.4 x 4.4'),
    ('Boot', '5.6 x 5.6', '4.4 x 5.4', '4.4 x 5.4'),
    ('Head, width', '11.2 (hood)', '9.1 (helmet), 9.2 (hood)', '8.4'),
]

TIERS = [
    (1, 'Divine', '#ccaf3b', 'r1tk', 'r1s', 'Hallowed', 'A silver cuirass with a gold wedge and a scarlet stone, a dark waist and undersuit, gold pauldrons and bracers. '
     'Instead of a hood, an open gold helmet with a silver crest, flat wings and a stone on the brow. A thin scarlet cloak.'),
    (2, 'Eden', '#d8401c', 'r2c', 'r2s', 'Petals and halo', 'Gold and scarlet petals lie flat on the chest and shoulders instead of sticking out. A scarlet undersuit, gold bracers only on the outside of the arm. '
     'A gold crown with petals and a fire stone, the halo closer to the head.'),
    (3, 'Wildwood', '#1a62e0', 'r3b', 'r3s', 'Wooden plate armor', 'Plate armor of blue wood following the body, a dark undersuit. The closed helmet is replaced by an open one, the branch antlers are thinner. The roots by the boots are removed.'),
    (4, 'Apalachia', '#c020d8', 'r4tk', 'r4s', 'Orichalcum', 'A faceted crimson cuirass with a lilac stone, a dark waist, short crystals on the shoulders. '
     'Instead of a hood, an open helmet with a fan of crystals. The floating petals around the body are removed.'),
    (5, 'Skythern', '#aeb2ba', 'r5i', 'r5s', 'Gray cloak', 'Gray plate armor and bracers on the outside of the arm. A white hood following the head and a narrow collar instead of a cape on the shoulders, a white cloak with a light blue trim and a clasp.'),
    (6, 'Mortum', '#ff3020', 'r6a', 'r6s', 'Hooded shadow', 'A hood following the head, the face in darkness, red eyes. A dark cuirass with a red trim, a narrower and shorter tattered cloak, a chain on the belt.'),
    (7, 'Halite', '#50e010', 'r7tk', 'r7s', 'Chlorophyte', 'Leaf plate armor with gray inlays, a dark waist, the leaves on the shoulders and back lie closer to the body. '
     'Instead of a hood, an open helmet with leaves, thin gray horns and a stone on the brow. The floating crystal is removed.'),
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
    return ('<div class="card"><div class="pic"><img src="img/%s.png" alt=""><div class="views"><span>front</span><span>back</span></div></div>'
            '<div class="body"><div class="title"><span class="tag">%s</span>%s</div><p class="desc">%s</p></div></div>' % (key, e(tag), e(title), e(desc)))


parts = ['<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Wanderer armor looks - fourth round</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s%s</style></head><body>' % (CSS, EXTRA)]
parts.append('<section class="page"><h1>Wanderer armor looks</h1><div class="sub">Fourth round: slim, like in Botania</div><p class="lede">%s</p>' % e(LEDE))
parts.append('<h3>Reference: the real Botania armor models</h3><div class="bot">')
parts += [card(k, '', t, d) for k, t, d in BOTANIA]
parts.append('</div><h3>Sizes</h3><table><tr><th>Part</th><th>Was</th><th>Now</th><th>Botania</th></tr>')
parts += ['<tr><td>%s</td><td>%s</td><td class="new">%s</td><td>%s</td></tr>' % tuple(e(c) for c in row) for row in SIZES]
parts.append('</table></section>')
for r, world, color, old, new, title, desc in TIERS:
    parts.append('<section class="page"><div class="head"><h2><span class="rank">R%d</span> %s</h2><span class="chips"><i style="background:%s"></i></span></div><div class="grid">' % (r, e(world), color))
    parts.append(card(old, 'Was', title, 'As it is now in the game.'))
    parts.append(card(new, 'Now', title, desc))
    parts.append('</div><div class="foot"><span>Wanderer armor looks, fourth round</span><span>R%d of 7</span></div></section>' % r)
parts.append('</body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
