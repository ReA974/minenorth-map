package fr.minenorth.map.server;

import java.util.BitSet;

/** Région serveur : 32x32 chunks, 1 octet (couleur packée) par bloc. */
final class ServerRegion {
    final int rx, rz;
    final byte[] colors;
    final BitSet rendered; // chunks déjà dessinés (index (lz<<5)|lx)
    long version;
    boolean dirty;
    long lastAccess = System.currentTimeMillis();
    private byte[] compressed;

    ServerRegion(int rx, int rz, byte[] colors, BitSet rendered, long version) {
        this.rx = rx;
        this.rz = rz;
        this.colors = colors;
        this.rendered = rendered;
        this.version = version;
    }

    static ServerRegion blank(int rx, int rz) {
        return new ServerRegion(rx, rz, new byte[RegionCodec.BYTES], new BitSet(1024), 0L);
    }

    static int chunkIndex(int cx, int cz) {
        return ((cz & 31) << 5) | (cx & 31);
    }

    void markChanged() {
        version = Math.max(version + 1, System.currentTimeMillis());
        dirty = true;
        compressed = null;
    }

    byte[] compressed() {
        if (compressed == null) compressed = RegionCodec.compress(colors);
        return compressed;
    }
}
