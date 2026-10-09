package fr.minenorth.map.network;

import fr.minenorth.map.server.WaypointEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> serveur : modification des points depuis l'interface de la carte (droit OP vérifié côté serveur).
 * name = point visé (ADD : nom du nouveau point), newName = nouveau nom (UPDATE), type = id du type,
 * company = id de l'entreprise liée (-1 = aucune), dim / x / y / z = position (ADD, MOVE).
 */
public record WaypointEditPacket(int action, String name, String newName, String type, int company,
                                 String dim, int x, int y, int z) {
    public static final int REFRESH = 0, ADD = 1, UPDATE = 2, MOVE = 3, REMOVE = 4;

    public static void encode(WaypointEditPacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.action);
        b.writeUtf(p.name, 64);
        b.writeUtf(p.newName, 64);
        b.writeUtf(p.type, 64);
        b.writeInt(p.company);
        b.writeUtf(p.dim, 128);
        b.writeInt(p.x);
        b.writeInt(p.y);
        b.writeInt(p.z);
    }

    public static WaypointEditPacket decode(FriendlyByteBuf b) {
        return new WaypointEditPacket(b.readVarInt(), b.readUtf(64), b.readUtf(64), b.readUtf(64), b.readInt(),
                b.readUtf(128), b.readInt(), b.readInt(), b.readInt());
    }

    public static void handle(WaypointEditPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sender != null) WaypointEditor.handle(sender, p);
        });
        ctx.get().setPacketHandled(true);
    }
}
