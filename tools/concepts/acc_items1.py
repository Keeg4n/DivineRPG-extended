"""Аксессуары, часть 1: кольца, амулеты, пояса, вещи с боссов. V(название, значок, посадка, кадр, ракурс); посадка - lambda (броня, значок)."""
from acc_kit import DISC, ELL, RING, RECT, POLY, LINE, PATH, SYM, DIAMOND, HEART, GEAR, cube, sym_x
import acc_wear as W
from acc_pal import *

K = '#1a1320'
ITEMS = []


def V(label, ic, wear, frame='chest', view='front'):
    return (label, ic, wear, frame, view)


def item(key, name, slot, *vs):
    ITEMS.append((key, name, slot, vs))


def skull(c, cx, cy, ramp=BONE, eye=K):
    c.f(RECT(cx - 2, cy - 2, cx + 2, cy + 1), ramp).f(RECT(cx - 1, cy + 2, cx + 1, cy + 2), ramp).d(eye, cx - 1, cy, cx + 1, cy).d(eye, cx, cy + 2)


def cords(c, y=4, col=BRN[2]):
    c.l(3, 0, 7, y, col).l(12, 0, 8, y, col)


def ring_band(c, ramp, cy=10.5, r=4.6, t=1.7):
    c.f(RING(8, cy, r, t), ramp)


# ------------------------------------------------------------------ кольца
item('bone_ring', 'Кольцо костяного стрелка', 'рука',
     V('Черепок на ободке', lambda c: (ring_band(c, BONE), skull(c, 8, 4)), lambda a, i: W.ring(a, BONE[2], BONE[3], big=True), 'hand', 'hand'),
     V('Наконечник стрелы', lambda c: (ring_band(c, BONE, 11, 4.2), c.f(POLY((8, .5), (12, 7), (8, 5.6), (4, 7)), SILV), c.l(8, 2, 8, 5, SILV[4])), lambda a, i: W.ring(a, BONE[2], SILV[2], big=True), 'hand', 'hand'),
     V('Две кости и зелёный глаз', lambda c: (c.f(LINE(3, 12, 12, 3, 2), BONE), c.f(LINE(3, 3, 12, 12, 2), BONE), [c.f(DISC(x, y, 1.6), BONE) for x, y in ((3, 4), (13, 4), (3, 13), (13, 13))], c.f(DISC(8.5, 8.5, 2.2), GRN), c.d(GRN[4], 8, 7)),
       lambda a, i: W.ring(a, BONE[2], GRN[2], 'L', True), 'handL', 'hand'))
item('wither_ring', 'Кольцо иссушения', 'рука',
     V('Чёрный ободок и череп', lambda c: (ring_band(c, BLK), skull(c, 8, 4, BLK, '#c8c0d8')), lambda a, i: W.ring(a, BLK[2], OBS[3], big=True), 'hand', 'hand'),
     V('Три головы', lambda c: (ring_band(c, IRON, 11, 4.2), skull(c, 3, 3, BLK, '#c8c0d8'), skull(c, 8, 4, BLK, '#c8c0d8'), skull(c, 12, 3, BLK, '#c8c0d8')), lambda a, i: W.ring(a, IRON[2], BLK[3], big=True), 'hand', 'hand'),
     V('Обвит шипами', lambda c: (ring_band(c, OBS, 8, 5.5, 2.0), c.d(BLK[4], 2, 3, 13, 3, 1, 8, 14, 8, 3, 13, 12, 13, 8, 1, 8, 14), c.f(DISC(8, 8, 1.6), PUR)), lambda a, i: W.ring(a, OBS[2], PUR[2], 'L'), 'handL', 'hand'))
item('eternal_ice_ring', 'Кольцо вечного льда', 'рука',
     V('Кристалл на серебре', lambda c: (ring_band(c, SILV, 11, 4.2), c.f(POLY((8, 0), (11.5, 3.5), (8, 7.5), (4.5, 3.5)), ICE), c.l(8, 1, 8, 6, ICE[4])), lambda a, i: W.ring(a, SILV[2], ICE[3], big=True), 'hand', 'hand'),
     V('Ободок изо льда в инее', lambda c: (c.f(RING(8, 8, 6, 2.6), ICE), c.d(WHITE[4], 3, 3, 13, 5, 5, 13, 11, 12, 1, 8, 14, 9), c.d(ICE[4], 4, 5, 11, 3)), lambda a, i: W.ring(a, ICE[3], WHITE[3]), 'hand', 'hand'),
     V('Ледяная спираль', lambda c: (c.f(PATH((3, 12), (3, 5), (6, 2), (11, 2), (13, 5), (13, 10), (10, 12), (7, 11), (6, 8), (8, 6), (10, 8), w=2), ICE), c.d(WHITE[4], 3, 7, 7, 2, 13, 7)), lambda a, i: W.ring(a, ICE[2], ICE[4], 'L'), 'handL', 'hand'))

