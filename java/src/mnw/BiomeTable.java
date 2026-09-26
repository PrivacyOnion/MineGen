package mnw;
import java.io.*;

/**
 * Table climatique de Minecraft 1.21.11 : 7593 boites a 6 dimensions
 * (temperature, humidite, continentalite, erosion, profondeur, weirdness)
 * plus un terme d'offset, chacune associee a l'un des 54 biomes overworld.
 *
 * La recherche est celle de vanilla : le biome retenu est celui dont la boite
 * minimise la distance au point climatique, la distance sur un axe valant 0 si
 * le point est dans l'intervalle et l'ecart au bord sinon.
 *
 * <h2>Ce que la table contient reellement</h2>
 *
 * L'axe profondeur ne prend que quatre intervalles distincts :
 *
 * <pre>
 *   0,000..0,000   3795 boites      1,000..1,000   3795 boites   (les memes)
 *   0,200..0,900      2 boites      1,100..1,100      1 boite
 * </pre>
 *
 * Les deux paquets de 3795 sont jumeaux : meme boite a 5 axes, meme biome. C'est
 * `addSurfaceBiome` de vanilla, qui pose chaque biome de surface deux fois, en
 * haut et en bas de l'axe. Les trois boites restantes sont lush_caves,
 * dripstone_caves et deep_dark.
 *
 * D'ou la structure ici : le BVH ne porte que les 3795 boites de surface et
 * n'indexe que les 5 axes climatiques -- la profondeur en est sortie. Le biome a
 * une profondeur donnee se deduit ensuite en comparant quatre distances
 * (`atDepth`), sans nouvelle recherche. Une colonne entiere coute donc une seule
 * descente d'arbre au lieu de 96, et l'arbre lui-meme est deux fois plus petit.
 *
 * Vanilla utilise un R-tree ; ici un BVH construit par mediane sur l'axe de plus
 * grande dispersion, avec elagage par borne inferieure. Un cache de dernier
 * resultat exploite la coherence spatiale, comme vanilla.
 */
public final class BiomeTable {
    /** Axes indexes : temperature, humidite, continentalite, erosion, weirdness.
     *  La profondeur (indice 4 de la requete) est traitee a part, l'offset est
     *  nul partout dans la table 1.21.11. */
    private static final int D = 5;
    private static final int[] AXIS = {0, 1, 2, 3, 5};

    public final String[] names;
    private final float[] lo, hi;            // [entree * D + axe]
    private final int[] biome;
    private final int n;

    // Boites souterraines : profondeur non ponctuelle a 0.
    private final float[] slo, shi;          // boites a 5 axes
    private final float[] sdLo, sdHi;        // intervalle de profondeur
    private final int[] sBiome;
    private final int ns;

    // BVH : noeuds en tableaux paralleles
    private final float[] nlo, nhi;
    private final int[] left, right, start, count;
    private int nodeCount = 0;
    private int[] order;

    public BiomeTable(InputStream src) throws IOException {
        DataInputStream in = new DataInputStream(src);
        names = new String[in.readInt()];
        for (int i = 0; i < names.length; i++) {
            byte[] b = new byte[in.readUnsignedByte()];
            in.readFully(b); names[i] = new String(b, java.nio.charset.StandardCharsets.UTF_8);
        }
        int total = in.readInt();
        float[] rlo = new float[total * 6], rhi = new float[total * 6];
        int[] rbio = new int[total];
        for (int i = 0; i < total; i++) {
            rbio[i] = in.readUnsignedShort();
            for (int k = 0; k < 6; k++) { rlo[i*6+k] = in.readFloat(); rhi[i*6+k] = in.readFloat(); }
            in.readFloat();                                  // offset : nul partout
        }
        in.close();

        // Tri des entrees : surface (profondeur ponctuelle a 0) contre souterraines.
        // Les jumelles a profondeur 1 sont ignorees, `atDepth` les reconstitue.
        int cS = 0, cU = 0;
        for (int i = 0; i < total; i++) {
            if (rlo[i*6+4] == 0f && rhi[i*6+4] == 0f) cS++;
            else if (!(rlo[i*6+4] == 1f && rhi[i*6+4] == 1f)) cU++;
        }
        n = cS; ns = cU;
        lo = new float[n * D]; hi = new float[n * D]; biome = new int[n];
        slo = new float[ns * D]; shi = new float[ns * D];
        sdLo = new float[ns]; sdHi = new float[ns]; sBiome = new int[ns];
        int a = 0, b = 0;
        for (int i = 0; i < total; i++) {
            float dl = rlo[i*6+4], dh = rhi[i*6+4];
            if (dl == 0f && dh == 0f) {
                for (int k = 0; k < D; k++) { lo[a*D+k] = rlo[i*6+AXIS[k]]; hi[a*D+k] = rhi[i*6+AXIS[k]]; }
                biome[a++] = rbio[i];
            } else if (!(dl == 1f && dh == 1f)) {
                for (int k = 0; k < D; k++) { slo[b*D+k] = rlo[i*6+AXIS[k]]; shi[b*D+k] = rhi[i*6+AXIS[k]]; }
                sdLo[b] = dl; sdHi[b] = dh; sBiome[b++] = rbio[i];
            }
        }

        int maxNodes = 4 * n;
        nlo = new float[maxNodes * D]; nhi = new float[maxNodes * D];
        left = new int[maxNodes]; right = new int[maxNodes];
        start = new int[maxNodes]; count = new int[maxNodes];
        order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        build(0, n);
    }

