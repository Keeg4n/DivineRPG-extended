"""Selection sheet: accessories (icon and look on two armors), boss loot, altars, Wanderer's Clock. Run: python3 acc_build.py <folder>; images go to <folder>/img, with an HTML for printing next to them."""
import html
import os
import re
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import acc_kit as K  # noqa: E402
import wconcepts as w  # noqa: E402
import acc_items1  # noqa: E402
import acc_items2  # noqa: E402,F401
import acc_loot as L  # noqa: E402
import acc_altars as AL  # noqa: E402

OUT = sys.argv[1]
IMG = os.path.join(OUT, 'img')
os.makedirs(IMG, exist_ok=True)
e = html.escape
LET = 'ABC'
LANG = {}
for line in open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../src/main/resources/assets/divinerpgextended/lang/en_US.lang'), encoding='utf-8'):
    if '=' in line:
        k, v = line.rstrip('\n').split('=', 1)
        LANG[k] = v


def desc(key):
    return LANG.get('dre.trinket.%s.desc' % key, '')


def rend(a, parts, frame, view, name):
    K.shot(a, parts, frame, view, w.POSE).save(os.path.join(IMG, name + '.jpg'), quality=90)


# ------------------------------------------------------------------ images
dv, wa = K.divine_armor(w.POSE), K.wanderer_armor(w.POSE)
for n, (key, name, slot, vs) in enumerate(acc_items1.ITEMS):
    for j, (label, fn, wear, frame, view) in enumerate(vs):
        ic = K.icon(fn)
        K.save_icon(ic, os.path.join(IMG, 'a%d_%d.png' % (n, j)), 6)
        rend(dv, wear(dv, ic), frame, view, 'a%d_%d_d' % (n, j))
        rend(wa, wear(wa, ic), frame, view, 'a%d_%d_w' % (n, j))
for n, (key, name, src, vs) in enumerate(L.LOOT):
    for j, (label, ic) in enumerate(vs):
        for k, fn in enumerate(ic if isinstance(ic, list) else [ic]):
            K.save_icon(K.icon(fn), os.path.join(IMG, 'l%d_%d_%d.png' % (n, j, k)), 6)
for j, (let, name, text, fn) in enumerate(L.CLOCKS):
    for t in range(1, 6):
        K.save_icon(K.icon(fn(t)), os.path.join(IMG, 'c%d_%d.png' % (j, t)), 8)
for n, (title, boss, cost, now, vs) in enumerate(AL.ALTARS):
    for j, (name, blocks, side, top) in enumerate(vs):
        s, t = side(), top()
        AL.block_render(s, t).save(os.path.join(IMG, 'b%d_%d.jpg' % (n, j)), quality=92)
        AL.flat_tex(s, os.path.join(IMG, 'b%d_%d_s.png' % (n, j)))
        AL.flat_tex(t, os.path.join(IMG, 'b%d_%d_t.png' % (n, j)))
print('images ready')

