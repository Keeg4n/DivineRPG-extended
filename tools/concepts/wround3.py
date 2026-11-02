"""Третий раунд концептов брони Странника: попроще - латы и одежда в цветах мира, одна-две приметные детали, почти без свечения.
Варианты Ж/З/И для R1 (из божественной брони), R4, R5, R7.

Запуск: python wround3.py [ключ ...]  (без ключей - все варианты раунда). Картинки - в wanderer_concepts рядом.
"""
import sys

from wconcepts import P, rect, disc, trim, dots, facets, box, sym, chest, belt, upper_arm, vambrace, thigh, boot, helm, pauldron, hood, cloak, build
from wround2 import new as _new, skirt, NEW as _NEW2, DG, DGD, DGL, CRIM, CRIMD, GEM, STEEL, STEELD

NEW = []
SIDES = ('north', 'south', 'east', 'west')


def new(key, tier, line, title):
    NEW.append(key)
    reg = _new(key, tier, line, title)
    _NEW2.remove(key)
    return reg


def open_helm(paint, inf=0.9):
    """Открытый шлем: верх, затылок и щёки, лицо видно."""
    return ([box('head', -4 - inf, -8 - inf, -4 - inf, 8 + 2 * inf, inf + 2.1, 8 + 2 * inf, paint),
             box('head', -4 - inf, -5.9, 4.0, 8 + 2 * inf, 5.9, inf, paint)]
            + sym(box('head', -4 - inf, -5.9, -4 - inf, inf, 5.0, 8 + inf, paint)))


def tabard(paint, top=0.4, length=13.0, w=5.2):
    return [box('body', -w / 2, top, -3.4, w, length, 0.5, paint), box('body', -w / 2, top, 2.9, w, length, 0.5, paint)]


# ---- R1 из божественной брони
@new('r1g', 1, 'Ж', 'Божественная броня начисто: золото, алый пояс, открытый шлем с камнем')
def r1g():
    gold = P(DG, trim(0.4, DGD))
    bs = chest(P(DG, trim(0.45, DGD), rect(('north',), 4.6, 5.4, 0, 99, DGD), disc(('north',), 5, 3.0, 0.8, GEM, glow=True)))
    bs += upper_arm(gold) + vambrace(P(DG, trim(0.35, CRIM))) + belt(P(CRIM, rect(('north',), 4.0, 5.4, 0.3, 2.3, DG)))
    bs += thigh(gold) + boot(P(STEEL, trim(0.35, STEELD), rect(SIDES, -1, 99, -1, 1.0, CRIM)), toe=0.3)
    bs += open_helm(P(DG, trim(0.35, DGD))) + [box('head', -0.8, -7.4, -5.2, 1.6, 1.6, 0.5, P(GEM, glow=True))]
    return bs


@new('r1h', 1, 'З', 'Золото и сталь: стальные рукава и бёдра, золотые кираса и наплечники, глухой шлем')
def r1h():
    gold, steel = P(DG, trim(0.45, DGD)), P(STEEL, trim(0.35, STEELD))
    bs = chest(P(DG, trim(0.45, DGD), rect(('north',), 2.0, 8.0, 7.2, 7.8, CRIM))) + upper_arm(steel) + vambrace(gold)
    bs += pauldron(P(DG, trim(0.4, CRIM)), tilt=-12, layers=1, big=0.5) + belt(P(STEELD, rect(('north',), 4.0, 5.4, 0.3, 2.3, DG)))
    bs += thigh(steel) + boot(gold, toe=0.4)
    bs += helm(P(STEEL, trim(0.4, DG), rect(('north',), 1.6, 8.4, 4.0, 4.8, '#14161a'), rect(('north',), 4.6, 5.4, 4.0, 7.6, '#14161a'),
                 rect(('up',), 4.4, 5.6, -1, 99, DG)))
    return bs


@new('r1i', 1, 'И', 'Алое сюрко: кольчуга, поверх алое сюрко с золотой каймой, кольчужный капюшон с обручем')
def r1i():
    mail = P(STEEL, dots('all', 0.8, 0.28, STEELD), noise=0.08, bevel=0.1)
    bs = chest(mail) + upper_arm(mail) + thigh(mail) + vambrace(P(DG, trim(0.3, DGD))) + boot(P(STEEL, trim(0.35, STEELD)), toe=0.3)
    bs += chest(P(CRIM, trim(0.45, DG), disc(('north',), 5.1, 4.0, 1.5, DG), disc(('north',), 5.1, 4.0, 0.8, GEM, glow=True)), top=-0.6, bottom=10.6, inf=1.3)
    bs += skirt(P(CRIM, trim(0.4, DG)), top=10.4, length=4.4, inf=1.4) + belt(P(DGD), y=9.6, h=1.4)
    bs += pauldron(P(DG, trim(0.3, DGD)), tilt=-10, layers=1, big=0.1)
    bs += open_helm(mail) + [box('head', -5.0, -7.2, -5.0, 10.0, 1.0, 10.0, P(DG, trim(0.2, DGD)))]
    return bs


