"""Texts of the third round of Wanderer armor concepts: simpler, variants Zh, Z, I."""
from wprint_data import PICKED, TOC

ROUND = 'Third round'
LEDE = ('Simpler variants: ordinary plate armor and clothes in the colors of the world, one or two eye-catching details on each, almost no glow or floating '
        'parts - this is closer to how the armor will look in the game. Letters G, H, I; the second round variants (D, E, F) are still valid too, '
        'you can pick from all of them. The picks from the first round are below.')

TIERS = [
    (1, 'Divine', 'made from Divine armor, no boss', ['#ccaf3b', '#a01c0c', '#46d6e6'], [
        ('r1g', 'G', 'Divine armor, clean version', 'Gold plate armor almost like the Divine armor, a scarlet belt and cuffs, steel boots, '
                                                  'an open gold helmet with a light blue stone on the brow.'),
        ('r1h', 'H', 'Gold and steel', 'A gold cuirass, bracers, pauldrons and boots, steel sleeves and thighs, a closed steel helmet '
                                      'with a gold stripe and a cross-shaped slit.'),
        ('r1i', 'I', 'Scarlet surcoat', 'Chainmail, over it a scarlet surcoat with a gold trim and a light blue stone on the chest, gold bracers, '
                                  'a chainmail hood with a gold circlet.')]),
    (4, 'Apalachia', 'boss - Great Cadillion of Apalachia', ['#c020d8', '#5a1078', '#f090ff'], [
        ('r4g', 'G', 'Purple leather', 'A purple laced jacket, lilac cuffs, a plain hood, a crystal glows on the belt.'),
        ('r4h', 'H', 'Smooth plate armor', 'Purple plates, a lilac stripe runs across the helmet and chest, a slit on the helmet.'),
        ('r4i', 'I', 'Plate armor and cape', 'Dark plate armor with a purple trim, a purple cape over the shoulders and a short cloak, '
                                      'lilac light in the helmet slit.')]),
    (5, 'Skythern', 'boss - Great Cadillion of Skythern', ['#aeb2ba', '#eef0f4', '#8ab8ec'], [
        ('r5g', 'G', 'Chainmail and tabard', 'Silver chainmail and a chainmail hood, a light blue tabard with a white circle, white bracers and boots.'),
        ('r5h', 'H', 'White plate armor', 'Smooth white plates with a light blue trim, double pauldrons, a closed helmet with a slit.'),
        ('r5i', 'I', 'Gray cloak', 'Plain gray plate armor, a white hood with a cape and a white cloak with a light blue trim, a light blue clasp.')]),
    (7, 'Halite', 'boss - Karot', ['#50e010', '#1e4a0a', '#e0b84a'], [
        ('r7g', 'G', 'Polished halite', 'Green faceted plate armor with a dark trim, a closed helmet with a slit, no glow.'),
        ('r7h', 'H', 'Halite and gold', 'Dark green plate armor with a gold trim, halite in a gold setting glows on the chest, '
                                      'a gold crest on the helmet, a short cloak.'),
        ('r7i', 'I', 'Halite hood', 'Dark leather, a green hood with a cape, halite bracers and a glowing halite brooch.')]),
]