# ------------------------------------------------------------------ pages
CSS = """
@page { size: A4 portrait; margin: 0; }
* { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
html, body { margin: 0; background: #131116; color: #ece5da; font-family: "Golos Text", "Segoe UI", sans-serif; }
.page { width: 210mm; height: 297mm; padding: 10mm 11mm 8mm; page-break-after: always; break-after: page; display: flex; flex-direction: column; overflow: hidden; }
.page:last-child { page-break-after: auto; break-after: auto; }
h1 { font-size: 25pt; font-weight: 600; margin: 0; line-height: 1.15; }
h2 { font-size: 15pt; font-weight: 600; margin: 0; }
.sub { color: #e3a347; font-size: 12pt; margin-top: 1mm; }
.lede { color: #b9b0bc; font-size: 10pt; line-height: 1.45; margin: 3mm 0 0; }
.head { display: flex; align-items: baseline; justify-content: space-between; gap: 4mm; border-bottom: 1px solid #322c38; padding-bottom: 2mm; margin-bottom: 2.5mm; }
.head span { color: #a39aa6; font-size: 8.5pt; }
.rows { display: flex; flex-direction: column; gap: 1.6mm; }
.row { background: #1c1920; border: 1px solid #322c38; border-radius: 1.5mm; padding: 1.4mm 2mm 1.6mm; }
.rt { display: flex; align-items: baseline; gap: 2mm; font-size: 9.5pt; }
.rt b { color: #e3a347; font-weight: 600; min-width: 5mm; }
.rt i { font-style: normal; font-weight: 600; }
.rt em { font-style: normal; color: #8f8794; font-size: 8pt; }
.rd { color: #a39aa6; font-size: 7.6pt; line-height: 1.25; margin: .3mm 0 1mm; }
.cols { display: grid; grid-template-columns: repeat(3, 1fr); gap: 2mm; }
.v .vt { font-size: 7.6pt; color: #d9d2cc; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; height: 3.4mm; line-height: 3.4mm; }
.v .vt b { color: #e3a347; font-weight: 600; margin-right: 1mm; }
.pics { display: flex; gap: .8mm; align-items: flex-start; margin-top: .4mm; }
.ico { width: 13mm; height: 13mm; image-rendering: pixelated; background: #2b2732; border-radius: .8mm; flex: none; }
.ren { width: 21.8mm; height: 25.6mm; display: block; border-radius: .8mm; }
.foot { margin-top: auto; padding-top: 2mm; display: flex; justify-content: space-between; color: #6e6674; font-size: 8pt; }
.toc { display: grid; grid-template-columns: repeat(4, 1fr); gap: 2.5mm; margin-top: 4mm; }
.toc div { border-top: 1mm solid #e3a347; padding-top: 1.5mm; font-size: 9pt; color: #a39aa6; line-height: 1.3; }
.toc b { display: block; color: #ece5da; font-size: 11pt; font-weight: 600; }
h3 { font-size: 11.5pt; font-weight: 600; color: #e3a347; margin: 5mm 0 2mm; }
.meth { display: grid; grid-template-columns: repeat(3, 1fr); gap: 2.5mm; }
.meth > div { background: #1c1920; border: 1px solid #322c38; border-radius: 1.5mm; padding: 2mm; font-size: 8.6pt; line-height: 1.35; color: #b9b0bc; }
.meth b { display: block; color: #ece5da; font-size: 9.6pt; font-weight: 600; margin-bottom: 1mm; }
.meth .two { display: flex; gap: 1mm; margin-bottom: 1.5mm; }
.meth .two img { width: 50%; display: block; border-radius: .8mm; }
ul { margin: 0; padding-left: 4.5mm; font-size: 9pt; line-height: 1.4; color: #b9b0bc; }
li { margin-bottom: 1mm; }
li b { color: #ece5da; font-weight: 600; }
.clock { background: #1c1920; border: 1px solid #322c38; border-radius: 1.5mm; padding: 2.5mm; }
.clock p { margin: .8mm 0 2mm; color: #a39aa6; font-size: 8.8pt; line-height: 1.35; }
.tiers { display: grid; grid-template-columns: repeat(5, 1fr); gap: 2mm; }
.tiers img { width: 100%; image-rendering: pixelated; background: #2b2732; border-radius: 1mm; display: block; }
.tiers div div { text-align: center; font-size: 7.6pt; color: #a39aa6; margin-top: .8mm; }
.alt .pics { align-items: stretch; }
.blk { width: 38mm; height: 38mm; border-radius: .8mm; display: block; }
.fl { display: flex; flex-direction: column; gap: 1mm; }
.fl img { width: 18.5mm; height: 18.5mm; image-rendering: pixelated; display: block; border-radius: .6mm; }
.vb { font-size: 6.8pt; color: #8f8794; line-height: 1.2; margin-top: .6mm; height: 5mm; overflow: hidden; }
.set { display: grid; gap: .6mm; margin-top: .5mm; }
.set img { width: 100%; image-rendering: pixelated; background: #2b2732; border-radius: .6mm; display: block; }
.names { color: #8f8794; font-size: 7pt; line-height: 1.25; }
"""
pages = []


def foot(section):
    return '<div class="foot"><span>%s</span><span>Page @@PAGE@@ of @@ALL@@</span></div>' % e(section)


