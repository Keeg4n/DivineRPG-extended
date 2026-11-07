"""Текстура Вьюжной ели 128x128: хвоя со снегом, кора, корни, ветви и глаза. Раскладка - client/ModelBlizzardSpruce."""
import os
import random
import struct
import sys
import zlib

W = H = 128
random.seed(7)
pix = [[(0, 0, 0, 0)] * W for _ in range(H)]


def noise(base, spread):
    d = random.randint(-spread, spread)
    return tuple(max(0, min(255, c + d)) for c in base) + (255,)


def rect(x, y, w, h, fn):
    for j in range(y, y + h):
        for i in range(x, x + w):
            pix[j][i] = fn(i, j)


def needles(top, height):
    """Хвоя: снег лежит сверху и редко на боках, ниже тёмная зелень с прожилками."""
    def fn(i, j):
        if j < top + height // 3 or random.random() < 0.08:
            return noise((226, 238, 246), 10)
        if (i + j * 3) % 7 == 0:
            return noise((22, 58, 40), 8)
        return noise((36, 86, 60), 12)
    return fn


def bark(i, j):
    if i % 5 == 0:
        return noise((52, 34, 24), 6)
    return noise((84, 56, 38), 10)


def eye(i, j):
    return (170, 240, 255, 255)


rect(0, 0, 112, 36, needles(0, 36))     # нижний ярус
rect(0, 36, 88, 30, needles(36, 30))    # второй
rect(0, 66, 64, 24, needles(66, 24))    # третий
rect(64, 66, 32, 16, needles(66, 16))   # верхушка
rect(96, 36, 32, 44, bark)              # ствол
rect(64, 90, 24, 14, bark)              # корни
rect(0, 100, 40, 8, bark)               # ветви-руки
rect(44, 100, 14, 3, eye)               # глаза


def png(path):
    raw = b''.join(b'\x00' + b''.join(struct.pack('4B', *p) for p in row) for row in pix)

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'divinerpgextended', 'textures', 'entity', 'blizzard_spruce.png')
png(out)
print('wrote', out)
