package mnw;
import java.io.*;
import java.util.*;

/**
 * Interpreteur des surface rules de Minecraft 1.21.11, extraites de
 * noise_settings/overworld.json (428 noeuds, 3,9 Ko).
 *
 * C'est ce qui donne le sable des deserts sur plusieurs blocs, les bandes de
 * terracotta des badlands, le podzol des vieilles taigas, la calcite, la boue
 * des mangroves, la glace et la neige poudreuse en altitude.
 *
 * Ecarts assumes par rapport a vanilla :
 *  - le jitter aleatoire de +/-0,25 bloc sur la profondeur de surface est omis ;
 *  - `above_preliminary_surface` est approxime par "a moins de 8 blocs du sommet" ;
 *  - les bandes de badlands sont regenerees par un hachage deterministe plutot
 *    que par le RandomSource de Mojang.
 */
public final class SurfaceRules {
    private static final int SEQ = 1, COND = 2, BLOCK = 3, BANDS_RULE = 4;
    private static final int BIOME = 10, NOISE = 11, STONE_DEPTH = 12, WATER = 13, Y_ABOVE = 14,
                             STEEP = 15, NOT = 16, HOLE = 17, VGRAD = 18, BANDS = 19,
                             ABOVE_PS = 20, TEMP = 21;

    private byte[] type;
    private int[] ia, ib, ic;
    private float[] fa, fb;
    private int[][] kids;
    private int nNodes;
    /** Une racine specialisee par biome : les tests de biome sont resolus au
     *  chargement et les branches mortes supprimees. Le biome est constant sur
     *  une colonne, donc les re-tester a chaque niveau etait du gaspillage. */
    private int[] bioRoot;
    /** Profondeur maximale sous le sommet ou une regle peut encore s'appliquer,
     *  par biome. Au-dela, evaluer l'arbre ne peut rien produire. */
    private int[] bioDepth;
    private final long[] biomeSet;                 // 54 biomes : un long suffit
    private final VanillaNoise[] noise;
    private final int secondaryNoiseIdx;
    private final int root;
    private final int[] bands;                     // bandes de terracotta par altitude

    public SurfaceRules(InputStream src, long seed) throws IOException {
        DataInputStream in = new DataInputStream(src);
        int nb = in.readInt();
        List<String> blocks = new ArrayList<>();
        for (int i = 0; i < nb; i++) {
            byte[] b = new byte[in.readUnsignedByte()];
            in.readFully(b);
            blocks.add(new String(b, java.nio.charset.StandardCharsets.UTF_8));
        }
        Palette.init(blocks);
        Blocks.bind();
        int[] blockId = new int[nb];
        for (int i = 0; i < nb; i++) blockId[i] = Palette.id(blocks.get(i));

        int ns = in.readInt();
        biomeSet = new long[ns];
        for (int i = 0; i < ns; i++) {
            int k = in.readUnsignedShort();
            for (int j = 0; j < k; j++) biomeSet[i] |= 1L << in.readUnsignedShort();
        }

        int nn = in.readInt();
        noise = new VanillaNoise[nn];
        int secIdx = -1;
        for (int i = 0; i < nn; i++) {
            byte[] b = new byte[in.readUnsignedByte()];
            in.readFully(b);
            String name = new String(b, java.nio.charset.StandardCharsets.UTF_8);
            int fo = in.readInt();
            double[] amps = new double[in.readUnsignedByte()];
            for (int j = 0; j < amps.length; j++) amps[j] = in.readFloat();
            noise[i] = new VanillaNoise(seed + 900 + i * 31L, fo, amps);
            if (name.equals("surface_secondary")) secIdx = i;
        }
        secondaryNoiseIdx = secIdx;

        root = in.readInt();
        int n = in.readInt();
        type = new byte[n]; ia = new int[n]; ib = new int[n]; ic = new int[n];
        fa = new float[n]; fb = new float[n]; kids = new int[n][];
        for (int i = 0; i < n; i++) {
            int t = in.readUnsignedByte();
            type[i] = (byte) t;
            switch (t) {
                case SEQ:
                    kids[i] = new int[in.readUnsignedByte()];
                    for (int j = 0; j < kids[i].length; j++) kids[i][j] = in.readInt();
                    break;
                case COND: ia[i] = in.readInt(); ib[i] = in.readInt(); break;
                case BLOCK: ia[i] = blockId[in.readUnsignedShort()]; break;
                case BIOME: ia[i] = in.readUnsignedShort(); break;
                case NOISE: ia[i] = in.readUnsignedByte(); fa[i] = in.readFloat(); fb[i] = in.readFloat(); break;
                case STONE_DEPTH:
                    ia[i] = in.readUnsignedByte(); ib[i] = in.readInt();
                    ic[i] = in.readUnsignedByte(); fa[i] = in.readInt();
                    break;
                case WATER: case Y_ABOVE:
                    ia[i] = in.readInt(); ib[i] = in.readInt(); ic[i] = in.readUnsignedByte();
                    break;
                case NOT: ia[i] = in.readInt(); break;
                case VGRAD: ia[i] = in.readInt(); ib[i] = in.readInt(); break;
                default: break;
            }
        }
        in.close();
        nNodes = n;
        bands = buildBands(seed);
        specializeByBiome();
    }

