#!/usr/bin/env python3
"""Generates src/main/resources/assets/shulkerpalette/data/shape_gap_fill.json: for every
material and every shape it doesn't come in, the closest-looking block that does.

"Closest" is measured from the vanilla block textures themselves (not hand-picked): a
material's average color, plus a roughness score (average pixel deviation from that color,
so a flat texture like stone and a busy one like cobblestone don't get treated as the same
just because their average colors are close). The candidate with the smallest combined
distance wins. See Shapes.java for how the table is used (it fills gaps in Shapes.build()
after the direct, vanilla block-family variants).

Usage: python3 tools/gen-shape-gap-fill.py <path to minecraft-merged.jar>
Regenerate whenever new blocks ship, by rerunning against the matching jar. The source
material/shape table is a text dump GapDump writes (src/gametest/.../checks/GapDump.java),
read from /tmp/shape-gaps.txt. GapDump isn't in fabric.mod.json's gametest list (it's not a
self-check), so before running this: add
"com.trevorschoeny.shulkerpalette.checks.GapDump" to that list's "client" array, run
`./gradlew :shulker-palette:runClientGameTest`, then take it back out.
"""
import json
import sys
import zipfile
from pathlib import Path
from PIL import Image

BUSY_WEIGHT = 3.0
SHAPES = ["stairs", "slab", "wall", "fence", "fence_gate", "carpet", "cushion",
          "pressure_plate", "button", "trapdoor", "door"]
# A few materials whose block model points at another file's texture (a "smooth" variant
# reusing its parent's top texture, for instance), found in the model json by hand.
ALIAS = {"smooth_quartz": "quartz_block_bottom", "smooth_sandstone": "sandstone_top",
         "smooth_red_sandstone": "red_sandstone_top"}


def extract_textures(jar_path, out_dir):
    with zipfile.ZipFile(jar_path) as z:
        for name in z.namelist():
            if name.startswith("assets/minecraft/textures/block/") and name.endswith(".png"):
                z.extract(name, out_dir)
    return Path(out_dir, "assets/minecraft/textures/block")


def texture_path(tex_dir, material):
    if material in ALIAS:
        return tex_dir / (ALIAS[material] + ".png")
    # A waxed copper block renders as its unwaxed stage; "_top"/"_side"/"_bottom" cover
    # blocks with no single flat texture (quartz, sandstone...).
    for candidate in [material, material.replace("waxed_", ""), material + "_top",
                       material + "_side", material + "_bottom"]:
        p = tex_dir / (candidate + ".png")
        if p.exists():
            return p
    matches = sorted(tex_dir.glob(material + "*.png"))
    return matches[0] if matches else None


def features(path):
    """(avg red, avg green, avg blue, roughness) from a texture's first frame (for animated strips)."""
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    if h > w:
        im = im.crop((0, 0, w, w))
    px = [p for p in im.getdata() if p[3] >= 16]
    if not px:
        return (128.0, 128.0, 128.0, 0.0)
    n = len(px)
    mr = sum(p[0] for p in px) / n
    mg = sum(p[1] for p in px) / n
    mb = sum(p[2] for p in px) / n
    busy = sum(abs(p[0] - mr) + abs(p[1] - mg) + abs(p[2] - mb) for p in px) / n / 3
    return (mr, mg, mb, busy)


def distance(a, b):
    return (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2 + ((a[3] - b[3]) * BUSY_WEIGHT) ** 2


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    jar_path = sys.argv[1]
    repo = Path(__file__).resolve().parent.parent
    gaps_file = Path("/tmp/shape-gaps.txt")
    if not gaps_file.exists():
        print("Run the gametest first (it writes /tmp/shape-gaps.txt via GapDump).")
        sys.exit(1)

    rows = {}
    for line in gaps_file.read_text().splitlines():
        if not line.strip():
            continue
        material, *parts = line.split("|")
        rows[material] = {s: (None if i == "-" else i) for s, i in (p.split("=") for p in parts)}

    tex_dir = extract_textures(jar_path, "/tmp/shape-gap-fill-textures")
    feat = {}
    for material in rows:
        path = texture_path(tex_dir, material)
        if path:
            feat[material] = features(path)
        else:
            print(f"no texture found for {material}, skipping it entirely", file=sys.stderr)

    substitutions = {}
    for material, shapes in rows.items():
        if material not in feat:
            continue
        entry = {}
        for shape in SHAPES:
            if shapes.get(shape) is not None:
                continue
            best_item, best_d = None, None
            for candidate, cshapes in rows.items():
                citem = cshapes.get(shape)
                if candidate == material or candidate not in feat or citem is None:
                    continue
                d = distance(feat[material], feat[candidate])
                if best_d is None or d < best_d:
                    best_d, best_item = d, citem
            if best_item:
                entry[shape] = best_item
        if entry:
            substitutions[material] = entry

    out = repo / "src/main/resources/assets/shulkerpalette/data/shape_gap_fill.json"
    out.write_text(json.dumps(substitutions, indent=1, sort_keys=True) + "\n")
    total = sum(len(v) for v in substitutions.values())
    print(f"{out}: {len(substitutions)} materials, {total} gap entries")


if __name__ == "__main__":
    main()
