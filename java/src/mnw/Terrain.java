package mnw;

/** Generateur procedural de latent, portage du generateur de reference Python.
 *  Fonction pure de (seed, coordonnees monde) : monde infini, deterministe,
 *  aucun ordre de generation, aucun stockage. */
public final class Terrain {
    public static final int SEA = 63;

    /**
     * Tout ce qui depend de la graine, pour UN monde.
     *
     * <p>Le generateur a toujours ete une fonction pure de (graine, coordonnees) :
     * il fonctionne sur n'importe quelle graine. Ce qui empechait deux mondes de
     * coexister n'etait pas l'algorithme mais quatre champs {@code static}, qui
     * levaient des qu'on demandait une seconde graine dans la meme JVM.</p>
     *
     * <p>Ce qui depend de la graine : le modele de hauteur ({@link VanillaTerrain})
     * et les surface rules, dont les bruits sont semes. Le reste ne depend que des
     * fichiers de donnees vanilla, identiques pour tous les mondes, et reste donc
     * partage : la table climatique (410 Ko de boites), les tables de biomes froids
     * et de badlands, et la palette de blocs.</p>
     */
    public static final class Tables {
        public final long seed;
        public final VanillaTerrain vt;
        public final SurfaceRules sr;
        Tables(long seed, VanillaTerrain vt, SurfaceRules sr) {
            this.seed = seed; this.vt = vt; this.sr = sr;
        }
        public BiomeTable biomes()          { return BT; }
        public boolean isCold(int biome)    { return COLD[biome < COLD.length ? biome : 0]; }
        public boolean isBadlands(int biome) { return BADLANDS[biome < BADLANDS.length ? biome : 0]; }
    }

    private static final java.util.concurrent.ConcurrentHashMap<Long, Tables> WORLDS =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Partage entre tous les mondes : ne depend que des donnees vanilla. */
    private static BiomeTable BT;
    private static boolean[] COLD, BADLANDS;
    /** Le premier monde initialise, pour les outils de diagnostic mono-monde. */
    private static volatile Tables DEFAULT;

