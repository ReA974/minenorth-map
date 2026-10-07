package fr.minenorth.map.server;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Compression des régions : 512x512 octets (couleurs packées façon carte vanilla). Partagé client/serveur. */
public final class RegionCodec {
    public static final int SIZE = 512;
    public static final int BYTES = SIZE * SIZE;
    public static final int MAGIC = 0x4D4E4D31; // "MNM1"

    private RegionCodec() {}

    public static byte[] compress(byte[] raw) {
        Deflater d = new Deflater(6);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(32768);
        byte[] buf = new byte[16384];
        while (!d.finished()) {
            int n = d.deflate(buf);
            out.write(buf, 0, n);
        }
        d.end();
        return out.toByteArray();
    }

    /** null si données invalides. */
    public static byte[] decompress(byte[] data) {
        Inflater inf = new Inflater();
        try {
            inf.setInput(data);
            byte[] out = new byte[BYTES];
            int off = 0;
            while (!inf.finished() && off < BYTES) {
                int n = inf.inflate(out, off, BYTES - off);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                off += n;
            }
            return off == BYTES ? out : null;
        } catch (DataFormatException e) {
            return null;
        } finally {
            inf.end();
        }
    }
}
