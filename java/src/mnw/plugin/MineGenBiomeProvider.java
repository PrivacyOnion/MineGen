package mnw.plugin;

import mnw.*;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Expose a Paper la carte de biomes issue de la table climatique vanilla
 * (7593 boites, 54 biomes). C'est elle qui pilote ensuite la decoration : les
 * arbres, l'herbe et les mobs poses par vanilla suivent ces biomes.
 *
 * L'attribution est en 3D, comme vanilla : le parametre `depth` vaut
 * (hauteur - y) / 128, ce qui fait apparaitre lush_caves, dripstone_caves et
 * deep_dark sous terre -- et donc les cites antiques que vanilla y place. Le
 * patch porte deja les quatre distances climatiques necessaires, si bien qu'une
 * requete ne coute que quatre comparaisons.
 */
public final class MineGenBiomeProvider extends BiomeProvider {
    private final MineGenChunkGenerator gen;
    private volatile Biome[] map;
    private volatile List<Biome> all;

    MineGenBiomeProvider(MineGenChunkGenerator gen) { this.gen = gen; }

    private void build(WorldInfo info) {
        if (map != null) return;
        gen.world(info);                       // force l'initialisation des tables
        String[] names = Terrain.biomes().names;
        Biome[] m = new Biome[names.length];
        List<Biome> list = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            Biome b = Registry.BIOME.get(NamespacedKey.minecraft(names[i]));
            m[i] = b != null ? b : Biome.PLAINS;
            if (!list.contains(m[i])) list.add(m[i]);
        }
        map = m; all = list;
    }

    @Override
    public Biome getBiome(WorldInfo info, int x, int y, int z) {
        build(info);
        ProcWorld w = gen.world(info);
        Patch p = w.patchAt(Math.floorDiv(x, Patch.SIZE), Math.floorDiv(z, Patch.SIZE));
        int gx = Math.floorMod(x, Patch.SIZE) >> 2, gz = Math.floorMod(z, Patch.SIZE) >> 2;
        int cell = gx * Patch.H_N + gz;
        float depth = (float) ((p.h[cell] / 16.0 - y) / 128.0);
        return map[Terrain.biomes().atDepth(p.s[cell] & 0xFF, p.bd, cell * Patch.BD, depth)];
    }

    @Override
    public List<Biome> getBiomes(WorldInfo info) {
        build(info);
        return all;
    }
}
