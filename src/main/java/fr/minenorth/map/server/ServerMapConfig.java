package fr.minenorth.map.server;

import net.minecraftforge.common.ForgeConfigSpec;

/** <monde>/serverconfig/minenorth_map-server.toml */
public final class ServerMapConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue AUTO_MAP_LOADED;
    public static final ForgeConfigSpec.IntValue LOADED_PER_TICK;
    public static final ForgeConfigSpec.IntValue UPDATES_PER_TICK;
    public static final ForgeConfigSpec.IntValue GENERATE_PER_TICK;
    public static final ForgeConfigSpec.IntValue REQUESTS_PER_TICK;
    public static final ForgeConfigSpec.IntValue PLAYERS_OP_LEVEL;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> TYPES;

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
        SPEC = b.build();
    }

    private ServerMapConfig() {}
}
