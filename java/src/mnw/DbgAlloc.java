package mnw;
import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;

/** Combien d'octets MineGen alloue par chunk : c'est le taux d'allocation qui
 *  fixe la frequence des pauses GC, pas le temps CPU. */
public class DbgAlloc {
    public static void main(String[] a) throws Exception {
        ThreadMXBean tb = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().getId();
        Terrain.vanilla(20260909L);
        ChunkDecoder d = new ChunkDecoder();
        int[] c = Blocks.newChunk();

        for (int warm = 0; warm < 2; warm++) {
            ProcWorld w = new ProcWorld(20260909L);
            long t0 = System.nanoTime(), a0 = tb.getThreadAllocatedBytes(id);
            int n = 0;
            for (int cx = 0; cx < 64; cx++) for (int cz = 0; cz < 64; cz++) {
                int px = Math.floorDiv(cx, 4), pz = Math.floorDiv(cz, 4);
                d.decode(w.patchAt(px, pz), Math.floorMod(cx, 4), Math.floorMod(cz, 4),
                         (long) px * Patch.SIZE, (long) pz * Patch.SIZE, c);
                n++;
            }
            long da = tb.getThreadAllocatedBytes(id) - a0;
            double ms = (System.nanoTime() - t0) / 1e6;
            if (warm == 1)
                System.out.printf("%d chunks : %.2f Mo alloues, %.1f Ko/chunk, %.2f ms/chunk%n"
                    + "  a 23 chunks/s (le debit mesure sur la box) -> %.1f Mo/s de dechets%n",
                    n, da / 1048576.0, da / 1024.0 / n, ms / n, da / 1048576.0 / n * 23);
        }
    }
}
