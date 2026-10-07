package fr.minenorth.map.network;

import fr.minenorth.map.client.ClientPlayersView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur -> client : le joueur a-t-il le droit d'afficher les joueurs sur la carte ? */
public record MapPermPacket(boolean canSeePlayers) {
    public static void encode(MapPermPacket p, FriendlyByteBuf buf) { buf.writeBoolean(p.canSeePlayers); }
    public static MapPermPacket decode(FriendlyByteBuf buf) { return new MapPermPacket(buf.readBoolean()); }
    public static void handle(MapPermPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPlayersView.setAllowed(p.canSeePlayers));
        ctx.get().setPacketHandled(true);
    }
}
