package mnw;

/** Simule un TP : le joueur arrive en (X,Z), on genere uniquement son rayon de vue. */
public final class Teleport {
    static double run(String label, long seed, long X, long Z, int viewDist, boolean quiet) {
        ProcWorld w = new ProcWorld(seed);
        ChunkDecoder dec = new ChunkDecoder();
        int[] chunk = Blocks.newChunk();
        long ccx = Math.floorDiv(X, 16), ccz = Math.floorDiv(Z, 16);
        int n = 0; long sink = 0;

        long t0 = System.nanoTime();
        for (long wcx = ccx - viewDist; wcx <= ccx + viewDist; wcx++)
            for (long wcz = ccz - viewDist; wcz <= ccz + viewDist; wcz++) {
                int px = (int) Math.floorDiv(wcx, 4), pz = (int) Math.floorDiv(wcz, 4);
                dec.decode(w.patchAt(px, pz), (int) Math.floorMod(wcx, 4), (int) Math.floorMod(wcz, 4),
                        (long) px * Patch.SIZE, (long) pz * Patch.SIZE, chunk);
                sink += chunk[50000]; n++;
            }
        double ms = (System.nanoTime() - t0) / 1e6;
        if (!quiet)
            System.out.printf("  %-26s %6d chunks | %4d patches | %7.1f ms total | %.3f ms/chunk%s%n",
                label, n, w.generated.get(), ms, ms / n, sink == Long.MIN_VALUE ? "!" : "");
        return ms / n;
    }

    public static void main(String[] args) {
        int vd = 12;
        System.out.println("Warmup JIT...");
        for (int i = 0; i < 3; i++) run("", 42L, 5000 * i, 7000 * i, vd, true);

        System.out.printf("%nTP avec view-distance %d (%d chunks charges)%n", vd, (2*vd+1)*(2*vd+1));
        double a = run("spawn (0, 0)",              42L, 0, 0, vd, false);
        double b = run("(100 000, 100 000)",        42L, 100_000, 100_000, vd, false);
        double c = run("(1 000 000, 1 000 000)",    42L, 1_000_000, 1_000_000, vd, false);
        double d = run("(29 999 984, -12 345 678)", 42L, 29_999_984, -12_345_678, vd, false);

        // baseline vanilla sur le meme nombre de chunks
        VanillaBaseline van = new VanillaBaseline(42L);
        int[] chunk = Blocks.newChunk(); long sink = 0;
        for (int i = 0; i < 200; i++) van.generate(i, i, chunk);
        int N = (2*vd+1)*(2*vd+1);
        long t0 = System.nanoTime();
        for (int i = 0; i < N; i++) { van.generate(i & 63, i >> 6, chunk); sink += chunk[50000]; }
        double vms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("  %-26s %6d chunks | %4s         | %7.1f ms total | %.3f ms/chunk%s%n",
            "vanilla (profil de cout)", N, "-", vms, vms / N, sink == Long.MIN_VALUE ? "!" : "");

        double best = Math.max(Math.max(a, b), Math.max(c, d));
        System.out.printf("%n  cout stable jusqu'a 30M blocs (ecart max %.1f%%)%n", 100 * (best / a - 1));
        System.out.printf("  gain vs vanilla : %.1fx  |  TP complet : %.2f s -> %.2f s (1 coeur)%n",
            (vms / N) / c, vms / 1000, c * N / 1000);
        System.out.printf("  estimation Xeon partage (x3) : vanilla %.2f s -> MineGen %.2f s%n",
            3 * vms / 1000, 3 * c * N / 1000);
    }
}
