"""Страницы переделки: алтарь 6 (новые Г, Д, Е), посадка шарфа 22А, ковёр 28 (сейчас, А, Г, Д)."""
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
            '<div class="blocks">%s</div></div>' % ('ГДЕ'[j], e(name), j, j, CAP % 'бок', j, CAP % 'верх', e(blocks)))

fits = ''
NOTES = ['хвост висит спереди поверх нагрудника', 'хвост через плечо, по спине (третий кадр - со спины)', 'узел у левого плеча, два коротких хвоста спереди']
for j, (label, kind) in enumerate(redo.FITS):
    third = 'со спины' if kind == 'back' else 'сбоку'
    fits += ('<div class="fit"><div class="lab"><b>А%d</b>%s<span>%s. Кадры: Divine спереди, Странник спереди, Странник %s.</span></div>'
             '<img src="img/scarf_%d_d.jpg" alt=""><img src="img/scarf_%d_w.jpg" alt=""><img src="img/scarf_%d_w2.jpg" alt=""></div>'
             % (j + 1, e(label), e(NOTES[j]), third, j, j, j))

cp = ''
labels = ['Сейчас в игре', 'А. Алый с золотом (выбран)', 'Г. Волна в полёте', 'Д. Ночной со звёздами']
for j, lb in enumerate(labels):
    cp += ('<div class="card"><div class="ttl">%s</div><img class="ico" src="img/carpet_%d.png" alt="">'
           '<img class="r" src="img/carpet_%d_d.jpg" alt=""><img class="r" src="img/carpet_%d_w.jpg" alt=""></div>' % (e(lb), j, j, j))

FOOT = '<div class="foot"><span>Переделка концептов</span><span>Страница %d из 3</span></div>'
WAS = ('<div class="was"><img src="img/scarf_old_w.jpg" alt=""><p class="lede" style="margin:0">Так было: воротник садился на высоту низа шлема. У брони Странника шлем кончается выше подбородка, поэтому воротник оказывался '
       'на уровне головы, и голова его закрывала: снаружи были видны только хвост на груди и красный клочок у плеча. Теперь воротник не поднимается выше низа головы '
       'и подгоняется по самой выступающей части брони на всей своей высоте, а хвост ложится на нагрудник на своей высоте.</p></div>')
p1 = ('<section class="page"><h1>Переделка: алтарь 6, шарф 22, ковёр 28</h1>'
      '<div class="sub">Что не подошло из первого листа</div>'
      '<p class="lede">Выбор по остальным записан: алтари 1Б 2Б 3Б 4А 5В 7Б, аксессуары - по списку. Здесь только то, что забраковано или под вопросом. '
      'Ответ так же номером и буквой: например, Алт 6Д, Акс 22А2, Акс 28 сейчас.</p>'
      '<h3>Алт 6. Алтарь логова джунглей (Древний драмкрикс)</h3>'
      '<p class="lede" style="margin:0 0 2.5mm">Прошлые три были пёстрыми: мшистый булыжник и мелкие нарисованные фигурки. Новые собраны крупно, как понравившиеся вода в песчанике, снежная шапка и трещины в обсидиане.</p>'
      '<div class="alt3">%s</div>%s</section>') % (alt, FOOT % 1)
p2 = ('<section class="page"><h3 style="margin-top:0">Акс 22А. Тёплый шарф: как носить</h3>'
      '<p class="lede" style="margin:0 0 2.5mm">Рисунок тот же, красно-белые полосы. Посадка исправлена, осталось выбрать, куда девать хвост.</p>'
      '%s<div style="height:2mm"></div>%s%s</section>') % (WAS, fits, FOOT % 2)
p3 = ('<section class="page"><h3 style="margin-top:0">Акс 28. Ковёр-самолёт</h3>'
      '<p class="lede" style="margin:0 0 2.5mm">Нынешний значок рядом с выбранным А и двумя новыми. Под значком - как ковёр лежит под ногами на броне Divine и Странника. Если оставить как есть, ответ "28 сейчас".</p>'
      '<div class="cp">%s</div>%s</section>') % (cp, FOOT % 3)

doc = ('<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Переделка концептов</title>'
       '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&display=block"><style>%s</style></head><body>%s%s</body></html>' % (CSS, p1, p2 + p3))
open(os.path.join(sys.argv[1] if len(sys.argv) > 1 else '/tmp/acc_redo', 'redo.html'), 'w', encoding='utf-8').write(doc)
print('html ok')
