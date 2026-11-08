"""Аксессуары, часть 2: от торговцев, из сундуков, объединённые, крылья, колчаны, знаки охотника."""
from acc_kit import DISC, ELL, RING, RECT, POLY, LINE, PATH, SYM, DIAMOND, HEART, GEAR, cube, sym_x, icon, tint
import acc_wear as W
from acc_pal import *
from acc_items1 import ITEMS, V, item, K, skull, cords, ring_band

# ------------------------------------------------------------------ от торговцев
item('tackle_box', 'Ящик для снастей', 'бедро',
     V('Зелёный ящик', lambda c: (c.f(RECT(1, 5, 14, 14), GRN), c.l(1, 8, 14, 8, GRN[0]), c.f(RECT(6, 7, 9, 10), GOLD), c.f(PATH((5, 5), (5, 2), (10, 2), (10, 5)), IRON)), lambda a, i: W.box_at_hip(a, GRN[2], GRN[0]), 'waist'),
     V('Плетёная корзинка', lambda c: (c.f(POLY((2, 6), (14, 6), (12.5, 15), (3.5, 15)), WOOD), [c.l(x, 6, x, 14, WOOD[1]) for x in (4, 7, 10, 12)], [c.l(3, y, 13, y, WOOD[3]) for y in (8, 11, 13)], c.f(PATH((3, 6), (5, 1), (11, 1), (13, 6)), BRN)), lambda a, i: W.box_at_hip(a, WOOD[2], BRN[2], h=3.0), 'waist'),
     V('Стальной с поплавком', lambda c: (c.f(RECT(1, 7, 14, 14), IRON), c.l(1, 10, 14, 10, IRON[0]), c.f(DISC(11, 4.5, 3.4), WHITE), c.c(RECT(8, 1, 14, 4), RED[2]), c.erase({(8, 1), (14, 1)}), c.d(IRON[4], 3, 8, 4, 8)), lambda a, i: W.box_at_hip(a, IRON[2], RED[2]), 'waist'))
item('master_scabbard', 'Ножны мастера', 'бедро',
     V('Кожа и золото', lambda c: (c.f(LINE(2, 13, 11, 4, 3), BRN), c.f(LINE(11, 4, 13, 2, 3), GOLD), c.f(LINE(1, 14, 3, 12, 2), GOLD), c.d(GOLD[3], 6, 9, 7, 8)), lambda a, i: W.scabbard(a, BRN[2], GOLD[2], BRN[1]), 'waist', 'side'),
     V('Серебро и крылышко', lambda c: (c.f(LINE(2, 13, 11, 4, 3), SILV), c.f(POLY((10, 3), (15, 0), (15, 5), (12, 7)), WHITE), c.f(LINE(1, 14, 3, 12, 2), LAP), c.d(LAP[3], 5, 10, 8, 7)), lambda a, i: W.scabbard(a, SILV[2], LAP[3], SILV[3]), 'waist', 'side'),
     V('Драконья чешуя', lambda c: (c.f(LINE(2, 13, 11, 4, 3), RED), [c.d(RED[0], x, y) for x, y in ((3, 12), (5, 10), (7, 8), (9, 6), (4, 10), (6, 8), (8, 6))], c.f(DIAMOND(12, 3.5, 3), OBS), c.d(MAG[3], 12, 3), c.f(LINE(1, 14, 2, 13, 2), OBS)), lambda a, i: W.scabbard(a, RED[1], OBS[3], BLK[3]), 'waist', 'side'))