    // --- specialisation par biome ---------------------------------------------

    private int alloc() {
        if (nNodes == type.length) {
            int cap = nNodes * 2;
            type = java.util.Arrays.copyOf(type, cap); ia = java.util.Arrays.copyOf(ia, cap);
            ib = java.util.Arrays.copyOf(ib, cap);     ic = java.util.Arrays.copyOf(ic, cap);
            fa = java.util.Arrays.copyOf(fa, cap);     fb = java.util.Arrays.copyOf(fb, cap);
            kids = java.util.Arrays.copyOf(kids, cap);
        }
        return nNodes++;
    }

    /** 0 = faux quel que soit y, 1 = vrai quel que soit y, -1 = depend de y. */
    private int foldCond(int n, int biome) {
        if (type[n] == BIOME) return (biomeSet[ia[n]] & (1L << biome)) != 0 ? 1 : 0;
        if (type[n] == NOT) { int r = foldCond(ia[n], biome); return r < 0 ? -1 : 1 - r; }
        return -1;
    }

    /** Copie la regle en resolvant les tests de biome. -1 = branche morte.
     *
     *  Les regles produisant du bedrock ou du deepslate sont retirees : le
     *  remplissage les pose deja (socle en degrade, frontiere deepslate ondulee),
     *  et leur presence rendait la profondeur utile non bornee pour les 54 biomes,
     *  ce qui empechait d'arreter tot le parcours de l'arbre. */
    private int dropBedrock = -1, dropDeepslate = -1;

    private int foldRule(int n, int biome) {
        switch (type[n]) {
            case BLOCK: {
                if (ia[n] == dropBedrock || ia[n] == dropDeepslate) return -1;
                int m = alloc(); type[m] = BLOCK; ia[m] = ia[n]; return m;
            }
            case BANDS_RULE: {
                int m = alloc(); type[m] = type[n]; ia[m] = ia[n]; return m;
            }
            case COND: {
                int f = foldCond(ia[n], biome);
                if (f == 0) return -1;
                int then = foldRule(ib[n], biome);
                if (then < 0) return -1;
                if (f == 1) return then;                    // condition toujours vraie
                int m = alloc(); type[m] = COND; ia[m] = ia[n]; ib[m] = then; return m;
            }
            case SEQ: {
                int[] tmp = new int[kids[n].length]; int k = 0;
                for (int c : kids[n]) { int r = foldRule(c, biome); if (r >= 0) tmp[k++] = r; }
                if (k == 0) return -1;
                if (k == 1) return tmp[0];
                int m = alloc(); type[m] = SEQ; kids[m] = java.util.Arrays.copyOf(tmp, k); return m;
            }
            default: return -1;
        }
    }

    private void specializeByBiome() {
        dropBedrock = Palette.id("bedrock");
        dropDeepslate = Palette.id("deepslate");
        bioRoot = new int[64];
        bioDepth = new int[64];
        for (int b = 0; b < 64; b++) {
            bioRoot[b] = foldRule(root, b);
            bioDepth[b] = bioRoot[b] < 0 ? 0 : depthBound(bioRoot[b], Integer.MAX_VALUE);
        }
    }

    /** Borne de profondeur : pour chaque chemin menant a un bloc, la contrainte
     *  `stone_depth <= K` la plus serree ; on garde le maximum sur les chemins.
     *  MAX_VALUE si un chemin n'est borne par aucune condition de profondeur
     *  (cas des bandes de badlands, qui descendent loin). */
    private static final int SURFACE_DEPTH_MAX = 6;    // (bruit*2,75 + 3) plafonne

    private int depthBound(int n, int cur) {
        switch (type[n]) {
            case BLOCK: case BANDS_RULE:
                return cur;
            case COND: {
                int c = ia[n], next = cur;
                if (type[c] == STONE_DEPTH && ia[c] == 0) {          // surface_type = floor
                    int k = 1 + ib[c] + (ic[c] == 1 ? SURFACE_DEPTH_MAX : 0) + (int) fa[c];
                    next = Math.min(cur, k);
                }
                return depthBound(ib[n], next);
            }
            case SEQ: {
                int best = 0;
                for (int k : kids[n]) best = Math.max(best, depthBound(k, cur));
                return best;
            }
            default:
                return 0;
        }
    }

    /** Profondeur utile sous le sommet pour ce biome. */
    public int usefulDepth(int biome) { return bioDepth[biome & 63]; }