# ------------------------------------------------------------------ амулеты на шее
item('dead_amulet', 'Амулет мертвеца', 'шея',
     V('Усохшая голова', lambda c: (cords(c), c.f(RECT(4, 5, 11, 13), ROT), c.d(K, 6, 8, 9, 8), c.d(K, 5, 12, 7, 12, 9, 12), c.d(ROT[0], 6, 11, 8, 11, 10, 11), c.d(RED[3], 6, 7)), lambda a, i: W.pendant(a, i, BRN[1])),
     V('Гробик на шнурке', lambda c: (cords(c, 2), c.f(POLY((6, 2), (10, 2), (12.5, 6), (10.5, 15.5), (5.5, 15.5), (3.5, 6)), WOOD), c.l(8, 5, 8, 12, GOLD[3]), c.l(6, 7, 10, 7, GOLD[3])), lambda a, i: W.pendant(a, i, '#22303a')),
     V('Зашитое сердце', lambda c: (cords(c, 3), c.f(HEART(8, 8, 6.2), ROT), c.l(8, 4, 8, 13, K), c.d(BONE[3], 7, 6, 9, 6, 7, 9, 9, 9, 7, 12, 9, 12)), lambda a, i: W.pendant(a, i, ROT[1], px=.26)))
item('jack_amulet', 'Тыквенный амулет Джека', 'шея',
     V('Тыква с ухмылкой', lambda c: (cords(c, 3), c.f(ELL(8, 9.5, 6.5, 5.5), PUMP), c.l(8, 3, 8, 4, GRN[2]), c.d('#fff080', 5, 8, 6, 8, 10, 8, 9, 8), c.d('#fff080', 5, 11, 6, 12, 7, 12, 8, 12, 9, 12, 10, 11), c.d(PUMP[0], 8, 6, 8, 7, 4, 9, 12, 9)), lambda a, i: W.pendant(a, i, BRN[1])),
     V('Фонарь Джека на поясе', lambda c: (c.f(RECT(5, 4, 10, 12), PUMP), c.f(RECT(4, 2, 11, 3), IRON), c.f(RECT(4, 13, 11, 14), IRON), c.l(7, 0, 8, 1, IRON[3]), c.d('#fff080', 6, 7, 9, 7, 7, 10, 8, 10)), lambda a, i: W.lantern(a, IRON[2], PUMP[3]), 'waist'),
     V('Маска-полумесяц', lambda c: (c.f(ELL(8, 8, 7, 7), PUMP), c.erase(ELL(12.5, 7, 6, 7)), c.d('#fff080', 3, 7, 4, 7, 3, 11, 4, 11, 5, 11), c.d(PUMP[0], 6, 5, 6, 9), c.l(4, 1, 4, 1, GRN[3])), lambda a, i: W.side_head(a, i, 'R', -4, .34), 'head'))
item('ancient_charm', 'Древний оберег', 'торс',
     V('Шестерня с рубином', lambda c: (c.l(4, 0, 7, 2, BRN[2]), c.l(11, 0, 8, 2, BRN[2]), c.f(GEAR(8, 9, 6.6), GOLD), c.f(DISC(8, 9, 2.4), RED), c.d(RED[4], 7, 8)), lambda a, i: W.pendant(a, i, GOLD[1])),
     V('Песочные часы', lambda c: (cords(c, 1), c.f(RECT(4, 2, 11, 3), GOLD), c.f(RECT(4, 14, 11, 15), GOLD), c.f(POLY((5, 4), (11, 4), (8.5, 9), (11, 14), (5, 14), (7.5, 9)), ICE), c.f(POLY((6, 11), (10, 11), (10.5, 14), (5.5, 14)), SAND), c.d(SAND[3], 8, 9, 8, 10)), lambda a, i: W.pendant(a, i, GOLD[1])),
     V('Монета с глазом', lambda c: (cords(c, 2), c.f(DISC(8, 9, 6.4), SAND), c.f(ELL(8, 9, 4, 2.2), BONE), c.f(DISC(8, 9, 1.7), LAP), c.d(K, 8, 9), c.d(SAND[0], 3, 6, 13, 6, 3, 12, 13, 12)), lambda a, i: W.pendant(a, i, SAND[1])))
