package mnw;

/**
 * Verifie l'invariant sur lequel repose le disk saver de MyBoxPaper : un chunk
 * que personne n'a modifie se regenere a l'identique.
 *
 * Trois choses peuvent le casser, et le test les couvre toutes les trois :
 * l'ordre de generation, l'etat accumule dans le cache de patches, et le
 * redemarrage de la JVM (lancer deux fois et comparer les fichiers).
 *
 *   usage : DbgDeterminism <seed> <rayon en chunks> <ordre: lineaire|melange|parallele> <sortie>
 */
public class DbgDeterminism {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        int r = Integer.parseInt(a[1]);
        boolean shuffle = a[2].startsWith("m"), parallel = a[2].startsWith("p");
        Terrain.vanilla(seed);
        ProcWorld w = new ProcWorld(seed);
        ChunkDecoder d = new ChunkDecoder();
        int[] buf = Blocks.newChunk();

        int c0 = 62500;
        java.util.List<int[]> coords = new java.util.ArrayList<>();
        for (int cx = c0; cx < c0 + r; cx++)
            for (int cz = c0; cz < c0 + r; cz++) coords.add(new int[]{cx, cz});
        if (shuffle) java.util.Collections.shuffle(coords, new java.util.Random(12345));

        java.util.Map<String, String> out = new java.util.concurrent.ConcurrentSkipListMap<>();
        if (parallel) {
            // La vraie condition de production : Paper genere sur ses threads worker.
            java.util.List<Thread> ts = new java.util.ArrayList<>();
            java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
            for (int t = 0; t < 6; t++) {
                Thread th = new Thread(() -> {
                    ChunkDecoder dd = new ChunkDecoder();
                    int[] bb = Blocks.newChunk();
                    for (int i; (i = next.getAndIncrement()) < coords.size(); ) hash(coords.get(i), w, dd, bb, out);
                });
                th.start(); ts.add(th);
            }
            for (Thread th : ts) th.join();
        } else {
            for (int[] c : coords) hash(c, w, d, buf, out);
        }
        try (java.io.PrintWriter p = new java.io.PrintWriter(a[3])) {
            out.forEach((k, v) -> p.println(k + " " + v));
        }
        System.out.printf("%d chunks, ordre %s -> %s%n", out.size(), a[2], a[3]);
    }

    private static void hash(int[] c, ProcWorld w, ChunkDecoder d, int[] buf,
                             java.util.Map<String, String> out) {
        int px = Math.floorDiv(c[0], 4), pz = Math.floorDiv(c[1], 4);
        d.decode(w.patchAt(px, pz), Math.floorMod(c[0], 4), Math.floorMod(c[1], 4),
                 (long) px * Patch.SIZE, (long) pz * Patch.SIZE, buf);
        long h = 1469598103934665603L;
        for (int v : buf) h = (h ^ v) * 1099511628211L;
        out.put(c[0] + "," + c[1], Long.toHexString(h));
    }
}
