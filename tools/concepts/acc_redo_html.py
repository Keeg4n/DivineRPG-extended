"""Redo pages: altar 6 (new G, D, E), scarf 22A fit, carpet 28 (current, A, G, D)."""
import html
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
src = open('/root/mc/dre/tools/concepts/acc_build.py', encoding='utf-8').read()
CSS = re.search(r'CSS = """(.*?)"""', src, re.S).group(1)
import acc_redo as redo  # noqa: E402

e = html.escape
CSS += """
.alt3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 3mm; }
.alt3 .card { background: #1c1920; border: 1px solid #322c38; border-radius: 1.5mm; padding: 2mm; }
.alt3 .cube { width: 100%; display: block; border-radius: .8mm; }
.alt3 .faces { display: grid; grid-template-columns: 1fr 1fr; gap: 1.2mm; margin-top: 1.2mm; }
.alt3 .faces img { width: 100%; image-rendering: pixelated; display: block; border-radius: .6mm; }
.alt3 .cap { font-size: 7pt; color: #8f8794; text-align: center; margin-top: .5mm; }
.ttl { font-size: 9pt; color: #ece5da; margin-bottom: 1.2mm; }
.ttl b { color: #e3a347; font-weight: 600; margin-right: 1.2mm; }
.blocks { font-size: 7.2pt; color: #8f8794; line-height: 1.25; margin-top: 1.2mm; }
.fit { display: grid; grid-template-columns: 46mm repeat(3, 40mm); justify-content: space-between; gap: 2mm; align-items: center; background: #1c1920; border: 1px solid #322c38; border-radius: 1.5mm; padding: 1.6mm 2mm; margin-bottom: 1.6mm; }
.fit img { width: 100%; display: block; border-radius: .8mm; }
.fit .lab { font-size: 9pt; line-height: 1.3; color: #d9d2cc; }
.fit .lab b { color: #e3a347; font-weight: 600; display: block; font-size: 11pt; }
.fit .lab span { display: block; color: #8f8794; font-size: 7.4pt; margin-top: 1mm; }
.was { display: grid; grid-template-columns: 40mm 1fr; gap: 4mm; align-items: center; background: #1c1920; border: 1px solid #322c38; border-radius: 1.5mm; padding: 2mm; }
.was img { width: 100%; display: block; border-radius: .8mm; }
.cp { display: grid; grid-template-columns: repeat(2, 1fr); gap: 3mm; }
.cp .card { background: #1c1920; border: 1px solid #322c38; border-radius: 1.5mm; padding: 2mm; display: grid; grid-template-columns: 20mm 1fr 1fr; gap: 1.6mm; align-items: start; }
.cp .card .ttl { grid-column: 1 / -1; margin: 0; }
.cp .ico { width: 20mm; height: 20mm; }
.cp img.r { width: 100%; display: block; border-radius: .8mm; }
"""
CAP = '<div class="cap">%s</div>'

alt = ''
for j, (name, blocks, _, _) in enumerate(redo.ALT6):
    alt += ('<div class="card"><div class="ttl"><b>%s</b>%s</div><img class="cube" src="img/alt6_%d.jpg" alt="">'
            '<div class="faces"><div><img src="img/alt6_%d_s.png" alt="">%s</div><div><img src="img/alt6_%d_t.png" alt="">%s</div></div>'
            '<div class="blocks">%s</div></div>' % ('DEF'[j], e(name), j, j, CAP % 'side', j, CAP % 'top', e(blocks)))

fits = ''
NOTES = ['tail hangs in front over the chestplate', 'tail over the shoulder, along the back (third frame - from behind)', 'knot at the left shoulder, two short tails in front']
for j, (label, kind) in enumerate(redo.FITS):
    third = 'from behind' if kind == 'back' else 'from the side'
    fits += ('<div class="fit"><div class="lab"><b>A%d</b>%s<span>%s. Frames: Divine from the front, Wanderer from the front, Wanderer %s.</span></div>'
             '<img src="img/scarf_%d_d.jpg" alt=""><img src="img/scarf_%d_w.jpg" alt=""><img src="img/scarf_%d_w2.jpg" alt=""></div>'
             % (j + 1, e(label), e(NOTES[j]), third, j, j, j))

cp = ''
labels = ['Currently in game', 'A. Scarlet and gold (chosen)', 'D. Wave in flight', 'E. Night with stars']
for j, lb in enumerate(labels):
    cp += ('<div class="card"><div class="ttl">%s</div><img class="ico" src="img/carpet_%d.png" alt="">'
           '<img class="r" src="img/carpet_%d_d.jpg" alt=""><img class="r" src="img/carpet_%d_w.jpg" alt=""></div>' % (e(lb), j, j, j))

FOOT = '<div class="foot"><span>Concept rework</span><span>Page %d of 3</span></div>'
WAS = ('<div class="was"><img src="img/scarf_old_w.jpg" alt=""><p class="lede" style="margin:0">How it was: the collar sat at the height of the bottom of the helmet. On Wanderer armor the helmet ends above the chin, so the collar ended up'
       'at head level, and the head covered it: from outside only the tail on the chest and a red scrap at the shoulder were visible. Now the collar does not rise above the bottom of the head'
       'and is fitted to the most protruding part of the armor along its whole height, and the tail lies on the chestplate at its own height.</p></div>')
p1 = ('<section class="page"><h1>Rework: altar 6, scarf 22, carpet 28</h1>'
      '<div class="sub">What did not fit from the first sheet</div>'
      '<p class="lede">The choices for the rest are recorded: altars 1B 2B 3B 4A 5C 7B, accessories as listed. Only what was rejected or is in question is here.'
      'Answer the same way, with a number and a letter: for example, Alt 6E, Acc 22A2, Acc 28 current.</p>'
      '<h3>Alt 6. Jungle Lair Altar (Ancient Dramcryx)</h3>'
      '<p class="lede" style="margin:0 0 2.5mm">The previous three were motley: mossy cobblestone and small drawn figures. The new ones are built large, like the ones you liked: water in sandstone, snow cap and cracks in obsidian.</p>'
      '<div class="alt3">%s</div>%s</section>') % (alt, FOOT % 1)
p2 = ('<section class="page"><h3 style="margin-top:0">Acc 22A. Warm Scarf: how to wear it</h3>'
      '<p class="lede" style="margin:0 0 2.5mm">The pattern is the same, red and white stripes. The fit is fixed, what is left is to choose where to put the tail.</p>'
      '%s<div style="height:2mm"></div>%s%s</section>') % (WAS, fits, FOOT % 2)
p3 = ('<section class="page"><h3 style="margin-top:0">Acc 28. Flying Carpet</h3>'
      '<p class="lede" style="margin:0 0 2.5mm">The current icon next to the chosen A and two new ones. Under the icon is how the carpet lies underfoot on Divine and Wanderer armor. If you leave it as is, the answer is "28 current".</p>'
      '<div class="cp">%s</div>%s</section>') % (cp, FOOT % 3)

doc = ('<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Concept rework</title>'
       '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&display=block"><style>%s</style></head><body>%s%s</body></html>' % (CSS, p1, p2 + p3))
open(os.path.join(sys.argv[1] if len(sys.argv) > 1 else '/tmp/acc_redo', 'redo.html'), 'w', encoding='utf-8').write(doc)
print('html ok')
