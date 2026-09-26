package mnw;

/**
 * Decodeur runtime : latent de patch -> blocs. Java pur, aucune convolution,
 * aucune bibliotheque native. C'est le seul composant destine au serveur de jeu.
 *
 * Ordre des etapes, celui de vanilla : remplissage depuis la hauteur, puis
 * application des surface rules, puis creusement (champ de caves et tunnels).
 * Les carvers passent apres la surface, donc ils entament aussi la terre et
 * l'herbe -- comme en vanilla.
 */
public final class ChunkDecoder {
    /** Le monde auquel ce decodeur appartient. Sans lui il lirait les surface rules
     *  du premier monde charge : terrain juste, surface fausse, des qu'une seconde
     *  graine existe dans la meme JVM. */
    private final Terrain.Tables tables;

    /** Pour les outils de diagnostic, qui n'ont qu'un monde. */
    public ChunkDecoder() { this(Terrain.defaultTables()); }
    public ChunkDecoder(Terrain.Tables tables) { this.tables = tables; }

    /** Sous ce niveau, les vides se remplissent de lave. */
    private static final int LAVA_LEVEL = -54;
    /** Les badlands empilent des bandes de terracotta loin sous la surface ;
     *  partout ailleurs, plus rien ne s'applique au-dela de la profondeur de
     *  surface plus quelques blocs. Evaluer 20 niveaux partout coutait le double. */
    private static final int BAND_BADLANDS = 26;

    /** Masque de phases, uniquement pour le profilage par ablation (bit 0 =
     *  remplissage, 1 = surface rules, 2 = champ de caves, 3 = creusement). */
    public static int PHASES = 0xF;

    private final float[] col = new float[Patch.C_NY];
    private final int[] fluid = new int[256];
    private final int[] topY = new int[256];
    private final SurfaceRules.Ctx ctx = new SurfaceRules.Ctx();

    public void decode(Patch t, int cx, int cz, int[] out) { decode(t, cx, cz, 0, 0, out); }

