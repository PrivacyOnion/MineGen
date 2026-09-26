package mnw;
public final class Stats {
    public static void main(String[] a) {
        Patch p = Terrain.patch(42L, 1000000, 1000000);
        System.out.printf("patch 4x4 chunks : %d o  (%.0f o/chunk)%n", p.bytes(), p.bytes()/16.0);
        System.out.printf("  hauteur %d o | biomes %d o | caves %d o | tunnels %d o%n",
            p.h.length*2, p.s.length, p.c.length, p.worms.length*4);
        int vd = 12, n = (2*vd+1)*(2*vd+1);
        int patches = (int)Math.pow(Math.ceil((2.0*vd+1)/4)+1, 2);
        System.out.printf("  TP view-distance %d : %d chunks, ~%d patches en RAM = %.1f Mo%n",
            vd, n, patches, patches*p.bytes()/1048576.0);
        System.out.printf("  20 joueurs disperses : %.1f Mo de latent resident%n",
            20*patches*p.bytes()/1048576.0);
    }
}
