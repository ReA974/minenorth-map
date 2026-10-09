package fr.minenorth.map.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/minenorth_map-client.toml */
public final class ClientConfig {
    public enum Position { HAUT_DROITE, HAUT_GAUCHE, BAS_DROITE, BAS_GAUCHE }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue MINIMAP_ENABLED;
    public static final ForgeConfigSpec.IntValue MINIMAP_SIZE;
    public static final ForgeConfigSpec.DoubleValue MINIMAP_ZOOM;
    public static final ForgeConfigSpec.EnumValue<Position> MINIMAP_POSITION;
    public static final ForgeConfigSpec.BooleanValue SHOW_COORDS;
    public static final ForgeConfigSpec.BooleanValue SHOW_GUIDE;
    public static final ForgeConfigSpec.BooleanValue HIDE_WAYPOINTS;
    public static final ForgeConfigSpec.IntValue TEMP_MINUTES;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> HIDDEN_TYPES;
    public static final ForgeConfigSpec.BooleanValue ONLY_OPEN;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("minicarte");
        MINIMAP_ENABLED = b.comment("Afficher la minicarte").define("activee", true);
        MINIMAP_SIZE = b.comment("Taille de la minicarte (pixels GUI)").defineInRange("taille", 110, 64, 256);
        MINIMAP_ZOOM = b.comment("Zoom de la minicarte (pixels par bloc)").defineInRange("zoom", 1.0, 0.25, 4.0);
        MINIMAP_POSITION = b.comment("Position à l'écran").defineEnum("position", Position.HAUT_DROITE);
        SHOW_COORDS = b.comment("Afficher les coordonnées sous la minicarte").define("coordonnees", true);
        SHOW_GUIDE = b.comment("Afficher le guidage (flèche + distance) vers le point suivi").define("guidage", true);
        b.pop();
        b.push("points");
        HIDE_WAYPOINTS = b.comment("Masquer les points de repère du serveur sur la carte et la minicarte (bouton sur la carte)")
                .define("masquerPoints", false);
        HIDDEN_TYPES = b.comment("Types de points masqués (filtre du bouton Filtres sur la carte)")
                .defineList("typesMasques", java.util.List.of(), o -> o instanceof String);
        ONLY_OPEN = b.comment("Masquer les entreprises fermées (filtre du bouton Filtres sur la carte)")
                .define("seulementOuvertes", false);
        TEMP_MINUTES = b.comment("Durée de vie des repères perso temporaires, en minutes (0 = jusqu'à la déconnexion)")
                .defineInRange("dureeReperesPerso", 60, 0, 1440);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}
}
