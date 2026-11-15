"""Texts of the "Terraria style" round: R1 hallowed, R4 orichalcum, R7 chlorophyte; each has three heads."""
from wprint_data import PICKED

ROUND = 'In the spirit of Terraria'
LEDE = ('Three tiers inspired by Terraria armor: R1 - Hallowed, R4 Apalachia - Orichalcum, R7 Halite - Chlorophyte. As in Terraria, '
        'the set has three heads for a fighting style: H - helmet (melee), M - mask (ranged), C - cowl (arcana); one body. '
        'The set bonuses from Terraria are shown as details: a leaf crystal above the Chlorophyte one, petals around the Orichalcum one. '
        'The earlier picks are below.')

TOC = [
    (1, 'Divine', '#f0c41c', 'Hallowed'),
    (2, 'Eden', '#d8401c', 'picked C'),
    (3, 'Wildwood', '#1a62e0', 'picked B'),
    (4, 'Apalachia', '#e030d4', 'Orichalcum'),
    (5, 'Skythern', '#aeb2ba', 'rounds 2-3'),
    (6, 'Mortum', '#ff3020', 'picked A'),
    (7, 'Halite', '#22a818', 'Chlorophyte'),
]

TIERS = [
    (1, 'Hallowed', 'made from Divine armor, no boss', ['#f0c41c', '#c8c8dc', '#a01434'], [
        ('r1th', 'H', 'Helmet', 'A closed gold helmet with a dark visor, a silver crest, small gold wings on the sides. Body: a dark gambeson, '
                             'a silver cuirass with a gold chevron and a red stone, big gold pauldrons, a scarlet cloak, silver boots.'),
        ('r1tm', 'M', 'Mask', 'A silver half mask for the lower face, an open gold helmet with a red stone and a tall gold plume.'),
        ('r1tk', 'C', 'Cowl', 'A gold cowl with a silver stripe, a silver circlet with a red stone, small gold wings.')]),
    (4, 'Orichalcum', 'Apalachia, boss - Great Cadillion of Apalachia', ['#e030d4', '#f590f0', '#c49ad8'], [
        ('r4th', 'H', 'Helmet', 'A closed faceted crimson helmet with a fan of pink crystals. Body: crimson faceted plates over dark violet, '
                             'lilac inlays, pink crystals on the shoulders, petals swirl around.'),
        ('r4tm', 'M', 'Mask', 'A lilac half mask, an open faceted helmet with a long curved horn crest.'),
        ('r4tk', 'C', 'Cowl', 'A faceted crimson cowl with a cape, a pink stone glows on the brow, three crystals on the crown.')]),
    (7, 'Chlorophyte', 'Halite, boss - Karot', ['#22a818', '#9ef000', '#727272'], [
        ('r7th', 'H', 'Helmet', 'A closed leaf helmet, gray horns to the sides, leaves as a crest. Body: green leaf plates with bright veins, '
                             'gray metal on the chest and belt, leaves fanned out on the shoulders and back, a glowing leaf crystal floats above the head.'),
        ('r7tm', 'M', 'Mask', 'A light green half mask, an open leaf helmet with a leaf crest and a gray horn.'),
        ('r7tk', 'C', 'Cowl', 'A leaf cowl with a cape, big curved gray horns, a green stone glows on the brow.')]),
]

ROUND_IN = 'in the spirit of Terraria'