    /** wpx, wpz : coin monde du patch, necessaire au detail et aux surface rules. */
    public void decode(Patch t, int cx, int cz, long wpx, long wpz, int[] out) {
        final int HN = Patch.H_N;
        final SurfaceRules sr = tables.sr;

        // --- 1. remplissage : pierre / deepslate jusqu'au sommet, eau jusqu'a la mer ---
        for (int lx = 0; lx < 16; lx++) {
            int x = cx * 16 + lx;
            for (int lz = 0; lz < 16; lz++) {
                int z = cz * 16 + lz;

                int top = t.heightAt(x, z, wpx, wpz);
                if (top < Blocks.MIN_Y) top = Blocks.MIN_Y;

                int slot = (lx << 4) | lz;
                topY[slot] = top;

                int base = Blocks.idx(lx, Blocks.MIN_Y, lz);
                int dsTop = Detail.deepslateTop(wpx + x, wpz + z);
                // La colonne est contigue et couverte en entier : quatre Arrays.fill
                // qui se suivent, sans effacement prealable du chunk. Effacer puis
                // recrire coutait un tiers d'ecritures memoire en plus.
                int dsEnd = Math.min(Math.max(dsTop, Blocks.MIN_Y), top + 1) - Blocks.MIN_Y;
                int solidEnd = (top - Blocks.MIN_Y) + 1;
                java.util.Arrays.fill(out, base, base + dsEnd, Blocks.DEEPSLATE);
                java.util.Arrays.fill(out, base + dsEnd, base + solidEnd, Blocks.STONE);
                int seaEnd = solidEnd;
                // Vanilla remplit d'eau tout y < sea_level : le bloc d'eau le plus haut
                // est donc y=62, pas 63. Un bloc de trop noyait toutes les plages.
                if (top < Blocks.SEA_LEVEL - 1) {
                    seaEnd = Blocks.SEA_LEVEL - Blocks.MIN_Y;
                    java.util.Arrays.fill(out, base + solidEnd, base + seaEnd, Blocks.WATER);
                }
                java.util.Arrays.fill(out, base + seaEnd, base + Blocks.HEIGHT, Blocks.AIR);

                int wt = Detail.waterTable(wpx + x, wpz + z);
                // Les cavites sous l'ocean ne sont PAS noyees.
                //
                // Elles l'etaient, colonne par colonne : chaque colonne dont la
                // surface passait sous le niveau de la mer voyait toute sa colonne
                // remplie jusqu'a y=62. Au trait de cote, une colonne noyee se
                // retrouvait donc contre une colonne seche, et l'eau avait de l'air
                // a cote d'elle sur toute la hauteur de la cavite.
                //
                // Le cout n'etait pas cosmetique. Chaque bloc d'eau instable
                // declenche un tick de fluide quand le chunk devient actif ; le
                // fluide s'ecoule, met a jour ses voisins, et si le voisin est dans
                // un chunk absent, le thread PRINCIPAL le charge de facon synchrone
                // et s'arrete jusqu'a ce qu'il arrive. Pendant ce temps aucun autre
                // chunk n'est promu : le joueur reste bloque dans le sien.
                // Mesure sur profil spark : 1 304 ms de syncLoad, sous
                // LevelChunk.postProcessGeneration -> FlowingFluid.spread.
                //
                // Mesure de l'effet, 144 chunks : 942 -> 17 blocs d'eau ayant de
                // l'air lateral, soit 7 -> 0 tick de fluide par chunk.
                fluid[slot] = wt == Detail.DRY ? Integer.MIN_VALUE : Math.min(wt, top - 2);
            }
        }

        // --- 2. surface rules vanilla ---
        if ((PHASES & 2) != 0)
        for (int lx = 0; lx < 16; lx++) {
            int x = cx * 16 + lx;
            int hx = x >> 2;
            for (int lz = 0; lz < 16; lz++) {
                int z = cz * 16 + lz;
                int hz = z >> 2;
                int slot = (lx << 4) | lz;
                int top = topY[slot];
                if (top <= Blocks.MIN_Y + 5) continue;

                int wx = (int) (wpx + x), wz = (int) (wpz + z);
                ctx.x = wx; ctx.z = wz;
                sr.beginColumn(ctx);
                ctx.biome = t.s[hx * HN + hz] & 0xFF;
                ctx.surfaceDepth = sr.surfaceDepthAt(ctx);
                ctx.surfaceTop = top;
                ctx.waterHeight = top < Blocks.SEA_LEVEL - 1 ? top + 1 : Integer.MIN_VALUE;
                ctx.stoneDepthBelow = 64;                 // pas de surplombs : la roche descend loin
                ctx.temperature = tables.isCold(ctx.biome) ? -1f : 1f;
                // "steep" de vanilla : denivele d'au moins 4 blocs entre z-1 et z+1
                int hzm = Math.max(hz - 1, 0), hzp = Math.min(hz + 1, HN - 1);
                ctx.steep = (t.h[hx * HN + hzp] >> 4) >= (t.h[hx * HN + hzm] >> 4) + 4;

                // borne exacte issue de l'arbre specialise, plafonnee par la bande
                int band = Math.min(sr.usefulDepth(ctx.biome),
                                    tables.isBadlands(ctx.biome) ? BAND_BADLANDS
                                                                  : Math.max(4, ctx.surfaceDepth + 5));
                int base = Blocks.idx(lx, Blocks.MIN_Y, lz);
                int lo = Math.max(Blocks.MIN_Y + 5, top - band);
                for (int y = top; y >= lo; y--) {
                    ctx.y = y;
                    ctx.stoneDepthAbove = top - y;
                    int b = sr.apply(ctx);
                    if (b >= 0) out[base + (y - Blocks.MIN_Y)] = b;
                }
            }
        }

        // --- 3. creusement : champ de caves, puis tunnels ---
        final int CN = Patch.C_N, CNY = Patch.C_NY;
        if ((PHASES & 4) != 0)
        for (int lx = 0; lx < 16; lx++) {
            int x = cx * 16 + lx;
            int qx = x >> 2;  float ux = (x & 3) * 0.25f;
            for (int lz = 0; lz < 16; lz++) {
                int z = cz * 16 + lz;
                int qz = z >> 2;  float uz = (z & 3) * 0.25f;
                int slot = (lx << 4) | lz;
                int top = topY[slot];
                int base = Blocks.idx(lx, Blocks.MIN_Y, lz);

                int cap = Math.min(top - 6, Patch.CAVE_MAX_Y);   // 6 blocs de toit sous la surface
                int kMax = Math.min(CNY - 1, ((cap - Patch.CAVE_MIN_Y) >> 2) + 1);
                if (kMax < 1) continue;
                int cb = (qx * CN + qz) * CNY, cbx = cb + CN * CNY, cbz = cb + CNY, cbxz = cbx + CNY;
                for (int k = 0; k <= kMax; k++) {
                    float c00 = t.c[cb + k], c10 = t.c[cbx + k], c01 = t.c[cbz + k], c11 = t.c[cbxz + k];
                    col[k] = (c00 + (c10 - c00) * ux) * (1 - uz) + (c01 + (c11 - c01) * ux) * uz;
                }
                // La densite est lineaire entre col[k] et col[k+1] : si les deux
                // bouts sont negatifs, les quatre blocs du segment le sont aussi.
                // Les caves occupent ~4,5 % du volume, donc on saute presque tout.
                final int wt = fluid[slot];
                for (int k = 0; k < kMax; k++) {
                    float a = col[k], b = col[k + 1];
                    if (a <= 0f && b <= 0f) continue;
                    int y0 = Patch.CAVE_MIN_Y + (k << 2);
                    float step = (b - a) * 0.25f;
                    int n = Math.min(4, cap - y0 + 1);
                    for (int j = 0; j < n; j++)
                        if (a + step * j > 0f)
                            out[base + (y0 + j - Blocks.MIN_Y)] = fill(y0 + j, wt);
                }
            }
        }
        if ((PHASES & 8) != 0) carveWorms(t, cx, cz, out);

        // --- 4. socle ---
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int base = Blocks.idx(lx, Blocks.MIN_Y, lz);
                long wx = wpx + cx * 16 + lx, wz = wpz + cz * 16 + lz;
                for (int y = Blocks.MIN_Y; y < Blocks.MIN_Y + 5; y++)
                    if (Detail.bedrock(wx, y, wz)) out[base + (y - Blocks.MIN_Y)] = Blocks.BEDROCK;
            }
    }

    /** Ce qui remplit un vide creuse : lave en profondeur, eau sous la nappe, air sinon. */
    private static int fill(int y, int waterTable) {
        if (y <= LAVA_LEVEL) return Blocks.LAVA;
        return y <= waterTable ? Blocks.WATER : Blocks.AIR;
    }

    /** Creuse les tunnels et ravins du patch qui croisent ce chunk.
     *  Section ellipsoidale : les ravins ont un rayon vertical trois fois le
     *  rayon horizontal, comme le yScale de canyon.json. */
    private void carveWorms(Patch t, int cx, int cz, int[] out) {
        final float[] w = t.worms;
        final int ox = cx * 16, oz = cz * 16;
        for (int i = 0; i < w.length; i += Carver.STRIDE) {
            float sx = w[i] - ox, sy = w[i + 1], sz = w[i + 2] - oz;
            float rh = w[i + 3], rv = w[i + 4];
            if (sx < -rh || sx > 15 + rh || sz < -rh || sz > 15 + rh) continue;
            int y0 = Math.max(Blocks.MIN_Y + 5, (int) Math.floor(sy - rv));
            int y1 = Math.min(Blocks.MIN_Y + Blocks.HEIGHT - 1, (int) Math.ceil(sy + rv));
            int x0 = Math.max(0, (int) Math.floor(sx - rh)), x1 = Math.min(15, (int) Math.ceil(sx + rh));
            int z0 = Math.max(0, (int) Math.floor(sz - rh)), z1 = Math.min(15, (int) Math.ceil(sz + rh));
            float irh2 = 1f / (rh * rh), irv2 = 1f / (rv * rv);
            for (int x = x0; x <= x1; x++) {
                float dx = x + 0.5f - sx, dx2 = dx * dx;
                for (int z = z0; z <= z1; z++) {
                    float dz = z + 0.5f - sz;
                    float base = (dx2 + dz * dz) * irh2;
                    if (base > 1f) continue;
                    int slot = (x << 4) | z;
                    // Les carvers vanilla debouchent a la surface : c'est ce qui rend
                    // les ravins visibles depuis le ciel. Mais SOUS L'OCEAN, deboucher
                    // signifie percer le plancher : l'eau se retrouve avec de l'air
                    // juste en dessous et se met a tomber. Chaque bloc qui tombe
                    // declenche un tick de fluide au chargement du chunk, une mise a
                    // jour de voisinage, et souvent un chargement SYNCHRONE du chunk
                    // voisin depuis le thread principal -- 47 % du temps de tick sur
                    // le profil spark fQn1VynkdO.
                    // Vanilla resout cela autrement : ses carvers s'arretent quand ils
                    // rencontrent de l'eau. On approxime en laissant le meme toit de
                    // 6 blocs que le champ de caves, mais seulement sous la mer : sur
                    // terre les ravins continuent de s'ouvrir normalement.
                    int colTop = topY[slot];
                    int hiY = Math.min(y1, colTop < Blocks.SEA_LEVEL - 1 ? colTop - 6 : colTop);
                    for (int y = y0; y <= hiY; y++) {
                        float dy = y + 0.5f - sy;
                        if (base + dy * dy * irv2 > 1f) continue;
                        int id = Blocks.idx(x, y, z);
                        int b = out[id];
                        if (b != Blocks.AIR && b != Blocks.BEDROCK && b != Blocks.WATER && b != Blocks.LAVA)
                            out[id] = fill(y, fluid[slot]);
                    }
                }
            }
        }
    }
}
