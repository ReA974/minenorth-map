package fr.minenorth.map.waypoint;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Catégories d'icônes de la carte (une texture 32x32 par catégorie dans textures/map_icons/). Aucune classe client-only ici.
 * Une icône vide ("") = aspect classique : petit carré de la couleur du type. Les couleurs doivent rester identiques à tools/gen_map_icons.py.
 */
public final class MapIcons {
    private MapIcons() {}

    public record Icon(String id, String label, int color) {}

    private static final Map<String, Icon> ALL = new LinkedHashMap<>();

    private static void add(String id, String label, int rgb) { ALL.put(id, new Icon(id, label, rgb)); }

    static {
        add("garage", "Garage", 0x607D8B);
        add("concession", "Concession", 0x0096C7);
        add("boutique", "Boutique", 0xFB8C00);
        add("banque", "Banque / ATM", 0x2E7D32);
        add("police", "Police", 0x3949AB);
        add("hopital", "Hôpital", 0xE53935);
        add("pompier", "Pompiers / secours", 0xD84315);
        add("restaurant", "Restaurant", 0xFF7043);
        add("bar", "Bar / club", 0x8E24AA);
        add("livraison", "Livraison", 0xFFAA33);
        add("station", "Station-service", 0x00897B);
        add("mairie", "Mairie", 0x795548);
        add("casino", "Casino", 0xC6285A);
        add("sport", "Salle de sport", 0x43A047);
        add("entreprise", "Entreprise", 0x1E88E5);
        add("atelier", "Atelier", 0x757575);
        add("gang", "Gang / territoire", 0x37474F);
        add("maison", "Maison", 0x8D6E63);
        add("aeroport", "Aéroport", 0x039BE5);
        add("port", "Port", 0x0277BD);
        add("transport", "Transport", 0x00ACC1);
        add("loisir", "Loisir", 0xEC407A);
        add("recolte", "Récolte", 0x7CB342);
    }

    public static Map<String, Icon> all() { return ALL; }

    public static boolean valid(String id) { return id != null && ALL.containsKey(id); }

    public static Icon get(String id) { return id == null ? null : ALL.get(id); }

    /** Icône par défaut d'un type de point (vide pour « autre » : l'ancien aspect est conservé). */
    public static String forType(String type) {
        if (type == null) return "";
        return switch (type) {
            case "entreprise" -> "entreprise";
            case "administration" -> "mairie";
            case "police" -> "police";
            case "secours" -> "pompier";
            case "sante" -> "hopital";
            case "commerce" -> "boutique";
            case "transport" -> "transport";
            case "loisir" -> "loisir";
            case "logement" -> "maison";
            case "recolte" -> "recolte";
            case "traitement" -> "atelier";
            case "garage" -> "garage";
            default -> "";
        };
    }

    private static final String[][] KEYWORDS = {
            {"aeroport|avion|heliport|helico", "aeroport"},
            {"\\bport\\b|\\bquai|marina|bateau|\\bnavire", "port"},
            {"garage|mecanic|depann|fourriere", "garage"},
            {"concession|vehicule|\\bauto\\b|\\bmoto\\b", "concession"},
            {"banque|\\batm\\b|distributeur|eurobank", "banque"},
            {"police|commissariat|gendarm|prison|\\bradar|\\bhack", "police"},
            {"hopital|clinique|urgence|blesse|ambulan", "hopital"},
            {"pompier|caserne|incendie|\\bfeu\\b", "pompier"},
            {"restaurant|\\bresto|pizz|burger|snack|kebab|cafe|brasserie|boulang|traiteur", "restaurant"},
            {"\\bbar\\b|\\bpub\\b|boite de nuit|night|\\bclub\\b|tavern", "bar"},
            {"livraison|coursier|colis", "livraison"},
            {"station|essence|carburant", "station"},
            {"mairie|prefecture|tribunal|administration|\\bpole emploi|notaire", "mairie"},
            {"casino|poker|jeux", "casino"},
            {"sport|\\bgym|fitness|musculation", "sport"},
            {"gang|territoire|\\bclan\\b|\\bmafia", "gang"},
            {"boutique|magasin|\\bshop|superette|epicerie|supermarche|marche|vetement|armurerie|pharmacie", "boutique"},
            {"maison|appart|villa|logement|immeuble", "maison"},
            {"atelier|menuiserie|forge|usine|fonderie|ferme|scierie|raffinerie", "atelier"},
    };
    private static final Pattern[] PATTERNS = new Pattern[KEYWORDS.length];

    static {
        for (int i = 0; i < KEYWORDS.length; i++) PATTERNS[i] = Pattern.compile(KEYWORDS[i][0]);
    }

    /** Devine une icône d'après le nom (sans accents, minuscules). "" si rien ne correspond. */
    public static String guess(String name) {
        if (name == null || name.isEmpty()) return "";
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        for (int i = 0; i < PATTERNS.length; i++) if (PATTERNS[i].matcher(s).find()) return KEYWORDS[i][1];
        return "";
    }

    /**
     * Icône réellement affichée d'un point serveur : celle choisie par l'admin, sinon déduite du type
     * (entreprises : d'après le nom, ex. « Chez Marco Pizzeria » = restaurant). "" = ancien carré coloré.
     */
    public static String resolve(Waypoint w) {
        if (valid(w.icon())) return w.icon();
        String byType = forType(w.type());
        if (w.type().equals("entreprise") || w.type().equals("commerce") || w.type().equals("loisir")) {
            String g = guess(w.name());
            if (!g.isEmpty()) return g;
        }
        return byType;
    }
}
