#!/usr/bin/env python3
"""Generates src/main/resources/assets/shulkerpalette/data/shape_gap_fill.json: for every
material and every variant it doesn't come in, the closest-looking block that does.

A "material" is a block family's base (stone, oak planks), a wool color, or any other solid
full block or variant block on its own (dirt, moss carpet). A variant in a box counts as its
material, so this one table answers for stone slabs too.

"Closest" is measured from the vanilla textures themselves (each block's real texture, read
through its blockstate and model), in two steps, the way a person would pick:

  1. Color first: the median color (what most of the face is), compared with CIEDE2000, the
     standard measure of how different two colors look (it already counts gray vs brown as
     a bigger step than light vs dark gray).
  2. Texture as the tiebreak: of every candidate whose color is within COLOR_BAND of the best
     color match ("pretty close"), the best texture match wins. Texture is three numbers from
     the lightness channel: contrast (standard deviation), detail (average difference between
     neighboring pixels) and structure (how much of the variation lines up in rows or columns,
     high for bricks and planks, low for speckled stone, andesite, dirt).

Stone's wall: stone bricks and cobblestone are nearer in color, but andesite is close enough
and speckled like stone, so andesite wins. Dirt's fence gate and door: spruce. Stone's
fence gate: poplar, the gray wood.
ShapeChecks pins these.

Usage: python3 tools/gen-shape-gap-fill.py <path to minecraft-merged.jar>
Regenerate whenever new blocks ship, by rerunning against the matching jar. The source
material/variant table is a text dump GapDump writes (src/gametest/.../checks/GapDump.java),
read from /tmp/shape-gaps.txt. GapDump isn't in fabric.mod.json's gametest list (it's not a
self-check), so before running this: add
"com.trevorschoeny.shulkerpalette.checks.GapDump" to that list's "client" array, run
`./gradlew :shulker-palette:runClientGameTest`, then take it back out.
"""
import io
import json
import math
import statistics
import sys
import zipfile
from pathlib import Path
from PIL import Image

# How far (CIEDE2000) past the best color match a candidate may be and still win on
# texture. Tuned so the pinned cases above come out right; widen it and texture wins more often.
COLOR_BAND = 4.0
# Structure runs 0 to 1; scaled so a brick-vs-speckle difference weighs about as much as a
# clear difference in contrast.
STRUCTURE_WEIGHT = 10.0
SHAPES = ["block", "stairs", "slab", "wall", "fence", "fence_gate", "carpet", "cushion",
          "pressure_plate", "button", "trapdoor", "door"]
# Model texture slots, best first, for the face that reads as "the block".
TEXTURE_KEYS = ["all", "side", "texture", "wall", "wool", "bottom", "end", "top", "front", "particle"]
# Tinted textures are gray in the jar; leaves are colored with their default (plains) tint.
TINTS = {"spruce_leaves": (0x61, 0x99, 0x61), "birch_leaves": (0x80, 0xA7, 0x55),
         "mangrove_leaves": (0x92, 0xC6, 0x48)}
FOLIAGE = (0x77, 0xAB, 0x2F)


class Jar:
    def __init__(self, path):
        self.zip = zipfile.ZipFile(path)
        self.names = set(self.zip.namelist())

    def json(self, path):
        return json.loads(self.zip.read(path)) if path in self.names else None

    def image(self, path):
        return Image.open(io.BytesIO(self.zip.read(path))) if path in self.names else None


def strip(ref):
    return ref.split(":")[-1]


def first_model(state):
    """The model of a blockstate's first variant (or first multipart piece)."""
    if "variants" in state:
        v = next(iter(state["variants"].values()))
    else:
        v = state["multipart"][0]["apply"]
    return strip((v[0] if isinstance(v, list) else v)["model"])


def model_textures(jar, model):
    """A model's texture slots merged up its parent chain, and whether any face is tinted."""
    textures, tinted = {}, False
    while model:
        m = jar.json(f"assets/minecraft/models/{model}.json")
        if m is None:
            break
        for k, v in m.get("textures", {}).items():
            textures.setdefault(k, v["sprite"] if isinstance(v, dict) else v)  # {"sprite": ..., options}
        for element in m.get("elements", []):
            tinted |= any("tintindex" in f for f in element.get("faces", {}).values())
        model = strip(m["parent"]) if "parent" in m else None
    return textures, tinted


def texture_of(jar, block):
    """(texture image, tint or None) for a block, or None when it has no readable texture."""
    state = jar.json(f"assets/minecraft/blockstates/{block}.json")
    if state is None:
        return None
    textures, tinted = model_textures(jar, first_model(state))
    for key in TEXTURE_KEYS:
        ref = textures.get(key)
        for _ in range(5):  # follow "#side"-style references
            if ref and ref.startswith("#"):
                ref = textures.get(ref[1:])
        if ref and not ref.startswith("#"):
            img = jar.image(f"assets/minecraft/textures/{strip(ref)}.png")
            if img:
                tint = TINTS.get(block, FOLIAGE) if tinted and "leaves" in block else None
                return img, tint
    return None


def srgb_to_lab(r, g, b):
    def lin(c):
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = lin(r), lin(g), lin(b)
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
    f = lambda t: t ** (1 / 3) if t > 0.008856 else 7.787 * t + 16 / 116
    return (116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))