def first():
    d = lambda n, j: '<div class="two"><img src="img/a%d_%d_d.jpg" alt=""><img src="img/a%d_%d_w.jpg" alt=""></div>' % (n, j, n, j)
    idx = {it[0]: i for i, it in enumerate(acc_items1.ITEMS)}
    return ('<section class="page"><h1>Accessories, loot, altars and clocks</h1><div class="sub">Concepts to choose from: three variants for each item</div>'
            '<p class="lede">As with the fish, the answer is a number and a letter, for example: Acc 1C, Loot 3B, Alt 2A, Clock B. Icons are flat, 16x16, and next to each one is the view on standard Divine armor and on first-tier Wanderer armor.'
            'The letter picks both the icon and the on-body look. None of this is in the game yet.</p>'
            '<div class="toc"><div><b>Wanderer\'s Clock</b>3 variants, 5 textures for the levels</div><div><b>Altars</b>7 altars, 3 variants each from vanilla blocks</div><div><b>Accessories</b>%d items, 3 icons and 3 on-body looks each</div><div><b>Boss loot</b>%d items and sets, 3 icons each</div></div>'
            '<h3>How it works in Botania</h3><p class="lede" style="margin:0 0 2mm">Botania draws baubles on top of the player model, in the RenderPlayerEvent.Specials.Post event. Two passes: on the body and on the head (the latter follows the head rotation). There are three techniques, and all three appear in the variants below.</p>'
            '<div class="meth"><div>%s<b>Flat icon with thickness</b>Pendants, laurel, monocle: the item icon itself, extruded to a thickness. The pendant moves forward if a chestplate is worn. Cheap: needs neither a model nor a separate texture.</div>'
            '<div>%s<b>Inflated band on the torso</b>Belts and cloak: a dedicated texture on a slightly enlarged torso box. Fits the body snugly. Each item needs a texture.</div>'
            '<div>%s<b>Icons on the back</b>Tiara wings: two flat icons spread apart, which also flap. In the variants the wings are the same, while crowns, pauldrons, lanterns, quivers and scabbards are assembled from small boxes: Botania does not do that, but it makes the item look three-dimensional.</div></div>'
            '<h3>What will differ from Botania</h3><ul>'
            '<li><b>Rings are visible.</b> Botania does not draw rings at all; here it is a band with a gem at the wrist. They can also be left hidden: tell me if it is excessive.</li>'
            '<li><b>Fit is computed from the armor.</b> An item sits on the surface: wide Divine armor and thin Wanderer armor differ, so the offset is separate for each armor type rather than one for all, as with the Botania pendant.</li>'
            '<li><b>No cloaks:</b> only wings, quivers and shells on the back, as agreed for Wanderer armor.</li>'
            '<li><b>What needs doing beyond the pictures:</b> sending clients what each player is wearing (currently accessories are stored on the server and the client sees them only in the open window; other players do not receive them), hiding items when invisible, as in Botania, and adding a client setting to turn it off.</li>'
            '</ul><h3>What needs deciding besides the letters</h3><ul>'
            '<li><b>Show accessories built into armor?</b> I would show them the same as worn ones: otherwise the item disappears from the model at the moment it is built in.</li>'
            '<li><b>Several items at once.</b> If three items are worn on the chest, they will overlap. I would spread them across attachment points, which needs 2-3 body slots per area.</li>'
            '</ul>%s</section>') % (len(acc_items1.ITEMS), len(L.LOOT), d(idx['pharaoh_ankh'], 0), d(idx['blast_belt'], 1), d(idx['dream_wings_2'], 0), foot('Introduction'))


def clock_page():
    rows = ''
    for j, (let, name, text, fn) in enumerate(L.CLOCKS):
        tiers = ''.join('<div><img src="img/c%d_%d.png" alt=""><div>%s</div></div>' % (j, t, e(L.TIER_NAMES[t - 1])) for t in range(1, 6))
        rows += '<div class="clock"><div class="rt"><b>%s</b><i>%s</i></div><p>%s</p><div class="tiers">%s</div></div>' % (let, e(name), e(text), tiers)
    return ('<section class="page"><div class="head"><h2>Wanderer\'s Clock</h2><span>one item, five levels</span></div>'
            '<p class="lede" style="margin:0 0 3mm">In code it is already a single item: the level is stored in the item itself and grows with a time gear in the workbench. Only the picture is missing: right now every level uses the Divine twilight clock texture. Below are five textures for each variant; the icon will change with the level by itself, which needs a small code change.</p>'
            '<div class="rows">%s</div>%s</section>') % (rows, foot('Wanderer\'s Clock'))


