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
            "recolte;Récolte;9CCC65",
            "traitement;Traitement;26A69A",
            "garage;Garage;607D8B",
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
        return withBuiltins(m);
    }

    /** Types de base ajoutés après coup : une config serveur déjà écrite ne les contient pas (id, libellé, couleur). */
    private static final String[][] BUILTINS = {
            {"recolte", "Récolte", "9CCC65"}, {"traitement", "Traitement", "26A69A"}, {"garage", "Garage", "607D8B"}};

    /**
     * Tant que la config ne définit pas elle-même un de ces types (pour changer sa couleur ou son libellé), il est ajouté juste avant
     * « autre » : pas besoin de modifier une config déjà écrite.
     */
    private static Map<String, WaypointType> withBuiltins(Map<String, WaypointType> m) {
        Map<String, WaypointType> out = new LinkedHashMap<>();
        for (Map.Entry<String, WaypointType> e : m.entrySet()) {
            if (e.getKey().equals(Waypoint.DEFAULT_TYPE)) addMissing(out, m);
            out.put(e.getKey(), e.getValue());
        }
        addMissing(out, m);   // pas de « autre » dans la config : à la fin
        return out;
    }

    private static void addMissing(Map<String, WaypointType> out, Map<String, WaypointType> config) {
        for (String[] t : BUILTINS) {
            if (!config.containsKey(t[0])) out.putIfAbsent(t[0], new WaypointType(t[0], t[1], Integer.parseInt(t[2], 16)));
        }
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
