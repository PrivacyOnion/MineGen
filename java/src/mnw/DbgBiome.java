package mnw;
public final class DbgBiome {
    public static void main(String[] a) throws Exception {
        BiomeTable bt = new BiomeTable(Res.open("vanilla_biomes.bin"));
        System.out.printf("%d biomes, %d boites, %d noeuds BVH%n", bt.names.length, bt.entries(), bt.nodes());
        Terrain.vanilla(42L);
        VanillaTerrain vt = new VanillaTerrain(42L);
        float[] clim = new float[6], sp = new float[4], q = new float[7];
        BiomeTable.Cursor cur = new BiomeTable.Cursor();
        int N = 400, STEP = 96; int[] hist = new int[bt.names.length];
        long t0 = System.nanoTime();
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++) {
                vt.column((i - N/2.0) * STEP, (j - N/2.0) * STEP, clim, sp);
                System.arraycopy(clim, 0, q, 0, 6); q[6] = 0;
                hist[bt.find(q, cur)]++;
            }
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("zone %d x %d blocs | %d colonnes en %.0f ms = %.2f us/colonne%n", N*STEP, N*STEP, N*N, ms, ms*1000/(N*(double)N));
        Integer[] o = new Integer[hist.length];
        for (int i = 0; i < o.length; i++) o[i] = i;
        java.util.Arrays.sort(o, (x, y) -> hist[y] - hist[x]);
        System.out.println("biomes rencontres :");
        for (int i = 0; i < 20 && hist[o[i]] > 0; i++)
            System.out.printf("  %-24s %5.2f %%%n", bt.names[o[i]], 100.0 * hist[o[i]] / (N*N));
        int nz = 0; for (int h : hist) if (h > 0) nz++;
        System.out.println("  ... " + nz + " biomes distincts au total");
    }
}
