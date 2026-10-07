package fr.minenorth.map.network;

import fr.minenorth.map.server.ServerMapManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : "envoie-moi la région (rx, rz) si elle est plus récente que knownVersion". */
public record RequestRegionPacket(String dim, int rx, int rz, long knownVersion) {

    public static void encode(RequestRegionPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.dim, 256);
        buf.writeInt(p.rx);
        buf.writeInt(p.rz);
        buf.writeLong(p.knownVersion);
    }

    public static RequestRegionPacket decode(FriendlyByteBuf buf) {
        return new RequestRegionPacket(buf.readUtf(256), buf.readInt(), buf.readInt(), buf.readLong());
    }

    public static void handle(RequestRegionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sender != null) ServerMapManager.onRequest(sender, p.dim, p.rx, p.rz, p.knownVersion);
        });
        ctx.get().setPacketHandled(true);
    }
}
