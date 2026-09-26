package mnw;

/**
 * Creusements parametriques : tunnels et ravins.
 *
 * Un champ 3D echantillonne tous les 4 blocs ne peut pas porter un tunnel de 2
 * blocs de large -- il disparait entre deux echantillons. Les grandes cavernes
 * restent dans le champ latent ; les formes fines sont decrites par des courbes,
 * ce qui coute quelques centaines d'octets au lieu d'une grille fine.
 *
 * Les parametres des ravins viennent de configured_carver/canyon.json de
 * Minecraft 1.21.11 : probabilite 0,01 par chunk, y entre 10 et 67, epaisseur
 * trapezoidale de 0 a 6, et yScale 3,0 -- d'ou une section verticale trois fois
 * plus haute que large.
 *
 * Chaque segment fait 5 flottants : x, y, z, rayon horizontal, rayon vertical.
 * Fonction pure de (seed, coordonnees) : aucun ordre de generation impose.
 */
public final class Carver {
    public static final int STRIDE = 5;

    // --- tunnels ---
    private static final int CELL = 64, STEPS = 130;
    private static final double STEP_LEN = 3.2, MAX_R = 3.4;

    // --- ravins ---
    private static final int R_CELL = 176, R_STEPS = 62;
    private static final double R_STEP_LEN = 1.8, R_MAX_R = 14.0;

