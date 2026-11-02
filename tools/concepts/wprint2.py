"""Второй раунд концептов брони Странника (A4): обложка с уже выбранными обликами + по листу на R1, R4, R5, R7 (варианты Г, Д, Е).
Пишет HTML по пути из argv[1]; картинки ждёт в img/ рядом."""
import html
import sys

import wprint_data as d

OUT = sys.argv[1]
e = html.escape

parts = ['<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Облики брони Странника - второй раунд</title>',
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s</style></head><body>' % d.CSS]
parts.append('<section class="page"><h1>Облики брони Странника</h1><div class="sub">Второй раунд</div>')
parts.append('<p class="lede">По первому раунду выбраны три облика, они ниже. Для R1, R4, R5 и R7 - по три новых варианта, '
             'буквы Г, Д, Е, чтобы не путать с первым раундом. R1 теперь делается из божественной брони, поэтому все три новых R1 '
             'идут от неё: золото, тёмно-алые вставки, голубой камень, стальные ботинки. Модели так же черновые: из блоков, на костях '
             'игрока, слева вид спереди, справа сзади.</p>')
parts.append('<h3>Выбрано</h3><div class="picked">')
for key, r, world, ln, title, desc in d.PICKED:
    parts.append('<div class="card"><div class="pic"><img src="img/%s.png" alt=""><div class="views"><span>спереди</span><span>сзади</span></div></div>'
                 '<div class="body"><div class="title"><span class="ln">R%d</span>%s</div><div class="title t2"><span class="ln">%s</span>%s</div>'
                 '<p class="desc">%s</p></div></div>' % (key, r, e(world), ln, e(title), e(desc)))
parts.append('</div>')
parts.append('<div class="toc">' + ''.join('<div style="border-color:%s"><span>R%d</span>%s<em>%s</em></div>' % (c, r, e(n), e(st))
                                           for r, n, c, st in d.TOC) + '</div></section>')
for r, name, meta, chips, cs in d.TIERS:
    parts.append('<section class="page"><div class="head"><h2><span class="rank">R%d</span> %s</h2><span class="chips">%s</span><span class="meta">%s</span></div>'
                 % (r, e(name), ''.join('<i style="background:%s"></i>' % c for c in chips), e(meta)))
    parts.append('<div class="grid">')
    for key, ln, title, desc in cs:
        parts.append('<div class="card"><div class="pic"><img src="img/%s.png" alt=""><div class="views"><span>спереди</span><span>сзади</span></div></div>'
                     '<div class="body"><div class="title"><span class="ln">%s</span>%s</div><p class="desc">%s</p></div></div>'
                     % (key, ln, e(title), e(desc)))
    parts.append('</div><div class="foot"><span>Облики брони Странника, второй раунд</span><span>R%d из 7</span></div></section>' % r)
parts.append('</body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
