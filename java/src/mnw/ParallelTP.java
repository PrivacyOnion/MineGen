package mnw;
import java.util.concurrent.*;

/** Meme TP, reparti sur N threads. La cible MyBox a 2 coeurs ; Paper charge deja
 *  les chunks sur un pool asynchrone, donc c'est le mode realiste. */
public final class ParallelTP {
    static double run(long seed, long X, long Z, int vd, int threads) throws Exception {
        ProcWorld w = new ProcWorld(seed);
        long ccx = Math.floorDiv(X, 16), ccz = Math.floorDiv(Z, 16);
        int side = 2 * vd + 1;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        long t0 = System.nanoTime();
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final int id = t;
            ex.submit(() -> {
                ChunkDecoder dec = new ChunkDecoder();
                int[] chunk = Blocks.newChunk();
                long sink = 0;
                // bandes de colonnes de chunks : chaque thread reste sur ses patches
                for (int i = 0; i < side * side; i++) {
                    int col = i / side;
                    if ((col >> 2) % threads != id) continue;
                    long wcx = ccx - vd + col, wcz = ccz - vd + i % side;
                    int px = (int) Math.floorDiv(wcx, 4), pz = (int) Math.floorDiv(wcz, 4);
                    dec.decode(w.patchAt(px, pz), (int) Math.floorMod(wcx, 4), (int) Math.floorMod(wcz, 4),
                               (long) px * Patch.SIZE, (long) pz * Patch.SIZE, chunk);
                    sink += chunk[50000];
                }
                if (sink == Long.MIN_VALUE) System.out.print("");
                done.countDown();
            });
        }
        done.await();
        double ms = (System.nanoTime() - t0) / 1e6;
        ex.shutdown();
        return ms;
    }

    public static void main(String[] a) throws Exception {
        int vd = 12, n = (2*vd+1)*(2*vd+1);
        for (int i = 0; i < 4; i++) run(42L, i * 5000, 0, vd, 2);   // warmup
        System.out.printf("TP a (1 000 000, 1 000 000), view-distance %d, %d chunks%n%n", vd, n);
        double t1 = 0;
        for (int th : new int[]{1, 2, 4, 6}) {
            double best = Double.MAX_VALUE;
            for (int r = 0; r < 3; r++) best = Math.min(best, run(42L, 1_000_000 + r*100_000, 1_000_000, vd, th));
            if (th == 1) t1 = best;
            System.out.printf("  %d thread%s : %6.1f ms  (%.3f ms/chunk)   acceleration %.2fx%n",
                th, th > 1 ? "s" : " ", best, best / n, t1 / best);
        }
        System.out.printf("%n  MyBox (2 coeurs Xeon, derating x3) : TP en ~%.2f s%n", 3 * 0.001 *
            run(42L, 1_000_000, 1_000_000, vd, 2));
    }
}
