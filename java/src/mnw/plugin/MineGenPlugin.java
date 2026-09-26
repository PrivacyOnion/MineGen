package mnw.plugin;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Point d'entree du plugin.
 *
 * Utilisation : dans server.properties,
 *   level-type=minecraft:normal
 *   generator-settings=
 * puis dans bukkit.yml,
 *   worlds:
 *     world:
 *       generator: MineGen
 */
public final class MineGenPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("MineGen actif : terrain, biomes et surface rules 1.21.11 ; "
                       + "decoration, structures et mobs restent vanilla.");
        BenchCommand bench = new BenchCommand(this);
        getCommand("mgbench").setExecutor(bench);

        // Mesure sans console : -Dminegen.autobench=12 [-Dminegen.autoshutdown=true]
        getServer().getScheduler().runTaskLater(this, () -> {
            org.bukkit.World w = getServer().getWorlds().get(0);
            org.bukkit.generator.ChunkGenerator g = w.getGenerator();
            getLogger().info("monde '" + w.getName() + "' -> generateur "
                           + (g == null ? "VANILLA (MineGen n'est PAS branche)" : g.getClass().getSimpleName()));
            if (g instanceof MineGenChunkGenerator mg) mg.installFastBiomes(w, getLogger());
        }, 1L);

        String tiles = System.getProperty("minegen.autobiomes");
        if (tiles != null)
            getServer().getScheduler().runTaskLater(this, () -> {
                bench.biomes(getServer().getConsoleSender(), Integer.parseInt(tiles.trim()));
                if (Boolean.getBoolean("minegen.autoshutdown")) getServer().shutdown();
            }, 100L);

        // Generation pure dans le monde MineGen, sans monde vanilla de comparaison :
        // c'est le monde vanilla qui saturait le planificateur de chunks et faisait
        // expirer la mesure. -Dminegen.autostats=R
        String st = System.getProperty("minegen.autostats");
        if (st != null) {
            int radius = Integer.parseInt(st.trim());
            getServer().getScheduler().runTaskLater(this, () -> {
              try {
                org.bukkit.World w = getServer().getWorlds().get(0);
                // Origine aleatoire par defaut, pour ne pas remesurer des chunks
                // deja sur le disque ; fixable par -Dminegen.origin=N quand on
                // compare deux executions entre elles.
                String fixed = System.getProperty("minegen.origin");
                int ox = fixed != null ? Integer.parseInt(fixed.trim())
                                       : (int) (System.nanoTime() >>> 12) & 0x3FF;
                int oz = ox * 7;
                ox += 100000; oz += 100000;
                long t0 = System.nanoTime();
                int n = 0;
                for (int cx = ox - radius; cx <= ox + radius; cx++)
                    for (int cz = oz - radius; cz <= oz + radius; cz++) { w.getChunkAt(cx, cz); n++; }
                double ms = (System.nanoTime() - t0) / 1e6;
                getServer().getConsoleSender().sendPlainMessage(
                    String.format("MGSTATS %d chunks charges en %.0f ms = %.3f ms/chunk bout-en-bout", n, ms, ms / n));

                // Empreinte du contenu reel : blocs et heightmaps. Les fichiers de
                // region portent des horodatages, leur md5 ne prouve rien ; ceci si.
                long hb = 0xcbf29ce484222325L, hbio = 0xcbf29ce484222325L;
                for (int cx = ox - radius; cx <= ox + radius; cx++)
                    for (int cz = oz - radius; cz <= oz + radius; cz++) {
                        org.bukkit.ChunkSnapshot snap = w.getChunkAt(cx, cz).getChunkSnapshot(false, true, false);
                        for (int x = 0; x < 16; x++)
                            for (int z = 0; z < 16; z++)
                                for (int y = w.getMinHeight(); y < w.getMaxHeight(); y++) {
                                    hb = (hb ^ snap.getBlockType(x, y, z).ordinal()) * 0x100000001b3L;
                                    if ((y & 3) == 0 && (x & 3) == 0 && (z & 3) == 0)
                                        hbio = (hbio ^ snap.getBiome(x, y, z).hashCode()) * 0x100000001b3L;
                                }
                    }
                getServer().getConsoleSender().sendPlainMessage(
                    String.format("MGHASH blocs=%016x biomes=%016x", hb, hbio));

                bench.onCommand(getServer().getConsoleSender(), null, "mgbench", new String[]{"stats"});
              } catch (Throwable t) {
                  getLogger().warning("autostats a echoue : " + t);
              } finally {
                  // Sans ce finally, une mesure qui echoue laisse le serveur en
                  // vie et son port occupe : la mesure suivante ne peut plus
                  // demarrer, et l'echec se propage a toutes les suivantes.
                  if (Boolean.getBoolean("minegen.autoshutdown")) getServer().shutdown();
              }
            }, 100L);
        }

        String auto = System.getProperty("minegen.autobench");
        if (auto != null) {
            int radius = Integer.parseInt(auto.trim());
            getServer().getScheduler().runTaskLater(this, () -> {
                bench.run(getServer().getConsoleSender(), radius);
                bench.onCommand(getServer().getConsoleSender(), null, "mgbench", new String[]{"stats"});
                if (Boolean.getBoolean("minegen.autoshutdown")) getServer().shutdown();
            }, 100L);
        }
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new MineGenChunkGenerator();
    }
}