item('pharaoh_ankh', 'Анх фараона', 'торс',
     V('Золото и лазурит', lambda c: (c.f(RING(8, 4.5, 3.6, 1.8), GOLD), c.f(RECT(7, 7, 8, 15), GOLD), c.f(RECT(3, 8, 12, 9), GOLD), c.d(LAP[3], 7, 11, 8, 11, 7, 13, 8, 13)), lambda a, i: W.pendant(a, i, LAP[2], px=.3)),
     V('Анх с крыльями', lambda c: (c.f(RING(8, 4, 3.2, 1.6), GOLD), c.f(RECT(7, 6, 8, 15), GOLD), c.f(POLY((7, 8), (0, 6), (1, 9), (3, 11), (7, 11)), LAP), c.f(POLY((9, 8), (16, 6), (15, 9), (13, 11), (9, 11)), LAP), c.d(GOLD[3], 2, 7, 13, 7, 4, 9, 11, 9)), lambda a, i: W.pendant(a, i, GOLD[1], px=.34)),
     V('Скарабей', lambda c: (c.f(ELL(8, 8.5, 5, 6), TEAL), c.l(8, 3, 8, 14, TEAL[0]), c.f(RECT(6, 1, 9, 3), GOLD), c.d(K, 6, 2, 9, 2), c.l(3, 6, 1, 4, GOLD[2]), c.l(12, 6, 14, 4, GOLD[2]), c.l(3, 11, 1, 13, GOLD[2]), c.l(12, 11, 14, 13, GOLD[2]), c.d(GOLD[3], 6, 7, 9, 7, 6, 10, 9, 10)), lambda a, i: W.pin(a, i, 0, 3, .32)))

# ------------------------------------------------------------------ пояса
TNT = lambda a: sum([cube('body', x, 7.6, W.waist(a, 9)[2] - .6, .9, 3.8, .6, RED[2]) + cube('body', x, 7.6, W.waist(a, 9)[2] - .62, .9, .7, .62, BONE[3]) for x in (-3.5, -2.2, 1.3, 2.6)], [])
item('blast_belt', 'Пояс укротителя криперов', 'пояс',
     V('Шашки на ремне', lambda c: (c.f(RECT(0, 7, 15, 10), BRN), [c.f(RECT(x, 3, x + 1, 12), RED) for x in (1, 4, 10, 13)], [c.c(RECT(x, 3, x + 1, 3), BONE[3]) for x in (1, 4, 10, 13)], c.f(RECT(6, 6, 9, 11), IRON), c.d(IRON[4], 7, 7)), lambda a, i: W.belt(a, BRN[2], BRN[1], IRON[3]) + TNT(a), 'waist'),
     V('Пряжка-крипер', lambda c: (c.f(RECT(0, 6, 15, 9), BRN), c.f(RECT(4, 3, 11, 12), GRN), c.c(RECT(5, 5, 6, 6), K), c.c(RECT(9, 5, 10, 6), K), c.c(RECT(7, 7, 8, 8), K), c.c(RECT(6, 8, 9, 10), K), c.erase({(7, 10), (8, 10)}), c.d(GRN[1], 7, 10, 8, 10)), lambda a, i: W.belt(a, BRN[2], BRN[1], GRN[2], buckle_w=.1) + W.buckle_icon(a, i, 10.3, .3), 'waist'),
     V('Патронташ с порохом', lambda c: (c.f(RECT(0, 5, 15, 11), IRON), [c.f(RECT(x, 6, x + 1, 10), BLK) for x in (1, 4, 7, 10, 13)], [c.d(SILV[3], x, 6) for x in (1, 4, 7, 10, 13)], c.l(0, 5, 15, 5, BRN[3])), lambda a, i: W.belt(a, BRN[2], BRN[1], BRN[3], h=2.6, y=8.8, buckle_w=1.6) + sum([cube('body', x, 8.4, W.waist(a, 9)[2] - .7, 1.0, 3.2, .7, IRON[2]) + cube('body', x, 8.4, W.waist(a, 9)[2] - .72, 1.0, .8, .72, SILV[3]) for x in (-4.0, -2.6, 1.6, 3.0)], []), 'waist'))
