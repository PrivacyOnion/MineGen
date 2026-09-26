package mnw;
/** Niveau de la surface d'eau et marches entre colonnes voisines : une marche
 *  d'un bloc fait couler l'eau, donc tick de fluide a chaque chunk genere. */
public class DbgWater {
    public static void main(String[] a) {
        Terrain.vanilla(20260909L);
        ProcWorld w = new ProcWorld(20260909L);
        ChunkDecoder d = new ChunkDecoder();
        int[] c = Blocks.newChunk();
        int[] topWater = new int[16*16];
        java.util.Map<Integer,Integer> surf = new java.util.TreeMap<>();
        long steps = 0, pairs = 0, cave = 0;
        for (int cx = 62500; cx < 62508; cx++) for (int cz = 62500; cz < 62508; cz++) {
            int px=Math.floorDiv(cx,4), pz=Math.floorDiv(cz,4);
            d.decode(w.patchAt(px,pz), Math.floorMod(cx,4), Math.floorMod(cz,4),
                     (long)px*Patch.SIZE,(long)pz*Patch.SIZE, c);
            java.util.Arrays.fill(topWater, Integer.MIN_VALUE);
            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                int base=Blocks.idx(lx,Blocks.MIN_Y,lz);
                for (int y=Blocks.MIN_Y+Blocks.HEIGHT-1;y>=Blocks.MIN_Y;y--)
                    if (c[base+(y-Blocks.MIN_Y)]==Blocks.WATER) { topWater[(lx<<4)|lz]=y; break; }
                int t=topWater[(lx<<4)|lz];
                if (t!=Integer.MIN_VALUE) { surf.merge(t,1,Integer::sum); if (t<Blocks.SEA_LEVEL-2) cave++; }
            }
            for (int lx=0;lx<15;lx++) for (int lz=0;lz<16;lz++) {
                int A=topWater[(lx<<4)|lz], B=topWater[((lx+1)<<4)|lz];
                if (A!=Integer.MIN_VALUE && B!=Integer.MIN_VALUE) { pairs++; if (A!=B) steps++; }
            }
        }
        System.out.println("altitude de la surface d'eau (colonnes) :");
        surf.entrySet().stream().sorted((x,y)->y.getValue()-x.getValue()).limit(6)
            .forEach(e -> System.out.printf("  y=%-4d %6d colonnes%n", e.getKey(), e.getValue()));
        System.out.printf("%nvanilla : la mer s'arrete a y=62 (sea-level 63 exclu)%n");
        System.out.printf("marches entre colonnes voisines : %d / %d (%.2f %%)%n", steps, pairs, 100.0*steps/pairs);
        System.out.printf("nappes de cave (sous y=61) : %d colonnes%n", cave);
    }
}
