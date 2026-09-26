package mnw;

/**
 * Pile d'octaves aux parametres exacts de Minecraft 1.21.11
 * (data/minecraft/worldgen/noise/*.json : firstOctave + amplitudes), avec la
 * normalisation de NormalNoise (deux piles decalees, valueFactor).
 *
 * Le generateur de nombres aleatoires de Mojang n'est PAS reproduit : le contenu
 * frequentiel et la forme du terrain sont ceux de vanilla, mais une meme seed ne
 * donnera pas le meme monde qu'un serveur vanilla.
 */
public final class VanillaNoise {
    private static final double INPUT_FACTOR = 1.0181268882175227;
    private final double[] amp;
    private final double lowFreq, lowVal, valueFactor;
    private final long seedA, seedB;

    public VanillaNoise(long seed, int firstOctave, double[] amplitudes) {
        this.amp = amplitudes;
        this.lowFreq = Math.pow(2.0, firstOctave);
        int n = amplitudes.length;
        this.lowVal = Math.pow(2.0, n - 1) / (Math.pow(2.0, n) - 1.0);
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) if (amplitudes[i] != 0) { lo = Math.min(lo, i); hi = Math.max(hi, i); }
        double expectedDeviation = 0.1 * (1.0 + 1.0 / (hi - lo + 1));
        this.valueFactor = (1.0 / 6.0) / expectedDeviation;
        this.seedA = seed; this.seedB = seed ^ 0x5DEECE66DL;
    }

    private double stack(double x, double y, double z, long s) {
        double d = 0, f = lowFreq, g = lowVal;
        for (int i = 0; i < amp.length; i++) {
            if (amp[i] != 0) d += amp[i] * NoiseJ.perlin3(x * f, y * f, z * f, s + i * 6364136223846793005L) * g;
            f *= 2.0; g /= 2.0;
        }
        return d;
    }

    private double stack2(double x, double z, long s) {
        double d = 0, f = lowFreq, g = lowVal;
        for (int i = 0; i < amp.length; i++) {
            if (amp[i] != 0) d += amp[i] * NoiseJ.perlin3y0(x * f, z * f, s + i * 6364136223846793005L) * g;
            f *= 2.0; g /= 2.0;
        }
        return d;
    }

    /** Meme chose que get(x, 0, z), sans payer la dimension verticale. Tous les
     *  bruits climatiques sont echantillonnes dans ce plan. */
    public double get2(double x, double z) {
        return (stack2(x, z, seedA) + stack2(x * INPUT_FACTOR, z * INPUT_FACTOR, seedB)) * valueFactor;
    }

    public double get(double x, double y, double z) {
        return (stack(x, y, z, seedA)
              + stack(x * INPUT_FACTOR, y * INPUT_FACTOR, z * INPUT_FACTOR, seedB)) * valueFactor;
    }
}
