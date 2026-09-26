package mnw.plugin;

import mnw.*;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Branchement de MineGen dans Paper.
 *
 * On prend en charge le terrain, la surface, les caves et le socle ; on laisse a
 * vanilla la decoration (arbres, minerais), les structures et les mobs. C'est le
 * decoupage voulu : le gain vient du terrain, et les features vanilla restent
 * les vraies features vanilla.
 */
public final class MineGenChunkGenerator extends ChunkGenerator {
    private final Object lock = new Object();
    private volatile ProcWorld world;
    private volatile Material[] mat;
    private MineGenBiomeProvider biomeProvider;

    private volatile Terrain.Tables tables;
    private final ThreadLocal<ChunkDecoder> decoder =
        ThreadLocal.withInitial(() -> new ChunkDecoder(tables));
    private final ThreadLocal<int[]> buffer = ThreadLocal.withInitial(Blocks::newChunk);

    /** Instrumentation : ou part reellement le temps d'un chunk. Trois compteurs
     *  atomiques, incrementes une fois par chunk -- negligeable devant le travail
     *  mesure, et c'est la seule facon de savoir si le cout est dans MineGen ou
     *  dans le pont Bukkit. Lus par /mgbench stats. */
    public static final java.util.concurrent.atomic.AtomicLong T_DECODE = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong T_WRITE  = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong N_CHUNKS = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong N_BLOCKS = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong N_CALLS  = new java.util.concurrent.atomic.AtomicLong();

    /** Ecriture directe dans les sections NMS, chargee par reflexion.
     *
     *  Le pont Bukkit (ChunkData.setRegion) coute 4,34 ms par chunk contre
     *  0,30 ms pour l'algorithme entier : sa boucle interne rappelle setBlock
     *  par bloc, en allouant un BlockPos et en remettant a jour les heightmaps a
     *  chaque fois. On l'evite quand les classes du serveur repondent, et on y
     *  retombe sinon -- un serveur dont les classes different reste fonctionnel,
     *  seulement plus lent. */
    private static volatile Object fastWriter;
    private static volatile java.lang.reflect.Method fastWrite;
    private static volatile boolean fastTried;

    /**
     * Debranche le sampler climatique de vanilla, que Paper calcule et jette avant
     * chaque appel a notre BiomeProvider. Mesure sur profil spark : 77 % du thread
     * de generation. Chargee par reflexion, sans effet si les classes du serveur ne
     * repondent pas -- MineGen reste alors fonctionnel, seulement plus lent.
     */
    void installFastBiomes(org.bukkit.World world, java.util.logging.Logger log) {
        if (Boolean.getBoolean("minegen.nofastbiome")) {
            log.info("biomes rapides desactives par -Dminegen.nofastbiome");
            return;
        }
        try {
            Object bp = getDefaultBiomeProvider(world);
            Class<?> c = Class.forName("mnw.plugin.nms.FastBiomeSource");
            java.lang.reflect.Method m = c.getMethod("install", org.bukkit.World.class,
                    org.bukkit.generator.BiomeProvider.class, org.bukkit.generator.WorldInfo.class);
            Object r = m.invoke(null, world, bp, world);
            log.info("sampler climatique vanilla debranche (" + r + ")");
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite ? ite.getCause() : t;
            log.warning("biomes rapides indisponibles (" + cause + ") : Paper continuera de calculer "
                      + "le climat vanilla avant chaque biome, environ 77 % du temps de generation");
        }
    }

    private static void initFastWriter(String[] names, java.util.logging.Logger log) {
        if (fastTried) return;
        synchronized (MineGenChunkGenerator.class) {
            if (fastTried) return;
            fastTried = true;
            // -Dminegen.nofast=true force le chemin Bukkit : sert a verifier que les
            // deux chemins produisent le meme monde.
            if (Boolean.getBoolean("minegen.nofast")) {
                log.info("ecriture directe desactivee par -Dminegen.nofast");
                return;
            }
            try {
                Class<?> c = Class.forName("mnw.plugin.nms.DirectWriter");
                Object w = c.getConstructor(String[].class).newInstance((Object) names);
                java.lang.reflect.Method m = c.getMethod("write",
                        org.bukkit.generator.ChunkGenerator.ChunkData.class, int[].class);
                fastWrite = m; fastWriter = w;
                log.info("ecriture directe des sections active (contourne ChunkData.setRegion)");
            } catch (Throwable t) {
                log.warning("ecriture directe indisponible (" + t + ") : repli sur ChunkData.setRegion, "
                          + "environ 4 ms par chunk en plus");
            }
        }
    }

    ProcWorld world(WorldInfo info) {
        ProcWorld w = world;
        if (w != null) return w;
        synchronized (lock) {
            if (world == null) {
                // Une instance de generateur par monde : chaque monde porte donc ses
                // propres tables, et deux mondes de graines differentes coexistent.
                Terrain.Tables t = Terrain.tables(info.getSeed());
                tables = t;
                String[] names = Palette.names();
                Material[] m = new Material[names.length];
                for (int i = 0; i < names.length; i++) {
                    m[i] = Material.matchMaterial("minecraft:" + names[i]);
                    if (m[i] == null) m[i] = Material.STONE;
                }
                mat = m;
                initFastWriter(names, java.util.logging.Logger.getLogger("MineGen"));
                world = new ProcWorld(t);
            }
            return world;
        }
    }

