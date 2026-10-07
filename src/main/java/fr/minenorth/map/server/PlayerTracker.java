package fr.minenorth.map.server;

import fr.minenorth.map.MineNorthMap;
import fr.minenorth.map.network.MapPermPacket;
import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.network.PlayerPositionsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Affichage des joueurs sur la grande carte, réservé au staff autorisé :
 * - permission "Carte : voir les joueurs" (MAP_PLAYERS) du mod minenorth_admin (rôle Gérant par défaut),
 * - ou op du niveau configuré (ServerMapConfig.PLAYERS_OP_LEVEL).
 * Les positions ne sont envoyées QU'AUX joueurs autorisés qui ont activé le bouton.
 */
public final class PlayerTracker {
    private static final Set<UUID> WATCHERS = new HashSet<>();
    private static final Map<UUID, Boolean> KNOWN_PERM = new HashMap<>();
    private static int ticks;

    // pont (optionnel) vers le mod admin, par réflexion pour ne pas créer de dépendance de compilation
    private static boolean adminTried;
    private static Method accessOf, accessHas;
    private static Object mapPerm;

    private PlayerTracker() {}

    public static void reset() {
        WATCHERS.clear();
        KNOWN_PERM.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initAdmin() {
        adminTried = true;
        if (!ModList.get().isLoaded("minenorth_admin")) return;
        try {
            Class<?> access = Class.forName("com.minenorth_admin.staff.Access");
            Class perm = Class.forName("com.minenorth_admin.staff.Perm");
            mapPerm = Enum.valueOf(perm, "MAP_PLAYERS");
            accessOf = access.getMethod("of", ServerPlayer.class);
            accessHas = access.getMethod("has", perm);
            MineNorthMap.LOGGER.info("Carte : permission MAP_PLAYERS du mod admin détectée");
        } catch (Throwable t) {
            accessOf = null;
            MineNorthMap.LOGGER.warn("Carte : mod admin présent mais permission MAP_PLAYERS introuvable ({})", t.toString());
        }
    }

    public static boolean canSeePlayers(ServerPlayer p) {
        if (p.hasPermissions(ServerMapConfig.PLAYERS_OP_LEVEL.get())) return true;
        if (!adminTried) initAdmin();
        if (accessOf == null) return false;
        try {
            Object acc = accessOf.invoke(null, p);
            return Boolean.TRUE.equals(accessHas.invoke(acc, mapPerm));
        } catch (Throwable t) {
            return false;
        }
    }

    public static void onLogin(ServerPlayer p) {
        boolean ok = canSeePlayers(p);
        KNOWN_PERM.put(p.getUUID(), ok);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new MapPermPacket(ok));
    }

    public static void onLogout(UUID id) {
        WATCHERS.remove(id);
        KNOWN_PERM.remove(id);
    }

    public static void setWatching(ServerPlayer p, boolean on) {
        if (on && canSeePlayers(p)) {
            WATCHERS.add(p.getUUID());
            sendTo(p, p.server);
        } else {
            WATCHERS.remove(p.getUUID());
        }
    }

    public static void tick(MinecraftServer server) {
        ticks++;

        // toutes les 5 s : on revérifie les droits (rôle retiré dans le panneau, deop...)
        if (ticks % 100 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                boolean ok = canSeePlayers(p);
                Boolean old = KNOWN_PERM.put(p.getUUID(), ok);
                if (old == null || old != ok) {
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new MapPermPacket(ok));
                }
                if (!ok) WATCHERS.remove(p.getUUID());
            }
        }

        // toutes les 0,5 s : positions aux observateurs
        if (ticks % 10 == 0 && !WATCHERS.isEmpty()) {
            List<PlayerPositionsPacket.Entry> list = snapshot(server);
            PlayerPositionsPacket pkt = new PlayerPositionsPacket(list);
            for (UUID id : new ArrayList<>(WATCHERS)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) {
                    WATCHERS.remove(id);
                    continue;
                }
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
            }
        }
    }

    private static void sendTo(ServerPlayer p, MinecraftServer server) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new PlayerPositionsPacket(snapshot(server)));
    }

    private static List<PlayerPositionsPacket.Entry> snapshot(MinecraftServer server) {
        List<PlayerPositionsPacket.Entry> list = new ArrayList<>();
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            list.add(new PlayerPositionsPacket.Entry(o.getUUID(), o.getGameProfile().getName(),
                    o.level().dimension().location().toString(),
                    (float) o.getX(), (float) o.getY(), (float) o.getZ(), o.getYRot()));
        }
        return list;
    }
}
