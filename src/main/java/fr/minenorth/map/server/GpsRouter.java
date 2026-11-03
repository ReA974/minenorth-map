package fr.minenorth.map.server;

import fr.minenorth.map.MineNorthMap;
import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.network.RoutePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Itinéraire GPS : A* sur une grille de cases (2 à 8 blocs) tirée de la carte commune. Les routes coûtent peu, le reste beaucoup :
 * le trajet suit les routes et ne les quitte que pour rejoindre le départ / l'arrivée ou franchir une coupure (passage piéton, marquage).
 * Routes connues grâce au masque noté au dessin de la carte (blocs de la config) ; chunks dessinés avant le GPS : devinées d'après la couleur.
 * Les régions sont lues sur le thread serveur, le calcul se fait sur un thread à part (un seul, priorité basse).
 */
public final class GpsRouter {
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MineNorth-GPS");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    /** Dernière demande acceptée par joueur (tick serveur), thread serveur. */
    private static final Map<UUID, Integer> LAST = new HashMap<>();
    /** Numéro de la dernière demande : un résultat plus ancien est jeté. */
    private static final Map<UUID, Integer> GEN = new ConcurrentHashMap<>();

    private static final int MIN_TICKS_BETWEEN = 20;
    private static final int MAX_EXPANSIONS = 4_000_000;
    private static final int MAX_POINTS = 2000;

    // coût d'une case selon sa nature (route = 1)
    private static final byte ROAD = 1, LAND = 4, UNKNOWN = 8, WATER = 20;
    private static final int WATER_ID = 12;   // MapColor.WATER
    private static final int GRAY_ID = 21, BLACK_ID = 29;   // couleurs des routes devinées (béton gris / noir, asphalte)

    private GpsRouter() {}

    public static void onLogout(UUID id) {
        LAST.remove(id);
        GEN.remove(id);
    }

