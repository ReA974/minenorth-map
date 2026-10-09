package fr.minenorth.map.network;

import fr.minenorth.map.client.ClientEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Serveur -> client : le joueur peut-il éditer les points depuis la carte ? Si oui, entreprises liables ("id|nom"). */
public record EditorInfoPacket(boolean canEdit, List<String> companies) {
    public static void encode(EditorInfoPacket p, FriendlyByteBuf b) {
        b.writeBoolean(p.canEdit);
        b.writeVarInt(p.companies.size());
        for (String c : p.companies) b.writeUtf(c, 128);
    }

    public static EditorInfoPacket decode(FriendlyByteBuf b) {
        boolean can = b.readBoolean();
        int n = Math.min(b.readVarInt(), 2048);
        List<String> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(b.readUtf(128));
        return new EditorInfoPacket(can, l);
    }

    public static void handle(EditorInfoPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientEditor.set(p.canEdit, p.companies));
        ctx.get().setPacketHandled(true);
    }
}
