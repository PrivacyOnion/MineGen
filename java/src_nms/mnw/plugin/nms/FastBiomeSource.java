package mnw.plugin.nms;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBiome;
import org.bukkit.craftbukkit.generator.CustomChunkGenerator;
import org.bukkit.craftbukkit.generator.CustomWorldChunkManager;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

/**
 * Supprime le calcul climatique de vanilla que Paper effectue et jette.
 *
 * <p>Quand un plugin fournit un BiomeProvider, Paper enveloppe celui-ci dans
 * {@code CustomWorldChunkManager}, dont {@code getNoiseBiome} fait :</p>
 *
 * <pre>
 *   biomeProvider.getBiome(worldInfo, x &lt;&lt; 2, y &lt;&lt; 2, z &lt;&lt; 2,
 *       CraftBiomeParameterPoint.createBiomeParameterPoint(noise, noise.sample(x, y, z)));
 * </pre>
 *
 * <p>{@code noise.sample(x, y, z)} evalue les sept fonctions de densite de vanilla,
 * splines cubiques et Perlin compris, pour construire un argument. MineGen surcharge
 * la variante a quatre arguments et ne regarde jamais cet argument : le calcul est
 * integralement jete.</p>
 *
 * <p>Profil spark Ur8s8qaK55, thread Paper Common Worker sur 59 884 ms :</p>
 *
 * <pre>
 *   generateBiomes -&gt; Climate$Sampler.sample   46 120 ms   77,0 %
 *   generateFeatures (decoration vanilla)       4 592 ms    7,7 %
 *   MineGenChunkGenerator.generateNoise         1 672 ms    2,8 %
 * </pre>
 *
 * <p>Cette classe herite de {@code CustomWorldChunkManager} et ne redefinit que
 * {@code getNoiseBiome}, sans appeler le sampler. Tout le reste -- codec, liste des
 * biomes possibles, source vanilla de secours -- reste celui de Paper.</p>
 *
 * <p>Le resultat est identique par construction : les deux chemins finissent sur
 * {@code provider.getBiome(info, x&lt;&lt;2, y&lt;&lt;2, z&lt;&lt;2)}, la surcharge a quatre
 * arguments qu'implemente MineGen. La variante a cinq arguments de l'API delegue a
 * celle-la par defaut ; seul le parametre jete disparait.</p>
 */
public final class FastBiomeSource extends CustomWorldChunkManager {

    private final WorldInfo info;
    private final BiomeProvider provider;

    public FastBiomeSource(WorldInfo info, BiomeProvider provider,
                           Registry<net.minecraft.world.level.biome.Biome> registry,
                           BiomeSource vanilla) {
        super(info, provider, registry, vanilla);
        this.info = info;
        this.provider = provider;
    }

    private static final boolean VERIFY = Boolean.getBoolean("minegen.verifybiome");
    public static final java.util.concurrent.atomic.AtomicLong CHECKED  = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong MISMATCH = new java.util.concurrent.atomic.AtomicLong();

    @Override
    public Holder<net.minecraft.world.level.biome.Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        Holder<net.minecraft.world.level.biome.Biome> fast =
            CraftBiome.bukkitToMinecraftHolder(this.provider.getBiome(this.info, x << 2, y << 2, z << 2));
        // -Dminegen.verifybiome=true : on refait aussi le chemin de Paper, celui qui
        // calcule le climat vanilla, et on compare. C'est la preuve que debrancher le
        // sampler ne change pas le monde. Deux fois le cout, donc jamais en production.
        if (VERIFY) {
            Holder<net.minecraft.world.level.biome.Biome> slow = super.getNoiseBiome(x, y, z, sampler);
            CHECKED.incrementAndGet();
            if (slow != fast && !slow.equals(fast)) MISMATCH.incrementAndGet();
        }
        return fast;
    }

    // ---------------------------------------------------------------- installation

    /**
     * Remplace la source de biomes du monde. Rend une ligne de journal decrivant ce
     * qui a ete fait, ou leve : l'appelant journalise et continue sans, auquel cas
     * MineGen reste fonctionnel et seulement plus lent.
     */
    public static String install(org.bukkit.World world, BiomeProvider provider, WorldInfo info) throws Exception {
        ServerLevel level = ((CraftWorld) world).getHandle();
        net.minecraft.world.level.chunk.ChunkGenerator gen = level.getChunkSource().getGenerator();
        BiomeSource current = gen.getBiomeSource();

        // Garde-fou : on ne remplace que notre propre enveloppe. Si le monde utilise
        // une autre source, on n'y touche pas -- on ne sait pas ce qu'elle fait.
        if (current instanceof FastBiomeSource) return "deja active";
        if (!(current instanceof CustomWorldChunkManager wrapper))
            throw new IllegalStateException("source de biomes inattendue : " + current.getClass().getName());

        Registry<net.minecraft.world.level.biome.Biome> registry =
            level.registryAccess().lookupOrThrow(Registries.BIOME);
        FastBiomeSource fast = new FastBiomeSource(info, provider, registry, wrapper.vanillaBiomeSource);

        // ChunkGenerator.biomeSource est protected final : l'ecriture passe par Unsafe.
        // Deux instances portent le champ -- le generateur de Paper et le generateur
        // vanilla qu'il delegue -- et les deux sont lues a des endroits differents
        // (createBiomes lit le sien, le placement de structures lit celui du delegue).
        int n = 0;
        n += set(gen, fast) ? 1 : 0;
        if (gen instanceof CustomChunkGenerator ccg) n += set(ccg.getDelegate(), fast) ? 1 : 0;
        if (n == 0) throw new IllegalStateException("aucun champ biomeSource ecrit");
        return n + " generateur(s) rebranche(s)";
    }

    private static boolean set(Object target, BiomeSource value) throws Exception {
        if (target == null) return false;
        java.lang.reflect.Field f =
            net.minecraft.world.level.chunk.ChunkGenerator.class.getDeclaredField("biomeSource");
        sun.misc.Unsafe u = unsafe();
        u.putObject(target, u.objectFieldOffset(f), value);
        return true;
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }
}