item('valor_medal', 'Медаль за отвагу', 'грудь',
     V('Звезда на ленте', lambda c: (c.f(POLY((5, 0), (11, 0), (10, 6), (6, 6)), RED), c.c(RECT(7, 0, 8, 5), LAP[2]), c.f(POLY((8, 5), (9.6, 8.6), (13.5, 8.8), (10.4, 11.2), (11.5, 15), (8, 12.8), (4.5, 15), (5.6, 11.2), (2.5, 8.8), (6.4, 8.6)), GOLD)), lambda a, i: W.pin(a, i, -1.9, 3.2, .28)),
     V('Щит и мечи', lambda c: (c.f(LINE(1, 2, 14, 14), SILV), c.f(LINE(14, 2, 1, 14), SILV), c.f(POLY((3, 3), (12, 3), (12, 10), (8, 15), (3, 10)), LAP), c.f(POLY((7, 5), (9, 5), (9, 8), (11, 8), (11, 10), (9, 10), (9, 12), (7, 12), (7, 10), (4, 10), (4, 8), (7, 8)), GOLD)), lambda a, i: W.pin(a, i, -1.9, 3.2, .28)),
     V('Лавровый венок', lambda c: (c.f(RING(8, 8, 7, 2), GRN), [c.d(GRN[4], x, y) for x, y in ((2, 5), (4, 2), (11, 2), (13, 5), (2, 10), (4, 13), (11, 13), (13, 10))], c.f(DISC(8, 8, 3), RED), c.d(RED[4], 7, 7), c.erase(RECT(7, 14, 8, 15))), lambda a, i: W.pin(a, i, -1.9, 3.2, .3)))
item('pocket_magnet', 'Карманный магнит', 'бедро',
     V('Подкова', lambda c: (c.f(RING(8, 7, 7, 3.2) - RECT(0, 8, 15, 15), RED), c.f(RECT(1, 7, 4, 11), RED), c.f(RECT(11, 7, 14, 11), LAP), c.f(RECT(8, 0, 14, 6) & RING(8, 7, 7, 3.2), LAP), c.c(RECT(1, 12, 4, 13), SILV[3]), c.c(RECT(11, 12, 14, 13), SILV[3])), lambda a, i: W.hip(a, i, 'R', .3), 'waist'),
     V('Брусок с искрами', lambda c: (c.f(RECT(1, 5, 7, 11), RED), c.f(RECT(8, 5, 14, 11), LAP), c.d(WHITE[4], 3, 1, 8, 2, 12, 1, 3, 14, 8, 13, 12, 14), c.d(SILV[4], 2, 6, 9, 6)), lambda a, i: W.float_at(a, i, (-8.5, -4, -2), .36), 'chest'),
     V('Магнитный компас', lambda c: (c.f(DISC(8, 8, 7.4), COP), c.f(DISC(8, 8, 5.2), BONE), c.l(8, 3, 8, 7, RED[2]), c.l(8, 8, 8, 13, LAP[2]), c.d(K, 8, 8), c.f(RECT(7, 0, 8, 1), GOLD)), lambda a, i: W.hip(a, i, 'R', .28), 'waist'))
item('warm_scarf', 'Тёплый шарф', 'шея',
     V('Красно-белые полосы', lambda c: (c.f(PATH((2, 3), (13, 3), (13, 13), w=3), RED), [c.c(RECT(x, 3, x + 1, 5), WHITE[3]) for x in (4, 8)], [c.c(RECT(13, y, 15, y + 1), WHITE[3]) for y in (7, 11)]), lambda a, i: W.scarf(a, RED[2], WHITE[3])),
     V('Толстый с бахромой', lambda c: (c.f(PATH((1, 4), (14, 4), (12, 12), w=4), GRN), [c.c(RECT(x, 4, x, 7), GOLD[3]) for x in (3, 6, 9)], c.c(LINE(11, 14, 11, 15), GOLD[3]), c.c(LINE(13, 14, 13, 15), GOLD[3])), lambda a, i: W.scarf(a, GRN[2], GOLD[3], fat=True)),
     V('Меховой воротник', lambda c: (c.f(ELL(8, 8, 7.5, 5.5), BONE), [c.d(BONE[0], x, y) for x, y in ((2, 7), (5, 4), (9, 3), (13, 6), (12, 11), (4, 11), (8, 12))], c.f(ELL(8, 8, 3.5, 2), BRN)), lambda a, i: W.scarf(a, BONE[2], BONE[3], tail=False, fat=True, fur=BONE[3])))

