package mnw;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Monde infini : les patches sont generes a la demande et caches. Aucun
 *  pre-calcul, aucun ordre impose, aucun stockage disque.
 *
 *  <p>Le cache est <b>borne</b>. Il ne l'etait pas, et c'etait une fuite : un
 *  joueur en elytre traverse des dizaines de milliers de chunks, chacun laissant
 *  son patch en memoire pour toujours. A 20 Ko le patch et 16 chunks couverts,
 *  100 000 chunks visites font 125 Mo qui ne redescendent jamais -- sur une box
 *  a 4 Go partages avec le reste du serveur, cela finit en pauses GC de plusieurs
 *  secondes, c'est-a-dire en saccades visibles a l'ecran des que le joueur bouge.</p>
 *
 *  <p>L'eviction utilise l'algorithme d'horloge : chaque lecture marque le patch
 *  {@code hot}, le balayage jette ceux qui ne le sont pas et refroidit les autres.
 *  Le cout sur le chemin chaud est une lecture de booleen, et une ecriture
 *  seulement quand le drapeau etait faux -- pas d'atomique, pas de liste chainee
 *  a maintenir, donc rien qui se contende entre les threads de generation.</p> */
public final class ProcWorld {
    /** Nombre de patches gardes en memoire. 2048 patches = 32 768 chunks, soit un
     *  carre de 181 chunks de cote, pour environ 40 Mo. Largement de quoi tenir
     *  plusieurs joueurs a view-distance 8 (289 chunks, 36 patches chacun) et leur
     *  trajet recent. Reglable par -Dminegen.patchcache=N. */
    public static final int MAX_PATCHES =
        Math.max(64, Integer.getInteger("minegen.patchcache", 2048));

    private final Map<Long, Patch> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean sweeping = new AtomicBoolean();
    private final Terrain.Tables tables;
    public final AtomicInteger generated = new AtomicInteger();
    public final AtomicInteger evicted = new AtomicInteger();

    public ProcWorld(long seed) { this(Terrain.tables(seed)); }
    public ProcWorld(Terrain.Tables tables) { this.tables = tables; }

    /** Les tables de ce monde : un ChunkDecoder doit les recevoir, sinon il lirait
     *  celles d'un autre monde charge dans la meme JVM. */
    public Terrain.Tables tables() { return tables; }

    public Patch patchAt(int px, int pz) {                // px,pz = index de patch
        long k = ((long) px << 32) ^ (pz & 0xFFFFFFFFL);
        Patch p = cache.get(k);
        if (p != null) {
            if (!p.hot) p.hot = true;      // lecture d'abord : le cas courant n'ecrit rien
            return p;
        }
        // computeIfAbsent verrouille le seau : un patch demande par plusieurs threads
        // n'est calcule qu'une fois. Sans cela, 6 threads recalculent 6 fois le meme
        // patch et jettent 5 resultats -- l'acceleration plafonnait a 1,3x.
        p = cache.computeIfAbsent(k, key -> {
            generated.incrementAndGet();
            return Terrain.patch(tables, px * Patch.SIZE, pz * Patch.SIZE);
        });
        if (cache.size() > MAX_PATCHES) sweep();
        return p;
    }

    /** Un tour d'horloge : jette les patches froids, refroidit les autres. Un seul
     *  thread balaie a la fois ; les autres continuent a generer pendant ce temps. */
    private void sweep() {
        if (!sweeping.compareAndSet(false, true)) return;
        try {
            // On descend a 3/4 du plafond et on s'arrete la : viser exactement le
            // plafond ferait rebalayer au chunk suivant, et vider entierement le
            // cache jetterait le voisinage immediat du joueur, qu'il faudrait
            // regenerer aussitot.
            final int target = MAX_PATCHES - (MAX_PATCHES >> 2);
            int n = 0;
            for (int pass = 0; pass < 2 && cache.size() > target; pass++) {
                for (Iterator<Patch> it = cache.values().iterator(); it.hasNext(); ) {
                    Patch p = it.next();
                    if (p.hot) p.hot = false;
                    else if (cache.size() - n > target) { it.remove(); n++; }
                    if (cache.size() <= target) break;
                }
            }
            // Au premier tour tout est chaud : la passe 1 ne fait que refroidir, la
            // passe 2 jette. Si les deux passes n'ont rien rendu, c'est que tout est
            // reellement utilise -- on laisse alors depasser le plafond plutot que
            // de jeter un patch dont un thread de generation se sert a l'instant.
            evicted.addAndGet(n);
        } finally { sweeping.set(false); }
    }

    public int cached() { return cache.size(); }
    public void clear() { cache.clear(); generated.set(0); evicted.set(0); }
}
