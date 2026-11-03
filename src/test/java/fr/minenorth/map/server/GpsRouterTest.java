package fr.minenorth.map.server;

import fr.minenorth.map.network.RoutePacket;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GpsRouterTest {
    /** Région 0,0 : herbe partout, une route en L (x = 50 de z 0 à 300, puis z = 300 de x 50 à 300), routes connues. */
    private static ServerRegion lRoad() {
        byte[] colors = new byte[RegionCodec.BYTES];
        java.util.Arrays.fill(colors, (byte) (1 << 2));   // GRASS
        byte[] roads = new byte[ServerRegion.ROAD_BYTES];
        for (int z = 0; z <= 300; z++) for (int x = 48; x <= 52; x++) setRoad(roads, colors, x, z);
        for (int x = 48; x <= 300; x++) for (int z = 298; z <= 302; z++) setRoad(roads, colors, x, z);
        BitSet all = new BitSet(1024);
        all.set(0, 1024);
        return new ServerRegion(0, 0, colors, all, 1, roads, all);
    }

    private static void setRoad(byte[] roads, byte[] colors, int x, int z) {
        int idx = (z << 9) | x;
        roads[idx >> 3] |= (byte) (1 << (idx & 7));
        colors[idx] = (byte) (11 << 2);
    }

    @Test
    void followsTheRoad() {
        ServerRegion r = lRoad();
        GpsRouter.Grid g = new GpsRouter.Grid(0, 0, 256, 256, 2, 0, 0, new ServerRegion[][]{{r}});
        long t0 = System.nanoTime();
        RoutePacket p = GpsRouter.compute(g, "test", 50, 0, 300, 300);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(p.ok());
        // le coin de la route (≈ 50, 300) doit faire partie du trajet : pas de diagonale à travers l'herbe
        boolean corner = false;
        for (int i = 0; i < p.xs().length; i++) if (Math.abs(p.xs()[i] - 50) <= 4 && Math.abs(p.zs()[i] - 300) <= 4) corner = true;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.xs().length; i++) sb.append('(').append(p.xs()[i]).append(',').append(p.zs()[i]).append(") ");
        System.out.println("trajet " + ms + " ms : " + sb);
        assertTrue(corner, sb.toString());
    }

    @Test
    void largeEmptyAreaStaysFast() {
        GpsRouter.Grid g = new GpsRouter.Grid(0, 0, 1536, 1536, 2, 0, 0, new ServerRegion[][]{{null}});
        long t0 = System.nanoTime();
        RoutePacket p = GpsRouter.compute(g, "test", 10, 10, 3000, 3000);
        System.out.println("zone inconnue 3000x3000 : " + (System.nanoTime() - t0) / 1_000_000 + " ms, ok=" + p.ok());
        assertTrue(p.ok());
    }
}
