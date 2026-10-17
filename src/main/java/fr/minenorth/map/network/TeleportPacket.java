package fr.minenorth.map.network;

import fr.minenorth.map.server.MapTeleport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> serveur : un administrateur (OP niveau 2, revérifié côté serveur) se téléporte à l'endroit cliqué sur la carte
 * (Maj + clic droit). hasY = faux : la hauteur est celle de la surface.
 */
public record TeleportPacket(String dim, int x, int y, int z, boolean hasY) {
    public static void encode(TeleportPacket p, FriendlyByteBuf b) {
        b.writeUtf(p.dim, 128);
        b.writeInt(p.x);
        b.writeInt(p.y);
        b.writeInt(p.z);
        b.writeBoolean(p.hasY);
    }

    public static TeleportPacket decode(FriendlyByteBuf b) {
        return new TeleportPacket(b.readUtf(128), b.readInt(), b.readInt(), b.readInt(), b.readBoolean());
    }

    public static void handle(TeleportPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sender != null) MapTeleport.handle(sender, p);
        });
        ctx.get().setPacketHandled(true);
    }
}
