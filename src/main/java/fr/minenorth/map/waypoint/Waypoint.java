package fr.minenorth.map.waypoint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Point de repère placé par un OP. color = 0xRRGGBB, déduit du type au moment de l'envoi aux clients.
 * type = id du type ("" pour les repères perso). company = id de l'entreprise liée (-1 = aucune).
 * status : 0 = sans objet, 1 = ouvert, 2 = fermé (calculé par le serveur pour les points liés à une entreprise).
 */
public record Waypoint(String name, String dimension, int x, int y, int z, int color, String type, int company, int status) {
    public static final String DEFAULT_TYPE = "autre";
    public static final int NO_COMPANY = -1;
    public static final int STATUS_NONE = 0, STATUS_OPEN = 1, STATUS_CLOSED = 2;

    /** Repère sans type (repères perso, repères poussés par un autre mod). */
    public Waypoint(String name, String dimension, int x, int y, int z, int color) {
        this(name, dimension, x, y, z, color, "", NO_COMPANY, STATUS_NONE);
    }

    public Waypoint withName(String n) { return new Waypoint(n, dimension, x, y, z, color, type, company, status); }
    public Waypoint withType(String t) { return new Waypoint(name, dimension, x, y, z, color, t, company, status); }
    public Waypoint withCompany(int c) { return new Waypoint(name, dimension, x, y, z, color, type, c, status); }
    public Waypoint withPos(String dim, int nx, int ny, int nz) { return new Waypoint(name, dim, nx, ny, nz, color, type, company, status); }
    /** Copie envoyée aux clients : couleur du type et état ouvert / fermé. */
    public Waypoint resolved(int c, int s) { return new Waypoint(name, dimension, x, y, z, c, type, company, s); }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("dim", dimension);
        t.putInt("x", x);
        t.putInt("y", y);
        t.putInt("z", z);
        t.putInt("color", color);   // ancienne couleur : repli si le type est inconnu
        t.putString("type", type);
        t.putInt("company", company);
        return t;
    }

    public static Waypoint load(CompoundTag t) {
        // anciennes sauvegardes (couleur seule) : type par défaut, à reclasser avec /carte point type
        String type = t.contains("type") ? t.getString("type") : DEFAULT_TYPE;
        int company = t.contains("company") ? t.getInt("company") : NO_COMPANY;
        return new Waypoint(t.getString("name"), t.getString("dim"),
                t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getInt("color"), type, company, STATUS_NONE);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeUtf(dimension);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(color);
        buf.writeUtf(type);
        buf.writeInt(company);
        buf.writeVarInt(status);
    }

    public static Waypoint read(FriendlyByteBuf buf) {
        return new Waypoint(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readUtf(), buf.readInt(), buf.readVarInt());
    }
}
