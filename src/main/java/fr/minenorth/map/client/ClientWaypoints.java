package fr.minenorth.map.client;

import fr.minenorth.map.waypoint.Waypoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Copie côté client des points de repère envoyés par le serveur. Aucune classe client-only ici. */
public final class ClientWaypoints {
    private static volatile List<Waypoint> waypoints = Collections.emptyList();
    /** Nom du point actuellement suivi (guidage), ou null. */
    private static volatile String tracked;

    private ClientWaypoints() {}

    public static void set(List<Waypoint> list) {
        waypoints = Collections.unmodifiableList(new ArrayList<>(list));
        if (tracked != null && getTracked() == null) tracked = null;
    }

    public static void clear() {
        waypoints = Collections.emptyList();
        tracked = null;
    }

    public static List<Waypoint> all() { return waypoints; }

    public static List<Waypoint> inDimension(String dim) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : waypoints) if (w.dimension().equals(dim)) out.add(w);
        return out;
    }

    public static Waypoint getTracked() {
        String t = tracked;
        if (t == null) return null;
        for (Waypoint w : waypoints) if (w.name().equalsIgnoreCase(t)) return w;
        return TempWaypoints.byName(t);
    }

    public static boolean isTracked(Waypoint w) {
        return tracked != null && tracked.equalsIgnoreCase(w.name());
    }

    public static void track(Waypoint w) { tracked = w == null ? null : w.name(); }

    public static void toggleTrack(Waypoint w) {
        if (isTracked(w)) tracked = null; else tracked = w.name();
    }
}
