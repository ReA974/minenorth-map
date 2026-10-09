package fr.minenorth.map;

import com.mojang.logging.LogUtils;
import fr.minenorth.map.client.ClientConfig;
import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.server.ServerMapConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * MineNorth Carte : minicarte + carte plein écran pour serveur RP.
 * - La carte est dessinée par le SERVEUR (commune à tous) et envoyée aux joueurs.
 * - Aucun autre joueur n'est jamais dessiné.
 * - Les points de repère sont gérés par les OP via /carte point ...
 */
@Mod(MineNorthMap.MODID)
public class MineNorthMap {
    public static final String MODID = "minenorth_map";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MineNorthMap() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // COMMON (dossier config/) et non SERVER (<monde>/serverconfig/) : le fichier du monde était remis à zéro à chaque redémarrage
        // sur certains serveurs (hybrides Forge/Fabric, monde restauré...). Même nom, mêmes sections : on peut copier l'ancien fichier.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ServerMapConfig.SPEC, "minenorth_map-server.toml");
        NetworkHandler.register();
    }
}
