package mnw;

/** Latent d'un patch de 4x4 chunks (64x64 blocs), genere a la demande a
 *  n'importe quelle coordonnee. Granularite choisie pour qu'un TP ne calcule
 *  pas beaucoup plus que son rayon de vue. */
public final class Patch {
    public static final int SIZE = 64;
    public static final int H_RES = 4, H_N = SIZE / H_RES + 1;    // 17, comme la grille vanilla
    public static final int C_RES = 4, C_N = SIZE / C_RES + 1;    // 17
    public static final int CAVE_MIN_Y = -64, CAVE_MAX_Y = 128;
    public static final int C_NY = (CAVE_MAX_Y - CAVE_MIN_Y) / C_RES + 1;  // 49

    public final short[] h = new short[H_N * H_N];
    public final byte[]  s = new byte[H_N * H_N];
    public final byte[]  c = new byte[C_N * C_N * C_NY];

    /** Par cellule de 4 blocs : distance climatique au biome de surface, puis aux
     *  trois boites souterraines. De quoi rendre le biome a n'importe quelle
     *  profondeur sans redescendre dans le BVH (voir BiomeTable.atDepth). */
    public static final int BD = 4;
    public final float[] bd = new float[H_N * H_N * BD];

    /** Drapeau de l'algorithme d'horloge du cache (voir ProcWorld) : mis a vrai
     *  a chaque lecture, remis a faux par le balayage. Non volatile : c'est une
     *  heuristique d'eviction, une valeur perimee ne coute qu'un patch garde ou
     *  jete un tour trop tot. */
    public boolean hot = true;
    /** Segments de tunnels (x, y, z, rayon) en coordonnees locales au patch. */
    public float[] worms = new float[0];

    public int bytes() { return h.length * 2 + s.length + c.length + worms.length * 4; }

    /** Hauteur exacte d'une colonne, detail compris : c'est la meme formule que
     *  celle du decodeur. Paper s'en sert pour le spawn et le placement des
     *  structures, donc les deux doivent coincider au bloc pres. */
    public int heightAt(int lx, int lz, long wpx, long wpz) {
        int hx = lx >> 2, hz = lz >> 2;
        float tx = (lx & 3) * 0.25f, tz = (lz & 3) * 0.25f;
        int hb = hx * H_N + hz;
        float h00 = h[hb], h10 = h[hb + H_N], h01 = h[hb + 1], h11 = h[hb + H_N + 1];
        float hf = (h00 + (h10 - h00) * tx) * (1 - tz) + (h01 + (h11 - h01) * tx) * tz;
        float slope = (Math.abs(h10 - h00) + Math.abs(h01 - h00)) * 0.5f;
        float rough = Math.min(1.0f, 0.30f + slope * 0.020f);
        return (int) Math.floor(hf * 0.0625f + Detail.height(wpx + lx, wpz + lz, rough));
    }
}
