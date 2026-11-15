"""Addon icons: the charged Arcanite Pickaxe, the mannequin icon and mob descriptions for Xaero's Minimap.

Xaero draws the head of the mob model on the radar; on Divine and addon models it cannot find it, and the mob disappears from the map.
The mob description {"default": "dot"} tells it to draw a colored dot instead of an icon (enemies are red); the mannequin has its own icon
(a straw face), not the player's head. Xaero looks up the description by mob id: assets/xaerominimap/entity/icon/definition/<id>.json,
the id is the name from EntityList (in 1.7.10 for mods with a dot: divinerpg.TheGrue). Divine mob ids are taken from its lang:
entity.<id>.name (Divine writes there all the names under which it registers mobs).

Run (needs Pillow): python xaero_icons.py <repo root> <Divine jar> [<Divine jar> ...]
"""
import colorsys
import json
import os
import re
import sys
import zipfile

from PIL import Image

ROOT, JARS = sys.argv[1], sys.argv[2:]
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets')
OURS = os.path.join(ASSETS, 'divinerpgextended')
XAERO = os.path.join(ASSETS, 'xaerominimap', 'entity', 'icon')
DUMMY_SPRITE = 'divinerpgextended_training_dummy.png'
BUILDER = 'xaero.common.minimap.render.radar.EntityIconDefinitions.buildVariantIdString'
# vanilla mobs: Xaero has its own descriptions for them, we leave them alone
VANILLA = {'Item', 'XPOrb', 'LeashKnot', 'Painting', 'Arrow', 'Snowball', 'Fireball', 'SmallFireball', 'ThrownEnderpearl',
           'EyeOfEnderSignal', 'ThrownPotion', 'ThrownExpBottle', 'ItemFrame', 'WitherSkull', 'PrimedTnt', 'FallingSand',
           'FireworksRocketEntity', 'Boat', 'MinecartRideable', 'MinecartChest', 'MinecartFurnace', 'MinecartTNT', 'MinecartHopper',
           'MinecartSpawner', 'MinecartCommandBlock', 'Mob', 'Monster', 'Creeper', 'Skeleton', 'Spider', 'Giant', 'Zombie', 'Slime',
           'Ghast', 'PigZombie', 'Enderman', 'CaveSpider', 'Silverfish', 'Blaze', 'LavaSlime', 'EnderDragon', 'WitherBoss', 'Bat',
           'Witch', 'Pig', 'Sheep', 'Cow', 'Chicken', 'Squid', 'Wolf', 'MushroomCow', 'SnowMan', 'Ozelot', 'Ocelot', 'VillagerGolem',
           'EntityHorse', 'Villager', 'EnderCrystal'}


def charged_pick():
    """Pickaxe head (turquoise) -> bright violet-pink, lighter; the handle as it was."""
    im = Image.open(os.path.join(OURS, 'textures', 'items', 'arcane_pick.png')).convert('RGBA')
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if not 0.45 <= h <= 0.62:
                continue
            nr, ng, nb = colorsys.hsv_to_rgb(0.82, s, min(1.0, v * 1.15 + 0.05))
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    im.save(os.path.join(OURS, 'textures', 'items', 'arcane_pick_charged.png'))


def dummy_sprite():
    """Mannequin face (8x8 from the straw texture) x6 in the middle of 64x64, with a dark border."""
    skin = Image.open(os.path.join(OURS, 'textures', 'entity', 'training_dummy.png')).convert('RGBA')
    face = skin.crop((8, 8, 16, 16)).resize((48, 48), Image.NEAREST)
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    out.paste(Image.new('RGBA', (52, 52), (40, 28, 12, 255)), (6, 6))
    out.paste(face, (8, 8))
    os.makedirs(os.path.join(XAERO, 'sprite'), exist_ok=True)
    out.save(os.path.join(XAERO, 'sprite', DUMMY_SPRITE))


def divine_ids():
    ids = set()
    for jar in JARS:
        z = zipfile.ZipFile(jar)
        for name in z.namelist():
            if name.startswith('assets/divinerpg/lang/') and name.endswith('.lang'):
                for line in z.read(name).decode('utf-8', 'replace').splitlines():
                    m = re.match(r'entity\.([A-Za-z0-9_.]+)\.name=', line)
                    if m and m.group(1) not in VANILLA:
                        ids.add(m.group(1))
    return ids


def our_ids():
    src = open(os.path.join(ROOT, 'src', 'main', 'java', 'com', 'divinerpgextended', 'registry', 'ModItems.java'), encoding='utf-8').read()
    return {'divinerpgextended.' + n for n in re.findall(r'registerModEntity\([^,]+,\s*"([A-Za-z0-9_]+)"', src)}


def definitions(ids):
    out_dir = os.path.join(XAERO, 'definition')
    os.makedirs(out_dir, exist_ok=True)
    for old in os.listdir(out_dir):
        if old.endswith('.json'):
            os.remove(os.path.join(out_dir, old))
    for i in sorted(ids):
        icon = 'sprite:' + DUMMY_SPRITE if i == 'divinerpgextended.TrainingDummy' else 'dot'
        with open(os.path.join(out_dir, i + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump({'variantIdBuilderMethod': BUILDER, 'variants': {'default': icon}, 'modelConfigs': []}, f, indent=1)
            f.write('\n')


if __name__ == '__main__':
    charged_pick()
    dummy_sprite()
    ids = divine_ids() | our_ids()
    definitions(ids)
    print('описаний Xaero:', len(ids))
