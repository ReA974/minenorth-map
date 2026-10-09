package fr.minenorth.map.server;

import fr.minenorth.map.waypoint.Waypoint;
import fr.minenorth.map.waypoint.WaypointType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Types de points définis dans la config serveur (« id;Libellé;RRGGBB »). Repli sur la liste par défaut si la config est illisible. */
public final class WaypointTypes {
    private WaypointTypes() {}

    public static final List<String> DEFAULTS = List.of(
            "entreprise;Entreprise;1E88E5",
            "administration;Administration;8E24AA",
            "police;Police;3949AB",
            "secours;Secours;E53935",
            "sante;Santé;43A047",
            "commerce;Commerce;FB8C00",
            "transport;Transport;00ACC1",
            "loisir;Loisir;EC407A",
            "logement;Logement;795548",
            "autre;Autre;FDD835");

    private static Map<String, WaypointType> parse(List<? extends String> lines) {
        Map<String, WaypointType> out = new LinkedHashMap<>();
        for (String s : lines) {
            String[] p = s.split(";");
            if (p.length < 3) continue;
            String id = p[0].trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty() || !id.matches("[a-z0-9_-]+")) continue;
            String hex = p[2].trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (!hex.matches("[0-9a-fA-F]{6}")) continue;
            out.putIfAbsent(id, new WaypointType(id, p[1].trim().isEmpty() ? id : p[1].trim(), Integer.parseInt(hex, 16)));
        }
        return out;
    }

    public static Map<String, WaypointType> all() {
        Map<String, WaypointType> m;
        try {
            m = parse(ServerMapConfig.TYPES.get());
        } catch (IllegalStateException e) {   // config pas encore chargée
            m = new LinkedHashMap<>();
        }
        if (m.isEmpty()) m = parse(DEFAULTS);
        return m;
    }

    public static List<WaypointType> list() { return new ArrayList<>(all().values()); }

    public static WaypointType get(String id) {
        return id == null ? null : all().get(id.toLowerCase(Locale.ROOT));
    }

    /** Type utilisé quand l'id est inconnu : « autre » s'il existe, sinon le dernier de la liste. */
    public static WaypointType fallback() {
        Map<String, WaypointType> m = all();
        WaypointType t = m.get(Waypoint.DEFAULT_TYPE);
        if (t != null) return t;
        WaypointType last = null;
        for (WaypointType w : m.values()) last = w;
        return last;
    }
}