    /** Ordre impose : les surface rules construisent la palette de blocs, donc
     *  elles doivent etre chargees avant toute utilisation des constantes Blocks. */
    public static Tables tables(long seed) {
        Tables t = WORLDS.get(seed);
        if (t != null) return t;
        synchronized (Terrain.class) {
            t = WORLDS.get(seed);
            if (t != null) return t;
            try {
                SurfaceRules sr = new SurfaceRules(Res.open("vanilla_surface.bin"), seed);
                VanillaTerrain vt = new VanillaTerrain(seed);
                if (BT == null) {
                    BiomeTable bt = new BiomeTable(Res.open("vanilla_biomes.bin"));
                    boolean[] cold = new boolean[bt.names.length];
                    boolean[] bad  = new boolean[bt.names.length];
                    for (int i = 0; i < cold.length; i++) {
                        String n = bt.names[i];
                        cold[i] = n.startsWith("snowy_") || n.startsWith("frozen_")
                               || n.equals("ice_spikes") || n.endsWith("_peaks") || n.equals("grove");
                        bad[i] = n.endsWith("badlands");
                    }
                    COLD = cold; BADLANDS = bad; BT = bt;   // BT en dernier : il sert de garde
                }
                t = new Tables(seed, vt, sr);
                WORLDS.put(seed, t);
                if (DEFAULT == null) DEFAULT = t;
                return t;
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
        }
    }

    /** Le premier monde initialise. Les outils de diagnostic n'en ont qu'un ; le
     *  plugin, lui, passe explicitement les tables de chaque monde. */
    public static Terrain.Tables defaultTables() {
        if (DEFAULT == null) throw new IllegalStateException(
            "aucun monde initialise : appeler Terrain.tables(seed) d'abord");
        return DEFAULT;
    }

    /** Nombre de mondes actuellement charges. */
    public static int worlds() { return WORLDS.size(); }

    // -- API mono-monde, conservee pour les outils de diagnostic et les bancs --
    public static VanillaTerrain vanilla(long seed)  { return tables(seed).vt; }
    public static BiomeTable biomes()                { return BT; }
    public static SurfaceRules surfaceRules()        { return DEFAULT.sr; }
    public static boolean isCold(int biome)          { return COLD[biome < COLD.length ? biome : 0]; }
    public static boolean isBadlands(int b)          { return BADLANDS[b < BADLANDS.length ? b : 0]; }

    private static double spline(double x, double[] xs, double[] ys) {
        if (x <= xs[0]) return ys[0];
        for (int i = 0; i < xs.length - 1; i++)
            if (x < xs[i + 1]) { double t = (x - xs[i]) / (xs[i + 1] - xs[i]); return ys[i] + t * (ys[i + 1] - ys[i]); }
        return ys[ys.length - 1];
    }
    private static final double[] CX = {-1.2,-0.55,-0.2,-0.05,0.12,0.45,1.2};
    private static final double[] CY = {-52,-34,-12,2,18,34,52};
    private static final double[] EX = {-1.2,-0.5,0.05,0.5,1.2};
    private static final double[] EY = {1.0,0.78,0.42,0.16,0.07};
    private static double clamp(double v, double a, double b) { return v < a ? a : (v > b ? b : v); }

    /** [hauteur, biome] pour une colonne. */
    private static void column(double wx, double wz, long seed, double[] out) {
        double warpX = wx + NoiseJ.fbm2(wx/900, wz/900, seed+91, 3) * 260;
        double warpZ = wz + NoiseJ.fbm2(wx/900+17, wz/900+17, seed+92, 3) * 260;
        double cont = clamp(NoiseJ.fbm2(warpX/2600, warpZ/2600, seed+1, 5) * 2.1, -1.2, 1.2);
        double ero  = clamp(NoiseJ.fbm2(wx/1500, wz/1500, seed+2, 4) * 2.0, -1.2, 1.2);
        double temp = clamp(NoiseJ.fbm2(wx/3400, wz/3400, seed+3, 3) * 2.2, -1.2, 1.2);
        double humi = clamp(NoiseJ.fbm2(wx/2300, wz/2300, seed+4, 3) * 2.2, -1.2, 1.2);
        double ridge = NoiseJ.fbm2(wx/620, wz/620, seed+5, 5);
        double pv = clamp((1 - Math.abs(ridge)) * 1.6 - 0.62, -1.2, 1.2) * 3.0;

        // bruit fin pour casser la regularite des frontieres de biome
        temp += NoiseJ.perlin2(wx/110, wz/110, seed+31) * 0.13;
        humi += NoiseJ.perlin2(wx/95, wz/95, seed+32) * 0.13;

        double base = spline(cont, CX, CY), flat = spline(ero, EX, EY);
        double land = Math.pow(clamp(cont + 0.05, 0, 1), 0.6);
        double det = NoiseJ.fbm2(wx/90, wz/90, seed+6, 4) * 4.5 * (0.25 + flat);
        double h = 62 + base + pv * 104 * flat * land + det;

        // domain warp : sans lui le contour zero du bruit donne des rivieres rectilignes
        double rwx = wx + NoiseJ.fbm2(wx/430, wz/430, seed+41, 3) * 340;
        double rwz = wz + NoiseJ.fbm2(wx/430+31, wz/430+31, seed+42, 3) * 340;
        double riv = NoiseJ.ridged1(rwx/1250, rwz/1250, seed+7);
        double rmask = clamp((riv - 0.9825) * 95, 0, 1) * land;
        h -= rmask * Math.max(0, h - (SEA - 4)) * 0.94;
        h = clamp(h, -58, 300);
        out[0] = h;
        out[1] = biome(cont, ero, temp, humi, pv, h, rmask);
    }

    private static int biomeFromClimate(float[] c, float h) {
        return biome(c[2], c[3], c[0], c[1], c[5], h, 0);
    }

    private static int biome(double cont, double ero, double temp, double humi, double pv, double h, double rmask) {
        int b = 12; // plains
        boolean warm = temp > 0.15, hot = temp > 0.55, cold = temp < -0.15, frozen = temp < -0.5;
        boolean wet = humi > 0.2, dry = humi < -0.25;
        if (!dry && !wet && !cold && !hot) b = 13;            // forest
        if (wet && warm && !hot) b = 13;
        if (wet && !warm && !cold) b = 15;                    // dark forest
        if (dry && warm) b = 17;                              // savanna
        if (dry && hot) b = 19;                               // desert
        if (dry && hot && ero < -0.35) b = 20;                // badlands
        if (wet && hot) b = 18;                               // jungle
        if (cold) b = 8;                                      // taiga
        if (cold && wet) b = 9;
        if (frozen) b = 5;
        if (frozen && dry) b = 4;
        if (wet && !cold && ero > 0.45 && h < SEA + 6 && h > SEA - 2) b = 16;   // swamp
        if (h > 88 && h < 120 && ero > 0.3 && !cold && pv < 0.2) b = 11;        // meadow
        if (h > 108) b = temp < -0.1 ? 7 : 10;
        if (h > 132) b = temp < 0.1 ? 6 : 10;
        if (h > 168) b = 22;
        if (h > 168 && pv > 0.55) b = 21;
        if (rmask > 0.35) b = 3;                              // river
        if (h < SEA - 1) b = 1;
        if (h < SEA - 26) b = 0;
        if (h >= SEA - 1 && h <= SEA + 2 && rmask < 0.35) b = 2;   // beach
        return b;
    }

    /** Densite de cave : >0 = creuse. Seuils calibres sur les quantiles mesures
     *  des bruits pour viser ~4,5 % du volume souterrain, ordre de grandeur de
     *  vanilla. Trois familles, comme 1.18+ : spaghetti, fromage, nouilles.
     *
     *  Evalue par colonne : les quatre bruits partagent le meme (x, z) sur les 49
     *  niveaux, ce que NoiseJ.Col exploite. */
    static final class CaveCol {
        private final NoiseJ.Col s1 = new NoiseJ.Col(), s2 = new NoiseJ.Col(),
                                 ch = new NoiseJ.Col(), nd = new NoiseJ.Col();

        void begin(double wx, double wz, long seed) {
            s1.begin(wx / 190, wz / 190, seed + 11);
            s2.begin(wx / 175 + 9, wz / 175 + 9, seed + 12);
            ch.begin(wx / 300, wz / 300, seed + 13);
            nd.begin(wx / 85, wz / 85, seed + 14);
        }

        double at(double wy) {
            double a = 1 - Math.abs(s1.at(wy / 105));
            double b = 1 - Math.abs(s2.at(wy / 98));
            double d = (Math.min(a, b) - 0.940) * 300;                 // spaghetti, ~2,3 %

            // Les cavernes "fromage" sont eteintes au-dessus de y=34 par leur
            // porte : inutile d'evaluer le bruit la-haut.
            if (wy < 34) {
                double c = Math.abs(ch.at(wy / 175));
                double gate = clamp((34 - wy) / 55, 0, 1);
                double cheese = (c - 0.615) * 260 * gate - (1 - gate) * 60;
                if (cheese > d) d = cheese;
            }
            // Les nouilles ne descendent pas sous la roche profonde ni au-dessus de y=50.
            if (wy < 50) {
                double n = 1 - Math.abs(nd.at(wy / 60));
                double noodle = (n - 0.992) * 900;
                if (noodle > d) d = noodle;
            }
            return d;
        }
    }

    /** Remplit le latent d'un patch dont le coin est en (px, pz) blocs monde. */
    public static Patch patch(long seed, int px, int pz) { return patch(tables(seed), px, pz); }

    public static Patch patch(Tables t, int px, int pz) {
        Patch p = new Patch();
        final long seed = t.seed;
        VanillaTerrain vt = t.vt;
        BiomeTable bt = BT;
        float[] clim = new float[6], sp = new float[4], q = new float[7];
        BiomeTable.Cursor cur = new BiomeTable.Cursor();
        float[] sd = new float[bt.specials()];
        for (int gx = 0; gx < Patch.H_N; gx++)
            for (int gz = 0; gz < Patch.H_N; gz++) {
                double h = vt.column(px + gx * Patch.H_RES, pz + gz * Patch.H_RES, clim, sp);
                h = clamp(h, -58, 315);
                p.h[gx * Patch.H_N + gz] = (short) Math.round(h * 16);
                System.arraycopy(clim, 0, q, 0, 6);
                int cell = gx * Patch.H_N + gz;
                p.s[cell] = (byte) bt.find(q, cur);
                p.bd[cell * Patch.BD] = cur.dist;
                bt.specialDists(q, sd);
                System.arraycopy(sd, 0, p.bd, cell * Patch.BD + 1, sd.length);
            }
        // Le champ de caves n'a de sens que SOUS le terrain. Les niveaux au-dessus
        // resteraient a "plein" de toute facon : on ne les calcule pas. En ocean et
        // en plaine, cela supprime la moitie des echantillons.
        java.util.Arrays.fill(p.c, (byte) -128);
        CaveCol col = new CaveCol();
        for (int cx = 0; cx < Patch.C_N; cx++)
            for (int cz = 0; cz < Patch.C_N; cz++) {
                int surf = p.h[cx * Patch.H_N + cz] >> 4;          // point fixe 1/16 -> blocs
                int top = Math.min(surf, Patch.CAVE_MAX_Y);
                int kMax = Math.min(Patch.C_NY - 1, ((top - Patch.CAVE_MIN_Y) >> 2) + 1);
                int base = (cx * Patch.C_N + cz) * Patch.C_NY;
                col.begin(px + cx * Patch.C_RES, pz + cz * Patch.C_RES, seed);
                for (int k = 0; k <= kMax; k++) {
                    double d = col.at(Patch.CAVE_MIN_Y + k * Patch.C_RES);
                    p.c[base + k] = (byte) clamp(d, -128, 127);
                }
            }
        p.worms = Carver.collect(seed, px, pz);
        return p;
    }
}
