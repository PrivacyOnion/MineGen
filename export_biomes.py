"""Exporte la table climatique de Minecraft 1.21.11 en binaire compact.

Source : sortie du data generator (`--reports`), biome_parameters/minecraft/overworld.json.
7593 boites a 6 dimensions (temperature, humidite, continentalite, erosion,
profondeur, weirdness) + un terme d'offset, chacune associee a un biome.

Format : i32 nBiomes, puis les noms (u8 len + utf8) ;
         i32 nEntries, puis par entree : u16 biomeIdx, 12 f32 (min,max x6), f32 offset.
"""
import json, struct, os, glob

# Point MINEGEN_BIOME_REPORT to the Minecraft data-generator report.
src = os.environ.get("MINEGEN_BIOME_REPORT", "vanilla/reports/biome_parameters/minecraft/overworld.json")
d = json.load(open(src))
entries = d["biomes"]

names = sorted({e["biome"] for e in entries})
idx = {n: i for i, n in enumerate(names)}
PARAMS = ["temperature", "humidity", "continentalness", "erosion", "depth", "weirdness"]

def rng(v):
    return (float(v), float(v)) if isinstance(v, (int, float)) else (float(v[0]), float(v[1]))

out = bytearray()
out += struct.pack(">i", len(names))
for n in names:
    b = n.replace("minecraft:", "").encode()
    out += struct.pack(">B", len(b)) + b
out += struct.pack(">i", len(entries))
for e in entries:
    p = e["parameters"]
    out += struct.pack(">H", idx[e["biome"]])
    for k in PARAMS:
        lo, hi = rng(p[k]); out += struct.pack(">ff", lo, hi)
    out += struct.pack(">f", float(p.get("offset", 0.0)))

open("java/res/vanilla_biomes.bin", "wb").write(out)
print(f"{len(names)} biomes, {len(entries)} boites -> java/res/vanilla_biomes.bin ({len(out)} o)")
print("\nbiomes :")
for i in range(0, len(names), 6):
    print("  " + "  ".join(f"{idx[n]:2d} {n.replace('minecraft:',''):<22s}" for n in names[i:i+6]))
