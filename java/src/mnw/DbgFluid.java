package mnw;
/** Combien de blocs d'eau MineGen laisse dans un etat instable.
 *
 *  Vanilla n'ecoule un fluide au chargement du chunk que si le bloc a un voisin
 *  ou couler : de l'air en dessous, ou de l'air a cote. Chacun de ces blocs
 *  coute un tick de fluide, un update de voisins, et marque le chunk modifie --
 *  donc non abandonnable par le disk saver. */
public class DbgFluid {
    public static void main(String[] a) {
        long SEED = Long.getLong("minegen.seed", 20260909L);
        Terrain.vanilla(SEED);
        ProcWorld w = new ProcWorld(SEED);
        ChunkDecoder d = new ChunkDecoder();
        final int R = 12;
        int[][] col = new int[(R+2)*(R+2)][];
        long water=0, unstableDown=0, unstableSide=0, surf=0, cave=0;
        java.util.Map<Integer,Integer> byY = new java.util.TreeMap<>();
        int[][] grid = new int[R*16][];
        // on decode une bande de R x R chunks dans un seul tableau 16R x 16R
        int W = R*16;
        int[] top = new int[W*W];
        byte[] isWater = new byte[W*W*64];   // y de 0 a 63 seulement (nappes + mer)
        int[] c = Blocks.newChunk();
        int O = Integer.getInteger("minegen.region", 0);
        for (int cx=O; cx<O+R; cx++) for (int cz=O; cz<O+R; cz++) {
            int px=Math.floorDiv(cx,4), pz=Math.floorDiv(cz,4);
            d.decode(w.patchAt(px,pz), Math.floorMod(cx,4), Math.floorMod(cz,4),
                     (long)px*Patch.SIZE,(long)pz*Patch.SIZE, c);
            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                int gx=(cx-O)*16+lx, gz=(cz-O)*16+lz, base=Blocks.idx(lx,Blocks.MIN_Y,lz);
                for (int y=0;y<64;y++)
                    isWater[(gx*W+gz)*64+y] = (byte)(c[base+(y-Blocks.MIN_Y)]==Blocks.WATER?1:
                                                     c[base+(y-Blocks.MIN_Y)]==Blocks.AIR?2:0);
            }
        }
        for (int gx=1; gx<W-1; gx++) for (int gz=1; gz<W-1; gz++)
            for (int y=1; y<64; y++) {
                if (isWater[(gx*W+gz)*64+y]!=1) continue;
                water++;
                if (y>=62) surf++; else cave++;
                if (isWater[(gx*W+gz)*64+y-1]==2) { unstableDown++; continue; }
                if (isWater[((gx-1)*W+gz)*64+y]==2 || isWater[((gx+1)*W+gz)*64+y]==2
                 || isWater[(gx*W+gz-1)*64+y]==2 || isWater[(gx*W+gz+1)*64+y]==2) {
                    unstableSide++;
                    byY.merge(y, 1, Integer::sum);
                }
            }
        long chunks=(long)R*R;
        System.out.printf("%d chunks (%dx%d blocs)%n", chunks, W, W);
        System.out.printf("  eau totale            %,12d  (%,d / chunk)%n", water, water/chunks);
        System.out.printf("    dont mer (y>=62)    %,12d%n", surf);
        System.out.printf("    dont nappes         %,12d%n", cave);
        System.out.printf("  INSTABLE, air dessous %,12d  (%,d / chunk)%n", unstableDown, unstableDown/chunks);
        System.out.printf("  INSTABLE, air a cote  %,12d  (%,d / chunk)%n", unstableSide, unstableSide/chunks);
        System.out.printf("  => %,d ticks de fluide par chunk au chargement%n",
                          (unstableDown+unstableSide)/chunks);
        System.out.println("  repartition en altitude des blocs instables :");
        byY.entrySet().stream().sorted((x,y)->y.getValue()-x.getValue()).limit(8)
           .forEach(e -> System.out.printf("     y=%-4d %5d%n", e.getKey(), e.getValue()));
    }
}
