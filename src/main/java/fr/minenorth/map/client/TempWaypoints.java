package fr.minenorth.map.client;

import fr.minenorth.map.waypoint.Waypoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repères perso temporaires : propres à chaque joueur (jamais envoyés au serveur),
 * effacés à l'arrivée, à la déconnexion ou après la durée configurée.
 */
public final class TempWaypoints {
    public static final int MAX = 5;
    public static final int COLOR = 0xFF55FF;

    private record Entry(Waypoint wp, long created) {}

    private static final List<Entry> entries = new ArrayList<>();
    private static int counter;
    /** Repères poussés par le serveur (autre mod, ex. secours) : ni limite, ni expiration, remplacés par nom. */
    private static final List<Waypoint> external = new ArrayList<>();
    /** Glissement des repères serveur qui bougent (véhicule suivi, balise) : de l'ancienne position vers la nouvelle en {@value #GLIDE_MS} ms. */
    private record Glide(double fx, double fy, double fz, long start) {}
    private static final java.util.Map<String, Glide> glides = new java.util.HashMap<>();
    private static final long GLIDE_MS = 1100;

    /** Position affichée d'un repère serveur : interpolée tant que le glissement n'est pas fini (pas de saut sur la carte). */
    private static Waypoint shown(Waypoint w) {
        Glide gl = glides.get(w.name());
        if (gl == null) return w;
        double t = (System.currentTimeMillis() - gl.start()) / (double) GLIDE_MS;
        if (t >= 1) { glides.remove(w.name()); return w; }
        return w.withPos(w.dimension(), (int) Math.round(gl.fx() + (w.x() - gl.fx()) * t), (int) Math.round(gl.fy() + (w.y() - gl.fy()) * t),
                (int) Math.round(gl.fz() + (w.z() - gl.fz()) * t));
    }

    private TempWaypoints() {}

    public static Waypoint add(String dim, int x, int y, int z) {
        if (entries.size() >= MAX) entries.remove(0); // remplace le plus ancien
        counter++;
        Waypoint w = new Waypoint("Repère perso " + counter, dim, x, y, z, COLOR);
        entries.add(new Entry(w, System.currentTimeMillis()));
        return w;
    }

    public static void remove(Waypoint w) {
        if (ClientWaypoints.isTracked(w)) ClientWaypoints.track(null);
        entries.removeIf(e -> e.wp.name().equals(w.name()));
        external.removeIf(e -> e.name().equals(w.name()));
        glides.remove(w.name());
    }

    public static boolean isTemp(Waypoint w) {
        for (Entry e : entries) if (e.wp.name().equals(w.name())) return true;
        for (Waypoint e : external) if (e.name().equals(w.name())) return true;
        return false;
    }

    public static List<Waypoint> all() {
        List<Waypoint> out = new ArrayList<>(entries.size() + external.size());
        for (Entry e : entries) out.add(e.wp);
        for (Waypoint e : external) out.add(shown(e));
        return Collections.unmodifiableList(out);
    }

    public static List<Waypoint> inDimension(String dim) {
        List<Waypoint> out = new ArrayList<>();
        for (Entry e : entries) if (e.wp.dimension().equals(dim)) out.add(e.wp);
        for (Waypoint e : external) if (e.dimension().equals(dim)) out.add(shown(e));
        return out;
    }

    public static Waypoint byName(String name) {
        for (Entry e : entries) if (e.wp.name().equalsIgnoreCase(name)) return e.wp;
        for (Waypoint e : external) if (e.name().equalsIgnoreCase(name)) return shown(e);
        return null;
    }

    /** Ajoute / remplace un repère venu du serveur ; guide = lance le guidage dessus. */
    public static void setExternal(Waypoint w, boolean guide) {
        Waypoint old = null;
        for (Waypoint e : external) if (e.name().equals(w.name())) old = e;
        if (old != null && old.dimension().equals(w.dimension())) {
            Waypoint from = shown(old);   // repart de là où il est affiché : pas d'à-coup si la mise à jour arrive en plein glissement
            double d = Math.abs(from.x() - w.x()) + Math.abs(from.z() - w.z());
            if (d > 0 && d < 400) glides.put(w.name(), new Glide(from.x(), from.y(), from.z(), System.currentTimeMillis()));
            else glides.remove(w.name());
        } else glides.remove(w.name());
        external.removeIf(e -> e.name().equals(w.name()));
        external.add(w);
        if (guide) ClientWaypoints.track(w);
    }

    public static void removeExternal(String name) {
        Waypoint w = null;
        for (Waypoint e : external) if (e.name().equalsIgnoreCase(name)) w = e;
        if (w != null) remove(w);
    }

    /** Retire tous les repères serveur dont le nom commence par ce préfixe ("" = tous). */
    public static void clearExternal(String prefix) {
        for (Waypoint w : new ArrayList<>(external)) if (w.name().startsWith(prefix)) remove(w);
    }

    /** Supprime les repères expirés. */
    public static void tick() {
        int minutes = ClientConfig.TEMP_MINUTES.get();
        if (minutes <= 0 || entries.isEmpty()) return;
        long limit = System.currentTimeMillis() - minutes * 60_000L;
        for (Entry e : new ArrayList<>(entries)) if (e.created < limit) remove(e.wp);
    }

    public static void clear() {
        external.clear();
        entries.clear();
        glides.clear();
    }
}
