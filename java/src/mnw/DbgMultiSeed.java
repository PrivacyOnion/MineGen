package mnw;
/** Deux mondes de graines differentes dans la meme JVM ne doivent pas se
 *  contaminer. On genere une reference pour chaque graine seule, dans un
 *  processus dedie, puis on refait les deux cote a cote et on compare. */
public class DbgMultiSeed {
    static long hash(long seed, int cx, int cz, ChunkDecoder d, ProcWorld w) {
        int[] c = Blocks.newChunk();
        int px = Math.floorDiv(cx, 4), pz = Math.floorDiv(cz, 4);
        d.decode(w.patchAt(px, pz), Math.floorMod(cx, 4), Math.floorMod(cz, 4),
                 (long) px * Patch.SIZE, (long) pz * Patch.SIZE, c);
        long h = 0xcbf29ce484222325L;
        for (int v : c) h = (h ^ v) * 0x100000001b3L;
        return h;
    }
    static long run(long seed) {
        Terrain.Tables t = Terrain.tables(seed);
        ProcWorld w = new ProcWorld(t);
        ChunkDecoder d = new ChunkDecoder(t);
        long h = 0xcbf29ce484222325L;
        for (int cx = 1000; cx < 1006; cx++)
            for (int cz = 1000; cz < 1006; cz++)
                h = (h ^ hash(seed, cx, cz, d, w)) * 0x100000001b3L;
        return h;
    }
    public static void main(String[] a) {
        if (a.length == 1) {                       // reference : une seule graine
            System.out.printf("%016x%n", run(Long.parseLong(a[0])));
            return;
        }
        long[] seeds = {20260909L, 42L, 777777L};
        long[] h = new long[seeds.length];
        for (int i = 0; i < seeds.length; i++) h[i] = run(seeds[i]);
        System.out.printf("%d monde(s) charge(s) dans la meme JVM%n", Terrain.worlds());
        for (int i = 0; i < seeds.length; i++)
            System.out.printf("  graine %-10d -> %016x%n", seeds[i], h[i]);
        // Les trois doivent differer entre elles : sinon les mondes se confondent.
        boolean distinct = h[0] != h[1] && h[1] != h[2] && h[0] != h[2];
        System.out.println(distinct ? "  les trois mondes sont bien distincts"
                                    : "  ECHEC : deux mondes identiques");
        // Et regenerer apres coup doit redonner la meme chose.
        for (int i = 0; i < seeds.length; i++)
            if (run(seeds[i]) != h[i]) { System.out.println("  ECHEC : instable a la relecture"); return; }
        System.out.println("  stables a la relecture");
    }
}
