package fr.minenorth.map.network;

import fr.minenorth.map.server.PlayerTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : activer / couper l'envoi des positions des joueurs (droit vérifié côté serveur). */
public record TogglePlayersPacket(boolean on) {
    public static void encode(TogglePlayersPacket p, FriendlyByteBuf buf) { buf.writeBoolean(p.on); }
    public static TogglePlayersPacket decode(FriendlyByteBuf buf) { return new TogglePlayersPacket(buf.readBoolean()); }
    public static void handle(TogglePlayersPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sender != null) PlayerTracker.setWatching(sender, p.on);
        });
        ctx.get().setPacketHandled(true);
    }
}
