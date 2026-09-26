package mnw;

/** Bruit de gradient en coordonnees MONDE, en double : reste exact a 1e6 blocs
 *  (en float32 la precision y tombe a 0,06 bloc et le terrain se met a marcher). */
public final class NoiseJ {
    private static final long M1 = 0x9E3779B97F4A7C15L, M2 = 0xC2B2AE3D27D4EB4FL, M3 = 0x165667B19E3779F9L;

    private static long mix(long h) {
        h ^= h >>> 29; h *= 0x94D049BB133111EBL; h ^= h >>> 32; return h;
    }
    private static long h2(long x, long z, long s) { return mix(mix(x * M1 ^ s) ^ z * M2); }
    private static long h3(long x, long y, long z, long s) { return mix(mix(mix(x * M1 ^ s) ^ y * M2) ^ z * M3); }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    /** Choisit un des 12 gradients. Un vrai modulo 12 sur long coute une division
     *  entiere -- mesuree a 4,3 ns, soit le triple du hachage lui-meme. La
     *  multiplication-decalage tire le meme tirage uniforme (biais 2^-32) en une
     *  multiplication. */
    private static int g12(long h) { return (int) (((h >>> 32) * 12) >>> 32) * 3; }

    public static double perlin2(double x, double z, long seed) {
        long xi = (long) Math.floor(x), zi = (long) Math.floor(z);
        double xf = x - xi, zf = z - zi, u = fade(xf), v = fade(zf);
        double d00 = g2(xi, zi, seed, xf, zf), d10 = g2(xi + 1, zi, seed, xf - 1, zf);
        double d01 = g2(xi, zi + 1, seed, xf, zf - 1), d11 = g2(xi + 1, zi + 1, seed, xf - 1, zf - 1);
        double n0 = d00 + u * (d10 - d00), n1 = d01 + u * (d11 - d01);
        return (n0 + v * (n1 - n0)) * 1.4;
    }
    private static final double[] CS = new double[16];
    static { for (int i = 0; i < 8; i++) { CS[i*2] = Math.cos(i*Math.PI/4); CS[i*2+1] = Math.sin(i*Math.PI/4); } }
    private static double g2(long x, long z, long s, double dx, double dz) {
        int i = (int) (h2(x, z, s) & 7) << 1;
        return CS[i] * dx + CS[i + 1] * dz;
    }

    private static final int[] G3 = {1,1,0, -1,1,0, 1,-1,0, -1,-1,0, 1,0,1, -1,0,1,
                                     1,0,-1, -1,0,-1, 0,1,1, 0,-1,1, 0,1,-1, 0,-1,-1};

    public static double perlin3(double x, double y, double z, long seed) {
        long xi = (long) Math.floor(x), yi = (long) Math.floor(y), zi = (long) Math.floor(z);
        double xf = x - xi, yf = y - yi, zf = z - zi;
        double u = fade(xf), v = fade(yf), w = fade(zf);
        double d000 = g3(xi,yi,zi,seed,xf,yf,zf),        d100 = g3(xi+1,yi,zi,seed,xf-1,yf,zf);
        double d010 = g3(xi,yi+1,zi,seed,xf,yf-1,zf),    d110 = g3(xi+1,yi+1,zi,seed,xf-1,yf-1,zf);
        double d001 = g3(xi,yi,zi+1,seed,xf,yf,zf-1),    d101 = g3(xi+1,yi,zi+1,seed,xf-1,yf,zf-1);
        double d011 = g3(xi,yi+1,zi+1,seed,xf,yf-1,zf-1),d111 = g3(xi+1,yi+1,zi+1,seed,xf-1,yf-1,zf-1);
        double c00 = d000 + u*(d100-d000), c10 = d010 + u*(d110-d010);
        double c01 = d001 + u*(d101-d001), c11 = d011 + u*(d111-d011);
        double c0 = c00 + v*(c10-c00), c1 = c01 + v*(c11-c01);
        return (c0 + w*(c1-c0)) * 1.15;
    }
    /** perlin3 au plan y = 0, ou le fondu vertical vaut zero : le plan haut ne
     *  pese rien et dy = 0 annule la composante gy. Quatre coins au lieu de huit,
     *  resultat bit a bit identique a perlin3(x, 0, z, seed). */
    public static double perlin3y0(double x, double z, long seed) {
        long xi = (long) Math.floor(x), zi = (long) Math.floor(z);
        double xf = x - xi, zf = z - zi, x1 = xf - 1, z1 = zf - 1;
        double u = fade(xf), w = fade(zf);
        // y = 0 annule le terme central du hachage, et les deux colonnes en x
        // sont communes aux deux rangees en z : 4 melanges au lieu de 12.
        long mx0 = mix(mix(xi * M1 ^ seed)), mx1 = mix(mix((xi + 1) * M1 ^ seed));
        long hz0 = zi * M3, hz1 = (zi + 1) * M3;
        int i00 = g12(mix(mx0 ^ hz0)), i10 = g12(mix(mx1 ^ hz0));
        int i01 = g12(mix(mx0 ^ hz1)), i11 = g12(mix(mx1 ^ hz1));
        double a00 = G3[i00] * xf + G3[i00+2] * zf, a10 = G3[i10] * x1 + G3[i10+2] * zf;
        double a01 = G3[i01] * xf + G3[i01+2] * z1, a11 = G3[i11] * x1 + G3[i11+2] * z1;
        double c00 = a00 + u * (a10 - a00), c01 = a01 + u * (a11 - a01);
        return (c00 + w * (c01 - c00)) * 1.15;
    }

