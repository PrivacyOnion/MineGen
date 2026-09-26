package mnw.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Mesure A/B sur le vrai serveur.
 *
 * Le banc hors ligne compare MineGen a un modele de cout que j'ai ecrit ; il ne
 * dit rien de Paper. Ici les deux cotes traversent le pipeline complet --
 * generation, decoration, structures, lighting, ecriture des sections -- dans
 * deux mondes de meme seed, aux memes coordonnees, sur le meme thread.
 *
 * Chaque passe prend une origine neuve : sans cela on mesurerait le cache de
 * chunks et non la generation.
 */
public final class BenchCommand implements CommandExecutor {
    private static final String W_VANILLA = "mgbench_vanilla";

    private final MineGenPlugin plugin;
    /** Decale l'origine a chaque passe ET a chaque demarrage : sinon la deuxieme
     *  mesure relit les regions ecrites par la premiere et note du disque. */
    private int run = (int) (System.nanoTime() >>> 12) & 0x3FF;

    BenchCommand(MineGenPlugin plugin) { this.plugin = plugin; }

    /** Le monde principal est deja genere par MineGen : c'est lui le cote mesure.
     *  On ne peut pas en creer un second, `Terrain` ne porte qu'un seed par JVM. */
    private World mineWorld() { return Bukkit.getWorlds().get(0); }

    /** Le temoin vanilla, cree au meme seed pour que le relief soit comparable. */
    private World vanillaWorld() {
        World w = Bukkit.getWorld(W_VANILLA);
        if (w != null) return w;
        return new WorldCreator(W_VANILLA).seed(mineWorld().getSeed()).createWorld();
    }

    /** Genere le carre de chunks autour de (ox, oz) et rend le temps total en ms. */
    private double generate(World w, int ox, int oz, int radius) {
        long t0 = System.nanoTime();
        for (int cx = ox - radius; cx <= ox + radius; cx++)
            for (int cz = oz - radius; cz <= oz + radius; cz++)
                w.loadChunk(cx, cz, true);
        double ms = (System.nanoTime() - t0) / 1e6;
        for (int cx = ox - radius; cx <= ox + radius; cx++)
            for (int cz = oz - radius; cz <= oz + radius; cz++)
                w.unloadChunk(cx, cz, false);
        return ms;
    }

    /** Ou part le temps d'un chunk : le calcul MineGen, ou le pont Bukkit qui
     *  recopie le resultat bloc par bloc dans le ProtoChunk. */
    private boolean stats(CommandSender s) {
        long n = MineGenChunkGenerator.N_CHUNKS.get();
        if (n == 0) { s.sendPlainMessage("aucun chunk genere depuis le demarrage"); return true; }
        double dec = MineGenChunkGenerator.T_DECODE.get() / 1e6 / n;
        double wr  = MineGenChunkGenerator.T_WRITE.get()  / 1e6 / n;
        long blocks = MineGenChunkGenerator.N_BLOCKS.get() / n;
        long calls  = MineGenChunkGenerator.N_CALLS.get()  / n;
        s.sendPlainMessage(String.format("%d chunks generes", n));
        s.sendPlainMessage(String.format("  calcul MineGen        %6.3f ms/chunk  (%.0f %%)",
            dec, 100 * dec / (dec + wr)));
        s.sendPlainMessage(String.format("  ecriture via Bukkit   %6.3f ms/chunk  (%.0f %%)",
            wr, 100 * wr / (dec + wr)));
        s.sendPlainMessage(String.format("  %d blocs ecrits, %d appel(s) d'ecriture par chunk", blocks, calls));
        s.sendPlainMessage(String.format("  soit %.3f us par bloc", wr * 1000 / Math.max(1, blocks)));
        s.sendPlainMessage(calls <= 1
            ? "  chemin : ecriture directe des sections NMS"
            : "  chemin : ChunkData.setRegion (un BlockPos alloue par bloc)");
        try {
            Class<?> dw = Class.forName("mnw.plugin.nms.DirectWriter");
            long checked = ((java.util.concurrent.atomic.AtomicLong) dw.getField("CHECKED").get(null)).get();
            if (checked > 0) {
                long bad = ((java.util.concurrent.atomic.AtomicLong) dw.getField("MISMATCH").get(null)).get();
                s.sendPlainMessage(String.format("  VERIFICATION : %,d blocs relus, %,d ecarts%s",
                    checked, bad, bad == 0 ? "  -> resultat identique a l'intention" : "  -> ECHEC"));
            }
        } catch (Throwable ignored) { }
        try {
            Class<?> fb = Class.forName("mnw.plugin.nms.FastBiomeSource");
            long ck = ((java.util.concurrent.atomic.AtomicLong) fb.getField("CHECKED").get(null)).get();
            if (ck > 0) {
                long bad = ((java.util.concurrent.atomic.AtomicLong) fb.getField("MISMATCH").get(null)).get();
                s.sendPlainMessage(String.format("  BIOMES : %,d cellules comparees aux deux chemins, %,d ecart%s",
                    ck, bad, bad == 0 ? "  -> identiques" : "  -> ECHEC"));
            }
        } catch (Throwable ignored) { }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("stats")) return stats(s);
        if (args.length > 0 && args[0].equalsIgnoreCase("tp")) return teleport(s);
        if (args.length > 0 && args[0].equalsIgnoreCase("biomes")) {
            int tiles = args.length > 1 ? Math.max(1, Math.min(4, Integer.parseInt(args[1]))) : 1;
            biomes(s, tiles);
            return true;
        }

