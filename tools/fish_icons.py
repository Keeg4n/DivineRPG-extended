"""Значки рыб 16x16: сразу по диагонали, как у ванильных (голова снизу слева, хвост сверху справа). Обычный поворот картинки
размыл бы пиксели, поэтому каждый пиксель считается в координатах рыбы: u - от головы к хвосту, v - от спины к брюху.

Запуск: python tools/fish_icons.py - ставит выбранные варианты (CHOSEN) в текстуры предметов;
python tools/fish_icons.py <папка> - кладёт в папку все варианты: <рыба>_a.png, _b.png, _c.png.
"""
import math
import os
import sys

from PIL import Image

R2 = math.sqrt(0.5)


def uv(x, y):
    px, py = x + 0.5 - 8, y + 0.5 - 8
    return (px - py) * R2, (px + py) * R2


def xy(u, v):
    return int(math.floor((u + v) * R2 + 8)), int(math.floor((-u + v) * R2 + 8))


def rgb(h):
    return (h >> 16) & 255, (h >> 8) & 255, h & 255


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t + 0.5) for i in range(3))


def dark(c, t=0.5):
    return mix(c, (10, 8, 16), t)


def light(c, t=0.45):
    return mix(c, (255, 255, 255), t)


WHITE, BLACK, RED, GOLD, CYAN, LIME, PINK, ORANGE, YELLOW, BONE = (240, 240, 245), (24, 20, 28), (214, 48, 40), (244, 196, 60), (110, 240, 250), (120, 240, 90), (240, 130, 190), (255, 130, 40), (255, 226, 90), (226, 216, 190)


