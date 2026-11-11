"""Значки вещей из реликвариев миров (16x16, своя рисовка) и посадка Солнечного нимба на теле.

Запуск в WSL: python3 tools/relic_icons.py [папка_превью]
Пишет textures/items/: storm_bow_standby/_pulling_0..2, tomahawk, cloud_staff, soul_harvest, trinket_sun_halo, а с 0.99.113
вещи Лиоплевродона: tsunami_standby/_pulling_0..2, blood_bait, trinket_lio_fins, anchor_crab, anchor_lio (последние два
DerivedTexturePack подставляет вместо текстур якорей Divine). В wear/accessories.txt дописывает (заменяет) блоки "a sun_halo" -
золотое кольцо над головой на высоте от короны лича - и "a lio_fins" - ласты под подошвой, ширина ноги от крылышек айеракО, в
каждом профиле брони. acc_export.py перезаписывает accessories.txt - он вызывает append_halo() в конце.
"""
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '../src/main/resources/assets/divinerpgextended')
OUTLINE = (26, 19, 32, 255)


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def blank():
    return [[None] * 16 for _ in range(16)]


def put(g, x, y, c):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < 16 and 0 <= y < 16:
        g[y][x] = c


def outline(g, col=OUTLINE):
    """Тёмный контур вокруг закрашенного (по 4 соседям), как у ванильных значков."""
    add = []
    for y in range(16):
        for x in range(16):
            if g[y][x] is not None:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and g[ny][nx] is not None and g[ny][nx] != col:
                    add.append((x, y))
                    break
    for x, y in add:
        g[y][x] = col
    return g


def image(g):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if g[y][x] is not None:
                im.putpixel((x, y), g[y][x])
    return im


def line(g, x0, y0, x1, y1, c):
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(n + 1):
        t = i / n
        put(g, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, c)


# ---------------------------------------------------------------- Грозовой лук (Дикий лес)

STORM = dict(dark='1f3a6a', mid='3f6fc0', light='7fb6ff', tip='cfe3ff', grip='ffd84a', string='e8e8f0', shaft='8a6238', head='4a96ff',
             flet='e8e8f0', spark='ffd84a')
# Цунами (Лиоплевродон): глубокая вода, пенные концы, рукоять из зуба, бирюзовый наконечник
TSUNAMI = dict(dark='0a2c5c', mid='1f7fb0', light='5fd0e8', tip='f0fcff', grip='e8e2c8', string='c8f0ff', shaft='6b4a2a', head='3fd8c8',
               flet='f0fcff', spark='ffffff')