# ------------------------------------------------------------------ из сундуков и с мобов
item('deep_shell', 'Ракушка глубин', 'голова',
     V('Трубач у виска', lambda c: (c.f(POLY((1, 14), (4, 5), (10, 1), (15, 4), (14, 10), (8, 14)), PINK), c.c(PATH((11, 3), (12, 7), (9, 10), (6, 9), (7, 6), (9, 6)), PINK[0]), c.d(PINK[4], 6, 4, 9, 2), c.d(WHITE[4], 2, 13, 3, 12)), lambda a, i: W.side_head(a, i, 'R', -4, .32), 'head'),
     V('Гребешок с жемчужиной', lambda c: (c.f(POLY((8, 15), (1, 7), (2, 3), (5, 1), (11, 1), (14, 3), (15, 7)), TEAL), [c.l(8, 14, x, 2, TEAL[0]) for x in (3, 6, 10, 13)], c.f(DISC(8, 11.5, 1.9), WHITE)), lambda a, i: W.on_head(a, i, 0, -6.4, .3), 'head'),
     V('Дыхательная раковина', lambda c: (c.f(ELL(8, 10, 6, 5), CYAN), c.f(ELL(8, 10, 3, 2.4), TEAL[0:2]), c.d(WHITE[4], 4, 7), [c.f(RING(x, y, r, 1), SKY) for x, y, r in ((3, 3, 2), (12, 4, 1.6))], c.d(SKY[4], 8, 1)), lambda a, i: W.on_head(a, i, 0, -1.6, .3), 'head'))
item('dungeon_lamp', 'Лампа подземелий', 'голова',
     V('Налобный фонарь', lambda c: (c.f(RECT(5, 4, 10, 11), PUR), c.c(RECT(6, 5, 9, 10), PUR[3]), c.c(RECT(7, 7, 8, 9), WHITE[4]), c.f(RECT(4, 2, 11, 3), IRON), c.f(RECT(4, 12, 11, 13), IRON), c.c(RECT(0, 7, 3, 8), BRN[2]), c.c(RECT(12, 7, 15, 8), BRN[2])), lambda a, i: W.circlet(a, BRN[2], i, -6.2, .3), 'head'),
     V('Фонарь на поясе', lambda c: (c.f(RECT(5, 5, 10, 13), MAG), c.c(RECT(7, 7, 8, 11), WHITE[4]), c.f(RECT(4, 3, 11, 4), GOLD), c.f(RECT(4, 13, 11, 15), GOLD), c.f(PATH((6, 3), (8, 0), (10, 3)), GOLD)), lambda a, i: W.lantern(a, GOLD[2], MAG[3]), 'waist'),
     V('Шар на цепочке', lambda c: (c.l(8, 0, 8, 3, SILV[2]), c.f(DISC(8, 9, 6), PUR), c.f(DISC(8, 9, 3), MAG), c.d(WHITE[4], 5, 6, 8, 9), c.f(RECT(6, 3, 9, 4), SILV)), lambda a, i: W.float_at(a, i, (-8.5, -4, -2), .36), 'chest'))

# ------------------------------------------------------------------ объединённые
item('heart_of_winter', 'Сердце зимы', 'шея и грудь',
     V('Сердце-льдина', lambda c: (c.f(HEART(8, 7.5, 7), ICE), c.l(5, 3, 7, 9, WHITE[3]), c.l(7, 9, 11, 7, WHITE[3]), c.d(WHITE[4], 4, 4, 5, 3)), lambda a, i: W.scarf(a, ICE[2], WHITE[3]) + W.pin(a, i, 1.9, 4, .28)),
     V('Снежинка в сердце', lambda c: (c.f(HEART(8, 7.5, 7), LAP), c.l(8, 3, 8, 12, WHITE[4]), c.l(4, 7, 12, 7, WHITE[4]), c.l(5, 4, 11, 10, ICE[3]), c.l(11, 4, 5, 10, ICE[3])), lambda a, i: W.pendant(a, i, ICE[2], px=.32) + W.scarf(a, LAP[2], WHITE[3], tail=False)),
     V('Живое сердце во льду', lambda c: (c.f(DIAMOND(8, 8, 7.8), ICE), c.f(HEART(8, 7.5, 3.8), RED), c.d(RED[4], 6, 6), c.d(WHITE[4], 4, 7, 8, 2)), lambda a, i: W.pin(a, i, 0, 3.5, .34) + W.scarf(a, RED[1], ICE[3], tail=False)))
