package mnw;
public final class DbgDepth {
    public static void main(String[] a) throws Exception {
        Terrain.vanilla(42L);
        SurfaceRules sr = Terrain.surfaceRules();
        String[] n = Terrain.biomes().names;
        int unb = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length; i++) {
            int d = sr.usefulDepth(i);
            if (d == Integer.MAX_VALUE) { unb++; sb.append(n[i]).append(' '); }
        }
        System.out.println("biomes a profondeur NON bornee (" + unb + "/" + n.length + ") :");
        System.out.println("  " + sb);
        System.out.print("profondeurs bornees : ");
        for (int i = 0; i < n.length; i++) {
            int d = sr.usefulDepth(i);
            if (d != Integer.MAX_VALUE) System.out.print(n[i] + "=" + d + " ");
        }
        System.out.println();
    }
}