    private static double g3(long x, long y, long z, long s, double dx, double dy, double dz) {
        int i = g12(h3(x, y, z, s));
        return G3[i]*dx + G3[i+1]*dy + G3[i+2]*dz;
    }


    /**
     * Perlin 3D specialise pour un parcours vertical.
     *
     * Le champ de caves echantillonne des colonnes : a (x, z) fixes, les quatre
     * coins du reseau en X et Z ne changent jamais, et le plan en Y ne change
     * qu'un echantillon sur 26. Or le produit scalaire du gradient est affine en
     * dy : grad . (dx, dy, dz) = (gx*dx + gz*dz) + gy*dy. On precalcule donc par
     * plan les deux coefficients A et G deja melanges en X et Z, et un
     * echantillon ne coute plus que deux multiplications et un fondu.
     *
     * En montant d'une cellule, le plan haut devient le plan bas : seuls quatre
     * coins sont a hacher au lieu de huit. Resultat identique a perlin3.
     */
    public static final class Col {
        private long xi, zi, seed, yCell = Long.MIN_VALUE;
        private double xf, zf, u, w;
        private long hx0, hx1, hz0, hz1;   // hachages en x et z, invariants sur la colonne
        private double alo, glo, ahi, ghi;

        /** Fixe la colonne. Les coordonnees sont deja divisees par l'echelle. */
        public void begin(double x, double z, long seed) {
            this.xi = (long) Math.floor(x); this.zi = (long) Math.floor(z);
            this.xf = x - xi; this.zf = z - zi;
            this.u = fade(xf); this.w = fade(zf);
            this.seed = seed; this.yCell = Long.MIN_VALUE;
            this.hx0 = mix(xi * M1 ^ seed); this.hx1 = mix((xi + 1) * M1 ^ seed);
            this.hz0 = zi * M3; this.hz1 = (zi + 1) * M3;
        }

        /** Melange en X puis en Z les quatre coins du plan y, pour A et pour G. */
        private void plane(long y, boolean upper) {
            long hy = y * M2;
            long my0 = mix(hx0 ^ hy), my1 = mix(hx1 ^ hy);
            int i00 = g12(mix(my0 ^ hz0)), i10 = g12(mix(my1 ^ hz0));
            int i01 = g12(mix(my0 ^ hz1)), i11 = g12(mix(my1 ^ hz1));
            double x1 = xf - 1, z1 = zf - 1;
            double a00 = G3[i00] * xf + G3[i00+2] * zf, a10 = G3[i10] * x1 + G3[i10+2] * zf;
            double a01 = G3[i01] * xf + G3[i01+2] * z1, a11 = G3[i11] * x1 + G3[i11+2] * z1;
            double b0 = a00 + u * (a10 - a00), b1 = a01 + u * (a11 - a01);
            double a = b0 + w * (b1 - b0);
            double c0 = G3[i00+1] + u * (G3[i10+1] - G3[i00+1]);
            double c1 = G3[i01+1] + u * (G3[i11+1] - G3[i01+1]);
            double g = c0 + w * (c1 - c0);
            if (upper) { ahi = a; ghi = g; } else { alo = a; glo = g; }
        }

        public double at(double y) {
            long yi = (long) Math.floor(y);
            if (yi != yCell) {
                if (yi == yCell + 1) { alo = ahi; glo = ghi; plane(yi + 1, true); }
                else { plane(yi, false); plane(yi + 1, true); }
                yCell = yi;
            }
            double yfr = y - yi;
            double lo = alo + glo * yfr, hi = ahi + ghi * (yfr - 1);
            return (lo + fade(yfr) * (hi - lo)) * 1.15;
        }
    }

    public static double fbm2(double x, double z, long seed, int oct) {
        double acc = 0, amp = 1, f = 1, norm = 0;
        for (int i = 0; i < oct; i++) { acc += amp * perlin2(x*f, z*f, seed + i*7919L); norm += amp; amp *= 0.5; f *= 2; }
        return acc / norm;
    }
    public static double ridged1(double x, double z, long seed) {
        return 1.0 - Math.abs(perlin2(x, z, seed));
    }
}
