"""Раунды 2+ концептов брони Странника (A4): обложка с уже выбранными обликами + по листу на ступень с новыми вариантами.
Запуск: python wprint2.py <out.html> [модуль данных раунда, по умолчанию wprint_data]. Картинки ждёт в img/ рядом с HTML."""
import html
import importlib
import sys

OUT = sys.argv[1]
d = importlib.import_module(sys.argv[2] if len(sys.argv) > 2 else 'wprint_data')
CSS = importlib.import_module('wprint_data').CSS
e = html.escape

parts = ['<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Облики брони Странника - %s</title>' % e(d.ROUND.lower()),
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;600&family=Pixelify+Sans:wght@700&display=block">',
         '<style>%s</style></head><body>' % CSS]
parts.append('<section class="page"><h1>Облики брони Странника</h1><div class="sub">%s</div>' % e(d.ROUND))
parts.append('<p class="lede">%s</p>' % e(d.LEDE))
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
    parts.append('</div><div class="foot"><span>Облики брони Странника, %s</span><span>R%d из 7</span></div></section>' % (e(d.ROUND.lower()), r))
parts.append('</body></html>')
open(OUT, 'w', encoding='utf-8').write('\n'.join(parts))
print('ok', OUT)
