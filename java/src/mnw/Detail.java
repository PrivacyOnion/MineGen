package mnw;

/** Detail haute frequence synthetise au decodage, jamais stocke.
 *
 *  Le latent ne porte que les basses frequences (1 echantillon / 2 blocs) ; les
 *  marches d'un bloc que produit l'interpolation sont cassees ici par un bruit
 *  de valeur a courte longueur d'onde, fonction pure des coordonnees monde.
 *  Cout : ~20 operations entieres par colonne, soit 0,3 % du budget d'un chunk.
 */
public final class Detail {
    private static double h01(long x, long z, long s) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ s;
        h ^= h >>> 30; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 27; h *= 0x94D049BB133111EBL;
        return ((h >>> 40) / (double) (1 << 24)) * 2.0 - 1.0;
    }
    private static double vnoise(double x, double z, long s) {
        long xi = (long) Math.floor(x), zi = (long) Math.floor(z);
        double fx = x - xi, fz = z - zi;
        double ux = fx * fx * (3 - 2 * fx), uz = fz * fz * (3 - 2 * fz);
        double a = h01(xi, zi, s), b = h01(xi + 1, zi, s);
        double c = h01(xi, zi + 1, s), d = h01(xi + 1, zi + 1, s);
        return (a + (b - a) * ux) * (1 - uz) + (c + (d - c) * ux) * uz;
    }
    /** Perturbation d'altitude en blocs, ~+/-1,6. `rough` module selon la pente. */
    public static double height(long wx, long wz, double rough) {
        return (vnoise(wx / 7.0, wz / 7.0, 0x51ED) * 1.05
              + vnoise(wx / 2.7, wz / 2.7, 0x9F13) * 0.55) * rough;
    }

    /** Niveau de la nappe phreatique, en blocs, ou DRY si la region est seche.
     *
     *  Vanilla module ses aquiferes par un bruit de "floodedness" : sans lui toutes
     *  les cavites sous y~30 seraient noyees et le sous-sol ressemblerait a un ocean.
     *  Ici une octave lente decide si la region porte une nappe, deux autres en
     *  donnent le niveau. Environ 10 operations par colonne. */
    public static final int DRY = Integer.MIN_VALUE;

    public static int waterTable(long wx, long wz) {
        double flood = vnoise(wx / 470.0, wz / 470.0, 0x3D19)
                     + vnoise(wx / 155.0, wz / 155.0, 0x8A62) * 0.45;
        if (flood < 0.30) return DRY;                       // ~72 % du sous-sol reste sec
        // Les aquiferes de vanilla siegent bas : au-dessus de y~20 les cavites sont
        // seches sauf sous un ocean, cas traite separement par le decodeur.
        return (int) (-12 + vnoise(wx / 210.0, wz / 210.0, 0x2B7F) * 26
                          + vnoise(wx / 61.0, wz / 61.0, 0xC4A1) * 6);
    }

    /** Socle : plein a y=-64, absent a partir de y=-59, aleatoire entre les deux.
     *  C'est la vertical_gradient `bedrock_floor` de vanilla. */
    public static boolean bedrock(long wx, int y, long wz) {
        if (y <= -64) return true;
        if (y >= -59) return false;
        double d = (-59 - y) / 5.0;                 // 1 en bas, 0 en haut
        long h = wx * 0x9E3779B97F4A7C15L ^ (long) y * 0xC2B2AE3D27D4EB4FL ^ wz * 0x165667B19E3779F9L;
        h ^= h >>> 30; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 31;
        return ((h >>> 40) / (double) (1 << 24)) < d;
    }

    /** Ondulation de la frontiere pierre / deepslate autour de y=0. */
    public static int deepslateTop(long wx, long wz) {
        return (int) (vnoise(wx / 23.0, wz / 23.0, 0x77E3) * 5.0);
    }
}
