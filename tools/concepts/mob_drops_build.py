"""Лист выбора значков дропа мобов: python3 mob_drops_build.py <папка>; картинки в <папка>/img, рядом concepts.html для печати в PDF."""
import html
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import acc_kit as K  # noqa: E402
import mob_drops as M  # noqa: E402

OUT = sys.argv[1]
IMG = os.path.join(OUT, 'img')
os.makedirs(IMG, exist_ok=True)
TEX = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../src/main/resources/assets/divinerpgextended/textures/items')
e = html.escape
LET = 'АБВ'
COLOR = {'Эдем': '#e8c828', 'Дикий лес': '#5cb0f4', 'Апалачия': '#c868ec', 'Небесный терн': '#dadee4', 'Мортум': '#c08050'}


def save(img16, name):
    img16.resize((96, 96), Image.NEAREST).save(os.path.join(IMG, name + '.png'))
    img16.resize((32, 32), Image.NEAREST).save(os.path.join(IMG, name + '_s.png'))


for n, (key, name, mob, world, use, vs) in enumerate(M.DROPS):
    save(Image.open(os.path.join(TEX, 'twilight_mat_%s.png' % key)).convert('RGBA'), 'm%d_now' % n)
    for j, (label, fn) in enumerate(vs):
        save(Image.fromarray(K.icon(fn), 'RGBA'), 'm%d_%d' % (n, j))

CSS = """
@page { size: A4 portrait; margin: 0; }
* { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
html, body { margin: 0; background: #131116; color: #ece5da; font-family: "Golos Text", "Segoe UI", sans-serif; }
.page { width: 210mm; height: 297mm; padding: 11mm 12mm 9mm; position: relative; page-break-after: always; overflow: hidden; }
.head { display: flex; justify-content: space-between; align-items: baseline; border-bottom: .4mm solid #3a3340; padding-bottom: 2mm; margin-bottom: 3mm; }
.head h2 { margin: 0; font-size: 6.2mm; font-weight: 600; }
.head span { color: #9a90a4; font-size: 3.3mm; }
.lede { font-size: 3.4mm; line-height: 1.45; color: #cfc6d6; margin: 0 0 3.5mm; }
.lede b { color: #ece5da; }
.world { font-size: 4mm; font-weight: 600; margin: 3mm 0 1.6mm; padding-left: 2.4mm; border-left: 1.4mm solid; }
.row { display: grid; grid-template-columns: 44mm 1fr; gap: 3mm; background: #1c1920; border-radius: 2mm; padding: 2.4mm 3mm; margin-bottom: 2.2mm; }
.rt b { display: block; font-size: 3.2mm; color: #9a90a4; font-weight: 600; }
.rt i { display: block; font-style: normal; font-size: 4.1mm; font-weight: 600; margin: .6mm 0 1mm; }
.rt em { display: block; font-style: normal; font-size: 2.9mm; color: #a99fb2; line-height: 1.35; }
.cols { display: grid; grid-template-columns: repeat(4, 1fr); gap: 2mm; }
.v { background: #241f29; border-radius: 1.6mm; padding: 1.6mm; text-align: center; }
.v.now { background: #18161b; border: .3mm dashed #4a4252; }
.vt { font-size: 2.9mm; line-height: 1.25; min-height: 7.4mm; color: #d6cede; }
.vt b { color: #f0c860; margin-right: 1mm; font-size: 3.4mm; }
.now .vt b { color: #8c8296; }
.pics { display: flex; align-items: flex-end; justify-content: center; gap: 2mm; margin-top: 1mm; }
.pics img { image-rendering: pixelated; }
.pics .big { width: 21mm; height: 21mm; }
.pics .sm { width: 6.8mm; height: 6.8mm; background: #8b8b8b; padding: .4mm; border-radius: .4mm; }
.foot { position: absolute; left: 12mm; right: 12mm; bottom: 6mm; display: flex; justify-content: space-between; font-size: 2.8mm; color: #6f6678; }
"""


def foot():
    return '<div class="foot"><span>DivineRPG Extended - дроп мобов сумеречных миров</span><span>Страница @@PAGE@@ из @@ALL@@</span></div>'


def row(n):
    key, name, mob, world, use, vs = M.DROPS[n]
    cols = '<div class="v now"><div class="vt"><b>-</b>Сейчас в игре</div><div class="pics"><img class="big" src="img/m%d_now.png" alt=""><img class="sm" src="img/m%d_now_s.png" alt=""></div></div>' % (n, n)
    for j, (label, _) in enumerate(vs):
        cols += ('<div class="v"><div class="vt"><b>%s</b>%s</div><div class="pics"><img class="big" src="img/m%d_%d.png" alt=""><img class="sm" src="img/m%d_%d_s.png" alt=""></div></div>'
                 % (LET[j], e(label), n, j, n, j))
    return ('<div class="row"><div class="rt"><b>Мат %d</b><i>%s</i><em>Падает: %s<br>Нужен: %s</em></div><div class="cols">%s</div></div>'
            % (n + 1, e(name), e(mob), e(use), cols))


def world_block(world, idx):
    return '<div class="world" style="border-color:%s">%s</div>%s' % (COLOR[world], e(world), ''.join(row(n) for n in idx))


by_world = {}
for n, d in enumerate(M.DROPS):
    by_world.setdefault(d[3], []).append(n)

lede = ('<p class="lede">Материалы, которые аддон добавил в дроп обычных мобов сумеречных миров: по три с каждого мира, рога кадиллионов идут в <b>Зов кадиллиона</b>, '
        'все - в <b>закалку брони и лука</b> своего мира. Падают с убитого игроком моба в 35% случаев (+10% за уровень Добычи). Сейчас у них временные значки (первая клетка в каждой строке). На каждый материал три варианта: '
        'крупно и в размере ячейки инвентаря (маленький квадрат на сером фоне). <b>Ответ - номер и буква</b>, например "Мат 2 Б"; можно смешивать идеи '
        '("форма из А, цвет из В") или оставить нынешний значок.</p>')
pages = [
    ('Дроп мобов: значки на выбор', lede + world_block('Эдем', by_world['Эдем'])),
    ('Дроп мобов: значки на выбор', world_block('Дикий лес', by_world['Дикий лес']) + world_block('Апалачия', by_world['Апалачия'])),
    ('Дроп мобов: значки на выбор', world_block('Небесный терн', by_world['Небесный терн']) + world_block('Мортум', by_world['Мортум'])),
]
doc = ('<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Дроп мобов - концепты</title>'
       '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&display=block"><style>%s</style></head><body>' % CSS)
for i, (title, body) in enumerate(pages, 1):
    doc += ('<section class="page"><div class="head"><h2>%s</h2><span>15 материалов, по 3 варианта</span></div>%s%s</section>' % (e(title), body, foot())).replace(
        '@@PAGE@@', str(i)).replace('@@ALL@@', str(len(pages)))
doc += '</body></html>'
open(os.path.join(OUT, 'concepts.html'), 'w', encoding='utf-8').write(doc)
print('страниц', len(pages))