        int radius = 12;
        if (args.length > 0) {
            try { radius = Math.max(1, Math.min(24, Integer.parseInt(args[0]))); }
            catch (NumberFormatException e) { s.sendPlainMessage("rayon invalide"); return true; }
        }
        run(s, radius);
        return true;
    }

    /** Le corps de la mesure, appelable aussi sans console (voir -Dminegen.autobench). */
    void run(CommandSender s, int radius) {
        int chunks = (2 * radius + 1) * (2 * radius + 1);
        s.sendPlainMessage("MineGen : preparation des deux mondes...");

        World mine = mineWorld(), van = vanillaWorld();
        if (mine == null || van == null) { s.sendPlainMessage("creation des mondes impossible"); return; }

        // Echauffement du JIT sur une zone qui ne sera pas mesuree.
        generate(mine, 90000, 90000 + run, 3);
        generate(van, 90000, 90000 + run, 3);

        s.sendPlainMessage(String.format("=== %d chunks par passe, pipeline Paper complet ===", chunks));
        double sMine = 0, sVan = 0;
        final int PASSES = 3;
        for (int i = 0; i < PASSES; i++) {
            run++;
            int ox = 62500 + run * 96, oz = 62500 + run * 96;   // ~1 000 000 blocs, terrain neuf
            // Ordre alterne : la premiere des deux paie le rechauffement du cache.
            double tMine, tVan;
            if ((i & 1) == 0) { tMine = generate(mine, ox, oz, radius); tVan = generate(van, ox, oz, radius); }
            else              { tVan = generate(van, ox, oz, radius);  tMine = generate(mine, ox, oz, radius); }
            sMine += tMine; sVan += tVan;
            s.sendPlainMessage(String.format("  passe %d  vanilla %7.2f ms/chunk   MineGen %7.2f   %.2fx",
                    i + 1, tVan / chunks, tMine / chunks, tVan / tMine));
        }
        double mv = sVan / (PASSES * chunks), mm = sMine / (PASSES * chunks);
        s.sendPlainMessage(String.format("  MOYENNE  vanilla %7.2f ms/chunk   MineGen %7.2f   %.2fx",
                mv, mm, mv / mm));
        s.sendPlainMessage(String.format("  %.2f ms economises par chunk ; le reste (%.2f ms) est de la",
                mv - mm, mm));
        s.sendPlainMessage("  decoration, des structures et du lighting, identiques des deux cotes.");
        plugin.getLogger().info(String.format(
                "bench r=%d x%d : vanilla %.3f ms/chunk, MineGen %.3f ms/chunk, %.2fx",
                radius, PASSES, mv, mm, mv / mm));
    }

    /**
     * Verifie les biomes tels que le serveur les a reellement ecrits, par tuiles
     * de 16x16 chunks -- l'unite de travail du generateur, pas le chunk.
     *
     * On relit `World.getBiome`, donc ce qui est dans les sections de chunk, pas
     * ce que le latent croit avoir produit. Un echantillon tous les 4 blocs sur
     * les trois axes : c'est exactement la resolution a laquelle Minecraft stocke
     * les biomes, donc le comptage est exact et non echantillonne.
     */
    void biomes(CommandSender s, int tiles) {
        World w = mineWorld();
        final int TILE = 16;                                  // chunks par cote
        int c0 = 62500, chunks = tiles * TILE;                // 62500 chunks = 1 000 000 blocs
        s.sendPlainMessage(String.format(
                "generation de %dx%d chunks (%d tuiles de 16x16) a (%d, %d)...",
                chunks, chunks, tiles * tiles, c0 * 16, c0 * 16));

        java.util.Map<String, int[]> hist = new java.util.HashMap<>();   // {compte, yMin, yMax}
        long total = 0;
        long t0 = System.nanoTime();
        for (int cx = c0; cx < c0 + chunks; cx++)
            for (int cz = c0; cz < c0 + chunks; cz++) {
                w.loadChunk(cx, cz, true);
                for (int lx = 0; lx < 16; lx += 4)
                    for (int lz = 0; lz < 16; lz += 4) {
                        int x = cx * 16 + lx, z = cz * 16 + lz;
                        for (int y = w.getMinHeight(); y < w.getMaxHeight(); y += 4) {
                            String b = w.getBiome(x, y, z).getKey().getKey();
                            int[] e = hist.computeIfAbsent(b, k -> new int[]{0, 9999, -9999});
                            e[0]++; e[1] = Math.min(e[1], y); e[2] = Math.max(e[2], y);
                            total++;
                        }
                    }
                w.unloadChunk(cx, cz, false);
            }
        double ms = (System.nanoTime() - t0) / 1e6;

        s.sendPlainMessage(String.format("=== biomes sur %d tuile(s) de 16x16 chunks, %.1f s ===",
                tiles * tiles, ms / 1000));
        s.sendPlainMessage(String.format("  %d cellules de biome, %d biomes distincts",
                total, hist.size()));
        s.sendPlainMessage("  -- souterrains (la verification qui compte) --");
        boolean any = false;
        for (String k : new String[]{"lush_caves", "dripstone_caves", "deep_dark"}) {
            int[] e = hist.get(k);
            if (e == null) { s.sendPlainMessage(String.format("  %-18s ABSENT", k)); continue; }
            any = true;
            s.sendPlainMessage(String.format("  %-18s %8d cellules  %5.2f %%   y %d .. %d",
                    k, e[0], 100.0 * e[0] / total, e[1], e[2]));
        }
        if (!any) s.sendPlainMessage("  aucun biome souterrain : l'attribution 3D ne marche pas.");
        s.sendPlainMessage("  -- surface, les 8 plus frequents --");
        final long tot = total;
        hist.entrySet().stream()
            .sorted((a, b) -> b.getValue()[0] - a.getValue()[0])
            .limit(8)
            .forEach(e -> s.sendPlainMessage(String.format("  %-18s %8d  %5.2f %%",
                    e.getKey(), e.getValue()[0], 100.0 * e.getValue()[0] / tot)));
        plugin.getLogger().info("verification biomes terminee : " + hist.size() + " biomes distincts");
    }

    private boolean teleport(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendPlainMessage("commande reservee a un joueur"); return true; }
        World w = mineWorld();
        Location dst = new Location(w, 1_000_000.5, 0, 1_000_000.5);
        dst.setY(w.getHighestBlockYAt(1_000_000, 1_000_000) + 2);
        p.teleport(dst);
        s.sendPlainMessage("dans " + w.getName() + " a 1 000 000, " + dst.getBlockY() + ", 1 000 000");
        return true;
    }
}
