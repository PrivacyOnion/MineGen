package mnw;

/** Perlin 3D "improved", equivalent en cout a celui de Minecraft. */
public final class Perlin {
    private final int[] p = new int[512];

    public Perlin(long seed) {
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) perm[i] = i;
        long s = seed;
        for (int i = 255; i > 0; i--) {            // shuffle deterministe
            s = s * 6364136223846793005L + 1442695040888963407L;
            int j = (int) Math.floorMod(s >>> 33, i + 1);
            int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
        }
        for (int i = 0; i < 512; i++) p[i] = perm[i & 255];
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    public double noise(double x, double y, double z) {
        int xi = (int) Math.floor(x) & 255, yi = (int) Math.floor(y) & 255, zi = (int) Math.floor(z) & 255;
        x -= Math.floor(x); y -= Math.floor(y); z -= Math.floor(z);
        double u = fade(x), v = fade(y), w = fade(z);
        int a = p[xi] + yi, aa = p[a] + zi, ab = p[a + 1] + zi;
        int b = p[xi + 1] + yi, ba = p[b] + zi, bb = p[b + 1] + zi;
        return lerp(w,
            lerp(v, lerp(u, grad(p[aa], x, y, z),         grad(p[ba], x - 1, y, z)),
                    lerp(u, grad(p[ab], x, y - 1, z),     grad(p[bb], x - 1, y - 1, z))),
            lerp(v, lerp(u, grad(p[aa + 1], x, y, z - 1), grad(p[ba + 1], x - 1, y, z - 1)),
                    lerp(u, grad(p[ab + 1], x, y - 1, z - 1), grad(p[bb + 1], x - 1, y - 1, z - 1))));
    }
}
