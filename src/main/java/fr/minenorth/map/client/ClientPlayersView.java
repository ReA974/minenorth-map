package fr.minenorth.map.client;

import fr.minenorth.map.network.PlayerPositionsPacket;

import java.util.Collections;
import java.util.List;

/** État côté client de l'affichage staff des joueurs. Aucune classe client-only ici. */
public final class ClientPlayersView {
    private static volatile boolean allowed;
    private static volatile boolean enabled;
    private static volatile List<PlayerPositionsPacket.Entry> players = Collections.emptyList();

    private ClientPlayersView() {}

    public static boolean allowed() { return allowed; }
    public static boolean enabled() { return enabled && allowed; }

    public static void setAllowed(boolean a) {
        allowed = a;
        if (!a) {
            enabled = false;
            players = Collections.emptyList();
        }
    }

    public static void setEnabled(boolean e) {
        enabled = e;
        if (!e) players = Collections.emptyList();
    }

    public static void setPlayers(List<PlayerPositionsPacket.Entry> list) {
        players = enabled() ? List.copyOf(list) : Collections.emptyList();
    }

    public static List<PlayerPositionsPacket.Entry> players() { return players; }

    public static void reset() {
        allowed = false;
        enabled = false;
        players = Collections.emptyList();
    }
}