    /**
     * Declare que ce generateur est une fonction pure de (graine, coordonnees monde).
     *
     * Un chunk que personne n'a modifie se regenere donc a l'identique : rien n'oblige a
     * l'ecrire sur le disque. MyBoxPaper cherche cette methode par reflexion pour laisser
     * son disk saver actif sur un monde a generateur de plugin, ou il se desactiverait
     * sinon par prudence. Aucune dependance entre les deux projets : c'est le nom de la
     * methode qui fait le contrat.
     *
     * Ce que la promesse engage, et qui est vrai ici : aucune dependance a l'ordre de
     * generation, aucun etat mutable partage hors du cache de patches (memoise, donc sans
     * effet sur le resultat), aucun tirage aleatoire non derive de la graine.
     */
    public boolean isDeterministicWorldgen() { return true; }

    @Override public boolean shouldGenerateNoise()       { return false; }
    @Override public boolean shouldGenerateSurface()     { return false; }
    @Override public boolean shouldGenerateCaves()       { return false; }
    @Override public boolean shouldGenerateBedrock()     { return false; }
    @Override public boolean shouldGenerateDecorations() { return true; }
    @Override public boolean shouldGenerateMobs()        { return true; }
    @Override public boolean shouldGenerateStructures()  { return true; }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        synchronized (lock) {
            if (biomeProvider == null) biomeProvider = new MineGenBiomeProvider(this);
            return biomeProvider;
        }
    }

    @Override
    public void generateNoise(WorldInfo info, Random rnd, int cx, int cz, ChunkData cd) {
        final long t0 = System.nanoTime();
        ProcWorld w = world(info);
        int[] chunk = buffer.get();
        int px = Math.floorDiv(cx, 4), pz = Math.floorDiv(cz, 4);
        decoder.get().decode(w.patchAt(px, pz), Math.floorMod(cx, 4), Math.floorMod(cz, 4),
                             (long) px * Patch.SIZE, (long) pz * Patch.SIZE, chunk);
        final long t1 = System.nanoTime();
        long blocks = 0, calls = 0;

        Object fw = fastWriter;
        if (fw != null) {
            try {
                blocks = (Integer) fastWrite.invoke(fw, cd, chunk);
                calls = 1;
                T_DECODE.addAndGet(t1 - t0);
                T_WRITE.addAndGet(System.nanoTime() - t1);
                N_CHUNKS.incrementAndGet();
                N_BLOCKS.addAndGet(blocks);
                N_CALLS.addAndGet(calls);
                return;
            } catch (Throwable t) {
                // Une seule tentative : si l'ecriture directe echoue une fois, elle
                // echouera toujours. On desactive et on finit ce chunk par le
                // chemin standard, sans perdre le chunk en cours.
                fastWriter = null;
            }
        }

        final int lo = Math.max(cd.getMinHeight(), Blocks.MIN_Y);
        final int hi = Math.min(cd.getMaxHeight(), Blocks.MIN_Y + Blocks.HEIGHT);
        final Material[] m = mat;
        final Material air = Material.AIR;

        // Ecriture par segments verticaux. Attention : setRegion N'EST PAS une
        // ecriture en bloc -- CraftChunkData l'implemente par une triple boucle
        // qui rappelle setBlock pour chaque bloc, en allouant un BlockPos et en
        // remettant a jour les heightmaps a chaque fois. Regrouper en segments ne
        // reduit donc que le nombre d'appels d'API, pas le travail sous-jacent.
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int runStart = lo;
                Material runMat = m[chunk[Blocks.idx(x, lo, z)]];
                for (int y = lo + 1; y < hi; y++) {
                    Material cur = m[chunk[Blocks.idx(x, y, z)]];
                    if (cur != runMat) {
                        if (runMat != air) { cd.setRegion(x, runStart, z, x + 1, y, z + 1, runMat); blocks += y - runStart; calls++; }
                        runStart = y; runMat = cur;
                    }
                }
                if (runMat != air) { cd.setRegion(x, runStart, z, x + 1, hi, z + 1, runMat); blocks += hi - runStart; calls++; }
            }

        T_DECODE.addAndGet(t1 - t0);
        T_WRITE.addAndGet(System.nanoTime() - t1);
        N_CHUNKS.incrementAndGet();
        N_BLOCKS.addAndGet(blocks);
        N_CALLS.addAndGet(calls);
    }

    @Override
    public int getBaseHeight(WorldInfo info, Random rnd, int x, int z, HeightMap map) {
        ProcWorld w = world(info);
        int px = Math.floorDiv(x, Patch.SIZE), pz = Math.floorDiv(z, Patch.SIZE);
        Patch p = w.patchAt(px, pz);
        return p.heightAt(Math.floorMod(x, Patch.SIZE), Math.floorMod(z, Patch.SIZE),
                          (long) px * Patch.SIZE, (long) pz * Patch.SIZE) + 1;
    }
}