class Fish:
    """shape: head - кончик морды, end - конец тела, w - половина толщины, peak - где тело толще (0..1), tail_end - кончик хвоста,
    tw - половина ширины хвоста, tail: fork/fan/crescent/none, dorsal/ventral: (начало, конец, высота) или None, wave: (амплитуда, частота) для угрей."""

    def __init__(self, main, head=-8.6, end=3.6, w=3.7, peak=0.42, tail_end=9.2, tw=3.6, tail='fork', dorsal=(-3.5, 2.5, 1.6), ventral=(-1.0, 2.0, 1.0), wave=None,
                 back=None, belly=None, fin=None, pattern='scales', pat=None, period=3.0, tailc=None, eye=(-5.6, -0.34), eyec=BLACK, flat=False, body=None, extras=()):
        self.main = rgb(main) if isinstance(main, int) else main
        self.head, self.end, self.w, self.peak, self.tail_end, self.tw, self.tail = head, end, w, peak, tail_end, tw, tail
        self.dorsal, self.ventral, self.wave = dorsal, ventral, wave
        self.back = back or dark(self.main, 0.32)
        self.belly = belly or light(self.main, 0.42)
        self.fin = fin or dark(self.main, 0.22)
        self.tailc = tailc or self.fin
        self.pattern, self.pat, self.period, self.eye, self.eyec, self.flat, self.body, self.extras = pattern, pat or dark(self.main, 0.4), period, eye, eyec, flat, body, extras

    def center(self, u):
        return self.wave[0] * math.sin(u * self.wave[1]) if self.wave else 0.0

    def half(self, u):
        if u < self.head or u > self.end:
            return -1.0
        t = (u - self.head) / (self.end - self.head)
        if self.body:
            return self.body(t, u) * self.w
        p = math.log(0.5) / math.log(self.peak)
        return max(0.9, self.w * math.sin(math.pi * t ** p) ** 0.72) if t > 0.03 else 0.6

    def region(self, u, v):
        vv = v - self.center(u)
        h = self.half(u)
        if h >= 0 and abs(vv) <= h:
            return 'body'
        if self.dorsal and self.dorsal[0] <= u <= self.dorsal[1] and vv < 0 and h >= 0:
            t = (u - self.dorsal[0]) / (self.dorsal[1] - self.dorsal[0])
            if -vv <= h + self.dorsal[2] * math.sin(math.pi * t ** 0.8):
                return 'fin'
        if self.ventral and self.ventral[0] <= u <= self.ventral[1] and vv > 0 and h >= 0:
            t = (u - self.ventral[0]) / (self.ventral[1] - self.ventral[0])
            if vv <= h + self.ventral[2] * math.sin(math.pi * t):
                return 'fin'
        if self.tail != 'none' and self.end - 0.2 < u <= self.tail_end:
            t = max(0.0, (u - self.end) / (self.tail_end - self.end))
            hw = 0.8 + (t ** 1.25 if self.tail == 'fan' else t) * (self.tw - 0.8)
            a = abs(vv)
            if a > hw:
                return None
            if self.tail == 'fan' and u > self.tail_end - 1.1 * (1 - min(1.0, a / self.tw)):
                return None
            if self.tail == 'fork' and u > self.tail_end - 2.2 * (1 - min(1.0, a / self.tw)):
                return None
            if self.tail == 'crescent' and (u > self.tail_end - 3.4 * (1 - min(1.0, a / self.tw)) or (t > 0.45 and a < hw - 1.7)):
                return None
            return 'tail'
        return None

    def color(self, x, y, u, v):
        vv = v - self.center(u)
        h = max(0.5, self.half(u))
        s = vv / h
        t = (u - self.head) / (self.end - self.head)
        c = self.back if s < -0.42 else self.belly if s > 0.5 else self.main
        p = self.pattern
        if p == 'scales' and -0.42 <= s <= 0.5 and (x + y) % 2 == 0:
            c = mix(self.main, self.back, 0.45)
        elif p == 'stripes' and t > 0.22 and (u - self.head) % self.period < 1.1:
            c = self.pat
        elif p == 'bands' and (u + 0.6 * vv) % self.period < 1.0:
            c = self.pat
        elif p == 'spots' and abs(s) < 0.95 and (x * 7 + y * 13) % 11 < 2:
            c = self.pat
        elif p == 'marble' and (x * 3 + y * 5) % 7 < 2:
            c = self.pat
        elif p == 'gradient':
            c = mix(self.main, self.pat, min(1.0, t * 1.15))
            if s > 0.55:
                c = light(c, 0.3)
            elif s < -0.5:
                c = dark(c, 0.22)
        elif p == 'line' and abs(s - 0.05) < 0.28:
            c = self.pat
        elif p == 'facets':
            c = self.main if (int(math.floor(u * 0.62)) + int(math.floor(vv * 0.62))) % 2 == 0 else self.belly
            if s < -0.55:
                c = self.back
        elif p == 'cracks' and abs(((u * 0.9 + vv * 1.4) % 3.1) - 1.5) < 0.42:
            c = self.pat
        elif p == 'koi' and (x * 5 + y * 3) % 9 < 4:
            c = self.pat
        elif p == 'ribs' and t > 0.25 and (u - self.head) % self.period < 0.9 and abs(s) < 0.72:
            c = self.pat
        return c

    def render(self):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        label = {}
        for y in range(16):
            for x in range(16):
                u, v = uv(x, y)
                r = self.region(u, v)
                if r:
                    label[(x, y)] = r
        for (x, y), r in label.items():
            u, v = uv(x, y)
            c = self.color(x, y, u, v) if r == 'body' else (self.tailc if r == 'tail' else self.fin)
            if r == 'tail' and self.tail == 'fan':  # лучи веера
                c = dark(c, 0.2) if int(abs(v - self.center(u)) * 1.25) % 2 else light(c, 0.12)
            elif r != 'body' and (x + y) % 2 == 0:
                c = light(c, 0.2)
            if any((x + dx, y + dy) not in label for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                c = dark(c, 0.48 if r == 'body' else 0.34)
            img.putpixel((x, y), c + (255,))
        if self.eye:
            eu = self.eye[0]
            ev = self.center(eu) + self.eye[1] * max(0.5, self.half(eu))
            ex, ey = xy(eu, ev)
            if 0 <= ex < 16 and 0 <= ey < 16:
                img.putpixel((ex, ey), self.eyec + (255,))
        for e in self.extras:
            e(self, img)
        return img


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(c) + (255,))