item('tide_armor', 'Доспех прилива', 'голова и спина',
     V('Панцирь и волна', lambda c: (c.f(ELL(8, 8, 7.5, 6), LAP), c.f(PATH((1, 8), (4, 6), (7, 8), (10, 6), (14, 8), w=2), CYAN), c.d(CYAN[4], 4, 3, 5, 3, 9, 3), c.l(4, 12, 11, 12, LAP[0])), lambda a, i: W.plate_back(a, i), 'up', 'back'),
     V('Жемчужные створки', lambda c: (c.f(ELL(8, 8, 7.5, 7), TEAL), [c.l(8, 8, x, y, TEAL[0]) for x, y in ((2, 3), (8, 1), (14, 3), (1, 9), (14, 9))], c.f(DISC(8, 9, 2.8), WHITE), c.d(TEAL[4], 4, 4)), lambda a, i: W.plate_back(a, i, .6) + W.side_head(a, i, 'R', -4, .22), 'up', 'back'),
     V('Кораллы и шипы', lambda c: (c.f(ELL(8, 10, 7, 5), ORG), c.f(PATH((3, 7), (3, 2)), PINK), c.f(PATH((8, 6), (8, 1), (6, 3)), PINK), c.f(PATH((13, 7), (13, 2), (11, 4)), PINK), c.d(PINK[4], 3, 1, 8, 0, 13, 1), c.l(4, 11, 12, 11, ORG[0])), lambda a, i: W.plate_back(a, i, .62, 3.5) + W.pauldron(a, ORG[2], PINK[3]), 'up', 'back'))

# ------------------------------------------------------------------ движение
RB = [RED[2], ORG[3], GOLD[3], GRN[3], CYAN[3], PUR[3]]
item('ayeraco_wing', 'Крыло Айрако', 'сапоги',
     V('Радужное крылышко', lambda c: ([c.f(POLY((0, 8 + k), (15 - k * 2, 1 + k * 2), (13 - k * 2, 4 + k * 2), (0, 11 + k)), [RB[k]]) for k in range(5, -1, -1)]), lambda a, i: W.boot_wing(a, i), 'feet'),
     V('Перо пяти цветов', lambda c: (c.f(POLY((2, 14), (7, 6), (13, 1), (14, 3), (10, 9), (5, 14)), [PUR[3]]), c.f(POLY((5, 10), (9, 6), (11, 7), (6, 12)), [GRN[3]]), c.f(POLY((8, 6), (12, 2), (13, 4), (10, 7)), [RED[3]]), c.l(0, 15, 12, 3, WHITE[4])), lambda a, i: W.boot_wing(a, i), 'feet'),
     V('Два маленьких крыла', lambda c: (c.f(POLY((8, 7), (15, 1), (15, 8), (8, 12)), RB[:1] + RB[2:3]), c.f(POLY((8, 7), (1, 1), (1, 8), (8, 12)), RB[:1] + RB[2:3]), c.f(POLY((8, 8), (14, 5), (13, 9), (8, 12)), [CYAN[3]]), c.f(POLY((8, 8), (2, 5), (3, 9), (8, 12)), [CYAN[3]])), lambda a, i: W.wings(a, W.halfwing(i), .9, 2.5, 22), 'up', 'back'))


def carpet_ic(c, ramp, trim, mid, tassel):
    c.f(RECT(1, 3, 14, 12), ramp)
    c.c(RECT(1, 3, 14, 3) | RECT(1, 12, 14, 12), trim[3])
    c.c(RECT(3, 5, 12, 10) - RECT(4, 6, 11, 9), trim[2])
    c.f(DIAMOND(8, 8, 2.6), mid)
    for x in (0, 15):
        for y in (4, 6, 8, 10, 11):
            c.d(tassel, x, y)


item('magic_carpet', 'Ковёр-самолёт', 'сапоги',
     V('Алый с золотом', lambda c: carpet_ic(c, RED, GOLD, LAP, GOLD[3]), lambda a, i: W.carpet(a, i), 'feet', 'high'),
     V('Синий восточный', lambda c: (carpet_ic(c, LAP, SILV, GOLD, SILV[3]), c.d(GOLD[3], 3, 8, 12, 8)), lambda a, i: W.carpet(a, i), 'feet', 'high'),
     V('Облачный лоскут', lambda c: (c.f(ELL(4, 8, 4, 3.5) | ELL(8, 7, 5, 5) | ELL(12, 8, 4, 3.5), WHITE), c.f(ELL(8, 9, 3, 1.5), SKY), c.d(GOLD[3], 2, 8, 13, 8, 8, 3)), lambda a, i: W.carpet(a, i), 'feet', 'high'))