def altar_pages():
    out = []
    A = AL.ALTARS
    for i in range(0, len(A), 4):
        rows = ''
        for n in range(i, min(i + 4, len(A))):
            title, boss, cost, now, vs = A[n]
            cols = ''
            for j, (name, blocks, _, _) in enumerate(vs):
                cols += ('<div class="v"><div class="vt"><b>%s</b>%s</div><div class="pics"><img class="blk" src="img/b%d_%d.jpg" alt=""><div class="fl"><img src="img/b%d_%d_s.png" alt=""><img src="img/b%d_%d_t.png" alt=""></div></div><div class="vb">%s</div></div>'
                         % (LET[j], e(name), n, j, n, j, n, j, e(blocks)))
            rows += ('<div class="row alt"><div class="rt"><b>Alt %d</b><i>%s</i><em>%s</em></div><div class="rd">Summon: %s. Cost: %s.</div><div class="cols">%s</div></div>' % (n + 1, e(title), e(now), e(boss), e(cost), cols))
        head = ('<div class="head"><h2>Summoning altars</h2><span>cube, faces on the right: side and top</span></div>')
        if i == 0:
            head += '<p class="lede" style="margin:0 0 2.5mm">Right now an altar is a single vanilla block. In the variants, real vanilla textures (captioned under the picture) are assembled into a new block with its own pattern. The block is still indestructible and glows.</p>'
        out.append('<section class="page">%s<div class="rows">%s</div>%s</section>' % (head, rows, foot('Altars')))
    return out


def acc_pages():
    out = []
    items = acc_items1.ITEMS
    per = 6
    for i in range(0, len(items), per):
        rows = ''
        for n in range(i, min(i + per, len(items))):
            key, name, slot, vs = items[n]
            cols = ''
            for j, (label, *_rest) in enumerate(vs):
                cols += ('<div class="v"><div class="vt"><b>%s</b>%s</div><div class="pics"><img class="ico" src="img/a%d_%d.png" alt=""><img class="ren" src="img/a%d_%d_d.jpg" alt=""><img class="ren" src="img/a%d_%d_w.jpg" alt=""></div></div>' % (LET[j], e(label), n, j, n, j, n, j))
            rows += '<div class="row"><div class="rt"><b>Acc %d</b><i>%s</i><em>%s</em></div><div class="rd">%s</div><div class="cols">%s</div></div>' % (n + 1, e(name), e(slot), e(desc(key)), cols)
        head = '<div class="head"><h2>Accessories</h2><span>in each triple: icon, on Divine armor, on Wanderer armor</span></div>'
        out.append('<section class="page">%s<div class="rows">%s</div>%s</section>' % (head, rows, foot('Accessories')))
    return out


def loot_pages():
    out, cur, h = [], [], 0
    LIM = 258

    def flush():
        nonlocal cur, h
        if cur:
            out.append('<section class="page"><div class="head"><h2>New boss loot</h2><span>16x16 icons</span></div><div class="rows">%s</div>%s</section>' % (''.join(cur), foot('Boss loot')))
        cur, h = [], 0

    for n, (key, name, src, vs) in enumerate(L.LOOT):
        multi = isinstance(vs[0][1], list)
        size = len(vs[0][1]) if multi else 1
        cols = ''
        for j, (label, ic) in enumerate(vs):
            count = len(ic) if isinstance(ic, list) else 1
            ncol = 6 if count > 4 else (4 if count == 4 else 1)
            imgs = ''.join('<img src="img/l%d_%d_%d.png" alt="">' % (n, j, k) for k in range(count))
            style = 'grid-template-columns: repeat(%d, 1fr);' % ncol if ncol > 1 else 'grid-template-columns: 18mm;'
            cols += '<div class="v"><div class="vt"><b>%s</b>%s</div><div class="set" style="%s">%s</div></div>' % (LET[j], e(label), style, imgs)
        names = ''
        if size == 4:
            names = '<div class="names">Left to right: %s.</div>' % ', '.join(x for x, _ in L.WORLDS)
        if size == 12:
            names = '<div class="names">Left to right, top to bottom: %s.</div>' % ', '.join(x for x, _ in L.SPARKS)
        rows_h = 44 if size == 12 else (33 if size == 4 else 31)
        if h + rows_h > LIM:
            flush()
        cur.append('<div class="row"><div class="rt"><b>Loot %d</b><i>%s</i><em>%s</em></div>%s<div class="cols" style="margin-top:.6mm">%s</div></div>' % (n + 1, e(name), e(src), names, cols))
        h += rows_h
    flush()
    return out


pages = [first(), clock_page()] + altar_pages() + acc_pages() + loot_pages()
doc = ('<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Accessories, loot, altars and clocks</title>'
       '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&display=block"><style>%s</style></head><body>' % CSS)
for i, p in enumerate(pages, 1):
    doc += p.replace('@@PAGE@@', str(i)).replace('@@ALL@@', str(len(pages)))
doc += '</body></html>'
open(os.path.join(OUT, 'concepts.html'), 'w', encoding='utf-8').write(doc)
print('pages', len(pages))
