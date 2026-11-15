"""Sheet of R8 concepts (A4): the tier for completing Vethea, three looks to choose from; R7 as in the game next to them for comparison.
Run: python wprint_r8.py <out.html>. Expects images in img/ next to the HTML: r8a..r8c (wr8.py) and r7z (wslim5.py)."""
import html
import os
import sys

from PIL import Image

from wprint_data import CSS

OUT = sys.argv[1]
e = html.escape
IMG = os.path.join(os.path.dirname(os.path.abspath(OUT)), 'img')

LEDE = ('The eighth tier of Wanderer armor is for completing Vethea, after Lady Luna. Only on it the Crown opens up: one property of your choice - '
        'flight, health regeneration or arcana flow. The flight is full, without limits. R7 no longer has these three perks. '
        'Below are three R8 looks, pick one: for example, R8 B.')

R7 = ('r7z', 'R7 now', 'Halite', 'The tier before R8, as in the game: Chlorophyte-style armor with a sharp helmet, horns and scales. For comparing the silhouette and color.')

VARIANTS = [
    ('r8a', 'A', 'Phantom', '#8ccff4', 'In the spirit of Terraria spectral armor. Pale light blue plate armor with glowing lines and a diamond on the chest, pauldrons with a glowing edge. '
     'Ragged ghostly ribbons from the belt, growing lighter and glowing toward the ends. A dark blue hood, the face is a smooth white mask with narrow light blue slits. Three wisps with tails behind the back.'),
    ('r8b', 'B', 'Lunar', '#b89ae8', 'The Lady Luna theme. Silver plate armor with lilac, a glowing crescent on the chest, pauldrons with a sickle crest. '
     'A smooth helmet with a lilac visor, a crescent floats above it with the horns up. A large moon sickle with a glowing edge on the back.'),
    ('r8c', 'C', 'Dreamer', '#e3c46a', 'Vethea is a world of dreams. Indigo in stars, a gold thread along the edges, a diamond with a turquoise stone on the chest. '
     'A smooth mask with closed eyes, an open third eye on the brow, gold plates at the temples. A dreamcatcher on the back: a gold ring, threads, a bead and three feathers.'),
]

EXTRA = """
.tag { font-family: "Golos Text", sans-serif; font-weight: 600; color: #e3a347; margin-right: 2mm; }
.page .grid .card { flex: 1 1 0; }
.trio { display: grid; grid-template-columns: repeat(3, 1fr); gap: 3mm; margin-top: 5mm; }
.trio .t { border: 1px solid #322c38; border-radius: 2mm; overflow: hidden; background: #1c1920; }
.trio img { width: 100%; display: block; }
.trio .lab { padding: 1.5mm 3mm 2mm; border-top: 1.2mm solid; display: flex; gap: 2mm; align-items: baseline; font-size: 11pt; }
.trio .lab b { color: #e3a347; font-weight: 600; }
.ref { margin-top: 4mm; display: flex; }
.head h2 { font-family: "Golos Text", sans-serif; font-weight: 600; }
.sub { font-family: "Golos Text", sans-serif; font-weight: 600; }
.solo { border: 1px solid #322c38; border-radius: 2mm; overflow: hidden; background: #1c1920; margin-top: 3mm; }
.solo img { width: 100%; display: block; }
.solo .vv { display: grid; grid-template-columns: 1fr 1fr; text-align: center; color: #a39aa6; font-size: 8.5pt; padding: 1.2mm 0 1.6mm; letter-spacing: .08em; text-transform: uppercase; }
.solotext { margin-top: 4mm; padding-left: 3mm; font-size: 11pt; line-height: 1.5; color: #d9d2cc; }

.crown { display: grid; grid-template-columns: repeat(3, 1fr); gap: 3mm; margin-top: 4mm; }
.crown div { border-top: 1mm solid #e3a347; padding-top: 1.5mm; font-size: 9pt; color: #a39aa6; line-height: 1.35; }
.crown b { display: block; color: #ece5da; font-size: 10.5pt; font-weight: 600; }
"""


def front(key):
    """Front view as a separate frame for the overview."""
    p = Image.open(os.path.join(IMG, key + '.png')).convert('RGB')
    w, h = p.width // 2, p.height
    cut = int(w * 0.13)
    p.crop((cut, int(h * 0.05), w - cut, h)).save(os.path.join(IMG, key + '_f.png'), optimize=True)


def card(key, tag, name, desc):
    return ('<div class="card"><div class="pic"><img src="img/%s.png" alt=""><div class="views"><span>front</span><span>back</span></div></div>'
            '<div class="body"><div class="title"><span class="tag">%s</span>%s</div><p class="desc">%s</p></div></div>' % (key, e(tag), e(name), e(desc)))


def foot(n):
    return '<div class="foot"><span>Wanderer armor looks, R8</span><span>Page %d of 4</span></div>' % n


for v in VARIANTS:
    front(v[0])
front(R7[0])
parts = ['<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Wanderer armor looks - R8</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s%s</style></head><body>' % (CSS, EXTRA)]
parts.append('<section class="page"><h1>Wanderer armor looks</h1><div class="sub">R8: for completing Vethea</div><p class="lede">%s</p>' % e(LEDE))
parts.append('<div class="crown"><div><b>Flight</b>As in creative mode, without limits.</div><div><b>Regeneration</b>Health grows on its own.</div><div><b>Arcana flow</b>Arcana restores faster.</div></div>')
parts.append('<div class="trio">%s</div>' % ''.join('<div class="t"><img src="img/%s_f.png" alt=""><div class="lab" style="border-color:%s"><b>%s</b>%s</div></div>' % (k, c, e(l), e(n)) for k, l, n, c, _ in VARIANTS))
parts.append('<div class="ref"><div class="t" style="width:52mm;border:1px solid #322c38;border-radius:2mm;overflow:hidden;background:#1c1920"><img src="img/%s_f.png" alt="" style="width:100%%;display:block"></div>'
             '<p class="lede" style="margin:0 0 0 4mm;align-self:center"><b style="color:#e3a347">%s, %s.</b> %s</p></div>' % (R7[0], e(R7[1]), e(R7[2]), e(R7[3])))
parts.append(foot(1) + '</section>')
for n, v in enumerate(VARIANTS, 2):
    parts.append('<section class="page"><div class="head"><h2>Variant %s: %s</h2></div>'
                 '<div class="solo"><img src="img/%s.png" alt=""><div class="vv"><span>front</span><span>back</span></div></div>'
                 '<p class="lede solotext" style="border-left:1.2mm solid %s">%s</p>%s</section>' % (e(v[1]), e(v[2]), v[0], v[3], e(v[4]), foot(n)))
parts.append('</body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