item('steadfast_belt', 'Несокрушимый пояс', 'пояс и плечи',
     V('Ледяная бляха', lambda c: (c.f(RECT(0, 6, 15, 10), IRON), c.f(POLY((8, 2), (12.5, 8), (8, 14), (3.5, 8)), ICE), c.f(DIAMOND(8, 8, 2.5), LAP), c.d(WHITE[4], 7, 5)), lambda a, i: W.belt(a, IRON[2], ICE[2], ICE[3], buckle_w=3.5) + W.pauldron(a, ICE[2], ICE[4]), 'chest'),
     V('Ремень через плечо', lambda c: (c.f(RECT(0, 10, 15, 13), BRN), c.f(LINE(2, 10, 13, 1, 2), BRN), c.f(RECT(11, 0, 15, 5), ICE), c.f(RECT(6, 9, 9, 14), SILV), c.d(ICE[4], 12, 1)), lambda a, i: W.belt(a, BRN[2], BRN[1], SILV[3]) + W.strap(a, BRN[2]) + W.pauldron(a, ICE[2], SILV[3]), 'chest'),
     V('Шипы инея', lambda c: (c.f(RECT(0, 7, 15, 11), SILV), [c.f(POLY((x, 7), (x + 3, 7), (x + 1.5, 1)), ICE) for x in (0, 4, 8, 12)], [c.d(K, x, 9) for x in (2, 6, 10, 14)]), lambda a, i: W.belt(a, SILV[2], ICE[3], ICE[2], buckle_w=5) + W.pauldron(a, SILV[2], ICE[3], spikes=ICE[3]), 'chest'))

# ------------------------------------------------------------------ с боссов
item('carcinus_shell', 'Панцирь Карцинуса', 'торс, спина',
     V('Панцирь со спины краба', lambda c: (c.f(ELL(8, 7.5, 7.5, 5.5), ORG), c.d(ORG[4], 4, 4, 5, 3), c.l(5, 7, 11, 7, ORG[0]), c.d(ORG[0], 8, 4, 8, 10), [c.l(x, 12, x + d, 15, ORG[1]) for x, d in ((3, -2), (6, -1), (9, 1), (12, 2))]), lambda a, i: W.plate_back(a, i), 'up', 'back'),
     V('Клешня-наруч', lambda c: (c.f(POLY((1, 15), (5, 15), (8, 11), (5, 8), (1, 11)), ORG), c.f(POLY((7, 10), (12, 10), (15, 7), (15, 10), (12, 13), (7, 13)), COP), c.f(POLY((4, 9), (3, 4), (7, 1), (13, 1), (15, 4), (11, 3), (8, 4), (8, 8)), ORG), c.d(ORG[4], 5, 3, 8, 2)), lambda a, i: W.spur(a, i, .4), 'hand', 'hand'),
     V('Панцирь с шипами', lambda c: ([c.f(POLY((x, 5), (x + 3, 5), (x + 1.5, 0)), COP) for x in (2, 6.5, 11)], c.f(ELL(8, 9, 7.5, 5.5), RED), c.l(8, 5, 8, 13, RED[0]), c.d(RED[4], 4, 7, 5, 6)), lambda a, i: W.plate_back(a, i, .68) + W.pauldron(a, RED[2], COP[3]), 'up', 'back'))
item('ice_brooch', 'Ледяная брошь', 'грудь',
     V('Снежинка', lambda c: (c.f(LINE(8, 1, 8, 14), ICE), c.f(LINE(1, 8, 14, 8), ICE), c.f(LINE(3, 3, 12, 12), SKY), c.f(LINE(3, 12, 12, 3), SKY), c.f(DIAMOND(8, 8, 2.6), WHITE), c.d(WHITE[4], 6, 2, 9, 2, 6, 13, 9, 13, 2, 6, 2, 9, 13, 6, 13, 9)), lambda a, i: W.pin(a, i)),
     V('Ромб в серебре', lambda c: (c.f(DIAMOND(8, 8, 7.4), SILV), c.f(DIAMOND(8, 8, 4.6), ICE), c.d(WHITE[4], 6, 7, 7, 6), c.d(SILV[4], 8, 1, 1, 8, 14, 8, 8, 14)), lambda a, i: W.pin(a, i)),
     V('Ледяная капля', lambda c: (c.f(RECT(5, 1, 10, 3), GOLD), c.f(POLY((8, 3), (12, 9), (11, 13), (8, 15), (5, 13), (4, 9)), ICE), c.d(WHITE[4], 6, 8, 6, 9, 7, 7)), lambda a, i: W.pin(a, i, 0, 1.6, .24)))
