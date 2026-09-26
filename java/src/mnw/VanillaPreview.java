package mnw;
import java.io.*;

public final class VanillaPreview {
    public static void main(String[] a) throws Exception {
        long X = Long.parseLong(a[0]), Z = Long.parseLong(a[1]);
        int size = Integer.parseInt(a[2]), step = a.length > 3 ? Integer.parseInt(a[3]) : 2;
        Terrain.vanilla(42L);
        VanillaTerrain vt = new VanillaTerrain(42L);
        int n = size / step;
        short[] h = new short[n*n]; byte[] bio = new byte[n*n];
        float[] clim = new float[6], sp = new float[4];
        long t0 = System.nanoTime();
        double mn = 1e9, mx = -1e9;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                double y = vt.column(X + i*step, Z + j*step, clim, sp);
                mn = Math.min(mn, y); mx = Math.max(mx, y);
                h[i*n+j] = (short) Math.round(Math.max(-64, Math.min(320, y)));
                bio[i*n+j] = 0;
            }
        double ms = (System.nanoTime()-t0)/1e6;
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("out/vheight.bin")))) {
            o.writeInt(n); o.writeInt(step);
            for (short v : h) o.writeShort(v);
        }
        System.out.printf("%dx%d colonnes (pas %d) en %.0f ms (%.2f us/colonne) | altitude %.0f..%.0f%n",
            n, n, step, ms, ms*1000/(n*(double)n), mn, mx);
    }
}
