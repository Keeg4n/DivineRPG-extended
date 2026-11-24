#!/bin/bash
# Optional resource pack: Wanderer armor in double resolution (4 texels per model pixel instead of 2), with the same detail pass.
# The pack carries its own meshes too (their UV layout is for the larger atlases); the game takes meshes and textures from resource
# packs over the jar's, so the pack has to match the addon version. Usage: tools/wanderer_hd_pack.sh <version>
# Writes build/hd-pack/DivineRPG-Extended-HD-Armor-<version>.zip.
set -eu
V=$1
cd "$(dirname "$0")/.." || exit 1
T=build/hd-pack/tmp
rm -rf "$T"
mkdir -p "$T"
python3 -B tools/wanderer_armor.py --hd "$T"
cp tools/hd_pack.png "$T/pack.png"
printf '{"pack": {"pack_format": 1, "description": "DivineRPG Extended %s: Wanderer armor in double resolution"}}\n' "$V" > "$T/pack.mcmeta"
Z=build/hd-pack/DivineRPG-Extended-HD-Armor-$V.zip
python3 - "$T" "$Z" <<'PY'
import os, sys, zipfile
src, out = sys.argv[1], sys.argv[2]
if os.path.exists(out):
    os.remove(out)
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(src):
        for f in sorted(files):
            p = os.path.join(root, f)
            z.write(p, os.path.relpath(p, src).replace(os.sep, '/'))
PY
echo "$Z $(du -h "$Z" | cut -f1)"