def bow(pull, pal=None):
    """pull: None - покой, 0..2 - натяжение. Как у ванильного лука: дуга выгнута к левому верхнему углу, тетива по диагонали."""
    g = blank()
    c = dict((k, hexc(v)) for k, v in (pal or STORM).items())
    dark, mid, light, tip, grip, string = c['dark'], c['mid'], c['light'], c['tip'], c['grip'], c['string']
    a, b, ctrl = (14.0, 1.0), (1.0, 14.0), (1.0, 1.0)
    pts = []
    for i in range(41):
        t = i / 40
        x = (1 - t) ** 2 * a[0] + 2 * (1 - t) * t * ctrl[0] + t * t * b[0]
        y = (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * ctrl[1] + t * t * b[1]
        pts.append((x, y, t))
    for x, y, t in pts:  # внешняя кромка темнее, внутренняя светлее
        put(g, x - 0.6, y - 0.6, dark)
    for x, y, t in pts:
        put(g, x, y, tip if t < 0.08 or t > 0.92 else (grip if 0.45 < t < 0.55 else (light if int(t * 20) % 3 == 0 else mid)))
    if pull is None:
        line(g, a[0], a[1], b[0], b[1], string)
    else:
        d = (1.5, 2.5, 3.5)[pull]
        p = (7.5 + d / math.sqrt(2) * 1.4, 7.5 + d / math.sqrt(2) * 1.4)
        line(g, a[0], a[1], p[0], p[1], string)
        line(g, p[0], p[1], b[0], b[1], string)
        # стрела Дикого леса: древко к левому верхнему, синий наконечник с искрой
        shaft, head, flet = c['shaft'], c['head'], c['flet']
        line(g, p[0] - 1, p[1] - 1, p[0] - 9 + d * 0.3, p[1] - 9 + d * 0.3, shaft)
        hx, hy = p[0] - 9 + d * 0.3, p[1] - 9 + d * 0.3
        put(g, hx, hy, head)
        put(g, hx - 1, hy, head)
        put(g, hx, hy - 1, head)
        put(g, hx - 1, hy - 1, c['spark'])
        put(g, p[0], p[1] - 1, flet)
        put(g, p[0] - 1, p[1], flet)
    return image(outline(g))


# ---------------------------------------------------------------- Томагавк (Апалачия)

def tomahawk():
    g = blank()
    wood, wood2, bind = hexc('6b4a2a'), hexc('8a6238'), hexc('d8c08a')
    for t in range(9):  # топорище с левого нижнего к правому верхнему
        x, y = 3 + t, 13 - t
        put(g, x, y, wood2 if t % 2 else wood)
        put(g, x + 1, y, wood)
    cx, cy = 12.4, 4.4
    dark, mid, edge = hexc('4a2066'), hexc('8a46c8'), hexc('e0b0ff')
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            u = (dx - dy) / math.sqrt(2)  # вдоль топорища
            v = (-dx - dy) / math.sqrt(2)  # к лезвию (влево-вверх)
            if 0 <= v <= 6.2 and abs(u) <= 1.3 + v * 0.62:
                g[y][x] = edge if v > 5.1 else (mid if v > 1.6 else dark)
            elif -2.2 <= v < 0 and abs(u) <= 1.1:
                g[y][x] = dark
    put(g, 11, 6, bind)
    put(g, 12, 6, bind)
    put(g, 13, 5, bind)
    put(g, 6, 11, hexc('c060ff'))  # перо-оберег на шнурке
    put(g, 5, 11, hexc('f0e6ff'))
    return image(outline(g))


# ---------------------------------------------------------------- Посох облаков (Небесный терн)

def cloud_staff():
    g = blank()
    s1, s2 = hexc('8a96aa'), hexc('cfd8e8')
    for t in range(11):
        x, y = 2 + t, 14 - t
        put(g, x, y, s2 if t % 3 else s1)
    white, light, shade, gem = hexc('ffffff'), hexc('eef6ff'), hexc('b8c8e0'), hexc('7fc8ff')
    for cx, cy, r in ((11.0, 4.5, 2.6), (8.6, 4.0, 1.8), (13.2, 3.4, 1.7), (11.6, 2.4, 1.9)):
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    g[y][x] = shade if y - cy > r * 0.45 else (white if y - cy < -r * 0.3 else light)
    put(g, 11, 4, gem)
    put(g, 12, 4, gem)
    return image(outline(g, hexc('46506a')))


# ---------------------------------------------------------------- Жатва душ (Мортум)

def soul_harvest():
    g = blank()
    spine, mid, edge = hexc('3a1418'), hexc('8a2a34'), hexc('ff6a74')
    for t in range(10):  # широкий клинок с левого нижнего к правому верхнему: обух снизу-слева, лезвие сверху-справа
        cx, cy = 4 + t, 10 - t
        put(g, cx, cy + 1, spine)
        put(g, cx, cy, mid)
        if t < 9:
            put(g, cx + 1, cy + 1, mid)
            put(g, cx + 1, cy, edge)
    for x, y in ((14, 1), (15, 2), (15, 3), (14, 4)):  # крюк жатвы у острия
        put(g, x, y, mid)
    put(g, 15, 4, edge)
    bone, grip, pom = hexc('e8e0c8'), hexc('2a1a1a'), hexc('d43a4a')
    for k in (-2, -1, 0, 1, 2):  # гарда поперёк клинка
        put(g, 3 + k, 11 + k, bone)
    put(g, 2, 12, grip)
    put(g, 1, 13, grip)
    put(g, 0, 14, pom)
    put(g, 9, 3, hexc('ff9aa2', 200))  # души
    put(g, 12, 8, hexc('ff9aa2', 200))
    return image(outline(g))


# ---------------------------------------------------------------- Солнечный нимб (Эдем)

def sun_halo():
    g = blank()
    dark, gold, light = hexc('9a6a1c'), hexc('f6d25e'), hexc('fff3b4')
    cx, cy, rx, ry = 7.5, 9.5, 6.4, 3.0
    for y in range(16):
        for x in range(16):
            e = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
            ei = ((x - cx) / (rx - 1.9)) ** 2 + ((y - cy) / (ry - 1.4)) ** 2
            if e <= 1.0 and ei > 1.0:
                g[y][x] = light if y < cy - 1 else (gold if y <= cy + 1 else dark)
    for x, y0, h in ((7, 1, 3), (8, 1, 3), (3, 3, 2), (12, 3, 2)):  # лучи над кольцом
        for y in range(y0, y0 + h):
            put(g, x, y, gold if y > y0 else light)
    return image(outline(g, hexc('5c3a0e')))


# ---------------------------------------------------------------- посадка нимба

def halo_block(acc_text):
    """Блок 'a sun_halo': по профилям из короны лича (её обод - первая коробка профиля) - кольцо выше на 3.4 пикселя."""
    out, cur, prof, done = ['a sun_halo'], None, None, set()
    for ln in acc_text.split('\n'):
        if ln.startswith('a '):
            cur = ln[2:].strip()
            continue
        if cur != 'lich_crown':
            continue
        if ln.startswith('p '):
            prof = ln[2:].strip()
            continue
        if ln.startswith('c head') and prof and prof not in done:
            f = ln.split()
            x, y, z, w = float(f[2]), float(f[3]), float(f[4]), float(f[5])
            done.add(prof)
            half = w / 2 * 0.78
            cx, cz = x + w / 2, z + w / 2
            yy = y - 3.4
            t = 0.7

            def fm(v):
                s = ('%.3f' % v).rstrip('0').rstrip('.')
                return '0' if s in ('-0', '') else s
            out.append('p %s' % prof)
            for bx, bz, bw, bd in ((cx - half, cz - half, 2 * half, t), (cx - half, cz + half - t, 2 * half, t),
                                   (cx - half, cz - half + t, t, 2 * half - 2 * t), (cx + half - t, cz - half + t, t, 2 * half - 2 * t)):
                out.append('c head %s %s %s %s 0.5 %s f6d25e' % (fm(bx), fm(yy), fm(bz), fm(bw), fm(bd)))
    assert len(done) >= 13, done
    return out


def fins_block(acc_text):
    """Блок 'a lio_fins': ласты под подошвой обеих ног, носок вперёд (-z). Половина ширины ноги в профиле - из крылышек айеракО
    (их спрайт стоит на 0.25 снаружи ноги); подошва опускается вместе с броней, но не больше чем на пиксель."""
    out, cur, prof, done = ['a lio_fins'], None, None, set()
    edge, main, rib = '0a2c5c', '1c5c9c', '5cb0f4'

    def fm(v):
        s = ('%.3f' % v).rstrip('0').rstrip('.')
        return '0' if s in ('-0', '') else s
    for ln in acc_text.split('\n'):
        if ln.startswith('a '):
            cur = ln[2:].strip()
            continue
        if cur != 'ayeraco_wing':
            continue
        if ln.startswith('p '):
            prof = ln[2:].strip()
            continue
        if ln.startswith('f rightLeg') and prof and prof not in done:
            done.add(prof)
            h = abs(float(ln.split()[2])) - 0.25
            sole = 12 + min(h - 2, 1.0)
            out.append('p %s' % prof)
            for leg in ('rightLeg', 'leftLeg'):
                # пластина под стопой с выносом вперёд, раструб у носка, две светлые прожилки сверху
                out.append('c %s %s %s %s %s 0.4 %s %s' % (leg, fm(-h + 0.2), fm(sole - 0.4), fm(-h - 3.0), fm(2 * h - 0.4), fm(2 * h + 2.4), main))
                out.append('c %s %s %s %s %s 0.3 2 %s' % (leg, fm(-h - 0.6), fm(sole - 0.35), fm(-h - 4.2), fm(2 * h + 1.2), edge))
                for rx in (-h * 0.45, h * 0.45 - 0.5):
                    out.append('c %s %s %s %s 0.5 0.2 3.4 %s' % (leg, fm(rx), fm(sole - 0.55), fm(-h - 3.6), rib))
    assert len(done) >= 13, done
    return out


def append_halo(path=os.path.join(RES, 'wear/accessories.txt')):
    """Свои посадки не из концептов: нимб и ласты (старые блоки заменяются)."""
    text = open(path, encoding='utf-8').read()
    lines, skip = [], False
    for ln in text.rstrip('\n').split('\n'):
        if ln.startswith('a '):
            skip = ln.strip() in ('a sun_halo', 'a lio_fins')
        if not skip:
            lines.append(ln)
    lines += halo_block(text)
    lines += fins_block(text)
    open(path, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')


# ---------------------------------------------------------------- рыбалка миров (0.99.112)

def sun_lure():
    """Солнечная блесна: золотая ложка-лепесток по диагонали, колечко сверху слева, крючок снизу справа."""
    g = blank()
    dark, gold, light = hexc('b8801c'), hexc('f6c43a'), hexc('fff0a0')
    cx, cy = 8.0, 8.0
    for y in range(16):
        for x in range(16):
            u = ((x - cx) + (y - cy)) / math.sqrt(2)  # вдоль ложки (к правому нижнему)
            v = ((x - cx) - (y - cy)) / math.sqrt(2)
            if (u / 4.6) ** 2 + (v / 2.6) ** 2 <= 1.0:
                g[y][x] = light if v < -0.9 else (gold if v < 0.9 else dark)
    ring = hexc('c8c8d0')
    for x, y in ((3, 2), (2, 3), (3, 4), (4, 3)):
        put(g, x, y, ring)
    hook = hexc('d8d8e0')
    for x, y in ((12, 12), (13, 13), (13, 14), (12, 14), (11, 13)):
        put(g, x, y, hook)
    put(g, 6, 6, hexc('ffffff'))
    return image(outline(g))


def wandering_soul():
    """Блуждающая душа: бледный огонёк-капля с хвостом, внутри алое ядро."""
    g = blank()
    pale, mid, core = hexc('f4d6d8'), hexc('e08a92'), hexc('c02a3a')
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 9.0
            if dx * dx / 16.0 + dy * dy / 20.0 <= 1.0 or (dy < -2 and abs(dx + (dy + 2) * 0.35) < 1.4 + (dy + 7) * 0.25 and dy > -8):
                g[y][x] = pale
    for y in range(16):
        for x in range(16):
            if g[y][x] is not None and (x - 7.5) ** 2 + (y - 9.5) ** 2 <= 6.5:
                g[y][x] = mid
            if g[y][x] is not None and (x - 7.5) ** 2 + (y - 10) ** 2 <= 2.0:
                g[y][x] = core
    put(g, 6, 9, hexc('2a0a10'))
    put(g, 9, 9, hexc('2a0a10'))
    return image(outline(g, hexc('7a2a34')))


def crate_cloud():
    """Ящик облачного пруда: ящик Небесного терна, высветленный к белому с голубым."""
    src = Image.open(os.path.join(RES, 'textures/items/crate_skythern.png')).convert('RGBA')
    out = Image.new('RGBA', src.size)
    for y in range(src.height):
        for x in range(src.width):
            r, gg, b, a = src.getpixel((x, y))
            if a == 0:
                continue
            lum = (r * 0.299 + gg * 0.587 + b * 0.114) / 255.0  # яркость дерева - в оттенки облака (бело-голубые)
            k = 0.55 + lum * 0.6
            out.putpixel((x, y), (min(255, int(200 * k)), min(255, int(218 * k)), min(255, int(245 * k)), a))
    return out


# ---------------------------------------------------------------- Лиоплевродон (0.99.113)

def pixmap(g, x0, y0, rows, pal):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                put(g, x0 + x, y0 + y, pal[ch])


def blood_bait():
    """Кровавая наживка: кусок сырого мяса, насаженный на серебряный крючок, капли крови снизу."""
    g = blank()
    dark, red, light, fat = hexc('6a0c14'), hexc('c0242c'), hexc('ec5a5a'), hexc('f4c0b0')
    steel, shine = hexc('a8b0bc'), hexc('eef2f8')
    for y in range(1, 12):  # цевьё крючка (под мясом его не видно - проткнуто)
        put(g, 11, y, steel)
    put(g, 11, 0, shine)
    for x, y in ((11, 12), (10, 13), (9, 13), (8, 12), (8, 11)):  # изгиб и жало
        put(g, x, y, steel)
    put(g, 9, 11, shine)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 6.5
            if (dx * 0.8 + dy * 0.45) ** 2 / 16.0 + (dy * 0.8 - dx * 0.45) ** 2 / 9.0 <= 1.0:
                g[y][x] = light if dy < -1.5 and dx < 1 else (dark if dy > 2.0 or dx > 3.0 else red)
    for x, y in ((6, 6), (7, 7), (9, 5), (10, 6)):  # прожилки жира
        put(g, x, y, fat)
    drop = hexc('8a0c18')
    for x, y in ((5, 12), (5, 13), (7, 14)):
        put(g, x, y, drop)
    return image(outline(g))


FIN = ['.aaaaa.',
       'abbcbba',
       'abcbcba',
       'abcbcba',
       'abcbcba',
       'abcbcba',
       '.abcba.',
       '.abcba.',
       '.abbba.',
       '..aba..',
       '.ddddd.',
       '.d.e.d.',
       '.ddddd.']


def lio_fins():
    """Ласты лиоплевродона: пара лопастей носком вверх (тёмно-синие, светлые прожилки), у пятки ремешок с пряжкой."""
    g = blank()
    pal = {'a': hexc('1c5c9c'), 'b': hexc('2a80d8'), 'c': hexc('8fd0ff'), 'd': hexc('3a3040'), 'e': hexc('e8e2c8')}
    pixmap(g, 1, 3, FIN, pal)
    pixmap(g, 8, 0, FIN, pal)
    return image(outline(g))


def anchor(pal):
    """Якорь стоймя: кольцо, шток с поперечиной, дуга лап со сходящимися лапами-зубьями по краям."""
    g = blank()
    dark, mid, light, acc = (hexc(v) for v in pal)
    for x, y in ((6, 0), (7, 0), (8, 0), (9, 0), (5, 1), (10, 1), (5, 2), (10, 2), (6, 3), (9, 3)):  # кольцо
        put(g, x, y, light if y < 2 else mid)
    for y in range(3, 14):  # шток 2 пикселя
        put(g, 7, y, light)
        put(g, 8, y, mid)
    for x in range(3, 13):  # поперечина
        put(g, x, 4, mid)
        put(g, x, 5, dark)
    put(g, 2, 4, acc)
    put(g, 13, 4, acc)
    for i in range(41):  # дуга лап
        t = i / 40.0
        a = math.pi * (0.08 + 0.84 * t)
        x, y = 7.5 - math.cos(a) * 5.8, 8.6 + math.sin(a) * 5.0
        put(g, x, y, mid if 0.25 < t < 0.75 else dark)
        put(g, x, y - 1, light if 0.3 < t < 0.7 else mid)
    for x, y in ((1, 8), (2, 8), (1, 9), (14, 8), (13, 8), (14, 9), (2, 7), (13, 7)):  # лапы
        put(g, x, y, acc)
    return image(outline(g))


def main():
    items = os.path.join(RES, 'textures/items')
    icons = {'storm_bow_standby': bow(None), 'storm_bow_pulling_0': bow(0), 'storm_bow_pulling_1': bow(1), 'storm_bow_pulling_2': bow(2),
             'tomahawk': tomahawk(), 'cloud_staff': cloud_staff(), 'soul_harvest': soul_harvest(), 'trinket_sun_halo': sun_halo(),
             'sun_lure': sun_lure(), 'wandering_soul': wandering_soul(), 'crate_cloud': crate_cloud(),
             'tsunami_standby': bow(None, TSUNAMI), 'tsunami_pulling_0': bow(0, TSUNAMI), 'tsunami_pulling_1': bow(1, TSUNAMI),
             'tsunami_pulling_2': bow(2, TSUNAMI), 'blood_bait': blood_bait(), 'trinket_lio_fins': lio_fins(),
             'anchor_crab': anchor(('7a1a0a', 'd0441c', 'f88848', 'ffc070')), 'anchor_lio': anchor(('0a1c50', '1f4fa8', '4f8fe8', 'e8e2c8'))}
    for name, im in icons.items():
        im.save(os.path.join(items, name + '.png'))
    append_halo()
    if len(sys.argv) > 1:  # превью: значки x12 на тёмном фоне
        sheet = Image.new('RGBA', (len(icons) * 200 + 20, 220), (34, 32, 44, 255))
        for i, (name, im) in enumerate(icons.items()):
            big = im.resize((192, 192), Image.NEAREST)
            sheet.paste(big, (20 + i * 200, 14), big)
        sheet.save(sys.argv[1])
    print('значков', len(icons), '+ посадка нимба и ласт')


if __name__ == '__main__':
    main()