# ---- R4 Апалачия
AP, APD, APL = '#c020d8', '#5a1078', '#f090ff'


@new('r4g', 4, 'Ж', 'Пурпурная кожа: куртка со шнуровкой, капюшон, кристалл на поясе')
def r4g():
    lea = P('#7a2a96', trim(0.35, APD))
    bs = chest(P('#7a2a96', trim(0.35, APD), rect(('north',), 4.7, 5.3, 0, 99, APD), *[rect(('north',), 4.2, 5.8, y, y + 0.35, APL) for y in (1.5, 3.5, 5.5)]))
    bs += upper_arm(lea) + vambrace(P(APD, rect(SIDES, -1, 99, -1, 0.9, APL))) + belt(P('#2a0c3c'))
    bs += [box('body', 1.6, 9.0, -3.4, 1.6, 2.6, 1.0, P(APL, facets('#ffffff', 0.25), glow=True, bevel=0.1))]
    bs += thigh(P('#4a1860')) + boot(lea, toe=0.3) + hood(P(AP, trim(0.35, APD)), shadow='#1a0420')
    return bs


@new('r4h', 4, 'З', 'Гладкие латы: пурпурные пластины с сиреневой полосой, шлем с прорезью')
def r4h():
    plate = P(AP, trim(0.45, APD), rect(('up',), -1, 99, -1, 99, '#d850ec'))
    bs = chest(P(AP, trim(0.45, APD), rect(('north', 'south'), 4.4, 5.6, 0, 99, APL))) + upper_arm(P(APD, trim(0.3, '#3a0850'))) + vambrace(plate)
    bs += pauldron(P(AP, trim(0.45, APD), rect(('up',), 2.6, 3.8, -1, 99, APL)), tilt=-12, layers=1, big=0.6) + belt(P(APD))
    bs += thigh(P(APD, trim(0.3, '#3a0850'))) + boot(plate, toe=0.5)
    bs += helm(P(AP, trim(0.45, APD), rect(('north',), 1.6, 8.4, 4.2, 5.0, '#14041c'), rect(('north', 'up', 'south'), 4.4, 5.6, -1, 99, APL)))
    return bs


@new('r4i', 4, 'И', 'Латы и накидка: тёмные латы, пурпурная накидка на плечах и короткий плащ')
def r4i():
    dark = P('#3a1a4c', trim(0.4, AP))
    bs = chest(dark) + upper_arm(P('#2a1238', trim(0.3, APD))) + vambrace(dark) + belt(P('#1e0c2a')) + thigh(P('#2a1238')) + boot(dark, toe=0.4)
    cape = P(AP, trim(0.4, APL))
    bs += [box('body', -5.8, -1.6, -3.6, 11.6, 3.8, 7.2, cape)]
    bs += sym(box('rightArm', -4.6, -3.9, -3.5, 6.4, 3.6, 7.0, cape, rot=(0, 0, -14), pivot=(-1, -3, 0)))
    bs += cloak(P(AP, trim(0.45, APD), grad=0.3), length=15, flare=8)
    bs += helm(P('#3a1a4c', trim(0.4, AP), rect(('north',), 1.6, 8.4, 4.2, 5.0, APL, glow=True)))
    return bs


# ---- R5 Небесный терн
SKY, SKYL, SKYD, BLUE = '#aeb2ba', '#eef0f4', '#6a6e78', '#8ab8ec'


@new('r5g', 5, 'Ж', 'Кольчуга и табар: серебряная кольчуга, голубой табар, кольчужный капюшон')
def r5g():
    mail = P(SKY, dots('all', 0.8, 0.28, '#8a8e98'), noise=0.06, bevel=0.1)
    bs = chest(mail) + upper_arm(mail) + thigh(mail) + vambrace(P(SKYL, trim(0.3, SKYD))) + boot(P(SKYL, trim(0.3, SKYD)), toe=0.3)
    bs += tabard(P(BLUE, trim(0.35, SKYL), disc(('north',), 2.6, 3.4, 1.2, SKYL))) + belt(P(SKYD, rect(('north',), 4.0, 5.4, 0.3, 2.3, SKYL)))
    bs += open_helm(mail)
    return bs


