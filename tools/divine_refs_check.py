"""Checking the addon against DivineRPG: all bytecode references to net/divinerpg classes, fields and methods must be present in the target Divine jar
(the addon is built against 1.4.2.3, but people also play on 1.4.1.5 - there a missing field crashes the game when the class loads).
Run: python3 divine_refs_check.py <addon jar> <Divine jar> [Minecraft/Forge jar ...]. The Minecraft/Forge jars are needed for
inherited members (Forge SRG jar and forgeSrc: the first has SRG names, the second has methods added by Forge such as getEntityData).
Reflection (getField by name) is not checked - it is soft anyway. Exit code 1 means there are gaps."""
import struct
import sys
import zipfile


def parse(data):
    """Constant pool and class members: (name, super, [(field, desc)], [(method, desc)], references [(kind, owner, name, desc)], classes)."""
    pos = 8
    n = struct.unpack('>H', data[pos:pos + 2])[0]
    pos += 2
    cp = [None] * n
    i = 1
    while i < n:
        tag = data[pos]
        if tag == 1:
            ln = struct.unpack('>H', data[pos + 1:pos + 3])[0]
            cp[i] = ('utf8', data[pos + 3:pos + 3 + ln].decode('utf-8', 'replace'))
            pos += 3 + ln
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1
        elif tag == 7:
            cp[i] = ('class', struct.unpack('>H', data[pos + 1:pos + 3])[0])
            pos += 3
        elif tag == 8:
            pos += 3
        elif tag in (9, 10, 11):
            c, nt = struct.unpack('>HH', data[pos + 1:pos + 5])
            cp[i] = ({9: 'field', 10: 'method', 11: 'imethod'}[tag], c, nt)
            pos += 5
        elif tag == 12:
            nm, ds = struct.unpack('>HH', data[pos + 1:pos + 5])
            cp[i] = ('nat', nm, ds)
            pos += 5
        elif tag == 15:
            pos += 4
        elif tag == 16:
            pos += 3
        elif tag == 18:
            pos += 5
        else:
            raise ValueError('tag %d' % tag)
        i += 1

    def utf(k):
        return cp[k][1]

    def cls(k):
        return utf(cp[k][1])

    access, this, sup = struct.unpack('>HHH', data[pos:pos + 6])
    pos += 6
    ni = struct.unpack('>H', data[pos:pos + 2])[0]
    ifaces = [cls(struct.unpack('>H', data[pos + 2 + 2 * j:pos + 4 + 2 * j])[0]) for j in range(ni)]
    pos += 2 + 2 * ni
    members = []
    for _ in range(2):
        cnt = struct.unpack('>H', data[pos:pos + 2])[0]
        pos += 2
        lst = []
        for _ in range(cnt):
            a, nm, ds, ac = struct.unpack('>HHHH', data[pos:pos + 8])
            pos += 8
            for _ in range(ac):
                ln = struct.unpack('>I', data[pos + 2:pos + 6])[0]
                pos += 6 + ln
            lst.append((utf(nm), utf(ds)))
        members.append(lst)
    refs, classes = [], set()
    for e in cp:
        if not e:
            continue
        if e[0] in ('field', 'method', 'imethod'):
            nat = cp[e[2]]
            refs.append((e[0], cls(e[1]), utf(nat[1]), utf(nat[2])))
        elif e[0] == 'class':
            classes.add(utf(e[1]))
    return cls(this), (cls(sup) if sup else None), ifaces, members[0], members[1], refs, classes


def load(jar, prefix=None):
    out = {}
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if n.endswith('.class') and (prefix is None or n.startswith(prefix)):
                info = parse(z.read(n))
                out[info[0]] = info
    return out


def main(addon, divine, *mc):
    ours = load(addon, 'com/divinerpgextended/')
    dv = load(divine, 'net/divinerpg/')
    pool = dict(dv)
    for jar in mc:  # Minecraft and Forge: inherited members (members of one class from different jars are merged)
        for k, v in load(jar, 'net/minecraft').items():
            if k in pool and not k.startswith('net/divinerpg/'):
                old = pool[k]
                pool[k] = (old[0], old[1], old[2], old[3] + v[3], old[4] + v[4], old[5], old[6])
            elif k not in pool:
                pool[k] = v
    OBJ = {'hashCode', 'equals', 'toString', 'getClass', 'clone', 'finalize', 'notify', 'notifyAll', 'wait'}

    def has(owner, kind, name, desc, seen=None):
        """True - present, False - definitely absent, None - cannot be checked (java.* classes)."""
        seen = seen if seen is not None else set()
        if owner in seen:
            return False
        seen.add(owner)
        if owner == 'java/lang/Object':
            return kind != 'field' and name in OBJ
        c = pool.get(owner)
        if c is None:
            return False if owner.startswith('net/divinerpg/') else None
        if (name, desc) in (c[3] if kind == 'field' else c[4]):
            return True
        unknown = False
        for parent in [c[1]] + c[2]:
            if not parent:
                continue
            r = has(parent, kind, name, desc, seen)
            if r:
                return True
            if r is None:
                unknown = True
        return None if unknown else False

    missing_cls, missing_mem, checked = set(), set(), 0
    for name, info in ours.items():
        for c in info[6]:
            base = c.lstrip('[').lstrip('L').rstrip(';')
            if base.startswith('net/divinerpg/') and base not in dv:
                missing_cls.add((base, name))
        for kind, owner, mname, desc in info[5]:
            if not owner.startswith('net/divinerpg/'):
                continue
            checked += 1
            if owner not in dv:
                missing_cls.add((owner, name))
                continue
            r = has(owner, 'field' if kind == 'field' else 'method', mname, desc)
            if r is False:
                missing_mem.add((owner, kind, mname, desc, name))
    print('addon classes %d, references to Divine %d' % (len(ours), checked))
    for c, by in sorted(missing_cls):
        print('NO CLASS', c, '<-', by)
    for o, k, m, d, by in sorted(missing_mem):
        print('MISSING', k, o + '.' + m, d, '<-', by)
    print('result:', 'all present' if not missing_cls and not missing_mem else 'gaps found')
    return 0 if not missing_cls and not missing_mem else 1


if __name__ == '__main__':
    sys.exit(main(*sys.argv[1:]))
