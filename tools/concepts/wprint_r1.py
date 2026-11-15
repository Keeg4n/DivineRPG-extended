"""Sheet of R1 fork concepts (A4): looks of first-tier Wanderer armor by the set it is forged from, three variants per set; divine is as in the game.
Run: python wprint_r1.py <out.html>. Expects images in img/ next to the HTML: r1v (wslim5.py) and variants from wr1.py; the "front" frames for the overview are built here."""
import html
import os
import sys

from PIL import Image

from wprint_data import CSS

OUT = sys.argv[1]
e = html.escape
IMG = os.path.join(os.path.dirname(os.path.abspath(OUT)), 'img')

LEDE = ('The first tier of Wanderer armor can be forged not only from Divine armor but from any final set of the ordinary world. Whichever set was brought, that look is received, '
        'and its property stays in the armor forever. Each new set has three looks, you can pick one: for example, Shadow B, Bedrock C, Ender A, Corruption B.')

DIVINE = ('r1v', 'Divine', 'melee', 'Unchanged, this look is already in the game. A silver cuirass with a gold wedge, an open gold helmet with wings and a scarlet stone, a silver shield with a gold cross on the back.')

SETS = [
    ('Shadow', 'speed', '#474747', [
        ('r1t', 'A', 'Ninja', 'Sleek black plate armor with a gray chevron. A closed helmet with a narrow white slit, wings swept back at the temples, thin plates on the shoulders, forearms and heels. The back is free.'),
        ('r1u', 'B', 'Raven', 'A helmet with a beak and round white eyes, a tuft and bunches of feathers on the shoulders, feather scales on the chest. A large shadow bow with a string slants across the back.'),
        ('r1w', 'C', 'Phantom', 'Wrapped clothes, a light scarf, the head in a dark wrap and a white mask with a squint. Pouches on the belt, a sheath on the hip. A large shuriken on the back. The simplest silhouette of the three.')]),
    ('Bedrock', 'resilience', '#7c7c7c', [
        ('r1k', 'A', 'Golem', 'Plate armor of speckled rock, wide two-step pauldrons, massive boots. A closed helmet with a rough-hewn crown and white eyes. The back is free.'),
        ('r1l', 'B', 'Bastion', 'Plate armor like fortress masonry: seams between the stones, battlements on the shoulders and on the helmet, like a tower. A narrow light blue slit, a rune on the chest. A full-height shield of a stone slab with the same rune on the back.'),
        ('r1m', 'C', 'Depths', 'Black rock, lava glows in the cracks. Boulders on the shoulders, a horned helmet with a red-hot slit. Stone spikes fanned out on the back and a red-hot core. The brightest of the three.')]),
    ('Ender', 'void step', '#1674d0', [
        ('r1n', 'A', 'Portal', 'Sleek blue plate armor with white sparks, like the Ender armor in Divine, white stripes and soles. A helmet with a diamond and an Ender eye, fins pointing up at the temples. A portal ring with pearls on the back.'),
        ('r1o', 'B', 'Ender Walker', 'Almost black plate armor in stars with blue glowing lines. Long eyes across the whole face, like an Enderman, long glowing claws. An Ender crystal in a diamond frame on the back.'),
        ('r1q', 'C', 'Dragon', 'Blue scales and light plates on the chest, a horned helmet with a snout, teeth and purple eyes, spikes on the shoulders. Leathery wings on the back, spread up and to the sides, not hanging like a cloak.')]),
    ('Corruption', 'ranged', '#594d9a', [
        ('r1p', 'A', 'Gunner', 'Blue-violet plate armor with purple spots and veins, a big eye on the chest. A helmet with three Grue eyes and a sewn mouth, bone spikes. A corruption cannon slanted across the back, the barrel glows.'),
        ('r1r', 'B', 'Hunter', 'A wide-brimmed hat with a purple ribbon and a feather, the face in shadow, only the eyes glow, the lower face covered by a scarf. Dark leather with a bandolier, a holster on the hip. A quiver of glowing arrows over the shoulder.'),
        ('r1x', 'C', 'Grue', 'Dark flesh, a toothy maw on the chest and a second one on the helmet under huge eyes, eyes on the shoulders. Tentacles with eyes at the ends grow from the back. The creepiest of the three.')]),
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
    """Front view as a separate frame for the overview."""
    p = Image.open(os.path.join(IMG, key + '.png')).convert('RGB')
    w, h = p.width // 2, p.height
    cut = int(w * 0.13)
    p.crop((cut, int(h * 0.05), w - cut, h)).save(os.path.join(IMG, key + '_f.png'), optimize=True)


def card(m, tag=None):
    key, letter, name, desc = m
    return ('<div class="card"><div class="pic"><img src="img/%s.png" alt=""><div class="views"><span>front</span><span>back</span></div></div>'
            '<div class="body"><div class="title"><span class="tag">%s</span>%s</div><p class="desc">%s</p></div></div>' % (key, e(tag or letter), e(name), e(desc)))


def foot(n):
    return '<div class="foot"><span>Wanderer armor looks, R1 fork</span><span>Page %d of 5</span></div>' % n


for _, _, _, vs in SETS:
    for v in vs:
        front(v[0])
parts = ['<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Wanderer armor looks - R1 fork</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s%s</style></head><body>' % (CSS, EXTRA)]
parts.append('<section class="page"><h1>Wanderer armor looks</h1><div class="sub">The first tier: five sets and variants</div><p class="lede">%s</p><div class="blocks">' % e(LEDE))
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
