package mnw;
import java.io.*;

/** Decode une zone via le VRAI chemin runtime et dump de quoi rendre des PNG. */
public final class Render {
    public static void main(String[] args) throws IOException {
        long X = Long.parseLong(args[0]), Z = Long.parseLong(args[1]);
        int size = Integer.parseInt(args[2]);
        Terrain.vanilla(42L);
        ProcWorld w = new ProcWorld(42L);
        ChunkDecoder dec = new ChunkDecoder();
        int[] chunk = Blocks.newChunk();

        short[] height = new short[size * size];
        byte[] topId = new byte[size * size], bio = new byte[size * size];
        long sliceZ = Z + size / 2;
        byte[] slice = new byte[size * Blocks.HEIGHT];

        long t0 = System.nanoTime(); int n = 0;
        for (long wcx = Math.floorDiv(X,16); wcx < Math.floorDiv(X + size,16); wcx++)
            for (long wcz = Math.floorDiv(Z,16); wcz < Math.floorDiv(Z + size,16); wcz++) {
                Patch p = w.patchAt((int) Math.floorDiv(wcx,4), (int) Math.floorDiv(wcz,4));
                dec.decode(p, (int) Math.floorMod(wcx,4), (int) Math.floorMod(wcz,4),
                    Math.floorDiv(wcx,4)*Patch.SIZE, Math.floorDiv(wcz,4)*Patch.SIZE, chunk); n++;
                for (int lx = 0; lx < 16; lx++)
                    for (int lz = 0; lz < 16; lz++) {
                        int px = (int)(wcx*16 + lx - X), pz = (int)(wcz*16 + lz - Z);
                        if (px < 0 || pz < 0 || px >= size || pz >= size) continue;
                        int hx = ((int) Math.floorMod(wcx,4) * 16 + lx) >> 2;
                        int hz = ((int) Math.floorMod(wcz,4) * 16 + lz) >> 2;
                        bio[px*size+pz] = p.s[hx * Patch.H_N + hz];
                        for (int y = Blocks.MIN_Y + Blocks.HEIGHT - 1; y >= Blocks.MIN_Y; y--) {
                            int b = chunk[Blocks.idx(lx, y, lz)];
                            if (b != Blocks.AIR) { height[px*size+pz] = (short) y; topId[px*size+pz] = (byte) b; break; }
                        }
                        if (wcz*16 + lz == sliceZ)
                            for (int y = 0; y < Blocks.HEIGHT; y++)
                                slice[px*Blocks.HEIGHT + y] = (byte) chunk[Blocks.idx(lx, Blocks.MIN_Y + y, lz)];
                    }
            }
        double ms = (System.nanoTime()-t0)/1e6;
        try (PrintWriter pw = new PrintWriter("out/palette.txt")) {
            for (String nm : Palette.names()) pw.println(nm);
        }
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("out/scene.bin")))) {
            o.writeInt(size); o.writeInt(Blocks.HEIGHT); o.writeLong(X); o.writeLong(Z);
            for (short h : height) o.writeShort(h);
            o.write(topId); o.write(bio); o.write(slice);
        }
        System.out.printf("(%d, %d) %dx%d : %d chunks, %d patches, %.0f ms (%.3f ms/chunk)%n",
            X, Z, size, size, n, w.generated.get(), ms, ms/n);
    }
}
