package fr.minenorth.map.api;

import fr.minenorth.map.network.MarkerPacket;
import fr.minenorth.map.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * API serveur pour les autres mods MineNorth. Appelée par réflexion (aucune dépendance de compilation) :
 * ne pas changer les noms ni les paramètres. Les repères n'existent que chez le joueur visé (ni sauvegarde, ni autres joueurs).
 */
public final class MapApi {
    private MapApi() {}

    /** Pose (ou déplace, le nom fait office d'identifiant) un repère sur la carte du joueur. guide = démarre le guidage dessus. */
    public static void setMarker(ServerPlayer p, String name, String dim, int x, int y, int z, int color, boolean guide) {
        send(p, new MarkerPacket(MarkerPacket.SET, name, dim, x, y, z, color, guide));
    }

    /**
     * Comme setMarker, avec une icône de catégorie (garage, concession, boutique, banque, police, hopital, pompier, restaurant, bar,
     * livraison, station, mairie, casino, sport, entreprise, atelier, gang, maison, aeroport, port, transport, loisir, recolte).
     * Icône inconnue ou vide : devinée d'après le nom du repère, sinon petit carré coloré.
     */
    public static void setMarker(ServerPlayer p, String name, String dim, int x, int y, int z, int color, boolean guide, String icon) {
        send(p, new MarkerPacket(MarkerPacket.SET, name, dim, x, y, z, color, guide, icon));
    }

    /** Liste des icônes disponibles (ids séparés par des virgules), pour les autres mods. */
    public static String iconIds() {
        return String.join(",", fr.minenorth.map.waypoint.MapIcons.all().keySet());
    }

    /**
     * Choisit l'icône d'un point de repère serveur existant (par son nom). icon vide = automatique (selon le type et le nom).
     * Renvoie faux si le point n'existe pas ou si l'icône est inconnue. Sans effet sur les points posés à la main par un admin
     * tant qu'aucun autre mod ne l'appelle.
     */
    public static boolean setWaypointIcon(net.minecraft.server.MinecraftServer server, String waypointName, String icon) {
        if (icon == null) icon = "";
        if (!icon.isEmpty() && !fr.minenorth.map.waypoint.MapIcons.valid(icon)) return false;
        fr.minenorth.map.waypoint.WaypointSavedData d = fr.minenorth.map.waypoint.WaypointSavedData.get(server);
        fr.minenorth.map.waypoint.Waypoint w = d.get(waypointName);
        if (w == null) return false;
        d.put(w.withIcon(icon));
        NetworkHandler.syncAll(server);
        return true;
    }

    public static void removeMarker(ServerPlayer p, String name) {
        send(p, new MarkerPacket(MarkerPacket.REMOVE, name, "", 0, 0, 0, 0, false));
    }

    /** Retire tous les repères poussés dont le nom commence par ce préfixe. */
    public static void clearMarkers(ServerPlayer p, String prefix) {
        send(p, new MarkerPacket(MarkerPacket.CLEAR_PREFIX, prefix, "", 0, 0, 0, 0, false));
    }

    /** Appelé par le mod Portes après un achat, une location ou une revente : recalcule tout de suite les points des locaux d'entreprise. */
    public static void refreshBuildings(net.minecraft.server.MinecraftServer server) {
        fr.minenorth.map.server.BuildingWaypoints.reconcile(server);
    }

    private static void send(ServerPlayer p, MarkerPacket pkt) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }
}
