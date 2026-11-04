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
        add("petshop", "Animalerie", 0x00BFA5);
        add("vetements", "Vêtements", 0xAD1457);
        add("pharmacie", "Pharmacie", 0x00C853);
        add("veterinaire", "Vétérinaire", 0x9575CD);
        add("telephone", "Téléphonie", 0x5E35B1);
        add("coiffeur", "Coiffeur / barbier", 0x00838F);
        add("tatoueur", "Tatoueur", 0xC2185B);
        // suivi de véhicules (mod Vol, repères poussés par le serveur) : traceur GPS du propriétaire, véhicule volé, balise espion
        add("vehicule_gps", "Véhicule suivi (GPS)", 0x30B070);
        add("vehicule_vole", "Véhicule volé", 0xD62838);
        add("balise", "Balise sur véhicule", 0xB03CDC);
        add("balise_joueur", "Balise sur personne", 0xB03CDC);
        // magasins de l'annuaire « Boutiques » du téléphone (anciens points automatiques ; à poser à la main) : même sac framboise que l'application
        add("boutiques", "Magasins (annuaire)", 0xE0457B);
        // acheteurs de ressources : même couleur dorée, une seule case dans les filtres (voir filterKey)
        add("rachat", "Acheteurs", 0xC79100);
        add("rachat_bois", "Acheteur de bois", 0xC79100);
        add("rachat_minerai", "Acheteur de minerais", 0xC79100);
        add("rachat_recolte", "Acheteur de récoltes", 0xC79100);
        add("rachat_poisson", "Acheteur de poisson", 0xC79100);
        add("rachat_petrole", "Acheteur de pétrole", 0xC79100);
    }

    /** Case de filtre d'une icône : toutes les icônes d'acheteur partagent la case « rachat ». */
    public static String filterKey(String id) {
        return id != null && id.startsWith("rachat_") ? "rachat" : id;
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
            {"veterinaire|\\bveto\\b", "veterinaire"},
            {"tatou|tattoo|piercing", "tatoueur"},
            {"coiffe|coiffure|barbier|barber|salon", "coiffeur"},
            {"pharmac|parapharm", "pharmacie"},
            {"telephon|smartphone|\\bmobile|\\bportable", "telephone"},
            {"animalerie|\\bpet ?shop|\\banimaux\\b", "petshop"},
            {"vetement|habit|pret.a.porter|fripe|friperie|couture|textile", "vetements"},
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
            {"boutique|magasin|\\bshop|superette|epicerie|supermarche|marche|armurerie", "boutique"},
            {"maison|appart|villa|logement|immeuble", "maison"},
            {"atelier|menuiserie|forge|usine|fonderie|ferme|scierie|raffinerie", "atelier"},
    };
    private static final Pattern[] PATTERNS = new Pattern[KEYWORDS.length];

    /** Acheteur de ressources (testé avant tout le reste : « Rachat de bois » n'est pas un atelier), puis la ressource rachetée. */
    private static final Pattern BUYER = Pattern.compile("acheteur|rachat|revente|reprise|recycl");
    private static final String[][] RESOURCES = {
            {"bois|bucheron|buche|scierie|\\bplanche", "rachat_bois"},
            {"minerai|\\bmine\\b|\\bor\\b|\\bfer\\b|diamant|charbon|cuivre|emeraude|lapis|redstone", "rachat_minerai"},
            {"poisson|peche|pecheur|crustace", "rachat_poisson"},
            {"petrole|\\bbrut\\b|raffin", "rachat_petrole"},
            {"fruit|recolte|ferme|\\bble\\b|legume|pomme|agricol|cereale", "rachat_recolte"},
    };
    private static final Pattern[] RESOURCE_PATTERNS = new Pattern[RESOURCES.length];

    static {
        for (int i = 0; i < KEYWORDS.length; i++) PATTERNS[i] = Pattern.compile(KEYWORDS[i][0]);
        for (int i = 0; i < RESOURCES.length; i++) RESOURCE_PATTERNS[i] = Pattern.compile(RESOURCES[i][0]);
    }

    /** Devine une icône d'après le nom (sans accents, minuscules). "" si rien ne correspond. */
    public static String guess(String name) {
        if (name == null || name.isEmpty()) return "";
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        if (BUYER.matcher(s).find()) {
            for (int i = 0; i < RESOURCE_PATTERNS.length; i++) if (RESOURCE_PATTERNS[i].matcher(s).find()) return RESOURCES[i][1];
            return "rachat";
        }
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
