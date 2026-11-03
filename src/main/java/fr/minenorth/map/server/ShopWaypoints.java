package fr.minenorth.map.server;

import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.waypoint.WaypointSavedData;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Map;

/**
 * Anciens points automatiques des boutiques de l'annuaire (clés « shop:<id> » dans autoMap / suppressed). Depuis le 03/11, les boutiques sont
 * posées à la main sur la carte (mode Édition, icônes « boutiques », « telephone », rachat…) : au démarrage, on retire une fois les points
 * encore automatiques. Un point modifié par l'admin est déjà devenu un point normal (sorti d'autoMap) : il est gardé.
 */
public final class ShopWaypoints {
    private ShopWaypoints() {}

    /** Préfixe des clés de ces points dans autoMap / suppressed (BuildingWaypoints ne les touche pas). */
    public static final String KEY = "shop:";

    private static boolean done;

    /** Retire les points automatiques des boutiques (une fois par démarrage du serveur). Renvoie vrai si la carte a changé. */
    public static synchronized boolean purge(MinecraftServer s) {
        if (done) return false;
        done = true;
        WaypointSavedData data = WaypointSavedData.get(s);
        boolean changed = data.suppressed().removeIf(k -> k.startsWith(KEY));
        for (Map.Entry<String, String> e : new ArrayList<>(data.autoMap().entrySet())) {
            if (!e.getKey().startsWith(KEY)) continue;
            data.remove(e.getValue());
            data.autoMap().remove(e.getKey());
            changed = true;
        }
        if (changed) {
            data.setDirty();
            NetworkHandler.syncAll(s);
        }
        return changed;
    }
}
