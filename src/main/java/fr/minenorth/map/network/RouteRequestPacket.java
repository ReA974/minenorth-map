package fr.minenorth.map.network;

import fr.minenorth.map.server.GpsRouter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : « calcule l'itinéraire GPS de ma position jusqu'au point guidé (name, en x / z) ». */
public record RouteRequestPacket(String name, String dim, int x, int z) {

    public static void encode(RouteRequestPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.name, 64);
        buf.writeUtf(p.dim, 256);
        buf.writeInt(p.x);
        buf.writeInt(p.z);
    }

    public static RouteRequestPacket decode(FriendlyByteBuf buf) {
        return new RouteRequestPacket(buf.readUtf(64), buf.readUtf(256), buf.readInt(), buf.readInt());
    }

    public static void handle(RouteRequestPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sender != null) GpsRouter.request(sender, p.name, p.dim, p.x, p.z);
        });
        ctx.get().setPacketHandled(true);
    }
}
