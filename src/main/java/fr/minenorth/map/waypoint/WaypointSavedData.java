package fr.minenorth.map.waypoint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stockage des points de repère dans le monde (world/data/minenorth_map_waypoints.dat). */
public class WaypointSavedData extends SavedData {
    private static final String ID = "minenorth_map_waypoints";
    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();

    public static WaypointSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(WaypointSavedData::load, WaypointSavedData::new, ID);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static WaypointSavedData load(CompoundTag tag) {
        WaypointSavedData d = new WaypointSavedData();
        ListTag list = tag.getList("waypoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Waypoint w = Waypoint.load(list.getCompound(i));
            d.waypoints.put(key(w.name()), w);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Waypoint w : waypoints.values()) list.add(w.save());
        tag.put("waypoints", list);
        return tag;
    }

    public Waypoint get(String name) { return waypoints.get(key(name)); }

    public boolean exists(String name) { return waypoints.containsKey(key(name)); }

    public void put(Waypoint w) {
        waypoints.put(key(w.name()), w);
        setDirty();
    }

    public Waypoint remove(String name) {
        Waypoint w = waypoints.remove(key(name));
        if (w != null) setDirty();
        return w;
    }

    /** Renomme en conservant l'ordre. */
    public void rename(String oldName, String newName) {
        Map<String, Waypoint> copy = new LinkedHashMap<>(waypoints);
        waypoints.clear();
        for (Map.Entry<String, Waypoint> e : copy.entrySet()) {
            if (e.getKey().equals(key(oldName))) {
                Waypoint w = e.getValue().withName(newName);
                waypoints.put(key(newName), w);
            } else {
                waypoints.put(e.getKey(), e.getValue());
            }
        }
        setDirty();
    }

    public Collection<Waypoint> all() { return waypoints.values(); }

    public List<Waypoint> snapshot() { return new ArrayList<>(waypoints.values()); }
}
