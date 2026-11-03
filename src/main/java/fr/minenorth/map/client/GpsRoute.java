package fr.minenorth.map.client;

import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.network.RouteRequestPacket;
import fr.minenorth.map.waypoint.Waypoint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Itinéraire GPS vers le point guidé : demandé au serveur dès qu'un point est suivi (carte, téléphone, autre mod), recalculé quand
 * le joueur s'écarte du trajet, effacé à l'arrivée ou quand le guidage s'arrête. Sans itinéraire (pas de route, trop loin) : guidage
 * classique en ligne droite.
 */
public final class GpsRoute {
    /** Violet du tracé (façon GPS de jeu). */
    public static final int COLOR = 0xB15CFF;

    private static final double OFF_ROUTE = 20;          // blocs : au-delà, le joueur a quitté le trajet
    private static final int OFF_TICKS = 40;              // ... pendant 2 s -> recalcul
    private static final long MIN_REQUEST_MS = 5000, RETRY_FAILED_MS = 30_000, PENDING_TIMEOUT_MS = 15_000;
    private static final double GROUND_AHEAD = 80, GROUND_STEP = 1.6;

    private static String wantedKey;      // point guidé (nom|x|z)
    private static String routeKey;       // point de l'itinéraire reçu
    private static int[] xs, zs;          // itinéraire reçu (null = aucun)
    private static boolean failed;        // le serveur n'a pas trouvé d'itinéraire pour wantedKey
    private static boolean pending;
    private static long lastRequest;
    private static int offTicks;

    // progression (mise à jour au tick)
    private static int seg;               // segment courant (xs[seg] -> xs[seg+1])
    private static double projX, projZ;   // point du trajet le plus proche du joueur
    private static double remaining;      // distance restante par la route
    private static double aheadX, aheadZ; // point un peu plus loin sur le trajet (direction de la flèche)
    /** Flèches au sol : x, y, z, dirX, dirZ par flèche. */
    private static float[] ground = new float[0];

    private GpsRoute() {}

    private static String key(Waypoint t) { return t.name() + "|" + t.x() + "|" + t.z(); }

    /** Un itinéraire est affiché pour le point guidé actuel. */
    public static boolean active() { return xs != null && routeKey != null && routeKey.equals(wantedKey); }

    public static double remaining() { return remaining; }

    public static double aheadX() { return aheadX; }

    public static double aheadZ() { return aheadZ; }

    public static float[] ground() { return ground; }

    public static void clear() {
        wantedKey = routeKey = null;
        xs = zs = null;
        failed = pending = false;
        offTicks = 0;
        ground = new float[0];
    }

    // ------------------------------------------------------------------ réseau

    private static void request(Waypoint t, String dim) {
        lastRequest = System.currentTimeMillis();
        pending = true;
        NetworkHandler.CHANNEL.sendToServer(new RouteRequestPacket(t.name(), dim, t.x(), t.z()));
    }