    private int build(int from, int to) {
        int node = nodeCount++;
        for (int k = 0; k < D; k++) { nlo[node*D+k] = Float.MAX_VALUE; nhi[node*D+k] = -Float.MAX_VALUE; }
        for (int i = from; i < to; i++) {
            int e = order[i];
            for (int k = 0; k < D; k++) {
                if (lo[e*D+k] < nlo[node*D+k]) nlo[node*D+k] = lo[e*D+k];
                if (hi[e*D+k] > nhi[node*D+k]) nhi[node*D+k] = hi[e*D+k];
            }
        }
        if (to - from <= 8) { left[node] = -1; start[node] = from; count[node] = to - from; return node; }

        int axis = 0; float best = -1;
        for (int k = 0; k < D; k++) {
            float ext = nhi[node*D+k] - nlo[node*D+k];
            if (ext > best) { best = ext; axis = k; }
        }
        final int ax = axis;
        Integer[] slice = new Integer[to - from];
        for (int i = 0; i < slice.length; i++) slice[i] = order[from + i];
        java.util.Arrays.sort(slice, (p, q) ->
            Float.compare(lo[p*D+ax] + hi[p*D+ax], lo[q*D+ax] + hi[q*D+ax]));
        for (int i = 0; i < slice.length; i++) order[from + i] = slice[i];

        int mid = (from + to) >>> 1;
        left[node] = build(from, mid);
        right[node] = build(mid, to);
        count[node] = 0;
        return node;
    }

    private static float axisDist(float v, float a, float b) {
        if (v < a) return a - v;
        if (v > b) return v - b;
        return 0f;
    }
    private float distEntry(int e, float[] q) {
        float s = 0;
        for (int k = 0; k < D; k++) { float d = axisDist(q[AXIS[k]], lo[e*D+k], hi[e*D+k]); s += d * d; }
        return s;
    }
    private float distNode(int node, float[] q) {
        float s = 0;
        for (int k = 0; k < D; k++) { float d = axisDist(q[AXIS[k]], nlo[node*D+k], nhi[node*D+k]); s += d * d; }
        return s;
    }

    /** Etat de recherche, sorti de la table pour qu'elle reste utilisable depuis
     *  plusieurs threads. Porte aussi le cache de derniere entree : la coherence
     *  spatiale fait qu'une colonne voisine retombe presque toujours dessus.
     *  `dist` expose la distance retenue, dont `atDepth` a besoin. */
    public static final class Cursor {
        int cache = -1; float bestDist; int bestEntry;
        public float dist;
    }

    /** `nd` est la distance du noeud, deja calculee par l'appelant : la recalculer
     *  a l'entree coutait un tiers des evaluations de boite. */
    private void search(int node, float nd, float[] q, Cursor c) {
        if (nd >= c.bestDist) return;
        if (left[node] < 0) {
            for (int i = start[node]; i < start[node] + count[node]; i++) {
                int e = order[i];
                float d = distEntry(e, q);
                if (d < c.bestDist) { c.bestDist = d; c.bestEntry = e; }
            }
            return;
        }
        int a = left[node], b = right[node];
        float da = distNode(a, q), db = distNode(b, q);
        if (da > db) { int t = a; a = b; b = t; float u = da; da = db; db = u; }
        search(a, da, q, c);
        search(b, db, q, c);
    }

    /** Biome de surface. q = {temperature, humidite, continentalite, erosion,
     *  profondeur, weirdness, 0} ; la profondeur est ignoree ici. La distance
     *  retenue reste lisible dans `c.dist`. */
    public int find(float[] q, Cursor c) {
        c.bestDist = Float.MAX_VALUE; c.bestEntry = -1;
        if (c.cache >= 0) { c.bestDist = distEntry(c.cache, q); c.bestEntry = c.cache; }
        search(0, distNode(0, q), q, c);
        c.cache = c.bestEntry;
        c.dist = c.bestDist;
        return biome[c.bestEntry];
    }

    /** Nombre de boites souterraines. */
    public int specials() { return ns; }

    /** Distances aux boites souterraines dans les 5 axes climatiques, a calculer
     *  une fois par colonne. `out` doit avoir au moins `specials()` cases. */
    public void specialDists(float[] q, float[] out) {
        for (int i = 0; i < ns; i++) {
            float s = 0;
            for (int k = 0; k < D; k++) { float d = axisDist(q[AXIS[k]], slo[i*D+k], shi[i*D+k]); s += d * d; }
            out[i] = s;
        }
    }

    /**
     * Biome a la profondeur donnee, sans nouvelle descente d'arbre. `bd[off]` est
     * la distance au biome de surface, suivie des `specials()` distances
     * souterraines -- exactement la disposition que Patch.bd stocke par cellule.
     *
     * La boite de surface existe en deux exemplaires, a profondeur 0 et 1 : sa
     * penalite est donc min(d2, (d-1)2). Chaque boite souterraine ajoute la
     * sienne a sa propre distance climatique.
     */
    public int atDepth(int surfBiome, float[] bd, int off, float depth) {
        float d0 = depth, d1 = depth - 1f;
        float best = bd[off] + Math.min(d0 * d0, d1 * d1);
        int b = surfBiome;
        for (int i = 0; i < ns; i++) {
            float t = axisDist(depth, sdLo[i], sdHi[i]);
            t = bd[off + 1 + i] + t * t;
            if (t < best) { best = t; b = sBiome[i]; }
        }
        return b;
    }

    public int entries() { return n; }
    public int nodes()   { return nodeCount; }
}