def dots(c, *points):
    """Пиксели в координатах значка."""
    def draw(f, img):
        for x, y in points:
            put(img, x, y, c)
    return draw


def on_fish(c, *points):
    """Пиксели в координатах рыбы (u, v): так детали едут вместе с формой."""
    def draw(f, img):
        for u, v in points:
            x, y = xy(u, v)
            put(img, x, y, c)
    return draw


def spines(c, *us):
    """Острые шипы по спине."""
    def draw(f, img):
        for u in us:
            h = max(0.5, f.half(u))
            x, y = xy(u, f.center(u) - h - 1.3)
            put(img, x, y, c)
    return draw


def diamond(t, u=None):
    return max(0.15, 1 - abs(t - 0.5) * 2) ** 0.85


def ray_body(t, u=None):
    """Скат: острые крылья и вогнутые края, иначе ромб на диагонали выглядит квадратом."""
    a = t / 0.42 if t < 0.42 else (1 - t) / 0.58
    return max(0.12, a ** 1.45)


def round_body(t, u=None):
    return max(0.25, math.sqrt(max(0.0, 1 - (2 * t - 1) ** 2)))


def eel_body(t, u=None):
    return 1.0 if 0.1 < t < 0.92 else 0.72


def shard(t, u=None):
    return min(1.0, t * 3.2 + 0.2) if t < 0.3 else max(0.28, 1.25 - t * 1.05)


def wide_head(t, u=None):
    return min(1.0, 0.72 + t * 2.0) if t < 0.2 else max(0.3, 1.14 - t * 0.85)


EEL = dict(head=-9.2, end=6.4, w=2.05, tail='fan', tail_end=9.6, tw=2.2, wave=(1.35, 0.6), dorsal=None, ventral=None, body=eel_body, eye=(-7.2, -0.25))
EEL_STRAIGHT = dict(head=-9.4, end=6.2, w=2.0, tail='fan', tail_end=9.6, tw=2.3, dorsal=(-4.0, 6.0, 0.8), ventral=None, body=eel_body, eye=(-7.6, -0.25))
ROUND = dict(head=-8.0, end=3.0, w=5.0, peak=0.48, tail_end=8.8, tw=3.8, dorsal=(-3.0, 2.0, 1.3), ventral=(-2.0, 1.5, 1.0), eye=(-5.0, -0.32))
SLIM = dict(head=-9.0, end=4.6, w=2.9, peak=0.38, tail_end=9.6, tw=3.0, dorsal=(-2.5, 2.0, 1.4), ventral=(0.0, 2.5, 0.8), eye=(-6.8, -0.3))
SHARK = dict(head=-9.2, end=4.4, w=2.9, peak=0.36, tail_end=9.6, tw=4.0, tail='crescent', dorsal=(-3.0, 0.8, 3.0), ventral=(-2.2, -0.2, 1.8), eye=(-6.8, -0.28))
PUFF = dict(head=-6.2, end=5.0, w=5.6, peak=0.5, tail_end=8.6, tw=2.0, tail='fan', dorsal=None, ventral=None, body=round_body, eye=(-3.2, -0.42))
RAY = dict(head=-8.4, end=2.4, w=7.2, tail='none', dorsal=None, ventral=None, body=ray_body, eye=(-5.4, -0.2))
FAN = dict(tail='fan', tail_end=9.8, tw=4.8)


def G(**k):
    return k


