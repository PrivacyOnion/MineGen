package mnw;
/** Meme scan que la commande `mgbench biomes` du plugin, mais hors serveur :
 *  memes coordonnees, meme seed, meme pas de 4 blocs. Sert a verifier que le
 *  chemin plugin et le chemin hors ligne donnent le meme monde. */
public class DbgTile {
    public static void main(String[] a) {
        long seed = Long.parseLong(a[0]);
        int c0 = Integer.parseInt(a[1]), chunks = Integer.parseInt(a[2]);
        Terrain.vanilla(seed);
        ProcWorld w = new ProcWorld(seed);
        BiomeTable bt = Terrain.biomes();
        java.util.Map<String,int[]> hist = new java.util.HashMap<>();
        long total = 0;
        for (int cx = c0; cx < c0 + chunks; cx++)
            for (int cz = c0; cz < c0 + chunks; cz++)
                for (int lx = 0; lx < 16; lx += 4)
                    for (int lz = 0; lz < 16; lz += 4) {
                        int x = cx * 16 + lx, z = cz * 16 + lz;
                        int px = Math.floorDiv(x, Patch.SIZE), pz = Math.floorDiv(z, Patch.SIZE);
                        Patch p = w.patchAt(px, pz);
                        int cell = (Math.floorMod(x, Patch.SIZE) >> 2) * Patch.H_N
                                 + (Math.floorMod(z, Patch.SIZE) >> 2);
                        int surf = p.s[cell] & 0xFF;
                        double h = p.h[cell] / 16.0;
                        for (int y = -64; y < 320; y += 4) {
                            int b = bt.atDepth(surf, p.bd, cell * Patch.BD, (float) ((h - y) / 128.0));
                            int[] e = hist.computeIfAbsent(bt.names[b], k -> new int[]{0, 9999, -9999});
                            e[0]++; e[1] = Math.min(e[1], y); e[2] = Math.max(e[2], y);
                            total++;
                        }
                    }
        System.out.printf("%d cellules, %d biomes distincts%n", total, hist.size());
        for (String k : new String[]{"lush_caves","dripstone_caves","deep_dark"}) {
            int[] e = hist.get(k);
            if (e == null) System.out.printf("  %-18s ABSENT%n", k);
            else System.out.printf("  %-18s %8d  %5.2f %%   y %d .. %d%n", k, e[0], 100.0*e[0]/total, e[1], e[2]);
        }
        final long t = total;
        hist.entrySet().stream().sorted((x,y)->y.getValue()[0]-x.getValue()[0]).limit(8)
            .forEach(e -> System.out.printf("  %-18s %8d  %5.2f %%%n", e.getKey(), e.getValue()[0], 100.0*e.getValue()[0]/t));
    }
}