    public static void request(ServerPlayer p, String name, String dim, int tx, int tz) {
        MinecraftServer server = p.server;
        if (!ServerMapConfig.GPS_ENABLED.get()) {
            send(p, RoutePacket.none(name));
            return;
        }
        ServerLevel level = p.serverLevel();
        if (!level.dimension().location().toString().equals(dim)) return;
        int now = server.getTickCount();
        Integer last = LAST.get(p.getUUID());
        if (last != null && now - last < MIN_TICKS_BETWEEN) return;   // le client redemande de lui-même
        LAST.put(p.getUUID(), now);

        int sx = p.getBlockX(), sz = p.getBlockZ();
        double dist = Math.hypot(tx - sx, tz - sz);
        if (dist > ServerMapConfig.GPS_MAX_DISTANCE.get()) {
            send(p, RoutePacket.none(name));
            return;
        }
        if (dist < 12) {
            send(p, new RoutePacket(name, true, new int[]{sx, tx}, new int[]{sz, tz}));
            return;
        }
        int margin = (int) Math.max(128, Math.min(768, dist * 0.35));
        int x0 = Math.min(sx, tx) - margin, z0 = Math.min(sz, tz) - margin;
        int x1 = Math.max(sx, tx) + margin, z1 = Math.max(sz, tz) + margin;
        int side = Math.max(x1 - x0, z1 - z0);
        int cell = side <= 3072 ? 2 : side <= 6144 ? 4 : 8;
        x0 = Math.floorDiv(x0, cell) * cell;
        z0 = Math.floorDiv(z0, cell) * cell;
        int w = (x1 - x0) / cell + 1, h = (z1 - z0) / cell + 1;

        // régions lues ici (thread serveur) ; leurs tableaux sont ensuite lus par le calcul
        int rx0 = Math.floorDiv(x0, 512), rz0 = Math.floorDiv(z0, 512);
        int rx1 = Math.floorDiv(x0 + w * cell, 512), rz1 = Math.floorDiv(z0 + h * cell, 512);
        ServerRegion[][] regions = new ServerRegion[rx1 - rx0 + 1][rz1 - rz0 + 1];
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) regions[rx - rx0][rz - rz0] = ServerMapManager.routeRegion(level, rx, rz);
        }

        UUID id = p.getUUID();
        int gen = GEN.merge(id, 1, Integer::sum);
        Grid grid = new Grid(x0, z0, w, h, cell, rx0, rz0, regions);
        int fsx = sx, fsz = sz;
        POOL.execute(() -> {
            RoutePacket pkt;
            try {
                pkt = compute(grid, name, fsx, fsz, tx, tz);
            } catch (Throwable t) {
                MineNorthMap.LOGGER.warn("[Carte] GPS : calcul impossible", t);
                pkt = RoutePacket.none(name);
            }
            RoutePacket result = pkt;
            server.execute(() -> {
                if (!Integer.valueOf(gen).equals(GEN.get(id))) return;
                ServerPlayer pl = server.getPlayerList().getPlayer(id);
                if (pl != null) send(pl, result);
            });
        });
    }

    private static void send(ServerPlayer p, RoutePacket pkt) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }

    // ------------------------------------------------------------------ grille

    record Grid(int x0, int z0, int w, int h, int cell, int rx0, int rz0, ServerRegion[][] regions) {
        /** Nature d'une case : route si un bloc échantillonné est une route ; eau si surtout de l'eau ; inconnue si rien n'est dessiné. */
        byte cost(int cx, int cz) {
            int step = cell <= 4 ? 1 : 2;
            int water = 0, known = 0, samples = 0;
            int bx0 = x0 + cx * cell, bz0 = z0 + cz * cell;
            for (int dz = 0; dz < cell; dz += step) {
                for (int dx = 0; dx < cell; dx += step) {
                    int bx = bx0 + dx, bz = bz0 + dz;
                    samples++;
                    int ri = Math.floorDiv(bx, 512) - rx0, rj = Math.floorDiv(bz, 512) - rz0;
                    if (ri < 0 || rj < 0 || ri >= regions.length || rj >= regions[0].length) continue;
                    ServerRegion r = regions[ri][rj];
                    if (r == null) continue;
                    int idx = ((bz & 511) << 9) | (bx & 511);
                    int color = (r.colors[idx] & 255) >> 2;
                    if (color == 0) continue;
                    known++;
                    boolean roadKnown = r.roadChunks.get(ServerRegion.chunkIndex(bx >> 4, bz >> 4));
                    if (roadKnown ? (r.roads[idx >> 3] & (1 << (idx & 7))) != 0 : color == GRAY_ID || color == BLACK_ID) return ROAD;
                    if (color == WATER_ID) water++;
                }
            }
            if (known == 0) return UNKNOWN;
            return water * 2 > samples ? WATER : LAND;
        }
    }

    // ------------------------------------------------------------------ A*

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final float SQRT2 = 1.41421356f;
    /** Heuristique gonflée (A* pondéré) : un peu moins optimal, beaucoup plus rapide sur les longs trajets. */
    private static final float WEIGHT = 1.25f;

    static RoutePacket compute(Grid g, String name, int sx, int sz, int tx, int tz) {
        int w = g.w, h = g.h, n = w * h;
        byte[] cost = new byte[n];   // 0 = pas encore lue
        float[] dist = new float[n];
        Arrays.fill(dist, Float.POSITIVE_INFINITY);
        byte[] from = new byte[n];   // direction d'arrivée + 1 (0 = départ / jamais atteinte)
        boolean[] closed = new boolean[n];
        int scx = clamp((sx - g.x0) / g.cell, w), scz = clamp((sz - g.z0) / g.cell, h);
        int ecx = clamp((tx - g.x0) / g.cell, w), ecz = clamp((tz - g.z0) / g.cell, h);
        int start = scz * w + scx, end = ecz * w + ecx;

        Heap open = new Heap();
        dist[start] = 0;
        open.push(start, heuristic(scx, scz, ecx, ecz));
        int expansions = 0;
        boolean found = false;
        while (open.size > 0) {
            int cur = open.pop();
            if (closed[cur]) continue;
            if (cur == end) {
                found = true;
                break;
            }
            closed[cur] = true;
            if (++expansions > MAX_EXPANSIONS) break;
            int cx = cur % w, cz = cur / w;
            for (int d = 0; d < 8; d++) {
                int nx = cx + DX[d], nz = cz + DZ[d];
                if (nx < 0 || nz < 0 || nx >= w || nz >= h) continue;
                int ni = nz * w + nx;
                if (closed[ni]) continue;
                if (cost[ni] == 0) cost[ni] = g.cost(nx, nz);
                if (cost[cur] == 0) cost[cur] = g.cost(cx, cz);
                float step = (cost[ni] + cost[cur]) * 0.5f * (d < 4 ? 1f : SQRT2);
                float nd = dist[cur] + step;
                if (nd < dist[ni]) {
                    dist[ni] = nd;
                    from[ni] = (byte) (d + 1);
                    open.push(ni, nd + heuristic(nx, nz, ecx, ecz));
                }
            }
        }
        if (!found) return RoutePacket.none(name);

        // chemin case par case (de l'arrivée au départ), puis simplifié
        List<int[]> pts = new ArrayList<>();
        int half = g.cell / 2;
        pts.add(new int[]{tx, tz});
        int cur = from[end] == 0 ? -1 : end;
        while (cur >= 0 && cur != start) {
            int d = from[cur] - 1;
            int cx = cur % w, cz = cur / w;
            if (cur != end) pts.add(new int[]{g.x0 + cx * g.cell + half, g.z0 + cz * g.cell + half});
            cur = (cz - DZ[d]) * w + (cx - DX[d]);
        }
        pts.add(new int[]{sx, sz});
        java.util.Collections.reverse(pts);
        List<int[]> simple = simplify(pts, Math.max(1.5, g.cell * 0.75));
        int m = Math.min(MAX_POINTS, simple.size());
        int[] xs = new int[m], zs = new int[m];
        for (int i = 0; i < m; i++) {
            xs[i] = simple.get(i)[0];
            zs[i] = simple.get(i)[1];
        }
        return new RoutePacket(name, true, xs, zs);
    }

    private static int clamp(int v, int size) { return Math.max(0, Math.min(size - 1, v)); }

    private static float heuristic(int x, int z, int ex, int ez) {
        int dx = Math.abs(x - ex), dz = Math.abs(z - ez);
        return WEIGHT * (Math.max(dx, dz) + (SQRT2 - 1) * Math.min(dx, dz));
    }

    /** Ramer-Douglas-Peucker (itératif) : ne garde que les virages. */
    private static List<int[]> simplify(List<int[]> pts, double eps) {
        int n = pts.size();
        if (n < 3) return pts;
        boolean[] keep = new boolean[n];
        keep[0] = keep[n - 1] = true;
        java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
        stack.push(new int[]{0, n - 1});
        while (!stack.isEmpty()) {
            int[] s = stack.pop();
            int a = s[0], b = s[1];
            double ax = pts.get(a)[0], az = pts.get(a)[1], bx = pts.get(b)[0], bz = pts.get(b)[1];
            double len = Math.hypot(bx - ax, bz - az);
            double best = -1;
            int bi = -1;
            for (int i = a + 1; i < b; i++) {
                double px = pts.get(i)[0], pz = pts.get(i)[1];
                double d = len < 1e-6 ? Math.hypot(px - ax, pz - az) : Math.abs((bx - ax) * (az - pz) - (ax - px) * (bz - az)) / len;
                if (d > best) {
                    best = d;
                    bi = i;
                }
            }
            if (bi >= 0 && best > eps) {
                keep[bi] = true;
                stack.push(new int[]{a, bi});
                stack.push(new int[]{bi, b});
            }
        }
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) if (keep[i]) out.add(pts.get(i));
        return out;
    }

    /** Tas binaire (case, priorité) en tableaux primitifs ; les doublons périmés sont ignorés au dépilage. */
    private static final class Heap {
        int[] nodes = new int[1 << 14];
        float[] keys = new float[1 << 14];
        int size;

        void push(int node, float key) {
            if (size == nodes.length) {
                nodes = Arrays.copyOf(nodes, size * 2);
                keys = Arrays.copyOf(keys, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (keys[p] <= key) break;
                nodes[i] = nodes[p];
                keys[i] = keys[p];
                i = p;
            }
            nodes[i] = node;
            keys[i] = key;
        }

        int pop() {
            int top = nodes[0];
            int node = nodes[--size];
            float key = keys[size];
            int i = 0;
            while (true) {
                int c = 2 * i + 1;
                if (c >= size) break;
                if (c + 1 < size && keys[c + 1] < keys[c]) c++;
                if (keys[c] >= key) break;
                nodes[i] = nodes[c];
                keys[i] = keys[c];
                i = c;
            }
            if (size > 0) {
                nodes[i] = node;
                keys[i] = key;
            }
            return top;
        }
    }
}