@new('r5h', 5, 'З', 'Белые латы: гладкие белые пластины с голубой каймой, шлем с прорезью')
def r5h():
    plate = P(SKYL, trim(0.45, BLUE))
    bs = chest(P(SKYL, trim(0.45, BLUE), rect(('north',), 4.5, 5.5, 0, 99, BLUE))) + upper_arm(P(SKY, trim(0.3, SKYD))) + vambrace(plate)
    bs += pauldron(P(SKYL, trim(0.45, BLUE)), tilt=-12, layers=2, big=0.4) + belt(P(SKYD)) + thigh(P(SKY, trim(0.3, SKYD))) + boot(plate, toe=0.5)
    bs += helm(P(SKYL, trim(0.45, BLUE), rect(('north',), 1.6, 8.4, 4.0, 4.8, '#10141c')))
    return bs


@new('r5i', 5, 'И', 'Серый плащ: простые серые латы, белый капюшон и плащ с голубой застёжкой')
def r5i():
    plate = P(SKY, trim(0.35, SKYD))
    bs = chest(plate) + upper_arm(plate) + vambrace(P(SKYL, trim(0.3, SKYD))) + belt(P(SKYD)) + thigh(P('#8a8e98')) + boot(plate, toe=0.3)
    bs += hood(P(SKYL, trim(0.35, SKY)), shadow='#1a1e28', capelet=P(SKYL, trim(0.35, BLUE)))
    bs += cloak(P(SKYL, trim(0.45, BLUE), grad=0.15), length=19, flare=9)
    bs += [box('body', -1.2, 0.9, -4.2, 2.4, 2.4, 0.8, P(BLUE, trim(0.3, '#5a88c8')))]
    return bs


# ---- R7 Галит
HA, HAD, HAM, HAL, GOLDH = '#50e010', '#1e4a0a', '#3a8a10', '#c0ff80', '#e0b84a'


@new('r7g', 7, 'Ж', 'Полированный галит: зелёные латы с гранями, глухой шлем')
def r7g():
    plate = P(HAM, trim(0.45, HAD), facets('#ffffff', 0.12))
    bs = chest(P(HAM, trim(0.45, HAD), facets('#ffffff', 0.12), rect(('north',), 4.6, 5.4, 0, 99, HAD))) + upper_arm(P(HAD, trim(0.3, '#0e2405')))
    bs += vambrace(plate) + pauldron(P(HAM, trim(0.45, HAD), facets('#ffffff', 0.12)), tilt=-12, layers=1, big=0.7) + belt(P('#0e2405'))
    bs += thigh(P(HAD, trim(0.3, '#0e2405'))) + boot(plate, toe=0.5)
    bs += helm(P(HAM, trim(0.45, HAD), facets('#ffffff', 0.12), rect(('north',), 1.6, 8.4, 4.2, 5.0, '#061004')))
    return bs


@new('r7h', 7, 'З', 'Галит и золото: тёмно-зелёные латы с золотой каймой, камень на груди, короткий плащ')
def r7h():
    plate = P(HAD, trim(0.45, GOLDH))
    bs = chest(P(HAD, trim(0.45, GOLDH), disc(('north',), 5, 3.6, 1.6, GOLDH), disc(('north',), 5, 3.6, 1.0, HA, glow=True)))
    bs += upper_arm(P(HAD, trim(0.3, HAM))) + vambrace(plate) + pauldron(P(HAD, trim(0.45, GOLDH)), tilt=-12, layers=1, big=0.6)
    bs += belt(P('#0e2405', rect(('north',), 4.0, 5.4, 0.3, 2.3, GOLDH))) + thigh(P(HAD, trim(0.3, HAM))) + boot(plate, toe=0.5)
    bs += helm(P(HAD, trim(0.45, GOLDH), rect(('north',), 1.6, 8.4, 4.2, 5.0, '#061004'), rect(('north', 'up', 'south'), 4.5, 5.5, -1, 99, GOLDH)))
    bs += cloak(P(HAD, trim(0.5, GOLDH), grad=0.3), length=15, flare=8)
    return bs


@new('r7i', 7, 'И', 'Галитовый капюшон: тёмная кожа, зелёный капюшон, галитовая брошь и наручи')
def r7i():
    lea = P('#24341c', trim(0.35, '#121c0c'))
    bs = chest(lea) + upper_arm(lea) + belt(P('#121c0c', rect(('north',), 4.0, 5.4, 0.3, 2.3, HAM))) + thigh(P('#1c2814')) + boot(lea, toe=0.3)
    bs += vambrace(P(HAM, trim(0.35, HAD), facets('#ffffff', 0.12)))
    bs += hood(P(HAM, trim(0.35, HAD)), shadow='#020a02', capelet=P(HAM, trim(0.35, HAD)))
    bs += [box('body', -1.1, 1.0, -4.4, 2.2, 2.2, 0.8, P(HAL, facets('#ffffff', 0.25), glow=True, bevel=0.1))]
    return bs


if __name__ == '__main__':
    for k in sys.argv[1:] or NEW:
        build(k)
