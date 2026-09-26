package mnw;
import java.io.IOException;

/**
 * Modele de terrain de Minecraft 1.21.11, reduit a sa partie 2D.
 *
 * Vanilla derive la surface d'un champ de densite 3D dont le coeur est
 *   sloped_cheese = 4 * quarter_neg((depth + jag) * factor) + base_3d_noise
 * avec depth = y_clamped_gradient(1.5 @ -64 -> -1.5 @ 320) + offset(x,z).
 * En annulant l'interieur on obtient la surface analytiquement :
 *
 *   h = 128 + 128 * (offset + jaggedness * half_neg(bruit_jagged))
 *
 * offset, factor et jaggedness sont des splines PUREMENT 2D de
 * (continents, erosion, ridges) : c'est le modele de hauteur de Mojang lui-meme.
 * On ne paie donc pas base_3d_noise, dont la rugosite locale est resynthetisee
 * par Detail pour une fraction du cout.
 */
public final class VanillaTerrain {
    private final VanillaNoise offsetN, contN, eroN, ridgeN, tempN, vegN, jaggedN;
    private final VanillaSpline spline;

    public VanillaTerrain(long seed) throws IOException {
        spline  = new VanillaSpline(Res.open("vanilla_splines.bin"));
        offsetN = new VanillaNoise(seed + 1, -3,  new double[]{1,1,1,0});
        contN   = new VanillaNoise(seed + 2, -9,  new double[]{1,1,2,2,2,1,1,1,1});
        eroN    = new VanillaNoise(seed + 3, -9,  new double[]{1,1,0,1,1});
        ridgeN  = new VanillaNoise(seed + 4, -7,  new double[]{1,2,1,0,0,0});
        tempN   = new VanillaNoise(seed + 5, -10, new double[]{1.5,0,1,0,0,0});
        vegN    = new VanillaNoise(seed + 6, -8,  new double[]{1,1,0,0,0,0});
        // jagged vanilla = 16 octaves depuis -16 ; au-dela de 6 c'est du bruit blanc
        // a l'echelle du bloc, deja fourni par Detail. On garde les 6 premieres.
        jaggedN = new VanillaNoise(seed + 7, -16, new double[]{1,1,1,1,1,1});
    }

    /** Remplit clim = {temperature, humidite, continents, erosion, profondeur, weirdness}
     *  et renvoie la hauteur de surface en blocs. */
    public double column(double wx, double wz, float[] clim, float[] sp) {
        double sx = wx * 0.25, sz = wz * 0.25;
        double shX = offsetN.get2(sx, sz) * 4.0;      // shift_a
        double shZ = offsetN.get(sz, sx, 0) * 4.0;      // shift_b : coordonnees permutees
        double px = sx + shX, pz = sz + shZ;

        double C = contN.get2(px, pz);
        double E = eroN.get2(px, pz);
        double R = ridgeN.get2(px, pz);
        double T = tempN.get2(px, pz);
        double V = vegN.get2(px, pz);
        double Rf = -3.0 * (-1.0/3.0 + Math.abs(-2.0/3.0 + Math.abs(R)));

        sp[0] = (float) C; sp[1] = (float) E; sp[2] = (float) R; sp[3] = (float) Rf;
        double offset = -0.50375 + spline.eval(VanillaSpline.OFFSET, sp);
        double jagAmp = spline.eval(VanillaSpline.JAGGEDNESS, sp);
        double jn = jaggedN.get2(wx * 1500.0, wz * 1500.0);
        double jag = jagAmp * (jn > 0 ? jn : jn * 0.5);          // half_negative

        double h = 128.0 + 128.0 * (offset + jag);
        clim[0] = (float) T; clim[1] = (float) V; clim[2] = (float) C;
        clim[3] = (float) E; clim[5] = (float) R;
        clim[4] = 0f;                                            // depth = 0 en surface
        return h;
    }
}
