"""Aplatit les surface rules de Minecraft 1.21.11 en table binaire.

Format :
  i32 nBlocks, puis noms (u8 len + utf8)
  i32 nBiomeSets, puis par ensemble : u16 n, puis n x u16 (index de biome)
  i32 nNoises, puis par bruit : u8 nameLen+utf8, i32 firstOctave, u8 nAmp, nAmp x f32
  i32 root, i32 nNodes, puis par noeud : u8 type + charge utile
"""
import json, struct

SR = json.load(open("vanilla/data/minecraft/worldgen/noise_settings/overworld.json"))["surface_rule"]

# --- biomes : index dans notre table vanilla_biomes.bin ---
def load_biome_names():
    b = open("java/res/vanilla_biomes.bin", "rb").read(); o = 0
    n = struct.unpack_from(">i", b, o)[0]; o += 4
    out = []
    for _ in range(n):
        ln = b[o]; o += 1; out.append(b[o:o+ln].decode()); o += ln
    return out
BIOMES = load_biome_names()
BIDX = {n: i for i, n in enumerate(BIOMES)}

blocks, block_idx = [], {}
def blk(name):
    if name not in block_idx:
        block_idx[name] = len(blocks); blocks.append(name)
    return block_idx[name]

bsets, bset_idx = [], {}
def bset(names):
    key = tuple(sorted(names))
    if key not in bset_idx:
        ids = [BIDX[n.replace("minecraft:", "")] for n in key if n.replace("minecraft:", "") in BIDX]
        bset_idx[key] = len(bsets); bsets.append(ids)
    return bset_idx[key]

noises, noise_idx = [], {}
def noi(name):
    short = name.replace("minecraft:", "")
    if short not in noise_idx:
        d = json.load(open(f"vanilla/data/minecraft/worldgen/noise/{short}.json"))
        noise_idx[short] = len(noises); noises.append((short, d["firstOctave"], d["amplitudes"]))
    return noise_idx[short]

MIN_Y, HEIGHT = -64, 384
def anchor(a):
    if "absolute" in a: return int(a["absolute"])
    if "above_bottom" in a: return MIN_Y + int(a["above_bottom"])
    return MIN_Y + HEIGHT - 1 - int(a["below_top"])

nodes = []
def emit(payload):
    nodes.append(payload); return len(nodes) - 1

T = {"SEQ":1, "COND":2, "BLOCK":3, "BANDS_RULE":4, "BIOME":10, "NOISE":11, "STONE_DEPTH":12, "WATER":13,
     "Y_ABOVE":14, "STEEP":15, "NOT":16, "HOLE":17, "VGRAD":18, "BANDS":19, "ABOVE_PS":20, "TEMP":21}

def rule(n):
    t = n["type"]
    if t == "minecraft:sequence":
        kids = [rule(k) for k in n["sequence"]]
        return emit(struct.pack(">BB", T["SEQ"], len(kids)) + b"".join(struct.pack(">i", k) for k in kids))
    if t == "minecraft:condition":
        c = cond(n["if_true"]); r = rule(n["then_run"])
        return emit(struct.pack(">Bii", T["COND"], c, r))
    if t == "minecraft:block":
        return emit(struct.pack(">BH", T["BLOCK"], blk(n["result_state"]["Name"])))
    if t == "minecraft:bandlands":
        # regle, pas condition : renvoie la bande de terracotta correspondant a l'altitude
        for nm in ["minecraft:terracotta", "minecraft:white_terracotta", "minecraft:orange_terracotta",
                   "minecraft:yellow_terracotta", "minecraft:brown_terracotta", "minecraft:red_terracotta",
                   "minecraft:light_gray_terracotta"]:
            blk(nm)
        return emit(struct.pack(">B", T["BANDS_RULE"]))
    raise ValueError(t)

def cond(n):
    t = n["type"]
    if t == "minecraft:biome":   return emit(struct.pack(">BH", T["BIOME"], bset(n["biome_is"])))
    if t == "minecraft:noise_threshold":
        # vanilla ecrit les bornes ouvertes comme des doubles infinis : on sature en float
        def f32(v):
            v = float(v)
            return max(-3.0e38, min(3.0e38, v))
        return emit(struct.pack(">BBff", T["NOISE"], noi(n["noise"]),
                                f32(n["min_threshold"]), f32(n["max_threshold"])))
    if t == "minecraft:stone_depth":
        return emit(struct.pack(">BBiBi", T["STONE_DEPTH"], 1 if n["surface_type"] == "ceiling" else 0,
                                int(n["offset"]), 1 if n["add_surface_depth"] else 0,
                                int(n["secondary_depth_range"])))
    if t == "minecraft:water":
        return emit(struct.pack(">BiiB", T["WATER"], int(n["offset"]),
                                int(n["surface_depth_multiplier"]), 1 if n["add_stone_depth"] else 0))
    if t == "minecraft:y_above":
        return emit(struct.pack(">BiiB", T["Y_ABOVE"], anchor(n["anchor"]),
                                int(n["surface_depth_multiplier"]), 1 if n["add_stone_depth"] else 0))
    if t == "minecraft:not":     return emit(struct.pack(">Bi", T["NOT"], cond(n["invert"])))
    if t == "minecraft:vertical_gradient":
        return emit(struct.pack(">Bii", T["VGRAD"], anchor(n["true_at_and_below"]),
                                anchor(n["false_at_and_above"])))
    simple = {"minecraft:steep":"STEEP", "minecraft:hole":"HOLE", "minecraft:bandlands":"BANDS",
              "minecraft:above_preliminary_surface":"ABOVE_PS", "minecraft:temperature":"TEMP"}
    if t in simple: return emit(struct.pack(">B", T[simple[t]]))
    raise ValueError(t)

root = rule(SR)
noi("minecraft:surface_secondary")   # utilise par secondary_depth_range

out = bytearray()
out += struct.pack(">i", len(blocks))
for b in blocks:
    e = b.replace("minecraft:", "").encode(); out += struct.pack(">B", len(e)) + e
out += struct.pack(">i", len(bsets))
for s in bsets:
    out += struct.pack(">H", len(s)) + b"".join(struct.pack(">H", i) for i in s)
out += struct.pack(">i", len(noises))
for name, fo, amps in noises:
    e = name.encode(); out += struct.pack(">B", len(e)) + e + struct.pack(">iB", fo, len(amps))
    out += b"".join(struct.pack(">f", float(a)) for a in amps)
out += struct.pack(">ii", root, len(nodes))
for p in nodes: out += p

open("java/res/vanilla_surface.bin", "wb").write(out)
print(f"{len(nodes)} noeuds, {len(blocks)} blocs, {len(bsets)} ensembles de biomes, "
      f"{len(noises)} bruits -> java/res/vanilla_surface.bin ({len(out)} o)")
print("blocs :", ", ".join(b.replace("minecraft:","") for b in blocks))
