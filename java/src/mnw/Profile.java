package mnw;

/** Repartition du cout : ou passent les 0,39 ms par chunk. */
public final class Profile {
    public static void main(String[] a) throws Exception {
        long seed = 42L;
        Terrain.vanilla(seed);
        VanillaTerrain vt = Terrain.vanilla(seed);
        BiomeTable bt = Terrain.biomes();
        int[] chunk = Blocks.newChunk();
        ChunkDecoder dec = new ChunkDecoder();

        for (int i = 0; i < 40; i++) { Patch p = Terrain.patch(seed, i*64, 0); dec.decode(p, 0, 0, i*64, 0, chunk); }

        int N = 400;
        float[] clim = new float[6], sp = new float[4], q = new float[7];
        BiomeTable.Cursor cur = new BiomeTable.Cursor();
        long t;

        t = System.nanoTime();
        for (int i = 0; i < N; i++) for (int gx = 0; gx < Patch.H_N; gx++) for (int gz = 0; gz < Patch.H_N; gz++)
            vt.column(1000000 + i*64 + gx*4, 1000000 + gz*4, clim, sp);
        double climMs = (System.nanoTime()-t)/1e6/N;

        t = System.nanoTime();
        for (int i = 0; i < N; i++) for (int gx = 0; gx < Patch.H_N; gx++) for (int gz = 0; gz < Patch.H_N; gz++) {
            vt.column(1000000 + i*64 + gx*4, 1000000 + gz*4, clim, sp);
            System.arraycopy(clim,0,q,0,6); bt.find(q, cur);
        }
        double climBioMs = (System.nanoTime()-t)/1e6/N;

        t = System.nanoTime();
        for (int i = 0; i < N; i++) Carver.collect(seed, 1000000 + i*64, 1000000);
        double wormMs = (System.nanoTime()-t)/1e6/N;

        t = System.nanoTime();
        for (int i = 0; i < N; i++) Terrain.patch(seed, 1000000 + i*64, 1000000);
        double patchMs = (System.nanoTime()-t)/1e6/N;

        Patch p = Terrain.patch(seed, 1000000, 1000000);
        t = System.nanoTime();
        for (int i = 0; i < N*16; i++) dec.decode(p, i & 3, (i>>2) & 3, 1000000, 1000000, chunk);
        double decMs = (System.nanoTime()-t)/1e6/(N*16);

        double caveMs = patchMs - climBioMs - wormMs;
        System.out.println("Par PATCH (4x4 = 16 chunks) :");
        System.out.printf("  climat + hauteur (17x17=289 col)  %6.3f ms%n", climMs);
        System.out.printf("  + recherche de biome              %6.3f ms  (+%.3f)%n", climBioMs, climBioMs-climMs);
        System.out.printf("  champ de caves (17x17x49)         %6.3f ms%n", caveMs);
        System.out.printf("  collecte des tunnels              %6.3f ms%n", wormMs);
        System.out.printf("  TOTAL patch                       %6.3f ms  (%.3f ms/chunk)%n", patchMs, patchMs/16);
        System.out.println("Par CHUNK :");
        System.out.printf("  decodage (remplissage + carve)    %6.3f ms%n", decMs);
        System.out.printf("  TOTAL                             %6.3f ms%n", patchMs/16 + decMs);
        System.out.printf("%n  part generation latent : %.0f %%  |  part decodage : %.0f %%%n",
            100*(patchMs/16)/(patchMs/16+decMs), 100*decMs/(patchMs/16+decMs));

        System.out.println(System.lineSeparator() + "Decodage, par ablation de phase :");
        String[] nm = {"remplissage seul", "+ surface rules", "+ champ de caves", "+ creusement"};
        int[] masks = {1, 3, 7, 15};
        double prev = 0;
        for (int i = 0; i < masks.length; i++) {
            ChunkDecoder.PHASES = masks[i];
            for (int k = 0; k < 200; k++) dec.decode(p, k & 3, (k >> 2) & 3, 1000000, 1000000, chunk);
            long t2 = System.nanoTime();
            for (int k = 0; k < N * 16; k++) dec.decode(p, k & 3, (k >> 2) & 3, 1000000, 1000000, chunk);
            double ms = (System.nanoTime() - t2) / 1e6 / (N * 16);
            System.out.printf("  %-20s %6.3f ms/chunk   (+%.3f)%n", nm[i], ms, ms - prev);
            prev = ms;
        }
        ChunkDecoder.PHASES = 0xF;
    }
}