    private static long mix(long h) {
        h ^= h >>> 30; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 27; h *= 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
    private static double rnd(long a, long b, long c) {
        return (mix(a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L)
                >>> 11) / (double) (1L << 53);
    }

    /** Cache des trajets : une cellule est demandee par tous les patches a portee,
     *  soit (2*reach+1)^2 fois. Sans cache on remarche le meme trajet ~170 fois. */
    private static final java.util.concurrent.ConcurrentHashMap<Long, float[]> PATHS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final float[] EMPTY = new float[0];

    /** Clef du cache. La graine DOIT en faire partie : sans elle, deux mondes de
     *  graines differentes chargés dans la meme JVM se partagent leurs tunnels --
     *  le premier monde initialise gagne, les suivants heritent de ses caves.
     *  Le bit de poids faible distingue tunnel et ravin ; le reste est un
     *  melange bijectif de la cellule et de la graine. */
    private static long key(long seed, int i, int j, int kind) {
        long cell = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        return (mix(cell ^ (seed * 0x9E3779B97F4A7C15L)) & ~1L) | kind;
    }

    private static float[] cached(long key, java.util.function.LongFunction<float[]> gen) {
        float[] c = PATHS.get(key);
        if (c != null) return c;
        if (PATHS.size() > 200000) PATHS.clear();
        float[] v = gen.apply(key);
        PATHS.put(key, v);
        return v;
    }

    /** Trajet d'un tunnel, en coordonnees monde. */
    private static float[] wormPath(long seed, int i, int j) {
        return cached(key(seed, i, j, 0), k -> {
            if (rnd(i, j, seed) > 0.88) return EMPTY;
            float[] out = new float[R_STRIDE_W()];
            double x = i * (double) CELL + rnd(i, j, seed + 1) * CELL;
            double z = j * (double) CELL + rnd(i, j, seed + 2) * CELL;
            double y = -52 + rnd(i, j, seed + 3) * 96;
            double yaw = rnd(i, j, seed + 4) * Math.PI * 2;
            double pitch = (rnd(i, j, seed + 5) - 0.5) * 0.55;
            double baseR = 1.9 + rnd(i, j, seed + 6) * 1.5;
            for (int s = 0; s < STEPS; s++) {
                yaw   += (rnd(i * 31L + s, j, seed + 7) - 0.5) * 0.42;
                pitch += (rnd(i, j * 31L + s, seed + 8) - 0.5) * 0.22;
                pitch *= 0.90;
                double cp = Math.cos(pitch);
                x += Math.cos(yaw) * cp * STEP_LEN;
                z += Math.sin(yaw) * cp * STEP_LEN;
                y += Math.sin(pitch) * STEP_LEN;
                if (y < -56 || y > 108) { pitch = -pitch; y = Math.max(-56, Math.min(108, y)); }
                float r = (float) (baseR * (0.95 + 0.32 * Math.sin(s * 0.19 + i)));
                int o = s * STRIDE;
                out[o] = (float) x; out[o+1] = (float) y; out[o+2] = (float) z;
                out[o+3] = r; out[o+4] = r;
            }
            return out;
        });
    }
    private static int R_STRIDE_W() { return STEPS * STRIDE; }

    /** Trajet d'un ravin : long, etroit, tres haut, ouvert vers le haut. */
    private static float[] ravinePath(long seed, int i, int j) {
        return cached(key(seed, i, j, 1), k -> {
            if (rnd(i, j, seed + 500) > 0.80) return EMPTY;
            float[] out = new float[R_STEPS * STRIDE];
            double x = i * (double) R_CELL + rnd(i, j, seed + 501) * R_CELL;
            double z = j * (double) R_CELL + rnd(i, j, seed + 502) * R_CELL;
            double y = 10 + rnd(i, j, seed + 503) * 57;                 // y de canyon.json
            double yaw = rnd(i, j, seed + 504) * Math.PI * 2;
            double pitch = (rnd(i, j, seed + 505) - 0.5) * 0.25;        // vertical_rotation
            // epaisseur trapezoidale min 0 / plateau 2 / max 6
            double thick = 1.2 + rnd(i, j, seed + 506) * 2.6;
            double len = 0.75 + rnd(i, j, seed + 507) * 0.25;           // distance_factor
            int steps = (int) (R_STEPS * len);
            for (int s = 0; s < steps; s++) {
                yaw   += (rnd(i * 37L + s, j, seed + 508) - 0.5) * 0.30;
                pitch += (rnd(i, j * 37L + s, seed + 509) - 0.5) * 0.06;
                pitch *= 0.92;
                double cp = Math.cos(pitch);
                x += Math.cos(yaw) * cp * R_STEP_LEN;
                z += Math.sin(yaw) * cp * R_STEP_LEN;
                y += Math.sin(pitch) * R_STEP_LEN;
                if (y < -40 || y > 72) { pitch = -pitch; y = Math.max(-40, Math.min(72, y)); }
                // effile aux deux bouts, plus large au milieu
                double taper = Math.sqrt(Math.sin(Math.PI * (s + 0.5) / steps));
                double wobble = 0.75 + 0.5 * rnd(i + s, j, seed + 510);
                float rh = (float) (thick * taper * wobble);
                float rv = (float) Math.min(R_MAX_R, rh * 3.0);         // yScale = 3
                int o = s * STRIDE;
                out[o] = (float) x; out[o+1] = (float) y; out[o+2] = (float) z;
                out[o+3] = rh; out[o+4] = rv;
            }
            return out;
        });
    }

    /** Segments (x, y, z, rH, rV) croisant le patch, en coordonnees locales. */
    public static float[] collect(long seed, int px, int pz) {
        float[] buf = new float[STRIDE * 3072];
        int n = 0;
        double lo = -R_MAX_R - 1, hi = Patch.SIZE + R_MAX_R + 1;

        int reach = (int) Math.ceil(STEPS * STEP_LEN / CELL) + 1;
        int c0x = Math.floorDiv(px, CELL), c0z = Math.floorDiv(pz, CELL);
        for (int i = c0x - reach; i <= c0x + reach; i++)
            for (int j = c0z - reach; j <= c0z + reach; j++)
                n = gather(wormPath(seed, i, j), px, pz, lo, hi, buf, n);

        int rReach = (int) Math.ceil(R_STEPS * R_STEP_LEN / R_CELL) + 1;
        int r0x = Math.floorDiv(px, R_CELL), r0z = Math.floorDiv(pz, R_CELL);
        for (int i = r0x - rReach; i <= r0x + rReach; i++)
            for (int j = r0z - rReach; j <= r0z + rReach; j++)
                n = gather(ravinePath(seed, i, j), px, pz, lo, hi, buf, n);

        return java.util.Arrays.copyOf(buf, n);
    }

    private static int gather(float[] w, int px, int pz, double lo, double hi, float[] buf, int n) {
        for (int s = 0; s < w.length; s += STRIDE) {
            if (w[s + 3] <= 0) continue;
            double lx = w[s] - px, lz = w[s + 2] - pz;
            if (lx < lo || lx > hi || lz < lo || lz > hi) continue;
            if (n + STRIDE > buf.length) return n;
            buf[n] = (float) lx; buf[n+1] = w[s+1]; buf[n+2] = (float) lz;
            buf[n+3] = w[s+3]; buf[n+4] = w[s+4];
            n += STRIDE;
        }
        return n;
    }
}
