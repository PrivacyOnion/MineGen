"""Aplatit les arbres de splines vanilla (offset / factor / jaggedness) en table binaire.

Format : int32 nRoots, int32 rootIdx[3], int32 nNodes,
         puis par noeud : u8 coord, u8 nPoints, puis par point :
         f32 location, f32 derivative, u8 kind (0=constante,1=reference), f32|i32 valeur.
"""
import json, struct

V = "vanilla/data/minecraft/worldgen/density_function"
COORDS = ["minecraft:overworld/continents", "minecraft:overworld/erosion",
          "minecraft:overworld/ridges", "minecraft:overworld/ridges_folded"]
nodes = []

def flatten(sp):
    pts = sp["points"]
    node = {"coord": COORDS.index(sp["coordinate"]), "pts": []}
    idx = len(nodes); nodes.append(node)
    for p in pts:
        v = p["value"]
        if isinstance(v, dict):
            node["pts"].append((p["location"], p.get("derivative", 0.0), 1, flatten(v)))
        else:
            node["pts"].append((p["location"], p.get("derivative", 0.0), 0, float(v)))
    return idx

def find_spline(n):
    """Trouve le noeud spline racine dans un arbre de density function."""
    if isinstance(n, dict):
        if n.get("type") == "minecraft:spline": return n["spline"]
        for v in n.values():
            r = find_spline(v)
            if r is not None: return r
    elif isinstance(n, list):
        for v in n:
            r = find_spline(v)
            if r is not None: return r
    return None

roots = []
for f in ["overworld/offset", "overworld/factor", "overworld/jaggedness"]:
    sp = find_spline(json.load(open(f"{V}/{f}.json")))
    roots.append(flatten(sp))
    print(f"{f:24s} racine #{roots[-1]}")

out = bytearray()
out += struct.pack(">i", len(roots))
for r in roots: out += struct.pack(">i", r)
out += struct.pack(">i", len(nodes))
for nd in nodes:
    out += struct.pack(">BB", nd["coord"], len(nd["pts"]))
    for loc, der, kind, val in nd["pts"]:
        out += struct.pack(">ffB", loc, der, kind)
        out += struct.pack(">f", val) if kind == 0 else struct.pack(">i", val)
open("java/res/vanilla_splines.bin", "wb").write(out)
print(f"\n{len(nodes)} noeuds, {sum(len(n['pts']) for n in nodes)} points -> "
      f"java/res/vanilla_splines.bin ({len(out)} o)")