def butterfly(c, r, acc):
    c.f(POLY((8, 7), (14, 1), (15, 6), (11, 9)), r).f(POLY((7, 7), (1, 1), (0, 6), (4, 9)), r)
    c.f(POLY((8, 9), (13, 10), (12, 14), (9, 13)), r).f(POLY((7, 9), (2, 10), (3, 14), (6, 13)), r)
    c.d(acc, 12, 4, 3, 4, 11, 12, 4, 12).c(RECT(7, 6, 8, 12), r[0])


def feathers(c, r, acc):
    for k in range(4):
        y = 2 + k * 2
        c.f(POLY((8, y + 2), (15 - k, y - 1 + k // 2), (14 - k, y + 2), (8, y + 4)), r)
        c.f(POLY((7, y + 2), (k, y - 1 + k // 2), (1 + k, y + 2), (7, y + 4)), r)
    c.c(RECT(7, 3, 8, 11), acc)


def membrane(c, r, acc):
    c.f(POLY((8, 4), (15, 1), (15, 5), (14, 9), (12, 7), (11, 11), (9, 8), (8, 12)), r)
    c.f(POLY((7, 4), (0, 1), (0, 5), (1, 9), (3, 7), (4, 11), (6, 8), (7, 12)), r)
    for pts in (((8, 4), (14, 2)), ((8, 5), (12, 7)), ((8, 6), (10, 8)), ((7, 4), (1, 2)), ((7, 5), (3, 7)), ((7, 6), (5, 8))):
        c.l(*pts[0], *pts[1], r[0])
    c.c(RECT(7, 3, 8, 11), acc)


for key, name, ramp, acc in (('dream_wings_1', 'Крылья сновидца', SKY, GOLD[3]), ('dream_wings_2', 'Крылья полуночи', PUR, CYAN[3]), ('dream_wings_3', 'Крылья кошмара', RED, LIME[3])):
    item(key, name, 'спина',
         V('Бабочка', lambda c, r=ramp, a_=acc: butterfly(c, r, a_), lambda a, i: W.wings(a, W.halfwing(i), 1.05, 3, 26), 'up', 'back'),
         V('Перья', lambda c, r=ramp, a_=acc: feathers(c, r, a_), lambda a, i: W.wings(a, W.halfwing(i), 1.05, 3, 18), 'up', 'back'),
         V('Перепонки', lambda c, r=ramp, a_=acc: membrane(c, r, a_), lambda a, i: W.wings(a, W.halfwing(i), 1.05, 3, 34), 'up', 'back'))

# ------------------------------------------------------------------ колчаны
item('endless_quiver', 'Бездонный колчан', 'спина',
     V('Кожаный', lambda c: (c.f(LINE(2, 13, 10, 5, 4), BRN), c.f(LINE(9, 5, 10, 4, 4), GOLD), [c.l(x, y, x + 3, y - 3, WOOD[3]) for x, y in ((10, 3), (12, 5))], c.d(WHITE[4], 13, 0, 14, 1, 15, 2, 15, 3)), lambda a, i: W.quiver(a, BRN[2], GOLD[2], WHITE[3]), 'up', 'back'),
     V('Светящиеся стрелы', lambda c: (c.f(LINE(2, 13, 10, 5, 4), LAP), c.f(LINE(9, 5, 10, 4, 4), SILV), [c.l(x, y, x + 3, y - 3, CYAN[3]) for x, y in ((10, 3), (12, 5))], c.d(WHITE[4], 13, 0, 14, 1, 15, 2, 15, 3), c.d(CYAN[3], 5, 11, 7, 9)), lambda a, i: W.quiver(a, LAP[2], SILV[3], CYAN[3]), 'up', 'back'),
     V('Чёрный с алым оперением', lambda c: (c.f(LINE(2, 13, 10, 5, 4), OBS), c.f(LINE(9, 5, 10, 4, 4), RED), [c.l(x, y, x + 3, y - 3, BLK[4]) for x, y in ((10, 3), (12, 5))], c.d(RED[3], 13, 0, 14, 1, 15, 2, 15, 3)), lambda a, i: W.quiver(a, OBS[2], RED[2], RED[3]), 'up', 'back'))
item('dust_pouch', 'Подсумок пыли', 'бедро',
     V('Кисет', lambda c: (c.f(POLY((6, 4), (10, 4), (14, 10), (13, 15), (3, 15), (2, 10)), PINK), c.f(RECT(5, 2, 10, 3), BRN), c.d(WHITE[4], 6, 8, 10, 11, 8, 13, 5, 11), c.d(GOLD[3], 7, 3)), lambda a, i: W.box_at_hip(a, PINK[2], BRN[2], w=2.0, h=2.6, d=2.8), 'waist'),
     V('Склянка в оплётке', lambda c: (c.f(RECT(6, 0, 9, 2), WOOD), c.f(RECT(4, 3, 11, 15), MAG), c.c(RECT(4, 9, 11, 10), BRN[2]), c.c(RECT(4, 13, 11, 13), BRN[2]), c.d(WHITE[4], 5, 5, 5, 6, 6, 4)), lambda a, i: W.hip(a, i, 'R', .32), 'waist'),
     V('Пороховой рог', lambda c: (c.f(POLY((1, 3), (5, 1), (13, 8), (14, 14), (11, 13), (5, 10)), BONE), c.f(RECT(0, 2, 3, 4), GOLD), c.l(4, 3, 12, 10, BONE[0]), c.f(LINE(12, 13, 14, 15, 2), GOLD)), lambda a, i: W.hip(a, i, 'R', .34), 'waist'))
item('slicer_sling', 'Перевязь метателя', 'грудь и бедро',
     V('Ремень и диски', lambda c: (c.f(LINE(1, 14, 14, 1, 2), BRN), [c.f(DISC(x, y, 2.6), SILV) for x, y in ((4, 11), (8, 7), (12, 3))], [c.d(K, x, y) for x, y in ((4, 11), (8, 7), (12, 3))]), lambda a, i: W.strap(a, BRN[2]) + W.pin(a, i, 0, 5, .26), 'chest'),
     V('Сумка-диск', lambda c: (c.f(GEAR(8, 8, 7.4, 8, 1.6), SILV), c.f(DISC(8, 8, 3), LAP), c.d(WHITE[4], 7, 7), c.c(RING(8, 8, 3, 1), SILV[1])), lambda a, i: W.hip(a, i, 'R', .32), 'waist'),
     V('Клинки-полумесяцы', lambda c: (c.f(RING(6, 6, 5.6, 2) - RECT(6, 6, 15, 15), SILV), c.f(RING(10, 10, 5.6, 2) - RECT(0, 0, 9, 9), SILV), c.l(3, 12, 12, 3, BRN[2], 2), c.d(WHITE[4], 3, 3, 12, 12)), lambda a, i: W.strap(a, IRON[2]) + W.back_icon(a, i, .42, 0, 5, 0, .5), 'up', 'back'))
item('arcane_ward', 'Арканный щит', 'предплечье',
     V('Щит на руке', lambda c: (c.f(POLY((2, 1), (13, 1), (13, 9), (8, 15), (2, 9)), ARC), c.c(RECT(3, 2, 12, 2), ARC[4]), c.f(PATH((8, 4), (11, 8), (8, 12), (5, 8), (8, 4)), MAG), c.d(WHITE[4], 8, 8)), lambda a, i: W.spur(a, i, .42), 'hand', 'hand'),
     V('Сфера и орбита', lambda c: (c.f(DISC(8, 8, 4.6), ARC), c.d(WHITE[4], 6, 6, 7, 5), c.f(RING(8, 8, 7.5, 1) - RECT(4, 4, 11, 11), MAG), c.d(MAG[4], 1, 8, 14, 8, 8, 1, 8, 14)), lambda a, i: W.float_at(a, i, (-8.5, -4, -2), .36), 'chest'),
     V('Плита с руной на спине', lambda c: (c.f(RECT(2, 1, 13, 14), OBS), c.c(RECT(3, 2, 12, 13), OBS[1]), c.f(PATH((5, 4), (10, 4), (5, 11), (10, 11)), ARC), c.d(ARC[4], 5, 4, 10, 11), c.d(GOLD[3], 2, 1, 13, 1, 2, 14, 13, 14)), lambda a, i: W.back_icon(a, i, .42, 0, 5, 0, .5), 'up', 'back'))
item('clarity_crystal', 'Кристалл ясности', 'голова',
     V('Парит над головой', lambda c: (c.f(POLY((8, 0), (12, 5), (11, 11), (8, 15.5), (5, 11), (4, 5)), PINK), c.l(8, 1, 8, 14, PINK[4]), c.l(4, 5, 12, 5, PINK[1]), c.d(WHITE[4], 6, 4)), lambda a, i: W.above_head(a, i, -3.4, .32), 'head'),
     V('Обруч с кристаллом', lambda c: (c.f(PATH((1, 12), (4, 10), (8, 9), (12, 10), (15, 12), w=2), GOLD), c.f(POLY((8, 0), (11, 4), (10, 9), (6, 9), (5, 4)), PINK), c.d(WHITE[4], 7, 3, 7, 4)), lambda a, i: W.circlet(a, GOLD[2], i, -6.4, .28), 'head'),
     V('Призма у виска', lambda c: (c.f(POLY((8, 1), (15, 14), (1, 14)), ICE), c.f(POLY((8, 1), (15, 14), (8, 14)), PINK), c.l(1, 11, 5, 9, GOLD[3]), c.l(11, 9, 15, 11, CYAN[3])), lambda a, i: W.side_head(a, i, 'R', -5.5, .32), 'head'))

# ------------------------------------------------------------------ знаки охотника: одна форма, цвет и значок мира
HUNTERS = [('overworld', 'обычного мира', OVER), ('nether', 'Ада', NETH), ('end', 'Края', END), ('iceika', 'Айсики', ICEK), ('eden', 'Эдема', EDEN), ('wildwood', 'Дикого леса', WILD),
           ('apalachia', 'Апалачии', APAL), ('skythern', 'Небесного терна', SKYT), ('mortum', 'Мортума', MORT), ('arcana', 'Арканы', ARC), ('vethea', 'Везеи', VETH)]


def claw(c, r):
    cords(c, 3)
    c.f(POLY((6, 4), (10, 4), (12, 8), (9, 15), (8, 15), (6.5, 10)), BONE)
    c.l(8, 6, 9, 12, BONE[1])
    c.f(RECT(6, 3, 9, 4), r)


def badge(c, r):
    c.f(POLY((2, 1), (13, 1), (13, 9), (8, 15), (2, 9)), r)
    c.f(POLY((8, 3), (11, 7), (8, 12), (5, 7)), BONE)
    c.d(K, 7, 6, 8, 6)
    c.c(RECT(2, 1, 13, 1), r[4])


def trophy(c, r):
    c.f(POLY((2, 7), (0, 1), (5, 4)), r)
    c.f(POLY((13, 7), (15, 1), (10, 4)), r)
    c.f(RECT(3, 4, 12, 11), BONE)
    c.f(RECT(5, 12, 10, 14), BONE)
    c.c(RECT(5, 7, 6, 8), r[1])
    c.c(RECT(9, 7, 10, 8), r[1])
    c.d(K, 7, 10, 8, 10, 6, 13, 8, 13)


for k_, nm, ramp in HUNTERS:
    item('hunter_' + k_, 'Знак охотника ' + nm, 'шея',
         V('Коготь на шнуре', lambda c, r=ramp: claw(c, r), lambda a, i: W.pendant(a, i, BRN[1])),
         V('Щиток с клыком', lambda c, r=ramp: badge(c, r), lambda a, i: W.pin(a, i, -1.9, 3.2, .28)),
         V('Череп-трофей на виске', lambda c, r=ramp: trophy(c, r), lambda a, i: W.side_head(a, i, 'R', -4, .3), 'head'))
