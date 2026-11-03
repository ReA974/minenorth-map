package fr.minenorth.map.server;

import java.util.BitSet;

/** Région serveur : 32x32 chunks, 1 octet (couleur packée) par bloc. */
final class ServerRegion {
    final int rx, rz;
    final byte[] colors;
    final BitSet rendered; // chunks déjà dessinés (index (lz<<5)|lx)
    /** Routes (GPS) : 1 bit par bloc, même index que colors. */
    final byte[] roads;
    /** Chunks dont les routes sont connues (dessinés depuis l'arrivée du GPS) ; les autres : routes devinées d'après la couleur. */
    final BitSet roadChunks;
    long version;
    boolean dirty;
    long lastAccess = System.currentTimeMillis();
    private byte[] compressed;

    static final int ROAD_BYTES = RegionCodec.BYTES / 8;

    ServerRegion(int rx, int rz, byte[] colors, BitSet rendered, long version, byte[] roads, BitSet roadChunks) {
        this.rx = rx;
        this.rz = rz;
        this.colors = colors;
        this.rendered = rendered;
        this.version = version;
        this.roads = roads;
        this.roadChunks = roadChunks;
    }

    static ServerRegion blank(int rx, int rz) {
        return new ServerRegion(rx, rz, new byte[RegionCodec.BYTES], new BitSet(1024), 0L, new byte[ROAD_BYTES], new BitSet(1024));
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
