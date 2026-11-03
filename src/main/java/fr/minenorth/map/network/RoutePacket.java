package fr.minenorth.map.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur -> client : itinéraire GPS vers le point name (points du trajet en x / z), ou ok = false (guidage en ligne droite). */
public record RoutePacket(String name, boolean ok, int[] xs, int[] zs) {

    public static RoutePacket none(String name) {
        return new RoutePacket(name, false, new int[0], new int[0]);
    }

    public static void encode(RoutePacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.name, 64);
        buf.writeBoolean(p.ok);
        buf.writeVarInt(p.xs.length);
        for (int i = 0; i < p.xs.length; i++) {
            buf.writeInt(p.xs[i]);
            buf.writeInt(p.zs[i]);
        }
    }

    public static RoutePacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf(64);
        boolean ok = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), 4096);
        int[] xs = new int[n], zs = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = buf.readInt();
            zs[i] = buf.readInt();
        }
        return new RoutePacket(name, ok, xs, zs);
    }

    public static void handle(RoutePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> fr.minenorth.map.client.GpsRoute.onRoute(p.name, p.ok, p.xs, p.zs)));
        ctx.get().setPacketHandled(true);
    }
}
