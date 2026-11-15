"""Texts and styles of the second round of Wanderer armor concepts (styles are shared by all rounds)."""

ROUND = 'Second round'
LEDE = ('Three looks were picked in the first round, they are below. For R1, R4, R5 and R7 there are three new variants each, letters D, E, F, so as not to confuse '
        'them with the first round. R1 is now made from Divine armor, so all three new R1 variants build on it: gold, dark scarlet inlays, '
        'a light blue stone, steel boots. The models are rough again: made of blocks, on the player bones, front view on the left, back view on the right.')

PICKED = [
    ('r2c', 2, 'Eden', 'C', 'Petals and halo', 'Gold and scarlet petals on the chest and shoulders, a gold circlet with a stone, a halo glows above the head.'),
    ('r3b', 3, 'Wildwood', 'B', 'Wooden plate armor', 'Plate armor of blue wood, a closed helmet with branching antlers, roots by the boots.'),
    ('r6a', 6, 'Mortum', 'A', 'Hooded shadow', 'A black tattered cloak and hood, the face in darkness, red eyes, chains on the belt.'),
]

TOC = [
    (1, 'Divine', '#ccaf3b', 'new'),
    (2, 'Eden', '#d8401c', 'picked C'),
    (3, 'Wildwood', '#1a62e0', 'picked B'),
    (4, 'Apalachia', '#c020d8', 'new'),
    (5, 'Skythern', '#aeb2ba', 'new'),
    (6, 'Mortum', '#ff3020', 'picked A'),
    (7, 'Halite', '#50e010', 'new'),
]

TIERS = [
    (1, 'Divine', 'made from Divine armor, no boss', ['#ccaf3b', '#a01c0c', '#46d6e6'], [
        ('r1d', 'D', 'Traveler in Divine', 'A dark traveling coat, over it a gold cuirass with a light blue stone, gold pauldrons and bracers, '
                                             'a circlet with a stone on the brow, a short scarlet cloak, steel boots like the Divine armor ones.'),
        ('r1e', 'E', 'Divine plate armor', 'The Divine armor itself, only heavier: gold plate armor with a scarlet trim, scarlet tassels under the pauldrons, '
                                         'a closed helmet with a light blue stone on the brow and a scarlet crest, steel boots.'),
        ('r1f', 'F', 'Herald', 'Light plate armor with gold and a scarlet sash, light blue stones on the helmet and chest, folded gold wings '
                               'on the back: the tops peek out over the shoulders, the flight feathers reach almost to the knees.')]),
    (4, 'Apalachia', 'boss - Great Cadillion of Apalachia', ['#c020d8', '#5a1078', '#f090ff'], [
        ('r4d', 'D', 'Sorcerer', 'A dark floor-length robe with a glowing trim and runes along the hem, wide sleeves, '
                               'a pointed hat with a lilac ribbon, a runic ring floats behind the back.'),
        ('r4e', 'E', 'Enchanted warrior', 'Steel plate armor with a lilac enchantment sheen, a purple tabard with a gold trim, '
                                         'a helmet with a long purple plume, a ghostly round shield on the back.'),
        ('r4f', 'F', 'Mountains of Apalachia', 'Armor like a rock: lilac stone, purple moss on the collar, belt and edges, '
                                     'stepped spikes with snowcaps on the shoulders and helmet.')]),
    (5, 'Skythern', 'boss - Great Cadillion of Skythern', ['#c8ccd4', '#1c2450', '#8ab8ec'], [
        ('r5d', 'D', 'Megalith', 'Heavy stone slabs with glowing light blue seams, huge square pauldrons, '
                               'a circle glows on the chest, a monolith helmet with a single glowing slit.'),
        ('r5e', 'E', 'Stargazer', 'Night sky: a dark blue hood and a cloak in twinkling stars with a silver trim, '
                                 'a crescent moon on the chest, small stars float nearby.'),
        ('r5f', 'F', 'Stormpetrel', 'Storm plate armor with a lightning bolt across the chest and helmet, clouds on the shoulders, lightning strikes down from them, '
                                   'a helmet with a bird beak and a feather crest.')]),
    (7, 'Halite', 'boss - Karot', ['#50e010', '#1e4a0a', '#e0b84a'], [
        ('r7d', 'D', 'Crown of wanderings', 'Dark green plate armor with gold. On the chest around the glowing halite are six stones of the worlds passed, '
                                        'the same stones in a gold crown on the helmet, a long cloak with a gold trim.'),
        ('r7e', 'E', 'Floating plate armor', 'The halite plates do not touch the body: they hang around a dark suit, seams glow between them, '
                                    'a helmet of separated plates with a glowing gap, a ring of light on the back.'),
        ('r7f', 'F', 'Wanderer at the goal', 'A long dark green frock coat with glowing buttons, a halite scarf, a wide-brimmed hat '
                                       'with a gold ribbon and a green feather, a halite lantern on the belt.')]),
]