def ciede2000(lab1, lab2, kL=1.0):
    """CIEDE2000 color difference (standard weights)."""
    L1, a1, b1 = lab1
    L2, a2, b2 = lab2
    C1, C2 = math.hypot(a1, b1), math.hypot(a2, b2)
    Cm = (C1 + C2) / 2
    G = 0.5 * (1 - math.sqrt(Cm ** 7 / (Cm ** 7 + 25 ** 7)))
    a1p, a2p = a1 * (1 + G), a2 * (1 + G)
    C1p, C2p = math.hypot(a1p, b1), math.hypot(a2p, b2)
    h1p = math.degrees(math.atan2(b1, a1p)) % 360
    h2p = math.degrees(math.atan2(b2, a2p)) % 360
    dLp, dCp = L2 - L1, C2p - C1p
    dh = h2p - h1p
    if C1p * C2p == 0:
        dh = 0
    elif dh > 180:
        dh -= 360
    elif dh < -180:
        dh += 360
    dHp = 2 * math.sqrt(C1p * C2p) * math.sin(math.radians(dh / 2))
    Lm, Cmp = (L1 + L2) / 2, (C1p + C2p) / 2
    if C1p * C2p == 0:
        hm = h1p + h2p
    elif abs(h1p - h2p) <= 180:
        hm = (h1p + h2p) / 2
    else:
        hm = (h1p + h2p + 360) / 2 if h1p + h2p < 360 else (h1p + h2p - 360) / 2
    T = (1 - 0.17 * math.cos(math.radians(hm - 30)) + 0.24 * math.cos(math.radians(2 * hm))
         + 0.32 * math.cos(math.radians(3 * hm + 6)) - 0.20 * math.cos(math.radians(4 * hm - 63)))
    SL = 1 + 0.015 * (Lm - 50) ** 2 / math.sqrt(20 + (Lm - 50) ** 2)
    SC = 1 + 0.045 * Cmp
    SH = 1 + 0.015 * Cmp * T
    RT = (-2 * math.sqrt(Cmp ** 7 / (Cmp ** 7 + 25 ** 7))
          * math.sin(math.radians(60 * math.exp(-(((hm - 275) / 25) ** 2)))))
    return math.sqrt((dLp / (kL * SL)) ** 2 + (dCp / SC) ** 2 + (dHp / SH) ** 2
                     + RT * (dCp / SC) * (dHp / SH))


def features(img, tint):
    """(average Lab color, (contrast, detail, structure)) over a texture's opaque pixels."""
    im = img.convert("RGBA")
    w, h = im.size
    if h > w:  # animated strip: first frame
        im, h = im.crop((0, 0, w, w)), w
    px = list(im.get_flattened_data()) if hasattr(im, "get_flattened_data") else list(im.getdata())
    if tint:
        px = [(p[0] * tint[0] // 255, p[1] * tint[1] // 255, p[2] * tint[2] // 255, p[3]) for p in px]
    solid = [p for p in px if p[3] >= 16]
    if not solid:
        return None
    n = len(solid)
    # The median, per Lab channel: the color most of the face is. An average lets a few
    # light specks pull dirt halfway to jungle planks; the median keeps it dark brown.
    labs = [srgb_to_lab(*p[:3]) for p in solid]
    color = tuple(statistics.median(lab[i] for lab in labs) for i in range(3))
    # Lightness per pixel (transparent ones take the average, so holes add no false detail).
    light = [srgb_to_lab(*p[:3])[0] if p[3] >= 16 else None for p in px]
    mean = sum(v for v in light if v is not None) / n
    grid = [[(light[y * w + x] if light[y * w + x] is not None else mean) for x in range(w)] for y in range(h)]
    var = sum((v - mean) ** 2 for row in grid for v in row) / (w * h)
    detail = sum(abs(grid[y][x] - grid[y][(x + 1) % w]) + abs(grid[y][x] - grid[(y + 1) % h][x])
                 for y in range(h) for x in range(w)) / (2 * w * h)
    rows = [sum(r) / w for r in grid]
    cols = [sum(grid[y][x] for y in range(h)) / h for x in range(w)]
    lined = sum((v - mean) ** 2 for v in rows) / h + sum((v - mean) ** 2 for v in cols) / w
    structure = lined / var if var > 1e-6 else 0.0
    return color, (math.sqrt(var), detail, structure)


def texture_distance(a, b):
    return math.sqrt((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + ((a[2] - b[2]) * STRUCTURE_WEIGHT) ** 2)


def closest(material, feat, candidates):
    """The candidate that looks most like the material: best color, texture among the near ones."""
    scored = [(ciede2000(feat[material][0], feat[c][0]), c) for c in candidates]
    best = min(d for d, _ in scored)
    near = [c for d, c in scored if d <= best + COLOR_BAND]
    return min(near, key=lambda c: (texture_distance(feat[material][1], feat[c][1]),
                                    ciede2000(feat[material][0], feat[c][0])))


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    jar = Jar(sys.argv[1])
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

    feat = {}
    for material in rows:
        tex = texture_of(jar, material)
        f = features(*tex) if tex else None
        if f:
            feat[material] = f
        else:
            print(f"no texture found for {material}, skipping it", file=sys.stderr)

    substitutions = {}
    for material, shapes in rows.items():
        if material not in feat:
            continue
        entry = {}
        for shape in SHAPES:
            if shapes.get(shape) is not None:
                continue
            # Candidates: materials that have this variant of their own. For a full block, only
            # real building materials (families, wool), not a lone crafting table or furnace.
            candidates = [c for c, cs in rows.items() if c != material and c in feat and cs.get(shape)
                          and (shape != "block" or sum(v is not None for v in cs.values()) > 1)]
            if candidates:
                entry[shape] = rows[closest(material, feat, candidates)][shape]
        if entry:
            substitutions[material] = entry

    out = repo / "src/main/resources/assets/shulkerpalette/data/shape_gap_fill.json"
    out.write_text(json.dumps(substitutions, indent=1, sort_keys=True) + "\n")
    total = sum(len(v) for v in substitutions.values())
    print(f"{out}: {len(substitutions)} materials, {total} gap entries")


if __name__ == "__main__":
    main()
