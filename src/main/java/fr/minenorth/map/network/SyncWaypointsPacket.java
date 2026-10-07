package fr.minenorth.map.network;

import fr.minenorth.map.client.ClientWaypoints;
import fr.minenorth.map.waypoint.Waypoint;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Serveur -> client : liste complète des points de repère (toutes dimensions). */
public class SyncWaypointsPacket {
    private final List<Waypoint> waypoints;

    public SyncWaypointsPacket(List<Waypoint> waypoints) {
        this.waypoints = waypoints;
    }

    public static void encode(SyncWaypointsPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.waypoints.size());
        for (Waypoint w : pkt.waypoints) w.write(buf);
    }

    public static SyncWaypointsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Waypoint> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(Waypoint.read(buf));
        return new SyncWaypointsPacket(list);
    }

    public static void handle(SyncWaypointsPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        // ClientWaypoints ne référence aucune classe client-only : chargement sûr côté serveur.
        ctx.get().enqueueWork(() -> ClientWaypoints.set(pkt.waypoints));
        ctx.get().setPacketHandled(true);
    }
}