item('colossus_pauldron', 'Наплечник Колосса', 'плечо',
     V('Ледяной купол', lambda c: (c.f(ELL(8, 10, 7.5, 7), ICE), c.f(RECT(0, 11, 15, 13), SILV), c.d(WHITE[4], 4, 5, 5, 4, 6, 4)), lambda a, i: W.pauldron(a, ICE[2], SILV[3], big=True)),
     V('Шипы Колосса', lambda c: ([c.f(POLY((x, 8), (x + 3.6, 8), (x + 1.8, 1)), ICE) for x in (1, 6, 11)], c.f(RECT(1, 8, 14, 14), SKY), c.l(1, 11, 14, 11, ICE[0])), lambda a, i: W.pauldron(a, SKY[2], ICE[0], big=True, spikes=ICE[3])),
     V('Плита с руной', lambda c: (c.f(RECT(2, 2, 13, 13), SILV), c.c(RECT(3, 3, 12, 12), SILV[2]), c.f(PATH((5, 5), (8, 11), (11, 5)), CYAN), c.l(8, 4, 8, 7, CYAN[4]), c.d(SILV[4], 2, 2, 13, 2)), lambda a, i: W.pauldron(a, SILV[2], SILV[1], big=True) + W.side_plate(a, i)))
item('lich_crown', 'Корона лича', 'голова',
     V('Чёрная с ледяными зубьями', lambda c: (c.f(RECT(1, 9, 14, 13), OBS), [c.f(POLY((x, 9), (x + 3, 9), (x + 1.5, 3)), ICE) for x in (1, 6.5, 12)], c.f(DIAMOND(8, 11, 1.8), CYAN), c.d(CYAN[3], 3, 11, 12, 11)), lambda a, i: W.crown(a, OBS[2], ICE[3], CYAN[3], n=3), 'head'),
     V('Костяной венец', lambda c: (c.f(RECT(1, 10, 14, 13), BONE), [c.f(POLY((x, 10), (x + 2, 10), (x + 1, 5)), BONE) for x in (0.5, 4, 10, 13.5)], skull(c, 8, 6), c.d(K, 3, 12, 6, 12, 9, 12, 12, 12)), lambda a, i: W.crown(a, BONE[2], BONE[3], BONE[4], spikes=1.8) + W.on_head(a, i, 0, W.head_top(a) + .3, .22), 'head'),
     V('Ледяная тиара', lambda c: (c.f(PATH((1, 11), (4, 8), (8, 7), (12, 8), (15, 11), w=2), SILV), c.f(POLY((8, 0), (11, 4), (8, 9), (5, 4)), ICE), c.d(WHITE[4], 7, 2, 7, 3), c.d(ICE[3], 4, 7, 12, 7)), lambda a, i: W.circlet(a, SILV[2], i, -6.4, .3), 'head'))
item('watcher_eye', 'Бдящее око', 'голова',
     V('Око на лбу', lambda c: (c.f(ELL(8, 8, 7.5, 4.6), BONE), c.f(DISC(8, 8, 3.2), RED), c.f(DISC(8, 8, 1.4), K), c.d(WHITE[4], 6, 6), c.l(2, 4, 4, 2, RED[1]), c.l(13, 4, 11, 2, RED[1]), c.l(8, 2, 8, 0, RED[1])), lambda a, i: W.on_head(a, i, 0, -6.4, .3), 'head'),
     V('Монокль с алой линзой', lambda c: (c.f(RING(6.5, 6.5, 5.6, 1.6), SILV), c.c(DISC(6.5, 6.5, 4), RED[2]), c.c(DISC(6.5, 6.5, 2), RED[1]), c.d(RED[4], 4, 4), c.f(PATH((11, 11), (13, 13), (11, 15)), GOLD)), lambda a, i: W.on_head(a, i, -2.2, -4.6, .26), 'head'),
     V('Парящий глаз', lambda c: (c.f(DISC(8, 6.5, 5.6), RED), c.f(DISC(8, 6, 3.2), BONE), c.f(DISC(8, 6, 1.6), K), [c.l(x, 12, x + d, 15, RED[1]) for x, d in ((4, -2), (8, 0), (12, 2))], c.d(WHITE[4], 5, 3)), lambda a, i: W.float_at(a, i, (-8.5, -4, -2), .36), 'chest'))
