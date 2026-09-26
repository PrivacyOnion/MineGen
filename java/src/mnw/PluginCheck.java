package mnw;
import java.io.File;
import java.lang.reflect.*;
import java.net.*;
import java.util.zip.*;

/** Verifie que MineGen.jar se chargerait sur un serveur Paper : plugin.yml
 *  coherent, classes resolubles, methodes de ChunkGenerator bien redefinies.
 *  Ne remplace pas un vrai demarrage de serveur, mais attrape l'essentiel. */
public final class PluginCheck {
    public static void main(String[] a) throws Exception {
        File jar = new File("MineGen.jar");
        File lib = new File(System.getProperty("user.home"), ".minegen/lib");
        java.util.List<URL> urls = new java.util.ArrayList<>();
        urls.add(jar.toURI().toURL());
        File[] libs = lib.listFiles((d, n) -> n.endsWith(".jar"));
        if (libs != null) for (File f : libs) urls.add(f.toURI().toURL());
        URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), null);

        try (ZipFile z = new ZipFile(jar)) {
            if (z.getEntry("plugin.yml") == null) throw new IllegalStateException("plugin.yml absent");
            String yml = new String(z.getInputStream(z.getEntry("plugin.yml")).readAllBytes());
            String main = null;
            for (String line : yml.split("\n")) if (line.startsWith("main:")) main = line.substring(5).trim();
            System.out.println("plugin.yml   : main = " + main);
            if (z.getEntry(main.replace('.', '/') + ".class") == null)
                throw new IllegalStateException("classe principale absente du jar");
            for (String r : new String[]{"mnw/res/vanilla_splines.bin", "mnw/res/vanilla_biomes.bin",
                                          "mnw/res/vanilla_surface.bin"})
                if (z.getEntry(r) == null) throw new IllegalStateException("ressource absente : " + r);
            System.out.println("ressources   : les 3 tables vanilla sont dans le jar");
        }

        Class<?> plugin = cl.loadClass("mnw.plugin.MineGenPlugin");
        Class<?> jp = cl.loadClass("org.bukkit.plugin.java.JavaPlugin");
        if (!jp.isAssignableFrom(plugin)) throw new IllegalStateException("n'etend pas JavaPlugin");
        Method gw = plugin.getMethod("getDefaultWorldGenerator", String.class, String.class);
        System.out.println("plugin       : etend JavaPlugin, " + gw.getName() + " correctement declare");

        Class<?> gen = cl.loadClass("mnw.plugin.MineGenChunkGenerator");
        Class<?> cg = cl.loadClass("org.bukkit.generator.ChunkGenerator");
        if (!cg.isAssignableFrom(gen)) throw new IllegalStateException("n'etend pas ChunkGenerator");
        Object inst = gen.getDeclaredConstructor().newInstance();
        String[] flags = {"shouldGenerateNoise", "shouldGenerateSurface", "shouldGenerateCaves",
                          "shouldGenerateBedrock", "shouldGenerateDecorations",
                          "shouldGenerateMobs", "shouldGenerateStructures"};
        StringBuilder sb = new StringBuilder();
        for (String f : flags)
            sb.append(f.substring(14)).append('=').append(gen.getMethod(f).invoke(inst)).append(' ');
        System.out.println("generateur   : " + sb.toString().trim());
        gen.getMethod("generateNoise", cl.loadClass("org.bukkit.generator.WorldInfo"),
                      java.util.Random.class, int.class, int.class,
                      cl.loadClass("org.bukkit.generator.ChunkGenerator$ChunkData"));
        gen.getMethod("getDefaultBiomeProvider", cl.loadClass("org.bukkit.generator.WorldInfo"));
        System.out.println("signatures   : generateNoise et getDefaultBiomeProvider resolus");
        System.out.println("\nOK : le jar est coherent avec l'API Paper.");
        cl.close();
    }
}
