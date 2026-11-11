"""Сверка аддона с DivineRPG: все обращения байткода к классам, полям и методам net/divinerpg должны быть в целевом jar Divine
(аддон собирается против 1.4.2.3, а играют и на 1.4.1.5 - там пропущенное поле роняет игру при загрузке класса).
Запуск: python3 divine_refs_check.py <jar аддона> <jar Divine> [jar Minecraft/Forge ...]. Jar Minecraft/Forge нужны для
унаследованных членов (Forge SRG-jar и forgeSrc: в первом имена SRG, во втором - добавленные Forge методы вроде getEntityData).
Рефлексия (getField по имени) не проверяется - она и так мягкая. Код выхода 1 - есть пропуски."""
import struct
import sys
import zipfile


def parse(data):
    """Пул констант и члены класса: (имя, super, [(поле, desc)], [(метод, desc)], ссылки [(kind, owner, name, desc)], классы)."""
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
    for jar in mc:  # Minecraft и Forge: наследованные члены (члены одного класса из разных jar складываются)
        for k, v in load(jar, 'net/minecraft').items():
            if k in pool and not k.startswith('net/divinerpg/'):
                old = pool[k]
                pool[k] = (old[0], old[1], old[2], old[3] + v[3], old[4] + v[4], old[5], old[6])
            elif k not in pool:
                pool[k] = v
    OBJ = {'hashCode', 'equals', 'toString', 'getClass', 'clone', 'finalize', 'notify', 'notifyAll', 'wait'}

    def has(owner, kind, name, desc, seen=None):
        """True - есть, False - точно нет, None - не проверить (классы java.*)."""
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
    print('классов аддона %d, обращений к Divine %d' % (len(ours), checked))
    for c, by in sorted(missing_cls):
        print('НЕТ КЛАССА', c, '<-', by)
    for o, k, m, d, by in sorted(missing_mem):
        print('НЕТ', k, o + '.' + m, d, '<-', by)
    print('итог:', 'всё есть' if not missing_cls and not missing_mem else 'есть пропуски')
    return 0 if not missing_cls and not missing_mem else 1


if __name__ == '__main__':
    sys.exit(main(*sys.argv[1:]))
