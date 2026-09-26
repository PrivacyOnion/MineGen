package mnw;

/**
 * Modele de cout de la worldgen vanilla 1.21.11, pour comparaison honnete.
 *
 * Il fait exactement le meme travail que MineGen -- meme climat, memes biomes
 * vanilla, memes surface rules vanilla -- SAUF pour la densite du terrain, qu'il
 * calcule comme vanilla : un routeur de bruit 3D evalue sur une grille de 4 blocs
 * en XZ et 8 en Y (5 x 5 x 49 coins par chunk), a 15 octaves de Perlin 3D, puis
 * interpole pour chaque bloc.
 *
 * La difference mesuree entre les deux est donc exactement ce que l'on remplace :
 * le passage d'un champ de densite 3D a une carte de hauteur 2D. Comparer contre
 * une baseline sans surface rules gonflerait artificiellement le gain.
 *
 * Ce n'est pas Paper : c'est une reproduction de sa structure de cout. Les 15
 * octaves sont une estimation basse du routeur overworld.
 */
public final class VanillaBaseline {
    private static final int CELL_XZ = 4, CELL_Y = 8;
    private static final int NX = 16 / CELL_XZ + 1;            // 5
    private static final int NY = Blocks.HEIGHT / CELL_Y + 1;  // 49
    private static final int OCTAVES = 15;

    private final Perlin[] oct = new Perlin[OCTAVES];
    private final double[] dens = new double[NX * NX * NY];
    private final int[] biome = new int[NX * NX];
    private final int[] topY = new int[256];
    private final float[] clim = new float[6], sp = new float[4], q = new float[7];
    private final BiomeTable.Cursor cur = new BiomeTable.Cursor();
    private final SurfaceRules.Ctx ctx = new SurfaceRules.Ctx();

    public VanillaBaseline(long seed) {
        Terrain.vanilla(seed);
        for (int i = 0; i < OCTAVES; i++) oct[i] = new Perlin(seed + i * 0x9E3779B9L);
    }

    /** Le routeur de bruit : somme d'octaves 3D + gradient vertical de densite. */
    private double router(double wx, double wy, double wz) {
        double v = 0, amp = 1, freq = 1.0 / 256.0;
        for (int i = 0; i < OCTAVES; i++) {
            v += amp * oct[i].noise(wx * freq, wy * freq, wz * freq);
            amp *= 0.55; freq *= 1.9;
        }
        return v - (wy - 64) * 0.012;
    }

    public void generate(int cx, int cz, int[] out) {
        java.util.Arrays.fill(out, Blocks.AIR);
        VanillaTerrain vt = Terrain.vanilla(0);
        BiomeTable bt = Terrain.biomes();
        SurfaceRules sr = Terrain.surfaceRules();

        // --- climat + biomes, comme vanilla (une colonne par cellule de 4 blocs) ---
        for (int gx = 0; gx < NX; gx++)
            for (int gz = 0; gz < NX; gz++) {
                vt.column(cx * 16 + gx * CELL_XZ, cz * 16 + gz * CELL_XZ, clim, sp);
                System.arraycopy(clim, 0, q, 0, 6);
                biome[gx * NX + gz] = bt.find(q, cur);
            }

        // --- densite 3D : la partie que MineGen remplace ---
        for (int gx = 0; gx < NX; gx++)
            for (int gz = 0; gz < NX; gz++)
                for (int gy = 0; gy < NY; gy++)
                    dens[(gx * NX + gz) * NY + gy] =
                        router(cx * 16 + gx * CELL_XZ, Blocks.MIN_Y + gy * CELL_Y, cz * 16 + gz * CELL_XZ);

        for (int lx = 0; lx < 16; lx++) {
            int gx = lx / CELL_XZ; double tx = (lx % CELL_XZ) / (double) CELL_XZ;
            for (int lz = 0; lz < 16; lz++) {
                int gz = lz / CELL_XZ; double tz = (lz % CELL_XZ) / (double) CELL_XZ;
                int b00 = (gx * NX + gz) * NY, b10 = ((gx + 1) * NX + gz) * NY;
                int b01 = (gx * NX + gz + 1) * NY, b11 = ((gx + 1) * NX + gz + 1) * NY;
                int top = Blocks.MIN_Y - 1;
                int base = Blocks.idx(lx, Blocks.MIN_Y, lz);
                for (int y = Blocks.MIN_Y; y < Blocks.MIN_Y + Blocks.HEIGHT; y++) {
                    int gy = (y - Blocks.MIN_Y) / CELL_Y;
                    double ty = ((y - Blocks.MIN_Y) % CELL_Y) / (double) CELL_Y;
                    double d0 = lerp(tx, lerp(ty, dens[b00 + gy], dens[b00 + gy + 1]),
                                         lerp(ty, dens[b10 + gy], dens[b10 + gy + 1]));
                    double d1 = lerp(tx, lerp(ty, dens[b01 + gy], dens[b01 + gy + 1]),
                                         lerp(ty, dens[b11 + gy], dens[b11 + gy + 1]));
                    double d = lerp(tz, d0, d1);
                    if (d > 0) { out[base + (y - Blocks.MIN_Y)] = Blocks.STONE; top = y; }
                    else if (y < Blocks.SEA_LEVEL) out[base + (y - Blocks.MIN_Y)] = Blocks.WATER;
                }
                topY[(lx << 4) | lz] = top;
            }
        }

        // --- surface rules vanilla : meme travail que MineGen ---
        for (int lx = 0; lx < 16; lx++) {
            int gx = Math.min(lx / CELL_XZ, NX - 1);
            for (int lz = 0; lz < 16; lz++) {
                int gz = Math.min(lz / CELL_XZ, NX - 1);
                int top = topY[(lx << 4) | lz];
                if (top <= Blocks.MIN_Y + 5) continue;
                ctx.x = cx * 16 + lx; ctx.z = cz * 16 + lz;
                sr.beginColumn(ctx);
                ctx.biome = biome[gx * NX + gz];
                ctx.surfaceDepth = sr.surfaceDepthAt(ctx);
                ctx.surfaceTop = top;
                ctx.waterHeight = top < Blocks.SEA_LEVEL - 1 ? top + 1 : Integer.MIN_VALUE;
                ctx.stoneDepthBelow = 64;
                ctx.temperature = Terrain.isCold(ctx.biome) ? -1f : 1f;
                ctx.steep = false;
                int band = Terrain.isBadlands(ctx.biome) ? 26 : Math.max(4, ctx.surfaceDepth + 5);
                int base = Blocks.idx(lx, Blocks.MIN_Y, lz);
                int lo = Math.max(Blocks.MIN_Y + 5, top - band);
                for (int y = top; y >= lo; y--) {
                    ctx.y = y; ctx.stoneDepthAbove = top - y;
                    int b = sr.apply(ctx);
                    if (b >= 0) out[base + (y - Blocks.MIN_Y)] = b;
                }
            }
        }

        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int base = Blocks.idx(lx, Blocks.MIN_Y, lz);
                for (int y = Blocks.MIN_Y; y < Blocks.MIN_Y + 5; y++)
                    out[base + (y - Blocks.MIN_Y)] = Blocks.BEDROCK;
            }
    }

    private static double lerp(double t, double a, double b) { return a + t * (b - a); }
}
