package mnw;

/** Identifiants de blocs, resolus depuis la palette des surface rules vanilla.
 *  Non "final" : ils dependent de la palette chargee au demarrage. */
public final class Blocks {
    public static int AIR, STONE, DEEPSLATE, DIRT, GRASS, SAND, WATER, BEDROCK,
                      SNOW, GRAVEL, LAVA;

    static void bind() {
        AIR = Palette.id("air");            STONE = Palette.id("stone");
        DEEPSLATE = Palette.id("deepslate");DIRT = Palette.id("dirt");
        GRASS = Palette.id("grass_block");  SAND = Palette.id("sand");
        WATER = Palette.id("water");        BEDROCK = Palette.id("bedrock");
        SNOW = Palette.id("snow_block");    GRAVEL = Palette.id("gravel");
        LAVA = Palette.id("lava");
    }

    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;          // -64 .. 319
    public static final int SEA_LEVEL = 63;

    /** Index dans le tableau de chunk.
     *
     *  Disposition COLONNE-MAJEURE : les 384 blocs d'une colonne sont contigus.
     *  Le generateur travaille colonne par colonne ; en disposition y-majeure
     *  chaque bloc successif sautait 1 Ko en memoire, ce qui ruinait le cache et
     *  interdisait Arrays.fill sur les longues sections de pierre ou d'eau. */
    public static int idx(int lx, int y, int lz) {
        return (((lx << 4) | lz) * HEIGHT) + (y - MIN_Y);
    }
    public static int[] newChunk() { return new int[256 * HEIGHT]; }
}
