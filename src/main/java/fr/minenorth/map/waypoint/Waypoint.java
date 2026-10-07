package fr.minenorth.map.waypoint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** Point de repère placé par un OP. color = 0xRRGGBB */
public record Waypoint(String name, String dimension, int x, int y, int z, int color) {

    public Waypoint withName(String n) { return new Waypoint(n, dimension, x, y, z, color); }
    public Waypoint withColor(int c) { return new Waypoint(name, dimension, x, y, z, c); }
    public Waypoint withPos(String dim, int nx, int ny, int nz) { return new Waypoint(name, dim, nx, ny, nz, color); }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("dim", dimension);
        t.putInt("x", x);
        t.putInt("y", y);
        t.putInt("z", z);
        t.putInt("color", color);
        return t;
    }

    public static Waypoint load(CompoundTag t) {
        return new Waypoint(t.getString("name"), t.getString("dim"),
                t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getInt("color"));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeUtf(dimension);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(color);
    }

    public static Waypoint read(FriendlyByteBuf buf) {
        return new Waypoint(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
    }
}
