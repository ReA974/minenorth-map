package fr.minenorth.map.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur -> client : données compressées d'une région (data null = inexistante ou inchangée). */
public record RegionDataPacket(String dim, int rx, int rz, long version, byte[] data) {

    public static void encode(RegionDataPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.dim, 256);
        buf.writeInt(p.rx);
        buf.writeInt(p.rz);
        buf.writeLong(p.version);
        buf.writeBoolean(p.data != null);
        if (p.data != null) buf.writeByteArray(p.data);
    }

    public static RegionDataPacket decode(FriendlyByteBuf buf) {
        String dim = buf.readUtf(256);
        int rx = buf.readInt(), rz = buf.readInt();
        long v = buf.readLong();
        byte[] data = buf.readBoolean() ? buf.readByteArray() : null;
        return new RegionDataPacket(dim, rx, rz, v, data);
    }

    public static void handle(RegionDataPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> fr.minenorth.map.client.MapStorage.onRegionData(p)));
        ctx.get().setPacketHandled(true);
    }
}
