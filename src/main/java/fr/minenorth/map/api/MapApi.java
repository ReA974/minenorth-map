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
     * livraison, station, mairie, casino, sport, entreprise, atelier, gang, maison, aeroport, port, transport, loisir, recolte, petshop,
     * vetements, pharmacie, veterinaire ; acheteurs : rachat, rachat_bois, rachat_minerai, rachat_recolte, rachat_poisson, rachat_petrole).
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

    /**
     * Point de repère serveur le plus proche de (x, z) dans la dimension dim dont l'icône (choisie ou devinée d'après le nom) est
     * l'une de celles demandées (ids séparés par des virgules, ex. « banque » ou « garage,concession »), ou dont le nom contient
     * l'un des mots « nom:mot » (ex. « nom:auto-école »).
     * Renvoie « nom;x;y;z », ou null s'il n'y en a aucun. Utilisé par le mod Bienvenue (parcours guidé des nouveaux joueurs).
     */
    public static String nearestWaypoint(net.minecraft.server.MinecraftServer server, String dim, int x, int z, String icons) {
        if (icons == null || icons.isBlank()) return null;
        java.util.Set<String> wanted = new java.util.HashSet<>();
        java.util.List<String> words = new java.util.ArrayList<>();
        for (String s : icons.split(",")) {
            if (s.isBlank()) continue;
            if (s.trim().startsWith("nom:")) words.add(s.trim().substring(4).toLowerCase(java.util.Locale.ROOT));
            else wanted.add(s.trim());
        }
        fr.minenorth.map.waypoint.Waypoint best = null;
        double bd = Double.MAX_VALUE;
        for (fr.minenorth.map.waypoint.Waypoint w : fr.minenorth.map.waypoint.WaypointSavedData.get(server).all()) {
            if (dim != null && !dim.equals(w.dimension())) continue;
            String ic = fr.minenorth.map.waypoint.MapIcons.resolve(w);
            if (ic.isEmpty()) ic = fr.minenorth.map.waypoint.MapIcons.guess(w.name());
            String low = w.name().toLowerCase(java.util.Locale.ROOT);
            if (!wanted.contains(ic) && words.stream().noneMatch(low::contains)) continue;
            double d = (double) (w.x() - x) * (w.x() - x) + (double) (w.z() - z) * (w.z() - z);
            if (d < bd) {
                bd = d;
                best = w;
            }
        }
        return best == null ? null : best.name() + ";" + best.x() + ";" + best.y() + ";" + best.z();
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
        fr.minenorth.map.server.LandWaypoints.reconcile(server);
    }

    private static void send(ServerPlayer p, MarkerPacket pkt) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }
}