    /** Bandes de terracotta des badlands : 192 niveaux, majoritairement nues. */
    private static int[] buildBands(long seed) {
        int[] b = new int[192];
        Arrays.fill(b, Palette.id("terracotta"));
        String[] tint = {"orange_terracotta", "yellow_terracotta", "brown_terracotta",
                         "red_terracotta", "white_terracotta", "light_gray_terracotta"};
        long h = seed * 0x9E3779B97F4A7C15L + 0x1234567L;
        int y = 0;
        while (y < b.length) {
            h = h * 6364136223846793005L + 1442695040888963407L;
            y += 4 + (int) ((h >>> 33) % 9);
            h = h * 6364136223846793005L + 1442695040888963407L;
            int thick = 1 + (int) ((h >>> 35) % 4);
            h = h * 6364136223846793005L + 1442695040888963407L;
            int col = Palette.id(tint[(int) ((h >>> 37) % tint.length)]);
            for (int k = 0; k < thick && y + k < b.length; k++) b[y + k] = col;
            y += thick;
        }
        return b;
    }

    /** Etat d'une colonne pendant l'application des regles. */
    public static final class Ctx {
        public int x, y, z, biome;
        public int surfaceDepth, stoneDepthAbove, stoneDepthBelow;
        public int waterHeight = Integer.MIN_VALUE;
        public int surfaceTop;                     // altitude du sommet solide
        public boolean steep;
        public float temperature;
        /** Les bruits de surface ne dependent que de (x, z) : on les evalue une fois
         *  par colonne au lieu d'une fois par bloc. Sans ce cache, les regles
         *  coutaient plus cher que tout le reste du decodage reuni. */
        float[] nv;
        boolean[] nok;
    }

    /** A appeler une fois par colonne, avant de parcourir ses blocs. */
    public void beginColumn(Ctx c) {
        if (c.nv == null) { c.nv = new float[noise.length]; c.nok = new boolean[noise.length]; }
        java.util.Arrays.fill(c.nok, false);
    }

    private float noiseAt(int i, Ctx c) {
        if (!c.nok[i]) { c.nv[i] = (float) noise[i].get2(c.x, c.z); c.nok[i] = true; }
        return c.nv[i];
    }

    /** Profondeur de surface vanilla, sans le jitter aleatoire de +/-0,25 bloc.
     *  Utilise le cache de colonne : appeler beginColumn() avant. */
    public int surfaceDepthAt(Ctx c) {
        return (int) (noiseAt(0, c) * 2.75 + 3.0);
    }

    /** Identifiant du bloc a poser, ou -1 si aucune regle ne s'applique. */
    public int apply(Ctx c) {
        int r = bioRoot[c.biome & 63];
        return r < 0 ? -1 : rule(r, c);
    }

    public int nodeCount() { return nNodes; }

    private int rule(int n, Ctx c) {
        switch (type[n]) {
            case SEQ:
                for (int k : kids[n]) { int r = rule(k, c); if (r >= 0) return r; }
                return -1;
            case COND:
                return cond(ia[n], c) ? rule(ib[n], c) : -1;
            case BLOCK:
                return ia[n];
            case BANDS_RULE: {
                int i = c.y - Blocks.MIN_Y;
                return bands[((i % bands.length) + bands.length) % bands.length];
            }
            default:
                return -1;
        }
    }

    private static double map(double v, double a, double b, double c, double d) {
        return c + (v - a) * (d - c) / (b - a);
    }

    private boolean cond(int n, Ctx c) {
        switch (type[n]) {
            case BIOME:
                return (biomeSet[ia[n]] & (1L << c.biome)) != 0;
            case NOISE: {
                float v = noiseAt(ia[n], c);
                return v >= fa[n] && v <= fb[n];
            }
            case STONE_DEPTH: {
                int depth = ia[n] == 1 ? c.stoneDepthBelow : c.stoneDepthAbove;
                int add = ic[n] == 1 ? c.surfaceDepth : 0;
                int range = (int) fa[n];
                int sec = 0;
                if (range != 0 && secondaryNoiseIdx >= 0)
                    sec = (int) map(noiseAt(secondaryNoiseIdx, c), -1, 1, 0, range);
                return depth <= 1 + ib[n] + add + sec;
            }
            case WATER: {
                if (c.waterHeight == Integer.MIN_VALUE) return true;
                int y = c.y + (ic[n] == 1 ? c.stoneDepthAbove : 0);
                return y >= c.waterHeight + ia[n] + c.surfaceDepth * ib[n];
            }
            case Y_ABOVE: {
                int y = c.y + (ic[n] == 1 ? c.stoneDepthAbove : 0);
                return y >= ia[n] + c.surfaceDepth * ib[n];
            }
            case STEEP:
                return c.steep;
            case NOT:
                return !cond(ia[n], c);
            case HOLE:
                return c.surfaceDepth <= 0;
            case VGRAD: {
                double d = map(c.y, ia[n], ib[n], 1.0, 0.0);
                if (d >= 1) return true;
                if (d <= 0) return false;
                long h = (c.x * 0x9E3779B97F4A7C15L) ^ (c.y * 0xC2B2AE3D27D4EB4FL)
                       ^ (c.z * 0x165667B19E3779F9L);
                h ^= h >>> 30; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 31;
                return ((h >>> 40) / (double) (1 << 24)) < d;
            }
            case BANDS:
                return true;
            case ABOVE_PS:
                return c.y >= c.surfaceTop - 8;
            case TEMP:
                return c.temperature < -0.15f || c.y >= 130;
            default:
                return false;
        }
    }
}
