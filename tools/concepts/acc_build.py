"""Лист выбора: аксессуары (значок и вид на двух бронях), лут с боссов, алтари, Часы Странника. Запуск: python3 acc_build.py <папка>; картинки в <папка>/img, рядом HTML для печати."""
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
LET = 'АБВ'
LANG = {}
for line in open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../src/main/resources/assets/divinerpgextended/lang/ru_RU.lang'), encoding='utf-8'):
    if '=' in line:
        k, v = line.rstrip('\n').split('=', 1)
        LANG[k] = v


def desc(key):
    return LANG.get('dre.trinket.%s.desc' % key, '').replace('в мире Энда', 'в мире Края').replace('в мире обычного мира', 'в обычном мире')


def rend(a, parts, frame, view, name):
    K.shot(a, parts, frame, view, w.POSE).save(os.path.join(IMG, name + '.jpg'), quality=90)


# ------------------------------------------------------------------ картинки
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
print('картинки готовы')

# ------------------------------------------------------------------ страницы
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
    return '<div class="foot"><span>%s</span><span>Страница @@PAGE@@ из @@ALL@@</span></div>' % e(section)


def first():
    d = lambda n, j: '<div class="two"><img src="img/a%d_%d_d.jpg" alt=""><img src="img/a%d_%d_w.jpg" alt=""></div>' % (n, j, n, j)
    idx = {it[0]: i for i, it in enumerate(acc_items1.ITEMS)}
    return ('<section class="page"><h1>Аксессуары, лут, алтари и часы</h1><div class="sub">Концепты на выбор: по три варианта на каждую вещь</div>'
            '<p class="lede">Как и с рыбами, ответ - номер и буква, например: Акс 1В, Лут 3Б, Алт 2А, Часы Б. Значки плоские, 16x16, рядом с каждым - вид на стандартной броне Divine и на броне Странника первой ступени. '
            'Буква выбирает сразу значок и вид на теле. В игре ничего из этого пока нет.</p>'
            '<div class="toc"><div><b>Часы Странника</b>3 варианта, по 5 текстур на уровни</div><div><b>Алтари</b>7 алтарей, по 3 варианта из ванильных блоков</div><div><b>Аксессуары</b>%d штук, по 3 значка и по 3 вида на теле</div><div><b>Лут с боссов</b>%d предметов и наборов, по 3 значка</div></div>'
            '<h3>Как это устроено у Ботании</h3><p class="lede" style="margin:0 0 2mm">Ботания рисует фенечки поверх модели игрока, в событии RenderPlayerEvent.Specials.Post. Два прохода: на теле и на голове (тот повторяет поворот головы). Есть три приёма, все три есть в вариантах ниже.</p>'
            '<div class="meth"><div>%s<b>Плоский значок с толщиной</b>Подвески, лавр, монокль: сам значок предмета, выдавленный на толщину. Подвеска отходит вперёд, если надет нагрудник. Дёшево: не нужно ни модели, ни отдельной текстуры.</div>'
            '<div>%s<b>Раздутая полоса на торсе</b>Пояса и плащ: своя текстура на чуть увеличенной коробке торса. Ложится на тело плотно. На каждую вещь нужна текстура.</div>'
            '<div>%s<b>Значки за спиной</b>Крылья тиары: два плоских значка, разведённых в стороны, у неё они ещё и машут. В вариантах крылья такие же, а короны, наплечники, фонари, колчаны и ножны собраны из маленьких коробок: Ботания так не делает, зато вещь объёмная.</div></div>'
            '<h3>Что будет иначе, чем у Ботании</h3><ul>'
            '<li><b>Кольца видно.</b> Ботания кольца не рисует совсем, здесь это ободок с камнем у кисти. Их можно и не показывать: скажи, если лишнее.</li>'
            '<li><b>Посадка считается от брони.</b> Вещь садится по поверхности: широкая Divine и тонкая броня Странника отличаются, поэтому смещение на каждый вид брони своё, а не одно на всех, как у подвески Ботании.</li>'
            '<li><b>Плащей нет:</b> на спине только крылья, колчаны и панцири, как договорились по броне Странника.</li>'
            '<li><b>Что надо будет сделать сверх картинок:</b> передавать клиентам, что на ком надето (сейчас аксессуары хранятся на сервере, клиент видит их только в открытом окне, другим игрокам они не приходят), прятать вещи при невидимости, как у Ботании, и дать настройку клиента на отключение.</li>'
            '</ul><h3>Что нужно решить кроме букв</h3><ul>'
            '<li><b>Встроенные в броню аксессуары показывать?</b> Я бы показывал так же, как надетые: иначе вещь пропадает с модели в тот момент, когда её встроили.</li>'
            '<li><b>Несколько вещей сразу.</b> Если надеты три вещи на грудь, они наложатся. Я бы разводил их по точкам крепления, для этого у каждого места нужно 2-3 слота на теле.</li>'
            '</ul>%s</section>') % (len(acc_items1.ITEMS), len(L.LOOT), d(idx['pharaoh_ankh'], 0), d(idx['blast_belt'], 1), d(idx['dream_wings_2'], 0), foot('Вводная'))


