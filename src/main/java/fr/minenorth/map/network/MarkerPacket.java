package fr.minenorth.map.network;

import fr.minenorth.map.client.TempWaypoints;
import fr.minenorth.map.waypoint.Waypoint;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur -> client : repère personnel poussé par un autre mod (ex. incendie, blessé). Voir {@link fr.minenorth.map.api.MapApi}. */
public class MarkerPacket {
    public static final int SET = 0, REMOVE = 1, CLEAR_PREFIX = 2;

    private final int action;
    private final String name, dim;
    private final int x, y, z, color;
    private final boolean guide;

    public MarkerPacket(int action, String name, String dim, int x, int y, int z, int color, boolean guide) {
        this.action = action; this.name = name; this.dim = dim; this.x = x; this.y = y; this.z = z; this.color = color; this.guide = guide;
    }

    public static void encode(MarkerPacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.action); b.writeUtf(p.name); b.writeUtf(p.dim);
        b.writeInt(p.x); b.writeInt(p.y); b.writeInt(p.z); b.writeInt(p.color); b.writeBoolean(p.guide);
    }

    public static MarkerPacket decode(FriendlyByteBuf b) {
        return new MarkerPacket(b.readVarInt(), b.readUtf(), b.readUtf(), b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readBoolean());
    }

    public static void handle(MarkerPacket p, Supplier<NetworkEvent.Context> ctx) {
        // TempWaypoints / ClientWaypoints ne référencent aucune classe client-only : chargement sûr côté serveur.
        ctx.get().enqueueWork(() -> {
            switch (p.action) {
                case SET -> TempWaypoints.setExternal(new Waypoint(p.name, p.dim, p.x, p.y, p.z, p.color), p.guide);
                case REMOVE -> TempWaypoints.removeExternal(p.name);
                case CLEAR_PREFIX -> TempWaypoints.clearExternal(p.name);
                default -> {}
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
