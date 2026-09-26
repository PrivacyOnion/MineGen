package mnw;
/** Simule un joueur qui voyage : verifie que le cache de patches reste borne. */
public class DbgCache {
    public static void main(String[] a) {
        Terrain.vanilla(20260909L);
        ProcWorld w = new ProcWorld(20260909L);
        Runtime r = Runtime.getRuntime();
        System.out.printf("plafond : %d patches%n", ProcWorld.MAX_PATCHES);
        for (int step = 1; step <= 5; step++) {
            for (int i = 0; i < 12000; i++) {          // trajet en ligne, 12 000 chunks
                int cx = (step - 1) * 12000 + i, cz = (i * 7) & 63;
                w.patchAt(Math.floorDiv(cx, 4), Math.floorDiv(cz, 4));
            }
            System.gc();
            System.out.printf("%6d chunks : %5d patches en cache, %4d generes, %4d evinces, tas %4d Mo%n",
                step * 12000, w.cached(), w.generated.get(), w.evicted.get(),
                (r.totalMemory() - r.freeMemory()) >> 20);
        }
    }
}