def clock_page():
    rows = ''
    for j, (let, name, text, fn) in enumerate(L.CLOCKS):
        tiers = ''.join('<div><img src="img/c%d_%d.png" alt=""><div>%s</div></div>' % (j, t, e(L.TIER_NAMES[t - 1])) for t in range(1, 6))
        rows += '<div class="clock"><div class="rt"><b>%s</b><i>%s</i></div><p>%s</p><div class="tiers">%s</div></div>' % (let, e(name), e(text), tiers)
    return ('<section class="page"><div class="head"><h2>Часы Странника</h2><span>один предмет, пять уровней</span></div>'
            '<p class="lede" style="margin:0 0 3mm">В коде это уже один предмет: уровень хранится в самом предмете и растёт шестернёй времени в верстаке. Не хватает только картинки: сейчас на всех уровнях стоит текстура сумеречных часов Divine. Ниже по пять текстур на каждый вариант, значок будет меняться с уровнем сам, для этого нужна маленькая правка кода.</p>'
            '<div class="rows">%s</div>%s</section>') % (rows, foot('Часы Странника'))


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
            rows += ('<div class="row alt"><div class="rt"><b>Алт %d</b><i>%s</i><em>%s</em></div><div class="rd">Призыв: %s. Плата: %s.</div><div class="cols">%s</div></div>' % (n + 1, e(title), e(now), e(boss), e(cost), cols))
        head = ('<div class="head"><h2>Алтари призыва</h2><span>куб, справа грани: бок и верх</span></div>')
        if i == 0:
            head += '<p class="lede" style="margin:0 0 2.5mm">Сейчас алтарь - это целиком один ванильный блок. В вариантах настоящие ванильные текстуры (подписаны под картинкой) собраны в новый блок со своим узором. Блок всё так же неразрушим и светится.</p>'
        out.append('<section class="page">%s<div class="rows">%s</div>%s</section>' % (head, rows, foot('Алтари')))
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
            rows += '<div class="row"><div class="rt"><b>Акс %d</b><i>%s</i><em>%s</em></div><div class="rd">%s</div><div class="cols">%s</div></div>' % (n + 1, e(name), e(slot), e(desc(key)), cols)
        head = '<div class="head"><h2>Аксессуары</h2><span>в каждой тройке: значок, на броне Divine, на броне Странника</span></div>'
        out.append('<section class="page">%s<div class="rows">%s</div>%s</section>' % (head, rows, foot('Аксессуары')))
    return out


def loot_pages():
    out, cur, h = [], [], 0
    LIM = 258

    def flush():
        nonlocal cur, h
        if cur:
            out.append('<section class="page"><div class="head"><h2>Новый лут с боссов</h2><span>значки 16x16</span></div><div class="rows">%s</div>%s</section>' % (''.join(cur), foot('Лут с боссов')))
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
            names = '<div class="names">Слева направо: %s.</div>' % ', '.join(x for x, _ in L.WORLDS)
        if size == 12:
            names = '<div class="names">Слева направо, сверху вниз: %s.</div>' % ', '.join(x for x, _ in L.SPARKS)
        rows_h = 44 if size == 12 else (33 if size == 4 else 31)
        if h + rows_h > LIM:
            flush()
        cur.append('<div class="row"><div class="rt"><b>Лут %d</b><i>%s</i><em>%s</em></div>%s<div class="cols" style="margin-top:.6mm">%s</div></div>' % (n + 1, e(name), e(src), names, cols))
        h += rows_h
    flush()
    return out


pages = [first(), clock_page()] + altar_pages() + acc_pages() + loot_pages()
doc = ('<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Аксессуары, лут, алтари и часы</title>'
       '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&display=block"><style>%s</style></head><body>' % CSS)
for i, p in enumerate(pages, 1):
    doc += p.replace('@@PAGE@@', str(i)).replace('@@ALL@@', str(len(pages)))
doc += '</body></html>'
open(os.path.join(OUT, 'concepts.html'), 'w', encoding='utf-8').write(doc)
print('страниц', len(pages))
