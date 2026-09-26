package mnw;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Palette de blocs, construite a partir de celle des surface rules vanilla.
 *  L'air est force a l'index 0 : le decodeur remplit les chunks a zero. */
public final class Palette {
    private static String[] NAMES;
    private static final Map<String, Integer> IDX = new HashMap<>();

    static synchronized void init(List<String> fromRules) {
        if (NAMES != null) return;
        List<String> all = new ArrayList<>();
        all.add("air");
        for (String s : fromRules) if (!all.contains(s)) all.add(s);
        for (String s : new String[]{"stone", "deepslate", "water", "lava", "bedrock",
                                     "gravel", "dirt", "grass_block", "sand", "snow_block"})
            if (!all.contains(s)) all.add(s);
        NAMES = all.toArray(new String[0]);
        for (int i = 0; i < NAMES.length; i++) IDX.put(NAMES[i], i);
    }
    public static int id(String name) {
        Integer i = IDX.get(name);
        if (i == null) throw new IllegalArgumentException("bloc inconnu : " + name);
        return i;
    }
    public static String[] names() { return NAMES; }
    public static int size() { return NAMES.length; }
}
