package mnw.plugin.nms;

import mnw.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.Material;
import org.bukkit.craftbukkit.generator.CraftChunkData;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/**
 * Ecriture directe dans les sections de chunk, en contournant ChunkData.setRegion.
 *
 * <p>Pourquoi : {@code CraftChunkData.setRegion} n'est pas une ecriture en bloc.
 * Son implementation est une triple boucle qui rappelle {@code setBlock} pour
 * chaque bloc, et chaque appel alloue un {@code BlockPos}, refait la recherche de
 * section, puis met a jour tous les heightmaps du statut courant. Mesure sur
 * Paper 1.21.11, 666 chunks :</p>
 *
 * <pre>
 *   calcul MineGen         0,303 ms/chunk   ( 7 %)
 *   ecriture via Bukkit    4,342 ms/chunk   (93 %)
 *   35 678 blocs ecrits, 0,82 Mo de BlockPos alloues par chunk
 * </pre>
 *
 * <p>Le pont coutait donc quatorze fois l'algorithme entier. Ici on ecrit dans la
 * palette de la section (aucune allocation, aucune recherche par bloc) et on
 * calcule les heightmaps une seule fois a la fin, par {@code primeHeightmaps} --
 * qui produit exactement le meme resultat qu'une mise a jour incrementale, en un
 * seul balayage.</p>
 *
 * <p>{@code useLocks = false} : pendant {@code fillFromNoise} le chunk n'est
 * visible que du thread qui le genere, c'est ce que fait vanilla au meme endroit.</p>
 *
 * <p>Cette classe est la seule du plugin a dependre de NMS. Elle est chargee par
 * reflexion et toute erreur de liaison fait retomber sur le chemin Bukkit
 * standard : sur un serveur dont les classes ne correspondent pas, MineGen reste
 * fonctionnel, seulement plus lent.</p>
 */
public final class DirectWriter {

    private static final boolean VERIFY = Boolean.getBoolean("minegen.verify");
    public static final java.util.concurrent.atomic.AtomicLong MISMATCH = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong CHECKED  = new java.util.concurrent.atomic.AtomicLong();

    private final BlockState[] states;
    private final int airId;

    public DirectWriter(String[] paletteNames) {
        this.states = new BlockState[paletteNames.length];
        int air = 0;
        for (int i = 0; i < paletteNames.length; i++) {
            Material m = Material.matchMaterial("minecraft:" + paletteNames[i]);
            Block b = m == null ? null : CraftMagicNumbers.getBlock(m);
            this.states[i] = b == null ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                                       : b.defaultBlockState();
            if ("air".equals(paletteNames[i])) air = i;
        }
        this.airId = air;
        // Verification de liaison a la construction : si une signature manque, on
        // le sait ici, une fois, et pas au milieu de la generation.
        if (this.states.length == 0) throw new IllegalStateException("palette vide");
    }

    /**
     * @param cd    le ChunkData fourni par Paper
     * @param chunk le chunk decode par MineGen, indexe par {@link Blocks#idx}
     * @return le nombre de blocs non-air ecrits
     */
    public int write(ChunkData cd, int[] chunk) {
        ChunkAccess ca = ((CraftChunkData) cd).getHandle();
        final int lo = Math.max(ca.getMinY(), Blocks.MIN_Y);
        final int hi = Math.min(ca.getMaxY() + 1, Blocks.MIN_Y + Blocks.HEIGHT);
        final BlockState[] st = this.states;
        final int air = this.airId;
        int written = 0;

        int y = lo;
        while (y < hi) {
            LevelChunkSection sec = ca.getSection(ca.getSectionIndex(y));
            int secEnd = Math.min(hi, (y & ~15) + 16);
            for (; y < secEnd; y++) {
                final int ly = y & 15;
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++) {
                        int id = chunk[Blocks.idx(x, y, z)];
                        if (id == air) continue;
                        sec.setBlockState(x, ly, z, st[id], false);
                        written++;
                    }
            }
        }

        Heightmap.primeHeightmaps(ca, ca.getPersistedStatus().heightmapsAfter());

        // -Dminegen.verify=true : relit chaque bloc par le chemin de lecture normal
        // du serveur et le compare a ce que MineGen voulait ecrire. Contourner
        // l'API demande de prouver que le resultat est le meme ; c'est cette
        // preuve. Trop couteux pour la production, d'ou le drapeau.
        if (VERIFY) {
            int bad = 0;
            for (int yy = lo; yy < hi; yy++)
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++) {
                        BlockState got = ca.getBlockState(new net.minecraft.core.BlockPos(
                                ca.getPos().getMinBlockX() + x, yy, ca.getPos().getMinBlockZ() + z));
                        BlockState want = st[chunk[Blocks.idx(x, yy, z)]];
                        if (got != want) bad++;
                    }
            MISMATCH.addAndGet(bad);
            CHECKED.addAndGet((long) (hi - lo) * 256);
        }
        return written;
    }
}
