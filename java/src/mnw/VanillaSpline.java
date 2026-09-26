package mnw;
import java.io.*;

/**
 * Splines de faconnage du terrain extraites de Minecraft 1.21.11
 * (data/minecraft/worldgen/density_function/overworld/{offset,factor,jaggedness}).
 *
 * 118 noeuds, 430 points, 5,8 Ko. Interpolation cubique de Hermite, dans la forme
 * exacte de Mojang. Les noeuds s'imbriquent : la valeur d'un point peut etre une
 * autre spline, evaluee sur une autre coordonnee (continents -> erosion -> ridges).
 */
public final class VanillaSpline {
    public static final int OFFSET = 0, FACTOR = 1, JAGGEDNESS = 2;
    private final int[] roots;
    private final byte[] coord;      // par noeud : index de coordonnee
    private final float[][] loc, der, val;
    private final int[][] ref;       // -1 = constante, sinon index de noeud

    public VanillaSpline(InputStream src) throws IOException {
        DataInputStream in = new DataInputStream(src);
        roots = new int[in.readInt()];
        for (int i = 0; i < roots.length; i++) roots[i] = in.readInt();
        int n = in.readInt();
        coord = new byte[n]; loc = new float[n][]; der = new float[n][];
        val = new float[n][]; ref = new int[n][];
        for (int i = 0; i < n; i++) {
            coord[i] = in.readByte();
            int p = in.readUnsignedByte();
            loc[i] = new float[p]; der[i] = new float[p]; val[i] = new float[p]; ref[i] = new int[p];
            for (int j = 0; j < p; j++) {
                loc[i][j] = in.readFloat(); der[i][j] = in.readFloat();
                if (in.readUnsignedByte() == 0) { val[i][j] = in.readFloat(); ref[i][j] = -1; }
                else { ref[i][j] = in.readInt(); }
            }
        }
        in.close();
    }

    /** c = {continents, erosion, ridges, ridges_folded}. */
    public float eval(int which, float[] c) { return node(roots[which], c); }

    private float node(int i, float[] c) {
        float x = c[coord[i]];
        float[] L = loc[i];
        int n = L.length;
        if (n == 0) return 0;
        if (x < L[0])     return value(i, 0, c) + der[i][0] * (x - L[0]);
        if (x >= L[n-1])  return value(i, n-1, c) + der[i][n-1] * (x - L[n-1]);
        int k = 0;
        while (k < n - 2 && x >= L[k + 1]) k++;
        float m = L[k+1] - L[k];
        float t = (x - L[k]) / m;
        float g = value(i, k, c), h = value(i, k+1, c);
        float p = der[i][k] * m - (h - g), q = -(der[i][k+1] * m) + (h - g);
        return g + t * (h - g) + t * (1 - t) * (p + t * (q - p));   // Hermite, forme Mojang
    }

    private float value(int i, int j, float[] c) {
        return ref[i][j] < 0 ? val[i][j] : node(ref[i][j], c);
    }
}
