package fr.minenorth.map.network;

import fr.minenorth.map.client.ClientPlayersView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Serveur -> client (staff autorisé uniquement) : positions de tous les joueurs connectés. */
public record PlayerPositionsPacket(List<Entry> players) {

    public record Entry(UUID id, String name, String dim, float x, float y, float z, float yaw) {}

    public static void encode(PlayerPositionsPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.players.size());
        for (Entry e : p.players) {
            buf.writeUUID(e.id());
            buf.writeUtf(e.name(), 64);
            buf.writeUtf(e.dim(), 256);
            buf.writeFloat(e.x());
            buf.writeFloat(e.y());
            buf.writeFloat(e.z());
            buf.writeFloat(e.yaw());
        }
    }

    public static PlayerPositionsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            l.add(new Entry(buf.readUUID(), buf.readUtf(64), buf.readUtf(256),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }
        return new PlayerPositionsPacket(l);
    }

    public static void handle(PlayerPositionsPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPlayersView.setPlayers(p.players));
        ctx.get().setPacketHandled(true);
    }
}