CSS = """
@page { size: A4 portrait; margin: 0; }
* { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
html, body { margin: 0; background: #131116; color: #ece5da; font-family: "Golos Text", "Segoe UI", sans-serif; }
.page { width: 210mm; height: 297mm; padding: 12mm 12mm 10mm; page-break-after: always; break-after: page; display: flex; flex-direction: column; overflow: hidden; }
.page:last-child { page-break-after: auto; break-after: auto; }
h1, h2, h3 { font-family: "Pixelify Sans", "Courier New", monospace; font-weight: 700; margin: 0; letter-spacing: .02em; }
h1 { font-size: 30pt; line-height: 1.1; }
.sub { font-family: "Pixelify Sans", monospace; color: #e3a347; font-size: 15pt; margin-top: 1mm; }
h3 { font-size: 13pt; color: #e3a347; margin: 5mm 0 3mm; }
.lede { color: #a39aa6; font-size: 10.5pt; margin: 4mm 0 0; line-height: 1.45; }
.picked { display: flex; flex-direction: column; gap: 3mm; flex: 1; min-height: 0; }
.picked .card { flex: 1; }
.toc { margin-top: 5mm; display: grid; grid-template-columns: repeat(7, 1fr); gap: 3mm; }
.toc div { border-top: 1.2mm solid; padding-top: 2mm; font-size: 9.5pt; color: #a39aa6; }
.toc span { display: block; font-family: "Pixelify Sans", monospace; color: #ece5da; font-size: 13pt; }
.toc em { display: block; font-style: normal; color: #e3a347; font-size: 8.5pt; margin-top: 1mm; }
.head { display: flex; align-items: baseline; gap: 5mm; border-bottom: 1px solid #322c38; padding-bottom: 3mm; }
.head h2 { font-size: 22pt; }
.head .rank { color: #e3a347; }
.head .meta { color: #a39aa6; font-size: 10.5pt; }
.chips { display: inline-flex; gap: 1.5mm; }
.chips i { width: 4mm; height: 4mm; border-radius: .8mm; display: inline-block; border: 1px solid #00000066; }
.grid { display: flex; flex-direction: column; gap: 4mm; margin-top: 5mm; flex: 1; min-height: 0; }
.card { background: #1c1920; border: 1px solid #322c38; border-radius: 2mm; overflow: hidden; display: flex; flex: 1; min-height: 0; }
.pic { position: relative; height: 100%; aspect-ratio: 3 / 2; flex: none; }
.pic img { height: 100%; width: 100%; display: block; }
.views { position: absolute; top: 2mm; left: 3mm; right: 3mm; display: flex; justify-content: space-between; font-size: 7.5pt; letter-spacing: .08em; text-transform: uppercase; color: #ffffffa0; }
.body { padding: 5mm 5mm; min-width: 0; }
.title { font-weight: 600; font-size: 12.5pt; }
.title.t2 { margin-top: 1mm; }
.title .ln { font-family: "Pixelify Sans", monospace; color: #e3a347; margin-right: 2mm; }
.desc { color: #a39aa6; font-size: 10pt; line-height: 1.4; margin: 1.5mm 0 0; }
.foot { margin-top: 3mm; display: flex; justify-content: space-between; color: #6e6674; font-size: 8.5pt; }
"""
