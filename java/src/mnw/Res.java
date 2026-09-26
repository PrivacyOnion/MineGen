package mnw;
import java.io.*;

/** Ouvre une ressource depuis le jar si possible, sinon depuis le disque.
 *  Le meme code sert au banc de test local et au plugin empaquete. */
public final class Res {
    public static InputStream open(String name) throws IOException {
        InputStream in = Res.class.getResourceAsStream("/mnw/res/" + name);
        if (in != null) return new BufferedInputStream(in);
        File f = new File("java/res/" + name);
        if (f.exists()) return new BufferedInputStream(new FileInputStream(f));
        throw new FileNotFoundException("ressource introuvable : " + name);
    }
}