item('blue_flame', 'Синее пламя', 'сапоги',
     V('Огоньки на сапогах', lambda c: (c.f(POLY((8, 0), (12, 5), (13, 10), (10, 15), (6, 15), (3, 10), (4, 5)), BLUEF), c.f(POLY((8, 6), (10.5, 10), (9, 14), (7, 14), (5.5, 10)), SKY), c.d(WHITE[4], 8, 11, 8, 12)), lambda a, i: W.flame_feet(a, i), 'feet'),
     V('Свеча у пояса', lambda c: (c.f(RECT(6, 9, 9, 13), BONE), c.f(RECT(4, 13, 11, 15), GOLD), c.f(POLY((7.5, 1), (10.5, 5), (9.5, 8.5), (6, 8.5), (5, 5)), BLUEF), c.d(WHITE[4], 7, 6, 8, 6)), lambda a, i: W.hip(a, i, 'R', .32), 'waist'),
     V('Пламя кольцом над плечом', lambda c: (c.f(RING(8, 9, 6, 2.8), BLUEF), [c.d(BLUEF[3], x, y) for x, y in ((3, 2), (8, 1), (13, 2), (1, 6), (14, 6))], c.f(DISC(8, 9, 1.4), WHITE)), lambda a, i: W.float_at(a, i, (-8.5, -4, -2), .36), 'chest'))
item('dramix_seal', 'Печать Драмикса', 'торс',
     V('Руна на диске', lambda c: (c.f(DISC(8, 8, 7.4), PUR), c.c(RING(8, 8, 5.6, 1), PUR[1]), c.f(PATH((8, 3), (11, 11), (4, 6), (12, 6), (5, 11), (8, 3)), MAG), c.d(WHITE[4], 8, 8)), lambda a, i: W.pin(a, i, 0, 3.4, .32)),
     V('Сургуч с лентами', lambda c: (c.f(POLY((6, 10), (2, 15), (5, 15), (8, 12)), MAG), c.f(POLY((10, 10), (14, 15), (11, 15), (8, 12)), MAG), c.f(GEAR(8, 6.5, 6, 9, 1.4), RED), c.f(DISC(8, 6.5, 2.6), RED[1:]), c.d(GOLD[3], 8, 6)), lambda a, i: W.hip(a, i, 'R', .34), 'waist'),
     V('Перстень-печатка', lambda c: (ring_band(c, GOLD, 11, 4.2), c.f(RECT(4, 1, 11, 7), PUR), c.c(RECT(5, 2, 10, 6), PUR[1]), c.f(PATH((6, 3), (9, 5)), MAG), c.f(PATH((9, 3), (6, 5)), MAG)), lambda a, i: W.ring(a, GOLD[2], PUR[2], big=True), 'hand', 'hand'))
item('parasecta_sting', 'Жало Парасекты', 'торс',
     V('Жало на предплечье', lambda c: (c.f(POLY((1, 15), (3, 15), (8, 9), (13, 3), (15, 0), (11, 2), (6, 7), (2, 12)), LIME), c.l(3, 13, 12, 3, LIME[4]), c.f(RECT(0, 12, 3, 15), BRN)), lambda a, i: W.spur(a, i, .42), 'hand', 'hand'),
     V('Склянка с ядом', lambda c: (c.f(RECT(6, 1, 9, 3), IRON), c.f(POLY((6, 4), (10, 4), (13, 9), (12, 14), (4, 14), (3, 9)), LIME), c.d(WHITE[4], 5, 8, 5, 9, 6, 6), c.d(LIME[0], 9, 12, 7, 10, 10, 9)), lambda a, i: W.hip(a, i, 'R', .32), 'waist'),
     V('Жало на цепочке', lambda c: (c.l(3, 0, 7, 3, SILV[2]), c.l(12, 0, 8, 3, SILV[2]), c.f(POLY((6, 4), (10, 4), (11, 8), (8, 15.5), (5, 8)), LIME), c.l(7, 5, 8, 12, LIME[4]), c.f(RECT(6, 3, 9, 4), SILV)), lambda a, i: W.pendant(a, i, SILV[1])))