VARIANTS = {
    'sea_bass': (
        ('Классический', G(main=0x5A7FA8)),
        ('Полосатый', G(main=0x7E9CBC, pattern='stripes', pat=(46, 70, 104), period=2.8, dorsal=(-4.0, 2.8, 2.0))),
        ('Глубинный', G(main=0xB8C8D4, back=(38, 72, 120), belly=(236, 242, 246), pattern='line', pat=(60, 110, 170), fin=(40, 70, 110), head=-9.0, w=3.2)),
    ),
    'coral_wrasse': (
        ('С белыми полосами', dict(ROUND, main=0xF07A5A, pattern='stripes', pat=WHITE, period=3.9, fin=(200, 70, 40))),
        ('Разноцветный', dict(ROUND, main=(70, 210, 190), pattern='gradient', pat=(245, 120, 70), fin=(245, 170, 70), tailc=(245, 120, 70))),
        ('Коралловый', dict(ROUND, **dict(FAN, main=(245, 130, 170), pattern='spots', pat=(255, 226, 120), fin=(240, 90, 110), tailc=(255, 110, 130)))),
    ),
    'shark_pup': (
        ('Акулёнок', dict(SHARK, main=0x8A9AA8, belly=(238, 240, 244), pattern='none', fin=(96, 108, 120))),
        ('Тигровый', dict(SHARK, main=0x9AA08C, belly=(236, 234, 220), pattern='stripes', pat=(62, 66, 58), period=2.6, fin=(90, 96, 80))),
        ('Синий', dict(SHARK, main=(70, 120, 190), back=(44, 80, 140), belly=(232, 240, 250), pattern='none', fin=(50, 90, 150), w=2.5)),
    ),
    'ruff': (
        ('Колючий', G(main=0x8A8A5A, dorsal=(-4.0, 3.0, 1.5), extras=(spines((60, 58, 34), -3.2, -1.2, 0.8),))),
        ('Пятнистый', G(main=0xA09A62, pattern='spots', pat=(62, 60, 34), fin=(120, 110, 60))),
        ('Лобастый', G(main=0x7C8450, head=-8.0, peak=0.3, w=4.1, end=2.6, fin=(226, 130, 50), tailc=(226, 130, 50))),
    ),
    'gold_crucian': (
        ('Золотой', dict(ROUND, main=0xF0C030, fin=(216, 150, 30))),
        ('Вуалехвост', dict(ROUND, **dict(FAN, main=0xF4B428, belly=(255, 240, 190), pattern='none', fin=(255, 214, 130), tailc=(255, 226, 150)))),
        ('Сверкающий', dict(ROUND, main=0xFFD84A, pattern='none', fin=(230, 160, 40), extras=(dots(WHITE, (6, 7), (8, 9), (7, 11), (11, 3)),))),
    ),
    'ice_trout': (
        ('Крапчатая', dict(SLIM, main=0xA8D8F0, pattern='spots', pat=(60, 110, 150))),
        ('Радужная', dict(SLIM, main=(180, 214, 230), back=(60, 100, 130), belly=WHITE, pattern='line', pat=(240, 140, 170), fin=(90, 140, 170))),
        ('Ледяная', dict(SLIM, main=(190, 234, 250), pattern='facets', extras=(spines(WHITE, -4.0, -1.8, 0.4, 2.4),))),
    ),
    'snow_burbot': (
        ('Налим с усом', dict(SLIM, main=0xE8F4FF, w=3.2, head=-9.4, end=5.2, tail='fan', tail_end=9.6, tw=2.4, dorsal=(-2.5, 5.2, 0.9), pattern='scales', extras=(on_fish((120, 150, 180), (-9.4, 0.8), (-10.2, 1.5)),))),
        ('Мраморный', dict(SLIM, main=(214, 226, 238), pat=(120, 140, 164), pattern='marble', w=2.9, end=5.0, tail='fan', tail_end=9.6, tw=2.6, dorsal=(-2.0, 5.0, 0.9))),
        ('Снежный', dict(SLIM, main=(246, 250, 255), back=(190, 214, 236), belly=WHITE, pattern='none', fin=(110, 170, 225), w=3.0, tail='fan', tail_end=9.8, tw=3.0, dorsal=(-4.0, 3.6, 1.4))),
    ),
    'crystal_fish': (
        ('Ромб', dict(RAY, main=0x9AF0FF, back=(70, 170, 210), pattern='facets', body=diamond, w=5.0, end=3.6, tail='fork', tail_end=9.4, tw=3.2, head=-9.0, eye=(-5.8, -0.3))),
        ('С искрами', dict(RAY, main=(150, 220, 255), back=(70, 130, 220), pattern='facets', body=diamond, w=5.0, end=3.6, tail='fan', tail_end=9.4, tw=3.2, head=-9.0, eye=(-5.8, -0.3), extras=(dots(WHITE, (7, 7), (9, 9), (5, 9)),))),
        ('Осколки', dict(PUFF, main=(170, 245, 255), back=(70, 170, 215), pattern='facets', w=4.6, extras=(on_fish(WHITE, (-5.0, -5.4), (0.0, -6.2), (4.2, -4.8), (-5.0, 5.4), (0.0, 6.2), (4.2, 4.8)),))),
    ),
    'piranha': (
        ('С красным брюхом', dict(ROUND, main=0x6AA040, back=(50, 84, 40), belly=(214, 58, 44), pattern='none', head=-8.4, extras=(on_fish(WHITE, (-8.0, 0.8), (-7.2, 1.7)),))),
        ('Серебристая', dict(ROUND, main=(176, 190, 196), back=(90, 104, 112), belly=(226, 70, 50), pattern='scales', fin=(190, 60, 50), head=-8.4, extras=(on_fish(WHITE, (-8.0, 0.8), (-7.2, 1.7)),))),
        ('Болотная', dict(ROUND, main=(52, 84, 58), back=(26, 44, 34), belly=(130, 170, 70), pattern='stripes', pat=(120, 190, 80), period=3.2, fin=(30, 60, 40), eyec=(240, 60, 40), head=-8.4, extras=(on_fish(WHITE, (-8.0, 0.8), (-7.2, 1.7)),))),
    ),
    'emerald_catfish': (
        ('Сомик с усами', G(main=0x30D080, body=wide_head, w=3.9, head=-8.2, dorsal=(-2.5, 1.5, 1.2), eye=(-6.2, -0.45), extras=(on_fish((20, 90, 60), (-8.8, -2.2), (-9.6, -3.0), (-8.8, 2.2), (-9.6, 3.0)),))),
        ('Золотые точки', G(main=(38, 170, 110), body=wide_head, w=3.9, head=-8.2, pattern='spots', pat=GOLD, dorsal=(-2.5, 1.5, 1.2), eye=(-6.2, -0.45), extras=(on_fish((20, 90, 60), (-8.8, -2.2), (-9.6, -3.0), (-8.8, 2.2), (-9.6, 3.0)),))),
        ('С изумрудом', G(main=(30, 110, 76), back=(18, 70, 50), belly=(70, 180, 120), body=wide_head, w=3.9, head=-8.2, pattern='none', dorsal=(-2.5, 1.5, 1.2), eye=(-6.2, -0.45), extras=(on_fish((120, 255, 170), (-3.2, -0.6), (-2.2, -0.6), (-2.7, 0.3)), on_fish((20, 90, 60), (-8.8, -2.2), (-9.6, -3.0), (-8.8, 2.2), (-9.6, 3.0)),))),
    ),
    'sand_carp': (
        ('Песчаный', G(main=0xD8C080, w=4.2, fin=(180, 150, 90))),
        ('С сёдлами', G(main=0xE0CC92, pattern='stripes', pat=(150, 110, 66), period=3.4, w=4.2, fin=(160, 120, 70))),
        ('Пустынный', G(main=0xCDB070, pattern='marble', pat=(166, 132, 80), w=4.2, fin=(226, 130, 60), tailc=(226, 130, 60))),
    ),
    'mummy_fish': (
        ('В бинтах', dict(ROUND, main=0xC8B898, pattern='bands', pat=(232, 224, 200), period=2.3, fin=(150, 136, 110))),
        ('Хвост-кость', dict(ROUND, main=0xBFAE8C, pattern='bands', pat=(226, 216, 190), period=2.3, tailc=BONE, fin=BONE, tail='fork')),
        ('Проклятая', dict(ROUND, main=0xA89878, pattern='bands', pat=(206, 196, 170), period=2.1, eyec=LIME, fin=(120, 108, 88), extras=(on_fish((30, 26, 22), (-1.0, -0.8), (1.6, 1.4)),))),
    ),
    'cavefish': (
        ('Безглазая', dict(SLIM, main=0xC8C8D0, pattern='none', eye=None, fin=(170, 170, 184))),
        ('Прозрачная', dict(SLIM, main=(236, 196, 200), back=(214, 170, 180), pattern='ribs', pat=(170, 90, 110), period=1.9, eye=None, fin=(240, 214, 214))),
        ('Светящаяся', dict(SLIM, main=(58, 60, 84), back=(36, 38, 58), belly=(84, 88, 116), pattern='none', eyec=CYAN, fin=(44, 46, 70), extras=(on_fish(CYAN, (-3.2, 1.3), (-0.6, 1.3), (2.0, 1.1)),))),
    ),
    'obsidifish': (
        ('Обсидиановая', G(main=0x3A2050, body=shard, w=3.9, tail='crescent', tail_end=9.6, eyec=(230, 90, 255), pattern='scales')),
        ('С лавой', G(main=(44, 28, 58), pattern='cracks', pat=(255, 120, 30), body=shard, w=3.9, eyec=(255, 180, 60), tail='crescent', tail_end=9.6, fin=(56, 34, 70))),
        ('Осколок', G(main=(66, 40, 96), back=(30, 18, 44), belly=(120, 70, 170), body=diamond, w=4.6, head=-9.0, end=4.2, tail='fork', tw=3.2, pattern='facets', eyec=(255, 110, 255))),
    ),
    'flarefin': (
        ('Огненная', dict(ROUND, main=0xFF6A20, pattern='gradient', pat=(255, 200, 60), tailc=(255, 200, 50), fin=(255, 170, 40))),
        ('Пламя на плавниках', dict(ROUND, main=0xE85018, pattern='none', fin=(255, 200, 40), tailc=(255, 150, 30), dorsal=(-4.0, 2.4, 2.6), ventral=(-2.4, 2.0, 1.8), tail='fan', tail_end=9.6, tw=4.4, extras=(on_fish(YELLOW, (7.6, -1.6), (7.6, 1.6), (6.4, 0)),))),
        ('Жёлтое брюхо', dict(ROUND, main=(214, 44, 28), back=(130, 22, 22), belly=(255, 210, 60), pattern='none', fin=(255, 130, 30))),
    ),
    'ember_fry': (
        ('Уголёк', dict(PUFF, main=0xFFA040, w=4.6, head=-5.6, end=4.2, pattern='none')),
        ('С трещинами', dict(PUFF, main=(60, 40, 40), back=(36, 24, 28), belly=(80, 50, 46), pattern='cracks', pat=(255, 150, 40), w=4.6, head=-5.6, end=4.2, eyec=YELLOW, tailc=(255, 130, 30))),
        ('Капля огня', G(main=0xFF8A28, pattern='gradient', pat=(255, 230, 90), head=-7.0, end=2.6, peak=0.28, w=4.3, tail='fork', tail_end=9.4, tw=2.6, dorsal=None, ventral=None, tailc=(255, 220, 80), eye=(-4.4, -0.3))),
    ),
    'arcane_eel': (
        ('Волной', dict(EEL, main=0x9A6AE0, pattern='none')),
        ('С рунами', dict(EEL_STRAIGHT, main=(112, 74, 190), pattern='stripes', pat=CYAN, period=3.6, fin=(80, 50, 150))),
        ('Кольчатый', dict(EEL, main=(48, 34, 92), back=(30, 20, 60), belly=(78, 56, 130), pattern='stripes', pat=(200, 120, 255), period=2.8, eyec=(255, 170, 255))),
    ),
    'stargazer': (
        ('Глаз сверху', dict(PUFF, main=0x6A8AFF, w=4.6, head=-6.4, end=4.2, pattern='spots', pat=WHITE, eye=(-2.4, -0.7))),
        ('Ночной', dict(PUFF, main=(36, 40, 96), back=(22, 24, 66), belly=(60, 66, 140), w=4.6, head=-6.4, end=4.2, pattern='none', eyec=YELLOW, eye=(-2.4, -0.7), tailc=(90, 110, 230), extras=(dots(YELLOW, (5, 8), (8, 10), (10, 7), (7, 6)),))),
        ('Звезда', dict(PUFF, main=(120, 150, 255), back=(70, 90, 210), w=3.8, head=-5.4, end=3.4, pattern='none', eye=(-2.4, -0.5), extras=(on_fish(YELLOW, (-6.6, 0), (-1.0, -5.2), (-1.0, 5.2), (3.6, -3.8), (3.6, 3.8)),))),
    ),
    'eden_goldfish': (
        ('Золотая', dict(ROUND, **dict(FAN, main=0xF8E050, fin=(240, 180, 50), tailc=(240, 180, 50)))),
        ('Бело-золотая', dict(ROUND, **dict(FAN, main=(255, 248, 214), back=(248, 214, 90), belly=WHITE, pattern='none', fin=(248, 206, 70), tailc=(248, 214, 90), dorsal=(-4.0, 2.4, 2.4)))),
        ('Лепестки', dict(ROUND, **dict(FAN, main=0xF8E050, pattern='none', fin=(245, 140, 190), tailc=(245, 140, 190), dorsal=(-4.0, 2.4, 2.2), ventral=(-2.4, 2.0, 1.8)))),
    ),
    'wildwood_glowfish': (
        ('С фонариком', G(main=0x40B0E0, w=4.0, dorsal=None, pattern='none', extras=(dots((22, 70, 110), (4, 7), (3, 7), (2, 7), (1, 8)), dots(LIME, (0, 9), (1, 9), (0, 10)),))),
        ('Светлячки', G(main=(24, 60, 110), back=(14, 36, 76), belly=(44, 96, 150), pattern='spots', pat=(120, 240, 255), fin=(30, 90, 150), eyec=(170, 250, 255))),
        ('Хвост-лист', G(main=(50, 170, 200), pattern='scales', tailc=(70, 210, 90), fin=(50, 170, 90), tail='fan', tail_end=9.8, tw=4.4, extras=(on_fish((30, 130, 60), (5.2, 0), (6.4, 0), (7.6, 0)),))),
    ),
    'apalachia_ray': (
        ('Скат', dict(RAY, main=0xC060F0, pattern='none', extras=(on_fish((90, 40, 130), (2.6, 0), (3.8, 0.2), (5.0, 0.4), (6.2, 0.9), (7.4, 1.4)),))),
        ('Пятнистый', dict(RAY, main=(170, 90, 230), pattern='spots', pat=(255, 210, 255), extras=(on_fish((90, 40, 130), (2.6, 0), (3.8, 0.2), (5.0, 0.4), (6.2, 0.9), (7.4, 1.4)),))),
        ('Манта', dict(RAY, main=(120, 60, 180), back=(70, 30, 120), belly=(220, 170, 250), w=7.8, pattern='none', eye=(-5.4, -0.2), extras=(on_fish((70, 30, 120), (-8.8, -2.2), (-8.8, 2.2), (2.6, 0), (3.8, 0.2), (5.0, 0.4), (6.2, 0.9)),))),
    ),
    'skythern_flyer': (
        ('Летучая', dict(SLIM, main=0xE8F0F8, back=(120, 170, 230), pattern='none', fin=(110, 170, 240), extras=(on_fish((90, 150, 230), (-2.4, -3.6), (-1.0, -4.6), (0.4, -5.6), (-1.0, -3.4), (0.4, -4.4), (1.8, -5.4)),))),
        ('Золотистая', dict(SLIM, main=(250, 248, 236), back=(226, 190, 90), pattern='none', fin=(240, 200, 90), tailc=(240, 200, 90), extras=(on_fish((226, 180, 70), (-2.4, -3.6), (-1.0, -4.6), (0.4, -5.6), (-1.0, -3.4), (0.4, -4.4), (1.8, -5.4)),))),
        ('Два крыла', dict(SLIM, main=0xDCE8F4, back=(150, 190, 235), pattern='none', w=2.5, fin=(180, 214, 248), extras=(on_fish((170, 208, 248), (-2.4, -3.2), (-1.0, -4.2), (0.4, -5.2), (-1.0, -3.0), (0.4, -4.0), (-2.4, 3.2), (-1.0, 4.2), (0.4, 5.2), (-1.0, 3.0), (0.4, 4.0)),))),
    ),
    'mortum_eel': (
        ('Волной', dict(EEL, main=0x4A2A2A, belly=(110, 60, 60), pattern='none', eyec=(255, 70, 40))),
        ('С рёбрами', dict(EEL_STRAIGHT, main=(52, 34, 38), pattern='ribs', pat=BONE, period=2.2, eyec=(255, 70, 40), fin=(90, 40, 40))),
        ('Шипастый', dict(EEL_STRAIGHT, main=(92, 26, 26), back=(44, 12, 14), belly=(150, 50, 40), pattern='none', eyec=YELLOW, dorsal=None, extras=(spines(BONE, -5.0, -2.6, -0.2, 2.2, 4.6),))),
    ),
    'dream_carp': (
        ('Мятный кои', dict(ROUND, **dict(FAN, main=0x50E0B0, fin=(70, 190, 170), tailc=(120, 240, 200)))),
        ('Пятнистый кои', dict(ROUND, **dict(FAN, main=(246, 246, 240), back=(232, 232, 226), belly=WHITE, pattern='koi', pat=(240, 90, 60), fin=(250, 220, 210), tailc=(250, 200, 190)))),
        ('Сон', dict(ROUND, **dict(FAN, main=(150, 110, 240), pattern='gradient', pat=(90, 240, 200), fin=(190, 150, 255), tailc=(120, 240, 210), extras=(dots(WHITE, (5, 6), (9, 10), (3, 11)),)))),
    ),
}


