package fr.minenorth.map.server;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/minenorth_map-server.toml (ancien emplacement : <monde>/serverconfig/minenorth_map-server.toml) */
public final class ServerMapConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue AUTO_MAP_LOADED;
    public static final ForgeConfigSpec.IntValue LOADED_PER_TICK;
    public static final ForgeConfigSpec.IntValue UPDATES_PER_TICK;
    public static final ForgeConfigSpec.IntValue GENERATE_PER_TICK;
    public static final ForgeConfigSpec.IntValue REQUESTS_PER_TICK;
    public static final ForgeConfigSpec.IntValue PLAYERS_OP_LEVEL;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> TYPES;
    public static final ForgeConfigSpec.BooleanValue GPS_ENABLED;
    public static final ForgeConfigSpec.IntValue GPS_MAX_DISTANCE;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> ROAD_BLOCKS;

    /** Blocs de route par défaut : id exact (« mod:bloc ») ou morceau de nom (« asphalt » = tout bloc dont l'id contient asphalt). */
    public static final java.util.List<String> DEFAULT_ROADS = java.util.List.of(
            "asphalt", "road", "tarmac", "bitume", "goudron",
            "minecraft:gray_concrete", "minecraft:black_concrete", "minecraft:gray_concrete_powder", "minecraft:black_concrete_powder",
            "minecraft:dirt_path");

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("carte");
        AUTO_MAP_LOADED = b.comment("Dessiner automatiquement sur la carte commune chaque chunk chargé par un joueur")
                .define("cartographieAuto", true);
        LOADED_PER_TICK = b.comment("Chunks nouvellement chargés dessinés par tick")
                .defineInRange("chunksChargesParTick", 16, 1, 128);
        UPDATES_PER_TICK = b.comment("Chunks modifiés (blocs posés/cassés) redessinés par tick")
                .defineInRange("misesAJourParTick", 8, 1, 128);
        GENERATE_PER_TICK = b.comment("Chunks traités par tick pendant /carte generer (plus = plus rapide mais plus de lag)")
                .defineInRange("generationParTick", 4, 1, 64);
        REQUESTS_PER_TICK = b.comment("Régions de carte envoyées aux joueurs par tick (tous joueurs confondus)")
                .defineInRange("envoisParTick", 6, 1, 64);
        PLAYERS_OP_LEVEL = b.comment("Niveau d'op qui peut afficher les joueurs sur la grande carte (en plus de la permission",
                        "\"Carte : voir les joueurs\" du panneau admin). 5 = personne via l'op, uniquement via le panneau.")
                .defineInRange("niveauOpVoirJoueurs", 4, 0, 5);
        b.pop();
        b.push("points");
        TYPES = b.comment("Types de points de repère : \"id;Libellé;RRGGBB\". Chaque type a une couleur fixe, les joueurs filtrent par type.",
                        "Le type par défaut des nouveaux points est \"autre\" (à garder dans la liste).")
                .defineList("types", WaypointTypes.DEFAULTS, o -> o instanceof String);
        b.pop();
        b.push("gps");
        GPS_ENABLED = b.comment("Itinéraire GPS : le trajet vers le point guidé suit les routes (calculé par le serveur)")
                .define("itineraire", true);
        GPS_MAX_DISTANCE = b.comment("Distance maximale (blocs) d'un itinéraire calculé ; au-delà, guidage en ligne droite")
                .defineInRange("distanceMax", 8000, 256, 30000);
        ROAD_BLOCKS = b.comment("Blocs de route : id exact (\"minecraft:gray_concrete\") ou morceau d'id (\"asphalt\").",
                        "Après un changement, refaire /carte generer sur la ville pour mettre les routes à jour (/carte route ajouter : bloc sous tes pieds).")
                .defineList("blocsRoute", DEFAULT_ROADS, o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private ServerMapConfig() {}
}
