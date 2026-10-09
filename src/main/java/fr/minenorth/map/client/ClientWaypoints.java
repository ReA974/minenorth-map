package fr.minenorth.map.client;

import fr.minenorth.map.waypoint.Waypoint;
import fr.minenorth.map.waypoint.WaypointType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Copie côté client des points de repère envoyés par le serveur. Aucune classe client-only ici. */
public final class ClientWaypoints {
    private static volatile List<Waypoint> waypoints = Collections.emptyList();
    private static volatile List<WaypointType> types = Collections.emptyList();
    /** Nom du point actuellement suivi (guidage), ou null. */
    private static volatile String tracked;

    private ClientWaypoints() {}

    public static void set(List<WaypointType> typeList, List<Waypoint> list) {
        types = Collections.unmodifiableList(new ArrayList<>(typeList));
        waypoints = Collections.unmodifiableList(new ArrayList<>(list));
        if (tracked != null && getTracked() == null) tracked = null;
    }

    public static void clear() {
        waypoints = Collections.emptyList();
        types = Collections.emptyList();
        tracked = null;
    }

    public static List<Waypoint> all() { return waypoints; }

    public static List<Waypoint> inDimension(String dim) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : waypoints) if (w.dimension().equals(dim)) out.add(w);
        return out;
    }

    public static List<WaypointType> types() { return types; }

    public static WaypointType typeOf(Waypoint w) {
        for (WaypointType t : types) if (t.id().equals(w.type())) return t;
        return null;
    }

    public static boolean isTypeHidden(String id) {
        return ClientConfig.HIDDEN_TYPES.get().contains(id);
    }

    public static void setTypeHidden(String id, boolean hidden) {
        java.util.List<String> l = new ArrayList<>(ClientConfig.HIDDEN_TYPES.get());
        l.remove(id);
        if (hidden) l.add(id);
        ClientConfig.HIDDEN_TYPES.set(l);
        ClientConfig.HIDDEN_TYPES.save();
    }

    public static void setOnlyOpen(boolean v) {
        ClientConfig.ONLY_OPEN.set(v);
        ClientConfig.ONLY_OPEN.save();
    }

    /** Passe les filtres de la carte (type masqué, entreprises fermées masquées). Les repères perso (sans type) passent toujours. */
    public static boolean passesFilter(Waypoint w) {
        if (w.type().isEmpty()) return true;
        if (isTypeHidden(w.type())) return false;
        return !(ClientConfig.ONLY_OPEN.get() && w.status() == Waypoint.STATUS_CLOSED);
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
