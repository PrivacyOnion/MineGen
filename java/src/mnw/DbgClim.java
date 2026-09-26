package mnw;
public final class DbgClim {
    public static void main(String[] a) throws Exception {
        Terrain.vanilla(42L);
        VanillaTerrain vt = new VanillaTerrain(42L);
        float[] clim = new float[6], sp = new float[4];
        int N = 400, STEP = 96;
        String[] nm = {"temperature","humidite","continents","erosion","depth","weirdness"};
        double[][] v = new double[6][N*N];
        double[] hs = new double[N*N];
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++) {
                hs[i*N+j] = vt.column((i - N/2.0) * STEP, (j - N/2.0) * STEP, clim, sp);
                for (int k = 0; k < 6; k++) v[k][i*N+j] = clim[k];
            }
        System.out.printf("zone %d x %d blocs, %d echantillons%n", N*STEP, N*STEP, N*N);
        for (int k = 0; k < 6; k++) {
            if (k == 4) continue;
            double[] s = v[k].clone(); java.util.Arrays.sort(s);
            double mean = 0; for (double x : s) mean += x; mean /= s.length;
            double sd = 0; for (double x : s) sd += (x-mean)*(x-mean); sd = Math.sqrt(sd/s.length);
            System.out.printf("  %-12s min %6.3f  q05 %6.3f  med %6.3f  q95 %6.3f  max %6.3f  | moy %6.3f  ecart-type %5.3f%n",
                nm[k], s[0], s[(int)(s.length*.05)], s[s.length/2], s[(int)(s.length*.95)], s[s.length-1], mean, sd);
        }
        double[] s = hs.clone(); java.util.Arrays.sort(s);
        System.out.printf("  %-12s min %6.0f  q05 %6.0f  med %6.0f  q95 %6.0f  max %6.0f%n",
            "hauteur", s[0], s[(int)(s.length*.05)], s[s.length/2], s[(int)(s.length*.95)], s[s.length-1]);
        int above = 0; for (double x : hs) if (x >= 63) above++;
        System.out.printf("  terres emergees : %.1f %%  (vanilla ~30-40 %%)%n", 100.0*above/hs.length);
    }
}