    /** Réponse du serveur. */
    public static void onRoute(String name, boolean ok, int[] rxs, int[] rzs) {
        Waypoint t = ClientWaypoints.getTracked();
        if (t == null || !t.name().equals(name) || !key(t).equals(wantedKey)) return;   // réponse périmée
        pending = false;
        offTicks = 0;
        if (!ok || rxs.length < 2) {
            failed = true;
            xs = zs = null;
            routeKey = null;
            ground = new float[0];
            return;
        }
        failed = false;
        xs = rxs;
        zs = rzs;
        routeKey = wantedKey;
        seg = 0;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) update(p);
    }

    // ------------------------------------------------------------------ tick

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        Waypoint t = ClientWaypoints.getTracked();
        String dim = mc.level == null ? "" : mc.level.dimension().location().toString();
        if (p == null || t == null || !ClientConfig.GPS_ENABLED.get() || !t.dimension().equals(dim)) {
            if (wantedKey != null) clear();
            return;
        }
        long now = System.currentTimeMillis();
        String k = key(t);
        if (!k.equals(wantedKey)) {   // nouveau point guidé (ou point déplacé)
            wantedKey = k;
            xs = zs = null;
            routeKey = null;
            failed = false;
            ground = new float[0];
            request(t, dim);
            return;
        }
        if (pending && now - lastRequest > PENDING_TIMEOUT_MS) pending = false;
        if (xs == null) {
            if (!pending && (!failed || now - lastRequest > RETRY_FAILED_MS)) request(t, dim);
            return;
        }
        update(p);
        if (Math.hypot(p.getX() - projX, p.getZ() - projZ) > OFF_ROUTE) {
            if (++offTicks >= OFF_TICKS && !pending && now - lastRequest > MIN_REQUEST_MS) {
                offTicks = 0;
                request(t, dim);
                p.displayClientMessage(Component.literal("GPS : recalcul de l'itinéraire...").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        } else {
            offTicks = 0;
        }
    }

    /** Projette le joueur sur le trajet (en avançant, sans revenir en arrière de plus d'un segment), puis distance et flèches. */
    private static void update(LocalPlayer p) {
        double px = p.getX(), pz = p.getZ();
        int n = xs.length;
        double best = Double.MAX_VALUE;
        int bestSeg = 0;
        double bx = xs[0], bz = zs[0];
        for (int i = Math.max(0, seg - 1); i < n - 1; i++) {
            double ax = xs[i] + 0.5, az = zs[i] + 0.5, cx = xs[i + 1] + 0.5, cz = zs[i + 1] + 0.5;
            double dx = cx - ax, dz = cz - az, len2 = dx * dx + dz * dz;
            double u = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / len2));
            double qx = ax + u * dx, qz = az + u * dz;
            double d = (px - qx) * (px - qx) + (pz - qz) * (pz - qz);
            if (d < best - 1e-6) {
                best = d;
                bestSeg = i;
                bx = qx;
                bz = qz;
            }
        }
        seg = bestSeg;
        projX = bx;
        projZ = bz;

        double rem = Math.hypot(xs[seg + 1] + 0.5 - bx, zs[seg + 1] + 0.5 - bz);
        for (int i = seg + 1; i < n - 1; i++) rem += Math.hypot(xs[i + 1] - xs[i], zs[i + 1] - zs[i]);
        remaining = rem + Math.sqrt(best);

        double[] a = along(15);
        aheadX = a[0];
        aheadZ = a[1];
        ground = ClientConfig.GPS_GROUND.get() ? buildGround(p) : new float[0];
    }

    /** Point situé à dist blocs plus loin sur le trajet, depuis la projection du joueur. */
    private static double[] along(double dist) {
        double x = projX, z = projZ;
        for (int i = seg + 1; i < xs.length; i++) {
            double nx = xs[i] + 0.5, nz = zs[i] + 0.5;
            double l = Math.hypot(nx - x, nz - z);
            if (l >= dist) return new double[]{x + (nx - x) * dist / l, z + (nz - z) * dist / l};
            dist -= l;
            x = nx;
            z = nz;
        }
        return new double[]{x, z};
    }

    /** Flèches au sol tous les GROUND_STEP blocs devant le joueur, posées sur le sol le plus proche de sa hauteur (ponts, tunnels). */
    private static float[] buildGround(LocalPlayer p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return new float[0];
        java.util.List<float[]> out = new java.util.ArrayList<>();
        double x = projX, z = projZ, left = GROUND_AHEAD, carry = GROUND_STEP * 0.5;
        double baseY = p.getY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = seg + 1; i < xs.length && left > 0; i++) {
            double nx = xs[i] + 0.5, nz = zs[i] + 0.5;
            double l = Math.hypot(nx - x, nz - z);
            if (l < 1e-3) continue;
            double dx = (nx - x) / l, dz = (nz - z) / l;
            double t = carry;
            while (t < l && left > 0) {
                double ax = x + dx * t, az = z + dz * t;
                double gy = groundY(level, pos, (int) Math.floor(ax), (int) Math.floor(az), (int) Math.floor(baseY));
                if (!Double.isNaN(gy)) {
                    baseY = gy;
                    out.add(new float[]{(float) ax, (float) gy + 0.06f, (float) az, (float) dx, (float) dz});
                }
                t += GROUND_STEP;
                left -= GROUND_STEP;
            }
            carry = t - l;
            x = nx;
            z = nz;
        }
        float[] flat = new float[out.size() * 5];
        for (int i = 0; i < out.size(); i++) System.arraycopy(out.get(i), 0, flat, i * 5, 5);
        return flat;
    }

    /** Hauteur du dessus du sol en (x, z), cherchée autour de nearY (ponts, tunnels) ; NaN si le chunk n'est pas chargé. */
    private static double groundY(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z, int nearY) {
        if (!level.hasChunk(x >> 4, z >> 4)) return Double.NaN;
        for (int y = nearY + 4; y >= nearY - 10; y--) {
            pos.set(x, y - 1, z);
            var below = level.getBlockState(pos).getCollisionShape(level, pos);
            if (below.isEmpty()) continue;
            pos.set(x, y, z);
            if (level.getBlockState(pos).isSolidRender(level, pos)) continue;
            // marquage, tapis, dalle posés dessus : on se met sur leur dessus
            var here = level.getBlockState(pos).getCollisionShape(level, pos);
            return here.isEmpty() ? y - 1 + below.max(net.minecraft.core.Direction.Axis.Y) : y + here.max(net.minecraft.core.Direction.Axis.Y);
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    // ------------------------------------------------------------------ dessin sur les cartes

    /**
     * Trajet restant (depuis la projection du joueur) converti en coordonnées écran ; null si aucun itinéraire.
     * toScreen : (x, z) monde -> {sx, sy}.
     */
    public static float[][] screenPoints(java.util.function.BiFunction<Double, Double, float[]> toScreen) {
        if (!active()) return null;
        int n = xs.length - seg;
        float[] sx = new float[n], sy = new float[n];
        float[] s = toScreen.apply(projX, projZ);
        sx[0] = s[0];
        sy[0] = s[1];
        for (int i = 1; i < n; i++) {
            s = toScreen.apply(xs[seg + i] + 0.5, zs[seg + i] + 0.5);
            sx[i] = s[0];
            sy[i] = s[1];
        }
        return new float[][]{sx, sy};
    }
}