# Что выбрал пользователь (06.11.2026): a, b, c - варианты А, Б, В на листе выбора.
CHOSEN = {'sea_bass': 'c', 'coral_wrasse': 'b', 'shark_pup': 'a', 'ruff': 'a', 'gold_crucian': 'a', 'ice_trout': 'b', 'snow_burbot': 'c', 'crystal_fish': 'b',
          'piranha': 'b', 'emerald_catfish': 'a', 'sand_carp': 'c', 'mummy_fish': 'b', 'cavefish': 'c', 'obsidifish': 'b', 'flarefin': 'b', 'ember_fry': 'c',
          'arcane_eel': 'c', 'stargazer': 'b', 'eden_goldfish': 'a', 'wildwood_glowfish': 'b', 'apalachia_ray': 'b', 'skythern_flyer': 'a', 'mortum_eel': 'b', 'dream_carp': 'c'}


def main(out):
    os.makedirs(out, exist_ok=True)
    for name, variants in VARIANTS.items():
        for letter, (title, spec) in zip('abc', variants):
            Fish(**spec).render().save(os.path.join(out, '%s_%s.png' % (name, letter)))
    print('значков', sum(len(v) for v in VARIANTS.values()))


def install():
    items = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'divinerpgextended', 'textures', 'items')
    for name, letter in CHOSEN.items():
        title, spec = VARIANTS[name]['abc'.index(letter)]
        Fish(**spec).render().save(os.path.join(items, 'fish_%s.png' % name))
    print('поставлено', len(CHOSEN))


if __name__ == '__main__':
    if len(sys.argv) > 1:
        main(sys.argv[1])
    else:
        install()
