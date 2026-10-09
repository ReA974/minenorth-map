package fr.minenorth.map.waypoint;

import net.minecraft.network.FriendlyByteBuf;

/** Type de point de repère (défini dans la config serveur) : sa couleur est fixe. color = 0xRRGGBB */
public record WaypointType(String id, String label, int color) {
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(label);
        buf.writeInt(color);
    }

    public static WaypointType read(FriendlyByteBuf buf) {
        return new WaypointType(buf.readUtf(), buf.readUtf(), buf.readInt());
    }
}
