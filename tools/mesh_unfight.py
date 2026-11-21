"""Remove z-fighting from an armor mesh (.mesh): when two quads of one bone lie in the same plane, face the same way and overlap, the
smaller one is moved outward along its normal by a fraction of a pixel (EPS units of 1/16 block). Several stacked quads get EPS, 2*EPS...
Called by wanderer_armor.py after saving each look; can also be run on mesh files directly: python3 tools/mesh_unfight.py <file.mesh>..."""
import itertools
import sys

import numpy as np

EPS = 0.03


def _parse(path):
    lines = open(path, encoding="utf-8").read().splitlines()
    quads, bone = [], None
    for i, line in enumerate(lines):
        if line.startswith("p "):
            bone = line.split()[2]
        elif line.startswith("q ") and bone is not None:
            v = [float(x) for x in line.split()[1:]]
            quads.append({"line": i, "bone": bone, "n": np.array(v[:3]), "pts": np.array([v[3 + k * 5:6 + k * 5] for k in range(4)]),
                          "uv": [v[6 + k * 5:8 + k * 5] for k in range(4)]})
    return lines, quads


def _area_overlap(a, b, ax):
    o = [i for i in range(3) if i != ax]
    lo = np.maximum(a[:, o].min(0), b[:, o].min(0))
    hi = np.minimum(a[:, o].max(0), b[:, o].max(0))
    d = np.clip(hi - lo, 0, None)
    return d[0] * d[1]


def _area(p, ax):
    o = [i for i in range(3) if i != ax]
    d = p[:, o].max(0) - p[:, o].min(0)
    return d[0] * d[1]


def unfight(path, eps=EPS, passes=8):
    """Fix the file in place; repeat while something still overlaps (three stacked quads need two passes). @return quads moved in total"""
    total = 0
    for _ in range(passes):
        moved = _pass(path, eps)
        total += moved
        if not moved:
            break
    return total


def _pass(path, eps):
    lines, quads = _parse(path)
    shift = {}
    by_bone = {}
    for q in quads:
        if abs(np.max(np.abs(q["n"])) - 1) < 1e-3:  # axis-aligned faces only (the armor is made of boxes)
            by_bone.setdefault(q["bone"], []).append(q)
    for qs in by_bone.values():
        for a, b in itertools.combinations(qs, 2):
            if np.dot(a["n"], b["n"]) < 0.999:
                continue
            ax = int(np.argmax(np.abs(a["n"])))
            if abs(a["pts"][:, ax].mean() + shift.get(id(a), 0) * a["n"][ax] - b["pts"][:, ax].mean() - shift.get(id(b), 0) * b["n"][ax]) > 0.02:
                continue
            if _area_overlap(a["pts"], b["pts"], ax) < 0.05:
                continue
            small = a if _area(a["pts"], ax) <= _area(b["pts"], ax) else b
            big = b if small is a else a
            shift[id(small)] = max(shift.get(id(small), 0), shift.get(id(big), 0)) + eps
    for q in quads:
        s = shift.get(id(q), 0)
        if not s:
            continue
        p = q["pts"] + q["n"] * s
        out = "q %.4f %.4f %.4f" % tuple(q["n"])
        for k in range(4):
            out += "  %.4f %.4f %.4f %.6f %.6f" % (p[k][0], p[k][1], p[k][2], q["uv"][k][0], q["uv"][k][1])
        lines[q["line"]] = out
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    return len(shift)


if __name__ == "__main__":
    for f in sys.argv[1:]:
        print(f, unfight(f))
