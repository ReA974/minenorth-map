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

    public static void removeMarker(ServerPlayer p, String name) {
        send(p, new MarkerPacket(MarkerPacket.REMOVE, name, "", 0, 0, 0, 0, false));
    }

    /** Retire tous les repères poussés dont le nom commence par ce préfixe. */
    public static void clearMarkers(ServerPlayer p, String prefix) {
        send(p, new MarkerPacket(MarkerPacket.CLEAR_PREFIX, prefix, "", 0, 0, 0, 0, false));
    }

    private static void send(ServerPlayer p, MarkerPacket pkt) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }
}
